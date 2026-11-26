package com.wmods.wppenhacer.xposed.core.devkit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

class PropsResolverMatcherTest {
    @Test
    fun acceptsLegacyStaticAndNewInstanceResolversOnly() {
        val matches = Props::class.java.declaredMethods.filter {
            PropsResolverMatcher.matches(it, Props::class.java, Context::class.java)
        }.map { it.name }.toSet()

        assertEquals(setOf("legacy", "instance"), matches)
    }

    @Test
    fun acceptsResolverFromHostsOwnKotlinClassLoader() {
        val resources = javaClass.classLoader!!
        val fixturePrefix = javaClass.name + "$"
        val isolated = object : ClassLoader(resources) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> {
                if (!name.startsWith(fixturePrefix)
                    && name != "kotlin.jvm.functions.Function1" && name != "kotlin.Function"
                ) return super.loadClass(name, resolve)
                val loaded = findLoadedClass(name) ?: resources
                    .getResourceAsStream(name.replace('.', '/') + ".class")!!.use {
                        val bytes = it.readBytes()
                        defineClass(name, bytes, 0, bytes.size)
                    }
                if (resolve) resolveClass(loaded)
                return loaded
            }
        }
        val props = isolated.loadClass(Props::class.java.name)
        val context = isolated.loadClass(Context::class.java.name)
        val instance = props.declaredMethods.single { it.name == "instance" }
        assertNotSame(Function1::class.java, instance.parameterTypes[2])
        assertEquals(setOf("legacy", "instance"), props.declaredMethods.filter {
            PropsResolverMatcher.matches(it, props, context)
        }.map { it.name }.toSet())
    }

    class Context

    @Suppress("UNUSED_PARAMETER")
    class Props {
        fun instance(context: Context, type: Int?, reader: (Int) -> Any,
                     source: Any, id: Int, flag: Boolean): Any = source

        fun wrongContext(context: Any, type: Int?, reader: (Int) -> Any,
                         id: Int, flag: Boolean): Any = context

        fun wrongType(context: Context, type: String, reader: (Int) -> Any,
                      id: Int, flag: Boolean): Any = type

        fun wrongReader(context: Context, type: Int?, reader: Any,
                        id: Int, flag: Boolean): Any = reader

        fun wrongReturn(context: Context, type: Int?, reader: (Int) -> Any,
                        id: Int, flag: Boolean): Boolean = flag

        fun wrongTail(context: Context, type: Int?, reader: (Int) -> Any,
                      flag: Boolean, id: Int): Any = id

        companion object {
            @JvmStatic
            fun legacy(context: Context, props: Props, type: Int?,
                       id: Int, flag: Boolean): Any = props

            @JvmStatic
            fun unrelated(context: Context, props: Any, type: Int?,
                          id: Int, flag: Boolean): Any = props
        }
    }
}
