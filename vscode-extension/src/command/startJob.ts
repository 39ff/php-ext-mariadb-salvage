import * as vscode from 'vscode';
import { JobManagerService } from '../service/JobManagerService';

export function registerStartJobCommand(
  context: vscode.ExtensionContext,
  jobManager: JobManagerService,
  onJobsChanged: () => void,
): vscode.Disposable {
  return vscode.commands.registerCommand('mariadbProfiler.startJob', async () => {
    const jobKey = await vscode.window.showInputBox({
      prompt: 'Enter a job key (leave empty for auto-generated key)',
      placeHolder: 'my-profiling-session',
    });

    // User cancelled
    if (jobKey === undefined) { return; }

    try {
      const key = await jobManager.startJob(jobKey || undefined);
      vscode.window.showInformationMessage(`Profiling job '${key}' started`);
      onJobsChanged();
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : String(e);
      vscode.window.showErrorMessage(`Failed to start job: ${msg}`);
    }
  });
}
