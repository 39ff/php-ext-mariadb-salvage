/*
  +----------------------------------------------------------------------+
  | MariaDB Query Profiler - Job State Reader                            |
  +----------------------------------------------------------------------+
  | Reads active job state from shared jobs.json file                    |
  +----------------------------------------------------------------------+
*/

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include "php.h"
#include "php_mariadb_profiler.h"
#include "profiler_job.h"

#include <sys/file.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <time.h>

/* {{{ profiler_job_get_jobs_path
 * Returns path to jobs.json (caller must efree) */
static char *profiler_job_get_jobs_path(void)
{
    char *path;
    spprintf(&path, 0, "%s/%s", PROFILER_G(log_dir), PROFILER_JOBS_FILENAME);
    return path;
}
/* }}} */

/* {{{ profiler_job_parse_active_jobs
 * Simple JSON parser for jobs.json - extracts active job keys
 * Format: {"active_jobs":{"uuid1":{...},"uuid2":{...}}}
 * We only need the keys of active_jobs object */
static int profiler_job_parse_active_jobs(const char *json, char ***keys, int *count)
{
    const char *ptr, *key_start;
    char **job_keys = NULL;
    int job_count = 0;
    int capacity = 8;

    *keys = NULL;
    *count = 0;

    if (!json || !*json) {
        return SUCCESS;
    }

    /* Find "active_jobs" key */
    ptr = strstr(json, "\"active_jobs\"");
    if (!ptr) {
        return SUCCESS;
    }

    /* Skip to the opening { of the active_jobs object */
    ptr = strchr(ptr + 13, '{');
    if (!ptr) {
        return SUCCESS;
    }
    ptr++; /* skip { */

    job_keys = (char **)pecalloc(capacity, sizeof(char *), 0);

    /* Parse keys from the object */
    while (*ptr) {
        /* Skip whitespace */
        while (*ptr && (*ptr == ' ' || *ptr == '\t' || *ptr == '\n' || *ptr == '\r' || *ptr == ',')) {
            ptr++;
        }

        if (*ptr == '}') {
            break; /* End of active_jobs object */
        }

        if (*ptr == '"') {
            ptr++; /* skip opening quote */
            key_start = ptr;

            /* Find closing quote */
            while (*ptr && *ptr != '"') {
                if (*ptr == '\\') ptr++; /* skip escaped char */
                ptr++;
            }

            if (*ptr == '"') {
                size_t key_len = ptr - key_start;
                if (key_len > 0 && key_len < PROFILER_MAX_JOB_KEY) {
                    /* Grow array if needed */
                    if (job_count >= capacity) {
                        capacity *= 2;
                        job_keys = (char **)perealloc(job_keys, capacity * sizeof(char *), 0);
                    }

                    job_keys[job_count] = (char *)pemalloc(key_len + 1, 0);
                    memcpy(job_keys[job_count], key_start, key_len);
                    job_keys[job_count][key_len] = '\0';
                    job_count++;
                }
                ptr++; /* skip closing quote */
            }

            /* Skip the value (everything until next key or end) */
            {
                int depth = 0;
                while (*ptr) {
                    if (*ptr == '{') depth++;
                    else if (*ptr == '}') {
                        if (depth == 0) break;
                        depth--;
                    }
                    else if (*ptr == ',' && depth == 0) break;
                    else if (*ptr == '"') {
                        ptr++;
                        while (*ptr && *ptr != '"') {
                            if (*ptr == '\\') ptr++;
                            ptr++;
                        }
                    }
                    ptr++;
                }
            }
        } else {
            ptr++; /* skip unexpected char */
        }
    }

    if (job_count == 0) {
        pefree(job_keys, 0);
        return SUCCESS;
    }

    *keys = job_keys;
    *count = job_count;
    return SUCCESS;
}
/* }}} */

/* {{{ profiler_job_refresh_active_jobs */
int profiler_job_refresh_active_jobs(void)
{
    char *jobs_path;
    int fd;
    struct stat st;
    char *buf = NULL;
    ssize_t bytes_read;

    /* Free previous state */
    profiler_job_free_active_jobs();

    jobs_path = profiler_job_get_jobs_path();

    fd = open(jobs_path, O_RDONLY);
    efree(jobs_path);

    if (fd < 0) {
        /* No jobs file = no active jobs */
        PROFILER_G(last_job_check) = time(NULL);
        return SUCCESS;
    }

    /* Shared lock for reading */
    if (flock(fd, LOCK_SH) != 0) {
        close(fd);
        return FAILURE;
    }

    if (fstat(fd, &st) != 0 || st.st_size == 0) {
        flock(fd, LOCK_UN);
        close(fd);
        PROFILER_G(last_job_check) = time(NULL);
        return SUCCESS;
    }

    buf = (char *)emalloc(st.st_size + 1);
    bytes_read = read(fd, buf, st.st_size);
    buf[bytes_read > 0 ? bytes_read : 0] = '\0';

    flock(fd, LOCK_UN);
    close(fd);

    /* Parse the JSON to get active job keys */
    profiler_job_parse_active_jobs(buf, &PROFILER_G(active_jobs), &PROFILER_G(active_job_count));

    efree(buf);
    PROFILER_G(last_job_check) = time(NULL);

    return SUCCESS;
}
/* }}} */

/* {{{ profiler_job_free_active_jobs */
void profiler_job_free_active_jobs(void)
{
    int i;
    if (PROFILER_G(active_jobs)) {
        for (i = 0; i < PROFILER_G(active_job_count); i++) {
            if (PROFILER_G(active_jobs)[i]) {
                pefree(PROFILER_G(active_jobs)[i], 0);
            }
        }
        pefree(PROFILER_G(active_jobs), 0);
        PROFILER_G(active_jobs) = NULL;
    }
    PROFILER_G(active_job_count) = 0;
}
/* }}} */

/* {{{ profiler_job_is_any_active
 * Check if there are active jobs, refreshing from file if interval elapsed */
int profiler_job_is_any_active(void)
{
    time_t now;

    if (!PROFILER_G(enabled)) {
        return 0;
    }

    now = time(NULL);

    /* Refresh job list if check interval has elapsed */
    if ((now - PROFILER_G(last_job_check)) >= PROFILER_G(job_check_interval)) {
        profiler_job_refresh_active_jobs();
    }

    return PROFILER_G(active_job_count) > 0;
}
/* }}} */

/* {{{ profiler_job_get_active_list */
char **profiler_job_get_active_list(int *count)
{
    if (count) {
        *count = PROFILER_G(active_job_count);
    }
    return PROFILER_G(active_jobs);
}
/* }}} */
