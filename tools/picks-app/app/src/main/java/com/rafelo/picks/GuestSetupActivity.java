package com.rafelo.picks;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.webkit.ProfileStore;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

/**
 * Opens SHEIN in the same signed-out "guest" profile that reads customers' carts,
 * so the owner can pick the store country (Kuwait) and currency (USD) once.
 */
public class GuestSetupActivity extends Activity {

    static final String GUEST_PROFILE = "guest";
    private static final String HOME = "https://m.shein.com/ar/";
    private static final String ME = "https://m.shein.com/ar/user/index";
    private WebView web;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            Toast.makeText(this, "هذا الهاتف لا يدعم ملف الزائر المنفصل. حدّث Android System WebView من المتجر.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        int pad = Math.round(8 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setPadding(pad, pad, pad, pad);
        bar.setBackgroundColor(Color.parseColor("#E7F3FA"));
        TextView t = new TextView(this);
        t.setText("ضبط متجر القراءة: هنا تتصفح شي ان كزائر. اختر البلد «الكويت» من أعلى الصفحة، والعملة «USD» من صفحة حسابي ← الإعدادات، ثم اضغط «تم».");
        t.setTextColor(Color.parseColor("#16232A"));
        t.setTextSize(13);
        bar.addView(t);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(btn("الرئيسية", v -> web.loadUrl(HOME)), weight());
        row.addView(btn("حسابي والإعدادات", v -> web.loadUrl(ME)), weight());
        Button done = btn("تم", v -> finish());
        done.setBackgroundColor(Color.parseColor("#2F7FB8"));
        done.setTextColor(Color.WHITE);
        row.addView(done, weight());
        bar.addView(row);
        root.addView(bar);

        web = new WebView(this);
        ProfileStore.getInstance().getOrCreateProfile(GUEST_PROFILE);
        WebViewCompat.setProfile(web, GUEST_PROFILE);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setUserAgentString(s.getUserAgentString().replace("; wv", "").replace("Version/4.0 ", ""));
        ProfileStore.getInstance().getProfile(GUEST_PROFILE).getCookieManager().setAcceptThirdPartyCookies(web, true);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                String scheme = req.getUrl().getScheme();
                String host = req.getUrl().getHost() == null ? "" : req.getUrl().getHost();
                if (host.endsWith("play.google.com") || host.endsWith("apps.apple.com")) return true;
                return !("http".equals(scheme) || "https".equals(scheme));
            }
        });
        root.addView(web, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
        web.loadUrl(HOME);
    }

    private Button btn(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setOnClickListener(l);
        return b;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.setMarginStart(Math.round(4 * getResources().getDisplayMetrics().density));
        return lp;
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
