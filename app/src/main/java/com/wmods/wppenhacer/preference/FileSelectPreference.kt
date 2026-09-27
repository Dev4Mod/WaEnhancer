package com.wmods.wppenhacer.preference

import android.Manifest
import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.AttributeSet
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.developer.filepicker.model.DialogConfigs
import com.developer.filepicker.model.DialogProperties
import com.developer.filepicker.view.FilePickerDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wmods.wppenhacer.App
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.utils.FilePicker
import com.wmods.wppenhacer.xposed.utils.Utils
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.CompletableFuture
import androidx.core.content.edit

class FileSelectPreference : Preference, Preference.OnPreferenceClickListener,
    FilePicker.OnFilePickedListener, FilePicker.OnUriPickedListener {

    private var mineTypes: Array<String> = arrayOf("*/*")
    private var selectDirectory = false

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) :
        super(context, attrs, defStyleAttr, defStyleRes) {
        init(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context, attrs)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun showAlertPermission() {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.storage_permission)
            .setMessage(R.string.permission_storage)
            .setPositiveButton(R.string.allow) { _, _ ->
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            }
            .setNegativeButton(R.string.deny) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    override fun onPreferenceClick(preference: Preference): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            showAlertPermission()
            return true
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                (context as Activity).requestPermissions(arrayOf(Manifest.permission.READ_MEDIA_IMAGES), 1)
                return true
            }
        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            (context as Activity).requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 1)
            return true
        }

        FilePicker.setOnFilePickedListener(this)
        if (selectDirectory) {
            showSelectDirectoryDialog()
            return true
        }

        if (mineTypes.size == 1 && mineTypes[0].contains("image")) {
            FilePicker.setOnUriPickedListener(this)
            FilePicker.imageCapture.launch(
                PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.SingleMimeType(mineTypes[0]))
                    .build()
            )
            return true
        }
        FilePicker.fileCapture.launch(mineTypes)
        return false
    }

    private fun showSelectDirectoryDialog() {
        val properties = DialogProperties().apply {
            selection_mode = DialogConfigs.SINGLE_MODE
            selection_type = DialogConfigs.DIR_SELECT
            root = File(DialogConfigs.DEFAULT_DIR)
            error_dir = File(DialogConfigs.DEFAULT_DIR)
            offset = File(DialogConfigs.DEFAULT_DIR)
        }
        val dialog = FilePickerDialog(context, properties)
        dialog.setTitle("Select a local to download")
        dialog.setDialogSelectionListener { selectionPaths ->
            getSharedPreferences()!!.edit { putString(key, selectionPaths[0]) }
            summary = selectionPaths[0]
        }
        dialog.show()
        Utils.showToast("Select a local to download", Toast.LENGTH_SHORT)
    }

    override fun onFilePicked(file: File) {
        if (file.isDirectory) {
            try {
                Files.write(File(file, "tmp.file").toPath(), byteArrayOf()).toFile().delete()
            } catch (_: Exception) {
                Toast.makeText(context, R.string.failed_save_directory, Toast.LENGTH_SHORT).show()
                return
            }
        } else if (!file.canRead()) {
            Toast.makeText(context, R.string.unable_to_read_this_file, Toast.LENGTH_SHORT).show()
            return
        }
        getSharedPreferences()!!.edit { putString(key, file.absolutePath) }
        summary = file.absolutePath
    }

    private fun init(context: Context, attrs: AttributeSet?) {
        onPreferenceClickListener = this
        val typedArray = context.theme.obtainStyledAttributes(attrs, R.styleable.FileSelectPreference, 0, 0)
        val values = typedArray.getTextArray(R.styleable.FileSelectPreference_android_entryValues)
        mineTypes = values?.map(CharSequence::toString)?.toTypedArray() ?: arrayOf("*/*")
        selectDirectory = typedArray.getBoolean(R.styleable.FileSelectPreference_directory, false)
        typedArray.recycle()

        summary = PreferenceManager.getDefaultSharedPreferences(context).getString(key, null)
    }

    override fun onUriPicked(uri: Uri) {
        val contentResolver: ContentResolver = context.contentResolver
        val extension = requireNotNull(contentResolver.getType(uri)).split("/")[1]
        val folder = File(App.waEnhancerFolder, "files")
        if (!folder.exists()) folder.mkdirs()
        val outFile = File(folder, "$key.$extension")
        val editor = sharedPreferences!!.edit()
        editor.putString(key, "").apply()
        summary = outFile.absolutePath

        CompletableFuture.runAsync {
            try {
                contentResolver.openInputStream(uri)!!.use { input ->
                    Files.copy(input, outFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            } catch (exception: Exception) {
                Utils.showToast("Failed to save file: $exception", Toast.LENGTH_SHORT)
            }
            editor.putString(key, outFile.absolutePath).apply()
        }
    }
}
