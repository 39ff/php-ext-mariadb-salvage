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

/*
 * ---- bool type compatibility for mysqlnd stmt dtor ----
 *
 * PHP 8.0+: uses C99 bool
 * PHP 7.x:  uses zend_bool (unsigned char)
 */
#if PHP_VERSION_ID >= 80000
# define PROFILER_BOOL_T bool
#else
# define PROFILER_BOOL_T zend_bool
#endif

/*
 * ---- Platform I/O compatibility (Windows) ----
 *
 * The extension uses POSIX APIs (flock, gettimeofday, localtime_r, open/read/close)
 * that are not available on Windows. These macros and inline functions provide
 * equivalent functionality using Win32 APIs.
 *
 * On Windows, php.h already includes <windows.h> and provides gettimeofday()
 * via win32/time.h. We only need to handle the remaining POSIX-specific APIs.
 */
#ifdef PHP_WIN32
# include <io.h>
# include <direct.h>

/* ssize_t is not defined in MSVC */
typedef intptr_t profiler_ssize_t;

/* POSIX I/O function mapping */
# define profiler_open     _open
# define profiler_read     _read
# define profiler_close    _close

/* S_ISDIR may not be defined on all MSVC versions */
# ifndef S_ISDIR
#  define S_ISDIR(m) (((m) & S_IFMT) == S_IFDIR)
# endif

/* mkdir: Windows _mkdir() takes only the path (no mode parameter) */
# define PROFILER_MKDIR(path, mode)  _mkdir(path)

/* localtime_r: Windows has localtime_s with swapped argument order */
static inline struct tm *profiler_localtime_r(const time_t *timep, struct tm *result)
{
    return (localtime_s(result, timep) == 0) ? result : NULL;
}
# define localtime_r profiler_localtime_r

/* File locking: Windows uses LockFileEx/UnlockFileEx instead of flock() */
# ifndef LOCK_SH
#  define LOCK_SH 1
# endif
# ifndef LOCK_EX
#  define LOCK_EX 2
# endif
# ifndef LOCK_NB
#  define LOCK_NB 4
# endif
# ifndef LOCK_UN
#  define LOCK_UN 8
# endif

/*
 * Map a Windows error code to errno.
 * PHP's internal _dosmaperr() is not exported for use by extensions,
 * so we provide our own minimal mapping for the error codes we handle.
 */
static inline void profiler_dosmaperr(unsigned long winerr)
{
    switch (winerr) {
        case ERROR_ACCESS_DENIED:       errno = EACCES; break;
        case ERROR_INVALID_HANDLE:      errno = EBADF;  break;
        case ERROR_NOT_ENOUGH_MEMORY:   errno = ENOMEM; break;
        case ERROR_LOCK_VIOLATION:      errno = EACCES; break;
        case ERROR_SHARING_VIOLATION:   errno = EACCES; break;
        case ERROR_FILE_NOT_FOUND:      errno = ENOENT; break;
        case ERROR_PATH_NOT_FOUND:      errno = ENOENT; break;
        case ERROR_ALREADY_EXISTS:      errno = EEXIST; break;
        case ERROR_FILE_EXISTS:         errno = EEXIST; break;
        default:                        errno = EINVAL; break;
    }
}

static inline int profiler_flock(int fd, int operation)
{
    HANDLE h = (HANDLE)_get_osfhandle(fd);
    DWORD flags = 0;
    OVERLAPPED ov = {0};

    if (h == INVALID_HANDLE_VALUE) {
        errno = EBADF;
        return -1;
    }

    if (operation & LOCK_UN) {
        if (UnlockFileEx(h, 0, MAXDWORD, MAXDWORD, &ov)) {
            return 0;
        }
        profiler_dosmaperr(GetLastError());
        return -1;
    }

    if (operation & LOCK_EX) {
        flags |= LOCKFILE_EXCLUSIVE_LOCK;
    }
    if (operation & LOCK_NB) {
        flags |= LOCKFILE_FAIL_IMMEDIATELY;
    }
    if (LockFileEx(h, flags, 0, MAXDWORD, MAXDWORD, &ov)) {
        return 0;
    }
    {
        DWORD lasterr = GetLastError();
        if ((operation & LOCK_NB) && lasterr == ERROR_LOCK_VIOLATION) {
            errno = EWOULDBLOCK;
        } else {
            profiler_dosmaperr(lasterr);
        }
    }
    return -1;
}
# define flock  profiler_flock

#else
/* Unix/Linux/macOS */

# include <unistd.h>
typedef ssize_t profiler_ssize_t;

# define profiler_open     open
# define profiler_read     read
# define profiler_close    close

# define PROFILER_MKDIR(path, mode)  mkdir(path, mode)

#endif /* PHP_WIN32 */

#endif /* PHP_MARIADB_PROFILER_COMPAT_H */
