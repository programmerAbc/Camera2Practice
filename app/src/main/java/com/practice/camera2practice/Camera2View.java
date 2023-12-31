package com.practice.camera2practice;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Surface;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.viewfinder.CameraViewfinder;
import androidx.camera.viewfinder.ViewfinderSurfaceRequest;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Camera2View extends FrameLayout {
    public static final String TAG = "Camera2View";
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

    CameraViewfinder cvf;
    int state = STATE_IDLE;
    Handler mainHandler;

    Config config;
    CameraManager cameraManager;
    ImageReader imageReader;
    Surface cvfSurface;


    CameraDevice cameraDevice = null;


    ViewfinderSurfaceRequest viewfinderSurfaceRequest;
    CameraCaptureSession cameraCaptureSession;

    public Camera2View(@NonNull Context context) {
        super(context);
        init();
    }

    public Camera2View(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public Camera2View(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }


    @SuppressLint({"WrongConstant", "MissingPermission"})
    public void prepare(Config config) {
        submitTask(new Runnable() {
            @Override
            public void run() {
                if (getState() != STATE_IDLE) return;
                Camera2View.this.config = config;
                viewfinderSurfaceRequest = new ViewfinderSurfaceRequest.Builder(new Size(config.getPreviewWidth(), config.getPreviewHeight()))
                        .setLensFacing(CameraCharacteristics.LENS_FACING_FRONT)
                        .setSensorOrientation(config.getRotation())
                        .build();
                setState(STATE_PREPARED);
            }
        });
    }


    private void init() {
        LayoutInflater.from(getContext()).inflate(R.layout.camera2view, this, true);
        cvf = findViewById(R.id.cvf);
        singleThreadExecutor = Executors.newSingleThreadExecutor();
        cameraManager = (CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);
        cameraThread = new HandlerThread("cameraThread");
        cameraThread.start();
        imageThread = new HandlerThread("imageThread");
        imageThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());
        imageHandler = new Handler(imageThread.getLooper());
        mainHandler = new Handler(Looper.getMainLooper());
        state = STATE_IDLE;
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

    public synchronized boolean canStopPreview() {
        return state == STATE_PREVIEWING;
    }


    @SuppressLint({"MissingPermission", "WrongConstant"})
    public void startPreview() {
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
                    cameraManager.openCamera(config.getCameraId(), new CameraDevice.StateCallback() {
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


                countDownLatch = new CountDownLatch(1);
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        try {

                            ListenableFuture<Surface> surfaceListenableFuture = cvf.requestSurfaceAsync(viewfinderSurfaceRequest);
                            Futures.addCallback(surfaceListenableFuture, new FutureCallback<Surface>() {
                                @Override
                                public void onSuccess(Surface result) {
                                    cvfSurface = result;
                                    countDownLatch.countDown();
                                }

                                @Override
                                public void onFailure(Throwable t) {
                                    ce = t;
                                    countDownLatch.countDown();
                                }
                            }, ContextCompat.getMainExecutor(getContext()));

                        } catch (Exception e) {
                            ce = e;
                            countDownLatch.countDown();
                        }
                    }
                });
                try {
                    countDownLatch.await();
                } catch (Exception e) {

                }

                if (ce != null) {
                    Log.e(TAG, "创建预览画面失败: " + Log.getStackTraceString(ce));
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
                            Image image = reader.acquireLatestImage();

                            Log.e(TAG, "onImageAvailable: " + image.getWidth() + "," + image.getHeight());
                            image.close();
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
                    surfaces.add(cvfSurface);
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
                    builder.addTarget(cvfSurface);
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

    public void stopPreview() {
        submitTask(new Runnable() {
            @Override
            public void run() {
                if (!canStopPreview()) return;
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
        submitTask(new Runnable() {
            @Override
            public void run() {
                if (canStopPreview()) {
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
                    setState(STATE_SHUTDOWN);
                }
                try {
                    cameraThread.quitSafely();
                } catch (Exception e) {

                }
                try {
                    imageThread.quitSafely();
                } catch (Exception e) {

                }


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


    public static class Config {
        int previewWidth;
        int previewHeight;
        int rotation;
        String cameraId;

        public Config() {
        }

        private Config(Builder builder) {
            setPreviewWidth(builder.previewWidth);
            setPreviewHeight(builder.previewHeight);
            setRotation(builder.rotation);
            setCameraId(builder.cameraId);
        }

        public int getPreviewWidth() {
            return previewWidth;
        }

        public void setPreviewWidth(int previewWidth) {
            this.previewWidth = previewWidth;
        }

        public int getPreviewHeight() {
            return previewHeight;
        }

        public void setPreviewHeight(int previewHeight) {
            this.previewHeight = previewHeight;
        }

        public int getRotation() {
            return rotation;
        }

        public void setRotation(int rotation) {
            this.rotation = rotation;
        }

        public String getCameraId() {
            return cameraId;
        }

        public void setCameraId(String cameraId) {
            this.cameraId = cameraId;
        }

        public static final class Builder {
            private int previewWidth;
            private int previewHeight;
            private int rotation;
            private String cameraId;

            public Builder() {
            }

            public Builder previewWidth(int val) {
                previewWidth = val;
                return this;
            }

            public Builder previewHeight(int val) {
                previewHeight = val;
                return this;
            }

            public Builder rotation(int val) {
                rotation = val;
                return this;
            }

            public Builder cameraId(String val) {
                cameraId = val;
                return this;
            }

            public Config build() {
                return new Config(this);
            }
        }
    }

}
