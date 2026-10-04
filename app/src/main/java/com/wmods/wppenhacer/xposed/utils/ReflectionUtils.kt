package com.wmods.wppenhacer.xposed.utils

import android.content.Context
import android.util.Pair
import com.wmods.wppenhacer.xposed.core.datastore.UnobfuscatorCacheDataStore
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Arrays
import java.util.Collections
import java.util.WeakHashMap
import java.util.function.Predicate
import java.util.stream.Collectors

@Suppress("unused")
object ReflectionUtils {

    private const val REFLECTION_NAMESPACE = UnobfuscatorCacheDataStore.NAMESPACE_REFLECTION

    private var cacheStore: UnobfuscatorCacheDataStore? = null

    @JvmStatic
    fun initCache(context: Context) {
        if (cacheStore == null) {
            cacheStore = UnobfuscatorCacheDataStore.getInstance(context)
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
        return try {
            Class.forName(className, false, classLoader)
        } catch (e: ClassNotFoundException) {
            throw ClassNotFoundException("Class not found: $className", e)
        }
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
    fun findAllConstructorsUsingFilter(
        clazz: Class<*>?,
        predicate: Predicate<Constructor<*>>
    ): Array<Constructor<*>> {
        var current: Class<*>? = clazz
        while (current != null) {
            val results = current.declaredConstructors.filter { predicate.test(it) }
            if (results.isNotEmpty()) return results.toTypedArray()
            current = current.superclass
        }
        return emptyArray()
    }

    @JvmStatic
    fun findConstructorUsingFilter(
        clazz: Class<*>?,
        predicate: Predicate<Constructor<*>>
    ): Constructor<*> {
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
        return Arrays.stream(cls.fields).filter { f: Field -> type.isAssignableFrom(f.type) }
            .collect(Collectors.toList())
    }

    @JvmStatic
    fun getFieldsByType(cls: Class<*>, type: Class<*>?): List<Field> {
        if (type == null) return emptyList()
        return Arrays.stream(cls.fields).filter { f: Field -> type == f.type }
            .collect(Collectors.toList())
    }

    @JvmStatic
    fun getFieldByExtendType(cls: Class<*>?, className: String?): Field? {
        if (cls == null || className == null) return null
        return getFieldByExtendType(cls, findClass(className, cls.classLoader!!))
    }

    @JvmStatic
    fun getFieldByExtendType(cls: Class<*>?, type: Class<*>?): Field? {
        if (cls == null) return null
        val t = type ?: return null
        val store = cacheStore
        if (store == null) {
            return Arrays.stream(cls.fields).filter { f: Field -> t.isAssignableFrom(f.type) }
                .findFirst().orElse(null)
        }

        val cacheKey = "field_cache_" + cls.name + "_" + t.name
        val cachedFieldName = store.getString(REFLECTION_NAMESPACE, cacheKey, null)
        if (cachedFieldName != null) {
            try {
                return cls.getField(cachedFieldName)
            } catch (_: NoSuchFieldException) {
                store.remove(REFLECTION_NAMESPACE, cacheKey)
            }
        }

        val field = Arrays.stream(cls.fields).filter { f: Field -> type.isAssignableFrom(f.type) }
            .findFirst().orElse(null)

        if (field != null && field.declaringClass == cls) {
            store.putString(REFLECTION_NAMESPACE, cacheKey, field.name)
        }

        return field
    }

    @JvmStatic
    fun getFieldByType(cls: Class<*>?, className: String?): Field? {
        if (cls == null || className == null) return null
        return getFieldByType(cls, findClass(className, cls.classLoader!!))
    }


    @JvmStatic
    fun getFieldByType(cls: Class<*>?, type: Class<*>?): Field? {
        if (cls == null) return null
        val t = type ?: return null
        val store = cacheStore
        if (store == null) {
            return Arrays.stream(cls.fields).filter { f: Field -> t == f.type }.findFirst()
                .orElse(null)
        }

        val cacheKey = "field_cache_direct_" + cls.name + "_" + t.name
        val cachedFieldName = store.getString(REFLECTION_NAMESPACE, cacheKey, null)
        if (cachedFieldName != null) {
            try {
                return cls.getField(cachedFieldName)
            } catch (_: NoSuchFieldException) {
                store.remove(REFLECTION_NAMESPACE, cacheKey)
            }
        }

        val field =
            Arrays.stream(cls.fields).filter { f: Field -> type == f.type }.findFirst().orElse(null)

        if (field != null && field.declaringClass == cls) {
            store.putString(REFLECTION_NAMESPACE, cacheKey, field.name)
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

    fun findIndexOfType(args: Array<out Any?>, type: Class<*>): Int {
        val targetType = when (type) {
            java.lang.Float.TYPE -> java.lang.Float::class.java
            java.lang.Integer.TYPE -> java.lang.Integer::class.java
            java.lang.Long.TYPE -> java.lang.Long::class.java
            java.lang.Double.TYPE -> java.lang.Double::class.java
            java.lang.Boolean.TYPE -> java.lang.Boolean::class.java
            java.lang.Byte.TYPE -> java.lang.Byte::class.java
            java.lang.Character.TYPE -> java.lang.Character::class.java
            java.lang.Short.TYPE -> java.lang.Short::class.java
            else -> type
        }
        for (i in args.indices) {
            val arg = args[i] ?: continue
            if (arg is Class<*>) {
                if (targetType.isAssignableFrom(arg) || type.isAssignableFrom(arg)) return i
                continue
            }
            if (targetType.isInstance(arg) || type.isInstance(arg)) return i
        }
        return -1
    }

    @JvmStatic
    fun <T> findInstancesOfType(args: Array<Any?>, type: Class<T>): List<Pair<Int, T>> {
        val result = mutableListOf<Pair<Int, T>>()
        for (i in args.indices) {
            val arg = args[i]
            if (arg == null || arg is Class<*>) continue
            if (type.isInstance(arg)) {
                result.add(Pair(i, type.cast(arg)!!))
            }
        }
        return result
    }

    @JvmStatic
    fun <T> findClassesOfType(
        args: Array<out Class<*>>,
        type: Class<T>
    ): List<Pair<Int, Class<out T>>> {
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
    fun <T> getArg(args: Array<Any?>, typeClass: Class<T>, i: Int): T? {
        val list = findInstancesOfType(args, typeClass)
        return if (list.size <= i) null else list[i].second
    }

    @JvmStatic
    fun isCalledFromStrings(vararg fragments: String): Boolean {
        for (fragment in fragments) {
            require(fragment.trim().isNotEmpty()) { "Stack trace fragments must not be blank." }
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
            @Suppress("SENSELESS_COMPARISON")
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

    // ---- Reflection helpers used by hooks (YukiHookAPI migration) ----
    private val additionalFields =
        Collections.synchronizedMap(WeakHashMap<Any, MutableMap<String, Any?>>())

    @JvmStatic
    fun getObjectField(instance: Any?, name: String?): Any? {
        if (instance == null || name == null) return null
        return findFieldRecursive(instance.javaClass, name).get(instance)
    }

    fun setObjectFromField(field: Field, instance: Any?, value: Any?) {
        try {
            field.set(instance, value)
        } catch (ignored: Exception) {
        }
    }

    fun getLongField(instance: Any?, name: String?): Long {
        if (instance == null || name == null) throw NullPointerException("instance and name must not be null")
        return findFieldRecursive(instance.javaClass, name).getLong(instance)
    }

    fun getBooleanField(instance: Any?, name: String?): Boolean {
        if (instance == null || name == null) throw NullPointerException("instance and name must not be null")
        return findFieldRecursive(instance.javaClass, name).getBoolean(instance)
    }

    fun getByteField(instance: Any?, name: String?): Byte {
        if (instance == null || name == null) throw NullPointerException("instance and name must not be null")
        return findFieldRecursive(instance.javaClass, name).getByte(instance)
    }

    fun getCharField(instance: Any?, name: String?): Char {
        if (instance == null || name == null) throw NullPointerException("instance and name must not be null")
        return findFieldRecursive(instance.javaClass, name).getChar(instance)
    }

    fun getShortField(instance: Any?, name: String?): Short {
        if (instance == null || name == null) throw NullPointerException("instance and name must not be null")
        return findFieldRecursive(instance.javaClass, name).getShort(instance)
    }

    fun getIntField(instance: Any?, name: String?): Int {
        if (instance == null || name == null) throw NullPointerException("instance and name must not be null")
        return findFieldRecursive(instance.javaClass, name).getInt(instance)
    }

    fun getFloatField(instance: Any?, name: String?): Float {
        if (instance == null || name == null) throw NullPointerException("instance and name must not be null")
        return findFieldRecursive(instance.javaClass, name).getFloat(instance)
    }

    fun getDoubleField(instance: Any?, name: String?): Double {
        if (instance == null || name == null) throw NullPointerException("instance and name must not be null")
        return findFieldRecursive(instance.javaClass, name).getDouble(instance)
    }

    fun getStaticObjectField(clazz: Class<*>, fieldName: String): Any? {
        return findFieldRecursive(clazz, fieldName).get(null)
    }

    fun getStaticBooleanField(clazz: Class<*>, fieldName: String): Boolean {
        return findFieldRecursive(clazz, fieldName).getBoolean(null)
    }

    fun getStaticByteField(clazz: Class<*>, fieldName: String): Byte {
        return findFieldRecursive(clazz, fieldName).getByte(null)
    }

    fun getStaticCharField(clazz: Class<*>, fieldName: String): Char {
        return findFieldRecursive(clazz, fieldName).getChar(null)
    }

    fun getStaticShortField(clazz: Class<*>, fieldName: String): Short {
        return findFieldRecursive(clazz, fieldName).getShort(null)
    }

    fun getStaticIntField(clazz: Class<*>, fieldName: String): Int {
        return findFieldRecursive(clazz, fieldName).getInt(null)
    }

    fun getStaticLongField(clazz: Class<*>, fieldName: String): Long {
        return findFieldRecursive(clazz, fieldName).getLong(null)
    }

    fun getStaticFloatField(clazz: Class<*>, fieldName: String): Float {
        return findFieldRecursive(clazz, fieldName).getFloat(null)
    }

    fun getStaticDoubleField(clazz: Class<*>, fieldName: String): Double {
        return findFieldRecursive(clazz, fieldName).getDouble(null)
    }

    fun setObjectField(instance: Any?, fieldName: String, obj: Any?) {
        if (instance == null) throw NullPointerException("instance must not be null")
        findFieldRecursive(instance.javaClass, fieldName).set(instance, obj)
    }

    fun setIntField(instance: Any?, fieldName: String, obj: Int) {
        if (instance == null) throw NullPointerException("instance must not be null")
        findFieldRecursive(instance.javaClass, fieldName).setInt(instance, obj)
    }

    fun setBooleanField(instance: Any?, fieldName: String, value: Boolean) {
        if (instance == null) throw NullPointerException("instance must not be null")
        findFieldRecursive(instance.javaClass, fieldName).setBoolean(instance, value)
    }

    fun setByteField(instance: Any?, fieldName: String, value: Byte) {
        if (instance == null) throw NullPointerException("instance must not be null")
        findFieldRecursive(instance.javaClass, fieldName).setByte(instance, value)
    }

    fun setCharField(instance: Any?, fieldName: String, value: Char) {
        if (instance == null) throw NullPointerException("instance must not be null")
        findFieldRecursive(instance.javaClass, fieldName).setChar(instance, value)
    }

    fun setShortField(instance: Any?, fieldName: String, value: Short) {
        if (instance == null) throw NullPointerException("instance must not be null")
        findFieldRecursive(instance.javaClass, fieldName).setShort(instance, value)
    }

    fun setLongField(instance: Any?, fieldName: String, value: Long) {
        if (instance == null) throw NullPointerException("instance must not be null")
        findFieldRecursive(instance.javaClass, fieldName).setLong(instance, value)
    }

    fun setFloatField(instance: Any?, fieldName: String, value: Float) {
        if (instance == null) throw NullPointerException("instance must not be null")
        findFieldRecursive(instance.javaClass, fieldName).setFloat(instance, value)
    }

    fun setDoubleField(instance: Any?, fieldName: String, value: Double) {
        if (instance == null) throw NullPointerException("instance must not be null")
        findFieldRecursive(instance.javaClass, fieldName).setDouble(instance, value)
    }

    fun callMethod(instance: Any?, methodName: String?, vararg args: Any?): Any? {
        if (instance == null || methodName == null) throw NullPointerException("instance and methodName must not be null")
        val method = findMethodBestMatchByArgs(instance.javaClass, methodName, args)
        return method.invoke(instance, *args)
    }

    fun callStaticMethod(clazz: Class<*>?, methodName: String?, vararg args: Any?): Any? {
        if (clazz == null || methodName == null) throw NullPointerException("clazz and methodName must not be null")
        val method = findMethodBestMatchByArgs(clazz, methodName, args)
        return method.invoke(null, *args)
    }

    fun getAdditionalInstanceField(instance: Any, key: String): Any? {
        return synchronized(additionalFields) {
            additionalFields[instance]?.get(key)
        }
    }

    fun setAdditionalInstanceField(instance: Any, key: String, value: Any?) {
        synchronized(additionalFields) {
            val fields = additionalFields.getOrPut(instance) { HashMap() }
            fields[key] = value
        }
    }

    fun removeAdditionalInstanceField(instance: Any, key: String) {
        synchronized(additionalFields) {
            val fields = additionalFields[instance] ?: return
            fields.remove(key)
            if (fields.isEmpty()) {
                additionalFields.remove(instance)
            }
        }
    }

    fun newInstance(clazz: Class<*>, vararg args: Any?): Any {
        val constructor = findConstructorBestMatch(clazz, args)
        return constructor.newInstance(*args)
    }

    fun setStaticIntField(klass: Class<*>, fieldName: String, value: Int) {
        findFieldRecursive(klass, fieldName).setInt(null, value)
    }

    fun setStaticObjectField(klass: Class<*>, fieldName: String, value: Any?) {
        findFieldRecursive(klass, fieldName).set(null, value)
    }

    fun setStaticBooleanField(klass: Class<*>, fieldName: String, value: Boolean) {
        findFieldRecursive(klass, fieldName).setBoolean(null, value)
    }

    fun setStaticByteField(klass: Class<*>, fieldName: String, value: Byte) {
        findFieldRecursive(klass, fieldName).setByte(null, value)
    }

    fun setStaticCharField(klass: Class<*>, fieldName: String, value: Char) {
        findFieldRecursive(klass, fieldName).setChar(null, value)
    }

    fun setStaticShortField(klass: Class<*>, fieldName: String, value: Short) {
        findFieldRecursive(klass, fieldName).setShort(null, value)
    }

    fun setStaticLongField(klass: Class<*>, fieldName: String, value: Long) {
        findFieldRecursive(klass, fieldName).setLong(null, value)
    }

    fun setStaticFloatField(klass: Class<*>, fieldName: String, value: Float) {
        findFieldRecursive(klass, fieldName).setFloat(null, value)
    }

    fun setStaticDoubleField(klass: Class<*>, fieldName: String, value: Double) {
        findFieldRecursive(klass, fieldName).setDouble(null, value)
    }

    private fun findFieldRecursive(clazz: Class<*>, fieldName: String): Field {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName).apply { isAccessible = true }
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        throw NoSuchFieldException("Field $fieldName not found in ${clazz.name}")
    }

    private fun findMethodBestMatchByArgs(
        clazz: Class<*>,
        methodName: String,
        args: Array<out Any?>
    ): Method {
        val candidates = mutableListOf<Method>()
        var current: Class<*>? = clazz
        while (current != null) {
            current.declaredMethods
                .filterTo(candidates) { it.name == methodName && it.parameterCount == args.size }
            current = current.superclass
        }
        val best = candidates
            .filter { isParameterTypesCompatible(it.parameterTypes, args) }
            .minByOrNull { getMatchScore(it.parameterTypes, args) }
            ?: throw NoSuchMethodException("Method $methodName(${args.size} args) not found in ${clazz.name}")
        best.isAccessible = true
        return best
    }

    private fun findConstructorBestMatch(clazz: Class<*>, args: Array<out Any?>): Constructor<*> {
        val best = clazz.declaredConstructors
            .filter { it.parameterCount == args.size }
            .filter { isParameterTypesCompatible(it.parameterTypes, args) }
            .minByOrNull { getMatchScore(it.parameterTypes, args) }
            ?: throw NoSuchMethodException("Constructor (${args.size} args) not found in ${clazz.name}")
        best.isAccessible = true
        return best
    }

    private fun isParameterTypesCompatible(
        parameterTypes: Array<Class<*>>,
        args: Array<out Any?>
    ): Boolean {
        return parameterTypes.indices.all { index ->
            isAssignable(
                parameterTypes[index],
                args[index]
            )
        }
    }

    private fun getMatchScore(parameterTypes: Array<Class<*>>, args: Array<out Any?>): Int {
        var score = 0
        for (i in parameterTypes.indices) {
            val arg = args[i] ?: continue
            val argClass = arg.javaClass
            val targetType = wrapPrimitive(parameterTypes[i])
            if (targetType != argClass) {
                score += 1
                if (!targetType.isAssignableFrom(argClass)) {
                    score += 10
                }
            }
        }
        return score
    }

    private fun isAssignable(targetType: Class<*>, arg: Any?): Boolean {
        if (arg == null) return !targetType.isPrimitive
        return wrapPrimitive(targetType).isAssignableFrom(arg.javaClass)
    }

    private fun wrapPrimitive(type: Class<*>): Class<*> {
        if (!type.isPrimitive) return type
        return when (type) {
            java.lang.Boolean.TYPE -> Boolean::class.javaObjectType
            java.lang.Byte.TYPE -> Byte::class.javaObjectType
            java.lang.Character.TYPE -> Char::class.javaObjectType
            java.lang.Short.TYPE -> Short::class.javaObjectType
            java.lang.Integer.TYPE -> Int::class.javaObjectType
            java.lang.Long.TYPE -> Long::class.javaObjectType
            java.lang.Float.TYPE -> Float::class.javaObjectType
            java.lang.Double.TYPE -> Double::class.javaObjectType
            java.lang.Void.TYPE -> Void::class.java
            else -> type
        }
    }

    /**
     * Finds the method that best matches the given parameter types, searching superclasses.
     */
    @JvmStatic
    fun findMethodBestMatch(
        clazz: Class<*>,
        methodName: String,
        vararg parameterTypes: Class<*>?
    ): Method {
        val candidates = mutableListOf<Method>()
        var current: Class<*>? = clazz
        while (current != null) {
            current.declaredMethods
                .filterTo(candidates) { it.name == methodName && it.parameterCount == parameterTypes.size }
            current = current.superclass
        }
        val exact = candidates.firstOrNull { it.parameterTypes.contentEquals(parameterTypes) }
        val best = exact ?: candidates
            .filter { m ->
                m.parameterTypes.indices.all { i ->
                    val given = parameterTypes[i]
                    given == null || wrapPrimitive(m.parameterTypes[i]).isAssignableFrom(
                        wrapPrimitive(given)
                    )
                }
            }
            .firstOrNull()
        ?: throw NoSuchMethodException("Method $methodName not found in ${clazz.name}")
        best.isAccessible = true
        return best
    }

    /** Finds a field by name, searching superclasses. */
    @JvmStatic
    fun findField(clazz: Class<*>, fieldName: String): Field = findFieldRecursive(clazz, fieldName)

    /** Finds a method by exact parameter types, searching superclasses. */
    @JvmStatic
    fun findMethodExact(
        clazz: Class<*>,
        methodName: String,
        vararg parameterTypes: Class<*>
    ): Method {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                return current.getDeclaredMethod(methodName, *parameterTypes)
                    .apply { isAccessible = true }
            } catch (_: NoSuchMethodException) {
                current = current.superclass
            }
        }
        throw NoSuchMethodException("Method $methodName not found in ${clazz.name}")
    }

    /** Finds a constructor by exact parameter types. */
    @JvmStatic
    fun findConstructorExact(clazz: Class<*>, vararg parameterTypes: Class<*>): Constructor<*> {
        return clazz.getDeclaredConstructor(*parameterTypes).apply { isAccessible = true }
    }

    /** Finds a class by name, or returns null if it does not exist. */
    @JvmStatic
    fun findClassIfExists(className: String, classLoader: ClassLoader?): Class<*>? {
        return try {
            Class.forName(className, false, classLoader ?: ClassLoader.getSystemClassLoader())
        } catch (_: ClassNotFoundException) {
            null
        }
    }
}
