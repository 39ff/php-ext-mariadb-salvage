/*
  +----------------------------------------------------------------------+
  | MariaDB Query Profiler - mysqlnd plugin                              |
  +----------------------------------------------------------------------+
  | Hooks into mysqlnd to intercept all queries                          |
  | Compatible with PHP 7.4 - 8.4+                                      |
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
 * PHP 7.x:
 *   - mysqlnd_conn_get_methods() returns st_mysqlnd_conn_data_methods *
 *   - send_query has extra 'type' param (enum_mysqlnd_send_query_type)
 *
 * PHP 8.0:
 *   - mysqlnd_conn_get_methods() returns st_mysqlnd_conn_methods * (outer)
 *   - mysqlnd_conn_data_get_methods() returns st_mysqlnd_conn_data_methods *
 *   - send_query still has 'type' param (enum_mysqlnd_send_query_type)
 *
 * PHP 8.1+:
 *   - enum_mysqlnd_send_query_type removed; send_query without 'type' param
 */

static unsigned int profiler_plugin_id;

/* Original method pointers we save for chaining */
static struct st_mysqlnd_conn_data_methods *orig_conn_data_methods = NULL;
static struct st_mysqlnd_stmt_methods *orig_stmt_methods = NULL;

/* {{{ profiler_query_hook
 * Called for every mysqlnd_conn_data::query() call */
static enum_func_status
MYSQLND_METHOD(profiler_conn, query)(
    MYSQLND_CONN_DATA *conn,
    const char *query,
    const size_t query_len)
{
    /* Log the query if any job is active */
    if (PROFILER_G(enabled) && profiler_job_is_any_active()) {
        profiler_log_query(query, query_len);
    }

    /* Call the original method */
    return orig_conn_data_methods->query(conn, query, query_len);
}
/* }}} */

/* {{{ profiler_send_query_hook
 * send_query() signature changed between PHP versions:
 *   PHP 7.x-8.0: (conn, query, query_len, type, read_cb, err_cb)
 *   PHP 8.1+:    (conn, query, query_len, read_cb, err_cb)
 */
#if PHP_VERSION_ID < 80100
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
#endif
/* }}} */

/* {{{ profiler_stmt_prepare_hook
 * Intercepts prepared statements at prepare time to log the query template */
static enum_func_status
MYSQLND_METHOD(profiler_stmt, prepare)(
    MYSQLND_STMT * const stmt,
    const char *query,
    const size_t query_len)
{
    /* Log prepared statement query template */
    if (PROFILER_G(enabled) && profiler_job_is_any_active()) {
        profiler_log_query(query, query_len);
    }
    return orig_stmt_methods->prepare(stmt, query, query_len);
}
/* }}} */

/* {{{ mariadb_profiler_mysqlnd_plugin_register */
void mariadb_profiler_mysqlnd_plugin_register(void)
{
    struct st_mysqlnd_conn_data_methods *conn_data_methods;
    struct st_mysqlnd_stmt_methods *stmt_methods;

    /* Register as a mysqlnd plugin */
    profiler_plugin_id = mysqlnd_plugin_register();

    /*
     * Get connection DATA methods (where query/send_query live).
     * PHP 7.x: mysqlnd_conn_get_methods() returns conn_data_methods directly
     * PHP 8.0+: mysqlnd_conn_data_get_methods() is the correct accessor
     */
#if PHP_VERSION_ID < 80000
    conn_data_methods = mysqlnd_conn_get_methods();
#else
    conn_data_methods = mysqlnd_conn_data_get_methods();
#endif

    /* Save originals for chaining */
    if (!orig_conn_data_methods) {
        orig_conn_data_methods = (struct st_mysqlnd_conn_data_methods *)
            pemalloc(sizeof(struct st_mysqlnd_conn_data_methods), 1);
        memcpy(orig_conn_data_methods, conn_data_methods,
            sizeof(struct st_mysqlnd_conn_data_methods));
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
