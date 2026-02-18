import * as vscode from 'vscode';
import {
  QueryEntry,
  BacktraceFrame,
  getQueryType,
  getShortSql,
  getTables,
  getBoundQuery,
  formatTimestamp,
  QueryType,
} from '../model/QueryEntry';
import { applyPathMappings } from '../util/pathMapping';

type QueryTreeItem = QueryEntryItem | QueryMetadataItem | BacktraceHeaderItem | BacktraceFrameItem;

export class QueryTreeProvider implements vscode.TreeDataProvider<QueryTreeItem> {
  private _onDidChangeTreeData = new vscode.EventEmitter<QueryTreeItem | undefined>();
  readonly onDidChangeTreeData = this._onDidChangeTreeData.event;

  private allEntries: QueryEntry[] = [];
  private filteredEntries: QueryEntry[] = [];
  private typeFilter: string | null = null;
  private tagFilter: string | null = null;
  private searchText: string | null = null;
  private resolvedFrameMap = new Map<number, number>(); // entryIndex -> frameIndex

  loadEntries(entries: QueryEntry[]): void {
    this.allEntries = entries;
    this.applyFilters();
  }

  appendEntries(entries: QueryEntry[]): void {
    this.allEntries.push(...entries);
    this.applyFilters();
  }

  setFilter(queryType: string | null): void {
    this.typeFilter = queryType;
    this.applyFilters();
  }

  setTagFilter(tag: string | null): void {
    this.tagFilter = tag;
    this.applyFilters();
  }

  setSearchText(text: string | null): void {
    this.searchText = text;
    this.applyFilters();
  }

  clearFilters(): void {
    this.typeFilter = null;
    this.tagFilter = null;
    this.searchText = null;
    this.applyFilters();
  }

  hasActiveFilters(): boolean {
    return this.typeFilter !== null || this.tagFilter !== null || this.searchText !== null;
  }

  getFilterDescription(): string {
    const parts: string[] = [];
    if (this.typeFilter) { parts.push(this.typeFilter); }
    if (this.tagFilter) { parts.push(`tag:${this.tagFilter}`); }
    if (this.searchText) { parts.push(`"${this.searchText}"`); }
    return parts.length > 0 ? parts.join(', ') : '';
  }

  getEntries(): QueryEntry[] {
    return this.allEntries;
  }

  getFilteredEntries(): QueryEntry[] {
    return this.filteredEntries;
  }

  setResolvedFrame(entryIndex: number, frameIndex: number): void {
    this.resolvedFrameMap.set(entryIndex, frameIndex);
  }

  getAllTags(): string[] {
    const tags = new Set<string>();
    for (const entry of this.allEntries) {
      if (entry.tag) { tags.add(entry.tag); }
    }
    return [...tags].sort();
  }

  getTreeItem(element: QueryTreeItem): vscode.TreeItem {
    return element;
  }

  getChildren(element?: QueryTreeItem): QueryTreeItem[] {
    if (!element) {
      // Root level: filtered query entries
      return this.filteredEntries.map((entry, index) => {
        const globalIndex = this.allEntries.indexOf(entry);
        return new QueryEntryItem(entry, globalIndex);
      });
    }

    if (element instanceof QueryEntryItem) {
      return this.getQueryChildren(element);
    }

    if (element instanceof BacktraceHeaderItem) {
      return this.getBacktraceChildren(element);
    }

    return [];
  }

  private getQueryChildren(item: QueryEntryItem): QueryTreeItem[] {
    const entry = item.entry;
    const children: QueryTreeItem[] = [];

    // Tables
    const tables = getTables(entry);
    if (tables.length > 0) {
      children.push(new QueryMetadataItem(`Tables: ${tables.join(', ')}`, 'symbol-class'));
    }

    // Tags
    if (entry.tag) {
      children.push(new QueryMetadataItem(`Tag: ${entry.tag}`, 'tag'));
    }

    // Status
    if (entry.status) {
      const icon = entry.status === 'ok' ? 'check' : 'error';
      children.push(new QueryMetadataItem(`Status: ${entry.status}`, icon));
    }

    // Params
    if (entry.params && entry.params.length > 0) {
      const paramStr = entry.params.map((p, i) => `?${i + 1} = ${p === null ? 'NULL' : p}`).join(', ');
      children.push(new QueryMetadataItem(`Params: ${paramStr}`, 'symbol-parameter'));
    }

    // Backtrace
    if (entry.trace && entry.trace.length > 0) {
      children.push(new BacktraceHeaderItem(entry.trace, item.entryIndex, this.resolvedFrameMap.get(item.entryIndex)));
    }

    return children;
  }

  private getBacktraceChildren(item: BacktraceHeaderItem): QueryTreeItem[] {
    return item.frames.map((frame, frameIndex) => {
      const isResolved = item.resolvedFrameIndex === frameIndex;
      return new BacktraceFrameItem(frame, isResolved);
    });
  }

  private applyFilters(): void {
    const maxQueries = vscode.workspace.getConfiguration('mariadbProfiler')
      .get<number>('maxQueries', 10000);

    let entries = this.allEntries;

    if (this.typeFilter) {
      const filter = this.typeFilter;
      entries = entries.filter(e => getQueryType(e) === filter);
    }

    if (this.tagFilter) {
      const tag = this.tagFilter;
      entries = entries.filter(e => e.tag === tag);
    }

    if (this.searchText) {
      const search = this.searchText.toLowerCase();
      entries = entries.filter(e => e.query.toLowerCase().includes(search));
    }

    this.filteredEntries = entries.slice(0, maxQueries);
    this._onDidChangeTreeData.fire(undefined);
  }
}

const QUERY_TYPE_ICONS: Record<QueryType, string> = {
  SELECT: 'database',
  INSERT: 'add',
  UPDATE: 'edit',
  DELETE: 'trash',
  OTHER: 'question',
};

const QUERY_TYPE_COLORS: Record<QueryType, string> = {
  SELECT: 'charts.blue',
  INSERT: 'charts.green',
  UPDATE: 'charts.orange',
  DELETE: 'charts.red',
  OTHER: 'charts.gray',
};

export class QueryEntryItem extends vscode.TreeItem {
  readonly entry: QueryEntry;
  readonly entryIndex: number;

  constructor(entry: QueryEntry, entryIndex: number) {
    const qtype = getQueryType(entry);
    const boundSql = getBoundQuery(entry).replace(/\s+/g, ' ').trim();
    const shortSql = boundSql.length <= 50 ? boundSql : boundSql.substring(0, 47) + '...';
    const label = `${qtype}  ${shortSql}`;

    super(label, vscode.TreeItemCollapsibleState.Collapsed);

    this.entry = entry;
    this.entryIndex = entryIndex;
    this.contextValue = 'queryEntry';

    // Description: [tag] HH:MM:SS.mmm
    const parts: string[] = [];
    if (entry.tag) { parts.push(`[${entry.tag}]`); }
    parts.push(formatTimestamp(entry.timestamp));
    this.description = parts.join(' ');

    this.iconPath = new vscode.ThemeIcon(
      QUERY_TYPE_ICONS[qtype],
      new vscode.ThemeColor(QUERY_TYPE_COLORS[qtype]),
    );

    this.tooltip = new vscode.MarkdownString();
    this.tooltip.appendCodeblock(getBoundQuery(entry), 'sql');
  }
}

export class QueryMetadataItem extends vscode.TreeItem {
  constructor(label: string, icon: string) {
    super(label, vscode.TreeItemCollapsibleState.None);
    this.iconPath = new vscode.ThemeIcon(icon);
    this.contextValue = 'queryMetadata';
  }
}

export class BacktraceHeaderItem extends vscode.TreeItem {
  readonly frames: BacktraceFrame[];
  readonly entryIndex: number;
  readonly resolvedFrameIndex?: number;

  constructor(frames: BacktraceFrame[], entryIndex: number, resolvedFrameIndex?: number) {
    super(`Backtrace (${frames.length} frames)`, vscode.TreeItemCollapsibleState.Collapsed);
    this.frames = frames;
    this.entryIndex = entryIndex;
    this.resolvedFrameIndex = resolvedFrameIndex;
    this.iconPath = new vscode.ThemeIcon('debug-stackframe');
    this.contextValue = 'backtraceHeader';
  }
}

export class BacktraceFrameItem extends vscode.TreeItem {
  constructor(frame: BacktraceFrame, isResolved: boolean) {
    const fileName = frame.file.split('/').pop() || frame.file;
    const label = `${fileName}:${frame.line}`;

    super(label, vscode.TreeItemCollapsibleState.None);

    this.description = frame.call;
    this.contextValue = 'backtraceFrame';

    this.iconPath = new vscode.ThemeIcon(
      'arrow-right',
      isResolved ? new vscode.ThemeColor('charts.green') : undefined,
    );

    // Map path for Docker environments
    const localPath = applyPathMappings(frame.file);

    this.command = {
      command: 'vscode.open',
      title: 'Open File',
      arguments: [
        vscode.Uri.file(localPath),
        { selection: new vscode.Range(frame.line - 1, 0, frame.line - 1, 0) } as vscode.TextDocumentShowOptions,
      ],
    };

    this.tooltip = `${frame.file}:${frame.line}\n${frame.call}`;
  }
}
