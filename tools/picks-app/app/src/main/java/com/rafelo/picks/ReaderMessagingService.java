package com.rafelo.picks;

import android.content.Intent;
import android.os.Build;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONObject;

import java.util.Map;

/** Receives the silent wake-up the website sends when a customer asks for a quote. */
public class ReaderMessagingService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(RemoteMessage msg) {
        Map<String, String> d = msg.getData();
        Intent i = new Intent(this, ReaderService.class);
        if (d.containsKey("job")) i.putExtra(ReaderService.EXTRA_JOB, d.get("job"));
        else if (d.containsKey("ping")) i.putExtra(ReaderService.EXTRA_PING, true);
        else return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
        else startService(i);
    }

    @Override
    public void onNewToken(String token) {
        if (!Reader.paired(this)) return;
        new Thread(() -> {
            try {
                JSONObject b = new JSONObject();
                b.put("fcmToken", token);
                Reader.call(this, "POST", "/api/reader/register", b);
            } catch (Exception ignored) { }
        }).start();
    }
}
