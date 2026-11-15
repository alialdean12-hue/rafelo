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

    private static final String VERSION = "0.6";
    private static final String AFFILIATE_URL = "https://m.shein.com/ar/affiliate";
    private static final String HOME_URL = "https://m.shein.com/ar/user/index";
    private static final String CART_URL = "https://m.shein.com/ar/cart";
    private static final int BLUE = Color.parseColor("#2F7FB8");
    private static final int RED = Color.parseColor("#B3261E");
    private static final int INK = Color.parseColor("#16232A");
    private static final int PANEL = Color.parseColor("#E7F3FA");
    private static final long CART_TIMEOUT_MS = 20000;
    private static final long LINK_TIMEOUT_MS = 25000;
    private static final int CHUNK = 20;

    private enum Goal { PICKS, PARTNER_CART }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private volatile String currentHost = "";

    private WebView web;
    private TextView status;
    private Button tabPicks, tabPartner;
    private LinearLayout picksPanel, partnerPanel;
    private EditText linkInput;          // picks: cart links
    private EditText partnerCartInput;   // partner: cart links
    private EditText partnerLinksInput;  // partner: partner product links

    private String hookJs = "";

    // Products read from the cart links, by goods id.
    private final Map<String, JSONObject> products = new LinkedHashMap<>();

    // Reading several cart links one after another.
    private Goal goal = Goal.PICKS;
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

    // Adding matching partner products to the cart.
    private boolean partnerRunning = false;
    private final List<String> partnerLinks = new ArrayList<>();
    private int partnerIndex = -1;
    private int partnerToken = 0;
    private boolean linkHandled = false;
    private String addingId = null;
    private int addAttempt = 0;
    private final Set<String> partnerSeenIds = new HashSet<>();
    private final List<String> addedLines = new ArrayList<>();
    private final List<String> failedLines = new ArrayList<>();
    private final Set<String> addedIds = new HashSet<>();
    private int linksNotInCart = 0;
    private int linksNotOpened = 0;

    // Reading a partner's list page.
    private volatile boolean scanningLink = false;
    private boolean listMode = false;
    private final Set<String> scanIds = new HashSet<>();
    private final List<String> listQueue = new ArrayList<>();
    private int partnerListCount = 0;

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
        panel.setPadding(pad, dp(6), pad, dp(6));
        panel.setBackgroundColor(PANEL);

        // Tabs
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabPicks = button("نقل إلى اختياراتي", v -> showTab(Goal.PICKS), false);
        tabPartner = button("سلة الشركاء", v -> showTab(Goal.PARTNER_CART), false);
        tabs.addView(tabPicks, weighted());
        tabs.addView(tabPartner, weighted());
        panel.addView(tabs);

        // Picks panel
        picksPanel = new LinearLayout(this);
        picksPanel.setOrientation(LinearLayout.VERTICAL);
        linkInput = input("الصق رابط سلة أو أكثر، كل رابط في سطر", 2, 4);
        picksPanel.addView(inputRow(linkInput));
        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.addView(button("نقل إلى اختياراتي", v -> startFromLinks(Goal.PICKS), true));
        row2.addView(button("المنتجات", v -> showProducts(), false));
        row2.addView(button("برنامج الشركاء", v -> web.loadUrl(AFFILIATE_URL), false));
        row2.addView(button("حسابي", v -> web.loadUrl(HOME_URL), false));
        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.addView(row2);
        picksPanel.addView(scroller);
        panel.addView(picksPanel);

        // Partner-cart panel
        partnerPanel = new LinearLayout(this);
        partnerPanel.setOrientation(LinearLayout.VERTICAL);
        partnerPanel.addView(label("رابط السلة (فيه المقاسات والكميات):"));
        partnerCartInput = input("الصق رابط السلة هنا", 1, 3);
        partnerPanel.addView(inputRow(partnerCartInput));
        partnerPanel.addView(label("رابط قائمة الشريك، أو روابط منتجاته، كل رابط في سطر:"));
        partnerLinksInput = input("الصق رابط الشريك هنا", 2, 5);
        partnerPanel.addView(inputRow(partnerLinksInput));
        LinearLayout prow = new LinearLayout(this);
        prow.setOrientation(LinearLayout.HORIZONTAL);
        prow.addView(button("نقل المتطابق إلى سلتي", v -> startFromLinks(Goal.PARTNER_CART), true), weighted());
        prow.addView(button("سلتي", v -> web.loadUrl(CART_URL), false));
        partnerPanel.addView(prow);
        panel.addView(partnerPanel);

        status = new TextView(this);
        status.setTextSize(13);
        status.setTextColor(INK);
        status.setPadding(0, dp(4), 0, 0);
        panel.addView(status);

        root.addView(panel);

        web = new WebView(this);
        root.addView(web, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        showTab(Goal.PICKS);
        return root;
    }

    private void showTab(Goal g) {
        boolean picks = g == Goal.PICKS;
        picksPanel.setVisibility(picks ? View.VISIBLE : View.GONE);
        partnerPanel.setVisibility(picks ? View.GONE : View.VISIBLE);
        styleTab(tabPicks, picks);
        styleTab(tabPartner, !picks);
        setStatus("الإصدار " + VERSION + " · " + (picks
                ? "الصق رابط سلة أو أكثر، ثم اضغط «نقل إلى اختياراتي»."
                : "الصق رابط السلة ورابط الشريك، ثم اضغط «نقل المتطابق إلى سلتي»."), INK);
    }

    private void styleTab(Button b, boolean active) {
        b.setBackgroundColor(active ? INK : Color.parseColor("#D3DDE0"));
        b.setTextColor(active ? Color.WHITE : INK);
    }

    private EditText input(String hint, int minLines, int maxLines) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_VARIATION_URI);
        e.setMinLines(minLines);
        e.setMaxLines(maxLines);
        e.setTextSize(13);
        return e;
    }

    private View inputRow(EditText e) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(e, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        side.addView(button("لصق", v -> pasteInto(e), false));
        side.addView(button("مسح", v -> e.setText(""), false));
        row.addView(side);
        return row;
    }

    private TextView label(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(12);
        t.setTextColor(INK);
        t.setPadding(0, dp(4), 0, 0);
        return t;
    }

    private Button button(String text, View.OnClickListener l, boolean primary) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setOnClickListener(l);
        if (primary) {
            b.setBackgroundColor(BLUE);
            b.setTextColor(Color.WHITE);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(dp(4));
        b.setLayoutParams(lp);
        return b;
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.setMarginStart(dp(4));
        return lp;
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
                String host = req.getUrl().getHost() == null ? "" : req.getUrl().getHost();
                if (host.endsWith("play.google.com") || host.endsWith("apps.apple.com")) {
                    // Partner pages try to send the user to the SHEIN app; stay on the website instead.
                    return true;
                }
                // Keep everything inside the app: block jumps to the SHEIN app.
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
                if (partnerRunning && !linkHandled && url.contains("/ark/")) {
                    // A partner's list page: read the products it holds.
                    linkHandled = true;
                    listMode = true;
                    partnerToken++; // cancel the product-page timeout
                    startListScan();
                }
                if (partnerRunning && !linkHandled) {
                    // Product pages also carry the id in their address: "...-p-123456.html".
                    Matcher m = Pattern.compile("-p-(\\d+)").matcher(url);
                    if (m.find()) { final String id = m.group(1); ui.postDelayed(() -> onProductSeen(id), 2500); }
                }
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

        @JavascriptInterface
        public void productSeen(String id) {
            if (!isShein(currentHost)) return;
            ui.post(() -> onProductSeen(id));
        }

        @JavascriptInterface
        public boolean isScanning() { return scanningLink && isShein(currentHost); }

        @JavascriptInterface
        public void scanIds(String json) {
            if (!scanningLink || !isShein(currentHost)) return;
            ui.post(() -> addScanIds(json));
        }

        @JavascriptInterface
        public void cartAddResult(String json) {
            if (!isShein(currentHost)) return;
            ui.post(() -> onCartAddResult(json));
        }
    }

    // ---------- reading carts ----------

    private void startFromLinks(Goal g) {
        String text = (g == Goal.PICKS ? linkInput : partnerCartInput).getText().toString();
        List<String> urls = extractUrls(text);
        if (urls.isEmpty()) {
            setStatus("لم أجد رابط سلة. الصق رسالة المشاركة أو الرابط الذي يبدأ بـ https.", RED);
            return;
        }
        if (g == Goal.PARTNER_CART) {
            partnerLinks.clear();
            partnerLinks.addAll(extractUrls(partnerLinksInput.getText().toString()));
            partnerLinks.removeAll(urls);
            if (partnerLinks.isEmpty()) {
                setStatus("الصق روابط منتجات الشركاء في الخانة الثانية.", RED);
                return;
            }
        }
        goal = g;
        stopAll();
        products.clear();
        cartQueue.clear();
        cartQueue.addAll(urls);
        cartReport.clear();
        cartIndex = -1;
        readingCarts = true;
        nextCart();
    }

    private void stopAll() {
        readingCarts = false;
        picking = false;
        rewriteArmed = false;
        partnerRunning = false;
        scanningLink = false;
        cartToken++;
        partnerToken++;
    }

    private void nextCart() {
        cartIndex++;
        if (cartIndex >= cartQueue.size()) {
            readingCarts = false;
            if (products.isEmpty()) {
                setStatus("لم أجد منتجات في رابط السلة. تأكد أنه رابط سلة مشتركة.", RED);
                showReport("لم أجد منتجات", String.join("\n", cartReport));
                return;
            }
            if (goal == Goal.PICKS) startPick(new ArrayList<>(products.values()));
            else startPartnerLinks();
            return;
        }
        final int token = ++cartToken;
        setStatus("أقرأ السلة " + (cartIndex + 1) + " من " + cartQueue.size() + "…", INK);
        web.loadUrl(cartQueue.get(cartIndex));
        ui.postDelayed(() -> {
            if (readingCarts && token == cartToken) {
                cartReport.add("✗ رابط السلة " + (cartIndex + 1) + ": لم يُقرأ (انتهت المهلة)");
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
            cartReport.add("✓ رابط السلة " + (cartIndex + 1) + ": " + arr.length() + " منتجات"
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
            JSONObject resp = parse(r.optString("resp"));
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
        setStatus("أُضيف " + ok + " من " + pickTargets.length() + " إلى اختياراتي.", failed.isEmpty() ? BLUE : RED);
        AlertDialog.Builder d = dialog("أُضيف " + ok + " من " + pickTargets.length(), sb.toString().trim());
        if (!failed.isEmpty()) d.setPositiveButton("إعادة محاولة الفاشلة", (x, w) -> startPick(failed));
        d.show();
    }

    // ---------- partner products → my cart ----------

    private void startPartnerLinks() {
        partnerRunning = true;
        partnerIndex = -1;
        partnerSeenIds.clear();
        addedLines.clear();
        failedLines.clear();
        addedIds.clear();
        linksNotInCart = 0;
        linksNotOpened = 0;
        partnerListCount = 0;
        nextPartnerLink();
    }

    private void nextPartnerLink() {
        if (!partnerRunning) return;
        partnerIndex++;
        if (partnerIndex >= partnerLinks.size()) { finishPartner(); return; }
        linkHandled = false;
        listMode = false;
        listQueue.clear();
        scanIds.clear();
        scanningLink = true;
        addingId = null;
        final int token = ++partnerToken;
        setStatus("أفتح رابط الشريك " + (partnerIndex + 1) + " من " + partnerLinks.size()
                + " · أُضيف حتى الآن " + addedIds.size(), INK);
        web.loadUrl(partnerLinks.get(partnerIndex));
        ui.postDelayed(() -> {
            if (partnerRunning && token == partnerToken && !linkHandled) {
                linkHandled = true;
                linksNotOpened++;
                failedLines.add("✗ رابط الشريك " + (partnerIndex + 1) + ": لم يفتح صفحة منتج");
                nextPartnerLink();
            }
        }, LINK_TIMEOUT_MS);
    }

    private void onProductSeen(String id) {
        if (!partnerRunning || linkHandled || listMode) return;
        linkHandled = true;
        partnerToken++; // cancel this link's timeout
        partnerSeenIds.add(id);
        JSONObject item = products.get(id);
        if (item == null || addedIds.contains(id)) {
            if (item == null) linksNotInCart++;
            ui.postDelayed(this::nextPartnerLink, 600);
            return;
        }
        addingId = id;
        addAttempt = 1;
        // Give the product page a moment to finish its own requests before adding.
        ui.postDelayed(this::sendAddToCart, 2000);
    }

    private void sendAddToCart() {
        if (!partnerRunning || addingId == null) return;
        JSONObject p = products.get(addingId);
        if (p == null) return;
        try {
            JSONObject item = new JSONObject();
            item.put("id", p.optString("id"));
            item.put("sku", p.optString("sku"));
            item.put("qty", p.optString("qty", "1"));
            item.put("mall", p.optString("mall", "1"));
            boolean withSdk = addAttempt >= 3;
            String js = "(function(){if(!window.__picksAddCart)return 'nohook';window.__picksAddCart(" + item + "," + withSdk + ");return 'sent';})()";
            web.evaluateJavascript(js, v -> {
                if (v != null && v.contains("nohook"))
                    onCartAddResult("{\"id\":\"" + addingId + "\",\"ok\":false,\"reason\":\"no-headers\"}");
            });
        } catch (Exception e) {
            onCartAddResult("{\"ok\":false,\"reason\":\"error\"}");
        }
    }

    private void onCartAddResult(String json) {
        if (!partnerRunning || addingId == null) return;
        JSONObject r = parse(json);
        if (r == null) return;
        if (!r.optString("id").isEmpty() && !r.optString("id").equals(addingId)) return;
        JSONObject p = products.get(addingId);
        String name = p == null ? addingId : shorten(p.optString("name"), 50);
        String qty = p == null ? "1" : p.optString("qty", "1");
        String attr = p == null ? "" : p.optString("attr");
        JSONObject resp = parse(r.optString("resp"));
        boolean ok = r.optBoolean("ok") && resp != null && "0".equals(resp.optString("code"));
        if (ok) {
            addedIds.add(addingId);
            addedLines.add("✓ " + name + (attr.isEmpty() ? "" : " · " + attr) + " · الكمية " + qty);
            addingId = null;
            ui.postDelayed(this::afterAdd, 1200);
            return;
        }
        // Attempt 1-2: let the page add its own security headers; attempt 3: copy them too.
        if (addAttempt < 3) {
            addAttempt++;
            ui.postDelayed(this::sendAddToCart, "no-headers".equals(r.optString("reason")) ? 2500 : 1200);
            return;
        }
        String msg = resp != null && !resp.optString("msg").isEmpty() ? resp.optString("msg")
                : ("no-headers".equals(r.optString("reason")) ? "لم تكتمل صفحة المنتج" : "رمز " + r.optInt("status"));
        failedLines.add("✗ " + name + " · " + msg);
        addingId = null;
        ui.postDelayed(this::afterAdd, 800);
    }

    private void afterAdd() {
        if (listMode && !listQueue.isEmpty()) addNextFromList();
        else nextPartnerLink();
    }

    private void addScanIds(String json) {
        try {
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length(); i++) scanIds.add(a.optString(i));
        } catch (Exception ignored) { }
    }

    // Scrolls the partner page so it loads more products, then reads every product id on it.
    private void startListScan() {
        setStatus("أقرأ منتجات صفحة الشريك… (ينزل التطبيق في الصفحة تلقائياً)", INK);
        final int token = partnerToken;
        for (int i = 1; i <= 8; i++) {
            ui.postDelayed(() -> {
                if (partnerRunning && token == partnerToken)
                    web.evaluateJavascript("window.scrollBy(0, window.innerHeight*0.9);", null);
            }, 1500L * i);
        }
        ui.postDelayed(() -> { if (partnerRunning && token == partnerToken) readListPage(); }, 1500L * 9 + 1000);
    }

    private void readListPage() {
        String js = "(function(){var ids={};"
                + "document.querySelectorAll('a[href]').forEach(function(a){var m=a.href.match(/-p-(\\d{5,})/);if(m)ids[m[1]]=1;});"
                + "document.querySelectorAll('[data-id],[data-goods-id],[goods-id],[data-goodsid]').forEach(function(el){"
                + "['data-id','data-goods-id','goods-id','data-goodsid'].forEach(function(k){var v=el.getAttribute(k);if(v&&/^\\d{5,}$/.test(v))ids[v]=1;});});"
                + "var re=/(?:goods_id|goodsId)[\\\\\"']*\\s*[:=]\\s*[\\\\\"']*(\\d{5,})/g,h=document.documentElement.innerHTML,m,n=0;"
                + "while((m=re.exec(h))&&n<2000){ids[m[1]]=1;n++;}"
                + "window.scrollTo(0,0);return JSON.stringify(Object.keys(ids));})()";
        web.evaluateJavascript(js, value -> {
            try {
                String list = new JSONArray("[" + value + "]").getString(0);
                addScanIds(list);
            } catch (Exception ignored) { }
            scanningLink = false;
            onListRead();
        });
    }

    private void onListRead() {
        if (!partnerRunning) return;
        partnerListCount += scanIds.size();
        partnerSeenIds.addAll(scanIds);
        listQueue.clear();
        for (String id : products.keySet()) {
            if (scanIds.contains(id) && !addedIds.contains(id)) listQueue.add(id);
        }
        setStatus("وجدت " + scanIds.size() + " منتجاً في صفحة الشريك، منها " + listQueue.size() + " في السلة. أضيفها…", INK);
        if (listQueue.isEmpty()) ui.postDelayed(this::nextPartnerLink, 500);
        else addNextFromList();
    }

    private void addNextFromList() {
        if (!partnerRunning || listQueue.isEmpty()) { nextPartnerLink(); return; }
        addingId = listQueue.remove(0);
        addAttempt = 1;
        sendAddToCart();
    }

    private void finishPartner() {
        partnerRunning = false;
        scanningLink = false;
        List<String> missing = new ArrayList<>();
        for (JSONObject p : products.values()) {
            if (!partnerSeenIds.contains(p.optString("id")))
                missing.add("• " + shorten(p.optString("name"), 50) + (p.optString("attr").isEmpty() ? "" : " · " + p.optString("attr")));
        }
        StringBuilder sb = new StringBuilder();
        sb.append("أُضيف إلى سلتك (").append(addedLines.size()).append("):\n");
        sb.append(addedLines.isEmpty() ? "لا شيء" : String.join("\n", addedLines)).append("\n\n");
        if (!failedLines.isEmpty()) sb.append("لم يُضف (").append(failedLines.size()).append("):\n").append(String.join("\n", failedLines)).append("\n\n");
        sb.append("في السلة وليس في روابط الشركاء (").append(missing.size()).append("):\n");
        sb.append(missing.isEmpty() ? "لا شيء" : String.join("\n", missing));
        if (linksNotInCart > 0) sb.append("\n\nروابط شركاء لمنتجات ليست في السلة: ").append(linksNotInCart);
        if (partnerListCount > 0) sb.append("\n\nعدد المنتجات التي وُجدت في صفحات الشركاء: ").append(partnerListCount)
                .append("\nقارنه بعدد منتجات الشريك في تطبيق شي ان لتعرف إن كانت القائمة كاملة.");
        if (!cartReport.isEmpty()) sb.append("\n\n").append(String.join("\n", cartReport));
        setStatus("أُضيف " + addedLines.size() + " منتجات إلى سلتك · " + missing.size() + " ليست في روابط الشركاء.",
                failedLines.isEmpty() ? BLUE : RED);
        dialog("نتيجة سلة الشركاء", sb.toString())
                .setPositiveButton("فتح سلتي", (x, w) -> web.loadUrl(CART_URL))
                .show();
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

    private static JSONObject parse(String s) {
        try { return new JSONObject(s); } catch (Exception e) { return null; }
    }

    private boolean handleShareIntent(Intent intent) {
        if (intent != null && Intent.ACTION_SEND.equals(intent.getAction())) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (text != null && !extractUrls(text).isEmpty()) {
                showTab(Goal.PICKS);
                linkInput.setText(text);
                startFromLinks(Goal.PICKS);
                return true;
            }
        }
        return false;
    }

    private void pasteInto(EditText e) {
        ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cb != null && cb.hasPrimaryClip() && cb.getPrimaryClip().getItemCount() > 0) {
            CharSequence t = cb.getPrimaryClip().getItemAt(0).coerceToText(this);
            String cur = e.getText().toString().trim();
            e.setText(cur.isEmpty() ? t : cur + "\n" + t);
            e.setSelection(e.getText().length());
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
