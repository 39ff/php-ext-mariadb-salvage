# mariadb_profiler

PHP extension (mysqlnd plugin) for intercepting and profiling MariaDB/MySQL queries. Captures all database queries executed through mysqlnd, logs them with contextual metadata, and provides CLI tools for analyzing query patterns and performance.

## Features

- **Query Interception** — Hooks into mysqlnd's `query()` / `send_query()` to capture all DB queries transparently
- **Context Tagging** — Stack-based tagging API to group queries by business logic (e.g. `user_registration`, `checkout`)
- **Stack Trace Capture** — Configurable PHP backtrace capture at query time to identify where queries originate
- **Job Management** — Named profiling sessions with start/stop control and file-lock concurrency
- **SQL Analysis** — Extracts table names, column references, and bound parameters from captured queries
- **Multiple Log Formats** — Raw text logs (`.raw.log`) and structured JSONL (`.jsonl`)
- **Wide PHP Support** — PHP 5.3.2 through PHP 8.4+, both ZTS and NTS builds

## Requirements

- PHP >= 5.3.2 with mysqlnd enabled
- GCC / Make toolchain
- Autoconf (for `phpize`)
- Composer (for CLI dependencies)

## Installation

### Build the extension

```bash
make ext-build
```

This runs `phpize`, `./configure --enable-mariadb_profiler`, and `make`.

### Install

```bash
make ext-install
```

### php.ini configuration

```ini
extension=mariadb_profiler.so
mariadb_profiler.enabled = 1
mariadb_profiler.log_dir = /tmp/mariadb_profiler
mariadb_profiler.raw_log = 1
mariadb_profiler.job_check_interval = 1
mariadb_profiler.trace_depth = 10
```

| Setting | Default | Description |
|---------|---------|-------------|
| `mariadb_profiler.enabled` | `0` | Enable/disable profiling globally |
| `mariadb_profiler.log_dir` | `/tmp/mariadb_profiler` | Directory for log files |
| `mariadb_profiler.raw_log` | `1` | Write raw text logs |
| `mariadb_profiler.job_check_interval` | `1` | Seconds between job file checks |
| `mariadb_profiler.trace_depth` | `0` | Stack frames to capture (0 = disabled) |

### CLI tool

```bash
make cli-install
composer install
```

Optionally create a symlink:

```bash
ln -sf $(pwd)/cli/mariadb_profiler.php /usr/local/bin/mariadb-profiler
```

## PHP API

### `mariadb_profiler_tag(string $tag): bool`

Push a context tag onto the stack. Subsequent queries are logged with this tag.

```php
mariadb_profiler_tag('user_registration');
// ... queries here are tagged as "user_registration" ...
```

### `mariadb_profiler_untag([string $tag]): ?string`

Pop the top tag from the stack. If `$tag` is given, pops all tags down to and including the specified tag.

```php
mariadb_profiler_untag();                    // pop top
mariadb_profiler_untag('user_registration'); // pop until match
```

### `mariadb_profiler_get_tag(): ?string`

Get the current (top) tag without removing it.

```php
$current = mariadb_profiler_get_tag();
```

## CLI Usage

```bash
php cli/mariadb_profiler.php [--log-dir=PATH] [--tag=TAG] job <subcommand> [args]
```

| Subcommand | Description |
|------------|-------------|
| `job start <key>` | Start a profiling session |
| `job end <key>` | End a session |
| `job list` | List active and completed jobs |
| `job show <key> [--tag=TAG]` | Display parsed queries, optionally filtered by tag |
| `job raw <key>` | Show raw log file |
| `job export <key>` | Export parsed data to `.parsed.json` |
| `job tags <key>` | Show tag summary with counts |
| `job callers <key>` | Show caller (backtrace) summary |
| `job purge` | Delete all completed job data |

## Log Formats

### Raw log (`.raw.log`)

```
[2025-01-23 10:00:01.000] [ok] [user_registration] SELECT * FROM users WHERE id = 1
  params: [1]
  <- UserController->register() /app/Controllers/UserController.php:42
```

### JSONL (`.jsonl`)

```json
{"k":"job-uuid","q":"SELECT * FROM users","ts":1700000001.0,"tag":"user_registration","trace":[{"call":"UserController->register","file":"/app/Controllers/UserController.php","line":42}]}
```

## Demo

A Docker Compose environment is included under `demo/` with MariaDB 11, PHP 8.3-FPM, Nginx, and a WebSocket server for real-time log streaming.

```bash
cd demo
docker compose up --build
```

Access the demo at `http://localhost:8080`.

## Project Structure

```
ext/mariadb_profiler/   # C extension source
cli/                    # CLI tool (PHP)
demo/                   # Docker Compose demo application
tests/                  # Test suite
Makefile                # Build orchestration
composer.json           # PHP dependencies
```
