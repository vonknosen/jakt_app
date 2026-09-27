package se.jaktlaget.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.SystemClock;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import java.util.List;
import java.util.UUID;
import org.json.JSONObject;

/** Private durable data. Only TrackingStore's serial worker uses this helper in production. */
public final class TrackingDatabase extends SQLiteOpenHelper {
    public static final String NAME = "tracking.db";
    public static final int PAGE_SIZE = 500;

    public TrackingDatabase(Context context) { this(context, NAME); }
    TrackingDatabase(Context context, String name) {
        super(context.getApplicationContext(), name, null, 1, database ->
            android.util.Log.e("JaktAppStorage", "Databasfel: filen bevaras; ingen automatisk radering."));
        setWriteAheadLoggingEnabled(true);
    }

    @Override public void onConfigure(SQLiteDatabase db) {
        db.setForeignKeyConstraintsEnabled(true);
        db.execSQL("PRAGMA synchronous=NORMAL");
    }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE tracking_session (id TEXT PRIMARY KEY NOT NULL, "
            + "started_at INTEGER NOT NULL, started_elapsed_ms INTEGER NOT NULL, "
            + "stopped_at INTEGER, stopped_elapsed_ms INTEGER, "
            + "status TEXT NOT NULL CHECK(status IN ('recording','stopped','interrupted','error')), "
            + "message TEXT NOT NULL, last_sequence INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE tracking_sample (session_id TEXT NOT NULL, sequence INTEGER NOT NULL CHECK(sequence>0), "
            + "latitude REAL NOT NULL CHECK(latitude BETWEEN -90 AND 90), "
            + "longitude REAL NOT NULL CHECK(longitude BETWEEN -180 AND 180), "
            + "accuracy REAL NOT NULL CHECK(accuracy>=0), speed REAL, measured_at INTEGER NOT NULL, "
            + "measured_elapsed_ms INTEGER NOT NULL, received_at INTEGER NOT NULL, received_elapsed_ms INTEGER NOT NULL, "
            + "screen_interactive INTEGER NOT NULL, device_locked INTEGER NOT NULL, "
            + "PRIMARY KEY(session_id,sequence), FOREIGN KEY(session_id) REFERENCES tracking_session(id) ON DELETE CASCADE)");
        db.execSQL("CREATE INDEX tracking_session_started ON tracking_session(started_at DESC,id)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("Databasmigrering saknas: " + oldVersion + " → " + newVersion);
    }

    /** Called once by the process singleton, never on a WebView reload or ordinary DB reopen. */
    public void recoverInterrupted() {
        ContentValues values = new ContentValues();
        values.put("status", "interrupted");
        values.put("message", "Avbruten vid tidigare processavslut. Sparade punkter finns kvar; exakt sluttid är okänd.");
        getWritableDatabase().update("tracking_session", values, "status='recording'", null);
    }

    public String begin(long now, long elapsed) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransactionNonExclusive();
        try {
            try (Cursor c = db.rawQuery("SELECT id FROM tracking_session WHERE status='recording' LIMIT 1", null)) {
                if (c.moveToFirst()) throw new IllegalStateException("En spårning pågår redan.");
            }
            String id = UUID.randomUUID().toString();
            ContentValues values = new ContentValues();
            values.put("id", id); values.put("started_at", now); values.put("started_elapsed_ms", elapsed);
            values.put("status", "recording"); values.put("message", "Spårning pågår. Punkter sparas lokalt.");
            db.insertOrThrow("tracking_session", null, values);
            db.setTransactionSuccessful();
            return id;
        } finally { db.endTransaction(); }
    }

    /** One transaction per native LocationResult, including its durable sequence counter. */
    public void append(String id, List<Sample> samples) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransactionNonExclusive();
        try {
            JSObject session = state(id);
            if (!"recording".equals(session.getString("phase"))) throw new IllegalStateException("Sessionen är inte aktiv.");
            long sequence = session.optLong("count", 0L);
            for (Sample p : samples) {
                ContentValues v = new ContentValues();
                v.put("session_id", id); v.put("sequence", ++sequence);
                v.put("latitude", p.latitude); v.put("longitude", p.longitude); v.put("accuracy", p.accuracy);
                if (p.speed == null) v.putNull("speed"); else v.put("speed", p.speed);
                v.put("measured_at", p.measuredAt); v.put("measured_elapsed_ms", p.measuredElapsedMs);
                v.put("received_at", p.receivedAt); v.put("received_elapsed_ms", p.receivedElapsedMs);
                v.put("screen_interactive", p.screenInteractive ? 1 : 0); v.put("device_locked", p.deviceLocked ? 1 : 0);
                db.insertOrThrow("tracking_sample", null, v);
            }
            ContentValues values = new ContentValues(); values.put("last_sequence", sequence);
            db.update("tracking_session", values, "id=?", new String[]{id});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public void finish(String id, String status, String message, long now, long elapsed) {
        if (!java.util.Arrays.asList("stopped", "interrupted", "error").contains(status)) throw new IllegalArgumentException("Fel slutstatus");
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransactionNonExclusive();
        try {
            ContentValues v = new ContentValues(); v.put("status", status); v.put("message", message);
            if ("stopped".equals(status)) { v.put("stopped_at", now); v.put("stopped_elapsed_ms", elapsed); }
            // Never overwrite an earlier error, or invent an exact stop time after an interruption.
            db.update("tracking_session", v, "id=? AND status='recording'", new String[]{id});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public JSObject state(String id) {
        String query = "SELECT * FROM tracking_session";
        String[] args = null;
        if (id != null) { query += " WHERE id=?"; args = new String[]{id}; }
        else query += " ORDER BY started_at DESC,rowid DESC LIMIT 1";
        try (Cursor c = getReadableDatabase().rawQuery(query, args)) {
            if (c.moveToFirst()) return session(c);
            if (id != null) throw new IllegalArgumentException("Spåret finns inte längre.");
            JSObject empty = new JSObject(); empty.put("sessionId", JSONObject.NULL); empty.put("phase", "idle");
            empty.put("message", "Inga sparade spår."); empty.put("count", 0);
            empty.put("startedAt", 0); empty.put("startedElapsedMs", 0);
            empty.put("stoppedAt", JSONObject.NULL); empty.put("stoppedElapsedMs", JSONObject.NULL);
            empty.put("nowElapsedMs", SystemClock.elapsedRealtime());
            return empty;
        }
    }
    private JSObject session(Cursor c) {
        JSObject s = new JSObject();
        s.put("sessionId", c.getString(c.getColumnIndexOrThrow("id")));
        s.put("phase", c.getString(c.getColumnIndexOrThrow("status")));
        s.put("message", c.getString(c.getColumnIndexOrThrow("message")));
        s.put("count", c.getLong(c.getColumnIndexOrThrow("last_sequence")));
        String[] columns = {"started_at", "started_elapsed_ms", "stopped_at", "stopped_elapsed_ms"};
        String[] names = {"startedAt", "startedElapsedMs", "stoppedAt", "stoppedElapsedMs"};
        for (int i = 0; i < columns.length; i++) {
            int col = c.getColumnIndexOrThrow(columns[i]); s.put(names[i], c.isNull(col) ? JSONObject.NULL : c.getLong(col));
        }
        s.put("nowElapsedMs", SystemClock.elapsedRealtime());
        return s;
    }
    public JSArray sessions() {
        JSArray list = new JSArray();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT * FROM tracking_session ORDER BY started_at DESC,rowid DESC", null)) {
            while (c.moveToNext()) list.put(session(c));
        }
        return list;
    }
    public JSObject read(String id, long after, Long through) {
        if (after < 0) throw new IllegalArgumentException("Fel läsposition.");
        JSObject s = state(id);
        String sessionId = s.getString("sessionId");
        long count = s.optLong("count", 0L);
        long upper = through == null ? count : Math.min(through, count);
        if (upper < after) throw new IllegalArgumentException("Fel läsgräns.");
        JSArray points = new JSArray();
        long next = after;
        if (sessionId != null) {
            try (Cursor c = getReadableDatabase().rawQuery(
                    "SELECT * FROM tracking_sample WHERE session_id=? AND sequence>? AND sequence<=? ORDER BY sequence LIMIT " + PAGE_SIZE,
                    new String[]{sessionId, Long.toString(after), Long.toString(upper)})) {
                while (c.moveToNext()) {
                    JSObject p = new JSObject(); p.put("sessionId", sessionId);
                    next = c.getLong(c.getColumnIndexOrThrow("sequence")); p.put("sequence", next);
                    for (String key : new String[]{"latitude", "longitude", "accuracy", "speed"}) {
                        int col = c.getColumnIndexOrThrow(key); p.put(key, c.isNull(col) ? JSONObject.NULL : c.getDouble(col));
                    }
                    String[] cols = {"measured_at", "measured_elapsed_ms", "received_at", "received_elapsed_ms"};
                    String[] keys = {"measuredAt", "measuredElapsedMs", "receivedAt", "receivedElapsedMs"};
                    for (int i = 0; i < cols.length; i++) p.put(keys[i], c.getLong(c.getColumnIndexOrThrow(cols[i])));
                    p.put("screenInteractive", c.getInt(c.getColumnIndexOrThrow("screen_interactive")) != 0);
                    p.put("deviceLocked", c.getInt(c.getColumnIndexOrThrow("device_locked")) != 0);
                    points.put(p);
                }
            }
        }
        JSObject page = new JSObject(); page.put("state", s); page.put("samples", points);
        page.put("nextSequence", next); page.put("throughSequence", upper); page.put("hasMore", next < upper);
        return page;
    }
    public void delete(String id) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransactionNonExclusive();
        try {
            if ("recording".equals(state(id).getString("phase"))) throw new IllegalStateException("Pågående spår får inte raderas.");
            db.delete("tracking_session", "id=?", new String[]{id});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public static final class Sample {
        final double latitude, longitude, accuracy;
        final Double speed;
        final long measuredAt, measuredElapsedMs, receivedAt, receivedElapsedMs;
        final boolean screenInteractive, deviceLocked;
        Sample(double lat, double lon, double accuracy, Double speed, long measuredAt, long measuredElapsed,
                long receivedAt, long receivedElapsed, boolean interactive, boolean locked) {
            this.latitude = lat; this.longitude = lon; this.accuracy = accuracy; this.speed = speed;
            this.measuredAt = measuredAt; this.measuredElapsedMs = measuredElapsed;
            this.receivedAt = receivedAt; this.receivedElapsedMs = receivedElapsed;
            this.screenInteractive = interactive; this.deviceLocked = locked;
        }
    }
}
