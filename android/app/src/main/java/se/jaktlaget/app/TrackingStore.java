package se.jaktlaget.app;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Process-local PoC state. No Activity, WebView, disk or network dependency. */
public final class TrackingStore {
    public static final TrackingStore INSTANCE = new TrackingStore(30_000);
    public static final int PAGE_SIZE = 500;
    private final int capacity;
    private final List<Sample> samples = new ArrayList<>();
    private String sessionId;
    private String phase = "idle";
    private String message = "Ingen testsession. Starta ett nytt test.";
    private long startedAt, stoppedAt, startedElapsedMs, stoppedElapsedMs;

    public TrackingStore(int capacity) { this.capacity = capacity; }

    public synchronized String begin(long now, long elapsedMs) {
        if (isActive()) throw new IllegalStateException("Spårning startar, pågår eller håller på att stoppas.");
        sessionId = UUID.randomUUID().toString();
        samples.clear();
        phase = "starting";
        message = "Startar GPS...";
        startedAt = now;
        startedElapsedMs = elapsedMs;
        stoppedAt = stoppedElapsedMs = 0;
        return sessionId;
    }

    public synchronized boolean accepts(String id) {
        return id != null && id.equals(sessionId) && (phase.equals("starting") || phase.equals("running"));
    }

    public synchronized boolean isActive() {
        return phase.equals("starting") || phase.equals("running") || phase.equals("stopping");
    }

    public synchronized void running(String id) {
        if (accepts(id)) { phase = "running"; message = "Spårning pågår"; }
    }

    /** False means the caller must stop. The final accepted point is retained. */
    public synchronized boolean append(String id, double latitude, double longitude, double accuracy,
            Double speed, long measuredAt, long measuredElapsedMs, long receivedAt,
            long receivedElapsedMs, boolean interactive, boolean locked) {
        if (!accepts(id)) return false;
        if (samples.size() < capacity) {
            samples.add(new Sample(sessionId, samples.size() + 1, latitude, longitude, accuracy, speed,
                measuredAt, measuredElapsedMs, receivedAt, receivedElapsedMs, interactive, locked));
        }
        if (samples.size() >= capacity) {
            phase = "stopping";
            message = "Bufferten är full (" + capacity + " punkter). Testet stoppades; inga äldre punkter raderades.";
            return false;
        }
        return true;
    }

    public synchronized void requestStop(String id, String reason) {
        if (id != null && id.equals(sessionId) && isActive()) {
            phase = "stopping";
            message = reason;
        }
    }

    public synchronized void finish(String id, long now, long elapsedMs) {
        if (id != null && id.equals(sessionId) && isActive()) {
            if (!phase.equals("stopping")) message = "Tjänsten avslutades. Testdata finns kvar så länge processen lever.";
            phase = "stopped";
            stoppedAt = now;
            stoppedElapsedMs = elapsedMs;
        }
    }

    /** Atomic page + state, non-destructive. A new session resets the reader cursor. */
    public synchronized Snapshot read(String readerSession, int afterSequence) {
        int from = sessionId != null && sessionId.equals(readerSession)
            ? Math.min(Math.max(afterSequence, 0), samples.size()) : 0;
        int to = Math.min(samples.size(), from + PAGE_SIZE);
        return new Snapshot(sessionId, phase, message, startedAt, stoppedAt, startedElapsedMs,
            stoppedElapsedMs, capacity, samples.size(), to, to < samples.size(),
            new ArrayList<>(samples.subList(from, to)));
    }

    public static final class Sample {
        public final String sessionId;
        public final int sequence;
        public final double latitude, longitude, accuracy;
        public final Double speed;
        public final long measuredAt, measuredElapsedMs, receivedAt, receivedElapsedMs;
        public final boolean screenInteractive, deviceLocked;
        Sample(String id, int seq, double lat, double lon, double accuracy, Double speed,
                long measured, long measuredElapsed, long received, long receivedElapsed,
                boolean interactive, boolean locked) {
            this.sessionId = id; this.sequence = seq; this.latitude = lat; this.longitude = lon;
            this.accuracy = accuracy; this.speed = speed; this.measuredAt = measured;
            this.measuredElapsedMs = measuredElapsed; this.receivedAt = received;
            this.receivedElapsedMs = receivedElapsed; this.screenInteractive = interactive;
            this.deviceLocked = locked;
        }
    }

    public static final class Snapshot {
        public final String sessionId, phase, message;
        public final long startedAt, stoppedAt, startedElapsedMs, stoppedElapsedMs;
        public final int capacity, count, nextSequence;
        public final boolean hasMore;
        public final List<Sample> samples;
        Snapshot(String id, String phase, String message, long start, long stop, long startElapsed,
                long stopElapsed, int capacity, int count, int next, boolean more, List<Sample> samples) {
            this.sessionId = id; this.phase = phase; this.message = message;
            this.startedAt = start; this.stoppedAt = stop; this.startedElapsedMs = startElapsed;
            this.stoppedElapsedMs = stopElapsed; this.capacity = capacity; this.count = count;
            this.nextSequence = next; this.hasMore = more; this.samples = samples;
        }
    }
}
