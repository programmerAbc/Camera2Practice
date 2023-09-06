package com.practice.camera2practice;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.databinding.DataBindingUtil;

import android.Manifest;
import android.os.Bundle;
import android.view.View;

import com.practice.camera2practice.databinding.ActivityMain2Binding;

public class MainActivity2 extends AppCompatActivity {
    ActivityMain2Binding bd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bd = DataBindingUtil.setContentView(this, R.layout.activity_main2);
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1);

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        init();
    }

    private void init() {
        bd.cameraView.setCameraId(0);
        bd.cameraView.startPreview();
        bd.startPreviewBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bd.cameraView.startPreview();
            }
        });
        bd.stopPreviewBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bd.cameraView.stopPreview();
            }
        });
        bd.rotate0Btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bd.cameraView.setRotate(0);
            }
        });
        bd.rotate90Btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bd.cameraView.setRotate(90);
            }
        });
        bd.rotate180Btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bd.cameraView.setRotate(180);
            }
        });
        bd.rotate270Btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bd.cameraView.setRotate(270);
            }
        });


    }

    @Override
    protected void onDestroy() {

        bd.cameraView.destroy();
        super.onDestroy();
    }
}