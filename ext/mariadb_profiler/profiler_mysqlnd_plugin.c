/*
  +----------------------------------------------------------------------+
  | MariaDB Query Profiler - mysqlnd plugin                              |
  +----------------------------------------------------------------------+
  | Hooks into mysqlnd to intercept all queries                          |
  | Compatible with PHP 5.3 - 8.4+                                      |
  +----------------------------------------------------------------------+
*/

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include "php.h"
#include "php_mariadb_profiler.h"
#include "profiler_log.h"
#include "profiler_xdebug.h"

#ifndef PHP_WIN32
# include <sys/time.h>
#endif

/* {{{ profiler_get_microtime
 * Get current time in seconds with microsecond precision. */
static double profiler_get_microtime(void)
{
    struct timeval tv;
    gettimeofday(&tv, NULL);
    return (double)tv.tv_sec + (double)tv.tv_usec / 1000000.0;
}
/* }}} */

/* {{{ profiler_check_slow_query
 * If xdebug is active and duration exceeds the threshold, trigger a breakpoint. */
static void profiler_check_slow_query(double duration_ms)
{
    double threshold;
    TSRMLS_FETCH();

    threshold = PROFILER_G(xdebug_break_threshold);
    if (threshold > 0 && duration_ms >= threshold * 1000.0) {
        if (profiler_xdebug_is_debugger_active()) {
            profiler_xdebug_break();
        }
    }
}
/* }}} */

/*
 * mysqlnd internal API changed across PHP versions:
 *
 * PHP 5.3:
 *   - Connection type: MYSQLND *
 *   - Methods: struct st_mysqlnd_conn_methods (no data/outer split)
 *   - mysqlnd_conn_get_methods() returns st_mysqlnd_conn_methods *
 *   - query_len: unsigned int
 *   - All signatures include TSRMLS_DC
 *   - send_query: simple (conn, query, query_len TSRMLS_DC)
 *
 * PHP 5.4-5.6:
 *   - Connection split into MYSQLND (outer) + MYSQLND_CONN_DATA (inner)
 *   - Methods: struct st_mysqlnd_conn_data_methods
 *   - mysqlnd_conn_data_get_methods() returns st_mysqlnd_conn_data_methods *
 *   - query_len: unsigned int
 *   - All signatures include TSRMLS_DC
 *   - send_query: simple (conn, query, query_len TSRMLS_DC)
 *
 * PHP 7.0-7.4:
 *   - Same types as 5.5 but TSRMLS removed from all signatures
 *   - query_len: const size_t
 *   - send_query has enum_mysqlnd_send_query_type + zval callbacks
 *
 * PHP 8.0:
 *   - mysqlnd_conn_data_get_methods() replaces mysqlnd_conn_get_methods()
 *
 * PHP 8.1+:
 *   - enum_mysqlnd_send_query_type removed from send_query
 */

static unsigned int profiler_plugin_id;

/* Original method pointers we save for chaining */
static PROFILER_CONN_METHODS_T *orig_conn_data_methods = NULL;
static struct st_mysqlnd_stmt_methods *orig_stmt_methods = NULL;

/* {{{ profiler_query_hook
 * Called for every mysqlnd_conn_data::query() call.
 * Signature adapts via PROFILER_CONN_T, PROFILER_QUERY_LEN_T, and TSRMLS_DC. */
static enum_func_status
MYSQLND_METHOD(profiler_conn, query)(
    PROFILER_CONN_T *conn,
    const char *query,
    PROFILER_QUERY_LEN_T query_len TSRMLS_DC)
{
    enum_func_status result;
    double t0, duration_ms;

    t0 = profiler_get_microtime();

    /* Call the original method first */
    result = orig_conn_data_methods->query(conn, query, query_len TSRMLS_CC);

    duration_ms = (profiler_get_microtime() - t0) * 1000.0;

    /* Log the query with execution status and duration */
    if (PROFILER_G(enabled) && profiler_job_is_any_active()) {
        profiler_log_query(query, query_len,
                           result == PASS ? "ok" : "err", duration_ms);
    }

    /* Check slow query threshold for xdebug break */
    profiler_check_slow_query(duration_ms);

    return result;
}
/* }}} */

/* {{{ profiler_send_query_hook
 * send_query() signature changed between PHP versions:
 *   PHP 5.3-5.6: (conn, query, query_len TSRMLS_DC)
 *   PHP 7.0-8.0: (conn, query, query_len, type, read_cb, err_cb)
 *   PHP 8.1+:    (conn, query, query_len, read_cb, err_cb)
 */
#if PHP_VERSION_ID >= 80100
/* PHP 8.1+: enum_mysqlnd_send_query_type removed */
static enum_func_status
MYSQLND_METHOD(profiler_conn, send_query)(
    MYSQLND_CONN_DATA *conn,
    const char *query,
    const size_t query_len,
    zval *read_cb,
    zval *err_cb)
{
    enum_func_status result;
    double t0, duration_ms;
    t0 = profiler_get_microtime();
    result = orig_conn_data_methods->send_query(conn, query, query_len, read_cb, err_cb);
    duration_ms = (profiler_get_microtime() - t0) * 1000.0;
    if (PROFILER_G(enabled) && profiler_job_is_any_active()) {
        profiler_log_query(query, query_len,
                           result == PASS ? "ok" : "err", duration_ms);
    }
    profiler_check_slow_query(duration_ms);
    return result;
}
#elif PHP_VERSION_ID >= 70000
/* PHP 7.0-8.0: no TSRMLS, size_t, with type param */
static enum_func_status
MYSQLND_METHOD(profiler_conn, send_query)(
    MYSQLND_CONN_DATA *conn,
    const char *query,
    const size_t query_len,
    enum_mysqlnd_send_query_type type,
    zval *read_cb,
    zval *err_cb)
{
    enum_func_status result;
    double t0, duration_ms;
    t0 = profiler_get_microtime();
    result = orig_conn_data_methods->send_query(conn, query, query_len, type, read_cb, err_cb);
    duration_ms = (profiler_get_microtime() - t0) * 1000.0;
    if (PROFILER_G(enabled) && profiler_job_is_any_active()) {
        profiler_log_query(query, query_len,
                           result == PASS ? "ok" : "err", duration_ms);
    }
    profiler_check_slow_query(duration_ms);
    return result;
}
#else
/* PHP 5.3-5.6: simple signature (conn, query, query_len TSRMLS_DC) */
static enum_func_status
MYSQLND_METHOD(profiler_conn, send_query)(
    PROFILER_CONN_T *conn,
    const char *query,
    unsigned int query_len TSRMLS_DC)
{
    enum_func_status result;
    double t0, duration_ms;
    t0 = profiler_get_microtime();
    result = orig_conn_data_methods->send_query(conn, query, query_len TSRMLS_CC);
    duration_ms = (profiler_get_microtime() - t0) * 1000.0;
    if (PROFILER_G(enabled) && profiler_job_is_any_active()) {
        profiler_log_query(query, query_len,
                           result == PASS ? "ok" : "err", duration_ms);
    }
    profiler_check_slow_query(duration_ms);
    return result;
}
#endif
/* }}} */

/* {{{ profiler_stmt_prepare_hook
 * Intercepts prepared statements at prepare time.
 * PHP 7.0+: stores query template for later use at execute() time.
 * PHP 5.x:  logs template immediately (no param capture support). */
static enum_func_status
MYSQLND_METHOD(profiler_stmt, prepare)(
    MYSQLND_STMT * const stmt,
    const char *query,
    PROFILER_QUERY_LEN_T query_len TSRMLS_DC)
{
    enum_func_status result;
    double t0, duration_ms;

    t0 = profiler_get_microtime();
    result = orig_stmt_methods->prepare(stmt, query, query_len TSRMLS_CC);
    duration_ms = (profiler_get_microtime() - t0) * 1000.0;

#if PHP_VERSION_ID >= 70000
    if (PROFILER_G(enabled)) {
        if (result == PASS && PROFILER_G(stmt_queries)) {
            /* Store query template on success - will be logged at execute() with bound params */
            zval zv;
            ZVAL_STRINGL(&zv, query, query_len);
            zend_hash_index_update(
                PROFILER_G(stmt_queries),
                (zend_ulong)(uintptr_t)stmt,
                &zv
            );
        } else if (result != PASS && profiler_job_is_any_active()) {
            /* Failed prepare has no subsequent execute(); log immediately with err status */
            profiler_log_query(query, query_len, "err", duration_ms);
            profiler_check_slow_query(duration_ms);
        }
    }
#else
    /* PHP 5.x: log template at prepare time (no param support) */
    if (PROFILER_G(enabled) && profiler_job_is_any_active()) {
        profiler_log_query(query, query_len,
                           result == PASS ? "ok" : "err", duration_ms);
    }
    profiler_check_slow_query(duration_ms);
#endif

    return result;
}
/* }}} */

#if PHP_VERSION_ID >= 70000

/*
 * MYSQLND_VERSION_ID equals PHP_VERSION_ID.  The internal layout of
 * st_mysqlnd_stmt_data (fields param_bind, param_count) has been stable
 * from PHP 7.0 through 8.4.  Guard direct access so that an unknown
 * future mysqlnd silently degrades to "no params" instead of crashing.
 *
 * NOTE: The upper bound (80500) is a defensive estimate – it should be
 * updated once PHP 8.5 is released and its mysqlnd ABI has been verified.
 * If the ABI remains unchanged, simply bump the upper bound.
 */
#define PROFILER_MYSQLND_PARAM_ACCESS_SAFE \
    (MYSQLND_VERSION_ID >= 70000 && MYSQLND_VERSION_ID < 80500)

/* {{{ profiler_build_params_json_write_string
 * Helper: write a JSON-escaped string value into the buffer.
 * Grows the buffer as needed via *buf_ptr / *buf_size_ptr.
 * Returns updated write position. */
static size_t profiler_build_params_json_write_string(
    char **buf_ptr, size_t *buf_size_ptr, size_t pos,
    const char *str, size_t str_len)
{
    char *buf = *buf_ptr;
    size_t buf_size = *buf_size_ptr;
    char *escaped = profiler_log_escape_json_string(str, str_len);
    size_t esc_len = strlen(escaped);

    if (pos + esc_len + 4 > buf_size) {
        buf_size = pos + esc_len + 256;
        buf = (char *)erealloc(buf, buf_size);
        *buf_ptr = buf;
        *buf_size_ptr = buf_size;
    }

    buf[pos++] = '"';
    memcpy(buf + pos, escaped, esc_len);
    pos += esc_len;
    buf[pos++] = '"';
    efree(escaped);
    return pos;
}

/* {{{ profiler_build_params_json
 * Build JSON array string from stmt's bound parameter values.
 * Formats each value according to the declared bind type (MYSQL_TYPE_*)
 * rather than the zval's runtime type, matching the coercion mysqlnd
 * performs before sending data to the server.
 * Returns emalloc'd string or NULL if no params. Caller must efree. */
static char *profiler_build_params_json(MYSQLND_STMT * const stmt)
{
#if PROFILER_MYSQLND_PARAM_ACCESS_SAFE
    MYSQLND_STMT_DATA *data = stmt->data;
    unsigned int i;
    size_t buf_size, pos;
    char *buf;

    if (!data || !data->param_bind || data->param_count == 0) {
        return NULL;
    }

    buf_size = 256;
    buf = (char *)emalloc(buf_size);
    pos = 0;

    buf[pos++] = '[';

    for (i = 0; i < data->param_count; i++) {
        zval *zv = &data->param_bind[i].zv;
        zend_uchar bind_type = data->param_bind[i].type;

        if (i > 0) {
            buf[pos++] = ',';
        }

        /* Ensure space for this param (grow if needed) */
        if (pos + 128 > buf_size) {
            buf_size *= 2;
            buf = (char *)erealloc(buf, buf_size);
        }

        /* Dereference if reference (bind_param uses references) */
        ZVAL_DEREF(zv);

        /* NULL zval is always serialized as JSON null regardless of bind type */
        if (Z_TYPE_P(zv) == IS_NULL) {
            memcpy(buf + pos, "null", 4);
            pos += 4;
            continue;
        }

        /*
         * Format according to the declared bind type rather than zval type,
         * matching the coercion mysqlnd performs before sending to the server.
         *   'i' -> MYSQL_TYPE_LONG      'd' -> MYSQL_TYPE_DOUBLE
         *   's' -> MYSQL_TYPE_VAR_STRING 'b' -> MYSQL_TYPE_LONG_BLOB
         */
        switch (bind_type) {
            case MYSQL_TYPE_LONG:
            case MYSQL_TYPE_LONGLONG: {
                zend_long val = (Z_TYPE_P(zv) == IS_LONG)
                    ? Z_LVAL_P(zv) : zval_get_long(zv);
                int written = snprintf(buf + pos, buf_size - pos,
                    "\"%ld\"", (long)val);
                if (written < 0) {
                    break;
                }
                if ((size_t)written >= buf_size - pos) {
                    buf_size = pos + (size_t)written + 64;
                    buf = (char *)erealloc(buf, buf_size);
                    pos += snprintf(buf + pos, buf_size - pos,
                        "\"%ld\"", (long)val);
                } else {
                    pos += (size_t)written;
                }
                break;
            }

            case MYSQL_TYPE_DOUBLE:
            case MYSQL_TYPE_FLOAT: {
                double val = (Z_TYPE_P(zv) == IS_DOUBLE)
                    ? Z_DVAL_P(zv) : zval_get_double(zv);
                int written = snprintf(buf + pos, buf_size - pos,
                    "\"%g\"", val);
                if (written < 0) {
                    break;
                }
                if ((size_t)written >= buf_size - pos) {
                    buf_size = pos + (size_t)written + 64;
                    buf = (char *)erealloc(buf, buf_size);
                    pos += snprintf(buf + pos, buf_size - pos,
                        "\"%g\"", val);
                } else {
                    pos += (size_t)written;
                }
                break;
            }

            case MYSQL_TYPE_LONG_BLOB:
                /* Blob data sent via send_long_data – log placeholder */
                memcpy(buf + pos, "\"[BLOB]\"", 8);
                pos += 8;
                break;

            default: {
                /* String types (MYSQL_TYPE_VAR_STRING, etc.) and any
                 * unrecognised bind type: coerce to string */
                if (Z_TYPE_P(zv) == IS_STRING) {
                    pos = profiler_build_params_json_write_string(
                        &buf, &buf_size, pos,
                        Z_STRVAL_P(zv), Z_STRLEN_P(zv));
                } else {
                    zend_string *str = zval_get_string(zv);
                    pos = profiler_build_params_json_write_string(
                        &buf, &buf_size, pos,
                        ZSTR_VAL(str), ZSTR_LEN(str));
                    zend_string_release(str);
                }
                break;
            }
        }
    }

    buf[pos++] = ']';
    buf[pos] = '\0';

    return buf;
#else
    /* Unknown mysqlnd version – skip param capture to avoid ABI issues */
    (void)stmt;
    return NULL;
#endif /* PROFILER_MYSQLND_PARAM_ACCESS_SAFE */
}
/* }}} */

/* {{{ profiler_stmt_execute_hook
 * Intercepts prepared statement execution to log query with bound params.
 * Logging is performed after execute so the result status can be recorded.
 * param_bind remains valid after execute (freed only on stmt dtor / rebind). */
static enum_func_status
MYSQLND_METHOD(profiler_stmt, execute)(
    MYSQLND_STMT * const stmt)
{
    enum_func_status result;
    double t0, duration_ms;

    t0 = profiler_get_microtime();

    /* Call the original method first */
    result = orig_stmt_methods->execute(stmt);

    duration_ms = (profiler_get_microtime() - t0) * 1000.0;

    /* Log with status, params, and duration after execution */
    if (PROFILER_G(enabled) && profiler_job_is_any_active() && PROFILER_G(stmt_queries)) {
        zval *entry = zend_hash_index_find(
            PROFILER_G(stmt_queries),
            (zend_ulong)(uintptr_t)stmt
        );
        if (entry && Z_TYPE_P(entry) == IS_STRING) {
            char *params_json = profiler_build_params_json(stmt);
            profiler_log_query_with_params(
                Z_STRVAL_P(entry), Z_STRLEN_P(entry), params_json,
                result == PASS ? "ok" : "err", duration_ms
            );
            if (params_json) {
                efree(params_json);
            }
        }
    }

    /* Check slow query threshold for xdebug break */
    profiler_check_slow_query(duration_ms);

    return result;
}
/* }}} */

/* {{{ profiler_stmt_dtor_hook
 * Clean up stored query template when statement is destroyed. */
static enum_func_status
MYSQLND_METHOD(profiler_stmt, dtor)(
    MYSQLND_STMT * const stmt,
    PROFILER_BOOL_T implicit)
{
    if (PROFILER_G(stmt_queries)) {
        zend_hash_index_del(
            PROFILER_G(stmt_queries),
            (zend_ulong)(uintptr_t)stmt
        );
    }

    return orig_stmt_methods->dtor(stmt, implicit);
}
/* }}} */

#endif /* PHP_VERSION_ID >= 70000 */

/* {{{ mariadb_profiler_mysqlnd_plugin_register */
void mariadb_profiler_mysqlnd_plugin_register(void)
{
    PROFILER_CONN_METHODS_T *conn_data_methods;
    struct st_mysqlnd_stmt_methods *stmt_methods;

    /* Register as a mysqlnd plugin */
    profiler_plugin_id = mysqlnd_plugin_register();

    /*
     * Get connection DATA methods (where query/send_query live).
     * PHP 5.3:     mysqlnd_conn_get_methods() returns st_mysqlnd_conn_methods *
     * PHP 5.4-5.6: mysqlnd_conn_data_get_methods() returns st_mysqlnd_conn_data_methods *
     * PHP 7.0-7.4: mysqlnd_conn_get_methods() returns st_mysqlnd_conn_data_methods *
     * PHP 8.0+:    mysqlnd_conn_data_get_methods() is the accessor
     */
    conn_data_methods = profiler_conn_data_get_methods();

    /* Save originals for chaining */
    if (!orig_conn_data_methods) {
        orig_conn_data_methods = (PROFILER_CONN_METHODS_T *)
            pemalloc(sizeof(PROFILER_CONN_METHODS_T), 1);
        memcpy(orig_conn_data_methods, conn_data_methods,
            sizeof(PROFILER_CONN_METHODS_T));
    }

    /* Install our hooks */
    conn_data_methods->query      = MYSQLND_METHOD(profiler_conn, query);
    conn_data_methods->send_query = MYSQLND_METHOD(profiler_conn, send_query);

    /* Hook statement methods */
    stmt_methods = mysqlnd_stmt_get_methods();

    if (!orig_stmt_methods) {
        orig_stmt_methods = (struct st_mysqlnd_stmt_methods *)
            pemalloc(sizeof(struct st_mysqlnd_stmt_methods), 1);
        memcpy(orig_stmt_methods, stmt_methods,
            sizeof(struct st_mysqlnd_stmt_methods));
    }

    stmt_methods->prepare = MYSQLND_METHOD(profiler_stmt, prepare);

#if PHP_VERSION_ID >= 70000
    stmt_methods->execute = MYSQLND_METHOD(profiler_stmt, execute);
    stmt_methods->dtor    = MYSQLND_METHOD(profiler_stmt, dtor);
#endif
}
/* }}} */
