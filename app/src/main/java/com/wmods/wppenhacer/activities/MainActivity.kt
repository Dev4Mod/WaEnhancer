package com.wmods.wppenhacer.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import androidx.core.app.ActivityOptionsCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.navigation.NavigationBarView
import com.waseemsabir.betterypermissionhelper.BatteryPermissionHelper
import com.wmods.wppenhacer.App
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.activities.base.BaseActivity
import com.wmods.wppenhacer.adapter.MainPagerAdapter
import com.wmods.wppenhacer.databinding.ActivityMainBinding
import com.wmods.wppenhacer.ui.fragments.GeneralFragment
import com.wmods.wppenhacer.ui.fragments.HomeFragment
import com.wmods.wppenhacer.ui.fragments.base.BasePreferenceFragment
import com.wmods.wppenhacer.utils.FilePicker
import java.io.File
import kotlin.math.abs

class MainActivity : BaseActivity() {
    private lateinit var binding: ActivityMainBinding
    private val batteryPermissionHelper = BatteryPermissionHelper.getInstance()
    private var pendingScrollToPreference: String? = null
    private var pendingScrollToFragment = -1
    private var pendingParentKey: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        App.changeLanguage(this)
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        binding.viewPager.adapter = MainPagerAdapter(this)
        binding.viewPager.setPageTransformer(DepthPageTransformer())

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (!prefs.getBoolean("call_recording_enable", false)) {
            binding.navView.menu.findItem(R.id.navigation_recordings).isVisible = false
        }

        binding.navView.setOnItemSelectedListener(NavigationBarView.OnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    binding.viewPager.setCurrentItem(0, true)
                    true
                }

                R.id.navigation_chat -> {
                    binding.viewPager.setCurrentItem(1, true)
                    true
                }

                R.id.navigation_privacy -> {
                    binding.viewPager.setCurrentItem(2, true)
                    true
                }

                R.id.navigation_media -> {
                    binding.viewPager.setCurrentItem(3, true)
                    true
                }

                R.id.navigation_colors -> {
                    binding.viewPager.setCurrentItem(4, true)
                    true
                }

                R.id.navigation_recordings -> {
                    binding.viewPager.setCurrentItem(5, true)
                    true
                }

                else -> false
            }
        })

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                binding.navView.menu.getItem(position).isChecked = true

                val scrollKey = pendingScrollToPreference
                if (pendingScrollToFragment == position && scrollKey != null) {
                    val parentKey = pendingParentKey
                    pendingScrollToPreference = null
                    pendingScrollToFragment = -1
                    pendingParentKey = null
                    binding.viewPager.postDelayed({
                        scrollToPreferenceInCurrentFragment(scrollKey, parentKey)
                    }, 300)
                }
            }
        })

        binding.viewPager.setCurrentItem(0, false)
        createMainDir()
        FilePicker.registerFilePicker(this)
        handleIncomingIntent(intent)
    }

    private fun createMainDir() {
        val nomedia = File(App.waEnhancerFolder, ".nomedia")
        if (nomedia.exists()) nomedia.delete()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        intent ?: return

        val fragmentPosition = intent.getIntExtra("navigate_to_fragment", -1)
        val preferenceKey = intent.getStringExtra("scroll_to_preference")
        val parentKey = intent.getStringExtra("parent_preference")

        if (fragmentPosition >= 0 && preferenceKey != null) {
            pendingScrollToPreference = preferenceKey
            pendingScrollToFragment = fragmentPosition
            pendingParentKey = parentKey
            binding.viewPager.setCurrentItem(fragmentPosition, false)
            intent.removeExtra("navigate_to_fragment")
            intent.removeExtra("scroll_to_preference")
            intent.removeExtra("parent_preference")
        } else if (fragmentPosition >= 0) {
            binding.viewPager.setCurrentItem(fragmentPosition, true)
        }
    }

    private fun scrollToPreferenceInCurrentFragment(preferenceKey: String, parentKey: String?) {
        val currentItem = binding.viewPager.currentItem
        val fragment = supportFragmentManager.findFragmentByTag("f$currentItem") ?: return

        if (fragment is GeneralFragment || fragment is HomeFragment) {
            if (!parentKey.isNullOrEmpty()) {
                navigateToSubFragmentAndScroll(fragment, parentKey, preferenceKey)
            } else {
                scrollInChildFragment(fragment, preferenceKey)
            }
        } else if (fragment is BasePreferenceFragment) {
            fragment.scrollToPreference(preferenceKey)
        }
    }

    private fun navigateToSubFragmentAndScroll(
        parentFragment: Fragment,
        parentKey: String,
        childPreferenceKey: String
    ) {
        val subFragment = when (parentKey) {
            "general_home" -> GeneralFragment.HomeGeneralPreference()
            "homescreen" -> GeneralFragment.HomeScreenGeneralPreference()
            "conversation" -> GeneralFragment.ConversationGeneralPreference()
            else -> null
        } ?: return

        val parentView = parentFragment.view ?: return
        parentFragment.childFragmentManager.beginTransaction()
            .replace(R.id.frag_container, subFragment)
            .commitNow()

        parentView.postDelayed({
            (subFragment as? BasePreferenceFragment)?.scrollToPreference(childPreferenceKey)
        }, 400)
    }

    private fun scrollInChildFragment(parentFragment: Fragment, preferenceKey: String) {
        val childFragment =
            parentFragment.childFragmentManager.findFragmentById(R.id.frag_container)
        (childFragment as? BasePreferenceFragment)?.scrollToPreference(preferenceKey)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        supportFragmentManager.fragments.forEach {
            it.onActivityResult(
                requestCode,
                resultCode,
                data
            )
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.header_menu, menu)
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
            menu.findItem(R.id.batteryoptimization).isVisible = false
        }
        return true
    }

    @SuppressLint("BatteryLife")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_search -> {
                val options = ActivityOptionsCompat.makeCustomAnimation(
                    this,
                    R.anim.slide_in_right,
                    R.anim.slide_out_left
                )
                startActivity(Intent(this, SearchActivity::class.java), options.toBundle())
                return true
            }

            R.id.menu_about -> {
                val options = ActivityOptionsCompat.makeCustomAnimation(
                    this,
                    R.anim.slide_in_right,
                    R.anim.slide_out_left
                )
                startActivity(Intent(this, AboutActivity::class.java), options.toBundle())
                return true
            }

            R.id.batteryoptimization -> {
                if (batteryPermissionHelper.isBatterySaverPermissionAvailable(this, true)) {
                    batteryPermissionHelper.getPermission(this, true, true)
                } else {
                    val batteryIntent = Intent().apply {
                        action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                        data = Uri.parse("package:$packageName")
                    }
                    @Suppress("DEPRECATION")
                    startActivityForResult(batteryIntent, 0)
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return super.onSupportNavigateUp()
    }


    private class DepthPageTransformer : ViewPager2.PageTransformer {
        override fun transformPage(page: android.view.View, position: Float) {
            val pageWidth = page.width
            when {
                position < -1 -> page.alpha = 0f
                position <= 0 -> {
                    page.alpha = 1f
                    page.translationX = 0f
                    page.translationZ = 0f
                    page.scaleX = 1f
                    page.scaleY = 1f
                }

                position <= 1 -> {
                    page.alpha = 1 - position
                    page.translationX = pageWidth * -position
                    page.translationZ = -1f
                    val scaleFactor = MIN_SCALE + (1 - MIN_SCALE) * (1 - abs(position))
                    page.scaleX = scaleFactor
                    page.scaleY = scaleFactor
                }

                else -> page.alpha = 0f
            }
        }

        companion object {
            private const val MIN_SCALE = 0.85f
        }
    }
}
