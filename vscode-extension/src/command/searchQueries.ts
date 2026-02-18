import * as vscode from 'vscode';
import { QueryTreeProvider } from '../provider/QueryTreeProvider';
import { updateFilterContext } from './filterQueries';

export function registerSearchQueryCommand(
  context: vscode.ExtensionContext,
  queryTreeProvider: QueryTreeProvider,
): vscode.Disposable {
  return vscode.commands.registerCommand('mariadbProfiler.searchQuery', async () => {
    const text = await vscode.window.showInputBox({
      prompt: 'Search queries by SQL text',
      placeHolder: 'e.g. users, SELECT, WHERE id =',
      value: '',
    });

    // User cancelled
    if (text === undefined) { return; }

    if (text === '') {
      queryTreeProvider.setSearchText(null);
    } else {
      queryTreeProvider.setSearchText(text);
    }

    updateFilterContext(queryTreeProvider);
  });
}
