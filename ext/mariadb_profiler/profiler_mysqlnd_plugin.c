/*
  +----------------------------------------------------------------------+
  | MariaDB Query Profiler - mysqlnd plugin                              |
  +----------------------------------------------------------------------+
  | Hooks into mysqlnd to intercept all queries                          |
  +----------------------------------------------------------------------+
*/

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include "php.h"
#include "php_mariadb_profiler.h"
#include "ext/mysqlnd/mysqlnd.h"
#include "ext/mysqlnd/mysqlnd_structs.h"
#include "ext/mysqlnd/mysqlnd_connection.h"
#include "ext/mysqlnd/mysqlnd_result.h"

static unsigned int profiler_plugin_id;

/* Original method pointers we save for chaining */
static struct st_mysqlnd_conn_data_methods *orig_conn_methods = NULL;

/* {{{ profiler_query_hook
 * Called for every mysqlnd_conn_data::query() call */
static enum_func_status
MYSQLND_METHOD(profiler_conn, query)(
    MYSQLND_CONN_DATA *conn,
    const char *query,
    const size_t query_len)
{
    enum_func_status ret;

    /* Log the query if any job is active */
    if (PROFILER_G(enabled) && profiler_job_is_any_active()) {
        profiler_log_query(query, query_len);
    }

    /* Call the original method */
    ret = orig_conn_methods->query(conn, query, query_len);

    return ret;
}
/* }}} */

/* {{{ profiler_send_query_hook
 * Called for every mysqlnd_conn_data::send_query() */
static enum_func_status
MYSQLND_METHOD(profiler_conn, send_query)(
    MYSQLND_CONN_DATA *conn,
    const char *query,
    const size_t query_len,
    zval *read_cb,
    zval *err_cb)
{
    enum_func_status ret;

    if (PROFILER_G(enabled) && profiler_job_is_any_active()) {
        profiler_log_query(query, query_len);
    }

    ret = orig_conn_methods->send_query(conn, query, query_len, read_cb, err_cb);

    return ret;
}
/* }}} */

/* {{{ profiler_prepare_hook
 * Intercepts prepared statements at prepare time to log the query template */
static MYSQLND_STMT *
MYSQLND_METHOD(profiler_conn, stmt_init)(
    MYSQLND_CONN_DATA * const conn)
{
    return orig_conn_methods->stmt_init(conn);
}
/* }}} */

/* Original stmt methods for chaining */
static struct st_mysqlnd_stmt_methods *orig_stmt_methods = NULL;

/* {{{ profiler_stmt_prepare_hook */
static enum_func_status
MYSQLND_METHOD(profiler_stmt, prepare)(
    MYSQLND_STMT * const stmt,
    const char *query,
    const size_t query_len)
{
    enum_func_status ret;

    /* Log prepared statement query template */
    if (PROFILER_G(enabled) && profiler_job_is_any_active()) {
        profiler_log_query(query, query_len);
    }

    ret = orig_stmt_methods->prepare(stmt, query, query_len);

    return ret;
}
/* }}} */

/* {{{ mariadb_profiler_mysqlnd_plugin_register */
void mariadb_profiler_mysqlnd_plugin_register(void)
{
    struct st_mysqlnd_conn_data_methods *conn_methods;
    struct st_mysqlnd_stmt_methods *stmt_methods;

    /* Register as a mysqlnd plugin */
    profiler_plugin_id = mysqlnd_plugin_register();

    /* Hook connection methods */
    conn_methods = mysqlnd_conn_get_methods();

    /* Save originals for chaining */
    if (!orig_conn_methods) {
        orig_conn_methods = (struct st_mysqlnd_conn_data_methods *)
            pemalloc(sizeof(struct st_mysqlnd_conn_data_methods), 1);
        memcpy(orig_conn_methods, conn_methods,
            sizeof(struct st_mysqlnd_conn_data_methods));
    }

    /* Install our hooks */
    conn_methods->query      = MYSQLND_METHOD(profiler_conn, query);
    conn_methods->send_query = MYSQLND_METHOD(profiler_conn, send_query);
    conn_methods->stmt_init  = MYSQLND_METHOD(profiler_conn, stmt_init);

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
