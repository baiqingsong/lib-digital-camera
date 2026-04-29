package com.dawn.digital_camera.view;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.dawn.digital_camera.AppSettings;
import com.dawn.digital_camera.IUsbCameraTakePicListener;
import com.dawn.digital_camera.SessionView;
import com.dawn.digital_camera.UsbCameraManager;
import com.dawn.digital_camera.ptp.Camera;

/**
 * Base activity for USB camera sessions.
 *
 * <p>Subclasses should call {@link #getCameraManager()} to set additional options (e.g.
 * {@link UsbCameraManager#setLiveView}) before the activity starts.
 * Fragments that act as a {@link SessionView} must call
 * {@link #setSessionView(SessionView)} in their {@code onCreateView()} so that
 * camera lifecycle callbacks are forwarded to them.
 */
public abstract class SessionActivity extends AppCompatActivity {

    private UsbCameraManager cameraManager;
    private AppSettings settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = new AppSettings(this);
        cameraManager = new UsbCameraManager(this, null, provideTakePicListener());
    }

    /**
     * Override to supply a take-picture listener. Returns null by default.
     */
    protected IUsbCameraTakePicListener provideTakePicListener() {
        return null;
    }

    /** Returns the camera manager instance. */
    public UsbCameraManager getCameraManager() {
        return cameraManager;
    }

    /** Returns the currently connected camera, or null. */
    public Camera getCamera() {
        return cameraManager.getCamera();
    }

    /**
     * Redirect camera lifecycle callbacks to a fragment or other SessionView.
     * Called automatically by {@link SessionFragment} implementations.
     */
    public void setSessionView(SessionView view) {
        cameraManager.setSessionView(view);
    }

    /** Returns app-level persistent settings. */
    public AppSettings getSettings() {
        return settings;
    }

    @Override
    protected void onStart() {
        super.onStart();
        cameraManager.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        cameraManager.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        cameraManager.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        cameraManager.onStop();
    }
}
