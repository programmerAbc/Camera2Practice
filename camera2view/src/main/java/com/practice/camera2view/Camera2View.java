package com.practice.camera2view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
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
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

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

    public static final int SURFACE_STATE_IDLE = 0;
    public static final int SURFACE_STATE_READY = 1;
    public static final int SURFACE_STATE_DESTROYED = 2;

    ExecutorService singleThreadExecutor = null;

    HandlerThread cameraThread = null;
    Handler cameraHandler = null;
    HandlerThread imageThread = null;
    Handler imageHandler = null;

    TextureView preview;
    int state = STATE_IDLE;
    int surfaceState = SURFACE_STATE_IDLE;
    Handler mainHandler;
    int userIntent = STATE_IDLE;
    Config config;
    CameraManager cameraManager;
    ImageReader imageReader;
    Surface previewSurface;
    FrameLayout previewContainer;
    FrameLayout coverContainer;

    Lock surfaceStateLock = new ReentrantLock();
    Condition surfaceStateCondition = surfaceStateLock.newCondition();


    CameraDevice cameraDevice = null;
    Callback callback = null;
    View coverLayout;


    CameraCaptureSession cameraCaptureSession;
    int viewWidth;
    int viewHeight;

    public Camera2View(@NonNull Context context) {
        super(context);
        init(null);
    }

    public Camera2View(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public Camera2View(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    public Callback getCallback() {
        return callback;
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    private void changeSurfaceState(int state) {
        try {
            surfaceStateLock.lock();
        } catch (Exception e) {

        }
        surfaceState = state;
        try {
            surfaceStateCondition.signalAll();
        } catch (Exception e) {

        }
        try {
            surfaceStateLock.unlock();
        } catch (Exception e) {

        }
    }


    private void setUserIntent(int userIntent) {
        this.userIntent = userIntent;
        coverLayout.setVisibility(userIntent == STATE_PREVIEWING ? View.INVISIBLE : View.VISIBLE);
    }

    private void handleOnSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        surface.setDefaultBufferSize(config.getPreviewWidth(), config.getPreviewHeight());
        previewSurface = new Surface(surface);
        changeSurfaceState(SURFACE_STATE_READY);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        viewWidth = getWidth();
        viewHeight = getHeight();
        Matrix matrix = new Matrix();
        float viewAspect = ((float) viewWidth) / viewHeight;
        float previewAspect = ((float) config.getPreviewWidth()) / config.getPreviewHeight();

        float scaleX = previewAspect / viewAspect;
        matrix.postScale(scaleX, 1);
        float scale = ((float) config.getPreviewHeight()) / ((float) viewHeight);
        matrix.postScale(scale, scale);
        if (config.getRotation() == 0) {
            if (viewAspect > previewAspect) {
                float vscale = ((float) viewWidth) / ((float) config.getPreviewWidth());
                matrix.postScale(vscale, vscale);
                matrix.postTranslate(0, (viewHeight - vscale * config.getPreviewHeight()) / 2);
            } else {
                float vscale = ((float) viewHeight) / ((float) config.getPreviewHeight());
                matrix.postScale(vscale, vscale);
                matrix.postTranslate(0, (viewWidth - vscale * config.getPreviewWidth()) / 2);
            }
        } else if (config.getRotation() == 90) {
            matrix.postRotate(90);
            matrix.postTranslate(config.getPreviewHeight(), 0);
            previewAspect = 1 / previewAspect;
            if (viewAspect > previewAspect) {
                float vscale = ((float) viewWidth) / ((float) config.getPreviewHeight());
                matrix.postScale(vscale, vscale);
                matrix.postTranslate(0, (viewHeight - vscale * config.getPreviewWidth()) / 2);
            } else {
                float vscale = ((float) viewHeight) / ((float) config.getPreviewWidth());
                matrix.postScale(vscale, vscale);
                matrix.postTranslate(0, (viewWidth - vscale * config.getPreviewHeight()) / 2);
            }
        } else if (config.getRotation() == 180) {
            matrix.postRotate(180);
            matrix.postTranslate(config.getPreviewWidth(), config.getPreviewHeight());
            if (viewAspect > previewAspect) {
                float vscale = ((float) viewWidth) / ((float) config.getPreviewWidth());
                matrix.postScale(vscale, vscale);
                matrix.postTranslate(0, (viewHeight - vscale * config.getPreviewHeight()) / 2);
            } else {
                float vscale = ((float) viewHeight) / ((float) config.getPreviewHeight());
                matrix.postScale(vscale, vscale);
                matrix.postTranslate(0, (viewWidth - vscale * config.getPreviewWidth()) / 2);
            }
        } else {
            matrix.postRotate(270);
            matrix.postTranslate(0, config.getPreviewWidth());
            previewAspect = 1 / previewAspect;
            if (viewAspect > previewAspect) {
                float vscale = ((float) viewWidth) / ((float) config.getPreviewHeight());
                matrix.postScale(vscale, vscale);
                matrix.postTranslate(0, (viewHeight - vscale * config.getPreviewWidth()) / 2);
            } else {
                float vscale = ((float) viewHeight) / ((float) config.getPreviewWidth());
                matrix.postScale(vscale, vscale);
                matrix.postTranslate(0, (viewWidth - vscale * config.getPreviewHeight()) / 2);
            }
        }
        preview.setTransform(matrix);
    }


    private void init(AttributeSet attrs) {
        setState(STATE_IDLE);
        int coverLayoutId = 0;
        if (attrs == null) {
            config = new Config.Builder()
                    .cameraId(-1)
                    .previewWidth(640)
                    .previewHeight(480)
                    .rotation(0)
                    .needJpg(false)
                    .build();
            coverLayoutId = R.layout.camera2view_default_cover_layout;

        } else {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.Camera2View);
            config = new Config.Builder()
                    .cameraId(a.getInt(R.styleable.Camera2View_c2v_camera_id, -1))
                    .previewWidth(a.getInt(R.styleable.Camera2View_c2v_preview_width, 640))
                    .previewHeight(a.getInt(R.styleable.Camera2View_c2v_preview_height, 480))
                    .rotation(a.getInt(R.styleable.Camera2View_c2v_preview_rotation, 0))
                    .needJpg(a.getBoolean(R.styleable.Camera2View_c2v_need_jpg, false))
                    .build();
            coverLayoutId = a.getResourceId(R.styleable.Camera2View_c2v_cover_layout, R.layout.camera2view_default_cover_layout);
            a.recycle();
        }
        LayoutInflater.from(getContext()).inflate(R.layout.camera2view, this, true);

        previewContainer = findViewById(R.id.previewContainer);
        coverContainer = findViewById(R.id.coverContainer);

        preview = new TextureView(getContext());
        preview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
                Log.e(TAG, "onSurfaceTextureAvailable: " + surface);
                handleOnSurfaceTextureAvailable(surface, width, height);
            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
                Log.e(TAG, "onSurfaceTextureSizeChanged: " + surface);
                surface.setDefaultBufferSize(config.getPreviewWidth(), config.getPreviewHeight());
            }

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
                Log.e(TAG, "onSurfaceTextureDestroyed: ");
                stopPreview();
                changeSurfaceState(SURFACE_STATE_DESTROYED);
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {

            }
        });
        if (preview.isAvailable()) {
            handleOnSurfaceTextureAvailable(preview.getSurfaceTexture(), preview.getWidth(), preview.getHeight());
        }
        previewContainer.addView(preview, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        coverLayout = LayoutInflater.from(getContext()).inflate(coverLayoutId, coverContainer, true);


        singleThreadExecutor = Executors.newSingleThreadExecutor();
        cameraManager = (CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);
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
                    surfaceStateLock.lock();
                } catch (Exception e) {

                }
                while (true) {
                    if (surfaceState == SURFACE_STATE_IDLE) {
                        try {
                            surfaceStateCondition.await();
                        } catch (Exception e) {

                        }
                    } else if (surfaceState == SURFACE_STATE_DESTROYED) {
                        ce = new Exception("surface destroyed");
                        break;
                    } else {
                        ce = null;
                        break;
                    }
                }

                try {
                    surfaceStateLock.unlock();
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
                            try {
                                Image image = reader.acquireLatestImage();
                                try {
                                    if ((callback != null) && (userIntent == STATE_PREVIEWING)) {
                                        byte[] nv21 = Utils.yuv420_888toNV21(image);
                                        byte[] jpg = null;
                                        if (config.isNeedJpg()) {
                                            jpg = Utils.nv21ToJpg(nv21, image.getWidth(), image.getHeight(), 100);
                                        }
                                        callback.imageData(nv21, jpg, image.getWidth(), image.getHeight());
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
                    surfaces.add(previewSurface);
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
                    builder.addTarget(previewSurface);
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
        requestLayout();
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
                    previewSurface.release();
                } catch (Exception e) {

                }
                changeSurfaceState(SURFACE_STATE_DESTROYED);
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


    public static class Config {
        int previewWidth;
        int previewHeight;
        int rotation;
        int cameraId;
        boolean needJpg;

        public Config() {
        }

        private Config(Builder builder) {
            setPreviewWidth(builder.previewWidth);
            setPreviewHeight(builder.previewHeight);
            setRotation(builder.rotation);
            setCameraId(builder.cameraId);
            setNeedJpg(builder.needJpg);
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

        public int getCameraId() {
            return cameraId;
        }

        public void setCameraId(int cameraId) {
            this.cameraId = cameraId;
        }

        public boolean isNeedJpg() {
            return needJpg;
        }

        public void setNeedJpg(boolean needJpg) {
            this.needJpg = needJpg;
        }

        public static final class Builder {
            private int previewWidth;
            private int previewHeight;
            private int rotation;
            private int cameraId;
            private boolean needJpg;

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

            public Builder cameraId(int val) {
                cameraId = val;
                return this;
            }

            public Builder needJpg(boolean val) {
                needJpg = val;
                return this;
            }

            public Config build() {
                return new Config(this);
            }
        }
    }


    public interface Callback {
        void imageData(byte[] nv21, byte[] jpg, int width, int height);
    }

    public static class Utils {
        public static byte[] yuv420_888toNV21(Image image) {

            int width = image.getWidth();
            int height = image.getHeight();
            int ySize = width * height;
            int uvSize = width * height / 4;

            byte[] nv21 = new byte[ySize + uvSize * 2];

            ByteBuffer yBuffer = image.getPlanes()[0].getBuffer(); // Y
            ByteBuffer uBuffer = image.getPlanes()[1].getBuffer(); // U
            ByteBuffer vBuffer = image.getPlanes()[2].getBuffer(); // V

            int rowStride = image.getPlanes()[0].getRowStride();
            assert (image.getPlanes()[0].getPixelStride() == 1);

            int pos = 0;

            if (rowStride == width) { // likely
                yBuffer.get(nv21, 0, ySize);
                pos += ySize;
            } else {
                int yBufferPos = -rowStride; // not an actual position
                for (; pos < ySize; pos += width) {
                    yBufferPos += rowStride;
                    yBuffer.position(yBufferPos);
                    yBuffer.get(nv21, pos, width);
                }
            }

            rowStride = image.getPlanes()[2].getRowStride();
            int pixelStride = image.getPlanes()[2].getPixelStride();

            assert (rowStride == image.getPlanes()[1].getRowStride());
            assert (pixelStride == image.getPlanes()[1].getPixelStride());

            if (pixelStride == 2 && rowStride == width && uBuffer.get(0) == vBuffer.get(1)) {
                // maybe V an U planes overlap as per NV21, which means vBuffer[1] is alias of uBuffer[0]
                byte savePixel = vBuffer.get(1);
                try {
                    vBuffer.put(1, (byte) ~savePixel);
                    if (uBuffer.get(0) == (byte) ~savePixel) {
                        vBuffer.put(1, savePixel);
                        vBuffer.position(0);
                        uBuffer.position(0);
                        vBuffer.get(nv21, ySize, 1);
                        uBuffer.get(nv21, ySize + 1, uBuffer.remaining());

                        return nv21; // shortcut
                    }
                } catch (ReadOnlyBufferException ex) {
                    // unfortunately, we cannot check if vBuffer and uBuffer overlap
                }

                // unfortunately, the check failed. We must save U and V pixel by pixel
                vBuffer.put(1, savePixel);
            }

            // other optimizations could check if (pixelStride == 1) or (pixelStride == 2),
            // but performance gain would be less significant

            for (int row = 0; row < height / 2; row++) {
                for (int col = 0; col < width / 2; col++) {
                    int vuPos = col * pixelStride + row * rowStride;
                    nv21[pos++] = vBuffer.get(vuPos);
                    nv21[pos++] = uBuffer.get(vuPos);
                }
            }

            return nv21;
        }

        public static byte[] nv21ToJpg(byte[] nv21, int width, int height, int quality) {
            byte[] jpgData = null;
            ByteArrayOutputStream byteArrayOutputStream = null;
            try {
                YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
                byteArrayOutputStream = new ByteArrayOutputStream();
                yuvImage.compressToJpeg(new Rect(0, 0, width, height), quality, byteArrayOutputStream);
                byteArrayOutputStream.flush();
                jpgData = byteArrayOutputStream.toByteArray();
            } catch (Exception e) {
                jpgData = null;
            }
            try {
                if (byteArrayOutputStream != null) {
                    byteArrayOutputStream.close();
                }
            } catch (Exception e) {

            }
            return jpgData;
        }

    }


}
