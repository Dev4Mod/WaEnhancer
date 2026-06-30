package com.wmods.wppenhacer.xposed.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Binder
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.wmods.wppenhacer.App
import com.wmods.wppenhacer.WppXposed.Companion.getPref
import com.wmods.wppenhacer.xposed.core.FeatureLoader
import com.wmods.wppenhacer.xposed.core.WppCore.getClientBridge
import com.wmods.wppenhacer.xposed.core.WppCore.getContactName
import com.wmods.wppenhacer.xposed.core.WppCore.getPrivString
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp.UserJid
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties
import java.util.Random
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.regex.Pattern

object Utils {
    lateinit var xprefs: XSharedPreferences
    private val ids = HashMap<String?, Int?>()
    lateinit var appClassLoader: ClassLoader

    fun init() {
        val context: Application = application
        val notificationManager = NotificationManagerCompat.from(context)
        val channel =
            NotificationChannel("wppenhacer", "WAE Enhancer", NotificationManager.IMPORTANCE_HIGH)
        notificationManager.createNotificationChannel(channel)
    }


    @JvmStatic
    val application: Application
        get() = FeatureLoader.mApp ?: App.instance!!

    fun getString(id: Int): String {
        return application.getString(id)
    }


    val executor: ExecutorService by lazy {
         Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }

    @JvmStatic
    fun doRestart(context: Context): Boolean {
        val packageManager = context.packageManager
        val intent =
            packageManager.getLaunchIntentForPackage(context.packageName) ?: return false
        val componentName = intent.component
        val mainIntent = Intent.makeRestartActivityTask(componentName)
        mainIntent.setPackage(context.packageName)
        context.startActivity(mainIntent)
        Runtime.getRuntime().exit(0)
        return true
    }

    /**
     * Retrieves the resource ID by name and type.
     * Uses caching to improve performance for repeated lookups.
     * 
     * @param name The resource name to look up
     * @param type The resource type (e.g., "id", "drawable", "layout", "string")
     * @return The resource ID or -1 if not found or an error occurred
     */
    @JvmStatic
    @SuppressLint("DiscouragedApi")
    fun getID(name: String?, type: String?): Int {
        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(type)) {
            return -1
        }

        val key = type + "_" + name

        synchronized(ids) {
            if (ids.containsKey(key)) {
                val cachedId = ids[key]
                return cachedId ?: -1
            }
        }

        try {
            val app: Application = application
            val context = app.applicationContext
            val id = context.resources.getIdentifier(name, type, app.packageName)

            synchronized(ids) {
                ids.put(key, id)
            }

            return id
        } catch (e: Exception) {
            XposedBridge.log("Error getting resource ID: type=" + type + ", name=" + name + ", error: " + e.message)
            return -1
        }
    }

    @JvmStatic
    fun dipToPixels(dipValue: Int): Int {
        val metrics = FeatureLoader.mApp!!.resources.displayMetrics
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dipValue.toFloat(), metrics)
            .toInt()
    }


    @JvmStatic
    fun dipToPixels(dipValue: Float): Int {
        val metrics = FeatureLoader.mApp!!.resources.displayMetrics
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dipValue, metrics).toInt()
    }

    @JvmStatic
    fun getDateTimeFromMillis(timestamp: Long): String {
        return SimpleDateFormat(
            "dd/MM/yyyy hh:mm:ss a",
            Locale.getDefault()
        ).format(Date(timestamp))
    }

    @SuppressLint("SdCardPath")
    fun getDestination(name: String): String {
        if (xprefs!!.getBoolean("lite_mode", false)) {
            val folder = getPrivString("download_folder", null)
                ?: throw Exception("Download Folder is not selected!")
            val documentFile = DocumentFile.fromTreeUri(application, folder.toUri())
            val wppFolder = getURIFolderByName(documentFile, "WhatsApp", true)
            getURIFolderByName(wppFolder, name, true) ?: throw Exception("Folder not found!")
            return "$folder/WhatsApp/$name"
        }
        val folder = getPref().getString("download_local", "/sdcard/Download")
        val waFolder = File(folder, "WhatsApp")
        val filePath = File(waFolder, name)
        try {
            getClientBridge()!!.createDir(filePath.absolutePath)
        } catch (_: Exception) {
        }
        return filePath.absolutePath + "/"
    }

    fun getURIFolderByName(
        documentFile: DocumentFile?,
        folderName: String,
        createDir: Boolean
    ): DocumentFile? {
        if (documentFile == null) {
            return null
        }
        val files = documentFile.listFiles()
        for (file in files) {
            if (file.name == folderName) {
                return file
            }
        }
        if (createDir) {
            return documentFile.createDirectory(folderName)!!
        }
        return null
    }

    fun copyFile(srcFile: File?, destFolder: String, name: String): String? {
        if (srcFile == null || !srcFile.exists()) return "File not found or is null"
        try {
            return copyFile(FileInputStream(srcFile), destFolder, name)
        } catch (e: Exception) {
            XposedBridge.log(e)
            return e.message
        }
    }


    fun copyFile(inputStream: InputStream, destFolder: String, name: String): String? {
        var destFolder = destFolder
        if (xprefs!!.getBoolean("lite_mode", false)) {
            try {
                val folder = getPrivString("download_folder", null)
                var documentFolder = DocumentFile.fromTreeUri(application, Uri.parse(folder))
                destFolder = destFolder.replace("$folder/", "")
                for (f in destFolder.split("/".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray()) {
                    documentFolder = getURIFolderByName(documentFolder, f, false)
                    if (documentFolder == null) return "Failed to get folder"
                }
                val newFile = documentFolder!!.createFile("*/*", name)
                    ?: return "Failed to create destination file"

                val contentResolver: ContentResolver = application.contentResolver

                inputStream.use { `in` ->
                    contentResolver.openOutputStream(newFile.uri).use { out ->
                        if (out == null) return "Failed to open output stream"
                        val buffer = ByteArray(1024)
                        var length: Int
                        while ((`in`.read(buffer).also { length = it }) > 0) {
                            out.write(buffer, 0, length)
                        }
                        return ""
                    }
                }
            } catch (e: Exception) {
                XposedBridge.log(e)
                return e.message
            }
        } else {
            val destFile = File(destFolder, name)
            try {
                inputStream.use { `in` ->
                    getClientBridge()!!.openFile(destFile.absolutePath, true)
                        .use { parcelFileDescriptor ->
                            val out = FileOutputStream(parcelFileDescriptor.fileDescriptor)
                            val bArr = ByteArray(1024)
                            while (true) {
                                val read = `in`.read(bArr)
                                if (read <= 0) {
                                    `in`.close()
                                    out.close()
                                    scanFile(destFile)
                                    return ""
                                }
                                out.write(bArr, 0, read)
                            }
                        }
                }
            } catch (e: Exception) {
                XposedBridge.log(e)
                return e.message
            }
        }
    }

    @JvmStatic
    @JvmOverloads
    fun showToast(message: String?, length: Int = 0) {
        if (message == null) return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Toast.makeText(application, message, length).show()
        } else {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    application,
                    message,
                    length
                ).show()
            }
        }
    }

    fun setToClipboard(string: String?) {
        val clipboard = application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("label", string)
        clipboard.setPrimaryClip(clip)
    }

    fun generateName(userJid: UserJid, fileFormat: String?): String {
        val contactName = getContactName(userJid)
        val number = userJid.phoneRawString
        return toValidFileName(contactName) + "_" + number + "_" + SimpleDateFormat(
            "yyyyMMdd-HHmmss",
            Locale.getDefault()
        ).format(
            Date()
        ) + "." + fileFormat
    }


    fun toValidFileName(input: String): String {
        return input.replace("[:\\\\/*\"?|<>']".toRegex(), " ")
    }

    fun scanFile(file: File) {
        MediaScannerConnection.scanFile(
            application,
            arrayOf<String>(file.absolutePath),
            arrayOf<String?>(MimeTypeUtils.getMimeTypeFromExtension(file.absolutePath))
        ) { _: String?, _: Uri? -> }
    }

    fun getProperties(prefs: SharedPreferences, key: String?, checkKey: String?): Properties {
        val properties = Properties()
        if (checkKey != null && !prefs.getBoolean(checkKey, false)) return properties
        val text = prefs.getString(key, "")!!
        val pattern = Pattern.compile("^/\\*\\s*(.*?)\\s*\\*/", Pattern.DOTALL)
        val matcher = pattern.matcher(text)

        if (matcher.find()) {
            val propertiesText = matcher.group(1)
            val lines =
                propertiesText!!.split("\\s*\\n\\s*".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray()

            for (line in lines) {
                val keyValue =
                    line.split("\\s*=\\s*".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val skey = keyValue[0].trim()
                val value =
                    keyValue[1].trim().replace("^\"|\"$".toRegex(), "") // Remove quotes, if any
                properties[skey] = value
            }
        }

        return properties
    }

    fun tryParseInt(wallpaperAlpha: String?, i: Int): Int {
        return try {
            wallpaperAlpha?.trim { it <= ' ' }?.toInt() ?: i
        } catch (_: Exception) {
            i
        }
    }

    fun getMyNumber(): String {
        return FeatureLoader.mApp!!.getSharedPreferences(
            FeatureLoader.mApp!!.packageName + "_preferences_light",
            Context.MODE_PRIVATE
        ).getString("ph", "")!!
    }


    @JvmStatic
    fun <T> binderLocalScope(block: BinderLocalScopeBlock<T?>): T? {
        val identity = Binder.clearCallingIdentity()
        try {
            return block.execute()
        } finally {
            Binder.restoreCallingIdentity(identity)
        }
    }

    @JvmStatic
    fun getAuthorFromCss(code: String?): String? {
        if (code == null) return null
        val match = Pattern.compile("author\\s*=\\s*(.*?)\n").matcher(code)
        if (!match.find()) return null
        return match.group(1)
    }

    @SuppressLint("MissingPermission")
    fun showNotification(title: String?, content: String?) {
        val context: Application = application
        val notificationManager = NotificationManagerCompat.from(context)
        val channel =
            NotificationChannel("wppenhacer", "WAE Enhancer", NotificationManager.IMPORTANCE_HIGH)
        notificationManager.createNotificationChannel(channel)
        val notification = NotificationCompat.Builder(context, "wppenhacer")
            .setSmallIcon(android.R.mipmap.sym_def_app_icon)
            .setContentTitle(title)
            .setContentText(content)
            .setAutoCancel(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
        notificationManager.notify(Random().nextInt(), notification.build())
    }

    @JvmStatic
    fun openLink(mActivity: Activity, url: String?) {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        mActivity.startActivity(browserIntent)
    }


    fun interface BinderLocalScopeBlock<T> {
        fun execute(): T?
    }
}
