#include <jni.h>
#include <pty.h>
#include <unistd.h>
#include <stdlib.h>
#include <fcntl.h>
#include <sys/wait.h>
#include <sys/ioctl.h>
#include <signal.h>
#include <string.h>
#include <errno.h>
#include <vector>
#include <string>

extern "C" JNIEXPORT jintArray JNICALL
Java_com_termdroid64_app_NativePty_createSubprocess(
        JNIEnv* env,
        jclass /* clazz */,
        jstring cmd,
        jstring cwd,
        jobjectArray envArray,
        jint rows,
        jint cols) {

    const char* cmdStr = env->GetStringUTFChars(cmd, nullptr);
    const char* cwdStr = env->GetStringUTFChars(cwd, nullptr);

    std::vector<std::string> envStrings;
    std::vector<char*> envPtrs;
    if (envArray != nullptr) {
        jsize envCount = env->GetArrayLength(envArray);
        for (jsize i = 0; i < envCount; ++i) {
            auto jStr = (jstring) env->GetObjectArrayElement(envArray, i);
            const char* cStr = env->GetStringUTFChars(jStr, nullptr);
            envStrings.emplace_back(cStr);
            env->ReleaseStringUTFChars(jStr, cStr);
            env->DeleteLocalRef(jStr);
        }
    }
    for (auto& s : envStrings) {
        envPtrs.push_back(const_cast<char*>(s.c_str()));
    }
    envPtrs.push_back(nullptr);

    struct winsize ws {};
    ws.ws_row = static_cast<unsigned short>(rows > 0 ? rows : 24);
    ws.ws_col = static_cast<unsigned short>(cols > 0 ? cols : 80);

    int masterFd = -1;
    int slaveFd = -1;
    if (openpty(&masterFd, &slaveFd, nullptr, nullptr, &ws) != 0) {
        env->ReleaseStringUTFChars(cmd, cmdStr);
        env->ReleaseStringUTFChars(cwd, cwdStr);
        return nullptr;
    }

    pid_t pid = fork();
    if (pid < 0) {
        close(masterFd);
        close(slaveFd);
        env->ReleaseStringUTFChars(cmd, cmdStr);
        env->ReleaseStringUTFChars(cwd, cwdStr);
        return nullptr;
    }

    if (pid == 0) {
        close(masterFd);
        setsid();
        ioctl(slaveFd, TIOCSCTTY, 0);

        dup2(slaveFd, STDIN_FILENO);
        dup2(slaveFd, STDOUT_FILENO);
        dup2(slaveFd, STDERR_FILENO);
        if (slaveFd > STDERR_FILENO) {
            close(slaveFd);
        }

        if (cwdStr != nullptr && strlen(cwdStr) > 0) {
            chdir(cwdStr);
        }

        char* argv[] = { const_cast<char*>(cmdStr), nullptr };
        execve(cmdStr, argv, envPtrs.data());
        _exit(127);
    }

    close(slaveFd);
    env->ReleaseStringUTFChars(cmd, cmdStr);
    env->ReleaseStringUTFChars(cwd, cwdStr);

    jintArray result = env->NewIntArray(2);
    jint values[2] = { static_cast<jint>(pid), static_cast<jint>(masterFd) };
    env->SetIntArrayRegion(result, 0, 2, values);
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_termdroid64_app_NativePty_setPtyWindowSize(
        JNIEnv* /* env */,
        jclass /* clazz */,
        jint fd,
        jint rows,
        jint cols) {
    struct winsize ws {};
    ws.ws_row = static_cast<unsigned short>(rows);
    ws.ws_col = static_cast<unsigned short>(cols);
    ioctl(fd, TIOCSWINSZ, &ws);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_termdroid64_app_NativePty_waitFor(
        JNIEnv* /* env */,
        jclass /* clazz */,
        jint pid) {
    int status = 0;
    if (waitpid(pid, &status, 0) < 0) {
        return -1;
    }
    if (WIFEXITED(status)) {
        return WEXITSTATUS(status);
    }
    return -1;
}

extern "C" JNIEXPORT void JNICALL
Java_com_termdroid64_app_NativePty_killProcess(
        JNIEnv* /* env */,
        jclass /* clazz */,
        jint pid) {
    if (pid > 0) {
        kill(pid, SIGKILL);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_termdroid64_app_NativePty_closeFd(
        JNIEnv* /* env */,
        jclass /* clazz */,
        jint fd) {
    if (fd >= 0) {
        close(fd);
    }
}
