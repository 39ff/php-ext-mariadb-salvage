<?php

namespace Database\Seeders;

use Illuminate\Database\Seeder;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Str;

class DemoSeeder extends Seeder
{
    public function run(): void
    {
        $now = now();

        // Users
        $users = [];
        $names = ['Alice', 'Bob', 'Charlie', 'Diana', 'Eve', 'Frank', 'Grace', 'Henry'];
        foreach ($names as $i => $name) {
            $users[] = [
                'id' => $i + 1,
                'name' => $name,
                'email' => strtolower($name) . '@example.com',
                'password' => Hash::make('password'),
                'active' => $i < 6 ? 1 : 0,
                'created_at' => $now,
                'updated_at' => $now,
            ];
        }
        DB::table('users')->insert($users);

        // Posts
        $posts = [];
        $postId = 1;
        foreach ([1, 1, 2, 2, 3, 3, 4, 5, 5, 6] as $userId) {
            $posts[] = [
                'id' => $postId,
                'user_id' => $userId,
                'title' => 'Post #' . $postId . ' by User ' . $userId,
                'body' => 'This is the body of post #' . $postId . '. ' . Str::random(100),
                'created_at' => $now->copy()->subDays(rand(0, 30)),
                'updated_at' => $now,
            ];
            $postId++;
        }
        DB::table('posts')->insert($posts);

        // Comments
        $comments = [];
        $commentId = 1;
        foreach (range(1, 10) as $pId) {
            $count = rand(0, 3);
            for ($c = 0; $c < $count; $c++) {
                $comments[] = [
                    'id' => $commentId++,
                    'post_id' => $pId,
                    'user_id' => rand(1, 8),
                    'body' => 'Comment on post #' . $pId . ': ' . Str::random(50),
                    'created_at' => $now->copy()->subDays(rand(0, 15)),
                    'updated_at' => $now,
                ];
            }
        }
        if (!empty($comments)) {
            DB::table('comments')->insert($comments);
        }
    }
}
