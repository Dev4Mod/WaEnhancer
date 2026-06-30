package com.wmods.wppenhacer.xposed.features.media

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.text.TextUtils
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.wmods.wppenhacer.xposed.bridge.WaeIIFace
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.FeatureLoader
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.utils.Utils
import de.robv.android.xposed.XC_MethodHook
import android.content.SharedPreferences 
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import org.json.JSONArray
import org.luckypray.dexkit.query.enums.StringMatchType
import java.io.File
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class CallRecording(
    loader: ClassLoader,
    preferences:SharedPreferences
) : Feature(loader, preferences) {

    private val isRecording = AtomicBoolean(false)
    private val isCallConnected = AtomicBoolean(false)
    private val mediaRecorderRef = AtomicReference<MediaRecorder?>()
    private val outputPfdRef = AtomicReference<ParcelFileDescriptor?>()
    private val outputStreamRef = AtomicReference<FileOutputStream?>()
    private val outputFileRef = AtomicReference<File?>()
    private val currentUserJid = AtomicReference<FMessageWpp.UserJid?>()
    private val delayedStartFuture = AtomicReference<ScheduledFuture<*>?>()

    private val delayedStartScheduler: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "WaEnhancer-CallDelayedStart").apply {
                isDaemon = true
            }
        }

    @Throws(Throwable::class)
    override fun doHook() {
        if (!prefs.getBoolean("call_recording_enable", false)) {
            logDebug("WaEnhancer: Call Recording is disabled")
            return
        }

        logDebug("WaEnhancer: Call Recording feature initializing...")
        hookCallStateChanges()
    }

    private fun hookCallStateChanges() {
        var hooksInstalled = 0

        try {
            val clsCallEventCallback = Unobfuscator.findFirstClassUsingName(
                classLoader,
                StringMatchType.EndsWith,
                "VoiceServiceEventCallback"
            )

            logDebug("WaEnhancer: Found VoiceServiceEventCallback: ${clsCallEventCallback.name}")

            try {
                XposedBridge.hookAllMethods(
                    clsCallEventCallback,
                    "fieldstatsReady",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            handleCallEnded("fieldstatsReady")
                        }
                    }
                )
                hooksInstalled++
            } catch (e: Throwable) {
                logDebug("WaEnhancer: Could not hook fieldstatsReady: ${e.message}")
            }

            try {
                XposedBridge.hookAllMethods(
                    clsCallEventCallback,
                    "soundPortCreated",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            logDebug("WaEnhancer: soundPortCreated - will record after 3s")
                            extractUserJid(param.thisObject)
                            isCallConnected.set(true)
                            scheduleDelayedStart()
                        }
                    }
                )
                hooksInstalled++
            } catch (e: Throwable) {
                logDebug("WaEnhancer: Could not hook soundPortCreated: ${e.message}")
            }
        } catch (e: Throwable) {
            logDebug("WaEnhancer: Could not hook VoiceServiceEventCallback: ${e.message}")
        }

        try {
            val voipActivityClass = Unobfuscator.findFirstClassUsingName(
                classLoader,
                StringMatchType.Contains,
                "VoipActivity"
            )

            if (Activity::class.java.isAssignableFrom(voipActivityClass)) {
                logDebug("WaEnhancer: Found VoipActivity: ${voipActivityClass.name}")

                XposedBridge.hookAllMethods(
                    voipActivityClass,
                    "onDestroy",
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            handleCallEnded("VoipActivity.onDestroy")
                        }
                    }
                )
                hooksInstalled++
            }
        } catch (e: Throwable) {
            logDebug("WaEnhancer: Could not hook VoipActivity: ${e.message}")
        }

        logDebug("WaEnhancer: Call Recording initialized with $hooksInstalled hooks")
    }

    private fun handleCallEnded(reason: String) {
        logDebug("WaEnhancer: Call ended by $reason")
        isCallConnected.set(false)
        cancelDelayedStart()
        stopRecording()
    }

    private fun scheduleDelayedStart() {
        cancelDelayedStart()

        try {
            val task = Runnable {
                if (!isCallConnected.get()) {
                    logDebug("WaEnhancer: Delayed start cancelled, call not connected")
                    return@Runnable
                }

                if (isRecording.get()) {
                    logDebug("WaEnhancer: Delayed start ignored, already recording")
                    return@Runnable
                }

                startRecording()
            }

            val future = delayedStartScheduler.schedule(task, 3, TimeUnit.SECONDS)

            delayedStartFuture.set(future)
        } catch (e: Throwable) {
            logDebug("WaEnhancer: Could not schedule delayed recording start: ${e.message}")
        }
    }

    private fun cancelDelayedStart() {
        delayedStartFuture.getAndSet(null)?.cancel(true)
    }

    private fun extractUserJid(callback: Any?) {
        if (callback == null) return

        try {
            val callInfo = XposedHelpers.callMethod(callback, "getCallInfo") ?: return

            val peerJid = runCatching {
                XposedHelpers.getObjectField(callInfo, "peerJid")
            }.getOrNull()

            if (peerJid != null && setCurrentUserJid(peerJid, "UserJid")) {
                return
            }

            val participantsObj = runCatching {
                XposedHelpers.getObjectField(callInfo, "participants")
            }.getOrNull()

            if (participantsObj is Map<*, *>) {
                for (key in participantsObj.keys) {
                    if (key != null && setCurrentUserJid(key, "single participant")) {
                        return
                    }
                }
            }
        } catch (e: Throwable) {
            logDebug("WaEnhancer: extractUserJid error: ${e.message}")
        }
    }

    private fun setCurrentUserJid(jidObject: Any, source: String): Boolean {
        val userJid = FMessageWpp.UserJid(jidObject)
        if (userJid.isNull) return false

        currentUserJid.set(userJid)
        logDebug("WaEnhancer: Found phone from $source: ${userJid.phoneNumber}")
        return true
    }

    private fun grantVoiceCallPermission() {
        if (permissionGranted.get()) return

        try {
            val app = FeatureLoader.mApp ?: run {
                logDebug("WaEnhancer: Could not grant permissions, app context is null")
                return
            }
            val packageName = app.packageName
            logDebug("WaEnhancer: Granting CAPTURE_AUDIO_OUTPUT via root")

            val commands = arrayOf(
                "pm grant $packageName android.permission.CAPTURE_AUDIO_OUTPUT",
                "appops set $packageName RECORD_AUDIO allow"
            )

            for (cmd in commands) {
                try {
                    val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                    val exitCode = process.waitFor()
                    logDebug("WaEnhancer: $cmd exit: $exitCode")
                } catch (e: Exception) {
                    logDebug("WaEnhancer: Root failed: ${e.message}")
                }
            }

            permissionGranted.set(true)
        } catch (e: Throwable) {
            logDebug("WaEnhancer: grantVoiceCallPermission error: ${e.message}")
        }
    }

    @Synchronized
    private fun startRecording() {
        if (isRecording.get()) {
            logDebug("WaEnhancer: Already recording")
            return
        }

        val cUserJid = currentUserJid.get()
        if (cUserJid != null && !shouldRecord(cUserJid.phoneNumber)) {
            logDebug("WaEnhancer: Skipping recording due to privacy settings for: ${cUserJid.phoneNumber}")
            return
        }

        if (!isCallConnected.get()) {
            logDebug("WaEnhancer: Skipping recording, call is not connected")
            return
        }

        try {
            val app = FeatureLoader.mApp ?: run {
                logDebug("WaEnhancer: Skipping recording, app context is null")
                return
            }

            if (ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                logDebug("WaEnhancer: No RECORD_AUDIO permission")
                return
            }

            val bridge = runCatching {
                WppCore.getClientBridge()
            }.onFailure {
                logDebug("WaEnhancer: Could not get client bridge: ${it.message}")
            }.getOrNull()

            val packageName = app.packageName
            val appName = if (packageName.contains("w4b")) "WA Business" else "WhatsApp"
            val defaultPath = Environment
                .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .absolutePath
            val settingsPath = prefs.getString("call_recording_path", defaultPath) ?: defaultPath

            val parentDir = File(settingsPath, "WA Call Recordings")
            val appDir = File(parentDir, appName)
            ensureOutputDirectory(appDir, bridge)

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = buildFileName(cUserJid, timestamp)
            val outputTarget = openOutputTarget(bridge, appDir, fileName)

            outputFileRef.set(outputTarget.file)
            outputPfdRef.set(outputTarget.parcelFileDescriptor)
            outputStreamRef.set(outputTarget.outputStream)

            if (prefs.getBoolean("call_recording_use_root", false)) {
                grantVoiceCallPermission()
            }

            val audioSources = intArrayOf(
                MediaRecorder.AudioSource.VOICE_CALL,
                MediaRecorder.AudioSource.VOICE_UPLINK,
                MediaRecorder.AudioSource.VOICE_DOWNLINK,
                6,
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                MediaRecorder.AudioSource.MIC
            )
            val sourceNames = arrayOf(
                "VOICE_CALL",
                "VOICE_UPLINK",
                "VOICE_DOWNLINK",
                "VOICE_RECOGNITION",
                "VOICE_COMMUNICATION",
                "MIC"
            )

            val recorderSelection = createStartedRecorder(audioSources, sourceNames, outputTarget.fd)
            if (recorderSelection == null) {
                logDebug("WaEnhancer: All audio sources failed")
                closeOutputResources(deleteOutputFile = false)
                return
            }

            mediaRecorderRef.set(recorderSelection.recorder)
            if (!isRecording.compareAndSet(false, true)) {
                releaseRecorder(recorderSelection.recorder, stopBeforeRelease = true)
                mediaRecorderRef.set(null)
                closeOutputResources(deleteOutputFile = false)
                return
            }

            logDebug("WaEnhancer: Recording started (${recorderSelection.sourceName}): ${outputTarget.file.absolutePath}")

            if (prefs.getBoolean("call_recording_toast", false)) {
                Utils.showToast("Recording started", Toast.LENGTH_SHORT)
            }
        } catch (e: Exception) {
            logDebug("WaEnhancer: startRecording error: ${e.message}")
            isRecording.set(false)
            mediaRecorderRef.getAndSet(null)?.let {
                releaseRecorder(it, stopBeforeRelease = false)
            }
            closeOutputResources(deleteOutputFile = true)
        }
    }

    private fun ensureOutputDirectory(appDir: File, bridge: WaeIIFace?) {
        if (appDir.exists()) return

        if (appDir.mkdirs() || appDir.exists()) return

        if (bridge != null) {
            val dirCreated = runCatching {
                bridge.createDir(appDir.absolutePath)
            }.getOrDefault(false)

            if (dirCreated || appDir.exists()) return
        }

        logDebug("WaEnhancer: Could not create preferred output directory, fallback may be used: ${appDir.absolutePath}")
    }

    private fun buildFileName(userJid: FMessageWpp.UserJid?, timestamp: String): String {
        if (userJid == null) return "Call_$timestamp.m4a"

        val contactName = runCatching {
            WppCore.getContactName(userJid)
        }.getOrNull()

        val identifier = if (contactName.isNullOrEmpty()) {
            userJid.phoneNumber
        } else {
            contactName
        }

        return "Call_${sanitizeFileNamePart(identifier)}_$timestamp.m4a"
    }

    private fun sanitizeFileNamePart(value: String?): String {
        val cleaned = value
            ?.replace(Regex("[\\\\/:*?\"<>|\\r\\n]+"), "_")
            ?.trim()
            .orEmpty()

        return cleaned.ifEmpty { "Unknown" }
    }

    private fun createStartedRecorder(
        audioSources: IntArray,
        sourceNames: Array<String>,
        outputFd: FileDescriptor
    ): RecorderSelection? {
        for (i in audioSources.indices) {
            val testRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(Utils.application)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            try {
                logDebug("WaEnhancer: Trying ${sourceNames[i]}")
                testRecorder.setAudioSource(audioSources[i])
                testRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                testRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                testRecorder.setAudioEncodingBitRate(96000)
                testRecorder.setAudioSamplingRate(44100)
                testRecorder.setOutputFile(outputFd)
                testRecorder.prepare()
                testRecorder.start()

                logDebug("WaEnhancer: SUCCESS ${sourceNames[i]}")
                return RecorderSelection(testRecorder, sourceNames[i])
            } catch (e: Exception) {
                logDebug("WaEnhancer: FAILED ${sourceNames[i]}: ${e.message}")
                releaseRecorder(testRecorder, stopBeforeRelease = false)
            }
        }

        return null
    }

    @Synchronized
    private fun stopRecording() {
        cancelDelayedStart()
        if (!isRecording.getAndSet(false)) return

        var saved = false
        var outputFile: File?

        try {
            mediaRecorderRef.getAndSet(null)?.let { recorder ->
                try {
                    recorder.stop()
                    saved = true
                } catch (e: RuntimeException) {
                    logDebug("WaEnhancer: MediaRecorder stop exception (no valid audio data received): $e")
                } finally {
                    releaseRecorder(recorder, stopBeforeRelease = false)
                }
            }

            outputFile = outputFileRef.get()
            closeOutputResources(deleteOutputFile = !saved)

            logDebug("WaEnhancer: Recording stopped, file=${outputFile?.absolutePath ?: "unknown"}")

            if (saved && outputFile != null) {
                Utils.scanFile(outputFile)
            }

            if (prefs.getBoolean("call_recording_toast", false)) {
                Utils.showToast(if (saved) "Recording saved!" else "Recording failed", Toast.LENGTH_SHORT)
            }

            currentUserJid.set(null)
        } catch (e: Exception) {
            logDebug("WaEnhancer: stopRecording error: ${e.message}")
            closeOutputResources(deleteOutputFile = false)
            outputFileRef.set(null)
        }
    }

    private fun releaseRecorder(recorder: MediaRecorder, stopBeforeRelease: Boolean) {
        if (stopBeforeRelease) {
            try {
                recorder.stop()
            } catch (_: RuntimeException) {
            }
        }

        try {
            recorder.reset()
            recorder.release()
        } catch (_: Throwable) {
        }
    }

    private fun closeOutputResources(deleteOutputFile: Boolean) {
        val stream = outputStreamRef.getAndSet(null)
        val pfd = outputPfdRef.getAndSet(null)
        val outputFile = outputFileRef.getAndSet(null)

        try {
            stream?.close()
        } catch (_: IOException) {
        }

        try {
            pfd?.close()
        } catch (_: IOException) {
        }

        if (deleteOutputFile && outputFile != null && outputFile.exists() && !outputFile.delete()) {
            logDebug("WaEnhancer: Could not delete incomplete recording: ${outputFile.absolutePath}")
        }
    }

    private fun openOutputTarget(
        bridge: WaeIIFace?,
        preferredDir: File,
        fileName: String
    ): OutputTarget {
        val preferredFile = File(preferredDir, fileName)

        if (bridge != null) {
            try {
                val parcelFileDescriptor = bridge.openFile(preferredFile.absolutePath, true)
                if (parcelFileDescriptor != null) {
                    return OutputTarget(
                        preferredFile,
                        parcelFileDescriptor,
                        null,
                        parcelFileDescriptor.fileDescriptor
                    )
                }
                logDebug("WaEnhancer: Bridge openFile returned null, fallback to Android/data path")
            } catch (t: Throwable) {
                logDebug("WaEnhancer: Bridge openFile failed, fallback to Android/data path: ${t.message}")
            }
        }

        val app = FeatureLoader.mApp ?: throw IOException("Could not resolve app context")
        val appExternalDir = app.getExternalFilesDir(null)
            ?: throw IOException("Could not resolve app external files directory")

        val fallbackDir = File(appExternalDir, "Recordings")
        if (!fallbackDir.exists() && !fallbackDir.mkdirs()) {
            throw IOException("Could not create fallback recording directory: ${fallbackDir.absolutePath}")
        }

        val fallbackFile = File(fallbackDir, fileName)
        val fallbackStream = FileOutputStream(fallbackFile)
        logDebug("WaEnhancer: Recording fallback path in Android/data: ${fallbackFile.absolutePath}")

        return OutputTarget(fallbackFile, null, fallbackStream, fallbackStream.fd)
    }

    private data class OutputTarget(
        val file: File,
        val parcelFileDescriptor: ParcelFileDescriptor?,
        val outputStream: FileOutputStream?,
        val fd: FileDescriptor
    )

    private data class RecorderSelection(
        val recorder: MediaRecorder,
        val sourceName: String
    )

    private fun shouldRecord(phoneNumber: String?): Boolean {
        try {
            val mode = prefs.getString("call_recording_mode", "0")?.toIntOrNull() ?: 0

            if (mode == 0) return true

            val blacklist = prefs.getString("call_recording_blacklist", "[]")
            val whitelist = prefs.getString("call_recording_whitelist", "[]")

            return when (mode) {
                2 -> {
                    if (phoneNumber == null) return true
                    val cleanPhone = phoneNumber.replace(Regex("[^0-9]"), "")
                    !isNumberInList(cleanPhone, blacklist)
                }

                3 -> {
                    if (whitelist.isNullOrEmpty() || whitelist == "[]") return false
                    if (phoneNumber == null) return false

                    val cleanPhone = phoneNumber.replace(Regex("[^0-9]"), "")
                    isNumberInList(cleanPhone, whitelist)
                }

                1 -> true
                else -> true
            }
        } catch (e: Exception) {
            logDebug("WaEnhancer: shouldRecord check error: ${e.message}")
        }

        return true
    }

    private fun isNumberInList(phone: String, jsonList: String?): Boolean {
        if (TextUtils.isEmpty(jsonList) || jsonList == "[]") return false

        try {
            val array = JSONArray(jsonList)
            for (i in 0 until array.length()) {
                val num = array.getString(i).replace(Regex("[^0-9]"), "")
                if (num == phone) return true
            }
        } catch (e: Exception) {
            logDebug("WaEnhancer: Error parsing list: ${e.message}")
        }

        return false
    }

    override fun getPluginName(): String = "Call Recording"

    companion object {
        private val permissionGranted = AtomicBoolean(false)
    }
}
