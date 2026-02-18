import * as vscode from 'vscode';
import { JobInfo, formatDuration } from '../model/JobInfo';
import { shortKey } from '../util/queryUtils';

export class JobTreeProvider implements vscode.TreeDataProvider<JobTreeItem> {
  private _onDidChangeTreeData = new vscode.EventEmitter<void>();
  readonly onDidChangeTreeData = this._onDidChangeTreeData.event;

  private jobs: JobInfo[] = [];
  private _onJobSelected = new vscode.EventEmitter<JobInfo>();
  readonly onJobSelected = this._onJobSelected.event;

  refresh(jobs: JobInfo[]): void {
    this.jobs = jobs;
    this._onDidChangeTreeData.fire();
  }

  getTreeItem(element: JobTreeItem): vscode.TreeItem {
    return element;
  }

  getChildren(): JobTreeItem[] {
    return this.jobs.map(job => new JobTreeItem(job));
  }

  selectJob(job: JobInfo): void {
    this._onJobSelected.fire(job);
  }
}

export class JobTreeItem extends vscode.TreeItem {
  constructor(public readonly job: JobInfo) {
    super(shortKey(job.key), vscode.TreeItemCollapsibleState.None);

    const queryInfo = job.queryCount !== undefined ? `${job.queryCount} queries` : 'recording...';
    const duration = formatDuration(job.startedAt, job.endedAt);
    this.description = `${queryInfo}, ${duration}`;

    this.iconPath = new vscode.ThemeIcon(
      job.isActive ? 'circle-filled' : 'circle-outline',
      job.isActive
        ? new vscode.ThemeColor('charts.green')
        : new vscode.ThemeColor('charts.gray'),
    );

    this.contextValue = job.isActive ? 'activeJob' : 'completedJob';

    this.command = {
      command: 'mariadbProfiler.selectJob',
      title: 'Select Job',
      arguments: [job],
    };

    this.tooltip = [
      `Job: ${job.key}`,
      `Status: ${job.isActive ? 'Active' : 'Completed'}`,
      `Started: ${new Date(job.startedAt * 1000).toLocaleString()}`,
      job.endedAt ? `Ended: ${new Date(job.endedAt * 1000).toLocaleString()}` : '',
      job.queryCount !== undefined ? `Queries: ${job.queryCount}` : '',
      job.parent ? `Parent: ${job.parent}` : '',
    ].filter(Boolean).join('\n');
  }
}
