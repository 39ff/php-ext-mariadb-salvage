import { describe, it, expect } from 'vitest';
import {
  parseJobsFile,
  jobsFileToJobInfos,
  formatDuration,
} from '../../src/model/JobInfo';

describe('parseJobsFile', () => {
  it('should parse standard jobs.json', () => {
    const json = JSON.stringify({
      active_jobs: {
        'abc-123': { started_at: 1705970401.0, parent: null },
      },
      completed_jobs: {
        'def-456': { started_at: 1705970300.0, ended_at: 1705970400.0, query_count: 42 },
      },
    });

    const result = parseJobsFile(json);
    expect(Object.keys(result.active_jobs)).toEqual(['abc-123']);
    expect(Object.keys(result.completed_jobs)).toEqual(['def-456']);
    expect(result.active_jobs['abc-123'].started_at).toBe(1705970401.0);
    expect(result.completed_jobs['def-456'].query_count).toBe(42);
  });

  it('should handle PHP empty array as empty map', () => {
    const json = JSON.stringify({
      active_jobs: [],
      completed_jobs: { 'def-456': { started_at: 1705970300.0 } },
    });

    const result = parseJobsFile(json);
    expect(result.active_jobs).toEqual({});
    expect(Object.keys(result.completed_jobs)).toEqual(['def-456']);
  });

  it('should handle both maps empty (PHP format)', () => {
    const json = JSON.stringify({
      active_jobs: [],
      completed_jobs: [],
    });

    const result = parseJobsFile(json);
    expect(result.active_jobs).toEqual({});
    expect(result.completed_jobs).toEqual({});
  });

  it('should handle parent field', () => {
    const json = JSON.stringify({
      active_jobs: {},
      completed_jobs: {
        'child-job': { started_at: 100, ended_at: 200, query_count: 5, parent: 'parent-job' },
      },
    });

    const result = parseJobsFile(json);
    expect(result.completed_jobs['child-job'].parent).toBe('parent-job');
  });
});

describe('jobsFileToJobInfos', () => {
  it('should convert to sorted JobInfo array', () => {
    const json = JSON.stringify({
      active_jobs: {
        'active-1': { started_at: 100 },
      },
      completed_jobs: {
        'completed-1': { started_at: 50, ended_at: 80, query_count: 10 },
        'completed-2': { started_at: 90, ended_at: 95, query_count: 5 },
      },
    });

    const jobsFile = parseJobsFile(json);
    const infos = jobsFileToJobInfos(jobsFile);

    // Active jobs first
    expect(infos[0].key).toBe('active-1');
    expect(infos[0].isActive).toBe(true);

    // Completed sorted by startedAt descending
    expect(infos[1].key).toBe('completed-2');
    expect(infos[1].isActive).toBe(false);
    expect(infos[2].key).toBe('completed-1');
    expect(infos[2].isActive).toBe(false);
    expect(infos[2].queryCount).toBe(10);
  });

  it('should handle empty jobs file', () => {
    const jobsFile = parseJobsFile(JSON.stringify({ active_jobs: [], completed_jobs: [] }));
    expect(jobsFileToJobInfos(jobsFile)).toEqual([]);
  });
});

describe('formatDuration', () => {
  it('should format short duration in seconds', () => {
    expect(formatDuration(100, 103.2)).toBe('3.2s');
  });

  it('should format longer duration with minutes', () => {
    expect(formatDuration(100, 225)).toBe('2m5s');
  });

  it('should handle zero duration', () => {
    expect(formatDuration(100, 100)).toBe('0.0s');
  });
});
