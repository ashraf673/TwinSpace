#include <jni.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <stdint.h>
#include <unistd.h>
#include <sys/mman.h>
#include <sys/syscall.h>
#include <sys/stat.h>
#include <sys/types.h>

#define MAX_RULES 16
#define MAX_PATH 1024

static char g_from[MAX_RULES][MAX_PATH];
static char g_to[MAX_RULES][MAX_PATH];
static int g_from_len[MAX_RULES];
static int g_count = 0;
static int g_hooked = 0;

static const char *redirect(const char *path, char *out) {
    int i;
    if (path == NULL || path[0] != '/') return path;
    for (i = 0; i < g_count; i++) {
        int n = g_from_len[i];
        if (n <= 0) continue;
        if (strncmp(path, g_from[i], (size_t) n) != 0) continue;
        if (path[n] != '\0' && path[n] != '/') continue;
        snprintf(out, MAX_PATH, "%s%s", g_to[i], path + n);
        return out;
    }
    return path;
}

#if defined(__aarch64__)
#define NR_OPENAT 56
#define NR_FACCESSAT 48
#define NR_MKDIRAT 34
#define NR_UNLINKAT 35
#define NR_RENAMEAT 38
#define NR_NEWFSTATAT 79

static void patch_fn(void *target, void *hook) {
    uintptr_t page = (uintptr_t) target & ~((uintptr_t) 4095);
    if (mprotect((void *) page, 8192, PROT_READ | PROT_WRITE | PROT_EXEC) != 0) return;
    uint32_t *p = (uint32_t *) target;
    /* LDR X16, #8 ; BR X16 ; .quad hook */
    p[0] = 0x58000050u;
    p[1] = 0xD61F0200u;
    memcpy(p + 2, &hook, sizeof(hook));
    __builtin___clear_cache((char *) target, (char *) target + 16);
}

static int hook_openat(int dirfd, const char *pathname, int flags, mode_t mode) {
    char buf[MAX_PATH];
    const char *p = redirect(pathname, buf);
    return (int) syscall(NR_OPENAT, dirfd, p, flags, mode);
}

static int hook_faccessat(int dirfd, const char *pathname, int mode, int flags) {
    char buf[MAX_PATH];
    const char *p = redirect(pathname, buf);
    return (int) syscall(NR_FACCESSAT, dirfd, p, mode, flags);
}

static int hook_mkdirat(int dirfd, const char *pathname, mode_t mode) {
    char buf[MAX_PATH];
    const char *p = redirect(pathname, buf);
    return (int) syscall(NR_MKDIRAT, dirfd, p, mode);
}

static int hook_unlinkat(int dirfd, const char *pathname, int flags) {
    char buf[MAX_PATH];
    const char *p = redirect(pathname, buf);
    return (int) syscall(NR_UNLINKAT, dirfd, p, flags);
}

static int hook_renameat(int oldfd, const char *oldpath, int newfd, const char *newpath) {
    char a[MAX_PATH], b[MAX_PATH];
    const char *p = redirect(oldpath, a);
    const char *q = redirect(newpath, b);
    return (int) syscall(NR_RENAMEAT, oldfd, p, newfd, q);
}

static int hook_fstatat(int dirfd, const char *pathname, void *statbuf, int flags) {
    char buf[MAX_PATH];
    const char *p = redirect(pathname, buf);
    return (int) syscall(NR_NEWFSTATAT, dirfd, p, statbuf, flags);
}

static void *find_sym(const char *name) {
    void *h = dlopen("libc.so", RTLD_NOW);
    if (h == NULL) h = dlopen("libc.so", RTLD_DEFAULT);
    if (h == NULL) return NULL;
    return dlsym(h, name);
}

static void install_hooks(void) {
    void *p;
    if (g_hooked) return;
    p = find_sym("openat"); if (p) patch_fn(p, (void *) hook_openat);
    p = find_sym("faccessat"); if (p) patch_fn(p, (void *) hook_faccessat);
    p = find_sym("mkdirat"); if (p) patch_fn(p, (void *) hook_mkdirat);
    p = find_sym("unlinkat"); if (p) patch_fn(p, (void *) hook_unlinkat);
    p = find_sym("renameat"); if (p) patch_fn(p, (void *) hook_renameat);
    p = find_sym("fstatat"); if (p) patch_fn(p, (void *) hook_fstatat);
    p = find_sym("newfstatat"); if (p) patch_fn(p, (void *) hook_fstatat);
    g_hooked = 1;
}
#else
static void install_hooks(void) {}
#endif

JNIEXPORT void JNICALL
Java_com_twinspace_app_virtual_IoNative_nativeInstall(JNIEnv *env, jclass cls, jobjectArray fromArr, jobjectArray toArr) {
    int n, i;
    (void) cls;
    n = (*env)->GetArrayLength(env, fromArr);
    if (n > MAX_RULES) n = MAX_RULES;
    g_count = 0;
    for (i = 0; i < n; i++) {
        jstring jf = (jstring) (*env)->GetObjectArrayElement(env, fromArr, i);
        jstring jt = (jstring) (*env)->GetObjectArrayElement(env, toArr, i);
        const char *fs;
        const char *ts;
        if (jf == NULL || jt == NULL) continue;
        fs = (*env)->GetStringUTFChars(env, jf, NULL);
        ts = (*env)->GetStringUTFChars(env, jt, NULL);
        strncpy(g_from[g_count], fs, MAX_PATH - 1);
        strncpy(g_to[g_count], ts, MAX_PATH - 1);
        g_from[g_count][MAX_PATH - 1] = 0;
        g_to[g_count][MAX_PATH - 1] = 0;
        g_from_len[g_count] = (int) strlen(g_from[g_count]);
        (*env)->ReleaseStringUTFChars(env, jf, fs);
        (*env)->ReleaseStringUTFChars(env, jt, ts);
        g_count++;
    }
    install_hooks();
}
