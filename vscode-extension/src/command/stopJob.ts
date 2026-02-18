import * as vscode from 'vscode';
import { JobManagerService } from '../service/JobManagerService';
import { JobInfo } from '../model/JobInfo';

export function registerStopJobCommand(
  context: vscode.ExtensionContext,
  jobManager: JobManagerService,
  onJobsChanged: () => void,
): vscode.Disposable {
  return vscode.commands.registerCommand('mariadbProfiler.stopJob', async (jobItem?: { job: JobInfo }) => {
    let jobKey: string | undefined;

    if (jobItem?.job) {
      jobKey = jobItem.job.key;
    } else {
      // Show picker for active jobs
      const activeJobs = jobManager.getActiveJobs();
      if (activeJobs.length === 0) {
        vscode.window.showInformationMessage('No active profiling jobs');
        return;
      }

      const items = activeJobs.map(j => ({
        label: j.key,
        description: `Started: ${new Date(j.startedAt * 1000).toLocaleString()}`,
      }));

      const selected = await vscode.window.showQuickPick(items, {
        placeHolder: 'Select a job to stop',
      });

      if (!selected) { return; }
      jobKey = selected.label;
    }

    try {
      await jobManager.stopJob(jobKey);
      vscode.window.showInformationMessage(`Profiling job '${jobKey}' stopped`);
      onJobsChanged();
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : String(e);
      vscode.window.showErrorMessage(`Failed to stop job: ${msg}`);
    }
  });
}
