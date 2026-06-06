package com.wmods.wppenhacer.activities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.activities.base.BaseActivity
import com.wmods.wppenhacer.databinding.ActivityCrashReportBinding

class CrashReportActivity : BaseActivity() {

    private lateinit var binding: ActivityCrashReportBinding
    private lateinit var reportText: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCrashReportBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.whatsapp_crash_report)

        val crashInfo = intent.getStringExtra(EXTRA_CRASH_INFO).orEmpty()
        val crashTrace = intent.getStringExtra(EXTRA_CRASH_TRACE).orEmpty()
        reportText = "$crashInfo\n\n$crashTrace"

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.tvCrashInfo.text = crashInfo
        binding.tvCrashTrace.text = crashTrace
        binding.btnCopy.setOnClickListener { copyReport() }
        binding.btnShare.setOnClickListener { shareReport() }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun copyReport() {
        reportText = "${binding.tvCrashInfo.text}\n\n${binding.tvCrashTrace.text}"
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.whatsapp_crash_report), reportText))
        Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }

    private fun shareReport() {
        reportText = "${binding.tvCrashInfo.text}\n\n${binding.tvCrashTrace.text}"
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.whatsapp_crash_report))
            .putExtra(Intent.EXTRA_TEXT, reportText)
        startActivity(Intent.createChooser(intent, getString(R.string.share)))
    }

    companion object {
        const val EXTRA_CRASH_INFO = "crash_info"
        const val EXTRA_CRASH_TRACE = "crash_trace"
    }
}
