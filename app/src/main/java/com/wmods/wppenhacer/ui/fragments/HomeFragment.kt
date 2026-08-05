package com.wmods.wppenhacer.ui.fragments

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wmods.wppenhacer.App
import com.wmods.wppenhacer.BuildConfig
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.activities.MainActivity
import com.wmods.wppenhacer.adapter.LogLineAdapter
import com.wmods.wppenhacer.databinding.DialogDiagnosticsLogBinding
import com.wmods.wppenhacer.databinding.FragmentHomeBinding
import com.wmods.wppenhacer.ui.fragments.base.BaseFragment
import com.wmods.wppenhacer.utils.FilePicker
import com.wmods.wppenhacer.utils.RootDiagnostics
import com.wmods.wppenhacer.xposed.core.FeatureLoader
import com.wmods.wppenhacer.xposed.utils.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import rikka.core.util.IOUtils
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.HashSet
import java.util.Locale
import java.util.concurrent.TimeUnit

class HomeFragment : BaseFragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intentFilter = IntentFilter("${BuildConfig.APPLICATION_ID}.RECEIVER_WPP")
        ContextCompat.registerReceiver(requireContext(), object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                try {
                    if (FeatureLoader.PACKAGE_WPP == intent.getStringExtra("PKG")) {
                        receiverBroadcastWpp(context, intent)
                    } else {
                        receiverBroadcastBusiness(context, intent)
                    }
                } catch (ignored: Exception) {
                }
            }
        }, intentFilter, ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        checkStateWpp(requireActivity())

        binding.rebootBtn.setOnClickListener { view ->
            animateClick(view)
            App.instance.restartApp(FeatureLoader.PACKAGE_WPP)
            disableWpp()
        }

        binding.scrollDiagBtn.setOnClickListener { view ->
            animateClick(view)
            binding.nestedScrollView.post {
                _binding?.let { b ->
                    b.nestedScrollView.smoothScrollTo(0, b.diagCard.top)
                }
            }
        }

        binding.rebootBtn2.setOnClickListener { view ->
            animateClick(view)
            App.instance.restartApp(FeatureLoader.PACKAGE_BUSINESS)
            disableBusiness()
        }

        binding.exportBtn.setOnClickListener { view ->
            animateClick(view)
            saveConfigs(requireContext())
        }

        binding.importBtn.setOnClickListener { view ->
            animateClick(view)
            importConfigs(requireContext())
        }

        binding.resetBtn.setOnClickListener { view ->
            animateClick(view)
            resetConfigs(requireContext())
        }

        binding.updateCard.setOnClickListener { view ->
            animateClick(view)
            Utils.openLink(requireActivity(), "https://t.me/waenhancher")
        }

        binding.diagBtn.setOnClickListener { view ->
            animateClick(view)
            showDiagnosticsDialog()
        }

        checkForUpdates()
        startCardAnimations()

        return binding.root
    }

    private fun startCardAnimations() {
        val context = context ?: return
        val slideUp = AnimationUtils.loadAnimation(context, R.anim.slide_up)
        val fadeIn = AnimationUtils.loadAnimation(context, R.anim.fade_in)

        binding.status.startAnimation(slideUp)

        binding.status2.postDelayed({
            if (!isAdded || _binding == null) return@postDelayed
            val anim = AnimationUtils.loadAnimation(requireContext(), R.anim.slide_up)
            binding.status2.startAnimation(anim)
        }, 100)

        binding.status3.postDelayed({
            if (!isAdded || _binding == null) return@postDelayed
            val anim = AnimationUtils.loadAnimation(requireContext(), R.anim.slide_up)
            binding.status3.startAnimation(anim)
        }, 200)

        binding.infoCard.postDelayed({
            if (!isAdded || _binding == null) return@postDelayed
            binding.infoCard.startAnimation(fadeIn)
        }, 300)

        binding.updateCard.postDelayed({
            if (!isAdded || _binding == null) return@postDelayed
            val anim = AnimationUtils.loadAnimation(requireContext(), R.anim.slide_up)
            binding.updateCard.startAnimation(anim)
        }, 400)
    }

    private fun animateClick(view: View) {
        val scaleIn = AnimationUtils.loadAnimation(context, R.anim.scale_in)
        view.startAnimation(scaleIn)
    }

    override fun onResume() {
        super.onResume()
        setDisplayHomeAsUpEnabled(false)
    }

    private fun receiverBroadcastBusiness(context: Context, intent: Intent) {
        if (App.isOriginalPackage) binding.status3.visibility = View.VISIBLE
        binding.statusTitle3.setText(R.string.business_in_background)
        val version = intent.getStringExtra("VERSION")
        val supportedList = context.resources.getStringArray(R.array.supported_versions_business).toList()
        if (version != null && supportedList.any { s -> version.startsWith(s.replace(".xx", "")) }) {
            binding.statusSummary3.text = getString(R.string.version_s, version)
            binding.status3.getChildAt(0).setBackgroundResource(R.drawable.gradient_success)
        } else {
            binding.statusSummary3.text = getString(R.string.version_s_not_listed, version)
            binding.status3.getChildAt(0).setBackgroundResource(R.drawable.gradient_warning)
        }
        binding.rebootBtn2.visibility = View.VISIBLE
        binding.statusSummary3.visibility = View.VISIBLE
        binding.statusIcon3.setImageResource(R.drawable.ic_round_check_circle_24)
    }

    private fun receiverBroadcastWpp(context: Context, intent: Intent) {
        binding.statusTitle2.setText(R.string.whatsapp_in_background)
        val version = intent.getStringExtra("VERSION")
        val supportedList = context.resources.getStringArray(R.array.supported_versions_wpp).toList()

        if (version != null && supportedList.any { s -> version.startsWith(s.replace(".xx", "")) }) {
            binding.statusSummary1.text = getString(R.string.version_s, version)
            binding.status2.getChildAt(0).setBackgroundResource(R.drawable.gradient_success)
        } else {
            binding.statusSummary1.text = getString(R.string.version_s_not_listed, version)
            binding.status2.getChildAt(0).setBackgroundResource(R.drawable.gradient_warning)
        }
        binding.rebootBtn.visibility = View.VISIBLE
        binding.statusSummary1.visibility = View.VISIBLE
        binding.statusIcon2.setImageResource(R.drawable.ic_round_check_circle_24)
    }

    private fun resetConfigs(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = prefs.edit()
        prefs.all.keys.forEach { key -> editor.remove(key) }
        editor.apply()
        App.instance.restartApp(FeatureLoader.PACKAGE_WPP)
        App.instance.restartApp(FeatureLoader.PACKAGE_BUSINESS)
        Utils.showToast(context.getString(R.string.configs_reset), Toast.LENGTH_SHORT)
    }

    @Throws(JSONException::class)
    private fun getJsonObject(prefs: SharedPreferences): JSONObject {
        val entries = prefs.all
        val jsonObject = JSONObject()
        for ((key, value) in entries) {
            val type = JSONObject()
            var keyValue: Any? = value
            if (keyValue is HashSet<*>) {
                keyValue = JSONArray(ArrayList(keyValue))
            }
            if (keyValue != null) {
                type.put("type", keyValue.javaClass.simpleName)
                type.put("value", keyValue)
                jsonObject.put(key, type)
            }
        }
        return jsonObject
    }

    private fun saveConfigs(context: Context) {
        FilePicker.setOnUriPickedListener { uri ->
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                        val jsonObject = getJsonObject(prefs)
                        output.write(jsonObject.toString(4).toByteArray())
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.configs_saved), Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
        val formattedDate = dateFormat.format(Date())
        FilePicker.fileSalve.launch("wpp_enhacer_configs_$formattedDate.json")
    }

    private fun importConfigs(context: Context) {
        FilePicker.setOnUriPickedListener { uri ->
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val data = IOUtils.toString(input)
                        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                        val jsonObject = JSONObject(data)

                        val editor = prefs.edit()
                        prefs.all.keys.forEach { key -> editor.remove(key) }

                        val keys = jsonObject.keys()
                        while (keys.hasNext()) {
                            val keyName = keys.next()
                            var value = jsonObject.get(keyName)
                            var type = value.javaClass.simpleName
                            if (value is JSONObject) {
                                type = value.getString("type")
                                value = value.get("value")
                            }

                            if (type == JSONArray::class.java.simpleName) {
                                val jsonArray = value as JSONArray
                                val hashSet = HashSet<String>()
                                for (i in 0 until jsonArray.length()) {
                                    hashSet.add(jsonArray.getString(i))
                                }
                                editor.putStringSet(keyName, hashSet)
                            } else if (type == String::class.java.simpleName) {
                                editor.putString(keyName, value as String)
                            } else if (type == Boolean::class.java.simpleName || type == "boolean") {
                                editor.putBoolean(keyName, value as Boolean)
                            } else if (type == Integer::class.java.simpleName || type == "int") {
                                editor.putInt(keyName, value as Int)
                            } else if (type == Long::class.java.simpleName || type == "long") {
                                editor.putLong(keyName, (value as Number).toLong())
                            } else if (type == Double::class.java.simpleName || type == Float::class.java.simpleName) {
                                editor.putFloat(keyName, (value as Number).toFloat())
                            }
                        }
                        editor.apply()
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.configs_imported), Toast.LENGTH_SHORT).show()
                        App.instance.restartApp(FeatureLoader.PACKAGE_WPP)
                        App.instance.restartApp(FeatureLoader.PACKAGE_BUSINESS)
                    }
                } catch (e: Exception) {
                    Log.e("importConfigs", e.message ?: "", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        FilePicker.fileCapture.launch(arrayOf("application/json"))
    }

    private fun checkStateWpp(activity: FragmentActivity) {
        if (MainActivity.isXposedEnabled()) {
            binding.statusIcon.setImageResource(R.drawable.ic_round_check_circle_24)
            binding.statusTitle.setText(R.string.module_enabled)
            binding.statusSummary.text = String.format(getString(R.string.version_s), BuildConfig.VERSION_NAME)
            binding.status.getChildAt(0).setBackgroundResource(R.drawable.gradient_success)
        } else {
            binding.statusIcon.setImageResource(R.drawable.ic_round_error_outline_24)
            binding.statusTitle.setText(R.string.module_disabled)
            binding.status.getChildAt(0).setBackgroundResource(R.drawable.gradient_error)
            binding.statusSummary.visibility = View.GONE
        }
        if (isInstalled(FeatureLoader.PACKAGE_WPP) && App.isOriginalPackage) {
            disableWpp()
        } else {
            binding.status2.visibility = View.GONE
        }
        if (App.isOriginalPackage) {
            binding.status3.visibility = View.GONE
        }
        checkWpp(activity)
        binding.deviceName.text = Build.MANUFACTURER
        binding.sdk.text = Build.VERSION.SDK_INT.toString()
        binding.modelName.text = Build.DEVICE
        if (App.isOriginalPackage) {
            binding.listWpp.text = activity.resources.getStringArray(R.array.supported_versions_wpp).contentToString()
        } else {
            binding.listWppTitle.visibility = View.GONE
            binding.listWpp.visibility = View.GONE
        }
        binding.listBusiness.text = activity.resources.getStringArray(R.array.supported_versions_business).contentToString()
    }

    private fun isInstalled(packageWpp: String): Boolean {
        return try {
            App.instance.packageManager.getPackageInfo(packageWpp, 0)
            true
        } catch (ignored: Exception) {
            false
        }
    }

    private fun disableBusiness() {
        binding.statusIcon3.setImageResource(R.drawable.ic_round_error_outline_24)
        binding.statusTitle3.setText(R.string.business_is_not_running_or_has_not_been_activated_in_lsposed)
        binding.status3.getChildAt(0).setBackgroundResource(R.drawable.gradient_error)
        binding.statusSummary3.visibility = View.GONE
        binding.rebootBtn2.visibility = View.GONE
    }

    private fun disableWpp() {
        binding.statusIcon2.setImageResource(R.drawable.ic_round_error_outline_24)
        binding.statusTitle2.setText(R.string.whatsapp_is_not_running_or_has_not_been_activated_in_lsposed)
        binding.status2.getChildAt(0).setBackgroundResource(R.drawable.gradient_error)
        binding.statusSummary1.visibility = View.GONE
        binding.rebootBtn.visibility = View.GONE
    }

    private fun checkWpp(activity: FragmentActivity) {
        val checkWpp = Intent("${BuildConfig.APPLICATION_ID}.CHECK_WPP")
        activity.sendBroadcast(checkWpp)
    }

    private fun checkForUpdates() {
        if (context == null) return
        binding.updateSummary.text = getString(R.string.current_version_s, BuildConfig.VERSION_NAME)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build()

                val request = Request.Builder()
                    .url("https://api.github.com/repos/Dev4Mod/WaEnhancer/releases/latest")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        updateCardState(false, false, null)
                        return@use
                    }

                    val body = response.body
                    val content = body?.string() ?: ""
                    val release = JSONObject(content)
                    val tagName = release.optString("tag_name", "")

                    if (tagName.isBlank()) {
                        updateCardState(true, true, null)
                        return@use
                    }

                    val parts = tagName.split("-")
                    val hash = if (parts.size > 1) parts[1].trim() else ""
                    val isNewVersion = hash.isNotEmpty() && !BuildConfig.VERSION_NAME.lowercase(Locale.ROOT).contains(hash.lowercase(Locale.ROOT))

                    updateCardState(true, !isNewVersion, tagName)
                }
            } catch (e: UnknownHostException) {
                updateCardState(false, false, null)
            } catch (e: Exception) {
                updateCardState(false, false, null)
            }
        }
    }

    private suspend fun updateCardState(success: Boolean, isUpToDate: Boolean, newVersion: String?) {
        withContext(Dispatchers.Main) {
            if (_binding == null || !isAdded) return@withContext

            if (!success) {
                binding.updateIcon.setImageResource(R.drawable.ic_round_error_outline_24)
                binding.updateTitle.setText(R.string.update_check_failed)
                binding.updateSummary.setText(R.string.update_check_failed_summary)
                binding.updateCard.getChildAt(0).setBackgroundResource(R.drawable.gradient_warning)
            } else if (isUpToDate) {
                binding.updateIcon.setImageResource(R.drawable.ic_round_check_circle_24)
                binding.updateTitle.setText(R.string.up_to_date)
                binding.updateSummary.text = getString(R.string.current_version_s, BuildConfig.VERSION_NAME)
                binding.updateCard.getChildAt(0).setBackgroundResource(R.drawable.gradient_success)
            } else {
                binding.updateIcon.setImageResource(R.drawable.ic_round_update_24)
                binding.updateTitle.setText(R.string.update_available)
                binding.updateSummary.text = getString(R.string.update_available_summary, newVersion)
                binding.updateCard.getChildAt(0).setBackgroundResource(R.drawable.gradient_update)
            }
        }
    }

    private fun showDiagnosticsDialog() {
        val context = requireContext()
        val dialogBinding = DialogDiagnosticsLogBinding.inflate(LayoutInflater.from(context))
        val logAdapter = LogLineAdapter()

        dialogBinding.logRecycler.layoutManager = LinearLayoutManager(context)
        dialogBinding.logRecycler.adapter = logAdapter

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.diag_dialog_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.diag_close, null)
            .setCancelable(true)
            .show()

        val handler = Handler(Looper.getMainLooper())
        val queue = ArrayList<RootDiagnostics.LogEntry>()

        RootDiagnostics.runDiagnostics(context) { entry ->
            if (!isAdded) return@runDiagnostics
            queue.add(entry)
        }

        val poller = object : Runnable {
            private var emptyCycles = 0

            override fun run() {
                if (!isAdded || _binding == null || !dialog.isShowing) return

                if (queue.isNotEmpty()) {
                    emptyCycles = 0
                    logAdapter.add(queue.removeAt(0))
                    dialogBinding.logRecycler.smoothScrollToPosition(logAdapter.itemCount - 1)
                    handler.postDelayed(this, 120)
                } else if (emptyCycles < 50) {
                    emptyCycles++
                    handler.postDelayed(this, 120)
                }
            }
        }
        handler.postDelayed(poller, 120)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
