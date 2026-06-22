package com.wmods.wppenhacer.xposed.features.general

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.text.InputType
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.LinearLayout
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore.homeActivityClass
import com.wmods.wppenhacer.xposed.core.components.AlertDialogWpp
import com.wmods.wppenhacer.xposed.utils.DesignUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedHelpers
import androidx.core.net.toUri

class NewChat(loader: ClassLoader, preferences: XSharedPreferences) : Feature(loader, preferences) {

    override fun doHook() {
        val homeActivity = homeActivityClass
        val action = prefs.getBoolean("buttonaction", true)

        if (!prefs.getBoolean("newchat", true)) return

        XposedHelpers.findAndHookMethod(
            homeActivity,
            "onCreateOptionsMenu",
            Menu::class.java,
            object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun afterHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject as Activity
                    val menu = param.args[0] as Menu
                    val item = menu.add(0, 0, 0, R.string.new_chat)
                    val drawable = DesignUtils.getDrawableByName("vec_ic_chat_add")

                    if (drawable != null) {
                        drawable.setTint(if (action) DesignUtils.getPrimaryTextColor() else -0x796960)
                        item.icon = drawable
                    }

                    if (action) {
                        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                    }

                    item.setOnMenuItemClickListener {
                        val view = LinearLayout(activity)
                        view.gravity = Gravity.CENTER
                        view.layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.MATCH_PARENT
                        )
                        val edt = EditText(view.context).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                1.0f
                            )
                            inputType = InputType.TYPE_CLASS_PHONE
                            transformationMethod = null
                            setHint(R.string.number_with_country_code)
                            view.addView(this)
                        }


                        AlertDialogWpp(activity)
                            .setTitle(activity.getString(R.string.new_chat))
                            .setView(view)
                            .setPositiveButton(
                                activity.getString(R.string.message)
                            ) { _, _ ->
                                val number = edt.text.toString()
                                val numberFomatted =
                                    number.replace("[+\\-()/\\s]".toRegex(), "")
                                val intent = Intent(Intent.ACTION_VIEW)
                                intent.data = ("https://wa.me/$numberFomatted").toUri()
                                intent.setPackage(Utils.application.packageName)
                                activity.startActivity(intent)
                            }
                            .setNegativeButton(activity.getString(R.string.cancel), null)
                            .show()
                        true
                    }
                }
            })
    }

    public override fun getPluginName(): String {
        return "New Chat"
    }
}
