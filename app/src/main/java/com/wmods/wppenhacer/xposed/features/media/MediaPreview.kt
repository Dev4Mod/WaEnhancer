package com.wmods.wppenhacer.xposed.features.media

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.PointF
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toColorInt
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp
import com.wmods.wppenhacer.xposed.core.db.MessageStore
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.features.general.Others
import com.wmods.wppenhacer.xposed.utils.DesignUtils
import com.wmods.wppenhacer.xposed.utils.HKDF
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.lang.reflect.InvocationTargetException
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class MediaPreview(
    loader: ClassLoader,
    preferences: XSharedPreferences
) : Feature(loader, preferences) {


    private var filePath: File? = null
    private var dialog: Dialog? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val MEDIA_PREVIEW_WRAPPER_TAG = "wpp_media_preview_wrapper"

        val MEDIA_KEYS = hashMapOf<String, ByteArray>()

        init {
            MEDIA_KEYS["image"] = "WhatsApp Image Keys".toByteArray()
            MEDIA_KEYS["video"] = "WhatsApp Video Keys".toByteArray()
            MEDIA_KEYS["audio"] = "WhatsApp Audio Keys".toByteArray()
            MEDIA_KEYS["document"] = "WhatsApp Document Keys".toByteArray()
            MEDIA_KEYS["image/webp"] = "WhatsApp Image Keys".toByteArray()
            MEDIA_KEYS["image/jpeg"] = "WhatsApp Image Keys".toByteArray()
            MEDIA_KEYS["image/png"] = "WhatsApp Image Keys".toByteArray()
            MEDIA_KEYS["video/mp4"] = "WhatsApp Video Keys".toByteArray()
            MEDIA_KEYS["audio/aac"] = "WhatsApp Audio Keys".toByteArray()
            MEDIA_KEYS["audio/ogg"] = "WhatsApp Audio Keys".toByteArray()
            MEDIA_KEYS["audio/wav"] = "WhatsApp Audio Keys".toByteArray()
        }
    }

    private var currentVideoView: VideoView? = null
    private var currentMediaPlayer: MediaPlayer? = null
    private var currentSpeed = 1.0f

    override fun doHook() {
        if (!prefs.getBoolean("media_preview", true)) return

        Others.propsBoolean[24205] = false

        val layoutClass = Unobfuscator.loadLayoutClass(classLoader)
        XposedHelpers.findAndHookMethod(View::class.java,"onAttachedToWindow",
            object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (!layoutClass.isInstance(param.thisObject))return
                val view = param.thisObject as View
                var resourceNames = listOf("invisible_press_surface","video_control_frame_view")
                for (rn in resourceNames){
                    val viewGroup = view.findViewById<View>(Utils.getID(rn, "id")) ?: continue
                    logDebug("Found Surface: $viewGroup")
                    handlePressSurface(view,viewGroup)
                    return
                }
                resourceNames = listOf("control_frame_new","control_frame")
                for (rn in resourceNames){
                    val viewGroup = view.findViewById<View>(Utils.getID(rn, "id")) ?: continue
                    logDebug("Found ControlFrame: $viewGroup")
                    handleMediaControlFrame(view,viewGroup)
                    return
                }
            }
        })

    }

    private fun handlePressSurface(
        mainContainer: View,
        surface: View,
    ) {
        if (surface !is ViewGroup) return
        val context = surface.context
        val controlFrame = surface.getChildAt(0) ?: return
        if (controlFrame.tag == MEDIA_PREVIEW_WRAPPER_TAG) return

        surface.removeViewAt(0)
        val linearLayout = LinearLayout(context).apply {
            tag = MEDIA_PREVIEW_WRAPPER_TAG
        }
        surface.addView(linearLayout)
        linearLayout.addView(controlFrame)

        linearLayout.addView(
            createPreviewButton(context).apply {
                setOnClickListener { startPreview(mainContainer, context) }
            }
        )
    }

    private fun handleMediaControlFrame(
        mainContainer: View,
        controlFrame: View,
    ) {
        val context = mainContainer.context
        val mediaContainer =
            mainContainer.findViewById<ViewGroup>(Utils.getID("media_container", "id")) ?: run {
                logDebug("MediaContainer not found in ImageContainer")
                return
            }

        val currentParent = controlFrame.parent as? ViewGroup
        if (currentParent?.tag == MEDIA_PREVIEW_WRAPPER_TAG) return

        val linearLayout = LinearLayout(context).apply {
            tag = MEDIA_PREVIEW_WRAPPER_TAG
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            orientation = LinearLayout.VERTICAL
            background = DesignUtils.getDrawableByName("fragment_transparent_divider")
        }

        currentParent?.removeView(controlFrame)
        linearLayout.addView(controlFrame)
        mediaContainer.addView(linearLayout)

        val prevBtn = createPreviewButton(context, Utils.dipToPixels(8f)).apply {
            visibility = controlFrame.visibility
            setOnClickListener { startPreview(mainContainer, context) }
        }
        linearLayout.addView(prevBtn)

        controlFrame.viewTreeObserver.addOnGlobalLayoutListener {
            if (prevBtn.visibility != controlFrame.visibility) {
                prevBtn.visibility = controlFrame.visibility
            }
        }
    }

    private fun createPreviewButton(context: Context, topMargin: Int = 0): ImageView {
        val drawable = context.getDrawable(R.drawable.preview_eye)
        drawable?.setTint(Color.WHITE)

        val padding = Utils.dipToPixels(4f)
        return ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                Utils.dipToPixels(42f),
                Utils.dipToPixels(32f)
            ).apply {
                gravity = Gravity.CENTER
                this.topMargin = topMargin
            }
            setImageDrawable(drawable)
            setPadding(padding, padding, padding, padding)
            background = DesignUtils.getDrawableByName("download_background")
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
    }

    private fun startPreview(messageSource: View, context: Context) {
        val fMessage = runCatching {
            val objmessage = XposedHelpers.callMethod(messageSource, "getFMessage")
            FMessageWpp(objmessage)
        }.onFailure {
            runCatching {
                messageSource.tag?.let {
                    FMessageWpp.Key(it).fMessage
                }
            }.getOrNull()
        }.getOrNull()

        if (fMessage == null){
            Utils.showToast("[MediaPreview]Error in find FMessage!")
            return
        }
        val userJid = WppCore.getCurrentUserJid()
        startPlayer(fMessage.rowId, context, userJid != null && userJid.isNewsletter)
    }

    private fun findBubbleContainer(view: View, bubbleLayoutClass: Class<*>): ViewGroup? {
        findParentRecursiveMethod(view, "getFMessage")?.let {
            logDebug("Find Class by getFMessage")
            return it as ViewGroup
        }
        return (findParentRecursive(view, bubbleLayoutClass) ?: run {
            logDebug("mainContainer Not Found")
            return null
        }) as? ViewGroup
    }

    private fun findParentRecursive(view: View, clazz: Class<*>): View? {
        var current = view.parent as? View
        while (current != null) {
                if (clazz.isInstance(current))
                    return current
            current = current.parent as? View
        }
        return null
    }

    private fun findParentRecursiveMethod(view: View, methodName: String): View? {
        var current = view.parent as? View
        while (current != null) {
            ReflectionUtils.findMethodUsingFilterIfExists(current.javaClass){
                it.name == methodName
            }?.let {
                return current
            }
            current = current.parent as? View
        }
        return null
    }


    @SuppressLint("SetTextI18n")
    private fun startPlayer(id: Long, context: Context, isNewsletter: Boolean) {
        val executor: ExecutorService = Executors.newSingleThreadExecutor()
        try {
            val query = String.format(
                Locale.ENGLISH,
                "SELECT message_url,mime_type,hex(media_key),direct_path,file_length FROM message_media WHERE message_row_id =\"%d\"",
                id
            )
            val cursor0 = MessageStore.getInstance().getDatabase()?.rawQuery(query, null)

            cursor0?.use { cursor ->
                if (cursor.count > 0) {
                    cursor.moveToFirst()
                    var url = cursor.getString(0)
                    val mimeType = cursor.getString(1)
                    val mediaKey = cursor.getString(2)
                    val directPath = cursor.getString(3)
                    val fileLength = cursor.getLong(4)

                    if (isNewsletter) {
                        url = "https://mmg.whatsapp.net$directPath"
                    }

                    dialog =
                        Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen).apply {
                            requestWindowFeature(Window.FEATURE_NO_TITLE)
                            setCancelable(true)
                            window?.let { window ->
                                window.setBackgroundDrawable("#E6000000".toColorInt().toDrawable())
                                window.setLayout(
                                    WindowManager.LayoutParams.MATCH_PARENT,
                                    WindowManager.LayoutParams.MATCH_PARENT
                                )
                                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            }
                        }

                    val mainContainer = RelativeLayout(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setBackgroundColor(Color.TRANSPARENT)
                    }

                    val header = createHeader(context, mimeType)
                    mainContainer.addView(header)

                    val contentContainer = FrameLayout(context).apply {
                        val contentParams = RelativeLayout.LayoutParams(
                            RelativeLayout.LayoutParams.MATCH_PARENT,
                            RelativeLayout.LayoutParams.MATCH_PARENT
                        )
                        contentParams.addRule(RelativeLayout.BELOW, header.id)
                        layoutParams = contentParams
                        this.id = View.generateViewId()
                    }
                    mainContainer.addView(contentContainer)

                    val loadingContainer = createLoadingView(context)
                    contentContainer.addView(loadingContainer)

                    val progressBar = loadingContainer.getChildAt(0) as ProgressBar
                    val progressText = loadingContainer.getChildAt(1) as TextView

                    dialog?.setContentView(mainContainer)
                    dialog?.setOnDismissListener { cleanupResources(executor) }
                    dialog?.show()

                    val finalUrl = url
                    executor.execute {
                        downloadAndDisplayMedia(
                            finalUrl,
                            mediaKey,
                            mimeType,
                            fileLength,
                            isNewsletter,
                            context,
                            contentContainer,
                            loadingContainer,
                            progressBar,
                            progressText,
                            executor
                        )
                    }
                }
            }
        } catch (e: Exception) {
            logDebug(e)
            Utils.showToast(e.message, Toast.LENGTH_LONG)
            cleanupDialog(executor)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun createHeader(context: Context, mimeType: String): RelativeLayout {
        val header = RelativeLayout(context).apply {
            id = View.generateViewId()
            val headerParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                dpToPx(context, 56)
            )
            headerParams.addRule(RelativeLayout.ALIGN_PARENT_TOP)
            layoutParams = headerParams
            setBackgroundColor("#CC000000".toColorInt())
            setPadding(dpToPx(context, 16), 0, dpToPx(context, 16), 0)
        }

        val closeBtn = ImageButton(context).apply {
            id = View.generateViewId()
            val closeBtnParams = RelativeLayout.LayoutParams(
                dpToPx(context, 40),
                dpToPx(context, 40)
            )
            closeBtnParams.addRule(RelativeLayout.ALIGN_PARENT_START)
            closeBtnParams.addRule(RelativeLayout.CENTER_VERTICAL)
            layoutParams = closeBtnParams
            setBackgroundColor(Color.TRANSPARENT)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setColorFilter(Color.WHITE)
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setOnClickListener {
                if (dialog?.isShowing == true) {
                    dialog?.dismiss()
                }
            }
        }
        header.addView(closeBtn)

        val title = TextView(context).apply {
            val titleParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            )
            titleParams.addRule(RelativeLayout.CENTER_IN_PARENT)
            layoutParams = titleParams
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            text = if (mimeType.startsWith("image")) {
                context.getString(R.string.preview_image)
            } else {
                context.getString(R.string.preview_video)
            }
        }
        header.addView(title)

        return header
    }

    @SuppressLint("SetTextI18n")
    private fun createLoadingView(context: Context): LinearLayout {
        val loadingContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val progressBar =
            ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                isIndeterminate = false
                max = 100
                progress = 0
                val progressParams = LinearLayout.LayoutParams(
                    dpToPx(context, 200),
                    dpToPx(context, 8)
                )
                progressParams.gravity = Gravity.CENTER
                layoutParams = progressParams
            }

        loadingContainer.addView(progressBar)

        val progressText = TextView(context).apply {
            val textParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            textParams.topMargin = dpToPx(context, 16)
            textParams.gravity = Gravity.CENTER
            layoutParams = textParams
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            text = "Downloading... 0%"
        }
        loadingContainer.addView(progressText)

        return loadingContainer
    }

    @SuppressLint("SetTextI18n")
    private fun downloadAndDisplayMedia(
        url: String, mediaKey: String, mimeType: String,
        expectedSize: Long, isNewsletter: Boolean, context: Context,
        contentContainer: FrameLayout, loadingContainer: LinearLayout,
        progressBar: ProgressBar, progressText: TextView, executor: ExecutorService
    ) {
        try {
            val fileExtension = if (mimeType.startsWith("image")) ".jpg" else ".mp4"
            filePath = File(
                Utils.application.cacheDir,
                "mediapreview_${System.currentTimeMillis()}$fileExtension"
            )

            filePath?.let { path ->
                if (path.exists()) {
                    path.delete()
                }
            }

            val client = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .addHeader("User-Agent", "Chrome/117.0.5938.150")
                            .build()
                    )
                }
                .build()

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                throw Exception("Failed to download media")
            }

            var contentLength = response.body.contentLength()
            if (contentLength <= 0) contentLength = expectedSize

            val inputStream = response.body.byteStream()

            if (isNewsletter) {
                downloadWithProgress(inputStream, contentLength, progressBar, progressText)
            } else {
                downloadAndDecryptWithProgress(
                    inputStream,
                    contentLength,
                    mediaKey,
                    mimeType,
                    progressBar,
                    progressText
                )
            }

            mainHandler.post {
                loadingContainer.visibility = View.GONE

                if (mimeType.startsWith("image")) {
                    displayImage(context, contentContainer)
                } else {
                    displayVideo(context, contentContainer)
                }
            }

        } catch (e: Throwable) {
            handleError(e, executor)
        }
    }

    @SuppressLint("SetTextI18n")
    @Throws(Exception::class)
    private fun downloadWithProgress(
        inputStream: InputStream, contentLength: Long,
        progressBar: ProgressBar, progressText: TextView
    ) {
        FileOutputStream(filePath).use { fos ->
            val buffer = ByteArray(8192)
            var totalBytesRead: Long = 0
            var bytesRead: Int

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                fos.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead

                if (contentLength > 0) {
                    val progress = ((totalBytesRead * 100) / contentLength).toInt()
                    val sizeInfo = "${formatSize(totalBytesRead)} / ${formatSize(contentLength)}"
                    mainHandler.post {
                        progressBar.progress = progress
                        progressText.text =
                            "${Utils.getString(R.string.downloading)} $progress%\n$sizeInfo"
                    }
                }
            }
        }
        inputStream.close()
    }

    @SuppressLint("SetTextI18n")
    @Throws(Exception::class)
    private fun downloadAndDecryptWithProgress(
        inputStream: InputStream, contentLength: Long,
        mediaKey: String, mimeType: String, progressBar: ProgressBar, progressText: TextView
    ) {
        val encryptedData: ByteArray
        ByteArrayOutputStream().use { baos ->
            val buffer = ByteArray(8192)
            var totalBytesRead: Long = 0
            var bytesRead: Int


            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                baos.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead

                if (contentLength > 0) {
                    val progress = ((totalBytesRead * 100) / contentLength).toInt()
                    val sizeInfo = "${formatSize(totalBytesRead)} / ${formatSize(contentLength)}"
                    mainHandler.post {
                        progressBar.progress = progress
                        progressText.text =
                            "${Utils.getString(R.string.downloading)} $progress%\n$sizeInfo"
                    }
                }
            }
            encryptedData = baos.toByteArray()
        }
        inputStream.close()

        mainHandler.post { progressText.setText(R.string.decrypting) }

        val decryptedData = decryptMedia(encryptedData, mediaKey, mimeType)

        FileOutputStream(filePath).use { fos ->
            fos.write(decryptedData)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun displayImage(context: Context, container: FrameLayout) {
        try {
            val bitmap = BitmapFactory.decodeFile(filePath?.absolutePath)
            if (bitmap == null) {
                Utils.showToast("Failed to load image", Toast.LENGTH_SHORT)
                return
            }

            val imageView = ZoomableImageView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                setImageBitmap(bitmap)
            }

            container.addView(imageView)

        } catch (e: Exception) {
            logDebug(e)
            Utils.showToast("Error displaying image: ${e.message}", Toast.LENGTH_SHORT)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun displayVideo(context: Context, container: FrameLayout) {
        try {
            val videoContainer = RelativeLayout(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }

            val videoView = VideoView(context)
            currentVideoView = videoView
            val videoParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
            ).apply {
                addRule(RelativeLayout.CENTER_IN_PARENT)
            }
            videoView.layoutParams = videoParams
            videoView.id = View.generateViewId()

            val controls = createVideoControls(context, videoView)

            videoContainer.addView(videoView)
            videoContainer.addView(controls)
            container.addView(videoContainer)

            videoView.setVideoURI(Uri.fromFile(filePath))
            videoView.setOnPreparedListener { mp ->
                currentMediaPlayer = mp
                mp.isLooping = false

                val isMuted = WppCore.getPrivBoolean("video_preview_muted", false)
                mp.setVolume(if (isMuted) 0f else 1f, if (isMuted) 0f else 1f)

                videoView.start()

                updateVideoDuration(controls, mp.duration)
            }

            videoView.setOnCompletionListener {
                videoView.seekTo(0)
                updatePlayPauseButton(controls, false)
            }

            videoView.setOnErrorListener { _, _, _ ->
                Utils.showToast("Error playing video", Toast.LENGTH_SHORT)
                true
            }

        } catch (e: Exception) {
            logDebug(e)
            Utils.showToast("Error displaying video: ${e.message}", Toast.LENGTH_SHORT)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun createVideoControls(context: Context, videoView: VideoView): LinearLayout {
        currentSpeed = 1.0f
        val controls = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val controlsParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            )
            controlsParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            layoutParams = controlsParams
            setBackgroundColor("#99000000".toColorInt())
            setPadding(
                dpToPx(context, 16),
                dpToPx(context, 8),
                dpToPx(context, 16),
                dpToPx(context, 16)
            )
        }

        val seekBar = SeekBar(context).apply {
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            max = 100
            progress = 0
        }
        controls.addView(seekBar)

        val buttonsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val buttonsParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            buttonsParams.topMargin = dpToPx(context, 8)
            layoutParams = buttonsParams
        }

        val playPauseBtn = ImageButton(context).apply {
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(dpToPx(context, 48), dpToPx(context, 48))
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.WHITE)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setImageResource(android.R.drawable.ic_media_pause)
            tag = "playing"
        }
        buttonsContainer.addView(playPauseBtn)

        val currentTime = TextView(context).apply {
            id = View.generateViewId()
            val currentTimeParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            currentTimeParams.leftMargin = dpToPx(context, 8)
            layoutParams = currentTimeParams
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            text = "00:00"
        }
        buttonsContainer.addView(currentTime)

        val separator = TextView(context).apply {
            text = " / "
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }
        buttonsContainer.addView(separator)

        val totalTime = TextView(context).apply {
            id = View.generateViewId()
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            text = "00:00"
        }
        buttonsContainer.addView(totalTime)

        val speedBtn = TextView(context).apply {
            text = "1.0x"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            setPadding(
                dpToPx(context, 8),
                dpToPx(context, 4),
                dpToPx(context, 8),
                dpToPx(context, 4)
            )
            background = DesignUtils.getDrawableByName("download_background")

            val speedParams = LinearLayout.LayoutParams(dpToPx(context, 40), dpToPx(context, 24))
            speedParams.leftMargin = dpToPx(context, 16)
            layoutParams = speedParams

            setOnClickListener {
                currentSpeed = when (currentSpeed) {
                    1.0f -> 1.5f
                    1.5f -> 2.0f
                    2.0f -> 0.5f
                    else -> 1.0f
                }

                text = "${currentSpeed}x"

                currentMediaPlayer?.let { mp ->
                    try {
                        val wasPlaying = mp.isPlaying
                        mp.playbackParams = mp.playbackParams.setSpeed(currentSpeed)
                        if (!wasPlaying) {
                            mp.pause()
                            updatePlayPauseButton(controls, false)
                        } else {
                            updatePlayPauseButton(controls, true)
                        }
                    } catch (e: Exception) {
                        logDebug(e)
                    }
                }
            }
        }
        buttonsContainer.addView(speedBtn)

        val muteBtn = ImageButton(context).apply {
            id = View.generateViewId()
            val muteParams = LinearLayout.LayoutParams(dpToPx(context, 40), dpToPx(context, 40))
            muteParams.leftMargin = dpToPx(context, 8)
            layoutParams = muteParams
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.WHITE)
            scaleType = ImageView.ScaleType.CENTER_INSIDE

            val isMuted = WppCore.getPrivBoolean("video_preview_muted", false)
            setImageResource(if (isMuted) android.R.drawable.ic_lock_silent_mode else android.R.drawable.ic_lock_silent_mode_off)
            tag = if (isMuted) "muted" else "unmuted"

            setOnClickListener {
                val currentMuted = tag == "muted"
                val newMuted = !currentMuted

                setImageResource(if (newMuted) android.R.drawable.ic_lock_silent_mode else android.R.drawable.ic_lock_silent_mode_off)
                tag = if (newMuted) "muted" else "unmuted"

                WppCore.setPrivBoolean("video_preview_muted", newMuted)

                currentMediaPlayer?.let { mp ->
                    try {
                        mp.setVolume(if (newMuted) 0f else 1f, if (newMuted) 0f else 1f)
                    } catch (e: Exception) {
                        logDebug(e)
                    }
                }
            }
        }
        buttonsContainer.addView(muteBtn)
        controls.addView(buttonsContainer)

        playPauseBtn.setOnClickListener {
            if (videoView.isPlaying) {
                videoView.pause()
                playPauseBtn.setImageResource(android.R.drawable.ic_media_play)
                playPauseBtn.tag = "paused"
            } else {
                videoView.start()
                playPauseBtn.setImageResource(android.R.drawable.ic_media_pause)
                playPauseBtn.tag = "playing"
            }
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser && videoView.duration > 0) {
                    val newPosition = (progress * videoView.duration) / 100
                    videoView.seekTo(newPosition)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        val updateHandler = Handler(Looper.getMainLooper())
        val updateRunnable = object : Runnable {
            override fun run() {
                if (videoView.isPlaying && videoView.duration > 0) {
                    val progress = (videoView.currentPosition * 100) / videoView.duration
                    seekBar.progress = progress
                    currentTime.text = formatTime(videoView.currentPosition)
                }
                if (dialog?.isShowing == true) {
                    updateHandler.postDelayed(this, 500)
                }
            }
        }
        updateHandler.post(updateRunnable)

        return controls
    }

    private fun updatePlayPauseButton(controls: LinearLayout, isPlaying: Boolean) {
        val buttonsContainer = controls.getChildAt(1) as LinearLayout
        val playPauseBtn = buttonsContainer.getChildAt(0) as ImageButton
        playPauseBtn.setImageResource(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
        playPauseBtn.tag = if (isPlaying) "playing" else "paused"
    }

    private fun updateVideoDuration(controls: LinearLayout, duration: Int) {
        val buttonsContainer = controls.getChildAt(1) as LinearLayout
        val totalTime = buttonsContainer.getChildAt(3) as TextView
        totalTime.text = formatTime(duration)
    }

    private fun formatTime(milliseconds: Int): String {
        var seconds = milliseconds / 1000
        val minutes = seconds / 60
        seconds %= 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    private fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        if (bytes < 1024 * 1024)
            return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
        return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), context.resources.displayMetrics
        ).toInt()
    }

    private fun handleError(e: Throwable, executor: ExecutorService) {
        if (e is InvocationTargetException) {
            logDebug(e.cause)
            mainHandler.post {
                Utils.showToast(
                    e.cause?.message ?: "Unknown error",
                    Toast.LENGTH_LONG
                )
            }
        } else {
            logDebug(e)
            mainHandler.post { Utils.showToast(e.message, Toast.LENGTH_LONG) }
        }
        cleanupDialog(executor)
    }

    private fun cleanupResources(executor: ExecutorService?) {
        currentMediaPlayer?.let { mp ->
            try {
                mp.release()
            } catch (ignored: Exception) {
            }
            currentMediaPlayer = null
        }
        currentVideoView = null

        filePath?.let { path ->
            if (path.exists()) {
                path.delete()
            }
        }

        if (executor != null && !executor.isShutdown) {
            executor.shutdownNow()
        }
    }

    private fun cleanupDialog(executor: ExecutorService?) {
        mainHandler.post {
            if (dialog?.isShowing == true) {
                dialog?.dismiss()
            }
        }
        cleanupResources(executor)
    }

    @SuppressLint("ClickableViewAccessibility")
    private class ZoomableImageView(context: Context) :
        ImageView(context) {
        private val matrixInstance = Matrix()
        private val savedMatrix = Matrix()

        private var mode = NONE
        private val matrixValues = FloatArray(9)
        private val lastTouch = PointF()
        private var oldDist = 1f
        private val midPoint = PointF()
        private var minScale = 0.5f
        private var maxScale = 10f
        private var baseScale = 1f
        private var isDoubleTapZoomed = false
        private var lastTapTime: Long = 0

        companion object {
            private const val DOUBLE_TAP_TIMEOUT: Long = 300
            private const val NONE = 0
            private const val DRAG = 1
            private const val ZOOM = 2
        }

        init {
            scaleType = ScaleType.MATRIX

            setOnTouchListener { _, event ->
                val action = event.action and MotionEvent.ACTION_MASK
                val pointerCount = event.pointerCount

                if (pointerCount > 1) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                }

                when (action) {
                    MotionEvent.ACTION_DOWN -> {
                        savedMatrix.set(matrixInstance)
                        lastTouch.set(event.x, event.y)
                        mode = DRAG

                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastTapTime < DOUBLE_TAP_TIMEOUT) {
                            handleDoubleTap(event.x, event.y)
                            lastTapTime = 0
                            return@setOnTouchListener true
                        } else {
                            lastTapTime = currentTime
                        }
                    }

                    MotionEvent.ACTION_POINTER_DOWN -> {
                        oldDist = spacing(event)
                        if (oldDist > 10f) {
                            savedMatrix.set(matrixInstance)
                            calculateMidPoint(midPoint, event)
                            mode = ZOOM
                        }
                    }

                    MotionEvent.ACTION_MOVE -> {
                        if (mode == DRAG && pointerCount == 1) {
                            val dx = event.x - lastTouch.x
                            val dy = event.y - lastTouch.y
                            matrixInstance.postTranslate(dx, dy)
                            lastTouch.set(event.x, event.y)
                        } else if (mode == ZOOM && pointerCount >= 2) {
                            val newDist = spacing(event)
                            if (newDist > 10f) {
                                val scale = newDist / oldDist
                                matrixInstance.postScale(scale, scale, midPoint.x, midPoint.y)
                                oldDist = newDist
                                calculateMidPoint(midPoint, event)
                            }
                        }
                        limitScale()
                        limitPan()
                        imageMatrix = matrixInstance
                    }

                    MotionEvent.ACTION_UP -> {
                        mode = NONE
                        parent?.requestDisallowInterceptTouchEvent(false)
                    }

                    MotionEvent.ACTION_POINTER_UP -> {
                        if (pointerCount <= 2) {
                            mode = DRAG
                            savedMatrix.set(matrixInstance)
                            val remainingPointerIndex = if (event.actionIndex == 0) 1 else 0
                            if (remainingPointerIndex < pointerCount) {
                                lastTouch.set(
                                    event.getX(remainingPointerIndex),
                                    event.getY(remainingPointerIndex)
                                )
                            }
                        }
                    }
                }
                true
            }
        }

        private fun spacing(event: MotionEvent): Float {
            if (event.pointerCount < 2) return 0f
            val x = event.getX(0) - event.getX(1)
            val y = event.getY(0) - event.getY(1)
            return kotlin.math.sqrt((x * x + y * y).toDouble()).toFloat()
        }

        private fun calculateMidPoint(point: PointF, event: MotionEvent) {
            if (event.pointerCount < 2) return
            val x = event.getX(0) + event.getX(1)
            val y = event.getY(0) + event.getY(1)
            point.set(x / 2f, y / 2f)
        }

        private fun handleDoubleTap(x: Float, y: Float) {
            matrixInstance.getValues(matrixValues)
            val currentScale = matrixValues[Matrix.MSCALE_X]

            if (!isDoubleTapZoomed || currentScale <= baseScale * 1.1f) {
                val targetScale = baseScale * 3f
                val scaleFactor = targetScale / currentScale
                matrixInstance.postScale(scaleFactor, scaleFactor, x, y)
                isDoubleTapZoomed = true
            } else {
                centerImage()
                isDoubleTapZoomed = false
            }
            limitScale()
            limitPan()
            imageMatrix = matrixInstance
        }

        private fun limitScale() {
            matrixInstance.getValues(matrixValues)
            val currentScale = matrixValues[Matrix.MSCALE_X]

            if (currentScale < minScale) {
                val compensate = minScale / currentScale
                matrixInstance.postScale(compensate, compensate, width / 2f, height / 2f)
            } else if (currentScale > maxScale) {
                val compensate = maxScale / currentScale
                matrixInstance.postScale(compensate, compensate, width / 2f, height / 2f)
            }
        }

        private fun limitPan() {
            if (drawable == null) return

            matrixInstance.getValues(matrixValues)
            val currentScale = matrixValues[Matrix.MSCALE_X]
            val transX = matrixValues[Matrix.MTRANS_X]
            val transY = matrixValues[Matrix.MTRANS_Y]

            val drawableWidth = drawable.intrinsicWidth * currentScale
            val drawableHeight = drawable.intrinsicHeight * currentScale
            val viewWidth = width.toFloat()
            val viewHeight = height.toFloat()

            val minX: Float
            val maxX: Float
            val minY: Float
            val maxY: Float

            if (drawableWidth <= viewWidth) {
                minX = (viewWidth - drawableWidth) / 2
                maxX = minX
            } else {
                minX = viewWidth - drawableWidth
                maxX = 0f
            }

            if (drawableHeight <= viewHeight) {
                minY = (viewHeight - drawableHeight) / 2
                maxY = minY
            } else {
                minY = viewHeight - drawableHeight
                maxY = 0f
            }

            var dx = 0f
            var dy = 0f
            if (transX < minX) dx = minX - transX
            else if (transX > maxX) dx = maxX - transX

            if (transY < minY) dy = minY - transY
            else if (transY > maxY) dy = maxY - transY

            if (dx != 0f || dy != 0f) {
                matrixInstance.postTranslate(dx, dy)
            }
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            super.onLayout(changed, left, top, right, bottom)
            if (changed) {
                centerImage()
            }
        }

        private fun centerImage() {
            if (drawable == null) return

            val viewWidth = width.toFloat()
            val viewHeight = height.toFloat()
            val drawableWidth = drawable.intrinsicWidth.toFloat()
            val drawableHeight = drawable.intrinsicHeight.toFloat()

            val scale = (viewWidth / drawableWidth).coerceAtMost(viewHeight / drawableHeight)
            baseScale = scale
            minScale = scale * 0.5f

            val dx = (viewWidth - drawableWidth * scale) / 2
            val dy = (viewHeight - drawableHeight * scale) / 2

            matrixInstance.setScale(scale, scale)
            matrixInstance.postTranslate(dx, dy)
            imageMatrix = matrixInstance
            isDoubleTapZoomed = false
        }
    }

    @Throws(Exception::class)
    private fun decryptMedia(
        encryptedData: ByteArray,
        mediaKey: String,
        mimeType: String
    ): ByteArray {
        if (mediaKey.length % 2 != 0 || mediaKey.length != 64) {
            throw IllegalArgumentException("Invalid media key.")
        }

        val keyBytes = ByteArray(32)
        for (i in 0 until 64 step 2) {
            keyBytes[i / 2] = ((Character.digit(mediaKey[i], 16) shl 4) + Character.digit(
                mediaKey[i + 1],
                16
            )).toByte()
        }

        val typeKey = MEDIA_KEYS[mimeType] ?: MEDIA_KEYS["document"]!!
        val derivedKey = HKDF.createFor(3).deriveSecrets(keyBytes, typeKey, 112)
        val iv = derivedKey.copyOfRange(0, 16)
        val aesKey = derivedKey.copyOfRange(16, 48)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(encryptedData.copyOfRange(0, encryptedData.size - 10))
    }

    override fun getPluginName(): String {
        return "Media Preview"
    }
}