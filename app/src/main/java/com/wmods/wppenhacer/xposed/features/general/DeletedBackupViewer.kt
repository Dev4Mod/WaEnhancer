package com.wmods.wppenhacer.xposed.features.general

import android.app.Activity
import android.app.Dialog
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.text.format.DateFormat
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.extension.kavaref.hook
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.components.AlertDialogWpp
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp
import com.wmods.wppenhacer.xposed.core.components.WaContactWpp
import com.wmods.wppenhacer.xposed.core.db.DelMessageStore
import com.wmods.wppenhacer.xposed.core.db.MessageStore
import com.wmods.wppenhacer.xposed.features.others.MenuHome
import com.wmods.wppenhacer.xposed.utils.DesignUtils
import com.wmods.wppenhacer.xposed.utils.HKDF
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import com.wmods.wppenhacer.xposed.utils.YukiLog
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class DeletedBackupViewer(
    classLoader: ClassLoader,
    preferences: SharedPreferences
) : Feature(classLoader, preferences) {

    companion object {
        private const val EXTRA_VIEWER = "wae_deleted_backup_viewer"
        private const val FIELD_CONTROLLER = "wae_deleted_backup_controller"
        private val MENU_ITEM_ADDED = AtomicBoolean(false)
        val OPEN_VIEWER_ON_NEXT_ABOUT = AtomicBoolean(false)

        private const val MENU_RESTORE_VISIBLE = 42001
        private const val REQUEST_CODE_AUTH = 42002

        private val MEDIA_KEYS = hashMapOf(
            "image" to "WhatsApp Image Keys".toByteArray(),
            "video" to "WhatsApp Video Keys".toByteArray(),
            "audio" to "WhatsApp Audio Keys".toByteArray(),
            "document" to "WhatsApp Document Keys".toByteArray()
        )

        private fun firstNonEmpty(first: String?, second: String?): String? {
            return if (!TextUtils.isEmpty(first)) first
            else if (!TextUtils.isEmpty(second)) second
            else null
        }

        private fun sanitizeText(value: String?): String? {
            if (value == null) return null
            val trimmed = value.trim()
            if (trimmed.isEmpty() || "null".equals(trimmed, ignoreCase = true)) return null
            return trimmed
        }

        private fun resolveMessageText(entry: DelMessageStore.DeletedBackupEntry): String {
            if (!TextUtils.isEmpty(entry.messageJson)) {
                try {
                    val message = JSONObject(entry.messageJson!!)
                    val text = sanitizeText(message.optString("text_data", null))
                    if (!TextUtils.isEmpty(text)) return text!!
                } catch (_: Exception) {
                }
            }
            if (!TextUtils.isEmpty(entry.messageMediaJson)) {
                try {
                    val media = JSONObject(entry.messageMediaJson!!)
                    val caption = sanitizeText(media.optString("media_caption", null))
                    if (!TextUtils.isEmpty(caption)) return caption!!
                    val mime = sanitizeText(media.optString("mime_type", null))
                    if (!TextUtils.isEmpty(mime)) return mime!!
                } catch (_: Exception) {
                }
            }
            if (!TextUtils.isEmpty(entry.messageTextJson)) {
                try {
                    val ext = JSONObject(entry.messageTextJson!!)
                    val description = sanitizeText(ext.optString("description", null))
                    if (!TextUtils.isEmpty(description)) return description!!
                    val title = sanitizeText(ext.optString("title", null))
                    if (!TextUtils.isEmpty(title)) return title!!
                } catch (_: Exception) {
                }
            }
            return ""
        }
    }

    override fun doHook() {
        if (!xprefs.getBoolean("deleted_message_capture", false)) return

        if (MENU_ITEM_ADDED.compareAndSet(false, true)) {
            MenuHome.addMenuItem { menu, activity -> insertViewerMenuItem(menu, activity) }
        }

        val aboutClass = WppCore.aboutActivityClass

        Activity::class.java.resolve().firstMethod {
            name = "onCreate"
            parameters(Bundle::class.java)
        }.hook {
            after {
                val activity = instance as Activity
                if (!isTargetAboutActivity(activity, aboutClass) || !isViewerLaunch(activity.intent)) return@after
                val controller = getOrCreateController(activity)
                controller.attach()
                OPEN_VIEWER_ON_NEXT_ABOUT.set(false)
            }
        }

        Activity::class.java.resolve().firstMethod {
            name = "onResume"
            parameters()
        }.hook {
            after {
                val activity = instance as Activity
                if (!isTargetAboutActivity(activity, aboutClass) || !isViewerLaunch(activity.intent)) return@after
                val controller = getOrCreateController(activity)
                activity.window.decorView.post {
                    controller.attach()
                    controller.reloadThreads()
                }
                OPEN_VIEWER_ON_NEXT_ABOUT.set(false)
            }
        }

        Activity::class.java.resolve().firstMethod {
            name = "onNewIntent"
            parameters(Intent::class.java)
        }.hook {
            after {
                val activity = instance as Activity
                if (!isTargetAboutActivity(activity, aboutClass)) return@after
                val intent = args[0] as Intent
                if (!isViewerLaunch(intent)) return@after
                activity.intent = intent
                val controller = getOrCreateController(activity)
                controller.attach()
                controller.reloadThreads()
                OPEN_VIEWER_ON_NEXT_ABOUT.set(false)
            }
        }

        Activity::class.java.resolve().firstMethod {
            name = "onDestroy"
            parameters()
        }.hook {
            before {
                val activity = instance as Activity
                if (!isTargetAboutActivity(activity, aboutClass)) return@before
                getController(activity)?.destroy()
                ReflectionUtils.removeAdditionalInstanceField(activity, FIELD_CONTROLLER)
            }
        }

        Activity::class.java.resolve().firstMethod {
            name = "onBackPressed"
            parameters()
        }.hook {
            before {
                val activity = instance as Activity
                if (!isTargetAboutActivity(activity, aboutClass)) return@before
                val controller = getController(activity)
                if (controller != null && controller.handleBackPressed()) {
                    result = null
                }
            }
        }

        Activity::class.java.resolve().firstMethod {
            name = "onCreateOptionsMenu"
            parameters(Menu::class.java)
        }.hook {
            before {
                val activity = instance as Activity
                if (!isTargetAboutActivity(activity, aboutClass)) return@before
                val controller = getController(activity) ?: return@before
                controller.onCreateOptionsMenu(args[0] as Menu)
                result = true
            }
        }

        Activity::class.java.resolve().firstMethod {
            name = "onOptionsItemSelected"
            parameters(MenuItem::class.java)
        }.hook {
            before {
                val activity = instance as Activity
                if (!isTargetAboutActivity(activity, aboutClass)) return@before
                val controller = getController(activity) ?: return@before
                if (controller.onOptionsItemSelected(args[0] as MenuItem)) {
                    result = true
                }
            }
        }

        Activity::class.java.resolve().firstMethod {
            name = "onActivityResult"
            parameters(Int::class.java, Int::class.java, Intent::class.java)
        }.hook {
            after {
                val activity = instance as Activity
                val requestCode = args[0] as Int
                val resultCode = args[1] as Int
                if (requestCode == REQUEST_CODE_AUTH && resultCode == Activity.RESULT_OK) {
                    OPEN_VIEWER_ON_NEXT_ABOUT.set(true)
                    openViewer(activity)
                }
            }
        }
    }

    private fun insertViewerMenuItem(menu: Menu, activity: Activity) {
        val newSettings = xprefs.getBoolean("buttonaction", true)
        val icon = activity.getDrawable(R.drawable.ic_delete)
        icon?.setTint(if (newSettings) DesignUtils.getPrimaryTextColor() else -0x796960)
        val item = menu.add(0, 0, 0, R.string.deleted_message_notification_channel)
        if (icon != null) item.icon = icon
        if (newSettings) {
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        item.setOnMenuItemClickListener {
            authenticateAndOpenViewer(activity)
            true
        }
    }

    private fun authenticateAndOpenViewer(activity: Activity) {
        try {
            val km = activity.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            if (km == null || !km.isDeviceSecure()) {
                OPEN_VIEWER_ON_NEXT_ABOUT.set(true)
                openViewer(activity)
                return
            }
            val intent = km.createConfirmDeviceCredentialIntent(
                activity.getString(R.string.deleted_message_notification_channel),
                activity.getString(R.string.auth_backup_viewer_description)
            )
            if (intent != null) {
                activity.startActivityForResult(intent, REQUEST_CODE_AUTH)
            } else {
                OPEN_VIEWER_ON_NEXT_ABOUT.set(true)
                openViewer(activity)
            }
        } catch (e: Exception) {
            YukiLog.log("DeletedBackupViewer: auth failed, falling back to direct access")
            OPEN_VIEWER_ON_NEXT_ABOUT.set(true)
            openViewer(activity)
        }
    }

    private fun openViewer(activity: Activity) {
        val aboutClass = WppCore.aboutActivityClass
        val intent = Intent()
        intent.setClassName(activity.packageName, aboutClass.name)
        intent.putExtra(EXTRA_VIEWER, true)
        activity.startActivity(intent)
    }

    private fun getController(activity: Activity): ViewerController? {
        return ReflectionUtils.getAdditionalInstanceField(
            activity,
            FIELD_CONTROLLER
        ) as? ViewerController
    }

    private fun getOrCreateController(activity: Activity): ViewerController {
        return getController(activity) ?: run {
            val controller = ViewerController(activity)
            ReflectionUtils.setAdditionalInstanceField(activity, FIELD_CONTROLLER, controller)
            controller
        }
    }

    private fun isViewerLaunch(intent: Intent?): Boolean {
        return (intent != null && intent.getBooleanExtra(
            EXTRA_VIEWER,
            false
        )) || OPEN_VIEWER_ON_NEXT_ABOUT.get()
    }

    private fun isTargetAboutActivity(activity: Activity, aboutClass: Class<*>): Boolean {
        return aboutClass.isAssignableFrom(activity.javaClass)
    }

    override fun getPluginName(): String = "Deleted Backup Viewer"


    private inner class ViewerController(
        private val activity: Activity
    ) : MediaResolver {

        private val mainHandler = Handler(Looper.getMainLooper())
        private val contactNameCache = HashMap<String, String>()
        private val avatarCache = HashMap<String, Drawable?>()
        private val mediaThumbCache = HashMap<Long, Bitmap>()
        private val mediaUriCache = HashMap<Long, Uri>()
        private val mediaMimeCache = HashMap<Long, String>()
        private val loadingThumbIds = HashSet<Long>()
        private val loadingMediaIds = HashSet<Long>()
        private val failedMediaIds = HashSet<Long>()
        private val mediaCacheLock = Any()
        private var mediaCacheDir: File? = null

        private var attached = false
        private var root: FrameLayout? = null
        private var chatListScreen: View? = null
        private var threadScreen: View? = null
        private var chatListAdapter: ChatListAdapter? = null
        private var messageAdapter: MessageAdapter? = null
        private var messageList: ListView? = null
        private var threadTitle: TextView? = null
        private var threadSubtitle: TextView? = null
        private var threadAvatar: AvatarView? = null
        private var renderedThreadKey: String? = null
        private val audioStates = HashMap<Long, AudioState>()
        private val backupListener = DelMessageStore.BackupListener {
            mainHandler.post { reloadThreads() }
        }

        val allThreads = ArrayList<ChatThread>()
        val visibleThreads = ArrayList<ChatThread>()
        var currentThread: ChatThread? = null
            private set
        var searchQuery = ""
            private set
        var showChatList = true
            private set

        private fun logError(msg: String) {
            Log.e("DeletedBackupViewer", msg)
        }

        private fun logError(e: Exception) {
            Log.e("DeletedBackupViewer", "Error", e)
        }

        fun attach() {
            if (activity.isFinishing || activity.isDestroyed) return
            if (root == null) {
                val container = FrameLayout(activity)
                container.setBackgroundColor(DesignUtils.getPrimarySurfaceColor())
                val list = buildChatListScreen()
                val thread = buildThreadScreen()
                container.addView(list, matchParent())
                container.addView(thread, matchParent())
                chatListScreen = list
                threadScreen = thread
                root = container
            }
            attached = true
            DelMessageStore.addBackupListener(backupListener)
            if (root?.parent == null) {
                activity.setContentView(root)
            }
            render()
        }

        private fun matchParent() = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        private fun dp(value: Int) = Utils.dipToPixels(value)

        private fun textColor() = DesignUtils.getPrimaryTextColor()

        private fun mutedColor() = 0xFF8A8A8A.toInt()

        private fun actionText(text: String, color: Int, sizeSp: Float, onClick: () -> Unit): TextView {
            return TextView(activity).apply {
                this.text = text
                setTextColor(color)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
                setTypeface(null, Typeface.BOLD)
                setPadding(dp(8), dp(8), dp(8), dp(8))
                setOnClickListener { onClick() }
            }
        }

        /** Re-applies the state to the two screens; cheap enough to call after every change. */
        private fun render() {
            val list = chatListScreen ?: return
            val thread = threadScreen ?: return
            val current = currentThread
            if (showChatList || current == null) {
                list.visibility = View.VISIBLE
                thread.visibility = View.GONE
                renderedThreadKey = null
                chatListAdapter?.rebuild()
                return
            }
            list.visibility = View.GONE
            thread.visibility = View.VISIBLE
            threadTitle?.text = resolveContactName(current.chatKey)
            threadSubtitle?.text = current.chatKey
            threadAvatar?.bind(
                resolveAvatar(current.chatPhoneRawString ?: current.chatRawString, current.group),
                current.group
            )
            messageAdapter?.setItems(current.messages)
            if (renderedThreadKey != current.chatKey) {
                renderedThreadKey = current.chatKey
                messageList?.setSelection(maxOf(0, current.messages.size - 1))
            }
        }

        // --- Chat list screen ---

        private fun buildChatListScreen(): View {
            val column = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }

            val toolbar = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), 0, dp(8), 0)
            }
            toolbar.addView(TextView(activity).apply {
                text = "<"
                setTextColor(textColor())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                setTypeface(null, Typeface.BOLD)
                setPadding(dp(12), dp(8), dp(12), dp(8))
                setOnClickListener { activity.finish() }
            })
            toolbar.addView(TextView(activity).apply {
                text = activity.getString(R.string.deleted_message_capture_title)
                setTextColor(textColor())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setTypeface(null, Typeface.BOLD)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            column.addView(toolbar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)))

            val search = EditText(activity).apply {
                hint = "Search contacts..."
                setHintTextColor(0xFF9A9A9A.toInt())
                setTextColor(textColor())
                setSingleLine(true)
                setPadding(dp(14), dp(10), dp(14), dp(10))
                background = GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    setColor(if (DesignUtils.isNightMode()) 0xFF182229.toInt() else android.graphics.Color.WHITE)
                }
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        onSearchQueryChanged(s?.toString() ?: "")
                    }
                })
            }
            column.addView(search, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12), dp(8), dp(12), dp(8)) })

            val adapter = ChatListAdapter()
            chatListAdapter = adapter
            val list = ListView(activity).apply {
                divider = null
                this.adapter = adapter
                setOnItemClickListener { _, _, position, _ ->
                    (adapter.getItem(position) as? ChatThread)?.let { onChatClicked(it) }
                }
                setOnItemLongClickListener { _, _, position, _ ->
                    (adapter.getItem(position) as? ChatThread)?.let { onChatLongClicked(it) }
                    true
                }
            }
            val empty = TextView(activity).apply {
                text = "No conversations found"
                setTextColor(textColor())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                gravity = Gravity.CENTER
            }
            adapter.emptyView = empty
            val frame = FrameLayout(activity)
            frame.addView(withPullToRefresh(list), matchParent())
            frame.addView(empty, matchParent())
            empty.isClickable = false
            column.addView(frame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            return column
        }

        private inner class ChatListAdapter : BaseAdapter() {
            private var rows: List<Any> = emptyList()
            var emptyView: View? = null

            fun rebuild() {
                val contacts = visibleThreads.filter { !it.group }
                val groups = visibleThreads.filter { it.group }
                val built = ArrayList<Any>()
                if (contacts.isNotEmpty()) {
                    built.add("Private Messages")
                    built.addAll(contacts)
                }
                if (groups.isNotEmpty()) {
                    built.add("Group Messages")
                    built.addAll(groups)
                }
                rows = built
                emptyView?.visibility = if (built.isEmpty()) View.VISIBLE else View.GONE
                notifyDataSetChanged()
            }

            override fun getCount() = rows.size
            override fun getItem(position: Int): Any = rows[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getViewTypeCount() = 2
            override fun getItemViewType(position: Int) = if (rows[position] is String) 0 else 1
            override fun isEnabled(position: Int) = rows[position] !is String

            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val row = rows[position]
                if (row is String) {
                    val header = (convertView as? TextView) ?: TextView(activity).apply {
                        setTextColor(DesignUtils.getUnSeenColor())
                        setTypeface(null, Typeface.BOLD)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                        setPadding(dp(14), dp(10), dp(14), dp(6))
                    }
                    header.text = row
                    return header
                }
                val thread = row as ChatThread
                val holder = (convertView?.tag as? ThreadHolder) ?: ThreadHolder().also {
                    it.build()
                }
                holder.bind(thread)
                return holder.view
            }
        }

        private inner class ThreadHolder {
            lateinit var view: LinearLayout
            lateinit var avatar: AvatarView
            lateinit var name: TextView
            lateinit var preview: TextView
            lateinit var time: TextView
            lateinit var count: TextView

            fun build() {
                view = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                    tag = this@ThreadHolder
                }
                avatar = AvatarView(activity, 48)
                view.addView(avatar, LinearLayout.LayoutParams(dp(48), dp(48)))
                val middle = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
                name = TextView(activity).apply {
                    setTextColor(textColor())
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    setTypeface(null, Typeface.BOLD)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }
                preview = TextView(activity).apply {
                    setTextColor(textColor())
                    alpha = 0.86f
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }
                middle.addView(name)
                middle.addView(preview)
                view.addView(middle, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(10)
                })
                val right = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.END
                }
                time = TextView(activity).apply {
                    setTextColor(mutedColor())
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                }
                count = TextView(activity).apply {
                    setTextColor(android.graphics.Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    setTypeface(null, Typeface.BOLD)
                    setPadding(dp(6), dp(2), dp(6), dp(2))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(9).toFloat()
                        setColor(DesignUtils.getUnSeenColor())
                    }
                }
                right.addView(time)
                right.addView(count, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(2) })
                view.addView(right)
            }

            fun bind(thread: ChatThread) {
                avatar.bind(
                    resolveAvatar(thread.chatPhoneRawString ?: thread.chatRawString, thread.group),
                    thread.group
                )
                name.text = resolveContactName(thread.chatKey)
                preview.text = thread.lastPreview
                time.text = DateFormat.format("dd/MM HH:mm", thread.lastTimestamp).toString()
                count.text = thread.messages.size.toString()
            }
        }

        private inner class AvatarView(context: Context, private val sizeDp: Int) : FrameLayout(context) {
            private val image = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
            private val letter = TextView(context).apply {
                setTextColor(textColor())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                gravity = Gravity.CENTER
            }

            init {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (DesignUtils.isNightMode()) 0xFF233138.toInt() else 0xFFD9D9D9.toInt())
                }
                outlineProvider = ViewOutlineProvider.BACKGROUND
                clipToOutline = true
                addView(image, LayoutParams(dp(sizeDp), dp(sizeDp)))
                addView(letter, LayoutParams(dp(sizeDp), dp(sizeDp)))
            }

            fun bind(drawable: Drawable?, group: Boolean) {
                if (drawable is BitmapDrawable) {
                    image.setImageDrawable(drawable)
                    image.visibility = View.VISIBLE
                    letter.visibility = View.GONE
                } else {
                    image.setImageDrawable(null)
                    image.visibility = View.GONE
                    letter.visibility = View.VISIBLE
                    letter.text = if (group) "G" else "P"
                }
            }
        }

        // --- Thread screen ---

        private fun buildThreadScreen(): View {
            val column = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(
                    if (DesignUtils.isNightMode()) 0xFF0B141A.toInt() else 0xFFECE5DD.toInt()
                )
            }
            val toolbar = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(DesignUtils.getPrimarySurfaceColor())
                setPadding(dp(8), 0, dp(8), 0)
            }
            toolbar.addView(TextView(activity).apply {
                text = "<"
                setTextColor(textColor())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                setTypeface(null, Typeface.BOLD)
                setPadding(dp(10), dp(8), dp(10), dp(8))
                setOnClickListener { showChatListScreen() }
            })
            val avatar = AvatarView(activity, 42)
            threadAvatar = avatar
            toolbar.addView(avatar, LinearLayout.LayoutParams(dp(42), dp(42)))
            val names = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            val title = TextView(activity).apply {
                setTextColor(textColor())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                setTypeface(null, Typeface.BOLD)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            val subtitle = TextView(activity).apply {
                setTextColor(mutedColor())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            threadTitle = title
            threadSubtitle = subtitle
            names.addView(title)
            names.addView(subtitle)
            toolbar.addView(names, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(10)
            })
            toolbar.addView(actionText(
                activity.getString(R.string.restore_message),
                DesignUtils.getUnSeenColor(), 12f
            ) { restoreCurrentThread() })
            toolbar.addView(actionText("Delete", 0xFFD64545.toInt(), 12f) { confirmRemoveCurrentThread() })
            column.addView(toolbar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)))

            val adapter = MessageAdapter()
            messageAdapter = adapter
            val list = ListView(activity).apply {
                divider = null
                dividerHeight = 0
                clipToPadding = false
                setPadding(dp(8), dp(8), dp(8), dp(8))
                this.adapter = adapter
            }
            messageList = list
            column.addView(
                withPullToRefresh(list),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            )
            return column
        }

        private inner class MessageAdapter : BaseAdapter() {
            private var items: List<DelMessageStore.DeletedBackupEntry> = emptyList()

            fun setItems(newItems: List<DelMessageStore.DeletedBackupEntry>) {
                items = ArrayList(newItems)
                notifyDataSetChanged()
            }

            override fun getCount() = items.size
            override fun getItem(position: Int) = items[position]
            override fun getItemId(position: Int) = items[position].id
            override fun hasStableIds() = true

            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                // Bubbles differ a lot between messages (media, audio, sender), so rebuild them.
                return buildBubble(items[position])
            }
        }

        private fun buildBubble(entry: DelMessageStore.DeletedBackupEntry): View {
            val isMine = entry.fromMe == 1
            val night = DesignUtils.isNightMode()
            val bubbleColor = if (isMine) {
                if (night) 0xFF005C4B.toInt() else 0xFFDCF8C6.toInt()
            } else {
                if (night) 0xFF202C33.toInt() else android.graphics.Color.WHITE
            }
            val bubbleText = if (isMine) {
                if (night) android.graphics.Color.WHITE else 0xFF111B21.toInt()
            } else textColor()

            val chatRaw = sanitizeText(firstNonEmpty(entry.chatPhoneRawString, entry.chatRawString))
            val isGroup = chatRaw != null && chatRaw.endsWith("@g.us")

            val bubble = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(9), dp(9), dp(9), dp(9))
                background = GradientDrawable().apply {
                    cornerRadius = dp(11).toFloat()
                    setColor(bubbleColor)
                }
            }

            if (isGroup && !isMine) {
                val senderRaw = firstNonEmpty(entry.senderPhoneRawString, entry.senderRawString)
                if (!TextUtils.isEmpty(senderRaw)) {
                    bubble.addView(TextView(activity).apply {
                        text = resolveContactName(senderRaw)
                        setTextColor(0xFF7B8794.toInt())
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                        setTypeface(null, Typeface.BOLD)
                        setPadding(0, 0, 0, dp(4))
                    })
                }
            }

            val mimeType = resolveMimeType(entry)
            if (!TextUtils.isEmpty(entry.messageMediaJson) && !TextUtils.isEmpty(mimeType)) {
                bubble.addView(
                    if (mimeType!!.startsWith("audio/")) buildAudioRow(entry) else buildMediaCard(entry, mimeType),
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dp(6) }
                )
            }

            val messageText = resolveMessageText(entry)
            if (messageText.isNotEmpty()) {
                bubble.addView(TextView(activity).apply {
                    text = messageText
                    setTextColor(bubbleText)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                })
            }

            val sentAt = if (entry.messageTimestamp > 0) entry.messageTimestamp else entry.deletedAt
            bubble.addView(TextView(activity).apply {
                text = DateFormat.format("HH:mm", sentAt).toString()
                setTextColor(if (isMine) 0x88000000.toInt() else mutedColor())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                gravity = Gravity.END
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(2) })

            if (!TextUtils.isEmpty(entry.messageMediaJson) || messageText.isNotEmpty()) {
                bubble.addView(TextView(activity).apply {
                    text = "Deleted " + DateFormat.format("dd/MM HH:mm", entry.deletedAt)
                    setTextColor(mutedColor())
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
                })
            }

            val actions = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
            }
            actions.addView(actionText(
                activity.getString(R.string.restore_message),
                DesignUtils.getUnSeenColor(), 11f
            ) { confirmRestoreEntry(entry.id) })
            actions.addView(actionText("Delete", 0xFFD64545.toInt(), 11f) { confirmRemove(entry.id) })
            bubble.addView(actions, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) })

            val row = FrameLayout(activity)
            row.setPadding(0, dp(3), 0, dp(3))
            row.addView(bubble, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                if (isMine) Gravity.END else Gravity.START
            ))
            bubble.minimumWidth = dp(120)
            bubble.layoutParams = (bubble.layoutParams as FrameLayout.LayoutParams)
            val maxWidth = dp(280)
            row.post {
                if (bubble.width > maxWidth) {
                    bubble.layoutParams = (bubble.layoutParams as FrameLayout.LayoutParams).also {
                        it.width = maxWidth
                    }
                }
            }
            return row
        }

        private fun mediaCardBackground() =
            if (DesignUtils.isNightMode()) 0xFF1A252A.toInt() else 0xFFE7EFEF.toInt()

        private fun buildMediaCard(entry: DelMessageStore.DeletedBackupEntry, mimeType: String): View {
            val isVisual = mimeType.startsWith("image/") || mimeType.startsWith("video/")
            val label = when {
                mimeType.startsWith("image/") -> "Photo"
                mimeType.startsWith("video/") -> "Video"
                else -> "Document"
            }
            val thumbnail = getThumbnail(entry.id)
            if (isVisual && thumbnail == null) {
                requestThumbnail(entry, Runnable { messageAdapter?.notifyDataSetChanged() })
            }
            val card = FrameLayout(activity).apply {
                background = GradientDrawable().apply {
                    cornerRadius = dp(10).toFloat()
                    setColor(mediaCardBackground())
                }
                outlineProvider = ViewOutlineProvider.BACKGROUND
                clipToOutline = true
                setOnClickListener { onMediaClick(entry) }
            }
            if (isVisual && thumbnail != null) {
                card.addView(ImageView(activity).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setImageBitmap(thumbnail)
                }, matchParent())
            }
            card.addView(TextView(activity).apply {
                text = if (isVisual && thumbnail == null) "Loading $label" else label
                setTextColor(android.graphics.Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTypeface(null, Typeface.BOLD)
                setPadding(dp(10), dp(5), dp(10), dp(5))
                background = GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    setColor(0x99000000.toInt())
                }
            }, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.END
            ).apply { setMargins(dp(8), dp(8), dp(8), dp(8)) })
            card.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(if (isVisual) 170 else 54)
            )
            return card
        }

        // --- Inline audio ---

        private inner class AudioState {
            var player: MediaPlayer? = null
            var prepared = false
            var playing = false
            var failed = false
            var loading = false
            var position = 0
            var duration = 0
        }

        private val audioTicker = object : Runnable {
            override fun run() {
                var anyPlaying = false
                for (state in audioStates.values) {
                    if (!state.playing) continue
                    anyPlaying = true
                    try {
                        state.position = state.player?.currentPosition?.coerceAtLeast(0) ?: 0
                        state.duration = state.player?.duration?.coerceAtLeast(0) ?: state.duration
                    } catch (_: Exception) {
                    }
                }
                if (anyPlaying) {
                    messageAdapter?.notifyDataSetChanged()
                    mainHandler.postDelayed(this, 250)
                }
            }
        }

        private fun releaseAllAudio() {
            mainHandler.removeCallbacks(audioTicker)
            for (state in audioStates.values) {
                try {
                    state.player?.release()
                } catch (_: Exception) {
                }
                state.player = null
            }
            audioStates.clear()
        }

        private fun pauseOtherAudio(except: AudioState) {
            for (state in audioStates.values) {
                if (state === except || !state.playing) continue
                try {
                    state.player?.pause()
                } catch (_: Exception) {
                }
                state.playing = false
            }
        }

        private fun buildAudioRow(entry: DelMessageStore.DeletedBackupEntry): View {
            val state = audioStates.getOrPut(entry.id) { AudioState() }
            val uri = getCachedMediaUri(entry)
            if (uri == null) {
                if (isMediaUnavailable(entry.id)) {
                    state.failed = true
                } else if (!isMediaLoading(entry.id)) {
                    state.loading = true
                    requestMediaUri(entry, Runnable { messageAdapter?.notifyDataSetChanged() })
                }
            } else if (state.loading && state.player == null && !state.failed) {
                state.loading = false
            }

            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(8), dp(8), dp(8))
                background = GradientDrawable().apply {
                    cornerRadius = dp(10).toFloat()
                    setColor(mediaCardBackground())
                }
            }
            val label = when {
                state.failed -> "!"
                (uri == null || state.loading) && !state.prepared -> "..."
                state.playing -> "Pause"
                else -> "Play"
            }
            val button = TextView(activity).apply {
                text = label
                setTextColor(if (state.failed) 0xFFD64545.toInt() else DesignUtils.getUnSeenColor())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTypeface(null, Typeface.BOLD)
                setPadding(dp(6), dp(8), dp(6), dp(8))
                isEnabled = uri != null && !state.failed
                setOnClickListener { toggleAudio(entry, state, uri) }
            }
            val seek = SeekBar(activity).apply {
                max = 1000
                progress = if (state.duration > 0) {
                    (state.position.coerceIn(0, state.duration).toFloat() / state.duration * 1000).toInt()
                } else 0
                isEnabled = state.prepared && !state.failed && state.duration > 0
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                        if (!fromUser) return
                        val player = state.player ?: return
                        if (!state.prepared || state.duration <= 0) return
                        val target = (progress / 1000f * state.duration).toInt()
                        try {
                            player.seekTo(target)
                            state.position = target
                        } catch (_: Exception) {
                        }
                    }

                    override fun onStartTrackingTouch(bar: SeekBar?) {}
                    override fun onStopTrackingTouch(bar: SeekBar?) {}
                })
            }
            val time = TextView(activity).apply {
                text = if (state.failed) "--:--"
                else "${formatPlaybackTime(state.position)} / ${formatPlaybackTime(state.duration)}"
                setTextColor(mutedColor())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setPadding(dp(6), 0, 0, 0)
            }
            row.addView(button)
            row.addView(seek, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(time)
            return row
        }

        private fun toggleAudio(entry: DelMessageStore.DeletedBackupEntry, state: AudioState, uri: Uri?) {
            val existing = state.player
            if (existing == null) {
                if (uri == null || state.failed) return
                state.loading = true
                state.player = createInlineAudioPlayer(
                    uri = uri,
                    onPrepared = { mediaPlayer ->
                        state.duration = mediaPlayer.duration.coerceAtLeast(0)
                        state.prepared = true
                        state.loading = false
                        pauseOtherAudio(state)
                        try {
                            mediaPlayer.start()
                            state.playing = true
                        } catch (_: Exception) {
                            state.failed = true
                        }
                        mainHandler.removeCallbacks(audioTicker)
                        mainHandler.post(audioTicker)
                        messageAdapter?.notifyDataSetChanged()
                    },
                    onCompletion = {
                        state.playing = false
                        state.position = state.duration
                        messageAdapter?.notifyDataSetChanged()
                    },
                    onError = {
                        state.loading = false
                        state.playing = false
                        state.failed = true
                        messageAdapter?.notifyDataSetChanged()
                    }
                )
                messageAdapter?.notifyDataSetChanged()
                return
            }
            if (!state.prepared || state.failed) return
            try {
                if (existing.isPlaying) {
                    existing.pause()
                    state.playing = false
                } else {
                    pauseOtherAudio(state)
                    existing.start()
                    state.playing = true
                    mainHandler.removeCallbacks(audioTicker)
                    mainHandler.post(audioTicker)
                }
            } catch (_: Exception) {
                state.failed = true
                state.playing = false
            }
            messageAdapter?.notifyDataSetChanged()
        }

        fun onCreateOptionsMenu(menu: Menu) {
            menu.clear()
            menu.add(0, MENU_RESTORE_VISIBLE, 0, R.string.restore_all_messages)
        }

        fun onOptionsItemSelected(item: MenuItem): Boolean {
            when (item.itemId) {
                android.R.id.home -> {
                    if (currentThread != null) {
                        showChatListScreen()
                    } else {
                        activity.finish()
                    }
                    return true
                }

                MENU_RESTORE_VISIBLE -> {
                    if (currentThread != null) {
                        restoreCurrentThread()
                    } else {
                        restoreVisibleThreads()
                    }
                    return true
                }
            }
            return false
        }

        fun handleBackPressed(): Boolean {
            return if (currentThread != null) {
                showChatListScreen()
                true
            } else false
        }

        fun reloadThreads(onFinished: (() -> Unit)? = null) {
            if (!attached || activity.isFinishing || activity.isDestroyed) {
                onFinished?.let { mainHandler.post(it) }
                return
            }

            Utils.executor.execute {
                try {
                    val entries = DelMessageStore.getInstance(activity)
                        .listDeletedMessageBackups(null, true, true, 0)
                    val threadMap = LinkedHashMap<String, ChatThread>()

                    for (entry in entries) {
                        val chatKeyRaw = normalizeRawJid(
                            firstNonEmpty(
                                entry.chatPhoneRawString,
                                entry.chatRawString
                            )
                        )
                        if (TextUtils.isEmpty(chatKeyRaw)) continue

                        val thread = threadMap.getOrPut(chatKeyRaw!!) {
                            ChatThread(
                                chatKey = chatKeyRaw,
                                chatRawString = entry.chatRawString,
                                chatPhoneRawString = entry.chatPhoneRawString,
                                group = isGroupRaw(chatKeyRaw)
                            )
                        }
                        thread.messages.add(entry)
                        val sortTs = getMessageSortTimestamp(entry)
                        if (sortTs > thread.lastTimestamp) {
                            thread.lastTimestamp = sortTs
                        }
                    }

                    val sorted = threadMap.values.sortedByDescending { it.lastTimestamp }
                    for (thread in sorted) {
                        thread.messages.sortBy { getMessageSortTimestamp(it) }
                    }

                    mainHandler.post {
                        onFinished?.invoke()
                        if (activity.isFinishing || activity.isDestroyed) return@post
                        allThreads.clear()
                        allThreads.addAll(sorted)
                        applyChatFilter()
                        render()

                        val refreshed = currentThread?.let { findThreadByKey(it.chatKey) }
                        if (refreshed == null) {
                            showChatListScreen()
                        } else {
                            openThread(refreshed)
                        }
                    }
                } catch (e: Exception) {
                    logError(e)
                    onFinished?.let { mainHandler.post(it) }
                }
            }
        }

        /** Wraps [list] so pulling it down reloads the backups from the database. */
        private fun withPullToRefresh(list: ListView): SwipeRefreshLayout {
            return SwipeRefreshLayout(activity).apply {
                setOnChildScrollUpCallback { _, _ -> list.canScrollVertically(-1) }
                setOnRefreshListener { reloadThreads { isRefreshing = false } }
                addView(list, ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ))
            }
        }

        fun applyChatFilter() {
            visibleThreads.clear()
            val query = normalizeText(searchQuery)

            for (thread in allThreads) {
                if (query.isEmpty()) {
                    visibleThreads.add(thread)
                    continue
                }
                val name = normalizeText(resolveContactName(thread.chatKey))
                val raw = normalizeText(thread.chatKey)
                val preview = normalizeText(thread.lastPreview)

                if (name.contains(query) || raw.contains(query) || preview.contains(query)) {
                    visibleThreads.add(thread)
                }
            }
            chatListAdapter?.rebuild()
        }

        fun onSearchQueryChanged(query: String) {
            searchQuery = query
            applyChatFilter()
        }

        fun finish() {
            activity.finish()
        }

        fun deletedMessagesTitle(): String {
            return activity.getString(R.string.deleted_message_capture_title)
        }

        fun onChatClicked(thread: ChatThread?) {
            if (thread != null) openThread(thread)
        }

        fun onChatLongClicked(thread: ChatThread?) {
            if (thread != null) showThreadActionsDialog(thread)
        }

        fun openThread(thread: ChatThread) {
            currentThread = thread
            showChatList = false
            activity.invalidateOptionsMenu()
            render()
        }

        fun showChatListScreen() {
            currentThread = null
            showChatList = true
            updateActionBarTitle(activity.getString(R.string.deleted_message_notification_channel))
            activity.invalidateOptionsMenu()
            render()
        }

        private fun updateActionBarTitle(title: String) {
            try {
                val actionBar = ReflectionUtils.callMethod(activity, "getSupportActionBar")
                if (actionBar != null) {
                    ReflectionUtils.callMethod(actionBar, "setTitle", title)
                    ReflectionUtils.callMethod(actionBar, "setDisplayHomeAsUpEnabled", true)
                    return
                }
            } catch (_: Throwable) {
            }
            activity.title = title
        }

        // --- Thread actions ---

        private fun showThreadActionsDialog(thread: ChatThread) {
            val items = arrayOf<CharSequence?>(
                activity.getString(R.string.restore_all_messages),
                activity.getString(R.string.delete_all_permanently)
            )
            AlertDialogWpp(activity)
                .setTitle(resolveContactName(thread.chatKey))
                .setItems(items) { _, which ->
                    when (which) {
                        0 -> restoreThread(thread)
                        1 -> confirmRemoveThread(thread)
                    }
                }
                .show()
        }

        fun restoreEntry(backupId: Long) {
            val loadingDialog = createLoadingDialog()
            loadingDialog.show()
            Utils.executor.execute {
                try {
                    val messageDb = MessageStore.getInstance().getWritableDatabase()
                    DelMessageStore.getInstance(activity).restoreBackupById(messageDb, backupId)
                } catch (e: Exception) {
                    logError(e)
                } finally {
                    mainHandler.post {
                        if (loadingDialog.isShowing) loadingDialog.dismiss()
                        reloadThreads()
                    }
                }
            }
        }

        fun confirmRestoreEntry(backupId: Long) {
            AlertDialogWpp(activity)
                .setTitle(activity.getString(R.string.deleted_message_notification_channel))
                .setMessage(activity.getString(R.string.confirm_restore_message))
                .setPositiveButton(activity.getString(R.string.yes)) { _, _ -> restoreEntry(backupId) }
                .setNegativeButton(activity.getString(R.string.no), null)
                .show()
        }

        private fun restoreVisibleThreads() {
            val ids = visibleThreads.flatMap { it.messages.map { e -> e.id } }
            confirmRestoreIds(ids)
        }

        fun restoreCurrentThread() {
            val thread = currentThread ?: return
            confirmRestoreIds(thread.messages.map { it.id })
        }

        private fun restoreThread(thread: ChatThread) {
            confirmRestoreIds(thread.messages.map { it.id })
        }

        private fun confirmRestoreIds(ids: List<Long>) {
            if (ids.isEmpty()) return
            AlertDialogWpp(activity)
                .setTitle(activity.getString(R.string.restore_all_messages))
                .setMessage(activity.getString(R.string.confirm_restore_all_messages, ids.size))
                .setPositiveButton(activity.getString(R.string.yes)) { _, _ -> restoreIds(ids) }
                .setNegativeButton(activity.getString(R.string.no), null)
                .show()
        }

        private fun restoreIds(ids: List<Long>) {
            if (ids.isEmpty()) return
            val loadingDialog = createLoadingDialog()
            loadingDialog.show()
            Utils.executor.execute {
                try {
                    val messageDb = MessageStore.getInstance().getWritableDatabase()
                    val restored = DelMessageStore.getInstance(activity)
                        .restoreBackupsByIds(messageDb, ids.toLongArray())
                    mainHandler.post {
                        if (loadingDialog.isShowing) loadingDialog.dismiss()
                        if (restored > 0) {
                            Utils.showToast(
                                activity.getString(R.string.messages_restored_success),
                                Toast.LENGTH_SHORT)
                        }
                        reloadThreads()
                    }
                } catch (e: Exception) {
                    logError(e)
                    mainHandler.post {
                        if (loadingDialog.isShowing) loadingDialog.dismiss()
                        reloadThreads()
                    }
                }
            }
        }

        fun confirmRemove(backupId: Long) {
            AlertDialogWpp(activity)
                .setTitle(activity.getString(R.string.delete_for_me))
                .setMessage(activity.getString(R.string.confirm_delete_message))
                .setPositiveButton(activity.getString(R.string.yes)) { _, _ ->
                    val loadingDialog = createLoadingDialog()
                    loadingDialog.show()
                    Utils.executor.execute {
                        try {
                            DelMessageStore.getInstance(activity).removeBackupById(backupId)
                        } catch (e: Exception) {
                            logError(e)
                        } finally {
                            mainHandler.post {
                                if (loadingDialog.isShowing) loadingDialog.dismiss()
                                reloadThreads()
                            }
                        }
                    }
                }
                .setNegativeButton(activity.getString(R.string.no), null)
                .show()
        }

        fun confirmRemoveCurrentThread() {
            val thread = currentThread ?: return
            confirmRemoveThread(thread)
        }

        private fun confirmRemoveThread(thread: ChatThread) {
            val ids = thread.messages.map { it.id }
            if (ids.isEmpty()) return
            AlertDialogWpp(activity)
                .setTitle(resolveContactName(thread.chatKey))
                .setMessage(activity.getString(R.string.confirm_delete_all_messages, ids.size))
                .setPositiveButton(activity.getString(R.string.yes)) { _, _ -> removeThread(thread) }
                .setNegativeButton(activity.getString(R.string.no), null)
                .show()
        }

        private fun removeThread(thread: ChatThread) {
            val loadingDialog = createLoadingDialog()
            loadingDialog.show()
            Utils.executor.execute {
                try {
                    val removed = DelMessageStore.getInstance(activity).removeBackupsByChat(
                        chatKey = thread.chatKey,
                        chatRawString = thread.chatRawString,
                        chatPhoneRawString = thread.chatPhoneRawString
                    )
                    mainHandler.post {
                        if (loadingDialog.isShowing) loadingDialog.dismiss()
                        if (removed > 0) {
                            Utils.showToast(
                                activity.getString(R.string.messages_deleted_success),
                                Toast.LENGTH_SHORT)
                        }
                        showChatListScreen()
                        reloadThreads()
                    }
                } catch (e: Exception) {
                    logError(e)
                    mainHandler.post {
                        if (loadingDialog.isShowing) loadingDialog.dismiss()
                        reloadThreads()
                    }
                }
            }
        }

        private fun removeIds(ids: List<Long>) {
            if (ids.isEmpty()) return
            val loadingDialog = createLoadingDialog()
            loadingDialog.show()
            Utils.executor.execute {
                try {
                    DelMessageStore.getInstance(activity).removeBackupsByIds(ids.toLongArray())
                    mainHandler.post {
                        if (loadingDialog.isShowing) loadingDialog.dismiss()
                        Utils.showToast(
                            activity.getString(R.string.messages_deleted_success),
                            Toast.LENGTH_SHORT)
                        reloadThreads()
                    }
                } catch (e: Exception) {
                    logError(e)
                    mainHandler.post {
                        if (loadingDialog.isShowing) loadingDialog.dismiss()
                        reloadThreads()
                    }
                }
            }
        }

        // --- Media ---

        fun onMediaClick(entry: DelMessageStore.DeletedBackupEntry) {
            openMediaPreview(entry)
        }

        fun createInlineAudioPlayer(
            uri: Uri,
            onPrepared: (MediaPlayer) -> Unit,
            onCompletion: () -> Unit,
            onError: () -> Unit
        ): MediaPlayer? {
            val player = MediaPlayer()
            return try {
                player.setDataSource(activity, uri)
                player.setOnPreparedListener { onPrepared(it) }
                player.setOnCompletionListener { onCompletion() }
                player.setOnErrorListener { _, _, _ ->
                    onError()
                    true
                }
                player.prepareAsync()
                player
            } catch (e: Exception) {
                logError(e)
                try {
                    player.release()
                } catch (_: Exception) {
                }
                onError()
                null
            }
        }

        private fun openMediaPreview(entry: DelMessageStore.DeletedBackupEntry) {
            if (TextUtils.isEmpty(entry.messageMediaJson)) {
                Utils.showToast(
                    activity.getString(R.string.download_not_available),
                    Toast.LENGTH_SHORT)
                return
            }
            try {
                val mediaObj = JSONObject(entry.messageMediaJson!!)
                val filePath = sanitizeText(mediaObj.optString("file_path", null))
                val messageUrl = sanitizeText(mediaObj.optString("message_url", null))

                var mediaUri: Uri? = null
                if (!TextUtils.isEmpty(filePath)) {
                    mediaUri =
                        if (filePath!!.startsWith("content://") || filePath.startsWith("file://")) {
                            Uri.parse(filePath)
                        } else {
                            val mediaDirs = Utils.application.externalMediaDirs
                            val appName = Utils.application.packageManager
                                .getApplicationLabel(Utils.application.applicationInfo)
                            if (mediaDirs.isNotEmpty()) {
                                val rootDir = File(mediaDirs[0], appName.toString())
                                val file = File(rootDir, filePath)
                                if (file.exists()) Uri.fromFile(file) else null
                            } else null
                        }
                }

                val mimeType = mediaObj.optString("mime_type", "").lowercase(Locale.US)

                if (mediaUri != null) {
                    openPreviewByMime(mimeType, mediaUri, null)
                    return
                }

                if (TextUtils.isEmpty(messageUrl)) {
                    Utils.showToast(
                        activity.getString(R.string.download_not_available),
                        Toast.LENGTH_SHORT)
                    return
                }

                val mediaKeyHex = extractMediaKeyHex(mediaObj)
                val loadingDialog = createLoadingDialog()
                loadingDialog.show()

                Utils.executor.execute {
                    try {
                        val previewFile = downloadPreviewFile(messageUrl!!, mimeType, mediaKeyHex)
                        val previewUri = Uri.fromFile(previewFile)
                        mainHandler.post {
                            if (loadingDialog.isShowing) loadingDialog.dismiss()
                            openPreviewByMime(mimeType, previewUri, null)
                        }
                    } catch (e: Exception) {
                        logError(e)
                        mainHandler.post {
                            if (loadingDialog.isShowing) loadingDialog.dismiss()
                            Utils.showToast(
                                activity.getString(R.string.download_not_available),
                                Toast.LENGTH_SHORT)
                        }
                    }
                }
            } catch (e: Exception) {
                Utils.showToast(
                    activity.getString(R.string.download_not_available),
                    Toast.LENGTH_SHORT)
            }
        }

        private fun openPreviewByMime(mimeType: String, mediaUri: Uri, cleanupFile: File?) {
            when {
                mimeType.startsWith("image/") -> showImagePreview(mediaUri, cleanupFile)
                mimeType.startsWith("video/") -> showVideoPreview(mediaUri, cleanupFile)
                mimeType.startsWith("audio/") -> showAudioPreview(mediaUri, cleanupFile)
                else -> Utils.showToast(
                    activity.getString(R.string.download_not_available),
                    Toast.LENGTH_SHORT)
            }
        }

        private fun createLoadingDialog(): Dialog {
            val dialog = Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            val root = FrameLayout(activity)
            root.setBackgroundColor(0xE6000000.toInt())
            val text = TextView(activity).apply {
                setTextColor(android.graphics.Color.WHITE)
                textSize = 15f
                text = activity.getString(R.string.decrypting)
                gravity = Gravity.CENTER
            }
            root.addView(
                text, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            )
            dialog.setContentView(root)
            dialog.setCancelable(true)
            return dialog
        }

        private fun downloadPreviewFile(
            messageUrl: String,
            mimeType: String,
            mediaKeyHex: String?
        ): File {
            val extension = inferFileExtension(mimeType)
            val cacheName = "media_${sha256(messageUrl + "|" + mediaKeyHex)}$extension"
            val tempFile = File(getMediaCacheDir(), "$cacheName.tmp")
            val cacheFile = File(getMediaCacheDir(), cacheName)

            if (cacheFile.exists() && cacheFile.length() > 0) {
                cacheFile.setLastModified(System.currentTimeMillis())
                return cacheFile
            }
            if (tempFile.exists()) tempFile.delete()

            val client = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .addHeader("User-Agent", "Chrome/117.0.5938.150")
                            .build()
                    )
                }
                .build()

            val request = Request.Builder().url(messageUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("download_failed")
                }
                val encryptedData = response.body.bytes()
                var outputData = encryptedData
                if (!TextUtils.isEmpty(mediaKeyHex)) {
                    outputData = decryptMedia(encryptedData, mediaKeyHex!!, mimeType)
                }
                FileOutputStream(tempFile).use { fos -> fos.write(outputData) }
            }

            if (cacheFile.exists()) cacheFile.delete()
            if (!tempFile.renameTo(cacheFile)) return tempFile
            pruneMediaCache()
            return cacheFile
        }

        private fun inferFileExtension(mimeType: String): String = when {
            mimeType.startsWith("image/") -> ".jpg"
            mimeType.startsWith("video/") -> ".mp4"
            mimeType.contains("ogg") -> ".ogg"
            mimeType.contains("aac") -> ".aac"
            mimeType.contains("wav") -> ".wav"
            mimeType.startsWith("audio/") -> ".m4a"
            else -> ".bin"
        }

        private fun extractMediaKeyHex(mediaObj: JSONObject): String? {
            var rawKey = sanitizeText(mediaObj.optString("media_key", null))
            if (TextUtils.isEmpty(rawKey)) {
                rawKey = sanitizeText(mediaObj.optString("e2ee_media_key", null))
            }
            if (TextUtils.isEmpty(rawKey)) return null

            if (rawKey!!.startsWith("B64:")) {
                return bytesToHex(
                    android.util.Base64.decode(rawKey.substring(4), android.util.Base64.NO_WRAP)
                )
            }
            val normalized = rawKey.replace(" ", "").trim()
            if (normalized.matches(Regex("(?i)^[0-9a-f]{64}$"))) {
                return normalized.lowercase(Locale.US)
            }
            try {
                val decoded = android.util.Base64.decode(normalized, android.util.Base64.DEFAULT)
                if (decoded.size == 32) return bytesToHex(decoded)
            } catch (_: Exception) {
            }
            return null
        }

        private fun decryptMedia(
            encryptedData: ByteArray,
            mediaKeyHex: String,
            mimeType: String
        ): ByteArray {
            if (!mediaKeyHex.matches(Regex("(?i)^[0-9a-f]{64}$"))) {
                throw IllegalArgumentException("invalid_media_key")
            }
            val keyBytes = hexToBytes(mediaKeyHex)
            val typeKey = MEDIA_KEYS[mimeType.split("/")[0]] ?: MEDIA_KEYS["document"]!!
            val derivedKey = HKDF.createFor(3).deriveSecrets(keyBytes, typeKey, 112)
                ?: throw IllegalStateException("media_key_derivation_failed")
            val iv = derivedKey.copyOfRange(0, 16)
            val aesKey = derivedKey.copyOfRange(16, 48)

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"), IvParameterSpec(iv))

            val encryptedSize = maxOf(0, encryptedData.size - 10)
            return cipher.doFinal(encryptedData.copyOfRange(0, encryptedSize))
        }

        private fun hexToBytes(hex: String): ByteArray {
            val bytes = ByteArray(hex.length / 2)
            for (i in hex.indices step 2) {
                bytes[i / 2] = (
                        (Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)
                        ).toByte()
            }
            return bytes
        }

        private fun bytesToHex(bytes: ByteArray): String {
            val builder = StringBuilder(bytes.size * 2)
            for (b in bytes) {
                builder.append(String.format(Locale.US, "%02x", b))
            }
            return builder.toString()
        }

        private fun showImagePreview(mediaUri: Uri, cleanupFile: File?) {
            val dialog = Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            val root = FrameLayout(activity)
            root.setBackgroundColor(0xE6000000.toInt())
            val image = ImageView(activity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageURI(mediaUri)
            }
            root.addView(image)
            root.addView(createCloseButton(dialog))
            dialog.setOnDismissListener {
                if (cleanupFile != null && cleanupFile.exists()) cleanupFile.delete()
            }
            dialog.setContentView(root)
            dialog.show()
        }

        private fun showVideoPreview(mediaUri: Uri, cleanupFile: File?) {
            val dialog = Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            val root = FrameLayout(activity)
            root.setBackgroundColor(android.graphics.Color.BLACK)

            val textureView = TextureView(activity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            val player = MediaPlayer()

            textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(
                    surface: SurfaceTexture,
                    width: Int,
                    height: Int
                ) {
                    try {
                        player.setSurface(Surface(surface))
                        player.setDataSource(activity, mediaUri)
                        player.setOnPreparedListener { mp ->
                            mp.isLooping = true
                            mp.start()
                        }
                        player.setOnErrorListener { _, _, _ ->
                            Utils.showToast(
                                activity.getString(R.string.download_not_available),
                                Toast.LENGTH_SHORT)
                            true
                        }
                        player.prepareAsync()
                    } catch (e: Exception) {
                        logError(e)
                        Utils.showToast(
                            activity.getString(R.string.download_not_available),
                            Toast.LENGTH_SHORT)
                    }
                }

                override fun onSurfaceTextureSizeChanged(
                    surface: SurfaceTexture,
                    width: Int,
                    height: Int
                ) {
                }

                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture) = true
                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
            }

            root.addView(textureView)
            root.addView(createCloseButton(dialog))
            dialog.setOnDismissListener {
                try {
                    if (player.isPlaying) player.stop()
                    player.release()
                } catch (_: Exception) {
                }
                if (cleanupFile != null && cleanupFile.exists()) cleanupFile.delete()
            }
            dialog.setContentView(root)
            dialog.show()
        }

        private fun showAudioPreview(mediaUri: Uri, cleanupFile: File?) {
            val dialog = Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            val root = FrameLayout(activity)
            root.setBackgroundColor(0xE6000000.toInt())

            val panel = FrameLayout(activity)
            val panelBg = GradientDrawable().apply {
                setCornerRadius(Utils.dipToPixels(14).toFloat())
                setColor(
                    if (DesignUtils.isNightMode()) 0xFF1F2C33.toInt()
                    else android.graphics.Color.WHITE
                )
            }
            panel.background = panelBg
            val panelParams = FrameLayout.LayoutParams(
                (activity.resources.displayMetrics.widthPixels * 0.86f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            panel.layoutParams = panelParams

            val title = TextView(activity).apply {
                text = activity.getString(R.string.voice_note)
                setTextColor(DesignUtils.getPrimaryTextColor())
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
            }

            val seekBar = SeekBar(activity).apply { max = 1000 }

            val playPause = ImageButton(activity).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setImageResource(android.R.drawable.ic_media_play)
                isEnabled = false
            }
            val currentTime = TextView(activity).apply {
                text = "00:00"
                setTextColor(0xFF8A8A8A.toInt())
            }
            val totalTime = TextView(activity).apply {
                text = "--:--"
                setTextColor(0xFF8A8A8A.toInt())
            }

            panel.addView(title)
            panel.addView(seekBar)
            panel.addView(playPause)
            panel.addView(currentTime)
            panel.addView(totalTime)

            root.addView(panel)
            root.addView(createCloseButton(dialog))

            val player = MediaPlayer()
            val updateHandler = Handler(Looper.getMainLooper())
            val progressRunnable = object : Runnable {
                override fun run() {
                    try {
                        if (player.isPlaying && player.duration > 0) {
                            val position = player.currentPosition
                            val duration = player.duration
                            seekBar.progress = ((position / duration.toFloat()) * 1000).toInt()
                            currentTime.text = formatPlaybackTime(position)
                        }
                    } catch (_: Exception) {
                    }
                    if (dialog.isShowing) updateHandler.postDelayed(this, 250)
                }
            }

            player.setOnPreparedListener { mp ->
                playPause.isEnabled = true
                totalTime.text = formatPlaybackTime(mp.duration)
                mp.start()
                playPause.setImageResource(android.R.drawable.ic_media_pause)
                updateHandler.post(progressRunnable)
            }
            player.setOnCompletionListener {
                playPause.setImageResource(android.R.drawable.ic_media_play)
                seekBar.progress = 1000
                currentTime.text = formatPlaybackTime(player.duration)
            }
            player.setOnErrorListener { _, _, _ ->
                Utils.showToast(
                    activity.getString(R.string.download_not_available),
                    Toast.LENGTH_SHORT)
                dialog.dismiss()
                true
            }
            playPause.setOnClickListener {
                try {
                    if (player.isPlaying) {
                        player.pause()
                        playPause.setImageResource(android.R.drawable.ic_media_play)
                    } else {
                        player.start()
                        playPause.setImageResource(android.R.drawable.ic_media_pause)
                    }
                } catch (_: Exception) {
                }
            }
            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    try {
                        val duration = player.duration
                        if (duration > 0) {
                            val target = ((progress / 1000f) * duration).toInt()
                            player.seekTo(target)
                            currentTime.text = formatPlaybackTime(target)
                        }
                    } catch (_: Exception) {
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

            try {
                player.setDataSource(activity, mediaUri)
                player.prepareAsync()
            } catch (e: Exception) {
                Utils.showToast(
                    activity.getString(R.string.download_not_available),
                    Toast.LENGTH_SHORT)
                try {
                    player.release()
                } catch (_: Exception) {
                }
                if (cleanupFile != null && cleanupFile.exists()) cleanupFile.delete()
                return
            }

            dialog.setOnDismissListener {
                updateHandler.removeCallbacks(progressRunnable)
                try {
                    if (player.isPlaying) player.stop()
                } catch (_: Exception) {
                }
                try {
                    player.release()
                } catch (_: Exception) {
                }
                if (cleanupFile != null && cleanupFile.exists()) cleanupFile.delete()
            }
            dialog.setContentView(root)
            dialog.show()
        }

        private fun formatPlaybackTime(millis: Int): String {
            val totalSeconds = maxOf(0, millis / 1000)
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }

        private fun createCloseButton(dialog: Dialog): ImageView {
            return ImageView(activity).apply {
                val params = FrameLayout.LayoutParams(
                    Utils.dipToPixels(42),
                    Utils.dipToPixels(42),
                    Gravity.TOP or Gravity.START
                )
                val margin = Utils.dipToPixels(16)
                params.setMargins(margin, margin, margin, margin)
                layoutParams = params
                setBackgroundColor(0x66000000.toInt())
                val drawable = activity.getDrawable(R.drawable.ic_baseline_arrow_back_24)
                if (drawable != null) {
                    drawable.setTint(android.graphics.Color.WHITE)
                    setImageDrawable(drawable)
                }
                setOnClickListener { dialog.dismiss() }
            }
        }

        // --- Contact / Avatar ---

        fun resolveContactName(rawJid: String?): String {
            val normalized = normalizeRawJid(rawJid)
            if (TextUtils.isEmpty(normalized)) return ""
            val cached = contactNameCache[normalized]
            if (!TextUtils.isEmpty(cached)) return cached!!

            var name: String? = null
            try {
                val userJid = FMessageWpp.UserJid(normalized!!)
                val waContact = WaContactWpp.getWaContactFromJid(userJid)
                if (waContact != null) {
                    name = waContact.displayName?.takeIf { it.isNotEmpty() } ?: waContact.waName
                }
                if (TextUtils.isEmpty(name)) {
                    name = userJid.phoneNumber
                }
            } catch (_: Exception) {
            }

            if (TextUtils.isEmpty(name)) name = normalized
            contactNameCache[normalized!!] = name!!
            return name
        }

        fun resolveAvatar(rawJid: String?, group: Boolean): Drawable? {
            val normalized = normalizeRawJid(rawJid)
            if (TextUtils.isEmpty(normalized)) return null
            val cacheKey = (if (group) "group:" else "user:") + normalized
            avatarCache[cacheKey]?.let { return it }

            var avatar: Drawable? = null
            if (!group) {
                try {
                    val userJid = FMessageWpp.UserJid(normalized!!)
                    val waContact = WaContactWpp.getWaContactFromJid(userJid)
                    if (waContact != null) {
                        val stream = waContact.getProfilePhoto(false)
                        if (stream != null) {
                            stream.use { s ->
                                val bitmap = BitmapFactory.decodeStream(s)
                                if (bitmap != null) {
                                    avatar = BitmapDrawable(activity.resources, bitmap)
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
            avatarCache[cacheKey] = avatar
            return avatar
        }

        // --- MediaResolver ---

        override fun resolveMimeType(entry: DelMessageStore.DeletedBackupEntry): String? {
            synchronized(mediaCacheLock) {
                mediaMimeCache[entry.id]?.let { return it }
            }
            val mediaData = parseMediaData(entry) ?: return null
            synchronized(mediaCacheLock) {
                mediaMimeCache[entry.id] = mediaData.mimeType
            }
            return mediaData.mimeType
        }

        override fun getThumbnail(entryId: Long): Bitmap? {
            synchronized(mediaCacheLock) {
                return mediaThumbCache[entryId]
            }
        }

        override fun requestThumbnail(
            entry: DelMessageStore.DeletedBackupEntry,
            onReady: Runnable
        ) {
            synchronized(mediaCacheLock) {
                if (mediaThumbCache.containsKey(entry.id) || loadingThumbIds.contains(entry.id)) return
                loadingThumbIds.add(entry.id)
            }

            Utils.executor.execute {
                var thumbnail: Bitmap? = null
                try {
                    val mediaData = parseMediaData(entry)
                    if (mediaData != null) {
                        thumbnail = loadThumbnail(mediaData)
                    }
                } catch (_: Exception) {
                }

                synchronized(mediaCacheLock) {
                    loadingThumbIds.remove(entry.id)
                    if (thumbnail != null) {
                        mediaThumbCache[entry.id] = thumbnail
                        trimThumbCacheLocked(90)
                    }
                }
                mainHandler.post(onReady)
            }
        }

        override fun getCachedMediaUri(entry: DelMessageStore.DeletedBackupEntry): Uri? {
            synchronized(mediaCacheLock) {
                mediaUriCache[entry.id]?.let { return it }
            }
            val mediaData = parseMediaData(entry) ?: return null
            val existing = resolveBestMediaUri(mediaData, false)
            if (existing != null) {
                synchronized(mediaCacheLock) {
                    mediaUriCache[entry.id] = existing
                    failedMediaIds.remove(entry.id)
                }
            }
            return existing
        }

        override fun requestMediaUri(entry: DelMessageStore.DeletedBackupEntry, onReady: Runnable) {
            synchronized(mediaCacheLock) {
                if (mediaUriCache.containsKey(entry.id) || loadingMediaIds.contains(entry.id) || failedMediaIds.contains(
                        entry.id
                    )
                ) return
                loadingMediaIds.add(entry.id)
            }

            Utils.executor.execute {
                var result: Uri? = null
                try {
                    val mediaData = parseMediaData(entry)
                    if (mediaData != null) {
                        result = resolveBestMediaUri(mediaData, true)
                    }
                } catch (e: Exception) {
                    logError(e)
                }

                synchronized(mediaCacheLock) {
                    loadingMediaIds.remove(entry.id)
                    if (result != null) {
                        mediaUriCache[entry.id] = result
                        failedMediaIds.remove(entry.id)
                    } else {
                        failedMediaIds.add(entry.id)
                    }
                }
                mainHandler.post(onReady)
            }
        }

        override fun isMediaLoading(entryId: Long): Boolean {
            synchronized(mediaCacheLock) {
                return loadingMediaIds.contains(entryId)
            }
        }

        override fun isMediaUnavailable(entryId: Long): Boolean {
            synchronized(mediaCacheLock) {
                return failedMediaIds.contains(entryId)
            }
        }

        private fun loadThumbnail(mediaData: ParsedMediaData): Bitmap? {
            decodeThumbFromJson(mediaData.mediaObj)?.let { return it }
            val mediaUri = resolveBestMediaUri(mediaData, true) ?: return null
            return if (mediaData.mimeType.startsWith("video/")) {
                extractVideoFrame(mediaUri)
            } else {
                decodeBitmapFromUri(mediaUri, Utils.dipToPixels(220))
            }
        }

        private fun decodeThumbFromJson(mediaObj: JSONObject): Bitmap? {
            for (key in arrayOf("thumbnail", "thumbnail_data", "thumb_image", "preview")) {
                val encoded = sanitizeText(mediaObj.optString(key, null)) ?: continue
                try {
                    val raw = if (encoded.startsWith("B64:")) {
                        android.util.Base64.decode(
                            encoded.substring(4),
                            android.util.Base64.NO_WRAP
                        )
                    } else {
                        android.util.Base64.decode(encoded, android.util.Base64.DEFAULT)
                    }
                    val bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size)
                    if (bitmap != null) return bitmap
                } catch (_: Exception) {
                }
            }
            return null
        }

        private fun decodeBitmapFromUri(uri: Uri, reqSize: Int): Bitmap? {
            try {
                if ("file".equals(uri.scheme, ignoreCase = true)) {
                    val file = File(uri.path ?: "")
                    if (!file.exists()) return null
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.absolutePath, bounds)
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = calculateInSampleSize(bounds, reqSize, reqSize)
                    }
                    return BitmapFactory.decodeFile(file.absolutePath, options)
                }

                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                activity.contentResolver.openInputStream(uri)?.use { boundsStream ->
                    BitmapFactory.decodeStream(boundsStream, null, bounds)
                }
                val decode = BitmapFactory.Options().apply {
                    inSampleSize = calculateInSampleSize(bounds, reqSize, reqSize)
                }
                return activity.contentResolver.openInputStream(uri)?.use { decodeStream ->
                    BitmapFactory.decodeStream(decodeStream, null, decode)
                }
            } catch (_: Exception) {
                return null
            }
        }

        private fun extractVideoFrame(uri: Uri): Bitmap? {
            val retriever = MediaMetadataRetriever()
            return try {
                retriever.setDataSource(activity, uri)
                retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } catch (_: Exception) {
                null
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {
                }
            }
        }

        private fun calculateInSampleSize(
            options: BitmapFactory.Options,
            reqWidth: Int,
            reqHeight: Int
        ): Int {
            var inSampleSize = 1
            val height = options.outHeight
            val width = options.outWidth
            if (height <= 0 || width <= 0) return inSampleSize
            while ((height / inSampleSize) > reqHeight || (width / inSampleSize) > reqWidth) {
                inSampleSize *= 2
            }
            return maxOf(1, inSampleSize)
        }

        private fun parseMediaData(entry: DelMessageStore.DeletedBackupEntry): ParsedMediaData? {
            if (TextUtils.isEmpty(entry.messageMediaJson)) return null
            return try {
                val mediaObj = JSONObject(entry.messageMediaJson!!)
                var mimeType = sanitizeText(mediaObj.optString("mime_type", ""))
                if (TextUtils.isEmpty(mimeType)) {
                    mimeType = sanitizeText(mediaObj.optString("mimetype", ""))
                }
                if (mimeType == null) mimeType = "application/octet-stream"
                val filePath = sanitizeText(mediaObj.optString("file_path", null))
                val messageUrl = resolveMessageUrl(mediaObj)
                mimeType = mimeType.lowercase(Locale.US)
                if (mimeType == "application/octet-stream") {
                    val inferred = inferMimeFromPath(firstNonEmpty(filePath, messageUrl))
                    if (!TextUtils.isEmpty(inferred)) mimeType = inferred!!
                }
                val mediaKeyHex = extractMediaKeyHex(mediaObj)
                val localUri = resolveLocalMediaUri(filePath)
                ParsedMediaData(mediaObj, mimeType, filePath, messageUrl, mediaKeyHex, localUri)
            } catch (_: Exception) {
                null
            }
        }

        private fun resolveBestMediaUri(mediaData: ParsedMediaData, allowDownload: Boolean): Uri? {
            val preferRemote = mediaData.mimeType.startsWith("audio/")

            if (!preferRemote && mediaData.localUri != null && isUsableUri(mediaData.localUri)) {
                return mediaData.localUri
            }

            val cacheFile = getCacheFile(mediaData)
            if (cacheFile.exists() && cacheFile.length() > 0) {
                return Uri.fromFile(cacheFile)
            }

            if (!allowDownload || TextUtils.isEmpty(mediaData.messageUrl)) return null

            return try {
                val downloaded = downloadPreviewFile(
                    mediaData.messageUrl!!,
                    mediaData.mimeType,
                    mediaData.mediaKeyHex
                )
                if (downloaded.exists() && downloaded.length() > 0) {
                    Uri.fromFile(downloaded)
                } else {
                    if (mediaData.localUri != null && isUsableUri(mediaData.localUri)) mediaData.localUri else null
                }
            } catch (e: Exception) {
                logError(e)
                if (mediaData.localUri != null && isUsableUri(mediaData.localUri)) mediaData.localUri else null
            }
        }

        private fun resolveMessageUrl(mediaObj: JSONObject): String? {
            val messageUrl = sanitizeText(mediaObj.optString("message_url", null))
            if (!TextUtils.isEmpty(messageUrl)) return messageUrl

            val directPath = sanitizeText(mediaObj.optString("direct_path", null))
            if (!TextUtils.isEmpty(directPath)) {
                return if (directPath!!.startsWith("http://") || directPath.startsWith("https://")) {
                    directPath
                } else {
                    val path = if (!directPath.startsWith("/")) "/$directPath" else directPath
                    "https://mmg.whatsapp.net$path"
                }
            }
            return sanitizeText(mediaObj.optString("url", null))
        }

        private fun inferMimeFromPath(path: String?): String? {
            val normalized = sanitizeText(path)?.lowercase(Locale.US) ?: return null
            return when {
                normalized.contains(".opus") || normalized.contains(".ogg") ||
                        normalized.contains(".mp3") || normalized.contains(".aac") ||
                        normalized.contains(".wav") || normalized.contains(".m4a") -> "audio/ogg"

                normalized.contains(".mp4") || normalized.contains(".3gp") ||
                        normalized.contains(".webm") -> "video/mp4"

                normalized.contains(".jpg") || normalized.contains(".jpeg") ||
                        normalized.contains(".png") || normalized.contains(".webp") ||
                        normalized.contains(".gif") -> "image/jpeg"

                else -> null
            }
        }

        private fun isUsableUri(uri: Uri): Boolean {
            return try {
                when {
                    "file".equals(uri.scheme, ignoreCase = true) -> {
                        val file = File(uri.path ?: "")
                        file.exists() && file.length() > 0
                    }

                    "content".equals(uri.scheme, ignoreCase = true) -> {
                        activity.contentResolver.openInputStream(uri)?.use { true } ?: false
                    }

                    else -> true
                }
            } catch (_: Exception) {
                false
            }
        }

        private fun resolveLocalMediaUri(filePath: String?): Uri? {
            if (TextUtils.isEmpty(filePath)) return null
            return try {
                if (filePath!!.startsWith("content://") || filePath.startsWith("file://")) {
                    val uri = Uri.parse(filePath)
                    if ("file".equals(uri.scheme, ignoreCase = true)) {
                        val file = File(uri.path ?: "")
                        if (!file.exists()) return null
                    }
                    uri
                } else {
                    val mediaDirs = Utils.application.externalMediaDirs
                    val appName = Utils.application.packageManager
                        .getApplicationLabel(Utils.application.applicationInfo)
                    if (mediaDirs.isEmpty()) return null
                    val rootDir = File(mediaDirs[0], appName.toString())
                    val file = File(rootDir, filePath)
                    if (file.exists()) Uri.fromFile(file) else null
                }
            } catch (_: Exception) {
                null
            }
        }

        private fun getCacheFile(mediaData: ParsedMediaData): File {
            val extension = inferFileExtension(mediaData.mimeType)
            var keyBase = firstNonEmpty(mediaData.messageUrl, mediaData.filePath)
            if (TextUtils.isEmpty(keyBase)) keyBase = mediaData.mimeType
            val cacheName = "media_${sha256(keyBase + "|" + mediaData.mediaKeyHex)}$extension"
            return File(getMediaCacheDir(), cacheName)
        }

        private fun getMediaCacheDir(): File {
            if (mediaCacheDir != null) return mediaCacheDir!!
            return File(Utils.application.cacheDir, "wae_deleted_media").also {
                if (!it.exists()) it.mkdirs()
                mediaCacheDir = it
            }
        }

        private fun sha256(input: String): String {
            return try {
                val digest = MessageDigest.getInstance("SHA-256")
                bytesToHex(digest.digest(input.toByteArray()))
            } catch (_: NoSuchAlgorithmException) {
                input.hashCode().toString()
            }
        }

        private fun trimThumbCacheLocked(maxEntries: Int) {
            if (mediaThumbCache.size <= maxEntries) return
            val keys = mediaThumbCache.keys.sorted()
            val removeCount = mediaThumbCache.size - maxEntries
            for (i in 0 until minOf(removeCount, keys.size)) {
                mediaThumbCache.remove(keys[i])
            }
        }

        private fun pruneMediaCache() {
            try {
                val dir = getMediaCacheDir()
                val files = dir.listFiles() ?: return
                if (files.size <= 140) return
                files.sortBy { it.lastModified() }
                val toDelete = files.size - 120
                for (i in 0 until toDelete) {
                    try {
                        files[i].delete()
                    } catch (_: Exception) {
                    }
                }
            } catch (_: Exception) {
            }
        }

        fun destroy() {
            attached = false
            DelMessageStore.removeBackupListener(backupListener)
            releaseAllAudio()
            root = null
            chatListAdapter = null
            messageAdapter = null
            allThreads.clear()
            visibleThreads.clear()
            currentThread = null
            contactNameCache.clear()
            avatarCache.clear()
            synchronized(mediaCacheLock) {
                mediaThumbCache.clear()
                mediaUriCache.clear()
                mediaMimeCache.clear()
                loadingThumbIds.clear()
                loadingMediaIds.clear()
                failedMediaIds.clear()
            }
        }

        // --- Helpers ---

        private fun findThreadByKey(chatKey: String?): ChatThread? {
            return allThreads.find { it.chatKey == chatKey }
        }

        private fun getMessageSortTimestamp(entry: DelMessageStore.DeletedBackupEntry): Long {
            return if (entry.messageTimestamp > 0) entry.messageTimestamp else entry.deletedAt
        }

        private fun isGroupRaw(rawJid: String?): Boolean {
            val normalized = normalizeRawJid(rawJid)
            return normalized != null && normalized.endsWith("@g.us")
        }

        private fun normalizeRawJid(rawJid: String?): String? {
            if (rawJid == null) return null
            val normalized = rawJid.trim().lowercase(Locale.US)
            if (normalized.isEmpty()) return null
            return normalized.replaceFirst("\\\\.[\\\\d:]+@".toRegex(), "@")
        }

        private fun normalizeText(text: String?): String {
            return text?.trim()?.lowercase(Locale.US) ?: ""
        }
    }


    // -------- Data classes --------


    private data class ParsedMediaData(
        val mediaObj: JSONObject,
        val mimeType: String,
        val filePath: String?,
        val messageUrl: String?,
        val mediaKeyHex: String?,
        val localUri: Uri?
    )

    private class ChatThread(
        val chatKey: String,
        val chatRawString: String?,
        val chatPhoneRawString: String?,
        val group: Boolean,
        val messages: ArrayList<DelMessageStore.DeletedBackupEntry> = ArrayList(),
        var lastTimestamp: Long = 0
    ) {
        val lastPreview: String
            get() {
                if (messages.isEmpty()) return ""
                return resolveMessageText(messages.last())
            }
    }

    // -------- MediaResolver interface --------

    private interface MediaResolver {
        fun resolveMimeType(entry: DelMessageStore.DeletedBackupEntry): String?
        fun getThumbnail(entryId: Long): Bitmap?
        fun requestThumbnail(entry: DelMessageStore.DeletedBackupEntry, onReady: Runnable)
        fun getCachedMediaUri(entry: DelMessageStore.DeletedBackupEntry): Uri?
        fun requestMediaUri(entry: DelMessageStore.DeletedBackupEntry, onReady: Runnable)
        fun isMediaLoading(entryId: Long): Boolean
        fun isMediaUnavailable(entryId: Long): Boolean
    }
}
