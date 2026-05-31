package com.wmods.wppenhacer.xposed.features.customization

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RoundRectShape
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.core.devkit.UnobfuscatorCache
import com.wmods.wppenhacer.xposed.utils.DesignUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Method

class FilterGroups(
    loader: ClassLoader,
    preferences: XSharedPreferences
) : Feature(loader, preferences) {

    @Volatile
    private var currentTab: Int = CHATS_TAB_POSITION

    @Volatile
    private var conversationFragment: Any? = null

    private var methodOnConversationsListChanged: Method? = null

    private var tabConversas: TextView? = null
    private var tabGrupos: TextView? = null

    @Throws(Throwable::class)
    override fun doHook() {
        if (!prefs.getBoolean(
                "filtergroups",
                false
            ) || prefs.getBoolean("separategroups", false)
        ) {
            return
        }

        try {
            methodOnConversationsListChanged =
                Unobfuscator.loadOnConversationsListChangedMethod(classLoader)
        } catch (throwable: Throwable) {
            logDebug(throwable)
        }

        val methodTabInstance = Unobfuscator.loadTabFragmentMethod(classLoader)
        XposedBridge.hookMethod(
            methodTabInstance,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    conversationFragment = param.thisObject
                    param.result = filterList(param.result as? List<*>)
                }
            }
        )

        val publishResultsMethod = Unobfuscator.loadGetFiltersMethod(classLoader)
        XposedBridge.hookMethod(
            publishResultsMethod,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val filters = param.args[1]
                    val chatsList = XposedHelpers.getObjectField(filters, "values") as? List<*>
                    val resultList = filterList(chatsList)

                    XposedHelpers.setObjectField(filters, "values", resultList)
                    XposedHelpers.setIntField(filters, "count", resultList.size)
                }
            }
        )

        val filterView = Unobfuscator.getFilterView(classLoader)
        XposedHelpers.findAndHookConstructor(
            filterView,
            Context::class.java,
            object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun afterHookedMethod(param: MethodHookParam) {
                    setSetupSeparate(param.thisObject as ViewGroup)
                }
            }
        )
    }

    private fun filterList(chatsList: List<*>?): List<*> {
        if (chatsList == null) return emptyList<Any>()

        val groupsTab = currentTab == GROUPS_TAB_POSITION

        val filtered = SeparateGroup.ArrayListFilter(
            { userJid ->
                if (groupsTab) {
                    userJid.isGroup || userJid.isBroadcast
                } else {
                    !userJid.isGroup && !userJid.isBroadcast
                }
            },
            !groupsTab
        )

        filtered.addAllFromList(chatsList)
        return filtered
    }

    private fun refreshFragment() {
        val fragment = conversationFragment
        val method = methodOnConversationsListChanged

        if (fragment == null || method == null) return

        try {
            method.invoke(fragment)
        } catch (throwable: Throwable) {
            logDebug(throwable)
        }
    }

    @SuppressLint("ResourceType")
    private fun setSetupSeparate(view: ViewGroup) {
        val context = view.context

        if (view.findViewWithTag<View>(FILTER_CONTAINER_TAG) != null) return

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            tag = FILTER_CONTAINER_TAG
        }

        val filter = view.getChildAt(0)
        view.removeView(filter)

        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL

            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Utils.dipToPixels(TAB_HEIGHT_DP)
            ).apply {
                leftMargin = Utils.dipToPixels(HORIZONTAL_MARGIN_DP)
                rightMargin = Utils.dipToPixels(HORIZONTAL_MARGIN_DP)
                bottomMargin = Utils.dipToPixels(BOTTOM_MARGIN_DP)
            }

            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                setStroke(Utils.dipToPixels(BORDER_WIDTH_DP), DesignUtils.getUnSeenColor())
                cornerRadius = getPillRadiusPx()
            }
        }

        val tabLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        mainLayout.addView(
            tabLayout,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val chatsLabel = UnobfuscatorCache.getInstance().getString("Chats")

        tabConversas = createTab(
            context = context,
            text = chatsLabel.ifEmpty { "Chats" },
            position = CHATS_TAB_POSITION
        ).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                TAB_WEIGHT
            )
            setOnClickListener { updateContent(CHATS_TAB_POSITION, true) }
        }

        tabGrupos = createTab(
            context = context,
            text = UnobfuscatorCache.getInstance().getString("groups"),
            position = GROUPS_TAB_POSITION
        ).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                TAB_WEIGHT
            )
            setOnClickListener { updateContent(GROUPS_TAB_POSITION, true) }
        }

        tabLayout.addView(tabConversas)
        tabLayout.addView(tabGrupos)

        container.addView(mainLayout)
        container.addView(filter)
        view.addView(container, 0)

        updateContent(CHATS_TAB_POSITION, false)
    }

    private fun createTab(
        context: Context,
        text: String,
        position: Int
    ): TextView {
        return TextView(context).apply {
            setText(text)
            gravity = Gravity.CENTER
            setPadding(
                TAB_PADDING_HORIZONTAL_PX,
                TAB_PADDING_VERTICAL_PX,
                TAB_PADDING_HORIZONTAL_PX,
                TAB_PADDING_VERTICAL_PX
            )
            setTextColor(DesignUtils.getPrimaryTextColor())

            setDrawableSelected(
                view = this,
                colorBackground = Color.TRANSPARENT,
                colorStroke = DesignUtils.getPrimaryTextColor(),
                position = position
            )
        }
    }

    private fun setDrawableSelected(
        view: View,
        colorBackground: Int,
        colorStroke: Int,
        position: Int
    ) {
        val radius = getPillRadiusPx()
        val cornerRadii = getTabCornerRadii(radius, position)

        val selectedBackground = ShapeDrawable(
            RoundRectShape(cornerRadii, null, null)
        ).apply {
            paint.color = colorBackground
            alpha = SELECTED_BACKGROUND_ALPHA
        }

        val borderDrawable = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            setStroke(Utils.dipToPixels(BORDER_WIDTH_DP), colorStroke)
            this.cornerRadii = cornerRadii
        }

        val inset = Utils.dipToPixels(BORDER_WIDTH_DP)

        view.background = LayerDrawable(
            arrayOf(borderDrawable, selectedBackground)
        ).apply {
            setLayerInset(1, inset, inset, inset, inset)
        }
    }

    private fun getTabCornerRadii(radius: Float, position: Int): FloatArray {
        return if (position == CHATS_TAB_POSITION) {
            floatArrayOf(
                radius, radius, // top-left
                0f, 0f,         // top-right
                0f, 0f,         // bottom-right
                radius, radius  // bottom-left
            )
        } else {
            floatArrayOf(
                0f, 0f,         // top-left
                radius, radius, // top-right
                radius, radius, // bottom-right
                0f, 0f          // bottom-left
            )
        }
    }

    private fun getPillRadiusPx(): Float {
        return Utils.dipToPixels(TAB_HEIGHT_DP / 2f).toFloat()
    }

    private fun updateContent(position: Int, refreshFragment: Boolean) {
        currentTab = position

        val chatsTab = tabConversas ?: return
        val groupsTab = tabGrupos ?: return

        val primaryTextColor = DesignUtils.getPrimaryTextColor()
        val selectedColor = DesignUtils.getUnSeenColor()

        if (position == CHATS_TAB_POSITION) {
            setDrawableSelected(
                view = chatsTab,
                colorBackground = selectedColor,
                colorStroke = primaryTextColor,
                position = CHATS_TAB_POSITION
            )

            setDrawableSelected(
                view = groupsTab,
                colorBackground = Color.TRANSPARENT,
                colorStroke = primaryTextColor,
                position = GROUPS_TAB_POSITION
            )
        } else {
            setDrawableSelected(
                view = chatsTab,
                colorBackground = Color.TRANSPARENT,
                colorStroke = primaryTextColor,
                position = CHATS_TAB_POSITION
            )

            setDrawableSelected(
                view = groupsTab,
                colorBackground = selectedColor,
                colorStroke = primaryTextColor,
                position = GROUPS_TAB_POSITION
            )
        }

        if (refreshFragment) {
            refreshFragment()
        }
    }

    override fun getPluginName(): String = "Filter Groups"

    private companion object {
        private const val FILTER_CONTAINER_TAG = "wae_filters"

        private const val CHATS_TAB_POSITION = 0
        private const val GROUPS_TAB_POSITION = 1

        private const val TAB_HEIGHT_DP = 48f
        private const val HORIZONTAL_MARGIN_DP = 20f
        private const val BOTTOM_MARGIN_DP = 5f
        private const val BORDER_WIDTH_DP = 2f

        private const val TAB_WEIGHT = 1.0f
        private const val TAB_PADDING_HORIZONTAL_PX = 32
        private const val TAB_PADDING_VERTICAL_PX = 16

        private const val SELECTED_BACKGROUND_ALPHA = 120
    }
}