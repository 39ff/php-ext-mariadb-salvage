import * as vscode from 'vscode';
import { JobManagerService } from '../service/JobManagerService';
import { LogParserService } from '../service/LogParserService';
import { FileWatcherService } from '../service/FileWatcherService';
import { getQueryType, getShortSql, formatTimestamp } from '../model/QueryEntry';

export class LiveTailManager implements vscode.Disposable {
  private outputChannel: vscode.OutputChannel;
  private fileWatcher: FileWatcherService;
  private logParser: LogParserService;
  private jobManager: JobManagerService;

  private isActive = false;
  private currentJobKey: string | null = null;
  private jsonlOffset = 0;

  constructor(
    outputChannel: vscode.OutputChannel,
    fileWatcher: FileWatcherService,
    logParser: LogParserService,
    jobManager: JobManagerService,
  ) {
    this.outputChannel = outputChannel;
    this.fileWatcher = fileWatcher;
    this.logParser = logParser;
    this.jobManager = jobManager;
  }

  start(jobKey: string): void {
    this.stop();

    this.currentJobKey = jobKey;
    this.isActive = true;
    this.jsonlOffset = 0;

    vscode.commands.executeCommand('setContext', 'mariadbProfiler.liveTailActive', true);

    this.outputChannel.clear();
    this.outputChannel.appendLine(`--- Live Tail: ${jobKey} ---`);
    this.outputChannel.appendLine('');
    this.outputChannel.show(true); // Don't steal focus

    const jsonlPath = this.jobManager.getJsonlPath(jobKey);

    // Initial load
    this.readNewEntries(jsonlPath);

    // Watch for changes
    this.fileWatcher.watchFile(jsonlPath, () => {
      if (this.isActive) {
        this.readNewEntries(jsonlPath);
      }
    });
  }

  stop(): void {
    if (this.currentJobKey) {
      const jsonlPath = this.jobManager.getJsonlPath(this.currentJobKey);
      this.fileWatcher.unwatchFile(jsonlPath);
    }

    this.isActive = false;
    this.currentJobKey = null;
    this.jsonlOffset = 0;

    vscode.commands.executeCommand('setContext', 'mariadbProfiler.liveTailActive', false);
  }

  getIsActive(): boolean {
    return this.isActive;
  }

  getCurrentJobKey(): string | null {
    return this.currentJobKey;
  }

  dispose(): void {
    this.stop();
  }

  private readNewEntries(jsonlPath: string): void {
    const result = this.logParser.parseJsonlFileFromOffset(jsonlPath, this.jsonlOffset);
    this.jsonlOffset = result.newOffset;

    for (const entry of result.entries) {
      const qtype = getQueryType(entry);
      const shortSql = getShortSql(entry, 80);
      const tag = entry.tag ? ` [${entry.tag}]` : '';
      const status = entry.status || 'ok';
      const time = formatTimestamp(entry.timestamp);

      this.outputChannel.appendLine(
        `[${time}] ${qtype} ${status.toUpperCase()}${tag} ${shortSql}`
      );

      // Show backtrace frames
      if (entry.trace) {
        for (let i = 0; i < Math.min(entry.trace.length, 3); i++) {
          const frame = entry.trace[i];
          this.outputChannel.appendLine(`  #${i} ${frame.file}:${frame.line}`);
        }
        if (entry.trace.length > 3) {
          this.outputChannel.appendLine(`  ... (${entry.trace.length - 3} more frames)`);
        }
        this.outputChannel.appendLine('');
      }
    }
  }
}

export function registerLiveTailCommands(
  context: vscode.ExtensionContext,
  liveTailManager: LiveTailManager,
  jobManager: JobManagerService,
): vscode.Disposable[] {
  const startCmd = vscode.commands.registerCommand('mariadbProfiler.startLiveTail', async () => {
    const activeJobs = jobManager.getActiveJobs();
    if (activeJobs.length === 0) {
      vscode.window.showInformationMessage('No active profiling jobs to tail');
      return;
    }

    let jobKey: string;

    if (activeJobs.length === 1) {
      jobKey = activeJobs[0].key;
    } else {
      const items = activeJobs.map(j => ({
        label: j.key,
        description: `Started: ${new Date(j.startedAt * 1000).toLocaleString()}`,
      }));

      const selected = await vscode.window.showQuickPick(items, {
        placeHolder: 'Select a job to tail',
      });

      if (!selected) { return; }
      jobKey = selected.label;
    }

    liveTailManager.start(jobKey);
    vscode.window.showInformationMessage(`Live tail started for job '${jobKey}'`);
  });

  const stopCmd = vscode.commands.registerCommand('mariadbProfiler.stopLiveTail', () => {
    liveTailManager.stop();
    vscode.window.showInformationMessage('Live tail stopped');
  });

  return [startCmd, stopCmd];
}
