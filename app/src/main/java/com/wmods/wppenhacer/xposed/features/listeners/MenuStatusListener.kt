package com.wmods.wppenhacer.xposed.features.listeners

import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

class MenuStatusListener(
    classLoader: ClassLoader,
    preferences: XSharedPreferences
) : Feature(classLoader, preferences) {

    @Throws(Throwable::class)
    override fun doHook() {
        val menuStatusMethod = Unobfuscator.loadMenuStatusMethod(classLoader)
        logDebug("MenuStatus method: ${menuStatusMethod.name}")

        val menuManagerClass = Unobfuscator.loadMenuManagerClass(classLoader)

        val statusPlaybackBaseFragmentClass =
            classLoader.loadClass("com.whatsapp.status.playback.fragment.StatusPlaybackBaseFragment")

        val statusPlaybackContactFragmentClass =
            classLoader.loadClass("com.whatsapp.status.playback.fragment.StatusPlaybackContactFragment")

        val listStatusField =
            ReflectionUtils.getFieldsByExtendType(
                statusPlaybackContactFragmentClass,
                List::class.java
            )[0]

        XposedBridge.hookMethod(
            menuStatusMethod,
            object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun afterHookedMethod(param: MethodHookParam) {


                    val fieldObjects = param.method.declaringClass.declaredFields
                        .mapNotNull { field ->
                            ReflectionUtils.getObjectField(field, param.thisObject)
                        }

                    val fragmentInstance = resolveFragmentInstance(
                        thisObject = param.thisObject,
                        fieldObjects = fieldObjects,
                        contactFragmentClass = statusPlaybackContactFragmentClass,
                        baseFragmentClass = statusPlaybackBaseFragmentClass
                    ) ?: return

                    val menu = resolveMenu(
                        args = param.args,
                        fieldObjects = fieldObjects,
                        menuManagerClass = menuManagerClass
                    ) ?: return

                    val index = XposedHelpers.getObjectField(fragmentInstance, "A00") as Int
                    val listStatus = listStatusField.get(fragmentInstance) as List<*>

                    var messageObject = listStatus.getOrNull(index) ?: return

                    if (!FMessageWpp.TYPE.isInstance(messageObject)) {
                        val methods =
                            ReflectionUtils.findAllMethodsUsingFilter(messageObject.javaClass) { m ->
                                m.returnType == FMessageWpp.Key.TYPE && m.parameterCount == 0
                            }
                        val result = methods.firstNotNullOfOrNull { f ->
                            f.invoke(messageObject)?.let { WppCore.getFMessageFromKey(it) }
                        }
                        if (result == null) {
                            Utils.showToast("FMessage not found in Story/Status", Toast.LENGTH_LONG)
                            return
                        }
                        messageObject = result
                    }

                    val fMessage = FMessageWpp(messageObject)

                    menuStatuses.forEach { menuStatus ->
                        val menuItem = menuStatus.addMenu(menu, fMessage) ?: return@forEach
                        menuItem.setOnMenuItemClickListener { item ->
                            menuStatus.onClick(item, fragmentInstance, fMessage)
                            true
                        }
                    }
                }
            }
        )
    }

    override fun getPluginName(): String {
        return "Menu Status"
    }

    private fun resolveFragmentInstance(
        thisObject: Any?,
        fieldObjects: List<Any>,
        contactFragmentClass: Class<*>,
        baseFragmentClass: Class<*>
    ): Any? {
        return when {
            thisObject != null && contactFragmentClass.isInstance(thisObject) -> thisObject
            else -> fieldObjects.firstOrNull { baseFragmentClass.isInstance(it) }
        }
    }

    private fun resolveMenu(
        args: Array<Any?>,
        fieldObjects: List<Any>,
        menuManagerClass: Class<*>
    ): Menu? {
        args.firstOrNull()?.let { firstArg ->
            if (firstArg is Menu) return firstArg
        }

        val menuManager = fieldObjects.firstOrNull { menuManagerClass.isInstance(it) }
            ?: return null

        val menuField = ReflectionUtils.getFieldByExtendType(menuManagerClass, Menu::class.java)

        return ReflectionUtils.getObjectField(menuField, menuManager) as? Menu
    }

    abstract class OnMenuItemStatusListener {

        abstract fun addMenu(menu: Menu, fMessage: FMessageWpp): MenuItem?

        abstract fun onClick(
            item: MenuItem,
            fragmentInstance: Any,
            fMessageWpp: FMessageWpp
        )
    }

    companion object {
        @JvmField
        val menuStatuses: HashSet<OnMenuItemStatusListener> = HashSet()

    }
}