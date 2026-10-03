package com.wmods.wppenhacer.xposed.features.customization

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.wmods.wppenhacer.utils.ColorReplacement.replaceColors
import com.wmods.wppenhacer.utils.DrawableColors.replaceColor
import com.wmods.wppenhacer.utils.IColors
import com.wmods.wppenhacer.views.WallpaperView
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.utils.DesignUtils
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import java.util.Collections
import java.util.Properties
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

class CustomThemeV2(loader: ClassLoader, preferences: SharedPreferences) :
    Feature(loader, preferences) {

    companion object {
        private const val FIELD_WALLPAPER_TOOLBAR = "wae_wallpaper_toolbar"

        @JvmStatic
        private fun processColors(color: String, mapColors: HashMap<String, String>) {
            val inputColorFull: String = when (color.length) {
                7 -> {
                    "#ff" + color.substring(1)
                }

                9 -> {
                    "#ff" + color.substring(3)
                }

                else -> {
                    return
                }
            }

            val inputR: Int
            val inputG: Int
            val inputB: Int
            try {
                inputR = inputColorFull.substring(3, 5).toInt(16)
                inputG = inputColorFull.substring(5, 7).toInt(16)
                inputB = inputColorFull.substring(7, 9).toInt(16)
            } catch (_: NumberFormatException) {
                return
            }

            for (c in mapColors.keys) {
                val value = mapColors[c]

                if (c.length == 9) {
                    var finalColorStr = inputColorFull

                    if (value != null && value.length == 9 && !value.startsWith("#ff")) {
                        try {
                            val existingAlphaInt = value.substring(1, 3).toInt(16)
                            val alphaFactor = existingAlphaInt / 255.0f

                            var newR = (inputR * alphaFactor + 255 * (1 - alphaFactor)).toInt()
                            var newG = (inputG * alphaFactor + 255 * (1 - alphaFactor)).toInt()
                            var newB = (inputB * alphaFactor + 255 * (1 - alphaFactor)).toInt()

                            newR = newR.coerceIn(0, 255)
                            newG = newG.coerceIn(0, 255)
                            newB = newB.coerceIn(0, 255)

                            finalColorStr = "#ff%02x%02x%02x".format(newR, newG, newB)

                        } catch (_: NumberFormatException) {
                            finalColorStr = inputColorFull
                        }
                    }
                    mapColors[c] = finalColorStr

                } else if (c.length == 7) {
                    mapColors[c] = inputColorFull.substring(3)
                }
            }
        }
    }

    private var wallAlpha: HashMap<String, String>? = null
    private var navAlpha: HashMap<String, String>? = null
    private var toolbarAlpha: HashMap<String, String>? = null
    private var properties: Properties? = null
    private val resolvedColors = ConcurrentHashMap<Int, Int>()
    private val processedResources = Collections.synchronizedMap(WeakHashMap<Any, Boolean>())

    @Volatile
    private var colorsReady = false

    @Throws(Throwable::class)
    override fun doHook() {
        properties = Utils.getProperties(xprefs, "custom_css", "custom_filters")
        hookTheme()
        hookWallpaper()
        ReflectionUtils.findClass("android.app.ActivityThread", classLoader).resolve().method {
            name = "handleRelaunchActivity"
        }.hookAll {
            before {
                loadAndApplyColors()
                loadAndApplyColorsWallpaper()
            }
        }
    }

    private fun loadAndApplyColorsWallpaper() {
        val customWallpaper = xprefs.getBoolean("wallpaper", false)

        if (customWallpaper || properties?.containsKey("wallpaper") == true) {
            wallAlpha = HashMap(IColors.colors)
            val wallpaperAlpha = if (customWallpaper) xprefs.getInt("wallpaper_alpha", 30)
            else Utils.tryParseInt(properties?.getProperty("wallpaper_alpha"), 30)
            wallAlpha?.let { replaceTransparency(it, (100 - wallpaperAlpha) / 100.0f) }

            navAlpha = HashMap(IColors.colors)
            val wallpaperAlphaNav =
                if (customWallpaper) xprefs.getInt("wallpaper_alpha_navigation", 30)
                else Utils.tryParseInt(properties?.getProperty("wallpaper_alpha_navigation"), 30)
            navAlpha?.let { replaceTransparency(it, (100 - wallpaperAlphaNav) / 100.0f) }

            toolbarAlpha = HashMap(IColors.colors)
            val wallpaperToolbarAlpha =
                if (customWallpaper) xprefs.getInt("wallpaper_alpha_toolbar", 30)
                else Utils.tryParseInt(properties?.getProperty("wallpaper_alpha_toolbar"), 30)
            toolbarAlpha?.let { replaceTransparency(it, (100 - wallpaperToolbarAlpha) / 100.0f) }
        }
    }

    @Throws(Exception::class)
    private fun hookWallpaper() {
        if (!xprefs.getBoolean("wallpaper", false)) return

        loadAndApplyColorsWallpaper()
        val homeActivityClass = WppCore.homeActivityClass
        val actionModeBarId = Utils.getID("action_mode_bar", "id")

        homeActivityClass.resolve().firstMethod {
            name = "onCreate"
            superclass()
            parameters(Bundle::class.java)
        }.hook {
            after {
                val activity = instance as Activity
                if (ContextCompat.checkSelfPermission(
                        activity,
                        Manifest.permission.READ_MEDIA_IMAGES
                    ) == PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(
                        activity,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    injectWallpaper(activity.findViewById(Utils.getID("root_view", "id")))
                }
            }
        }

        View::class.java.resolve().firstMethod {
            name = "onAttachedToWindow"
            superclass()
            emptyParameters()
        }.hook {
            after {
                val view = instance as View
                if (actionModeBarId > 0 && view.id == actionModeBarId)
                    view.background = DesignUtils.getPrimarySurfaceColor().toDrawable()
            }
        }

        View::class.java.resolve().firstMethod {
            name = "setBackgroundColor"
            superclass()
            parameters(Int::class)
        }.hook {
            before {
                val colors = toolbarAlpha ?: return@before
                if (ReflectionUtils.getAdditionalInstanceField(
                        instance,
                        FIELD_WALLPAPER_TOOLBAR
                    ) != true
                ) return@before

                val color = colors[IColors.toString(args[0] as Int)]
                if (color != null) {
                    args[0] = IColors.parseColor(color)
                }
            }
        }

        val hookFragmentView = Unobfuscator.loadFragmentViewMethod(classLoader)

        hookFragmentView.hook {
            after {
                if (!colorsReady || checkNotHomeActivity()) return@after
                val viewGroup = result as ViewGroup
                val colors = wallAlpha ?: return@after
                replaceColors(viewGroup, colors)
            }
        }

        val loadTabFrameClass = Unobfuscator.loadTabFrameClass(classLoader)
        FrameLayout::class.java.resolve().firstMethod {
            name = "onMeasure"
            superclass()
            parameters(Int::class, Int::class)
        }.hook {
            after {
                if (!loadTabFrameClass.isInstance(instance)) return@after
                if (!colorsReady || checkNotHomeActivity()) return@after
                val viewGroup = instance as ViewGroup
                val background = viewGroup.background
                val colors = navAlpha ?: return@after
                replaceColor(background, colors)
            }
        }
    }

    @Throws(Throwable::class)
    fun hookTheme() {
        loadAndApplyColors()

        AssetManager::class.java.resolve().method {
            name = "getResourceValue"
        }.hookAll {
            after {
                if (!colorsReady) return@after
                val typedValue = args[2] as TypedValue
                if (typedValue.type >= TypedValue.TYPE_FIRST_INT
                    && typedValue.type <= TypedValue.TYPE_LAST_INT
                ) {
                    if (typedValue.data == 0) return@after
                    val originalColor = typedValue.data
                    val mappedColor = IColors.getFromIntColor(originalColor, IColors.colors)
                    if (mappedColor == originalColor || checkNotApplyColor(originalColor)) return@after
                    typedValue.data = mappedColor
                }
            }
        }

        val resourceImpl =
            ReflectionUtils.findClass("android.content.res.ResourcesImpl", classLoader)

        resourceImpl.resolve().method {
            name = "loadDrawable"
        }.hookAll {
            after {
                if (!colorsReady) return@after
                val drawable = result as? Drawable ?: return@after
                if (processedResources.put(drawable, true) != null) return@after
                replaceColor(drawable, IColors.colors)
            }
        }

        resourceImpl.resolve().method {
            name = "loadColorStateList"
        }.hookAll {
            after {
                if (!colorsReady) return@after
                val colorStateList = result as? ColorStateList ?: return@after
                if (processedResources.put(colorStateList, true) != null) return@after
                val mColors =
                    ReflectionUtils.getObjectField(colorStateList, "mColors") as IntArray
                for (i in mColors.indices) {
                    mColors[i] = IColors.getFromIntColor(mColors[i], IColors.colors)
                }
            }
        }
        Paint::class.java.resolve().firstMethod {
            name = "setColor"
            superclass()
            parameters(Int::class)
        }.hook {
            before {
                if (!colorsReady) return@before
                val currentActivity = WppCore.getCurrentActivity()
                if (currentActivity == null || currentActivity.javaClass.simpleName == "Conversation") return@before

                val color = args[0] as Int
                val mappedColor = resolvedColors[color]
                    ?: IColors.getFromIntColor(color, IColors.colors).also {
                        resolvedColors[color] = it
                    }
                args[0] = mappedColor
            }
        }
    }

    fun loadAndApplyColors() {
        colorsReady = false
        resolvedColors.clear()
        processedResources.clear()
        IColors.initColors()

        var primaryColorInt = xprefs.getInt("primary_color", 0)
        var textColorInt = xprefs.getInt("text_color", 0)
        var backgroundColorInt = xprefs.getInt("background_color", 0)
        val changeColorEnabled = xprefs.getBoolean("changecolor", false)
        val changeColorMode = xprefs.getString("changecolor_mode", "manual")
        val useMonetColors = changeColorEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && changeColorMode == "monet"

        if (useMonetColors) {
            val primaryMonetColor = resolveMonetColor(
                if (DesignUtils.isNightMode()) "system_accent1_300" else "system_accent1_600"
            )
            val textMonetColor = resolveMonetColor(
                if (DesignUtils.isNightMode()) "system_neutral1_100" else "system_neutral1_900"
            )
            val backgroundMonetColor = resolveMonetColor(
                if (DesignUtils.isNightMode()) "system_neutral1_900" else "system_neutral1_10"
            )

            if (primaryMonetColor != 0) primaryColorInt = primaryMonetColor
            if (textMonetColor != 0) textColorInt = textMonetColor
            if (backgroundMonetColor != 0) backgroundColorInt = backgroundMonetColor
        }

        var primaryColor =
            DesignUtils.checkSystemColor(properties?.getProperty("primary_color", "0"))
        var textColor =
            DesignUtils.checkSystemColor(properties?.getProperty("text_color", "0"))
        var backgroundColor =
            DesignUtils.checkSystemColor(properties?.getProperty("background_color", "0"))

        if (changeColorEnabled) {
            primaryColor = if (primaryColorInt == 0) "0" else IColors.toString(primaryColorInt)
            textColor = if (textColorInt == 0) "0" else IColors.toString(textColorInt)
            backgroundColor =
                if (backgroundColorInt == 0) "0" else IColors.toString(backgroundColorInt)
        }

        if (!DesignUtils.isNightMode()) {
            IColors.textColors.clear()
            IColors.textColors.putAll(IColors.backgroundColors)
            IColors.backgroundColors.clear()
        }

        if (changeColorEnabled || properties?.getProperty("change_colors") == "true") {
            if (primaryColor != "0" && DesignUtils.isValidColor(primaryColor)) {
                processColors(primaryColor, IColors.primaryColors)
                processColors(primaryColor, IColors.alphacolors)
            }

            if (textColor != "0" && DesignUtils.isValidColor(textColor)) {
                processColors(textColor, IColors.textColors)
            }

            if (backgroundColor != "0" && DesignUtils.isValidColor(backgroundColor)) {
                processColors(backgroundColor, IColors.backgroundColors)
            }

            val entries = IColors.alphacolors.entries
            val newAlphaColors = HashMap<String, String>()
            for (entry in entries) {
                val color = IColors.primaryColors[entry.key]
                if (color == null) {
                    newAlphaColors[entry.key] = entry.value
                    continue
                }
                val realColor = entry.value
                newAlphaColors[color] = realColor
            }
            IColors.alphacolors = newAlphaColors
        }

        IColors.colors.putAll(IColors.primaryColors)
        IColors.colors.putAll(IColors.textColors)
        IColors.colors.putAll(IColors.backgroundColors)
        IColors.primaryColors.clear()
        IColors.textColors.clear()

        if (!DesignUtils.isNightMode()) {
            IColors.backgroundColors.clear()
            IColors.backgroundColors["#ff1b8755"] = "#ffffffff"
            IColors.backgroundColors["#ffffffff"] = "#ffffffff"
            IColors.backgroundColors["ffffff"] = "ffffff"
        }
        colorsReady = true
    }

    @SuppressLint("DiscouragedApi")
    private fun resolveMonetColor(resourceName: String): Int {
        var colorRes = Resources.getSystem().getIdentifier(resourceName, "color", "android")
        if (colorRes == 0) {
            try {
                colorRes = android.R.color::class.java.getField(resourceName).getInt(null)
            } catch (_: Throwable) {
                return 0
            }
        }
        if (colorRes == 0) return 0
        return try {
            ContextCompat.getColor(Utils.application, colorRes)
        } catch (_: Throwable) {
            0
        }
    }

    private fun replaceTransparency(wallpaperColors: HashMap<String, String>?, mAlpha: Float) {
        if (wallpaperColors == null) return
        val clampedAlpha = mAlpha.coerceIn(0f, 1f)
        val alphaInt = (clampedAlpha * 255).roundToInt()
        var hexAlpha = Integer.toHexString(alphaInt)
        if (hexAlpha.length == 1) hexAlpha = "0$hexAlpha"
        val keysToIterate = HashSet(IColors.backgroundColors.keys)

        for (c in keysToIterate) {
            val oldColor = wallpaperColors.getOrDefault(c, IColors.backgroundColors[c])
            if (oldColor == null || oldColor.length < 9 || !oldColor.startsWith("#")) continue
            val newColor = "#$hexAlpha${oldColor.substring(3)}"
            wallpaperColors[c] = newColor
            wallpaperColors[oldColor] = newColor
        }
    }

    private fun injectWallpaper(view: View?) {
        val content = view as ViewGroup
        val rootView = content.getChildAt(0) as ViewGroup

        val header = content.findViewById<ViewGroup>(Utils.getID("header", "id"))
        header.background = null
        header.backgroundTintList = null
        val toolbarContainer =
            content.findViewById<ViewGroup>(Utils.getID("toolbar_container", "id"))
        if (toolbarContainer != null) {
            ReflectionUtils.setAdditionalInstanceField(
                toolbarContainer,
                FIELD_WALLPAPER_TOOLBAR,
                true
            )
            toolbarContainer.background = null
            toolbarContainer.backgroundTintList = null
        }
        val toolbar = content.findViewById<View>(Utils.getID("toolbar", "id"))
        val firstChild = header.getChildAt(0)
        if (firstChild != null && toolbar != firstChild) {
            firstChild.background = null
            firstChild.backgroundTintList = null
        }
        ReflectionUtils.setAdditionalInstanceField(toolbar, FIELD_WALLPAPER_TOOLBAR, true)
        toolbar.background = null
        toolbar.backgroundTintList = null
        replaceColors(toolbar, toolbarAlpha)
        val frameLayout = WallpaperView(rootView.context, xprefs, properties!!)
        rootView.addView(frameLayout, 0)
    }

    private fun checkNotHomeActivity(): Boolean {
        val homeClass = WppCore.homeActivityClass
        val currentActivity = WppCore.getCurrentActivity()
        return (currentActivity == null || !homeClass.isInstance(currentActivity))
    }

    private fun checkNotApplyColor(color: Int): Boolean {
        val activity = WppCore.getCurrentActivity()
        if (activity != null && activity.javaClass.simpleName == "Conversation"
            && ReflectionUtils.isCalledFromStrings("getValue")
            && !ReflectionUtils.isCalledFromStrings("android.view")
        ) {
            return color != 0xff12181c.toInt()
        }
        return false
    }

    override fun getPluginName(): String {
        return "Custom Theme V2"
    }
}
