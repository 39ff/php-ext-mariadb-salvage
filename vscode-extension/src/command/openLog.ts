import * as vscode from 'vscode';
import { JobManagerService } from '../service/JobManagerService';
import { LogParserService } from '../service/LogParserService';
import { QueryTreeProvider } from '../provider/QueryTreeProvider';

export function registerOpenLogCommand(
  context: vscode.ExtensionContext,
  jobManager: JobManagerService,
  logParser: LogParserService,
  queryTreeProvider: QueryTreeProvider,
): vscode.Disposable {
  return vscode.commands.registerCommand('mariadbProfiler.openLog', async () => {
    const uris = await vscode.window.showOpenDialog({
      canSelectFiles: true,
      canSelectMany: false,
      filters: { 'JSONL files': ['jsonl'] },
      defaultUri: vscode.Uri.file(jobManager.getLogDir()),
      title: 'Open Profiler Log File',
    });

    if (!uris || uris.length === 0) { return; }

    const filePath = uris[0].fsPath;
    const entries = logParser.parseJsonlFile(filePath);
    queryTreeProvider.loadEntries(entries);

    vscode.window.showInformationMessage(`Loaded ${entries.length} queries from log file`);
  });
}
