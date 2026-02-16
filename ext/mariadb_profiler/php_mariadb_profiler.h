/*
  +----------------------------------------------------------------------+
  | MariaDB Query Profiler                                               |
  +----------------------------------------------------------------------+
  | mysqlnd plugin for intercepting and logging all DB queries            |
  +----------------------------------------------------------------------+
*/

#ifndef PHP_MARIADB_PROFILER_H
#define PHP_MARIADB_PROFILER_H

extern zend_module_entry mariadb_profiler_module_entry;
#define phpext_mariadb_profiler_ptr &mariadb_profiler_module_entry

#define PHP_MARIADB_PROFILER_VERSION "0.1.0"

#ifdef PHP_WIN32
# define PHP_MARIADB_PROFILER_API __declspec(dllexport)
#elif defined(__GNUC__) && __GNUC__ >= 4
# define PHP_MARIADB_PROFILER_API __attribute__ ((visibility("default")))
#else
# define PHP_MARIADB_PROFILER_API
#endif

#ifdef ZTS
#include "TSRM.h"
#endif

#include "ext/mysqlnd/mysqlnd.h"
#include "ext/mysqlnd/mysqlnd_structs.h"
#if PHP_VERSION_ID >= 50400
# include "ext/mysqlnd/mysqlnd_statistics.h"
#endif
/*
 * mysqlnd_ext_plugin.h was introduced in PHP 5.4; on PHP 5.3 the plugin
 * registration functions (mysqlnd_plugin_register, mysqlnd_conn_get_methods,
 * mysqlnd_stmt_get_methods) are declared directly in mysqlnd.h.
 */
#if PHP_VERSION_ID >= 50400
# include "ext/mysqlnd/mysqlnd_ext_plugin.h"
#endif

/* Include compatibility layer (must come after php.h and mysqlnd headers) */
#include "php_mariadb_profiler_compat.h"

/* Context tag limits */
#define PROFILER_MAX_TAG_DEPTH 64
#define PROFILER_MAX_TAG_LEN   256

/* Module globals */
ZEND_BEGIN_MODULE_GLOBALS(mariadb_profiler)
    zend_bool  enabled;
    char      *log_dir;
    zend_bool  raw_log;
    /* Runtime state */
    time_t     last_job_check;
    zend_long  job_check_interval; /* seconds between job file checks */
    char     **active_jobs;
    int        active_job_count;
    /* Context tag stack */
    char      *tag_stack[PROFILER_MAX_TAG_DEPTH];
    int        tag_depth;
    /* Trace settings */
    zend_long  trace_depth;         /* 0=disabled, N=capture N frames */
#if PHP_VERSION_ID >= 70000
    /* Prepared statement query template storage (PHP 7.0+) */
    HashTable *stmt_queries;        /* stmt ptr -> query template string */
#endif
ZEND_END_MODULE_GLOBALS(mariadb_profiler)

/* Globals accessor: extern declaration for use across compilation units */
ZEND_EXTERN_MODULE_GLOBALS(mariadb_profiler)

/*
 * PROFILER_G accessor:
 *   PHP 7.0+ ZTS: uses ZEND_MODULE_GLOBALS_ACCESSOR (TSRMG_FAST)
 *   PHP 5.x  ZTS: uses TSRMG (provided by compat header)
 *   NTS:          direct global access
 */
#ifdef ZTS
#define PROFILER_G(v) ZEND_MODULE_GLOBALS_ACCESSOR(mariadb_profiler, v)
#else
#define PROFILER_G(v) (mariadb_profiler_globals.v)
#endif

/* Function declarations */
PHP_MINIT_FUNCTION(mariadb_profiler);
PHP_MSHUTDOWN_FUNCTION(mariadb_profiler);
PHP_RINIT_FUNCTION(mariadb_profiler);
PHP_RSHUTDOWN_FUNCTION(mariadb_profiler);
PHP_MINFO_FUNCTION(mariadb_profiler);

/* mysqlnd plugin */
void mariadb_profiler_mysqlnd_plugin_register(void);

/* Job management */
int  profiler_job_refresh_active_jobs(void);
void profiler_job_free_active_jobs(void);
int  profiler_job_is_any_active(void);
char **profiler_job_get_active_list(int *count);

/* Logging – status is "ok" or "err" (NULL treated as "ok") */
void profiler_log_query(const char *query, size_t query_len, const char *status);
void profiler_log_query_with_params(const char *query, size_t query_len,
                                    const char *params_json, const char *status);
void profiler_log_raw(const char *job_key, const char *query, size_t query_len,
                      const char *tag, const char *trace_json,
                      const char *params_json, const char *status);
void profiler_log_init(void);
void profiler_log_shutdown(void);

/* Context tags */
const char *profiler_tag_current(void);
int         profiler_tag_push(const char *tag, size_t tag_len);
char       *profiler_tag_pop(void);
char       *profiler_tag_pop_until(const char *target, size_t target_len);
void        profiler_tag_clear_all(void);

/* Trace */
char *profiler_trace_capture_json(void);

/* PHP functions */
PHP_FUNCTION(mariadb_profiler_tag);
PHP_FUNCTION(mariadb_profiler_untag);
PHP_FUNCTION(mariadb_profiler_get_tag);

#endif /* PHP_MARIADB_PROFILER_H */
