package com.wmods.wppenhacer.activities

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.activities.base.BaseActivity
import com.wmods.wppenhacer.preference.ThemePreference
import com.wmods.wppenhacer.xposed.utils.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.core.util.IOUtils
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.util.concurrent.CompletableFuture
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TextEditorActivity : BaseActivity() {

    private var folderName: String? = null
    private lateinit var mGetContent: ActivityResultLauncher<String>
    private lateinit var mExportFile: ActivityResultLauncher<String>
    private var webView: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_text_editor)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val wv = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.allowContentAccess = true
            settings.domStorageEnabled = true
            @Suppress("DEPRECATION")
            settings.allowUniversalAccessFromFileURLs = true
            settings.javaScriptCanOpenWindowsAutomatically = true
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
        }
        webView = wv
        updateWebViewContent("")

        val container = findViewById<FrameLayout>(R.id.webViewContainer)
        container.addView(
            wv,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        mGetContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            onUriSelected(uri)
        }
        mExportFile = registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
            if (uri != null) {
                exportAsZip(uri)
            }
        }

        folderName = intent.getStringExtra("folder_name")
        if (!TextUtils.isEmpty(folderName)) {
            readFile(folderName!!)
        }
    }

    private fun updateWebViewContent(newContent: String) {
        val wv = webView ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                assets.open("css_editor.html").use { inputStream ->
                    var code = IOUtils.toString(inputStream)
                    code = code.replace("{{content}}", newContent)
                    val finalCode = code
                    withContext(Dispatchers.Main) {
                        wv.loadDataWithBaseURL(
                            "file:///android_asset/",
                            finalCode,
                            "text/html",
                            "UTF-8",
                            null
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun getTextareaContentAsync(): CompletableFuture<String?> {
        val future = CompletableFuture<String?>()
        val wv = webView
        if (wv != null) {
            wv.evaluateJavascript("getTextareaContent();") { content ->
                var cleaned = content
                if (cleaned != null) {
                    if (cleaned.startsWith("\"") && cleaned.endsWith("\"") && cleaned.length >= 2) {
                        cleaned = cleaned.substring(1, cleaned.length - 1)
                    }
                    cleaned = cleaned
                        .replace("\\n", "\n")
                        .replace("\\r", "\r")
                        .replace("\\\"", "\"")
                        .replace("\\'", "'")
                        .replace("\\\\", "\\")
                }
                future.complete(cleaned)
            }
        } else {
            future.completeExceptionally(Exception("WebView is null"))
        }
        return future
    }

    private fun readFile(folderName: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val folderFolder = File(ThemePreference.rootDirectory, folderName)
                val cssCode = File(folderFolder, "style.css")
                if (cssCode.exists()) {
                    val code = cssCode.readText(Charset.defaultCharset())
                    withContext(Dispatchers.Main) {
                        updateWebViewContent(code)
                    }
                } else {
                    cssCode.createNewFile()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.css_editor_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menuitem_save -> {
                try {
                    getTextareaContentAsync().thenAccept { content ->
                        val code = content ?: ""
                        lifecycleScope.launch(Dispatchers.IO) {
                            val targetFolder = folderName ?: return@launch
                            val folderFolder = File(ThemePreference.rootDirectory, targetFolder)
                            val cssCode = File(folderFolder, "style.css")
                            cssCode.writeText(code, Charset.defaultCharset())

                            val prefs = PreferenceManager.getDefaultSharedPreferences(this@TextEditorActivity)
                            val key = intent.getStringExtra("key")
                            if (key != null && prefs.getString(key, "") == targetFolder) {
                                prefs.edit().putString("custom_css", code).apply()
                            }

                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@TextEditorActivity, R.string.saved, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            R.id.menuitem_exit -> finish()
            R.id.menuitem_clear -> updateWebViewContent("")
            R.id.menuitem_import_image -> mGetContent.launch("image/*")
            R.id.menuitem_export -> mExportFile.launch("$folderName.zip")
        }
        return super.onOptionsItemSelected(item)
    }

    private fun exportAsZip(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    ZipOutputStream(outputStream).use { zipOutputStream ->
                        val dir = ThemePreference.rootDirectory.absolutePath + "/"
                        val currentFolder = folderName ?: return@use
                        val folderFolder = File(ThemePreference.rootDirectory, currentFolder)
                        val files = getAllFilesPath(folderFolder)
                        for (file in files) {
                            val name = file.absolutePath.replace(dir, "")
                            zipOutputStream.putNextEntry(ZipEntry(name))
                            val bytes = file.readBytes()
                            zipOutputStream.write(bytes)
                            zipOutputStream.closeEntry()
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@TextEditorActivity, R.string.exported, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Utils.showToast("Error: " + e.message, Toast.LENGTH_SHORT)
                }
            }
        }
    }

    private fun getAllFilesPath(folderFolder: File): List<File> {
        val files = folderFolder.listFiles() ?: return emptyList()
        val list = ArrayList<File>()
        for (file in files) {
            if (file.isDirectory) {
                list.addAll(getAllFilesPath(file))
            } else {
                list.add(file)
            }
        }
        return list
    }

    private fun onUriSelected(uri: Uri?) {
        if (uri == null) return
        val linearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
        val input = EditText(this).apply {
            hint = "example.png"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        linearLayout.addView(input)
        AlertDialog.Builder(this)
            .setTitle(R.string.enter_image_file_name)
            .setPositiveButton("OK") { _, _ ->
                val fileName = input.text.toString()
                if (fileName.endsWith(".png")) {
                    copyFromUri(fileName, uri)
                } else {
                    Toast.makeText(this, R.string.error_image_name, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .setView(linearLayout)
            .show()
    }

    private fun copyFromUri(fileName: String, uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            val currentFolder = folderName ?: return@launch
            val outFolder = File(ThemePreference.rootDirectory, currentFolder)
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    val outFile = File(outFolder, fileName)
                    FileOutputStream(outFile).use { out ->
                        bitmap?.compress(Bitmap.CompressFormat.PNG, 90, out)
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@TextEditorActivity,
                        getString(R.string.imported_as) + fileName,
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@TextEditorActivity, "Error: " + e.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
