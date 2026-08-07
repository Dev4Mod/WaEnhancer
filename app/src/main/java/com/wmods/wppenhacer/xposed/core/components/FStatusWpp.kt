package com.wmods.wppenhacer.xposed.core.components

import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.io.File
import java.lang.reflect.Field
import java.lang.reflect.Method

class FStatusWpp(val fstatus: Any?) {

    companion object {

        private lateinit var classFMediaStatus: Class<*>
        private lateinit var methodGetStatusByKey: Method

        lateinit var TYPE: Class<*>
        private lateinit var fieldFStatusKey: Field

        private var mStatusStore: Any? = null

        @JvmStatic
        fun initialize(classLoader: ClassLoader) {
            FStatusKey.initialize(classLoader)
            TYPE = Unobfuscator.loadFStatusClass(classLoader)
            val fStatusKeyClass = Unobfuscator.loadFStatusKeyClass(classLoader)
            fieldFStatusKey = ReflectionUtils.getFieldByType(TYPE, fStatusKeyClass)!!
            methodGetStatusByKey = Unobfuscator.loadGetStatusByKey(classLoader)
            XposedBridge.hookAllConstructors(
                methodGetStatusByKey.declaringClass,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        mStatusStore = param.thisObject
                    }
                })
            classFMediaStatus = Unobfuscator.loadFMediaStatusClass(classLoader)
        }

        @JvmStatic
        fun getFStatusFromFKeyStatus(fStatusKey: FStatusKey): FStatusWpp? {
            try {
                if (mStatusStore == null) {
                    mStatusStore = methodGetStatusByKey.declaringClass.declaredConstructors.first()
                        .newInstance()
                }
                return FStatusWpp(methodGetStatusByKey.invoke(mStatusStore, fStatusKey.thisObject))
            } catch (e: Exception) {
                XposedBridge.log(e)
            }
            return null
        }

    }


    init {
        if (fstatus == null) throw RuntimeException("Object FStatus is null")
        if (!TYPE.isInstance(fstatus))
            throw RuntimeException("Object is not a FStatus Instance")
    }

    val isMediaFile by lazy {
        classFMediaStatus.isInstance(fstatus)
    }


    val fStatusKey by lazy {
        FStatusKey(fieldFStatusKey.get(fstatus))
    }



    val fMessage: FMessageWpp? by lazy {
        try {
            FMessageWpp(WppCore.getFMessageFromFStatus(fstatus))
        } catch (e: Exception) {
            XposedBridge.log(e)
            null
        }
    }

    private val mediaFileAccessor: Pair<Field, Method>? by lazy {
        classFMediaStatus.declaredFields.firstNotNullOfOrNull { field ->
            field.isAccessible = true

            val method = field.type.declaredMethods.firstOrNull {
                it.returnType == File::class.java
            }

            if (method != null) {
                method.isAccessible = true
                Pair(field, method)
            } else {
                null
            }
        }
    }

    fun getMediaFile(): File? {
        if (!isMediaFile) return null

        val (field, method) = mediaFileAccessor ?: run {
            XposedBridge.log("Media file accessor not found for FStatus class: ${classFMediaStatus.name}")
            return null
        }

        val item = field.get(fstatus) ?: return null
        return runCatching {
            method.invoke(item) as? File
        }.getOrNull()
    }

    override fun toString(): String {
        return "FStatusWpp(fstatus=$fstatus, isMedia=$isMediaFile, fStatusKey=$fStatusKey)"
    }

    class FStatusKey {

        companion object {
            /**
             * The class type of the key object.
             */
            lateinit var TYPE: Class<*>

            @JvmStatic
            fun initialize(classLoader: ClassLoader) {
                TYPE = Unobfuscator.loadFStatusKeyClass(classLoader)
            }

        }

        @JvmField
        var senderJid: FMessageWpp.UserJid

        /**
         * The underlying key object from WhatsApp's code.
         */
        @JvmField
        var thisObject: Any? = null

        /**
         * The unique identifier for the message.
         */
        @JvmField
        var messageID: String

        /**
         * A boolean indicating if the message was sent by the current user.
         */
        @JvmField
        var isFromMe: Boolean = false

        /**
         * The JID of whatsapp
         */
        @JvmField
        var remoteJid: FMessageWpp.UserJid


        @JvmField
        var fStatus: FStatusWpp? = null


        val key: FMessageWpp.Key by lazy {
            try {
                ReflectionUtils.findFieldUsingFilter(TYPE) {
                    FMessageWpp.Key.TYPE.isAssignableFrom(it.type)
                }.let {
                    FMessageWpp.Key(it.get(thisObject))
                }
            } catch (e: Exception) {
                XposedBridge.log(e)
                FMessageWpp.Key(null)
            }
        }

        constructor(key: Any?) {
            this.thisObject = key
            this.senderJid = FMessageWpp.UserJid(XposedHelpers.getObjectField(key, "A01"))
            this.messageID = XposedHelpers.getObjectField(key, "A02") as String
            this.isFromMe = XposedHelpers.getBooleanField(key, "A03")
            this.remoteJid = FMessageWpp.UserJid(XposedHelpers.getObjectField(key, "A00"))
            this.fStatus = getFStatusFromFKeyStatus(this)
        }

        override fun toString(): String {
            return "FStatusKey{" +
                    "thisObject=" + thisObject +
                    ", messageID='" + messageID + '\'' +
                    ", isFromMe=" + isFromMe +
                    ", remoteJid=" + remoteJid +
                    ", senderJid=" + senderJid +
                    '}'
        }
    }

}