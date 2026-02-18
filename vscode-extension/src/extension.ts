import * as vscode from 'vscode';
import { LogParserService } from './service/LogParserService';
import { JobManagerService } from './service/JobManagerService';
import { StatisticsService } from './service/StatisticsService';
import { FileWatcherService } from './service/FileWatcherService';
import { FrameResolverService } from './service/FrameResolverService';
import { JobTreeProvider } from './provider/JobTreeProvider';
import { QueryTreeProvider } from './provider/QueryTreeProvider';
import { StatisticsTreeProvider } from './provider/StatisticsTreeProvider';
import { QueryDocumentProvider } from './provider/QueryDocumentProvider';
import { registerStartJobCommand } from './command/startJob';
import { registerStopJobCommand } from './command/stopJob';
import { registerOpenLogCommand } from './command/openLog';
import {
  registerFilterByTypeCommand,
  registerFilterByTagCommand,
  registerClearFilterCommand,
  updateFilterContext,
} from './command/filterQueries';
import { registerSearchQueryCommand } from './command/searchQueries';
import { LiveTailManager, registerLiveTailCommands } from './command/liveTail';
import { JobInfo } from './model/JobInfo';

export function activate(context: vscode.ExtensionContext): void {
  // --- Output Channels ---
  const errorChannel = vscode.window.createOutputChannel('MariaDB Profiler');
  const liveTailChannel = vscode.window.createOutputChannel('MariaDB Profiler Live Tail');
  context.subscriptions.push(errorChannel, liveTailChannel);

  // --- Services ---
  const logParser = new LogParserService(errorChannel);
  const jobManager = new JobManagerService(errorChannel);
  const statisticsService = new StatisticsService();
  const fileWatcher = new FileWatcherService();
  const frameResolver = new FrameResolverService(errorChannel);
  context.subscriptions.push(fileWatcher);

  // --- UI Providers ---
  const jobTreeProvider = new JobTreeProvider();
  const queryTreeProvider = new QueryTreeProvider();
  const statisticsTreeProvider = new StatisticsTreeProvider();
  const queryDocumentProvider = new QueryDocumentProvider();

  // Register TreeViews
  const jobTreeView = vscode.window.createTreeView('mariadbProfiler.jobs', {
    treeDataProvider: jobTreeProvider,
    showCollapseAll: false,
  });
  const queryTreeView = vscode.window.createTreeView('mariadbProfiler.queries', {
    treeDataProvider: queryTreeProvider,
    showCollapseAll: true,
  });
  const statisticsTreeView = vscode.window.createTreeView('mariadbProfiler.statistics', {
    treeDataProvider: statisticsTreeProvider,
    showCollapseAll: true,
  });
  context.subscriptions.push(jobTreeView, queryTreeView, statisticsTreeView);

  // Register Virtual Document Provider
  context.subscriptions.push(
    vscode.workspace.registerTextDocumentContentProvider(
      QueryDocumentProvider.scheme,
      queryDocumentProvider,
    ),
  );

  // --- Live Tail ---
  const liveTailManager = new LiveTailManager(
    liveTailChannel, fileWatcher, logParser, jobManager,
  );
  context.subscriptions.push(liveTailManager);

  // --- State ---
  let selectedJobKey: string | null = null;
  let jsonlOffset = 0;
  let refreshTimer: ReturnType<typeof setInterval> | undefined;

  // --- Helper: Refresh jobs list ---
  function refreshJobs(): void {
    const jobs = jobManager.loadJobs();
    jobTreeProvider.refresh(jobs);
  }

  // --- Helper: Load queries for a job ---
  function loadJobQueries(jobKey: string): void {
    const jsonlPath = jobManager.getJsonlPath(jobKey);
    const entries = logParser.parseJsonlFile(jsonlPath);

    // Resolve frames
    for (let i = 0; i < entries.length; i++) {
      const frameIndex = frameResolver.resolve(entries[i]);
      if (frameIndex > 0) {
        queryTreeProvider.setResolvedFrame(i, frameIndex);
      }
    }

    queryTreeProvider.loadEntries(entries);
    jsonlOffset = entries.length > 0
      ? require('fs').statSync(jsonlPath).size
      : 0;

    // Update statistics
    const stats = statisticsService.computeStats(entries);
    statisticsTreeProvider.updateStats(stats);

    // Update filter context
    updateFilterContext(queryTreeProvider);
  }

  // --- Helper: Incremental update for active job ---
  function updateActiveJob(): void {
    if (!selectedJobKey) { return; }

    const jsonlPath = jobManager.getJsonlPath(selectedJobKey);
    const result = logParser.parseJsonlFileFromOffset(jsonlPath, jsonlOffset);

    if (result.entries.length > 0) {
      // Resolve frames for new entries
      const startIndex = queryTreeProvider.getEntries().length;
      for (let i = 0; i < result.entries.length; i++) {
        const frameIndex = frameResolver.resolve(result.entries[i]);
        if (frameIndex > 0) {
          queryTreeProvider.setResolvedFrame(startIndex + i, frameIndex);
        }
      }

      queryTreeProvider.appendEntries(result.entries);
      jsonlOffset = result.newOffset;

      // Recompute statistics
      const allEntries = queryTreeProvider.getEntries();
      const stats = statisticsService.computeStats(allEntries);
      statisticsTreeProvider.updateStats(stats);
    }

    // Also refresh jobs list to pick up status changes
    refreshJobs();
  }

  // --- Helper: Start/stop refresh timer ---
  function startRefreshTimer(): void {
    stopRefreshTimer();
    const interval = vscode.workspace.getConfiguration('mariadbProfiler')
      .get<number>('refreshInterval', 5) * 1000;

    refreshTimer = setInterval(() => {
      updateActiveJob();
    }, interval);
  }

  function stopRefreshTimer(): void {
    if (refreshTimer) {
      clearInterval(refreshTimer);
      refreshTimer = undefined;
    }
  }

  // --- Job Selection Handler ---
  const selectJobCmd = vscode.commands.registerCommand(
    'mariadbProfiler.selectJob',
    (job: JobInfo) => {
      selectedJobKey = job.key;
      loadJobQueries(job.key);

      // Set up auto-refresh for active jobs
      if (job.isActive) {
        startRefreshTimer();
      } else {
        stopRefreshTimer();
      }
    },
  );
  context.subscriptions.push(selectJobCmd);

  // --- Show Full SQL Command ---
  const showSqlCmd = vscode.commands.registerCommand(
    'mariadbProfiler.showQuerySql',
    (item: { entry?: import('./model/QueryEntry').QueryEntry; entryIndex?: number }) => {
      if (item?.entry) {
        queryDocumentProvider.showQueryDetail(item.entry, item.entryIndex ?? 0);
      }
    },
  );
  context.subscriptions.push(showSqlCmd);

  // --- Register Commands ---
  context.subscriptions.push(
    registerStartJobCommand(context, jobManager, refreshJobs),
    registerStopJobCommand(context, jobManager, refreshJobs),
    registerOpenLogCommand(context, jobManager, logParser, queryTreeProvider),
    registerFilterByTypeCommand(context, queryTreeProvider),
    registerFilterByTagCommand(context, queryTreeProvider),
    registerClearFilterCommand(context, queryTreeProvider),
    registerSearchQueryCommand(context, queryTreeProvider),
    ...registerLiveTailCommands(context, liveTailManager, jobManager),
  );

  // --- Refresh Command ---
  const refreshCmd = vscode.commands.registerCommand('mariadbProfiler.refresh', () => {
    refreshJobs();
    if (selectedJobKey) {
      loadJobQueries(selectedJobKey);
    }
  });
  context.subscriptions.push(refreshCmd);

  // --- Watch jobs.json for external changes ---
  const jobsJsonPath = jobManager.getJobsJsonPath();
  fileWatcher.watchFile(jobsJsonPath, () => {
    refreshJobs();
  });

  // --- Configuration change handler ---
  const configWatcher = vscode.workspace.onDidChangeConfiguration(e => {
    if (e.affectsConfiguration('mariadbProfiler.frameResolverScript')) {
      frameResolver.invalidateCache();
    }
    if (e.affectsConfiguration('mariadbProfiler.refreshInterval') && selectedJobKey) {
      startRefreshTimer();
    }
  });
  context.subscriptions.push(configWatcher);

  // --- Cleanup on deactivate ---
  context.subscriptions.push({
    dispose: () => {
      stopRefreshTimer();
    },
  });

  // --- Initial Load ---
  refreshJobs();

  errorChannel.appendLine('[MariaDB Profiler] Extension activated');
}

export function deactivate(): void {
  // Cleanup handled by disposables
}
