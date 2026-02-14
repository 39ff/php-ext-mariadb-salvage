#!/usr/bin/env php
<?php

/**
 * Test suite for SqlAnalyzer
 */

require_once __DIR__ . '/../vendor/autoload.php';

use MariadbProfiler\SqlAnalyzer;

$analyzer = new SqlAnalyzer();
$passed = 0;
$failed = 0;

function test($name, $sql, $expectedTables, $expectedColumns)
{
    global $analyzer, $passed, $failed;

    $result = $analyzer->analyze($sql);

    $tablesOk = assertArrayEquals($expectedTables, $result['tables']);
    $columnsOk = assertArrayEquals($expectedColumns, $result['columns']);

    if ($tablesOk && $columnsOk) {
        echo "[PASS] {$name}\n";
        $passed++;
    } else {
        echo "[FAIL] {$name}\n";
        echo "  SQL: {$sql}\n";
        if (!$tablesOk) {
            echo "  Tables expected: " . json_encode($expectedTables) . "\n";
            echo "  Tables got:      " . json_encode($result['tables']) . "\n";
        }
        if (!$columnsOk) {
            echo "  Columns expected: " . json_encode($expectedColumns) . "\n";
            echo "  Columns got:      " . json_encode($result['columns']) . "\n";
        }
        $failed++;
    }
}

function assertArrayEquals($expected, $actual)
{
    $e = $expected;
    $a = $actual;
    sort($e);
    sort($a);
    return $e === $a;
}

// ============================================================================
// Test cases
// ============================================================================

echo "=== SqlAnalyzer Test Suite ===\n\n";

// Simple SELECT
test(
    'Simple SELECT',
    'SELECT id, name FROM users',
    ['users'],
    ['id', 'name']
);

// SELECT with table prefix
test(
    'SELECT with table prefix',
    'SELECT u.id, u.name FROM users u',
    ['users'],
    ['users.id', 'users.name']
);

// SELECT with JOIN
test(
    'SELECT with JOIN',
    'SELECT u.name, o.total FROM users u JOIN orders o ON u.id = o.user_id',
    ['orders', 'users'],
    ['orders.total', 'orders.user_id', 'users.id', 'users.name']
);

// SELECT with LEFT JOIN
test(
    'SELECT with LEFT JOIN',
    'SELECT u.name, p.title FROM users u LEFT JOIN posts p ON u.id = p.author_id WHERE u.active = 1',
    ['posts', 'users'],
    ['posts.author_id', 'posts.title', 'users.active', 'users.id', 'users.name']
);

// INSERT
test(
    'INSERT',
    'INSERT INTO users (name, email, created_at) VALUES ("test", "test@example.com", NOW())',
    ['users'],
    ['created_at', 'email', 'name']
);

// UPDATE
test(
    'UPDATE',
    'UPDATE users SET name = "updated", email = "new@example.com" WHERE id = 1',
    ['users'],
    ['email', 'id', 'name']
);

// DELETE
test(
    'DELETE',
    'DELETE FROM users WHERE id = 1',
    ['users'],
    ['id']
);

// Multiple JOINs
test(
    'Multiple JOINs',
    'SELECT u.name, o.total, p.name as product_name FROM users u ' .
    'INNER JOIN orders o ON u.id = o.user_id ' .
    'INNER JOIN order_items oi ON o.id = oi.order_id ' .
    'INNER JOIN products p ON oi.product_id = p.id',
    ['order_items', 'orders', 'products', 'users'],
    ['order_items.order_id', 'order_items.product_id', 'orders.id',
     'orders.total', 'orders.user_id', 'products.id', 'products.name',
     'users.id', 'users.name']
);

// SELECT with WHERE and GROUP BY
test(
    'SELECT with GROUP BY',
    'SELECT status, COUNT(id) FROM orders WHERE created_at > "2024-01-01" GROUP BY status',
    ['orders'],
    ['created_at', 'id', 'status']
);

// Backtick-quoted identifiers
test(
    'Backtick-quoted identifiers',
    'SELECT `u`.`name`, `u`.`email` FROM `users` `u` WHERE `u`.`id` = 1',
    ['users'],
    ['users.email', 'users.id', 'users.name']
);

echo "\n=== Results: {$passed} passed, {$failed} failed ===\n";
exit($failed > 0 ? 1 : 0);
