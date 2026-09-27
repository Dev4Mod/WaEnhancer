package com.wmods.wppenhacer.utils

import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File

object FilePicker {
    private var onFilePickedListener: OnFilePickedListener? = null
    private var onUriPickedListener: OnUriPickedListener? = null
    private lateinit var activity: AppCompatActivity

    lateinit var fileSalve: ActivityResultLauncher<String>
        private set
    lateinit var fileCapture: ActivityResultLauncher<Array<String>>
        private set
    lateinit var directoryCapture: ActivityResultLauncher<Uri?>
        private set
    lateinit var imageCapture: ActivityResultLauncher<PickVisualMediaRequest>
        private set

    fun registerFilePicker(activity: AppCompatActivity) {
        this.activity = activity
        fileCapture = activity.registerForActivityResult(ActivityResultContracts.OpenDocument(), ::setFile)
        imageCapture = activity.registerForActivityResult(ActivityResultContracts.PickVisualMedia(), ::setFile)
        directoryCapture = activity.registerForActivityResult(ActivityResultContracts.OpenDocumentTree(), ::setDirectory)
        fileSalve = activity.registerForActivityResult(ActivityResultContracts.CreateDocument("*/*"), ::setFile)
    }

    private fun setFile(uri: Uri?) {
        uri ?: return

        onUriPickedListener?.let { listener ->
            listener.onUriPicked(uri)
            onUriPickedListener = null
        }

        onFilePickedListener?.let { listener ->
            val realPath = try {
                RealPathUtil.getRealFilePath(activity, uri)
            } catch (_: Exception) {
                null
            } ?: return
            listener.onFilePicked(File(realPath))
            onFilePickedListener = null
        }
    }

    private fun setDirectory(uri: Uri?) {
        uri ?: return

        if (onFilePickedListener == null) {
            onUriPickedListener!!.onUriPicked(uri)
            onUriPickedListener = null
        }

        onFilePickedListener?.let { listener ->
            val realPath = try {
                RealPathUtil.getRealFolderPath(activity, uri)
            } catch (_: Exception) {
                null
            } ?: return
            listener.onFilePicked(File(realPath))
            onFilePickedListener = null
        }
    }

    fun setOnFilePickedListener(listener: OnFilePickedListener?) {
        onFilePickedListener = listener
        onUriPickedListener = null
    }

    fun setOnUriPickedListener(listener: OnUriPickedListener?) {
        onUriPickedListener = listener
        onFilePickedListener = null
    }

    fun interface OnFilePickedListener {
        fun onFilePicked(file: File)
    }

    fun interface OnUriPickedListener {
        fun onUriPicked(uri: Uri)
    }
}
