import * as vscode from 'vscode';
import { JobManagerService } from '../service/JobManagerService';
import { LogParserService } from '../service/LogParserService';
import { StatisticsService } from '../service/StatisticsService';
import { QueryTreeProvider } from '../provider/QueryTreeProvider';
import { StatisticsTreeProvider } from '../provider/StatisticsTreeProvider';
import { updateFilterContext } from './filterQueries';

export function registerOpenLogCommand(
  context: vscode.ExtensionContext,
  jobManager: JobManagerService,
  logParser: LogParserService,
  queryTreeProvider: QueryTreeProvider,
  statisticsService: StatisticsService,
  statisticsTreeProvider: StatisticsTreeProvider,
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

    let entries;
    try {
      entries = logParser.parseJsonlFile(filePath);
    } catch (e) {
      vscode.window.showErrorMessage(`Failed to parse log file: ${e}`);
      return;
    }

    queryTreeProvider.loadEntries(entries);
    updateFilterContext(queryTreeProvider);

    const stats = statisticsService.computeStats(entries);
    statisticsTreeProvider.updateStats(stats);

    vscode.window.showInformationMessage(`Loaded ${entries.length} queries from log file`);
  });
}
