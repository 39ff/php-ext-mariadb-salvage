# php-ext-mariadb-salvage

```mermaid
flowchart LR
    subgraph APP[PHP Application Runtime]
        PHP[PHP App using mysqli/PDO]
        EXT[mariadb_profiler extension\n(mysqlnd hook)]
        PHP --> EXT
    end

    subgraph LOGS[Shared Log Directory]
        JOBS[jobs.json\nactive/completed jobs]
        JSONL[<jobKey>.jsonl\nstructured query logs]
        RAW[<jobKey>.raw.log\nhuman-readable logs]
    end

    EXT --> JOBS
    EXT --> JSONL
    EXT --> RAW

    subgraph TOOLING[Developer Tooling]
        CLI[CLI: cli/mariadb_profiler.php\nstart/end/list/show/export]
        JB[JetBrains Plugin\nviewer, stats, live tail]
        DEMO[Docker Demo (Laravel + Nginx + WS)]
    end

    CLI <--> JOBS
    CLI <--> JSONL
    CLI <--> RAW

    JB <--> JOBS
    JB <--> JSONL
    JB <--> RAW

    DEMO <--> JOBS
    DEMO <--> JSONL
    DEMO <--> RAW
```

`php-ext-mariadb-salvage` is a MariaDB/MySQL query profiling project composed of:

- a PHP extension (`mysqlnd` plugin) that captures executed queries,
- a CLI to manage profiling jobs and inspect logs,
- a JetBrains plugin for IDE-side visualization,
- and a Docker demo environment for browser-based exploration.

## Key Capabilities

### PHP Extension (`ext/mariadb_profiler`)
- Hooks into `mysqlnd` query execution.
- Tracks active profiling jobs via `jobs.json`.
- Writes per-job logs:
  - `<jobKey>.jsonl` (structured, machine-readable)
  - `<jobKey>.raw.log` (plain-text, human-readable)
- Supports runtime tagging and optional PHP backtrace capture.

### CLI (`cli/mariadb_profiler.php`)
- Start/end profiling jobs.
- List active/completed jobs.
- Show parsed logs, raw logs, tag summaries, caller summaries.
- Export parsed output.
- Purge completed jobs.

### JetBrains Plugin (`jetbrains-plugin/`)
- Query log inspection inside IDEs.
- Job browsing.
- Statistics and live-tail style monitoring.

### Demo (`demo/`)
- Docker-based stack (Laravel + Nginx + MariaDB + WebSocket)
- Start/stop profiling sessions from the browser.
- Real-time log streaming.

## Repository Layout

```text
.
├── ext/mariadb_profiler/    # PHP extension source
├── cli/                     # CLI source and entrypoint
├── tests/                   # PHP tests for analyzer/job flow
├── jetbrains-plugin/        # IntelliJ/PhpStorm plugin project
├── demo/                    # Docker demo app and environment
├── composer.json
└── Makefile
```

## Requirements

- PHP with extension build toolchain
- `phpize`, `php-config`, `make`, C compiler
- Composer
- Optional: Docker / Docker Compose (for demo)
- Optional: Java + Gradle (for JetBrains plugin work)

## Setup

### 1) Install dependencies

```bash
composer install
```

### 2) Build extension

```bash
make ext-build
```

### 3) Install extension

```bash
make ext-install
```

After installation, apply the printed configuration snippet to your `php.ini`.

## Recommended `php.ini` Settings

```ini
extension=mariadb_profiler.so
mariadb_profiler.enabled = 1
mariadb_profiler.log_dir = /tmp/mariadb_profiler
mariadb_profiler.raw_log = 1
mariadb_profiler.job_check_interval = 1
mariadb_profiler.trace_depth = 0
```

- `trace_depth = 0`: disable backtrace capture.
- `trace_depth = N`: capture up to N frames for each query.

## CLI Usage

Entrypoint:

```bash
php cli/mariadb_profiler.php
```

Common commands:

```bash
# Start a job (auto-generates key when omitted)
php cli/mariadb_profiler.php job start <key>

# End a job
php cli/mariadb_profiler.php job end <key>

# List jobs
php cli/mariadb_profiler.php job list

# Show parsed logs (optional tag filter)
php cli/mariadb_profiler.php job show <key> [--tag=<tag>]

# Show raw log
php cli/mariadb_profiler.php job raw <key>

# Export parsed output
php cli/mariadb_profiler.php job export <key>

# Tag and caller summaries
php cli/mariadb_profiler.php job tags <key>
php cli/mariadb_profiler.php job callers <key>

# Purge all completed jobs
php cli/mariadb_profiler.php job purge
```

Use `--log-dir` to override the default log directory.

## PHP Tagging API

```php
<?php
mariadb_profiler_tag('checkout');

// Queries in this context will include tag=checkout

mariadb_profiler_untag();
$current = mariadb_profiler_get_tag(); // null or current tag
```

## Testing

```bash
make test
```

Optional extension-level tests:

```bash
make test-extension
```

## Run the Demo UI

```bash
cd demo
docker compose up --build
```

Open `http://localhost:8080` and use Start/Stop plus Demo Queries to validate behavior.

## JetBrains Plugin Tests

```bash
cd jetbrains-plugin
./gradlew test
```
