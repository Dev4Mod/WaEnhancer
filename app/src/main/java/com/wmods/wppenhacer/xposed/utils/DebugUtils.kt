package com.wmods.wppenhacer.xposed.utils

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.nio.charset.StandardCharsets
import java.util.Arrays

object DebugUtils {

    @JvmStatic
    fun debugFields(cls: Class<*>?, thisObject: Any?) {
        if (cls == null) return
        XposedBridge.log("------------------------------------")
        XposedBridge.log("DEBUG FIELDS: Class " + cls.name + " -> Object " + thisObject)
        for (field in cls.declaredFields) {
            try {
                field.isAccessible = true
                val name = field.name
                var value = field[thisObject]
                if (value != null && value.javaClass.isArray) {
                    value = Arrays.toString(value as Array<Any>)
                }
                XposedBridge.log("FIELD: $name -> TYPE: ${field.type.name} -> VALUE: $value")
            } catch (_: Exception) {
            }
        }
    }

    @JvmStatic
    fun debugAllMethods(className: String, methodName: String, printMethods: Boolean, printFields: Boolean, printArgs: Boolean, printTrace: Boolean) {
        XposedBridge.hookAllMethods(
            XposedHelpers.findClass(className, Utils.application.classLoader),
            methodName,
            getDebugMethodHook(printMethods, printFields, printArgs, printTrace)
        )
    }

    @JvmStatic
    fun getDebugMethodHook(printMethods: Boolean, printFields: Boolean, printArgs: Boolean, printTrace: Boolean): XC_MethodHook {
        return object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                XposedBridge.log("-----------------HOOKED DEBUG START-----------------------------")
                XposedBridge.log("DEBUG CLASS: " + param.method.declaringClass.name + "->" + param.method.name + ": " + param.thisObject)

                if (printArgs) {
                    debugArgs(param.args)
                    XposedBridge.log("Return value: " + (param.result?.javaClass?.name ?: null) + " -> VALUE: " + param.result)
                }

                if (printFields) {
                    debugFields(param.thisObject?.javaClass ?: param.method.declaringClass, param.thisObject)
                }

                if (printMethods) {
                    debugMethods(param.thisObject?.javaClass ?: param.method.declaringClass, param.thisObject)
                }

                if (printTrace) {
                    for (trace in Thread.currentThread().stackTrace) {
                        XposedBridge.log("TRACE: " + trace.toString())
                    }
                }

                XposedBridge.log("-----------------HOOKED DEBUG END-----------------------------\n\n")
            }
        }
    }

    @JvmStatic
    fun debugArgs(args: Array<Any>) {
        for (i in args.indices) {
            XposedBridge.log("ARG[$i]: " + (args[i]?.javaClass?.name ?: null) + " -> VALUE: " + parseValue(args[i]))
        }
    }

    @JvmStatic
    fun parseValue(value: Any?): String {
        val sb = StringBuilder()
        if (value == null)
            return "null"
        when (value) {
            is List<*> -> {
                sb.append("List[")
                for (item in value) {
                    sb.append(parseValue(item)).append(", ")
                }
                sb.append("]")
            }
            is Map<*, *> -> {
                val keys = value.keys
                sb.append("Map[")
                for (key in keys) {
                    sb.append(key).append(": ").append(parseValue(value[key])).append(" ")
                }
                sb.append("]")
            }
            is ByteArray -> {
                try {
                    sb.append(String(value, StandardCharsets.UTF_8))
                } catch (_: Exception) {
                }
            }
            else -> {
                sb.append(value)
            }
        }
        return sb.toString()
    }

    @JvmStatic
    fun debugMethods(cls: Class<*>?, thisObject: Any?) {
        if (cls == null) return
        XposedBridge.log("DEBUG METHODS: Class " + cls.name)
        for (method in cls.declaredMethods) {
            if (method.parameterCount > 0 || method.returnType == Void.TYPE) continue
            try {
                method.isAccessible = true
                XposedBridge.log("METHOD: " + method.name + " -> VALUE: " + method.invoke(thisObject))
            } catch (_: Exception) {
            }
        }
    }

    @JvmStatic
    fun debugObject(srj: Any?) {
        if (srj == null) return
        XposedBridge.log("DEBUG OBJECT: " + srj.javaClass.name)
        debugFields(srj.javaClass, srj)
        debugMethods(srj.javaClass, srj)
    }
}
