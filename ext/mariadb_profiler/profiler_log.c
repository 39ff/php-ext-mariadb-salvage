/*
  +----------------------------------------------------------------------+
  | MariaDB Query Profiler - Log Writer                                  |
  +----------------------------------------------------------------------+
  | Writes query logs to per-job files                                   |
  +----------------------------------------------------------------------+
*/

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include "php.h"
#include "php_mariadb_profiler.h"
#include "profiler_log.h"
#include "profiler_job.h"

#include <sys/file.h>
#include <sys/time.h>
#include <time.h>

/* {{{ profiler_log_escape_json_string
 * Escape a string for JSON output. Caller must efree result. */
static char *profiler_log_escape_json_string(const char *str, size_t len)
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
 * Write raw query to job's raw log file */
void profiler_log_raw(const char *job_key, const char *query, size_t query_len)
{
    char *filepath;
    FILE *fp;
    char *timestamp;

    spprintf(&filepath, 0, "%s/%s%s", PROFILER_G(log_dir), job_key, PROFILER_RAW_LOG_EXT);

    fp = fopen(filepath, "a");
    efree(filepath);

    if (!fp) {
        return;
    }

    /* Lock for writing */
    flock(fileno(fp), LOCK_EX);

    timestamp = profiler_log_get_timestamp();
    fprintf(fp, "[%s] %.*s\n", timestamp, (int)query_len, query);
    efree(timestamp);

    flock(fileno(fp), LOCK_UN);
    fclose(fp);
}
/* }}} */

/* {{{ profiler_log_jsonl
 * Write JSON line to job's parsed log file.
 * Only writes raw query + timestamp here. SQL parsing (table/column extraction)
 * is done by the CLI tool using PHPSQLParser for accuracy. */
static void profiler_log_jsonl(const char *job_key, const char *query, size_t query_len)
{
    char *filepath;
    FILE *fp;
    char *escaped_query;
    char *escaped_key;
    double ts;

    spprintf(&filepath, 0, "%s/%s%s", PROFILER_G(log_dir), job_key, PROFILER_PARSED_LOG_EXT);

    fp = fopen(filepath, "a");
    efree(filepath);

    if (!fp) {
        return;
    }

    flock(fileno(fp), LOCK_EX);

    escaped_query = profiler_log_escape_json_string(query, query_len);
    escaped_key = profiler_log_escape_json_string(job_key, strlen(job_key));
    ts = profiler_log_get_microtime();

    fprintf(fp, "{\"k\":\"%s\",\"q\":\"%s\",\"ts\":%.6f}\n",
        escaped_key, escaped_query, ts);

    efree(escaped_query);
    efree(escaped_key);

    flock(fileno(fp), LOCK_UN);
    fclose(fp);
}
/* }}} */

/* {{{ profiler_log_query
 * Main entry point: log a query to all active jobs */
void profiler_log_query(const char *query, size_t query_len)
{
    char **jobs;
    int job_count;
    int i;

    jobs = profiler_job_get_active_list(&job_count);

    if (!jobs || job_count == 0) {
        return;
    }

    for (i = 0; i < job_count; i++) {
        /* Write JSONL entry (raw query + metadata) */
        profiler_log_jsonl(jobs[i], query, query_len);

        /* Write raw log if enabled */
        if (PROFILER_G(raw_log)) {
            profiler_log_raw(jobs[i], query, query_len);
        }
    }
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
