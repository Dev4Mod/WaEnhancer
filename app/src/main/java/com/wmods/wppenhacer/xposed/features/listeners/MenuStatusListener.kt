package com.wmods.wppenhacer.xposed.features.listeners

import android.view.Menu
import android.view.MenuItem
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Method

class MenuStatusListener(classLoader: ClassLoader, preferences: XSharedPreferences) :
    Feature(classLoader, preferences) {

    private lateinit var fStatusClass: Class<Any>
    private lateinit var fStatusToFMessage: Method

    companion object {
        @JvmStatic
        val menuStatuses = LinkedHashSet<OnMenuItemStatusListener>()

        @JvmStatic
        val currentStatusList = ArrayList<FMessageWpp>()

        @JvmField
        var currentIndex = -1


        @JvmStatic
        fun getFMessageFromStatusData(obj: Any?): FMessageWpp? {
            if (obj == null) return null
            var fMessageObj = ReflectionUtils.findFieldUsingFilterIfExists(obj.javaClass) { f ->
                FMessageWpp.TYPE.isAssignableFrom(f.type)
            }?.get(obj)

            if (fMessageObj == null) {
                val classLoader = obj.javaClass.classLoader!!
                val mapFStatusToFMessage = Unobfuscator.loadFStatusToFMessage(classLoader)
                val fStatusClass = mapFStatusToFMessage.parameterTypes.first()
                fMessageObj = ReflectionUtils.findFieldUsingFilterIfExists(obj.javaClass) { f ->
                    fStatusClass.isAssignableFrom(f.type)
                }?.let {
                    WppCore.getFMessageFromFStatus(it.get(obj))
                }
            }
            if (fMessageObj == null) {
                XposedBridge.log("FMessage not found in Story/Status using StatusData")
                return null
            }
            return FMessageWpp(fMessageObj)
        }


    }


    override fun doHook() {

        val menuStatusMethod = Unobfuscator.loadMenuStatusMethod(classLoader)
        val menuManagerClass = Unobfuscator.loadMenuManagerClass(classLoader)

        val statusPlaybackBaseFragmentClass =
            classLoader.loadClass("com.whatsapp.status.playback.fragment.StatusPlaybackBaseFragment")
        val statusPlaybackContactFragmentClass =
            classLoader.loadClass("com.whatsapp.status.playback.fragment.StatusPlaybackContactFragment")
        val listStatusField = ReflectionUtils.getFieldByExtendType(
            statusPlaybackContactFragmentClass,
            List::class.java
        )

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

                val index = XposedHelpers.getObjectField(fragmentInstance, "A00") as Int
                val listStatus = listStatusField?.get(fragmentInstance) as List<*>

                val fMessageList = listStatus.mapNotNull { obj -> getFMessageFromStatusData(obj) }

                currentStatusList.clear()
                currentStatusList.addAll(fMessageList)

                currentIndex = index

                if (index < 0 || index >= fMessageList.size) return

                for (menuStatus in menuStatuses) {
                    val menuItem = menuStatus.addMenu(menu, fMessageList, index) ?: continue

                    menuItem.setOnMenuItemClickListener { item ->
                        menuStatus.onClick(item, fragmentInstance, fMessageList, index)
                        true
                    }
                }
            }
        })
    }

    override fun getPluginName(): String {
        return "Menu Status"
    }

    abstract class OnMenuItemStatusListener {

        abstract fun addMenu(
            menu: Menu,
            fMessageList: List<FMessageWpp>,
            currentIndex: Int
        ): MenuItem?

        abstract fun onClick(
            item: MenuItem,
            fragmentInstance: Any,
            fMessageList: List<FMessageWpp>,
            currentIndex: Int
        )
    }
}