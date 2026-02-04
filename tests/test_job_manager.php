#!/usr/bin/env php
<?php

declare(strict_types=1);

/**
 * Test suite for JobManager
 */

require_once __DIR__ . '/../vendor/autoload.php';

use MariadbProfiler\JobManager;

$testDir = sys_get_temp_dir() . '/mariadb_profiler_test_' . getmypid();
$passed = 0;
$failed = 0;

function assert_true(string $name, bool $condition): void
{
    global $passed, $failed;
    if ($condition) {
        echo "[PASS] {$name}\n";
        $passed++;
    } else {
        echo "[FAIL] {$name}\n";
        $failed++;
    }
}

function cleanup(string $dir): void
{
    if (!is_dir($dir)) {
        return;
    }
    $files = glob($dir . '/*');
    foreach ($files as $file) {
        if (is_file($file)) {
            unlink($file);
        }
    }
    rmdir($dir);
}

echo "=== JobManager Test Suite ===\n\n";

// Setup
cleanup($testDir);
$manager = new JobManager($testDir);

// Test: Start a job
$result = $manager->startJob('test-001');
assert_true('Start job test-001', $result === true);

// Test: Job appears in active list
$active = $manager->listActiveJobs();
assert_true('Job test-001 is active', isset($active['test-001']));

// Test: Start nested job
$result = $manager->startJob('test-002');
assert_true('Start nested job test-002', $result === true);

$active = $manager->listActiveJobs();
assert_true('Both jobs are active', count($active) === 2);
assert_true('test-002 parent is test-001', ($active['test-002']['parent'] ?? '') === 'test-001');

// Test: Cannot start duplicate job
$result = $manager->startJob('test-001');
assert_true('Duplicate job start fails', $result === false);

// Test: Simulate query logs by writing to JSONL file
$jsonlFile = $testDir . '/test-002.jsonl';
$entries = [
    '{"k":"test-002","q":"SELECT id, name FROM users","ts":1700000001.123}',
    '{"k":"test-002","q":"SELECT u.name, o.total FROM users u JOIN orders o ON u.id = o.user_id","ts":1700000002.456}',
];
file_put_contents($jsonlFile, implode("\n", $entries) . "\n");

// Test: End job
$count = $manager->endJob('test-002');
assert_true('End job test-002 returns query count', $count === 2);

// Test: Job moved to completed
$active = $manager->listActiveJobs();
$completed = $manager->listCompletedJobs();
assert_true('test-002 no longer active', !isset($active['test-002']));
assert_true('test-002 is completed', isset($completed['test-002']));
assert_true('test-002 query count recorded', ($completed['test-002']['query_count'] ?? 0) === 2);

// Test: Get queries
$queries = $manager->getJobQueries('test-002');
assert_true('Get queries returns 2 entries', count($queries) === 2);
assert_true('First query correct', ($queries[0]['q'] ?? '') === 'SELECT id, name FROM users');

// Test: End remaining job
$manager->endJob('test-001');
$active = $manager->listActiveJobs();
assert_true('No active jobs after ending all', count($active) === 0);

// Test: Purge
$purged = $manager->purgeCompleted();
assert_true('Purge returns count', $purged === 2);
$completed = $manager->listCompletedJobs();
assert_true('No completed jobs after purge', count($completed) === 0);

// Cleanup
cleanup($testDir);

echo "\n=== Results: {$passed} passed, {$failed} failed ===\n";
exit($failed > 0 ? 1 : 0);
