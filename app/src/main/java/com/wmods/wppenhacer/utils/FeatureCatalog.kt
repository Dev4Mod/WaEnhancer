package com.wmods.wppenhacer.utils

import android.content.Context
import com.wmods.wppenhacer.R
import com.wmods.wppenhacer.model.SearchableFeature
import java.util.ArrayList
import java.util.Locale

/** Central catalog of searchable features in the WaEnhancer app. */
object FeatureCatalog {
    private var features: List<SearchableFeature>? = null

    @JvmStatic
    fun getAllFeatures(context: Context): List<SearchableFeature> {
        if (features == null) features = buildFeatureCatalog(context)
        return features!!
    }

    @JvmStatic
    fun search(context: Context, query: String?): List<SearchableFeature> {
        if (query.isNullOrBlank()) return ArrayList()
        return getAllFeatures(context).filter { it.matches(query) }
    }

    private fun buildFeatureCatalog(context: Context): List<SearchableFeature> {
        val catalog = ArrayList<SearchableFeature>()
        val general = SearchableFeature.FragmentType.GENERAL
        val privacy = SearchableFeature.FragmentType.PRIVACY
        val media = SearchableFeature.FragmentType.MEDIA
        val customization = SearchableFeature.FragmentType.CUSTOMIZATION
        val home = SearchableFeature.FragmentType.HOME

        // General preferences.
        add(catalog, context, "thememode", R.string.theme_mode, R.string.theme_mode_sum,
            SearchableFeature.Category.GENERAL_HOME, general, "general_home", "dark", "light", "theme")
        add(catalog, context, "update_check", R.string.update_check, R.string.update_check_sum,
            SearchableFeature.Category.GENERAL_HOME, general, "general_home", "update", "check", "automatic")
        add(catalog, context, "disable_expiration", R.string.disable_whatsapp_expiration,
            R.string.disable_whatsapp_expiration_sum, SearchableFeature.Category.GENERAL_HOME,
            general, "general_home", "expiration", "version")
        add(catalog, context, "force_restore_backup_feature", R.string.force_restore_backup,
            R.string.force_restore_backup_summary, SearchableFeature.Category.GENERAL_HOME,
            general, "general_home", "backup", "restore", "force")
        add(catalog, context, "force_english", R.string.force_english, null,
            SearchableFeature.Category.GENERAL_HOME, general, "general_home", "english", "language")
        add(catalog, context, "enablelogs", R.string.verbose_logs, null,
            SearchableFeature.Category.GENERAL_HOME, general, "general_home", "logs", "debug", "verbose")
        add(catalog, context, "bypass_version_check", R.string.disable_version_check,
            R.string.disable_version_check_sum, SearchableFeature.Category.GENERAL_HOME,
            general, "general_home", "version", "check", "bypass")
        add(catalog, context, "bootloader_spoofer", R.string.bootloader_spoofer,
            R.string.bootloader_spoofer_sum, SearchableFeature.Category.GENERAL_HOME,
            general, "general_home", "bootloader", "spoofer", "ban")
        add(catalog, context, "ampm", R.string.ampm, null,
            SearchableFeature.Category.GENERAL_HOME, general, "general_home", "time", "12", "hour", "format")
        add(catalog, context, "segundos", R.string.segundosnahora, R.string.segundosnahora_sum,
            SearchableFeature.Category.GENERAL_HOME, general, "general_home", "seconds", "timestamp", "time")
        add(catalog, context, "secondstotime", R.string.textonahora, R.string.textonahora_sum,
            SearchableFeature.Category.GENERAL_HOME, general, "general_home", "text", "timestamp", "custom")
        add(catalog, context, "tasker", R.string.enable_tasker_automation,
            R.string.enable_tasker_automation_sum, SearchableFeature.Category.GENERAL_HOME,
            general, "general_home", "tasker", "automation", "intent")

        // Home screen preferences.
        add(catalog, context, "buttonaction", R.string.show_menu_buttons_as_icons,
            R.string.show_menu_buttons_as_icons_sum, SearchableFeature.Category.GENERAL_HOMESCREEN,
            general, "homescreen", "menu", "icons", "buttons")
        add(catalog, context, "shownamehome", R.string.showname, R.string.showname_sum,
            SearchableFeature.Category.GENERAL_HOMESCREEN, general, "homescreen", "name", "profile", "title")
        add(catalog, context, "showbiohome", R.string.showbio, R.string.showbio_sum,
            SearchableFeature.Category.GENERAL_HOMESCREEN, general, "homescreen", "bio", "status", "toolbar")
        add(catalog, context, "show_dndmode", R.string.show_dnd_button, R.string.show_dnd_button_sum,
            SearchableFeature.Category.GENERAL_HOMESCREEN, general, "homescreen", "dnd", "do not disturb", "button")
        add(catalog, context, "newchat", R.string.enable_new_chat_button,
            R.string.enable_new_chat_button_sum, SearchableFeature.Category.GENERAL_HOMESCREEN,
            general, "homescreen", "new", "chat", "button")
        add(catalog, context, "restartbutton", R.string.enable_restart_button,
            R.string.enable_restart_button_sum, SearchableFeature.Category.GENERAL_HOMESCREEN,
            general, "homescreen", "restart", "reboot", "button")
        add(catalog, context, "open_wae", R.string.enable_wa_enhancer_button,
            R.string.enable_wa_enhancer_button_sum, SearchableFeature.Category.GENERAL_HOMESCREEN,
            general, "homescreen", "wa enhancer", "open", "button")
        add(catalog, context, "separategroups", R.string.separate_groups, R.string.separate_groups_sum,
            SearchableFeature.Category.GENERAL_HOMESCREEN, general, "homescreen", "separate", "groups", "filter")
        add(catalog, context, "filtergroups", R.string.new_ui_group_filter, R.string.new_ui_group_filter_sum,
            SearchableFeature.Category.GENERAL_HOMESCREEN, general, "homescreen", "filter", "groups", "ui")
        add(catalog, context, "dotonline", R.string.show_online_dot_in_conversation_list,
            R.string.show_online_dot_in_conversation_list_sum, SearchableFeature.Category.GENERAL_HOMESCREEN,
            general, "homescreen", "online", "dot", "green")
        add(catalog, context, "showonlinetext", R.string.show_online_last_seen_in_conversation_list,
            R.string.show_online_last_seen_in_conversation_list_sum, SearchableFeature.Category.GENERAL_HOMESCREEN,
            general, "homescreen", "online", "last seen", "text")
        add(catalog, context, "filterseen", R.string.enable_filter_chats, R.string.enable_filter_chats_sum,
            SearchableFeature.Category.GENERAL_HOMESCREEN, general, "homescreen", "filter", "chats", "unseen")
        add(catalog, context, "metaai", R.string.disable_metaai, R.string.disable_metaai_sum,
            SearchableFeature.Category.GENERAL_HOMESCREEN, general, "homescreen", "meta", "ai", "disable")
        add(catalog, context, "chatfilter", R.string.novofiltro, R.string.novofiltro_sum,
            SearchableFeature.Category.GENERAL_HOMESCREEN, general, "homescreen", "search", "filter", "icon", "bar")
        add(catalog, context, "disable_profile_status", R.string.disable_status_in_the_profile_photo,
            R.string.disable_status_in_the_profile_photo_sum, SearchableFeature.Category.GENERAL_HOMESCREEN,
            general, "homescreen", "status", "profile", "photo", "circle")

        // Conversation preferences.
        add(catalog, context, "showonline", R.string.show_toast_on_contact_online,
            R.string.show_toast_on_contact_online_sum, SearchableFeature.Category.GENERAL_CONVERSATION,
            general, "conversation", "toast", "online", "notification")
        add(catalog, context, "toastdeleted", R.string.toast_on_delete, R.string.toast_on_delete_sum,
            SearchableFeature.Category.GENERAL_CONVERSATION, general, "conversation", "toast", "deleted", "notification")
        add(catalog, context, "toast_viewed_message", R.string.toast_on_viewed_message,
            R.string.toast_on_viewed_message_sum, SearchableFeature.Category.GENERAL_CONVERSATION,
            general, "conversation", "toast", "viewed", "read", "notification")
        add(catalog, context, "antirevoke", R.string.antirevoke, R.string.antirevoke_sum,
            SearchableFeature.Category.GENERAL_CONVERSATION, general, "conversation", "anti", "revoke", "delete", "deleted")
        add(catalog, context, "antirevokestatus", R.string.antirevokestatus,
            R.string.antirevokestatus_sum, SearchableFeature.Category.GENERAL_CONVERSATION,
            general, "conversation", "anti", "delete", "status")
        add(catalog, context, "antidisappearing", R.string.antidisappearing,
            R.string.antidisappearing_sum, SearchableFeature.Category.GENERAL_CONVERSATION,
            general, "conversation", "anti", "disappearing", "temporary", "messages")
        add(catalog, context, "broadcast_tag", R.string.show_chat_broadcast_icon,
            R.string.show_chat_broadcast_icon_sum, SearchableFeature.Category.GENERAL_CONVERSATION,
            general, "conversation", "broadcast", "icon", "tag")
        add(catalog, context, "pinnedlimit", R.string.disable_pinned_limit, R.string.disable_pinned_limit_sum,
            SearchableFeature.Category.GENERAL_CONVERSATION, general, "conversation", "pinned", "limit", "chats")
        add(catalog, context, "removeforwardlimit", R.string.removeforwardlimit,
            R.string.removeforwardlimit_sum, SearchableFeature.Category.GENERAL_CONVERSATION,
            general, "conversation", "forward", "limit", "remove")
        add(catalog, context, "hidetag", R.string.hidetag, R.string.hidetag_sum,
            SearchableFeature.Category.GENERAL_CONVERSATION, general, "conversation", "forwarded", "tag", "hide")
        add(catalog, context, "revokeallmessages", R.string.delete_for_everyone_all_messages,
            R.string.delete_for_everyone_all_messages_sum, SearchableFeature.Category.GENERAL_CONVERSATION,
            general, "conversation", "delete", "everyone", "limit", "revoke")
        add(catalog, context, "removeseemore", R.string.remove_see_more_button,
            R.string.remove_see_more_button_, SearchableFeature.Category.GENERAL_CONVERSATION,
            general, "conversation", "see more", "button", "remove")
        add(catalog, context, "antieditmessages", R.string.show_edited_message_history,
            R.string.show_edited_message_history_sum, SearchableFeature.Category.GENERAL_CONVERSATION,
            general, "conversation", "edited", "history", "message")
        add(catalog, context, "alertsticker", R.string.enable_confirmation_to_send_sticker,
            R.string.enable_confirmation_to_send_sticker_sum, SearchableFeature.Category.GENERAL_CONVERSATION,
            general, "conversation", "sticker", "confirmation", "alert")
        add(catalog, context, "calltype", R.string.selection_of_call_type,
            R.string.selection_of_call_type_sum, SearchableFeature.Category.GENERAL_CONVERSATION,
            general, "conversation", "call", "type", "selection", "phone")
        add(catalog, context, "disable_defemojis", R.string.disable_default_emojis,
            R.string.disable_default_emojis_sum, SearchableFeature.Category.GENERAL_CONVERSATION,
            general, "conversation", "emoji", "default", "disable")
        add(catalog, context, "stamp_copied_message", R.string.stamp_copied_messages,
            R.string.stamp_copied_messages_sum, SearchableFeature.Category.GENERAL_CONVERSATION,
            general, "conversation", "copy", "stamp", "copied", "messages")
        add(catalog, context, "doubletap2like", R.string.double_click_to_react,
            R.string.double_click_to_like_sum, SearchableFeature.Category.GENERAL_CONVERSATION,
            general, "conversation", "double", "tap", "click", "react", "like")
        add(catalog, context, "doubletap2like_emoji", R.string.custom_reaction,
            R.string.custom_reaction_sum, SearchableFeature.Category.GENERAL_CONVERSATION,
            general, "conversation", "reaction", "emoji", "custom")
        add(catalog, context, "google_translate", R.string.google_translate, R.string.google_translate_sum,
            SearchableFeature.Category.GENERAL_CONVERSATION, general, "conversation", "translate", "google", "language")
        add(catalog, context, "verify_blocked_contact", R.string.show_contact_added_status,
            R.string.show_contact_added_status_sum, SearchableFeature.Category.GENERAL_CONVERSATION,
            general, "conversation", "added", "contact", "verify")

        // Status preferences.
        add(catalog, context, "autonext_status", R.string.disable_auto_status,
            R.string.disable_auto_status_sum, SearchableFeature.Category.GENERAL, general,
            null, "auto", "status", "skip")
        add(catalog, context, "copystatus", R.string.enable_copy_status,
            R.string.enable_copy_status_sum, SearchableFeature.Category.GENERAL, general,
            null, "copy", "status", "caption")
        add(catalog, context, "toast_viewed_status", R.string.toast_on_viewed_status,
            R.string.toast_on_viewed_status_sum, SearchableFeature.Category.GENERAL, general,
            null, "toast", "viewed", "status", "notification")

        addPrivacyFeatures(context, catalog, privacy)
        addMediaFeatures(context, catalog, media)
        addCustomizationFeatures(context, catalog, customization)
        addHomeActions(context, catalog, home)
        return catalog
    }

    private fun addPrivacyFeatures(
        context: Context,
        catalog: MutableList<SearchableFeature>,
        fragment: SearchableFeature.FragmentType
    ) {
        val category = SearchableFeature.Category.PRIVACY
        add(catalog, context, "typearchive", R.string.hide_archived_chat, R.string.hide_archived_chat_sum,
            category, fragment, null, "archive", "hide", "hidden")
        add(catalog, context, "show_freezeLastSeen", R.string.show_freezeLastSeen_button,
            R.string.show_freezeLastSeen_sum, category, fragment, null, "freeze", "last seen", "button")
        add(catalog, context, "ghostmode", R.string.ghost_mode_title, R.string.ghost_mode_sum,
            category, fragment, null, "ghost", "mode", "invisible")
        add(catalog, context, "always_online", R.string.always_online, R.string.always_online_sum,
            category, fragment, null, "always", "online", "status")
        add(catalog, context, "lockedchats_enhancer", R.string.lockedchats_enhancer,
            R.string.lockedchats_enhancer_sum, category, fragment, null, "locked", "chats", "enhanced")
        add(catalog, context, "custom_privacy_type", R.string.custom_privacy_per_contact,
            R.string.custom_privacy_per_contact_sum, category, fragment, null, "custom", "privacy", "contact")
        add(catalog, context, "freezelastseen", R.string.freezelastseen, R.string.freezelastseen_sum,
            category, fragment, null, "freeze", "last seen")
        add(catalog, context, "hideread", R.string.hideread, R.string.hideread_sum,
            category, fragment, null, "hide", "read", "blue", "ticks")
        add(catalog, context, "hide_seen_view", R.string.view_seen_tick, R.string.view_seen_tick_sum,
            category, fragment, null, "view", "seen", "tick")
        add(catalog, context, "blueonreply", R.string.blueonreply, R.string.blueonreply_sum,
            category, fragment, null, "blue", "tick", "reply")
        add(catalog, context, "hideread_group", R.string.hideread_group, R.string.hideread_group_sum,
            category, fragment, null, "hide", "read", "group", "ticks")
        add(catalog, context, "hidereceipt", R.string.hidereceipt, R.string.hidereceipt_sum,
            category, fragment, null, "hide", "delivered", "receipt")
        add(catalog, context, "ghostmode_t", R.string.ghostmode, R.string.ghostmode_sum,
            category, fragment, null, "ghost", "typing", "hide")
        add(catalog, context, "ghostmode_r", R.string.ghostmode_r, R.string.ghostmode_sum_r,
            category, fragment, null, "ghost", "recording", "audio")
        add(catalog, context, "hideonceseen", R.string.hide_once_view_seen,
            R.string.hide_once_view_seen_sum, category, fragment, null, "view", "once", "seen", "hide")
        add(catalog, context, "hideaudioseen", R.string.hide_audio_seen, R.string.hide_audio_seen_sum,
            category, fragment, null, "audio", "seen", "hide")
        add(catalog, context, "viewonce", R.string.viewonce, R.string.viewonce_sum,
            category, fragment, null, "view", "once", "unlimited")
        add(catalog, context, "seentick", R.string.show_button_to_send_blue_tick,
            R.string.show_button_to_send_blue_tick_sum, category, fragment, null,
            "blue", "tick", "button", "mark", "read")
        add(catalog, context, "hidestatusview", R.string.hidestatusview, R.string.hidestatusview_sum,
            category, fragment, null, "status", "view", "hide")
        add(catalog, context, "call_info", R.string.additional_call_information,
            R.string.additional_call_information_sum, category, fragment, null, "call", "information", "additional")
        add(catalog, context, "call_privacy", R.string.call_blocker, R.string.call_blocker_sum,
            category, fragment, null, "call", "blocker", "block")
        add(catalog, context, "call_type", R.string.call_blocking_type, R.string.call_blocking_type_sum,
            category, fragment, null, "call", "blocking", "type")
    }

    private fun addMediaFeatures(
        context: Context,
        catalog: MutableList<SearchableFeature>,
        fragment: SearchableFeature.FragmentType
    ) {
        val category = SearchableFeature.Category.MEDIA
        add(catalog, context, "imagequality", R.string.imagequality, R.string.imagequality_sum,
            category, fragment, null, "image", "quality", "hd")
        add(catalog, context, "download_local", R.string.local_download, null,
            category, fragment, null, "download", "local", "folder")
        add(catalog, context, "downloadstatus", R.string.statusdowload, R.string.statusdowload_sum,
            category, fragment, null, "download", "status", "share")
        add(catalog, context, "downloadviewonce", R.string.downloadviewonce, R.string.downloadviewonce_sum,
            category, fragment, null, "download", "view", "once")
        add(catalog, context, "video_limit_size", R.string.increase_video_size_limit, null,
            category, fragment, null, "video", "size", "limit", "mb")
        add(catalog, context, "videoquality", R.string.videoquality, R.string.videoquality_sum,
            category, fragment, null, "video", "quality", "hd")
        add(catalog, context, "video_real_resolution", R.string.send_video_in_real_resolution,
            R.string.send_video_in_real_resolution_sum, category, fragment, null, "video", "resolution", "real")
        add(catalog, context, "video_maxfps", R.string.send_video_in_60fps,
            R.string.send_video_in_60fps_sum, category, fragment, null, "video", "60fps", "fps")
        add(catalog, context, "call_recording_enable", R.string.call_recording_enable,
            R.string.call_recording_enable_sum, category, fragment, null, "call", "recording", "record")
        add(catalog, context, "call_recording_path", R.string.call_recording_path, null,
            category, fragment, null, "recording", "path", "folder")
        add(catalog, context, "call_recording_toast", R.string.call_recording_toast_title,
            R.string.call_recording_toast_summary, category, fragment, null,
            "recording", "toast", "notification", "show", "hide")
        add(catalog, context, "disable_sensor_proximity", R.string.disable_the_proximity_sensor,
            R.string.disable_the_proximity_sensor_sum, category, fragment, null, "proximity", "sensor", "screen")
        add(catalog, context, "proximity_audios", R.string.disable_audio_sensor,
            R.string.disable_audio_sensor_sum, category, fragment, null, "audio", "proximity", "sensor")
        add(catalog, context, "audio_type", R.string.send_audio_as_voice_audio_note,
            R.string.send_audio_as_voice_audio_note_sum, category, fragment, null, "audio", "voice", "note")
        add(catalog, context, "voicenote_speed", R.string.voice_note_speed, null,
            category, fragment, null, "voice", "note", "speed")
        add(catalog, context, "audio_transcription", R.string.audio_transcription,
            R.string.audio_transcription_sum, category, fragment, null, "audio", "transcription", "text")
        add(catalog, context, "transcription_provider", R.string.transcription_provider,
            R.string.transcription_provider_sum, category, fragment, null, "transcription", "provider", "ai")
        add(catalog, context, "media_preview", R.string.enable_media_preview,
            R.string.enable_media_preview_sum, category, fragment, null, "media", "preview", "temporary")
    }

    private fun addCustomizationFeatures(
        context: Context,
        catalog: MutableList<SearchableFeature>,
        fragment: SearchableFeature.FragmentType
    ) {
        val category = SearchableFeature.Category.CUSTOMIZATION
        add(catalog, context, "changecolor", R.string.colors_customization,
            R.string.colors_customization_sum, category, fragment, null, "colors", "customization", "theme")
        add(catalog, context, "primary_color", R.string.primary_color, null,
            category, fragment, null, "primary", "color")
        add(catalog, context, "background_color", R.string.background_color, null,
            category, fragment, null, "background", "color")
        add(catalog, context, "text_color", R.string.text_color, null,
            category, fragment, null, "text", "color")
        add(catalog, context, "wallpaper", R.string.wallpaper_in_home_screen,
            R.string.wallpaper_in_home_screen_sum, category, fragment, null, "wallpaper", "background", "image")
        add(catalog, context, "hidetabs", R.string.hide_tabs_on_home, R.string.hide_tabs_on_home_sum,
            category, fragment, null, "hide", "tabs", "home")
        add(catalog, context, "custom_filters", R.string.custom_appearance, R.string.custom_filters_sum,
            category, fragment, null, "custom", "appearance", "filters", "css")
        add(catalog, context, "animation_list", R.string.list_animations_home_screen,
            R.string.list_animations_home_screen_sum, category, fragment, null, "animation", "list", "home")
        add(catalog, context, "admin_grp", R.string.show_admin_group_icon,
            R.string.show_admin_group_icon_sum, category, fragment, null, "admin", "group", "icon")
        add(catalog, context, "floatingmenu", R.string.new_context_menu_ui,
            R.string.new_context_menu_ui_sum, category, fragment, null, "floating", "menu", "context", "ios")
        add(catalog, context, "animation_emojis", R.string.animation_emojis,
            R.string.animation_emojis_sum, category, fragment, null, "animation", "emojis", "large")
        add(catalog, context, "bubble_color", R.string.change_bubble_colors,
            R.string.change_blubble_color_sum, category, fragment, null, "bubble", "color", "chat")
        add(catalog, context, "menuwicon", R.string.menuwicon, R.string.menuwicon_sum,
            category, fragment, null, "menu", "icons")
        add(catalog, context, "novaconfig", R.string.novaconfig, R.string.novaconfig_sum,
            category, fragment, null, "settings", "style", "profile")
        add(catalog, context, "igstatus", R.string.igstatus_on_home_screen,
            R.string.igstatus_on_home_screen_sum, category, fragment, null, "instagram", "status", "ig")
        add(catalog, context, "channels", R.string.disable_channels, R.string.disable_channels_sum,
            category, fragment, null, "channels", "disable", "hide")
        add(catalog, context, "removechannel_rec", R.string.remove_channel_recomendations,
            R.string.remove_channel_recomendations_sum, category, fragment, null, "channel", "recommendations", "remove")
        add(catalog, context, "status_style", R.string.style_of_stories_status,
            R.string.style_of_stories_status_sum, category, fragment, null, "status", "style", "stories")
        add(catalog, context, "oldstatus", R.string.old_statuses, R.string.old_statuses_sum,
            category, fragment, null, "old", "status", "vertical")
        add(catalog, context, "statuscomposer", R.string.custom_colors_for_text_status,
            R.string.custom_colors_for_text_status_sum, category, fragment, null, "status", "composer", "colors", "text")
    }

    private fun addHomeActions(
        context: Context,
        catalog: MutableList<SearchableFeature>,
        fragment: SearchableFeature.FragmentType
    ) {
        val category = SearchableFeature.Category.HOME_ACTIONS
        add(catalog, context, "export_config", R.string.export_settings, R.string.backup_settings,
            category, fragment, null, "export", "backup", "settings", "config")
        add(catalog, context, "import_config", R.string.import_settings, R.string.backup_settings,
            category, fragment, null, "import", "restore", "settings", "config")
        add(catalog, context, "reset_config", R.string.reset_settings, null,
            category, fragment, null, "reset", "settings", "clear")
        add(catalog, context, "reboot_wpp", R.string.restart_whatsapp, null,
            category, fragment, null, "restart", "reboot", "whatsapp", "refresh")
    }

    private fun add(
        catalog: MutableList<SearchableFeature>,
        context: Context,
        key: String,
        titleResource: Int,
        summaryResource: Int?,
        category: SearchableFeature.Category,
        fragment: SearchableFeature.FragmentType,
        parentKey: String?,
        vararg tags: String
    ) {
        catalog.add(
            SearchableFeature(
                key,
                context.getString(titleResource),
                summaryResource?.let { context.getString(it) },
                category,
                fragment,
                parentKey,
                tags.toList()
            )
        )
    }
}
