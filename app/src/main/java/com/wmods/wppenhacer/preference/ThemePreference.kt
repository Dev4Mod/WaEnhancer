package com.wmods.wppenhacer.preference

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.text.TextUtils
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wmods.wppenhacer.App
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.activities.TextEditorActivity
import com.wmods.wppenhacer.utils.FilePicker
import com.wmods.wppenhacer.xposed.utils.Utils
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.CompletableFuture
import java.util.zip.ZipInputStream

class ThemePreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs),
    FilePicker.OnUriPickedListener {

    private var mainDialog: AlertDialog? = null

    init {
        isPersistent = false
    }

    override fun onClick() {
        super.onClick()
        val needsPermission =
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) ||
                    (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) != PackageManager.PERMISSION_GRANTED)
        if (needsPermission) {
            App.showRequestStoragePermission(context as Activity)
        } else {
            showThemeDialog()
        }
    }

    @SuppressLint("ApplySharedPref")
    private fun showThemeDialog() {
        val currentContext = context
        val folders = getFolders().toMutableList().apply { add(0, "Default Theme") }
        val selectedFolder = sharedPreferences!!.getString(key, null)

        val dialogView =
            LayoutInflater.from(currentContext).inflate(R.layout.preference_theme, null)
        val builder = MaterialAlertDialogBuilder(currentContext).setView(dialogView)
        val folderListContainer = dialogView.findViewById<LinearLayout>(R.id.folder_list_container)
        val newThemeButton = dialogView.findViewById<Button>(R.id.create_theme_button)
        newThemeButton.setOnClickListener { showCreateNewThemeDialog() }

        val importThemeButton = dialogView.findViewById<Button>(R.id.import_theme_button)
        importThemeButton.setOnClickListener {
            FilePicker.setOnUriPickedListener(this)
            FilePicker.fileCapture.launch(arrayOf("application/zip"))
        }

        folders.forEach { folder ->
            val cssFile = File(rootDirectory, "$folder/style.css")
            if (!cssFile.exists() && folder != "Default Theme") return@forEach

            val itemView =
                LayoutInflater.from(currentContext).inflate(R.layout.item_folder, null, false)
            val folderNameView = itemView.findViewById<TextView>(R.id.folder_name)
            folderNameView.text = folder
            if (folder == selectedFolder) {
                folderNameView.setTextColor(
                    com.google.android.material.color.MaterialColors.getColor(
                        currentContext,
                        R.attr.colorPrimary,
                        0
                    )
                )
            }
            if (cssFile.exists()) {
                val author = Utils.getAuthorFromCss(cssFile.readText(Charset.defaultCharset()))
                if (!TextUtils.isEmpty(author)) {
                    itemView.findViewById<TextView>(R.id.author).text = author
                }
            }

            itemView.setOnClickListener {
                val preferences = PreferenceManager.getDefaultSharedPreferences(currentContext)
                preferences.edit(commit = true) { putString(key, folder) }
                preferences.edit(commit = true) {
                    putString(
                        "custom_css",
                        if (cssFile.exists()) cssFile.readText(Charset.defaultCharset()) else ""
                    )
                }
                mainDialog?.dismiss()
            }

            val editButton = itemView.findViewById<View>(R.id.edit_button)
            if (folder == "Default Theme") {
                editButton.visibility = View.INVISIBLE
            } else {
                editButton.setOnClickListener {
                    val intent = Intent(currentContext, TextEditorActivity::class.java)
                        .putExtra("folder_name", folder)
                        .putExtra("key", key)
                    ContextCompat.startActivity(currentContext, intent, null)
                }
            }
            folderListContainer.addView(itemView)
        }
        mainDialog = builder.show()
    }

    private fun getFolders(): List<String> = rootDirectory.listFiles { file -> file.isDirectory }
        ?.map { it.name }
        ?: emptyList()

    private fun showCreateNewThemeDialog() {
        val input = EditText(context)
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.new_theme_name)
            .setView(input)
            .setPositiveButton(R.string.create) { _, _ ->
                val folderName = input.text.toString()
                if (!TextUtils.isEmpty(folderName)) createNewFolder(folderName)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun createNewFolder(folderName: String) {
        val themesRoot =
            File(Environment.getExternalStorageDirectory(), "Download/WaEnhancer/themes")
        val newFolder = File(themesRoot, folderName)
        if (!newFolder.exists() && newFolder.mkdirs()) {
            mainDialog?.dismiss()
            showThemeDialog()
        }
    }

    override fun onUriPicked(uri: Uri) {
        CompletableFuture.runAsync {
            Utils.showToast("Importing theme...", Toast.LENGTH_SHORT)
            try {
                val inputStream = context.contentResolver.openInputStream(uri)!!
                val zipInputStream = ZipInputStream(inputStream)
                val zipFileName = getZipFileName(uri)
                var zipEntry = zipInputStream.nextEntry

                while (zipEntry != null) {
                    val entryName = zipEntry.name
                    val root = File(
                        Environment.getExternalStorageDirectory(),
                        "Download/WaEnhancer/themes"
                    )
                    val lastSlashIndex = entryName.lastIndexOf('/')
                    val folderName: String
                    val targetPath: String
                    if (lastSlashIndex > 0) {
                        folderName = entryName.substring(0, lastSlashIndex)
                        targetPath = entryName
                    } else {
                        folderName = zipFileName
                        targetPath = "$zipFileName/$entryName"
                    }

                    val newFolder = File(root, folderName)
                    if (!newFolder.exists()) newFolder.mkdirs()
                    if (!entryName.endsWith("/")) {
                        Files.copy(
                            zipInputStream,
                            File(root, targetPath).toPath(),
                            StandardCopyOption.REPLACE_EXISTING
                        )
                    }
                    zipEntry = zipInputStream.nextEntry
                }

                (context as Activity).runOnUiThread {
                    Utils.showToast(
                        context.getString(R.string.theme_imported_successfully),
                        Toast.LENGTH_SHORT
                    )
                    mainDialog?.dismiss()
                    showThemeDialog()
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun getZipFileName(uri: Uri): String {
        var fileName: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex =
                            cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) fileName = cursor.getString(nameIndex)
                    }
                }
            } catch (_: Exception) {
            }
        }

        if (fileName == null) fileName = uri.lastPathSegment
        if (fileName?.lowercase()?.endsWith(".zip") == true) {
            fileName = fileName.substring(0, fileName.length - 4)
        }
        return fileName?.takeIf { it.isNotEmpty() }
            ?: "imported_theme_${System.currentTimeMillis()}"
    }

    companion object {
        @JvmField
        val rootDirectory = File(App.waEnhancerFolder, "themes")
    }
}
