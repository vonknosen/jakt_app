package se.jaktlaget.app;

import android.Manifest;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;
import com.getcapacitor.JSObject;
import com.google.android.gms.location.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** GPS delivery is independent of WebView. Accepted batches are committed by TrackingStore. */
public class TrackingService extends Service {
    public static final String STOP = "se.jaktlaget.app.STOP_TRACKING";
    private static final String CHANNEL = "jaktapp_tracking_poc";
    private static final int NOTIFICATION_ID = 4101;
    private static TrackingService live; // Access only on Android's main thread.
    private final Handler main = new Handler(Looper.getMainLooper());
    private TrackingStore store;
    private FusedLocationProviderClient client;
    private LocationCallback callback;
    private String sessionId;
    private boolean closing;
    private CompletableFuture<JSObject> stopResult;
    private JSObject finalState;
    private Throwable finalError;
    private boolean saved;
    private boolean destroyed;

    public static boolean isStopping() { return live != null && live.closing; }
    @Override public void onCreate() { super.onCreate(); store = TrackingStore.get(this); live = this; }
    @Override public IBinder onBind(Intent intent) { return null; }

    /** Called on main: closes admission before queueing the durable stop barrier. */
    public static CompletableFuture<JSObject> stopTracking(Context context) {
        if (live != null && live.sessionId != null) return live.close("stopped", "Stoppat och sparat.");
        TrackingStore store = TrackingStore.get(context);
        return store.finish(store.activeId(), "stopped", "Stoppat och sparat.",
            System.currentTimeMillis(), SystemClock.elapsedRealtime());
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }
        if (STOP.equals(intent.getAction())) {
            if (sessionId != null && sessionId.equals(intent.getStringExtra("sessionId"))) close("stopped", "Stoppat och sparat från aviseringen.");
            else if (sessionId == null) stopSelf();
            return START_NOT_STICKY;
        }
        if (sessionId != null) return START_NOT_STICKY;
        sessionId = intent.getStringExtra("sessionId");
        try {
            // Must enter foreground promptly, even if a stop raced the service launch.
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(),
                Build.VERSION.SDK_INT >= 29 ? ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION : 0);
            if (!store.accepts(sessionId)) { close("stopped", "Stoppat och sparat."); return START_NOT_STICKY; }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                close("error", "Exakt platsbehörighet saknas."); return START_NOT_STICKY;
            }
            client = LocationServices.getFusedLocationProviderClient(this);
            PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
            KeyguardManager keyguard = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
            callback = new LocationCallback() {
                @Override public void onLocationResult(LocationResult result) {
                    if (closing || !store.accepts(sessionId)) return;
                    long receivedAt = System.currentTimeMillis(), receivedElapsed = SystemClock.elapsedRealtime();
                    boolean interactive = power.isInteractive(), locked = keyguard.isKeyguardLocked();
                    List<TrackingDatabase.Sample> batch = new ArrayList<>();
                    for (android.location.Location p : result.getLocations()) {
                        batch.add(new TrackingDatabase.Sample(p.getLatitude(), p.getLongitude(), p.getAccuracy(),
                            p.hasSpeed() ? (double) p.getSpeed() : null, p.getTime(), p.getElapsedRealtimeNanos() / 1_000_000,
                            receivedAt, receivedElapsed, interactive, locked));
                    }
                    store.append(sessionId, batch).whenComplete((unused, error) -> {
                        if (error != null) main.post(() -> close("error", "Skrivfel: " + error.getMessage()));
                    });
                }
            };
            LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000)
                .setMinUpdateIntervalMillis(1000).setMinUpdateDistanceMeters(0)
                .setMaxUpdateDelayMillis(0).setMaxUpdateAgeMillis(0).build();
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
                .addOnSuccessListener(unused -> { if (closing) client.removeLocationUpdates(callback); })
                .addOnFailureListener(error -> close("error", "GPS kunde inte starta: " + error.getMessage()));
        } catch (RuntimeException error) { close("error", "Spårning kunde inte starta: " + error.getMessage()); }
        return START_NOT_STICKY;
    }

    private CompletableFuture<JSObject> close(String status, String message) {
        if (closing) return stopResult;
        closing = true;
        stopResult = new CompletableFuture<>();
        if (client != null && callback != null) client.removeLocationUpdates(callback);
        store.finish(sessionId, status, message, System.currentTimeMillis(), SystemClock.elapsedRealtime())
            .whenComplete((state, error) -> main.post(() -> {
                finalState = state; finalError = error; saved = true;
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf(); // onDestroy completes the UI acknowledgement, after the durable barrier.
                if (destroyed) acknowledge();
            }));
        return stopResult;
    }
    private void acknowledge() {
        if (finalError != null) stopResult.completeExceptionally(finalError);
        else stopResult.complete(finalState);
    }
    @Override public void onDestroy() {
        destroyed = true;
        if (client != null && callback != null) client.removeLocationUpdates(callback);
        if (live == this) live = null;
        if (!closing) {
            closing = true;
            store.finish(sessionId, "interrupted", "Tjänsten avslutades oväntat. Sparade punkter finns kvar.", 0, 0);
        } else if (saved && stopResult != null) {
            if (finalError != null) stopResult.completeExceptionally(finalError);
            else stopResult.complete(finalState);
        }
        super.onDestroy();
    }
    private Notification notification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(new NotificationChannel(CHANNEL,
            "JaktApp testspårning", NotificationManager.IMPORTANCE_LOW));
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        Intent stop = new Intent(this, TrackingService.class).setAction(STOP).putExtra("sessionId", sessionId);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        return new NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_tracking).setContentTitle("JaktApp – spårning pågår")
            .setContentText("GPS sparas på telefonen. Tryck Stoppa för att avsluta.")
            .setContentIntent(PendingIntent.getActivity(this, 0, open, flags))
            .addAction(R.drawable.ic_tracking, "Stoppa", PendingIntent.getService(this, 1, stop, flags))
            .setOngoing(true).setOnlyAlertOnce(true).setCategory(NotificationCompat.CATEGORY_SERVICE).build();
    }
}
