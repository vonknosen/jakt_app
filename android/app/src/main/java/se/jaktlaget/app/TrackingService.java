package se.jaktlaget.app;

import android.Manifest;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

/** Started service: GPS and buffering continue without the Capacitor bridge. */
public class TrackingService extends Service {
    public static final String STOP = "se.jaktlaget.app.STOP_TRACKING";
    private static final String CHANNEL = "jaktapp_tracking_poc";
    private static final int NOTIFICATION_ID = 4101;
    private final TrackingStore store = TrackingStore.INSTANCE;
    private FusedLocationProviderClient client;
    private LocationCallback callback;
    private String sessionId;
    private boolean closing;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }
        if (STOP.equals(intent.getAction())) {
            String requestedId = intent.getStringExtra("sessionId");
            if (sessionId != null && sessionId.equals(requestedId)) close("Stoppad från aviseringen.");
            else if (sessionId == null) stopSelf();
            return START_NOT_STICKY;
        }
        if (sessionId != null) return START_NOT_STICKY;
        sessionId = intent.getStringExtra("sessionId");
        if (!store.accepts(sessionId)) { stopSelf(); return START_NOT_STICKY; }
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(),
                Build.VERSION.SDK_INT >= 29 ? ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION : 0);
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                close("Exakt platsbehörighet saknas. Testet stoppades.");
                return START_NOT_STICKY;
            }
            client = LocationServices.getFusedLocationProviderClient(this);
            PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
            KeyguardManager keyguard = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
            callback = new LocationCallback() {
                @Override public void onLocationResult(LocationResult result) {
                    if (closing || !store.accepts(sessionId)) return;
                    long receivedAt = System.currentTimeMillis();
                    long receivedElapsed = SystemClock.elapsedRealtime();
                    boolean interactive = power.isInteractive();
                    boolean locked = keyguard.isKeyguardLocked();
                    for (android.location.Location point : result.getLocations()) {
                        boolean keepGoing = store.append(sessionId, point.getLatitude(), point.getLongitude(),
                            point.getAccuracy(), point.hasSpeed() ? (double) point.getSpeed() : null,
                            point.getTime(), point.getElapsedRealtimeNanos() / 1_000_000,
                            receivedAt, receivedElapsed, interactive, locked);
                        if (!keepGoing) { close(null); break; }
                    }
                }
            };
            // Preliminary field-test values. No batching, distance filter or cached initial fix.
            LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000)
                .setMinUpdateIntervalMillis(1000).setMinUpdateDistanceMeters(0)
                .setMaxUpdateDelayMillis(0).setMaxUpdateAgeMillis(0).build();
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
                .addOnSuccessListener(unused -> {
                    if (closing || !store.accepts(sessionId)) {
                        client.removeLocationUpdates(callback);
                    } else store.running(sessionId);
                })
                .addOnFailureListener(error -> close("GPS kunde inte starta: " + error.getMessage()));
        } catch (RuntimeException error) {
            close("Spårning kunde inte starta: " + error.getMessage());
        }
        return START_NOT_STICKY;
    }

    private Notification notification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(new NotificationChannel(CHANNEL,
                "JaktApp testspårning", NotificationManager.IMPORTANCE_LOW));
        }
        Intent open = new Intent(this, MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        Intent stop = new Intent(this, TrackingService.class).setAction(STOP)
            .putExtra("sessionId", sessionId);
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        return new NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_tracking).setContentTitle("JaktApp – spårning pågår")
            .setContentText("GPS-test med släckt skärm. Tryck Stoppa för att avsluta.")
            .setContentIntent(PendingIntent.getActivity(this, 0, open, pendingFlags))
            .addAction(R.drawable.ic_tracking, "Stoppa", PendingIntent.getService(this, 1, stop, pendingFlags))
            .setOngoing(true).setOnlyAlertOnce(true).setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build();
    }

    private void close(String reason) {
        if (closing) return;
        closing = true;
        if (reason != null) store.requestStop(sessionId, reason);
        if (client != null && callback != null) client.removeLocationUpdates(callback);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override public void onDestroy() {
        closing = true;
        if (client != null && callback != null) client.removeLocationUpdates(callback);
        store.finish(sessionId, System.currentTimeMillis(), SystemClock.elapsedRealtime());
        super.onDestroy();
    }
}
