package se.jaktlaget.app;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class TrackingStoreTest {
    private TrackingDatabase.Sample point(double latitude) {
        return new TrackingDatabase.Sample(latitude, 17, 5, null, 1000, 1000, 1010, 1010, false, true);
    }
    @Test public void stopAcknowledgesAllEarlierQueuedWritesAndRecoveryRunsOnlyOnce() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String name = "store-test-" + UUID.randomUUID() + ".db";
        TrackingStore store = new TrackingStore(context, name);
        try {
            String id = store.begin().get(10, TimeUnit.SECONDS).getString("sessionId");
            CompletableFuture<Void> last = null;
            for (int i = 0; i < 100; i++) last = store.append(id, Collections.singletonList(point(62)));
            assertEquals("recording", store.state().get(10, TimeUnit.SECONDS).getString("phase"));
            assertEquals(100, store.finish(id, "stopped", "Stoppat och sparat", 2000, 2000)
                .get(10, TimeUnit.SECONDS).optLong("count"));
            assertTrue(last.isDone()); assertNull(store.activeId());
            assertEquals("stopped", store.read(id, 0, null).get(10, TimeUnit.SECONDS).getJSObject("state").getString("phase"));
            store.closeForTest().get(10, TimeUnit.SECONDS);
            store = new TrackingStore(context, name);
            assertEquals(100, store.state().get(10, TimeUnit.SECONDS).optLong("count"));
            String next = store.begin().get(10, TimeUnit.SECONDS).getString("sessionId");
            store.append(next, Collections.singletonList(point(62))).get(10, TimeUnit.SECONDS);
            store.closeForTest().get(10, TimeUnit.SECONDS);
            store = new TrackingStore(context, name); // A new process owner, not merely a WebView.
            assertEquals("interrupted", store.state().get(10, TimeUnit.SECONDS).getString("phase"));
            assertNull(store.activeId()); assertEquals(2, store.sessions().get(10, TimeUnit.SECONDS).getJSONArray("sessions").length());
        } finally { store.closeForTest().get(10, TimeUnit.SECONDS); context.deleteDatabase(name); }
    }
    @Test public void failedWriteCannotBeAcknowledgedAsSavedAndErrorSurvivesReopen() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String name = "store-test-" + UUID.randomUUID() + ".db";
        TrackingStore store = new TrackingStore(context, name);
        try {
            String id = store.begin().get(10, TimeUnit.SECONDS).getString("sessionId");
            store.append(id, Collections.singletonList(point(62))).get(10, TimeUnit.SECONDS);
            try { store.append(id, Collections.singletonList(point(999))).get(10, TimeUnit.SECONDS); fail(); }
            catch (java.util.concurrent.ExecutionException expected) { }
            try { store.finish(id, "stopped", "stopped", 2000, 2000).get(10, TimeUnit.SECONDS); fail(); }
            catch (java.util.concurrent.ExecutionException expected) { }
            assertNull(store.activeId());
            assertEquals("error", store.state().get(10, TimeUnit.SECONDS).getString("phase"));
            assertEquals(1, store.state().get(10, TimeUnit.SECONDS).optLong("count"));
            store.closeForTest().get(10, TimeUnit.SECONDS); store = new TrackingStore(context, name);
            assertEquals("error", store.state().get(10, TimeUnit.SECONDS).getString("phase"));
        } finally { store.closeForTest().get(10, TimeUnit.SECONDS); context.deleteDatabase(name); }
    }
}
