package se.jaktlaget.app;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Intent;
import android.location.LocationManager;
import android.os.Build;

import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.location.LocationManagerCompat;
import androidx.lifecycle.Lifecycle;

import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;


@CapacitorPlugin(name = "TestTracking", permissions = {
    @Permission(alias = "location", strings = {Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}),
    @Permission(alias = "notifications", strings = {Manifest.permission.POST_NOTIFICATIONS})
})
public class TestTrackingPlugin extends Plugin {
    private TrackingStore store;
    @Override public void load() { store = TrackingStore.get(getContext()); }
    private boolean starting;

    @PluginMethod public void startTracking(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            if (starting || store.activeId() != null || TrackingService.isStopping()) { call.reject("En spårning startar eller pågår redan."); return; }
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
                store.begin().whenComplete((state, error) -> getActivity().runOnUiThread(() -> {
                    if (error != null) { fail(call, error.getMessage()); return; }
                    String sessionId = state.getString("sessionId");
                    try {
                        if (!getActivity().getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)) {
                            throw new IllegalStateException("Öppna appen och starta igen.");
                        }
                        ContextCompat.startForegroundService(getContext(),
                            new Intent(getContext(), TrackingService.class).putExtra("sessionId", sessionId));
                        starting = false;
                        call.resolve(state);
                    } catch (RuntimeException launchError) {
                        store.finish(sessionId, "error", "Start misslyckades: " + launchError.getMessage(), 0, 0)
                            .whenComplete((unused, saveError) -> getActivity().runOnUiThread(() ->
                                fail(call, "Start misslyckades: " + launchError.getMessage()
                                    + (saveError == null ? "" : "; sparfel: " + saveError.getMessage()))));
                    }
                }));
            } catch (RuntimeException error) { fail(call, "Spårning kunde inte starta: " + error.getMessage()); }
        });
    }

    private void fail(PluginCall call, String message) { starting = false; call.reject(message); }

    private void resolve(PluginCall call, java.util.concurrent.CompletableFuture<JSObject> result) {
        result.whenComplete((value, error) -> {
            if (error != null) call.reject(error.getMessage()); else call.resolve(value);
        });
    }
    @PluginMethod public void stopTracking(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            if (starting) { call.reject("Vänta tills starten är klar."); return; }
            resolve(call, TrackingService.stopTracking(getContext()));
        });
    }
    @PluginMethod public void getTrackingState(PluginCall call) { resolve(call, store.state()); }
    @PluginMethod public void listSessions(PluginCall call) { resolve(call, store.sessions()); }
    @PluginMethod public void readSamples(PluginCall call) {
        resolve(call, store.read(call.getString("sessionId"), call.getLong("afterSequence", 0L), call.getLong("throughSequence")));
    }
    @PluginMethod public void deleteSession(PluginCall call) { resolve(call, store.delete(call.getString("sessionId"))); }
}
