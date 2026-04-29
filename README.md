# lib-digital-camera

数码相机 USB (PTP) 控制库，支持通过 USB OTG 连接佳能 (Canon EOS) 和尼康 (Nikon) 数码相机，实现实时预览、拍照等功能。

## 功能

- **USB 连接检测**：自动检测 USB 相机插拔 (`UsbCameraDetector`, `UsbCameraPresenceMonitor`)
- **PTP 协议**：完整的 PTP/MTP 协议实现，支持 Canon EOS 扩展命令和 Nikon 扩展命令
- **实时预览 (Live View)**：将相机实时取景画面显示到 `PictureView`
- **远程拍照**：触发快门并接收拍摄结果 (`IUsbCameraTakePicListener`)
- **手势控制**：双指缩放、拖动、长按对焦 (`GestureDetector`)
- **相机属性**：读写快门速度、光圈、ISO、白平衡等属性

## 集成方式

### JitPack

在根目录 `build.gradle` 添加：
```groovy
allprojects {
    repositories {
        maven { url 'https://jitpack.io' }
    }
}
```

在 `app/build.gradle` 添加：
```groovy
dependencies {
    implementation 'com.github.baiqingsong:lib-digital-camera:1.0.0'
}
```

## 快速使用

### 1. 布局中加入 PictureView

```xml
<com.dawn.digital_camera.PictureView
    android:id="@+id/picture_view"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

### 2. Activity 中初始化

```java
PictureView pictureView = findViewById(R.id.picture_view);

UsbCameraManager cameraManager = new UsbCameraManager(this, pictureView, bitmap -> {
    // 收到拍照结果
    runOnUiThread(() -> imageView.setImageBitmap(bitmap));
});

// 监听连接状态
cameraManager.addUsbCameraStateListener(state -> {
    // UsbCameraDetector.State: UNKNOWN / CONNECTING / CONNECTED / NOT_FOUND / ERROR
});
```

### 3. 生命周期绑定

```java
@Override
protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
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
```

### 4. 拍照

```java
cameraManager.takePicture();
```

## AndroidManifest 配置

```xml
<!-- USB 权限 -->
<uses-feature android:name="android.hardware.usb.host" />

<activity android:name=".YourActivity">
    <intent-filter>
        <action android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED" />
    </intent-filter>
    <meta-data
        android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED"
        android:resource="@xml/device_filter" />
</activity>
```

## 主要类说明

| 类 / 接口 | 说明 |
|-----------|------|
| `UsbCameraManager` | 入口类，管理整个相机生命周期 |
| `IUsbCameraTakePicListener` | 拍照回调接口，返回 `Bitmap` |
| `UsbCameraStateListener` | 相机连接状态监听接口 |
| `UsbCameraDetector` | USB 相机检测单例 |
| `UsbCameraPresenceMonitor` | USB 插拔事件监听单例 |
| `PictureView` | 实时预览 View（继承自 `View`） |
| `PtpService` | PTP 协议服务接口 |
| `Camera` | 相机操作接口（属性、拍照、LiveView） |
| `EosCamera` | Canon EOS 实现 |
| `NikonCamera` | Nikon 实现 |
| `AppConfig` | 配置常量（超时、日志开关） |
