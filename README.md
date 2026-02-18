# MariaDB Profiler for PHP

A MariaDB/MySQL query profiler that runs as a PHP extension. It hooks into PHP's `mysqlnd` driver to intercept, record, and analyze all executed SQL queries.

Works with any database access method that uses mysqlnd, including PDO, mysqli, and Laravel Eloquent.

## IntelliJ Plugin Ready
<img width="4012" height="2800" alt="ss0" src="https://github.com/user-attachments/assets/a38dbc5f-69ee-4f48-9aa1-e3fb0eb07de8" /><BR>
<img width="3996" height="2436" alt="ss1" src="https://github.com/user-attachments/assets/460b75a9-996e-4c20-8e6f-818b4550f5d6" /><BR>
<img width="3996" height="2436" alt="ss2" src="https://github.com/user-attachments/assets/78388037-728d-477b-bfba-1c3f75e0e1b0" />



## Components

| Component | Description |
|---|---|
| `ext/mariadb_profiler/` | PHP extension (C) |
| `cli/` | CLI profiler management tool (PHP) |
| `demo/` | Docker-based web demo (Laravel + WebSocket) |
| `jetbrains-plugin/` | JetBrains IDE plugin (Kotlin) |

## Features

- **Query interception** — Captures all SQL queries at the mysqlnd level
- **Context tags** — Stack-based tags to group queries by business logic
- **PHP backtrace** — Records call stacks at configurable depth
- **Prepared statement support** — Logs bound parameters (PHP 7.0+)
- **SQL analysis** — Automatic extraction of table and column names
- **Job management** — Concurrent profiling sessions with parent-child relationships
- **Cross-platform** — Linux / macOS / Windows

## Comparison with Similar Tools

This project sits between application performance monitoring (APM) tools and DB log analyzers.

| Category | Representative Tools | Strengths | Differences from this project |
|---|---|---|---|
| PHP APM / Profiler | Tideways, Blackfire, New Relic, Datadog APM | End-to-end tracing, rich dashboards, broad performance visibility | Usually SaaS-centric or full-stack focused; not specialized for local mysqlnd-level SQL capture with per-job CLI workflow |
| DB Log Analysis | Percona Toolkit (`pt-query-digest`), MariaDB/MySQL slow query log analysis | Strong query aggregation and optimization insights | Relies on DB-side logs; does not natively include PHP call stacks or in-app context tags |
| mysqlnd Hooking (low-level) | mysqlnd userland/handler approaches (e.g. mysqlnd_uh-style extensions) | Flexible low-level interception in mysqlnd layer | Often framework/tooling primitives; not an integrated profiler workflow with tagging + job lifecycle + JSONL export |

### Why this project is different

- Captures SQL directly at PHP `mysqlnd` layer used by PDO / mysqli / Eloquent
- Adds optional PHP backtrace context to each query
- Supports explicit business-context tagging via `mariadb_profiler_tag()` stack
- Uses job-oriented CLI operations (`start`, `end`, `show`, `export`, `tags`, `callers`)
- Can be used fully locally without external SaaS dependencies

## When You Should Use This Project

Use **MariaDB Profiler for PHP** when one or more of the following apply:

1. You need SQL-level visibility with **PHP call-site context** (which file/function triggered the query).
2. You want to profile only a specific workflow/request window using **start/end job control**.
3. You need to group queries by business flow (checkout, import, batch, etc.) using **tag stack APIs**.
4. You want **self-hosted/local-first** profiling without sending telemetry to external services.
5. You need logs that are easy to post-process (`raw.log` + structured `jsonl`) in CI or custom tooling.

### Prefer alternatives when...

- You need broad distributed tracing across many services out of the box → prefer APM tools.
- You only need DB-wide slow query aggregation from server logs → prefer `pt-query-digest` style workflows.
- You mainly need CPU/memory/function-level profiling beyond SQL behavior → prefer general PHP profilers.

## Requirements

| Component | Requirements |
|---|---|
| Extension | PHP 5.3 – 8.4+, mysqlnd |
| CLI tool | PHP 5.3+, Composer |
| Demo | Docker, Docker Compose |

## Installation

### Building the Extension

```bash
cd ext/mariadb_profiler
phpize
./configure --enable-mariadb_profiler
make
sudo make install
```

Add the following to php.ini:

```ini
extension=mariadb_profiler.so
mariadb_profiler.enabled=1
mariadb_profiler.log_dir=/var/log/mariadb_profiler
```

### CLI Tool

```bash
composer install
```

## Configuration (php.ini)

```ini
mariadb_profiler.enabled = 1            ; Enable the extension
mariadb_profiler.log_dir = /tmp/mariadb_profiler  ; Log output directory
mariadb_profiler.raw_log = 1            ; Write raw text logs
mariadb_profiler.job_check_interval = 1 ; Interval to check jobs.json (seconds)
mariadb_profiler.trace_depth = 0        ; Backtrace depth (0 = disabled)
```

## Usage

### Managing Profiling Jobs

```bash
# Start a job
php cli/mariadb_profiler.php job start [<key>]

# End a job
php cli/mariadb_profiler.php job end <key>

# List jobs
php cli/mariadb_profiler.php job list

# Show parsed queries
php cli/mariadb_profiler.php job show <key> [--tag=<tag>]

# Show raw log
php cli/mariadb_profiler.php job raw <key>

# Export as JSON
php cli/mariadb_profiler.php job export <key>

# Show tag summary
php cli/mariadb_profiler.php job tags <key>

# Show caller summary
php cli/mariadb_profiler.php job callers <key>

# Purge completed jobs
php cli/mariadb_profiler.php job purge
```

### Tagging Queries in PHP

```php
// Push a tag
mariadb_profiler_tag('checkout_flow');

// Queries executed here are tagged with 'checkout_flow'
$db->query('SELECT * FROM orders WHERE user_id = ?');

// Get the current tag
$tag = mariadb_profiler_get_tag(); // 'checkout_flow'

// Pop the tag
mariadb_profiler_untag();
```

### Demo

```bash
cd demo
docker compose up --build
# Open http://localhost:8080
```

## PHP Function Reference

| Function | Description |
|---|---|
| `mariadb_profiler_tag(string $tag): void` | Push a context tag onto the stack |
| `mariadb_profiler_untag(?string $tag = null): ?string` | Pop a tag (optionally unwind to a specific tag) |
| `mariadb_profiler_get_tag(): ?string` | Get the current tag (null if none) |

## Log Formats

Two files are generated per job:

- `{job_key}.raw.log` — One query per line in text format (with timestamp, status, tag, and trace)
- `{job_key}.jsonl` — Parsed JSON format with extracted table and column names
