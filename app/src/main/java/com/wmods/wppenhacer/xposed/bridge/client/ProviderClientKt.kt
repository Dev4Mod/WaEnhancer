package com.wmods.wppenhacer.xposed.bridge.client

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import com.wmods.wppenhacer.BuildConfig
import com.wmods.wppenhacer.activities.ForceStartActivity
import com.wmods.wppenhacer.xposed.bridge.WaeIIFace
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.utils.Utils
import com.wmods.wppenhacer.xposed.utils.WaeCoroutineExceptionHandler
import de.robv.android.xposed.XposedBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.milliseconds

class ProviderClientKt : BaseClient() {

    override var service: WaeIIFace? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob() + WaeCoroutineExceptionHandler)
    private val reconnectMutex = Mutex()


    override suspend fun connect(): Boolean {
        return performConnection()
    }

    private suspend fun performConnection(): Boolean = withContext(Dispatchers.IO) {
        if (service?.asBinder()?.pingBinder() == true) {
            return@withContext true
        }
        runCatching {
            val intent = Intent().apply {
                component =
                    ComponentName(BuildConfig.APPLICATION_ID, ForceStartActivity::class.java.name)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            WppCore.getCurrentActivity()?.startActivity(intent)
        }

        try {
            withTimeout(3000L.milliseconds) {
                val resolver = Utils.application.contentResolver
                val bundle =
                    resolver.call(Settings.System.CONTENT_URI, "WaEnhancer", "getHookBinder", null)
                val binder = bundle?.getBinder("binder")
                if (binder != null) {
                    val potentialService = WaeIIFace.Stub.asInterface(binder)
                    if (potentialService?.asBinder()?.pingBinder() == true) {
                        service = potentialService
                        XposedBridge.log("Bridge Connected: $service")
                        return@withTimeout true
                    }
                }
                false
            }
        } catch (e: TimeoutCancellationException) {
            XposedBridge.log("Connection timed out: ${e.message}")
            false
        } catch (e: Exception) {
            XposedBridge.log("Connection error: ${e.message}")
            false
        }
    }


    override fun tryReconnect() {
        scope.launch {
            reconnectMutex.withLock {
                if (service?.asBinder()?.pingBinder() == true) return@launch

                var success = false
                repeat(3) { attempt ->
                    XposedBridge.log("Attempt ${attempt + 1} to reconnect...")
                    if (performConnection()) {
                        success = true
                        return@repeat
                    }
                    delay(1000.milliseconds)
                }
                Utils.showToast(
                    if (success) "Reconnected to Bridge" else "Failed to reconnect to Bridge..",
                    Toast.LENGTH_SHORT
                )
            }
        }
    }
}
