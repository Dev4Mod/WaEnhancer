package com.wmods.wppenhacer.activities

import android.content.Intent
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.widget.LinearLayout
import androidx.core.net.toUri
import com.google.android.material.button.MaterialButton
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.activities.base.BaseActivity
import com.wmods.wppenhacer.databinding.ActivityAboutBinding

class AboutActivity : BaseActivity() {
    private val contributors = arrayOf(
        arrayOf("Dev4Mod", "https://github.com/Dev4Mod"),
        arrayOf("frknkrc44", "https://github.com/frknkrc44"),
        arrayOf("mubashardev", "https://github.com/mubashardev"),
        arrayOf("masbentoooredoo", "https://github.com/masbentoooredoo"),
        arrayOf("zhongerxll", "https://github.com/zhongerxll"),
        arrayOf("BryanGIG", "https://github.com/BryanGIG"),
        arrayOf("rizqi-developer", "https://github.com/rizqi-developer"),
        arrayOf("pedroborraz", "https://github.com/pedroborraz"),
        arrayOf("ahmedtohamy1", "https://github.com/ahmedtohamy1"),
        arrayOf("mohdafix", "https://github.com/mohdafix"),
        arrayOf("maulana-kurniawan", "https://github.com/maulana-kurniawan"),
        arrayOf("erzachn", "https://github.com/erzachn"),
        arrayOf("cvnertnc", "https://github.com/cvnertnc"),
        arrayOf("rkorossy", "https://github.com/rkorossy"),
        arrayOf("StupidRepo", "https://github.com/StupidRepo"),
        arrayOf("Blank517", "https://github.com/Blank517"),
        arrayOf("astola-studio", "https://github.com/astola-studio"),
        arrayOf("Strange-IPmart", "https://github.com/Strange-IPmart")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnTelegram.setOnClickListener { openUrl("https://t.me/waenhancer") }
        binding.btnGithub.setOnClickListener { openUrl("https://github.com/Dev4Mod/WaEnhancer") }

        val contributorTopMargin = resources.getDimensionPixelSize(R.dimen.spacing_small)
        contributors.forEachIndexed { index, contributor ->
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                if (index > 0) topMargin = contributorTopMargin
            }
            val button =
                MaterialButton(ContextThemeWrapper(this, R.style.ModernButton_Outlined)).apply {
                    text = contributor[0]
                    setIconResource(R.drawable.ic_github)
                    iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                    iconPadding = resources.getDimensionPixelSize(R.dimen.spacing_small)
                    layoutParams = params
                    setOnClickListener { openUrl(contributor[1]) }
                }
            binding.contributorsContainer.addView(button)
        }
    }

    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }
}
