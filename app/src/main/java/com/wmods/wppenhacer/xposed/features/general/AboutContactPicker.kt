package com.wmods.wppenhacer.xposed.features.general

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.model.ContactData
import com.wmods.wppenhacer.model.ContactPickerResult
import com.wmods.wppenhacer.utils.WhatsAppContactPickerLauncher
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp
import com.wmods.wppenhacer.xposed.core.components.WaContactWpp
import com.wmods.wppenhacer.xposed.utils.DesignUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.util.Collections
import java.util.Locale

class AboutContactPicker(loader: ClassLoader, preferences: XSharedPreferences) :
    Feature(loader, preferences) {

    override fun doHook() {
        val aboutClass = WppCore.getAboutActivityClass(classLoader)

        XposedHelpers.findAndHookMethod(
            Activity::class.java,
            "onCreate",
            Bundle::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject as Activity
                    if (!isTargetAboutActivity(
                            activity,
                            aboutClass
                        ) || !isPickerLaunch(activity.intent)
                    ) {
                        return
                    }
                    val controller = getOrCreateController(activity)
                    controller.bindIntent(activity.intent)
                    controller.attach()
                }
            })

        XposedHelpers.findAndHookMethod(Activity::class.java, "onResume", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val activity = param.thisObject as Activity
                if (!isTargetAboutActivity(
                        activity,
                        aboutClass
                    ) || !isPickerLaunch(activity.intent)
                ) {
                    return
                }
                val controller = getOrCreateController(activity)
                controller.bindIntent(activity.intent)
                activity.window.decorView.post { controller.attach() }
            }
        })

        XposedHelpers.findAndHookMethod(
            Activity::class.java,
            "onNewIntent",
            Intent::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject as Activity
                    if (!isTargetAboutActivity(activity, aboutClass)) {
                        return
                    }
                    val intent = param.args[0] as Intent
                    if (!isPickerLaunch(intent)) {
                        return
                    }
                    activity.intent = intent
                    val controller = getOrCreateController(activity)
                    controller.bindIntent(intent)
                    controller.attach()
                    controller.reloadItems()
                }
            })

        XposedHelpers.findAndHookMethod(
            Activity::class.java,
            "onDestroy",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject as Activity
                    if (!isTargetAboutActivity(activity, aboutClass)) {
                        return
                    }
                    val controller = getController(activity)
                    controller?.destroy()
                    XposedHelpers.removeAdditionalInstanceField(activity, FIELD_CONTROLLER)
                }
            })

        XposedHelpers.findAndHookMethod(
            Activity::class.java,
            "onBackPressed",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject as Activity
                    if (!isTargetAboutActivity(activity, aboutClass)) {
                        return
                    }
                    val controller = getController(activity)
                    if (controller != null && controller.handleBackPressed()) {
                        param.result = null
                    }
                }
            })

        XposedHelpers.findAndHookMethod(
            Activity::class.java,
            "onCreateOptionsMenu",
            Menu::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject as Activity
                    if (!isTargetAboutActivity(
                            activity,
                            aboutClass
                        ) || !isPickerLaunch(activity.intent)
                    ) {
                        return
                    }
                    (param.args[0] as Menu).clear()
                    param.result = true
                }
            })

        XposedHelpers.findAndHookMethod(
            Activity::class.java,
            "onOptionsItemSelected",
            MenuItem::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject as Activity
                    if (!isTargetAboutActivity(
                            activity,
                            aboutClass
                        ) || !isPickerLaunch(activity.intent)
                    ) {
                        return
                    }
                    param.result = true
                }
            })
    }

    private fun getController(activity: Activity): PickerController? {
        return XposedHelpers.getAdditionalInstanceField(
            activity,
            FIELD_CONTROLLER
        ) as? PickerController
    }

    private fun getOrCreateController(activity: Activity): PickerController {
        var current = getController(activity)
        if (current != null) {
            return current
        }
        current = PickerController(activity)
        XposedHelpers.setAdditionalInstanceField(activity, FIELD_CONTROLLER, current)
        return current
    }

    private fun isPickerLaunch(intent: Intent?): Boolean {
        return intent?.getBooleanExtra(
            WhatsAppContactPickerLauncher.EXTRA_PICKER_MODE,
            false
        ) == true
    }

    private fun isTargetAboutActivity(activity: Activity, aboutClass: Class<*>): Boolean {
        return aboutClass.isAssignableFrom(activity.javaClass)
    }

    override fun getPluginName(): String {
        return "About Contact Picker"
    }

    companion object {
        private const val FIELD_CONTROLLER = "wae_contact_picker_controller"
        private const val EXTRA_PICKER_RESULTS = "picker_contacts"
    }

    private class PickerController(val activity: Activity) {

        private val mainHandler = Handler(Looper.getMainLooper())
        private val allItems = ArrayList<ContactPickerItem>()
        private val visibleItems = ArrayList<ContactPickerItem>()
        private val selectedJids = LinkedHashSet<String>()
        private val avatarCache = HashMap<String, Drawable>()
        private val avatarLoading = Collections.synchronizedSet(HashSet<String>())

        private var rootView: FrameLayout? = null
        private var contentView: LinearLayout? = null
        private var titleView: TextView? = null
        private var subtitleView: TextView? = null
        private var searchInput: EditText? = null
        private var swipeRefreshLayout: SwipeRefreshLayout? = null
        private lateinit var emptyView: TextView
        private lateinit var adapter: ContactPickerAdapter
        private var filterRunnable: Runnable? = null
        private var resultKey: String? = null
        private var filterMode = FilterMode.ALL
        private var attached = false
        private var loading = false

        fun bindIntent(intent: Intent?) {
            if (intent == null) return
            resultKey = intent.getStringExtra("key")
            selectedJids.clear()
            intent.getStringArrayListExtra("contacts")?.let {
                selectedJids.addAll(it)
            }
        }

        fun attach() {
            if (activity.isFinishing || activity.isDestroyed) {
                return
            }

            if (rootView == null) {
                buildRoot()
            }

            attached = true
            activity.setContentView(rootView)

            rootView?.post {
                checkAndApplyPadding()
            }

            updateActionBarTitle()
            if (allItems.isEmpty() && !loading) {
                reloadItems()
            } else {
                applyFilters()
            }
        }

        private fun checkAndApplyPadding() {
            val root = rootView ?: return
            val content = contentView ?: return

            val location = IntArray(2)
            root.getLocationOnScreen(location)
            val viewY = location[1]

            if (viewY > 0) {
                content.setPadding(0, 0, 0, 0)
                return
            }
            val statusBarHeight = getSystemDimen("status_bar_height", 24f)
            val navBarHeight = getSystemDimen("navigation_bar_height", 0f)
            content.setPadding(0, statusBarHeight, 0, navBarHeight)
        }

        @SuppressLint("InternalInsetResource", "DiscouragedApi")
        private fun getSystemDimen(name: String, fallbackDp: Float): Int {
            val resourceId = activity.resources.getIdentifier(name, "dimen", "android")
            return if (resourceId > 0) {
                activity.resources.getDimensionPixelSize(resourceId)
            } else {
                Utils.dipToPixels(fallbackDp)
            }
        }

        private fun buildRoot() {
            rootView = FrameLayout(activity).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(DesignUtils.getPrimarySurfaceColor())
            }

            contentView = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(DesignUtils.getPrimarySurfaceColor())
            }

            contentView?.let { content ->
                content.addView(buildHeader())
                content.addView(buildSearchField())
                content.addView(buildFilters())

                swipeRefreshLayout = SwipeRefreshLayout(activity).apply {
                    setColorSchemeColors(DesignUtils.getUnSeenColor())
                    setOnRefreshListener { reloadItems() }
                }

                adapter = ContactPickerAdapter(this)
                val listView = ListView(activity).apply {
                    setBackgroundColor(Color.TRANSPARENT)
                    divider = null
                    dividerHeight = 0
                    this.adapter = this@PickerController.adapter
                }

                swipeRefreshLayout?.addView(
                    listView, ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )

                emptyView = TextView(activity).apply {
                    gravity = Gravity.CENTER
                    setTextColor(DesignUtils.getPrimaryTextColor())
                    textSize = 15f
                    setText(R.string.picker_loading_contacts)
                    val padding = Utils.dipToPixels(24f)
                    setPadding(padding, padding, padding, padding)
                }

                val listContainer = FrameLayout(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
                    )
                    addView(
                        swipeRefreshLayout, FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                    addView(
                        emptyView, FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                }

                content.addView(listContainer)
                rootView?.addView(content)
            }
        }

        private fun buildHeader(): View {
            val header = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(
                    Utils.dipToPixels(14f), Utils.dipToPixels(14f),
                    Utils.dipToPixels(14f), Utils.dipToPixels(10f)
                )
            }

            val cancelView = buildHeaderAction(R.string.cancel).apply {
                setOnClickListener { finishCancelled() }
            }

            val titleContainer = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(Utils.dipToPixels(12f), 0, Utils.dipToPixels(12f), 0)
            }

            titleView = TextView(activity).apply {
                setText(R.string.select_contacts)
                setTextColor(DesignUtils.getPrimaryTextColor())
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
            }

            subtitleView = TextView(activity).apply {
                setTextColor(0xFF8A8A8A.toInt())
                textSize = 12f
            }

            titleContainer.addView(titleView)
            titleContainer.addView(subtitleView)

            val doneView = buildHeaderAction(R.string.yes).apply {
                typeface = Typeface.DEFAULT_BOLD
                setOnClickListener { submitSelection() }
            }

            header.addView(cancelView)
            header.addView(titleContainer)
            header.addView(doneView)

            return header
        }

        private fun buildHeaderAction(textRes: Int): TextView {
            return TextView(activity).apply {
                setText(textRes)
                setTextColor(DesignUtils.getUnSeenColor())
                textSize = 15f
                val padding = Utils.dipToPixels(8f)
                setPadding(padding, padding, padding, padding)
            }
        }

        private fun buildSearchField(): View {
            searchInput = EditText(activity).apply {
                isSingleLine = true
                setHint(R.string.search_contacts)
                setTextColor(DesignUtils.getPrimaryTextColor())
                setHintTextColor(0xFF9A9A9A.toInt())
                setPadding(
                    Utils.dipToPixels(14f), Utils.dipToPixels(12f),
                    Utils.dipToPixels(14f), Utils.dipToPixels(12f)
                )

                val bg = GradientDrawable().apply {
                    cornerRadius = Utils.dipToPixels(16f).toFloat()
                    setColor(if (DesignUtils.isNightMode()) 0xFF182229.toInt() else 0xFFFFFFFF.toInt())
                }
                background = bg

                activity.getDrawable(R.drawable.ic_search)?.let { searchDrawable ->
                    searchDrawable.setTint(DesignUtils.getPrimaryTextColor())
                    setCompoundDrawablesRelativeWithIntrinsicBounds(
                        searchDrawable,
                        null,
                        null,
                        null
                    )
                    compoundDrawablePadding = Utils.dipToPixels(10f)
                }

                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(
                        s: CharSequence?,
                        start: Int,
                        count: Int,
                        after: Int
                    ) {
                    }

                    override fun onTextChanged(
                        s: CharSequence?,
                        start: Int,
                        before: Int,
                        count: Int
                    ) {
                        scheduleFilterApply()
                    }

                    override fun afterTextChanged(s: Editable?) {}
                })

                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(
                        Utils.dipToPixels(14f),
                        0,
                        Utils.dipToPixels(14f),
                        Utils.dipToPixels(10f)
                    )
                }
            }
            return searchInput!!
        }

        private fun buildFilters(): View {
            val wrapper = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(Utils.dipToPixels(14f), 0, Utils.dipToPixels(14f), Utils.dipToPixels(8f))
            }

            val scrollView = HorizontalScrollView(activity).apply {
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val filters = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(buildFilterChip(FilterMode.ALL, R.string.mode_all))
                addView(buildFilterChip(FilterMode.CONTACTS, R.string.picker_contacts))
                addView(buildFilterChip(FilterMode.GROUPS, R.string.groups))
            }
            scrollView.addView(filters)

            val selectAllView = TextView(activity).apply {
                setText(R.string.select_all)
                setTextColor(DesignUtils.getUnSeenColor())
                typeface = Typeface.DEFAULT_BOLD
                setPadding(Utils.dipToPixels(12f), Utils.dipToPixels(8f), 0, Utils.dipToPixels(8f))
                setOnClickListener { selectAllVisible() }
            }

            wrapper.addView(scrollView)
            wrapper.addView(selectAllView)
            return wrapper
        }

        private fun buildFilterChip(mode: FilterMode, textRes: Int): TextView {
            return TextView(activity).apply {
                tag = mode
                setText(textRes)
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(
                    Utils.dipToPixels(14f), Utils.dipToPixels(8f),
                    Utils.dipToPixels(14f), Utils.dipToPixels(8f)
                )

                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = Utils.dipToPixels(8f)
                }

                setOnClickListener { v ->
                    filterMode = mode
                    updateFilterChips(v.parent as? ViewGroup)
                    applyFilters()
                }
                updateFilterChipStyle(this, mode == filterMode)
            }
        }

        private fun updateFilterChips(container: ViewGroup?) {
            if (container == null) return
            for (i in 0 until container.childCount) {
                val child = container.getChildAt(i)
                if (child is TextView) {
                    updateFilterChipStyle(child, child.tag == filterMode)
                }
            }
        }

        private fun updateFilterChipStyle(chip: TextView, selected: Boolean) {
            val bg = GradientDrawable().apply {
                cornerRadius = Utils.dipToPixels(18f).toFloat()
                setStroke(
                    Utils.dipToPixels(1f),
                    if (selected) DesignUtils.getUnSeenColor() else 0x1F888888
                )
                setColor(if (selected) (DesignUtils.getUnSeenColor() and 0x22FFFFFF) else Color.TRANSPARENT)
            }
            chip.background = bg
            chip.setTextColor(if (selected) DesignUtils.getUnSeenColor() else DesignUtils.getPrimaryTextColor())
        }

        private fun scheduleFilterApply() {
            filterRunnable?.let { mainHandler.removeCallbacks(it) }
            filterRunnable = Runnable { applyFilters() }
            mainHandler.postDelayed(filterRunnable!!, 160L)
        }

        fun reloadItems() {
            if (!attached || activity.isFinishing || activity.isDestroyed) {
                stopRefreshing()
                return
            }
            loading = true
            emptyView.setText(R.string.picker_loading_contacts)
            emptyView.visibility = View.VISIBLE
            swipeRefreshLayout?.isRefreshing = true

            val preservedSelection = LinkedHashSet(selectedJids)
            Utils.getExecutor().execute {
                try {
                    val loadedItems = ContactPickerDataProvider.loadPickerItems(
                        activity,
                        ArrayList(preservedSelection)
                    )
                    mainHandler.post {
                        if (activity.isFinishing || activity.isDestroyed) {
                            stopRefreshing()
                            return@post
                        }
                        allItems.clear()
                        allItems.addAll(loadedItems)
                        selectedJids.clear()
                        for (item in loadedItems) {
                            if (preservedSelection.contains(item.jid)) {
                                selectedJids.add(item.jid)
                            }
                        }
                        applyFilters()
                        stopRefreshing()
                    }
                } catch (throwable: Throwable) {
                    XposedBridge.log(throwable)
                    mainHandler.post {
                        emptyView.setText(R.string.picker_no_results)
                        stopRefreshing()
                    }
                }
            }
        }

        private fun stopRefreshing() {
            loading = false
            swipeRefreshLayout?.isRefreshing = false
        }

        private fun applyFilters() {
            visibleItems.clear()
            val query = normalize(searchInput?.text?.toString())

            for (item in allItems) {
                if (filterMode == FilterMode.CONTACTS && item.type != ContactType.CONTACT) continue
                if (filterMode == FilterMode.GROUPS && item.type != ContactType.GROUP) continue

                if (query.isNotEmpty()) {
                    val displayName = normalize(item.displayName)
                    val waName = normalize(item.waName)
                    val jid = normalize(item.jid)
                    if (!displayName.contains(query) && !waName.contains(query) && !jid.contains(
                            query
                        )
                    ) {
                        continue
                    }
                }
                visibleItems.add(item)
            }

            visibleItems.sortWith { left, right ->
                java.lang.Boolean.compare(
                    selectedJids.contains(right.jid),
                    selectedJids.contains(left.jid)
                )
            }

            adapter.submit(visibleItems)
            emptyView.visibility = if (visibleItems.isEmpty()) View.VISIBLE else View.GONE
            if (visibleItems.isEmpty()) {
                emptyView.setText(if (loading) R.string.picker_loading_contacts else R.string.picker_no_results)
            }
            updateActionBarTitle()
        }

        private fun updateActionBarTitle() {
            if (titleView == null || subtitleView == null) return

            activity.title = activity.getString(R.string.select_contacts)
            titleView?.setText(R.string.select_contacts)

            if (selectedJids.isEmpty()) {
                subtitleView?.setText(R.string.no_contacts_selected)
            } else {
                subtitleView?.text =
                    activity.getString(R.string.contact_were_selected, selectedJids.size)
            }
        }

        fun toggleSelection(item: ContactPickerItem) {
            if (selectedJids.contains(item.jid)) {
                selectedJids.remove(item.jid)
            } else {
                selectedJids.add(item.jid)
            }
            applyFilters()
            updateActionBarTitle()
        }

        private fun selectAllVisible() {
            for (item in visibleItems) {
                selectedJids.add(item.jid)
            }
            applyFilters()
            updateActionBarTitle()
        }

        fun isSelected(item: ContactPickerItem): Boolean {
            return selectedJids.contains(item.jid)
        }

        fun getAvatar(item: ContactPickerItem): Drawable {
            val cached = avatarCache[item.jid]
            if (cached != null) {
                return cached
            }

            val placeholder = createAvatarPlaceholder(item)
            if (avatarLoading.add(item.jid)) {
                Utils.getExecutor().execute {
                    val drawable = loadAvatarDrawable(item)
                    if (drawable != null) {
                        avatarCache[item.jid] = drawable
                        mainHandler.post { adapter.notifyDataSetChanged() }
                    }
                    avatarLoading.remove(item.jid)
                }
            }
            return placeholder
        }

        private fun loadAvatarDrawable(item: ContactPickerItem): Drawable? {
            return try {
                val userJid = FMessageWpp.UserJid(item.jid)
                val waContact = WaContactWpp.getWaContactFromJid(userJid) ?: return null

                waContact.getProfilePhoto(false)?.use { inputStream ->
                    val bitmap = BitmapFactory.decodeStream(inputStream) ?: return null
                    RoundedBitmapDrawableFactory.create(activity.resources, bitmap).apply {
                        isCircular = true
                    }
                }
            } catch (throwable: Throwable) {
                XposedBridge.log(throwable)
                null
            }
        }

        private fun createAvatarPlaceholder(item: ContactPickerItem): Drawable {
            return GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (item.type == ContactType.GROUP) 0xFF6C8F7F.toInt() else 0xFF7C8DA6.toInt())
            }
        }

        private fun submitSelection() {
            val orderedResults = ArrayList<ContactPickerResult>()
            val contacts = ArrayList<String>()
            val legacyContacts = ArrayList<ContactData>()

            for (item in allItems) {
                if (!selectedJids.contains(item.jid)) continue

                val result = ContactPickerResult(item.jid, item.displayName)
                orderedResults.add(result)
                contacts.add(item.jid)
                legacyContacts.add(result.toContactData())
            }

            val intent = Intent().apply {
                putStringArrayListExtra("contacts", contacts)
                putExtra("contacts_data", legacyContacts)
                putExtra(EXTRA_PICKER_RESULTS, orderedResults)
                if (resultKey != null) {
                    putExtra("key", resultKey)
                }
            }

            activity.setResult(Activity.RESULT_OK, intent)
            activity.finish()
        }

        private fun finishCancelled() {
            activity.setResult(Activity.RESULT_CANCELED)
            activity.finish()
        }

        fun handleBackPressed(): Boolean {
            finishCancelled()
            return true
        }

        fun destroy() {
            attached = false
            filterRunnable?.let { mainHandler.removeCallbacks(it) }
        }

        private fun normalize(value: String?): String {
            return value?.trim()?.lowercase(Locale.ROOT) ?: ""
        }
    }

    private enum class FilterMode {
        ALL, CONTACTS, GROUPS
    }

    private enum class ContactType {
        CONTACT, GROUP
    }

    private data class ContactPickerItem(
        val jid: String,
        val displayName: String,
        val waName: String,
        val type: ContactType
    ) {
        fun typeLabel(activity: Activity): String {
            return if (type == ContactType.GROUP) {
                activity.getString(R.string.picker_group)
            } else {
                activity.getString(R.string.picker_contact)
            }
        }
    }

    private class ContactPickerAdapter(private val controller: PickerController) : BaseAdapter() {

        private val items = ArrayList<ContactPickerItem>()

        @SuppressLint("NotifyDataSetChanged")
        fun submit(newItems: List<ContactPickerItem>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun getCount(): Int = items.size

        override fun getItem(position: Int): ContactPickerItem = items[position]

        override fun getItemId(position: Int): Long {
            return items[position].jid.hashCode().toLong()
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val holder: ViewHolder
            val view: View

            if (convertView == null) {
                val context = parent.context

                val root = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(
                        Utils.dipToPixels(14f), Utils.dipToPixels(12f),
                        Utils.dipToPixels(14f), Utils.dipToPixels(12f)
                    )
                }

                val avatarView = ImageView(context).apply {
                    layoutParams =
                        LinearLayout.LayoutParams(Utils.dipToPixels(44f), Utils.dipToPixels(44f))
                }

                val content = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams =
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                            setMargins(Utils.dipToPixels(12f), 0, Utils.dipToPixels(12f), 0)
                        }
                }

                val title = TextView(context).apply {
                    setTextColor(DesignUtils.getPrimaryTextColor())
                    textSize = 16f
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }

                val waName = TextView(context).apply {
                    setTextColor(0xFF8A8A8A.toInt())
                    textSize = 13f
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }

                val type = TextView(context).apply {
                    setTextColor(0xFF6A6A6A.toInt())
                    textSize = 12f
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }

                content.addView(title)
                content.addView(waName)
                content.addView(type)

                val selectionView = ImageView(context).apply {
                    layoutParams =
                        LinearLayout.LayoutParams(Utils.dipToPixels(24f), Utils.dipToPixels(24f))
                }

                root.addView(avatarView)
                root.addView(content)
                root.addView(selectionView)

                holder = ViewHolder(root, avatarView, title, waName, type, selectionView)
                view = root
                view.tag = holder
            } else {
                view = convertView
                holder = view.tag as ViewHolder
            }

            val item = items[position]
            holder.titleView.text = item.displayName
            holder.waNameView.text = item.waName
            holder.typeView.text = item.typeLabel(controller.activity)
            holder.avatarView.setImageDrawable(controller.getAvatar(item))
            holder.selectionView.setImageDrawable(
                createSelectionDrawable(
                    controller.activity,
                    controller.isSelected(item)
                )
            )
            holder.itemView.setOnClickListener { controller.toggleSelection(item) }

            return view
        }

        private fun createSelectionDrawable(activity: Activity, checked: Boolean): Drawable {
            if (checked) {
                activity.getDrawable(R.drawable.ic_round_check_circle_24)?.let { drawable ->
                    drawable.setTint(DesignUtils.getUnSeenColor())
                    return drawable
                }
            }
            return GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke(
                    Utils.dipToPixels(2f),
                    if (checked) DesignUtils.getUnSeenColor() else 0x66888888
                )
                setColor(Color.TRANSPARENT)
            }
        }

        class ViewHolder(
            val itemView: View,
            val avatarView: ImageView,
            val titleView: TextView,
            val waNameView: TextView,
            val typeView: TextView,
            val selectionView: ImageView
        )
    }

    private object ContactPickerDataProvider {

        fun loadPickerItems(
            activity: Activity,
            pinnedJids: List<String>
        ): ArrayList<ContactPickerItem> {
            val items = LinkedHashMap<String, ContactPickerItem>()
            val database = WppCore.getWaDatabase()

            if (database != null) {
                loadFromWaDatabase(items, database)
            }

            for (jid in pinnedJids) {
                if (TextUtils.isEmpty(jid) || items.containsKey(jid) || !isSupportedPickerJid(jid)) {
                    continue
                }
                items[jid] = buildItem(jid)
            }

            return ArrayList(items.values)
        }

        private fun loadFromWaDatabase(
            items: LinkedHashMap<String, ContactPickerItem>,
            database: SQLiteDatabase
        ) {
            try {
                database.query(
                    "wa_contacts",
                    arrayOf("jid", "display_name"),
                    "jid IS NOT NULL AND jid != '' AND (jid LIKE '%@s.whatsapp.net' OR jid LIKE '%@g.us') AND (jid LIKE '%g.us' OR is_contact_synced is not NULL) AND is_whatsapp_user = 1",
                    null, null, null,
                    "display_name COLLATE NOCASE ASC, jid COLLATE NOCASE ASC"
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val jid = cursor.getString(0)
                        if (TextUtils.isEmpty(jid) || items.containsKey(jid) || !isSupportedPickerJid(
                                jid
                            )
                        ) {
                            continue
                        }
                        items[jid] = buildItem(jid, cursor.getString(1))
                    }
                }
            } catch (throwable: Throwable) {
                XposedBridge.log(throwable)
            }
        }

        private fun buildItem(jid: String, fallbackDisplayName: String? = null): ContactPickerItem {
            val type = if (jid.endsWith("@g.us")) ContactType.GROUP else ContactType.CONTACT
            val userJid = FMessageWpp.UserJid(jid)

            var displayName: String? = null
            var waName: String? = null

            try {
                WaContactWpp.getWaContactFromJid(userJid)?.let { waContact ->
                    displayName = sanitize(waContact.displayName)
                    waName = sanitize(waContact.waName)
                }
            } catch (throwable: Throwable) {
                XposedBridge.log(throwable)
            }

            return ContactPickerItem(jid, displayName ?: "", waName ?: "", type)
        }

        private fun isSupportedPickerJid(jid: String): Boolean {
            if (!jid.contains("@")) return false
            if (jid.endsWith("@broadcast") || jid.endsWith("@newsletter") || jid.startsWith("status@")) return false
            return jid.endsWith("@g.us") || jid.endsWith("@s.whatsapp.net") || jid.endsWith("@lid") || jid.contains(
                "@lid"
            )
        }

        private fun localPart(jid: String): String {
            val index = jid.indexOf('@')
            return if (index > 0) jid.substring(0, index) else jid
        }

        private fun sanitize(value: String?): String? {
            val trimmed = value?.trim()
            return if (trimmed.isNullOrEmpty()) null else trimmed
        }
    }
}