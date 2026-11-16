package com.rafelo.picks;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Short-lived background job: opens a customer's shared cart link in an invisible web view,
 * collects the items and sends them to the website. Shows a small notification while it works
 * (Android requires it) and stops itself when the queue is empty.
 */
public class ReaderService extends Service {

    static final String EXTRA_JOB = "job";
    static final String EXTRA_PING = "ping";
    static final String EXTRA_PENDING = "pending";
    private static final String CHANNEL = "reader";
    private static final String ALERT_CHANNEL = "customers";
    private static final int NOTE_ID = 7;
    private static final long CART_TIMEOUT_MS = 35000;
    private static final long QUIET_MS = 3000;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ArrayDeque<String> queue = new ArrayDeque<>();
    private final Set<String> seenJobs = new HashSet<>();
    private boolean busy = false;
    private boolean checkedPending = false;

    private WebView web;
    private String hookJs = "";
    private volatile String currentHost = "";

    // Current job
    private String jobId;
    private int jobToken = 0;
    private final Map<String, JSONObject> items = new LinkedHashMap<>();
    private int expected = 0;
    private long jobStarted;

    @Override
    public void onCreate() {
        super.onCreate();
        hookJs = readAsset("hook.js");
        Notification n = note("جارٍ حساب سلة زبون…");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            startForeground(NOTE_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        else startForeground(NOTE_ID, n);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getBooleanExtra(EXTRA_PING, false)) {
            log(null, false, 0, 0, 0, "تجربة إيقاظ من صفحة التحكم · وصلت");
            alert("تجربة الإيقاظ وصلت", "هاتف القارئ يعمل ويستقبل طلبات الزبائن.");
        }
        String job = intent == null ? null : intent.getStringExtra(EXTRA_JOB);
        if (job != null && seenJobs.add(job)) queue.add(job);
        if (intent != null && intent.getBooleanExtra(EXTRA_PENDING, false)) checkedPending = false;
        next();
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        if (web != null) { web.destroy(); web = null; }
        io.shutdown();
        super.onDestroy();
    }

    // ---------- queue ----------

    private void next() {
        if (busy) return;
        String job = queue.poll();
        if (job != null) { start(job); return; }
        if (!checkedPending && Reader.paired(this)) {
            // Catch customers whose wake-up was missed or arrived while we were busy.
            checkedPending = true;
            io.execute(() -> {
                try {
                    JSONObject r = Reader.call(this, "GET", "/api/reader/pending", null);
                    JSONArray a = r.optJSONArray("jobs");
                    ui.post(() -> {
                        if (a != null) for (int i = 0; i < a.length(); i++) {
                            String id = a.optString(i);
                            if (seenJobs.add(id)) queue.add(id);
                        }
                        next();
                    });
                } catch (Exception e) {
                    ui.post(this::next);
                }
            });
            return;
        }
        finish();
    }

    private void finish() {
        if (web != null) { web.destroy(); web = null; }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE);
        else stopForeground(true);
        stopSelf();
    }

    private void start(String id) {
        busy = true;
        jobId = id;
        items.clear();
        expected = 0;
        jobStarted = System.currentTimeMillis();
        final int token = ++jobToken;
        io.execute(() -> {
            try {
                JSONObject j = Reader.call(this, "GET", "/api/reader/job/" + id, null);
                String url = j.optString("url");
                String status = j.optString("status");
                if (url.isEmpty() || "done".equals(status) || "failed".equals(status)) { ui.post(() -> done(token)); return; }
                String host = Uri.parse(url).getHost();
                if (host == null || !(host.equals("shein.com") || host.endsWith(".shein.com"))) {
                    report(token, null, "bad-url");
                    return;
                }
                ui.post(() -> load(token, url));
            } catch (Exception e) {
                ui.post(() -> done(token));
            }
        });
    }

    private void load(int token, String url) {
        if (token != jobToken) return;
        if (web == null) web = makeWebView();
        web.loadUrl(url);
        ui.postDelayed(() -> {
            if (token != jobToken) return;
            if (items.isEmpty()) report(token, null, "timeout");
            else report(token, collected(), null);
        }, CART_TIMEOUT_MS);
    }

    // Called from the page: one batch of cart items (long carts may arrive in several).
    private void onItems(String list) {
        if (jobId == null) return;
        final int token = jobToken;
        try {
            JSONArray a = new JSONArray(list);
            for (int i = 0; i < a.length(); i++) {
                JSONObject p = a.optJSONObject(i);
                if (p != null && !p.optString("id").isEmpty()) items.put(p.optString("id"), p);
            }
        } catch (Exception ignored) { }
        if (items.isEmpty()) return;
        if (expected > 0 && items.size() >= expected) {
            ui.postDelayed(() -> { if (token == jobToken) report(token, collected(), null); }, 500);
            return;
        }
        // Scroll to make the page load any remaining items, then send once it goes quiet.
        if (web != null) web.evaluateJavascript("window.scrollTo(0, document.body.scrollHeight);", null);
        final int size = items.size();
        ui.postDelayed(() -> {
            if (token == jobToken && items.size() == size) report(token, collected(), null);
        }, QUIET_MS);
    }

    private JSONArray collected() { return new JSONArray(items.values()); }

    private void report(int token, JSONArray list, String error) {
        if (token != jobToken) return;
        jobToken++; // nothing else may report for this job
        final String id = jobId;
        final long secs = (System.currentTimeMillis() - jobStarted) / 1000;
        io.execute(() -> {
            boolean ok = false;
            double totalUsd = 0;
            long totalIqd = 0;
            int count = 0;
            try {
                JSONObject b = new JSONObject();
                if (list != null) b.put("items", list); else b.put("error", error);
                JSONObject r = Reader.call(this, "POST", "/api/reader/job/" + id, b);
                ok = list != null && r.optBoolean("ok");
                totalUsd = r.optDouble("totalUsd", 0);
                totalIqd = r.optLong("totalIqd", 0);
                count = r.optInt("count", list == null ? 0 : list.length());
            } catch (Exception ignored) { }
            String err = list != null ? null : ("timeout".equals(error) ? "لم تُقرأ السلة خلال المهلة" : "رابط غير صالح");
            String mode = guestMode ? " · كزائر" : " · بحسابك (الهاتف لا يدعم وضع الزائر)";
            if (list != null && list.length() > 0) {
                JSONObject first = list.optJSONObject(0);
                if (first != null && !first.optString("local").isEmpty())
                    mode += " · أول قطعة: " + first.optString("local") + " = $" + first.optString("usd");
            }
            log(id, ok, count, totalUsd, totalIqd, (err == null ? secs + " ثانية" : err) + mode);
            if (ok) alert("زبون حسب سلته", count + " قطعة · " + String.format(java.util.Locale.US, "%,d", totalIqd) + " د.ع");
            else alert("تعذر حساب سلة زبون", err == null ? "لم يقبل الموقع النتيجة" : err);
            ui.post(() -> done(-1));
        });
    }

    private void done(int ignored) {
        jobId = null;
        busy = false;
        next();
    }

    private void log(String id, boolean ok, int count, double usd, long iqd, String note) {
        try {
            JSONObject e = new JSONObject();
            e.put("t", System.currentTimeMillis());
            e.put("job", id == null ? "" : id);
            e.put("ok", ok);
            e.put("count", count);
            e.put("usd", usd);
            e.put("iqd", iqd);
            e.put("note", note);
            Reader.addLog(this, e);
        } catch (Exception ignored) { }
    }

    // ---------- web view ----------

    private static final String GUEST_PROFILE = "guest";
    private boolean guestMode = false;

    private WebView makeWebView() {
        WebView w = new WebView(this);
        // Read customers' carts as a signed-out visitor, so prices show the public offers only
        // and never the owner's account coupons. The owner's login lives in the default profile.
        guestMode = false;
        CookieManager cookies = CookieManager.getInstance();
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            try {
                androidx.webkit.Profile p = androidx.webkit.ProfileStore.getInstance().getOrCreateProfile(GUEST_PROFILE);
                WebViewCompat.setProfile(w, GUEST_PROFILE);
                cookies = p.getCookieManager();
                guestMode = true;
            } catch (Exception ignored) { }
        }
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setBlockNetworkImage(true); // images are not needed to read prices
        s.setUserAgentString(s.getUserAgentString().replace("; wv", "").replace("Version/4.0 ", ""));
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(w, true);
        // The view is never on screen; give it a phone-sized layout so the page renders normally.
        w.layout(0, 0, 1080, 2400);
        w.addJavascriptInterface(new Bridge(), "PicksBridge");
        boolean startScript = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT);
        if (startScript) {
            Set<String> origins = new HashSet<>();
            origins.add("https://*.shein.com");
            WebViewCompat.addDocumentStartJavaScript(w, hookJs, origins);
        }
        w.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                String scheme = req.getUrl().getScheme();
                String host = req.getUrl().getHost() == null ? "" : req.getUrl().getHost();
                if (!("http".equals(scheme) || "https".equals(scheme))) return true;
                // Only SHEIN pages are opened here.
                return !(host.equals("shein.com") || host.endsWith(".shein.com"));
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                currentHost = host(url);
                if (!startScript && isShein(currentHost)) view.evaluateJavascript(hookJs, null);
            }

            @Override
            public void onPageFinished(WebView view, String url) { currentHost = host(url); }
        });
        w.resumeTimers();
        return w;
    }

    private class Bridge {
        @JavascriptInterface public void cartData(String list) { if (isShein(currentHost)) ui.post(() -> onItems(list)); }
        @JavascriptInterface public void cartTotal(String n) {
            try { int v = Integer.parseInt(n); ui.post(() -> expected = v); } catch (Exception ignored) { }
        }
        @JavascriptInterface public boolean isScanning() { return false; }
        @JavascriptInterface public void scanIds(String s) { }
        @JavascriptInterface public void productSeen(String s) { }
        @JavascriptInterface public void pickResult(String s) { }
        @JavascriptInterface public void cartAddResult(String s) { }
    }

    // ---------- helpers ----------

    private Notification note(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (nm.getNotificationChannel(CHANNEL) == null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL, "حساب سلال الزبائن", NotificationManager.IMPORTANCE_LOW);
                ch.setShowBadge(false);
                nm.createNotificationChannel(ch);
            }
            b = new Notification.Builder(this, CHANNEL);
        } else {
            b = new Notification.Builder(this);
        }
        return b.setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("زهرة SHEIN")
                .setContentText(text)
                .setOngoing(true)
                .build();
    }

    // Customer alerts: sound and a pop-up, as the owner asked. Tapping opens the app.
    private void alert(String title, String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (nm.getNotificationChannel(ALERT_CHANNEL) == null) {
                NotificationChannel ch = new NotificationChannel(ALERT_CHANNEL, "زبائن الموقع", NotificationManager.IMPORTANCE_HIGH);
                ch.enableVibration(true);
                nm.createNotificationChannel(ch);
            }
            b = new Notification.Builder(this, ALERT_CHANNEL);
        } else {
            b = new Notification.Builder(this).setPriority(Notification.PRIORITY_HIGH).setDefaults(Notification.DEFAULT_ALL);
        }
        android.app.PendingIntent open = android.app.PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT);
        nm.notify((int) (System.currentTimeMillis() % 100000) + 100, b.setSmallIcon(android.R.drawable.stat_notify_more)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build());
    }

    private static String host(String url) {
        try { String h = Uri.parse(url).getHost(); return h == null ? "" : h; } catch (Exception e) { return ""; }
    }

    private static boolean isShein(String h) { return h.equals("shein.com") || h.endsWith(".shein.com"); }

    private String readAsset(String name) {
        try (InputStream in = getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } catch (Exception e) {
            return "";
        }
    }
}
