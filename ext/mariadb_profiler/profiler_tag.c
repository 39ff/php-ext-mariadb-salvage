/*
  +----------------------------------------------------------------------+
  | MariaDB Query Profiler - Context Tag Implementation                  |
  +----------------------------------------------------------------------+
  | Push/pop context tags for grouping queries by business logic.        |
  | Tags are stored in a per-request stack in module globals.            |
  | Compatible with PHP 5.3 - 8.4+                                      |
  +----------------------------------------------------------------------+
*/

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include "php.h"
#include "php_mariadb_profiler.h"
#include "profiler_tag.h"

/* {{{ profiler_tag_current
 * Returns the current (top of stack) tag, or NULL if stack is empty.
 * The returned pointer is owned by the stack - do NOT free it. */
const char *profiler_tag_current(void)
{
    TSRMLS_FETCH();

    if (PROFILER_G(tag_depth) <= 0) {
        return NULL;
    }
    return PROFILER_G(tag_stack)[PROFILER_G(tag_depth) - 1];
}
/* }}} */

/* {{{ profiler_tag_push */
int profiler_tag_push(const char *tag, size_t tag_len)
{
    TSRMLS_FETCH();

    if (PROFILER_G(tag_depth) >= PROFILER_MAX_TAG_DEPTH) {
        php_error_docref(NULL TSRMLS_CC, E_WARNING,
            "mariadb_profiler: tag stack overflow (max %d)", PROFILER_MAX_TAG_DEPTH);
        return FAILURE;
    }

    if (tag_len > PROFILER_MAX_TAG_LEN) {
        tag_len = PROFILER_MAX_TAG_LEN;
    }

    PROFILER_G(tag_stack)[PROFILER_G(tag_depth)] = estrndup(tag, tag_len);
    PROFILER_G(tag_depth)++;
    return SUCCESS;
}
/* }}} */

/* {{{ profiler_tag_pop
 * Pop the top tag. Caller must efree() the returned string. */
char *profiler_tag_pop(void)
{
    char *tag;
    TSRMLS_FETCH();

    if (PROFILER_G(tag_depth) <= 0) {
        return NULL;
    }

    PROFILER_G(tag_depth)--;
    tag = PROFILER_G(tag_stack)[PROFILER_G(tag_depth)];
    PROFILER_G(tag_stack)[PROFILER_G(tag_depth)] = NULL;
    return tag;
}
/* }}} */

/* {{{ profiler_tag_pop_until
 * Pop all tags down to and including the target tag.
 * Searches from the top of the stack downward.
 * Returns the target tag string (caller must efree), or NULL if not found. */
char *profiler_tag_pop_until(const char *target, size_t target_len)
{
    int i, found = -1;
    char *result;
    TSRMLS_FETCH();

    /* Search from top of stack */
    for (i = PROFILER_G(tag_depth) - 1; i >= 0; i--) {
        if (PROFILER_G(tag_stack)[i]
            && strlen(PROFILER_G(tag_stack)[i]) == target_len
            && memcmp(PROFILER_G(tag_stack)[i], target, target_len) == 0) {
            found = i;
            break;
        }
    }

    if (found < 0) {
        return NULL;
    }

    /* Keep the target tag as the return value */
    result = PROFILER_G(tag_stack)[found];

    /* Free everything above the found position */
    for (i = PROFILER_G(tag_depth) - 1; i > found; i--) {
        if (PROFILER_G(tag_stack)[i]) {
            efree(PROFILER_G(tag_stack)[i]);
            PROFILER_G(tag_stack)[i] = NULL;
        }
    }
    PROFILER_G(tag_stack)[found] = NULL;
    PROFILER_G(tag_depth) = found;

    return result;
}
/* }}} */

/* {{{ profiler_tag_clear_all
 * Free all tags on the stack (called at request shutdown) */
void profiler_tag_clear_all(void)
{
    int i;
    TSRMLS_FETCH();

    for (i = 0; i < PROFILER_G(tag_depth); i++) {
        if (PROFILER_G(tag_stack)[i]) {
            efree(PROFILER_G(tag_stack)[i]);
            PROFILER_G(tag_stack)[i] = NULL;
        }
    }
    PROFILER_G(tag_depth) = 0;
}
/* }}} */

/* ======================================================================
 * PHP userland functions
 * ====================================================================== */

/* {{{ proto bool mariadb_profiler_tag(string $tag)
 * Push a context tag onto the stack.
 * Returns true on success, false if profiling is disabled or stack overflows. */
PHP_FUNCTION(mariadb_profiler_tag)
{
    char *tag;
    PROFILER_PARAM_STR_LEN_T tag_len;
    TSRMLS_FETCH();

#if PHP_VERSION_ID >= 70000
    ZEND_PARSE_PARAMETERS_START(1, 1)
        Z_PARAM_STRING(tag, tag_len)
    ZEND_PARSE_PARAMETERS_END();
#else
    if (zend_parse_parameters(ZEND_NUM_ARGS() TSRMLS_CC, "s",
            &tag, &tag_len) == FAILURE) {
        RETURN_FALSE;
    }
#endif

    if (!PROFILER_G(enabled)) {
        RETURN_FALSE;
    }

    if (profiler_tag_push(tag, (size_t)tag_len) == SUCCESS) {
        RETURN_TRUE;
    }
    RETURN_FALSE;
}
/* }}} */

/* {{{ proto ?string mariadb_profiler_untag([string $tag])
 * Pop a tag from the stack.
 * Without argument: pops the top tag.
 * With argument: pops all tags down to and including the named tag.
 * Returns the removed tag, or null if stack is empty / tag not found. */
PHP_FUNCTION(mariadb_profiler_untag)
{
    char *tag = NULL;
    PROFILER_PARAM_STR_LEN_T tag_len = 0;
    TSRMLS_FETCH();

#if PHP_VERSION_ID >= 70000
    ZEND_PARSE_PARAMETERS_START(0, 1)
        Z_PARAM_OPTIONAL
        Z_PARAM_STRING(tag, tag_len)
    ZEND_PARSE_PARAMETERS_END();
#else
    if (zend_parse_parameters(ZEND_NUM_ARGS() TSRMLS_CC, "|s",
            &tag, &tag_len) == FAILURE) {
        RETURN_NULL();
    }
#endif

    if (!PROFILER_G(enabled) || PROFILER_G(tag_depth) <= 0) {
        RETURN_NULL();
    }

    if (tag == NULL || tag_len == 0) {
        /* No argument: simple pop */
        char *popped = profiler_tag_pop();
        if (popped) {
            PROFILER_RETVAL_STRING(popped);
            efree(popped);
            return;
        }
        RETURN_NULL();
    } else {
        /* Named argument: pop until the specified tag */
        char *popped = profiler_tag_pop_until(tag, (size_t)tag_len);
        if (popped) {
            PROFILER_RETVAL_STRING(popped);
            efree(popped);
            return;
        }
        php_error_docref(NULL TSRMLS_CC, E_WARNING,
            "mariadb_profiler: tag '%s' not found in stack", tag);
        RETURN_NULL();
    }
}
/* }}} */

/* {{{ proto ?string mariadb_profiler_get_tag()
 * Get the current (top of stack) tag without removing it.
 * Returns the tag string, or null if no tag is set. */
PHP_FUNCTION(mariadb_profiler_get_tag)
{
    const char *tag;
    TSRMLS_FETCH();

#if PHP_VERSION_ID >= 70000
    ZEND_PARSE_PARAMETERS_NONE();
#endif

    if (!PROFILER_G(enabled)) {
        RETURN_NULL();
    }

    tag = profiler_tag_current();
    if (tag) {
        PROFILER_RETURN_STRING(tag);
    }
    RETURN_NULL();
}
/* }}} */
