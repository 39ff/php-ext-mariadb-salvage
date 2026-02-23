/*
  +----------------------------------------------------------------------+
  | MariaDB Query Profiler - xdebug Integration                         |
  +----------------------------------------------------------------------+
  | Detects xdebug and calls xdebug_break() on slow queries             |
  | Compatible with PHP 5.3 - 8.4+                                      |
  +----------------------------------------------------------------------+
*/

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include "php.h"
#include "php_mariadb_profiler.h"
#include "profiler_xdebug.h"

/*
 * Per-request cache for xdebug detection.
 *   0  = not yet checked
 *   1  = xdebug step-debug is available
 *  -1  = xdebug not available / no step-debug
 */
static int xdebug_detected = 0;

/* {{{ profiler_xdebug_detect
 * Perform the actual detection of xdebug.
 * Checks the module registry for the "xdebug" extension.
 * On xdebug 3.x, also checks if xdebug.mode includes "debug". */
static int profiler_xdebug_detect(void)
{
#if PHP_VERSION_ID >= 70000
    zend_string *name;
    zval *mode_val;

    /* Check if xdebug module is loaded */
    name = zend_string_init("xdebug", sizeof("xdebug") - 1, 0);
    if (!zend_hash_exists(&module_registry, name)) {
        zend_string_release(name);
        return 0;
    }
    zend_string_release(name);

    /*
     * xdebug 3.x uses xdebug.mode to control which features are active.
     * If mode does not include "debug", step-debugging is disabled.
     * xdebug 2.x does not have xdebug.mode; step-debug is always available.
     */
    mode_val = zend_ini_str("xdebug.mode", sizeof("xdebug.mode") - 1, 0);
    if (mode_val && Z_TYPE_P(mode_val) == IS_STRING) {
        /* xdebug 3.x: check for "debug" in the mode string */
        if (strstr(Z_STRVAL_P(mode_val), "debug") != NULL) {
            return 1;
        }
        /* mode is set but doesn't include "debug" */
        return 0;
    }

    /* No xdebug.mode setting: either xdebug 2.x or mode defaults to "develop".
     * For xdebug 2.x, step-debug is always available.
     * For xdebug 3.x with default mode ("develop"), step-debug is off.
     * We check if xdebug_break function exists as the definitive test. */
    {
        zend_string *fn_name = zend_string_init("xdebug_break", sizeof("xdebug_break") - 1, 0);
        int exists = zend_hash_exists(EG(function_table), fn_name);
        zend_string_release(fn_name);
        return exists ? 1 : 0;
    }
#else
    /* PHP 5.x: check module registry with old API */
    if (zend_hash_exists(&module_registry, "xdebug", sizeof("xdebug")) == 0) {
        return 0;
    }
    /* xdebug 2.x on PHP 5: step-debug is always available */
    return 1;
#endif
}
/* }}} */

/* {{{ profiler_xdebug_is_debugger_active */
int profiler_xdebug_is_debugger_active(void)
{
    if (xdebug_detected == 0) {
        xdebug_detected = profiler_xdebug_detect() ? 1 : -1;
    }
    return (xdebug_detected == 1);
}
/* }}} */

/* {{{ profiler_xdebug_break
 * Call xdebug_break() to trigger a debugger breakpoint.
 * Works by looking up and calling the PHP function. */
void profiler_xdebug_break(void)
{
#if PHP_VERSION_ID >= 70000
    zval retval;
    zval fname;

    ZVAL_STRING(&fname, "xdebug_break");

    if (call_user_function(EG(function_table), NULL, &fname, &retval, 0, NULL) == SUCCESS) {
        zval_ptr_dtor(&retval);
    }

    zval_ptr_dtor(&fname);
#else
    zval *retval_ptr = NULL;
    zval *fname;
    zval retval;
    TSRMLS_FETCH();

    MAKE_STD_ZVAL(fname);
    ZVAL_STRING(fname, "xdebug_break", 1);

    INIT_ZVAL(retval);
    if (call_user_function(EG(function_table), NULL, fname, &retval, 0, NULL TSRMLS_CC) == SUCCESS) {
        zval_dtor(&retval);
    }

    zval_ptr_dtor(&fname);
#endif
}
/* }}} */

/* {{{ profiler_xdebug_request_shutdown */
void profiler_xdebug_request_shutdown(void)
{
    xdebug_detected = 0;
}
/* }}} */
