package com.wmods.wppenhacer.xposed.bridge.client

import com.wmods.wppenhacer.xposed.bridge.WaeIIFace

abstract class BaseClient {
    abstract val service: WaeIIFace?

    abstract suspend fun connect(): Boolean

    abstract fun tryReconnect()
}
