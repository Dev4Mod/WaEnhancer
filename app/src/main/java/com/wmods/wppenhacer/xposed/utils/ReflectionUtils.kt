package com.wmods.wppenhacer.xposed.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Pair
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Arrays
import java.util.function.Predicate
import java.util.stream.Collectors

@Suppress("unused")
object ReflectionUtils {

    private var cachePrefs: SharedPreferences? = null

    @JvmStatic
    fun initCache(context: Context) {
        if (cachePrefs == null) {
            cachePrefs = context.getSharedPreferences("UnobfuscatorCache", Context.MODE_PRIVATE)
        }
    }

    @JvmField
    val primitiveClasses: Map<String, Class<*>> = mapOf(
        "byte" to java.lang.Byte.TYPE,
        "short" to java.lang.Short.TYPE,
        "int" to java.lang.Integer.TYPE,
        "long" to java.lang.Long.TYPE,
        "float" to java.lang.Float.TYPE,
        "boolean" to java.lang.Boolean.TYPE
    )

    @JvmStatic
    fun findClass(className: String?, classLoader: ClassLoader): Class<*> {
        if (className == null) throw RuntimeException("Class name is null")
        val primitive = primitiveClasses[className]
        if (primitive != null) return primitive
        return XposedHelpers.findClass(className, classLoader)
    }

    @JvmStatic
    fun findMethodUsingFilter(clazz: Class<*>?, predicate: Predicate<Method>): Method {
        var current: Class<*>? = clazz
        while (current != null) {
            for (method in current.declaredMethods) {
                if (predicate.test(method)) return method
            }
            current = current.superclass
        }
        throw RuntimeException("Method not found")
    }

    @JvmStatic
    fun findAllMethodsUsingFilter(clazz: Class<*>?, predicate: Predicate<Method>): Array<Method> {
        var current: Class<*>? = clazz
        while (current != null) {
            val results = current.declaredMethods.filter { predicate.test(it) }
            if (results.isNotEmpty()) return results.toTypedArray()
            current = current.superclass
        }
        throw RuntimeException("Method not found")
    }

    @JvmStatic
    fun findFieldUsingFilter(clazz: Class<*>?, predicate: Predicate<Field>): Field {
        var current: Class<*>? = clazz
        while (current != null) {
            for (field in current.declaredFields) {
                if (predicate.test(field)) return field
            }
            current = current.superclass
        }
        throw RuntimeException("Field not found")
    }

    @JvmStatic
    fun findAllConstructorsUsingFilter(clazz: Class<*>?, predicate: Predicate<Constructor<*>>): Array<Constructor<*>> {
        var current: Class<*>? = clazz
        while (current != null) {
            val results = current.declaredConstructors.filter { predicate.test(it) }
            if (results.isNotEmpty()) return results.toTypedArray()
            current = current.superclass
        }
        return emptyArray()
    }

    @JvmStatic
    fun findConstructorUsingFilter(clazz: Class<*>?, predicate: Predicate<Constructor<*>>): Constructor<*> {
        var current: Class<*>? = clazz
        while (current != null) {
            for (constructor in current.declaredConstructors) {
                if (predicate.test(constructor)) return constructor
            }
            current = current.superclass
        }
        throw RuntimeException("Constructor not found")
    }

    @JvmStatic
    fun findAllFieldsUsingFilter(clazz: Class<*>?, predicate: Predicate<Field>): Array<Field> {
        var current: Class<*>? = clazz
        while (current != null) {
            val results = current.declaredFields.filter { predicate.test(it) }
            if (results.isNotEmpty()) return results.toTypedArray()
            current = current.superclass
        }
        return emptyArray()
    }


    @JvmStatic
    fun findMethodUsingFilterIfExists(clazz: Class<*>?, predicate: Predicate<Method>): Method? {
        var cls = clazz
        do {
            val results = Arrays.stream(cls!!.declaredMethods).filter(predicate).findFirst()
            if (results.isPresent) return results.get()
        } while ((cls.superclass.also { cls = it }) != null)
        return null
    }

    @JvmStatic
    fun findFieldUsingFilterIfExists(clazz: Class<*>?, predicate: Predicate<Field>): Field? {
        var cls = clazz
        do {
            val results = Arrays.stream(cls!!.declaredFields).filter(predicate).findFirst()
            if (results.isPresent) return results.get()
        } while ((cls.superclass.also { cls = it }) != null)
        return null
    }

    @JvmStatic
    fun isOverridden(method: Method?): Boolean {
        if (method == null) return false
        return try {
            val superclass = method.declaringClass.superclass ?: return false
            val parentMethod = superclass.getMethod(method.name, *method.parameterTypes)
            parentMethod != method
        } catch (_: NoSuchMethodException) {
            false
        }
    }


    @JvmStatic
    fun getFieldsByExtendType(cls: Class<*>, type: Class<*>?): List<Field> {
        if (type == null) return emptyList()
        return Arrays.stream(cls.fields).filter { f: Field -> type.isAssignableFrom(f.type) }.collect(Collectors.toList())
    }

    @JvmStatic
    fun getFieldsByType(cls: Class<*>, type: Class<*>?): List<Field> {
        if (type == null) return emptyList()
        return Arrays.stream(cls.fields).filter { f: Field -> type == f.type }.collect(Collectors.toList())
    }

    @JvmStatic
    fun getFieldByExtendType(cls: Class<*>?, className: String?): Field? {
        if (cls == null || className == null) return null
        return getFieldByExtendType(cls, findClass(className, cls.classLoader))
    }

    @JvmStatic
    fun getFieldByExtendType(cls: Class<*>?, type: Class<*>?): Field? {
        if (cls == null) return null
        val t = type ?: return null
        if (cachePrefs == null) {
            return Arrays.stream(cls.fields).filter { f: Field -> t.isAssignableFrom(f.type) }.findFirst().orElse(null)
        }

        val cacheKey = "field_cache_" + cls.name + "_" + t.name
        val cachedFieldName = cachePrefs?.getString(cacheKey, null)
        if (cachedFieldName != null) {
            try {
                return cls.getField(cachedFieldName)
            } catch (_: NoSuchFieldException) {
                cachePrefs?.edit()?.remove(cacheKey)?.commit()
            }
        }

        val field = Arrays.stream(cls.fields).filter { f: Field -> type.isAssignableFrom(f.type) }.findFirst().orElse(null)

        if (field != null && field.declaringClass == cls) {
            cachePrefs?.edit()?.putString(cacheKey, field.name)?.commit()
        }

        return field
    }

    @JvmStatic
    fun getFieldByType(cls: Class<*>?, className: String?): Field? {
        if (cls == null || className == null) return null
        return getFieldByType(cls, findClass(className, cls.classLoader))
    }


    @JvmStatic
    fun getFieldByType(cls: Class<*>?, type: Class<*>?): Field? {
        if (cls == null) return null
        val t = type ?: return null
        if (cachePrefs == null) {
            return Arrays.stream(cls.fields).filter { f: Field -> t == f.type }.findFirst().orElse(null)
        }

        val cacheKey = "field_cache_direct_" + cls.name + "_" + t.name
        val cachedFieldName = cachePrefs?.getString(cacheKey, null)
        if (cachedFieldName != null) {
            try {
                return cls.getField(cachedFieldName)
            } catch (_: NoSuchFieldException) {
                cachePrefs?.edit()?.remove(cacheKey)?.apply()
            }
        }

        val field = Arrays.stream(cls.fields).filter { f: Field -> type == f.type }.findFirst().orElse(null)

        if (field != null && field.declaringClass == cls) {
            cachePrefs?.edit()?.putString(cacheKey, field.name)?.apply()
        }

        return field
    }

    @JvmStatic
    fun callMethod(method: Method?, instance: Any?, vararg args: Any?): Any? {
        if (method == null) return null
        return try {
            var actualArgs = args
            val count = method.parameterCount
            if (count != args.size) {
                val newargs = initArray(method.parameterTypes)
                System.arraycopy(args, 0, newargs, 0, minOf(args.size, count))
                actualArgs = newargs
            }
            method.invoke(instance, *actualArgs)
        } catch (_: Exception) {
            null
        }
    }

    @JvmStatic
    fun initArray(parameterTypes: Array<Class<*>>): Array<Any?> {
        val args = arrayOfNulls<Any>(parameterTypes.size)
        for (i in parameterTypes.indices) {
            args[i] = getDefaultValue(parameterTypes[i])
        }
        return args
    }

    @JvmStatic
    fun getDefaultValue(paramType: Class<*>?): Any? {
        return when (paramType) {
            Int::class.java, Int::class.javaObjectType -> 0
            Long::class.java, Long::class.javaObjectType -> 0L
            Double::class.java, Double::class.javaObjectType -> 0.0
            Boolean::class.java, Boolean::class.javaObjectType -> false
            else -> null
        }
    }

    @JvmStatic
    fun getObjectField(field: Field?, thisObject: Any?): Any? {
        if (field == null) return null
        return try {
            field[thisObject]
        } catch (_: Exception) {
            null
        }
    }

    @JvmStatic
    fun findIndexOfType(args: Array<out Any?>, type: Class<*>): Int {
        for (i in args.indices) {
            val arg = args[i] ?: continue
            if (arg is Class<*>) {
                if (type.isAssignableFrom(arg)) return i
                continue
            }
            if (type.isInstance(arg)) return i
        }
        return -1
    }

    @JvmStatic
    fun <T> findInstancesOfType(args: Array<out Any?>, type: Class<T>): List<Pair<Int, T>> {
        val result = ArrayList<Pair<Int, T>>()
        for (i in args.indices) {
            val arg = args[i]
            if (arg == null || arg is Class<*>) continue
            if (type.isInstance(arg)) {
                @Suppress("UNCHECKED_CAST")
                result.add(Pair(i, type.cast(arg)))
            }
        }
        return result
    }

    @JvmStatic
    fun <T> findClassesOfType(args: Array<out Class<*>>, type: Class<T>): List<Pair<Int, Class<out T>>> {
        val result = ArrayList<Pair<Int, Class<out T>>>()
        for (i in args.indices) {
            val arg = args[i]
            if (type.isAssignableFrom(arg)) {
                @Suppress("UNCHECKED_CAST")
                result.add(Pair(i, arg as Class<out T>))
            }
        }
        return result
    }

    @JvmStatic
    fun <T> getArg(args: Array<out Any?>, typeClass: Class<T>, index: Int): T? {
        val list = findInstancesOfType(args, typeClass)
        if (list.isEmpty()) return null
        if (index == -1) return list[list.size - 1].second
        if (index < list.size) return list[index].second
        return null
    }

    @JvmStatic
    fun isCalledFromStrings(vararg fragments: String): Boolean {
        for (fragment in fragments) {
            require(fragment != null && fragment.trim().isNotEmpty()) { "Stack trace fragments must not be blank." }
        }

        val trace = Throwable().stackTrace
        val limit = minOf(trace.size, 20)

        for (i in 2 until limit) {
            val frame = trace[i]
            val className = frame.className
            val methodName = frame.methodName

            for (fragment in fragments) {
                if (className.contains(fragment) || methodName.contains(fragment)) {
                    return true
                }
            }
        }

        return false
    }

    @JvmStatic
    fun isClassSimpleNameString(aClass: Class<*>?, s: String?): Boolean {
        if (aClass == null || s == null) return false
        try {
            var cls: Class<*>? = aClass
            do {
                if (cls!!.simpleName == s) return true
                if (cls.name.startsWith("android.widget.") || cls.name.startsWith("android.view."))
                    return false
            } while (cls.also { cls = it.superclass } != null)
        } catch (_: Exception) {
        }
        return false
    }

    @JvmStatic
    fun isCalledFromClass(cls: Class<*>?): Boolean {
        val className = cls?.name ?: return false
        val stacks = Throwable().stackTrace

        for (i in 2 until stacks.size) {
            if (stacks[i].className == className) {
                return true
            }
        }

        return false
    }

    @JvmStatic
    fun isCalledFromMethod(method: Method?): Boolean {
        if (method == null) return false
        val declaringClassName = method.declaringClass.name
        val methodName = method.name
        val stacks = Throwable().stackTrace

        for (i in 2 until stacks.size) {
            if (stacks[i].className == declaringClassName && stacks[i].methodName == methodName) {
                return true
            }
        }

        return false
    }


    @JvmStatic
    fun setObjectField(field: Field?, instance: Any?, value: Any?) {
        if (field == null) return
        try {
            field[instance] = value
        } catch (_: Exception) {
        }
    }
}
