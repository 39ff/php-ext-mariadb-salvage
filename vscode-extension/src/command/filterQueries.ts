import * as vscode from 'vscode';
import { QueryTreeProvider } from '../provider/QueryTreeProvider';
import { getQueryType, QueryType } from '../model/QueryEntry';

export function registerFilterByTypeCommand(
  context: vscode.ExtensionContext,
  queryTreeProvider: QueryTreeProvider,
): vscode.Disposable {
  return vscode.commands.registerCommand('mariadbProfiler.filterByType', async () => {
    const entries = queryTreeProvider.getEntries();

    // Count queries by type
    const typeCounts: Record<string, number> = {};
    for (const entry of entries) {
      const qtype = getQueryType(entry);
      typeCounts[qtype] = (typeCounts[qtype] || 0) + 1;
    }

    const items: vscode.QuickPickItem[] = [
      { label: 'All', description: `(${entries.length} queries)`, detail: 'Clear type filter' },
    ];

    for (const [type, count] of Object.entries(typeCounts)) {
      items.push({ label: type, description: `(${count} queries)` });
    }

    const selected = await vscode.window.showQuickPick(items, {
      placeHolder: 'Filter by query type',
    });

    if (!selected) { return; }

    if (selected.label === 'All') {
      queryTreeProvider.setFilter(null);
    } else {
      queryTreeProvider.setFilter(selected.label);
    }

    updateFilterContext(queryTreeProvider);
  });
}

export function registerFilterByTagCommand(
  context: vscode.ExtensionContext,
  queryTreeProvider: QueryTreeProvider,
): vscode.Disposable {
  return vscode.commands.registerCommand('mariadbProfiler.filterByTag', async () => {
    const entries = queryTreeProvider.getEntries();
    const tags = queryTreeProvider.getAllTags();

    // Count by tag
    const tagCounts: Record<string, number> = {};
    for (const entry of entries) {
      if (entry.tag) {
        tagCounts[entry.tag] = (tagCounts[entry.tag] || 0) + 1;
      }
    }

    const items: vscode.QuickPickItem[] = [
      { label: 'All', description: `(${entries.length} queries)`, detail: 'Clear tag filter' },
    ];

    for (const tag of tags) {
      items.push({ label: tag, description: `(${tagCounts[tag] || 0} queries)` });
    }

    const selected = await vscode.window.showQuickPick(items, {
      placeHolder: 'Filter by tag',
    });

    if (!selected) { return; }

    if (selected.label === 'All') {
      queryTreeProvider.setTagFilter(null);
    } else {
      queryTreeProvider.setTagFilter(selected.label);
    }

    updateFilterContext(queryTreeProvider);
  });
}

export function registerClearFilterCommand(
  context: vscode.ExtensionContext,
  queryTreeProvider: QueryTreeProvider,
): vscode.Disposable {
  return vscode.commands.registerCommand('mariadbProfiler.clearFilter', () => {
    queryTreeProvider.clearFilters();
    updateFilterContext(queryTreeProvider);
  });
}

export function updateFilterContext(queryTreeProvider: QueryTreeProvider): void {
  vscode.commands.executeCommand(
    'setContext',
    'mariadbProfiler.hasFilter',
    queryTreeProvider.hasActiveFilters(),
  );
}
