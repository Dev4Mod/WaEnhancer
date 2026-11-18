package com.wmods.wppenhacer.xposed.features.privacy

import android.app.Activity
import android.content.SharedPreferences
import androidx.viewpager.widget.ViewPager
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.extension.kavaref.hookAll
import com.highcapable.yukihookapi.extension.kavaref.toClass
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp.UserJid
import com.wmods.wppenhacer.xposed.core.components.StatusItemWpp
import com.wmods.wppenhacer.xposed.core.components.WaContactWpp
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import org.luckypray.dexkit.query.enums.StringMatchType
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

class LockedChatsEnhancer(classLoader: ClassLoader, preferences: SharedPreferences) :
    Feature(classLoader, preferences) {

    private var chatCache: Any? = null
    private var carouselItemKeyMethod: Method? = null
    private val callItemJidMethods = ConcurrentHashMap<Class<*>, Method>()
    private val rawJidMethods = ConcurrentHashMap<Class<*>, Method>()
    private val pendingInitialItem = Collections.synchronizedMap(WeakHashMap<Any, Int>())

    override fun doHook() {
        if (!xprefs.getBoolean("lockedchats_enhancer", false)) return

        val jidNotifications = Unobfuscator.loadNotificationMethod(classLoader)
        val lockedChatsMethod = Unobfuscator.loadLockedChatsMethod(classLoader)
        val suppressLockedChats = ThreadLocal.withInitial { false }

        jidNotifications.hook {
            before {
                suppressLockedChats.set(true)
            }
            after {
                suppressLockedChats.remove()
            }
        }

        lockedChatsMethod.hook {
            before {
                if (suppressLockedChats.get() == true) {
                    result = ArrayList<Any?>()
                }
            }
        }

        val chatCacheClass = Unobfuscator.loadChatCacheClass(classLoader)
        val lockedChatsFields = ReflectionUtils.findAllFieldsUsingFilter(chatCacheClass) { f ->
            f.type == HashSet::class.java
        }
        val lockedChatsField =
            lockedChatsFields.getOrNull(1) ?: lockedChatsFields.firstOrNull() ?: return
        lockedChatsField.isAccessible = true

        chatCacheClass.resolve().constructor { }.hookAll {
            after {
                chatCache = instance
            }
        }
        chatCacheClass.declaredMethods.firstOrNull { !Modifier.isStatic(it.modifiers) }?.hook {
            before {
                if (chatCache == null) {
                    chatCache = instance
                }
            }
        }

        runCatching {
            Unobfuscator.loadCallsHistoryBuildMethod(classLoader).hook {
                before {
                    val lockedIdentifiers = getLockedIdentifiers(lockedChatsField)
                    if (lockedIdentifiers.isEmpty()) return@before
                    val history = args.getOrNull(2)
                    val filteredHistory = filterCallHistory(history, lockedIdentifiers)
                    if (filteredHistory !== history) {
                        args[2] = filteredHistory
                    }
                }
            }
        }.onFailure { logDebug(it) }

        Unobfuscator.loadLoadedContactsMethod(classLoader)?.hook {
            before {
                val model = args[0] ?: return@before
                val listFields = ReflectionUtils.getDeclaredFieldsByExtendType(
                    model.javaClass, List::class.java, instanceOnly = true
                )
                if (listFields.isEmpty()) return@before
                val lockedIdentifiers = getLockedIdentifiers(lockedChatsField)
                if (lockedIdentifiers.isEmpty()) return@before
                for (listField in listFields) {
                    val list = listField.get(model) as? List<*> ?: continue
                    if (list.none { WaContactWpp.TYPE.isInstance(it) }) continue
                    val filteredList = list.filter {
                        if (!WaContactWpp.TYPE.isInstance(it)) return@filter true
                        val userJid = WaContactWpp(it).userJid
                        !isUserLocked(userJid, lockedIdentifiers)
                    }
                    if (filteredList.size != list.size) {
                        listField.set(model, filteredList)
                    }
                }
            }
        }

        runCatching {
            val statusListUpdatesClass = Unobfuscator.loadStatusListUpdatesClass(classLoader)
            statusListUpdatesClass.resolve().constructor {}.hookAll {
                before {
                    val lockedIdentifiers = getLockedIdentifiers(lockedChatsField)
                    if (lockedIdentifiers.isEmpty()) return@before

                    val listIndices = ArrayList<Int>()
                    for (i in args.indices) {
                        val list = args[i] as? List<*> ?: continue
                        listIndices.add(i)

                        @Suppress("UNCHECKED_CAST")
                        val mutableList = list as? MutableList<Any?>
                        var mutated = false
                        if (mutableList != null) {
                            try {
                                val iterator = mutableList.iterator()
                                while (iterator.hasNext()) {
                                    val item = iterator.next()
                                    if (isStatusItemLocked(item, lockedIdentifiers)) {
                                        iterator.remove()
                                    }
                                }
                                mutated = true
                            } catch (_: Throwable) {
                                mutated = false
                            }
                        }
                        if (!mutated) {
                            val filtered = list.filter { item ->
                                if (item == null) return@filter true
                                !isStatusItemLocked(item, lockedIdentifiers)
                            }
                            args[i] = filtered
                        }
                    }

                    if (listIndices.size >= 3) {
                        val mutedList = args[listIndices[2]] as? List<*>
                        if (mutedList != null && mutedList.isEmpty() && args.size > 3) {
                            val entrypoint = args[3]
                            if (entrypoint != null && entrypoint !is List<*> && entrypoint !is Boolean) {
                                args[3] = null
                            }
                        }
                    }
                }
            }
        }.onFailure { logDebug(it) }

        hookStatusPlaybackCarousel(lockedChatsField)
    }

    private fun hookStatusPlaybackCarousel(lockedChatsField: Field) {
        val statusPlaybackActivityClass = runCatching {
            Unobfuscator.findFirstClassUsingName(
                classLoader,
                StringMatchType.EndsWith,
                "StatusPlaybackActivity"
            )
        }.getOrNull()

        val statusPlaybackNavigationMethod = runCatching {
            Unobfuscator.loadStatusPlaybackNavigationMethod(classLoader)
        }.getOrNull()
        val statusPlaybackCarouselField = runCatching {
            Unobfuscator.loadStatusPlaybackCarouselField(classLoader).apply { isAccessible = true }
        }.getOrNull()
        carouselItemKeyMethod =
            Unobfuscator.loadStatusPlaybackCarouselItemKeyMethod(classLoader)

        statusPlaybackActivityClass?.let { actClass ->
            actClass.declaredMethods.filter { m ->
                !Modifier.isStatic(m.modifiers) && m.parameterTypes.any {
                    List::class.java.isAssignableFrom(
                        it
                    )
                }
            }.forEach { method ->
                method.hook {
                    before {
                        val lockedIdentifiers = getLockedIdentifiers(lockedChatsField)
                        if (lockedIdentifiers.isEmpty()) return@before
                        val activity = instanceOrNull<Activity>()
                        filterMethodArgsLists(
                            args,
                            lockedIdentifiers,
                            activity?.let { getAllowedStatusJid(it, lockedIdentifiers) },
                        )
                    }
                }
            }

            statusPlaybackNavigationMethod?.hook {
                before {
                    val activity = instanceOrNull<Activity>() ?: return@before
                    val lockedIdentifiers = getLockedIdentifiers(lockedChatsField)
                    if (lockedIdentifiers.isEmpty()) return@before
                    filterObjectFromField(
                        activity,
                        statusPlaybackCarouselField,
                        lockedIdentifiers,
                        getAllowedStatusJid(activity, lockedIdentifiers),
                    )
                }
            }
        }

        val viewPagerClass = runCatching {
            "androidx.viewpager.widget.ViewPager".toClass(classLoader)
        }.getOrNull()

        viewPagerClass?.resolve()?.method {
            name = "setAdapter"
        }?.hookAll {
            before {
                val adapter = args.getOrNull(0) ?: return@before
                val currentActivity = WppCore.getCurrentActivity() ?: return@before
                if (statusPlaybackActivityClass?.isInstance(currentActivity) != true) return@before
                val lockedIdentifiers = getLockedIdentifiers(lockedChatsField)
                if (lockedIdentifiers.isEmpty()) return@before
                val allowedJid = getAllowedStatusJid(currentActivity, lockedIdentifiers)
                val jid = currentActivity.intent.extras?.getString("jid")

                val carousel = try {
                    statusPlaybackCarouselField?.let {
                        it.isAccessible = true
                        it.get(currentActivity)
                    }
                } catch (_: Throwable) {
                    null
                }
                val carouselList = getCarouselList(carousel)
                val oldTargetIndex =
                    jid?.let { carouselList?.let { list -> findStatusItemIndex(list, it) } } ?: -1

                filterObjectLists(adapter, lockedIdentifiers, allowedJid)
                filterObjectFromField(
                    currentActivity,
                    statusPlaybackCarouselField,
                    lockedIdentifiers,
                    allowedJid,
                )

                if (jid != null && carouselList != null) {
                    val newTargetIndex = findStatusItemIndex(carouselList, jid)
                    if (newTargetIndex >= 0) {
                        pendingInitialItem[instance] = newTargetIndex
                        if (oldTargetIndex >= 0 && oldTargetIndex != newTargetIndex) {
                            syncViewModelInitialIndex(
                                currentActivity,
                                oldTargetIndex,
                                newTargetIndex
                            )
                        }
                    } else if (isRawJidLocked(jid, lockedIdentifiers) && allowedJid == null) {
                        currentActivity.finish()
                    }
                }
            }
            after {
                val viewPager = instanceOrNull<ViewPager>() ?: return@after
                viewPager.post {
                    val targetIndex = pendingInitialItem.remove(viewPager)
                    if (targetIndex != null && viewPager.currentItem != targetIndex) {
                        viewPager.setCurrentItem(targetIndex, false)
                    }
                }
            }
        }

        viewPagerClass?.resolve()?.method {
            name = "setCurrentItem"
            parameters(Int::class)
        }?.hookAll {
            before {
                val targetIndex = pendingInitialItem.remove(instance) ?: return@before
                args[0] = targetIndex
            }
        }

        viewPagerClass?.resolve()?.optional(silent = true)?.method {
            name = "setCurrentItem"
            parameters(Int::class, Boolean::class)
        }?.hookAll {
            before {
                val targetIndex = pendingInitialItem.remove(instance) ?: return@before
                args[0] = targetIndex
            }
        }

        val pagerAdapterClass = runCatching {
            "androidx.viewpager.widget.PagerAdapter".toClass(classLoader)
        }.getOrNull()

        pagerAdapterClass?.resolve()?.method {
            name = "notifyDataSetChanged"
        }?.hookAll {
            before {
                val currentActivity = WppCore.getCurrentActivity() ?: return@before
                if (statusPlaybackActivityClass?.isInstance(currentActivity) != true) return@before
                val lockedIdentifiers = getLockedIdentifiers(lockedChatsField)
                if (lockedIdentifiers.isEmpty()) return@before
                val allowedJid = getAllowedStatusJid(currentActivity, lockedIdentifiers)
                filterObjectLists(instance, lockedIdentifiers, allowedJid)
                filterObjectFromField(
                    currentActivity,
                    statusPlaybackCarouselField,
                    lockedIdentifiers,
                    allowedJid,
                )
            }
        }
    }

    private fun getCarouselList(carousel: Any?): MutableList<Any?>? {
        if (carousel == null) return null
        for (field in getDeclaredFieldsSafely(carousel.javaClass)) {
            if (Modifier.isStatic(field.modifiers)) continue
            val value = try {
                field.isAccessible = true
                field.get(carousel)
            } catch (_: Throwable) {
                null
            }
            if (value is MutableList<*>) {
                @Suppress("UNCHECKED_CAST")
                return value as MutableList<Any?>
            }
        }
        return null
    }

    private fun getJidFromCarouselItem(item: Any?): String? {
        if (item == null) return null
        val itemKey = carouselItemKeyMethod
            ?.takeIf { it.declaringClass.isInstance(item) }
            ?.let { method -> runCatching { method.invoke(item) as? String }.getOrNull() }
        if (itemKey != null) {
            val jid = itemKey.substringBefore('_')
            if (jid.endsWith("@s.whatsapp.net") || jid.endsWith("@lid") || jid == "status@broadcast") {
                return jid
            }
        }
        val raw = getRawJidFromStatusItem(item)
        if (raw != null) return raw
        return getUserJidFromStatusItem(item)?.let { it.userRawString ?: it.phoneRawString }
    }

    private fun findStatusItemIndex(list: List<*>, targetJid: String): Int {
        val normalizedTarget = normalizeRawJid(targetJid)
        for (i in list.indices) {
            val item = list[i] ?: continue
            val jid = getJidFromCarouselItem(item)
            if (jid != null && normalizeRawJid(jid) == normalizedTarget) {
                return i
            }
        }
        return -1
    }

    private fun getStatusPlaybackLvs(activity: Activity): Any? {
        val b7w = runCatching {
            activity.javaClass.methods.firstOrNull {
                !Modifier.isStatic(it.modifiers) &&
                        it.parameterCount == 0 &&
                        it.returnType != Void.TYPE &&
                        !it.returnType.isPrimitive &&
                        it.name == "B7W"
            }?.invoke(activity)
        }.getOrNull()
        if (b7w != null) return b7w

        for (field in getDeclaredFieldsSafely(activity.javaClass)) {
            if (Modifier.isStatic(field.modifiers)) continue
            val value = try {
                field.isAccessible = true
                field.get(activity)
            } catch (_: Throwable) {
                null
            } ?: continue
            val className = value.javaClass.name
            if (!className.startsWith("android.") && !className.startsWith("java.") &&
                !className.startsWith("androidx.viewpager") &&
                (value.javaClass.interfaces.isNotEmpty() || className.contains(
                    "StatusPlayback",
                    ignoreCase = true
                ))
            ) {
                val hasIntMethods = value.javaClass.methods.any {
                    !Modifier.isStatic(it.modifiers) && it.parameterCount == 0 && it.returnType == Int::class.javaPrimitiveType
                }
                if (hasIntMethods) return value
            }
        }
        return null
    }

    private fun syncViewModelInitialIndex(activity: Activity, oldIndex: Int, newIndex: Int) {
        if (oldIndex == newIndex) return
        val lvs = getStatusPlaybackLvs(activity) ?: return
        val candidateMethods = lvs.javaClass.methods.filter {
            !Modifier.isStatic(it.modifiers) &&
                    it.parameterCount == 0 &&
                    it.returnType == Int::class.javaPrimitiveType &&
                    it.declaringClass != Any::class.java
        }
        val matchingMethod = candidateMethods.firstOrNull {
            runCatching { it.invoke(lvs) == oldIndex }.getOrDefault(false)
        }

        if (matchingMethod != null) {
            for (field in getDeclaredFieldsSafely(lvs.javaClass)) {
                if (Modifier.isStatic(field.modifiers)) continue
                if (field.type == Int::class.javaPrimitiveType) {
                    try {
                        field.isAccessible = true
                        if (field.getInt(lvs) == oldIndex) {
                            val testVal = 999999
                            field.setInt(lvs, testVal)
                            val returnsTestVal =
                                runCatching { matchingMethod.invoke(lvs) == testVal }.getOrDefault(
                                    false
                                )
                            field.setInt(lvs, if (returnsTestVal) newIndex else oldIndex)
                            if (returnsTestVal) break
                        }
                    } catch (_: Throwable) {
                    }
                }
            }
        } else {
            for (field in getDeclaredFieldsSafely(lvs.javaClass)) {
                if (Modifier.isStatic(field.modifiers)) continue
                if (field.type == Int::class.javaPrimitiveType) {
                    try {
                        field.isAccessible = true
                        if (field.getInt(lvs) == oldIndex) {
                            field.setInt(lvs, newIndex)
                        }
                    } catch (_: Throwable) {
                    }
                }
            }
        }

        for (field in getDeclaredFieldsSafely(activity.javaClass)) {
            if (Modifier.isStatic(field.modifiers)) continue
            if (field.type == Int::class.javaPrimitiveType) {
                try {
                    field.isAccessible = true
                    if (field.getInt(activity) == oldIndex) {
                        field.setInt(activity, newIndex)
                    }
                } catch (_: Throwable) {
                }
            }
        }
    }

    private fun getAllowedStatusJid(
        activity: Activity,
        lockedIdentifiers: Set<String>,
    ): String? {
        val jid = activity.intent.extras?.getString("jid") ?: return null
        return jid.takeIf { isRawJidLocked(it, lockedIdentifiers) }
    }

    private fun filterObjectFromField(
        target: Any?,
        field: Field?,
        lockedIdentifiers: Set<String>,
        allowedJid: String?,
    ): Boolean {
        if (target == null || field == null) return false
        val value = try {
            field.isAccessible = true
            field.get(target)
        } catch (_: Throwable) {
            null
        }
        return filterObjectLists(value, lockedIdentifiers, allowedJid)
    }

    private fun filterObjectLists(
        target: Any?,
        lockedIdentifiers: Set<String>,
        allowedJid: String?,
    ): Boolean {
        if (target == null) return false
        var changed = false
        var clazz: Class<*>? = target.javaClass
        while (clazz != null && clazz != Any::class.java) {
            for (field in getDeclaredFieldsSafely(clazz)) {
                if (Modifier.isStatic(field.modifiers)) continue
                val value = try {
                    field.isAccessible = true
                    field.get(target)
                } catch (_: Throwable) {
                    null
                } ?: continue

                if (value is List<*>) {
                    val filtered = value.filter { item ->
                        if (item == null) return@filter true
                        !isStatusItemLocked(item, lockedIdentifiers, allowedJid)
                    }
                    if (filtered.size != value.size) {
                        var mutated = false
                        if (value is MutableList<*>) {
                            try {
                                val it = value.iterator()
                                while (it.hasNext()) {
                                    val item = it.next()
                                    if (isStatusItemLocked(item, lockedIdentifiers, allowedJid)) {
                                        it.remove()
                                    }
                                }
                                mutated = true
                                changed = true
                            } catch (_: Throwable) {
                                mutated = false
                            }
                        }
                        if (!mutated) {
                            try {
                                field.set(target, filtered)
                                changed = true
                            } catch (_: Throwable) {
                            }
                        }
                    }
                }
            }
            clazz = clazz.superclass
        }
        return changed
    }

    private fun filterMethodArgsLists(
        args: Array<Any?>,
        lockedIdentifiers: Set<String>,
        allowedJid: String? = null,
    ) {
        for (i in args.indices) {
            val list = args[i] as? List<*> ?: continue
            val filtered = list.filter { item ->
                if (item == null) return@filter true
                !isStatusItemLocked(item, lockedIdentifiers, allowedJid)
            }
            if (filtered.size != list.size) {
                var mutated = false
                if (list is MutableList<*>) {
                    try {
                        val it = list.iterator()
                        while (it.hasNext()) {
                            val item = it.next()
                            if (isStatusItemLocked(item, lockedIdentifiers, allowedJid)) {
                                it.remove()
                            }
                        }
                        mutated = true
                    } catch (_: Throwable) {
                        mutated = false
                    }
                }
                if (!mutated) {
                    args[i] = filtered
                }
            }
        }
    }

    private fun filterCallHistory(history: Any?, lockedIdentifiers: Set<String>): Any? {
        @Suppress("UNCHECKED_CAST")
        val callHistory = history as? Map<Any?, Any?> ?: return history
        var filteredHistory: LinkedHashMap<Any?, Any?>? = null
        for ((key, value) in callHistory) {
            if (isCallHistoryItemLocked(value, lockedIdentifiers)) {
                if (filteredHistory == null) {
                    filteredHistory = LinkedHashMap(callHistory)
                }
                filteredHistory.remove(key)
            }
        }
        return filteredHistory ?: history
    }

    private fun isCallHistoryItemLocked(item: Any?, lockedIdentifiers: Set<String>): Boolean {
        if (item == null) return false
        val jidMethod = callItemJidMethods[item.javaClass] ?: run {
            val method = runCatching {
                item.javaClass.methods.firstOrNull {
                    it.name == "getJid" && it.parameterCount == 0
                }
            }.getOrNull() ?: return false
            method.isAccessible = true
            callItemJidMethods.putIfAbsent(item.javaClass, method)
            method
        }
        val jid = runCatching { jidMethod.invoke(item) }.getOrNull() ?: return false
        val rawJid = jid as? String ?: getRawJid(jid)
        return rawJid != null && isRawJidLocked(rawJid, lockedIdentifiers)
    }

    private fun getRawJid(jid: Any): String? {
        val method = rawJidMethods[jid.javaClass] ?: run {
            val discovered = runCatching {
                jid.javaClass.methods.firstOrNull {
                    it.name == "getRawString" &&
                            it.parameterCount == 0 &&
                            it.returnType == String::class.java
                }
            }.getOrNull() ?: return null
            discovered.isAccessible = true
            rawJidMethods.putIfAbsent(jid.javaClass, discovered)
            discovered
        }
        return runCatching { method.invoke(jid) as? String }.getOrNull()
    }

    private fun getLockedIdentifiers(lockedChatsField: Field): Set<String> {
        val cache = chatCache ?: run {
            chatCache = getDeclaredFieldsSafely(lockedChatsField.declaringClass).firstOrNull {
                Modifier.isStatic(it.modifiers) && lockedChatsField.declaringClass.isAssignableFrom(
                    it.type
                )
            }?.apply { isAccessible = true }?.get(null)
            chatCache
        } ?: return emptySet()

        val lockedChats = try {
            lockedChatsField.get(cache) as? HashSet<*> ?: return emptySet()
        } catch (_: Throwable) {
            return emptySet()
        }
        if (lockedChats.isEmpty()) return emptySet()

        val identifiers = HashSet<String>()
        for (item in lockedChats) {
            if (item == null) continue
            val userJid = UserJid(item)
            userJid.phoneRawString?.let { identifiers.add(it) }
            userJid.userRawString?.let { identifiers.add(it) }
            userJid.phoneNumber?.let { identifiers.add(it) }
        }
        return identifiers
    }

    private fun isUserLocked(
        userJid: UserJid,
        lockedIdentifiers: Set<String>
    ): Boolean {
        val phoneRaw = userJid.phoneRawString
        if (phoneRaw != null && lockedIdentifiers.contains(phoneRaw)) return true
        val userRaw = userJid.userRawString
        if (userRaw != null && lockedIdentifiers.contains(userRaw)) return true
        val phoneNum = userJid.phoneNumber
        return phoneNum != null && lockedIdentifiers.contains(phoneNum)
    }

    private fun isStatusItemLocked(
        item: Any?,
        lockedIdentifiers: Set<String>,
        allowedJid: String? = null,
    ): Boolean {
        val userJid = getUserJidFromStatusItem(item)
        if (allowedJid != null && userJid != null && isSameUserJid(userJid, allowedJid)) {
            return false
        }
        if (userJid != null && isUserLocked(userJid, lockedIdentifiers)) return true

        val rawJid = getRawJidFromStatusItem(item)
        return !(allowedJid != null && rawJid != null && isSameRawJid(rawJid, allowedJid)) && rawJid != null && isRawJidLocked(rawJid, lockedIdentifiers)
    }

    private fun isRawJidLocked(rawJid: String, lockedIdentifiers: Set<String>): Boolean {
        val normalized = normalizeRawJid(rawJid)
        if (lockedIdentifiers.contains(normalized)) return true
        val atIndex = normalized.indexOf('@')
        return atIndex > 0 && lockedIdentifiers.contains(normalized.substring(0, atIndex))
    }

    private fun isSameRawJid(first: String, second: String): Boolean {
        return normalizeRawJid(first) == normalizeRawJid(second)
    }

    private fun isSameUserJid(userJid: UserJid, rawJid: String): Boolean {
        return userJid.phoneRawString?.let { isSameRawJid(it, rawJid) } == true ||
                userJid.userRawString?.let { isSameRawJid(it, rawJid) } == true
    }

    private fun normalizeRawJid(rawJid: String): String {
        val atIndex = rawJid.indexOf('@')
        if (atIndex <= 0) return rawJid
        val userPart = rawJid.substring(0, atIndex).substringBefore('.').substringBefore(':')
        val domain = rawJid.substring(atIndex + 1)
        return "$userPart@$domain"
    }

    private fun getRawJidFromStatusItem(item: Any?, depth: Int = 0): String? {
        if (item == null || depth > 2) return null
        if (item is String) {
            return item.takeIf { it.endsWith("@s.whatsapp.net") || it.endsWith("@lid") }
        }

        val methods = try {
            item.javaClass.methods
        } catch (_: LinkageError) {
            emptyArray()
        }
        for (method in methods) {
            if (Modifier.isStatic(method.modifiers) || method.parameterCount != 0 ||
                method.returnType != String::class.java || method.name == "toString"
            ) {
                continue
            }
            val rawJid = try {
                method.invoke(item) as? String
            } catch (_: Throwable) {
                null
            }
            if (rawJid?.let { it.endsWith("@s.whatsapp.net") || it.endsWith("@lid") } == true) {
                return rawJid
            }
        }

        val jidClass = runCatching { UserJid.TYPE_JID }.getOrNull()
        if (jidClass != null) {
            if (jidClass.isInstance(item)) {
                val rawJid = runCatching {
                    ReflectionUtils.callMethod(item, "getRawString") as? String
                }.getOrNull()
                if (rawJid?.let { it.endsWith("@s.whatsapp.net") || it.endsWith("@lid") } == true) {
                    return rawJid
                }
            }

            for (method in methods) {
                if (Modifier.isStatic(method.modifiers) || method.parameterCount != 0 ||
                    !jidClass.isAssignableFrom(method.returnType)
                ) {
                    continue
                }
                val jid = try {
                    method.invoke(item)
                } catch (_: Throwable) {
                    null
                } ?: continue
                val rawJid = runCatching {
                    ReflectionUtils.callMethod(jid, "getRawString") as? String
                }.getOrNull()
                if (rawJid?.let { it.endsWith("@s.whatsapp.net") || it.endsWith("@lid") } == true) {
                    return rawJid
                }
            }
        }

        if (depth == 2) return null
        for (field in getDeclaredFieldsSafely(item.javaClass)) {
            if (Modifier.isStatic(field.modifiers)) continue
            val nested = try {
                field.isAccessible = true
                field.get(item)
            } catch (_: Throwable) {
                null
            } ?: continue
            val nestedClassName = nested.javaClass.name
            if (nestedClassName.startsWith("java.") || nestedClassName.startsWith("android.") ||
                nestedClassName.startsWith("androidx.")
            ) {
                continue
            }
            getRawJidFromStatusItem(nested, depth + 1)?.let { return it }
        }
        return null
    }

    private fun getUserJidFromStatusItem(item: Any?): UserJid? {
        if (item == null) return null
        try {
            val jidClass = UserJid.TYPE_JID
            if (jidClass.isInstance(item)) {
                val uJid = UserJid(item)
                if (uJid.phoneRawString != null || uJid.userRawString != null) {
                    return uJid
                }
            }

            if (WaContactWpp.TYPE.isInstance(item)) {
                val uJid = WaContactWpp(item).userJid
                if (uJid.phoneRawString != null || uJid.userRawString != null) {
                    return uJid
                }
            }

            if (item is String && (item.endsWith("@s.whatsapp.net") || item.endsWith("@lid"))) {
                val uJid = UserJid(item)
                if (uJid.phoneRawString != null || uJid.userRawString != null) {
                    return uJid
                }
            }

            if (FMessageWpp.TYPE.isInstance(item)) {
                val uJid = FMessageWpp(item).userJid
                if (uJid.phoneRawString != null || uJid.userRawString != null) {
                    return uJid
                }
            }

            val statusItem = StatusItemWpp.from(item)
            if (statusItem != null) {
                val uJid = statusItem.senderJid
                    ?: statusItem.fMessage?.userJid
                    ?: statusItem.fMessage?.key?.remoteJid
                if (uJid != null && (uJid.phoneRawString != null || uJid.userRawString != null)) {
                    return uJid
                }
            }

            val contactField = ReflectionUtils.findFieldUsingFilterIfExists(item.javaClass) { f ->
                WaContactWpp.TYPE.isAssignableFrom(f.type)
            }
            if (contactField != null) {
                val contact = contactField.get(item)
                if (contact != null) {
                    val uJid = WaContactWpp(contact).userJid
                    if (uJid.phoneRawString != null || uJid.userRawString != null) {
                        return uJid
                    }
                }
            }

            val directJidField = ReflectionUtils.findFieldUsingFilterIfExists(item.javaClass) { f ->
                jidClass.isAssignableFrom(f.type)
            }
            if (directJidField != null) {
                val jid = directJidField.get(item)
                if (jid != null) {
                    val uJid = UserJid(jid)
                    if (uJid.phoneRawString != null || uJid.userRawString != null) {
                        return uJid
                    }
                }
            }

            for (field in getDeclaredFieldsSafely(item.javaClass)) {
                if (Modifier.isStatic(field.modifiers)) continue
                field.isAccessible = true
                val nested = field.get(item) ?: continue
                val nestedClass = nested.javaClass
                if (nestedClass.name.startsWith("java.") || nestedClass.name.startsWith("android.")) continue

                val nestedJidField =
                    ReflectionUtils.findFieldUsingFilterIfExists(nestedClass) { f ->
                        jidClass.isAssignableFrom(f.type)
                    }
                if (nestedJidField != null) {
                    val jid = nestedJidField.get(nested)
                    if (jid != null) {
                        val uJid = UserJid(jid)
                        if (uJid.phoneRawString != null || uJid.userRawString != null) {
                            return uJid
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            logDebug(e)
        }
        return null
    }

    private fun getDeclaredFieldsSafely(clazz: Class<*>): Array<Field> {
        return runCatching {
            clazz.declaredFields
        }.getOrElse {
            emptyArray()
        }
    }

    override fun getPluginName(): String = "Locked Chats Enhancer"
}
