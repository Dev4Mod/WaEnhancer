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
        clock.now = 91000L;
        tracker.flushOfflineTimeouts();

        assertEquals(3, sink.events.size());
        assertEquals(new Event("12345", PresenceStateTracker.Status.ONLINE, 1000L), sink.events.get(0));
        assertEquals(new Event("67890", PresenceStateTracker.Status.ONLINE, 2000L), sink.events.get(1));
        assertEquals(new Event("12345", PresenceStateTracker.Status.OFFLINE, 91000L), sink.events.get(2));
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
