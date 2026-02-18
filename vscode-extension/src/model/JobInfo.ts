export interface JobData {
  started_at: number;
  ended_at?: number;
  query_count?: number;
  parent?: string | null;
}

export interface JobsFile {
  active_jobs: Record<string, JobData>;
  completed_jobs: Record<string, JobData>;
}

export interface JobInfo {
  key: string;
  startedAt: number;
  endedAt?: number;
  queryCount?: number;
  parent?: string | null;
  isActive: boolean;
}

export function parseJobsFile(content: string): JobsFile {
  const raw = JSON.parse(content);
  return {
    active_jobs: normalizeJobMap(raw.active_jobs),
    completed_jobs: normalizeJobMap(raw.completed_jobs),
  };
}

// PHP encodes empty associative arrays as [] instead of {}
function normalizeJobMap(value: unknown): Record<string, JobData> {
  if (Array.isArray(value) && value.length === 0) {
    return {};
  }
  if (typeof value === 'object' && value !== null) {
    return value as Record<string, JobData>;
  }
  return {};
}

export function jobsFileToJobInfos(jobsFile: JobsFile): JobInfo[] {
  const jobs: JobInfo[] = [];

  for (const [key, data] of Object.entries(jobsFile.active_jobs)) {
    jobs.push({
      key,
      startedAt: data.started_at,
      endedAt: data.ended_at,
      queryCount: data.query_count,
      parent: data.parent,
      isActive: true,
    });
  }

  for (const [key, data] of Object.entries(jobsFile.completed_jobs)) {
    jobs.push({
      key,
      startedAt: data.started_at,
      endedAt: data.ended_at,
      queryCount: data.query_count,
      parent: data.parent,
      isActive: false,
    });
  }

  // Sort: active first, then by startedAt descending
  jobs.sort((a, b) => {
    if (a.isActive !== b.isActive) { return a.isActive ? -1 : 1; }
    return b.startedAt - a.startedAt;
  });

  return jobs;
}

export function formatDuration(startedAt: number, endedAt?: number): string {
  if (endedAt === undefined) { return 'running...'; }
  const seconds = endedAt - startedAt;
  if (seconds < 1) { return `${Math.round(seconds * 1000)} ms`; }
  if (seconds < 60) { return `${seconds.toFixed(1)}s`; }
  const totalSeconds = Math.round(seconds);
  const minutes = Math.floor(totalSeconds / 60);
  const secs = totalSeconds % 60;
  return `${minutes}m${secs}s`;
}
