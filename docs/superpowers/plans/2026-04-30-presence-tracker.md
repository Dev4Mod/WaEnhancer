# Contact Online Activity Tracker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build settings-based contact online activity tracking that persists `ONLINE` and inferred `OFFLINE` transition rows for selected contacts.

**Architecture:** Add a pure Java state manager for testable transition detection, an Android SQLite store for durable event rows, and a small Xposed feature that reuses the verified WhatsApp online event hook. The first implementation uses the approved 90-second `OFFLINE_TIMEOUT_MS` fallback because the repo does not currently verify a remote-contact offline payload.

**Tech Stack:** Android Java, Xposed hooks, AndroidX/Rikka preferences, SQLiteOpenHelper, Gradle Kotlin DSL, JUnit 4 JVM tests.

---

## File Structure

- Create `app/src/main/java/com/wmods/wppenhacer/xposed/features/general/PresenceStateTracker.java`
  - Pure Java state machine for tracked contact parsing, transition detection, duplicate suppression, and timeout-based offline inference.
- Create `app/src/test/java/com/wmods/wppenhacer/xposed/features/general/PresenceStateTrackerTest.java`
  - JVM tests for tracker disabled state, contact filtering, duplicate suppression, multi-contact state, and offline timeout.
- Create `app/src/main/java/com/wmods/wppenhacer/xposed/core/db/PresenceLogStore.java`
  - SQLite persistence helper with `presence_events` table and `insertEvent()`.
- Create `app/src/main/java/com/wmods/wppenhacer/xposed/features/general/PresenceTracker.java`
  - Xposed feature that reads preferences, hooks `Unobfuscator.loadCheckOnlineMethod()`, normalizes JIDs, and forwards online events to `PresenceStateTracker`.
- Modify `gradle/libs.versions.toml`
  - Add JUnit 4 catalog entry.
- Modify `app/build.gradle.kts`
  - Add `testImplementation(libs.junit)`.
- Modify `app/src/main/java/com/wmods/wppenhacer/xposed/core/FeatureLoader.java`
  - Import and register `PresenceTracker`.
- Modify `app/src/main/res/xml/preference_general_conversation.xml`
  - Add `presence_tracker` and `presence_tracker_contacts` preferences.
- Modify `app/src/main/res/values/strings.xml`
  - Add setting strings.
- Modify `app/src/main/java/com/wmods/wppenhacer/ui/fragments/GeneralFragment.java`
  - Route contact picker results for `ConversationGeneralPreference`.
- Modify `app/src/main/java/com/wmods/wppenhacer/utils/FeatureCatalog.java`
  - Add searchable entries for the tracker settings.
- Modify `.wolf/anatomy.md`, `.wolf/structured-memory.md`, `.wolf/memory.md`
  - Record new files and implementation plan knowledge per OpenWolf.

## Task 1: Add Test Dependency

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add JUnit to the version catalog**

In `gradle/libs.versions.toml`, add this version under `[versions]`:

```toml
junit = "4.13.2"
```

Add this library under `[libraries]`:

```toml
junit = { module = "junit:junit", version.ref = "junit" }
```

- [ ] **Step 2: Add the JVM test dependency**

In `app/build.gradle.kts`, inside `dependencies`, add:

```kotlin
testImplementation(libs.junit)
```

- [ ] **Step 3: Run Gradle dependency resolution**

Run:

```bash
./gradlew :app:dependencies --configuration whatsappDebugUnitTestRuntimeClasspath
```

Expected: command completes successfully and includes `junit:junit:4.13.2`.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "test: add junit for presence tracker"
```

## Task 2: Build Pure Transition State Manager

**Files:**
- Create: `app/src/main/java/com/wmods/wppenhacer/xposed/features/general/PresenceStateTracker.java`
- Create: `app/src/test/java/com/wmods/wppenhacer/xposed/features/general/PresenceStateTrackerTest.java`

- [ ] **Step 1: Write failing tests**

Create `app/src/test/java/com/wmods/wppenhacer/xposed/features/general/PresenceStateTrackerTest.java`:

```java
package com.wmods.wppenhacer.xposed.features.general;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class PresenceStateTrackerTest {

    @Test
    public void ignoresOnlineWhenDisabled() {
        FakeClock clock = new FakeClock(1000L);
        RecordingSink sink = new RecordingSink();
        PresenceStateTracker tracker = new PresenceStateTracker(clock, sink, 90000L);

        tracker.updateTrackedContacts("[12345@s.whatsapp.net]");
        tracker.setEnabled(false);
        tracker.onOnline("12345@s.whatsapp.net");

        assertTrue(sink.events.isEmpty());
    }

    @Test
    public void logsOnlineOnlyForTrackedContact() {
        FakeClock clock = new FakeClock(1000L);
        RecordingSink sink = new RecordingSink();
        PresenceStateTracker tracker = new PresenceStateTracker(clock, sink, 90000L);

        tracker.updateTrackedContacts("[12345@s.whatsapp.net]");
        tracker.setEnabled(true);
        tracker.onOnline("99999@s.whatsapp.net");
        tracker.onOnline("12345@s.whatsapp.net");

        assertEquals(1, sink.events.size());
        assertEquals(new Event("12345", PresenceStateTracker.Status.ONLINE, 1000L), sink.events.get(0));
    }

    @Test
    public void suppressesDuplicateOnlineTransition() {
        FakeClock clock = new FakeClock(1000L);
        RecordingSink sink = new RecordingSink();
        PresenceStateTracker tracker = new PresenceStateTracker(clock, sink, 90000L);

        tracker.updateTrackedContacts("[12345@s.whatsapp.net]");
        tracker.setEnabled(true);
        tracker.onOnline("12345@s.whatsapp.net");
        clock.now = 2000L;
        tracker.onOnline("12345@s.whatsapp.net");

        assertEquals(1, sink.events.size());
        assertEquals(new Event("12345", PresenceStateTracker.Status.ONLINE, 1000L), sink.events.get(0));
    }

    @Test
    public void logsOfflineAfterTimeout() {
        FakeClock clock = new FakeClock(1000L);
        RecordingSink sink = new RecordingSink();
        PresenceStateTracker tracker = new PresenceStateTracker(clock, sink, 90000L);

        tracker.updateTrackedContacts("[12345@s.whatsapp.net]");
        tracker.setEnabled(true);
        tracker.onOnline("12345@s.whatsapp.net");
        clock.now = 91000L;
        tracker.flushOfflineTimeouts();

        assertEquals(2, sink.events.size());
        assertEquals(new Event("12345", PresenceStateTracker.Status.ONLINE, 1000L), sink.events.get(0));
        assertEquals(new Event("12345", PresenceStateTracker.Status.OFFLINE, 91000L), sink.events.get(1));
    }

    @Test
    public void repeatedOnlineRefreshesOfflineDeadline() {
        FakeClock clock = new FakeClock(1000L);
        RecordingSink sink = new RecordingSink();
        PresenceStateTracker tracker = new PresenceStateTracker(clock, sink, 90000L);

        tracker.updateTrackedContacts("[12345@s.whatsapp.net]");
        tracker.setEnabled(true);
        tracker.onOnline("12345@s.whatsapp.net");
        clock.now = 45000L;
        tracker.onOnline("12345@s.whatsapp.net");
        clock.now = 91000L;
        tracker.flushOfflineTimeouts();
        clock.now = 135000L;
        tracker.flushOfflineTimeouts();

        assertEquals(2, sink.events.size());
        assertEquals(new Event("12345", PresenceStateTracker.Status.ONLINE, 1000L), sink.events.get(0));
        assertEquals(new Event("12345", PresenceStateTracker.Status.OFFLINE, 135000L), sink.events.get(1));
    }

    @Test
    public void tracksMultipleContactsIndependently() {
        FakeClock clock = new FakeClock(1000L);
        RecordingSink sink = new RecordingSink();
        PresenceStateTracker tracker = new PresenceStateTracker(clock, sink, 90000L);

        tracker.updateTrackedContacts("[12345@s.whatsapp.net, 67890@s.whatsapp.net]");
        tracker.setEnabled(true);
        tracker.onOnline("12345@s.whatsapp.net");
        clock.now = 2000L;
        tracker.onOnline("67890@s.whatsapp.net");
        clock.now = 92000L;
        tracker.flushOfflineTimeouts();

        assertEquals(3, sink.events.size());
        assertEquals(new Event("12345", PresenceStateTracker.Status.ONLINE, 1000L), sink.events.get(0));
        assertEquals(new Event("67890", PresenceStateTracker.Status.ONLINE, 2000L), sink.events.get(1));
        assertEquals(new Event("12345", PresenceStateTracker.Status.OFFLINE, 92000L), sink.events.get(2));
    }

    private static final class FakeClock implements PresenceStateTracker.Clock {
        private long now;

        private FakeClock(long now) {
            this.now = now;
        }

        @Override
        public long now() {
            return now;
        }
    }

    private static final class RecordingSink implements PresenceStateTracker.EventSink {
        private final List<Event> events = new ArrayList<>();

        @Override
        public void insert(String contactId, PresenceStateTracker.Status status, long timestamp) {
            events.add(new Event(contactId, status, timestamp));
        }
    }

    private record Event(String contactId, PresenceStateTracker.Status status, long timestamp) {
    }
}
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```bash
./gradlew :app:testWhatsappDebugUnitTest --tests com.wmods.wppenhacer.xposed.features.general.PresenceStateTrackerTest
```

Expected: compilation fails because `PresenceStateTracker` does not exist.

- [ ] **Step 3: Implement the state manager**

Create `app/src/main/java/com/wmods/wppenhacer/xposed/features/general/PresenceStateTracker.java`:

```java
package com.wmods.wppenhacer.xposed.features.general;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PresenceStateTracker {

    public enum Status {
        ONLINE,
        OFFLINE
    }

    public interface Clock {
        long now();
    }

    public interface EventSink {
        void insert(String contactId, Status status, long timestamp);
    }

    private final Clock clock;
    private final EventSink eventSink;
    private final long offlineTimeoutMs;
    private final Set<String> trackedContacts = new HashSet<>();
    private final Map<String, Status> states = new HashMap<>();
    private final Map<String, Long> lastOnlineAt = new HashMap<>();
    private boolean enabled;

    public PresenceStateTracker(@NonNull Clock clock, @NonNull EventSink eventSink, long offlineTimeoutMs) {
        this.clock = clock;
        this.eventSink = eventSink;
        this.offlineTimeoutMs = offlineTimeoutMs;
    }

    public synchronized void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            states.clear();
            lastOnlineAt.clear();
        }
    }

    public synchronized void updateTrackedContacts(@Nullable String contactsValue) {
        trackedContacts.clear();
        if (contactsValue == null || contactsValue.length() < 2) {
            states.clear();
            lastOnlineAt.clear();
            return;
        }
        String body = contactsValue.substring(1, contactsValue.length() - 1);
        if (body.trim().isEmpty()) {
            states.clear();
            lastOnlineAt.clear();
            return;
        }
        for (String item : body.split(", ")) {
            String normalized = normalizeContactId(item);
            if (normalized != null) {
                trackedContacts.add(normalized);
            }
        }
        states.keySet().removeIf(contactId -> !trackedContacts.contains(contactId));
        lastOnlineAt.keySet().removeIf(contactId -> !trackedContacts.contains(contactId));
    }

    public synchronized void onOnline(@Nullable String rawJid) {
        if (!enabled) return;
        String contactId = normalizeContactId(rawJid);
        if (contactId == null || !trackedContacts.contains(contactId)) return;

        long now = clock.now();
        lastOnlineAt.put(contactId, now);
        if (states.get(contactId) == Status.ONLINE) return;

        states.put(contactId, Status.ONLINE);
        eventSink.insert(contactId, Status.ONLINE, now);
    }

    public synchronized void flushOfflineTimeouts() {
        if (!enabled) return;
        long now = clock.now();
        for (String contactId : new HashSet<>(lastOnlineAt.keySet())) {
            Long onlineAt = lastOnlineAt.get(contactId);
            if (onlineAt == null || now - onlineAt < offlineTimeoutMs) continue;
            if (states.get(contactId) != Status.ONLINE) continue;

            states.put(contactId, Status.OFFLINE);
            lastOnlineAt.remove(contactId);
            eventSink.insert(contactId, Status.OFFLINE, now);
        }
    }

    @Nullable
    public static String normalizeContactId(@Nullable String rawJid) {
        if (rawJid == null) return null;
        String value = rawJid.trim();
        if (value.isEmpty()) return null;
        if (value.startsWith("[") && value.endsWith("]") && value.length() > 1) {
            value = value.substring(1, value.length() - 1).trim();
        }
        int atIndex = value.indexOf('@');
        if (atIndex > 0) {
            String domain = value.substring(atIndex + 1);
            if (domain.equals("g.us") || domain.equals("broadcast") || domain.equals("newsletter") || domain.equals("status")) {
                return null;
            }
            value = value.substring(0, atIndex);
        }
        int dotIndex = value.indexOf('.');
        if (dotIndex > 0) {
            value = value.substring(0, dotIndex);
        }
        return value.isEmpty() ? null : value;
    }
}
```

- [ ] **Step 4: Run tests to verify pass**

Run:

```bash
./gradlew :app:testWhatsappDebugUnitTest --tests com.wmods.wppenhacer.xposed.features.general.PresenceStateTrackerTest
```

Expected: all tests in `PresenceStateTrackerTest` pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/wmods/wppenhacer/xposed/features/general/PresenceStateTracker.java app/src/test/java/com/wmods/wppenhacer/xposed/features/general/PresenceStateTrackerTest.java
git commit -m "feat: add presence transition tracker"
```

## Task 3: Add Presence Log SQLite Store

**Files:**
- Create: `app/src/main/java/com/wmods/wppenhacer/xposed/core/db/PresenceLogStore.java`

- [ ] **Step 1: Create the SQLite helper**

Create `app/src/main/java/com/wmods/wppenhacer/xposed/core/db/PresenceLogStore.java`:

```java
package com.wmods.wppenhacer.xposed.core.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.features.general.PresenceStateTracker;
import com.wmods.wppenhacer.xposed.utils.Utils;

public class PresenceLogStore extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "presence_logs.db";
    private static final int DATABASE_VERSION = 1;
    public static final String TABLE_PRESENCE_EVENTS = "presence_events";
    private static PresenceLogStore mInstance;

    private PresenceLogStore(@NonNull Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    public static PresenceLogStore getInstance() {
        synchronized (PresenceLogStore.class) {
            if (mInstance == null || !mInstance.getWritableDatabase().isOpen()) {
                mInstance = new PresenceLogStore(Utils.getApplication());
            }
        }
        return mInstance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_PRESENCE_EVENTS + " (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "contact_id TEXT NOT NULL, " +
                "status TEXT NOT NULL, " +
                "timestamp INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_presence_events_contact_time ON " +
                TABLE_PRESENCE_EVENTS + "(contact_id, timestamp)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    public void insertEvent(@NonNull String contactId, @NonNull PresenceStateTracker.Status status, long timestamp) {
        try (SQLiteDatabase dbWrite = this.getWritableDatabase()) {
            ContentValues values = new ContentValues();
            values.put("contact_id", contactId);
            values.put("status", status.name());
            values.put("timestamp", timestamp);
            dbWrite.insert(TABLE_PRESENCE_EVENTS, null, values);
        }
    }
}
```

- [ ] **Step 2: Build compile target**

Run:

```bash
./gradlew :app:compileWhatsappDebugJavaWithJavac
```

Expected: compile succeeds.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/wmods/wppenhacer/xposed/core/db/PresenceLogStore.java
git commit -m "feat: add presence log store"
```

## Task 4: Add Xposed Presence Tracker Feature

**Files:**
- Create: `app/src/main/java/com/wmods/wppenhacer/xposed/features/general/PresenceTracker.java`
- Modify: `app/src/main/java/com/wmods/wppenhacer/xposed/core/FeatureLoader.java`

- [ ] **Step 1: Create the feature class**

Create `app/src/main/java/com/wmods/wppenhacer/xposed/features/general/PresenceTracker.java`:

```java
package com.wmods.wppenhacer.xposed.features.general;

import android.os.BaseBundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.wmods.wppenhacer.xposed.core.Feature;
import com.wmods.wppenhacer.xposed.core.components.FMessageWpp;
import com.wmods.wppenhacer.xposed.core.db.PresenceLogStore;
import com.wmods.wppenhacer.xposed.core.devkit.Unobfuscator;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class PresenceTracker extends Feature {

    public static final long OFFLINE_TIMEOUT_MS = 90_000L;
    private static final String PREF_ENABLED = "presence_tracker";
    private static final String PREF_CONTACTS = "presence_tracker_contacts";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final PresenceStateTracker tracker;
    private final Runnable flushOfflineRunnable = new Runnable() {
        @Override
        public void run() {
            tracker.flushOfflineTimeouts();
            handler.postDelayed(this, 1000L);
        }
    };

    public PresenceTracker(@NonNull ClassLoader classLoader, @NonNull XSharedPreferences preferences) {
        super(classLoader, preferences);
        tracker = new PresenceStateTracker(
                System::currentTimeMillis,
                (contactId, status, timestamp) -> PresenceLogStore.getInstance().insertEvent(contactId, status, timestamp),
                OFFLINE_TIMEOUT_MS);
    }

    @Override
    public void doHook() throws Throwable {
        reloadSettings();
        if (!prefs.getBoolean(PREF_ENABLED, false)) return;

        handler.postDelayed(flushOfflineRunnable, 1000L);
        var checkOnlineMethod = Unobfuscator.loadCheckOnlineMethod(classLoader);
        XposedBridge.hookMethod(checkOnlineMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                reloadSettings();
                if (!prefs.getBoolean(PREF_ENABLED, false)) return;
                var message = (Message) param.args[0];
                if (message.arg1 != 5 || !(message.obj instanceof BaseBundle baseBundle)) return;
                var jid = baseBundle.getString("jid");
                if (TextUtils.isEmpty(jid)) return;
                var userJid = new FMessageWpp.UserJid(jid);
                if (userJid.isGroup()) return;
                tracker.onOnline(jid);
            }
        });
    }

    private void reloadSettings() {
        prefs.reload();
        tracker.setEnabled(prefs.getBoolean(PREF_ENABLED, false));
        tracker.updateTrackedContacts(prefs.getString(PREF_CONTACTS, ""));
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Presence Tracker";
    }
}
```

- [ ] **Step 2: Register the feature**

In `app/src/main/java/com/wmods/wppenhacer/xposed/core/FeatureLoader.java`, add the import:

```java
import com.wmods.wppenhacer.xposed.features.general.PresenceTracker;
```

In the `classes` array, insert `PresenceTracker.class` next to the other general features, immediately before `Others.class`:

```java
                NewChat.class,
                PresenceTracker.class,
                Others.class,
```

- [ ] **Step 3: Run unit tests and compile**

Run:

```bash
./gradlew :app:testWhatsappDebugUnitTest --tests com.wmods.wppenhacer.xposed.features.general.PresenceStateTrackerTest
./gradlew :app:compileWhatsappDebugJavaWithJavac
```

Expected: tests and compile pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/wmods/wppenhacer/xposed/features/general/PresenceTracker.java app/src/main/java/com/wmods/wppenhacer/xposed/core/FeatureLoader.java
git commit -m "feat: hook presence tracker events"
```

## Task 5: Add Settings And Contact Picker Result Handling

**Files:**
- Modify: `app/src/main/res/xml/preference_general_conversation.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/java/com/wmods/wppenhacer/ui/fragments/GeneralFragment.java`

- [ ] **Step 1: Add setting strings**

In `app/src/main/res/values/strings.xml`, after `show_toast_on_contact_online_sum`, add:

```xml
    <string name="presence_tracker">Contact Online Activity Tracker</string>
    <string name="presence_tracker_sum">Logs online and offline activity for selected contacts</string>
    <string name="presence_tracker_contacts">Tracked Contacts</string>
```

- [ ] **Step 2: Add preferences**

In `app/src/main/res/xml/preference_general_conversation.xml`, after the `showonline` switch, add:

```xml
        <rikka.material.preference.MaterialSwitchPreference
            app:key="presence_tracker"
            app:summary="@string/presence_tracker_sum"
            app:title="@string/presence_tracker" />

        <com.wmods.wppenhacer.preference.ContactPickerPreference
            android:key="presence_tracker_contacts"
            android:title="@string/presence_tracker_contacts"
            app:summaryOff="@string/no_contacts_selected"
            app:summaryOn="@string/contact_were_selected" />
```

- [ ] **Step 3: Route contact picker results in General conversation settings**

In `app/src/main/java/com/wmods/wppenhacer/ui/fragments/GeneralFragment.java`, add imports:

```java
import static com.wmods.wppenhacer.preference.ContactPickerPreference.REQUEST_CONTACT_PICKER;

import android.app.Activity;
import android.content.Intent;
import com.wmods.wppenhacer.preference.ContactPickerPreference;
```

Inside `ConversationGeneralPreference`, add:

```java
        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (requestCode == REQUEST_CONTACT_PICKER && resultCode == Activity.RESULT_OK && data != null) {
                ContactPickerPreference contactPickerPref = findPreference(data.getStringExtra("key"));
                if (contactPickerPref != null) {
                    contactPickerPref.handleActivityResult(requestCode, resultCode, data);
                }
            }
        }
```

- [ ] **Step 4: Compile resources and Java**

Run:

```bash
./gradlew :app:compileWhatsappDebugJavaWithJavac
```

Expected: compile succeeds with the new strings, preference XML, and fragment imports.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/xml/preference_general_conversation.xml app/src/main/res/values/strings.xml app/src/main/java/com/wmods/wppenhacer/ui/fragments/GeneralFragment.java
git commit -m "feat: add presence tracker settings"
```

## Task 6: Add Search Catalog Entries

**Files:**
- Modify: `app/src/main/java/com/wmods/wppenhacer/utils/FeatureCatalog.java`

- [ ] **Step 1: Add searchable feature entries**

In `FeatureCatalog`, near the existing General Conversation entry for `showonline`, add:

```java
        catalog.add(new SearchableFeature("presence_tracker",
                context.getString(R.string.presence_tracker),
                context.getString(R.string.presence_tracker_sum),
                SearchableFeature.Category.GENERAL_CONVERSATION,
                SearchableFeature.FragmentType.GENERAL,
                "conversation",
                Arrays.asList("presence", "online", "offline", "tracker", "activity", "log")));

        catalog.add(new SearchableFeature("presence_tracker_contacts",
                context.getString(R.string.presence_tracker_contacts),
                context.getString(R.string.no_contacts_selected),
                SearchableFeature.Category.GENERAL_CONVERSATION,
                SearchableFeature.FragmentType.GENERAL,
                "conversation",
                Arrays.asList("presence", "contacts", "tracked", "online", "offline")));
```

- [ ] **Step 2: Compile**

Run:

```bash
./gradlew :app:compileWhatsappDebugJavaWithJavac
```

Expected: compile succeeds.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/wmods/wppenhacer/utils/FeatureCatalog.java
git commit -m "feat: index presence tracker settings"
```

## Task 7: Final Verification And OpenWolf Updates

**Files:**
- Modify: `.wolf/anatomy.md`
- Modify: `.wolf/structured-memory.md`
- Modify: `.wolf/cerebrum.md`
- Modify: `.wolf/memory.md`

- [ ] **Step 1: Run full relevant verification**

Run:

```bash
./gradlew :app:testWhatsappDebugUnitTest
./gradlew :app:assembleWhatsappDebug
```

Expected: tests pass and the WhatsApp debug APK builds successfully.

- [ ] **Step 2: Update OpenWolf anatomy for created files**

Add entries:

```markdown
## app/src/test/java/com/wmods/wppenhacer/xposed/features/general/

- `PresenceStateTrackerTest.java` — JVM tests for contact filtering, duplicate suppression, multi-contact tracking, and offline timeout. (~1200 tok)
```

Under `app/src/main/java/com/wmods/wppenhacer/xposed/features/general/`, add:

```markdown
- `PresenceStateTracker.java` — Pure transition state manager for tracked contact presence events. (~900 tok)
- `PresenceTracker.java` — Xposed feature hooking WhatsApp online events and persisting presence transitions. (~900 tok)
```

Under `app/src/main/java/com/wmods/wppenhacer/xposed/core/db/`, add:

```markdown
- `PresenceLogStore.java` — SQLite helper for persistent contact presence event logs. (~600 tok)
```

- [ ] **Step 3: Update OpenWolf structured memory**

Append to `.wolf/structured-memory.md`:

```markdown
## implementation_result_presence_tracker

Implemented Contact Online Activity Tracker using `PresenceTracker`, `PresenceStateTracker`, and `PresenceLogStore`.

Settings:
- `presence_tracker`
- `presence_tracker_contacts`

Persistence:
- database `presence_logs.db`
- table `presence_events`
- columns `_id`, `contact_id`, `status`, `timestamp`

Offline behavior: direct remote-contact offline hook was not used because the repo only verified the online payload shape. `OFFLINE` is inferred after `OFFLINE_TIMEOUT_MS = 90000`.
```

- [ ] **Step 4: Update OpenWolf cerebrum**

Append to `.wolf/cerebrum.md` `## Key Learnings`:

```markdown
- Contact Online Activity Tracker uses a pure Java `PresenceStateTracker` so transition and timeout behavior can be JVM-tested without Android/Xposed runtime.
```

- [ ] **Step 5: Log final memory entry**

Append to `.wolf/memory.md`:

```markdown
| HH:MM | Implemented and verified Contact Online Activity Tracker | PresenceTracker.java, PresenceStateTracker.java, PresenceLogStore.java, preference_general_conversation.xml, strings.xml, FeatureLoader.java, FeatureCatalog.java | tests and assemble passed; tracker logs selected contact online events and 90-second inferred offline transitions | ~8000 |
```

Replace `HH:MM` with the current local time from `date '+%H:%M'`.

- [ ] **Step 6: Commit OpenWolf updates if project policy wants memory committed**

If `.wolf` remains intentionally untracked in this repo, do not commit it. If the project owner asks to track OpenWolf files, run:

```bash
git add .wolf/anatomy.md .wolf/structured-memory.md .wolf/cerebrum.md .wolf/memory.md
git commit -m "docs: record presence tracker implementation memory"
```

Expected: memory commit is created only when explicitly desired.

