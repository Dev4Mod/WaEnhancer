package com.wmods.wppenhacer.xposed.features.customization

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.listeners.OnMultiClickListener
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.features.general.Others
import com.wmods.wppenhacer.xposed.utils.DesignUtils
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import org.luckypray.dexkit.query.enums.StringMatchType
import java.lang.reflect.Method
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TYPE_ARCHIVE_MULTI_CLICK = "1"
private const val TYPE_ARCHIVE_LONG_CLICK = "2"
private const val MULTI_CLICK_COUNT = 5
private const val MULTI_CLICK_INTERVAL = 700
private const val TITLE_TEXT_SIZE = 20f
private const val SUBTITLE_TEXT_SIZE = 12f

private var onMenuItemSelected: Method? = null

class CustomToolbar(loader: ClassLoader, preferences: SharedPreferences) :
    Feature(loader, preferences) {

    private var mDateExpiration: String? = null

    @Throws(Exception::class)
    override fun doHook() {
        val showName = xprefs.getBoolean("shownamehome", false)
        val showBio = xprefs.getBoolean("showbiohome", false)
        val typeArchive = xprefs.getString("typearchive", "0") ?: "0"

        onMenuItemSelected = Unobfuscator.loadOnMenuItemSelected(classLoader)

        val methodHook = ToolbarMethodHook(this, showName, showBio, typeArchive)
        WppCore.homeActivityClass.resolve().firstMethod {
            name = "onCreate"
            superclass()
            parameters(Bundle::class.java)
        }.hook {
            after {
                methodHook.afterHook(instance as Activity)
            }
        }

        hookExpirationInfo()
        Others.propsBoolean[6481] = false
    }

    override fun getPluginName(): String {
        return "Show Name and Bio"
    }

    private fun hookExpirationInfo() {
        hookExpirationDate()
        hookAboutActivity()
    }

    private fun hookExpirationDate() {
        val expirationClass = Unobfuscator.loadExpirationClass(classLoader)

        expirationClass.resolve().constructor { }.hookAll {
            after {
                val method = ReflectionUtils.findMethodUsingFilter(
                    instance.javaClass
                ) { m: Method -> m.returnType == Date::class.java }
                val date = method.invoke(instance) as Date
                mDateExpiration = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    .format(date)
            }
        }
    }

    private fun hookAboutActivity() {
        WppCore.aboutActivityClass.resolve().firstMethod {
            name = "onCreate"
            superclass()
            parameters(Bundle::class.java)
        }.hook {
            after {
                val activity = instance as Activity
                val viewRoot = activity.window.decorView
                val version = viewRoot.findViewById<TextView>(Utils.getID("version", "id"))

                if (version != null) {
                    val expirationText = activity.getString(R.string.expiration, mDateExpiration)
                    version.text = "${version.text} $expirationText"
                }
            }
        }
    }

    private class ToolbarMethodHook(
        private val packageParam: PackageParam,
        private val showName: Boolean,
        private val showBio: Boolean,
        private val typeArchive: String
    ) {

        fun afterHook(homeActivity: Activity) {
            val toolbar = homeActivity.findViewById<ViewGroup>(Utils.getID("toolbar", "id"))
            val logo = toolbar.findViewById<View>(Utils.getID("toolbar_logo", "id"))

            val tabInstance = getTabInstance(homeActivity)
            val archiveIntent = createArchiveIntent(homeActivity)

            setupArchiveListener(toolbar, homeActivity, archiveIntent)

            if (!showBio && !showName) return

            val toolbarLayout = createToolbarLayout(homeActivity, toolbar)
            createTitleView(homeActivity, toolbarLayout)

            if (showBio) {
                createSubtitleView(homeActivity, toolbarLayout)
            }

            hideOriginalLogo(homeActivity, logo)
            setupTabVisibilityHook(tabInstance, toolbarLayout)
        }

        private fun getTabInstance(homeActivity: Activity): Any {
            val clazz = WppCore.tabsPagerClass
            val fieldTab = ReflectionUtils.getFieldByType(homeActivity.javaClass, clazz)
            return fieldTab!!.get(homeActivity)!!
        }

        private fun createArchiveIntent(homeActivity: Activity): Intent {
            val archivedClass = Unobfuscator.findFirstClassUsingName(
                homeActivity.classLoader,
                StringMatchType.EndsWith,
                "ArchivedConversationsActivity"
            )
            return Intent().apply {
                setClassName(Utils.application.packageName, archivedClass.name)
            }
        }

        private fun setupArchiveListener(
            toolbar: ViewGroup,
            homeActivity: Activity,
            intent: Intent
        ) {
            when (typeArchive) {
                TYPE_ARCHIVE_MULTI_CLICK -> setupMultiClickListener(toolbar, homeActivity, intent)
                TYPE_ARCHIVE_LONG_CLICK -> setupLongClickListener(toolbar, homeActivity, intent)
            }
        }

        private fun setupMultiClickListener(
            toolbar: ViewGroup,
            homeActivity: Activity,
            intent: Intent
        ) {
            val listener =
                object : OnMultiClickListener(MULTI_CLICK_COUNT, MULTI_CLICK_INTERVAL.toLong()) {
                    override fun onMultiClick(v: View) {
                        homeActivity.startActivity(intent)
                    }
                }
            toolbar.setOnClickListener(listener)
        }

        private fun setupLongClickListener(
            toolbar: ViewGroup,
            homeActivity: Activity,
            intent: Intent
        ) {
            toolbar.setOnLongClickListener {
                homeActivity.startActivity(intent)
                true
            }
        }

        private fun createToolbarLayout(homeActivity: Activity, toolbar: ViewGroup): LinearLayout {
            val linearLayout = LinearLayout(homeActivity)
            linearLayout.orientation = LinearLayout.VERTICAL
            toolbar.addView(linearLayout, 0)
            return linearLayout
        }

        private fun createTitleView(homeActivity: Activity, parent: LinearLayout) {
            val name = WppCore.getMyName()
            val titleText = if (showName) name else "WhatsApp"

            val mTitle = TextView(homeActivity)
            mTitle.text = titleText
            mTitle.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f
            )
            mTitle.textSize = TITLE_TEXT_SIZE
            mTitle.setTextColor(DesignUtils.getPrimaryTextColor())

            if (!showBio) {
                mTitle.gravity = Gravity.CENTER
            }

            parent.addView(mTitle)
        }

        private fun createSubtitleView(homeActivity: Activity, parent: LinearLayout) {
            val bio = WppCore.getMyBio()

            val mSubtitle = TextView(homeActivity)
            mSubtitle.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            mSubtitle.text = bio
            mSubtitle.textSize = SUBTITLE_TEXT_SIZE
            mSubtitle.setTextColor(DesignUtils.getPrimaryTextColor())
            mSubtitle.marqueeRepeatLimit = -1
            mSubtitle.ellipsize = TextUtils.TruncateAt.MARQUEE
            mSubtitle.isSingleLine = true
            mSubtitle.isSelected = true

            parent.addView(mSubtitle)
        }

        private fun hideOriginalLogo(homeActivity: Activity, logo: View) {
            val parent = logo.parent as ViewGroup
            val window = homeActivity.window.decorView as ViewGroup

            parent.removeView(logo)

            val hideLayout = RelativeLayout(homeActivity)
            hideLayout.layoutParams = LinearLayout.LayoutParams(2, 2)
            hideLayout.visibility = View.GONE

            window.addView(hideLayout)
            hideLayout.addView(logo)
        }

        private fun setupTabVisibilityHook(tabInstance: Any, toolbarLayout: LinearLayout) {
            packageParam.apply {
                onMenuItemSelected!!.hook {
                    before {
                        if (tabInstance != instance) return@before

                        val currentIndex = args[0] as Int
                        val visibility = if (currentIndex == 0) View.VISIBLE else View.GONE
                        toolbarLayout.visibility = visibility
                    }
                }
            }
        }
    }
}
