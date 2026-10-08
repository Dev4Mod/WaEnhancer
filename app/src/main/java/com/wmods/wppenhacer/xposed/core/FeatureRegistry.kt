package com.wmods.wppenhacer.xposed.core

import com.wmods.wppenhacer.xposed.features.customization.BubbleColors
import com.wmods.wppenhacer.xposed.features.customization.ContactVerify
import com.wmods.wppenhacer.xposed.features.customization.CustomThemeV2
import com.wmods.wppenhacer.xposed.features.customization.CustomTime
import com.wmods.wppenhacer.xposed.features.customization.CustomToolbar
import com.wmods.wppenhacer.xposed.features.customization.CustomView
import com.wmods.wppenhacer.xposed.features.customization.DefaultEmoji
import com.wmods.wppenhacer.xposed.features.customization.FilterGroups
import com.wmods.wppenhacer.xposed.features.customization.FloatingBottomBar
import com.wmods.wppenhacer.xposed.features.customization.HideSeenView
import com.wmods.wppenhacer.xposed.features.customization.HideTabs
import com.wmods.wppenhacer.xposed.features.customization.IGStatus
import com.wmods.wppenhacer.xposed.features.customization.SeparateGroup
import com.wmods.wppenhacer.xposed.features.customization.ShowOnline
import com.wmods.wppenhacer.xposed.features.general.AboutContactPicker
import com.wmods.wppenhacer.xposed.features.general.AntiRevoke
import com.wmods.wppenhacer.xposed.features.general.CallType
import com.wmods.wppenhacer.xposed.features.general.CaptureDevice
import com.wmods.wppenhacer.xposed.features.general.ChatLimit
import com.wmods.wppenhacer.xposed.features.general.DeleteStatus
import com.wmods.wppenhacer.xposed.features.general.NewChat
import com.wmods.wppenhacer.xposed.features.general.Others
import com.wmods.wppenhacer.xposed.features.general.PinnedLimit
import com.wmods.wppenhacer.xposed.features.general.SeenTick
import com.wmods.wppenhacer.xposed.features.general.ShareLimit
import com.wmods.wppenhacer.xposed.features.general.ShowEditMessage
import com.wmods.wppenhacer.xposed.features.general.Tasker
import com.wmods.wppenhacer.xposed.features.listeners.ContactItemListener
import com.wmods.wppenhacer.xposed.features.listeners.ConversationItemListener
import com.wmods.wppenhacer.xposed.features.media.CallRecording
import com.wmods.wppenhacer.xposed.features.media.DownloadProfile
import com.wmods.wppenhacer.xposed.features.media.DownloadViewOnce
import com.wmods.wppenhacer.xposed.features.media.MediaPreview
import com.wmods.wppenhacer.xposed.features.media.MediaQuality
import com.wmods.wppenhacer.xposed.features.media.StatusDownload
import com.wmods.wppenhacer.xposed.features.others.ActivityController
import com.wmods.wppenhacer.xposed.features.others.AudioTranscript
import com.wmods.wppenhacer.xposed.features.others.BackupRestore
import com.wmods.wppenhacer.xposed.features.others.Channels
import com.wmods.wppenhacer.xposed.features.others.ChatFilters
import com.wmods.wppenhacer.xposed.features.others.CopySelectionMessage
import com.wmods.wppenhacer.xposed.features.others.CopyStatus
import com.wmods.wppenhacer.xposed.features.others.DebugFeature
import com.wmods.wppenhacer.xposed.features.others.GoogleTranslate
import com.wmods.wppenhacer.xposed.features.others.GroupAdmin
import com.wmods.wppenhacer.xposed.features.others.JumpFirstMessage
import com.wmods.wppenhacer.xposed.features.others.MenuHome
import com.wmods.wppenhacer.xposed.features.others.MinorFixes
import com.wmods.wppenhacer.xposed.features.others.Stickers
import com.wmods.wppenhacer.xposed.features.others.TextStatusComposer
import com.wmods.wppenhacer.xposed.features.others.ToastViewer
import com.wmods.wppenhacer.xposed.features.others.importchat.ImportChat
import com.wmods.wppenhacer.xposed.features.privacy.AntiWa
import com.wmods.wppenhacer.xposed.features.privacy.CallPrivacy
import com.wmods.wppenhacer.xposed.features.privacy.CustomPrivacy
import com.wmods.wppenhacer.xposed.features.privacy.DndMode
import com.wmods.wppenhacer.xposed.features.privacy.FreezeLastSeen
import com.wmods.wppenhacer.xposed.features.privacy.HideChat
import com.wmods.wppenhacer.xposed.features.privacy.HideSeen
import com.wmods.wppenhacer.xposed.features.privacy.LockedChatsEnhancer
import com.wmods.wppenhacer.xposed.features.privacy.TagMessage
import com.wmods.wppenhacer.xposed.features.privacy.TypingPrivacy
import com.wmods.wppenhacer.xposed.features.privacy.ViewOnce
import com.wmods.wppenhacer.xposed.features.providers.ContextMenuActionProvider
import com.wmods.wppenhacer.xposed.features.providers.MenuStatusProvider

/** Every feature installed by [FeatureLoader], in load order. */
internal object FeatureRegistry {
    val features: List<Class<out Feature>> = listOf(
        DebugFeature::class.java,
        MinorFixes::class.java,
        ContactItemListener::class.java,
        ConversationItemListener::class.java,
        MenuStatusProvider::class.java,
        ShowEditMessage::class.java,
        AntiRevoke::class.java,
        CustomToolbar::class.java,
        CustomView::class.java,
        SeenTick::class.java,
        BubbleColors::class.java,
        CallPrivacy::class.java,
        ActivityController::class.java,
        CustomThemeV2::class.java,
        FloatingBottomBar::class.java,
        ChatLimit::class.java,
        SeparateGroup::class.java,
        ShowOnline::class.java,
        DndMode::class.java,
        FreezeLastSeen::class.java,
        TypingPrivacy::class.java,
        HideChat::class.java,
        HideSeen::class.java,
        HideSeenView::class.java,
        TagMessage::class.java,
        HideTabs::class.java,
        IGStatus::class.java,
        MediaQuality::class.java,
        NewChat::class.java,
        Others::class.java,
        PinnedLimit::class.java,
        CustomTime::class.java,
        ShareLimit::class.java,
        StatusDownload::class.java,
        ViewOnce::class.java,
        CallType::class.java,
        MediaPreview::class.java,
        FilterGroups::class.java,
        Tasker::class.java,
        DeleteStatus::class.java,
        DownloadViewOnce::class.java,
        Channels::class.java,
        DownloadProfile::class.java,
        ChatFilters::class.java,
        GroupAdmin::class.java,
        Stickers::class.java,
        CopyStatus::class.java,
        CopySelectionMessage::class.java,
        TextStatusComposer::class.java,
        ToastViewer::class.java,
        MenuHome::class.java,
        AntiWa::class.java,
        CustomPrivacy::class.java,
        AudioTranscript::class.java,
        GoogleTranslate::class.java,
        ContactVerify::class.java,
        LockedChatsEnhancer::class.java,
        CallRecording::class.java,
        BackupRestore::class.java,
        ImportChat::class.java,
        JumpFirstMessage::class.java,
        AboutContactPicker::class.java,
        DefaultEmoji::class.java,
        CaptureDevice::class.java,
        ContextMenuActionProvider::class.java
    )
}
