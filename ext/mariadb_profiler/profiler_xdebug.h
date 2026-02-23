/*
  +----------------------------------------------------------------------+
  | MariaDB Query Profiler - xdebug Integration                         |
  +----------------------------------------------------------------------+
  | Detects xdebug presence and triggers breakpoints on slow queries    |
  +----------------------------------------------------------------------+
*/

#ifndef PROFILER_XDEBUG_H
#define PROFILER_XDEBUG_H

/*
 * Check whether the xdebug extension is loaded and has the step-debug
 * mode active.  Result is cached per-request so the module registry
 * lookup only happens once.
 *
 * Returns 1 if xdebug step-debug is available, 0 otherwise.
 */
int profiler_xdebug_is_debugger_active(void);

/*
 * Call xdebug_break() from the PHP userland function table.
 * This causes the xdebug debugger (IDE) to pause execution at the
 * current point, letting the developer inspect the context of a slow
 * query interactively.
 *
 * No-op if xdebug is not loaded or xdebug_break function is absent.
 */
void profiler_xdebug_break(void);

/*
 * Reset the per-request cached detection state.
 * Must be called during RSHUTDOWN.
 */
void profiler_xdebug_request_shutdown(void);

#endif /* PROFILER_XDEBUG_H */
