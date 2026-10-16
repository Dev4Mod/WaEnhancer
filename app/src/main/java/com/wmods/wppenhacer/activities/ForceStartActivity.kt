package com.wmods.wppenhacer.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class ForceStartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val packageName = intent.getStringExtra("pkg")
        // Actividad exportada: solo se puede lanzar WhatsApp / WhatsApp Business.
        if (packageName != null && packageName in ALLOWED_PACKAGES) {
            packageManager.getLaunchIntentForPackage(packageName)?.let { launchIntent ->
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
            }
        }
        finish()
    }

    private companion object {
        val ALLOWED_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")
    }
}
