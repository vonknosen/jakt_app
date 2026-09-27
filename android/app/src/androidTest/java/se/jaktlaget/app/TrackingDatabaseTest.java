package se.jaktlaget.app;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.getcapacitor.JSObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Runs on Android SQLite, not a mock. Uses an isolated randomly named database. */
@RunWith(AndroidJUnit4.class)
public class TrackingDatabaseTest {
    private Context context;
    private TrackingDatabase db;
    private String name;
    @Before public void setup() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        name = "tracking-test-" + UUID.randomUUID() + ".db";
        db = new TrackingDatabase(context, name);
    }
    @After public void cleanup() { db.close(); context.deleteDatabase(name); }
    private TrackingDatabase.Sample point(long time) {
        return new TrackingDatabase.Sample(62, 17, 5, null, time + 100000, time, time + 100010, time + 10, false, true);
    }
    private void stop(String id) { db.finish(id, "stopped", "Stoppat och sparat.", 200000, 100000); }
    private long scalar(String sql) {
        try (Cursor c = db.getReadableDatabase().rawQuery(sql, null)) { assertTrue(c.moveToFirst()); return c.getLong(0); }
    }
    @Test public void createWalForeignKeysAndReopenStoppedTrack() throws Exception {
        String id = db.begin(100000, 1);
        db.append(id, Collections.singletonList(point(1000))); stop(id);
        assertEquals(1, scalar("PRAGMA user_version"));
        assertEquals(1, scalar("PRAGMA foreign_keys"));
        assertEquals(1, scalar("PRAGMA synchronous"));
        try (Cursor c = db.getReadableDatabase().rawQuery("PRAGMA journal_mode", null)) {
            assertTrue(c.moveToFirst()); assertEquals("wal", c.getString(0));
        }
        db.close(); db = new TrackingDatabase(context, name); db.recoverInterrupted();
        JSObject page = db.read(id, 0, null);
        assertEquals("stopped", page.getJSObject("state").getString("phase"));
        assertEquals(1, page.getJSONArray("samples").length());
        assertEquals(101000, page.getJSONArray("samples").getJSONObject(0).getLong("measuredAt"));
        assertTrue(page.getJSONArray("samples").getJSONObject(0).isNull("speed"));
    }
    @Test public void invalidBatchRollsBackEveryPointAndSequenceCounter() throws Exception {
        String id = db.begin(1, 1); db.append(id, Collections.singletonList(point(10)));
        List<TrackingDatabase.Sample> batch = new ArrayList<>(); batch.add(point(20));
        batch.add(new TrackingDatabase.Sample(999, 17, 5, null, 30, 30, 40, 40, false, true));
        try { db.append(id, batch); fail("Constraint should abort transaction"); }
        catch (android.database.sqlite.SQLiteConstraintException expected) { }
        assertEquals(1, db.state(id).optLong("count"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM tracking_sample"));
        db.append(id, Collections.singletonList(point(40)));
        assertEquals(2, db.read(id, 1, null).getJSONArray("samples").getJSONObject(0).getLong("sequence"));
    }
    @Test public void multipleSessionsAreIndependentAndActiveSessionCannotBeReplaced() throws Exception {
        String a = db.begin(1, 1); db.append(a, Collections.singletonList(point(2)));
        try { db.begin(3, 3); fail("Must reject second active session"); } catch (IllegalStateException expected) { }
        stop(a); String b = db.begin(4, 4); db.append(b, Collections.singletonList(point(5)));
        assertNotEquals(a, b); assertEquals(2, db.sessions().length());
        assertEquals(1, db.state(a).optLong("count")); assertEquals(1, db.state(b).optLong("count"));
        assertEquals(a, db.read(a, 0, null).getJSONArray("samples").getJSONObject(0).getString("sessionId"));
    }
    @Test public void paginationExceedsOldCapsAndFreezesUpperBound() throws Exception {
        String id = db.begin(1, 1);
        List<TrackingDatabase.Sample> batch = new ArrayList<>();
        for (int i = 0; i < 33501; i++) batch.add(point(i + 2));
        db.append(id, batch);
        JSObject page = db.read(id, 0, null);
        long upper = page.optLong("throughSequence"), cursor = 0;
        db.append(id, Collections.singletonList(point(40000)));
        int pages = 0;
        do {
            page = db.read(id, cursor, upper); pages++;
            assertTrue(page.getJSONArray("samples").length() <= 500);
            for (int i = 0; i < page.getJSONArray("samples").length(); i++) {
                assertEquals(++cursor, page.getJSONArray("samples").getJSONObject(i).getLong("sequence"));
            }
        } while (page.getBoolean("hasMore"));
        assertEquals(33501, cursor); assertEquals(68, pages);
        assertEquals(33502, db.read(id, cursor, null).optLong("nextSequence"));
        assertEquals(500, db.read(id, 0, upper).getJSONArray("samples").length());
    }
    @Test public void deleteCascadesButRejectsRecordingAndPreservesOtherSession() {
        String a = db.begin(1, 1); db.append(a, Collections.singletonList(point(2)));
        try { db.delete(a); fail("Active deletion must fail"); } catch (IllegalStateException expected) { }
        stop(a); String b = db.begin(3, 3); db.append(b, Collections.singletonList(point(4)));
        db.delete(a); assertEquals(1, scalar("SELECT COUNT(*) FROM tracking_sample"));
        assertEquals(1, db.sessions().length()); assertEquals(1, db.state(b).optLong("count"));
    }
    @Test public void onlyExplicitProcessRecoveryInterruptsUnfinishedSessions() {
        String a = db.begin(1, 1); db.append(a, Collections.singletonList(point(2)));
        db.close(); db = new TrackingDatabase(context, name);
        assertEquals("recording", db.state(a).getString("phase")); // Reopen is not process recovery.
        db.recoverInterrupted(); db.recoverInterrupted();
        assertEquals("interrupted", db.state(a).getString("phase"));
        assertTrue(db.state(a).isNull("stoppedAt")); assertTrue(db.state(a).isNull("stoppedElapsedMs"));
        assertEquals(1, db.state(a).optLong("count"));
        String b = db.begin(3, 3); stop(b); db.recoverInterrupted();
        assertEquals("stopped", db.state(b).getString("phase"));
        db.delete(a); assertEquals(1, db.sessions().length());
    }
    @Test public void unfinishedTransactionIsRolledBackOnCloseAndErrorIsPreserved() {
        String id = db.begin(1, 1); db.append(id, Collections.singletonList(point(2)));
        SQLiteDatabase sql = db.getWritableDatabase();
        sql.beginTransactionNonExclusive();
        try { sql.execSQL("UPDATE tracking_session SET last_sequence=999 WHERE id=?", new Object[]{id}); }
        finally { sql.endTransaction(); } // No setTransactionSuccessful: model interruption before commit.
        db.close(); db = new TrackingDatabase(context, name); db.recoverInterrupted();
        assertEquals(1, db.state(id).optLong("count"));
        String second = db.begin(3, 3); db.finish(second, "error", "Testfel", 0, 0); stop(second);
        assertEquals("error", db.state(second).getString("phase"));
    }
    @Test public void corruptDatabaseIsNotSilentlyDeletedAndRecreated() throws Exception {
        db.close();
        java.io.File file = context.getDatabasePath(name);
        file.getParentFile().mkdirs();
        byte[] invalid = new byte[4096]; java.util.Arrays.fill(invalid, (byte) 42);
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) { out.write(invalid); }
        db = new TrackingDatabase(context, name);
        try { db.getWritableDatabase(); fail("Corruption must be reported"); }
        catch (android.database.sqlite.SQLiteException expected) { }
        assertTrue(file.exists()); assertEquals(4096, file.length());
        byte[] actual = new byte[4096];
        try (java.io.FileInputStream in = new java.io.FileInputStream(file)) { assertEquals(4096, in.read(actual)); }
        assertArrayEquals(invalid, actual);
    }
}
