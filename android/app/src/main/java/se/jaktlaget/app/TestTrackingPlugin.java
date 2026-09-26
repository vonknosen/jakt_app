package se.jaktlaget.app;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Intent;
import android.location.LocationManager;
import android.os.Build;
import android.os.SystemClock;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.location.LocationManagerCompat;
import androidx.lifecycle.Lifecycle;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import org.json.JSONObject;

@CapacitorPlugin(name = "TestTracking", permissions = {
    @Permission(alias = "location", strings = {Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}),
    @Permission(alias = "notifications", strings = {Manifest.permission.POST_NOTIFICATIONS})
})
public class TestTrackingPlugin extends Plugin {
    private final TrackingStore store = TrackingStore.INSTANCE;
    private boolean starting;

    @PluginMethod public void startTracking(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            if (starting || store.isActive()) { call.reject("En spårning startar eller pågår redan."); return; }
            starting = true;
            if (getPermissionState("location") != PermissionState.GRANTED) {
                requestPermissionForAlias("location", call, "locationResult");
            } else locationResult(call);
        });
    }

    @PermissionCallback private void locationResult(PluginCall call) {
        if (getPermissionState("location") != PermissionState.GRANTED) {
            fail(call, "Aktivera exakt plats för JaktApp för att köra GPS-testet."); return;
        }
        if (Build.VERSION.SDK_INT >= 33 && getPermissionState("notifications") != PermissionState.GRANTED) {
            requestPermissionForAlias("notifications", call, "notificationsResult");
        } else notificationsResult(call);
    }

    @PermissionCallback private void notificationsResult(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            String id = null;
            try {
                if (!getActivity().getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)) {
                    fail(call, "Öppna appen och tryck Starta igen."); return;
                }
                if (!NotificationManagerCompat.from(getContext()).areNotificationsEnabled()) {
                    fail(call, "Tillåt aviseringar för JaktApp innan testet startas."); return;
                }
                NotificationManager notifications = getContext().getSystemService(NotificationManager.class);
                if (Build.VERSION.SDK_INT >= 26 && notifications.getNotificationChannel("jaktapp_tracking_poc") != null
                        && notifications.getNotificationChannel("jaktapp_tracking_poc").getImportance() == NotificationManager.IMPORTANCE_NONE) {
                    fail(call, "Aktivera aviseringskanalen JaktApp testspårning i Androids inställningar."); return;
                }
                LocationManager locations = (LocationManager) getContext().getSystemService(android.content.Context.LOCATION_SERVICE);
                if (!LocationManagerCompat.isLocationEnabled(locations)) {
                    fail(call, "Slå på telefonens platstjänster och försök igen."); return;
                }
                id = store.begin(System.currentTimeMillis(), SystemClock.elapsedRealtime());
                ContextCompat.startForegroundService(getContext(),
                    new Intent(getContext(), TrackingService.class).putExtra("sessionId", id));
                starting = false;
                call.resolve(state(store.read(null, 0)));
            } catch (RuntimeException error) {
                if (id != null) {
                    store.requestStop(id, "Start misslyckades: " + error.getMessage());
                    store.finish(id, System.currentTimeMillis(), SystemClock.elapsedRealtime());
                }
                fail(call, "Spårning kunde inte starta: " + error.getMessage());
            }
        });
    }

    private void fail(PluginCall call, String message) { starting = false; call.reject(message); }

    @PluginMethod public void stopTracking(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            TrackingStore.Snapshot snapshot = store.read(null, 0);
            store.requestStop(snapshot.sessionId, "Testspårningen är stoppad.");
            boolean stopping = getContext().stopService(new Intent(getContext(), TrackingService.class));
            if (!stopping) store.finish(snapshot.sessionId, System.currentTimeMillis(), SystemClock.elapsedRealtime());
            call.resolve(state(store.read(null, 0)));
        });
    }

    @PluginMethod public void getTrackingState(PluginCall call) { call.resolve(state(store.read(null, 0))); }

    @PluginMethod public void readSamples(PluginCall call) {
        TrackingStore.Snapshot snapshot = store.read(call.getString("sessionId"), call.getInt("afterSequence", 0));
        JSArray points = new JSArray();
        for (TrackingStore.Sample sample : snapshot.samples) {
            JSObject point = new JSObject();
            point.put("sessionId", sample.sessionId); point.put("sequence", sample.sequence);
            point.put("latitude", sample.latitude); point.put("longitude", sample.longitude);
            point.put("accuracy", sample.accuracy); point.put("speed", sample.speed == null ? JSONObject.NULL : sample.speed);
            point.put("measuredAt", sample.measuredAt); point.put("measuredElapsedMs", sample.measuredElapsedMs);
            point.put("receivedAt", sample.receivedAt); point.put("receivedElapsedMs", sample.receivedElapsedMs);
            point.put("screenInteractive", sample.screenInteractive); point.put("deviceLocked", sample.deviceLocked);
            points.put(point);
        }
        JSObject result = new JSObject();
        result.put("state", state(snapshot)); result.put("samples", points);
        result.put("nextSequence", snapshot.nextSequence); result.put("hasMore", snapshot.hasMore);
        call.resolve(result);
    }

    private JSObject state(TrackingStore.Snapshot snapshot) {
        JSObject state = new JSObject();
        state.put("sessionId", snapshot.sessionId == null ? JSONObject.NULL : snapshot.sessionId);
        state.put("phase", snapshot.phase); state.put("message", snapshot.message);
        state.put("startedAt", snapshot.startedAt); state.put("stoppedAt", snapshot.stoppedAt);
        state.put("startedElapsedMs", snapshot.startedElapsedMs); state.put("stoppedElapsedMs", snapshot.stoppedElapsedMs);
        state.put("nowElapsedMs", SystemClock.elapsedRealtime());
        state.put("count", snapshot.count); state.put("capacity", snapshot.capacity);
        return state;
    }
}
