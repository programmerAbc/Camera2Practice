#include <jni.h>
#include "libyuv.h"
//
// Created by consul on 2023/11/4.
//

extern "C"
JNIEXPORT jstring JNICALL
Java_com_practice_libyuv_LibYuv_stringFromJNI(JNIEnv *env, jclass clazz) {
    // TODO: implement stringFromJNI()
    return env->NewStringUTF("HELLO LIBYUV");
}


extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_practice_libyuv_LibYuv_nv21ToArgb(JNIEnv *env, jclass clazz, jbyteArray nv21, jint width,
                                           jint height) {
    int dataLength = env->GetArrayLength(nv21);
    uint8_t *nv21data = new uint8_t[dataLength];
    int sizeFrame = width * height;
    env->GetByteArrayRegion(nv21, 0, dataLength, reinterpret_cast<jbyte *>(nv21data));
    uint8_t *py = nv21data;
    uint8_t *puv = nv21data + sizeFrame;
    int argbDataLength = sizeFrame * 4;
    uint8_t *argbData = new uint8_t[argbDataLength];

    libyuv::NV21ToABGR(py, width, puv, width, argbData, width * 4, width, height);
    jbyteArray argbByteArray = env->NewByteArray(argbDataLength);
    env->SetByteArrayRegion(argbByteArray, 0, argbDataLength,
                            reinterpret_cast<const jbyte *>(argbData));
    delete[] nv21data;
    delete[] argbData;
    return argbByteArray;
}