/*
 * Native JVM launcher for LumoCraft.
 *
 * Android mounts app-writable directories with `noexec`, so executing
 * `bin/java` from the extracted runtime fails with EACCES. Instead the
 * JVM is loaded in-process through JNI: `libjli.so` is dlopen'd and
 * `JLI_Launch` runs the game JVM on a dedicated thread. The game's
 * stdout/stderr are redirected into a pipe that Kotlin streams into the
 * session log, and the JVM exit code is returned to Kotlin.
 *
 * JNI surface (matching NativeJvmLauncher in
 * com.lumocraft.app.data.launch):
 *
 *   launch(javaHome, workingDirectory, environment, argv, stdoutFd) -> int
 *   exitCode()  -> int   (blocks until the JVM exits)
 *   lastError() -> String
 *   cancel()    -> void  (best-effort System.exit inside the game JVM)
 *
 * All functions are member externals on the singleton class, so the JNI
 * entry points receive an unused jobject `this`.
 */
#ifndef LUMOCRAFT_JVM_LAUNCHER_H
#define LUMOCRAFT_JVM_LAUNCHER_H

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jint JNICALL
Java_com_lumocraft_app_data_launch_NativeJvmLauncher_launch(
    JNIEnv* env, jobject thiz,
    jstring javaHome,
    jstring workingDirectory,
    jobjectArray environment,
    jobjectArray argv,
    jobject stdoutFd);

JNIEXPORT jint JNICALL
Java_com_lumocraft_app_data_launch_NativeJvmLauncher_exitCode(
    JNIEnv* env, jobject thiz);

JNIEXPORT jstring JNICALL
Java_com_lumocraft_app_data_launch_NativeJvmLauncher_lastError(
    JNIEnv* env, jobject thiz);

JNIEXPORT void JNICALL
Java_com_lumocraft_app_data_launch_NativeJvmLauncher_cancel(
    JNIEnv* env, jobject thiz);

#ifdef __cplusplus
}
#endif

#endif /* LUMOCRAFT_JVM_LAUNCHER_H */