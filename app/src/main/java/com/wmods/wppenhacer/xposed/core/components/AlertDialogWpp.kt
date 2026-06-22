package com.wmods.wppenhacer.xposed.core.components

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.DialogInterface.OnMultiChoiceClickListener
import android.view.View
import android.widget.Toast
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator.loadMaterialAlertDialog
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Method

open class AlertDialogWpp(val context: Context?) {
    private var mAlertDialog: AlertDialog.Builder? = null
    private var mAlertDialogWpp: Any? = null
    private var mCreate: Dialog? = null

    init {
        if (isSystemDialog) {
            mAlertDialog = AlertDialog.Builder(context)
        }else {
            try {
                mAlertDialogWpp = getAlertDialog!!.invoke(null, context)
                setMessage(null)
            } catch (_: Exception) {
                throw RuntimeException("Failed to create AlertDialogWpp")
            }
        }
    }

    fun setTitle(title: String?): AlertDialogWpp {
        if (isSystemDialog) {
            mAlertDialog!!.setTitle(title)
            return this
        }
        XposedHelpers.callMethod(mAlertDialogWpp, "setTitle", title)
        return this
    }

    fun setTitle(title: Int): AlertDialogWpp {
        if (isSystemDialog) {
            mAlertDialog!!.setTitle(title)
            return this
        }
        XposedHelpers.callMethod(mAlertDialogWpp, "setTitle", this.context!!.getString(title))
        return this
    }

    fun setMessage(message: CharSequence?): AlertDialogWpp {
        if (isSystemDialog) {
            mAlertDialog!!.setMessage(message)
            return this
        }
        try {
            setMessageMethod!!.invoke(mAlertDialogWpp, message)
        } catch (_: Exception) {
        }
        return this
    }

    fun setItems(
        items: Array<CharSequence?>?,
        listener: DialogInterface.OnClickListener?
    ): AlertDialogWpp {
        if (isSystemDialog) {
            mAlertDialog!!.setItems(items, listener)
            return this
        }
        try {
            setItemsMethod!!.invoke(mAlertDialogWpp, listener, items)
        } catch (e: Exception) {
            XposedBridge.log(e)
        }
        return this
    }


    fun setMultiChoiceItems(
        items: Array<String>,
        checkedItems: BooleanArray?,
        listener: OnMultiChoiceClickListener?
    ): AlertDialogWpp {
        if (isSystemDialog) {
            mAlertDialog!!.setMultiChoiceItems(items, checkedItems, listener)
            return this
        }
        try {
            setMultiChoiceItemsMethod!!.invoke(mAlertDialogWpp, listener, items, checkedItems)
        } catch (_: Exception) {
        }
        return this
    }

    fun setNegativeButton(
        text: CharSequence?,
        listener: DialogInterface.OnClickListener?
    ): AlertDialogWpp {
        if (isSystemDialog) {
            mAlertDialog!!.setNegativeButton(text, listener)
            return this
        }
        try {
            setNegativeButtonMethod!!.invoke(mAlertDialogWpp, listener, text)
        } catch (_: Exception) {
        }
        return this
    }

    fun setPositiveButton(
        text: CharSequence?,
        listener: DialogInterface.OnClickListener?
    ): AlertDialogWpp {
        if (isSystemDialog) {
            mAlertDialog!!.setPositiveButton(text, listener)
            return this
        }
        try {
            setPositiveButtonMethod!!.invoke(mAlertDialogWpp, listener, text)
        } catch (_: Exception) {
        }
        return this
    }

    fun setView(view: View?): AlertDialogWpp {
        if (isSystemDialog) {
            mAlertDialog!!.setView(view)
            return this
        }
        XposedHelpers.callMethod(mAlertDialogWpp, "setView", view)
        return this
    }


    open fun create(): Dialog {
        if (mCreate != null) return mCreate!!
        mCreate = if (isSystemDialog) {
            mAlertDialog!!.create()
        } else {
            XposedHelpers.callMethod(mAlertDialogWpp, "create") as Dialog
        }
        return mCreate!!
    }

    fun dismiss() {
        if (mCreate == null) return
        mCreate!!.dismiss()
    }

    fun show() {
        if (this.context is Activity) {
            val activity = this.context
            if (activity.isFinishing || activity.isDestroyed) {
                return
            }
        }
        if (isSystemDialog) {
            mAlertDialog!!.show()
            return
        }
        create()!!.show()
    }

    companion object {
        private var getAlertDialog: Method? = null
        private var setItemsMethod: Method? = null
        private var isAvailable = false
        private var setMessageMethod: Method? = null
        private var setNegativeButtonMethod: Method? = null
        private var setPositiveButtonMethod: Method? = null
        private var setMultiChoiceItemsMethod: Method? = null
        fun initDialog(loader: ClassLoader) {
            try {
                getAlertDialog = loadMaterialAlertDialog(loader)
                val alertDialogClass: Class<*> = getAlertDialog!!.returnType
                setItemsMethod = ReflectionUtils.findMethodUsingFilter(
                    alertDialogClass
                ) { method: Method? -> method!!.parameterCount == 2 && method.parameterTypes[0] == DialogInterface.OnClickListener::class.java && method.parameterTypes[1] == Array<CharSequence>::class.java }
                setMultiChoiceItemsMethod = ReflectionUtils.findMethodUsingFilter(
                    alertDialogClass
                ) { method: Method? -> method!!.parameterCount == 3 && method.parameterTypes[0] == OnMultiChoiceClickListener::class.java && method.parameterTypes[1] == Array<CharSequence>::class.java }
                setMessageMethod = ReflectionUtils.findMethodUsingFilter(
                    alertDialogClass
                ) { method: Method? -> method!!.parameterCount == 1 && method.parameterTypes[0] == CharSequence::class.java }
                val buttons = ReflectionUtils.findAllMethodsUsingFilter(
                    alertDialogClass
                ) { method: Method? -> method!!.parameterCount == 2 && method.parameterTypes[0] == DialogInterface.OnClickListener::class.java && method.parameterTypes[1] == CharSequence::class.java }
                setNegativeButtonMethod = buttons[0]
                setPositiveButtonMethod = buttons[2]
                isAvailable = true
            } catch (e: Throwable) {
                isAvailable = false
                XposedBridge.log(e)
                Utils.showToast("Failed to load MaterialAlertDialog", Toast.LENGTH_SHORT)
            }
        }

        val isSystemDialog: Boolean
            get() = !isAvailable
    }
}
