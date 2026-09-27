package com.wmods.wppenhacer.activities

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.google.android.material.appbar.MaterialToolbar
import com.wmods.wppenhacer.R
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

class CallRecordingSettingsActivity : AppCompatActivity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var radioGroupMode: RadioGroup
    private lateinit var radioRoot: RadioButton
    private lateinit var radioNonRoot: RadioButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call_recording_settings)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setTitle(R.string.call_recording_settings)
        }

        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        radioGroupMode = findViewById(R.id.radio_group_mode)
        radioRoot = findViewById(R.id.radio_root)
        radioNonRoot = findViewById(R.id.radio_non_root)

        val useRoot = prefs.getBoolean("call_recording_use_root", false)
        Log.d(TAG, "Loaded call_recording_use_root: $useRoot")
        if (useRoot) radioRoot.isChecked = true else radioNonRoot.isChecked = true

        radioRoot.setOnClickListener {
            Log.d(TAG, "Root mode clicked")
            radioRoot.isChecked = true
            radioNonRoot.isChecked = false
            Toast.makeText(this, "Checking root access...", Toast.LENGTH_SHORT).show()
            checkRootAccess()
        }

        radioNonRoot.setOnClickListener {
            Log.d(TAG, "Non-root mode clicked")
            radioNonRoot.isChecked = true
            radioRoot.isChecked = false
            val saved = prefs.edit().putBoolean("call_recording_use_root", false).commit()
            Log.d(TAG, "Saved non-root preference: $saved")
            Toast.makeText(this, R.string.non_root_mode_enabled, Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkRootAccess() {
        Thread {
            var hasRoot: Boolean
            var rootOutput = ""
            try {
                Log.d(TAG, "Executing su command...")
                val process = Runtime.getRuntime().exec("su")
                val output = DataOutputStream(process.outputStream)
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                output.writeBytes("id\n")
                output.writeBytes("exit\n")
                output.flush()
                rootOutput = reader.readLines().joinToString("")

                val exitCode = process.waitFor()
                Log.d(TAG, "Root check exit code: $exitCode, output: $rootOutput")
                hasRoot = exitCode == 0 && rootOutput.contains("uid=0")
            } catch (exception: Exception) {
                Log.e(TAG, "Root check exception: ${exception.message}")
                hasRoot = false
            }

            val rootGranted = hasRoot
            val output = rootOutput
            runOnUiThread {
                if (rootGranted) {
                    val saved = prefs.edit().putBoolean("call_recording_use_root", true).commit()
                    Log.d(TAG, "Root granted, saved preference: $saved")
                    Toast.makeText(this, R.string.root_access_granted, Toast.LENGTH_SHORT).show()
                } else {
                    val saved = prefs.edit().putBoolean("call_recording_use_root", false).commit()
                    Log.d(TAG, "Root denied, saved preference: $saved, output: $output")
                    radioNonRoot.isChecked = true
                    Toast.makeText(this, R.string.root_access_denied, Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        private const val TAG = "WaEnhancer"
    }
}
