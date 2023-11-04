package com.practice.camera2view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;
import android.view.View;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Camera2Helper {
    public static final String TAG = "Camera2Helper";
    public static final int STATE_IDLE = 0;
    public static final int STATE_PREPARED = 1;
    public static final int STATE_STARTING_PREVIEW = 2;
    public static final int STATE_PREVIEWING = 3;

    public static final int STATE_STOPPING_PREVIEWING = 4;
    public static final int STATE_PREVIEW_STOPPED = 5;

    public static final int STATE_SHUTTING_DOWN = 5;
    public static final int STATE_SHUTDOWN = 6;

    ExecutorService singleThreadExecutor = null;
    HandlerThread cameraThread = null;
    Handler cameraHandler = null;
    HandlerThread imageThread = null;
    Handler imageHandler = null;
    int state = STATE_IDLE;
    Handler mainHandler;
    int userIntent = STATE_IDLE;
    Config config;
    CameraManager cameraManager;
    ImageReader imageReader;
    CameraDevice cameraDevice = null;
    ImageDataCallback callback = null;
    Context context;
    CameraCaptureSession cameraCaptureSession;
    public Camera2Helper(Context context, Config config) {
        this.context = context;
        this.config = config;
        singleThreadExecutor = Executors.newSingleThreadExecutor();
        cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        cameraThread = new HandlerThread("cameraThread");
        cameraThread.start();
        imageThread = new HandlerThread("imageThread");
        imageThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());
        imageHandler = new Handler(imageThread.getLooper());
        mainHandler = new Handler(Looper.getMainLooper());
        setUserIntent(STATE_IDLE);
        setState(STATE_PREPARED);
    }

    public void setCallback(ImageDataCallback callback) {
        this.callback = callback;
    }

    private void setUserIntent(int userIntent) {
        this.userIntent = userIntent;
    }
    public synchronized void setState(int value) {
        state = value;
    }
    public synchronized int getState() {
        return state;
    }

    public synchronized boolean canStartPreview() {
        return state == STATE_PREPARED || state == STATE_PREVIEW_STOPPED;
    }

    public synchronized boolean isPreviewing() {
        return state == STATE_PREVIEWING;
    }

    public synchronized boolean isShutdown() {
        return state == STATE_SHUTTING_DOWN || state == STATE_SHUTDOWN;
    }

    @SuppressLint({"MissingPermission", "WrongConstant"})
    public void startPreview() {
        if (userIntent != STATE_SHUTDOWN) {
            setUserIntent(STATE_PREVIEWING);
        } else {
            setUserIntent(STATE_SHUTDOWN);
        }


        submitTask(new Runnable() {
            Throwable ce = null;
            CountDownLatch countDownLatch = null;

            @Override
            public void run() {
                if (!canStartPreview()) return;
                setState(STATE_STARTING_PREVIEW);
                countDownLatch = new CountDownLatch(1);
                ce = null;
                try {
                    if (config.getCameraId() < 0) {
                        throw new Exception("未配置摄像头id");
                    }
                    String cameraDeviceId = "";
                    try {
                        cameraDeviceId = cameraManager.getCameraIdList()[config.getCameraId()];
                        if (TextUtils.isEmpty(cameraDeviceId)) {
                            throw new Exception("没有找到匹配摄像头");
                        }
                    } catch (Exception e) {
                        throw e;
                    }

                    cameraManager.openCamera(cameraDeviceId, new CameraDevice.StateCallback() {
                        @Override
                        public void onOpened(@NonNull CameraDevice camera) {
                            cameraDevice = camera;
                            countDownLatch.countDown();
                        }

                        @Override
                        public void onDisconnected(@NonNull CameraDevice camera) {
                            ce = new Exception();
                            if (getState() == STATE_PREVIEWING) {
                                Log.e(TAG, "onDisconnected: " + Log.getStackTraceString(ce));
                                stopPreview();
                            }
                            countDownLatch.countDown();
                        }

                        @Override
                        public void onError(@NonNull CameraDevice camera, int error) {
                            ce = new Exception("camera error:" + error);
                            if (getState() == STATE_PREVIEWING) {
                                Log.e(TAG, "onError: " + Log.getStackTraceString(ce));
                                stopPreview();
                            }
                            countDownLatch.countDown();
                        }
                    }, cameraHandler);
                } catch (Exception e) {
                    ce = e;
                    countDownLatch.countDown();
                }
                try {
                    countDownLatch.await();
                } catch (Exception e) {

                }


                if (ce != null) {
                    Log.e(TAG, "打开摄像头失败: " + Log.getStackTraceString(ce));
                    try {
                        cameraDevice.close();
                    } catch (Exception e) {

                    }
                    cameraDevice = null;
                    setState(STATE_PREVIEW_STOPPED);
                    return;
                }


                try {
                    imageReader = ImageReader.newInstance(config.getPreviewWidth(), config.getPreviewHeight(), ImageFormat.YUV_420_888, 2);
                    imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                        @Override
                        public void onImageAvailable(ImageReader reader) {
                            try {
                                Image image = reader.acquireLatestImage();
                                try {
                                    if ((callback != null) && (userIntent == STATE_PREVIEWING)) {
                                        long startTime=System.currentTimeMillis();
                                        boolean fast=config.isFast();
                                        int rotation=config.getRotation();

                                        byte[] nv21 = Camera2View.Utils.yuv420_888toNV21(fast,rotation, image);
                                        Log.e(TAG, "onImageAvailable: useTime"+(System.currentTimeMillis()-startTime));
                                        int dWidth = 0;
                                        int dHeight = 0;
                                        switch (rotation) {
                                            case 0:
                                            case 180:
                                                dWidth = image.getWidth();
                                                dHeight = image.getHeight();
                                                break;
                                            default:
                                                dWidth = image.getHeight();
                                                dHeight =  image.getWidth();
                                                break;
                                        }
                                        callback.imageData(nv21, dWidth, dHeight);
                                    }
                                } catch (Exception e) {

                                }
                                image.close();
                            } catch (Exception e) {
                                Log.e(TAG, "onImageAvailable: " + Log.getStackTraceString(e));
                            }

                        }
                    }, imageHandler);
                } catch (Exception e) {
                    ce = e;
                }

                if (ce != null) {
                    Log.e(TAG, "创建预览ImageReader失败: " + Log.getStackTraceString(ce));
                    try {
                        cameraDevice.close();
                    } catch (Exception e) {

                    }
                    cameraDevice = null;

                    try {
                        imageReader.close();
                    } catch (Exception e) {

                    }
                    imageReader = null;
                    setState(STATE_PREVIEW_STOPPED);
                    return;
                }

                countDownLatch = new CountDownLatch(1);
                try {
                    List<Surface> surfaces = new ArrayList<>();
                    surfaces.add(imageReader.getSurface());

                    cameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            cameraCaptureSession = session;
                            countDownLatch.countDown();
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            ce = new Exception("配置失败");
                            countDownLatch.countDown();
                        }
                    }, cameraHandler);
                } catch (CameraAccessException e) {
                    ce = e;
                    countDownLatch.countDown();
                }
                try {
                    countDownLatch.await();
                } catch (Exception e) {

                }

                if (ce != null) {
                    Log.e(TAG, "创建CaptureSession失败: " + Log.getStackTraceString(ce));
                    try {
                        cameraCaptureSession.close();
                    } catch (Exception e) {

                    }
                    cameraCaptureSession = null;

                    try {
                        cameraDevice.close();
                    } catch (Exception e) {

                    }
                    cameraDevice = null;

                    try {
                        imageReader.close();
                    } catch (Exception e) {

                    }
                    imageReader = null;
                    setState(STATE_PREVIEW_STOPPED);
                    return;
                }

                try {
                    CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                    builder.addTarget(imageReader.getSurface());
                    cameraCaptureSession.setRepeatingRequest(builder.build(), null, cameraHandler);
                } catch (Exception e) {
                    ce = e;
                }

                if (ce != null) {
                    Log.e(TAG, "启动预览失败: " + Log.getStackTraceString(ce));
                    try {
                        cameraCaptureSession.close();
                    } catch (Exception e) {

                    }
                    cameraCaptureSession = null;

                    try {
                        cameraDevice.close();
                    } catch (Exception e) {

                    }
                    cameraDevice = null;

                    try {
                        imageReader.close();
                    } catch (Exception e) {

                    }
                    imageReader = null;
                    setState(STATE_PREVIEW_STOPPED);
                } else {
                    setState(STATE_PREVIEWING);
                }
            }
        });
    }

    public void setCameraId(int id) {
        submitTask(new Runnable() {
            @Override
            public void run() {
                if (isShutdown()) return;
                if (isPreviewing()) {
                    stopPreview();
                    submitTask(new Runnable() {
                        @Override
                        public void run() {
                            config.setCameraId(id);
                        }
                    });
                    startPreview();
                } else {
                    config.setCameraId(id);
                }
            }
        });
    }

    public void setRotate(int degree) {
        config.setRotation(degree);
    }


    public void stopPreview() {
        if (userIntent != STATE_SHUTDOWN) {
            setUserIntent(STATE_PREVIEW_STOPPED);
        } else {
            setUserIntent(STATE_SHUTDOWN);
        }
        submitTask(new Runnable() {
            @Override
            public void run() {
                if (!isPreviewing()) return;
                setState(STATE_STOPPING_PREVIEWING);
                try {
                    cameraCaptureSession.close();
                } catch (Exception e) {

                }
                try {
                    cameraDevice.close();
                } catch (Exception e) {

                }

                try {
                    imageReader.close();
                } catch (Exception e) {

                }
                setState(STATE_PREVIEW_STOPPED);
            }
        });
    }

    public void destroy() {
        setUserIntent(STATE_SHUTDOWN);
        submitTask(new Runnable() {
            @Override
            public void run() {
                if (isPreviewing()) {
                    setState(STATE_SHUTTING_DOWN);
                    try {
                        cameraCaptureSession.close();
                    } catch (Exception e) {

                    }
                    try {
                        cameraDevice.close();
                    } catch (Exception e) {

                    }

                    try {
                        imageReader.close();
                    } catch (Exception e) {

                    }
                } else {
                    setState(STATE_SHUTTING_DOWN);
                }
                try {
                    cameraThread.quitSafely();
                } catch (Exception e) {

                }
                try {
                    imageThread.quitSafely();
                } catch (Exception e) {

                }
                setState(STATE_SHUTDOWN);
            }
        });
        singleThreadExecutor.shutdown();
    }

    private void submitTask(Runnable runnable) {
        if (runnable == null) return;
        try {
            singleThreadExecutor.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        runnable.run();
                    } catch (Exception e) {

                    }
                }
            });
        } catch (Exception e) {

        }
    }

}
