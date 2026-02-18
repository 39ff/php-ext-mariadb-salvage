import * as fs from 'fs';
import * as path from 'path';
import * as vscode from 'vscode';
import { execFile } from 'child_process';
import { JobInfo, JobsFile, parseJobsFile, jobsFileToJobInfos } from '../model/JobInfo';

export class JobManagerService {
  private errorChannel: vscode.OutputChannel;

  constructor(errorChannel: vscode.OutputChannel) {
    this.errorChannel = errorChannel;
  }

  loadJobs(): JobInfo[] {
    const jobsPath = this.getJobsJsonPath();
    if (!fs.existsSync(jobsPath)) { return []; }

    try {
      const content = fs.readFileSync(jobsPath, 'utf-8');
      const jobsFile = parseJobsFile(content);
      return jobsFileToJobInfos(jobsFile);
    } catch (e) {
      this.errorChannel.appendLine(`[JobManager] Failed to load jobs.json: ${e}`);
      return [];
    }
  }

  getActiveJobs(): JobInfo[] {
    return this.loadJobs().filter(j => j.isActive);
  }

  getCompletedJobs(): JobInfo[] {
    return this.loadJobs().filter(j => !j.isActive);
  }

  async startJob(jobKey?: string): Promise<string> {
    const args = ['job', 'start'];
    if (jobKey) { args.push(jobKey); }

    const output = await this.runCli(args);
    // CLI outputs: "Job '{key}' started"
    const match = output.match(/Job '([^']+)' started/);
    if (match) { return match[1]; }

    // Fallback: return the key if provided, or extract from output
    return jobKey || output.trim();
  }

  async stopJob(jobKey: string): Promise<void> {
    await this.runCli(['job', 'end', jobKey]);
  }

  getJsonlPath(jobKey: string): string {
    return path.join(this.getLogDir(), `${jobKey}.jsonl`);
  }

  getRawLogPath(jobKey: string): string {
    return path.join(this.getLogDir(), `${jobKey}.raw.log`);
  }

  getJobsJsonPath(): string {
    return path.join(this.getLogDir(), 'jobs.json');
  }

  getLogDir(): string {
    return vscode.workspace.getConfiguration('mariadbProfiler')
      .get<string>('logDirectory', '/tmp/mariadb_profiler');
  }

  private getPhpPath(): string {
    return vscode.workspace.getConfiguration('mariadbProfiler')
      .get<string>('phpPath', 'php');
  }

  private getCliScriptPath(): string {
    const configured = vscode.workspace.getConfiguration('mariadbProfiler')
      .get<string>('cliScriptPath', '');

    if (configured) { return configured; }

    // Auto-detect from workspace
    const workspaceFolders = vscode.workspace.workspaceFolders;
    if (workspaceFolders) {
      for (const folder of workspaceFolders) {
        const candidate = path.join(folder.uri.fsPath, 'cli', 'mariadb_profiler.php');
        if (fs.existsSync(candidate)) { return candidate; }
      }
    }

    return '';
  }

  private runCli(args: string[]): Promise<string> {
    return new Promise((resolve, reject) => {
      const phpPath = this.getPhpPath();
      const scriptPath = this.getCliScriptPath();

      if (!scriptPath) {
        reject(new Error('CLI script path not configured and not found in workspace'));
        return;
      }

      const cliArgs = [scriptPath, `--log-dir=${this.getLogDir()}`, ...args];

      execFile(phpPath, cliArgs, { timeout: 60000 }, (error, stdout, stderr) => {
        if (error) {
          this.errorChannel.appendLine(`[CLI] Error: ${error.message}`);
          if (stderr) { this.errorChannel.appendLine(`[CLI] stderr: ${stderr}`); }
          reject(error);
          return;
        }
        resolve(stdout);
      });
    });
  }
}
