package com.wmods.wppenhacer.xposed.core.devkit

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.hardware.SensorEventListener
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.enums.StringMatchType
import org.luckypray.dexkit.query.matchers.base.OpCodesMatcher
import org.luckypray.dexkit.result.ClassData
import org.luckypray.dexkit.result.MethodData
import org.luckypray.dexkit.util.DexSignUtil
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Arrays
import java.util.Collections
import java.util.Date
import java.util.Objects
import java.util.Timer
import java.util.TimerTask
import java.util.stream.Collectors

object Unobfuscator {

    private lateinit var bridge: DexKitBridge

    val cacheClasses = HashMap<String, Class<*>>()

    init {
        System.loadLibrary("dexkit")
    }

    @JvmStatic
    fun initWithPath(path: String): Boolean {
        return try {
            bridge = DexKitBridge.create(path)
            true
        } catch (_: Exception) {
            false
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun findFirstMethodUsingStrings(
        classLoader: ClassLoader,
        type: StringMatchType,
        vararg strings: String
    ): Method? {
        val result = bridge.findMethod {
            matcher {
                for (string in strings) {
                    addUsingString(string, type)
                }
            }
        }
        if (result.isEmpty()) return null
        for (methodData in result) {
            if (methodData.isMethod) return methodData.getMethodInstance(classLoader)
        }
        return null
    }

    @Throws(Exception::class)
    @JvmStatic
    fun findFirstMethodUsingStringsFilter(
        classLoader: ClassLoader,
        packageFilter: String,
        type: StringMatchType,
        vararg strings: String
    ): Method? {
        val result = bridge.findMethod {
            searchPackages(packageFilter)
            matcher {
                for (string in strings) {
                    addUsingString(string, type)
                }
            }
        }
        if (result.isEmpty()) return null

        for (methodData in result) {
            if (methodData.isMethod) return methodData.getMethodInstance(classLoader)
        }
        throw NoSuchMethodException()
    }

    @JvmStatic
    fun findAllMethodUsingStrings(
        classLoader: ClassLoader,
        type: StringMatchType,
        vararg strings: String
    ): Array<Method> {
        val result = bridge.findMethod {
            matcher {
                for (string in strings) {
                    addUsingString(string, type)
                }
            }
        }
        if (result.isEmpty()) return emptyArray()
        return result.stream().filter { it.isMethod }
            .map { methodData -> convertRealMethod(methodData, classLoader) }
            .filter { it != null }
            .map { it!! }
            .toArray { length -> arrayOfNulls<Method>(length) }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun findFirstClassUsingStrings(
        classLoader: ClassLoader,
        type: StringMatchType,
        vararg strings: String
    ): Class<Any>? {
        val result = bridge.findClass {
            matcher {
                for (string in strings) {
                    addUsingString(string, type)
                }
            }
        }
        if (result.isEmpty()) return null
        @Suppress("UNCHECKED_CAST")
        return result[0].getInstance(classLoader) as Class<Any>
    }

    @Throws(Exception::class)
    @JvmStatic
    fun findFirstClassUsingName(
        classLoader: ClassLoader,
        type: StringMatchType,
        name: String
    ): Class<*> {
        return UnobfuscatorCache.getInstance().getClass(classLoader, name) {
            val result = bridge.findClass {
                matcher {
                    className(name, type)
                }
            }.firstOrNull() ?: throw ClassNotFoundException("Class not found: $name")
            result.getInstance(classLoader)
        }
    }

    @JvmStatic
    fun getMethodDescriptor(method: Method?): String? {
        if (method == null) return null
        return method.declaringClass.name + "->" + method.name + "(" +
                Arrays.stream(method.parameterTypes).map { it.name }
                    .collect(Collectors.joining(",")) + ")"
    }

    @JvmStatic
    fun getFieldDescriptor(field: Field): String {
        return field.declaringClass.name + "->" + field.name + ":" + field.type.name
    }

    @JvmStatic
    fun convertRealMethod(methodData: MethodData, classLoader: ClassLoader): Method? {
        return try {
            methodData.getMethodInstance(classLoader)
        } catch (_: Exception) {
            null
        }
    }

    @JvmStatic
    fun convertRealClass(classData: ClassData, classLoader: ClassLoader): Class<*>? {
        return try {
            classData.getInstance(classLoader)
        } catch (_: Exception) {
            null
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadFreezeSeenMethod(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            UnobfuscatorCache.getInstance().getMethod(classLoader) {
                findFirstMethodUsingStrings(
                    classLoader,
                    StringMatchType.Contains,
                    "presencestatemanager/setAvailable/new-state"
                )
            }
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadGhostModeMethod(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            val method = findFirstMethodUsingStrings(
                classLoader,
                StringMatchType.Contains,
                "HandleMeComposing/sendComposing"
            )
                ?: throw Exception("GhostMode method not found")
            if (method.parameterTypes.size > 2 && method.parameterTypes[2] == Int::class.java) {
                return@getMethod method
            }
            throw Exception("GhostMode method not found parameter type")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadReceiptMethod(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            val classDeviceJid =
                findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "jid.DeviceJid")
            val classProtocolTreeNode = findFirstClassUsingStrings(
                classLoader,
                StringMatchType.Contains,
                "ProtocolTreeNode/getAttributeJid"
            )

            val methods = bridge.findMethod {
                matcher {
                    addUsingString("receipt")
                    returnType(classProtocolTreeNode!!)
                }
            }

            for (method in methods) {
                val params = method.paramTypeNames
                val hasRequiredParams = params.contains(classDeviceJid.name)
                if (!hasRequiredParams) continue

                return@getMethod method.getMethodInstance(classLoader)
            }

            throw NoSuchMethodError(
                "Receipt method not found. returnType=" + classProtocolTreeNode?.name +
                        ", requiredParams=[" + classDeviceJid.name + "]"
            )
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadReceiptMessageInfoClass(classLoader: ClassLoader): Class<*> {
        return UnobfuscatorCache.getInstance().getClass(classLoader) {
            val methodData = bridge.findMethod {
                matcher {
                    addUsingString("ReadReceiptUtils/buildReadReceiptHandler malformed")
                }
            }.single()
            val deviceJid =
                findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "jid.DeviceJid")
            for (invoke in methodData.invokes) {
                if (invoke.isConstructor && invoke.paramTypeNames.contains(deviceJid.name)) {
                    return@getClass invoke.getClassInstance(classLoader)
                }
            }
            null
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadForwardTagMethod(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            val messageInfoClass = loadFMessageClass(classLoader)
            val methodList = bridge.findMethod {
                matcher {
                    addUsingString("chatInfo/incrementUnseenImportantMessageCount")
                }
            }
            if (methodList.isEmpty()) throw Exception("ForwardTag method support not found")
            val invokes = methodList[0].invokes
            for (invoke in invokes) {
                val method = invoke.getMethodInstance(classLoader)
                if (method.parameterCount == 1 &&
                    (method.parameterTypes[0] == Int::class.java || method.parameterTypes[0] == Long::class.java) &&
                    method.declaringClass == messageInfoClass &&
                    method.returnType == Void.TYPE
                ) {
                    return@getMethod method
                }
            }
            throw Exception("ForwardTag method not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadBroadcastTagField(classLoader: ClassLoader): Field {
        return UnobfuscatorCache.getInstance().getField(classLoader) {
            val fmessage = loadFMessageClass(classLoader)
            val clazzData = bridge.findClass {
                matcher {
                    addUsingString("UPDATE_MESSAGE_MAIN_BROADCAST_SCAN_SQL")
                }
            }
            if (clazzData.isEmpty()) throw Exception("BroadcastTag class not found")

            var methodData = bridge.findMethod {
                searchInClass(clazzData)
                matcher {
                    usingStrings("participant_hash", "view_mode", "broadcast")
                }
            }

            if (methodData.isEmpty()) {
                methodData = bridge.findMethod {
                    searchInClass(clazzData)
                    matcher {
                        usingStrings("received_timestamp", "view_mode", "message")
                    }
                }
                if (!methodData.isEmpty()) {
                    val calledMethods = methodData[0].invokes
                    for (cmethod in calledMethods) {
                        if (Modifier.isStatic(cmethod.modifiers) && cmethod.paramCount == 2 &&
                            fmessage.name == cmethod.declaredClass?.name
                        ) {
                            val pTypes = cmethod.paramTypes
                            if (pTypes[0].name == ContentValues::class.java.name && pTypes[1].name == fmessage.name) {
                                methodData.clear()
                                methodData.add(cmethod)
                                break
                            }
                        }
                    }
                }
            }

            if (methodData.isEmpty()) throw Exception("BroadcastTag method support not found")
            val usingFields = methodData[0].usingFields
            for (ufield in usingFields) {
                val field = ufield.field
                if (field.declaredClass.name == fmessage.name && field.type.name == Boolean::class.java.name) {
                    return@getField field.getFieldInstance(classLoader)
                }
            }
            throw Exception("BroadcastTag field not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadForwardClassMethod(classLoader: ClassLoader): Class<*> {
        return UnobfuscatorCache.getInstance().getClass(classLoader) {
            for (s in arrayOf(
                "UserActions/userActionForwardMessage",
                "UserActionsMessageForwarding/userActionForwardMessage"
            )) {
                val cls = findFirstClassUsingStrings(classLoader, StringMatchType.Contains, s)
                if (cls != null) return@getClass cls
            }
            throw ClassNotFoundException("ForwardClass method not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadHideViewSendReadJob(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            val classData = bridge.getClassData(
                findFirstClassUsingName(
                    classLoader,
                    StringMatchType.EndsWith,
                    "SendReadReceiptJob"
                )
            )
            var methodResult = classData!!.findMethod {
                matcher {
                    addUsingString("receipt", StringMatchType.Equals)
                }
            }
            if (methodResult.isEmpty()) {
                methodResult = classData.superClass!!.findMethod {
                    matcher {
                        addUsingString("receipt", StringMatchType.Equals)
                    }
                }
            }
            if (methodResult.isEmpty()) throw Exception("HideViewSendReadJob method not found")
            methodResult[0].getMethodInstance(classLoader)
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadFMessageClass(classLoader: ClassLoader): Class<*> {
        return UnobfuscatorCache.getInstance().getClass(classLoader) {
            findFirstClassUsingStrings(
                classLoader,
                StringMatchType.Contains,
                "FMessage/getSenderUserJid/key.id"
            )
                ?: throw Exception("Message class not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadTabListMethod(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            val result = bridge.findMethod {
                matcher {
                    addUsingNumber(200)
                    addUsingNumber(300)
                    returnType(ArrayList::class.java)
                }
            }.singleOrNull() ?: throw Exception("TabList method not found")
            result.getMethodInstance(classLoader)
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadGetTabMethod(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            findFirstMethodUsingStringsFilter(
                classLoader,
                "X.",
                StringMatchType.Contains,
                "No HomeFragment mapping for community tab id:"
            )
                ?: throw Exception("GetTab method not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadTabFragmentMethod(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            val clsFrag = XposedHelpers.findClass(
                "com.whatsapp.conversationslist.ConversationsFragment",
                classLoader
            )
            Arrays.stream(clsFrag.declaredMethods).parallel()
                .filter { m -> m.parameterTypes.isEmpty() && m.returnType == MutableList::class.java }
                .findFirst()
                .orElse(null) ?: throw Exception("TabFragment method not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadTabNameMethod(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            val id = UnobfuscatorCache.getInstance().getOfuscateIDString("updates")
            if (id < 1) throw Exception("TabName ID not found")
            val result = bridge.findMethod {
                matcher {
                    returnType(String::class.java)
                    usingNumbers(id)
                }
            }
            if (result.isEmpty()) throw Exception("TabName method not found")
            result[0].getMethodInstance(classLoader)
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadFabMethod(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            val classData =
                bridge.getClassData("com.whatsapp.conversationslist.ConversationsFragment")
            Objects.requireNonNull(classData)
            for (clazz in listOf(classData, classData!!.superClass)) {
                val result = clazz?.findMethod {
                    matcher {
                        paramCount(0)
                        usingNumbers(200)
                        returnType(Int::class.java)
                    }
                }?.firstOrNull()
                if (result != null) return@getMethod result.getMethodInstance(classLoader)
            }
            throw Exception("Fab method not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadIconTabMethod(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            val id1 = Utils.getID("home_tab_communities_selector", "drawable")
            val id2 = Utils.getID("home_tab_calls_selector", "drawable")
            val id3 = Utils.getID("home_tab_chats_selector", "drawable")

            val methodData = bridge.findMethod {
                searchPackages("X.")
                matcher {
                    addUsingNumber(id1)
                    addUsingNumber(id2)
                    addUsingNumber(id3)
                }
            }.singleOrNull() ?: throw Exception("IconTab method not found")
            methodData.getMethodInstance(classLoader)
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadTabCountMethod(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            findFirstMethodUsingStrings(
                classLoader,
                StringMatchType.Contains,
                "required free space should be > 0"
            )
                ?: throw Exception("TabCount method not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadEnableCountTabMethod(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            findFirstMethodUsingStrings(
                classLoader,
                StringMatchType.Contains,
                "Tried to set badge for invalid"
            )
                ?: throw Exception("EnableCountTab method not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadEnableCountTabBadgeWrapper(classLoader: ClassLoader): Constructor<*> {
        return UnobfuscatorCache.getInstance().getConstructor(classLoader) {
            val countMethod = loadEnableCountTabMethod(classLoader)
            val indiceClass = countMethod.parameterTypes[1]
            val result = bridge.findClass {
                matcher {
                    superClass = indiceClass.name
                    methods {
                        add {
                            name = "<init>"
                            paramCount(1,2)
                        }
                    }
                }
            }
            if (result.isEmpty()) throw Exception("EnableCountTabBadgeWrapper method not found")
            return@getConstructor result[0].getInstance(classLoader).constructors[0]
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadEnableCountTabBadgeItem(classLoader: ClassLoader): Constructor<*> {
        return UnobfuscatorCache.getInstance().getConstructor(classLoader) {
            val countTabConstructor1 = loadEnableCountTabBadgeWrapper(classLoader)
            val indiceClass = countTabConstructor1.parameterTypes[0]
            val result = bridge.findClass {
                matcher {
                    superClass(indiceClass.name)
                    addMethod {
                        paramCount(1)
                        addParamType(Int::class.java)
                    }
                }
            }
            if (result.isEmpty()) throw Exception("EnableCountTab method not found")
            result[0].getInstance(classLoader).constructors[0]
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadEnableCountTabEmptyBadgeClass(classLoader: ClassLoader): Class<*> {
        return UnobfuscatorCache.getInstance().getClass(classLoader) {
            val countMethod = loadEnableCountTabMethod(classLoader)
            val indiceClass = countMethod.parameterTypes[1]
            val result = bridge.findClass {
                matcher {
                    superClass(indiceClass.name)
                    addMethod {
                        paramCount(0)
                    }
                }
            }
            if (result.isEmpty()) throw Exception("EnableCountTab method not found")
            result[0].getInstance(classLoader)
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadTimeToSecondsMethod(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            bridge.findMethod {
                matcher {
                    usingNumbers(223, 224)
                    modifiers = Modifier.STATIC
                    returnType = "java.lang.String"
                    paramCount = 2
                    paramTypes(null, "java.util.Calendar")
                }
            }.single().getMethodInstance(classLoader)
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadDndModeMethod(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            findFirstMethodUsingStrings(classLoader, StringMatchType.Equals, "MessageHandler/start")
                ?: throw Exception("DndMode method not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadMediaQualityVideoMethod2(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            findFirstMethodUsingStrings(
                classLoader,
                StringMatchType.Contains,
                "getCorrectedResolution"
            )
                ?: throw Exception("MediaQualityVideo method not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadMediaQualityVideoFields(classLoader: ClassLoader): HashMap<String, Field> {
        return UnobfuscatorCache.getInstance().getMapField(classLoader) {
            val method = loadMediaQualityVideoMethod2(classLoader)
            val methodString = method.returnType.getDeclaredMethod("toString")
            val methodData = bridge.getMethodData(methodString) ?: return@getMapField null
            val usingFields = methodData.usingFields
            val usingStrings = methodData.usingStrings
            val result = HashMap<String, Field>()
            var idxStrings = 0
            var idxFields = 0
            while (idxStrings < usingStrings.size) {
                if (idxFields == usingFields.size) break
                if (usingStrings[idxStrings] == "outputAspectRatio") {
                    idxStrings++
                    continue
                }
                val field = usingFields[idxFields].field.getFieldInstance(classLoader)
                result[usingStrings[idxStrings]] = field
                idxStrings++
                idxFields++
            }
            result
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadMediaQualityOriginalVideoFields(classLoader: ClassLoader): HashMap<String, Field> {
        return UnobfuscatorCache.getInstance().getMapField(classLoader) {
            val method = loadMediaQualityVideoMethod2(classLoader)
            val methodString = try {
                method.parameterTypes[0].getDeclaredMethod("toString")
            } catch (_: Exception) {
                return@getMapField HashMap<String, Field>()
            }
            val methodData = bridge.getMethodData(methodString) ?: return@getMapField null
            val usingFields = methodData.usingFields
            val usingStrings = methodData.usingStrings
            val result = HashMap<String, Field>()
            for (i in usingStrings.indices) {
                if (i == usingFields.size) break
                val field = usingFields[i].field.getFieldInstance(classLoader)
                result[usingStrings[i]] = field
            }
            result
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadProcessVideoQualityClass(classLoader: ClassLoader): Class<*> {
        return UnobfuscatorCache.getInstance().getClass(classLoader) {
            findFirstClassUsingStrings(
                classLoader,
                StringMatchType.StartsWith,
                "ProcessVideoQuality("
            )
                ?: throw Exception("ProcessVideoQuality method not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadMenuManagerClass(classLoader: ClassLoader): Class<*> {
        return UnobfuscatorCache.getInstance().getClass(classLoader) {
            val methods = findAllMethodUsingStrings(
                classLoader,
                StringMatchType.Contains,
                "MenuPopupHelper cannot be used without an anchor"
            )
            for (method in methods) {
                if (method.returnType == Void.TYPE) return@getClass method.declaringClass
            }
            throw Exception("MenuManager class not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadMenuStatusMethod(loader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(loader) {
            val id = Utils.getID("menuitem_conversations_message_contact", "id")
            val methods = bridge.findMethod {
                matcher {
                    addUsingNumber(id)
                }
            }
            if (methods.isEmpty()) throw Exception("MenuStatus method not found")
            methods[0].getMethodInstance(loader)
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadViewOnceMethod(classLoader: ClassLoader): Array<Method> {
        return UnobfuscatorCache.getInstance().getMethods(classLoader) {
            val method = bridge.findMethod {
                matcher {
                    addUsingString("INSERT_VIEW_ONCE_SQL", StringMatchType.Contains)
                }
            }
            if (method.isEmpty()) throw Exception("ViewOnce method not found")
            val methodData = method[0]
            val listMethods = methodData.invokes
            val list = ArrayList<Method>()
            for (m in listMethods) {
                val mInstance = m.getMethodInstance(classLoader)
                if (mInstance.declaringClass.isInterface && mInstance.declaringClass.methods.size == 2) {
                    val listClasses = bridge.findClass {
                        matcher {
                            addInterface(mInstance.declaringClass.name)
                        }
                    }
                    for (c in listClasses) {
                        val clazz = c.getInstance(classLoader)
                        for (m2 in clazz.declaredMethods) {
                            if (m2.parameterCount != 1 || m2.parameterTypes[0] != Int::class.javaPrimitiveType || m2.returnType != Void.TYPE) continue
                            list.add(m2)
                        }
                    }
                    if (list.isEmpty()) throw Exception("ViewOnce method not found")
                    return@getMethods list.toTypedArray()
                }
            }
            throw Exception("ViewOnce method not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadViewOnceDownloadMenuMethod(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            val id1 = Utils.getID("ic_viewonce", "drawable")
            val setShowAsAction = MenuItem::class.java.getDeclaredMethod(
                "setShowAsAction",
                Int::class.javaPrimitiveType
            )
            val methodData = bridge.findMethod {
                matcher {
                    addUsingNumber(id1)
                    addInvoke(DexSignUtil.getMethodDescriptor(setShowAsAction))
                }
            }
            val result = methodData.stream().filter { m ->
                m.paramCount > 1 && m.paramTypeNames.contains(Menu::class.java.name)
            }.findFirst()
            if (!result.isPresent) throw Exception("ViewOnceDownloadMenu method not found")
            result.get().getMethodInstance(classLoader)
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadMaterialShapeDrawableClass(loader: ClassLoader): Class<*> {
        return UnobfuscatorCache.getInstance().getClass(loader) {
            findFirstClassUsingStrings(
                loader,
                StringMatchType.Contains,
                "Compatibility shadow requested"
            )
                ?: throw Exception("MaterialShapeDrawable class not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadPropsBooleanMethod(loader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(loader) {
            findFirstMethodUsingStrings(loader, StringMatchType.Contains, "Unknown BooleanField")
                ?: throw Exception("Props method not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadPropsIntegerMethod(loader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(loader) {
            findFirstMethodUsingStrings(loader, StringMatchType.Contains, "Unknown IntField")
                ?: throw Exception("Props method not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadHomeConversationFragmentMethod(loader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(loader) {
            val homeClass = WppCore.homeActivityClass
            val convFragment = XposedHelpers.findClass("com.whatsapp.ConversationFragment", loader)
            val method = bridge.findMethod {
                searchInClass(Collections.singletonList(bridge.getClassData(homeClass)))
                matcher {
                    returnType(convFragment)
                }
            }.singleOrNull() ?: throw Exception("HomeConversationFragmentMethod not found")
            method.getMethodInstance(loader)
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadAntiRevokeConvFragmentField(loader: ClassLoader): Field {
        return UnobfuscatorCache.getInstance().getField(loader) {
            val chatClass = findFirstClassUsingStrings(
                loader,
                StringMatchType.Contains,
                "conversation/createconversation"
            )
            val conversation = XposedHelpers.findClass("com.whatsapp.ConversationFragment", loader)
            ReflectionUtils.getFieldByType(conversation, chatClass)
                ?: throw Exception("AntiRevokeConvChat field not found")
        }
    }


    @Throws(Exception::class)
    @JvmStatic
    fun loadUserJidConversationDelegate(loader: ClassLoader): Field {
        return UnobfuscatorCache.getInstance().getField(loader) {
            val chatClass = findFirstClassUsingStrings(
                loader,
                StringMatchType.Contains,
                "conversation/createconversation"
            )
            val jidClass = findFirstClassUsingName(loader, StringMatchType.EndsWith, "jid.Jid")
            ReflectionUtils.getFieldByExtendType(chatClass, jidClass)
                ?: throw Exception("UserJidConversationDelegate field not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadAntiRevokeMessageMethod(loader: ClassLoader): Method? {
        return UnobfuscatorCache.getInstance().getMethod(loader) {
            for (s in listOf("msgstore/edit/revoke", "msgstore/revoking/")) {
                val method = findFirstMethodUsingStrings(loader, StringMatchType.Contains, s)
                if (method != null) return@getMethod method
            }
            null
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadMessageKeyField(loader: ClassLoader): Field {
        return UnobfuscatorCache.getInstance().getField(loader) {
            val classList = bridge.findClass {
                matcher {
                    fieldCount(3)
                    addMethod {
                        addUsingString("Key")
                        name("toString")
                    }
                }
            }
            if (classList.isEmpty()) throw Exception("MessageKey class not found")
            for (classData in classList) {
                val keyMessageClass = classData.getInstance(loader)
                val classMessage = loadFMessageClass(loader)
                val fields = ReflectionUtils.getFieldsByExtendType(classMessage, keyMessageClass)
                if (fields.isEmpty()) continue
                return@getField fields[fields.size - 1]
            }
            throw Exception("MessageKey field not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadConversationRowClass(loader: ClassLoader): Class<*>? {
        return UnobfuscatorCache.getInstance().getClass(loader) {
            val clazz = findFirstClassUsingStrings(
                loader,
                StringMatchType.Contains,
                "ConversationRow/setupUserNameInGroupView/"
            )
            if (clazz != null) return@getClass clazz
            val conversationHeader = Utils.getID("name_in_group", "id")
            val classData = bridge.findClass {
                matcher {
                    addMethod {
                        addUsingNumber(conversationHeader)
                    }
                }
            }
            for (c in classData) {
                val clazzInstance = c.getInstance(loader)
                if (ViewGroup::class.java.isAssignableFrom(clazzInstance)) return@getClass clazzInstance
            }
            null
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadUnknownStatusPlaybackMethod(loader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(loader) {
            val statusPlaybackClass = XposedHelpers.findClass(
                "com.whatsapp.status.playback.fragment.StatusPlaybackContactFragment",
                loader
            )
            val refreshCurrentPage = bridge.findMethod {
                matcher {
                    addUsingString("playbackFragment/refreshCurrentPageSubTitle message is empty")
                }
            }[0]
            val invokes = refreshCurrentPage.invokes
            for (invoke in invokes) {
                val method = invoke.getMethodInstance(loader)
                if (Modifier.isStatic(method.modifiers) && method.parameterCount > 1 &&
                    listOf(*method.parameterTypes).contains(statusPlaybackClass) &&
                    method.declaringClass === statusPlaybackClass
                ) {
                    return@getMethod method
                }
            }
            throw Exception("UnknownStatusPlayback method not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadStatusPlaybackViewClass(loader: ClassLoader): Class<*> {
        return UnobfuscatorCache.getInstance().getClass(loader) {
            val ids = listOf(Utils.getID("status_header", "id"), Utils.getID("menu", "id"))
            val clazz = bridge.findClass {
                matcher {
                    addMethod {
                        usingNumbers(ids)
                    }
                }
            }
            if (clazz.isEmpty()) throw Exception("Not Found StatusPlaybackViewClass")
            clazz[0].getInstance(loader)
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadBlueOnReplayMessageJobMethod(loader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(loader) {
            findFirstMethodUsingStrings(loader, StringMatchType.Contains, "SendE2EMessageJob/onRun")
                ?: throw Exception("BlueOnReplayMessageJob method not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadBlueOnReplayWaJobManagerMethod(loader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(loader) {
            val result =
                findFirstClassUsingStrings(loader, StringMatchType.Contains, "WaJobManager/start")
                    ?: throw Exception("BlueOnReplayWaJobManager method not found")
            val job = XposedHelpers.findClass("org.whispersystems.jobqueue.Job", loader)
            Arrays.stream(result.methods)
                .filter { m -> m.parameterCount == 1 && m.parameterTypes[0] === job }
                .findFirst()
                .orElse(null) ?: throw Exception("BlueOnReplayWaJobManager method not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadArchiveChatClass(loader: ClassLoader): Class<*> {
        return UnobfuscatorCache.getInstance().getClass(loader) {
            var clazz = findFirstClassUsingStrings(
                loader,
                StringMatchType.Contains,
                "archive/set-content-indicator-to-empty"
            )
            if (clazz == null)
                clazz = findFirstClassUsingStrings(
                    loader,
                    StringMatchType.Contains,
                    "archive/Unsupported mode in ArchivePreviewView:"
                )
            clazz ?: throw Exception("ArchiveHideView method not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadAntiRevokeOnCallReceivedMethod(loader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(loader) {
            findFirstMethodUsingStrings(
                loader,
                StringMatchType.Contains,
                "voip/callStateChangedOnUIThread"
            )
                ?: throw Exception("OnCallReceiver method not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadOnChangeStatus(loader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(loader) {
            var method = findFirstMethodUsingStrings(
                loader,
                StringMatchType.Contains,
                "ConversationViewFiller/setParentGroupProfilePhoto"
            )
                ?: throw Exception("OnChangeStatus method not found")

            if (method.parameterCount < 6) {
                val declaringClassData = bridge.getClassData(method.declaringClass)
                    ?: throw Exception("OnChangeStatus method not found")

                val arg1Class = loadWaContactClass(loader)
                val methodData = declaringClassData.findMethod {
                    matcher {
                        paramCount(6, 8)
                    }
                }

                for (methodItem in methodData) {
                    val paramTypes = methodItem.paramTypes
                    if (paramTypes[0].getInstance(loader) === arg1Class && paramTypes[1].getInstance(
                            loader
                        ) === arg1Class
                    ) {
                        method = methodItem.getMethodInstance(loader)
                        break
                    }
                }
            }
            method
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadViewHolder(loader: ClassLoader): Class<*> {
        return UnobfuscatorCache.getInstance().getClass(loader) {
            val methods = bridge.findMethod {
                matcher {
                    usingNumbers(
                        Utils.getID("conversations_row_header_stub", "id"),
                        Utils.getID("pin_indicator", "id"),
                        Utils.getID("mute_indicator", "id"),
                        Utils.getID("contact_photo", "id")
                    )
                }
            }.stream()
                .filter { methodData -> methodData.paramTypes[0].name == Context::class.java.name }
                .collect(Collectors.toList())
            if (methods.isEmpty()) throw ClassNotFoundException("View Holder not found!")
            methods[0].getMethodInstance(loader).declaringClass
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadViewHolderField1(loader: ClassLoader): Field {
        return UnobfuscatorCache.getInstance().getField(loader) {
            val class1 = loadOnChangeStatus(loader).declaringClass.superclass
            ReflectionUtils.getFieldByType(class1, loadViewHolder(loader))
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadStatusUserMethod(loader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(loader) {
            val id = UnobfuscatorCache.getInstance().getOfuscateIDString("lastseensun%s")
            if (id < 1) throw Exception("GetStatusUser ID not found")
            val result = bridge.findMethod {
                matcher {
                    addUsingNumber(id)
                    returnType(String::class.java)
                }
            }
            if (result.isEmpty()) throw Exception("GetStatusUser method not found")
            result[result.size - 1].getMethodInstance(loader)
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadSendPresenceMethod(loader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(loader) {
            val methodData = bridge.findMethod {
                matcher {
                    addUsingString("app/send-presence-subscription jid=")
                }
            }
            if (methodData.isEmpty()) throw Exception("SendPresence method not found")
            var methodCallers = methodData[0].callers
            if (methodCallers.isEmpty()) {
                val method = methodData[0]
                val superMethodInterfaces = method.declaredClass!!.interfaces
                if (superMethodInterfaces.isEmpty()) throw Exception("SendPresence method interface list empty")
                val superMethod = superMethodInterfaces[0].findMethod {
                    matcher {
                        name(method.name)
                    }
                }.firstOrNull() ?: throw Exception("SendPresence method interface method not found")
                methodCallers = superMethod.callers
            }
            val newMethod = methodCallers.firstOrNull { it.paramCount == 4 }
                ?: throw Exception("SendPresence method not found 2")
            newMethod.getMethodInstance(loader)
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadPinnedHashSetMethod(loader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(loader) {
            findFirstMethodUsingStrings(
                loader,
                StringMatchType.Contains,
                "getPinnedJids/QUERY_CHAT_SETTINGS"
            )
                ?: throw Exception("PinnedHashSet method not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadGetFiltersMethod(loader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(loader) {
            val clazzFilters = findFirstClassUsingStrings(
                loader,
                StringMatchType.Contains,
                "conversations/filter/performFiltering"
            )
                ?: throw RuntimeException("Filters class not found")
            Arrays.stream(clazzFilters.declaredMethods).parallel()
                .filter { m -> m.name == "publishResults" }.findFirst().orElse(null)
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadPinnedInChatMethod(loader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(loader) {
            val method = bridge.findMethod {
                matcher {
                    addUsingNumber(3732)
                    returnType(Int::class.java)
                }
            }
            if (method.isEmpty()) throw RuntimeException("PinnedInChat method not found")
            method[0].getMethodInstance(loader)
        }
    }


    @Throws(Exception::class)
    @JvmStatic
    fun loadBlueOnReplayViewButtonMethod(loader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(loader) {
            findFirstMethodUsingStrings(
                loader,
                StringMatchType.Contains,
                "PLAYBACK_PAGE_ITEM_ON_CREATE_VIEW_END"
            )
                ?: throw RuntimeException("BlueOnReplayViewButton method not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadBlueOnReplayStatusViewMethod(loader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(loader) {
            findFirstMethodUsingStrings(
                loader,
                StringMatchType.Contains,
                "StatusPlaybackPage/onViewCreated"
            )
                ?: throw RuntimeException("BlueOnReplayViewButton method not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadChatLimitDeleteMethod(loader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(loader) {
            val clazz = findFirstClassUsingStrings(
                loader,
                StringMatchType.Contains,
                "app/time server update processed"
            )
                ?: throw RuntimeException("ChatLimitDelete class not found")
            var method = Arrays.stream(clazz.declaredMethods)
                .filter { m -> m.returnType == Long::class.javaPrimitiveType && Modifier.isStatic(m.modifiers) }
                .findFirst().orElse(null)
            if (method == null) {
                val methodList = bridge.getClassData(clazz)?.findMethod {
                    matcher {
                        opCodes(
                            OpCodesMatcher().opNames(
                                listOf(
                                    "invoke-static",
                                    "move-result-wide",
                                    "iget-wide",
                                    "const-wide/16",
                                    "cmp-long",
                                    "if-eqz",
                                    "iget-wide",
                                    "add-long/2addr",
                                    "return-wide",
                                    "iget-wide",
                                    "cmp-long",
                                    "if-eqz",
                                    "iget-wide",
                                    "goto",
                                    "invoke-static",
                                    "move-result-wide",
                                    "iget-wide",
                                    "sub-long/2addr",
                                    "return-wide"
                                )
                            )
                        )
                    }
                } ?: return@getMethod null
                if (methodList.isEmpty()) throw RuntimeException("ChatLimitDelete method not found")
                method = methodList[0].getMethodInstance(loader)
            }
            method
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadChatLimitDelete2Method(loader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(loader) {
            findFirstMethodUsingStrings(
                loader,
                StringMatchType.Contains,
                "dialog/delete no messages",
                "pref_delete_media"
            )
                ?: throw RuntimeException("ChatLimitDelete2 method not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadNewMessageMethod(loader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(loader) {
            val clazzMessageName = loadFMessageClass(loader).name
            val listMethods = bridge.findMethod {
                searchPackages("com.whatsapp")
                matcher {
                    addUsingString("extra_payment_note", StringMatchType.Equals)
                }
            }
            if (listMethods.isEmpty()) throw Exception("NewMessage method not found")
            val invokes = listMethods[0].invokes
            val method = invokes.parallelStream()
                .filter { invoke ->
                    clazzMessageName == invoke.declaredClass?.name &&
                            invoke.returnType != null &&
                            invoke.returnType?.name == "java.lang.String"
                }.findFirst().orElse(null) ?: throw RuntimeException("NewMessage method not found")
            method.getMethodInstance(loader)
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadOriginalMessageKey(loader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(loader) {
            findFirstMethodUsingStrings(
                loader,
                StringMatchType.Contains,
                "FMessageUtil/getOriginalMessageKeyIfEdited"
            )
                ?: throw RuntimeException("MessageEdit method not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadNewMessageWithMediaMethod(loader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(loader) {
            val methodList = bridge.findMethod {
                matcher {
                    addUsingString("INSERT_TABLE_MESSAGE_QUOTED", StringMatchType.Equals)
                }
            }
            if (methodList.isEmpty()) throw Exception("NewMessageWithMedia method not found")
            val methodData = methodList[0]
            val invokes = methodData.invokes
            val clazzMessageName = loadFMessageClass(loader).name
            val method = invokes.parallelStream()
                .filter { invoke ->
                    clazzMessageName == invoke.declaredClass?.name &&
                            invoke.returnType != null &&
                            invoke.returnType?.name == "java.lang.String"
                }.findFirst().orElse(null)
                ?: throw RuntimeException("NewMessageWithMedia method not found")
            method.getMethodInstance(loader)
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadMessageEditMethod(loader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(loader) {
            findFirstMethodUsingStrings(
                loader,
                StringMatchType.Contains,
                "MessageEditInfoStore/insertEditInfo/missing"
            )
                ?: throw RuntimeException("MessageEdit method not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadCallerMessageEditMethod(loader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(loader) {
            val methodData1 = bridge.getMethodData(loadMessageEditMethod(loader))
            val fMessage = loadFMessageClass(loader)
            val invokes = methodData1!!.invokes
            for (methodData in invokes) {
                if (methodData.isConstructor) continue
                val method = methodData.getMethodInstance(loader)
                if (Modifier.isStatic(method.modifiers) &&
                    method.parameterCount == 1 && method.parameterTypes[0] == fMessage &&
                    !method.returnType.isPrimitive
                ) {
                    return@getMethod methodData.getMethodInstance(loader)
                }
            }
            throw RuntimeException("CallerMessageEdit method not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadGetEditMessageMethod(loader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(loader) {
            val method = findFirstMethodUsingStrings(
                loader,
                StringMatchType.Contains,
                "MessageEditInfoStore/insertEditInfo/missing"
            )
                ?: throw RuntimeException("GetEditMessage method not found")
            val methodData = bridge.getMethodData(DexSignUtil.getMethodDescriptor(method))
                ?: throw RuntimeException("GetEditMessage method not found")
            val invokes = methodData.invokes
            for (invoke in invokes) {
                if (invoke.paramTypes.isEmpty() && invoke.declaredClass == methodData.paramTypes[0]) {
                    return@getMethod invoke.getMethodInstance(loader)
                }
                if (Modifier.isStatic(invoke.getMethodInstance(loader).modifiers) &&
                    invoke.paramTypes[0] == methodData.paramTypes[0] &&
                    invoke.paramTypes[0] != invoke.declaredClass
                ) {
                    return@getMethod invoke.getMethodInstance(loader)
                }
            }
            throw RuntimeException("GetEditMessage method not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadSetEditMessageField(loader: ClassLoader): Field {
        return UnobfuscatorCache.getInstance().getField(loader) {
            var method = findFirstMethodUsingStrings(
                loader,
                StringMatchType.Contains,
                "CoreMessageStore/updateCheckoutMessageWithTransactionInfo"
            )
            if (method == null)
                method = findFirstMethodUsingStrings(
                    loader,
                    StringMatchType.Contains,
                    "UPDATE_MESSAGE_ADD_ON_FLAGS_MAIN_SQL"
                ) ?: return@getField null
            val classData = bridge.getClassData(loadFMessageClass(loader))
            val methodData = bridge.getMethodData(DexSignUtil.getMethodDescriptor(method))
            val usingFields = methodData!!.usingFields
            for (f in usingFields) {
                val field = f.field
                if (field.declaredClass == classData && field.type.name == Long::class.javaPrimitiveType?.name) {
                    return@getField field.getFieldInstance(loader)
                }
            }
            throw RuntimeException("SetEditMessage method not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadCoreMessageStore(loader: ClassLoader): Class<*> {
        return UnobfuscatorCache.getInstance().getClass(loader) {
            var clazz = findFirstClassUsingStrings(
                loader,
                StringMatchType.Contains,
                "CoreMessageStore/updateCheckoutMessageWithTransactionInfo"
            )
            if (clazz == null)
                clazz = findFirstClassUsingStrings(
                    loader,
                    StringMatchType.Contains,
                    "UPDATE_MESSAGE_ADD_ON_FLAGS_MAIN_SQL"
                )
            clazz ?: throw Exception("CoreMessageStore class not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadDialogViewClass(loader: ClassLoader): Class<*> {
        return UnobfuscatorCache.getInstance().getClass(loader) {
            val id = Utils.getID("touch_outside", "id")
            val results = bridge.findMethod {
                matcher {
                    addUsingNumber(id)
                    returnType(FrameLayout::class.java)
                }
            }
            if (results.isEmpty()) throw Exception("DialogView class not found")
            results[0].declaredClass!!.getInstance(loader)
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadRecreateFragmentConstructor(loader: ClassLoader): Constructor<*> {
        return UnobfuscatorCache.getInstance().getConstructor(loader) {
            val data = bridge.findMethod {
                searchPackages("X.")
                matcher {
                    addUsingString("Instantiated fragment")
                }
            }
            if (data.isEmpty()) throw RuntimeException("RecreateFragment method not found")
            if (!data.single().isConstructor) throw RuntimeException("RecreateFragment method not found")
            data.single().getConstructorInstance(loader)
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadOnTabItemAddMethod(loader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(loader) {
            findFirstMethodUsingStrings(
                loader,
                StringMatchType.Contains,
                "Maximum number of items supported by"
            )
                ?: throw RuntimeException("OnTabItemAdd method not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadGetViewConversationMethod(loader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(loader) {
            val clazz = XposedHelpers.findClass(
                "com.whatsapp.conversationslist.ConversationsFragment",
                loader
            )
            Arrays.stream(clazz.declaredMethods).filter { m ->
                m.parameterCount == 3 && m.returnType == View::class.java && m.parameterTypes[1] == LayoutInflater::class.java
            }.findFirst().orElse(null)
                ?: throw RuntimeException("GetViewConversation method not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadOnMenuItemSelected(loader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(loader) {
            val aClass = XposedHelpers.findClass("androidx.viewpager.widget.ViewPager", loader)
            val result = Arrays.stream(aClass.declaredMethods).filter { m ->
                m.parameterCount == 4 &&
                        m.parameterTypes[0] == Int::class.javaPrimitiveType &&
                        m.parameterTypes[1] == Int::class.javaPrimitiveType &&
                        m.parameterTypes[2] == Boolean::class.javaPrimitiveType &&
                        m.parameterTypes[3] == Boolean::class.javaPrimitiveType
            }.collect(Collectors.toList())
            if (result.isEmpty()) throw RuntimeException("OnMenuItemSelected method not found")
            result[1]
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadOnUpdateStatusChanged(loader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(loader) {
            val clazzData = bridge.findClass {
                matcher {
                    addUsingString("UpdatesViewModel/")
                }
            }.firstOrNull()
            val methodSeduleche = XposedHelpers.findMethodBestMatch(
                Timer::class.java,
                "schedule",
                TimerTask::class.java,
                Long::class.javaPrimitiveType,
                Long::class.javaPrimitiveType
            )
            var result = clazzData?.findMethod {
                matcher {
                    addInvoke(DexSignUtil.getMethodDescriptor(methodSeduleche))
                }
            } ?: emptyList()
            if (result.isEmpty()) {
                result = bridge.findMethod {
                    matcher {
                        addUsingString("UpdatesViewModel/Scheduled updates list refresh")
                    }
                }
            }
            if (result.isEmpty()) throw RuntimeException("OnUpdateStatusChanged method not found")
            result[0].getMethodInstance(loader)
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadGetInvokeField(loader: ClassLoader): Field {
        return UnobfuscatorCache.getInstance().getField(loader) {
            val method = loadOnUpdateStatusChanged(loader)
            val methodData = bridge.getMethodData(DexSignUtil.getMethodDescriptor(method))
            val fields = methodData!!.usingFields
            val field = fields.stream().map { it.field }
                .filter { f -> f.declaredClass == methodData.declaredClass }.findFirst()
                .orElse(null)
                ?: throw RuntimeException("GetInvokeField method not found")
            field.getFieldInstance(loader)
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadStatusInfoClass(loader: ClassLoader): Class<*> {
        return UnobfuscatorCache.getInstance().getClass(loader) {
            findFirstClassUsingStrings(loader, StringMatchType.Contains, "ContactStatusDataItem")
                ?: throw RuntimeException("StatusInfo class not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadStatusListUpdatesClass(loader: ClassLoader): Class<*> {
        return UnobfuscatorCache.getInstance().getClass(loader) {
            findFirstClassUsingStrings(loader, StringMatchType.Contains, "StatusListUpdates")
                ?: throw RuntimeException("StatusListUpdates class not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadTabFrameClass(loader: ClassLoader): Class<*> {
        return UnobfuscatorCache.getInstance().getClass(loader) {
            findFirstClassUsingStrings(loader, StringMatchType.Contains, "android:menu:presenters")
                ?: throw RuntimeException("TabFrame class not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadRemoveChannelRecClass(loader: ClassLoader): Class<*> {
        return UnobfuscatorCache.getInstance().getClass(loader) {
            findFirstClassUsingStrings(
                loader,
                StringMatchType.Contains,
                "hasNewsletterSubscriptions"
            )
                ?: throw RuntimeException("RemoveChannelRec class not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadFilterAdaperClass(loader: ClassLoader): Class<*> {
        return UnobfuscatorCache.getInstance().getClass(loader) {
            val clazzList = bridge.findClass {
                matcher {
                    addMethod {
                        addUsingString("CONTACTS_FILTER")
                        paramCount(1)
                        addParamType(Int::class.java)
                    }
                }
            }
            if (clazzList.isEmpty()) throw RuntimeException("FilterAdapter class not found")
            clazzList[0].getInstance(loader)
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadSeeMoreConstructor(loader: ClassLoader): Constructor<*> {
        return UnobfuscatorCache.getInstance().getConstructor(loader) {
            val commentClass =
                findFirstClassUsingName(loader, StringMatchType.EndsWith, "CommentTextView")
            val commentClassData = bridge.getClassData(commentClass)
            val methods = commentClassData!!.methods
            val arrayList = ArrayList<ClassData>()
            methods.forEach { methodData ->
                val invokes = methodData.invokes
                val classes =
                    invokes.stream().map { it.declaredClass!! }.collect(Collectors.toSet())
                arrayList.addAll(classes)
            }

            val clazzData = bridge.findClass {
                searchIn(arrayList)
                matcher {
                    addMethod {
                        addUsingNumber(16384)
                        addUsingNumber(512)
                        addUsingNumber(64)
                        addUsingNumber(16)
                    }
                }
            }.singleOrNull() ?: throw RuntimeException("SeeMore constructor 1 not found")

            for (method in clazzData.methods) {
                if (method.paramCount > 1 && method.isConstructor &&
                    method.paramTypes.stream()
                        .allMatch { c -> c.name == Int::class.javaPrimitiveType?.name }
                ) {
                    return@getConstructor method.getConstructorInstance(loader)
                }
            }
            throw RuntimeException("SeeMore constructor 2 not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadSendStickerMethods(loader: ClassLoader): Array<Method> {
        return UnobfuscatorCache.getInstance().getMethods(loader) {
            findAllMethodUsingStrings(
                loader,
                StringMatchType.Contains,
                "StickerGridViewItem.StickerLocal"
            )
                .ifEmpty { throw RuntimeException("SendSticker method not found") }
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadMaterialAlertDialog(loader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(loader) {
            val callConfirmationFragment = XposedHelpers.findClass(
                "com.whatsapp.calling.fragment.CallConfirmationFragment",
                loader
            )
            val method = ReflectionUtils.findMethodUsingFilter(callConfirmationFragment) { m ->
                m.parameterCount == 1 && m.parameterTypes[0] == Bundle::class.java
            }
            val methodData = bridge.getMethodData(method)
            val invokes = methodData!!.invokes
            for (invoke in invokes) {
                if (invoke.isMethod && Modifier.isStatic(invoke.modifiers) && invoke.paramCount == 1 &&
                    invoke.paramTypes[0].name == Context::class.java.name
                ) {
                    return@getMethod invoke.getMethodInstance(loader)
                }
                if (invoke.isMethod && invoke.paramCount == 1 &&
                    invoke.paramTypes[0].name == Context::class.java.name
                ) {
                    return@getMethod invoke.getMethodInstance(loader)
                }
            }
            throw RuntimeException("MaterialAlertDialog not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadJidFactory(loader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(loader) {
            findFirstMethodUsingStrings(
                loader,
                StringMatchType.Contains,
                "lid_me",
                "status_me",
                "s.whatsapp.net"
            )
                ?: throw RuntimeException("JidFactory method not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadGroupCheckAdminMethod(loader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(loader) {
            val classData = bridge.findClass {
                matcher {
                    addUsingString("saveGroupParticipants/INSERT_GROUP_PARTICIPANT_USER")
                }
            }.singleOrNull() ?: throw RuntimeException("GroupCheckAdmin class data not found")
            val groupChatClass =
                findFirstClassUsingName(loader, StringMatchType.EndsWith, "GroupChatInfoActivity")
            val onCreateMenu = ReflectionUtils.findMethodUsingFilter(groupChatClass) { method ->
                method.name == "onCreateContextMenu"
            }
            val onCreateMenuData = bridge.getMethodData(onCreateMenu)
            val invokes = onCreateMenuData!!.invokes.stream()
                .filter { m -> m.declaredClassName == classData.name }
                .collect(Collectors.toList())
            for (invoke in invokes) {
                val invokeMethod = invoke.getMethodInstance(loader)
                if (invokeMethod.parameterCount != 2 || invokeMethod.returnType != Boolean::class.javaPrimitiveType) continue
                if (invokeMethod.parameterTypes[1].name.contains("jid.UserJid")) {
                    XposedBridge.log("FIND: $invokeMethod")
                    return@getMethod invokeMethod
                }
            }
            throw RuntimeException("GroupCheckAdmin method not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadStartPrefsConfig(loader: ClassLoader): Constructor<*> {
        return UnobfuscatorCache.getInstance().getConstructor(loader) {
            val results = bridge.findMethod {
                matcher {
                    addUsingString("startup_migrated_version")
                }
            }
            if (results.isEmpty()) throw RuntimeException("StartPrefsConfig constructor not found")
            results[0].getConstructorInstance(loader)
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadCheckOnlineMethod(loader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(loader) {
            var method = findFirstMethodUsingStrings(
                loader,
                StringMatchType.Contains,
                "MessageHandler/handleConnectionThreadReady connectionready"
            )
            if (method == null)
                method = findFirstMethodUsingStrings(
                    loader,
                    StringMatchType.Contains,
                    "app/xmpp/recv/handle_available"
                )
            method ?: throw RuntimeException("CheckOnline method not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadEphemeralInsertdb(loader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(loader) {
            val method = bridge.findMethod {
                matcher {
                    addUsingString("expire_timestamp")
                    addUsingString("ephemeral_initiated_by_me")
                    addUsingString("ephemeral_trigger")
                    returnType(ContentValues::class.java)
                }
            }
            if (method.isEmpty()) throw RuntimeException("FieldExpireTime method not found")
            method[0].getMethodInstance(loader)
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadDefEmojiClass(loader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(loader) {
            findFirstMethodUsingStrings(loader, StringMatchType.Contains, "emojis.oba")
                ?: throw RuntimeException("DefEmoji class not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadVideoViewContainerClass(loader: ClassLoader): Class<*> {
        return UnobfuscatorCache.getInstance().getClass(loader) {
            findFirstClassUsingStrings(
                loader,
                StringMatchType.Contains,
                "frame_visibility_serial_worker"
            )
                ?: throw RuntimeException("VideoViewContainer class not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadImageVewContainerClass(loader: ClassLoader): Class<*> {
        return UnobfuscatorCache.getInstance().getClass(loader) {
            val clazzList = bridge.findClass {
                matcher {
                    addMethod {
                        addUsingNumber(Utils.getID("hd_invisible_touch", "id"))
                        addUsingNumber(Utils.getID("control_btn", "id"))
                    }
                }
            }
            if (clazzList.isEmpty()) throw RuntimeException("ImageViewContainer class not found")
            for (clazzData in clazzList) {
                val clazz = clazzData.getInstance(loader)
                if (ViewGroup::class.java.isAssignableFrom(clazz)) return@getClass clazz
            }
            throw ClassNotFoundException("Class ImageViewContainer not Found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun getFilterView(loader: ClassLoader): Class<*> {
        return UnobfuscatorCache.getInstance().getClass(loader) {
            val filterId = Utils.getID("conversations_swipe_to_reveal_filters_stub", "id")
            val results = bridge.findClass {
                matcher {
                    addMethod {
                        addUsingNumber(filterId)
                    }
                }
            }
            if (results.isEmpty()) throw RuntimeException("FilterView class not found")
            results[0].getInstance(loader)
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadActionUser(loader: ClassLoader): Class<*> {
        return UnobfuscatorCache.getInstance().getClass(loader) {
            val fMessageClass = loadFMessageClass(loader)
            val result = bridge.findMethod {
                matcher {
                    paramTypes(fMessageClass, String::class.java, Boolean::class.javaPrimitiveType)
                    modifiers(Modifier.PUBLIC or Modifier.FINAL)
                    returnType(Boolean::class.javaPrimitiveType!!)
                }
            }.singleOrNull() ?: throw RuntimeException("ActionUser class not found")
            result.declaredClass!!.getInstance(loader)
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadOnPlaybackFinished(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            findFirstMethodUsingStrings(
                classLoader,
                StringMatchType.Contains,
                "playbackPage/onPlaybackContentFinished"
            )
                ?: throw RuntimeException("OnPlaybackFinished method not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadNextStatusRunMethod(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            val methodList = bridge.findMethod {
                matcher {
                    addUsingString("playMiddleTone")
                    name("run")
                }
            }
            if (methodList.isEmpty()) throw RuntimeException("RunNextStatus method not found")
            methodList[0].getMethodInstance(classLoader)
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadOnInsertReceipt(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            val method = bridge.findMethod {
                matcher {
                    addUsingString("INSERT_RECEIPT_USER")
                    paramCount(1)
                }
            }.singleOrNull() ?: throw RuntimeException("OnInsertReceipt method not found")
            method.getMethodInstance(classLoader)
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadSendAudioTypeMethod(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            val classMsgReplyAct = findFirstClassUsingName(
                classLoader,
                StringMatchType.EndsWith,
                "MessageReplyActivity"
            )
            val method = classMsgReplyAct.getMethod(
                "onActivityResult",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Intent::class.java
            )
            val methodData = bridge.getMethodData(method) ?: return@getMethod null
            val invokes = methodData.invokes
            for (invoke in invokes) {
                if (!invoke.isMethod) continue
                val m1 = invoke.getMethodInstance(classLoader)
                val params = listOf(*m1.parameterTypes)
                if (params.contains(MutableList::class.java) && params.contains(Int::class.javaPrimitiveType) && params.contains(
                        Uri::class.java
                    )
                ) {
                    return@getMethod m1
                }
            }
            throw NoSuchMethodException("SendAudioType method not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadOriginFMessageField(classLoader: ClassLoader): Field {
        return UnobfuscatorCache.getInstance().getField(classLoader) {
            val commonStrings = arrayOf(
                "audio/ogg; codecs=opus",
                "audio/ogg",
                "audio/amr",
                "audio/mp4",
                "audio/aac"
            )

            val clazz = loadFMessageClass(classLoader)

            for (str in commonStrings) {
                try {
                    val result = bridge.findMethod {
                        matcher {
                            addUsingString(str, StringMatchType.Contains)
                        }
                    }
                    if (result.isEmpty()) continue

                    for (m in result) {
                        val fields = m.usingFields
                        for (field in fields) {
                            val f = field.field.getFieldInstance(classLoader)
                            if (f.declaringClass == clazz) return@getField f
                        }
                    }
                } catch (_: Exception) {
                }
            }
            throw RuntimeException("OriginFMessageField field not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadForwardAudioTypeMethod(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            val results = findAllMethodUsingStrings(
                classLoader,
                StringMatchType.Contains,
                "FMessageFactory/newFMessageForForward/thumbnail"
            )
            if (results.isEmpty()) throw RuntimeException("ForwardAudioType method not found")
            if (results.size > 1) {
                findFirstMethodUsingStrings(
                    classLoader,
                    StringMatchType.Contains,
                    "forwardable",
                    "FMessageFactory/newFMessageForForward/thumbnail"
                )!!
            } else {
                findFirstMethodUsingStrings(
                    classLoader,
                    StringMatchType.Contains,
                    "Non-forwardable message("
                )!!
            }
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadPlaybackSpeed(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            val method = findFirstMethodUsingStrings(
                classLoader,
                StringMatchType.Contains,
                "heroaudioplayer/setPlaybackSpeed"
            )
            if (method != null) return@getMethod method

            val methodData = bridge.findMethod {
                matcher {
                    addUsingString("setPlaybackSpeed", StringMatchType.Equals)
                    addUsingString("newSpeed")
                }
            }.singleOrNull() ?: throw RuntimeException("PlaybackSpeed method not found")
            methodData.getMethodInstance(classLoader)
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadListUpdateItems(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            val method = bridge.findMethod {
                matcher {
                    addUsingString("Running diff util, updates list size", StringMatchType.Contains)
                }
            }
            if (method.isEmpty()) throw RuntimeException("ListUpdateItems method not found")
            method[0].getMethodInstance(classLoader)
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadHeaderChannelItemClass(classLoader: ClassLoader): Class<*> {
        return UnobfuscatorCache.getInstance().getClass(classLoader) {
            findFirstClassUsingStrings(classLoader, StringMatchType.Contains, "statusTilesEnabled")
                ?: throw RuntimeException("HeaderChannelItem class not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadListChannelItemClass(classLoader: ClassLoader): Class<*> {
        return UnobfuscatorCache.getInstance().getClass(classLoader) {
            findFirstClassUsingStrings(
                classLoader,
                StringMatchType.Contains,
                "isMuteIndicatorEnabled"
            )
                ?: throw RuntimeException("NewsletterDataItem class not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadTextStatusData(classLoader: ClassLoader): Array<Method> {
        return UnobfuscatorCache.getInstance().getMethods(classLoader) {
            var textData: Class<*>?
            val textDataList = bridge.findClass {
                matcher {
                    addUsingString("TextData;")
                }
            }
            textData = if (textDataList.isEmpty()) {
                findFirstClassUsingName(classLoader, StringMatchType.EndsWith, "TextData")
            } else {
                textDataList[0].getInstance(classLoader)
            }
            val methods = bridge.findMethod {
                matcher {
                    addParamType(textData)
                }
            }
            if (methods.isEmpty()) throw RuntimeException("loadTextStatusData method not found")

            methods.stream().filter { it.isMethod }
                .map { convertRealMethod(it, classLoader) }
                .filter { it != null }
                .map { it!! }
                .toArray { length -> arrayOfNulls<Method>(length) }
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadExpirationClass(classLoader: ClassLoader): Class<*> {
        return UnobfuscatorCache.getInstance().getClass(classLoader) {
            val methods = findAllMethodUsingStrings(
                classLoader,
                StringMatchType.Contains,
                "software_forced_expiration"
            )
            val expirationMethod = Arrays.stream(methods)
                .filter { methodData -> methodData.returnType == Date::class.java }
                .findFirst().orElse(null) ?: throw RuntimeException("Expiration class not found")
            expirationMethod.declaringClass
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadAbsViewHolder(classLoader: ClassLoader): Class<*> {
        return UnobfuscatorCache.getInstance().getClass(classLoader) {
            findFirstClassUsingStrings(classLoader, StringMatchType.Contains, "not recyclable")
                ?: throw RuntimeException("AbsViewHolder class not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadFragmentViewMethod(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            findFirstMethodUsingStrings(
                classLoader,
                StringMatchType.Contains,
                "this was called before onCreateView()"
            )
                ?: throw RuntimeException("FragmentView method not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadCopiedMessageMethod(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            findFirstMethodUsingStrings(
                classLoader,
                StringMatchType.Contains,
                "conversation/copymessage"
            )
                ?: throw RuntimeException("CopiedMessage method not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadSenderPlayedClass(classLoader: ClassLoader): Class<*> {
        return UnobfuscatorCache.getInstance().getClass(classLoader) {
            findFirstClassUsingStrings(
                classLoader,
                StringMatchType.Contains,
                "sendmethods/sendClearDirty"
            )
                ?: throw RuntimeException("SenderPlayed class not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadSenderPlayedMethod(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            val clazz = loadSenderPlayedClass(classLoader)
            val abstractMediaMessageClass = loadAbstractMediaMessageClass(classLoader)
            val interfaces = abstractMediaMessageClass.interfaces

            val interfacesList = ArrayList<Class<*>>()
            interfacesList.add(abstractMediaMessageClass)
            interfacesList.addAll(listOf(*interfaces))

            var methodResult: Method? = null
            main_loop@ for (method in clazz.methods) {
                if (method.parameterCount != 1) continue
                val parameterType = method.parameterTypes[0]
                for (interfaceClass in interfacesList) {
                    if (interfaceClass.isAssignableFrom(parameterType)) {
                        methodResult = method
                        break@main_loop
                    }
                }
            }

            val fmessageClass = loadFMessageClass(classLoader)
            if (methodResult == null) {
                val method = findFirstMethodUsingStrings(
                    classLoader,
                    StringMatchType.Contains,
                    "mediaHash and fileType not both present for upload URL generation"
                )
                if (method != null) {
                    val cMethods = bridge.getMethodData(method)!!.invokes
                    Collections.reverse(cMethods)
                    for (cmethod in cMethods) {
                        if (cmethod.isMethod && cmethod.paramCount == 1) {
                            val cParamType = cmethod.paramTypes[0].getInstance(classLoader)
                            if (fmessageClass.isAssignableFrom(cParamType)) {
                                methodResult = cmethod.getMethodInstance(classLoader)
                                break
                            }
                        }
                    }
                }
            }

            methodResult ?: throw RuntimeException("SenderPlayed method not found 2")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadSenderPlayedBusiness(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            val loadSenderPlayed = loadSenderPlayedClass(classLoader)
            ReflectionUtils.findMethodUsingFilter(loadSenderPlayed) { method ->
                method.parameterCount > 0 && method.parameterTypes[0] == Set::class.java
            } ?: throw RuntimeException("SenderPlayedBusiness method not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadMediaTypeField(classLoader: ClassLoader): Field {
        return UnobfuscatorCache.getInstance().getField(classLoader) {
            val methodData = bridge.findMethod {
                matcher {
                    addUsingString("conversation/refresh")
                }
            }
            if (methodData.isEmpty()) throw RuntimeException("MediaType: aux method not found")
            val fclass = bridge.getClassData(loadFMessageClass(classLoader))
            val usingFields = methodData[0].usingFields
            for (f in usingFields) {
                val field = f.field
                if (field.declaredClass == fclass && field.type.name == Int::class.javaPrimitiveType?.name) {
                    return@getField field.getFieldInstance(classLoader)
                }
            }
            throw RuntimeException("MediaType field not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadBubbleDrawableMethod(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            val methodData = bridge.findMethod {
                matcher {
                    addUsingString("Unreachable code: direction=")
                    returnType(Drawable::class.java)
                }
            }
            if (methodData.isEmpty()) throw Exception("BubbleDrawable method not found")
            methodData[0].getMethodInstance(classLoader)
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadBallonDateDrawable(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            val methodData = bridge.findMethod {
                matcher {
                    addUsingString("Unreachable code: direction=")
                    returnType(Rect::class.java)
                }
            }
            if (methodData.isEmpty()) throw Exception("LoadDateWrapper method not found")
            val clazz = methodData[0].getMethodInstance(classLoader).declaringClass
            ReflectionUtils.findMethodUsingFilterIfExists(clazz) { m ->
                listOf(
                    1,
                    2
                ).contains(m.parameterCount) && m.parameterTypes[0] == Int::class.javaPrimitiveType && m.returnType == Drawable::class.java
            } ?: throw RuntimeException("DateWrapper method not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadBallonBorderDrawable(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            val clazz = loadBallonDateDrawable(classLoader).declaringClass
            ReflectionUtils.findMethodUsingFilterIfExists(clazz) { m ->
                m.parameterCount == 3 && m.returnType == Drawable::class.java
            } ?: throw RuntimeException("Ballon Border method not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadRootDetector(classLoader: ClassLoader): Array<Method> {
        return UnobfuscatorCache.getInstance().getMethods(classLoader) {
            val methods =
                findAllMethodUsingStrings(classLoader, StringMatchType.Contains, "/system/bin/su")
            if (methods.isEmpty()) throw RuntimeException("RootDetector method not found")
            methods
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadCheckEmulator(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            findFirstMethodUsingStrings(
                classLoader,
                StringMatchType.Contains,
                "Android SDK built for x86"
            )
                ?: throw RuntimeException("CheckEmulator method not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadCheckCustomRom(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            findFirstMethodUsingStrings(classLoader, StringMatchType.Contains, "cyanogen")
                ?: throw RuntimeException("CheckCustomRom method not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadTranscribeMethod(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            findFirstMethodUsingStrings(
                classLoader,
                StringMatchType.Contains,
                "transcribe: starting transcription"
            )!!
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadCheckSupportLanguage(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            findFirstMethodUsingStrings(
                classLoader,
                StringMatchType.Contains,
                "Unsupported language"
            )!!
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadTranscriptSegment(classLoader: ClassLoader): Class<*> {
        return UnobfuscatorCache.getInstance().getClass(classLoader) {
            findFirstClassUsingStrings(
                classLoader,
                StringMatchType.Contains,
                "TranscriptionSegment("
            )!!
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadStateChangeMethod(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            findFirstMethodUsingStrings(
                classLoader,
                StringMatchType.Contains,
                "presencestatemanager/startTransitionToUnavailable/new-state"
            )!!
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadCachedMessageStoreKey(loader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(loader) {
            findFirstMethodUsingStrings(
                loader,
                StringMatchType.Contains,
                "CachedMessageStore/getMessage/key"
            )
                ?: throw RuntimeException("CachedMessageStore class not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadAbstractMediaMessageClass(loader: ClassLoader): Class<*> {
        return UnobfuscatorCache.getInstance().getClass(loader) {
            for (str in listOf(
                "first_viewed_timestamp",
                "Field is set but is null in MediaDataV2"
            )) {
                val classList = bridge.findClass {
                    matcher {
                        addUsingString(str)
                    }
                }
                for (clazz in classList) {
                    val clazzInstance = clazz.getInstance(loader)
                    if (FMessageWpp.checkUnsafeIsFMessage(
                            loader,
                            clazzInstance
                        )
                    ) return@getClass clazzInstance
                }
            }
            throw ClassNotFoundException("AbstractMediaMessage Not Found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadFragmentClass(classLoader: ClassLoader): Class<*> {
        return UnobfuscatorCache.getInstance().getClass(classLoader) {
            findFirstClassUsingStrings(classLoader, StringMatchType.Contains, "mFragmentId=#")
                ?: throw RuntimeException("Fragment class not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadMediaQualitySelectionMethod(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            var methodData = bridge.findMethod {
                matcher {
                    addUsingString("enable_media_quality_tool")
                    returnType(Boolean::class.javaPrimitiveType!!)
                }
            }
            if (methodData.isEmpty()) {
                methodData = bridge.findMethod {
                    matcher {
                        addUsingString("show_media_quality_toggle")
                        returnType(Boolean::class.javaPrimitiveType!!)
                    }
                }
            }
            if (methodData.isEmpty()) throw RuntimeException("MediaQualitySelection method not found")
            methodData[0].getMethodInstance(classLoader)
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadFmessageTimestampField(classLoader: ClassLoader): Field {
        return UnobfuscatorCache.getInstance().getField(classLoader) {
            val fmessageClass = loadFMessageClass(classLoader)
            val chatLimitDelete2Method = loadChatLimitDelete2Method(classLoader)
            val usingFields = bridge.getMethodData(chatLimitDelete2Method)!!.usingFields
            for (uField in usingFields) {
                val field = uField.field
                if (field.declaredClass.name == fmessageClass.name && field.type.name == Long::class.javaPrimitiveType?.name) {
                    return@getField field.getFieldInstance(classLoader)
                }
            }
            throw RuntimeException("FMessage Timestamp method not found")
        }
    }


    @Throws(Exception::class)
    @JvmStatic
    fun loadFilterItemClass(classLoader: ClassLoader): Class<*> {
        return UnobfuscatorCache.getInstance().getClass(classLoader) {
            var methodList = bridge.findMethod {
                matcher {
                    addUsingNumber(Utils.getID("invisible_height_placeholder", "id"))
                    addUsingNumber(Utils.getID("container_view", "id"))
                }
            }
            if (methodList.isNotEmpty()) return@getClass methodList[0].getClassInstance(classLoader)

            for (s in listOf(
                "ConversationsFilter/selectFilter",
                "has_seen_detected_outcomes_nux"
            )) {
                val applyClazz =
                    findFirstClassUsingStrings(classLoader, StringMatchType.Contains, s) ?: continue
                methodList = bridge.findMethod {
                    matcher {
                        paramTypes(View::class.java, applyClazz)
                    }
                }
                if (methodList.isNotEmpty()) return@getClass methodList[0].getClassInstance(
                    classLoader
                )
            }
            throw RuntimeException("FilterItemClass Not Found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadProximitySensorListenerClasses(classLoader: ClassLoader): Array<Class<*>> {
        return UnobfuscatorCache.getInstance().getClasses(classLoader) {
            val classDataList = bridge.findClass {
                matcher {
                    addInterface(SensorEventListener::class.java.name)
                }
            }
            if (classDataList.isEmpty()) throw Exception("Class SensorEventListener not found")
            classDataList.stream().map { classData -> convertRealClass(classData, classLoader) }
                .filter { it != null }
                .map { it!! }
                .toArray { length -> arrayOfNulls<Class<*>>(length) }
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadRefreshStatusClass(classLoader: ClassLoader): Class<*> {
        return UnobfuscatorCache.getInstance().getClass(classLoader) {
            val keyset = Map::class.java.getDeclaredMethod("keySet")
            val results = bridge.findClass {
                matcher {
                    addMethod {
                        returnType(String::class.java)
                        addInvoke(DexSignUtil.getMethodDescriptor(keyset))
                        addUsingString(",", StringMatchType.Equals)
                        addUsingString("", StringMatchType.Equals)
                    }
                    addMethod {
                        addUsingNumber(0x3684)
                    }
                }
            }
            if (results.isEmpty()) throw RuntimeException("RefreshStatus Class Not Found")
            results[0].getInstance(classLoader)
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadTcTokenMethod(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            findFirstMethodUsingStrings(
                classLoader,
                StringMatchType.Contains,
                "GET_RECEIVED_TOKEN_AND_TIMESTAMP_BY_JID"
            )!!
        }
    }

    @Throws(ClassNotFoundException::class)
    @JvmStatic
    fun getClassByName(className: String, classLoader: ClassLoader): Class<*> {
        if (cacheClasses.containsKey(className)) return cacheClasses[className]!!
        val classDataList = bridge.findClass {
            matcher {
                className(className, StringMatchType.EndsWith)
            }
        }
        if (classDataList.isEmpty()) throw RuntimeException("Class $className not found!")
        val clazz = classDataList[0].getInstance(classLoader)
        cacheClasses[className] = clazz
        return clazz
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadVoipManager(classLoader: ClassLoader): Class<*> {
        return UnobfuscatorCache.getInstance().getClass(classLoader) {
            val voipClass = WppCore.voipManagerClass
            val superClasses = bridge.findClass {
                matcher {
                    superClass(voipClass.name)
                }
            }
            if (superClasses.isEmpty()) throw ClassNotFoundException("VoipManager Class not found")
            for (supclass in superClasses) {
                if (!Modifier.isAbstract(supclass.modifiers)) return@getClass supclass.getInstance(
                    classLoader
                )
            }
            throw ClassNotFoundException("VoipManager Class not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadWaContactClass(classLoader: ClassLoader): Class<*> {
        return UnobfuscatorCache.getInstance().getClass(classLoader) {
            findFirstClassUsingStrings(
                classLoader,
                StringMatchType.Contains,
                "problematic contact:"
            )!!
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadViewAddSearchBarMethod(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            for (str in listOf(
                "HeaderFooterRecyclerViewAdapter/addHeaderViewItemIfNeeded",
                "HeaderFooterRecyclerViewAdapter/addFooterViewItemAtPositionIfNeeded"
            )) {
                val method = findFirstMethodUsingStrings(classLoader, StringMatchType.Contains, str)
                if (method != null) return@getMethod method
            }
            throw RuntimeException("ViewAddSearchBar method not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadAddOptionSearchBarMethod(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            val classData = bridge.getClassData(WppCore.homeActivityClass)
                ?: return@getMethod null
            val methodData = classData.findMethod {
                matcher {
                    addUsingNumber(Utils.getID("menuitem_search", "id"))
                    addUsingNumber(200)
                    paramCount(1)
                    addParamType(Menu::class.java)
                }
            }
            if (methodData.isEmpty()) throw NoSuchMethodError("MenuSearch not found in HomeActivity")
            methodData[0].getMethodInstance(classLoader)
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadAddMenuAndroidX(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            findFirstMethodUsingStrings(
                classLoader,
                StringMatchType.Contains,
                "Maximum number of items supported by"
            )!!
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadConvertLidToJid(loader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(loader) {
            findFirstMethodUsingStrings(
                loader,
                StringMatchType.Contains,
                "WaJidMapRepository/getPhoneJidByAccountUserJid"
            )!!
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadConvertJidToLid(loader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(loader) {
            findFirstMethodUsingStrings(
                loader,
                StringMatchType.Contains,
                "WaJidMapRepository/getAccountUserJidByPhoneJid"
            )!!
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadMeManagerClass(classLoader: ClassLoader): Class<*> {
        return UnobfuscatorCache.getInstance().getClass(classLoader) {
            findFirstClassUsingStrings(classLoader, StringMatchType.StartsWith, "memanager/setMe")
                ?: throw RuntimeException("MeManager class not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadVerifyKeyClass(classLoader: ClassLoader): Class<*> {
        return UnobfuscatorCache.getInstance().getClass(classLoader) {
            val result = bridge.findMethod {
                matcher {
                    addUsingNumber(2966)
                    paramCount(1)
                    addParamType(Int::class.java)
                }
            }
            if (result.isEmpty()) throw RuntimeException("VerifyKey class not found")
            val classList =
                result[0].declaredClass ?: throw ClassNotFoundException("VerifyKey class not found")
            classList.getInstance(classLoader)
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadVerifyKeyRunnableConstructor(classLoader: ClassLoader): Constructor<*> {
        return UnobfuscatorCache.getInstance().getConstructor(classLoader) {
            val data = bridge.findMethod {
                matcher {
                    usingStrings("deviceidentityverifier/verify Primary")
                }
            }.singleOrNull() ?: throw RuntimeException("VerifyKey method not found")
            val clazz = data.declaredClass!!.getInstance(classLoader)
            ReflectionUtils.findConstructorUsingFilter(clazz) { c ->
                c.parameterCount == 2 && c.parameterTypes[0].simpleName == "Object"
            }
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadVerifyKeyInt(classLoader: ClassLoader): Number {
        return UnobfuscatorCache.getInstance().getNumber(classLoader) {
            val method = loadVerifyKeyItemConstructor(classLoader)
            val callers = bridge.getMethodData(method)!!.callers
            val resultMethod = callers.stream().filter { i ->
                i.isMethod && i.declaredClassName.contains("IdentityVerificationActivity")
            }.findFirst().orElse(null)
                ?: throw RuntimeException("VerifyKey method caller not found")
            val usingNumbers = resultMethod.usingNumbers
            var findMagicNumber = false
            for (i in usingNumbers.indices) {
                val n = usingNumbers[i]
                if (n.toInt() == 2966) {
                    findMagicNumber = true
                } else if (findMagicNumber) {
                    return@getNumber n
                }
            }
            throw RuntimeException("VerifyKey int not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadVerifyKeyItemConstructor(classLoader: ClassLoader): Constructor<*> {
        return UnobfuscatorCache.getInstance().getConstructor(classLoader) {
            val clazz = bridge.findClass {
                matcher {
                    className("IdentityVerificationActivity", StringMatchType.EndsWith)
                }
            }.singleOrNull() ?: throw RuntimeException("IdentityVerificationActivity not found")
            val methodResult = bridge.findMethod {
                searchInClass(listOf(clazz))
                matcher {
                    addUsingNumber(2966)
                }
            }.singleOrNull() ?: throw RuntimeException("VerifyKey item constructor base not found")

            for (invoke in methodResult.invokes) {
                if (!invoke.isConstructor) continue
                val paramTypes = invoke.paramTypes
                if (paramTypes.size != 2) continue
                if (paramTypes[1].simpleName != "List") continue
                return@getConstructor invoke.getConstructorInstance(classLoader)
            }
            throw RuntimeException("VerifyKey constructor not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadMySearchBarMethod(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            findFirstMethodUsingStrings(
                classLoader,
                StringMatchType.EndsWith,
                "search_bar_render_start"
            )
                ?: throw NoSuchMethodException("MySearchBar method not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadAdVerifyMethod(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            val method = bridge.findMethod {
                matcher {
                    usingStrings("is_wfal_paused")
                    paramCount(1)
                }
            }.singleOrNull() ?: throw NoSuchMethodException("loadAdVerify Not Found")
            method.getMethodInstance(classLoader)
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadChatFilterView(classLoader: ClassLoader): Class<*>? {
        return UnobfuscatorCache.getInstance().getClass(classLoader) {
            val value = Utils.getID("conversations_inbox_filters_stub", "id")
            val clazz = bridge.findClass {
                matcher {
                    addMethod {
                        addUsingNumber(value)
                    }
                }
            }.singleOrNull() ?: return@getClass null
            clazz.getInstance(classLoader)
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadNotificationMethod(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            val invokedMethod = bridge.findMethod {
                matcher {
                    addUsingString("LastMessageStore/getLastMessagesForNotificationAfterReply")
                }
            }.singleOrNull() ?: throw RuntimeException("Notification invoked method not found")
            invokedMethod.getMethodInstance(classLoader)
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadLockedChatsMethod(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            val classData = bridge.findClass {
                matcher {
                    addUsingString("conversationsmgr/replacecontact")
                }
            }.singleOrNull() ?: throw RuntimeException("ConversationsManager class not found")

            val invokedMethod = bridge.getMethodData(loadNotificationMethod(classLoader))
            for (invoke in invokedMethod!!.invokes) {
                if (!invoke.isMethod) continue
                if (invoke.className != classData.name) continue
                if (invoke.returnType?.name != ArrayList::class.java.name) continue
                return@getMethod invoke.getMethodInstance(classLoader)
            }
            throw RuntimeException("LockedChats method not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadGetProfilePhotoMethod(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            findFirstMethodUsingStrings(
                classLoader,
                StringMatchType.Contains,
                "contactPhotosBitmapManager/getphotostream/"
            )!!
        }
    }


    @Throws(Exception::class)
    @JvmStatic
    fun loadChatCacheClass(classLoader: ClassLoader): Class<*> {
        return UnobfuscatorCache.getInstance().getClass(classLoader) {
            findFirstClassUsingStrings(classLoader, StringMatchType.StartsWith, "Chatscache/")!!
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadLoadedContactsMethod(classLoader: ClassLoader): Method? {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            val methods = bridge.findMethod {
                matcher {
                    addUsingNumber(8726)
                    paramCount(1)
                    addParamType(Any::class.java)
                }
            }
            if (methods.isEmpty()) return@getMethod null
            methods[0].getMethodInstance(classLoader)
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadVideoTranscoderStartMethod(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            findFirstMethodUsingStrings(
                classLoader,
                StringMatchType.Contains,
                "VideoTranscoder/transcodeVideoNew/"
            )!!
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadWaContactGetWaNameField(classLoader: ClassLoader): Field {
        return UnobfuscatorCache.getInstance().getField(classLoader) {
            val method = bridge.findMethod {
                matcher {
                    addUsingString("ContactManagerDatabase/updateContactWAName")
                }
            }.singleOrNull() ?: throw NoSuchMethodException("WaContactGetWaName field not found")

            val waContact = loadWaContactClass(classLoader).name
            val usingFields = method.usingFields
            for (usingField in usingFields) {
                val field = usingField.field
                if (field.className == waContact && field.type.name == String::class.java.name) {
                    return@getField field.getFieldInstance(classLoader)
                }
            }
            val waContactData = loadWaContactDataClass(classLoader).name
            for (usingField in usingFields) {
                val field = usingField.field
                if (field.className == waContactData && field.type.name == String::class.java.name) {
                    return@getField field.getFieldInstance(classLoader)
                }
            }
            throw NoSuchMethodException("WaContactGetWaName field not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadWaContactDataClass(classLoader: ClassLoader): Class<*> {
        return UnobfuscatorCache.getInstance().getClass(classLoader) {
            findFirstClassUsingStrings(classLoader, StringMatchType.EndsWith, "WaContactData")!!
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadWaContactDataDisplayNameMethod(classLoader: ClassLoader): Field? {
        return UnobfuscatorCache.getInstance().getField(classLoader) {
            val methods = bridge.findMethod {
                matcher {
                    addUsingString("ContactManagerDatabase/updateGroupInfo")
                }
            }
            if (methods.isEmpty()) throw NoSuchMethodException("WaContactDiplayName not found")

            val waContactDataClassName = loadWaContactDataClass(classLoader).name
            val waContactClassName = loadWaContactClass(classLoader).name
            val invokes = methods[0].invokes
            for (invoke in invokes) {
                if (invoke.className != waContactClassName) continue
                if (invoke.returnTypeName == String::class.java.name) {
                    for (usingFieldData in invoke.usingFields) {
                        if (usingFieldData.field.declaredClassName != waContactDataClassName) continue
                        if (usingFieldData.field.typeName == String::class.java.name) {
                            return@getField usingFieldData.field.getFieldInstance(classLoader)
                        }
                    }
                }
            }
            for (usingFieldData in methods[0].usingFields) {
                if (usingFieldData.field.declaredClassName != waContactDataClassName) continue
                if (usingFieldData.field.typeName == String::class.java.name) {
                    return@getField usingFieldData.field.getFieldInstance(classLoader)
                }
            }
            null
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadGetWaContactMethod(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            findFirstMethodUsingStrings(
                classLoader,
                StringMatchType.Contains,
                "ContactManager/getContactFromCacheOrDbByJid"
            )!!
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadSharedPreferencesClasses(classLoader: ClassLoader): Array<Class<*>>? {
        return UnobfuscatorCache.getInstance().getClasses(classLoader) {
            val classesData = bridge.findClass {
                matcher {
                    addInterface(SharedPreferences::class.java.name)
                }
            }
            if (classesData.isEmpty()) return@getClasses null
            classesData.stream().map { classData -> convertRealClass(classData, classLoader) }
                .toArray { length -> arrayOfNulls<Class<*>>(length) }
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadPinnedFilterMethod(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            val method = findFirstMethodUsingStrings(
                classLoader,
                StringMatchType.Contains,
                "pinSelectedJids"
            ) ?: return@getMethod null
            val methodData = bridge.getMethodData(method)
            for (invoke in methodData!!.invokes) {
                if (!invoke.isMethod) continue
                val methodInstance = invoke.getMethodInstance(classLoader)
                if (!Set::class.java.isAssignableFrom(methodInstance.returnType)) continue
                if (methodInstance.parameterCount == 2 && methodInstance.parameterTypes[0] == Iterable::class.java && methodInstance.parameterTypes[1] == Set::class.java) {
                    return@getMethod methodInstance
                }
            }
            throw NoSuchMethodException("PinnedLinkedHashMethod not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadSetPinnedLimitMethod(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            findFirstMethodUsingStrings(
                classLoader,
                StringMatchType.Contains,
                "ChatSettingsStore/setPin"
            )
                ?: throw NoSuchMethodException("SetPinnedLimit method not found")
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun getAllMapFields(clazz: Class<*>): HashMap<String, Field> {
        val cache = UnobfuscatorCache.getInstance()
        val classLoader = clazz.classLoader
        if (cache != null && classLoader != null) {
            val cacheKey = "getAllMapFields:" + clazz.name
            return cache.getMapField(classLoader, cacheKey) { buildAllMapFields(clazz) }
        }
        return buildAllMapFields(clazz)
    }

    @Throws(Exception::class)
    private
    @JvmStatic
    fun buildAllMapFields(clazz: Class<*>): HashMap<String, Field> {
        val methodString = try {
            clazz.getDeclaredMethod("toString")
        } catch (_: Exception) {
            return HashMap()
        }
        val methodData = bridge.getMethodData(methodString) ?: return HashMap()
        val usingFields = methodData.usingFields
        val usingStrings = methodData.usingStrings
        val result = HashMap<String, Field>()
        var idxFields = 0
        for (i in usingStrings.indices) {
            if (idxFields == usingFields.size) break
            val raw = usingStrings[i]
            val string = raw.trim()
            if (string.isEmpty()) continue
            val eq = string.lastIndexOf('=')
            if (eq < 0) continue
            var start = 0
            for (j in eq - 1 downTo 0) {
                val c = string[j]
                if (c == '\'' || c == ',' || c == ' ' || c == '(' || c == ')' || c == ':' || c == '{' || c == '}') {
                    start = j + 1
                    break
                }
            }
            if (start >= eq) continue
            val name = string.substring(start, eq)
            val field = usingFields[idxFields].field.getFieldInstance(clazz.classLoader!!)
            result[name] = field
            idxFields++
        }
        return result
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadMediaDataVideoConfigurationClass(classLoader: ClassLoader): Class<*> {
        return UnobfuscatorCache.getInstance().getClass(classLoader) {
            findFirstClassUsingStrings(
                classLoader,
                StringMatchType.Contains,
                "MediaDataVideoConfiguration("
            )!!
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadStatusStyleMethod(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            val method = bridge.findMethod {
                matcher {
                    addUsingNumber(8522)
                    returnType(Int::class.javaPrimitiveType!!)
                }
            }.singleOrNull() ?: throw NoSuchMethodException("StatusStyle method not found")
            method.getMethodInstance(classLoader)
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadProcessImageQualityClass(classLoader: ClassLoader): Class<*> {
        return UnobfuscatorCache.getInstance().getClass(classLoader) {
            val classDataList = bridge.findClass {
                matcher {
                    addUsingString("ProcessImageQuality(", StringMatchType.StartsWith)
                }
            }
            if (classDataList.isEmpty()) throw RuntimeException("ProcessImageQuality class not found")
            classDataList[0].getInstance(classLoader)
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadGetProfilePhoto(classLoader: ClassLoader): Class<*> {
        return UnobfuscatorCache.getInstance().getClass(classLoader) {
            findFirstClassUsingStrings(
                classLoader,
                StringMatchType.Contains,
                "ProfilePhotoManager/sendGetProfilePhoto"
            )!!
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadDialerProfilePictureLoader(classLoader: ClassLoader): Class<*> {
        return UnobfuscatorCache.getInstance().getClass(classLoader) {
            findFirstClassUsingStrings(
                classLoader,
                StringMatchType.Contains,
                "DialerProfilePictureLoader/syncFetchProfilePhoto/onPhotoReceived"
            )!!
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadBottomBarConfigClass(classLoader: ClassLoader): Class<*> {
        return UnobfuscatorCache.getInstance().getClass(classLoader) {
            findFirstClassUsingStrings(classLoader, StringMatchType.Contains, "BottomBarConfig(")!!
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadOnCreatedMenuConversation(loader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(loader) {
            val conversationClass =
                findFirstClassUsingName(loader, StringMatchType.EndsWith, "Conversation")
            ReflectionUtils.findMethodUsingFilter(conversationClass) { m -> m.parameterCount == 1 && m.parameterTypes[0] == Menu::class.java }!!
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadFStatusKeyClass(classLoader: ClassLoader): Class<*> {
        return UnobfuscatorCache.getInstance().getClass(classLoader) {
            bridge.findClass {
                matcher {
                    addUsingString("Key(id=")
                    addUsingString("senderJid")
                }
            }.first().getInstance(classLoader)
        }
    }

    @Throws(Exception::class)

    @JvmStatic
    fun loadFStatusClass(classLoader: ClassLoader): Class<*> {
        return UnobfuscatorCache.getInstance().getClass(classLoader) {
            findFirstClassUsingStrings(classLoader, StringMatchType.Contains, "FStatus state")!!
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadAntiRevokeFStatusMethod(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            val fStatusKeyClass = loadFStatusKeyClass(classLoader)
            val clazz = findFirstClassUsingStrings(
                classLoader,
                StringMatchType.Contains,
                "RevokeStatusManager/failed"
            )
            ReflectionUtils.findMethodUsingFilter(clazz) { method ->
                method.parameterCount > 0 && fStatusKeyClass.isAssignableFrom(method.parameterTypes[0])
            }!!
        }
    }

    @Throws(Exception::class)

    @JvmStatic
    fun loadGetStatusByKey(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            findFirstMethodUsingStrings(
                classLoader,
                StringMatchType.Contains,
                "StatusStore/GET_STATUS_BY_KEY"
            )!!
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadFStatusToFMessage(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            findFirstMethodUsingStrings(
                classLoader,
                StringMatchType.Contains,
                "mapFStatusToFMessageForForwarding"
            )!!
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadStatusPlaybackReplyContainer(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            val clazz = findFirstClassUsingStrings(
                classLoader,
                StringMatchType.Contains,
                "replyContainer",
                "bottomSheet",
                "contentSheet"
            ) ?: return@getMethod null
            val clazzData = bridge.getClassData(clazz)
            val methodData = clazzData!!.findMethod {
                matcher {
                    addUsingString("replyContainer")
                }
            }
            if (methodData.isEmpty()) throw RuntimeException("StatusPlaybackReply method not found")
            methodData[0].getMethodInstance(classLoader)
        }
    }

    @Throws(Exception::class)

    @JvmStatic
    fun loadProtocolTreeNodeClass(classLoader: ClassLoader): Class<*> {
        return UnobfuscatorCache.getInstance().getClass(classLoader) {
            findFirstClassUsingStrings(
                classLoader,
                StringMatchType.Contains,
                "ProtocolTreeNode/getAttributeJid"
            )!!
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadKeyValueClass(classLoader: ClassLoader): Class<*> {
        return UnobfuscatorCache.getInstance().getClass(classLoader) {
            findFirstClassUsingStrings(classLoader, StringMatchType.Contains, "KeyValue{key=")!!
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadLockedAuthCheckMethod(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            findFirstMethodUsingStrings(
                classLoader,
                StringMatchType.Contains,
                "privacy_fingerprint_enabled",
                "app_lock_auth_needed"
            )!!
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadGetCurrentPageInHomeField(classLoader: ClassLoader): Field {
        return UnobfuscatorCache.getInstance().getField(classLoader) {
            val method = bridge.getMethodData(loadAddOptionSearchBarMethod(classLoader))
            for (uField in method!!.usingFields) {
                if (uField.field.declaredClassName == method.declaredClassName && uField.field.typeName == "int")
                    return@getField uField.field.getFieldInstance(classLoader)
            }
            throw NoSuchFieldException("CurrentPageInHome field not found")
        }
    }

    @Throws(Exception::class)

    @JvmStatic
    fun loadFMediaStatusClass(classLoader: ClassLoader): Class<*> {
        return UnobfuscatorCache.getInstance().getClass(classLoader) {
            findFirstClassUsingStrings(
                classLoader,
                StringMatchType.Contains,
                "FStatusMedia/mediaDataV2"
            )!!
        }
    }

    @Throws(Exception::class)

    @JvmStatic
    fun loadWaContactNumberField(classLoader: ClassLoader): Field? {
        return UnobfuscatorCache.getInstance().getField(classLoader) {
            val waContact = loadWaContactClass(classLoader)
            val waContactData = bridge.getClassData(waContact)
                ?: throw NoSuchFieldException("WaContact class data not found")

            val methodData = waContactData.findMethod {
                matcher {
                    usingNumbers(-4, 0)
                    returnType(Long::class.javaPrimitiveType!!)
                }
            }.firstOrNull() ?: throw NoSuchFieldException("Number Method not found!")

            for (ufield in methodData.usingFields) {
                val field = ufield.field.getFieldInstance(classLoader)
                if (field.declaringClass == waContact && !field.type.isPrimitive) {
                    return@getField field
                }
            }
            null
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadSeenReceiptForStatus(classLoader: ClassLoader): Method {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            findFirstMethodUsingStrings(
                classLoader,
                StringMatchType.Contains,
                "StatusReceiptStore/insertOrUpdateSeenReceiptForStatus"
            )!!
        }
    }

    @Throws(Exception::class)
    @JvmStatic
    fun loadOnConversationsListChangedMethod(classLoader: ClassLoader): Method? {
        return UnobfuscatorCache.getInstance().getMethod(classLoader) {
            findFirstMethodUsingStringsFilter(
                classLoader,
                "com.whatsapp.conversationslist",
                StringMatchType.Contains,
                "onConversationsListChanged"
            )
        }
    }

    fun loadMultiSelectionLimitInfoClass(classLoader: ClassLoader): Class<*> {
        return UnobfuscatorCache.getInstance().getClass(classLoader) {
            bridge.findClass {
                matcher {
                    usingStrings("MultiSelectionLimitInfo")
                }
            }.single().getInstance(classLoader)
        }
    }


    fun loadOndispatchMessage(classLoader: ClassLoader): Array<Method> {
        return UnobfuscatorCache.getInstance().getMethods(classLoader){
            val result = bridge.findMethod {
                matcher {
                    usingNumbers(419)
                    paramCount(1,3)
                }
            }.filter { !it.paramTypeNames.isEmpty() && it.paramTypeNames[0].contains("Message") }
                .map { it.getMethodInstance(classLoader) }.toTypedArray()
            if (result.isEmpty())return@getMethods null
            result
        }

    }
}