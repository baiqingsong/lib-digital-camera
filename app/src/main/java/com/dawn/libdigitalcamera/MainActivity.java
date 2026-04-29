package com.dawn.libdigitalcamera;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.dawn.digital_camera.IUsbCameraTakePicListener;
import com.dawn.digital_camera.PictureView;
import com.dawn.digital_camera.UsbCameraDetector;
import com.dawn.digital_camera.UsbCameraManager;
import com.dawn.digital_camera.UsbCameraStateListener;

/**
 * Demo Activity for lib-digital-camera.
 *
 * <p>Shows how to integrate {@link UsbCameraManager} to achieve:
 * <ul>
 *   <li>Automatic USB camera connection / reconnection</li>
 *   <li>Live view streaming onto {@link PictureView}</li>
 *   <li>Remote shutter trigger via {@link UsbCameraManager#takePicture()}</li>
 * </ul>
 * </p>
 */
public class MainActivity extends AppCompatActivity {

    private UsbCameraManager cameraManager;
    private TextView tvStatus;
    private Button btnTakePicture;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tv_status);
        btnTakePicture = findViewById(R.id.btn_take_picture);
        PictureView pictureView = findViewById(R.id.picture_view);

        // Initialize the camera manager with the live-view surface and shot callback
        cameraManager = new UsbCameraManager(this, pictureView, new IUsbCameraTakePicListener() {
            @Override
            public void onTakePic(Bitmap bitmap) {
                runOnUiThread(() ->
                        Toast.makeText(MainActivity.this, "拍照成功", Toast.LENGTH_SHORT).show()
                );
            }
        });

        // Observe USB camera connection state
        cameraManager.addUsbCameraStateListener(new UsbCameraStateListener() {
            @Override
            public void onUsbCameraStateChanged(UsbCameraDetector.State state) {
                runOnUiThread(() -> updateStatus(state));
            }

            @Override
            public void onUsbCameraError(String message) {
                runOnUiThread(() -> tvStatus.setText(getString(R.string.status_error) + ": " + message));
            }
        });

        btnTakePicture.setOnClickListener(v -> cameraManager.takePicture());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Forward USB permission intents to the manager
        cameraManager.onNewIntent(intent);
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

    private void updateStatus(UsbCameraDetector.State state) {
        switch (state) {
            case CONNECTED:
                tvStatus.setText(R.string.status_connected);
                btnTakePicture.setEnabled(true);
                break;
            case CONNECTING:
                tvStatus.setText(R.string.status_connecting);
                btnTakePicture.setEnabled(false);
                break;
            case NOT_FOUND:
            case UNKNOWN:
                tvStatus.setText(R.string.status_not_connected);
                btnTakePicture.setEnabled(false);
                break;
            case ERROR:
                tvStatus.setText(R.string.status_error);
                btnTakePicture.setEnabled(false);
                break;
        }
    }
}
