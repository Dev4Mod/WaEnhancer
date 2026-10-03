package com.wmods.wppenhacer.xposed.features.others

import android.content.SharedPreferences
import android.view.View
import android.widget.EditText
import androidx.core.graphics.drawable.toDrawable
import com.wmods.wppenhacer.views.dialog.SimpleColorPickerDialog
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils

class TextStatusComposer(
    classLoader: ClassLoader,
    preferences: SharedPreferences
) : Feature(classLoader, preferences) {

    private var customTextColor: Int? = null
    private var customBackgroundColor: Int? = null

    @Throws(Throwable::class)
    override fun doHook() {
        if (!xprefs.getBoolean("statuscomposer", false)) return

        val methodOnCreate = Unobfuscator.loadTextStatusComposerOnCreate(classLoader)

        methodOnCreate.hook {
            after {
                customTextColor = null
                customBackgroundColor = null

                val activity = WppCore.getCurrentActivity() ?: return@after
                val viewRoot = args.filterIsInstance<View>().first()

                val pickerColor = viewRoot.findViewById<View>(Utils.getID("color_picker_btn", "id"))
                val entry = viewRoot.findViewById<EditText>(Utils.getID("entry", "id"))

                pickerColor?.setOnLongClickListener {
                    val dialog = SimpleColorPickerDialog(activity) { color ->
                        try {
                            activity.window.setBackgroundDrawable(color.toDrawable())
                            viewRoot.findViewById<View>(Utils.getID("background", "id"))
                                ?.setBackgroundColor(color)
                            viewRoot.findViewById<View>(Utils.getID("controls", "id"))
                                ?.setBackgroundColor(color)
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
        }

        val statusDataHook = Unobfuscator.loadTextStatusDataFStatus(classLoader)
        statusDataHook.hook {
            before {
                val textData = args[0] ?: return@before
                setCustomColorTextData(textData)
            }
        }


        val methodsTextStatus = Unobfuscator.loadTextStatusData(classLoader)

        methodsTextStatus.forEach {
            it.hook {
                before {
                    val textData = args[0] ?: return@before
                    setCustomColorTextData(textData)
                }
            }
        }
    }

    private fun setCustomColorTextData(textData: Any) {
        customTextColor?.let { color ->
            ReflectionUtils.setObjectField(textData, "textColor", color)
        }
        customBackgroundColor?.let { color ->
            ReflectionUtils.setObjectField(textData, "backgroundColor", color)
        }
        textData.javaClass.declaredFields.firstOrNull {
            it.name == "backgroundColorHasChanged"
        }?.set(textData, true)
    }

    override fun getPluginName(): String {
        return "Text Status Composer"
    }
}