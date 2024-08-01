package com.wmods.wppenhacer.xposed.bridge.client;

import com.wmods.wppenhacer.xposed.bridge.WaeIIFace;

import java.util.concurrent.CompletableFuture;

public abstract class BaseClient {

    public abstract WaeIIFace getService();

    public abstract CompletableFuture<Boolean> connect();

    public abstract void tryReconnect();

}
