package com.wmods.wppenhacer.xposed.features.customization

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RectShape
import android.text.TextUtils
import android.util.DisplayMetrics
import android.util.Log
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.scale
import com.wmods.wppenhacer.preference.ThemePreference
import com.wmods.wppenhacer.utils.ColorReplacement.replaceColors
import com.wmods.wppenhacer.utils.IColors
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import cz.vutbr.web.css.CSSFactory
import cz.vutbr.web.css.RuleSet
import cz.vutbr.web.css.StyleSheet
import cz.vutbr.web.css.Term
import cz.vutbr.web.css.TermColor
import cz.vutbr.web.css.TermFloatValue
import cz.vutbr.web.css.TermFunction
import cz.vutbr.web.css.TermLength
import cz.vutbr.web.css.TermURI
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Objects
import java.util.Properties
import java.util.WeakHashMap
import kotlin.math.cos
import kotlin.math.sin

class CustomView(loader: ClassLoader, preferences:SharedPreferences) : Feature(loader, preferences) {

    private var cacheImages: DrawableCache? = null
    private val chacheDrawables = HashMap<String, Drawable>()
    private var properties: Properties? = null
    private val processedViews = WeakHashMap<View, Boolean>()
    private val forcedVisibilityMap = WeakHashMap<View, Int>()
    private val forcedBackgroundMap = WeakHashMap<View, Drawable>()
    private val forcedDrawableMap = WeakHashMap<View, Drawable>()
    private var mapIds: HashMap<Int, ArrayList<CachedRuleItem>>? = null
    private var leafMapIds: HashMap<Int, ArrayList<CachedRuleItem>>? = null
    private val resolvedClasses = HashMap<String, Class<*>>()
    private val widgetClassCache = HashMap<String, Boolean>()

    override fun doHook() {
        if (prefs.getBoolean("lite_mode", false)) return

        val filterItens = prefs.getString("css_theme", "") ?: ""
        val folderTheme = prefs.getString("folder_theme", "") ?: ""
        val customCss = prefs.getString("custom_css", "") ?: ""

        if ((TextUtils.isEmpty(filterItens) && TextUtils.isEmpty(folderTheme) && TextUtils.isEmpty(customCss))
            || !prefs.getBoolean("custom_filters", true)
        ) return

        properties = Utils.getProperties(prefs, "custom_css", "custom_filters")

        WppCore.addListenerActivity { activity1, type ->
            if (type != WppCore.ActivityChangeState.ChangeType.CREATED) return@addListenerActivity
            changeDPI(activity1, prefs, properties!!)
        }

        hookDrawableViews()

        themeDir = File(ThemePreference.rootDirectory, folderTheme)
        val cssContent = "$filterItens\n$customCss"
        cacheImages = DrawableCache(Utils.application, 100 * 1024 * 1024)

        var waVersion = ""
        try {
            waVersion = Utils.application.packageManager?.getPackageInfo(
                Utils.application.packageName ?: "", 0
            )?.versionName ?: ""
        } catch (_: Exception) {
        }

        val hash = hashContent(cssContent + waVersion)
        val cacheDir = Utils.application.cacheDir
        val cacheFile = File(cacheDir, "cv_rules_${hash}.cache")

        var loaded = false
        if (cacheFile.exists()) {
            try {
                ObjectInputStream(FileInputStream(cacheFile)).use { ois ->
                    @Suppress("UNCHECKED_CAST")
                    mapIds = ois.readObject() as HashMap<Int, ArrayList<CachedRuleItem>>
                    @Suppress("UNCHECKED_CAST")
                    leafMapIds = ois.readObject() as HashMap<Int, ArrayList<CachedRuleItem>>
                    loaded = true
                    log("CustomView: loaded compiled rules from disk cache")
                }
            } catch (e: Exception) {
                log("CustomView: cache load failed – ${e.message}")
                mapIds = null
                leafMapIds = null
            }
        }

        if (!loaded) {
            val sheet = CSSFactory.parseString(cssContent, URL("https://base.url/"))
            mapIds = HashMap()
            leafMapIds = HashMap()
            buildRuleMaps(sheet)

            val old = cacheDir.listFiles { _, n -> n.startsWith("cv_rules_") && n.endsWith(".cache") }
            if (old != null) {
                for (f in old) {
                    if (f != cacheFile) f.delete()
                }
            }

            val mCopy = mapIds
            val lCopy = leafMapIds
            Thread({
                try {
                    ObjectOutputStream(FileOutputStream(cacheFile)).use { oos ->
                        oos.writeObject(mCopy)
                        oos.writeObject(lCopy)
                    }
                } catch (e: Exception) {
                    log("CustomView: cache save failed – ${e.message}")
                }
            }, "cv-cache-writer").start()
        }

        registerHooks()
    }

    private fun hashContent(content: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val h = md.digest(content.toByteArray(StandardCharsets.UTF_8))
            h.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            content.hashCode().toString()
        }
    }

    private fun buildRuleMaps(sheet: StyleSheet) {
        for (selector in sheet) {
            val ruleSet = selector as RuleSet
            for (selectorItem in ruleSet.selectors) {
                var targetClassName: String? = null
                val parts = ArrayList<SelectorPart>()

                for ((i, element) in selectorItem.withIndex()) {
                    val item = element
                    val part = SelectorPart()
                    if (item.className != null) {
                        part.className = item.className.replace("_", ".").trim()
                        if (i == 0) targetClassName = part.className
                    } else if (item.idName != null) {
                        part.idName = item.idName.trim()
                        if (part.idName!!.contains("android_")) {
                            try {
                                part.resolvedId = android.R.id::class.java
                                    .getField(part.idName!!.substring(8)).getInt(null)
                            } catch (_: NoSuchFieldException) {
                            } catch (_: IllegalAccessException) {
                            }
                        } else {
                            part.resolvedId = Utils.getID(part.idName!!, "id")
                        }
                    } else {
                        val typeStr = item.toString()
                        val split = typeStr.split(":", limit = 2)
                        part.typeSelector = split[0].trim()
                        if (split.size > 1) part.pseudoClass = split[1].trim()
                    }
                    parts.add(part)
                }

                val name: String? = if (parts[0].className != null && parts.size > 1) {
                    parts[1].idName
                } else {
                    parts[0].idName
                }
                if (name == null) continue

                var id = 0
                for (p in parts) {
                    if (name == p.idName) {
                        id = p.resolvedId
                        break
                    }
                }
                if (id <= 0) continue

                val decls = ArrayList<SerialDeclaration>()
                for (declaration in ruleSet) {
                    val sd = SerialDeclaration()
                    sd.property = declaration.property
                    sd.terms = ArrayList()
                    for (i in declaration.indices) {
                        sd.terms.add(toSerialTerm(declaration[i]))
                    }
                    decls.add(sd)
                }

                val ruleItem = CachedRuleItem(parts, decls, targetClassName)
                val list = mapIds!!.getOrDefault(id, ArrayList())
                list.add(ruleItem)
                mapIds!![id] = list

                var leafName: String? = null
                for (i in selectorItem.size - 1 downTo 0) {
                    val leafItem = selectorItem[i]
                    if (leafItem.idName != null) {
                        leafName = leafItem.idName.trim()
                        break
                    }
                }
                if (leafName != null && leafName != name) {
                    var leafId = 0
                    for (p in parts) {
                        if (leafName == p.idName) {
                            leafId = p.resolvedId
                            break
                        }
                    }
                    if (leafId > 0) {
                        val leafList = leafMapIds!!.getOrDefault(leafId, ArrayList())
                        leafList.add(ruleItem)
                        leafMapIds!![leafId] = leafList
                    }
                }
            }
        }
    }

    private fun registerHooks() {
        XposedHelpers.findAndHookMethod(
            View::class.java, "onAttachedToWindow",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    applyRulesForView(param.thisObject as View)
                }
            })

        XposedHelpers.findAndHookMethod(
            View::class.java, "onDetachedFromWindow",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    processedViews.remove(param.thisObject as View)
                }
            })

        XposedHelpers.findAndHookMethod(
            View::class.java, "setFlags", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (param.args[1] as Int and 0x0000000C == 0) return
                    val view = param.thisObject as View
                    val forced = forcedVisibilityMap[view] ?: return
                    param.args[0] = (param.args[0] as Int and 0x0000000C.inv()) or forced
                }
            })
    }

    private fun applyRulesForView(view: View) {
        val id = view.id
        applyRules(view, mapIds?.get(id))

        val leafList = leafMapIds?.get(id) ?: return
        for (item in leafList) {
            try {
                val activityClass = resolveClass(item.targetActivityClassName)
                if (activityClass != null && !activityClass.isInstance(WppCore.getCurrentActivity()))
                    continue
                val rootView = findSelectorRoot(view, item.selector) ?: continue
                val resultViews = ArrayList<View>()
                captureSelector(rootView, item.selector, 0, resultViews)
                for (targetView in resultViews) {
                    if (processedViews.containsKey(targetView)) continue
                    setRuleInView(item, targetView)
                    processedViews[targetView] = true
                }
            } catch (_: Throwable) {
            }
        }
    }

    private fun applyRules(startView: View, rules: ArrayList<CachedRuleItem>?) {
        if (rules == null) return
        for (item in rules) {
            try {
                val activityClass = resolveClass(item.targetActivityClassName)
                if (activityClass != null && !activityClass.isInstance(WppCore.getCurrentActivity()))
                    continue
                val resultViews = ArrayList<View>()
                captureSelector(startView, item.selector, 0, resultViews)
                for (targetView in resultViews) {
                    if (processedViews.containsKey(targetView)) continue
                    setRuleInView(item, targetView)
                    processedViews[targetView] = true
                }
            } catch (_: Throwable) {
            }
        }
    }

    private fun findSelectorRoot(leafView: View, selector: ArrayList<SelectorPart>): View? {
        for (part in selector) {
            if (part.idName == null) continue
            val rootId = part.resolvedId
            if (rootId <= 0) continue
            val root = leafView.rootView
            if (root.id == rootId) return root
            return root.findViewById(rootId)
        }
        return null
    }

    private fun hookDrawableViews() {
        XposedHelpers.findAndHookMethod(
            View::class.java, "setBackground", Drawable::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val view = param.thisObject as View
                    val newDrawable = param.args[0] as? Drawable
                    val forced = forcedBackgroundMap[view] ?: return
                    if (newDrawable !== forced) param.result = null
                }
            })

        XposedHelpers.findAndHookMethod(
            ImageView::class.java, "setImageDrawable", Drawable::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val view = param.thisObject as ImageView
                    val newDrawable = param.args[0] as? Drawable
                    val forced = forcedDrawableMap[view] ?: return
                    if (newDrawable !== forced) param.result = null
                }
            })
    }

    private fun setRuleInView(ruleItem: CachedRuleItem, startView: View) {
        var view = startView
        for (declaration in ruleItem.declarations) {
            val property = declaration.property
            val terms = declaration.terms
            when (property) {
                "parent" -> {
                    val value = terms[0].strValue.trim()
                    val parent = if (value != "root") view.rootView.findViewById(Utils.getID(value, "id"))
                    else view.rootView
                    if (parent is ViewGroup) {
                        val oldParent = view.parent as View
                        if (oldParent.tag != "relative") {
                            (view.parent as ViewGroup).removeView(view)
                            val frameLayout = FrameLayout(parent.context)
                            frameLayout.tag = "relative"
                            val params = FrameLayout.LayoutParams(view.layoutParams)
                            parent.addView(frameLayout, 0, params)
                            frameLayout.addView(view)
                        } else {
                            view = oldParent
                        }
                    }
                }
                "background-color" -> {
                    if (terms.size != 2) continue
                    val color = terms[0].colorRgb
                    val colorNew = terms[1].colorRgb
                    val colors = HashMap<String, String>()
                    colors[IColors.toString(color)] = IColors.toString(colorNew)
                    replaceColors(view, colors)
                    if (view is ImageView) {
                        val drawable = view.drawable ?: continue
                        drawable.setTint(colorNew)
                        view.postInvalidate()
                    }
                }
                "display" -> {
                    when (terms[0].strValue) {
                        "none" -> {
                            forcedVisibilityMap[view] = View.GONE
                            view.visibility = View.GONE
                        }
                        "block" -> {
                            forcedVisibilityMap[view] = View.VISIBLE
                            view.visibility = View.VISIBLE
                        }
                        "invisible" -> {
                            forcedVisibilityMap[view] = View.INVISIBLE
                            view.visibility = View.INVISIBLE
                        }
                    }
                }
                "font-size" -> {
                    if (view !is TextView) continue
                    view.textSize = getRealValue(terms[0], 0).toFloat()
                }
                "color" -> {
                    if (view !is TextView) continue
                    view.setTextColor(terms[0].colorRgb)
                }
                "alpha", "opacity" -> view.alpha = terms[0].numValue
                "background-image" -> {
                    if (terms[0].type != SerialTerm.URI) continue
                    val draw = cacheImages?.getDrawable(terms[0].strValue, view.width, view.height) ?: continue
                    if (forcedBackgroundMap.containsKey(view) || forcedDrawableMap.containsKey(view))
                        continue
                    setHookedDrawable(view, draw)
                }
                "background-size" -> {
                    if (terms[0].type == SerialTerm.LENGTH) {
                        val widthTerm = terms[0]
                        val heightTerm = terms[1]
                        if (view is ImageView) {
                            if (widthTerm.percentage || heightTerm.percentage) {
                                if ((widthTerm.numValue.toInt()) == 100 || (heightTerm.numValue.toInt()) == 100) {
                                    view.scaleType = ImageView.ScaleType.FIT_CENTER
                                    continue
                                }
                            }
                            val drawable = view.drawable
                            if (drawable !is BitmapDrawable) continue
                            val bitmap = drawable.bitmap
                            val resized = Bitmap.createScaledBitmap(bitmap,
                                getRealValue(widthTerm, view.width),
                                getRealValue(heightTerm, view.height), false)
                            setHookedDrawable(view, resized.toDrawable(view.context.resources))
                        } else {
                            val drawable = view.background
                            if (drawable !is BitmapDrawable) continue
                            val bitmap = drawable.bitmap
                            val resized = Bitmap.createScaledBitmap(bitmap,
                                getRealValue(widthTerm, 0), getRealValue(heightTerm, 0), false)
                            setHookedDrawable(view, resized.toDrawable(view.context.resources))
                        }
                    } else {
                        val value = terms[0].strValue.trim()
                        if (value == "cover") {
                            if (view is ImageView) {
                                view.scaleType = ImageView.ScaleType.CENTER_CROP
                            } else {
                                if (view.width < 1 || view.height < 1) {
                                    view.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
                                        override fun onLayoutChange(v: View, left: Int, top: Int, right: Int, bottom: Int,
                                                                     oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
                                            val w = right - left
                                            val h = bottom - top
                                            if (w < 1 || h < 1) return
                                            v.removeOnLayoutChangeListener(this)
                                            val bg = v.background
                                            if (bg !is BitmapDrawable) return
                                            val bmp = bg.bitmap
                                            setHookedDrawable(v,
                                                Bitmap.createScaledBitmap(bmp, w, h, true)
                                                    .toDrawable(v.context.resources))
                                        }
                                    })
                                    continue
                                }
                                val drawable = view.background
                                if (drawable !is BitmapDrawable) continue
                                val bitmap = drawable.bitmap
                                setHookedDrawable(view,
                                    Bitmap.createScaledBitmap(bitmap, view.width, view.height, true)
                                        .toDrawable(view.context.resources))
                            }
                        }
                    }
                }
                "background" -> {
                    val t0 = terms[0]
                    if (t0.type == SerialTerm.COLOR) {
                        view.setBackgroundColor(t0.colorRgb)
                        continue
                    }
                    if (t0.type == SerialTerm.URI) {
                        val draw = cacheImages?.getDrawable(t0.strValue, view.width, view.height) ?: continue
                        setHookedDrawable(view, draw)
                        continue
                    }
                    if (t0.type == SerialTerm.STRING && t0.strValue == "none") {
                        forcedBackgroundMap.remove(view)
                        view.background = null
                    } else {
                        setBackgroundModel(view, t0)
                    }
                }
                "foreground" -> {
                    val t0 = terms[0]
                    if (t0.type == SerialTerm.COLOR) {
                        view.foreground = t0.colorRgb.toDrawable()
                        continue
                    }
                    if (t0.type == SerialTerm.URI) {
                        val draw = cacheImages?.getDrawable(t0.strValue, view.width, view.height) ?: continue
                        view.foreground = draw
                        continue
                    }
                    if (t0.type == SerialTerm.STRING && t0.strValue == "none") {
                        view.foreground = null
                    }
                }
                "width" -> {
                    view.layoutParams.width = getRealValue(terms[0], 0)
                    view.requestLayout()
                }
                "height" -> {
                    view.layoutParams.height = getRealValue(terms[0], 0)
                }
                "left" -> {
                    when (val lp = view.layoutParams) {
                        is RelativeLayout.LayoutParams -> lp.addRule(RelativeLayout.ALIGN_LEFT, getRealValue(terms[0], 0))
                        is ViewGroup.MarginLayoutParams -> lp.leftMargin = getRealValue(terms[0], 0)
                    }
                }
                "right" -> {
                    when (val lp = view.layoutParams) {
                        is RelativeLayout.LayoutParams -> lp.addRule(RelativeLayout.ALIGN_RIGHT, getRealValue(terms[0], 0))
                        is ViewGroup.MarginLayoutParams -> lp.rightMargin = getRealValue(terms[0], 0)
                    }
                }
                "top" -> {
                    when (val lp = view.layoutParams) {
                        is RelativeLayout.LayoutParams -> lp.addRule(RelativeLayout.ALIGN_TOP, getRealValue(terms[0], 0))
                        is ViewGroup.MarginLayoutParams -> lp.topMargin = getRealValue(terms[0], 0)
                    }
                }
                "bottom" -> {
                    when (val lp = view.layoutParams) {
                        is RelativeLayout.LayoutParams -> lp.addRule(RelativeLayout.ALIGN_BOTTOM, getRealValue(terms[0], 0))
                        is ViewGroup.MarginLayoutParams -> lp.bottomMargin = getRealValue(terms[0], 0)
                    }
                }
                "color-filter" -> {
                    val mode = terms[0].strValue.trim()
                    if (mode == "none") {
                        if (view is ImageView) view.clearColorFilter()
                        else view.background?.clearColorFilter()
                    } else {
                        if (terms.size < 2 || terms[1].type != SerialTerm.COLOR) continue
                        try {
                            val pMode = PorterDuff.Mode.valueOf(mode)
                            @Suppress("DEPRECATION")
                            if (view is ImageView)
                                view.setColorFilter(terms[1].colorRgb, pMode)
                            else
                                view.background?.setColorFilter(terms[1].colorRgb, pMode)
                        } catch (_: IllegalArgumentException) {
                        }
                    }
                }
                "color-tint" -> {
                    if (terms[0].type == SerialTerm.COLOR) {
                        val csl = if (terms.size == 1) ColorStateList.valueOf(terms[0].colorRgb)
                        else getColorStateList(terms)
                        if (view is ImageView) {
                            view.imageTintList = csl
                        } else {
                            view.background?.setTintList(csl)
                        }
                    } else {
                        val value = terms[0].strValue.trim()
                        if (value == "none") {
                            if (view is ImageView) view.imageTintList = null
                            else view.background?.setTintList(null)
                        }
                    }
                }
                "font-weight" -> {
                    if (view !is TextView) continue
                    val value = terms[0].strValue
                    val cur = view.typeface
                    if (value == "bold" || value == "700" || value == "800" || value == "900")
                        view.setTypeface(cur, Typeface.BOLD)
                    else if (value == "normal" || value == "400")
                        view.setTypeface(Typeface.create(cur, Typeface.NORMAL))
                }
                "font-style" -> {
                    if (view !is TextView) continue
                    val value = terms[0].strValue
                    val cur = view.typeface
                    if (value == "italic") view.setTypeface(cur, Typeface.ITALIC)
                    else if (value == "normal") view.setTypeface(Typeface.create(cur, Typeface.NORMAL))
                }
                "text-decoration" -> {
                    if (view !is TextView) continue
                    val value = terms[0].strValue
                    if (value.contains("underline"))
                        view.paintFlags = view.paintFlags or Paint.UNDERLINE_TEXT_FLAG
                    if (value.contains("line-through"))
                        view.paintFlags = view.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                    if (value.contains("none"))
                        view.paintFlags = view.paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv() and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                }
                "text-transform" -> {
                    if (view !is TextView) continue
                    when (terms[0].strValue) {
                        "uppercase" -> view.isAllCaps = true
                        "lowercase" -> {
                            view.isAllCaps = false
                            view.text = view.text.toString().lowercase()
                        }
                        "none" -> view.isAllCaps = false
                    }
                }
                "text-align" -> {
                    if (view !is TextView) continue
                    when (terms[0].strValue) {
                        "center" -> view.gravity = Gravity.CENTER
                        "right", "end" -> view.gravity = Gravity.END or Gravity.CENTER_VERTICAL
                        "left", "start" -> view.gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    }
                }
                "box-shadow" -> {
                    for (term in terms) {
                        if (term.type == SerialTerm.LENGTH) {
                            val `val` = getExactValue(term, 0)
                            if (`val` > 0) view.elevation = `val`.toFloat()
                            break
                        }
                    }
                }
                "transform" -> {
                    for (term in terms) {
                        if (term.type != SerialTerm.FUNCTION) continue
                        val args = term.args
                        if (term.strValue == "rotate" && !args.isNullOrEmpty()) {
                            val arg = args[0]
                            if (arg.type == SerialTerm.LENGTH && arg.unitName == "deg") {
                                view.rotation = arg.numValue
                            }
                        } else if (term.strValue == "scale" && args != null) {
                            if (args.isNotEmpty()) {
                                val sx = numericFromTerm(args[0])
                                view.scaleX = sx
                                view.scaleY = sx
                            }
                            if (args.size >= 2) {
                                view.scaleY = numericFromTerm(args[1])
                            }
                        }
                    }
                }
                "margin" -> {
                    val params = view.layoutParams
                    if (params !is ViewGroup.MarginLayoutParams) continue
                    var l = params.leftMargin
                    var t = params.topMargin
                    var r = params.rightMargin
                    var b = params.bottomMargin
                    when (terms.size) {
                        1 -> {
                            l = getExactValue(terms[0], view.width)
                            t = l
                            r = l
                            b = l
                        }
                        2 -> {
                            t = getExactValue(terms[0], view.height)
                            b = t
                            l = getExactValue(terms[1], view.width)
                            r = l
                        }
                        4 -> {
                            t = getExactValue(terms[0], view.height)
                            r = getExactValue(terms[1], view.width)
                            b = getExactValue(terms[2], view.height)
                            l = getExactValue(terms[3], view.width)
                        }
                    }
                    params.setMargins(l, t, r, b)
                    view.requestLayout()
                }
                "margin-left" -> {
                    val p = view.layoutParams
                    if (p !is ViewGroup.MarginLayoutParams) continue
                    p.leftMargin = getExactValue(terms[0], view.width)
                    view.requestLayout()
                }
                "margin-top" -> {
                    val p = view.layoutParams
                    if (p !is ViewGroup.MarginLayoutParams) continue
                    p.topMargin = getExactValue(terms[0], view.height)
                    view.requestLayout()
                }
                "margin-right" -> {
                    val p = view.layoutParams
                    if (p !is ViewGroup.MarginLayoutParams) continue
                    p.rightMargin = getExactValue(terms[0], view.width)
                    view.requestLayout()
                }
                "margin-bottom" -> {
                    val p = view.layoutParams
                    if (p !is ViewGroup.MarginLayoutParams) continue
                    p.bottomMargin = getExactValue(terms[0], view.height)
                    view.requestLayout()
                }
                "padding" -> {
                    var l = view.paddingLeft
                    var t = view.paddingTop
                    var r = view.paddingRight
                    var b = view.paddingBottom
                    when (terms.size) {
                        1 -> {
                            l = getExactValue(terms[0], view.width)
                            t = l
                            r = l
                            b = l
                        }
                        2 -> {
                            t = getExactValue(terms[0], view.height)
                            b = t
                            l = getExactValue(terms[1], view.width)
                            r = l
                        }
                        4 -> {
                            t = getExactValue(terms[0], view.height)
                            r = getExactValue(terms[1], view.width)
                            b = getExactValue(terms[2], view.height)
                            l = getExactValue(terms[3], view.width)
                        }
                    }
                    view.setPadding(l, t, r, b)
                }
                "padding-left" -> view.setPadding(
                    getExactValue(terms[0], view.width),
                    view.paddingTop, view.paddingRight, view.paddingBottom)
                "padding-top" -> view.setPadding(
                    view.paddingLeft,
                    getExactValue(terms[0], view.height),
                    view.paddingRight, view.paddingBottom)
                "padding-right" -> view.setPadding(
                    view.paddingLeft, view.paddingTop,
                    getExactValue(terms[0], view.width),
                    view.paddingBottom)
                "padding-bottom" -> view.setPadding(
                    view.paddingLeft, view.paddingTop, view.paddingRight,
                    getExactValue(terms[0], view.height))
            }
        }
    }

    private fun setHookedDrawable(view: View, draw: Drawable) {
        if (view is ImageView) {
            forcedDrawableMap[view] = draw
            view.setImageDrawable(draw)
        } else {
            forcedBackgroundMap[view] = draw
            view.background = draw
        }
    }

    private fun setBackgroundModel(view: View, term: SerialTerm) {
        if (term.type == SerialTerm.LINEAR_GRADIENT) {
            try {
                var gradientDrawable = chacheDrawables[term.strValue]
                if (gradientDrawable == null) {
                    gradientDrawable = GradientDrawableParser.parseGradient(
                        term.gradientAngle, term.gradientColors, term.gradientPositions,
                        view.width, view.height)
                    chacheDrawables[term.strValue] = gradientDrawable
                }
                forcedBackgroundMap[view] = gradientDrawable
                view.background = gradientDrawable
            } catch (e: Exception) {
                log("Error parsing gradient: ${e.message}")
            }
        }
    }

    override fun getPluginName(): String = "Custom View"

    inner class DrawableCache(context: Context, maxSize: Int) {
        private val drawableCache = LruCache<String, CachedDrawable>(maxSize)
        private val context = context.applicationContext

        fun getDrawable(filePath: String, width: Int, height: Int): Drawable? {
            val file = if (filePath.startsWith("/")) File(filePath) else File(themeDir, filePath)
            val key = file.absolutePath

            val cachedDrawable = drawableCache.get(key)

            if (cachedDrawable != null) {
                if (System.currentTimeMillis() - cachedDrawable.lastCheckTime < 2000) {
                    return cachedDrawable.drawable
                }
            }

            if (!file.exists()) return null

            val lastModified = file.lastModified()
            if (cachedDrawable != null) {
                cachedDrawable.lastCheckTime = System.currentTimeMillis()
                if (cachedDrawable.lastModified == lastModified) return cachedDrawable.drawable
            }

            val cached = loadDrawableFromCache(key, lastModified)
            if (cached != null) {
                val entry = CachedDrawable(cached, lastModified)
                drawableCache.put(key, entry)
                return cached
            }

            val drawable = loadDrawableFromFile(key, width, height) ?: return null
            saveDrawableToCache(key, drawable as BitmapDrawable, lastModified)
            val entry = CachedDrawable(drawable, lastModified)
            drawableCache.put(key, entry)
            return drawable
        }

        private fun loadDrawableFromFile(filePath: String, reqWidth: Int, reqHeight: Int): Drawable? {
            return try {
                val file = File(filePath)
                val bitmap = if (!file.canRead()) {
                    val bridge = WppCore.getClientBridge() ?: return null
                    val parcelFile = bridge.openFile(filePath, false)
                    BitmapFactory.decodeStream(FileInputStream(parcelFile.fileDescriptor))
                } else {
                    BitmapFactory.decodeFile(file.absolutePath)
                } ?: return null
                val newHeight = if (reqHeight < 1) bitmap.height else minOf(bitmap.height, reqHeight)
                val newWidth = if (reqWidth < 1) bitmap.width else minOf(bitmap.width, reqWidth)
                val resized =
                    bitmap.scale(newWidth, newHeight)
                resized.toDrawable(context.resources)
            } catch (e: Exception) {
                XposedBridge.log(e)
                null
            }
        }

        private fun saveDrawableToCache(key: String, drawable: BitmapDrawable, lastModified: Long) {
            val cacheDir = context.cacheDir
            val cacheLocation = File(cacheDir, "drawable_cache")
            if (!cacheLocation.exists()) cacheLocation.mkdirs()
            val cacheFile = File(cacheLocation, getCacheFileName(key))
            val metadataFile = File(cacheLocation, "${getCacheFileName(key)}.meta")
            try {
                FileOutputStream(cacheFile).use { out ->
                    drawable.bitmap.compress(Bitmap.CompressFormat.PNG, 80, out)
                }
                ObjectOutputStream(FileOutputStream(metadataFile)).use { metaOut ->
                    metaOut.writeLong(lastModified)
                }
                Log.d("DrawableCache", "Saved drawable to cache: ${cacheFile.absolutePath}")
            } catch (e: IOException) {
                Log.e("DrawableCache", "Failed to save drawable to cache", e)
            }
        }

        private fun loadDrawableFromCache(key: String, originalLastModified: Long): Drawable? {
            val cacheDir = context.cacheDir
            val cacheLocation = File(cacheDir, "drawable_cache")
            val cacheFile = File(cacheLocation, getCacheFileName(key))
            val metadataFile = File(cacheLocation, "${getCacheFileName(key)}.meta")
            if (!cacheFile.exists() || !metadataFile.exists()) {
                log("Drawable not found in cache: ${cacheFile.absolutePath}")
                return null
            }
            return try {
                ObjectInputStream(FileInputStream(metadataFile)).use { metaIn ->
                    val cachedLastModified = metaIn.readLong()
                    if (cachedLastModified != originalLastModified) return@use null
                    val bitmap = BitmapFactory.decodeFile(cacheFile.absolutePath)
                    if (bitmap != null) bitmap.toDrawable(context.resources) else null
                }
            } catch (e: IOException) {
                Log.e("DrawableCache", "Failed to load drawable from cache", e)
                null
            }
        }

        private fun getCacheFileName(input: String): String {
            return Objects.hash(input).toString()
        }
    }

    private class CachedDrawable(val drawable: Drawable, val lastModified: Long) {
        var lastCheckTime = System.currentTimeMillis()
    }

    class SelectorPart : Serializable {
        var idName: String? = null
        var resolvedId: Int = 0
        var className: String? = null
        var typeSelector: String? = null
        var pseudoClass: String? = null
    }

    class SerialTerm : Serializable {
        companion object {
            const val COLOR: Byte = 1
            const val LENGTH: Byte = 2
            const val URI: Byte = 3
            const val FLOAT_VAL: Byte = 4
            const val STRING: Byte = 5
            const val LINEAR_GRADIENT: Byte = 6
            const val FUNCTION: Byte = 7
        }

        var type: Byte = 0
        var colorRgb: Int = 0
        var numValue: Float = 0f
        var percentage: Boolean = false
        var unitName: String? = null
        var strValue: String = ""
        var args: ArrayList<SerialTerm>? = null
        var gradientAngle: Float = 0f
        var gradientColors: IntArray = IntArray(0)
        var gradientPositions: FloatArray = FloatArray(0)
    }

    class SerialDeclaration : Serializable {
        var property: String = ""
        var terms: ArrayList<SerialTerm> = ArrayList()
    }

    class CachedRuleItem(
        var selector: ArrayList<SelectorPart>,
        var declarations: ArrayList<SerialDeclaration>,
        var targetActivityClassName: String?
    ) : Serializable

    class GradientDrawableParser {
        companion object {
            fun parseGradient(angle: Float, colors: IntArray, positions: FloatArray,
                              width: Int, height: Int): BitmapDrawable {
                val lg = createLinearGradient(angle, colors, positions, width, height)
                val sd = ShapeDrawable(RectShape())
                sd.intrinsicWidth = width
                sd.intrinsicHeight = height
                sd.paint.shader = lg
                val bitmap = createBitmap(width, height)
                sd.bounds = android.graphics.Rect(0, 0, width, height)
                sd.draw(Canvas(bitmap))
                return bitmap.toDrawable(Utils.application.resources)
            }

            private fun createLinearGradient(angle: Float, colors: IntArray, positions: FloatArray,
                                             width: Int, height: Int): LinearGradient {
                val radians = Math.toRadians(angle.toDouble())
                val x0 = (0.5 * width + 0.5 * width * cos(radians - Math.PI / 2)).toFloat()
                val y0 = (0.5 * height + 0.5 * height * sin(radians - Math.PI / 2)).toFloat()
                val x1 = (0.5 * width + 0.5 * width * cos(radians + Math.PI / 2)).toFloat()
                val y1 = (0.5 * height + 0.5 * height * sin(radians + Math.PI / 2)).toFloat()
                return LinearGradient(x0, y0, x1, y1, colors, positions, Shader.TileMode.CLAMP)
            }
        }
    }

    private fun captureSelector(currentView: View, selector: ArrayList<SelectorPart>,
                                position: Int, resultViews: ArrayList<View>) {
        if (selector.size == position) return
        val part = selector[position]
        if (part.className != null) {
            captureSelector(currentView, selector, position + 1, resultViews)
        } else if (part.idName != null) {
            val id = part.resolvedId
            if (id <= 0) return
            val view = if (currentView.id == id) currentView else currentView.findViewById(id)
            if (selector.size == position + 1) {
                resultViews.add(view)
            } else {
                captureSelector(view, selector, position + 1, resultViews)
            }
        } else {
            if (currentView !is ViewGroup) return
            val typeName = part.typeSelector
            val pseudo = part.pseudoClass
            val itemCount = intArrayOf(0)
            for (i in 0 until currentView.childCount) {
                val itemView = currentView.getChildAt(i)
                if (ReflectionUtils.isClassSimpleNameString(itemView.javaClass, typeName)) {
                    if (pseudo != null && checkAttribute(itemView, itemCount, pseudo)) continue
                    if (selector.size == position + 1) {
                        resultViews.add(itemView)
                    } else {
                        captureSelector(itemView, selector, position + 1, resultViews)
                    }
                } else if (isWidgetString(typeName) && itemView is ViewGroup) {
                    for (j in 0 until itemView.childCount) {
                        captureSelector(itemView.getChildAt(j), selector, position, resultViews)
                    }
                }
            }
        }
    }

    private fun checkAttribute(itemView: View, itemCount: IntArray, name: String): Boolean {
        return if (name.startsWith("nth-child")) {
            val index = name.substring(name.indexOf('(') + 1, name.indexOf(')')).toInt() - 1
            index != itemCount[0]++
        } else if (name.startsWith("contains")) {
            val contains = name.substring(name.indexOf('(') + 1, name.indexOf(')'))
            if (itemView is TextView) !itemView.text.toString().contains(contains)
            else !itemView.toString().contains(contains)
        } else false
    }

    private fun isWidgetString(view: String?): Boolean {
        if (view == null) return false
        return widgetClassCache.getOrPut(view) {
            XposedHelpers.findClassIfExists("android.widget.$view", null) != null
        }
    }

    private fun resolveClass(className: String?): Class<*>? {
        if (className == null) return null
        return resolvedClasses.getOrPut(className) {
            XposedHelpers.findClassIfExists(className, classLoader)
        }
    }

    private fun getColorStateList(terms: List<SerialTerm>): ColorStateList {
        val def = terms[0].colorRgb
        val pressed = terms[1].colorRgb
        val disabled = terms[2].colorRgb
        return ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_pressed),
                intArrayOf(android.R.attr.state_enabled),
                intArrayOf(android.R.attr.state_focused, android.R.attr.state_pressed),
                intArrayOf(-android.R.attr.state_enabled),
                intArrayOf()
            ),
            intArrayOf(pressed, def, pressed, disabled, def)
        )
    }

    private fun getRealValue(pValue: SerialTerm, size: Int): Int {
        val value = if (pValue.unitName == "px") {
            Utils.dipToPixels(pValue.numValue)
        } else if (pValue.percentage) {
            size * pValue.numValue.toInt() / 100
        } else {
            pValue.numValue.toInt()
        }
        return if (value > 0) value else 1
    }

    private fun getExactValue(pValue: SerialTerm, size: Int): Int {
        if (pValue.unitName == "px") return Utils.dipToPixels(pValue.numValue)
        if (pValue.percentage) return size * pValue.numValue.toInt() / 100
        return pValue.numValue.toInt()
    }

    private fun numericFromTerm(t: SerialTerm): Float {
        if (t.type == SerialTerm.FLOAT_VAL || t.type == SerialTerm.LENGTH) return t.numValue
        return try {
            t.strValue.toFloat()
        } catch (_: Exception) {
            1f
        }
    }

    companion object {
        private var themeDir: File? = null
        private fun changeDPI(activity: Activity, prefs:SharedPreferences, properties: Properties) {
            val dpi = when {
                prefs.getString("change_dpi", "0") != "0" -> prefs.getString("change_dpi", "0")
                properties.getProperty("change_dpi") != null -> properties.getProperty("change_dpi")
                else -> null
            }?.toIntOrNull() ?: return
            if (dpi == 0) return
            val res = activity.resources
            val runningMetrics = res.displayMetrics
            val newMetrics = if (runningMetrics != null) {
                DisplayMetrics().also { it.setTo(runningMetrics) }
            } else {
                res.displayMetrics
            }
            newMetrics.density = dpi / 160f
            newMetrics.densityDpi = dpi
            res.displayMetrics.setTo(newMetrics)
        }

        private fun toSerialTerm(term: Term<*>): SerialTerm {
            val st = SerialTerm()
            when (term) {
                is TermFunction.LinearGradient -> {
                    st.type = SerialTerm.LINEAR_GRADIENT
                    st.strValue = term.toString()
                    st.gradientAngle = term.angle.value
                    val stops = term.colorStops
                    st.gradientColors = IntArray(stops.size)
                    st.gradientPositions = FloatArray(stops.size)
                    for (i in stops.indices) {
                        st.gradientColors[i] = stops[i].color.value.rgb
                        st.gradientPositions[i] = stops[i].length.value / 100f
                    }
                }
                is TermFunction -> {
                    st.type = SerialTerm.FUNCTION
                    st.strValue = term.functionName
                    val values = term.getValues(true)
                    st.args = ArrayList()
                    for (v in values) st.args!!.add(toSerialTerm(v))
                }
                is TermColor -> {
                    st.type = SerialTerm.COLOR
                    st.colorRgb = term.value.rgb
                }
                is TermLength -> {
                    st.type = SerialTerm.LENGTH
                    st.numValue = term.value
                    st.percentage = term.isPercentage
                    st.unitName = term.unit?.toString()
                }
                is TermFloatValue -> {
                    st.type = SerialTerm.FLOAT_VAL
                    st.numValue = term.value
                }
                is TermURI -> {
                    st.type = SerialTerm.URI
                    st.strValue = term.value
                }
                else -> {
                    st.type = SerialTerm.STRING
                    st.strValue = term.toString()
                }
            }
            return st
        }
    }
}
