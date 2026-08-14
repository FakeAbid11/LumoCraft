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
 *   waitForExit(timeoutMillis) -> int  (bounded poll; kExitTimeout = -6)
 *   lastError() -> String
 *   cancel()    -> void  (best-effort System.exit inside the game JVM)
 *   recycleLaunch() -> void  (reap the JLI thread after exit; reset state)
 *
 * waitForExit never joins the JLI thread: it polls a completion flag, so
 * the caller can time out (kExitTimeout) and keep the app responsive.
 * The thread is joined only in recycleLaunch, after it has already
 * exited. All functions are member externals on the singleton class, so
 * the JNI entry points receive an unused jobject `this`.
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

/*
 * Polls for JLI_Launch completion. Returns the JVM exit code when the
 * JLI thread finished within timeoutMillis (<= 0 waits indefinitely),
 * otherwise kExitTimeout (-6). Never blocks the caller in pthread_join.
 */
JNIEXPORT jint JNICALL
Java_com_lumocraft_app_data_launch_NativeJvmLauncher_waitForExit(
    JNIEnv* env, jobject thiz, jlong timeoutMillis);

JNIEXPORT jstring JNICALL
Java_com_lumocraft_app_data_launch_NativeJvmLauncher_lastError(
    JNIEnv* env, jobject thiz);

JNIEXPORT void JNICALL
Java_com_lumocraft_app_data_launch_NativeJvmLauncher_cancel(
    JNIEnv* env, jobject thiz);

/*
 * Reaps the JLI thread once it has exited (pthread_join of a finished
 * thread) and resets the launch state so a new session can start. No-op
 * while the JVM thread is still running: a wedged JVM must be handled by
 * restarting the app, never by allowing a second in-process JVM.
 */
JNIEXPORT void JNICALL
Java_com_lumocraft_app_data_launch_NativeJvmLauncher_recycleLaunch(
    JNIEnv* env, jobject thiz);

#ifdef __cplusplus
}
#endif

#endif /* LUMOCRAFT_JVM_LAUNCHER_H */