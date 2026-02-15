/*
  +----------------------------------------------------------------------+
  | MariaDB Query Profiler - PHP Version Compatibility Layer             |
  +----------------------------------------------------------------------+
  | Provides compatibility macros for PHP 5.3 through 8.4+              |
  +----------------------------------------------------------------------+
*/

#ifndef PHP_MARIADB_PROFILER_COMPAT_H
#define PHP_MARIADB_PROFILER_COMPAT_H

#if PHP_VERSION_ID < 50300
# error "mariadb_profiler requires PHP 5.3.0 or later"
#endif

/* ---- zend_long (introduced in PHP 7.0) ---- */
#if PHP_VERSION_ID < 70000
typedef long zend_long;
#endif

/* ---- TSRMLS macros (empty/removed in PHP 7.0+) ---- */
#ifndef TSRMLS_DC
# define TSRMLS_DC
#endif
#ifndef TSRMLS_CC
# define TSRMLS_CC
#endif
#ifndef TSRMLS_D
# define TSRMLS_D  void
#endif
#ifndef TSRMLS_C
# define TSRMLS_C
#endif
#ifndef TSRMLS_FETCH
# define TSRMLS_FETCH()
#endif

/* ---- ZEND_TSRMLS_CACHE (introduced in PHP 7.0) ---- */
#ifndef ZEND_TSRMLS_CACHE_DEFINE
# define ZEND_TSRMLS_CACHE_DEFINE()
#endif
#ifndef ZEND_TSRMLS_CACHE_UPDATE
# define ZEND_TSRMLS_CACHE_UPDATE()
#endif

/* ---- PHP_FE_END (introduced in PHP 5.3.7) ---- */
#ifndef PHP_FE_END
# define PHP_FE_END { NULL, NULL, NULL }
#endif

/* ---- ZEND_MOD_END (4 fields: name, rel, version, type) ---- */
#ifndef ZEND_MOD_END
# define ZEND_MOD_END { NULL, NULL, NULL, 0 }
#endif

/* ---- ZEND_MODULE_GLOBALS_ACCESSOR (introduced in PHP 7.0) ---- */
#if PHP_VERSION_ID < 70000 && defined(ZTS)
# ifndef ZEND_MODULE_GLOBALS_ACCESSOR
#  define ZEND_MODULE_GLOBALS_ACCESSOR(module_name, v) \
     TSRMG(module_name##_globals_id, zend_##module_name##_globals *, v)
# endif
#endif

/*
 * ---- mysqlnd compatibility ----
 *
 * PHP 5.3:
 *   - Connection type: MYSQLND *
 *   - Methods struct: struct st_mysqlnd_conn_methods (has query/send_query)
 *   - Accessor: mysqlnd_conn_get_methods() -> st_mysqlnd_conn_methods *
 *   - send_query: simple (conn, query, query_len TSRMLS_DC)
 *
 * PHP 5.4-5.6:
 *   - Connection split: MYSQLND (outer) + MYSQLND_CONN_DATA (inner)
 *   - Methods struct: struct st_mysqlnd_conn_data_methods (has query/send_query)
 *   - Accessor: mysqlnd_conn_data_get_methods() -> st_mysqlnd_conn_data_methods *
 *     (mysqlnd_conn_get_methods() returns the OUTER st_mysqlnd_conn_methods)
 *   - send_query: simple (conn, query, query_len TSRMLS_DC)
 *
 * PHP 7.0-7.4:
 *   - Same data types as 5.4 but TSRMLS removed, query_len is const size_t
 *   - Accessor: mysqlnd_conn_get_methods() -> st_mysqlnd_conn_data_methods *
 *     (return type changed from outer to inner)
 *   - send_query: extended (type, read_cb, err_cb) with enum_mysqlnd_send_query_type
 *
 * PHP 8.0:
 *   - Accessor renamed: mysqlnd_conn_data_get_methods()
 *
 * PHP 8.1+:
 *   - send_query: enum_mysqlnd_send_query_type removed
 */

#if PHP_VERSION_ID < 50400
# define PROFILER_CONN_T           MYSQLND
# define PROFILER_CONN_METHODS_T   struct st_mysqlnd_conn_methods
#else
# define PROFILER_CONN_T           MYSQLND_CONN_DATA
# define PROFILER_CONN_METHODS_T   struct st_mysqlnd_conn_data_methods
#endif

#if PHP_VERSION_ID < 70000
# define PROFILER_QUERY_LEN_T      unsigned int
#else
# define PROFILER_QUERY_LEN_T      const size_t
#endif

/*
 * Methods accessor:
 *   PHP 5.3:     mysqlnd_conn_get_methods() returns st_mysqlnd_conn_methods *
 *   PHP 5.4-5.6: mysqlnd_conn_data_get_methods() returns st_mysqlnd_conn_data_methods *
 *   PHP 7.0-7.4: mysqlnd_conn_get_methods() returns st_mysqlnd_conn_data_methods *
 *   PHP 8.0+:    mysqlnd_conn_data_get_methods() returns st_mysqlnd_conn_data_methods *
 */
#if PHP_VERSION_ID < 50400
# define profiler_conn_data_get_methods() mysqlnd_conn_get_methods()
#elif PHP_VERSION_ID < 70000
# define profiler_conn_data_get_methods() mysqlnd_conn_data_get_methods()
#elif PHP_VERSION_ID < 80000
# define profiler_conn_data_get_methods() mysqlnd_conn_get_methods()
#else
# define profiler_conn_data_get_methods() mysqlnd_conn_data_get_methods()
#endif

/*
 * ---- zend_fetch_debug_backtrace compatibility ----
 *
 * PHP 5.3:     zend_fetch_debug_backtrace(zval*, int, int TSRMLS_DC)
 *              - no "limit" parameter
 * PHP 5.4-5.6: zend_fetch_debug_backtrace(zval*, int, int, int TSRMLS_DC)
 *              - 4th param = limit
 * PHP 7.0+:    zend_fetch_debug_backtrace(zval*, int, int, int)
 *              - TSRMLS removed
 */
#if PHP_VERSION_ID < 50400
# define PROFILER_FETCH_TRACE(zv, skip, opts, limit) \
    zend_fetch_debug_backtrace((zv), (skip), (opts) TSRMLS_CC)
#elif PHP_VERSION_ID < 70000
# define PROFILER_FETCH_TRACE(zv, skip, opts, limit) \
    zend_fetch_debug_backtrace((zv), (skip), (opts), (limit) TSRMLS_CC)
#else
# define PROFILER_FETCH_TRACE(zv, skip, opts, limit) \
    zend_fetch_debug_backtrace((zv), (skip), (opts), (limit))
#endif

/*
 * ---- zend_parse_parameters string length type ----
 *
 * PHP 5.x: int
 * PHP 7.0+: size_t
 */
#if PHP_VERSION_ID < 70000
# define PROFILER_PARAM_STR_LEN_T  int
#else
# define PROFILER_PARAM_STR_LEN_T  size_t
#endif

/*
 * ---- RETURN_STRING / RETVAL_STRING compatibility ----
 *
 * PHP 5.x: RETURN_STRING(s, dup) / RETVAL_STRING(s, dup)
 * PHP 7.0+: RETURN_STRING(s) / RETVAL_STRING(s)  (always copies)
 */
#if PHP_VERSION_ID < 70000
# define PROFILER_RETURN_STRING(s)  RETURN_STRING((s), 1)
# define PROFILER_RETVAL_STRING(s)  RETVAL_STRING((s), 1)
#else
# define PROFILER_RETURN_STRING(s)  RETURN_STRING(s)
# define PROFILER_RETVAL_STRING(s)  RETVAL_STRING(s)
#endif

/*
 * ---- HashTable access compatibility ----
 *
 * PHP 5.x: zend_hash_find returns int, stores result in void**
 * PHP 7.0+: zend_hash_str_find returns zval*
 */
#if PHP_VERSION_ID >= 70000
# define PROFILER_HT_FIND_STR(ht, key, key_len) \
    zend_hash_str_find((ht), (key), (key_len))
#endif

#endif /* PHP_MARIADB_PROFILER_COMPAT_H */
