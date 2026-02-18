import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import * as fs from 'fs';
import * as path from 'path';
import * as os from 'os';

// Mock vscode module for LogParserService
import { vi } from 'vitest';
vi.mock('vscode', () => ({
  window: {
    createOutputChannel: () => ({
      appendLine: () => {},
      show: () => {},
      clear: () => {},
      dispose: () => {},
    }),
  },
  workspace: {
    getConfiguration: () => ({
      get: (_key: string, defaultValue: unknown) => defaultValue,
    }),
  },
}));

import { LogParserService } from '../../src/service/LogParserService';

describe('LogParserService', () => {
  let tmpDir: string;
  let service: LogParserService;
  const mockChannel = {
    appendLine: () => {},
    show: () => {},
    clear: () => {},
    dispose: () => {},
  } as any;

  beforeEach(() => {
    tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'logparser-test-'));
    service = new LogParserService(mockChannel);
  });

  afterEach(() => {
    fs.rmSync(tmpDir, { recursive: true, force: true });
  });

  describe('parseJsonlFile', () => {
    it('should parse valid JSONL file', () => {
      const filePath = path.join(tmpDir, 'test.jsonl');
      const lines = [
        '{"k":"job1","q":"SELECT 1","ts":100}',
        '{"k":"job1","q":"INSERT INTO t VALUES (1)","ts":101,"tag":"api"}',
      ];
      fs.writeFileSync(filePath, lines.join('\n'));

      const entries = service.parseJsonlFile(filePath);
      expect(entries).toHaveLength(2);
      expect(entries[0].jobKey).toBe('job1');
      expect(entries[0].query).toBe('SELECT 1');
      expect(entries[1].tag).toBe('api');
    });

    it('should skip invalid lines', () => {
      const filePath = path.join(tmpDir, 'test.jsonl');
      const lines = [
        '{"k":"job1","q":"SELECT 1","ts":100}',
        'INVALID JSON',
        '{"k":"job1","q":"SELECT 2","ts":102}',
      ];
      fs.writeFileSync(filePath, lines.join('\n'));

      const entries = service.parseJsonlFile(filePath);
      expect(entries).toHaveLength(2);
    });

    it('should return empty for non-existent file', () => {
      const entries = service.parseJsonlFile('/nonexistent/file.jsonl');
      expect(entries).toEqual([]);
    });

    it('should handle empty file', () => {
      const filePath = path.join(tmpDir, 'empty.jsonl');
      fs.writeFileSync(filePath, '');
      const entries = service.parseJsonlFile(filePath);
      expect(entries).toEqual([]);
    });

    it('should handle trailing newline', () => {
      const filePath = path.join(tmpDir, 'test.jsonl');
      fs.writeFileSync(filePath, '{"k":"j","q":"SELECT 1","ts":0}\n');
      const entries = service.parseJsonlFile(filePath);
      expect(entries).toHaveLength(1);
    });
  });

  describe('parseJsonlFileFromOffset', () => {
    it('should read new entries from offset', () => {
      const filePath = path.join(tmpDir, 'test.jsonl');
      const line1 = '{"k":"job1","q":"SELECT 1","ts":100}\n';
      const line2 = '{"k":"job1","q":"SELECT 2","ts":101}\n';
      fs.writeFileSync(filePath, line1);

      // First read
      const result1 = service.parseJsonlFileFromOffset(filePath, 0);
      expect(result1.entries).toHaveLength(1);
      expect(result1.newOffset).toBe(Buffer.byteLength(line1));

      // Append new data
      fs.appendFileSync(filePath, line2);

      // Second read from offset
      const result2 = service.parseJsonlFileFromOffset(filePath, result1.newOffset);
      expect(result2.entries).toHaveLength(1);
      expect(result2.entries[0].query).toBe('SELECT 2');
    });

    it('should return empty when no new data', () => {
      const filePath = path.join(tmpDir, 'test.jsonl');
      fs.writeFileSync(filePath, '{"k":"j","q":"SELECT 1","ts":0}\n');

      const result1 = service.parseJsonlFileFromOffset(filePath, 0);
      const result2 = service.parseJsonlFileFromOffset(filePath, result1.newOffset);
      expect(result2.entries).toHaveLength(0);
      expect(result2.newOffset).toBe(result1.newOffset);
    });

    it('should handle non-existent file', () => {
      const result = service.parseJsonlFileFromOffset('/nonexistent', 0);
      expect(result.entries).toEqual([]);
      expect(result.newOffset).toBe(0);
    });
  });

  describe('readRawLogTail', () => {
    it('should read tail of raw log', () => {
      const filePath = path.join(tmpDir, 'test.raw.log');
      const lines = Array.from({ length: 10 }, (_, i) => `line ${i}`);
      fs.writeFileSync(filePath, lines.join('\n'));

      const result = service.readRawLogTail(filePath, 3);
      const resultLines = result.split('\n');
      expect(resultLines).toHaveLength(3);
      expect(resultLines[0]).toBe('line 7');
      expect(resultLines[2]).toBe('line 9');
    });

    it('should return empty for non-existent file', () => {
      expect(service.readRawLogTail('/nonexistent')).toBe('');
    });
  });

  describe('tailRawLog', () => {
    it('should read new content from offset', () => {
      const filePath = path.join(tmpDir, 'test.raw.log');
      fs.writeFileSync(filePath, 'line 1\n');

      const r1 = service.tailRawLog(filePath, 0);
      expect(r1.content).toBe('line 1\n');

      fs.appendFileSync(filePath, 'line 2\n');

      const r2 = service.tailRawLog(filePath, r1.newOffset);
      expect(r2.content).toBe('line 2\n');
    });
  });
});
