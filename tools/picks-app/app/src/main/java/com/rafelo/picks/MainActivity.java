package com.rafelo.picks;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
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
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    private static final String AFFILIATE_URL = "https://m.shein.com/ar/affiliate";
    private static final String HOME_URL = "https://m.shein.com/ar/user/index";
    private static final int TEAL = Color.parseColor("#0D6E6E");
    private static final int RED = Color.parseColor("#B3261E");

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<String> log = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean recording = false;
    private volatile String currentHost = "";

    private WebView web;
    private EditText linkInput;
    private TextView status;
    private Button recordBtn;

    private String hookJs = "";
    private JSONArray products = new JSONArray();
    private String rawCart = "";
    private boolean waitingForCart = false;

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
        panel.setBackgroundColor(Color.parseColor("#EEF2F3"));

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);
        linkInput = new EditText(this);
        linkInput.setHint("الصق رسالة أو رابط السلة هنا");
        linkInput.setSingleLine(true);
        linkInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        linkInput.setTextSize(14);
        row1.addView(linkInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row1.addView(button("لصق", v -> pasteFromClipboard(), false));
        row1.addView(button("قراءة السلة", v -> openCart(), true));
        panel.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.addView(button("المنتجات", v -> showProducts(), false));
        row2.addView(button("برنامج الشركاء", v -> web.loadUrl(AFFILIATE_URL), false));
        recordBtn = button("بدء التسجيل", v -> toggleRecording(), false);
        row2.addView(recordBtn);
        row2.addView(button("نسخ السجل", v -> copyLog(), false));
        row2.addView(button("حسابي", v -> web.loadUrl(HOME_URL), false));
        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.addView(row2);
        panel.addView(scroller);

        status = new TextView(this);
        status.setTextSize(13);
        status.setTextColor(Color.parseColor("#16232A"));
        status.setPadding(0, dp(4), 0, 0);
        status.setText("سجّل الدخول بحسابك الرئيسي في الصفحة بالأسفل أول مرة، ثم الصق رابط السلة.");
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

    // ---------- web view ----------

    private void setupWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        // Present as regular mobile Chrome rather than an embedded web view.
        s.setUserAgentString(s.getUserAgentString().replace("; wv", "").replace("Version/4.0 ", ""));

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        web.addJavascriptInterface(new Bridge(), "PicksBridge");

        final boolean startScript = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT);
        if (startScript) {
            java.util.Set<String> origins = new java.util.HashSet<>();
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
                if (waitingForCart && url.contains("/cart/share")) {
                    // If the site's own request was not seen in 8 s, read the products off the page instead.
                    ui.postDelayed(() -> { if (waitingForCart) scrapeCartPage(); }, 8000);
                }
            }
        });
    }

    private class Bridge {
        @JavascriptInterface
        public void log(String entry) {
            if (!isShein(currentHost) || !recording) return;
            if (log.size() >= 200) log.remove(0);
            log.add(entry);
            ui.post(() -> status.setText("التسجيل يعمل: " + log.size() + " طلب. اضغط «اختار» على منتج واحد ثم «نسخ السجل»."));
        }

        @JavascriptInterface
        public void cartData(String list, String raw) {
            if (!isShein(currentHost)) return;
            ui.post(() -> onCartProducts(list, raw, "استجابة الموقع"));
        }
    }

    // ---------- cart ----------

    private void openCart() {
        String url = extractUrl(linkInput.getText().toString());
        if (url == null) {
            status.setText("لم أجد رابطاً في النص. الصق رسالة المشاركة أو الرابط الذي يبدأ بـ https.");
            status.setTextColor(RED);
            return;
        }
        status.setTextColor(Color.parseColor("#16232A"));
        products = new JSONArray();
        rawCart = "";
        waitingForCart = true;
        status.setText("أفتح السلة وأقرأ المنتجات…");
        web.loadUrl(url);
    }

    private void onCartProducts(String list, String raw, String source) {
        try {
            JSONArray arr = new JSONArray(list);
            if (raw != null && !raw.isEmpty()) rawCart = raw;
            if (arr.length() == 0) {
                status.setText("فتحت السلة لكن لم أجد فيها منتجات بعد. انتظر قليلاً أو اضغط «قراءة السلة» مرة أخرى.");
                return;
            }
            waitingForCart = false;
            products = arr;
            status.setText("وجدت " + arr.length() + " منتجات في السلة (" + source + "). اضغط «المنتجات» لعرضها.");
            showProducts();
        } catch (Exception e) {
            status.setText("تعذرت قراءة المنتجات: " + e.getMessage());
        }
    }

    private void scrapeCartPage() {
        String js = "(function(){var out=[],seen={};"
                + "document.querySelectorAll('a[href]').forEach(function(a){var m=a.href.match(/-p-(\\d+)/);"
                + "if(m&&!seen[m[1]]){seen[m[1]]=1;out.push({id:m[1],name:(a.innerText||a.title||'').trim().slice(0,120),sn:'',sku:'',mall:'',img:''});}});"
                + "document.querySelectorAll('[data-goods-id],[goods-id],[data-id]').forEach(function(el){var id=el.getAttribute('data-goods-id')||el.getAttribute('goods-id')||el.getAttribute('data-id');"
                + "if(id&&/^\\d{5,}$/.test(id)&&!seen[id]){seen[id]=1;out.push({id:id,name:(el.innerText||'').trim().split('\\n')[0].slice(0,120),sn:'',sku:'',mall:'',img:''});}});"
                + "return JSON.stringify(out);})()";
        web.evaluateJavascript(js, value -> {
            try {
                String list = new JSONArray("[" + value + "]").getString(0);
                onCartProducts(list, "", "من الصفحة");
            } catch (Exception e) {
                status.setText("لم أستطع قراءة المنتجات من الصفحة.");
            }
        });
    }

    private void showProducts() {
        if (products.length() == 0) {
            toast("لا توجد منتجات بعد. الصق رابط السلة واضغط «قراءة السلة».");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < products.length(); i++) {
            JSONObject p = products.optJSONObject(i);
            if (p == null) continue;
            sb.append(i + 1).append(". ").append(p.optString("name")).append("\n")
              .append("   رقم المنتج: ").append(p.optString("id"));
            if (!p.optString("sn").isEmpty()) sb.append("  |  SKU: ").append(p.optString("sn"));
            sb.append("\n\n");
        }
        TextView tv = new TextView(this);
        tv.setText(sb.toString().trim());
        tv.setTextIsSelectable(true);
        tv.setPadding(dp(20), dp(12), dp(20), dp(12));
        tv.setTextSize(14);
        ScrollView sv = new ScrollView(this);
        sv.addView(tv);
        new AlertDialog.Builder(this)
                .setTitle("منتجات السلة (" + products.length() + ")")
                .setView(sv)
                .setPositiveButton("نسخ للمطوّر", (d, w) -> copyCartForDeveloper())
                .setNegativeButton("إغلاق", null)
                .show();
    }

    private void copyCartForDeveloper() {
        try {
            JSONObject o = new JSONObject();
            o.put("app", "picks 0.1");
            o.put("products", products);
            o.put("raw", rawCart);
            copy(o.toString(1), "نُسخت بيانات السلة. أرسلها كما هي.");
        } catch (Exception e) {
            toast("تعذر النسخ.");
        }
    }

    // ---------- recording ----------

    private void toggleRecording() {
        recording = !recording;
        if (recording) {
            log.clear();
            recordBtn.setText("إيقاف التسجيل");
            recordBtn.setTextColor(RED);
            status.setText("التسجيل يعمل. افتح برنامج الشركاء، واضغط «اختار» على منتج واحد فقط، ثم «نسخ السجل».");
        } else {
            recordBtn.setText("بدء التسجيل");
            recordBtn.setTextColor(Color.BLACK);
            status.setText("توقف التسجيل. في السجل " + log.size() + " طلب.");
        }
    }

    private void copyLog() {
        if (log.isEmpty()) {
            toast("السجل فارغ. اضغط «بدء التسجيل» ثم «اختار» على منتج.");
            return;
        }
        StringBuilder sb = new StringBuilder("picks 0.1 log\n");
        synchronized (log) {
            for (String l : log) sb.append(l).append("\n");
        }
        copy(sb.toString(), "نُسخ السجل (" + log.size() + " طلب). راجعه ثم أرسله.");
    }

    // ---------- helpers ----------

    private boolean handleShareIntent(Intent intent) {
        if (intent != null && Intent.ACTION_SEND.equals(intent.getAction())) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (text != null && extractUrl(text) != null) {
                linkInput.setText(text);
                openCart();
                return true;
            }
        }
        return false;
    }

    private void pasteFromClipboard() {
        ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cb != null && cb.hasPrimaryClip() && cb.getPrimaryClip().getItemCount() > 0) {
            CharSequence t = cb.getPrimaryClip().getItemAt(0).coerceToText(this);
            linkInput.setText(t);
        }
    }

    private void copy(String text, String msg) {
        ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cb != null) cb.setPrimaryClip(ClipData.newPlainText("picks", text));
        toast(msg);
    }

    static String extractUrl(String text) {
        if (text == null) return null;
        Matcher m = Pattern.compile("https?://[^\\s]+").matcher(text);
        return m.find() ? m.group() : null;
    }

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
