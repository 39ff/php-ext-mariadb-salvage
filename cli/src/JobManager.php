<?php

declare(strict_types=1);

namespace MariadbProfiler;

/**
 * JobManager - manages profiling job state via shared file.
 */
class JobManager
{
    private string $logDir;
    private string $jobsFile;

    public function __construct(?string $logDir = null)
    {
        $this->logDir = $logDir ?? $this->detectLogDir();
        $this->jobsFile = $this->logDir . '/jobs.json';

        if (!is_dir($this->logDir)) {
            mkdir($this->logDir, 0777, true);
        }
    }

    /**
     * Detect log directory from php.ini or use default.
     */
    private function detectLogDir(): string
    {
        $dir = ini_get('mariadb_profiler.log_dir');
        if ($dir && $dir !== '') {
            return $dir;
        }
        return '/tmp/mariadb_profiler';
    }

    public function getLogDir(): string
    {
        return $this->logDir;
    }

    /**
     * Start a new profiling job.
     */
    public function startJob(string $key): bool
    {
        $data = $this->readJobsFile();

        if (isset($data['active_jobs'][$key])) {
            fwrite(STDERR, "[ERROR] Job '{$key}' is already active.\n");
            return false;
        }

        // Determine parent (the most recently started active job, if any)
        $parent = null;
        if (!empty($data['active_jobs'])) {
            $latest = null;
            $latestTime = 0;
            foreach ($data['active_jobs'] as $k => $v) {
                if (($v['started_at'] ?? 0) >= $latestTime) {
                    $latestTime = $v['started_at'] ?? 0;
                    $latest = $k;
                }
            }
            $parent = $latest;
        }

        $data['active_jobs'][$key] = [
            'started_at' => microtime(true),
            'parent' => $parent,
        ];

        $this->writeJobsFile($data);

        return true;
    }

    /**
     * End a profiling job.
     *
     * @return int|false Number of queries captured, or false on failure
     */
    public function endJob(string $key)
    {
        $data = $this->readJobsFile();

        if (!isset($data['active_jobs'][$key])) {
            fwrite(STDERR, "[ERROR] Job '{$key}' is not active.\n");
            return false;
        }

        // Count queries in the JSONL file
        $queryCount = $this->countQueries($key);

        // Add to completed jobs
        if (!isset($data['completed_jobs'])) {
            $data['completed_jobs'] = [];
        }

        $data['completed_jobs'][$key] = [
            'started_at' => $data['active_jobs'][$key]['started_at'],
            'ended_at' => microtime(true),
            'parent' => $data['active_jobs'][$key]['parent'],
            'query_count' => $queryCount,
        ];

        // Remove from active
        unset($data['active_jobs'][$key]);

        $this->writeJobsFile($data);

        return $queryCount;
    }

    /**
     * List all active jobs.
     *
     * @return array<string, array>
     */
    public function listActiveJobs(): array
    {
        $data = $this->readJobsFile();
        return $data['active_jobs'] ?? [];
    }

    /**
     * List all completed jobs.
     *
     * @return array<string, array>
     */
    public function listCompletedJobs(): array
    {
        $data = $this->readJobsFile();
        return $data['completed_jobs'] ?? [];
    }

    /**
     * List all jobs (active + completed).
     */
    public function listAllJobs(): array
    {
        $data = $this->readJobsFile();
        return [
            'active' => $data['active_jobs'] ?? [],
            'completed' => $data['completed_jobs'] ?? [],
        ];
    }

    /**
     * Get raw queries for a job from the JSONL file.
     *
     * @return array<array>
     */
    public function getJobQueries(string $key): array
    {
        $file = $this->logDir . '/' . $key . '.jsonl';
        if (!file_exists($file)) {
            return [];
        }

        $queries = [];
        $handle = fopen($file, 'r');
        if (!$handle) {
            return [];
        }

        while (($line = fgets($handle)) !== false) {
            $line = trim($line);
            if ($line === '') {
                continue;
            }
            $entry = json_decode($line, true);
            if (is_array($entry)) {
                $queries[] = $entry;
            }
        }

        fclose($handle);
        return $queries;
    }

    /**
     * Get raw log content for a job.
     */
    public function getRawLog(string $key): ?string
    {
        $file = $this->logDir . '/' . $key . '.raw.log';
        if (!file_exists($file)) {
            return null;
        }
        return file_get_contents($file);
    }

    /**
     * Count queries in a job's JSONL file.
     */
    private function countQueries(string $key): int
    {
        $file = $this->logDir . '/' . $key . '.jsonl';
        if (!file_exists($file)) {
            return 0;
        }

        $count = 0;
        $handle = fopen($file, 'r');
        if (!$handle) {
            return 0;
        }

        while (($line = fgets($handle)) !== false) {
            if (trim($line) !== '') {
                $count++;
            }
        }

        fclose($handle);
        return $count;
    }

    /**
     * Read jobs.json with shared lock.
     */
    private function readJobsFile(): array
    {
        if (!file_exists($this->jobsFile)) {
            return ['active_jobs' => [], 'completed_jobs' => []];
        }

        $handle = fopen($this->jobsFile, 'r');
        if (!$handle) {
            return ['active_jobs' => [], 'completed_jobs' => []];
        }

        flock($handle, LOCK_SH);
        $content = stream_get_contents($handle);
        flock($handle, LOCK_UN);
        fclose($handle);

        if (!$content) {
            return ['active_jobs' => [], 'completed_jobs' => []];
        }

        $data = json_decode($content, true);
        if (!is_array($data)) {
            return ['active_jobs' => [], 'completed_jobs' => []];
        }

        if (!isset($data['active_jobs'])) {
            $data['active_jobs'] = [];
        }
        if (!isset($data['completed_jobs'])) {
            $data['completed_jobs'] = [];
        }

        return $data;
    }

    /**
     * Write jobs.json with exclusive lock.
     */
    private function writeJobsFile(array $data): void
    {
        $handle = fopen($this->jobsFile, 'c');
        if (!$handle) {
            fwrite(STDERR, "[ERROR] Cannot open {$this->jobsFile} for writing.\n");
            return;
        }

        flock($handle, LOCK_EX);
        ftruncate($handle, 0);
        rewind($handle);
        fwrite($handle, json_encode($data, JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE));
        fflush($handle);
        flock($handle, LOCK_UN);
        fclose($handle);
    }

    /**
     * Remove all completed job data.
     */
    public function purgeCompleted(): int
    {
        $data = $this->readJobsFile();
        $count = count($data['completed_jobs'] ?? []);

        foreach (array_keys($data['completed_jobs'] ?? []) as $key) {
            $this->removeJobFiles($key);
        }

        $data['completed_jobs'] = [];
        $this->writeJobsFile($data);

        return $count;
    }

    /**
     * Remove log files for a job.
     */
    private function removeJobFiles(string $key): void
    {
        $files = [
            $this->logDir . '/' . $key . '.jsonl',
            $this->logDir . '/' . $key . '.raw.log',
            $this->logDir . '/' . $key . '.parsed.json',
        ];

        foreach ($files as $file) {
            if (file_exists($file)) {
                unlink($file);
            }
        }
    }
}
