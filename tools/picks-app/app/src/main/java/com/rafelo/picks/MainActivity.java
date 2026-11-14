package com.rafelo.picks;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    private static final String AFFILIATE_URL = "https://m.shein.com/ar/affiliate";
    private static final String HOME_URL = "https://m.shein.com/ar/user/index";
    private static final int TEAL = Color.parseColor("#2F7FB8");
    private static final int RED = Color.parseColor("#B3261E");
    private static final int INK = Color.parseColor("#16232A");
    private static final long CART_TIMEOUT_MS = 20000;
    private static final int CHUNK = 20;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private volatile String currentHost = "";

    private WebView web;
    private EditText linkInput;
    private TextView status;

    private String hookJs = "";

    // All products read so far, by goods id, in the order they were found.
    private final Map<String, JSONObject> products = new LinkedHashMap<>();

    // Reading several cart links one after another.
    private final List<String> cartQueue = new ArrayList<>();
    private final List<String> cartReport = new ArrayList<>();
    private int cartIndex = -1;
    private int cartToken = 0;
    private boolean readingCarts = false;

    // Adding to "اختياراتي".
    private boolean picking = false;
    private boolean sendScheduled = false;
    private boolean rewriteArmed = false;
    private int pickAttempts = 0;
    private int chunkIndex = 0;
    private final List<JSONArray> chunks = new ArrayList<>();
    private JSONArray pickTargets = new JSONArray();
    private final Set<String> pickedIds = new HashSet<>();

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        hookJs = readAsset("hook.js");
        setContentView(buildLayout());
        setupWebView();
        if (saved != null) web.restoreState(saved);
        else if (!handleShareIntent(getIntent())) web.loadUrl(HOME_URL);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleShareIntent(intent);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onPause() { super.onPause(); web.onPause(); }

    @Override
    protected void onResume() { super.onResume(); web.onResume(); }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    // ---------- layout ----------

    private View buildLayout() {
        int pad = dp(10);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(Color.WHITE);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(pad, pad, pad, dp(6));
        panel.setBackgroundColor(Color.parseColor("#E7F3FA"));

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);
        linkInput = new EditText(this);
        linkInput.setHint("الصق رابطاً أو أكثر، كل رابط في سطر");
        linkInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_VARIATION_URI);
        linkInput.setMinLines(2);
        linkInput.setMaxLines(4);
        linkInput.setTextSize(13);
        row1.addView(linkInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        side.addView(button("لصق", v -> pasteFromClipboard(), false));
        side.addView(button("مسح", v -> { linkInput.setText(""); }, false));
        row1.addView(side);
        panel.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.addView(button("نقل إلى اختياراتي", v -> startFromLinks(), true));
        row2.addView(button("المنتجات", v -> showProducts(), false));
        row2.addView(button("برنامج الشركاء", v -> web.loadUrl(AFFILIATE_URL), false));
        row2.addView(button("حسابي", v -> web.loadUrl(HOME_URL), false));
        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.addView(row2);
        panel.addView(scroller);

        status = new TextView(this);
        status.setTextSize(13);
        status.setTextColor(INK);
        status.setPadding(0, dp(4), 0, 0);
        status.setText("الصق رابط سلة أو أكثر، ثم اضغط «نقل إلى اختياراتي».");
        panel.addView(status);

        root.addView(panel);

        web = new WebView(this);
        root.addView(web, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        return root;
    }

    private Button button(String text, View.OnClickListener l, boolean primary) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setOnClickListener(l);
        if (primary) {
            b.setBackgroundColor(TEAL);
            b.setTextColor(Color.WHITE);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(dp(4));
        b.setLayoutParams(lp);
        return b;
    }

    private void setStatus(String text, int color) {
        status.setTextColor(color);
        status.setText(text);
    }

    // ---------- web view ----------

    private void setupWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        // Present as regular mobile Chrome rather than an embedded web view.
        s.setUserAgentString(s.getUserAgentString().replace("; wv", "").replace("Version/4.0 ", ""));
        // Smoother scrolling on long product pages.
        s.setOffscreenPreRaster(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            web.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true);
        }
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        web.addJavascriptInterface(new Bridge(), "PicksBridge");

        final boolean startScript = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT);
        if (startScript) {
            Set<String> origins = new HashSet<>();
            origins.add("https://*.shein.com");
            WebViewCompat.addDocumentStartJavaScript(web, hookJs, origins);
        }

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                String scheme = req.getUrl().getScheme();
                // Keep everything inside the app: block jumps to the SHEIN app or the Play Store.
                return !("http".equals(scheme) || "https".equals(scheme));
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                currentHost = hostOf(url);
                if (!startScript && isShein(currentHost)) view.evaluateJavascript(hookJs, null);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                currentHost = hostOf(url);
                if (picking && !sendScheduled && url.contains("/affiliate")) {
                    sendScheduled = true;
                    ui.postDelayed(MainActivity.this::trySendChunk, 4000);
                }
                if (rewriteArmed && isShein(currentHost)) armRewriteInPage();
            }
        });
    }

    private class Bridge {
        @JavascriptInterface
        public void cartData(String list) {
            if (!isShein(currentHost)) return;
            ui.post(() -> onCartProducts(list));
        }

        @JavascriptInterface
        public void pickResult(String json) {
            if (!isShein(currentHost)) return;
            ui.post(() -> onPickResult(json));
        }
    }

    // ---------- reading carts ----------

    private void startFromLinks() {
        List<String> urls = extractUrls(linkInput.getText().toString());
        if (urls.isEmpty()) {
            setStatus("لم أجد رابطاً. الصق رسالة المشاركة أو الرابط الذي يبدأ بـ https.", RED);
            return;
        }
        picking = false;
        rewriteArmed = false;
        products.clear();
        cartQueue.clear();
        cartQueue.addAll(urls);
        cartReport.clear();
        cartIndex = -1;
        readingCarts = true;
        nextCart();
    }

    private void nextCart() {
        cartIndex++;
        if (cartIndex >= cartQueue.size()) {
            readingCarts = false;
            if (products.isEmpty()) {
                setStatus("لم أجد منتجات في الروابط. تأكد أنها روابط سلة مشتركة.", RED);
                showReport("لم أجد منتجات", String.join("\n", cartReport));
                return;
            }
            startPick(new ArrayList<>(products.values()));
            return;
        }
        final int token = ++cartToken;
        setStatus("أقرأ السلة " + (cartIndex + 1) + " من " + cartQueue.size() + "…", INK);
        web.loadUrl(cartQueue.get(cartIndex));
        ui.postDelayed(() -> {
            if (readingCarts && token == cartToken) {
                cartReport.add("✗ الرابط " + (cartIndex + 1) + ": لم تُقرأ السلة (انتهت المهلة)");
                nextCart();
            }
        }, CART_TIMEOUT_MS);
    }

    private void onCartProducts(String list) {
        if (!readingCarts) return;
        try {
            JSONArray arr = new JSONArray(list);
            int added = 0;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject p = arr.optJSONObject(i);
                if (p == null) continue;
                String id = p.optString("id");
                if (!id.isEmpty() && !products.containsKey(id)) { products.put(id, p); added++; }
            }
            if (arr.length() == 0) return; // wait for the next response or the timeout
            cartReport.add("✓ الرابط " + (cartIndex + 1) + ": " + arr.length() + " منتجات"
                    + (added < arr.length() ? " (" + (arr.length() - added) + " مكررة)" : ""));
            cartToken++; // cancel this cart's timeout
            ui.postDelayed(this::nextCart, 800);
        } catch (Exception e) {
            // ignore malformed data; the timeout moves on
        }
    }

    private void showProducts() {
        if (products.isEmpty()) {
            toast("لا توجد منتجات بعد. الصق رابط السلة واضغط «نقل إلى اختياراتي».");
            return;
        }
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (JSONObject p : products.values()) {
            sb.append(i++).append(". ").append(shorten(p.optString("name"), 80)).append("\n")
              .append("   رقم المنتج: ").append(p.optString("id")).append("\n\n");
        }
        showReport("منتجات السلة (" + products.size() + ")", sb.toString().trim());
    }

    // ---------- adding to "اختياراتي" ----------

    private void startPick(List<JSONObject> items) {
        pickTargets = new JSONArray(items);
        chunks.clear();
        JSONArray cur = new JSONArray();
        for (JSONObject p : items) {
            try {
                JSONObject o = new JSONObject();
                o.put("id", p.optString("id"));
                o.put("sn", p.optString("sn"));
                cur.put(o);
            } catch (Exception ignored) { }
            if (cur.length() == CHUNK) { chunks.add(cur); cur = new JSONArray(); }
        }
        if (cur.length() > 0) chunks.add(cur);
        chunkIndex = 0;
        pickAttempts = 0;
        pickedIds.clear();
        rewriteArmed = false;
        picking = true;
        sendScheduled = false;
        setStatus("أضيف " + items.size() + " منتجات إلى اختياراتي…", INK);
        web.loadUrl(AFFILIATE_URL);
    }

    private void trySendChunk() {
        if (!picking || chunkIndex >= chunks.size()) return;
        String items = chunks.get(chunkIndex).toString();
        String js = "(function(){if(!window.__picksPick)return 'nohook';window.__picksPick(" + items + ");return 'sent';})()";
        web.evaluateJavascript(js, v -> {
            if (v != null && v.contains("nohook")) onPickResult("{\"mode\":\"direct\",\"ok\":false,\"reason\":\"no-headers\"}");
        });
    }

    private void onPickResult(String json) {
        try {
            JSONObject r = new JSONObject(json);
            String mode = r.optString("mode");
            if ("direct".equals(mode) && !picking) return;
            if ("no-headers".equals(r.optString("reason"))) {
                if (++pickAttempts < 4) {
                    setStatus("أنتظر أن تكتمل صفحة برنامج الشركاء… (" + pickAttempts + ")", INK);
                    ui.postDelayed(this::trySendChunk, 3000);
                } else {
                    armRewrite("لم يكتمل تحميل برنامج الشركاء أو لم تسجّل الدخول.");
                }
                return;
            }
            JSONObject resp = null;
            try { resp = new JSONObject(r.optString("resp")); } catch (Exception ignored) { }
            boolean okCode = r.optBoolean("ok") && resp != null && "0".equals(resp.optString("code"));
            if (okCode) {
                JSONObject info = resp.optJSONObject("info");
                JSONArray list = info == null ? null : info.optJSONArray("pickedGoodsIdList");
                if (list != null) for (int i = 0; i < list.length(); i++) pickedIds.add(String.valueOf(list.optLong(i)));
                if ("direct".equals(mode) && ++chunkIndex < chunks.size()) {
                    setStatus("أُضيفت دفعة " + chunkIndex + " من " + chunks.size() + "…", INK);
                    ui.postDelayed(this::trySendChunk, 1500);
                } else {
                    finishPick();
                }
                return;
            }
            String msg = resp != null ? resp.optString("msg") : ("رمز " + r.optInt("status"));
            if ("direct".equals(mode)) armRewrite("رفض الموقع الإضافة المباشرة (" + msg + ").");
            else {
                rewriteArmed = false;
                setStatus("رفض الموقع الإضافة: " + msg, RED);
            }
        } catch (Exception e) {
            setStatus("نتيجة غير مفهومة من الموقع.", RED);
        }
    }

    // Fallback: the user taps "اختار" on any product and the app adds the cart products to that same request.
    private void armRewrite(String reason) {
        picking = false;
        rewriteArmed = true;
        armRewriteInPage();
        setStatus(reason + " اضغط «اختار» على أي منتج غير مختار في هذه الصفحة، وسأضيف معه منتجات السلة.", RED);
    }

    private void armRewriteInPage() {
        JSONArray remaining = new JSONArray();
        for (int i = chunkIndex; i < chunks.size(); i++) {
            JSONArray c = chunks.get(i);
            for (int j = 0; j < c.length(); j++) remaining.put(c.opt(j));
        }
        web.evaluateJavascript("window.__picksRewrite=" + remaining + ";", null);
    }

    private void finishPick() {
        picking = false;
        rewriteArmed = false;
        web.evaluateJavascript("window.__picksRewrite=null;", null);
        List<JSONObject> failed = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        if (!cartReport.isEmpty()) sb.append(String.join("\n", cartReport)).append("\n\n");
        int ok = 0;
        for (int i = 0; i < pickTargets.length(); i++) {
            JSONObject p = pickTargets.optJSONObject(i);
            if (p == null) continue;
            boolean done = pickedIds.isEmpty() || pickedIds.contains(p.optString("id"));
            if (done) ok++; else failed.add(p);
            sb.append(done ? "✓ " : "✗ ").append(shorten(p.optString("name"), 60)).append("\n");
        }
        setStatus("أُضيف " + ok + " من " + pickTargets.length() + " إلى اختياراتي.", failed.isEmpty() ? TEAL : RED);
        AlertDialog.Builder d = dialog("أُضيف " + ok + " من " + pickTargets.length(), sb.toString().trim());
        if (!failed.isEmpty()) d.setPositiveButton("إعادة محاولة الفاشلة", (x, w) -> startPick(failed));
        d.show();
    }

    // ---------- helpers ----------

    private void showReport(String title, String body) { dialog(title, body).show(); }

    private AlertDialog.Builder dialog(String title, String body) {
        TextView tv = new TextView(this);
        tv.setText(body);
        tv.setTextIsSelectable(true);
        tv.setPadding(dp(20), dp(12), dp(20), dp(12));
        tv.setTextSize(14);
        ScrollView sv = new ScrollView(this);
        sv.addView(tv);
        return new AlertDialog.Builder(this).setTitle(title).setView(sv).setNegativeButton("إغلاق", null);
    }

    private boolean handleShareIntent(Intent intent) {
        if (intent != null && Intent.ACTION_SEND.equals(intent.getAction())) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (text != null && !extractUrls(text).isEmpty()) {
                linkInput.setText(text);
                startFromLinks();
                return true;
            }
        }
        return false;
    }

    private void pasteFromClipboard() {
        ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cb != null && cb.hasPrimaryClip() && cb.getPrimaryClip().getItemCount() > 0) {
            CharSequence t = cb.getPrimaryClip().getItemAt(0).coerceToText(this);
            String cur = linkInput.getText().toString().trim();
            linkInput.setText(cur.isEmpty() ? t : cur + "\n" + t);
            linkInput.setSelection(linkInput.getText().length());
        }
    }

    static List<String> extractUrls(String text) {
        Set<String> out = new LinkedHashSet<>();
        if (text != null) {
            Matcher m = Pattern.compile("https?://[^\\s]+").matcher(text);
            while (m.find()) out.add(m.group());
        }
        return new ArrayList<>(out);
    }

    private static String shorten(String s, int n) { return s.length() > n ? s.substring(0, n) + "…" : s; }

    private static String hostOf(String url) {
        try { String h = Uri.parse(url).getHost(); return h == null ? "" : h; }
        catch (Exception e) { return ""; }
    }

    private static boolean isShein(String host) {
        return host.equals("shein.com") || host.endsWith(".shein.com");
    }

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

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
