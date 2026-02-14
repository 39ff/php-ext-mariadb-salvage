# MariaDB Query Profiler - Demo

Web-based demo for the `php-ext-mariadb-salvage` query profiler extension.

## Quick Start

```bash
cd demo
docker compose up --build
```

Open http://localhost:8080 in your browser.

## Usage

1. **Start Session** - Click to begin a profiling session. A new tab appears with a live log terminal.
2. **Run Demo Queries** - Executes sample queries (SELECT, JOIN, INSERT, UPDATE) against the demo database.
3. **Stop** - Stops the profiling session. The captured query count is displayed.

Each session tab shows the raw query log in real-time via xterm.js.

## Architecture

```
Browser (:8080)
  |
  +-- HTTP --> Nginx --> PHP-FPM (Laravel + mariadb_profiler.so)
  |                        |
  +-- WebSocket --> Nginx --> Node.js (tail -f logs)
                                |
                          Shared Volume (/var/profiler)
                                |
                          MariaDB 11
```

## Services

| Service   | Role                                    | Port |
|-----------|-----------------------------------------|------|
| nginx     | Reverse proxy (HTTP + WebSocket)        | 8080 |
| app       | PHP-FPM with extension + Laravel        | 9000 |
| websocket | Node.js WebSocket server for log stream | 3000 |
| mariadb   | MariaDB 11 database                     | 3306 |

## Cleanup

```bash
docker compose down -v
```
