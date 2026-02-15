#!/usr/bin/env php
<?php

/**
 * Integration test - simulates the full job workflow using the CLI tool.
 * This test does NOT require the C extension (tests CLI + PHPSQLParser only).
 */

// Polyfill for PHP < 8.0
function str_contains_compat($haystack, $needle)
{
    return $needle === '' || strpos($haystack, $needle) !== false;
}

$cliTool = __DIR__ . '/../cli/mariadb_profiler.php';
$testDir = sys_get_temp_dir() . '/mariadb_profiler_integ_' . getmypid();
$passed = 0;
$failed = 0;

function run($cmd)
{
    $output = [];
    $code = 0;
    exec($cmd . ' 2>&1', $output, $code);
    return ['output' => implode("\n", $output), 'code' => $code];
}

function assert_test($name, $condition, $detail = '')
{
    global $passed, $failed;
    if ($condition) {
        echo "[PASS] {$name}\n";
        $passed++;
    } else {
        echo "[FAIL] {$name}\n";
        if ($detail) {
            echo "  Detail: {$detail}\n";
        }
        $failed++;
    }
}

function cleanup($dir)
{
    if (!is_dir($dir)) {
        return;
    }
    $files = glob($dir . '/*');
    if (!$files) {
        $files = [];
    }
    foreach ($files as $file) {
        if (is_file($file)) {
            unlink($file);
        }
    }
    rmdir($dir);
}

echo "=== Integration Test Suite ===\n\n";

// Cleanup from previous runs
cleanup($testDir);

$base = "php {$cliTool} --log-dir={$testDir}";

// Start job uuid1
$r = run("{$base} job start uuid1");
assert_test('Start uuid1', str_contains_compat($r['output'], '[OK]'), $r['output']);

// Start job uuid2 (nested)
$r = run("{$base} job start uuid2");
assert_test('Start uuid2', str_contains_compat($r['output'], '[OK]'), $r['output']);

// List should show 2 active jobs
$r = run("{$base} job list");
assert_test('List shows uuid1', str_contains_compat($r['output'], 'uuid1'), $r['output']);
assert_test('List shows uuid2', str_contains_compat($r['output'], 'uuid2'), $r['output']);

// Simulate query log for uuid1 (as if the extension wrote them)
$queries1 = [
    '{"k":"uuid1","q":"SELECT u.name, u.email FROM users u WHERE u.active = 1","ts":1700000001.0}',
    '{"k":"uuid1","q":"SELECT u.name, o.total FROM users u JOIN orders o ON u.id = o.user_id WHERE o.status = \'active\'","ts":1700000002.0}',
    '{"k":"uuid1","q":"INSERT INTO logs (message, user_id, created_at) VALUES (\'login\', 1, NOW())","ts":1700000003.0}',
    '{"k":"uuid1","q":"UPDATE users SET last_login = NOW() WHERE id = 1","ts":1700000004.0}',
    '{"k":"uuid1","q":"DELETE FROM sessions WHERE expired_at < NOW()","ts":1700000005.0}',
];
file_put_contents($testDir . '/uuid1.jsonl', implode("\n", $queries1) . "\n");

// Simulate raw log for uuid1
$raw1 = <<<'RAW'
[2025-01-23 10:00:01.000] SELECT u.name, u.email FROM users u WHERE u.active = 1
[2025-01-23 10:00:02.000] SELECT u.name, o.total FROM users u JOIN orders o ON u.id = o.user_id WHERE o.status = 'active'
[2025-01-23 10:00:03.000] INSERT INTO logs (message, user_id, created_at) VALUES ('login', 1, NOW())
[2025-01-23 10:00:04.000] UPDATE users SET last_login = NOW() WHERE id = 1
[2025-01-23 10:00:05.000] DELETE FROM sessions WHERE expired_at < NOW()
RAW;
file_put_contents($testDir . '/uuid1.raw.log', $raw1);

// Simulate queries for uuid2
$queries2 = [
    '{"k":"uuid2","q":"SELECT p.name, p.price, c.name FROM products p INNER JOIN categories c ON p.category_id = c.id WHERE p.stock > 0","ts":1700000002.5}',
];
file_put_contents($testDir . '/uuid2.jsonl', implode("\n", $queries2) . "\n");

// End uuid2
$r = run("{$base} job end uuid2");
assert_test('End uuid2', str_contains_compat($r['output'], '[OK]') && str_contains_compat($r['output'], '1 queries'), $r['output']);

// Start uuid3
$r = run("{$base} job start uuid3");
assert_test('Start uuid3', str_contains_compat($r['output'], '[OK]'), $r['output']);

// End uuid3 (no queries)
$r = run("{$base} job end uuid3");
assert_test('End uuid3', str_contains_compat($r['output'], '[OK]'), $r['output']);

// End uuid1
$r = run("{$base} job end uuid1");
assert_test('End uuid1', str_contains_compat($r['output'], '[OK]') && str_contains_compat($r['output'], '5 queries'), $r['output']);

// Show parsed results for uuid1
$r = run("{$base} job show uuid1");
assert_test('Show uuid1 has output', strlen($r['output']) > 50, $r['output']);

// Parse each line and validate
$lines = array_filter(explode("\n", $r['output']), function($l) { return trim($l) !== ''; });
$lines = array_values($lines);
assert_test('Show uuid1 has 5 lines', count($lines) === 5, "Got " . count($lines) . " lines");

if (count($lines) >= 2) {
    $line2 = json_decode($lines[1], true);
    $tables2 = is_array($line2) && isset($line2['t']) ? $line2['t'] : [];
    $cols2 = is_array($line2) && isset($line2['c']) ? $line2['c'] : [];
    assert_test('Line 2 has tables users and orders',
        in_array('users', $tables2) && in_array('orders', $tables2),
        json_encode($tables2)
    );
    assert_test('Line 2 has column users.name',
        in_array('users.name', $cols2),
        json_encode($cols2)
    );
}

// Show parsed results for uuid2
$r = run("{$base} job show uuid2");
$lines = array_filter(explode("\n", $r['output']), function($l) { return trim($l) !== ''; });
$lines = array_values($lines);
if (count($lines) >= 1) {
    $line1 = json_decode($lines[0], true);
    $tables1 = is_array($line1) && isset($line1['t']) ? $line1['t'] : [];
    assert_test('uuid2 has tables products and categories',
        in_array('products', $tables1) && in_array('categories', $tables1),
        json_encode($tables1)
    );
}

// Raw log
$r = run("{$base} job raw uuid1");
assert_test('Raw log contains SELECT', str_contains_compat($r['output'], 'SELECT u.name'), $r['output']);

// Export
$r = run("{$base} job export uuid1");
assert_test('Export succeeds', str_contains_compat($r['output'], '[OK]'), $r['output']);
assert_test('Parsed file exists', file_exists($testDir . '/uuid1.parsed.json'));

if (file_exists($testDir . '/uuid1.parsed.json')) {
    $parsed = json_decode(file_get_contents($testDir . '/uuid1.parsed.json'), true);
    assert_test('Parsed JSON is valid array', is_array($parsed));
    $parsedCount = is_array($parsed) ? count($parsed) : 0;
    assert_test('Parsed JSON has 5 entries', $parsedCount === 5, "Got " . $parsedCount);
}

// List should show all completed
$r = run("{$base} job list");
assert_test('List shows COMPLETED section', str_contains_compat($r['output'], 'COMPLETED'), $r['output']);

// Purge
$r = run("{$base} job purge");
assert_test('Purge succeeds', str_contains_compat($r['output'], '[OK]'), $r['output']);

// ============================================================================
// Context Tag + Trace Tests
// ============================================================================
echo "\n--- Context Tag + Trace Tests ---\n\n";

// Start a new job for tag/trace testing
$r = run("{$base} job start tag-test");
assert_test('Start tag-test', str_contains_compat($r['output'], '[OK]'), $r['output']);

// Simulate JSONL with tags and traces (as if the C extension wrote them)
$tagQueries = [
    '{"k":"tag-test","q":"SELECT * FROM users WHERE id = 1","tag":"user_registration","trace":[{"call":"UserController->register","file":"/app/Http/Controllers/UserController.php","line":42}],"ts":1700000010.0}',
    '{"k":"tag-test","q":"INSERT INTO email_queue (user_id, type) VALUES (1, \'welcome\')","tag":"send_email","trace":[{"call":"UserController->register","file":"/app/Http/Controllers/UserController.php","line":50},{"call":"Router->dispatch","file":"/app/routes.php","line":10}],"ts":1700000011.0}',
    '{"k":"tag-test","q":"INSERT INTO audit_logs (action) VALUES (\'user_created\')","tag":"user_registration","trace":[{"call":"UserController->register","file":"/app/Http/Controllers/UserController.php","line":55}],"ts":1700000012.0}',
    '{"k":"tag-test","q":"SELECT COUNT(*) FROM users","ts":1700000013.0}',
    '{"k":"tag-test","q":"UPDATE users SET verified = 1 WHERE id = 1","tag":"user_registration","ts":1700000014.0}',
];
file_put_contents($testDir . '/tag-test.jsonl', implode("\n", $tagQueries) . "\n");

// Simulate raw log with tags and traces
$rawTagLog = <<<'RAW'
[2025-01-23 10:00:10.000] [user_registration] SELECT * FROM users WHERE id = 1
  <- UserController->register() /app/Http/Controllers/UserController.php:42
[2025-01-23 10:00:11.000] [send_email] INSERT INTO email_queue (user_id, type) VALUES (1, 'welcome')
  <- UserController->register() /app/Http/Controllers/UserController.php:50
  <- Router->dispatch() /app/routes.php:10
[2025-01-23 10:00:12.000] [user_registration] INSERT INTO audit_logs (action) VALUES ('user_created')
  <- UserController->register() /app/Http/Controllers/UserController.php:55
[2025-01-23 10:00:13.000] SELECT COUNT(*) FROM users
[2025-01-23 10:00:14.000] [user_registration] UPDATE users SET verified = 1 WHERE id = 1
RAW;
file_put_contents($testDir . '/tag-test.raw.log', $rawTagLog);

// End tag-test
$r = run("{$base} job end tag-test");
assert_test('End tag-test', str_contains_compat($r['output'], '[OK]') && str_contains_compat($r['output'], '5 queries'), $r['output']);

// Test: job show includes tag in output
$r = run("{$base} job show tag-test");
$lines = array_filter(explode("\n", $r['output']), function($l) { return trim($l) !== ''; });
$lines = array_values($lines);
assert_test('Show tag-test has 5 lines', count($lines) === 5, "Got " . count($lines) . " lines");

if (count($lines) >= 1) {
    $line1 = json_decode($lines[0], true);
    assert_test('Line 1 has tag user_registration',
        is_array($line1) && isset($line1['tag']) && $line1['tag'] === 'user_registration',
        json_encode($line1));
    assert_test('Line 1 has trace',
        is_array($line1) && isset($line1['trace']) && is_array($line1['trace']) && count($line1['trace']) > 0,
        json_encode(isset($line1['trace']) ? $line1['trace'] : null));
    assert_test('Line 1 trace has call field',
        is_array($line1) && isset($line1['trace'][0]['call']) && $line1['trace'][0]['call'] === 'UserController->register',
        json_encode(isset($line1['trace'][0]) ? $line1['trace'][0] : null));
}

if (count($lines) >= 4) {
    $line4 = json_decode($lines[3], true);
    assert_test('Line 4 (untagged) has no tag field',
        is_array($line4) && !isset($line4['tag']),
        json_encode($line4));
    assert_test('Line 4 (untagged) has no trace field',
        is_array($line4) && !isset($line4['trace']),
        json_encode($line4));
}

// Test: --tag filter
$r = run("{$base} job show tag-test --tag=user_registration");
$lines = array_filter(explode("\n", $r['output']), function($l) { return trim($l) !== ''; });
$lines = array_values($lines);
assert_test('Tag filter shows 3 queries', count($lines) === 3, "Got " . count($lines) . " lines");

$r = run("{$base} job show tag-test --tag=send_email");
$lines = array_filter(explode("\n", $r['output']), function($l) { return trim($l) !== ''; });
$lines = array_values($lines);
assert_test('Tag filter send_email shows 1 query', count($lines) === 1, "Got " . count($lines) . " lines");

// Test: job tags command
$r = run("{$base} job tags tag-test");
assert_test('Tags command shows user_registration', str_contains_compat($r['output'], 'user_registration'), $r['output']);
assert_test('Tags command shows send_email', str_contains_compat($r['output'], 'send_email'), $r['output']);
assert_test('Tags command shows (untagged)', str_contains_compat($r['output'], '(untagged)'), $r['output']);
assert_test('Tags command shows header', str_contains_compat($r['output'], 'TAG'), $r['output']);

// Test: job callers command
$r = run("{$base} job callers tag-test");
assert_test('Callers command shows UserController', str_contains_compat($r['output'], 'UserController->register'), $r['output']);
assert_test('Callers command shows header', str_contains_compat($r['output'], 'CALLER'), $r['output']);

// Test: export includes tag and trace
$r = run("{$base} job export tag-test");
assert_test('Export tag-test succeeds', str_contains_compat($r['output'], '[OK]'), $r['output']);

if (file_exists($testDir . '/tag-test.parsed.json')) {
    $parsed = json_decode(file_get_contents($testDir . '/tag-test.parsed.json'), true);
    assert_test('Parsed JSON is valid array', is_array($parsed));
    if (is_array($parsed) && count($parsed) >= 1) {
        assert_test('Export entry 1 has tag',
            isset($parsed[0]['tag']) && $parsed[0]['tag'] === 'user_registration',
            json_encode(isset($parsed[0]['tag']) ? $parsed[0]['tag'] : null));
        assert_test('Export entry 1 has trace',
            isset($parsed[0]['trace']) && is_array($parsed[0]['trace']),
            json_encode(isset($parsed[0]['trace']) ? $parsed[0]['trace'] : null));
    }
    if (is_array($parsed) && count($parsed) >= 4) {
        assert_test('Export entry 4 has no tag (untagged)',
            !isset($parsed[3]['tag']),
            json_encode(array_keys($parsed[3])));
    }
}

// Purge tag-test
$r = run("{$base} job purge");
assert_test('Purge tag-test succeeds', str_contains_compat($r['output'], '[OK]'), $r['output']);

// Cleanup
cleanup($testDir);

echo "\n=== Results: {$passed} passed, {$failed} failed ===\n";
exit($failed > 0 ? 1 : 0);
