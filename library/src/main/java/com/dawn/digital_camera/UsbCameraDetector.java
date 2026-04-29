package com.dawn.digital_camera;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * USB camera connection state monitor.
 *
 * <p>
 * IMPORTANT: This class does NOT call {@code PtpService.setCameraListener(...)} anymore.
 * The preview page (UsbCameraManager) owns the PTP listener to receive live view frames.
 * </p>
 */
public final class UsbCameraDetector {

    private static final String TAG = "UsbCameraDetector";

    public enum State {
        UNKNOWN,
        CONNECTING,
        CONNECTED,
        NOT_FOUND,
        ERROR
    }

    public interface Listener {
        void onUsbCameraStateChanged(State state);

        default void onUsbCameraError(String message) {
            // no-op
        }
    }

    private static volatile UsbCameraDetector instance;

    public static UsbCameraDetector getInstance(Context context) {
        if (instance == null) {
            synchronized (UsbCameraDetector.class) {
                if (instance == null) {
                    instance = new UsbCameraDetector(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Set<Listener> listeners = new CopyOnWriteArraySet<>();

    private final UsbCameraPresenceMonitor presenceMonitor;

    private volatile State state = State.UNKNOWN;
    private volatile String lastError;

    private UsbCameraDetector(Context appContext) {
        this.presenceMonitor = UsbCameraPresenceMonitor.getInstance(appContext);
        this.presenceMonitor.start();
        // initialize state based on current presence
        refreshPresenceState();
        this.presenceMonitor.addListener(new UsbCameraPresenceMonitor.Listener() {
            @Override
            public void onUsbCameraPresenceChanged(UsbCameraPresenceMonitor.PresenceState presence) {
                refreshPresenceState();
            }
        });
    }

    public State getState() {
        return state;
    }

    public boolean isConnected() {
        return state == State.CONNECTED;
    }

    public String getLastError() {
        return lastError;
    }

    public void addListener(Listener listener) {
        if (listener == null) {
            return;
        }
        listeners.add(listener);
        mainHandler.post(() -> {
            listener.onUsbCameraStateChanged(state);
            if (state == State.ERROR && lastError != null) {
                listener.onUsbCameraError(lastError);
            }
        });
    }

    public void stop(){
        if (presenceMonitor!=null)
            presenceMonitor.stop();
    }
    public void removeListener(Listener listener) {
        if (listener == null) {
            return;
        }
        listeners.remove(listener);
    }

    /**
     * Called by UI (e.g., HomeActivity) to force refresh of presence state.
     * This does NOT start PTP connection.
     */
    public void refreshPresence() {
        presenceMonitor.refreshNow();
    }

    /**
     * Called by preview page to reflect connection lifecycle.
     */
    void reportConnecting() {
        setState(State.CONNECTING, null);
    }

    void reportConnected() {
        setState(State.CONNECTED, null);
    }

    void reportDisconnected() {
        refreshPresenceState();
    }

    void reportError(String message) {
        setState(State.ERROR, message);
    }

    public void onNewIntent(Context context, Intent intent) {
        // No-op here: PTP permission handling is owned by UsbCameraManager/PtpUsbService.
    }

    private void refreshPresenceState() {
        UsbCameraPresenceMonitor.PresenceState presence = presenceMonitor.getState();
        if (presence == UsbCameraPresenceMonitor.PresenceState.PRESENT) {
            // Device present but not necessarily connected to PTP.
            if (state != State.CONNECTED && state != State.CONNECTING) {
                setState(State.UNKNOWN, null);
            }
        } else if (presence == UsbCameraPresenceMonitor.PresenceState.NOT_PRESENT) {
            setState(State.NOT_FOUND, null);
        } else {
            setState(State.UNKNOWN, null);
        }
    }

    private void setState(State newState, String error) {
        boolean changed = this.state != newState || (error != null && !error.equals(this.lastError));
        this.state = newState;
        this.lastError = error;
        if (!changed) {
            return;
        }

        mainHandler.post(() -> {
            for (Listener l : listeners) {
                l.onUsbCameraStateChanged(newState);
                if (newState == State.ERROR && error != null) {
                    l.onUsbCameraError(error);
                }
            }
        });
    }
}
