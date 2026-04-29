package com.dawn.digital_camera;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.Looper;

import com.dawn.digital_camera.ptp.PtpConstants;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * USB camera presence monitor (plug/unplug) based on UsbManager broadcasts.
 *
 * <p>
 * This deliberately does NOT bind to PTP streaming callbacks, so it won't contend with the
 * preview page that needs exclusive CameraListener for live view.
 * </p>
 */
public final class UsbCameraPresenceMonitor {

    public enum PresenceState {
        UNKNOWN,
        PRESENT,
        NOT_PRESENT
    }

    public interface Listener {
        void onUsbCameraPresenceChanged(PresenceState state);

        /** Optional: provides the matched UsbDevice when PRESENT. */
        default void onUsbCameraDevice(UsbDevice device) {
            // no-op
        }
    }

    private static volatile UsbCameraPresenceMonitor instance;

    public static UsbCameraPresenceMonitor getInstance(Context context) {
        if (instance == null) {
            synchronized (UsbCameraPresenceMonitor.class) {
                if (instance == null) {
                    instance = new UsbCameraPresenceMonitor(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private final Context appContext;
    private final UsbManager usbManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Set<Listener> listeners = new CopyOnWriteArraySet<>();

    private volatile PresenceState state = PresenceState.UNKNOWN;
    private volatile UsbDevice lastDevice;
    private boolean registered;

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)
                    || UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                refreshNow();
            }
        }
    };

    private UsbCameraPresenceMonitor(Context appContext) {
        this.appContext = appContext;
        this.usbManager = (UsbManager) appContext.getSystemService(Context.USB_SERVICE);
    }

    public PresenceState getState() {
        return state;
    }

    public UsbDevice getLastDevice() {
        return lastDevice;
    }

    /** Start monitoring plug/unplug; safe to call multiple times. */
    public synchronized void start() {
        if (!registered) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
            filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
            appContext.registerReceiver(usbReceiver, filter);
            registered = true;
        }
        refreshNow();
    }

    /** Stop monitoring; usually only call when app exits. */
    public synchronized void stop() {
        if (registered) {
            appContext.unregisterReceiver(usbReceiver);
            registered = false;
        }
        state = PresenceState.UNKNOWN;
        lastDevice = null;
    }

    public void addListener(Listener listener) {
        if (listener == null) {
            return;
        }
        listeners.add(listener);
        // immediate replay
        mainHandler.post(() -> {
            listener.onUsbCameraPresenceChanged(state);
            if (state == PresenceState.PRESENT && lastDevice != null) {
                listener.onUsbCameraDevice(lastDevice);
            }
        });
    }

    public void removeListener(Listener listener) {
        if (listener == null) {
            return;
        }
        listeners.remove(listener);
    }

    /** Refresh presence based on current UsbManager device list. */
    public void refreshNow() {
        UsbDevice matched = lookupCompatibleDevice();
        PresenceState newState = matched != null ? PresenceState.PRESENT : PresenceState.NOT_PRESENT;
        UsbDevice oldDevice = lastDevice;
        if (newState == state && oldDevice == matched) {
            return;
        }
        state = newState;
        lastDevice = matched;

        mainHandler.post(() -> {
            for (Listener l : listeners) {
                l.onUsbCameraPresenceChanged(state);
                if (state == PresenceState.PRESENT && matched != null) {
                    l.onUsbCameraDevice(matched);
                }
            }
        });
    }

    private UsbDevice lookupCompatibleDevice() {
        Map<String, UsbDevice> deviceList = usbManager.getDeviceList();
        for (Map.Entry<String, UsbDevice> e : deviceList.entrySet()) {
            UsbDevice d = e.getValue();
            if (d != null && PtpConstants.isCompatibleVendor(d.getVendorId())) {
                return d;
            }
        }
        return null;
    }
}

