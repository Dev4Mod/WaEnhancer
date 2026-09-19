package com.wmods.wppenhacer.xposed.features.providers

import android.content.SharedPreferences
import android.view.Menu
import android.view.MenuItem
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.components.StatusItemWpp
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import org.luckypray.dexkit.query.enums.StringMatchType
import java.lang.reflect.Field
import java.util.concurrent.CopyOnWriteArraySet

class MenuStatusProvider(classLoader: ClassLoader, preferences: SharedPreferences) :
    Feature(classLoader, preferences) {

    interface Provider {
        fun addMenu(
            menu: Menu,
            statusData: StatusData,
        ): MenuItem?

        fun onClick(
            item: MenuItem,
            statusData: StatusData
        )
    }

    companion object {
        private val providers = CopyOnWriteArraySet<Provider>()

        @JvmStatic
        fun register(provider: Provider) {
            providers += provider
        }

        fun unregister(provider: Provider) {
            providers -= provider
        }

        @JvmStatic
        lateinit var statusData: StatusData

        private var currentIndexField: Field? = null
    }

    override fun doHook() {
        val menuStatusMethod = Unobfuscator.loadMenuStatusMethod(classLoader)
        val menuManagerClass = Unobfuscator.loadMenuManagerClass(classLoader)

        val statusPlaybackBaseFragmentClass = Unobfuscator.findFirstClassUsingName(
            classLoader,
            StringMatchType.EndsWith,
            "StatusPlaybackBaseFragment"
        )
        val statusPlaybackContactFragmentClass = Unobfuscator.findFirstClassUsingName(
            classLoader,
            StringMatchType.EndsWith,
            "StatusPlaybackContactFragment"
        )
        val listStatusField = ReflectionUtils.getFieldByExtendType(
            statusPlaybackContactFragmentClass,
            List::class.java
        )

        currentIndexField = runCatching {
            Unobfuscator.loadStatusPlaybackCurrentIndexField(classLoader).apply { isAccessible = true }
        }.getOrNull()

        XposedBridge.hookMethod(menuStatusMethod, object : XC_MethodHook() {

            @Throws(Throwable::class)
            override fun afterHookedMethod(param: MethodHookParam) {
                val fieldObjects = param.method.declaringClass.declaredFields
                    .mapNotNull { field -> ReflectionUtils.getObjectField(field, param.thisObject) }

                val fragmentInstance: Any =
                    if (param.thisObject != null && statusPlaybackContactFragmentClass.isInstance(
                            param.thisObject
                        )
                    ) {
                        param.thisObject
                    } else {
                        fieldObjects.firstOrNull { statusPlaybackBaseFragmentClass.isInstance(it) }
                            ?: return
                    }

                val menu: Menu = if (param.args.isNotEmpty() && param.args[0] is Menu) {
                    param.args[0] as Menu
                } else {
                    val menuManager = fieldObjects.firstOrNull { menuManagerClass.isInstance(it) }
                    val menuField =
                        ReflectionUtils.getFieldByExtendType(menuManagerClass, Menu::class.java)
                    ReflectionUtils.getObjectField(menuField, menuManager) as Menu
                }

                val listStatus = listStatusField?.get(fragmentInstance) as List<*>

                statusData = StatusData(listStatus, fragmentInstance)

                for (provider in providers) {
                    val menuItem = provider.addMenu(menu, statusData) ?: continue

                    menuItem.setOnMenuItemClickListener { item ->
                        provider.onClick(item, statusData)
                        true
                    }
                }
            }
        })
    }

    override fun getPluginName(): String = "MenuStatusProvider"

    open class StatusData(private val listStatus: List<*>, private val fragmentInstance: Any) {

        private var cachedItemList: List<StatusItemWpp>? = null

        val currentItem: StatusItemWpp
            get() = getCurrentItemList()[currentIndex]

        val currentIndex: Int
            get() {
                val resolvedIndex = currentIndexField?.let { field ->
                    runCatching { field.getInt(fragmentInstance) }.getOrNull()
                }
                return resolvedIndex
                    ?: (XposedHelpers.getObjectField(fragmentInstance, "A02") as? Int)
                    ?: (XposedHelpers.getObjectField(fragmentInstance, "A00") as? Int)
                    ?: 0
            }

        fun getCurrentItemList(): List<StatusItemWpp> {
            return cachedItemList ?: listStatus.mapNotNull { obj ->
                StatusItemWpp.from(obj)
            }.also { cachedItemList = it }
        }
    }
}
