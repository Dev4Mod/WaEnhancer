package com.wmods.wppenhacer.xposed.bridge.client;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import com.wmods.wppenhacer.BuildConfig;
import com.wmods.wppenhacer.activities.ForceStartActivity;
import com.wmods.wppenhacer.xposed.bridge.WaeIIFace;
import com.wmods.wppenhacer.xposed.core.WppCore;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import de.robv.android.xposed.XposedBridge;

public class ProviderClient extends BaseClient {

    private final Context context;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final Semaphore reconnectSemaphore = new Semaphore(1);
    private WaeIIFace service;
    private CompletableFuture<Boolean> continuation;

    public ProviderClient(Context context) {
        this.context = context;
    }

    @Override
    public WaeIIFace getService() {
        return service;
    }

    @Override
    public CompletableFuture<Boolean> connect() {
        if (service != null && service.asBinder().pingBinder()) {
            return CompletableFuture.completedFuture(true);
        }

        continuation = new CompletableFuture<>();

        CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
            boolean connected = false;
            try {
                var intent = new Intent();
                intent.setComponent(new ComponentName(BuildConfig.APPLICATION_ID, ForceStartActivity.class.getName()));
                intent.putExtra("pkg", WppCore.getCurrentActivity().getPackageName());
                WppCore.getCurrentActivity().startActivity(intent);
            } catch (Exception ignored) {
            }

            try {
                var resolver = Utils.getApplication().getContentResolver();
                var bundle = resolver.call(Settings.System.CONTENT_URI, "WaEnhancer", "getHookBinder", null);
                var binder = bundle != null ? bundle.getBinder("binder") : null;
                if (binder != null) {
                    service = WaeIIFace.Stub.asInterface(binder);
                    if (service != null && service.asBinder().pingBinder()) {
                        XposedBridge.log("" + service);

                        connected = true;
                    }
                }
            } catch (Exception e) {
                XposedBridge.log("Error in Bridge: " + e.getMessage());
            }

            continuation.complete(connected);
            return continuation.join();
        });

        scheduler.schedule(() -> {
            if (!continuation.isDone()) {
                continuation.completeExceptionally(new Exception("Connection timed out"));
            }
        }, 3, TimeUnit.SECONDS);

        return future.exceptionally(ex -> false);
    }

    @Override
    public void tryReconnect() {
        reconnectSemaphore.acquireUninterruptibly();
        try {
            if (service != null && service.asBinder().pingBinder()) return;
            connect().thenAccept(canLoad -> {
                if (!Boolean.TRUE.equals(canLoad)) {
                    Log.e("ProviderClient", "failed to reconnect to service, result=" + canLoad);
                    Utils.doRestart(context);
                } else {
                    Utils.showToast("Reconnected to Bridge", Toast.LENGTH_SHORT);
                }
            }).exceptionally((e) -> {
                Utils.doRestart(context);
                return null;
            });
        } finally {
            reconnectSemaphore.release();
        }
    }
}