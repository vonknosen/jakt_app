package se.jaktlaget.app;

import android.content.Context;
import android.os.SystemClock;
import com.getcapacitor.JSObject;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One process-wide owner. All DB access and accepted writes run FIFO, independently of WebView. */
public final class TrackingStore {
    private static TrackingStore instance;
    public static synchronized TrackingStore get(Context context) {
        if (instance == null) instance = new TrackingStore(context);
        return instance;
    }
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final TrackingDatabase database;
    private boolean initialized;
    private volatile String activeId;
    private volatile String failedId;
    private volatile String failure;
    private TrackingStore(Context context) { this(context, TrackingDatabase.NAME); }
    TrackingStore(Context context, String databaseName) { database = new TrackingDatabase(context, databaseName); }
    // Used by isolated Android tests; production owns the store for the process lifetime.
    CompletableFuture<Void> closeForTest() {
        CompletableFuture<Void> result = submit(() -> { database.close(); return null; });
        worker.shutdown(); return result;
    }
    public String activeId() { return activeId; }
    public boolean accepts(String id) { return id != null && id.equals(activeId) && !id.equals(failedId); }

    private <T> CompletableFuture<T> submit(Callable<T> task) {
        CompletableFuture<T> result = new CompletableFuture<>();
        worker.execute(() -> {
            try {
                if (!initialized) { database.recoverInterrupted(); initialized = true; }
                result.complete(task.call());
            } catch (Exception error) { result.completeExceptionally(error); }
        });
        return result;
    }
    public CompletableFuture<JSObject> begin() {
        return submit(() -> {
            if (activeId != null) throw new IllegalStateException("En spårning startar eller pågår redan.");
            activeId = database.begin(System.currentTimeMillis(), SystemClock.elapsedRealtime());
            return database.state(activeId);
        });
    }
    public CompletableFuture<Void> append(String id, List<TrackingDatabase.Sample> points) {
        return submit(() -> {
            if (!accepts(id)) throw new IllegalStateException("Spårningen tar inte emot fler punkter.");
            try { database.append(id, points); }
            catch (RuntimeException error) {
                failedId = id; failure = "Skrivfel. Alla mottagna punkter kunde inte sparas: " + error.getMessage();
                try { database.finish(id, "error", failure, 0, 0); }
                catch (RuntimeException secondary) { error.addSuppressed(secondary); }
                throw error;
            }
            return null;
        });
    }
    public CompletableFuture<JSObject> finish(String id, String status, String message, long now, long elapsed) {
        return submit(() -> {
            if (id == null) return database.state(null);
            try {
                if (id.equals(failedId)) { statusError(id); throw new IllegalStateException(failure); }
                database.finish(id, status, message, now, elapsed);
                return database.state(id);
            } catch (RuntimeException error) {
                failedId = id; failure = "Sparningen kunde inte slutföras: " + error.getMessage();
                try { statusError(id); } catch (RuntimeException secondary) { error.addSuppressed(secondary); }
                throw error;
            } finally { if (id.equals(activeId)) activeId = null; }
        });
    }
    private void statusError(String id) { database.finish(id, "error", failure, 0, 0); }
    private JSObject overlay(JSObject state) {
        if (failedId != null && failedId.equals(state.getString("sessionId"))) {
            state.put("phase", "error"); state.put("message", failure);
        }
        return state;
    }
    public CompletableFuture<JSObject> state() { return submit(() -> overlay(database.state(activeId))); }
    public CompletableFuture<JSObject> sessions() {
        return submit(() -> {
            JSObject result = new JSObject(); result.put("sessions", database.sessions());
            result.put("activeSessionId", activeId == null ? org.json.JSONObject.NULL : activeId);
            result.put("storageError", failure == null ? org.json.JSONObject.NULL : failure);
            return result;
        });
    }
    public CompletableFuture<JSObject> read(String id, long after, Long through) {
        return submit(() -> {
            JSObject page = database.read(id, after, through);
            page.put("state", overlay(page.getJSObject("state")));
            return page;
        });
    }
    public CompletableFuture<JSObject> delete(String id) {
        return submit(() -> {
            if (id == null || id.equals(activeId)) throw new IllegalArgumentException("Pågående eller ospecificerat spår får inte raderas.");
            database.delete(id);
            return new JSObject();
        });
    }
}
