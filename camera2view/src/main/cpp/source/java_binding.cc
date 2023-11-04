#include <jni.h>
#include <cstring>
#include "libyuv.h"
//
// Created by consul on 2023/11/4.
//

extern "C"
JNIEXPORT jstring JNICALL
Java_com_practice_camera2view_LibYuv_stringFromJNI(JNIEnv *env, jclass clazz) {
    // TODO: implement stringFromJNI()
    return env->NewStringUTF("HELLO LIBYUV");
}
extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_practice_camera2view_LibYuv_nv21ToArgb(JNIEnv *env, jclass clazz, jbyteArray nv21,
                                                jint width, jint height) {
    int dataLength = env->GetArrayLength(nv21);
    uint8_t *nv21data = new uint8_t[dataLength];
    int sizeFrame = width * height;
    env->GetByteArrayRegion(nv21, 0, dataLength, reinterpret_cast<jbyte *>(nv21data));
    uint8_t *py = nv21data;
    uint8_t *pvu = nv21data + sizeFrame;
    int argbDataLength = sizeFrame * 4;
    uint8_t *argbData = new uint8_t[argbDataLength];

    libyuv::NV21ToABGR(py, width, pvu, width, argbData, width * 4, width, height);
    jbyteArray argbByteArray = env->NewByteArray(argbDataLength);
    env->SetByteArrayRegion(argbByteArray, 0, argbDataLength,
                            reinterpret_cast<const jbyte *>(argbData));
    delete[] nv21data;
    delete[] argbData;
    return argbByteArray;
}
extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_practice_camera2view_LibYuv_i420ToNV21(JNIEnv *env, jclass clazz, jbyteArray i420,
                                                jint width, jint height) {
    int sizeFrame = width * height;
    int dataLength = env->GetArrayLength(i420);
    uint8_t *i420data = new uint8_t[dataLength];
    env->GetByteArrayRegion(i420, 0, dataLength, reinterpret_cast<jbyte *>(i420data));
    uint8_t *py = i420data;
    uint8_t *pu = i420data + sizeFrame;
    uint8_t *pv = i420data + sizeFrame + sizeFrame / 4;
    uint8_t *nv21data = new uint8_t[dataLength];
    uint8_t *nv21y = nv21data;
    uint8_t *nv21vu = nv21data + sizeFrame;
    libyuv::I420ToNV21(py, width, pu, width / 2, pv, width / 2, nv21y, width, nv21vu, width, width,
                       height);
    jbyteArray nv21ByteArray = env->NewByteArray(dataLength);
    env->SetByteArrayRegion(nv21ByteArray, 0, dataLength,
                            reinterpret_cast<const jbyte *>(nv21data));
    delete[] i420data;
    delete[] nv21data;
    return nv21ByteArray;
}
extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_practice_camera2view_LibYuv_rotateNV21(JNIEnv *env, jclass clazz, jbyteArray nv21,
                                                jint width, jint height, jint degree) {

    int dataLength = env->GetArrayLength(nv21);
    uint8_t *nv21data = new uint8_t[dataLength];
    int sizeFrame = width * height;
    env->GetByteArrayRegion(nv21, 0, dataLength, reinterpret_cast<jbyte *>(nv21data));
    uint8_t *py = nv21data;
    uint8_t *pvu = nv21data + sizeFrame;
    uint8_t *i420data = new uint8_t[dataLength];
    uint8_t *dstY = i420data;
    uint8_t *dstU = i420data + sizeFrame;
    uint8_t *dstV = i420data + sizeFrame + sizeFrame / 4;
    libyuv::NV21ToI420(py, width, pvu, width, dstY, width, dstU, width / 2, dstV, width / 2, width,
                       height);

    libyuv::RotationModeEnum rotationMode;
    int dWidth = 0;
    int dHeight = 0;
    switch (degree) {
        case 0:
            dWidth = width;
            dHeight = height;
            rotationMode = libyuv::kRotate0;
            break;
        case 90:
            dWidth = height;
            dHeight = width;
            rotationMode = libyuv::kRotate90;
            break;
        case 180:
            dWidth = width;
            dHeight = height;
            rotationMode = libyuv::kRotate180;
            break;
        default:
            dWidth = height;
            dHeight = width;
            rotationMode = libyuv::kRotate270;
            break;
    }

    uint8_t *i420Rdata = new uint8_t[dataLength];
    uint8_t *dstRY = i420Rdata;
    uint8_t *dstRU = i420Rdata + sizeFrame;
    uint8_t *dstRV = i420Rdata + sizeFrame + sizeFrame / 4;

    libyuv::I420Rotate(dstY, width, dstU, width / 2, dstV, width / 2, dstRY, dWidth, dstRU,
                       dWidth / 2, dstRV, dWidth / 2, width, height, rotationMode);

    libyuv::I420ToNV21(dstRY, dWidth, dstRU, dWidth / 2, dstRV, dWidth / 2, py, dWidth, pvu, dWidth,
                       dWidth, dHeight);

    jbyteArray nv21ByteArray = env->NewByteArray(dataLength);
    env->SetByteArrayRegion(nv21ByteArray, 0, dataLength,
                            reinterpret_cast<const jbyte *>(nv21data));
    delete[] nv21data;
    delete[] i420data;
    delete[] i420Rdata;
    return nv21ByteArray;
}
extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_practice_camera2view_LibYuv_rotateI420(JNIEnv *env, jclass clazz, jbyteArray i420,
                                                jint width, jint height, jint degree) {
    int sizeFrame = width * height;
    int dataLength = env->GetArrayLength(i420);
    uint8_t *i420data = new uint8_t[dataLength];
    env->GetByteArrayRegion(i420, 0, dataLength, reinterpret_cast<jbyte *>(i420data));
    uint8_t *py = i420data;
    uint8_t *pu = i420data + sizeFrame;
    uint8_t *pv = i420data + sizeFrame + sizeFrame / 4;

    uint8_t *dstData = new uint8_t[dataLength];

    uint8_t *dstY = dstData;
    uint8_t *dstU = dstData + sizeFrame;
    uint8_t *dstV = dstData + sizeFrame + sizeFrame / 4;

    libyuv::RotationModeEnum rotationMode;
    int dWidth = 0;
    int dHeight = 0;
    switch (degree) {
        case 0:
            dWidth = width;
            dHeight = height;
            rotationMode = libyuv::kRotate0;
            break;
        case 90:
            dWidth = height;
            dHeight = width;
            rotationMode = libyuv::kRotate90;
            break;
        case 180:
            dWidth = width;
            dHeight = height;
            rotationMode = libyuv::kRotate180;
            break;
        default:
            dWidth = height;
            dHeight = width;
            rotationMode = libyuv::kRotate270;
            break;
    }
    libyuv::I420Rotate(py, width, pu, width / 2, pv, width / 2, dstY, dWidth, dstU, dWidth / 2,
                       dstV,
                       dWidth / 2, width, height, rotationMode);

    jbyteArray dstArray = env->NewByteArray(dataLength);
    env->SetByteArrayRegion(dstArray, 0, dataLength, reinterpret_cast<const jbyte *>(dstData));
    delete[] i420data;
    delete[] dstData;
    return dstArray;
}


