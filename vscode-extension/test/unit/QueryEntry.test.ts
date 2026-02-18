import { describe, it, expect } from 'vitest';
import {
  QueryEntry,
  RawQueryEntry,
  fromRaw,
  getQueryType,
  getBoundQuery,
  getTables,
  getShortSql,
  getSourceFile,
  formatTimestamp,
} from '../../src/model/QueryEntry';

describe('fromRaw', () => {
  it('should convert raw JSONL entry to QueryEntry', () => {
    const raw: RawQueryEntry = {
      k: 'job1',
      q: 'SELECT * FROM users',
      ts: 1705970401.123,
      tag: 'api',
      s: 'ok',
      params: ['42'],
      trace: [{ call: 'UserController->index', file: '/app/UserController.php', line: 42 }],
    };

    const entry = fromRaw(raw);

    expect(entry.jobKey).toBe('job1');
    expect(entry.query).toBe('SELECT * FROM users');
    expect(entry.timestamp).toBe(1705970401.123);
    expect(entry.tag).toBe('api');
    expect(entry.status).toBe('ok');
    expect(entry.params).toEqual(['42']);
    expect(entry.trace).toHaveLength(1);
    expect(entry.trace![0].file).toBe('/app/UserController.php');
  });

  it('should handle minimal entry', () => {
    const raw: RawQueryEntry = { k: 'j', q: 'SELECT 1', ts: 0 };
    const entry = fromRaw(raw);

    expect(entry.jobKey).toBe('j');
    expect(entry.query).toBe('SELECT 1');
    expect(entry.tag).toBeUndefined();
    expect(entry.status).toBeUndefined();
    expect(entry.params).toBeUndefined();
    expect(entry.trace).toBeUndefined();
  });
});

describe('getQueryType', () => {
  it('should detect SELECT', () => {
    expect(getQueryType({ jobKey: '', query: 'SELECT * FROM users', timestamp: 0 })).toBe('SELECT');
  });

  it('should detect INSERT', () => {
    expect(getQueryType({ jobKey: '', query: 'INSERT INTO logs VALUES (1)', timestamp: 0 })).toBe('INSERT');
  });

  it('should detect UPDATE', () => {
    expect(getQueryType({ jobKey: '', query: 'UPDATE users SET name = ?', timestamp: 0 })).toBe('UPDATE');
  });

  it('should detect DELETE', () => {
    expect(getQueryType({ jobKey: '', query: 'DELETE FROM logs WHERE id = 1', timestamp: 0 })).toBe('DELETE');
  });

  it('should detect OTHER for non-standard queries', () => {
    expect(getQueryType({ jobKey: '', query: 'SHOW TABLES', timestamp: 0 })).toBe('OTHER');
  });

  it('should handle leading whitespace', () => {
    expect(getQueryType({ jobKey: '', query: '  SELECT 1', timestamp: 0 })).toBe('SELECT');
  });

  it('should be case-insensitive', () => {
    expect(getQueryType({ jobKey: '', query: 'select * from t', timestamp: 0 })).toBe('SELECT');
  });
});

describe('getBoundQuery', () => {
  it('should return original query when no params', () => {
    const entry: QueryEntry = { jobKey: '', query: 'SELECT 1', timestamp: 0 };
    expect(getBoundQuery(entry)).toBe('SELECT 1');
  });

  it('should return original query when params is empty', () => {
    const entry: QueryEntry = { jobKey: '', query: 'SELECT 1', timestamp: 0, params: [] };
    expect(getBoundQuery(entry)).toBe('SELECT 1');
  });

  it('should bind single parameter', () => {
    const entry: QueryEntry = {
      jobKey: '', query: 'SELECT * FROM users WHERE id = ?', timestamp: 0,
      params: ['42'],
    };
    expect(getBoundQuery(entry)).toBe("SELECT * FROM users WHERE id = '42'");
  });

  it('should bind multiple parameters', () => {
    const entry: QueryEntry = {
      jobKey: '', query: 'SELECT * FROM users WHERE name = ? AND id = ?', timestamp: 0,
      params: ['John', '42'],
    };
    expect(getBoundQuery(entry)).toBe("SELECT * FROM users WHERE name = 'John' AND id = '42'");
  });

  it('should handle NULL parameters', () => {
    const entry: QueryEntry = {
      jobKey: '', query: 'INSERT INTO t (a) VALUES (?)', timestamp: 0,
      params: [null],
    };
    expect(getBoundQuery(entry)).toBe('INSERT INTO t (a) VALUES (NULL)');
  });

  it('should not replace ? inside string literals', () => {
    const entry: QueryEntry = {
      jobKey: '', query: "SELECT * FROM t WHERE a = '?' AND b = ?", timestamp: 0,
      params: ['val'],
    };
    expect(getBoundQuery(entry)).toBe("SELECT * FROM t WHERE a = '?' AND b = 'val'");
  });

  it('should not replace ? inside backtick identifiers', () => {
    const entry: QueryEntry = {
      jobKey: '', query: 'SELECT `?` FROM t WHERE a = ?', timestamp: 0,
      params: ['val'],
    };
    expect(getBoundQuery(entry)).toBe("SELECT `?` FROM t WHERE a = 'val'");
  });

  it('should skip ? in line comments', () => {
    const entry: QueryEntry = {
      jobKey: '', query: 'SELECT 1 -- ? placeholder\nWHERE a = ?', timestamp: 0,
      params: ['val'],
    };
    expect(getBoundQuery(entry)).toBe("SELECT 1 -- ? placeholder\nWHERE a = 'val'");
  });

  it('should skip ? in block comments', () => {
    const entry: QueryEntry = {
      jobKey: '', query: 'SELECT /* ? */ * FROM t WHERE a = ?', timestamp: 0,
      params: ['val'],
    };
    expect(getBoundQuery(entry)).toBe("SELECT /* ? */ * FROM t WHERE a = 'val'");
  });
});

describe('getTables', () => {
  it('should extract tables from SELECT', () => {
    const entry: QueryEntry = {
      jobKey: '', query: 'SELECT * FROM users u JOIN posts p ON p.user_id = u.id', timestamp: 0,
    };
    expect(getTables(entry)).toEqual(['posts', 'users']);
  });

  it('should extract tables from INSERT', () => {
    const entry: QueryEntry = {
      jobKey: '', query: 'INSERT INTO logs (msg) VALUES (?)', timestamp: 0,
    };
    expect(getTables(entry)).toEqual(['logs']);
  });

  it('should extract tables from UPDATE', () => {
    const entry: QueryEntry = {
      jobKey: '', query: 'UPDATE users SET name = ?', timestamp: 0,
    };
    expect(getTables(entry)).toEqual(['users']);
  });

  it('should extract tables from DELETE', () => {
    const entry: QueryEntry = {
      jobKey: '', query: 'DELETE FROM logs WHERE id = 1', timestamp: 0,
    };
    expect(getTables(entry)).toEqual(['logs']);
  });

  it('should handle backtick-quoted table names', () => {
    const entry: QueryEntry = {
      jobKey: '', query: 'SELECT * FROM `users`', timestamp: 0,
    };
    expect(getTables(entry)).toEqual(['users']);
  });

  it('should deduplicate tables', () => {
    const entry: QueryEntry = {
      jobKey: '', query: 'SELECT * FROM users u WHERE u.id IN (SELECT user_id FROM users)', timestamp: 0,
    };
    expect(getTables(entry)).toEqual(['users']);
  });
});

describe('getShortSql', () => {
  it('should return short SQL as-is', () => {
    const entry: QueryEntry = { jobKey: '', query: 'SELECT 1', timestamp: 0 };
    expect(getShortSql(entry)).toBe('SELECT 1');
  });

  it('should truncate long SQL', () => {
    const longQuery = 'SELECT ' + 'a'.repeat(100) + ' FROM users';
    const entry: QueryEntry = { jobKey: '', query: longQuery, timestamp: 0 };
    const result = getShortSql(entry, 20);
    expect(result.length).toBe(20);
    expect(result.endsWith('...')).toBe(true);
  });

  it('should normalize whitespace', () => {
    const entry: QueryEntry = { jobKey: '', query: 'SELECT\n  *\n  FROM\n  users', timestamp: 0 };
    expect(getShortSql(entry)).toBe('SELECT * FROM users');
  });
});

describe('getSourceFile', () => {
  it('should return null when no trace', () => {
    const entry: QueryEntry = { jobKey: '', query: 'SELECT 1', timestamp: 0 };
    expect(getSourceFile(entry)).toBeNull();
  });

  it('should return null when trace is empty', () => {
    const entry: QueryEntry = { jobKey: '', query: 'SELECT 1', timestamp: 0, trace: [] };
    expect(getSourceFile(entry)).toBeNull();
  });

  it('should return first frame as file:line', () => {
    const entry: QueryEntry = {
      jobKey: '', query: 'SELECT 1', timestamp: 0,
      trace: [{ call: 'test()', file: '/app/Test.php', line: 42 }],
    };
    expect(getSourceFile(entry)).toBe('/app/Test.php:42');
  });
});

describe('formatTimestamp', () => {
  it('should format unix timestamp to HH:MM:SS.mmm', () => {
    const result = formatTimestamp(1705970401.123);
    // Just check format, not exact value (timezone dependent)
    expect(result).toMatch(/^\d{2}:\d{2}:\d{2}\.\d{3}$/);
  });
});
