package com.wmods.wppenhacer.xposed.features.others

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.components.AlertDialogWpp
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.utils.Utils
import com.wmods.wppenhacer.xposed.utils.setTouchClickAndLongClickListener

class Stickers(classLoader: ClassLoader, preferences: SharedPreferences) :
    Feature(classLoader, preferences) {

    override fun doHook() {

        if (!xprefs.getBoolean("alertsticker", false)) return
        View::class.java.resolve().firstMethod {
            name = "onAttachedToWindow"
            superclass()
            emptyParameters()
        }.hook {
            after {
                val view = instance as View
                if (view.id != Utils.getID("stickerContainer", "id")) return@after
                if (view.tag == "wae_hooked") return@after
                view.tag = "wae_hooked"
                view.setTouchClickAndLongClickListener(
                    onClick = {
                        showAlertDialog(view)
                    },
                    onLongClick = {
                        view.performLongClick()
                    }
                )
            }
        }
        if (xprefs.getBoolean("remove_sticker_white_outline", false)) {
            val stickerColoredOutline = Unobfuscator.loadStickerColoredOutline(classLoader)
            stickerColoredOutline.hook {
                before {
                    val source = args[0] as Bitmap
                    val safeConfig = source.config ?: Bitmap.Config.ARGB_8888
                    result = source.copy(safeConfig, true)
                }
            }
        }
    }


    private fun showAlertDialog(view: View) {
        val context = view.context
        val stickerView = view.findViewById<ImageView?>(
            Utils.getID("sticker", "id")
        ) ?: return

        val dialog = AlertDialogWpp(context)
        dialog.setTitle(context.getString(R.string.send_sticker))
        val linearLayout = LinearLayout(context)
        linearLayout.orientation = LinearLayout.VERTICAL
        linearLayout.gravity = Gravity.CENTER_HORIZONTAL
        val padding = Utils.dipToPixels(16)
        linearLayout.setPadding(padding, padding, padding, padding)
        val image = ImageView(context)
        val size = Utils.dipToPixels(72)
        val params = LinearLayout.LayoutParams(size, size)
        params.bottomMargin = padding
        image.layoutParams = params
        image.setImageDrawable(stickerView.drawable)
        linearLayout.addView(image)

        val text = TextView(context)
        text.text = context.getString(R.string.do_you_want_to_send_sticker)
        text.textAlignment = View.TEXT_ALIGNMENT_CENTER
        linearLayout.addView(text)


        dialog.setView(linearLayout)
        dialog.setPositiveButton(
            context.getString(R.string.send)
        ) { _, _ -> view.performClick() }
        dialog.setNegativeButton(
            context.getString(R.string.cancel),
            null
        )
        dialog.show()
    }


    override fun getPluginName(): String {
        return "Stickers"
    }
}
