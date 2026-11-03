package com.wmods.wppenhacer.xposed.features.others

import android.app.Activity
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.highcapable.yukihookapi.extension.kavaref.hook
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.core.WppCore
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator
import com.wmods.wppenhacer.xposed.features.listeners.ConversationItemListener
import com.wmods.wppenhacer.xposed.features.others.translation.TranslationSettings
import com.wmods.wppenhacer.xposed.utils.ReflectionUtils
import com.wmods.wppenhacer.xposed.utils.Utils
import com.wmods.wppenhacer.xposed.utils.YukiLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.WeakHashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class GoogleTranslateOutgoing(
    loader: ClassLoader,
    preferences: SharedPreferences,
    private val translate: (String?, String, String, String?) -> CompletableFuture<String?>,
    private val getProvider: (() -> com.wmods.wppenhacer.xposed.features.others.translation.TranslationProvider)? = null,
    private val translateWithResult: ((String?, String, String, Boolean, String?) -> CompletableFuture<com.wmods.wppenhacer.xposed.features.others.translation.TranslationResult>)? = null
) : Feature(loader, preferences) {

    private val main = Handler(Looper.getMainLooper())
    // Module settings (read-only from WA): just for reading google_translate toggle
    private val moduleSettings get() = xprefs
    // WhatsApp writable settings: per-chat outgoing config, cached originals
    private val waSettings get() = WppCore.getPrivPrefs()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val renderedViews = WeakHashMap<TextView, String>()
    private val isTranslating = AtomicBoolean(false)
    
    private companion object {
        const val TAG = "[WaeOutTranslate]"
        const val PREF_PREFIX = "gtrans_out_orig_"
        const val MAX_STORED_ORIGINALS = 200
        const val TRANSLATION_TIMEOUT_SECONDS = 5L
        const val MENU_ITEM_ID = 0x7fff0001 // Unique ID for our menu item
    }
    
    private fun keyOutgoing(chat: String?) = "google_translation_outgoing_v1_" + (chat ?: "default")
    private fun keyIncludeOriginal() = "google_translation_include_original"
    
    private fun configOutgoing(chat: String?): GoogleTranslationConfigOutgoing {
        val local = chat?.let { waSettings.getString(keyOutgoing(it), null) }
        val global = waSettings.getString(keyOutgoing(null), null)
        return GoogleTranslateSettingsOutgoing.decode(local) 
            ?: GoogleTranslateSettingsOutgoing.decode(global) 
            ?: GoogleTranslationConfigOutgoing()
    }
    
    private fun includeOriginal(): Boolean {
        return waSettings.getBoolean(keyIncludeOriginal(), true)
    }
    
    private fun storeOriginalText(messageId: String, original: String) {
        scope.launch {
            try {
                val editor = waSettings.edit()
                val key = PREF_PREFIX + messageId
                editor.putString(key, original)
                
                val allKeys = waSettings.all.keys.filter { it.startsWith(PREF_PREFIX) }
                if (allKeys.size >= MAX_STORED_ORIGINALS) {
                    allKeys.sorted().take(allKeys.size - MAX_STORED_ORIGINALS + 1).forEach {
                        editor.remove(it)
                    }
                }
                
                editor.apply()
            } catch (e: Exception) {
                YukiLog.log("$TAG ERROR: Failed to store original: ${e.message}")
            }
        }
    }
    
    private fun getOriginalText(messageId: String): String? {
        return try {
            waSettings.getString(PREF_PREFIX + messageId, null)
        } catch (e: Exception) {
            YukiLog.log("$TAG ERROR: Failed to retrieve original: ${e.message}")
            null
        }
    }
    
    private fun rawJid(jid: Any?): String? = runCatching {
        GoogleTranslateSettings.normalizeChatId(
            ReflectionUtils.callMethod(jid, "getRawString") as? String
        )
    }.getOrNull()

    private fun chatId(jid: Any?): String? {
        val raw = rawJid(jid) ?: return null
        if (raw.endsWith("@g.us")) return raw
        return rawJid(WppCore.getPhoneJidFromUserJid(jid)) ?: raw
    }

    override fun doHook() {
        if (!moduleSettings.getBoolean("google_translate", false)) return
        
        hookConversationMenu()
        installOutgoingRendering()
    }

    private fun hookConversationMenu() {
        YukiLog.log("$TAG Hooking conversation menu for translate button")
        
        val onCreateMenuConversationMethod = Unobfuscator.loadOnCreatedMenuConversation(classLoader)
        
        // onCreate - add the menu item and set visibility
        onCreateMenuConversationMethod.hook {
            after {
                try {
                    val menu = args[0] as Menu
                    
                    // Remove existing item to refresh visibility
                    val existingItem = menu.findItem(MENU_ITEM_ID)
                    if (existingItem != null) {
                        menu.removeItem(MENU_ITEM_ID)
                    }
                    
                    YukiLog.log("$TAG Adding translate menu item to conversation toolbar")
                    
                    // Get provider badge from actual provider
                    val providerBadge = try {
                        getProvider?.invoke()?.getBadge() ?: "T"
                    } catch (e: Exception) {
                        YukiLog.log("$TAG Error getting provider badge: ${e.message}")
                        "T"
                    }
                    
                    val menuItem = menu.add(0, MENU_ITEM_ID, 0, "Translate")
                    menuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                    
                    // Use text badge showing provider
                    menuItem.setTitle(providerBadge)
                    YukiLog.log("$TAG Menu item showing badge: $providerBadge")
                    
                    menuItem.setOnMenuItemClickListener {
                        YukiLog.log("$TAG Translate menu item clicked")
                        handleTranslateClick()
                        true
                    }
                    
                    // Set visibility based on current chat settings
                    updateMenuItemVisibility(menuItem)
                    
                    YukiLog.log("$TAG Translate menu item added successfully")
                    
                } catch (e: Exception) {
                    YukiLog.log("$TAG ERROR: Failed to add menu item: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }
    
    private fun updateMenuItemVisibility(menuItem: MenuItem) {
        try {
            // Check global master toggle
            if (!moduleSettings.getBoolean("google_translate", false)) {
                YukiLog.log("$TAG Master toggle disabled, hiding menu item")
                menuItem.isVisible = false
                return
            }
            
            // Get current chat ID
            val currentJid = WppCore.getCurrentUserJid()
            val chat = currentJid?.let { chatId(it.userJid) }
            
            if (chat == null) {
                YukiLog.log("$TAG No chat ID, hiding menu item")
                menuItem.isVisible = false
                return
            }
            
            // Check if translation is enabled for this chat
            val config = configOutgoing(chat)
            val enabled = config.enabled
            
            YukiLog.log("$TAG Chat $chat: translation enabled=$enabled")
            menuItem.isVisible = enabled
            
        } catch (e: Exception) {
            YukiLog.log("$TAG ERROR updating visibility: ${e.message}")
            menuItem.isVisible = false
        }
    }

    private fun handleTranslateClick() {
        if (!isTranslating.compareAndSet(false, true)) {
            YukiLog.log("$TAG Already translating, ignoring click")
            return
        }
        
        try {
            // Get current activity
            val activity = WppCore.getCurrentActivity()
            if (activity == null) {
                YukiLog.log("$TAG ERROR: No current activity")
                isTranslating.set(false)
                Utils.showToast("Error: No active conversation", Toast.LENGTH_SHORT)
                return
            }
            
            // Find entry EditText at click time
            val entryEditText = activity.findViewById<EditText>(Utils.getID("entry", "id"))
            if (entryEditText == null) {
                YukiLog.log("$TAG ERROR: Entry EditText not found")
                isTranslating.set(false)
                Utils.showToast("Error: Could not find message input", Toast.LENGTH_SHORT)
                return
            }
            
            var originalText = entryEditText.text?.toString()
            if (originalText.isNullOrBlank()) {
                YukiLog.log("$TAG Empty text")
                isTranslating.set(false)
                Utils.showToast("Enter a message first", Toast.LENGTH_SHORT)
                return
            }
            
            // Check if text already has our format (user tapped T twice)
            // Format: "translation\n\n_original_" or multi-line italics
            originalText = extractOriginalFromFormatted(originalText)
            
            // Trim whitespace including NBSP and zero-width characters
            originalText = cleanText(originalText)
            
            if (originalText.isBlank()) {
                YukiLog.log("$TAG Empty text after cleaning")
                isTranslating.set(false)
                Utils.showToast("Enter a message first", Toast.LENGTH_SHORT)
                return
            }
            
            if (originalText.length > 4000) {
                YukiLog.log("$TAG Text too long (${originalText.length} chars)")
                isTranslating.set(false)
                Utils.showToast("Message too long to translate", Toast.LENGTH_SHORT)
                return
            }
            
            // Get target language
            val currentJid = WppCore.getCurrentUserJid()
            val chat = currentJid?.let { chatId(it.userJid) }
            val chatJid = currentJid?.userJid as? String
            val config = configOutgoing(chat)
            
            val targetLang = config.target
            if (targetLang.isNullOrBlank()) {
                YukiLog.log("$TAG No target language configured")
                isTranslating.set(false)
                Utils.showToast("Please set a target language in translation settings", Toast.LENGTH_SHORT)
                return
            }
            
            YukiLog.log("$TAG Starting translation: \"${originalText.take(50)}...\" -> $targetLang")
            
            // Show progress
            Utils.showToast("Translating...", Toast.LENGTH_SHORT)
            
            // Translate on background thread
            scope.launch {
                try {
                    val startTime = System.currentTimeMillis()
                    val translated = translate(originalText, "auto", targetLang, chatJid)
                        .get(TRANSLATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    val duration = System.currentTimeMillis() - startTime
                    
                    main.post {
                        try {
                            if (translated.isNullOrBlank()) {
                                YukiLog.log("$TAG Translation returned empty after ${duration}ms")
                                Utils.showToast("Translation failed", Toast.LENGTH_SHORT)
                                return@post
                            }
                            
                            YukiLog.log("$TAG Translation success in ${duration}ms: \"${translated.take(50)}...\"")
                            
                            // Format message: translation, blank line, original in italics
                            val formattedText = if (includeOriginal()) {
                                formatTranslatedMessage(translated, originalText)
                            } else {
                                cleanText(translated)
                            }
                            
                            // Set formatted text
                            entryEditText.setText(formattedText)
                            entryEditText.setSelection(formattedText.length)
                            
                            YukiLog.log("$TAG Text formatted and set in EditText (includeOriginal=${includeOriginal()})")
                            
                        } catch (e: Exception) {
                            YukiLog.log("$TAG ERROR in success handler: ${e.message}")
                            Utils.showToast("Translation failed", Toast.LENGTH_SHORT)
                        } finally {
                            isTranslating.set(false)
                        }
                    }
                    
                } catch (e: Exception) {
                    YukiLog.log("$TAG ERROR: Translation failed: ${e.javaClass.simpleName}: ${e.message}")
                    
                    main.post {
                        try {
                            Utils.showToast("Translation failed: ${e.message}", Toast.LENGTH_SHORT)
                        } catch (ex: Exception) {
                            YukiLog.log("$TAG ERROR in failure handler: ${ex.message}")
                        } finally {
                            isTranslating.set(false)
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            YukiLog.log("$TAG ERROR in handleTranslateClick: ${e.message}")
            e.printStackTrace()
            Utils.showToast("Translation error", Toast.LENGTH_SHORT)
            isTranslating.set(false)
        }
    }

    private fun cleanText(text: String): String {
        // Remove trailing/leading whitespace including NBSP (\u00A0) and zero-width characters
        return text.trim()
            .replace("\u00A0", " ")  // NBSP to space
            .replace("\u200B", "")   // Zero-width space
            .replace("\u200C", "")   // Zero-width non-joiner
            .replace("\u200D", "")   // Zero-width joiner
            .replace("\uFEFF", "")   // Zero-width no-break space
            .trim()
    }
    
    private fun extractOriginalFromFormatted(text: String): String {
        // Check if text has our format: "translation\n\n_original_"
        // Look for \n\n followed by underscore patterns
        val lines = text.split("\n")
        
        // Find the blank line separator
        var blankLineIndex = -1
        for (i in 0 until lines.size - 1) {
            if (lines[i].isEmpty() && i < lines.size - 1) {
                // Check if lines after blank are italicized
                val nextLine = lines[i + 1]
                if (nextLine.startsWith("_") && nextLine.endsWith("_")) {
                    blankLineIndex = i
                    break
                }
            }
        }
        
        if (blankLineIndex >= 0) {
            // Extract original part (after blank line)
            val originalLines = lines.subList(blankLineIndex + 1, lines.size)
            return originalLines.joinToString("\n") { line ->
                // Remove surrounding underscores
                if (line.startsWith("_") && line.endsWith("_") && line.length > 2) {
                    line.substring(1, line.length - 1)
                } else {
                    line
                }
            }
        }
        
        return text
    }
    
    private fun formatTranslatedMessage(translated: String, original: String): String {
        val cleanTranslated = cleanText(translated)
        val cleanOriginal = cleanText(original)
        
        // Split original into lines and italicize each non-empty line
        val italicLines = cleanOriginal.split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n") { "_${it}_" }
        
        return buildString {
            append(cleanTranslated)
            if (italicLines.isNotEmpty()) {
                append("\n\n")
                append(italicLines)
            }
        }
    }
    
    private fun installOutgoingRendering() {
        ConversationItemListener.conversationListeners.add(object :
            ConversationItemListener.OnConversationItemListener() {
            override fun onItemBind(
                fMessage: FMessageWpp,
                view: ViewGroup,
                position: Int,
                convertView: android.view.View?
            ) {
                if (!fMessage.key.isFromMe) return
                
                val messageId = fMessage.key.messageID
                val original = getOriginalText(messageId) ?: return
                
                val textView = findMessageTextView(view, fMessage.messageStr ?: return) ?: return
                
                if (renderedViews[textView] == messageId) return
                
                val translated = fMessage.messageStr ?: return
                if (original.trim() == translated.trim()) return
                
                val output = SpannableStringBuilder(original)
                    .append("\n\n")
                    .append("Sent as\n")
                    .append(translated)
                
                renderedViews[textView] = messageId
                textView.text = output
            }
        })
    }

    private fun findMessageTextView(root: ViewGroup, text: String): TextView? {
        val messageTextId = Utils.getID("message_text", "id")
        root.findViewById<TextView>(messageTextId)?.let { 
            if (it.text.toString() == text || renderedViews[it] != null) return it 
        }
        
        fun findTextViewRecursive(view: android.view.View): TextView? {
            if (view is TextView && view !is android.widget.EditText) {
                val viewText = view.text.toString()
                if (viewText == text || renderedViews[view] != null) {
                    return view
                }
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    findTextViewRecursive(view.getChildAt(i))?.let { return it }
                }
            }
            return null
        }
        
        return findTextViewRecursive(root)
    }

    override fun getPluginName() = "Google Translate Outgoing"
}
