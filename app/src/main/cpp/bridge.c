/* MonjaroTube JNI bridge. MIT. ByeDPI runs only in the private :byedpi process. */
#include <jni.h>
#include <stdatomic.h>
#include <stdlib.h>
#include <string.h>
#include <android/log.h>
#include <pthread.h>
#include <stdio.h>
#include <arpa/inet.h>
static JNIEnv *worker_env;
static jobject worker_self;
static jmethodID resolve_method;
int monjaro_resolve_ipv4(const char *host, void *address) {
    if (!worker_env || !worker_self || !resolve_method) return -1;
    JNIEnv *env = worker_env;
    jstring name = (*env)->NewStringUTF(env, host);
    if (!name) { (*env)->ExceptionClear(env); return -1; }
    jstring answer = (jstring)(*env)->CallObjectMethod(env, worker_self, resolve_method, name);
    (*env)->DeleteLocalRef(env, name);
    int result = -1;
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    else if (answer) {
        const char *ip = (*env)->GetStringUTFChars(env, answer, NULL);
        if (ip) { result = inet_pton(AF_INET, ip, address) == 1 ? 0 : -1; (*env)->ReleaseStringUTFChars(env, answer, ip); }
        else (*env)->ExceptionClear(env);
    }
    if (answer) (*env)->DeleteLocalRef(env, answer);
    return result;
}
static atomic_int state;
static atomic_llong requests, video_requests, tunnel_bytes;
static pthread_mutex_t error_lock = PTHREAD_MUTEX_INITIALIZER;
static char last_error[512];
void monjaro_resolution_failed(const char *host, const char *reason) {
    pthread_mutex_lock(&error_lock);
    snprintf(last_error, sizeof(last_error), "DNS %s: %s", host, reason);
    __android_log_print(ANDROID_LOG_WARN, "MonjaroDPI", "%s", last_error);
    pthread_mutex_unlock(&error_lock);
}
extern int main(int argc, char **argv);
void monjaro_listening(void) { atomic_store(&state, 1); }
void monjaro_bytes(size_t count) { atomic_fetch_add(&tunnel_bytes, count); }
void monjaro_host(const char *host, size_t length) {
    atomic_fetch_add(&requests, 1);
    const char suffix[] = ".googlevideo.com";
    size_t n = sizeof(suffix) - 1;
    if ((length > n && !memcmp(host + length - n, suffix, n)) ||
        (length == n - 1 && !memcmp(host, suffix + 1, n - 1))) {
        atomic_fetch_add(&video_requests, 1);
        __android_log_print(ANDROID_LOG_INFO, "MonjaroDPI", "SOCKS googlevideo request (domain only): %.*s", (int)length, host);
    }
}
JNIEXPORT jint JNICALL Java_ru_geelymonjaro_monjarotube_proxy_NativeDpi_run(JNIEnv *env, jobject self, jobjectArray arguments) {
    // The upstream resolver runs on this same worker thread, never the Android main thread.
    worker_env = env;
    worker_self = self;
    jclass klass = (*env)->GetObjectClass(env, self);
    resolve_method = (*env)->GetMethodID(env, klass, "resolveHost", "(Ljava/lang/String;)Ljava/lang/String;");
    (*env)->DeleteLocalRef(env, klass);
    if (!resolve_method) return -1;
    const char *base[] = {"ciadpi", "-i", "127.0.0.1", "-p", "1088", "-U", "-c", "128"};
    jsize count = (*env)->GetArrayLength(env, arguments);
    if (count > 64) return -1;
    char *argv[8 + 64 + 1] = {0};
    int argc = 0;
    for (unsigned i = 0; i < sizeof(base)/sizeof(base[0]); i++) argv[argc++] = strdup(base[i]);
    for (jsize i = 0; i < count; i++) {
        jstring value = (jstring)(*env)->GetObjectArrayElement(env, arguments, i);
        const char *utf = value ? (*env)->GetStringUTFChars(env, value, NULL) : NULL;
        if (utf) {
            argv[argc++] = strdup(utf);
            (*env)->ReleaseStringUTFChars(env, value, utf);
        }
        if (value) (*env)->DeleteLocalRef(env, value);
        if (!utf || !argv[argc - 1]) {
            for (int j = 0; j < argc; j++) free(argv[j]);
            return -1;
        }
    }
    int result = -1;
    for (int i = 0; i < argc; i++) if (!argv[i]) goto finished;
    result = main(argc, argv);
finished:
    worker_env = NULL;
    worker_self = NULL;
    resolve_method = NULL;
    atomic_store(&state, -1);
    for (int i = 0; i < argc; i++) free(argv[i]);
    return result;
}
JNIEXPORT jlongArray JNICALL Java_ru_geelymonjaro_monjarotube_proxy_NativeDpi_stats(JNIEnv *env, jobject self) {
    (void)self;
    jlong values[] = {atomic_load(&state), atomic_load(&requests), atomic_load(&video_requests), atomic_load(&tunnel_bytes)};
    jlongArray array = (*env)->NewLongArray(env, 4);
    if (array) (*env)->SetLongArrayRegion(env, array, 0, 4, values);
    return array;
}
JNIEXPORT jstring JNICALL Java_ru_geelymonjaro_monjarotube_proxy_NativeDpi_lastError(JNIEnv *env, jobject self) {
    (void)self;
    pthread_mutex_lock(&error_lock);
    jstring result = (*env)->NewStringUTF(env, last_error);
    pthread_mutex_unlock(&error_lock);
    return result;
}
