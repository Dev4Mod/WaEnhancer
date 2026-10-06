package com.wmods.wppenhacer.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.wmods.wppenhacer.xposed.core.FeatureLoader

class ForceStartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        when (val targetPackage = intent.getStringExtra("pkg")) {
            FeatureLoader.PACKAGE_WPP,
            FeatureLoader.PACKAGE_BUSINESS -> {
                packageManager.getLaunchIntentForPackage(targetPackage)?.let { launchIntent ->
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(launchIntent)
                }
            }
        }
        finish()
    }
}
