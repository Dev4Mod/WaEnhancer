package com.wmods.wppenhacer.xposed.core.components

import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import org.luckypray.dexkit.query.enums.StringMatchType
import java.io.InputStream
import java.lang.reflect.Field
import java.lang.reflect.Method

class WaContactWpp(instance: Any?) {

    val mInstance: Any

    init {
        if (instance == null) {
            throw RuntimeException("instance cannot be null")
        }
        if (!TYPE.isInstance(instance)) {
            throw RuntimeException("object is not a WaContactWpp")
        }
        this.mInstance = instance
    }

    companion object {


        lateinit var TYPE: Class<*>

        // Fields
        private var fieldContactData: Field? = null
        private var fieldUserJid: Field? = null
        private var fieldGetWaName: Field? = null
        private var fieldDataGetDisplayName: Field? = null
        private var fieldWaContactInData: Field? = null

        // Methods
        private var getWaContactMethod: Method? = null
        private var getProfilePhoto: Method? = null

        // Instances
        private var mInstanceGetWaContact: Any? = null
        private var mInstanceGetProfilePhoto: Any? = null

        @JvmStatic
        fun initialize(classLoader: ClassLoader) {
            try {
                TYPE = Unobfuscator.loadWaContactClass(classLoader)
                val classPhoneUserJid = Unobfuscator.findFirstClassUsingName(
                    classLoader,
                    StringMatchType.EndsWith,
                    "jid.PhoneUserJid"
                )
                val classJid = Unobfuscator.findFirstClassUsingName(
                    classLoader,
                    StringMatchType.EndsWith,
                    "jid.Jid"
                )

                val phoneUserJid = ReflectionUtils.getFieldByExtendType(TYPE, classPhoneUserJid)
                if (phoneUserJid == null) {
                    val contactDataClass = Unobfuscator.loadWaContactDataClass(classLoader)
                    fieldContactData = ReflectionUtils.getFieldByType(TYPE, contactDataClass)
                    fieldUserJid = ReflectionUtils.getFieldByExtendType(contactDataClass, classJid)
                } else {
                    fieldUserJid = ReflectionUtils.getFieldByExtendType(TYPE, classJid)
                }

                fieldDataGetDisplayName =
                    Unobfuscator.loadWaContactDataDisplayNameMethod(classLoader)
                fieldGetWaName = Unobfuscator.loadWaContactGetWaNameField(classLoader)

                getWaContactMethod = Unobfuscator.loadGetWaContactMethod(classLoader)
                getWaContactMethod?.let { method ->
                    XposedBridge.hookAllConstructors(
                        method.declaringClass,
                        object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                mInstanceGetWaContact = param.thisObject
                            }
                        })
                }

                getProfilePhoto = Unobfuscator.loadGetProfilePhotoMethod(classLoader)
                getProfilePhoto?.let { method ->
                    XposedBridge.hookAllConstructors(
                        method.declaringClass,
                        object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                mInstanceGetProfilePhoto = param.thisObject
                            }
                        })
                }

                fieldWaContactInData = fieldContactData!!.type.declaredFields.first {
                    it.type == TYPE
                }


            } catch (e: Exception) {
                XposedBridge.log(e)
            }
        }

        @JvmStatic
        fun getWaContactFromJid(userJid: FMessageWpp.UserJid): WaContactWpp? {
            return try {
                val instance = mInstanceGetWaContact
                val method = getWaContactMethod
                if (instance != null && method != null) {
                    var jid = userJid.userJid
                    if (!method.parameterTypes[0].isInstance(jid)) {
                        jid = FMessageWpp.UserJid.forceConverter(jid).userJid
                    }
                    val result = method.invoke(instance, jid)
                    result?.let { WaContactWpp(it) }
                } else null
            } catch (e: Exception) {
                XposedBridge.log(e)
                null
            }
        }
    }

    fun getObject(): Any = mInstance

    val userJid: FMessageWpp.UserJid by lazy {
        try {
            val userJidObj = fieldUserJid?.get(waContactData)
            userJidObj?.let { FMessageWpp.UserJid(it) } ?: FMessageWpp.UserJid()
        } catch (e: Exception) {
            XposedBridge.log(e)
            FMessageWpp.UserJid()
        }
    }

    val displayName: String?
        get() {
            return try {
                fieldDataGetDisplayName?.get(waContactData) as? String
            } catch (e: Exception) {
                XposedBridge.log(e)
                null
            }
        }

    val waName: String?
        get() {
            return try {
                if (fieldContactData!!.type.isAssignableFrom(fieldGetWaName!!.declaringClass)) {
                    return fieldGetWaName?.get(waContactData) as? String
                }
                fieldGetWaName?.get(mInstance) as? String
            } catch (e: Exception) {
                XposedBridge.log(e)
                null
            }
        }


    val waContactData: Any? by lazy {
        try {
            fieldContactData?.get(mInstance)
        } catch (e: Exception) {
            XposedBridge.log(e)
            null
        }
    }

    fun getProfilePhoto(fullImage: Boolean): InputStream? {
        return try {
            val instance = mInstanceGetProfilePhoto
            val method = getProfilePhoto
            if (instance != null && method != null) {
                method.invoke(instance, mInstance, fullImage) as? InputStream
            } else null
        } catch (e: Exception) {
            XposedBridge.log(e)
            null
        }
    }


}