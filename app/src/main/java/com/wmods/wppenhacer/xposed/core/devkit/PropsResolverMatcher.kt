package com.wmods.wppenhacer.xposed.core.devkit

import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object PropsResolverMatcher {
    fun matches(method: Method, propsClass: Class<*>, contextType: Class<*>): Boolean {
        val types = method.parameterTypes
        if (method.returnType != Any::class.java || types.size < 4
            || types.first() != contextType
            || types[types.size - 2] != Int::class.javaPrimitiveType
            || types.last() != Boolean::class.javaPrimitiveType
        ) return false

        // 2.26.46 moved the resolver onto the props instance and removed the
        // explicit props argument. Keep the old static signature for earlier builds.
        return if (Modifier.isStatic(method.modifiers)) {
            types[1] == propsClass
        } else {
            types[1] == Int::class.javaObjectType
                // The host and module may load separate Kotlin runtimes.
                && types[2].name == Function1::class.java.name
        }
    }
}
