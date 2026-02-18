import * as fs from 'fs';
import * as vscode from 'vscode';

interface WatchEntry {
  callback: () => void;
  lastSize: number;
  lastMtime: number;
}

export class FileWatcherService implements vscode.Disposable {
  private watchers = new Map<string, WatchEntry>();
  private timer: ReturnType<typeof setInterval> | undefined;
  private pollIntervalMs = 1000;

  constructor() {
    this.timer = setInterval(() => this.poll(), this.pollIntervalMs);
  }

  watchFile(filePath: string, onChange: () => void): void {
    this.watchers.set(filePath, {
      callback: onChange,
      lastSize: this.getFileSize(filePath),
      lastMtime: this.getFileMtime(filePath),
    });
  }

  unwatchFile(filePath: string): void {
    this.watchers.delete(filePath);
  }

  dispose(): void {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = undefined;
    }
    this.watchers.clear();
  }

  private poll(): void {
    for (const [filePath, entry] of this.watchers) {
      const size = this.getFileSize(filePath);
      const mtime = this.getFileMtime(filePath);

      if (size !== entry.lastSize || mtime !== entry.lastMtime) {
        entry.lastSize = size;
        entry.lastMtime = mtime;
        try {
          entry.callback();
        } catch (e) {
          // Ignore callback errors
        }
      }
    }
  }

  private getFileSize(filePath: string): number {
    try {
      return fs.statSync(filePath).size;
    } catch {
      return -1;
    }
  }

  private getFileMtime(filePath: string): number {
    try {
      return fs.statSync(filePath).mtimeMs;
    } catch {
      return -1;
    }
  }
}
