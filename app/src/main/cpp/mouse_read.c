#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <dirent.h>
#include <pthread.h>
#include <linux/input.h>
#include <sys/epoll.h>
#include <errno.h>

#define TAG "ApexMapper-Native"
#define MAX_DEVICES 32

typedef struct {
    JavaVM *javaVM;
    jobject serviceObj;
    jmethodID onMouseEvent;
    jmethodID onKeyEvent;
    int done;
    int mouse_fds[MAX_DEVICES];
    int mouse_count;
} NativeContext;

static NativeContext g_ctx;

// Find mouse/keyboard input devices
static int find_input_devices(int *fds, int max) {
    int count = 0;
    DIR *dir = opendir("/dev/input");
    if (!dir) return 0;

    struct dirent *entry;
    while ((entry = readdir(dir)) != NULL && count < max) {
        if (strncmp(entry->d_name, "event", 5) != 0) continue;

        char path[256];
        snprintf(path, sizeof(path), "/dev/input/%s", entry->d_name);

        int fd = open(path, O_RDONLY | O_NONBLOCK);
        if (fd < 0) continue;

        // Check if device has mouse/keyboard capabilities
        unsigned long ev_bits = 0;
        if (ioctl(fd, EVIOCGBIT(0, sizeof(ev_bits)), &ev_bits) >= 0) {
            // Check for EV_REL (mouse) or EV_KEY (keyboard)
            if (ev_bits & ((1 << EV_REL) | (1 << EV_KEY))) {
                fds[count++] = fd;
            } else {
                close(fd);
            }
        } else {
            close(fd);
        }
    }
    closedir(dir);
    return count;
}

static void *mouse_reader_thread(void *arg) {
    NativeContext *ctx = (NativeContext *)arg;
    JNIEnv *env;

    jint res = (*ctx->javaVM)->GetEnv(ctx->javaVM, (void **)&env, JNI_VERSION_1_6);
    if (res != JNI_OK) {
        res = (*ctx->javaVM)->AttachCurrentThread(ctx->javaVM, &env, NULL);
        if (res != JNI_OK) return NULL;
    }

    int epoll_fd = epoll_create1(0);
    if (epoll_fd < 0) return NULL;

    // Register all mouse devices
    for (int i = 0; i < ctx->mouse_count; i++) {
        struct epoll_event ev;
        ev.events = EPOLLIN;
        ev.data.fd = ctx->mouse_fds[i];
        epoll_ctl(epoll_fd, EPOLL_CTL_ADD, ctx->mouse_fds[i], &ev);
    }

    int rel_x = 0, rel_y = 0, buttons = 0, wheel = 0;
    struct epoll_event events[MAX_DEVICES];

    while (!ctx->done) {
        int nfds = epoll_wait(epoll_fd, events, MAX_DEVICES, 100);
        if (nfds < 0) {
            if (errno == EINTR) continue;
            break;
        }

        for (int i = 0; i < nfds; i++) {
            struct input_event ev;
            int fd = events[i].data.fd;

            while (read(fd, &ev, sizeof(ev)) == sizeof(ev)) {
                if (ev.type == EV_REL) {
                    if (ev.code == REL_X) rel_x += ev.value;
                    else if (ev.code == REL_Y) rel_y += ev.value;
                    else if (ev.code == REL_WHEEL) wheel += ev.value;
                } else if (ev.type == EV_KEY) {
                    if (ev.code == BTN_LEFT) {
                        if (ev.value) buttons |= 1; else buttons &= ~1;
                    } else if (ev.code == BTN_RIGHT) {
                        if (ev.value) buttons |= 2; else buttons &= ~2;
                    } else if (ev.code == BTN_MIDDLE) {
                        if (ev.value) buttons |= 4; else buttons &= ~4;
                    } else if (ev.code < 256) {
                        // Keyboard key
                        (*env)->CallVoidMethod(env, ctx->serviceObj,
                            ctx->onKeyEvent, (jint)ev.code, (jboolean)(ev.value > 0));
                    }
                } else if (ev.type == EV_SYN && ev.code == SYN_REPORT) {
                    // Send accumulated mouse event
                    if (rel_x != 0 || rel_y != 0 || wheel != 0 || buttons != 0) {
                        (*env)->CallVoidMethod(env, ctx->serviceObj,
                            ctx->onMouseEvent,
                            (jint)rel_x, (jint)rel_y, (jint)buttons, (jint)wheel);
                        rel_x = 0;
                        rel_y = 0;
                        wheel = 0;
                    }
                }
            }
        }
    }

    close(epoll_fd);
    (*ctx->javaVM)->DetachCurrentThread(ctx->javaVM);
    return NULL;
}

JNIEXPORT void JNICALL
Java_com_example_server_RemoteService_nativeStartMouseReader(JNIEnv *env, jobject thiz) {
    memset(&g_ctx, 0, sizeof(g_ctx));
    (*env)->GetJavaVM(env, &g_ctx.javaVM);
    g_ctx.serviceObj = (*env)->NewGlobalRef(env, thiz);

    jclass clz = (*env)->GetObjectClass(env, thiz);
    g_ctx.onMouseEvent = (*env)->GetMethodID(env, clz, "onNewMouseRelEvent", "(IIII)V");
    g_ctx.onKeyEvent = (*env)->GetMethodID(env, clz, "onKeyEvent", "(IZ)V");

    g_ctx.mouse_count = find_input_devices(g_ctx.mouse_fds, MAX_DEVICES);

    if (g_ctx.mouse_count > 0) {
        pthread_t thread;
        pthread_create(&thread, NULL, mouse_reader_thread, &g_ctx);
        pthread_detach(thread);
    }
}

JNIEXPORT jstring JNICALL
Java_com_example_bridge_NativeInputBridge_nativeCapabilitiesSummary(JNIEnv *env, jclass clazz) {
    (void)clazz;
    return (*env)->NewStringUTF(env, "JNI mouse/keyboard reader · epoll loop · EV_KEY/EV_REL scan · Shizuku handoff");
}

JNIEXPORT jint JNICALL
Java_com_example_bridge_NativeInputBridge_nativeMaxInputDevices(JNIEnv *env, jclass clazz) {
    (void)env;
    (void)clazz;
    return MAX_DEVICES;
}
