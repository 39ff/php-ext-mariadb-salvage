<?php

namespace App\Http\Controllers;

use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Symfony\Component\Process\Process;

class ProfilerApiController extends Controller
{
    private string $cliPath = '/opt/profiler/cli/mariadb_profiler.php';
    private string $logDir = '/var/profiler';

    public function start(): JsonResponse
    {
        $process = new Process([
            'php', $this->cliPath,
            '--log-dir=' . $this->logDir,
            'job', 'start',
        ]);
        $process->run();

        $output = $process->getOutput() . $process->getErrorOutput();

        // Extract job key from output like "Job started: <key>"
        if (preg_match('/Job started:\s+(\S+)/', $output, $matches)) {
            return response()->json([
                'success' => true,
                'job_key' => $matches[1],
            ]);
        }

        return response()->json([
            'success' => false,
            'error' => 'Failed to start job',
            'output' => $output,
        ], 500);
    }

    public function stop(string $key): JsonResponse
    {
        $process = new Process([
            'php', $this->cliPath,
            '--log-dir=' . $this->logDir,
            'job', 'end', $key,
        ]);
        $process->run();

        $output = $process->getOutput() . $process->getErrorOutput();

        if ($process->isSuccessful()) {
            // Extract query count from output
            $queryCount = 0;
            if (preg_match('/(\d+)\s+quer/', $output, $matches)) {
                $queryCount = (int) $matches[1];
            }

            return response()->json([
                'success' => true,
                'query_count' => $queryCount,
            ]);
        }

        return response()->json([
            'success' => false,
            'error' => 'Failed to stop job',
            'output' => $output,
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
}
