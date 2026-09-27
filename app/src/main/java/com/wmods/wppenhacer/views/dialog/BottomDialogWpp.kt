package com.wmods.wppenhacer.views.dialog

import android.app.Dialog
import android.graphics.Color
import android.view.View
import android.view.WindowManager
import com.wmods.wppenhacer.xposed.utils.Utils

class BottomDialogWpp(private val dialog: Dialog) {
    fun dismissDialog() {
        dialog.dismiss()
    }

    fun showDialog() {
        dialog.show()
        dialog.window?.let { window ->
            window.setBackgroundDrawable(null)
            window.setDimAmount(0f)
            window.decorView.findViewById<View>(Utils.getID("design_bottom_sheet", "id"))
                .setBackgroundColor(Color.TRANSPARENT)
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        }
    }

    fun setContentView(view: View) {
        dialog.setContentView(view)
    }

    fun setCanceledOnTouchOutside(canceled: Boolean) {
        dialog.setCanceledOnTouchOutside(canceled)
    }
}
