/*
  +----------------------------------------------------------------------+
  | MariaDB Query Profiler - Log Writer                                  |
  +----------------------------------------------------------------------+
  | Writes query logs to per-job files                                   |
  | Supports context tags and PHP backtrace in log output                |
  +----------------------------------------------------------------------+
*/

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include "php.h"
#include "php_mariadb_profiler.h"
#include "profiler_log.h"
#include "profiler_job.h"
#include "profiler_tag.h"
#include "profiler_trace.h"

#ifndef PHP_WIN32
# include <sys/file.h>
# include <sys/time.h>
#endif
#include <time.h>

/* {{{ profiler_log_escape_json_string
 * Escape a string for JSON output. Caller must efree result. */
char *profiler_log_escape_json_string(const char *str, size_t len)
{
    size_t i;
    size_t out_len = 0;
    char *out;
    char *p;

    /* First pass: calculate output length */
    for (i = 0; i < len; i++) {
        switch (str[i]) {
            case '"':  case '\\': case '/':
                out_len += 2; break;
            case '\b': case '\f': case '\n': case '\r': case '\t':
                out_len += 2; break;
            default:
                if ((unsigned char)str[i] < 0x20) {
                    out_len += 6; /* \uXXXX */
                } else {
                    out_len++;
                }
        }
    }

    out = (char *)emalloc(out_len + 1);
    p = out;

    /* Second pass: write escaped output */
    for (i = 0; i < len; i++) {
        switch (str[i]) {
            case '"':  *p++ = '\\'; *p++ = '"';  break;
            case '\\': *p++ = '\\'; *p++ = '\\'; break;
            case '/':  *p++ = '\\'; *p++ = '/';  break;
            case '\b': *p++ = '\\'; *p++ = 'b';  break;
            case '\f': *p++ = '\\'; *p++ = 'f';  break;
            case '\n': *p++ = '\\'; *p++ = 'n';  break;
            case '\r': *p++ = '\\'; *p++ = 'r';  break;
            case '\t': *p++ = '\\'; *p++ = 't';  break;
            default:
                if ((unsigned char)str[i] < 0x20) {
                    p += snprintf(p, 7, "\\u%04x", (unsigned char)str[i]);
                } else {
                    *p++ = str[i];
                }
        }
    }
    *p = '\0';

    return out;
}
/* }}} */

/* {{{ profiler_log_get_timestamp
 * Returns current timestamp as string (caller must efree) */
static char *profiler_log_get_timestamp(void)
{
    struct timeval tv;
    struct tm tm_buf;
    char *buf;

    buf = (char *)emalloc(64);
    gettimeofday(&tv, NULL);
    localtime_r(&tv.tv_sec, &tm_buf);

    snprintf(buf, 64, "%04d-%02d-%02d %02d:%02d:%02d.%03d",
        tm_buf.tm_year + 1900, tm_buf.tm_mon + 1, tm_buf.tm_mday,
        tm_buf.tm_hour, tm_buf.tm_min, tm_buf.tm_sec,
        (int)(tv.tv_usec / 1000));

    return buf;
}
/* }}} */

/* {{{ profiler_log_get_microtime
 * Returns current time as float string for JSON */
static double profiler_log_get_microtime(void)
{
    struct timeval tv;
    gettimeofday(&tv, NULL);
    return (double)tv.tv_sec + (double)tv.tv_usec / 1000000.0;
}
/* }}} */

/* {{{ profiler_log_raw
 * Write raw query to job's raw log file.
 * tag, trace_json, params_json, and status may be NULL.
 * duration_ms: query execution time in milliseconds, negative if not measured. */
void profiler_log_raw(const char *job_key, const char *query, size_t query_len,
                      const char *tag, const char *trace_json,
                      const char *params_json, const char *status,
                      double duration_ms)
{
    char *filepath;
    FILE *fp;
    char *timestamp;
    TSRMLS_FETCH();

    spprintf(&filepath, 0, "%s/%s%s", PROFILER_G(log_dir), job_key, PROFILER_RAW_LOG_EXT);

    fp = fopen(filepath, "a");
    efree(filepath);

    if (!fp) {
        return;
    }

    /* Lock for writing */
    flock(fileno(fp), LOCK_EX);

    timestamp = profiler_log_get_timestamp();

    if (duration_ms >= 0) {
        if (tag) {
            fprintf(fp, "[%s] [%s] [%.3fms] [%s] %.*s\n", timestamp,
                    status ? status : "ok", duration_ms, tag,
                    (int)query_len, query);
        } else {
            fprintf(fp, "[%s] [%s] [%.3fms] %.*s\n", timestamp,
                    status ? status : "ok", duration_ms,
                    (int)query_len, query);
        }
    } else {
        if (tag) {
            fprintf(fp, "[%s] [%s] [%s] %.*s\n", timestamp,
                    status ? status : "ok", tag, (int)query_len, query);
        } else {
            fprintf(fp, "[%s] [%s] %.*s\n", timestamp,
                    status ? status : "ok", (int)query_len, query);
        }
    }

    /* Append bound parameter values if present */
    if (params_json && params_json[0] == '[' && params_json[1] != ']') {
        fprintf(fp, "  params: %s\n", params_json);
    }

    /* Append trace lines (indented with arrow prefix) */
    if (trace_json && trace_json[0] == '[' && trace_json[1] != ']') {
        /*
         * trace_json is a JSON array like:
         *   [{"call":"Foo->bar","file":"/app/Foo.php","line":42}, ...]
         * We do a lightweight parse to extract call/file/line for raw log.
         */
        const char *p = trace_json;
        while ((p = strstr(p, "\"call\":\"")) != NULL) {
            const char *call_start, *call_end;
            const char *file_start, *file_end;
            const char *line_start;
            long line_val = 0;

            /* Extract call */
            p += 8; /* skip "call":" */
            call_start = p;
            call_end = strchr(p, '"');
            if (!call_end) break;
            p = call_end + 1;

            /* Extract file */
            file_start = file_end = "";
            {
                const char *fp2 = strstr(p, "\"file\":\"");
                if (fp2 && fp2 < p + 200) {
                    fp2 += 8;
                    file_start = fp2;
                    file_end = strchr(fp2, '"');
                    if (!file_end) break;
                    p = file_end + 1;
                }
            }

            /* Extract line */
            {
                const char *lp = strstr(p, "\"line\":");
                if (lp && lp < p + 100) {
                    lp += 7;
                    line_val = strtol(lp, NULL, 10);
                }
            }

            fprintf(fp, "  <- %.*s() %.*s:%ld\n",
                (int)(call_end - call_start), call_start,
                (int)(file_end - file_start), file_start,
                line_val);
        }
    }

    efree(timestamp);

    flock(fileno(fp), LOCK_UN);
    fclose(fp);
}
/* }}} */

/* {{{ profiler_log_jsonl
 * Write JSON line to job's parsed log file.
 * tag, trace_json, params_json, and status may be NULL.
 * duration_ms: query execution time in milliseconds, negative if not measured.
 * SQL parsing (table/column extraction) is done by the CLI tool. */
static void profiler_log_jsonl(const char *job_key, const char *query, size_t query_len,
                               const char *tag, const char *trace_json,
                               const char *params_json, const char *status,
                               double duration_ms)
{
    char *filepath;
    FILE *fp;
    char *escaped_query;
    char *escaped_key;
    char *escaped_tag = NULL;
    double ts;
    TSRMLS_FETCH();

    spprintf(&filepath, 0, "%s/%s%s", PROFILER_G(log_dir), job_key, PROFILER_PARSED_LOG_EXT);

    fp = fopen(filepath, "a");
    efree(filepath);

    if (!fp) {
        return;
    }

    flock(fileno(fp), LOCK_EX);

    escaped_query = profiler_log_escape_json_string(query, query_len);
    escaped_key = profiler_log_escape_json_string(job_key, strlen(job_key));
    if (tag) {
        escaped_tag = profiler_log_escape_json_string(tag, strlen(tag));
    }
    ts = profiler_log_get_microtime();

    /* Build JSON line with optional tag, params, and trace fields */
    fprintf(fp, "{\"k\":\"%s\",\"q\":\"%s\"", escaped_key, escaped_query);

    if (escaped_tag) {
        fprintf(fp, ",\"tag\":\"%s\"", escaped_tag);
    }

    /* params_json is already a valid JSON array string e.g. ["123","active",null] */
    if (params_json) {
        fprintf(fp, ",\"params\":%s", params_json);
    }

    if (trace_json) {
        /* trace_json is already a valid JSON array string */
        fprintf(fp, ",\"trace\":%s", trace_json);
    }

    if (status) {
        fprintf(fp, ",\"s\":\"%s\"", status);
    }

    if (duration_ms >= 0) {
        fprintf(fp, ",\"duration_ms\":%.3f", duration_ms);
    }

    fprintf(fp, ",\"ts\":%.6f}\n", ts);

    efree(escaped_query);
    efree(escaped_key);
    if (escaped_tag) {
        efree(escaped_tag);
    }

    flock(fileno(fp), LOCK_UN);
    fclose(fp);
}
/* }}} */

/* {{{ profiler_log_query_internal
 * Internal: log a query to all active jobs with optional params and status.
 * Captures the current context tag and PHP trace once, shared across all jobs.
 * duration_ms: query execution time in milliseconds, negative if not measured. */
static void profiler_log_query_internal(const char *query, size_t query_len,
                                        const char *params_json,
                                        const char *status,
                                        double duration_ms)
{
    char **jobs;
    int job_count;
    int i;
    const char *tag;
    char *trace_json;
    TSRMLS_FETCH();

    jobs = profiler_job_get_active_list(&job_count);

    if (!jobs || job_count == 0) {
        return;
    }

    /* Capture tag and trace once (shared across all active jobs) */
    tag = profiler_tag_current();
    trace_json = profiler_trace_capture_json(); /* NULL if disabled */

    for (i = 0; i < job_count; i++) {
        /* Write JSONL entry */
        profiler_log_jsonl(jobs[i], query, query_len, tag, trace_json, params_json, status, duration_ms);

        /* Write raw log if enabled */
        if (PROFILER_G(raw_log)) {
            profiler_log_raw(jobs[i], query, query_len, tag, trace_json, params_json, status, duration_ms);
        }
    }

    if (trace_json) {
        efree(trace_json);
    }
}
/* }}} */

/* {{{ profiler_log_query
 * Main entry point: log a query (without params) to all active jobs.
 * status is "ok" or "err" (NULL treated as "ok").
 * duration_ms: query execution time in milliseconds, negative if not measured. */
void profiler_log_query(const char *query, size_t query_len,
                        const char *status, double duration_ms)
{
    profiler_log_query_internal(query, query_len, NULL, status, duration_ms);
}
/* }}} */

/* {{{ profiler_log_query_with_params
 * Log a prepared statement query with bound parameter values to all active jobs.
 * status is "ok" or "err" (NULL treated as "ok").
 * duration_ms: query execution time in milliseconds, negative if not measured. */
void profiler_log_query_with_params(const char *query, size_t query_len,
                                    const char *params_json, const char *status,
                                    double duration_ms)
{
    profiler_log_query_internal(query, query_len, params_json, status, duration_ms);
}
/* }}} */

/* {{{ profiler_log_init */
void profiler_log_init(void)
{
    /* Nothing to initialize for now - file handles opened per-write */
}
/* }}} */

/* {{{ profiler_log_shutdown */
void profiler_log_shutdown(void)
{
    /* Nothing to clean up */
}
/* }}} */
