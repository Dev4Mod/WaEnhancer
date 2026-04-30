# Contact Online Activity Tracker Design

## Summary

Implement a first version of Contact Online Activity Tracker for WaEnhancer. Users can enable tracking, choose contacts from the existing WhatsApp contact picker, and WaEnhancer will persist online/offline transition rows with timestamps.

This version does not include an in-app log viewer. Logs are stored durably for later consumption or a future viewer.

## Goals

- Let users add and remove contacts from a tracking list.
- Record an `ONLINE` event with a timestamp when a tracked contact comes online.
- Record an `OFFLINE` event with a timestamp when a tracked contact goes offline.
- Keep runtime overhead low by processing only presence events for selected contacts.
- Follow existing WaEnhancer Xposed feature, preference, and SQLite patterns.

## Non-Goals

- No in-app activity history screen in the first version.
- No notification or toast for tracker events.
- No attempt to reconstruct presence while WhatsApp is disconnected, battery-killed, or unable to receive presence events.
- No tracking of groups, newsletters, broadcasts, or status contacts.

## Existing Architecture

WaEnhancer enters WhatsApp through `WppXposed.handleLoadPackage()`, then calls `FeatureLoader.start()`. The feature loader initializes core wrappers and loads each feature class listed in `FeatureLoader.plugins()`. Feature classes extend `Feature`, read `XSharedPreferences`, and install Xposed hooks from `doHook()`.

Settings are AndroidX/Rikka preferences backed by default shared preferences. The module makes these preferences world-readable so WhatsApp-side Xposed code can read them through `XSharedPreferences`.

The existing contact picker is `ContactPickerPreference`. It launches WhatsApp's notification contact picker and stores selected contact JIDs as a string value under the preference key.

Long-lived feature data is stored with small `SQLiteOpenHelper` helpers under `xposed/core/db`, using singleton accessors and `ContentValues` inserts.

## Presence Model

The repo already has a confirmed event-driven online path in `Others.showOnline()`. It hooks `Unobfuscator.loadCheckOnlineMethod()`, reads an Android `Message`, filters `message.arg1 == 5`, reads `((BaseBundle) message.obj).getString("jid")`, excludes groups, and emits existing toast/Tasker behavior.

Offline transitions are not yet proven through a remote-contact payload. The repo has `Unobfuscator.loadStateChangeMethod()` located by `presencestatemanager/startTransitionToUnavailable/new-state`, but current usage is for suppressing the local user's unavailable transition in the Always Online feature. The implementation plan must first verify whether an unavailable/offline hook exposes a remote contact JID. If it does not, the implementation will use the timeout fallback defined below.

Presence tracking is bounded by real-time event delivery. No connection means no tracking; delayed or missed WhatsApp events mean delayed or missing log rows.

## Proposed Architecture

Add a focused feature class under `com.wmods.wppenhacer.xposed.features.general` named `PresenceTracker`, and register it in `FeatureLoader.plugins()`.

`PresenceTracker` owns:

- preference loading for `presence_tracker` and `presence_tracker_contacts`
- tracked contact normalization and membership checks
- in-memory last-known state per contact
- transition detection
- persistence calls to a new SQLite helper

Add a SQLite helper under `com.wmods.wppenhacer.xposed.core.db` named `PresenceLogStore`, with a `presence_events` table:

```text
_id INTEGER PRIMARY KEY AUTOINCREMENT
contact_id TEXT NOT NULL
status TEXT NOT NULL
timestamp INTEGER NOT NULL
```

Indexes should support later reads by contact and time:

```text
CREATE INDEX IF NOT EXISTS idx_presence_events_contact_time
ON presence_events(contact_id, timestamp)
```

## Settings

Add settings to General -> Conversation:

- `presence_tracker`: switch to enable or disable tracking.
- `presence_tracker_contacts`: `ContactPickerPreference` for selected contacts.

Because `ContactPickerPreference` result handling currently lives in `PrivacyFragment`, add equivalent result forwarding to `GeneralFragment.ConversationGeneralPreference` or a shared base path so the contact picker works from General settings.

Add strings and search catalog entries for the two settings. The searchable entry should point to the General fragment with parent key `conversation`.

## Event Flow

1. WhatsApp receives a presence event.
2. `PresenceTracker` receives the event through the existing online hook or a verified paired offline hook.
3. The raw JID is normalized through `FMessageWpp.UserJid` and `WppCore.stripJID()` style behavior.
4. Groups and non-contact JIDs are ignored.
5. If the contact is not in `presence_tracker_contacts`, return immediately.
6. Compare incoming status with the last-known status for that contact.
7. If status changed, insert `{contact_id, status, timestamp}` using `System.currentTimeMillis()`.
8. Update in-memory state.

## Offline Handling Strategy

Preferred behavior is direct event-driven offline logging from a verified WhatsApp unavailable/offline hook that carries the remote contact JID.

If repo implementation cannot verify a remote-contact offline payload, the first version should use timeout-based offline inference:

- when an `ONLINE` event is logged, schedule an offline check for that contact
- if no newer online event has arrived within 90 seconds, log `OFFLINE`
- the 90-second timeout must be centralized as a constant named `OFFLINE_TIMEOUT_MS`
- repeated online events should refresh the deadline without adding duplicate `ONLINE` rows

The timeout fallback is less precise than direct offline events, but it keeps the feature functional without a brittle unverified hook.

## Edge Cases

- Rapid toggles: transition detection prevents duplicate rows for the same state, and direct offline events should be logged in order as received.
- Delayed events: timestamps represent local receipt/processing time, not guaranteed server-side presence time.
- Multiple contacts: use a map keyed by normalized contact ID; only tracked contacts incur state checks and database writes.
- Tracking list changes: preference reloads already occur through `XSharedPreferences` listener in `FeatureLoader`; the feature should reload the selected set before processing events or when preferences change.
- Freeze Last Seen or privacy restrictions: presence may not be received. The tracker must not try to bypass this; it can only log events WhatsApp receives.

## Validation

Manual and automated validation should cover:

- tracker disabled: no rows are inserted
- no contacts selected: no rows are inserted
- one tracked contact online event: one `ONLINE` row
- untracked contact online event: no row
- duplicate online events: no duplicate `ONLINE` transition rows
- multiple tracked contacts: independent state per contact
- offline event or timeout fallback: one `OFFLINE` row after `ONLINE`
- rapid online/offline changes: transitions remain ordered and no duplicate same-state rows are written

## Implementation Decision

The implementation should prefer direct offline events only if code investigation confirms a remote contact JID is available. Otherwise it must use the 90-second timeout fallback. This keeps the spec deterministic while still allowing the implementation to choose the most reliable verified hook path.
