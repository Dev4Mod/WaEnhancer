package com.wmods.wppenhacer.xposed.bridge.client

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Process
import android.widget.Toast
import com.wmods.wppenhacer.BuildConfig
import com.wmods.wppenhacer.activities.ForceStartActivity
import com.wmods.wppenhacer.xposed.bridge.WaeIIFace
import com.wmods.wppenhacer.xposed.bridge.service.BridgeService
import com.wmods.wppenhacer.xposed.utils.Utils
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.resume

class BridgeClientKt(private val context: Context) : BaseClient(), ServiceConnection {

    private var service: WaeIIFace? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val connectionMutex = Mutex()

    private var connectionContinuation: CancellableContinuation<Boolean>? = null

    override fun getService(): WaeIIFace? = service

    override fun connect(): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()
        scope.launch {
            val result = performConnection()
            future.complete(result)
        }
        return future
    }

    private suspend fun performConnection(): Boolean = withContext(Dispatchers.IO) {
        if (service?.asBinder()?.pingBinder() == true) {
            return@withContext true
        }

        connectionMutex.withLock {
            runCatching {
                val intent = Intent().apply {
                    component = ComponentName(
                        BuildConfig.APPLICATION_ID,
                        ForceStartActivity::class.java.name
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                }
                context.startActivity(intent)
            }.onFailure { XposedBridge.log("Failed to start ForceStartActivity: ${it.message}") }

            val connected = withTimeoutOrNull(3000L) {
                suspendCancellableCoroutine<Boolean> { continuation ->
                    connectionContinuation = continuation

                    try {
                        if (service != null) {
                            runCatching { context.unbindService(this@BridgeClientKt) }
                        }

                        val intent = Intent().apply {
                            setClassName(BuildConfig.APPLICATION_ID, BridgeService::class.java.name)
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            context.bindService(
                                intent,
                                Context.BIND_AUTO_CREATE,
                                { it.run() },
                                this@BridgeClientKt
                            )
                        } else {
                            val handlerThread = HandlerThread("BridgeClient").apply { start() }
                            val handler = Handler(handlerThread.getLooper())

                            XposedHelpers.callMethod(
                                context, "bindServiceAsUser", intent, this@BridgeClientKt,
                                Context.BIND_AUTO_CREATE, handler, Process.myUserHandle()
                            )
                        }
                    } catch (e: Exception) {
                        XposedBridge.log("Bind failed: ${e.message}")
                        continuation.resume(false)
                    }
                }
            } ?: false

            return@withContext connected
        }
    }

    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
        service = WaeIIFace.Stub.asInterface(binder)
        XposedBridge.log("Service Connected: $service")
        connectionContinuation?.let {
            if (it.isActive) it.resume(true)
        }
    }

    override fun onNullBinding(name: ComponentName?) {
        XposedBridge.log("Service Binding returned null")
        connectionContinuation?.let {
            if (it.isActive) it.resume(false)
        }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        XposedBridge.log("Service Disconnected")
        service = null
    }

    override fun tryReconnect() {
        scope.launch {
            if (service?.asBinder()?.pingBinder() == true) return@launch

            var success = false
            repeat(3) { attempt ->
                XposedBridge.log("Attempting reconnect... ($attempt)")
                if (performConnection()) {
                    success = true
                    return@repeat
                }
                delay(1500)
            }

            withContext(Dispatchers.Main) {
                val msg = if (success) "Reconnected to Bridge" else "Failed to reconnect to Bridge"
                Utils.showToast(msg, Toast.LENGTH_SHORT)
            }
        }
    }

    fun onDestroy() {
        scope.cancel()
        runCatching { context.unbindService(this) }
    }
}