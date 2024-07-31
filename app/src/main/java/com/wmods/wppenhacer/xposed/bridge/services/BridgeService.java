package com.wmods.wppenhacer.xposed.bridge.services;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;

public class BridgeService extends Service {

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new HookBinder();
    }

}
