package com.practice.camera2practice;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.viewfinder.CameraViewfinder;
import androidx.camera.viewfinder.ViewfinderSurfaceRequest;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.practice.camera2practice.databinding.ActivityMainBinding;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    public static final String TAG = MainActivity.class.getSimpleName();
    ActivityMainBinding bd;
    CameraManager manager;
    CameraCharacteristics cameraCharacteristics;

    HandlerThread cameraThread;
    HandlerThread imageThread;
    Handler cameraHandler;
    Handler imageHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bd = DataBindingUtil.setContentView(this, R.layout.activity_main);
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1);
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        init();
    }

    @SuppressLint("MissingPermission")
    private void init() {
        cameraThread = new HandlerThread("cameraThread");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());
        imageThread = new HandlerThread("imageThread");
        imageThread.start();
        imageHandler = new Handler(imageThread.getLooper());
        manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            cameraCharacteristics = manager.getCameraCharacteristics("0");
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }


        Size previewResolution = new Size(640, 480);
        ViewfinderSurfaceRequest viewfinderSurfaceRequest = new ViewfinderSurfaceRequest.Builder(previewResolution)
                .setLensFacing(CameraCharacteristics.LENS_FACING_FRONT)
                .setSensorOrientation(90)
                .build();
        ListenableFuture<Surface> surfaceListenableFuture = bd.frontVF.requestSurfaceAsync(viewfinderSurfaceRequest);
        Futures.addCallback(surfaceListenableFuture, new FutureCallback<Surface>() {
            @Override
            public void onSuccess(Surface result) {

                ImageReader imageReader = ImageReader.newInstance(640, 480, ImageFormat.YUV_420_888, 2);
                imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                    @Override
                    public void onImageAvailable(ImageReader reader) {
                        Image image = reader.acquireLatestImage();
                        Log.e(TAG, "onImageAvailable: " + image.getWidth() + "," + image.getHeight());
                        image.close();
                    }
                }, imageHandler);
                ArrayList<Surface> surfaces = new ArrayList<>();
                surfaces.add(result);
                surfaces.add(imageReader.getSurface());
                try {
                    manager.openCamera("1", new CameraDevice.StateCallback() {
                        @Override
                        public void onOpened(@NonNull CameraDevice camera) {
                            try {
                                camera.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                                    @Override
                                    public void onConfigured(@NonNull CameraCaptureSession session) {
                                        try {
                                            CaptureRequest.Builder builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                                            builder.addTarget(result);
                                            builder.addTarget(imageReader.getSurface());
                                            session.setRepeatingRequest(builder.build(), null, cameraHandler);
                                            session.stopRepeating();
                                        } catch (CameraAccessException e) {
                                            throw new RuntimeException(e);
                                        }
                                    }

                                    @Override
                                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                                    }
                                }, cameraHandler);
                            } catch (CameraAccessException e) {
                                throw new RuntimeException(e);
                            }
                        }

                        @Override
                        public void onDisconnected(@NonNull CameraDevice camera) {

                        }

                        @Override
                        public void onError(@NonNull CameraDevice camera, int error) {

                        }
                    }, cameraHandler);
                } catch (CameraAccessException e) {
                    throw new RuntimeException(e);
                }


            }

            @Override
            public void onFailure(Throwable t) {

            }
        }, ContextCompat.getMainExecutor(MainActivity.this));


    }
}