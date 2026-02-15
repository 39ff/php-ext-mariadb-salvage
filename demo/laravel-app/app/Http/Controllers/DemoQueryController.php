<?php

namespace App\Http\Controllers;

use Illuminate\Http\JsonResponse;
use Illuminate\Support\Facades\DB;

class DemoQueryController extends Controller
{
    public function run(): JsonResponse
    {
        $queries = [];

        // 1. Simple SELECT
        $users = DB::select('SELECT id, name, email FROM users LIMIT 5');
        $queries[] = 'SELECT users (LIMIT 5)';

        // 2. SELECT with WHERE
        $active = DB::select('SELECT id, name FROM users WHERE active = ?', [1]);
        $queries[] = 'SELECT users WHERE active';

        // 3. JOIN query
        $posts = DB::select('
            SELECT u.name, p.title, p.created_at
            FROM users u
            INNER JOIN posts p ON u.id = p.user_id
            ORDER BY p.created_at DESC
            LIMIT 10
        ');
        $queries[] = 'SELECT users JOIN posts';

        // 4. LEFT JOIN with comments
        $comments = DB::select('
            SELECT p.title, c.body, u.name as commenter
            FROM posts p
            LEFT JOIN comments c ON p.id = c.post_id
            LEFT JOIN users u ON c.user_id = u.id
            WHERE p.user_id = ?
            LIMIT 10
        ', [1]);
        $queries[] = 'SELECT posts LEFT JOIN comments, users';

        // 5. Aggregate query
        $stats = DB::select('
            SELECT u.name, COUNT(p.id) as post_count, MAX(p.created_at) as last_post
            FROM users u
            LEFT JOIN posts p ON u.id = p.user_id
            GROUP BY u.id, u.name
            HAVING COUNT(p.id) > 0
            ORDER BY post_count DESC
        ');
        $queries[] = 'Aggregate: posts per user';

        // 6. Subquery
        $topUsers = DB::select('
            SELECT name, email FROM users
            WHERE id IN (
                SELECT user_id FROM posts
                GROUP BY user_id
                HAVING COUNT(*) >= 2
            )
        ');
        $queries[] = 'Subquery: users with 2+ posts';

        // 7. INSERT
        DB::insert('INSERT INTO posts (user_id, title, body, created_at, updated_at) VALUES (?, ?, ?, NOW(), NOW())', [
            1, 'Demo Post ' . time(), 'This post was created by the demo query runner.',
        ]);
        $queries[] = 'INSERT into posts';

        // 8. UPDATE
        DB::update('UPDATE users SET updated_at = NOW() WHERE active = ?', [1]);
        $queries[] = 'UPDATE users set updated_at';

        return response()->json([
            'success' => true,
            'queries_executed' => count($queries),
            'queries' => $queries,
        ]);
    }
}
