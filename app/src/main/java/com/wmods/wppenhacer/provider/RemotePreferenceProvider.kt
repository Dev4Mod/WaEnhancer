package com.wmods.wppenhacer.provider

import com.crossbowffs.remotepreferences.RemotePreferenceProvider
import com.wmods.wppenhacer.BuildConfig
import com.wmods.wppenhacer.xposed.bridge.service.CallerGuard

class RemotePreferenceProvider : RemotePreferenceProvider(
    BuildConfig.APPLICATION_ID + ".preferences",
    arrayOf(BuildConfig.APPLICATION_ID + "_preferences")
) {
    // Solo lectura, y solo para el módulo y WhatsApp/WhatsApp Business.
    override fun checkAccess(prefFileName: String, prefKey: String, write: Boolean): Boolean {
        return !write && CallerGuard.isCallerAllowed(context)
    }
}
