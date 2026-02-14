<?php

namespace App\Http\Controllers;

use Illuminate\Http\JsonResponse;

// Load the profiler CLI library autoloader
require_once '/opt/profiler/vendor/autoload.php';

use MariadbProfiler\JobManager;

class ProfilerApiController extends Controller
{
    private string $logDir = '/var/profiler';

    private function manager(): JobManager
    {
        return new JobManager($this->logDir);
    }

    public function start(): JsonResponse
    {
        $manager = $this->manager();

        // Generate a UUID key
        $key = $this->generateKey();

        if ($manager->startJob($key)) {
            return response()->json([
                'success' => true,
                'job_key' => $key,
            ]);
        }

        return response()->json([
            'success' => false,
            'error' => 'Failed to start job',
        ], 500);
    }

    public function stop(string $key): JsonResponse
    {
        $manager = $this->manager();
        $count = $manager->endJob($key);

        if ($count !== false) {
            return response()->json([
                'success' => true,
                'query_count' => $count,
            ]);
        }

        return response()->json([
            'success' => false,
            'error' => 'Failed to stop job',
        ], 500);
    }

    public function list(): JsonResponse
    {
        $jobsFile = $this->logDir . '/jobs.json';

        if (!file_exists($jobsFile)) {
            return response()->json([
                'active_jobs' => [],
                'completed_jobs' => [],
            ]);
        }

        $data = json_decode(file_get_contents($jobsFile), true) ?: [];

        return response()->json([
            'active_jobs' => $data['active_jobs'] ?? [],
            'completed_jobs' => $data['completed_jobs'] ?? [],
        ]);
    }

    private function generateKey(): string
    {
        $data = random_bytes(16);
        $data[6] = chr(ord($data[6]) & 0x0f | 0x40);
        $data[8] = chr(ord($data[8]) & 0x3f | 0x80);
        return vsprintf('%s%s-%s-%s-%s-%s%s%s', str_split(bin2hex($data), 4));
    }
}
