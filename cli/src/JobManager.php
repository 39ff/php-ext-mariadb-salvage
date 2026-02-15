<?php

namespace MariadbProfiler;

/**
 * JobManager - manages profiling job state via shared file.
 */
class JobManager
{
    private $logDir;
    private $jobsFile;

    public function __construct($logDir = null)
    {
        $this->logDir = $logDir !== null ? $logDir : $this->detectLogDir();
        $this->jobsFile = $this->logDir . '/jobs.json';

        if (!is_dir($this->logDir)) {
            mkdir($this->logDir, 0777, true);
        }
    }

    /**
     * Detect log directory from php.ini or use default.
     */
    private function detectLogDir()
    {
        $dir = ini_get('mariadb_profiler.log_dir');
        if ($dir && $dir !== '') {
            return $dir;
        }
        return '/tmp/mariadb_profiler';
    }

    public function getLogDir()
    {
        return $this->logDir;
    }

    /**
     * Start a new profiling job.
     */
    public function startJob($key)
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
                $startedAt = isset($v['started_at']) ? $v['started_at'] : 0;
                if ($startedAt >= $latestTime) {
                    $latestTime = $startedAt;
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
    public function endJob($key)
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
     * @return array
     */
    public function listActiveJobs()
    {
        $data = $this->readJobsFile();
        return isset($data['active_jobs']) ? $data['active_jobs'] : [];
    }

    /**
     * List all completed jobs.
     *
     * @return array
     */
    public function listCompletedJobs()
    {
        $data = $this->readJobsFile();
        return isset($data['completed_jobs']) ? $data['completed_jobs'] : [];
    }

    /**
     * List all jobs (active + completed).
     */
    public function listAllJobs()
    {
        $data = $this->readJobsFile();
        return [
            'active' => isset($data['active_jobs']) ? $data['active_jobs'] : [],
            'completed' => isset($data['completed_jobs']) ? $data['completed_jobs'] : [],
        ];
    }

    /**
     * Get raw queries for a job from the JSONL file.
     *
     * @return array
     */
    public function getJobQueries($key)
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
     *
     * @return string|null
     */
    public function getRawLog($key)
    {
        $file = $this->logDir . '/' . $key . '.raw.log';
        if (!file_exists($file)) {
            return null;
        }
        return file_get_contents($file);
    }

    /**
     * Count queries in a job's JSONL file.
     *
     * @return int Number of non-empty lines (queries) in the JSONL file; 0 if the file does not exist
     */
    private function countQueries($key)
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
    private function readJobsFile()
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
    private function writeJobsFile($data)
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
    public function purgeCompleted()
    {
        $data = $this->readJobsFile();
        $completed = isset($data['completed_jobs']) ? $data['completed_jobs'] : [];
        $count = count($completed);

        foreach (array_keys($completed) as $key) {
            $this->removeJobFiles($key);
        }

        $data['completed_jobs'] = [];
        $this->writeJobsFile($data);

        return $count;
    }

    /**
     * Remove log files for a job.
     */
    private function removeJobFiles($key)
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
