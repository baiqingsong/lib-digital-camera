package com.dawn.digital_camera;

/**
 * Listener for USB camera availability/connection state.
 *
 * Note: callbacks are dispatched on the main thread.
 */
public interface UsbCameraStateListener {

    void onUsbCameraStateChanged(UsbCameraDetector.State state);

    /**
     * Optional: provides additional error messages when state becomes ERROR.
     */
    default void onUsbCameraError(String message) {
        // no-op
    }
}
