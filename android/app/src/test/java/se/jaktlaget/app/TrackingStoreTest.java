package se.jaktlaget.app;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.concurrent.CountDownLatch;

public class TrackingStoreTest {
    private boolean add(TrackingStore store, String id, int value) {
        return store.append(id, 62, 17, 5, null, value, value, value + 10, value + 10, false, true);
    }
    @Test public void pagesAreNonDestructiveAndNewSessionResetsCursor() {
        TrackingStore store = new TrackingStore(1000);
        String first = store.begin(100, 100);
        for (int i = 0; i < 600; i++) assertTrue(add(store, first, i));
        assertEquals(500, store.read(first, 0).samples.size());
        assertEquals(500, store.read(first, 0).samples.size());
        assertTrue(store.read(first, 0).hasMore);
        assertEquals(100, store.read(first, 500).samples.size());
        assertEquals(501, store.read(first, 500).samples.get(0).sequence);
        store.requestStop(first, "Stopped");
        assertFalse(add(store, first, 601));
        store.finish(first, 800, 800);
        assertEquals(600, store.read(first, 0).count);
        String second = store.begin(900, 900);
        assertFalse(add(store, first, 901));
        assertTrue(add(store, second, 902));
        assertEquals(1, store.read(first, 600).samples.get(0).sequence);
    }
    @Test public void capacityStopsWithoutOverwritingAndStopIsIdempotent() {
        TrackingStore store = new TrackingStore(2);
        String id = store.begin(1, 1);
        assertTrue(add(store, id, 2));
        assertFalse(add(store, id, 3));
        assertFalse(add(store, id, 4));
        assertEquals("stopping", store.read(id, 0).phase);
        store.finish(id, 5, 5); store.finish(id, 6, 6);
        assertEquals(5, store.read(id, 0).stoppedAt);
        assertEquals(2, store.read(id, 0).count);
        assertEquals(2, store.read(id, 0).samples.get(0).measuredAt);
    }
    @Test public void concurrentWritersHaveUniqueSequencesAndReadersKeepData() throws Exception {
        TrackingStore store = new TrackingStore(5000);
        String id = store.begin(1, 1);
        CountDownLatch go = new CountDownLatch(1);
        Thread[] writers = new Thread[4];
        for (int t = 0; t < writers.length; t++) {
            writers[t] = new Thread(() -> {
                try { go.await(); } catch (InterruptedException e) { throw new RuntimeException(e); }
                for (int i = 0; i < 500; i++) { add(store, id, i); store.read(id, 0); }
            });
            writers[t].start();
        }
        go.countDown(); for (Thread writer : writers) writer.join();
        assertEquals(2000, store.read(id, 0).count);
        int cursor = 0;
        do {
            TrackingStore.Snapshot page = store.read(id, cursor);
            for (TrackingStore.Sample sample : page.samples) assertEquals(++cursor, sample.sequence);
            if (!page.hasMore) break;
        } while (true);
        assertEquals(2000, cursor);
    }
}
