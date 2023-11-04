package com.practice.camera2practice;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.databinding.DataBindingUtil;

import android.Manifest;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;

import com.practice.camera2practice.databinding.ActivityMain2Binding;
import com.practice.camera2view.Camera2Helper;
import com.practice.camera2view.Config;
import com.practice.camera2view.ImageDataCallback;
import com.practice.camera2view.PreviewFrame;


public class MainActivity2 extends AppCompatActivity {

    ActivityMain2Binding bd;
    Camera2Helper camera2Helper;
    PreviewFrame previewFrame;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bd = DataBindingUtil.setContentView(this, R.layout.activity_main2);
        previewFrame = new PreviewFrame();
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        init();
    }

    private void init() {
        camera2Helper = new Camera2Helper(this, new Config.Builder()
                .cameraId(0)
                .previewHeight(480)
                .previewWidth(640)
                .fast(true)
                .build());
        camera2Helper.setCallback(new ImageDataCallback() {
            @Override
            public void imageData(byte[] nv21, int width, int height) {
                previewFrame.update(nv21, width, height);
            }
        });
        previewFrame.addCallback(new PreviewFrame.Callback() {
            @Override
            public void preview(Bitmap bitmap) {
                bd.previewIv.setImageBitmap(bitmap);
            }
        });
        bd.startPreviewBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                camera2Helper.startPreview();
            }
        });
        bd.stopPreviewBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                camera2Helper.stopPreview();
            }
        });
        bd.rotate0Btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                camera2Helper.setRotate(0);
            }
        });
        bd.rotate90Btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                camera2Helper.setRotate(90);
            }
        });
        bd.rotate180Btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                camera2Helper.setRotate(180);
            }
        });
        bd.rotate270Btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                camera2Helper.setRotate(270);
            }
        });


    }

    @Override
    protected void onDestroy() {
        camera2Helper.destroy();
        super.onDestroy();
    }
}