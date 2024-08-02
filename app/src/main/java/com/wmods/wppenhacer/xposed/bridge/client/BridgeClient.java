package com.wmods.wppenhacer.xposed.bridge.client;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import com.wmods.wppenhacer.BuildConfig;
import com.wmods.wppenhacer.activities.ForceStartActivity;
import com.wmods.wppenhacer.xposed.bridge.WaeIIFace;
import com.wmods.wppenhacer.xposed.bridge.service.BridgeService;
import com.wmods.wppenhacer.xposed.utils.Utils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class BridgeClient extends BaseClient implements ServiceConnection {
    private final Context context;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final Semaphore connectSemaphore = new Semaphore(1);
    private final Semaphore reconnectSemaphore = new Semaphore(1);
    public WaeIIFace service;
    private CompletableFuture<Boolean> continuation;

    public BridgeClient(Context context) {
        this.context = context;
    }

    private void resumeContinuation(boolean state) {
        connectSemaphore.acquireUninterruptibly();
        try {
            if (continuation != null) {
                continuation.complete(state);
                continuation = null;
            }
        } finally {
            connectSemaphore.release();
        }
    }

    public CompletableFuture<Boolean> connect() {
        if (service != null && service.asBinder().pingBinder()) {
            return CompletableFuture.completedFuture(true);
        }

        continuation = new CompletableFuture<>();
        CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
            try {
                Intent intent = new Intent();
                intent.setClassName(BuildConfig.APPLICATION_ID, ForceStartActivity.class.getName())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
                context.startActivity(intent);

                intent.setClassName(BuildConfig.APPLICATION_ID, BridgeService.class.getName());
                if (service != null) {
                    context.unbindService(this);
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.bindService(intent, Context.BIND_AUTO_CREATE, Executors.newSingleThreadExecutor(), this);
                } else {
                    HandlerThread handlerThread = new HandlerThread("BridgeClient");
                    handlerThread.start();
                    Handler handler = new Handler(handlerThread.getLooper());
                    XposedHelpers.callMethod(context, "bindServiceAsUser", intent, this, Context.BIND_AUTO_CREATE,
                            handler, android.os.Process.myUserHandle());
                }
            } catch (Exception e) {
                XposedBridge.log(e);
                resumeContinuation(false);
            }
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
    public void onServiceConnected(ComponentName name, IBinder service) {
        this.service = WaeIIFace.Stub.asInterface(service);
        resumeContinuation(true);
    }

    @Override
    public void onNullBinding(ComponentName name) {
        resumeContinuation(false);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        continuation = null;
    }

    public void tryReconnect() {
        reconnectSemaphore.acquireUninterruptibly();
        try {
            if (service.asBinder().pingBinder()) return;
            connect().thenAccept(canLoad -> {
                if (!Boolean.TRUE.equals(canLoad)) {
                    Log.e("BridgeClient", "failed to reconnect to service, result=" + canLoad);
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

    @Override
    public WaeIIFace getService() {
        return service;
    }
}