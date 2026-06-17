package com.wmods.wppenhacer.xposed.features.others

import android.view.View
import android.widget.EditText
import androidx.core.graphics.drawable.toDrawable
import com.wmods.wppenhacer.views.dialog.SimpleColorPickerDialog
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.utils.Utils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

class TextStatusComposer(
    classLoader: ClassLoader,
    preferences: XSharedPreferences
) : Feature(classLoader, preferences) {

    private var customTextColor: Int? = null
    private var customBackgroundColor: Int? = null

    @Throws(Throwable::class)
    override fun doHook() {
        if (!prefs.getBoolean("statuscomposer", false)) return

        val methodOnCreate = Unobfuscator.loadTextStatusComposerOnCreate(classLoader)

        XposedBridge.hookMethod(methodOnCreate, object : XC_MethodHook(){
            override fun afterHookedMethod(param: MethodHookParam) {
                customTextColor = null
                customBackgroundColor = null

                val activity = WppCore.getCurrentActivity() ?: return
                val viewRoot = param.args.filterIsInstance<View>().first()

                val pickerColor = viewRoot.findViewById<View>(Utils.getID("color_picker_btn", "id"))
                val entry = viewRoot.findViewById<EditText>(Utils.getID("entry", "id"))

                pickerColor?.setOnLongClickListener {
                    val dialog = SimpleColorPickerDialog(activity) { color ->
                        try {
                            activity.window.setBackgroundDrawable(color.toDrawable())
                            viewRoot.findViewById<View>(Utils.getID("background", "id"))?.setBackgroundColor(color)
                            viewRoot.findViewById<View>(Utils.getID("controls", "id"))?.setBackgroundColor(color)
                            customBackgroundColor = color
                        } catch (e: Exception) {
                            logDebug(e)
                        }
                    }
                    dialog.create().setCanceledOnTouchOutside(false)
                    dialog.show()
                    true
                }

                val textColorBtn = viewRoot.findViewById<View>(Utils.getID("font_picker_btn", "id"))
                textColorBtn?.setOnLongClickListener {
                    val dialog = SimpleColorPickerDialog(activity) { color ->
                        customTextColor = color
                        entry?.setTextColor(color)
                    }
                    dialog.create().setCanceledOnTouchOutside(false)
                    dialog.show()
                    true
                }
            }
        })

        val statusDataHook = Unobfuscator.loadTextStatusDataFStatus(classLoader)
        XposedBridge.hookMethod(statusDataHook, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val textData = param.args[0] ?: return
                setCustomColorTextData(textData)
            }
        })


        val methodsTextStatus = Unobfuscator.loadTextStatusData(classLoader)

        methodsTextStatus.forEach {
            XposedBridge.hookMethod(it, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val textData = param.args[0] ?: return
                    setCustomColorTextData(textData)
                }
            })
        }
    }

    private fun setCustomColorTextData(textData: Any) {
        customTextColor?.let { color ->
            XposedHelpers.setObjectField(textData, "textColor", color)
        }
        customBackgroundColor?.let { color ->
            XposedHelpers.setObjectField(textData, "backgroundColor", color)
        }
        textData.javaClass.declaredFields.firstOrNull {
            it.name == "backgroundColorHasChanged"
        }?.set(textData, true)
    }

    override fun getPluginName(): String {
        return "Text Status Composer"
    }
}