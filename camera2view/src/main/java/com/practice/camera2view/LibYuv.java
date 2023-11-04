package com.practice.camera2view;

public class LibYuv {

    // Used to load the 'libyuv' library on application startup.
    static {
        System.loadLibrary("yuv");
    }

    /**
     * A native method that is implemented by the 'libyuv' native library,
     * which is packaged with this application.
     */
    public static native String stringFromJNI();

    public static native byte[] nv21ToArgb(byte[] nv21, int width, int height);

    public static native byte[] i420ToNV21(byte[] i420, int width, int height);

    public static native byte[] rotateI420(byte[] i420, int width, int height, int degree);

    public static native byte[] rotateNV21(byte[] nv21, int width, int height, int degree);
}