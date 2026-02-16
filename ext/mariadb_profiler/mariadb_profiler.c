/*
  +----------------------------------------------------------------------+
  | MariaDB Query Profiler                                               |
  +----------------------------------------------------------------------+
  | Main module: lifecycle hooks and ini settings                        |
  | Compatible with PHP 5.3 - 8.4+                                      |
  +----------------------------------------------------------------------+
*/

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include "php.h"
#include "php_ini.h"
#include "ext/standard/info.h"
#include "php_mariadb_profiler.h"

#include <sys/stat.h>
#include <errno.h>

ZEND_DECLARE_MODULE_GLOBALS(mariadb_profiler)

/* {{{ INI settings */
PHP_INI_BEGIN()
    STD_PHP_INI_BOOLEAN("mariadb_profiler.enabled",
        "0",
        PHP_INI_SYSTEM,
        OnUpdateBool,
        enabled,
        zend_mariadb_profiler_globals,
        mariadb_profiler_globals)

    STD_PHP_INI_ENTRY("mariadb_profiler.log_dir",
        "/tmp/mariadb_profiler",
        PHP_INI_SYSTEM,
        OnUpdateString,
        log_dir,
        zend_mariadb_profiler_globals,
        mariadb_profiler_globals)

    STD_PHP_INI_BOOLEAN("mariadb_profiler.raw_log",
        "1",
        PHP_INI_SYSTEM,
        OnUpdateBool,
        raw_log,
        zend_mariadb_profiler_globals,
        mariadb_profiler_globals)

    STD_PHP_INI_ENTRY("mariadb_profiler.job_check_interval",
        "1",
        PHP_INI_SYSTEM,
        OnUpdateLong,
        job_check_interval,
        zend_mariadb_profiler_globals,
        mariadb_profiler_globals)

    STD_PHP_INI_ENTRY("mariadb_profiler.trace_depth",
        "0",
        PHP_INI_SYSTEM,
        OnUpdateLong,
        trace_depth,
        zend_mariadb_profiler_globals,
        mariadb_profiler_globals)
PHP_INI_END()
/* }}} */

/* {{{ php_mariadb_profiler_init_globals */
static void php_mariadb_profiler_init_globals(zend_mariadb_profiler_globals *g)
{
    memset(g, 0, sizeof(zend_mariadb_profiler_globals));
}
/* }}} */

/* {{{ Ensure log directory exists */
static int profiler_ensure_log_dir(TSRMLS_D)
{
    struct stat st;
    const char *dir = PROFILER_G(log_dir);

    if (!dir || !dir[0]) {
        return FAILURE;
    }

    if (stat(dir, &st) == 0) {
        if (S_ISDIR(st.st_mode)) {
            return SUCCESS;
        }
        php_error_docref(NULL TSRMLS_CC, E_WARNING,
            "mariadb_profiler.log_dir '%s' exists but is not a directory", dir);
        return FAILURE;
    }

    /* Try to create directory (mode 0777, umask will apply) */
    if (mkdir(dir, 0777) == 0) {
        return SUCCESS;
    }

    /* mkdir failed - try recursive creation */
    {
        char tmp[4096];
        char *p;
        size_t len;

        len = snprintf(tmp, sizeof(tmp), "%s", dir);
        if (len >= sizeof(tmp)) {
            return FAILURE;
        }

        /* Remove trailing slash */
        if (tmp[len - 1] == '/') {
            tmp[len - 1] = '\0';
        }

        for (p = tmp + 1; *p; p++) {
            if (*p == '/') {
                *p = '\0';
                if (stat(tmp, &st) != 0) {
                    if (mkdir(tmp, 0777) != 0 && errno != EEXIST) {
                        return FAILURE;
                    }
                }
                *p = '/';
            }
        }

        if (mkdir(tmp, 0777) != 0 && errno != EEXIST) {
            php_error_docref(NULL TSRMLS_CC, E_WARNING,
                "mariadb_profiler: failed to create log_dir '%s': %s",
                dir, strerror(errno));
            return FAILURE;
        }
    }

    return SUCCESS;
}
/* }}} */

/* {{{ PHP_MINIT_FUNCTION */
PHP_MINIT_FUNCTION(mariadb_profiler)
{
    ZEND_INIT_MODULE_GLOBALS(mariadb_profiler, php_mariadb_profiler_init_globals, NULL);
    REGISTER_INI_ENTRIES();

    if (PROFILER_G(enabled)) {
        mariadb_profiler_mysqlnd_plugin_register();
        profiler_log_init();
    }

    return SUCCESS;
}
/* }}} */

/* {{{ PHP_MSHUTDOWN_FUNCTION */
PHP_MSHUTDOWN_FUNCTION(mariadb_profiler)
{
    if (PROFILER_G(enabled)) {
        profiler_log_shutdown();
    }

    UNREGISTER_INI_ENTRIES();
    return SUCCESS;
}
/* }}} */

/* {{{ PHP_RINIT_FUNCTION */
PHP_RINIT_FUNCTION(mariadb_profiler)
{
#if defined(COMPILE_DL_MARIADB_PROFILER) && defined(ZTS)
    ZEND_TSRMLS_CACHE_UPDATE();
#endif

    if (PROFILER_G(enabled)) {
        /* Ensure log dir exists on first request */
        profiler_ensure_log_dir(TSRMLS_C);
        /* Load active jobs at request start */
        profiler_job_refresh_active_jobs();
#if PHP_VERSION_ID >= 70000
        /* Initialize prepared statement query template storage */
        ALLOC_HASHTABLE(PROFILER_G(stmt_queries));
        zend_hash_init(PROFILER_G(stmt_queries), 16, NULL, ZVAL_PTR_DTOR, 0);
#endif
    }

    return SUCCESS;
}
/* }}} */

/* {{{ PHP_RSHUTDOWN_FUNCTION */
PHP_RSHUTDOWN_FUNCTION(mariadb_profiler)
{
    if (PROFILER_G(enabled)) {
        profiler_tag_clear_all();
        profiler_job_free_active_jobs();
#if PHP_VERSION_ID >= 70000
        /* Free prepared statement query template storage */
        if (PROFILER_G(stmt_queries)) {
            zend_hash_destroy(PROFILER_G(stmt_queries));
            FREE_HASHTABLE(PROFILER_G(stmt_queries));
            PROFILER_G(stmt_queries) = NULL;
        }
#endif
    }
    return SUCCESS;
}
/* }}} */

/* {{{ PHP_MINFO_FUNCTION */
PHP_MINFO_FUNCTION(mariadb_profiler)
{
    char trace_depth_str[32];

    snprintf(trace_depth_str, sizeof(trace_depth_str), "%ld",
        (long)PROFILER_G(trace_depth));

    php_info_print_table_start();
    php_info_print_table_header(2, "MariaDB Query Profiler", "enabled");
    php_info_print_table_row(2, "Version", PHP_MARIADB_PROFILER_VERSION);
    php_info_print_table_row(2, "Log directory", PROFILER_G(log_dir));
    php_info_print_table_row(2, "Raw logging", PROFILER_G(raw_log) ? "Yes" : "No");
    php_info_print_table_row(2, "Trace depth", trace_depth_str);
    php_info_print_table_end();

    DISPLAY_INI_ENTRIES();
}
/* }}} */

/* {{{ arginfo for PHP functions */
ZEND_BEGIN_ARG_INFO_EX(arginfo_mariadb_profiler_tag, 0, 0, 1)
    ZEND_ARG_INFO(0, tag)
ZEND_END_ARG_INFO()

ZEND_BEGIN_ARG_INFO_EX(arginfo_mariadb_profiler_untag, 0, 0, 0)
    ZEND_ARG_INFO(0, tag)
ZEND_END_ARG_INFO()

ZEND_BEGIN_ARG_INFO_EX(arginfo_mariadb_profiler_get_tag, 0, 0, 0)
ZEND_END_ARG_INFO()
/* }}} */

/* {{{ mariadb_profiler_functions[] */
static const zend_function_entry mariadb_profiler_functions[] = {
    PHP_FE(mariadb_profiler_tag,     arginfo_mariadb_profiler_tag)
    PHP_FE(mariadb_profiler_untag,   arginfo_mariadb_profiler_untag)
    PHP_FE(mariadb_profiler_get_tag, arginfo_mariadb_profiler_get_tag)
    PHP_FE_END
};
/* }}} */

/* {{{ mariadb_profiler_module_deps[] */
static const zend_module_dep mariadb_profiler_module_deps[] = {
    ZEND_MOD_REQUIRED("mysqlnd")
    ZEND_MOD_END
};
/* }}} */

/* {{{ mariadb_profiler_module_entry */
zend_module_entry mariadb_profiler_module_entry = {
    STANDARD_MODULE_HEADER_EX,
    NULL,
    mariadb_profiler_module_deps,
    "mariadb_profiler",
    mariadb_profiler_functions,
    PHP_MINIT(mariadb_profiler),
    PHP_MSHUTDOWN(mariadb_profiler),
    PHP_RINIT(mariadb_profiler),
    PHP_RSHUTDOWN(mariadb_profiler),
    PHP_MINFO(mariadb_profiler),
    PHP_MARIADB_PROFILER_VERSION,
    PHP_MODULE_GLOBALS(mariadb_profiler),
    NULL,  /* GINIT */
    NULL,  /* GSHUTDOWN */
    NULL,  /* RPOSTSHUTDOWN */
    STANDARD_MODULE_PROPERTIES_EX
};
/* }}} */

#ifdef COMPILE_DL_MARIADB_PROFILER
# ifdef ZTS
ZEND_TSRMLS_CACHE_DEFINE()
# endif
ZEND_GET_MODULE(mariadb_profiler)
#endif
