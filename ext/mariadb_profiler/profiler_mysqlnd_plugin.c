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

/*
 * mysqlnd internal API changed across PHP versions:
 *
 * PHP 5.3-5.4:
 *   - Connection type: MYSQLND *
 *   - Methods: struct st_mysqlnd_conn_methods (no data/outer split)
 *   - mysqlnd_conn_get_methods() returns st_mysqlnd_conn_methods *
 *   - query_len: unsigned int
 *   - All signatures include TSRMLS_DC
 *   - send_query has enum_mysqlnd_send_query_type + zval callbacks
 *
 * PHP 5.5-5.6:
 *   - Connection split into MYSQLND (outer) + MYSQLND_CONN_DATA (inner)
 *   - Methods: struct st_mysqlnd_conn_data_methods
 *   - mysqlnd_conn_get_methods() returns st_mysqlnd_conn_data_methods *
 *   - query_len: unsigned int
 *   - All signatures include TSRMLS_DC
 *   - send_query has enum_mysqlnd_send_query_type + zval callbacks
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
    /* Log the query if any job is active */
    if (PROFILER_G(enabled) && profiler_job_is_any_active()) {
        profiler_log_query(query, query_len);
    }

    /* Call the original method */
    return orig_conn_data_methods->query(conn, query, query_len TSRMLS_CC);
}
/* }}} */

/* {{{ profiler_send_query_hook
 * send_query() signature changed between PHP versions:
 *   PHP 5.3-5.6: (conn, query, query_len, type, read_cb, err_cb TSRMLS_DC)
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
    if (PROFILER_G(enabled) && profiler_job_is_any_active()) {
        profiler_log_query(query, query_len);
    }
    return orig_conn_data_methods->send_query(conn, query, query_len, read_cb, err_cb);
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
    if (PROFILER_G(enabled) && profiler_job_is_any_active()) {
        profiler_log_query(query, query_len);
    }
    return orig_conn_data_methods->send_query(conn, query, query_len, type, read_cb, err_cb);
}
#else
/* PHP 5.3-5.6: unsigned int, TSRMLS, with type param */
static enum_func_status
MYSQLND_METHOD(profiler_conn, send_query)(
    PROFILER_CONN_T *conn,
    const char *query,
    unsigned int query_len,
    enum_mysqlnd_send_query_type type,
    zval *read_cb,
    zval *err_cb TSRMLS_DC)
{
    if (PROFILER_G(enabled) && profiler_job_is_any_active()) {
        profiler_log_query(query, query_len);
    }
    return orig_conn_data_methods->send_query(conn, query, query_len, type, read_cb, err_cb TSRMLS_CC);
}
#endif
/* }}} */

/* {{{ profiler_stmt_prepare_hook
 * Intercepts prepared statements at prepare time to log the query template */
static enum_func_status
MYSQLND_METHOD(profiler_stmt, prepare)(
    MYSQLND_STMT * const stmt,
    const char *query,
    PROFILER_QUERY_LEN_T query_len TSRMLS_DC)
{
    /* Log prepared statement query template */
    if (PROFILER_G(enabled) && profiler_job_is_any_active()) {
        profiler_log_query(query, query_len);
    }
    return orig_stmt_methods->prepare(stmt, query, query_len TSRMLS_CC);
}
/* }}} */

/* {{{ mariadb_profiler_mysqlnd_plugin_register */
void mariadb_profiler_mysqlnd_plugin_register(void)
{
    PROFILER_CONN_METHODS_T *conn_data_methods;
    struct st_mysqlnd_stmt_methods *stmt_methods;

    /* Register as a mysqlnd plugin */
    profiler_plugin_id = mysqlnd_plugin_register();

    /*
     * Get connection DATA methods (where query/send_query live).
     * PHP 5.3-5.4: mysqlnd_conn_get_methods() returns st_mysqlnd_conn_methods *
     * PHP 5.5-7.4: mysqlnd_conn_get_methods() returns st_mysqlnd_conn_data_methods *
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
}
/* }}} */
