#!/usr/bin/env php
<?php

/**
 * MariaDB Query Profiler - CLI Tool
 *
 * Usage:
 *   php mariadb_profiler.php job start <key>
 *   php mariadb_profiler.php job end <key>
 *   php mariadb_profiler.php job list
 *   php mariadb_profiler.php job show <key> [--tag=<tag>]  # Show parsed queries (with table/column extraction)
 *   php mariadb_profiler.php job raw <key>                  # Show raw log
 *   php mariadb_profiler.php job export <key>               # Export parsed JSON to file
 *   php mariadb_profiler.php job tags <key>                 # Show tag summary
 *   php mariadb_profiler.php job callers <key>              # Show caller summary
 *   php mariadb_profiler.php job purge                      # Remove all completed job data
 */

// Find autoloader
$autoloadPaths = [
    __DIR__ . '/../vendor/autoload.php',
    __DIR__ . '/../../vendor/autoload.php',
    __DIR__ . '/../../../autoload.php',
];

$autoloaded = false;
foreach ($autoloadPaths as $path) {
    if (file_exists($path)) {
        require_once $path;
        $autoloaded = true;
        break;
    }
}

if (!$autoloaded) {
    fwrite(STDERR, "[ERROR] Cannot find composer autoloader. Run 'composer install' first.\n");
    exit(1);
}

use MariadbProfiler\JobManager;
use MariadbProfiler\SqlAnalyzer;

// Parse arguments
$args = array_slice($argv, 1);

if (empty($args)) {
    showUsage();
    exit(0);
}

// Check for --log-dir and --tag options
$logDir = null;
$tagFilter = null;
$filteredArgs = [];
for ($i = 0; $i < count($args); $i++) {
    if ($args[$i] === '--log-dir' && isset($args[$i + 1])) {
        $logDir = $args[$i + 1];
        $i++;
    } elseif (strpos($args[$i], '--log-dir=') === 0) {
        $logDir = substr($args[$i], strlen('--log-dir='));
    } elseif ($args[$i] === '--tag' && isset($args[$i + 1])) {
        $tagFilter = $args[$i + 1];
        $i++;
    } elseif (strpos($args[$i], '--tag=') === 0) {
        $tagFilter = substr($args[$i], strlen('--tag='));
    } else {
        $filteredArgs[] = $args[$i];
    }
}
$args = $filteredArgs;

$command = isset($args[0]) ? $args[0] : '';
$subCommand = isset($args[1]) ? $args[1] : '';
$key = isset($args[2]) ? $args[2] : '';

if ($command !== 'job') {
    fwrite(STDERR, "[ERROR] Unknown command: {$command}\n");
    showUsage();
    exit(1);
}

$manager = new JobManager($logDir);

switch ($subCommand) {
    case 'start':
        cmdJobStart($manager, $key);
        break;
    case 'end':
        cmdJobEnd($manager, $key);
        break;
    case 'list':
        cmdJobList($manager);
        break;
    case 'show':
        cmdJobShow($manager, $key, $tagFilter);
        break;
    case 'raw':
        cmdJobRaw($manager, $key);
        break;
    case 'export':
        cmdJobExport($manager, $key);
        break;
    case 'tags':
        cmdJobTags($manager, $key);
        break;
    case 'callers':
        cmdJobCallers($manager, $key);
        break;
    case 'purge':
        cmdJobPurge($manager);
        break;
    default:
        fwrite(STDERR, "[ERROR] Unknown sub-command: {$subCommand}\n");
        showUsage();
        exit(1);
}

// ============================================================================
// Command implementations
// ============================================================================

function cmdJobStart(JobManager $manager, $key)
{
    if ($key === '') {
        // Auto-generate UUID if not provided
        $key = generateUuid();
        fwrite(STDOUT, "[INFO] Generated job key: {$key}\n");
    }

    if ($manager->startJob($key)) {
        fwrite(STDOUT, "[OK] Job '{$key}' started.\n");
        fwrite(STDOUT, "     Log dir: {$manager->getLogDir()}\n");
    } else {
        exit(1);
    }
}

function cmdJobEnd(JobManager $manager, $key)
{
    if ($key === '') {
        fwrite(STDERR, "[ERROR] Job key is required.\n");
        exit(1);
    }

    $count = $manager->endJob($key);
    if ($count !== false) {
        fwrite(STDOUT, "[OK] Job '{$key}' ended. {$count} queries captured.\n");
    } else {
        exit(1);
    }
}

function cmdJobList(JobManager $manager)
{
    $all = $manager->listAllJobs();

    $activeJobs = $all['active'];
    $completedJobs = $all['completed'];

    if (empty($activeJobs) && empty($completedJobs)) {
        fwrite(STDOUT, "No jobs found.\n");
        return;
    }

    if (!empty($activeJobs)) {
        fwrite(STDOUT, "ACTIVE JOBS:\n");
        foreach ($activeJobs as $key => $info) {
            $started = date('Y-m-d H:i:s', (int)(isset($info['started_at']) ? $info['started_at'] : 0));
            $parent = isset($info['parent']) ? $info['parent'] : '-';
            fwrite(STDOUT, "  {$key}  started: {$started}  parent: {$parent}\n");
        }
    }

    if (!empty($completedJobs)) {
        fwrite(STDOUT, "\nCOMPLETED JOBS:\n");
        foreach ($completedJobs as $key => $info) {
            $started = date('Y-m-d H:i:s', (int)(isset($info['started_at']) ? $info['started_at'] : 0));
            $ended = date('Y-m-d H:i:s', (int)(isset($info['ended_at']) ? $info['ended_at'] : 0));
            $count = isset($info['query_count']) ? $info['query_count'] : 0;
            fwrite(STDOUT, "  {$key}  started: {$started}  ended: {$ended}  queries: {$count}\n");
        }
    }
}

function cmdJobShow(JobManager $manager, $key, $tagFilter = null)
{
    if ($key === '') {
        fwrite(STDERR, "[ERROR] Job key is required.\n");
        exit(1);
    }

    $queries = $manager->getJobQueries($key);

    if (empty($queries)) {
        fwrite(STDOUT, "No queries found for job '{$key}'.\n");
        return;
    }

    $analyzer = new SqlAnalyzer();

    foreach ($queries as $entry) {
        $sql = isset($entry['q']) ? $entry['q'] : '';
        if ($sql === '') {
            continue;
        }

        // Apply tag filter
        $entryTag = isset($entry['tag']) ? $entry['tag'] : null;
        if ($tagFilter !== null && $entryTag !== $tagFilter) {
            continue;
        }

        $result = $analyzer->analyze($sql);

        $output = [
            'k' => $key,
            'q' => $sql,
            't' => $result['tables'],
            'c' => $result['columns'],
        ];

        // Include tag if present
        if ($entryTag !== null) {
            $output['tag'] = $entryTag;
        }

        // Include trace if present
        if (isset($entry['trace']) && !empty($entry['trace'])) {
            $output['trace'] = $entry['trace'];
        }

        fwrite(STDOUT, json_encode($output, JSON_UNESCAPED_UNICODE) . "\n");
    }
}

function cmdJobRaw(JobManager $manager, $key)
{
    if ($key === '') {
        fwrite(STDERR, "[ERROR] Job key is required.\n");
        exit(1);
    }

    $raw = $manager->getRawLog($key);

    if ($raw === null) {
        fwrite(STDOUT, "No raw log found for job '{$key}'.\n");
        return;
    }

    fwrite(STDOUT, $raw);
}

function cmdJobExport(JobManager $manager, $key)
{
    if ($key === '') {
        fwrite(STDERR, "[ERROR] Job key is required.\n");
        exit(1);
    }

    $queries = $manager->getJobQueries($key);

    if (empty($queries)) {
        fwrite(STDERR, "[ERROR] No queries found for job '{$key}'.\n");
        exit(1);
    }

    $analyzer = new SqlAnalyzer();
    $parsed = [];

    foreach ($queries as $entry) {
        $sql = isset($entry['q']) ? $entry['q'] : '';
        if ($sql === '') {
            continue;
        }

        $result = $analyzer->analyze($sql);

        $item = [
            'k' => $key,
            'q' => $sql,
            't' => $result['tables'],
            'c' => $result['columns'],
            'ts' => isset($entry['ts']) ? $entry['ts'] : null,
        ];

        // Include tag if present
        if (isset($entry['tag'])) {
            $item['tag'] = $entry['tag'];
        }

        // Include trace if present
        if (isset($entry['trace']) && !empty($entry['trace'])) {
            $item['trace'] = $entry['trace'];
        }

        $parsed[] = $item;
    }

    // Write parsed JSON file
    $parsedFile = $manager->getLogDir() . '/' . $key . '.parsed.json';
    file_put_contents(
        $parsedFile,
        json_encode($parsed, JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE) . "\n"
    );
    fwrite(STDOUT, "[OK] Parsed export: {$parsedFile}\n");

    // Report raw log path
    $rawFile = $manager->getLogDir() . '/' . $key . '.raw.log';
    if (file_exists($rawFile)) {
        fwrite(STDOUT, "[OK] Raw log:      {$rawFile}\n");
    }

    // Report JSONL path
    $jsonlFile = $manager->getLogDir() . '/' . $key . '.jsonl';
    if (file_exists($jsonlFile)) {
        fwrite(STDOUT, "[OK] Query log:    {$jsonlFile}\n");
    }
}

function cmdJobTags(JobManager $manager, $key)
{
    if ($key === '') {
        fwrite(STDERR, "[ERROR] Job key is required.\n");
        exit(1);
    }

    $summary = $manager->getTagSummary($key);

    if (empty($summary)) {
        fwrite(STDOUT, "No queries found for job '{$key}'.\n");
        return;
    }

    // Sort by count descending
    arsort($summary);

    fwrite(STDOUT, sprintf("%-40s %s\n", "TAG", "QUERIES"));
    fwrite(STDOUT, str_repeat('-', 50) . "\n");

    foreach ($summary as $tag => $count) {
        fwrite(STDOUT, sprintf("%-40s %d\n", $tag, $count));
    }
}

function cmdJobCallers(JobManager $manager, $key)
{
    if ($key === '') {
        fwrite(STDERR, "[ERROR] Job key is required.\n");
        exit(1);
    }

    $summary = $manager->getCallerSummary($key);

    if (empty($summary)) {
        fwrite(STDOUT, "No trace data found for job '{$key}'.\n");
        return;
    }

    // Sort by count descending
    arsort($summary);

    fwrite(STDOUT, sprintf("%-60s %s\n", "CALLER", "QUERIES"));
    fwrite(STDOUT, str_repeat('-', 70) . "\n");

    foreach ($summary as $caller => $count) {
        fwrite(STDOUT, sprintf("%-60s %d\n", $caller, $count));
    }
}

function cmdJobPurge(JobManager $manager)
{
    $count = $manager->purgeCompleted();
    fwrite(STDOUT, "[OK] Purged {$count} completed jobs.\n");
}

// ============================================================================
// Helpers
// ============================================================================

function generateUuid()
{
    if (function_exists('random_bytes')) {
        $data = random_bytes(16);
    } elseif (function_exists('openssl_random_pseudo_bytes')) {
        $data = openssl_random_pseudo_bytes(16);
    } else {
        $data = '';
        for ($i = 0; $i < 16; $i++) {
            $data .= chr(mt_rand(0, 255));
        }
    }
    $data[6] = chr(ord($data[6]) & 0x0f | 0x40); // Version 4
    $data[8] = chr(ord($data[8]) & 0x3f | 0x80); // Variant
    return vsprintf('%s%s-%s-%s-%s-%s%s%s', str_split(bin2hex($data), 4));
}

function showUsage()
{
    $usage = <<<'USAGE'
MariaDB Query Profiler - CLI Tool

Usage:
  php mariadb_profiler.php [--log-dir=<path>] job <command> [<key>] [options]

Commands:
  job start [<key>]    Start a profiling job (auto-generates key if omitted)
  job end <key>        End a profiling job
  job list             List all jobs (active and completed)
  job show <key>       Show parsed queries with table/column extraction
  job raw <key>        Show raw query log
  job export <key>     Export parsed JSON + raw log to files
  job tags <key>       Show tag summary (query count per context tag)
  job callers <key>    Show caller summary (query count per call site)
  job purge            Remove all completed job data

Options:
  --log-dir=<path>     Override log directory (default: from php.ini or /tmp/mariadb_profiler)
  --tag=<tag>          Filter queries by context tag (for 'show' command)

Examples:
  php mariadb_profiler.php job start my-trace-001
  php mariadb_profiler.php job end my-trace-001
  php mariadb_profiler.php job show my-trace-001
  php mariadb_profiler.php job show my-trace-001 --tag=user_registration
  php mariadb_profiler.php job tags my-trace-001
  php mariadb_profiler.php job callers my-trace-001
  php mariadb_profiler.php job export my-trace-001

USAGE;
    fwrite(STDOUT, $usage);
}
