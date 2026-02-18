import * as fs from 'fs';
import * as vscode from 'vscode';
import { QueryEntry, RawQueryEntry, fromRaw } from '../model/QueryEntry';

export class LogParserService {
  private errorChannel: vscode.OutputChannel;

  constructor(errorChannel: vscode.OutputChannel) {
    this.errorChannel = errorChannel;
  }

  parseJsonlFile(filePath: string): QueryEntry[] {
    if (!fs.existsSync(filePath)) { return []; }

    const content = fs.readFileSync(filePath, 'utf-8');
    return this.parseJsonlContent(content);
  }

  parseJsonlFileFromOffset(filePath: string, offset: number): { entries: QueryEntry[]; newOffset: number } {
    if (!fs.existsSync(filePath)) {
      return { entries: [], newOffset: offset };
    }

    const stat = fs.statSync(filePath);
    if (stat.size <= offset) {
      return { entries: [], newOffset: offset };
    }

    const fd = fs.openSync(filePath, 'r');
    try {
      const bufSize = stat.size - offset;
      const buffer = Buffer.alloc(bufSize);
      fs.readSync(fd, buffer, 0, bufSize, offset);
      const content = buffer.toString('utf-8');
      const entries = this.parseJsonlContent(content);
      return { entries, newOffset: stat.size };
    } finally {
      fs.closeSync(fd);
    }
  }

  readRawLogTail(filePath: string, maxLines: number = 500): string {
    if (!fs.existsSync(filePath)) { return ''; }

    const content = fs.readFileSync(filePath, 'utf-8');
    const lines = content.split('\n');
    const tail = lines.slice(-maxLines);
    return tail.join('\n');
  }

  tailRawLog(filePath: string, offset: number): { content: string; newOffset: number } {
    if (!fs.existsSync(filePath)) {
      return { content: '', newOffset: offset };
    }

    const stat = fs.statSync(filePath);
    if (stat.size <= offset) {
      return { content: '', newOffset: offset };
    }

    const fd = fs.openSync(filePath, 'r');
    try {
      const bufSize = stat.size - offset;
      const buffer = Buffer.alloc(bufSize);
      fs.readSync(fd, buffer, 0, bufSize, offset);
      return { content: buffer.toString('utf-8'), newOffset: stat.size };
    } finally {
      fs.closeSync(fd);
    }
  }

  private parseJsonlContent(content: string): QueryEntry[] {
    const entries: QueryEntry[] = [];
    const lines = content.split('\n');

    for (const line of lines) {
      const trimmed = line.trim();
      if (!trimmed) { continue; }

      try {
        const raw: RawQueryEntry = JSON.parse(trimmed);
        entries.push(fromRaw(raw));
      } catch (e) {
        this.errorChannel.appendLine(`[LogParser] Failed to parse line: ${trimmed.substring(0, 100)}`);
      }
    }

    return entries;
  }
}
