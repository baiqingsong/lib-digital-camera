package com.dawn.digital_camera.util;


import android.graphics.Bitmap;
import android.graphics.Color;

public class ImageUtils {
    private static final int DARK_THRESHOLD = 16; // RGB值小于16视为黑色

    public static boolean isBlackFrame(Bitmap bitmap, float threshold) {
        if (bitmap == null || bitmap.isRecycled()) return false;

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int totalPixels = width * height;
        int darkPixelCount = 0;

        // 采样检测，避免全像素扫描
        int sampleStep = Math.max(1, (int) Math.sqrt(totalPixels / 1000));

        for (int y = 0; y < height; y += sampleStep) {
            for (int x = 0; x < width; x += sampleStep) {
                int pixel = bitmap.getPixel(x, y);
                if (isDarkPixel(pixel)) {
                    darkPixelCount++;
                }
            }
        }

        int totalSamples = (width / sampleStep) * (height / sampleStep);
        float darkRatio = (float) darkPixelCount / totalSamples;

        return darkRatio >= threshold;
    }

    public static boolean isBlackFrameFromBytes(byte[] jpegData, float threshold) {
        // 轻量级头部分析
        int darkHeaderCount = 0;
        int totalHeaderSamples = 0;

        // 只扫描前2KB数据
        int scanLength = Math.min(2048, jpegData.length);

        for (int i = 0; i < scanLength; i += 3) {
            if (i + 2 >= scanLength) break;

            byte r = jpegData[i];
            byte g = jpegData[i + 1];
            byte b = jpegData[i + 2];

            if (isDarkPixel(r, g, b)) {
                darkHeaderCount++;
            }
            totalHeaderSamples++;
        }

        if (totalHeaderSamples == 0) return false;

        float darkRatio = (float) darkHeaderCount / totalHeaderSamples;
        return darkRatio >= threshold;
    }

    private static boolean isDarkPixel(int pixel) {
        int r = Color.red(pixel);
        int g = Color.green(pixel);
        int b = Color.blue(pixel);
        return isDarkPixel(r, g, b);
    }

    private static boolean isDarkPixel(int r, int g, int b) {
        return r < DARK_THRESHOLD &&
                g < DARK_THRESHOLD &&
                b < DARK_THRESHOLD;
    }
}

