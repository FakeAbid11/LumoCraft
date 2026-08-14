/*
 * In-process JVM launcher: dlopen(libjli.so) -> JLI_Launch -> Minecraft.
 *
 * Why not ProcessBuilder? Android mounts app-writable directories with
 * `noexec`, so exec'ing <runtime>/bin/java fails with EACCES
 * (error=13). Shared libraries are unaffected by `noexec`, so the JVM is
 * loaded through its own launcher library instead:
 *
 *   1. dlopen <javaHome>/lib/jli/libjli.so
 *   2. resolve JLI_Launch (JDK 9+ launcher entry point)
 *   3. redirect fd 1/2 into the pipe Kotlin passed in
 *   4. run JLI_Launch on a dedicated pthread with the full
 *      [java, -D..., -cp ..., MainClass, game args] command line
 *   5. JLI_Launch resolves the JRE home from libjli.so's own location
 *      (dladdr) and dlopens <javaHome>/lib/server/libjvm.so itself
 *   6. when the game exits, JLI_Launch returns the JVM exit code,
 *      stdout/stderr are restored, the pipe write end is closed (EOF)
 *      and the exit code is handed back to Kotlin
 *
 * The JVM and the launcher (ART) share one process; cancellation asks
 * the game JVM to exit through its own JNI invocation API
 * (JNI_GetCreatedJavaVMs + System.exit), which runs shutdown hooks and
 * makes JLI_Launch return normally.
 *
 * Every step is logged to logcat so failures stay diagnosable; no step
 * calls exit() directly (JLI may exit the process on JVM initialization
 * errors, which is mitigated by Kotlin preflight checks).
 */
#include "jvm_launcher.h"

#include <android/log.h>
#include <dlfcn.h>
#include <errno.h>
#include <pthread.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include <atomic>
#include <string>
#include <vector>

#define LOG_TAG "LumoCraft/JvmLauncher"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

/*
 * JLI_Launch as declared in <JDK>/src/java.base/share/native/libjli/java.h
 * (JDK 9+; identical for 11/17/21). The appclassc/appclassv parameters
 * replaced the single `appclass` string in JDK 9.
 */
using JLI_LaunchFn = int (*)(int argc, char** argv,
                             int jargc, const char** jargv,
                             int appclassc, const char** appclassv,
                             const char* fullversion, const char* dotversion,
                             const char* pname, const char* lname,
                             jboolean javaargs, jboolean cpwildcard,
                             jboolean javaw, jint ergo);

/*
 * launch() error codes handed back to Kotlin. 0 means the JVM thread
 * was started; negative codes map to user-friendly messages.
 */
constexpr int kOk = 0;
constexpr int kAlreadyLaunched = -1;
constexpr int kLibJliMissing = -2;
constexpr int kDlopenFailed = -3;
constexpr int kSymbolMissing = -4;
constexpr int kSetupFailed = -5;

/** Everything the JLI thread needs; owned by the thread, deleted on exit. */
struct LaunchState {
    JLI_LaunchFn jli_launch = nullptr;
    std::vector<std::string> args;
    std::vector<char*> argv;       // points into args; NULL-terminated
    int saved_stdout = -1;         // original fd 1 before redirect
    int saved_stderr = -1;         // original fd 2 before redirect
    int pipe_fd = -1;              // write end of the stdout/stderr pipe
};

std::atomic<bool> g_launched{false};
std::atomic<bool> g_cancel_requested{false};
std::atomic<bool> g_thread_done{false};
std::atomic<int> g_exit_code{1};
pthread_t g_jli_thread{};

std::string g_libjvm_path;
std::string g_last_error;

std::string jstringToString(JNIEnv* env, jstring str) {
    if (str == nullptr) return {};
    const char* utf = env->GetStringUTFChars(str, nullptr);
    if (utf == nullptr) return {};
    std::string out(utf);
    env->ReleaseStringUTFChars(str, utf);
    return out;
}

std::vector<std::string> jarrayToStrings(JNIEnv* env, jobjectArray array) {
    std::vector<std::string> out;
    if (array == nullptr) return out;
    const jsize len = env->GetArrayLength(array);
    out.reserve(len);
    for (jsize i = 0; i < len; ++i) {
        jstring str = static_cast<jstring>(env->GetObjectArrayElement(array, i));
        if (str != nullptr) out.push_back(jstringToString(env, str));
        env->DeleteLocalRef(str);
    }
    return out;
}

/** Reads the platform fd out of a java.io.FileDescriptor. */
int fdFromJobject(JNIEnv* env, jobject fdObj) {
    jclass fdClass = env->FindClass("java/io/FileDescriptor");
    if (fdClass == nullptr) return -1;
    jfieldID fdField = env->GetFieldID(fdClass, "fd", "I");
    if (fdField == nullptr) return -1;
    jint fd = env->GetIntField(fdObj, fdField);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return -1;
    }
    return static_cast<int>(fd);
}

void setLastError(const std::string& message) {
    g_last_error = message;
    LOGE("%s", message.c_str());
}

/*
 * Runs JLI_Launch until the game JVM exits, then restores stdout/stderr
 * and closes the pipe write end so Kotlin's reader sees EOF.
 */
void* jliThreadMain(void* raw) {
    auto* state = static_cast<LaunchState*>(raw);
    if (g_cancel_requested.load()) {
        LOGW("JLI_Launch cancelled before the JVM thread started");
        g_exit_code.store(1);
    } else {
        LOGI("JLI_Launch entering (%zu args, pname=java lname=java)",
             state->argv.size());
        const int code = state->jli_launch(
            static_cast<int>(state->argv.size()), state->argv.data(),
            /*jargc*/ 0, /*jargv*/ nullptr,
            /*appclassc*/ 0, /*appclassv*/ nullptr,
            /*fullversion*/ nullptr, /*dotversion*/ nullptr,
            /*pname*/ "java", /*lname*/ "java",
            /*javaargs*/ JNI_FALSE, /*cpwildcard*/ JNI_FALSE,
            /*javaw*/ JNI_FALSE, /*ergo*/ 0);
        LOGI("JLI_Launch returned, exit code %d", code);
        g_exit_code.store(code);
    }
    if (state->saved_stdout >= 0) dup2(state->saved_stdout, STDOUT_FILENO);
    if (state->saved_stderr >= 0) dup2(state->saved_stderr, STDERR_FILENO);
    if (state->pipe_fd >= 0) close(state->pipe_fd);
    g_thread_done.store(true);
    delete state;
    return nullptr;
}

}  // namespace

JNIEXPORT jint JNICALL
Java_com_lumocraft_app_data_launch_NativeJvmLauncher_launch(
    JNIEnv* env, jobject, jstring javaHome, jstring workingDirectory,
    jobjectArray environment, jobjectArray argv, jobject stdoutFd) {
    if (g_launched.exchange(true)) {
        setLastError("A JVM launch is already in progress");
        return kAlreadyLaunched;
    }
    g_cancel_requested.store(false);
    g_thread_done.store(false);
    g_exit_code.store(1);

    // Ownership contract: once Kotlin passes the pipe, the native side
    // owns the write end and closes it on every path (error returns and
    // thread exit). Kotlin only ever closes the read end, so a closed fd
    // number is never re-close()d here.
    const int pipe_fd = fdFromJobject(env, stdoutFd);
    auto close_pipe = [&]() {
        if (pipe_fd >= 0) close(pipe_fd);
    };

    const std::string java_home = jstringToString(env, javaHome);
    if (java_home.empty()) {
        setLastError("javaHome is empty");
        close_pipe();
        return kSetupFailed;
    }

    // 1. Locate and load the JLI launcher library. Temurin ships it at
    //    lib/jli/libjli.so; some builds (e.g. Microsoft) keep it in lib/.
    const std::string jli_path = java_home + "/lib/jli/libjli.so";
    const std::string jli_flat_path = java_home + "/lib/libjli.so";
    const std::string jli_paths = jli_path + " or " + jli_flat_path;
    LOGI("JLI library path: %s", jli_paths.c_str());
    const char* jli_candidate =
        (access(jli_path.c_str(), F_OK) == 0) ? jli_path.c_str()
        : (access(jli_flat_path.c_str(), F_OK) == 0) ? jli_flat_path.c_str()
        : nullptr;
    if (jli_candidate == nullptr) {
        setLastError("Launcher library not found: " + jli_paths);
        close_pipe();
        return kLibJliMissing;
    }
    void* jli = dlopen(jli_candidate, RTLD_NOW | RTLD_GLOBAL);
    if (jli == nullptr) {
        setLastError("dlopen(" + std::string(jli_candidate) + ") failed: " +
                     dlerror());
        close_pipe();
        return kDlopenFailed;
    }
    LOGI("dlopen ok: %s", jli_candidate);

    // 2. Resolve the JLI_Launch entry point.
    auto launch_fn = reinterpret_cast<JLI_LaunchFn>(dlsym(jli, "JLI_Launch"));
    if (launch_fn == nullptr) {
        setLastError("Symbol JLI_Launch not found in " + jli_path +
                     ": " + dlerror());
        close_pipe();
        return kSymbolMissing;
    }
    LOGI("JLI_Launch resolved at %p", reinterpret_cast<void*>(launch_fn));

    // 3. JLI resolves the JRE home from libjli.so's location and loads
    //    lib/server/libjvm.so itself; remember the path for cancel().
    g_libjvm_path = java_home + "/lib/server/libjvm.so";

    // 4. Apply the process environment (JAVA_HOME, LD_LIBRARY_PATH, ...).
    const std::vector<std::string> env_vars = jarrayToStrings(env, environment);
    for (const std::string& kv : env_vars) {
        const size_t eq = kv.find('=');
        if (eq == std::string::npos) continue;
        if (setenv(kv.substr(0, eq).c_str(), kv.substr(eq + 1).c_str(), 1) != 0) {
            setLastError("setenv(" + kv.substr(0, eq) + ") failed: " +
                         strerror(errno));
            close_pipe();
            return kSetupFailed;
        }
    }

    // 5. Working directory for the game session.
    const std::string work_dir = jstringToString(env, workingDirectory);
    if (!work_dir.empty() && chdir(work_dir.c_str()) != 0) {
        setLastError("chdir(" + work_dir + ") failed: " + strerror(errno));
        close_pipe();
        return kSetupFailed;
    }

    // 6. Redirect stdout/stderr into the pipe Kotlin created, so the game
    //    output streams into the session log without an external process.
    //    On failure the partially redirected fds are restored first.
    const int saved_stdout = dup(STDOUT_FILENO);
    const int saved_stderr = dup(STDERR_FILENO);
    auto restore_output = [&]() {
        if (saved_stdout >= 0) dup2(saved_stdout, STDOUT_FILENO);
        if (saved_stderr >= 0) dup2(saved_stderr, STDERR_FILENO);
    };
    if (dup2(pipe_fd, STDOUT_FILENO) < 0 || dup2(pipe_fd, STDERR_FILENO) < 0) {
        restore_output();
        setLastError("dup2 to stdout/stderr failed: " +
                     std::string(strerror(errno)));
        close_pipe();
        return kSetupFailed;
    }
    LOGI("stdout/stderr redirected into pipe fd %d", pipe_fd);

    // 7. Build the command line: [java, ...jvm args, mainClass, ...game args].
    //    JLI walks argv until a NULL terminator (SelectVersion/ParseArguments),
    //    so the array must be NULL-terminated.
    auto* state = new LaunchState();
    state->jli_launch = launch_fn;
    state->args = jarrayToStrings(env, argv);
    state->argv.reserve(state->args.size() + 1);
    for (std::string& arg : state->args) {
        state->argv.push_back(const_cast<char*>(arg.c_str()));
    }
    state->argv.push_back(nullptr);
    state->saved_stdout = saved_stdout;
    state->saved_stderr = saved_stderr;
    state->pipe_fd = pipe_fd;

    if (pthread_create(&g_jli_thread, nullptr, jliThreadMain, state) != 0) {
        restore_output();
        setLastError("pthread_create failed: " + std::string(strerror(errno)));
        close_pipe();
        delete state;
        return kSetupFailed;
    }

    LOGI("JVM thread started (pid %d)", getpid());
    return kOk;
}

JNIEXPORT jint JNICALL
Java_com_lumocraft_app_data_launch_NativeJvmLauncher_exitCode(
    JNIEnv*, jobject) {
    if (!g_launched.load()) {
        setLastError("No JVM launch in progress");
        return -1;
    }
    if (!g_thread_done.load()) {
        pthread_join(g_jli_thread, nullptr);
    }
    const int code = g_exit_code.load();
    LOGI("JVM exit code %d", code);
    return code;
}

JNIEXPORT jstring JNICALL
Java_com_lumocraft_app_data_launch_NativeJvmLauncher_lastError(
    JNIEnv* env, jobject) {
    return env->NewStringUTF(g_last_error.c_str());
}

JNIEXPORT void JNICALL
Java_com_lumocraft_app_data_launch_NativeJvmLauncher_cancel(
    JNIEnv*, jobject) {
    g_cancel_requested.store(true);
    if (!g_launched.load() || g_thread_done.load()) return;

    // Best effort: ask the game JVM to exit through its own invocation
    // API so shutdown hooks run and JLI_Launch returns a code. If the VM
    // is not created yet (startup race), the cancel flag above stops the
    // JLI thread before it runs JLI_Launch.
    void* jvm_lib = dlopen(g_libjvm_path.c_str(), RTLD_NOW);
    if (jvm_lib == nullptr) {
        LOGW("cancel: dlopen(%s) failed: %s",
             g_libjvm_path.c_str(), dlerror());
        return;
    }
    using GetCreatedJavaVMsFn = jint (*)(JavaVM**, jsize, jsize*);
    auto get_vms = reinterpret_cast<GetCreatedJavaVMsFn>(
        dlsym(jvm_lib, "JNI_GetCreatedJavaVMs"));
    if (get_vms == nullptr) return;

    JavaVM* vm = nullptr;
    jsize count = 0;
    if (get_vms(&vm, 1, &count) != JNI_OK || count == 0 || vm == nullptr) {
        LOGW("cancel: HotSpot VM not created yet");
        return;
    }
    JNIEnv* jvm_env = nullptr;
    if (vm->AttachCurrentThread(reinterpret_cast<void**>(&jvm_env), nullptr) != JNI_OK ||
        jvm_env == nullptr) {
        LOGW("cancel: AttachCurrentThread failed");
        return;
    }
    jclass system = jvm_env->FindClass("java/lang/System");
    if (system != nullptr) {
        jmethodID exit =
            jvm_env->GetStaticMethodID(system, "exit", "(I)V");
        if (exit != nullptr) {
            LOGI("cancel: calling System.exit(0) inside the game JVM");
            jvm_env->CallStaticVoidMethod(system, exit, 0);
        }
        jvm_env->DeleteLocalRef(system);
    }
    vm->DetachCurrentThread();
}