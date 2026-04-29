package com.dawn.digital_camera;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;

import com.dawn.digital_camera.ptp.Camera;
import com.dawn.digital_camera.ptp.FocusPoint;
import com.dawn.digital_camera.ptp.PtpConstants;
import com.dawn.digital_camera.ptp.PtpService;
import com.dawn.digital_camera.ptp.model.LiveViewData;
import com.dawn.digital_camera.ptp.model.ObjectInfo;

public class UsbCameraManager implements SessionView, GestureDetector.GestureHandler,
        Camera.RetrieveImageInfoListener, Camera.CameraListener {
    private final String TAG = UsbCameraManager.class.getSimpleName();

    private static final int JUSTCAPTUREDRESETRUNNER = 1;
    private static final int LIVEVIEWRESTARTERRUNNER = 2;
    private static final int USB_RECONNECT_RUNNER = 3;
    private static final long FIRST_CONNECT_KICK_MS = 150;
    private static final long RECONNECT_RETRY_INTERVAL_MS = 350;
    private static final int RECONNECT_MAX_ATTEMPTS = 10;

    private final Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case JUSTCAPTUREDRESETRUNNER:
                    justCaptured = false;
                    break;
                case LIVEVIEWRESTARTERRUNNER:
                    startLiveViewAgain();
                    break;
                case USB_RECONNECT_RUNNER:
                    doReconnectStep();
                    break;
            }
        }
    };
    private PictureView liveView;
    private LiveViewData currentLiveViewData;
    private LiveViewData currentLiveViewData2;
    private Bitmap currentCapturedBitmap;
    private GestureDetector gestureDetector;
    private boolean showsCapturedPicture;
    private boolean isPro = true;
    private long showCapturedPictureDuration;
    private boolean showCapturedPictureNever;
    private boolean justCaptured;
    private Camera camera;
    private Activity activity;
    private SessionView sessionFrag;
    private boolean isInResume;
    private boolean started; // replaces inStart/isInStart
    private IUsbCameraTakePicListener mUsbCameraTakePicListener;
    private final UsbCameraDetector detector;
    private final PtpService ptp;
    private final UsbCameraPresenceMonitor presenceMonitor;
    private UsbCameraPresenceMonitor.Listener presenceListener;

    private int reconnectAttempts;
    private boolean reconnectLoopRunning;

    /**
     * Create with explicit live view (simple use-case).
     */
    public UsbCameraManager(Activity activity, PictureView pictureView, IUsbCameraTakePicListener mUsbCameraTakePicListener) {
        this.activity = activity;
        this.liveView = pictureView;
        this.sessionFrag = this;
        this.mUsbCameraTakePicListener = mUsbCameraTakePicListener;

        this.detector = UsbCameraDetector.getInstance(activity);
        this.ptp = PtpService.Singleton.getInstance(activity);
        this.presenceMonitor = UsbCameraPresenceMonitor.getInstance(activity);

        gestureDetector = new GestureDetector(activity, this);
        if (pictureView != null) {
            pictureView.setOnTouchListener((v, event) -> {
                if (camera != null) {
                    gestureDetector.onTouch(event);
                }
                return true;
            });
        }
    }

    /**
     * Update the live-view surface. Call before onStart() when using SessionActivity.
     */
    public void setLiveView(PictureView view) {
        this.liveView = view;
        if (view != null) {
            view.setOnTouchListener((v, event) -> {
                if (camera != null) {
                    gestureDetector.onTouch(event);
                }
                return true;
            });
        }
    }

    /**
     * Redirect camera lifecycle callbacks to a different SessionView (e.g. a Fragment).
     * Pass null to restore self-dispatch.
     */
    public void setSessionView(SessionView view) {
        this.sessionFrag = (view != null) ? view : this;
    }

    /**
     * Returns the current connected camera, or null if disconnected.
     */
    public Camera getCamera() {
        return camera;
    }

    /**
     * Register a listener for global USB camera state changes.
     * Delegates to {@link UsbCameraDetector}.
     */
    public void addUsbCameraStateListener(UsbCameraStateListener listener) {
        if (listener == null) {
            return;
        }
        detector.addListener(new UsbCameraDetector.Listener() {
            @Override
            public void onUsbCameraStateChanged(UsbCameraDetector.State state) {
                listener.onUsbCameraStateChanged(state);
            }

            @Override
            public void onUsbCameraError(String message) {
                listener.onUsbCameraError(message);
            }
        });
    }

    public void removeUsbCameraStateListener(UsbCameraStateListener listener) {
        // No-op: the old API used direct listener instances; with detector we recommend using detector.addListener/removeListener directly.
        // Keeping this method to avoid breaking callers.
    }

    /**
     * Trigger a detection attempt. Safe to call from any page.
     * <p>
     * Note: detection result is asynchronous and will be delivered via listeners.
     */
    public void startDetection() {
        // Keep old API, but now it only refreshes presence (non-PTP).
        detector.refreshPresence();
    }

    public boolean hasUsbCameraConnected() {
        return detector.isConnected();
    }

    public UsbCameraDetector.State getUsbCameraState() {
        return detector.getState();
    }

    public String getUsbCameraLastError() {
        return detector.getLastError();
    }

    public void onNewIntent(Intent intent) {
        if (started) {
            // Preview owns PTP permission handling.
            ptp.setCameraListener(this);
            ptp.initialize(activity, intent);
        }
    }

    public void onStart() {
        started = true;

        // Preview must own the PTP camera listener to receive live view callbacks.
        ptp.setCameraListener(this);

        // Auto-reconnect on unplug/replug while staying on preview page.
        if (presenceListener == null) {
            presenceListener = new UsbCameraPresenceMonitor.Listener() {
                @Override
                public void onUsbCameraPresenceChanged(UsbCameraPresenceMonitor.PresenceState state) {
                    if (!started) {
                        return;
                    }
                    if (state == UsbCameraPresenceMonitor.PresenceState.NOT_PRESENT) {
                        Log.w(TAG, "USB camera detached while in preview");
                        handleUsbDetached();
                    } else if (state == UsbCameraPresenceMonitor.PresenceState.PRESENT) {
                        Log.i(TAG, "USB camera attached while in preview, scheduling reconnect");
                        scheduleReconnect();
                    }
                }
            };
        }
        presenceMonitor.addListener(presenceListener);
        presenceMonitor.start();

        // IMPORTANT: When entering the preview page with a camera already plugged in,
        // the broadcast receiver may not fire an ATTACHED event. Force a presence refresh
        // and start a single connect loop.
        presenceMonitor.refreshNow();
        maybeStartReconnectLoop("onStart");

        showCapturedPictureNever = false;
        showCapturedPictureDuration = 1000;
        if (liveView != null) liveView.setLiveViewData(null);
    }

    private void startLiveViewAgain() {
        showsCapturedPicture = false;
        // Avoid recycling bitmaps here: PictureView might still draw it.
        if (currentCapturedBitmap != null) {
            if (liveView != null) liveView.setPicture(null);
            currentCapturedBitmap = null;
        }
        if (camera != null && camera.isLiveViewOpen()) {
            if (liveView != null) liveView.setLiveViewData(null);
            currentLiveViewData = null;
            currentLiveViewData2 = null;
            camera.getLiveViewPicture(null);
        }
    }

    public void takePicture() {
        if (camera == null) {
            Log.w(TAG, "takePicture ignored: camera is null");
            return;
        }
        Log.e("UsbCameraManager", "UsbCameraManager>>takePicture:" + camera.isAutoFocusSupported());
        camera.focus();
    }


    public void onResume() {
        isInResume = true;
        if (camera != null) {
            if (isPro && camera.isLiveViewOpen()) {
                currentLiveViewData = null;
                currentLiveViewData2 = null;
                camera.getLiveViewPicture(null);
            }
        }
    }

    public void onPause() {
        isInResume = false;
    }

    public void onStop() {
        handler.removeCallbacksAndMessages(null);
        mUsbCameraTakePicListener = null;
        started = false;

        stopReconnectLoop();

        // Detach presence listener to avoid reconnect attempts when leaving the page.
        if (presenceListener != null) {
            presenceMonitor.removeListener(presenceListener);
        }

        // Stop liveview on the camera when leaving preview.
        if (camera != null) {
            try {
                camera.setLiveView(false);
            } catch (Exception ignore) {
            }
            // 强制关闭相机 WorkerThread，停止事件轮询，释放 CPU 资源
            // 使用 shutdownHard() 确保 WorkerThread 完全停止，防止线程泄漏
            camera.shutdownHard();
            camera = null;
        }

        // Release PTP listener when leaving preview.
        ptp.setCameraListener(null);
        detector.reportDisconnected();

        showsCapturedPicture = false;
        justCaptured = false;
        currentLiveViewData = null;
        currentLiveViewData2 = null;
        currentCapturedBitmap = null;
        if (liveView != null) liveView.setLiveViewData(null);
        if (liveView != null) liveView.setPicture(null);
    }

    private void handleUsbDetached() {
        // Stop live view & clear UI immediately to avoid frozen frames.
        try {
            if (camera != null) {
                camera.setLiveView(false);
            }
        } catch (Exception ignore) {
        }
        camera = null;
        detector.reportDisconnected();

        showsCapturedPicture = false;
        justCaptured = false;
        currentLiveViewData = null;
        currentLiveViewData2 = null;
        currentCapturedBitmap = null;
        if (liveView != null) liveView.setLiveViewData(null);
        if (liveView != null) liveView.setPicture(null);

        // Make sure future attach triggers a fresh init.
        ptp.setCameraListener(this);
    }

    private void scheduleReconnect() {
        // When we get ATTACHED while staying on page, start (or keep) the loop.
        maybeStartReconnectLoop("usbAttached");
    }

    private void startReconnectLoop() {
        reconnectAttempts = 0;
        reconnectLoopRunning = true;
        handler.removeMessages(USB_RECONNECT_RUNNER);
        handler.sendEmptyMessage(USB_RECONNECT_RUNNER);
    }

    private void stopReconnectLoop() {
        reconnectAttempts = 0;
        reconnectLoopRunning = false;
        handler.removeMessages(USB_RECONNECT_RUNNER);
    }

    private void maybeStartReconnectLoop(String reason) {
        if (!started) {
            return;
        }
        if (camera != null) {
            return;
        }
        if (presenceMonitor.getState() != UsbCameraPresenceMonitor.PresenceState.PRESENT) {
            detector.reportDisconnected();
            return;
        }
        if (reconnectLoopRunning) {
            return;
        }
        Log.i(TAG, "Start reconnect loop (" + reason + ")");
        detector.reportConnecting();
        startReconnectLoop();
    }

    private void doReconnectStep() {
        if (!started) {
            stopReconnectLoop();
            return;
        }

        // Already connected -> stop loop
        if (camera != null) {
            stopReconnectLoop();
            return;
        }

        // Only try when USB device is present
        if (presenceMonitor.getState() != UsbCameraPresenceMonitor.PresenceState.PRESENT) {
            detector.reportDisconnected();
            stopReconnectLoop();
            return;
        }

        if (reconnectAttempts >= RECONNECT_MAX_ATTEMPTS) {
            Log.e(TAG, "Reconnect attempts exceeded: " + reconnectAttempts);
            detector.reportError("USB camera connect timeout");
            stopReconnectLoop();
            return;
        }

        reconnectAttempts++;
        Log.i(TAG, "Reconnect attempt " + reconnectAttempts + "/" + RECONNECT_MAX_ATTEMPTS);

        // Ensure we still own the listener
        ptp.setCameraListener(this);
        ptp.initialize(activity, activity.getIntent());

        handler.sendEmptyMessageDelayed(USB_RECONNECT_RUNNER, RECONNECT_RETRY_INTERVAL_MS);
    }

    @Override
    public void onLongTouch(float posx, float posy) {
        if (camera == null) {
            return;
        }
        if (camera.isLiveViewOpen()) {
            if (camera.isLiveViewAfAreaSupported() && liveView != null) {
                camera.setLiveViewAfArea(liveView.calculatePictureX(posx), liveView.calculatePictureY(posy));
            }
        } else if (false) {
            if (liveView == null) return;
            float x = liveView.calculatePictureX(posx);
            float y = liveView.calculatePictureY(posy);
            for (FocusPoint fp : camera.getFocusPoints()) {
                if (Math.abs(x - fp.posx) <= fp.radius && Math.abs(y - fp.posy) <= fp.radius) {
                    camera.setProperty(Camera.Property.CurrentFocusPoint, fp.id);
                    break;
                }
            }
        }
    }

    @Override
    public void onPinchZoom(float pX, float pY, float distInPixel) {
        if (liveView != null) liveView.zoomAt(pX, pY, distInPixel);
    }

    @Override
    public void onTouchMove(float dx, float dy) {
        if (liveView != null) liveView.pan(dx, dy);
    }

    @Override
    public void onFling(float velx, float vely) {
        if (liveView != null) liveView.fling(velx, vely);
    }

    @Override
    public void onStopFling() {
        if (liveView != null) liveView.stopFling();
    }

    @Override
    public void enableUi(boolean enabled) {

    }

    @Override
    public void cameraStarted(Camera camera) {
        propertyChanged(Camera.Property.BatteryLevel, camera.getProperty(Camera.Property.BatteryLevel));
        propertyChanged(Camera.Property.FocusMode, camera.getProperty(Camera.Property.FocusMode));
        propertyChanged(Camera.Property.AvailableShots, camera.getProperty(Camera.Property.AvailableShots));
        propertyChanged(Camera.Property.CurrentFocusPoint, camera.getProperty(Camera.Property.CurrentFocusPoint));
        if (isPro) {
            if (camera.isLiveViewSupported()) {

            }

            if (camera.isLiveViewOpen()) {
                liveViewStarted();
            } else if (camera.isSettingPropertyPossible(Camera.Property.FocusPoints)) {

            }
        }
        camera.setLiveView(true);
    }

    @Override
    public void cameraStopped(Camera camera) {

    }

    @Override
    public void propertyChanged(int property, int value) {
        Log.e("UsbCameraManager", "UsbCameraManager>>propertyChanged:" + property);
        if (!started || camera == null) {
            return;
        }
        if (property == Camera.Property.ShootingMode) {
            Log.e("UsbCameraManager", "UsbCameraManager>>propertyChanged 修改拍摄方式:" + value);
            if (isPro && !camera.isLiveViewOpen()) {
                camera.isSettingPropertyPossible(Camera.Property.FocusPoints);
            }
        } else if (isPro) {
            if (property == Camera.Property.CurrentFocusPoint && liveView != null) {
                liveView.setCurrentFocusPoint(value);
            }
        }
    }

    @Override
    public void propertyDescChanged(int property, int[] values) {
        if (!started || camera == null) {
            return;
        }
    }

    @Override
    public void setCaptureBtnText(String text) {

    }

    @Override
    public void focusStarted() {

    }

    @Override
    public void focusEnded(boolean hasFocused) {
        if (hasFocused) {
            capture();
        }
    }

    private void capture() {
        if (camera == null) {
            return;
        }
        camera.capture();
        justCaptured = true;
        Message message = new Message();
        message.what = JUSTCAPTUREDRESETRUNNER;
        handler.sendMessageDelayed(message, 500);
    }

    @Override
    public void liveViewStarted() {
        if (!isPro) {
            return;
        }
        if (!started || camera == null) {
            return;
        }
        if (camera.isDriveLensSupported()) {
        }
        if (camera.isHistogramSupported()) {
        }

        if (liveView != null) liveView.setLiveViewData(null);
        showsCapturedPicture = false;
        currentLiveViewData = null;
        currentLiveViewData2 = null;
        camera.getLiveViewPicture(null);
    }

    @Override
    public void liveViewStopped() {
        if (!isPro) {
            return;
        }
        if (!started || camera == null) {
            return;
        }
        camera.isSettingPropertyPossible(Camera.Property.FocusPoints);
    }

    @Override
    public void liveViewData(LiveViewData data) {
        if (!isPro) {
            return;
        }
        if (!started || camera == null) {
            return;
        }
        if (justCaptured || showsCapturedPicture) {
            return;
        }
        if (data == null) {
            camera.getLiveViewPicture(null);
            return;
        }

        data.hasHistogram &= false;

        if (liveView != null) liveView.setLiveViewData(data);
        currentLiveViewData2 = currentLiveViewData;
        this.currentLiveViewData = data;
        camera.getLiveViewPicture(currentLiveViewData2);
    }

    /**
     * 收到拍摄的资源回调
     *
     * @param objectHandle 对象ID
     * @param filename     格式
     * @param thumbnail    缩略图
     * @param bitmap       原图
     */
    @Override
    public void capturedPictureReceived(int objectHandle, String filename, Bitmap thumbnail, Bitmap bitmap) {
        if (!started) {
            // If callbacks arrive late, just ignore safely.
            return;
        }
        if (bitmap == null) {
            Log.e("UsbCameraManager", "UsbCameraManager>>capturedPictureReceived: bitmap is null (" + filename + ")");
            return;
        }

        showsCapturedPicture = true;
        Message message = new Message();
        message.what = LIVEVIEWRESTARTERRUNNER;
        handler.sendMessageDelayed(message, showCapturedPictureDuration);

        if (liveView != null) liveView.setPicture(bitmap);
        Log.e("UsbCameraManager", "UsbCameraManager>>capturedPictureReceived:" + filename);

        if (mUsbCameraTakePicListener != null) {
            mUsbCameraTakePicListener.onTakePic(bitmap);
        }

        // Don't recycle old bitmap here: PictureView may still be drawing it.
        currentCapturedBitmap = bitmap;
    }

    /**
     * 收到对象添加事件
     *
     * @param handle 对象ID
     * @param format 对象格式
     */
    @Override
    public void objectAdded(int handle, int format) {
        if (camera == null) {
            return;
        }
        //如果是JPG格式的文件，通过handle获取文件字节
        if (format == PtpConstants.ObjectFormat.EXIF_JPEG) {
            if (isPro && showCapturedPictureNever) {
                camera.retrieveImageInfo(this, handle);
                Message message = new Message();
                message.what = LIVEVIEWRESTARTERRUNNER;
                handler.sendMessage(message);
            } else {
                //获取JPG文件缩略图和原图
                camera.retrievePicture(handle);
            }
        }
    }

    @Override
    public void onCameraStarted(Camera camera) {
        this.camera = camera;
        detector.reportConnected();
        stopReconnectLoop();

        if (AppConfig.LOG) {
            Log.i(TAG, "camera started");
        }
        camera.setCapturedPictureSampleSize(1);
        sessionFrag.cameraStarted(camera);

        // If liveview doesn't start automatically on some devices, kick a first frame pull.
        // (cameraStarted() will call camera.setLiveView(true); liveViewStarted() will call getLiveViewPicture)
        handler.postDelayed(() -> {
            if (!started || this.camera == null) {
                return;
            }
            try {
                if (isPro && this.camera.isLiveViewOpen()) {
                    this.camera.getLiveViewPicture(null);
                }
            } catch (Exception ignore) {
            }
        }, FIRST_CONNECT_KICK_MS);
    }

    @Override
    public void onCameraStopped(Camera camera) {
        Camera c = this.camera;
        if (c != null) {
            c.setLiveView(false);
        }
        this.camera = null;
        detector.reportDisconnected();
        sessionFrag.cameraStopped(camera);
    }

    @Override
    public void onNoCameraFound() {
        this.camera = null;
        detector.reportDisconnected();
        maybeStartReconnectLoop("noCameraFound");
    }

    @Override
    public void onError(String message) {
        detector.reportError(message);
        maybeStartReconnectLoop("onError");
        sessionFrag.cameraStopped(null);
        Log.e("UsbCameraManager", "UsbCameraManager>>onError:" + message);
    }

    @Override
    public void onPropertyChanged(int property, int value) {
        sessionFrag.propertyChanged(property, value);
        Log.e("UsbCameraManager", "UsbCameraManager>>onPropertyChanged--------property:" + property+",value:"+value);
    }

    @Override
    public void onPropertyStateChanged(int property, boolean enabled) {
        Log.e("UsbCameraManager", "UsbCameraManager>>onPropertyStateChanged---------property" + property+",enabled:"+enabled);
    }

    @Override
    public void onPropertyDescChanged(int property, int[] values) {
        sessionFrag.propertyDescChanged(property, values);
        Log.e("UsbCameraManager", "UsbCameraManager>>onPropertyDescChanged: property=" + property);
    }

    @Override
    public void onLiveViewStarted() {
        Log.e("UsbCameraManager", "UsbCameraManager>>onLiveViewStarted:" + "");
        sessionFrag.liveViewStarted();
    }

    @Override
    public void onLiveViewData(LiveViewData data) {
        if (!isInResume) {
            return;
        }
        sessionFrag.liveViewData(data);
    }

    @Override
    public void onLiveViewStopped() {
        sessionFrag.liveViewStopped();
        Log.e("UsbCameraManager", "UsbCameraManager>>onLiveViewStopped:" + "");
    }

    /**
     * 拍照结果回调
     *
     * @param objectHandle 对象ID
     * @param filename     文件名称
     * @param thumbnail    缩略图
     * @param bitmap       原图
     */
    @Override
    public void onCapturedPictureReceived(int objectHandle, String filename, Bitmap thumbnail, Bitmap bitmap) {
        Log.e("UsbCameraManager", "UsbCameraManager>>onCapturedPictureReceived:" + "");
        if (bitmap != null) {
            //收到拍摄的照片资源
            sessionFrag.capturedPictureReceived(objectHandle, filename, thumbnail, bitmap);
        } else {
            Log.e("UsbCameraManager", "UsbCameraManager>>onCapturedPictureReceived:" + "No thumbnail available");
        }
    }

    @Override
    public void onBulbStarted() {
        sessionFrag.setCaptureBtnText("0");
        Log.e("UsbCameraManager", "UsbCameraManager>>onBulbStarted:" + "");
    }

    @Override
    public void onBulbExposureTime(int seconds) {
        sessionFrag.setCaptureBtnText("" + seconds);
        Log.e("UsbCameraManager", "UsbCameraManager>>onBulbExposureTime:" + "" + seconds);
    }

    @Override
    public void onBulbStopped() {
        sessionFrag.setCaptureBtnText("Fire");
        Log.e("UsbCameraManager", "UsbCameraManager>>onBulbStopped:" + "");
    }

    @Override
    public void onFocusStarted() {
        sessionFrag.focusStarted();
        Log.e("UsbCameraManager", "UsbCameraManager>>onFocusEnded:" + "");
    }

    @Override
    public void onFocusEnded(boolean hasFocused) {
        sessionFrag.focusEnded(hasFocused);
        Log.e("UsbCameraManager", "UsbCameraManager>>onFocusEnded:" + "" + hasFocused);
    }

    @Override
    public void onFocusPointsChanged() {
        Log.e("UsbCameraManager", "UsbCameraManager>>onFocusPointsChanged:" + "");
    }

    @Override
    public void onObjectAdded(int handle, int format) {
        //收到拍摄照片通知
        sessionFrag.objectAdded(handle, format);
        Log.e("UsbCameraManager", "UsbCameraManager>>onObjectAdded--------handle:"+handle + ",format:" +format );
    }

    @Override
    public void onImageInfoRetrieved(int objectHandle, ObjectInfo objectInfo, Bitmap thumbnail) {

    }
}
