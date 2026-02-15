/*
  +----------------------------------------------------------------------+
  | MariaDB Query Profiler - Log Writer Header                           |
  +----------------------------------------------------------------------+
*/

#ifndef PROFILER_LOG_H
#define PROFILER_LOG_H

#define PROFILER_RAW_LOG_EXT    ".raw.log"
#define PROFILER_PARSED_LOG_EXT ".jsonl"

/* Shared JSON escape utility (defined in profiler_log.c) */
char *profiler_log_escape_json_string(const char *str, size_t len);

#endif /* PROFILER_LOG_H */
