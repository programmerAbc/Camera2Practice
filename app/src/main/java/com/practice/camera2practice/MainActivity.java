package com.practice.camera2practice;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.databinding.DataBindingUtil;

import android.Manifest;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import com.practice.camera2practice.databinding.ActivityMain2Binding;
import com.practice.camera2view.Camera2View;

public class MainActivity extends AppCompatActivity {
    ActivityMain2Binding bd;
    Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mainHandler = new Handler(Looper.getMainLooper());
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
        bd.cameraView.setRotate(90);
        bd.cameraView.setCallback(new Camera2View.Callback() {
            @Override
            public void imageData(byte[] nv21, byte[] jpg, int width, int height) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Bitmap bitmap = BitmapFactory.decodeByteArray(jpg, 0, jpg.length);
                        bd.jpgIv.setImageBitmap(bitmap);
                    }
                });
            }
        });
        bd.cameraView.startPreview();
        bd.startPreviewBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                bd.cameraView.startPreview();
                bd.cameraView.startPreview();
                bd.cameraView.stopPreview();
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
        bd.action1Btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ConstraintLayout.LayoutParams layoutParams = (ConstraintLayout.LayoutParams) bd.cameraView.getLayoutParams();
                layoutParams.leftMargin = 50;
                layoutParams.topMargin = 50;
                layoutParams.rightMargin = 50;
                layoutParams.bottomMargin = 50;
                bd.cameraView.setLayoutParams(layoutParams);


            }
        });
        bd.action2Btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ConstraintLayout.LayoutParams layoutParams = (ConstraintLayout.LayoutParams) bd.cameraView.getLayoutParams();
                layoutParams.leftMargin = 0;
                layoutParams.topMargin = 0;
                layoutParams.rightMargin = 0;
                layoutParams.bottomMargin = 0;
                bd.cameraView.setLayoutParams(layoutParams);


            }
        });

    }

    @Override
    protected void onDestroy() {

        bd.cameraView.destroy();
        super.onDestroy();
    }
}