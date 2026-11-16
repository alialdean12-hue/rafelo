package com.rafelo.picks;

import android.app.Application;

public class ZahraApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Reader.initFirebase(this);
    }
}
