/*
  +----------------------------------------------------------------------+
  | MariaDB Query Profiler - Trace Capture Header                        |
  +----------------------------------------------------------------------+
  | Captures PHP call stack at query execution time                      |
  +----------------------------------------------------------------------+
*/

#ifndef PROFILER_TRACE_H
#define PROFILER_TRACE_H

/*
 * Capture the current PHP backtrace and return it as a JSON array string.
 *
 * Returns NULL if trace_depth is 0 (disabled) or capture fails.
 * Caller must efree() the returned string.
 *
 * Output format:
 *   [{"call":"ClassName->method","file":"/path/to/file.php","line":42}, ...]
 */
char *profiler_trace_capture_json(void);

#endif /* PROFILER_TRACE_H */
