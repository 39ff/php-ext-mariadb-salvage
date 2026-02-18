import * as vscode from 'vscode';

export function applyPathMappings(containerPath: string): string {
  const config = vscode.workspace.getConfiguration('mariadbProfiler');
  const mappings = config.get<Record<string, string>>('pathMappings', {});

  for (const [from, to] of Object.entries(mappings)) {
    if (containerPath.startsWith(from)) {
      return containerPath.replace(from, to);
    }
  }

  return containerPath;
}
