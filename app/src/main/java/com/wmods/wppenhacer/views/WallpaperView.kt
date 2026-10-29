package com.wmods.wppenhacer.views

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import com.wmods.wppenhacer.preference.ThemePreference
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.utils.YukiLog
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties
import java.util.concurrent.Executors

@SuppressLint("ViewConstructor")
class WallpaperView(
    context: Context,
    private val prefs: SharedPreferences,
    private val properties: Properties
) : FrameLayout(context) {

    init {
        initView(context)
    }

    private fun initView(context: Context) {
        val bgView = ImageView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            scaleType = ImageView.ScaleType.CENTER_CROP
            setAdjustViewBounds(false)
        }
        addView(bgView)

        val shown = loaded?.bitmap
        if (shown != null) {
            bgView.setImageDrawable(BitmapDrawable(resources, shown))
        }

        val appContext = context.applicationContext
        loader.execute {
            val bitmap = load(appContext, prefs, properties)?.bitmap ?: return@execute
            if (bitmap === shown) return@execute
            bgView.post { bgView.setImageDrawable(BitmapDrawable(resources, bitmap)) }
        }
    }

    private class LoadedWallpaper(val key: String, val bitmap: Bitmap)

    companion object {
        private val loader = Executors.newSingleThreadExecutor { r ->
            Thread(r, "WAE-Wallpaper").apply { isDaemon = true }
        }

        @Volatile
        private var loaded: LoadedWallpaper? = null

        @JvmStatic
        fun prefetch(context: Context, prefs: SharedPreferences, properties: Properties) {
            val appContext = context.applicationContext
            loader.execute { load(appContext, prefs, properties) }
        }

        private fun load(
            context: Context,
            prefs: SharedPreferences,
            properties: Properties
        ): LoadedWallpaper? {
            return try {
                val file = File(resolveImagePath(prefs, properties))
                if (!file.exists()) return null

                val cacheKey = "${file.absolutePath}_${file.lastModified()}"
                loaded?.takeIf { it.key == cacheKey }?.let { return it }

                val bitmap = decodeBitmap(context, file, cacheKey) ?: return null
                LoadedWallpaper(cacheKey, bitmap).also { loaded = it }
            } catch (e: Exception) {
                YukiLog.log("Error loading wallpaper drawable: " + e.message)
                null
            }
        }

        private fun resolveImagePath(prefs: SharedPreferences, properties: Properties): String {
            if (prefs.getBoolean("wallpaper", false)) {
                return prefs.getString("wallpaper_file", "") ?: ""
            }
            return ThemePreference.rootDirectory.absolutePath + "/" + prefs.getString(
                "folder_theme",
                ""
            ) + "/" + properties.getProperty("wallpaper_file")
        }

        private fun decodeBitmap(context: Context, file: File, cacheKey: String): Bitmap? {
            val fileOut = context.filesDir.absolutePath + "/wallpaper.jpg"

            val cachedData = WppCore.getPrivString("wallpaper_data", "")

            if (cacheKey == cachedData && File(fileOut).exists()) {
                return BitmapFactory.decodeFile(fileOut)
            }

            val filePath = file.absolutePath
            val bitmap = if (!file.canRead()) {
                val parcelFile = WppCore.getClientBridge()?.openFile(filePath, false) ?: return null
                BitmapFactory.decodeStream(FileInputStream(parcelFile.fileDescriptor))
            } else {
                BitmapFactory.decodeFile(filePath)
            } ?: return null

            val displayMetrics = DisplayMetrics()
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            @Suppress("DEPRECATION")
            windowManager?.defaultDisplay?.getMetrics(displayMetrics)
            val width =
                if (displayMetrics.widthPixels > 0) displayMetrics.widthPixels else bitmap.width
            val height =
                if (displayMetrics.heightPixels > 0) displayMetrics.heightPixels else bitmap.height

            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)

            try {
                FileOutputStream(fileOut).use { outputStream ->
                    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                    outputStream.flush()
                }
            } catch (ignored: Exception) {
            }

            WppCore.setPrivString("wallpaper_data", cacheKey)
            if (scaledBitmap != bitmap) {
                bitmap.recycle()
            }

            return scaledBitmap
        }
    }
}
