/*
  +----------------------------------------------------------------------+
  | MariaDB Query Profiler - Context Tag Header                          |
  +----------------------------------------------------------------------+
  | Push/pop context tags for grouping queries by business logic         |
  +----------------------------------------------------------------------+
*/

#ifndef PROFILER_TAG_H
#define PROFILER_TAG_H

#include <stddef.h> /* size_t */

/* Get the current (top of stack) tag, or NULL if none */
const char *profiler_tag_current(void);

/* Push a tag onto the stack. Returns SUCCESS/FAILURE. */
int profiler_tag_push(const char *tag, size_t tag_len);

/* Pop the top tag. Caller must efree() the result. Returns NULL if empty. */
char *profiler_tag_pop(void);

/* Pop all tags down to and including target. Caller must efree() result.
 * Returns NULL and emits E_WARNING if target not found. */
char *profiler_tag_pop_until(const char *target, size_t target_len);

/* Free all tags on the stack (called at request shutdown) */
void profiler_tag_clear_all(void);

#endif /* PROFILER_TAG_H */
