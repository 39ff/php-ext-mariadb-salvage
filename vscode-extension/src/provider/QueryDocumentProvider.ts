import * as vscode from 'vscode';
import { QueryEntry, getBoundQuery, getQueryType, getTables, formatTimestamp } from '../model/QueryEntry';

const MAX_DOCUMENTS = 50;

export class QueryDocumentProvider implements vscode.TextDocumentContentProvider {
  static readonly scheme = 'mariadb-profiler';

  private _onDidChange = new vscode.EventEmitter<vscode.Uri>();
  readonly onDidChange = this._onDidChange.event;

  private documents = new Map<string, QueryEntry>();
  private insertionOrder: string[] = [];

  provideTextDocumentContent(uri: vscode.Uri): string {
    const entry = this.documents.get(uri.toString());
    if (!entry) { return '-- Query not found'; }

    return this.formatQueryDocument(entry);
  }

  async showQueryDetail(entry: QueryEntry, index: number): Promise<void> {
    const uri = vscode.Uri.parse(
      `${QueryDocumentProvider.scheme}:query-${index}.sql?ts=${entry.timestamp}`
    );

    const key = uri.toString();
    if (this.documents.size >= MAX_DOCUMENTS && !this.documents.has(key)) {
      const oldest = this.insertionOrder.shift();
      if (oldest) { this.documents.delete(oldest); }
    }
    this.documents.set(key, entry);
    this.insertionOrder.push(key);
    this._onDidChange.fire(uri);

    const doc = await vscode.workspace.openTextDocument(uri);
    await vscode.window.showTextDocument(doc, {
      preview: true,
      viewColumn: vscode.ViewColumn.One,
    });
  }

  private formatQueryDocument(entry: QueryEntry): string {
    const lines: string[] = [];

    // Header metadata as SQL comments
    lines.push(`-- Job: ${entry.jobKey}`);
    lines.push(`-- Time: ${new Date(entry.timestamp * 1000).toISOString()}`);
    lines.push(`-- Type: ${getQueryType(entry)}`);

    if (entry.tag) {
      lines.push(`-- Tag: ${entry.tag}`);
    }
    if (entry.status) {
      lines.push(`-- Status: ${entry.status}`);
    }

    const tables = getTables(entry);
    if (tables.length > 0) {
      lines.push(`-- Tables: ${tables.join(', ')}`);
    }

    lines.push('');

    // Main SQL
    lines.push(entry.query);
    lines.push('');

    // Bound parameters
    if (entry.params && entry.params.length > 0) {
      lines.push('-- Bound Parameters:');
      entry.params.forEach((param, i) => {
        lines.push(`-- ?${i + 1} = ${param === null ? 'NULL' : param}`);
      });
      lines.push('');

      // Resolved query
      lines.push('-- Resolved Query:');
      lines.push(`-- ${getBoundQuery(entry).replace(/\n/g, '\n-- ')}`);
      lines.push('');
    }

    // Backtrace
    if (entry.trace && entry.trace.length > 0) {
      lines.push('-- Backtrace:');
      entry.trace.forEach((frame, i) => {
        lines.push(`-- #${i} ${frame.file}:${frame.line}  ${frame.call}`);
      });
    }

    return lines.join('\n');
  }
}
