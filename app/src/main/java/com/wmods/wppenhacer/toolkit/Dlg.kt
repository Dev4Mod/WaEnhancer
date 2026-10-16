package com.wmods.wppenhacer.toolkit

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.view.View

/** Envoltorio mínimo sobre AlertDialog que permite configurar, crear una vez y mostrar. */
class Dlg(context: Context) {
    private val builder = AlertDialog.Builder(context)
    private var dialog: AlertDialog? = null

    fun setTitle(title: String) = apply { builder.setTitle(title) }
    fun setMessage(message: String) = apply { builder.setMessage(message) }
    fun setView(view: View) = apply { builder.setView(view) }

    fun setPositiveButton(text: String, onClick: ((DialogInterface, Int) -> Unit)?) = apply {
        builder.setPositiveButton(text, onClick?.let { c -> DialogInterface.OnClickListener { d, w -> c(d, w) } })
    }

    fun setNegativeButton(text: String, onClick: ((DialogInterface, Int) -> Unit)?) = apply {
        builder.setNegativeButton(text, onClick?.let { c -> DialogInterface.OnClickListener { d, w -> c(d, w) } })
    }

    fun create(): AlertDialog = dialog ?: builder.create().also { dialog = it }

    fun show() {
        val d = create()
        d.show()
        Monet.styleDialog(d)
    }

    fun dismiss() {
        try {
            dialog?.dismiss()
        } catch (_: Exception) {
        }
    }
}
