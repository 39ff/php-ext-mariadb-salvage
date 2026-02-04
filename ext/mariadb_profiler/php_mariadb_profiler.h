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
#include "ext/mysqlnd/mysqlnd_statistics.h"

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
ZEND_END_MODULE_GLOBALS(mariadb_profiler)

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

/* Logging */
void profiler_log_query(const char *query, size_t query_len);
void profiler_log_raw(const char *job_key, const char *query, size_t query_len);
void profiler_log_init(void);
void profiler_log_shutdown(void);

#endif /* PHP_MARIADB_PROFILER_H */
