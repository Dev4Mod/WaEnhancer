package com.wmods.wppenhacer.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.wmods.wppenhacer.ui.fragments.CustomizationFragment
import com.wmods.wppenhacer.ui.fragments.GeneralFragment
import com.wmods.wppenhacer.ui.fragments.HomeFragment
import com.wmods.wppenhacer.ui.fragments.MediaFragment
import com.wmods.wppenhacer.ui.fragments.PrivacyFragment
import com.wmods.wppenhacer.ui.fragments.RecordingsFragment

class MainPagerAdapter(fragmentActivity: FragmentActivity) :
    FragmentStateAdapter(fragmentActivity) {
    private val isRecordingEnabled = PreferenceManager.getDefaultSharedPreferences(fragmentActivity)
        .getBoolean("call_recording_enable", false)

    override fun createFragment(position: Int): Fragment = when (position) {
        1 -> GeneralFragment()
        2 -> PrivacyFragment()
        3 -> MediaFragment()
        4 -> CustomizationFragment()
        5 -> RecordingsFragment()
        else -> HomeFragment()
    }

    override fun getItemCount(): Int = if (isRecordingEnabled) 6 else 5
}
