package com.practice.camera2view;

public class NativeLib {

    // Used to load the 'camera2view' library on application startup.
    static {
        System.loadLibrary("camera2view");
    }

    /**
     * A native method that is implemented by the 'camera2view' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();
}