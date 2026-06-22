package com.wmods.wppenhacer.xposed.bridge.client

import com.wmods.wppenhacer.xposed.bridge.WaeIIFace
import java.util.concurrent.CompletableFuture

abstract class BaseClient {
    abstract val service: WaeIIFace?

    abstract fun connect(): CompletableFuture<Boolean>

    abstract fun tryReconnect()
}
