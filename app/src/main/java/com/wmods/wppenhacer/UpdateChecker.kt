package com.wmods.wppenhacer

import android.app.Activity
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.components.AlertDialogWpp
import com.wmods.wppenhacer.xposed.utils.Utils
import de.robv.android.xposed.XposedBridge
import io.noties.markwon.Markwon
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class UpdateChecker(private val mActivity: Activity) : Runnable {

    companion object {
        private const val LATEST_RELEASE_API = "https://api.github.com/repos/Dev4Mod/WaEnhancer/releases/latest"
        private const val TELEGRAM_UPDATE_URL = "https://t.me/waenhancher"

        private val httpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build()
        }
    }

    override fun run() {
        try {
            val request = okhttp3.Request.Builder()
                .url(LATEST_RELEASE_API)
                .build()

            val hash: String
            val changelog: String
            val publishedAt: String

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return

                val content = response.body.string()
                val release = JSONObject(content)
                val tagName = release.optString("tag_name", "")

                if (tagName.isBlank()) return

                hash = tagName.split("-")[1].trim()
                changelog = release.optString("body", "No changelog available.").trim()
                publishedAt = release.optString("published_at", "")
            }

            if (hash.isBlank()) return

            val packageInfo = try {
                mActivity.packageManager.getPackageInfo(BuildConfig.APPLICATION_ID, 0)
            } catch (e: Exception) {
                XposedBridge.log(e)
                return
            }

            val isNewVersion = !packageInfo.versionName!!.lowercase().contains(hash.lowercase().trim())
            val isIgnored = WppCore.getPrivString("ignored_version", "") == hash

            if (isNewVersion && !isIgnored) {
                mActivity.runOnUiThread {
                    showUpdateDialog(hash, changelog, publishedAt)
                }
            }
        } catch (e: Exception) {
            XposedBridge.log(e)
        }
    }

    private fun showUpdateDialog(hash: String, changelog: String, publishedAt: String) {
        try {
            val markwon = Markwon.create(mActivity)
            val dialog = AlertDialogWpp(mActivity)

            val formattedDate = formatPublishedDate(publishedAt)

            val message = buildString {
                append("📦 **Version:** `").append(hash).append("`\n")
                if (formattedDate.isNotEmpty()) {
                    append("📅 **Released:** ").append(formattedDate).append("\n")
                }
                append("\n### What's New\n\n").append(changelog)
            }

            dialog.setTitle("🎉 New Update Available!")
            dialog.setMessage(markwon.toMarkdown(message))
            dialog.setNegativeButton("Ignore") { dialog, _ ->
                WppCore.setPrivString("ignored_version", hash)
                dialog.dismiss()
            }
            dialog.setPositiveButton("Update Now") { dialog, _ ->
                Utils.openLink(mActivity, TELEGRAM_UPDATE_URL)
                dialog.dismiss()
            }
            dialog.show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun formatPublishedDate(isoDate: String?): String {
        if (isoDate.isNullOrEmpty()) return ""

        return try {
            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            val date = isoFormat.parse(isoDate)
            if (date != null) {
                val displayFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
                displayFormat.format(date)
            } else ""
        } catch (e: Exception) {
            XposedBridge.log(e)
            ""
        }
    }
}
