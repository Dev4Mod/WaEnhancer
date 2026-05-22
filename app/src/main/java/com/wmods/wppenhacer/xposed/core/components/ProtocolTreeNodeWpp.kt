package com.wmods.wppenhacer.xposed.core.components

import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import de.robv.android.xposed.XposedBridge
import java.lang.reflect.Constructor
import java.lang.reflect.Field

@Suppress("UNCHECKED_CAST", "UNUSED")
class ProtocolTreeNodeWpp(val mInstance: Any) {

    init {
        if (!TYPE.isInstance(mInstance)) throw RuntimeException("object is not a ProtocolTreeNode")
    }

    companion object {
        lateinit var TYPE: Class<*>
        private lateinit var fieldTag: Field
        private lateinit var fieldData: Field
        private lateinit var fieldChildren: Field
        private lateinit var fieldAttributes: Field
        private lateinit var constructorFull: Constructor<*>

        @JvmStatic
        fun initialize(classLoader: ClassLoader) {
            try {
                TYPE = Unobfuscator.loadProtocolTreeNodeClass(classLoader)
                val keyValueClass = Unobfuscator.loadKeyValueClass(classLoader)
                KeyValueWpp.initialize(keyValueClass)

                // A00 -> String (Tag)
                fieldTag = TYPE.declaredFields.first { it.type == String::class.java }
                    .apply { isAccessible = true }

                // A01 -> byte[] (Data)
                fieldData = TYPE.declaredFields.first { it.type == ByteArray::class.java }
                    .apply { isAccessible = true }

                // A02 -> ProtocolTreeNode[] (Children)
                val selfArrayType = java.lang.reflect.Array.newInstance(TYPE, 0)::class.java
                fieldChildren = TYPE.declaredFields.first { it.type == selfArrayType }
                    .apply { isAccessible = true }

                // A03 -> KeyValue[] (Attributes)
                val keyValueArrayType =
                    java.lang.reflect.Array.newInstance(keyValueClass, 0)::class.java
                fieldAttributes = TYPE.declaredFields.first { it.type == keyValueArrayType }
                    .apply { isAccessible = true }

                // Construtor: (String, byte[], KeyValue[], ProtocolTreeNode[])
                constructorFull = TYPE.getDeclaredConstructor(
                    String::class.java,
                    ByteArray::class.java,
                    keyValueArrayType,
                    selfArrayType
                ).apply { isAccessible = true }

            } catch (e: Exception) {
                XposedBridge.log("ProtocolTreeNodeWpp Init Error: ${e.message}")
            }
        }

        fun create(
            tag: String,
            data: ByteArray?,
            attributes: List<KeyValueWpp>?,
            children: List<ProtocolTreeNodeWpp>?
        ): ProtocolTreeNodeWpp? {
            try {
                val attrArray = if (!attributes.isNullOrEmpty()) {
                    val arr = java.lang.reflect.Array.newInstance(
                        KeyValueWpp.TYPE,
                        attributes.size
                    ) as Array<Any>
                    for (i in attributes.indices) {
                        arr[i] = attributes[i].mInstance
                    }
                    arr
                } else null

                val childArray = if (!children.isNullOrEmpty()) {
                    val arr = java.lang.reflect.Array.newInstance(TYPE, children.size) as Array<Any>
                    for (i in children.indices) {
                        arr[i] = children[i].mInstance
                    }
                    arr
                } else null

                val instance = constructorFull.newInstance(tag, data, attrArray, childArray)
                return ProtocolTreeNodeWpp(instance)
            } catch (e: Exception) {
                XposedBridge.log(e)
                return null
            }
        }
    }

    val tag: String?
        get() = try {
            fieldTag.get(mInstance) as? String
        } catch (_: Exception) {
            null
        }

    val data: ByteArray?
        get() = try {
            fieldData.get(mInstance) as? ByteArray
        } catch (_: Exception) {
            null
        }

    val children: List<ProtocolTreeNodeWpp>
        get() {
            val arr = try {
                fieldChildren.get(mInstance) as? Array<*>
            } catch (_: Exception) {
                null
            } ?: return emptyList()
            return arr.filterNotNull().map { ProtocolTreeNodeWpp(it) }
        }

    val attributes: List<KeyValueWpp>
        get() {
            val arr = try {
                fieldAttributes.get(mInstance) as? Array<*>
            } catch (_: Exception) {
                null
            } ?: return emptyList()
            return arr.filterNotNull().map { KeyValueWpp(it) }
        }

    // ==========================================
    // Operações de Modificação de KeyValue
    // ==========================================

    /**
     * Adiciona um novo KeyValue ao array. Se já existirem chaves com o mesmo nome,
     * a nova será adicionada ao final, permitindo chaves duplicadas.
     */
    fun addKeyValue(key: String, value: String) {
        try {
            val currentAttrs = attributes.map { it.mInstance }.toMutableList()
            val newKv = KeyValueWpp.create(key, value) ?: return
            currentAttrs.add(newKv.mInstance)

            val newArray = java.lang.reflect.Array.newInstance(
                KeyValueWpp.TYPE,
                currentAttrs.size
            ) as Array<Any>
            for (i in currentAttrs.indices) {
                newArray[i] = currentAttrs[i]
            }
            fieldAttributes.set(mInstance, newArray)
        } catch (e: Exception) {
            XposedBridge.log(e)
        }
    }

    /**
     * Remove TODAS as ocorrências de um KeyValue correspondente à chave fornecida.
     */
    fun removeAllKeyValuesByKey(key: String) {
        try {
            val currentAttrs = attributes
            val filtered = currentAttrs.filter { it.key != key }.map { it.mInstance }

            val newArray =
                java.lang.reflect.Array.newInstance(KeyValueWpp.TYPE, filtered.size) as Array<Any>
            for (i in filtered.indices) {
                newArray[i] = filtered[i]
            }
            fieldAttributes.set(mInstance, newArray)
        } catch (e: Exception) {
            XposedBridge.log(e)
        }
    }

    /**
     * Remove uma instância específica de KeyValue.
     * Útil quando existem chaves duplicadas e você quer remover apenas uma delas.
     */
    fun removeKeyValue(keyValueToRemove: KeyValueWpp) {
        try {
            val currentAttrs = attributes
            // Filtra comparando a referência exata da instância (=== / !==)
            val filtered = currentAttrs.filter { it.mInstance !== keyValueToRemove.mInstance }
                .map { it.mInstance }

            val newArray =
                java.lang.reflect.Array.newInstance(KeyValueWpp.TYPE, filtered.size) as Array<Any>
            for (i in filtered.indices) {
                newArray[i] = filtered[i]
            }
            fieldAttributes.set(mInstance, newArray)
        } catch (e: Exception) {
            XposedBridge.log(e)
        }
    }

    /**
     * Modifica TODOS os valores de um KeyValue existente com a chave fornecida.
     * Caso existam duplicatas, todas assumirão o novo valor.
     */
    fun modifyKeyValue(key: String, newValue: String) {
        try {
            val currentAttrs = attributes
            val modifiedList = currentAttrs.map {
                if (it.key == key) {
                    KeyValueWpp.create(key, newValue)?.mInstance ?: it.mInstance
                } else {
                    it.mInstance
                }
            }

            val newArray = java.lang.reflect.Array.newInstance(
                KeyValueWpp.TYPE,
                modifiedList.size
            ) as Array<Any>
            for (i in modifiedList.indices) {
                newArray[i] = modifiedList[i]
            }
            fieldAttributes.set(mInstance, newArray)
        } catch (e: Exception) {
            XposedBridge.log(e)
        }
    }

    /**
     * Obtém o valor do primeiro KeyValue que corresponda à chave, se existir.
     */
    fun getFirstKeyValue(key: String): String? {
        return attributes.firstOrNull { it.key == key }?.value
    }

    class KeyValueWpp(val mInstance: Any) {

        init {
            if (!TYPE.isInstance(mInstance)) throw RuntimeException("object is not a KeyValue")
        }

        companion object {
            lateinit var TYPE: Class<*>
            private lateinit var fieldKey: Field
            private lateinit var fieldValue: Field
            private lateinit var fieldJid: Field
            private lateinit var constructorStringString: Constructor<*>


            fun initialize(keyValueClass: Class<*>) {
                try {
                    TYPE = keyValueClass

                    // A classe KeyValue possui exatamente dois campos String (key e A03 que é o value)
                    val stringFields = TYPE.declaredFields.filter { it.type == String::class.java }
                    if (stringFields.size >= 2) {
                        fieldKey = stringFields[0].apply { isAccessible = true }
                        fieldValue = stringFields[1].apply { isAccessible = true }
                    }

                    fieldJid = TYPE.declaredFields.first { it.type == FMessageWpp.UserJid.TYPE_JID }
                        .apply { isAccessible = true }

                    // Construtor (String, String) usado para criar novas instâncias simples
                    constructorStringString =
                        TYPE.getDeclaredConstructor(String::class.java, String::class.java).apply {
                            isAccessible = true
                        }
                } catch (e: Exception) {
                    XposedBridge.log("KeyValueWpp Init Error: ${e.message}")
                }
            }

            fun create(key: String, value: String): KeyValueWpp? {
                return try {
                    val instance = constructorStringString.newInstance(key, value)
                    KeyValueWpp(instance)
                } catch (e: Exception) {
                    XposedBridge.log(e)
                    null
                }
            }
        }

        var key: String?
            get() = try {
                fieldKey.get(mInstance) as? String
            } catch (e: Exception) {
                XposedBridge.log(e)
                null
            }
            set(value) = try {
                fieldKey.set(mInstance, value)
            } catch (e: Exception) {
                XposedBridge.log(e)
            }

        var value: String?
            get() = try {
                fieldValue.get(mInstance) as? String
            } catch (e: Exception) {
                XposedBridge.log(e)
                null
            }
            set(value) = try {
                fieldValue.set(mInstance, value)
            } catch (e: Exception) {
                XposedBridge.log(e)
            }

        var userJid: FMessageWpp.UserJid?
            get() = try {
                FMessageWpp.UserJid(FMessageWpp.UserJid(fieldJid.get(mInstance)).userRawString)
            } catch (e: Exception) {
                null
            }
            set(value) = try {
                fieldJid.set(mInstance, value?.userJid)
            } catch (e: Exception) {
                XposedBridge.log(e)
            }

        override fun toString(): String {
            return "KeyValueWpp(key=$key, value=$value)"
        }
    }


}