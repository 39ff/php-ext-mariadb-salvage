# MariaDB Query Profiler for PHP

A PHP extension that transparently intercepts and logs all database queries executed through MySQLnd. It works with any PHP code using PDO or mysqli without requiring code changes.

## Features

- **Transparent Query Interception** - Hooks into MySQLnd to capture all queries (PDO, mysqli) with zero application changes
- **Job-Based Profiling** - Start and stop profiling sessions independently; multiple sessions can run concurrently
- **Context Tagging** - Tag queries with business context (e.g., "UserUpdate", "OrderProcessing") using a stack-based API
- **PHP Backtrace Capture** - Record the PHP call stack at the point of each query execution
- **Multiple Output Formats** - Raw text logs (`.raw.log`) and structured JSON Lines (`.jsonl`)
- **SQL Analysis** - CLI tool parses captured queries to extract table names, column names, and operation types
- **Wide PHP Version Support** - Compatible with PHP 5.3 through 8.4+

## Requirements

- PHP 5.3.2 or later with MySQLnd enabled
- C compiler (gcc or clang)
- autoconf / automake
- phpize
- Composer (for CLI tool dependencies)

## Installation

### Build the Extension

```bash
# Install Composer dependencies
make composer

# Build the extension
make ext-build

# Install the extension .so
make ext-install
```

### Configure php.ini

Add the following to your `php.ini`:

```ini
extension=mariadb_profiler.so
mariadb_profiler.enabled = 1
mariadb_profiler.log_dir = /tmp/mariadb_profiler
mariadb_profiler.raw_log = 1
mariadb_profiler.job_check_interval = 1
mariadb_profiler.trace_depth = 0
```

| Directive | Default | Description |
|---|---|---|
| `mariadb_profiler.enabled` | `0` | Enable or disable the profiler |
| `mariadb_profiler.log_dir` | `/tmp/mariadb_profiler` | Directory for log output |
| `mariadb_profiler.raw_log` | `0` | Write plain-text `.raw.log` files |
| `mariadb_profiler.job_check_interval` | `1` | Seconds between job file checks |
| `mariadb_profiler.trace_depth` | `0` | Stack trace depth (0 = disabled) |

### Install the CLI Tool (Optional)

```bash
make install

# Or create a symlink manually:
ln -sf $(pwd)/cli/mariadb_profiler.php /usr/local/bin/mariadb-profiler
```

## Usage

### Starting a Profiling Session

Use the CLI tool to manage profiling jobs:

```bash
# Start a profiling session
php cli/mariadb_profiler.php job start my-session

# Run your application / execute queries...

# Stop the session
php cli/mariadb_profiler.php job end my-session
```

### Viewing Results

```bash
# List all jobs
php cli/mariadb_profiler.php job list

# Show parsed queries
php cli/mariadb_profiler.php job show my-session

# Show parsed queries filtered by tag
php cli/mariadb_profiler.php job show my-session --tag=UserUpdate

# Show raw log
php cli/mariadb_profiler.php job raw my-session

# Export as JSON
php cli/mariadb_profiler.php job export my-session

# Tag summary
php cli/mariadb_profiler.php job tags my-session

# Caller summary (file:line)
php cli/mariadb_profiler.php job callers my-session

# Clean up completed jobs
php cli/mariadb_profiler.php job purge
```

### Context Tagging in Application Code

Tag queries from within your PHP application to group them by business context:

```php
// Push a context tag
mariadb_profiler_tag("UserRegistration");

// All queries executed here will be tagged with "UserRegistration"
$pdo->query("INSERT INTO users ...");
$pdo->query("INSERT INTO user_settings ...");

// Remove the tag
mariadb_profiler_untag("UserRegistration");

// Get the current tag
$current = mariadb_profiler_get_tag();
```

Tags are stack-based (up to 64 levels deep), so nested tags work as expected.

## Project Structure

```
├── ext/mariadb_profiler/   # PHP C extension (MySQLnd plugin)
│   ├── mariadb_profiler.c  # Module lifecycle, INI settings, PHP functions
│   ├── profiler_mysqlnd_plugin.c  # MySQLnd hook implementation
│   ├── profiler_job.c      # Job state management
│   ├── profiler_log.c      # Query log writer
│   ├── profiler_tag.c      # Context tag stack
│   └── profiler_trace.c    # PHP backtrace capture
├── cli/                    # CLI tool
│   ├── mariadb_profiler.php
│   └── src/
│       ├── JobManager.php  # Job lifecycle management
│       └── SqlAnalyzer.php # SQL parsing and metadata extraction
├── demo/                   # Docker Compose demo with Laravel
├── tests/                  # Unit and integration tests
└── Makefile
```

## Demo Application

A full Docker Compose demo is included in the `demo/` directory. It runs a Laravel application with the profiler extension, a MariaDB instance, and a WebSocket server for real-time log streaming.

```bash
cd demo
docker compose up --build
# Open http://localhost:8080
```

See [demo/README.md](demo/README.md) for details.

## Testing

```bash
# Run unit tests
make test

# Run extension tests (requires built extension)
make test-extension
```

## How It Works

The extension registers as a [MySQLnd plugin](https://www.php.net/manual/en/mysqlnd.plugin.php) and intercepts the `query()`, `send_query()`, and `prepare()` methods on every MySQLnd connection. When a profiling job is active, each intercepted query is written to the job's log files along with metadata (timestamp, context tag, backtrace). The original query is always forwarded to the database unmodified.

Job state is coordinated through a shared `jobs.json` file with file locking, allowing the CLI tool and PHP-FPM worker processes to communicate without a separate daemon.

## License

MIT
