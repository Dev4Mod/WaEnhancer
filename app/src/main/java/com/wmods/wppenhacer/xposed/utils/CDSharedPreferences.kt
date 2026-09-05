package com.wmods.wppenhacer.xposed.utils

import android.content.SharedPreferences
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlSerializer
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * A custom implementation of [SharedPreferences] that reads from and writes to
 * an XML file located in any arbitrary directory.
 */
class CDSharedPreferences(private val xmlFile: File) : SharedPreferences {

    private val lock = Any()
    private var preferencesMap = mutableMapOf<String, Any?>()
    private val listeners = mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    init {
        loadData()
    }

    /**
     * Parses the SharedPreferences XML file format from the custom directory.
     */
    private fun loadData() {
        synchronized(lock) {
            if (!xmlFile.exists() || !xmlFile.isFile) return

            try {
                FileInputStream(xmlFile).use { inputStream ->
                    val parser = Xml.newPullParser()
                    parser.setInput(inputStream, "UTF-8")

                    var eventType = parser.eventType
                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        if (eventType == XmlPullParser.START_TAG) {
                            val tagName = parser.name
                            val key = parser.getAttributeValue(null, "name")

                            if (key != null) {
                                when (tagName) {
                                    "string" -> preferencesMap[key] = parser.nextText()
                                    "boolean" -> {
                                        val valueStr = parser.getAttributeValue(null, "value")
                                        preferencesMap[key] = valueStr?.toBoolean() ?: false
                                    }

                                    "int" -> {
                                        val valueStr = parser.getAttributeValue(null, "value")
                                        preferencesMap[key] = valueStr?.toIntOrNull() ?: 0
                                    }

                                    "long" -> {
                                        val valueStr = parser.getAttributeValue(null, "value")
                                        preferencesMap[key] = valueStr?.toLongOrNull() ?: 0L
                                    }

                                    "float" -> {
                                        val valueStr = parser.getAttributeValue(null, "value")
                                        preferencesMap[key] = valueStr?.toFloatOrNull() ?: 0f
                                    }
                                }
                            }
                        }
                        eventType = parser.next()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Saves the memory cache state back into the physical XML file.
     */
    private fun saveData(mapToSave: Map<String, Any?>) {
        synchronized(lock) {
            try {
                if (xmlFile.parentFile?.exists() == false) {
                    xmlFile.parentFile?.mkdirs()
                }

                FileOutputStream(xmlFile).use { outputStream ->
                    val serializer: XmlSerializer = Xml.newSerializer()
                    serializer.setOutput(outputStream, "UTF-8")
                    serializer.startDocument("UTF-8", true)
                    serializer.startTag(null, "map")

                    for ((key, value) in mapToSave) {
                        when (value) {
                            is String -> {
                                serializer.startTag(null, "string")
                                serializer.attribute(null, "name", key)
                                serializer.text(value)
                                serializer.endTag(null, "string")
                            }

                            is Boolean -> {
                                serializer.startTag(null, "boolean")
                                serializer.attribute(null, "name", key)
                                serializer.attribute(null, "value", value.toString())
                                serializer.endTag(null, "boolean")
                            }

                            is Int -> {
                                serializer.startTag(null, "int")
                                serializer.attribute(null, "name", key)
                                serializer.attribute(null, "value", value.toString())
                                serializer.endTag(null, "int")
                            }

                            is Long -> {
                                serializer.startTag(null, "long")
                                serializer.attribute(null, "name", key)
                                serializer.attribute(null, "value", value.toString())
                                serializer.endTag(null, "long")
                            }

                            is Float -> {
                                serializer.startTag(null, "float")
                                serializer.attribute(null, "name", key)
                                serializer.attribute(null, "value", value.toString())
                                serializer.endTag(null, "float")
                            }
                        }
                    }

                    serializer.endTag(null, "map")
                    serializer.endDocument()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // --- SharedPreferences Interface Methods ---

    override fun getAll(): Map<String, *> = synchronized(lock) { preferencesMap.toMap() }

    override fun getString(key: String, defValue: String?): String? = synchronized(lock) {
        preferencesMap[key] as? String ?: defValue
    }

    override fun getStringSet(
        p0: String?,
        p1: Set<String?>?
    ): Set<String?>? {
        TODO("Not yet implemented")
    }

    override fun getInt(key: String, defValue: Int): Int = synchronized(lock) {
        preferencesMap[key] as? Int ?: defValue
    }

    override fun getLong(key: String, defValue: Long): Long = synchronized(lock) {
        preferencesMap[key] as? Long ?: defValue
    }

    override fun getFloat(key: String, defValue: Float): Float = synchronized(lock) {
        preferencesMap[key] as? Float ?: defValue
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean = synchronized(lock) {
        preferencesMap[key] as? Boolean ?: defValue
    }

    override fun contains(key: String): Boolean = synchronized(lock) {
        preferencesMap.containsKey(key)
    }

    override fun edit(): SharedPreferences.Editor {
        return CustomEditor()
    }

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        synchronized(lock) { listeners.add(listener) }
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        synchronized(lock) { listeners.remove(listener) }
    }

    private inner class CustomEditor : SharedPreferences.Editor {
        private val localChanges = mutableMapOf<String, Any?>()
        private val keysToRemove = mutableSetOf<String>()
        private var clearAll = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            localChanges[key] = value
            keysToRemove.remove(key)
            return this
        }

        override fun putStringSet(
            p0: String?,
            p1: Set<String?>?
        ): SharedPreferences.Editor? {
            TODO("Not yet implemented")
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            localChanges[key] = value
            keysToRemove.remove(key)
            return this
        }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            localChanges[key] = value
            keysToRemove.remove(key)
            return this
        }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
            localChanges[key] = value
            keysToRemove.remove(key)
            return this
        }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            localChanges[key] = value
            keysToRemove.remove(key)
            return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            keysToRemove.add(key)
            localChanges.remove(key)
            return this
        }

        override fun apply() {
            TODO("Not yet implemented")
        }

        override fun clear(): SharedPreferences.Editor? {
            TODO("Not yet implemented")
        }

        override fun commit(): Boolean {
            TODO("Not yet implemented")
        }
    }
}