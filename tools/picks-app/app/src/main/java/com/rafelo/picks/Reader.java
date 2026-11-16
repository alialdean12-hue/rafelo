package com.rafelo.picks;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Settings, history and network calls for the website reader. */
final class Reader {
    private static final String PREFS = "reader";
    private static final int MAX_LOG = 200;

    private Reader() { }

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    static String site(Context c) { return prefs(c).getString("site", ""); }
    static String token(Context c) { return prefs(c).getString("token", ""); }
    static boolean paired(Context c) { return !site(c).isEmpty() && !token(c).isEmpty(); }

    /** Starts Firebase from the options the website gave us (there is no google-services.json in the app). */
    static boolean initFirebase(Context c) {
        if (!FirebaseApp.getApps(c).isEmpty()) return true;
        try {
            JSONObject f = new JSONObject(prefs(c).getString("firebase", "{}"));
            if (f.optString("appId").isEmpty()) return false;
            FirebaseOptions o = new FirebaseOptions.Builder()
                    .setApiKey(f.getString("apiKey"))
                    .setApplicationId(f.getString("appId"))
                    .setProjectId(f.getString("projectId"))
                    .setGcmSenderId(f.optString("senderId"))
                    .build();
            FirebaseApp.initializeApp(c.getApplicationContext(), o);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------- history ----------

    static synchronized void addLog(Context c, JSONObject entry) {
        try {
            JSONArray old = new JSONArray(prefs(c).getString("log", "[]"));
            JSONArray out = new JSONArray();
            out.put(entry);
            for (int i = 0; i < old.length() && out.length() < MAX_LOG; i++) out.put(old.get(i));
            prefs(c).edit().putString("log", out.toString()).apply();
        } catch (Exception ignored) { }
    }

    static JSONArray log(Context c) {
        try { return new JSONArray(prefs(c).getString("log", "[]")); } catch (Exception e) { return new JSONArray(); }
    }

    // ---------- network ----------

    static JSONObject call(String site, String token, String method, String path, JSONObject body) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(site + path).openConnection();
        h.setConnectTimeout(15000);
        h.setReadTimeout(20000);
        h.setRequestMethod(method);
        h.setRequestProperty("authorization", "Bearer " + token);
        h.setRequestProperty("accept", "application/json");
        if (body != null) {
            h.setDoOutput(true);
            h.setRequestProperty("content-type", "application/json; charset=utf-8");
            try (OutputStream os = h.getOutputStream()) { os.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
        }
        int code = h.getResponseCode();
        InputStream in = code >= 400 ? h.getErrorStream() : h.getInputStream();
        String text = "";
        if (in != null) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            text = out.toString("UTF-8");
        }
        h.disconnect();
        JSONObject j;
        try { j = new JSONObject(text); } catch (Exception e) { j = new JSONObject(); }
        j.put("_status", code);
        return j;
    }

    static JSONObject call(Context c, String method, String path, JSONObject body) throws Exception {
        return call(site(c), token(c), method, path, body);
    }
}
