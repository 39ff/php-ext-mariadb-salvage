import * as vm from 'vm';
import * as vscode from 'vscode';
import { QueryEntry, BacktraceFrame } from '../model/QueryEntry';

export class FrameResolverService {
  private cachedScript: vm.Script | null = null;
  private cachedScriptText: string = '';
  private failedScripts = new Set<string>();
  private errorChannel: vscode.OutputChannel;

  constructor(errorChannel: vscode.OutputChannel) {
    this.errorChannel = errorChannel;
  }

  resolve(entry: QueryEntry): number {
    if (!entry.trace || entry.trace.length === 0) { return 0; }

    const scriptText = vscode.workspace.getConfiguration('mariadbProfiler')
      .get<string>('frameResolverScript', '');

    if (!scriptText) { return 0; }

    if (!vscode.workspace.isTrusted) {
      this.errorChannel.appendLine('[FrameResolver] Workspace is not trusted, skipping script execution');
      return 0;
    }

    try {
      const script = this.getScript(scriptText);
      if (!script) { return 0; }

      const sandbox: Record<string, unknown> = {
        trace: entry.trace.map(f => ({
          file: f.file,
          line: f.line,
          call: f.call,
          function: f.function || '',
          class_name: f.class_name || '',
        })),
        tag: entry.tag || '',
        query: entry.query,
      };

      const context = vm.createContext(sandbox);
      const result = script.runInContext(context, { timeout: 1000 });

      if (Number.isInteger(result) && result >= 0 && result < entry.trace.length) {
        return result;
      }

      return 0;
    } catch (e) {
      this.errorChannel.appendLine(`[FrameResolver] Error: ${e}`);
      return 0;
    }
  }

  invalidateCache(): void {
    this.cachedScript = null;
    this.cachedScriptText = '';
    this.failedScripts.clear();
  }

  private getScript(scriptText: string): vm.Script | null {
    if (this.cachedScriptText === scriptText && this.cachedScript) {
      return this.cachedScript;
    }

    if (this.failedScripts.has(scriptText)) {
      return null;
    }

    try {
      this.cachedScript = new vm.Script(scriptText, { filename: 'frameResolver.js' });
      this.cachedScriptText = scriptText;
      return this.cachedScript;
    } catch (e) {
      this.errorChannel.appendLine(`[FrameResolver] Compile error: ${e}`);
      this.failedScripts.add(scriptText);
      this.cachedScript = null;
      this.cachedScriptText = '';
      return null;
    }
  }
}
