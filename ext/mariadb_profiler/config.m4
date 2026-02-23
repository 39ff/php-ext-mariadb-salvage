dnl config.m4 for extension mariadb_profiler

PHP_ARG_ENABLE([mariadb_profiler],
  [whether to enable mariadb_profiler support],
  [AS_HELP_STRING([--enable-mariadb_profiler],
    [Enable mariadb_profiler support])],
  [no])

if test "$PHP_MARIADB_PROFILER" != "no"; then
  dnl Check for mysqlnd
  PHP_CHECK_FUNC(mysqlnd_query)

  dnl PHP 7.0+ supports static TSRMLS cache; harmless but unnecessary on 5.x
  PHP_VERSION_ID=$($PHP_CONFIG --vernum 2>/dev/null || echo 0)
  if test "$PHP_VERSION_ID" -ge 70000 2>/dev/null; then
    PROFILER_CFLAGS="-DZEND_ENABLE_STATIC_TSRMLS_CACHE=1"
  else
    PROFILER_CFLAGS=""
  fi

  PHP_NEW_EXTENSION(mariadb_profiler,
    mariadb_profiler.c profiler_mysqlnd_plugin.c profiler_job.c profiler_log.c profiler_tag.c profiler_trace.c profiler_xdebug.c,
    $ext_shared,, $PROFILER_CFLAGS)

  dnl Require mysqlnd
  PHP_ADD_EXTENSION_DEP(mariadb_profiler, mysqlnd, true)
fi
