package com.wmods.wppenhacer.xposed.utils

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.param.HookParam
import com.highcapable.yukihookapi.hook.param.PackageParam
import java.nio.charset.StandardCharsets

object DebugUtils {

    @JvmStatic
    fun debugFields(cls: Class<*>?, thisObject: Any?) {
        if (cls == null) return
        YukiLog.log("------------------------------------")
        YukiLog.log("DEBUG FIELDS: Class " + cls.name + " -> Object " + thisObject)
        for (field in cls.declaredFields) {
            try {
                field.isAccessible = true
                val name = field.name
                var value = field[thisObject]
                if (value != null && value.javaClass.isArray) {
                    value = (value as Array<*>).contentToString()
                }
                YukiLog.log("FIELD: $name -> TYPE: ${field.type.name} -> VALUE: $value")
            } catch (_: Exception) {
            }
        }
    }

    @JvmStatic
    fun debugAllMethods(
        packageParam: PackageParam,
        className: String,
        methodName: String,
        printMethods: Boolean,
        printFields: Boolean,
        printArgs: Boolean,
        printTrace: Boolean
    ) {
        packageParam.apply {
            ReflectionUtils.findClass(className, Utils.application.classLoader).resolve().method {
                name = methodName
            }.hookAll {
                after {
                    logHookDebug(this, printMethods, printFields, printArgs, printTrace)
                }
            }
        }
    }

    @JvmStatic
    fun logHookDebug(
        param: HookParam,
        printMethods: Boolean,
        printFields: Boolean,
        printArgs: Boolean,
        printTrace: Boolean
    ) {
        val thisObject = param.instanceOrNull
        YukiLog.log("-----------------HOOKED DEBUG START-----------------------------")
        YukiLog.log("DEBUG CLASS: " + param.method.declaringClass.name + "->" + param.method.name + ": " + thisObject)

        if (printArgs) {
            @Suppress("UNCHECKED_CAST")
            debugArgs(param.args as Array<Any>)
            YukiLog.log(
                "Return value: " + (param.result?.javaClass?.name
                    ?: null) + " -> VALUE: " + param.result
            )
        }

        if (printFields) {
            debugFields(thisObject?.javaClass ?: param.method.declaringClass, thisObject)
        }

        if (printMethods) {
            debugMethods(thisObject?.javaClass ?: param.method.declaringClass, thisObject)
        }

        if (printTrace) {
            for (trace in Thread.currentThread().stackTrace) {
                YukiLog.log("TRACE: " + trace.toString())
            }
        }

        YukiLog.log("-----------------HOOKED DEBUG END-----------------------------\n\n")
    }

    @JvmStatic
    fun debugArgs(args: Array<Any>) {
        for (i in args.indices) {
            YukiLog.log(
                "ARG[$i]: " + (args[i]?.javaClass?.name
                    ?: null) + " -> VALUE: " + parseValue(args[i])
            )
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
        YukiLog.log("DEBUG METHODS: Class " + cls.name)
        for (method in cls.declaredMethods) {
            if (method.parameterCount > 0 || method.returnType == Void.TYPE) continue
            try {
                method.isAccessible = true
                YukiLog.log("METHOD: " + method.name + " -> VALUE: " + method.invoke(thisObject))
            } catch (_: Exception) {
            }
        }
    }

    @JvmStatic
    fun debugObject(srj: Any?) {
        if (srj == null) return
        YukiLog.log("DEBUG OBJECT: " + srj.javaClass.name)
        debugFields(srj.javaClass, srj)
        debugMethods(srj.javaClass, srj)
    }
}
