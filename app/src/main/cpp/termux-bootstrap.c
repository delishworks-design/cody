#include <jni.h>

extern const unsigned char blob[];
extern const int blob_size;

JNIEXPORT jbyteArray JNICALL
Java_com_termux_app_install_TermuxBootstrapNative_getZip(JNIEnv *env, jclass clazz)
{
    (void) clazz;
    jbyteArray ret = (*env)->NewByteArray(env, blob_size);
    if (ret == NULL) return NULL;
    (*env)->SetByteArrayRegion(env, ret, 0, blob_size, (const jbyte *) blob);
    return ret;
}
