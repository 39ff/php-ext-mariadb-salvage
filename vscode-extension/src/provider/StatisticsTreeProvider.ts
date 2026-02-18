import * as vscode from 'vscode';
import { QueryStats } from '../service/StatisticsService';
import { generateBar, formatPercent } from '../util/queryUtils';

type StatTreeItem = StatHeaderItem | StatCategoryItem | StatBarItem;

export class StatisticsTreeProvider implements vscode.TreeDataProvider<StatTreeItem> {
  private _onDidChangeTreeData = new vscode.EventEmitter<void>();
  readonly onDidChangeTreeData = this._onDidChangeTreeData.event;

  private stats: QueryStats | null = null;

  updateStats(stats: QueryStats): void {
    this.stats = stats;
    this._onDidChangeTreeData.fire();
  }

  clear(): void {
    this.stats = null;
    this._onDidChangeTreeData.fire();
  }

  getTreeItem(element: StatTreeItem): vscode.TreeItem {
    return element;
  }

  getChildren(element?: StatTreeItem): StatTreeItem[] {
    if (!this.stats) {
      return [new StatHeaderItem('No job selected', 'info')];
    }

    if (!element) {
      // Root level
      return [
        new StatHeaderItem(`Total Queries: ${this.stats.totalQueries}`, 'pulse'),
        new StatCategoryItem('Query Types', 'symbol-enum', this.stats.byType, this.stats.totalQueries),
        new StatCategoryItem('Top Tables', 'symbol-class', this.stats.byTable, this.stats.totalQueries),
        new StatCategoryItem('Top Tags', 'tag', this.stats.byTag, this.stats.totalQueries),
      ];
    }

    if (element instanceof StatCategoryItem) {
      return this.getCategoryChildren(element);
    }

    return [];
  }

  private getCategoryChildren(item: StatCategoryItem): StatBarItem[] {
    const entries = Object.entries(item.data);
    if (entries.length === 0) {
      return [new StatBarItem('(none)', '', 0)];
    }

    const maxValue = entries.length > 0 ? entries[0][1] : 0;

    return entries.slice(0, 10).map(([key, value]) => {
      const bar = generateBar(value, maxValue);
      const pct = formatPercent(value, item.total);
      return new StatBarItem(`${key}  ${bar}`, `${value} (${pct})`, value);
    });
  }
}

class StatHeaderItem extends vscode.TreeItem {
  constructor(label: string, icon: string) {
    super(label, vscode.TreeItemCollapsibleState.None);
    this.iconPath = new vscode.ThemeIcon(icon);
    this.contextValue = 'statHeader';
  }
}

class StatCategoryItem extends vscode.TreeItem {
  readonly data: Record<string, number>;
  readonly total: number;

  constructor(label: string, icon: string, data: Record<string, number>, total: number) {
    const count = Object.keys(data).length;
    super(label, count > 0 ? vscode.TreeItemCollapsibleState.Expanded : vscode.TreeItemCollapsibleState.None);
    this.data = data;
    this.total = total;
    this.description = `(${count})`;
    this.iconPath = new vscode.ThemeIcon(icon);
    this.contextValue = 'statCategory';
  }
}

class StatBarItem extends vscode.TreeItem {
  constructor(label: string, description: string, _value: number) {
    super(label, vscode.TreeItemCollapsibleState.None);
    this.description = description;
    this.contextValue = 'statBar';
  }
}
