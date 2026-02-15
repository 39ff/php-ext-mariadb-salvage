/*
  +----------------------------------------------------------------------+
  | MariaDB Query Profiler - Trace Capture Implementation                |
  +----------------------------------------------------------------------+
  | Uses zend_fetch_debug_backtrace to capture the PHP call stack and    |
  | format it as a JSON array for inclusion in query logs.               |
  | Compatible with PHP 5.3 - 8.4+                                      |
  +----------------------------------------------------------------------+
*/

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include "php.h"
#include "php_mariadb_profiler.h"
#include "profiler_trace.h"

#include <string.h>

/* Forward declaration for the JSON escape function in profiler_log.c */
static char *profiler_trace_escape_json(const char *str, size_t len)
{
    size_t i;
    size_t out_len = 0;
    char *out;
    char *p;

    for (i = 0; i < len; i++) {
        switch (str[i]) {
            case '"':  case '\\':
                out_len += 2; break;
            default:
                if ((unsigned char)str[i] < 0x20) {
                    out_len += 6;
                } else {
                    out_len++;
                }
        }
    }

    out = (char *)emalloc(out_len + 1);
    p = out;

    for (i = 0; i < len; i++) {
        switch (str[i]) {
            case '"':  *p++ = '\\'; *p++ = '"';  break;
            case '\\': *p++ = '\\'; *p++ = '\\'; break;
            default:
                if ((unsigned char)str[i] < 0x20) {
                    p += snprintf(p, 7, "\\u%04x", (unsigned char)str[i]);
                } else {
                    *p++ = str[i];
                }
        }
    }
    *p = '\0';

    return out;
}

/* {{{ profiler_trace_append_frame
 * Append a single trace frame as JSON object to the buffer.
 * Returns the new position in the buffer. */
static int profiler_trace_append_frame(
    char *buf, int pos, int buf_size,
    const char *call, const char *file, long line, int is_first)
{
    char *escaped_call = NULL;
    char *escaped_file = NULL;
    int written;

    if (call) {
        escaped_call = profiler_trace_escape_json(call, strlen(call));
    }
    if (file) {
        escaped_file = profiler_trace_escape_json(file, strlen(file));
    }

    written = snprintf(buf + pos, buf_size - pos,
        "%s{\"call\":\"%s\",\"file\":\"%s\",\"line\":%ld}",
        is_first ? "" : ",",
        escaped_call ? escaped_call : "",
        escaped_file ? escaped_file : "",
        line);

    if (escaped_call) efree(escaped_call);
    if (escaped_file) efree(escaped_file);

    if (written < 0 || pos + written >= buf_size - 1) {
        return pos; /* buffer full, stop */
    }
    return pos + written;
}
/* }}} */

#if PHP_VERSION_ID >= 70000
/* ---- PHP 7.0+ implementation ---- */

/* {{{ profiler_trace_capture_json */
char *profiler_trace_capture_json(void)
{
    zval trace;
    zval *frame;
    int depth;
    char buf[8192];
    int pos = 0;
    int is_first = 1;
    char *result;
    TSRMLS_FETCH();

    depth = (int)PROFILER_G(trace_depth);
    if (depth <= 0) {
        return NULL;
    }

    PROFILER_FETCH_TRACE(&trace, 0, DEBUG_BACKTRACE_IGNORE_ARGS, depth);

    if (Z_TYPE(trace) != IS_ARRAY) {
        zval_ptr_dtor(&trace);
        return NULL;
    }

    pos += snprintf(buf + pos, sizeof(buf) - pos, "[");

    ZEND_HASH_FOREACH_VAL(Z_ARRVAL(trace), frame) {
        zval *zfile, *zline, *zfunc, *zclass, *ztype;
        const char *file_str = "";
        long line_val = 0;
        char call_buf[512];

        if (Z_TYPE_P(frame) != IS_ARRAY) continue;

        zfile  = zend_hash_str_find(Z_ARRVAL_P(frame), "file", sizeof("file") - 1);
        zline  = zend_hash_str_find(Z_ARRVAL_P(frame), "line", sizeof("line") - 1);
        zfunc  = zend_hash_str_find(Z_ARRVAL_P(frame), "function", sizeof("function") - 1);
        zclass = zend_hash_str_find(Z_ARRVAL_P(frame), "class", sizeof("class") - 1);
        ztype  = zend_hash_str_find(Z_ARRVAL_P(frame), "type", sizeof("type") - 1);

        if (zfile && Z_TYPE_P(zfile) == IS_STRING) {
            file_str = Z_STRVAL_P(zfile);
        }
        if (zline && Z_TYPE_P(zline) == IS_LONG) {
            line_val = Z_LVAL_P(zline);
        }

        /* Build "call" string: "Class->method" or "function" */
        if (zclass && Z_TYPE_P(zclass) == IS_STRING
            && ztype && Z_TYPE_P(ztype) == IS_STRING
            && zfunc && Z_TYPE_P(zfunc) == IS_STRING) {
            snprintf(call_buf, sizeof(call_buf), "%s%s%s",
                Z_STRVAL_P(zclass), Z_STRVAL_P(ztype), Z_STRVAL_P(zfunc));
        } else if (zfunc && Z_TYPE_P(zfunc) == IS_STRING) {
            snprintf(call_buf, sizeof(call_buf), "%s", Z_STRVAL_P(zfunc));
        } else {
            snprintf(call_buf, sizeof(call_buf), "(unknown)");
        }

        pos = profiler_trace_append_frame(buf, pos, sizeof(buf),
            call_buf, file_str, line_val, is_first);
        is_first = 0;

        /* Stop if buffer is nearly full */
        if (pos >= (int)sizeof(buf) - 256) break;

    } ZEND_HASH_FOREACH_END();

    pos += snprintf(buf + pos, sizeof(buf) - pos, "]");
    buf[sizeof(buf) - 1] = '\0';

    result = estrndup(buf, pos);
    zval_ptr_dtor(&trace);
    return result;
}
/* }}} */

#else
/* ---- PHP 5.x implementation ---- */

/* {{{ profiler_trace_capture_json (PHP 5.x) */
char *profiler_trace_capture_json(void)
{
    zval trace;
    HashPosition hpos;
    zval **frame;
    int depth;
    char buf[8192];
    int pos = 0;
    int is_first = 1;
    char *result;
    TSRMLS_FETCH();

    depth = (int)PROFILER_G(trace_depth);
    if (depth <= 0) {
        return NULL;
    }

    INIT_ZVAL(trace);
    PROFILER_FETCH_TRACE(&trace, 0, DEBUG_BACKTRACE_IGNORE_ARGS, depth);

    if (Z_TYPE(trace) != IS_ARRAY) {
        zval_dtor(&trace);
        return NULL;
    }

    pos += snprintf(buf + pos, sizeof(buf) - pos, "[");

    for (zend_hash_internal_pointer_reset_ex(Z_ARRVAL(trace), &hpos);
         zend_hash_get_current_data_ex(Z_ARRVAL(trace), (void **)&frame, &hpos) == SUCCESS;
         zend_hash_move_forward_ex(Z_ARRVAL(trace), &hpos))
    {
        zval **zfile = NULL, **zline = NULL, **zfunc = NULL, **zclass = NULL, **ztype = NULL;
        const char *file_str = "";
        long line_val = 0;
        char call_buf[512];

        if (Z_TYPE_PP(frame) != IS_ARRAY) continue;

        zend_hash_find(Z_ARRVAL_PP(frame), "file", sizeof("file"), (void **)&zfile);
        zend_hash_find(Z_ARRVAL_PP(frame), "line", sizeof("line"), (void **)&zline);
        zend_hash_find(Z_ARRVAL_PP(frame), "function", sizeof("function"), (void **)&zfunc);
        zend_hash_find(Z_ARRVAL_PP(frame), "class", sizeof("class"), (void **)&zclass);
        zend_hash_find(Z_ARRVAL_PP(frame), "type", sizeof("type"), (void **)&ztype);

        if (zfile && Z_TYPE_PP(zfile) == IS_STRING) {
            file_str = Z_STRVAL_PP(zfile);
        }
        if (zline && Z_TYPE_PP(zline) == IS_LONG) {
            line_val = Z_LVAL_PP(zline);
        }

        /* Build "call" string */
        if (zclass && Z_TYPE_PP(zclass) == IS_STRING
            && ztype && Z_TYPE_PP(ztype) == IS_STRING
            && zfunc && Z_TYPE_PP(zfunc) == IS_STRING) {
            snprintf(call_buf, sizeof(call_buf), "%s%s%s",
                Z_STRVAL_PP(zclass), Z_STRVAL_PP(ztype), Z_STRVAL_PP(zfunc));
        } else if (zfunc && Z_TYPE_PP(zfunc) == IS_STRING) {
            snprintf(call_buf, sizeof(call_buf), "%s", Z_STRVAL_PP(zfunc));
        } else {
            snprintf(call_buf, sizeof(call_buf), "(unknown)");
        }

        pos = profiler_trace_append_frame(buf, pos, sizeof(buf),
            call_buf, file_str, line_val, is_first);
        is_first = 0;

        if (pos >= (int)sizeof(buf) - 256) break;
    }

    pos += snprintf(buf + pos, sizeof(buf) - pos, "]");
    buf[sizeof(buf) - 1] = '\0';

    result = estrndup(buf, pos);
    zval_dtor(&trace);
    return result;
}
/* }}} */

#endif /* PHP_VERSION_ID >= 70000 */
