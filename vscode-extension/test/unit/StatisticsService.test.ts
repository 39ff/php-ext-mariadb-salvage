import { describe, it, expect } from 'vitest';
import { StatisticsService } from '../../src/service/StatisticsService';
import { QueryEntry } from '../../src/model/QueryEntry';

describe('StatisticsService', () => {
  const service = new StatisticsService();

  it('should compute empty stats for no entries', () => {
    const stats = service.computeStats([]);
    expect(stats.totalQueries).toBe(0);
    expect(stats.byType).toEqual({});
    expect(stats.byTable).toEqual({});
    expect(stats.byTag).toEqual({});
  });

  it('should count queries by type', () => {
    const entries: QueryEntry[] = [
      { jobKey: 'j', query: 'SELECT 1', timestamp: 0 },
      { jobKey: 'j', query: 'SELECT 2', timestamp: 0 },
      { jobKey: 'j', query: 'INSERT INTO t VALUES (1)', timestamp: 0 },
      { jobKey: 'j', query: 'UPDATE t SET a = 1', timestamp: 0 },
      { jobKey: 'j', query: 'DELETE FROM t', timestamp: 0 },
    ];

    const stats = service.computeStats(entries);
    expect(stats.totalQueries).toBe(5);
    expect(stats.byType['SELECT']).toBe(2);
    expect(stats.byType['INSERT']).toBe(1);
    expect(stats.byType['UPDATE']).toBe(1);
    expect(stats.byType['DELETE']).toBe(1);
  });

  it('should count queries by table', () => {
    const entries: QueryEntry[] = [
      { jobKey: 'j', query: 'SELECT * FROM users', timestamp: 0 },
      { jobKey: 'j', query: 'SELECT * FROM users JOIN posts ON 1=1', timestamp: 0 },
      { jobKey: 'j', query: 'INSERT INTO posts VALUES (1)', timestamp: 0 },
    ];

    const stats = service.computeStats(entries);
    expect(stats.byTable['users']).toBe(2);
    expect(stats.byTable['posts']).toBe(2);
  });

  it('should count queries by tag', () => {
    const entries: QueryEntry[] = [
      { jobKey: 'j', query: 'SELECT 1', timestamp: 0, tag: 'api' },
      { jobKey: 'j', query: 'SELECT 2', timestamp: 0, tag: 'api' },
      { jobKey: 'j', query: 'SELECT 3', timestamp: 0, tag: 'web' },
      { jobKey: 'j', query: 'SELECT 4', timestamp: 0 }, // no tag
    ];

    const stats = service.computeStats(entries);
    expect(stats.byTag['api']).toBe(2);
    expect(stats.byTag['web']).toBe(1);
    expect(stats.byTag['']).toBeUndefined(); // no-tag entries not counted
  });

  it('should sort by value descending', () => {
    const entries: QueryEntry[] = [
      { jobKey: 'j', query: 'SELECT 1', timestamp: 0, tag: 'rare' },
      { jobKey: 'j', query: 'SELECT 2', timestamp: 0, tag: 'common' },
      { jobKey: 'j', query: 'SELECT 3', timestamp: 0, tag: 'common' },
      { jobKey: 'j', query: 'SELECT 4', timestamp: 0, tag: 'common' },
      { jobKey: 'j', query: 'SELECT 5', timestamp: 0, tag: 'medium' },
      { jobKey: 'j', query: 'SELECT 6', timestamp: 0, tag: 'medium' },
    ];

    const stats = service.computeStats(entries);
    const tagKeys = Object.keys(stats.byTag);
    expect(tagKeys[0]).toBe('common');
    expect(tagKeys[1]).toBe('medium');
    expect(tagKeys[2]).toBe('rare');
  });
});
