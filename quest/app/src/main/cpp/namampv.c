// JNI binding between app.nama.quest.player.MpvNative and libmpv.
//
// Only non-blocking libmpv calls are exposed: commands and property writes are
// async, values arrive through observed properties. mpv_wait_event runs on the
// Kotlin event thread via pumpEvents. See AGENTS.md: a synchronous call into
// mpv can wait on a core that is itself waiting, and the app freezes.

#include <jni.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <android/log.h>
#include <mpv/client.h>

#define TAG "Nama"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// From libavcodec/jni.h; declared here so FFmpeg's headers need not ship.
int av_jni_set_java_vm(void *vm, void *log_ctx);

#define H(handle) ((mpv_handle *)(intptr_t)(handle))

// ---------------------------------------------------------------------------
// Strings. JNI's "UTF" calls use modified UTF-8, which breaks on emoji and on
// invalid bytes (CheckJNI aborts), and subtitles carry both. So convert
// between real UTF-8 and UTF-16 by hand.

static jstring new_jstring(JNIEnv *env, const char *s)
{
    if (!s)
        return NULL;
    size_t len = strlen(s);
    jchar stackbuf[256];
    jchar *out = len <= 256 ? stackbuf : malloc(len * sizeof(jchar));
    if (!out)
        return NULL;
    size_t n = 0;
    const unsigned char *p = (const unsigned char *)s, *end = p + len;
    while (p < end) {
        uint32_t c = *p;
        int extra = c < 0x80 ? 0 : (c >> 5) == 6 ? 1 : (c >> 4) == 14 ? 2 : (c >> 3) == 30 ? 3 : -1;
        if (extra < 0 || p + extra >= end + (extra ? 0 : 1)) {
            out[n++] = 0xFFFD;
            p++;
            continue;
        }
        if (extra > 0)
            c &= 0x3F >> extra;
        int ok = 1;
        for (int i = 1; i <= extra; i++) {
            if (p + i >= end || (p[i] & 0xC0) != 0x80) {
                ok = 0;
                break;
            }
            c = (c << 6) | (p[i] & 0x3F);
        }
        if (!ok || c > 0x10FFFF || (c >= 0xD800 && c <= 0xDFFF)) {
            out[n++] = 0xFFFD;
            p++;
            continue;
        }
        p += extra + 1;
        if (c >= 0x10000) {
            c -= 0x10000;
            out[n++] = (jchar)(0xD800 | (c >> 10));
            out[n++] = (jchar)(0xDC00 | (c & 0x3FF));
        } else {
            out[n++] = (jchar)c;
        }
    }
    jstring r = (*env)->NewString(env, out, (jsize)n);
    if (out != stackbuf)
        free(out);
    return r;
}

// Caller frees the result with free().
static char *utf8_from_jstring(JNIEnv *env, jstring js)
{
    if (!js)
        return NULL;
    jsize len = (*env)->GetStringLength(env, js);
    const jchar *u = (*env)->GetStringChars(env, js, NULL);
    if (!u)
        return NULL;
    char *out = malloc((size_t)len * 3 + 1);
    if (!out) {
        (*env)->ReleaseStringChars(env, js, u);
        return NULL;
    }
    size_t n = 0;
    for (jsize i = 0; i < len; i++) {
        uint32_t c = u[i];
        if (c >= 0xD800 && c <= 0xDBFF && i + 1 < len && u[i + 1] >= 0xDC00 && u[i + 1] <= 0xDFFF) {
            c = 0x10000 + ((c - 0xD800) << 10) + (u[i + 1] - 0xDC00);
            i++;
        } else if (c >= 0xD800 && c <= 0xDFFF) {
            c = 0xFFFD;
        }
        if (c < 0x80) {
            out[n++] = (char)c;
        } else if (c < 0x800) {
            out[n++] = (char)(0xC0 | (c >> 6));
            out[n++] = (char)(0x80 | (c & 0x3F));
        } else if (c < 0x10000) {
            out[n++] = (char)(0xE0 | (c >> 12));
            out[n++] = (char)(0x80 | ((c >> 6) & 0x3F));
            out[n++] = (char)(0x80 | (c & 0x3F));
        } else {
            out[n++] = (char)(0xF0 | (c >> 18));
            out[n++] = (char)(0x80 | ((c >> 12) & 0x3F));
            out[n++] = (char)(0x80 | ((c >> 6) & 0x3F));
            out[n++] = (char)(0x80 | (c & 0x3F));
        }
    }
    out[n] = 0;
    (*env)->ReleaseStringChars(env, js, u);
    return out;
}

// ---------------------------------------------------------------------------
// mpv_node to JSON, so Kotlin can read track-list and friends with org.json.

struct buf {
    char *s;
    size_t len, cap;
    int oom;
};

static void buf_put(struct buf *b, const char *s, size_t n)
{
    if (b->oom)
        return;
    if (b->len + n + 1 > b->cap) {
        size_t cap = b->cap ? b->cap : 256;
        while (cap < b->len + n + 1)
            cap *= 2;
        char *ns = realloc(b->s, cap);
        if (!ns) {
            b->oom = 1;
            return;
        }
        b->s = ns;
        b->cap = cap;
    }
    memcpy(b->s + b->len, s, n);
    b->len += n;
    b->s[b->len] = 0;
}

static void buf_str(struct buf *b, const char *s)
{
    buf_put(b, s, strlen(s));
}

static void buf_json_string(struct buf *b, const char *s)
{
    buf_put(b, "\"", 1);
    for (const unsigned char *p = (const unsigned char *)s; *p; p++) {
        char esc[8];
        switch (*p) {
        case '"': buf_put(b, "\\\"", 2); break;
        case '\\': buf_put(b, "\\\\", 2); break;
        case '\n': buf_put(b, "\\n", 2); break;
        case '\r': buf_put(b, "\\r", 2); break;
        case '\t': buf_put(b, "\\t", 2); break;
        default:
            if (*p < 0x20) {
                snprintf(esc, sizeof(esc), "\\u%04x", *p);
                buf_str(b, esc);
            } else {
                buf_put(b, (const char *)p, 1);
            }
        }
    }
    buf_put(b, "\"", 1);
}

static void buf_json_node(struct buf *b, const mpv_node *node, int depth)
{
    char num[64];
    if (depth > 32) { // mpv never nests this deep; guards against a cycle bug
        buf_str(b, "null");
        return;
    }
    switch (node->format) {
    case MPV_FORMAT_STRING:
        buf_json_string(b, node->u.string ? node->u.string : "");
        break;
    case MPV_FORMAT_FLAG:
        buf_str(b, node->u.flag ? "true" : "false");
        break;
    case MPV_FORMAT_INT64:
        snprintf(num, sizeof(num), "%lld", (long long)node->u.int64);
        buf_str(b, num);
        break;
    case MPV_FORMAT_DOUBLE:
        if (node->u.double_ != node->u.double_ || node->u.double_ > 1e308 || node->u.double_ < -1e308) {
            buf_str(b, "null"); // NaN and infinity are not JSON
        } else {
            snprintf(num, sizeof(num), "%.17g", node->u.double_);
            buf_str(b, num);
        }
        break;
    case MPV_FORMAT_NODE_ARRAY:
    case MPV_FORMAT_NODE_MAP: {
        int map = node->format == MPV_FORMAT_NODE_MAP;
        mpv_node_list *list = node->u.list;
        buf_str(b, map ? "{" : "[");
        for (int i = 0; list && i < list->num; i++) {
            if (i)
                buf_str(b, ",");
            if (map) {
                buf_json_string(b, list->keys[i]);
                buf_str(b, ":");
            }
            buf_json_node(b, &list->values[i], depth + 1);
        }
        buf_str(b, map ? "}" : "]");
        break;
    }
    default:
        buf_str(b, "null");
    }
}

// ---------------------------------------------------------------------------

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved)
{
    (void)reserved;
    // mpv's Surface and MediaCodec code gets the VM from FFmpeg
    av_jni_set_java_vm(vm, NULL);
    return JNI_VERSION_1_6;
}

#define FN(name) Java_app_nama_quest_player_MpvNative_##name

JNIEXPORT jlong JNICALL FN(create)(JNIEnv *env, jobject thiz)
{
    (void)env; (void)thiz;
    return (jlong)(intptr_t)mpv_create();
}

JNIEXPORT jint JNICALL FN(initialize)(JNIEnv *env, jobject thiz, jlong h)
{
    (void)env; (void)thiz;
    return mpv_initialize(H(h));
}

// Only valid before initialize(): the core does not run yet, so nothing waits.
JNIEXPORT jint JNICALL FN(setOptionString)(JNIEnv *env, jobject thiz, jlong h, jstring jname, jstring jvalue)
{
    (void)thiz;
    char *name = utf8_from_jstring(env, jname), *value = utf8_from_jstring(env, jvalue);
    int r = name && value ? mpv_set_option_string(H(h), name, value) : MPV_ERROR_NOMEM;
    free(name);
    free(value);
    return r;
}

JNIEXPORT jint JNICALL FN(setOptionLong)(JNIEnv *env, jobject thiz, jlong h, jstring jname, jlong value)
{
    (void)thiz;
    char *name = utf8_from_jstring(env, jname);
    int64_t v = value;
    int r = name ? mpv_set_option(H(h), name, MPV_FORMAT_INT64, &v) : MPV_ERROR_NOMEM;
    free(name);
    return r;
}

JNIEXPORT jint JNICALL FN(requestLogMessages)(JNIEnv *env, jobject thiz, jlong h, jstring jlevel)
{
    (void)thiz;
    char *level = utf8_from_jstring(env, jlevel);
    int r = level ? mpv_request_log_messages(H(h), level) : MPV_ERROR_NOMEM;
    free(level);
    return r;
}

JNIEXPORT jint JNICALL FN(commandAsync)(JNIEnv *env, jobject thiz, jlong h, jlong userdata, jobjectArray jargs)
{
    (void)thiz;
    jsize n = (*env)->GetArrayLength(env, jargs);
    const char **args = calloc((size_t)n + 1, sizeof(char *));
    if (!args)
        return MPV_ERROR_NOMEM;
    int r = 0;
    for (jsize i = 0; i < n; i++) {
        jstring js = (jstring)(*env)->GetObjectArrayElement(env, jargs, i);
        args[i] = utf8_from_jstring(env, js);
        (*env)->DeleteLocalRef(env, js);
        if (!args[i])
            r = MPV_ERROR_NOMEM;
    }
    if (r == 0)
        r = mpv_command_async(H(h), (uint64_t)userdata, args);
    for (jsize i = 0; i < n; i++)
        free((void *)args[i]);
    free(args);
    return r;
}

JNIEXPORT jint JNICALL FN(setPropertyStringAsync)(JNIEnv *env, jobject thiz, jlong h, jlong userdata,
                                                   jstring jname, jstring jvalue)
{
    (void)thiz;
    char *name = utf8_from_jstring(env, jname), *value = utf8_from_jstring(env, jvalue);
    int r = name && value
        ? mpv_set_property_async(H(h), (uint64_t)userdata, name, MPV_FORMAT_STRING, &value)
        : MPV_ERROR_NOMEM;
    free(name);
    free(value);
    return r;
}

JNIEXPORT jint JNICALL FN(setPropertyLongAsync)(JNIEnv *env, jobject thiz, jlong h, jlong userdata,
                                                 jstring jname, jlong value)
{
    (void)thiz;
    char *name = utf8_from_jstring(env, jname);
    int64_t v = value;
    int r = name ? mpv_set_property_async(H(h), (uint64_t)userdata, name, MPV_FORMAT_INT64, &v) : MPV_ERROR_NOMEM;
    free(name);
    return r;
}

JNIEXPORT jint JNICALL FN(setPropertyDoubleAsync)(JNIEnv *env, jobject thiz, jlong h, jlong userdata,
                                                   jstring jname, jdouble value)
{
    (void)thiz;
    char *name = utf8_from_jstring(env, jname);
    double v = value;
    int r = name ? mpv_set_property_async(H(h), (uint64_t)userdata, name, MPV_FORMAT_DOUBLE, &v) : MPV_ERROR_NOMEM;
    free(name);
    return r;
}

JNIEXPORT jint JNICALL FN(setPropertyFlagAsync)(JNIEnv *env, jobject thiz, jlong h, jlong userdata,
                                                 jstring jname, jboolean value)
{
    (void)thiz;
    char *name = utf8_from_jstring(env, jname);
    int v = value ? 1 : 0;
    int r = name ? mpv_set_property_async(H(h), (uint64_t)userdata, name, MPV_FORMAT_FLAG, &v) : MPV_ERROR_NOMEM;
    free(name);
    return r;
}

JNIEXPORT jint JNICALL FN(observeProperty)(JNIEnv *env, jobject thiz, jlong h, jlong userdata,
                                            jstring jname, jint format)
{
    (void)thiz;
    char *name = utf8_from_jstring(env, jname);
    int r = name ? mpv_observe_property(H(h), (uint64_t)userdata, name, (mpv_format)format) : MPV_ERROR_NOMEM;
    free(name);
    return r;
}

JNIEXPORT jint JNICALL FN(unobserveProperty)(JNIEnv *env, jobject thiz, jlong h, jlong userdata)
{
    (void)env; (void)thiz;
    return mpv_unobserve_property(H(h), (uint64_t)userdata);
}

JNIEXPORT jstring JNICALL FN(errorString)(JNIEnv *env, jobject thiz, jint error)
{
    (void)thiz;
    return new_jstring(env, mpv_error_string(error));
}

// The Surface stays alive through a global reference; mpv receives the
// reference's value as "wid" and turns it into an ANativeWindow itself.
JNIEXPORT jlong JNICALL FN(newSurfaceRef)(JNIEnv *env, jobject thiz, jobject surface)
{
    (void)thiz;
    return (jlong)(intptr_t)(*env)->NewGlobalRef(env, surface);
}

JNIEXPORT void JNICALL FN(deleteSurfaceRef)(JNIEnv *env, jobject thiz, jlong ref)
{
    (void)thiz;
    if (ref)
        (*env)->DeleteGlobalRef(env, (jobject)(intptr_t)ref);
}

JNIEXPORT void JNICALL FN(wakeup)(JNIEnv *env, jobject thiz, jlong h)
{
    (void)env; (void)thiz;
    mpv_wakeup(H(h));
}

// Must not run while another thread is inside pumpEvents on the same handle.
JNIEXPORT void JNICALL FN(terminateDestroy)(JNIEnv *env, jobject thiz, jlong h)
{
    (void)env; (void)thiz;
    mpv_terminate_destroy(H(h));
}

// ---------------------------------------------------------------------------
// Event pump. Blocks in mpv_wait_event and hands each event to the sink until
// a wakeup (returns 0) or shutdown (returns 1). Runs on the Kotlin event
// thread only.

struct sink {
    jmethodID on_event, on_property, on_log;
};

static int check_exception(JNIEnv *env)
{
    if ((*env)->ExceptionCheck(env)) {
        LOGE("mpv event listener threw");
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
        return 1;
    }
    return 0;
}

static void deliver_property(JNIEnv *env, jobject obj, struct sink *s, uint64_t userdata, mpv_event_property *p)
{
    jlong l = 0;
    jdouble d = 0;
    jstring str = NULL;
    int format = p->format;
    switch (p->format) {
    case MPV_FORMAT_FLAG:
        l = *(int *)p->data;
        break;
    case MPV_FORMAT_INT64:
        l = *(int64_t *)p->data;
        break;
    case MPV_FORMAT_DOUBLE:
        d = *(double *)p->data;
        break;
    case MPV_FORMAT_STRING:
    case MPV_FORMAT_OSD_STRING:
        str = new_jstring(env, *(char **)p->data);
        break;
    case MPV_FORMAT_NODE: {
        struct buf b = {0};
        buf_json_node(&b, (mpv_node *)p->data, 0);
        if (!b.oom && b.s)
            str = new_jstring(env, b.s);
        free(b.s);
        break;
    }
    default: // MPV_FORMAT_NONE: the property is unavailable right now
        format = MPV_FORMAT_NONE;
    }
    jstring name = new_jstring(env, p->name);
    (*env)->CallVoidMethod(env, obj, s->on_property, name, (jlong)userdata, (jint)format, l, d, str);
    check_exception(env);
    (*env)->DeleteLocalRef(env, name);
    if (str)
        (*env)->DeleteLocalRef(env, str);
}

JNIEXPORT jint JNICALL FN(pumpEvents)(JNIEnv *env, jobject thiz, jlong h, jobject obj)
{
    (void)thiz;
    jclass cls = (*env)->GetObjectClass(env, obj);
    struct sink s = {
        .on_event = (*env)->GetMethodID(env, cls, "onNativeEvent", "(IIJJJ)V"),
        .on_property = (*env)->GetMethodID(env, cls, "onNativeProperty", "(Ljava/lang/String;JIJDLjava/lang/String;)V"),
        .on_log = (*env)->GetMethodID(env, cls, "onNativeLog", "(Ljava/lang/String;ILjava/lang/String;)V"),
    };
    (*env)->DeleteLocalRef(env, cls);
    if (!s.on_event || !s.on_property || !s.on_log) {
        check_exception(env);
        LOGE("mpv event sink is missing a callback");
        return -1;
    }

    for (;;) {
        mpv_event *ev = mpv_wait_event(H(h), -1);
        switch (ev->event_id) {
        case MPV_EVENT_NONE:
            return 0;
        case MPV_EVENT_PROPERTY_CHANGE:
            deliver_property(env, obj, &s, ev->reply_userdata, ev->data);
            break;
        case MPV_EVENT_LOG_MESSAGE: {
            mpv_event_log_message *m = ev->data;
            jstring prefix = new_jstring(env, m->prefix), text = new_jstring(env, m->text);
            (*env)->CallVoidMethod(env, obj, s.on_log, prefix, (jint)m->log_level, text);
            check_exception(env);
            (*env)->DeleteLocalRef(env, prefix);
            (*env)->DeleteLocalRef(env, text);
            break;
        }
        default: {
            jlong a = 0, b = 0;
            if (ev->event_id == MPV_EVENT_END_FILE) {
                mpv_event_end_file *e = ev->data;
                a = e->reason;
                b = e->playlist_entry_id;
                (*env)->CallVoidMethod(env, obj, s.on_event, (jint)ev->event_id, (jint)e->error,
                                       (jlong)ev->reply_userdata, a, b);
            } else {
                if (ev->event_id == MPV_EVENT_START_FILE)
                    a = ((mpv_event_start_file *)ev->data)->playlist_entry_id;
                else if (ev->event_id == MPV_EVENT_FILE_LOADED)
                    a = 0;
                (*env)->CallVoidMethod(env, obj, s.on_event, (jint)ev->event_id, (jint)ev->error,
                                       (jlong)ev->reply_userdata, a, b);
            }
            check_exception(env);
            if (ev->event_id == MPV_EVENT_SHUTDOWN)
                return 1;
        }
        }
    }
}
