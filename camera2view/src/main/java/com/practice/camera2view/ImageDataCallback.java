package com.practice.camera2view;

public interface ImageDataCallback {
    void imageData(byte[] nv21, int width, int height);
}
