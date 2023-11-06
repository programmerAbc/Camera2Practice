package com.practice.camera2view;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.UiThread;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class PreviewFrame {
    public static final String TAG = "PreviewFrame";
    Bitmap bitmap = null;
    Handler mainHandler = null;
    AtomicBoolean previewFlag = null;
    List<Callback> callbackList = null;

    public PreviewFrame() {
        callbackList = new ArrayList<>();
        mainHandler = new Handler(Looper.getMainLooper());
        previewFlag = new AtomicBoolean(false);
    }


    public void addCallback(Callback callback) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    callbackList.add(callback);
                } catch (Exception e) {
                }
            }
        });
    }


    public void removeCallback(Callback callback) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    callbackList.remove(callback);
                } catch (Exception e) {
                }
            }
        });
    }


    @UiThread
    public Bitmap getBitmap() {
        return bitmap;
    }

    public void update(byte[] nv21, int width, int height) {
        if (nv21 == null || nv21.length == 0 || nv21.length != (width * height * 3 / 2)) return;
        if (!previewFlag.compareAndSet(false, true)) {
            return;
        }
        long startTime = System.currentTimeMillis();
        final byte[] argb = LibYuv.nv21ToArgb(nv21, width, height);
        Log.e(TAG, "nv21ToArgb: useTime:" + (System.currentTimeMillis() - startTime));


        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    long startTime = System.currentTimeMillis();
                    if (bitmap == null || bitmap.getWidth() != width || bitmap.getHeight() != height) {
                        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    }
                    bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(argb));
                    Log.e(TAG, "updateBitmap: useTime:" + (System.currentTimeMillis() - startTime));
                    try {
                        for (Callback callback : callbackList) {
                            try {
                                callback.preview(bitmap);
                            } catch (Exception e) {

                            }
                        }
                    } catch (Exception e) {

                    }
                } catch (Exception e) {
                }
                previewFlag.set(false);
            }
        });
    }


    public interface Callback {
        void preview(Bitmap bitmap);
    }


}
