#include <android/log.h>
#include <dlfcn.h>
#include <jni.h>

#define TAG "rencompat"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

typedef void (*resize7_fn)(JNIEnv *, jclass);
typedef void (*mouse7_fn)(JNIEnv *, jclass, jint, jint, jfloat, jfloat,
                          jboolean);
typedef void (*resize699_fn)(JNIEnv *, jclass, jint, jint, jint, jfloat);
typedef void (*mouse699_fn)(JNIEnv *, jclass, jint, jint, jfloat, jfloat);

static resize7_fn g_onNativeResize7 = NULL;
static mouse7_fn g_onNativeMouse7 = NULL;
static resize699_fn g_onNativeResize699 = NULL;
static mouse699_fn g_onNativeMouse699 = NULL;

static void init_symbols(void) {
  if (!g_onNativeResize7) {
    g_onNativeResize7 = (resize7_fn)dlsym(
        RTLD_DEFAULT, "Java_org_libsdl_app_SDLActivity_onNativeResize");
  }
  if (!g_onNativeMouse7) {
    g_onNativeMouse7 = (mouse7_fn)dlsym(
        RTLD_DEFAULT, "Java_org_libsdl_app_SDLActivity_onNativeMouse");
  }
}

JNIEXPORT void JNICALL Java_org_libsdl_app_SDLActivity_onNativeResize__IIIF(
    JNIEnv *env, jclass cls, jint x, jint y, jint format, jfloat rate) {
  if (!g_onNativeResize699) {
    g_onNativeResize699 = (resize699_fn)dlsym(
        RTLD_DEFAULT, "Java_org_libsdl_app_SDLActivity_onNativeResize");
  }
  if (g_onNativeResize699) {
    g_onNativeResize699(env, cls, x, y, format, rate);
  } else {
    LOGE("Failed to find 6.99 Java_org_libsdl_app_SDLActivity_onNativeResize");
  }
}

JNIEXPORT void JNICALL Java_org_libsdl_app_SDLActivity_onNativeMouse__IIFF(
    JNIEnv *env, jclass cls, jint button, jint action, jfloat x, jfloat y) {
  if (!g_onNativeMouse699) {
    g_onNativeMouse699 = (mouse699_fn)dlsym(
        RTLD_DEFAULT, "Java_org_libsdl_app_SDLActivity_onNativeMouse");
  }
  if (g_onNativeMouse699) {
    g_onNativeMouse699(env, cls, button, action, x, y);
  } else {
    LOGE("Failed to find 6.99 Java_org_libsdl_app_SDLActivity_onNativeMouse");
  }
}

// backwards compatibility aliases
JNIEXPORT void JNICALL
Java_org_libsdl_app_SDLActivity_onNativeResize7(JNIEnv *env, jclass cls) {
  init_symbols();
  if (g_onNativeResize7) {
    g_onNativeResize7(env, cls);
  }
}

JNIEXPORT void JNICALL Java_org_libsdl_app_SDLActivity_onNativeMouse7(
    JNIEnv *env, jclass cls, jint button, jint action, jfloat x, jfloat y,
    jboolean relative) {
  init_symbols();
  if (g_onNativeMouse7) {
    g_onNativeMouse7(env, cls, button, action, x, y, relative);
  }
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
  LOGI("librencompat initialized");
  return JNI_VERSION_1_6;
}
