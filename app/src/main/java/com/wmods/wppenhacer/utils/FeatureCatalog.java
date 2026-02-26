package com.wmods.wppenhacer.utils;

import android.content.Context;

import com.wmods.wppenhacer.R;
import com.wmods.wppenhacer.model.SearchableFeature;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Central catalog of all searchable features in the WaEnhancer app.
 * This class builds and maintains a complete index of all features from
 * preference XMLs.
 */
public class FeatureCatalog {

    private static List<SearchableFeature> features;

    /**
     * Initialize and return the complete feature catalog.
     */
    public static List<SearchableFeature> getAllFeatures(Context context) {
        if (features == null) {
            features = buildFeatureCatalog(context);
        }
        return features;
    }

    /**
     * Search features by query string.
     * Returns all features that match the search query.
     */
    public static List<SearchableFeature> search(Context context, String query) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>();
        }

        return getAllFeatures(context).stream()
                .filter(feature -> feature.matches(query))
                .collect(Collectors.toList());
    }

    /**
     * Build the complete feature catalog from all preference XMLs.
     */
    private static List<SearchableFeature> buildFeatureCatalog(Context context) {
        List<SearchableFeature> catalog = new ArrayList<>();

        // GENERAL FRAGMENT - General sub-preferences
        catalog.add(new SearchableFeature("thememode",
                context.getString(R.string.theme_mode),
                context.getString(R.string.theme_mode_sum),
                SearchableFeature.Category.GENERAL_HOME,
                SearchableFeature.FragmentType.GENERAL,
                "general_home",
                Arrays.asList("dark", "light", "theme")));

        catalog.add(new SearchableFeature("update_check",
                context.getString(R.string.update_check),
                context.getString(R.string.update_check_sum),
                SearchableFeature.Category.GENERAL_HOME,
                SearchableFeature.FragmentType.GENERAL,
                "general_home",
                Arrays.asList("update", "check", "automatic")));

        catalog.add(new SearchableFeature("disable_expiration",
                context.getString(R.string.disable_whatsapp_expiration),
                context.getString(R.string.disable_whatsapp_expiration_sum),
                SearchableFeature.Category.GENERAL_HOME,
                SearchableFeature.FragmentType.GENERAL,
                "general_home",
                Arrays.asList("expiration", "version")));

        catalog.add(new SearchableFeature("force_restore_backup_feature",
                context.getString(R.string.force_restore_backup),
                context.getString(R.string.force_restore_backup_summary),
                SearchableFeature.Category.GENERAL_HOME,
                SearchableFeature.FragmentType.GENERAL,
                "general_home",
                Arrays.asList("backup", "restore", "force")));

        catalog.add(new SearchableFeature("disable_ads",
                context.getString(R.string.disable_ads),
                context.getString(R.string.disable_ads_sum),
                SearchableFeature.Category.GENERAL_HOME,
                SearchableFeature.FragmentType.GENERAL,
                "general_home",
                Arrays.asList("ads", "advertising", "block")));

        catalog.add(new SearchableFeature("lite_mode",
                context.getString(R.string.lite_mode),
                context.getString(R.string.lite_mode_sum),
                SearchableFeature.Category.GENERAL_HOME,
                SearchableFeature.FragmentType.GENERAL,
                "general_home",
                Arrays.asList("lite", "performance", "battery")));

        catalog.add(new SearchableFeature("force_english",
                context.getString(R.string.force_english),
                null,
                SearchableFeature.Category.GENERAL_HOME,
                SearchableFeature.FragmentType.GENERAL,
                "general_home",
                Arrays.asList("english", "language")));

        catalog.add(new SearchableFeature("enablelogs",
                context.getString(R.string.verbose_logs),
                null,
                SearchableFeature.Category.GENERAL_HOME,
                SearchableFeature.FragmentType.GENERAL,
                "general_home",
                Arrays.asList("logs", "debug", "verbose")));

        catalog.add(new SearchableFeature("bypass_version_check",
                context.getString(R.string.disable_version_check),
                context.getString(R.string.disable_version_check_sum),
                SearchableFeature.Category.GENERAL_HOME,
                SearchableFeature.FragmentType.GENERAL,
                "general_home",
                Arrays.asList("version", "check", "bypass")));

        catalog.add(new SearchableFeature("bootloader_spoofer",
                context.getString(R.string.bootloader_spoofer),
                context.getString(R.string.bootloader_spoofer_sum),
                SearchableFeature.Category.GENERAL_HOME,
                SearchableFeature.FragmentType.GENERAL,
                "general_home",
                Arrays.asList("bootloader", "spoofer", "ban")));

        catalog.add(new SearchableFeature("ampm",
                context.getString(R.string.ampm),
                null,
                SearchableFeature.Category.GENERAL_HOME,
                SearchableFeature.FragmentType.GENERAL,
                "general_home",
                Arrays.asList("time", "12", "hour", "format")));

        catalog.add(new SearchableFeature("segundos",
                context.getString(R.string.segundosnahora),
                context.getString(R.string.segundosnahora_sum),
                SearchableFeature.Category.GENERAL_HOME,
                SearchableFeature.FragmentType.GENERAL,
                "general_home",
                Arrays.asList("seconds", "timestamp", "time")));

        catalog.add(new SearchableFeature("secondstotime",
                context.getString(R.string.textonahora),
                context.getString(R.string.textonahora_sum),
                SearchableFeature.Category.GENERAL_HOME,
                SearchableFeature.FragmentType.GENERAL,
                "general_home",
                Arrays.asList("text", "timestamp", "custom")));

        catalog.add(new SearchableFeature("tasker",
                context.getString(R.string.enable_tasker_automation),
                context.getString(R.string.enable_tasker_automation_sum),
                SearchableFeature.Category.GENERAL_HOME,
                SearchableFeature.FragmentType.GENERAL,
                "general_home",
                Arrays.asList("tasker", "automation", "intent")));

        // GENERAL FRAGMENT - Homescreen sub-preferences
        catalog.add(new SearchableFeature("buttonaction",
                context.getString(R.string.show_menu_buttons_as_icons),
                context.getString(R.string.show_menu_buttons_as_icons_sum),
                SearchableFeature.Category.GENERAL_HOMESCREEN,
                SearchableFeature.FragmentType.GENERAL,
                "homescreen",
                Arrays.asList("menu", "icons", "buttons")));

        catalog.add(new SearchableFeature("shownamehome",
                context.getString(R.string.showname),
                context.getString(R.string.showname_sum),
                SearchableFeature.Category.GENERAL_HOMESCREEN,
                SearchableFeature.FragmentType.GENERAL,
                "homescreen",
                Arrays.asList("name", "profile", "title")));

        catalog.add(new SearchableFeature("showbiohome",
                context.getString(R.string.showbio),
                context.getString(R.string.showbio_sum),
                SearchableFeature.Category.GENERAL_HOMESCREEN,
                SearchableFeature.FragmentType.GENERAL,
                "homescreen",
                Arrays.asList("bio", "status", "toolbar")));

        catalog.add(new SearchableFeature("show_dndmode",
                context.getString(R.string.show_dnd_button),
                context.getString(R.string.show_dnd_button_sum),
                SearchableFeature.Category.GENERAL_HOMESCREEN,
                SearchableFeature.FragmentType.GENERAL,
                "homescreen",
                Arrays.asList("dnd", "do not disturb", "button")));

        catalog.add(new SearchableFeature("newchat",
                context.getString(R.string.enable_new_chat_button),
                context.getString(R.string.enable_new_chat_button_sum),
                SearchableFeature.Category.GENERAL_HOMESCREEN,
                SearchableFeature.FragmentType.GENERAL,
                "homescreen",
                Arrays.asList("new", "chat", "button")));

        catalog.add(new SearchableFeature("restartbutton",
                context.getString(R.string.enable_restart_button),
                context.getString(R.string.enable_restart_button_sum),
                SearchableFeature.Category.GENERAL_HOMESCREEN,
                SearchableFeature.FragmentType.GENERAL,
                "homescreen",
                Arrays.asList("restart", "reboot", "button")));

        catalog.add(new SearchableFeature("open_wae",
                context.getString(R.string.enable_wa_enhancer_button),
                context.getString(R.string.enable_wa_enhancer_button_sum),
                SearchableFeature.Category.GENERAL_HOMESCREEN,
                SearchableFeature.FragmentType.GENERAL,
                "homescreen",
                Arrays.asList("wa enhancer", "open", "button")));

        catalog.add(new SearchableFeature("separategroups",
                context.getString(R.string.separate_groups),
                context.getString(R.string.separate_groups_sum),
                SearchableFeature.Category.GENERAL_HOMESCREEN,
                SearchableFeature.FragmentType.GENERAL,
                "homescreen",
                Arrays.asList("separate", "groups", "filter")));

        catalog.add(new SearchableFeature("filtergroups",
                context.getString(R.string.new_ui_group_filter),
                context.getString(R.string.new_ui_group_filter_sum),
                SearchableFeature.Category.GENERAL_HOMESCREEN,
                SearchableFeature.FragmentType.GENERAL,
                "homescreen",
                Arrays.asList("filter", "groups", "ui")));

        catalog.add(new SearchableFeature("dotonline",
                context.getString(R.string.show_online_dot_in_conversation_list),
                context.getString(R.string.show_online_dot_in_conversation_list_sum),
                SearchableFeature.Category.GENERAL_HOMESCREEN,
                SearchableFeature.FragmentType.GENERAL,
                "homescreen",
                Arrays.asList("online", "dot", "green")));

        catalog.add(new SearchableFeature("showonlinetext",
                context.getString(R.string.show_online_last_seen_in_conversation_list),
                context.getString(R.string.show_online_last_seen_in_conversation_list_sum),
                SearchableFeature.Category.GENERAL_HOMESCREEN,
                SearchableFeature.FragmentType.GENERAL,
                "homescreen",
                Arrays.asList("online", "last seen", "text")));

        catalog.add(new SearchableFeature("filterseen",
                context.getString(R.string.enable_filter_chats),
                context.getString(R.string.enable_filter_chats_sum),
                SearchableFeature.Category.GENERAL_HOMESCREEN,
                SearchableFeature.FragmentType.GENERAL,
                "homescreen",
                Arrays.asList("filter", "chats", "unseen")));

        catalog.add(new SearchableFeature("metaai",
                context.getString(R.string.disable_metaai),
                context.getString(R.string.disable_metaai_sum),
                SearchableFeature.Category.GENERAL_HOMESCREEN,
                SearchableFeature.FragmentType.GENERAL,
                "homescreen",
                Arrays.asList("meta", "ai", "disable")));

        catalog.add(new SearchableFeature("chatfilter",
                context.getString(R.string.novofiltro),
                context.getString(R.string.novofiltro_sum),
                SearchableFeature.Category.GENERAL_HOMESCREEN,
                SearchableFeature.FragmentType.GENERAL,
                "homescreen",
                Arrays.asList("search", "filter", "icon", "bar")));

        catalog.add(new SearchableFeature("disable_profile_status",
                context.getString(R.string.disable_status_in_the_profile_photo),
                context.getString(R.string.disable_status_in_the_profile_photo_sum),
                SearchableFeature.Category.GENERAL_HOMESCREEN,
                SearchableFeature.FragmentType.GENERAL,
                "homescreen",
                Arrays.asList("status", "profile", "photo", "circle")));

        // GENERAL FRAGMENT - Conversation sub-preferences
        catalog.add(new SearchableFeature("showonline",
                context.getString(R.string.show_toast_on_contact_online),
                context.getString(R.string.show_toast_on_contact_online_sum),
                SearchableFeature.Category.GENERAL_CONVERSATION,
                SearchableFeature.FragmentType.GENERAL,
                "conversation",
                Arrays.asList("toast", "online", "notification")));

        catalog.add(new SearchableFeature("toastdeleted",
                context.getString(R.string.toast_on_delete),
                context.getString(R.string.toast_on_delete_sum),
                SearchableFeature.Category.GENERAL_CONVERSATION,
                SearchableFeature.FragmentType.GENERAL,
                "conversation",
                Arrays.asList("toast", "deleted", "notification")));

        catalog.add(new SearchableFeature("toast_viewed_message",
                context.getString(R.string.toast_on_viewed_message),
                context.getString(R.string.toast_on_viewed_message_sum),
                SearchableFeature.Category.GENERAL_CONVERSATION,
                SearchableFeature.FragmentType.GENERAL,
                "conversation",
                Arrays.asList("toast", "viewed", "read", "notification")));

        catalog.add(new SearchableFeature("antirevoke",
                context.getString(R.string.antirevoke),
                context.getString(R.string.antirevoke_sum),
                SearchableFeature.Category.GENERAL_CONVERSATION,
                SearchableFeature.FragmentType.GENERAL,
                "conversation",
                Arrays.asList("anti", "revoke", "delete", "deleted")));

        catalog.add(new SearchableFeature("antirevokestatus",
                context.getString(R.string.antirevokestatus),
                context.getString(R.string.antirevokestatus_sum),
                SearchableFeature.Category.GENERAL_CONVERSATION,
                SearchableFeature.FragmentType.GENERAL,
                "conversation",
                Arrays.asList("anti", "delete", "status")));

        catalog.add(new SearchableFeature("antidisappearing",
                context.getString(R.string.antidisappearing),
                context.getString(R.string.antidisappearing_sum),
                SearchableFeature.Category.GENERAL_CONVERSATION,
                SearchableFeature.FragmentType.GENERAL,
                "conversation",
                Arrays.asList("anti", "disappearing", "temporary", "messages")));

        catalog.add(new SearchableFeature("broadcast_tag",
                context.getString(R.string.show_chat_broadcast_icon),
                context.getString(R.string.show_chat_broadcast_icon_sum),
                SearchableFeature.Category.GENERAL_CONVERSATION,
                SearchableFeature.FragmentType.GENERAL,
                "conversation",
                Arrays.asList("broadcast", "icon", "tag")));

        catalog.add(new SearchableFeature("pinnedlimit",
                context.getString(R.string.disable_pinned_limit),
                context.getString(R.string.disable_pinned_limit_sum),
                SearchableFeature.Category.GENERAL_CONVERSATION,
                SearchableFeature.FragmentType.GENERAL,
                "conversation",
                Arrays.asList("pinned", "limit", "chats")));

        catalog.add(new SearchableFeature("removeforwardlimit",
                context.getString(R.string.removeforwardlimit),
                context.getString(R.string.removeforwardlimit_sum),
                SearchableFeature.Category.GENERAL_CONVERSATION,
                SearchableFeature.FragmentType.GENERAL,
                "conversation",
                Arrays.asList("forward", "limit", "remove")));

        catalog.add(new SearchableFeature("hidetag",
                context.getString(R.string.hidetag),
                context.getString(R.string.hidetag_sum),
                SearchableFeature.Category.GENERAL_CONVERSATION,
                SearchableFeature.FragmentType.GENERAL,
                "conversation",
                Arrays.asList("forwarded", "tag", "hide")));

        catalog.add(new SearchableFeature("revokeallmessages",
                context.getString(R.string.delete_for_everyone_all_messages),
                context.getString(R.string.delete_for_everyone_all_messages_sum),
                SearchableFeature.Category.GENERAL_CONVERSATION,
                SearchableFeature.FragmentType.GENERAL,
                "conversation",
                Arrays.asList("delete", "everyone", "limit", "revoke")));

        catalog.add(new SearchableFeature("removeseemore",
                context.getString(R.string.remove_see_more_button),
                context.getString(R.string.remove_see_more_button_),
                SearchableFeature.Category.GENERAL_CONVERSATION,
                SearchableFeature.FragmentType.GENERAL,
                "conversation",
                Arrays.asList("see more", "button", "remove")));

        catalog.add(new SearchableFeature("antieditmessages",
                context.getString(R.string.show_edited_message_history),
                context.getString(R.string.show_edited_message_history_sum),
                SearchableFeature.Category.GENERAL_CONVERSATION,
                SearchableFeature.FragmentType.GENERAL,
                "conversation",
                Arrays.asList("edited", "history", "message")));

        catalog.add(new SearchableFeature("alertsticker",
                context.getString(R.string.enable_confirmation_to_send_sticker),
                context.getString(R.string.enable_confirmation_to_send_sticker_sum),
                SearchableFeature.Category.GENERAL_CONVERSATION,
                SearchableFeature.FragmentType.GENERAL,
                "conversation",
                Arrays.asList("sticker", "confirmation", "alert")));

        catalog.add(new SearchableFeature("calltype",
                context.getString(R.string.selection_of_call_type),
                context.getString(R.string.selection_of_call_type_sum),
                SearchableFeature.Category.GENERAL_CONVERSATION,
                SearchableFeature.FragmentType.GENERAL,
                "conversation",
                Arrays.asList("call", "type", "selection", "phone")));

        catalog.add(new SearchableFeature("disable_defemojis",
                context.getString(R.string.disable_default_emojis),
                context.getString(R.string.disable_default_emojis_sum),
                SearchableFeature.Category.GENERAL_CONVERSATION,
                SearchableFeature.FragmentType.GENERAL,
                "conversation",
                Arrays.asList("emoji", "default", "disable")));

        catalog.add(new SearchableFeature("stamp_copied_message",
                context.getString(R.string.stamp_copied_messages),
                context.getString(R.string.stamp_copied_messages_sum),
                SearchableFeature.Category.GENERAL_CONVERSATION,
                SearchableFeature.FragmentType.GENERAL,
                "conversation",
                Arrays.asList("copy", "stamp", "copied", "messages")));

        catalog.add(new SearchableFeature("doubletap2like",
                context.getString(R.string.double_click_to_react),
                context.getString(R.string.double_click_to_like_sum),
                SearchableFeature.Category.GENERAL_CONVERSATION,
                SearchableFeature.FragmentType.GENERAL,
                "conversation",
                Arrays.asList("double", "tap", "click", "react", "like")));

        catalog.add(new SearchableFeature("doubletap2like_emoji",
                context.getString(R.string.custom_reaction),
                context.getString(R.string.custom_reaction_sum),
                SearchableFeature.Category.GENERAL_CONVERSATION,
                SearchableFeature.FragmentType.GENERAL,
                "conversation",
                Arrays.asList("reaction", "emoji", "custom")));

        catalog.add(new SearchableFeature("google_translate",
                context.getString(R.string.google_translate),
                context.getString(R.string.google_translate_sum),
                SearchableFeature.Category.GENERAL_CONVERSATION,
                SearchableFeature.FragmentType.GENERAL,
                "conversation",
                Arrays.asList("translate", "google", "language")));

        catalog.add(new SearchableFeature("deleted_messages_activity",
                context.getString(R.string.deleted_messages_title),
                context.getString(R.string.deleted_messages_sum),
                SearchableFeature.Category.GENERAL_CONVERSATION,
                SearchableFeature.FragmentType.ACTIVITY,
                null,
                Arrays.asList("deleted", "messages", "restore", "history", "log")));

        catalog.add(new SearchableFeature("verify_blocked_contact",
                context.getString(R.string.show_contact_blocked),
                context.getString(R.string.show_contact_blocked_sum),
                SearchableFeature.Category.GENERAL_CONVERSATION,
                SearchableFeature.FragmentType.GENERAL,
                "conversation",
                Arrays.asList("blocked", "contact", "verify")));

        // GENERAL FRAGMENT - Status
        catalog.add(new SearchableFeature("autonext_status",
                context.getString(R.string.disable_auto_status),
                context.getString(R.string.disable_auto_status_sum),
                SearchableFeature.Category.GENERAL,
                SearchableFeature.FragmentType.GENERAL,
                null,
                Arrays.asList("auto", "status", "skip")));

        catalog.add(new SearchableFeature("copystatus",
                context.getString(R.string.enable_copy_status),
                context.getString(R.string.enable_copy_status_sum),
                SearchableFeature.Category.GENERAL,
                SearchableFeature.FragmentType.GENERAL,
                null,
                Arrays.asList("copy", "status", "caption")));

        catalog.add(new SearchableFeature("toast_viewed_status",
                context.getString(R.string.toast_on_viewed_status),
                context.getString(R.string.toast_on_viewed_status_sum),
                SearchableFeature.Category.GENERAL,
                SearchableFeature.FragmentType.GENERAL,
                null,
                Arrays.asList("toast", "viewed", "status", "notification")));

        // PRIVACY FRAGMENT
        catalog.add(new SearchableFeature("typearchive",
                context.getString(R.string.hide_archived_chat),
                context.getString(R.string.hide_archived_chat_sum),
                SearchableFeature.Category.PRIVACY,
                SearchableFeature.FragmentType.PRIVACY,
                null,
                Arrays.asList("archive", "hide", "hidden")));

        catalog.add(new SearchableFeature("show_freezeLastSeen",
                context.getString(R.string.show_freezeLastSeen_button),
                context.getString(R.string.show_freezeLastSeen_sum),
                SearchableFeature.Category.PRIVACY,
                SearchableFeature.FragmentType.PRIVACY,
                null,
                Arrays.asList("freeze", "last seen", "button")));

        catalog.add(new SearchableFeature("ghostmode",
                context.getString(R.string.ghost_mode_title),
                context.getString(R.string.ghost_mode_sum),
                SearchableFeature.Category.PRIVACY,
                SearchableFeature.FragmentType.PRIVACY,
                null,
                Arrays.asList("ghost", "mode", "invisible")));

        catalog.add(new SearchableFeature("always_online",
                context.getString(R.string.always_online),
                context.getString(R.string.always_online_sum),
                SearchableFeature.Category.PRIVACY,
                SearchableFeature.FragmentType.PRIVACY,
                null,
                Arrays.asList("always", "online", "status")));

        catalog.add(new SearchableFeature("lockedchats_enhancer",
                context.getString(R.string.lockedchats_enhancer),
                context.getString(R.string.lockedchats_enhancer_sum),
                SearchableFeature.Category.PRIVACY,
                SearchableFeature.FragmentType.PRIVACY,
                null,
                Arrays.asList("locked", "chats", "enhanced")));

        catalog.add(new SearchableFeature("custom_privacy_type",
                context.getString(R.string.custom_privacy_per_contact),
                context.getString(R.string.custom_privacy_per_contact_sum),
                SearchableFeature.Category.PRIVACY,
                SearchableFeature.FragmentType.PRIVACY,
                null,
                Arrays.asList("custom", "privacy", "contact")));

        catalog.add(new SearchableFeature("freezelastseen",
                context.getString(R.string.freezelastseen),
                context.getString(R.string.freezelastseen_sum),
                SearchableFeature.Category.PRIVACY,
                SearchableFeature.FragmentType.PRIVACY,
                null,
                Arrays.asList("freeze", "last seen")));

        catalog.add(new SearchableFeature("hideread",
                context.getString(R.string.hideread),
                context.getString(R.string.hideread_sum),
                SearchableFeature.Category.PRIVACY,
                SearchableFeature.FragmentType.PRIVACY,
                null,
                Arrays.asList("hide", "read", "blue", "ticks")));

        catalog.add(new SearchableFeature("hide_seen_view",
                context.getString(R.string.view_seen_tick),
                context.getString(R.string.view_seen_tick_sum),
                SearchableFeature.Category.PRIVACY,
                SearchableFeature.FragmentType.PRIVACY,
                null,
                Arrays.asList("view", "seen", "tick")));

        catalog.add(new SearchableFeature("blueonreply",
                context.getString(R.string.blueonreply),
                context.getString(R.string.blueonreply_sum),
                SearchableFeature.Category.PRIVACY,
                SearchableFeature.FragmentType.PRIVACY,
                null,
                Arrays.asList("blue", "tick", "reply")));

        catalog.add(new SearchableFeature("hideread_group",
                context.getString(R.string.hideread_group),
                context.getString(R.string.hideread_group_sum),
                SearchableFeature.Category.PRIVACY,
                SearchableFeature.FragmentType.PRIVACY,
                null,
                Arrays.asList("hide", "read", "group", "ticks")));

        catalog.add(new SearchableFeature("hidereceipt",
                context.getString(R.string.hidereceipt),
                context.getString(R.string.hidereceipt_sum),
                SearchableFeature.Category.PRIVACY,
                SearchableFeature.FragmentType.PRIVACY,
                null,
                Arrays.asList("hide", "delivered", "receipt")));

        catalog.add(new SearchableFeature("ghostmode_t",
                context.getString(R.string.ghostmode),
                context.getString(R.string.ghostmode_sum),
                SearchableFeature.Category.PRIVACY,
                SearchableFeature.FragmentType.PRIVACY,
                null,
                Arrays.asList("ghost", "typing", "hide")));

        catalog.add(new SearchableFeature("ghostmode_r",
                context.getString(R.string.ghostmode_r),
                context.getString(R.string.ghostmode_sum_r),
                SearchableFeature.Category.PRIVACY,
                SearchableFeature.FragmentType.PRIVACY,
                null,
                Arrays.asList("ghost", "recording", "audio")));

        catalog.add(new SearchableFeature("hideonceseen",
                context.getString(R.string.hide_once_view_seen),
                context.getString(R.string.hide_once_view_seen_sum),
                SearchableFeature.Category.PRIVACY,
                SearchableFeature.FragmentType.PRIVACY,
                null,
                Arrays.asList("view", "once", "seen", "hide")));

        catalog.add(new SearchableFeature("hideaudioseen",
                context.getString(R.string.hide_audio_seen),
                context.getString(R.string.hide_audio_seen_sum),
                SearchableFeature.Category.PRIVACY,
                SearchableFeature.FragmentType.PRIVACY,
                null,
                Arrays.asList("audio", "seen", "hide")));

        catalog.add(new SearchableFeature("viewonce",
                context.getString(R.string.viewonce),
                context.getString(R.string.viewonce_sum),
                SearchableFeature.Category.PRIVACY,
                SearchableFeature.FragmentType.PRIVACY,
                null,
                Arrays.asList("view", "once", "unlimited")));

        catalog.add(new SearchableFeature("seentick",
                context.getString(R.string.show_button_to_send_blue_tick),
                context.getString(R.string.show_button_to_send_blue_tick_sum),
                SearchableFeature.Category.PRIVACY,
                SearchableFeature.FragmentType.PRIVACY,
                null,
                Arrays.asList("blue", "tick", "button", "mark", "read")));

        catalog.add(new SearchableFeature("hidestatusview",
                context.getString(R.string.hidestatusview),
                context.getString(R.string.hidestatusview_sum),
                SearchableFeature.Category.PRIVACY,
                SearchableFeature.FragmentType.PRIVACY,
                null,
                Arrays.asList("status", "view", "hide")));

        catalog.add(new SearchableFeature("call_info",
                context.getString(R.string.additional_call_information),
                context.getString(R.string.additional_call_information_sum),
                SearchableFeature.Category.PRIVACY,
                SearchableFeature.FragmentType.PRIVACY,
                null,
                Arrays.asList("call", "information", "additional")));

        catalog.add(new SearchableFeature("call_privacy",
                context.getString(R.string.call_blocker),
                context.getString(R.string.call_blocker_sum),
                SearchableFeature.Category.PRIVACY,
                SearchableFeature.FragmentType.PRIVACY,
                null,
                Arrays.asList("call", "blocker", "block")));

        catalog.add(new SearchableFeature("call_type",
                context.getString(R.string.call_blocking_type),
                context.getString(R.string.call_blocking_type_sum),
                SearchableFeature.Category.PRIVACY,
                SearchableFeature.FragmentType.PRIVACY,
                null,
                Arrays.asList("call", "blocking", "type")));

        // Continue in next part...
        addMediaFeatures(context, catalog);
        addCustomizationFeatures(context, catalog);
        addHomeActions(context, catalog);

        return catalog;
    }

    private static void addMediaFeatures(Context context, List<SearchableFeature> catalog) {
        catalog.add(new SearchableFeature("imagequality",
                context.getString(R.string.imagequality),
                context.getString(R.string.imagequality_sum),
                SearchableFeature.Category.MEDIA,
                SearchableFeature.FragmentType.MEDIA,
                null,
                Arrays.asList("image", "quality", "hd")));

        catalog.add(new SearchableFeature("download_local",
                context.getString(R.string.local_download),
                null,
                SearchableFeature.Category.MEDIA,
                SearchableFeature.FragmentType.MEDIA,
                null,
                Arrays.asList("download", "local", "folder")));

        catalog.add(new SearchableFeature("downloadstatus",
                context.getString(R.string.statusdowload),
                context.getString(R.string.statusdowload_sum),
                SearchableFeature.Category.MEDIA,
                SearchableFeature.FragmentType.MEDIA,
                null,
                Arrays.asList("download", "status", "share")));

        catalog.add(new SearchableFeature("downloadviewonce",
                context.getString(R.string.downloadviewonce),
                context.getString(R.string.downloadviewonce_sum),
                SearchableFeature.Category.MEDIA,
                SearchableFeature.FragmentType.MEDIA,
                null,
                Arrays.asList("download", "view", "once")));

        catalog.add(new SearchableFeature("video_limit_size",
                context.getString(R.string.increase_video_size_limit),
                null,
                SearchableFeature.Category.MEDIA,
                SearchableFeature.FragmentType.MEDIA,
                null,
                Arrays.asList("video", "size", "limit", "mb")));

        catalog.add(new SearchableFeature("videoquality",
                context.getString(R.string.videoquality),
                context.getString(R.string.videoquality_sum),
                SearchableFeature.Category.MEDIA,
                SearchableFeature.FragmentType.MEDIA,
                null,
                Arrays.asList("video", "quality", "hd")));

        catalog.add(new SearchableFeature("video_real_resolution",
                context.getString(R.string.send_video_in_real_resolution),
                context.getString(R.string.send_video_in_real_resolution_sum),
                SearchableFeature.Category.MEDIA,
                SearchableFeature.FragmentType.MEDIA,
                null,
                Arrays.asList("video", "resolution", "real")));

        catalog.add(new SearchableFeature("video_maxfps",
                context.getString(R.string.send_video_in_60fps),
                context.getString(R.string.send_video_in_60fps_sum),
                SearchableFeature.Category.MEDIA,
                SearchableFeature.FragmentType.MEDIA,
                null,
                Arrays.asList("video", "60fps", "fps")));

        catalog.add(new SearchableFeature("call_recording_enable",
                context.getString(R.string.call_recording_enable),
                context.getString(R.string.call_recording_enable_sum),
                SearchableFeature.Category.MEDIA,
                SearchableFeature.FragmentType.MEDIA,
                null,
                Arrays.asList("call", "recording", "record")));

        catalog.add(new SearchableFeature("call_recording_path",
                context.getString(R.string.call_recording_path),
                null,
                SearchableFeature.Category.MEDIA,
                SearchableFeature.FragmentType.MEDIA,
                null,
                Arrays.asList("recording", "path", "folder")));

        catalog.add(new SearchableFeature("call_recording_toast",
                context.getString(R.string.call_recording_toast_title),
                context.getString(R.string.call_recording_toast_summary),
                SearchableFeature.Category.MEDIA,
                SearchableFeature.FragmentType.MEDIA,
                null,
                Arrays.asList("recording", "toast", "notification", "show", "hide")));

        catalog.add(new SearchableFeature("disable_sensor_proximity",
                context.getString(R.string.disable_the_proximity_sensor),
                context.getString(R.string.disable_the_proximity_sensor_sum),
                SearchableFeature.Category.MEDIA,
                SearchableFeature.FragmentType.MEDIA,
                null,
                Arrays.asList("proximity", "sensor", "screen")));

        catalog.add(new SearchableFeature("proximity_audios",
                context.getString(R.string.disable_audio_sensor),
                context.getString(R.string.disable_audio_sensor_sum),
                SearchableFeature.Category.MEDIA,
                SearchableFeature.FragmentType.MEDIA,
                null,
                Arrays.asList("audio", "proximity", "sensor")));

        catalog.add(new SearchableFeature("audio_type",
                context.getString(R.string.send_audio_as_voice_audio_note),
                context.getString(R.string.send_audio_as_voice_audio_note_sum),
                SearchableFeature.Category.MEDIA,
                SearchableFeature.FragmentType.MEDIA,
                null,
                Arrays.asList("audio", "voice", "note")));

        catalog.add(new SearchableFeature("voicenote_speed",
                context.getString(R.string.voice_note_speed),
                null,
                SearchableFeature.Category.MEDIA,
                SearchableFeature.FragmentType.MEDIA,
                null,
                Arrays.asList("voice", "note", "speed")));

        catalog.add(new SearchableFeature("audio_transcription",
                context.getString(R.string.audio_transcription),
                context.getString(R.string.audio_transcription_sum),
                SearchableFeature.Category.MEDIA,
                SearchableFeature.FragmentType.MEDIA,
                null,
                Arrays.asList("audio", "transcription", "text")));

        catalog.add(new SearchableFeature("transcription_provider",
                context.getString(R.string.transcription_provider),
                context.getString(R.string.transcription_provider_sum),
                SearchableFeature.Category.MEDIA,
                SearchableFeature.FragmentType.MEDIA,
                null,
                Arrays.asList("transcription", "provider", "ai")));

        catalog.add(new SearchableFeature("media_preview",
                context.getString(R.string.enable_media_preview),
                context.getString(R.string.enable_media_preview_sum),
                SearchableFeature.Category.MEDIA,
                SearchableFeature.FragmentType.MEDIA,
                null,
                Arrays.asList("media", "preview", "temporary")));
    }

    private static void addCustomizationFeatures(Context context, List<SearchableFeature> catalog) {
        catalog.add(new SearchableFeature("changecolor",
                context.getString(R.string.colors_customization),
                context.getString(R.string.colors_customization_sum),
                SearchableFeature.Category.CUSTOMIZATION,
                SearchableFeature.FragmentType.CUSTOMIZATION,
                null,
                Arrays.asList("colors", "customization", "theme")));

        catalog.add(new SearchableFeature("primary_color",
                context.getString(R.string.primary_color),
                null,
                SearchableFeature.Category.CUSTOMIZATION,
                SearchableFeature.FragmentType.CUSTOMIZATION,
                null,
                Arrays.asList("primary", "color")));

        catalog.add(new SearchableFeature("background_color",
                context.getString(R.string.background_color),
                null,
                SearchableFeature.Category.CUSTOMIZATION,
                SearchableFeature.FragmentType.CUSTOMIZATION,
                null,
                Arrays.asList("background", "color")));

        catalog.add(new SearchableFeature("text_color",
                context.getString(R.string.text_color),
                null,
                SearchableFeature.Category.CUSTOMIZATION,
                SearchableFeature.FragmentType.CUSTOMIZATION,
                null,
                Arrays.asList("text", "color")));

        catalog.add(new SearchableFeature("wallpaper",
                context.getString(R.string.wallpaper_in_home_screen),
                context.getString(R.string.wallpaper_in_home_screen_sum),
                SearchableFeature.Category.CUSTOMIZATION,
                SearchableFeature.FragmentType.CUSTOMIZATION,
                null,
                Arrays.asList("wallpaper", "background", "image")));

        catalog.add(new SearchableFeature("hidetabs",
                context.getString(R.string.hide_tabs_on_home),
                context.getString(R.string.hide_tabs_on_home_sum),
                SearchableFeature.Category.CUSTOMIZATION,
                SearchableFeature.FragmentType.CUSTOMIZATION,
                null,
                Arrays.asList("hide", "tabs", "home")));

        catalog.add(new SearchableFeature("custom_filters",
                context.getString(R.string.custom_appearance),
                context.getString(R.string.custom_filters_sum),
                SearchableFeature.Category.CUSTOMIZATION,
                SearchableFeature.FragmentType.CUSTOMIZATION,
                null,
                Arrays.asList("custom", "appearance", "filters", "css")));

        catalog.add(new SearchableFeature("animation_list",
                context.getString(R.string.list_animations_home_screen),
                context.getString(R.string.list_animations_home_screen_sum),
                SearchableFeature.Category.CUSTOMIZATION,
                SearchableFeature.FragmentType.CUSTOMIZATION,
                null,
                Arrays.asList("animation", "list", "home")));

        catalog.add(new SearchableFeature("admin_grp",
                context.getString(R.string.show_admin_group_icon),
                context.getString(R.string.show_admin_group_icon_sum),
                SearchableFeature.Category.CUSTOMIZATION,
                SearchableFeature.FragmentType.CUSTOMIZATION,
                null,
                Arrays.asList("admin", "group", "icon")));

        catalog.add(new SearchableFeature("floatingmenu",
                context.getString(R.string.new_context_menu_ui),
                context.getString(R.string.new_context_menu_ui_sum),
                SearchableFeature.Category.CUSTOMIZATION,
                SearchableFeature.FragmentType.CUSTOMIZATION,
                null,
                Arrays.asList("floating", "menu", "context", "ios")));

        catalog.add(new SearchableFeature("animation_emojis",
                context.getString(R.string.animation_emojis),
                context.getString(R.string.animation_emojis_sum),
                SearchableFeature.Category.CUSTOMIZATION,
                SearchableFeature.FragmentType.CUSTOMIZATION,
                null,
                Arrays.asList("animation", "emojis", "large")));

        catalog.add(new SearchableFeature("bubble_color",
                context.getString(R.string.change_bubble_colors),
                context.getString(R.string.change_blubble_color_sum),
                SearchableFeature.Category.CUSTOMIZATION,
                SearchableFeature.FragmentType.CUSTOMIZATION,
                null,
                Arrays.asList("bubble", "color", "chat")));

        catalog.add(new SearchableFeature("menuwicon",
                context.getString(R.string.menuwicon),
                context.getString(R.string.menuwicon_sum),
                SearchableFeature.Category.CUSTOMIZATION,
                SearchableFeature.FragmentType.CUSTOMIZATION,
                null,
                Arrays.asList("menu", "icons")));

        catalog.add(new SearchableFeature("novaconfig",
                context.getString(R.string.novaconfig),
                context.getString(R.string.novaconfig_sum),
                SearchableFeature.Category.CUSTOMIZATION,
                SearchableFeature.FragmentType.CUSTOMIZATION,
                null,
                Arrays.asList("settings", "style", "profile")));

        catalog.add(new SearchableFeature("igstatus",
                context.getString(R.string.igstatus_on_home_screen),
                context.getString(R.string.igstatus_on_home_screen_sum),
                SearchableFeature.Category.CUSTOMIZATION,
                SearchableFeature.FragmentType.CUSTOMIZATION,
                null,
                Arrays.asList("instagram", "status", "ig")));

        catalog.add(new SearchableFeature("channels",
                context.getString(R.string.disable_channels),
                context.getString(R.string.disable_channels_sum),
                SearchableFeature.Category.CUSTOMIZATION,
                SearchableFeature.FragmentType.CUSTOMIZATION,
                null,
                Arrays.asList("channels", "disable", "hide")));

        catalog.add(new SearchableFeature("removechannel_rec",
                context.getString(R.string.remove_channel_recomendations),
                context.getString(R.string.remove_channel_recomendations_sum),
                SearchableFeature.Category.CUSTOMIZATION,
                SearchableFeature.FragmentType.CUSTOMIZATION,
                null,
                Arrays.asList("channel", "recommendations", "remove")));

        catalog.add(new SearchableFeature("status_style",
                context.getString(R.string.style_of_stories_status),
                context.getString(R.string.style_of_stories_status_sum),
                SearchableFeature.Category.CUSTOMIZATION,
                SearchableFeature.FragmentType.CUSTOMIZATION,
                null,
                Arrays.asList("status", "style", "stories")));

        catalog.add(new SearchableFeature("oldstatus",
                context.getString(R.string.old_statuses),
                context.getString(R.string.old_statuses_sum),
                SearchableFeature.Category.CUSTOMIZATION,
                SearchableFeature.FragmentType.CUSTOMIZATION,
                null,
                Arrays.asList("old", "status", "vertical")));

        catalog.add(new SearchableFeature("statuscomposer",
                context.getString(R.string.custom_colors_for_text_status),
                context.getString(R.string.custom_colors_for_text_status_sum),
                SearchableFeature.Category.CUSTOMIZATION,
                SearchableFeature.FragmentType.CUSTOMIZATION,
                null,
                Arrays.asList("status", "composer", "colors", "text")));
    }

    private static void addHomeActions(Context context, List<SearchableFeature> catalog) {
        // Home Fragment Actions
        catalog.add(new SearchableFeature("export_config",
                context.getString(R.string.export_settings),
                context.getString(R.string.backup_settings),
                SearchableFeature.Category.HOME_ACTIONS,
                SearchableFeature.FragmentType.HOME,
                null,
                Arrays.asList("export", "backup", "settings", "config")));

        catalog.add(new SearchableFeature("import_config",
                context.getString(R.string.import_settings),
                context.getString(R.string.backup_settings),
                SearchableFeature.Category.HOME_ACTIONS,
                SearchableFeature.FragmentType.HOME,
                null,
                Arrays.asList("import", "restore", "settings", "config")));

        catalog.add(new SearchableFeature("reset_config",
                context.getString(R.string.reset_settings),
                null,
                SearchableFeature.Category.HOME_ACTIONS,
                SearchableFeature.FragmentType.HOME,
                null,
                Arrays.asList("reset", "settings", "clear")));

        catalog.add(new SearchableFeature("reboot_wpp",
                context.getString(R.string.restart_whatsapp),
                null,
                SearchableFeature.Category.HOME_ACTIONS,
                SearchableFeature.FragmentType.HOME,
                null,
                Arrays.asList("restart", "reboot", "whatsapp", "refresh")));
    }
}
