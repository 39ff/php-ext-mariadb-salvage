# VSCode Extension 実装計画: MariaDB Profiler Viewer

## 概要

`php-ext-mariadb-salvage` が生成するクエリプロファイリングデータを Visual Studio Code 上でわかりやすく可視化・操作する拡張機能を作成する。

既存の JetBrains Plugin (`jetbrains-plugin/`) と同等の機能を VSCode で実現し、PhpStorm 以外の開発環境でもプロファイリングデータを活用可能にする。

**UI 方針: Webview を使用せず、VSCode ネイティブ API のみで構成する。** 軽量・高速・テーマ完全統合を優先する。

---

## JetBrains Plugin との機能対応表

| 機能 | JetBrains Plugin | VSCode Extension |
|------|------------------|------------------|
| クエリログビューア | Swing JTable (QueryLogPanel) | **TreeView** (クエリ一覧、展開で詳細表示) |
| クエリ詳細表示 | Swing JTextArea + HTML (QueryDetailPanel) | **Virtual Document** (`.sql` として開き、シンタックスハイライト自動適用) |
| ジョブマネージャ | Swing JList (JobListPanel) | **TreeView** (Native VSCode API) |
| バックトレースナビゲーション | OpenFileDescriptor (BacktracePanel) | TreeView 子要素 + `vscode.workspace.openTextDocument` |
| リアルタイム監視 | Timer + FileWatcher (LiveTailPanel) | **OutputChannel** (`vscode.window.createOutputChannel`) |
| 統計ダッシュボード | Graphics2D バーチャート (StatisticsPanel) | **TreeView** (Unicode バーチャート `████`) |
| 設定 | IntelliJ Configurable (ProfilerConfigurable) | `contributes.configuration` (VSCode Settings) |
| IDE アクション | AnAction (Start/Stop/Open) | `contributes.commands` + コマンドパレット |
| フレームリゾルバ | Groovy スクリプト (FrameResolverService) | JavaScript スクリプト (`vm` モジュール) |
| エラーログ | ErrorLogPanel | **OutputChannel** (`vscode.window.createOutputChannel`) |
| パスマッピング | ProfilerState テキスト設定 | VSCode Settings (JSON 形式) |
| フィルタ・検索 | テーブル上のフィルタバー | **QuickPick** (コマンドパレット) + TreeView `view/title` メニュー |

---

## 技術スタック

| 項目 | 選択 | 理由 |
|------|------|------|
| 言語 | TypeScript | VSCode Extension 標準言語 |
| ビルド | esbuild (バンドル) + tsc (型チェック) | 高速ビルド & 小バンドルサイズ |
| UI | VSCode ネイティブ API のみ | Webview 不使用、依存ゼロ、省メモリ、テーマ完全統合 |
| JSON パース | ネイティブ `JSON.parse` | 追加依存不要 |
| ファイル監視 | `fs.watchFile` (ポーリング) | Docker ボリューム対応 |
| テスト | Vitest (ユニット) + @vscode/test-electron (統合) | 高速・設定簡易 |
| パッケージ | `@vscode/vsce` | VSCode Marketplace 公式ツール |

---

## ディレクトリ構成

```
vscode-extension/
├── package.json                          # Extension マニフェスト
├── tsconfig.json                         # TypeScript 設定
├── esbuild.mjs                           # ビルドスクリプト
├── .vscodeignore                         # パッケージ除外設定
├── README.md                             # Marketplace 用ドキュメント
│
├── src/
│   ├── extension.ts                      # Extension エントリポイント (activate/deactivate)
│   │
│   ├── model/                            # データモデル
│   │   ├── QueryEntry.ts                 # クエリログエントリ
│   │   ├── JobInfo.ts                    # ジョブ情報
│   │   └── BacktraceFrame.ts            # バックトレースフレーム
│   │
│   ├── service/                          # サービス層
│   │   ├── LogParserService.ts           # JSONL パーサ (オフセット対応)
│   │   ├── JobManagerService.ts          # jobs.json 読み書き + CLI 連携
│   │   ├── StatisticsService.ts          # クエリ統計計算
│   │   ├── FileWatcherService.ts         # ログファイル変更検知
│   │   └── FrameResolverService.ts       # JS スクリプトによるフレーム解決
│   │
│   ├── provider/                         # VSCode UI プロバイダ
│   │   ├── JobTreeProvider.ts            # TreeView: ジョブ一覧
│   │   ├── QueryTreeProvider.ts          # TreeView: クエリ一覧 (展開で詳細)
│   │   ├── StatisticsTreeProvider.ts     # TreeView: 統計ダッシュボード
│   │   └── QueryDocumentProvider.ts      # Virtual Document: SQL 詳細表示
│   │
│   ├── command/                          # VSCode コマンド
│   │   ├── startJob.ts                   # ジョブ開始
│   │   ├── stopJob.ts                    # ジョブ停止
│   │   ├── openLog.ts                    # ログファイルを開く
│   │   ├── filterQueries.ts             # クエリフィルタ (QuickPick)
│   │   └── searchQueries.ts             # クエリ検索 (QuickPick)
│   │
│   └── util/                             # ユーティリティ
│       ├── pathMapping.ts                # Docker パスマッピング
│       └── queryUtils.ts                 # SQL 短縮・パラメータバインド
│
└── test/
    ├── unit/                             # ユニットテスト (Vitest)
    │   ├── LogParserService.test.ts
    │   ├── JobManagerService.test.ts
    │   ├── StatisticsService.test.ts
    │   └── QueryEntry.test.ts
    │
    └── integration/                      # 統合テスト (@vscode/test-electron)
        └── extension.test.ts
```

**Webview 版との差分:**
- `webview/` ディレクトリが不要 (HTML/CSS/JS ビルドパイプラインなし)
- `ProfilerWebviewProvider.ts` → `QueryTreeProvider.ts` + `StatisticsTreeProvider.ts` + `QueryDocumentProvider.ts` に分解
- フィルタ・検索は `command/` 配下に QuickPick ベースで実装

---

## Extension マニフェスト (package.json 設計)

```jsonc
{
  "name": "mariadb-profiler-viewer",
  "displayName": "MariaDB Profiler Viewer",
  "description": "Visualize and analyze MariaDB/MySQL query profiling data from php-ext-mariadb-salvage",
  "version": "0.1.0",
  "publisher": "mariadb-profiler",
  "engines": { "vscode": "^1.85.0" },
  "categories": ["Other", "Debuggers"],
  "activationEvents": [],

  "main": "./dist/extension.js",

  "contributes": {
    // Activity Bar コンテナ
    "viewsContainers": {
      "activitybar": [{
        "id": "mariadb-profiler",
        "title": "MariaDB Profiler",
        "icon": "resources/icons/profiler.svg"
      }]
    },

    // 全て TreeView (Webview なし)
    "views": {
      "mariadb-profiler": [
        {
          "id": "mariadbProfiler.jobs",
          "name": "Jobs",
          "type": "tree"
        },
        {
          "id": "mariadbProfiler.queries",
          "name": "Queries",
          "type": "tree"
        },
        {
          "id": "mariadbProfiler.statistics",
          "name": "Statistics",
          "type": "tree"
        }
      ]
    },

    // コマンド
    "commands": [
      { "command": "mariadbProfiler.startJob",       "title": "Start Profiling Job",   "category": "MariaDB Profiler", "icon": "$(play)" },
      { "command": "mariadbProfiler.stopJob",        "title": "Stop Profiling Job",    "category": "MariaDB Profiler", "icon": "$(debug-stop)" },
      { "command": "mariadbProfiler.openLog",        "title": "Open Profiler Log",     "category": "MariaDB Profiler", "icon": "$(folder-opened)" },
      { "command": "mariadbProfiler.refresh",        "title": "Refresh",               "category": "MariaDB Profiler", "icon": "$(refresh)" },
      { "command": "mariadbProfiler.filterByType",   "title": "Filter by Query Type",  "category": "MariaDB Profiler", "icon": "$(filter)" },
      { "command": "mariadbProfiler.filterByTag",    "title": "Filter by Tag",         "category": "MariaDB Profiler", "icon": "$(tag)" },
      { "command": "mariadbProfiler.searchQuery",    "title": "Search Queries",        "category": "MariaDB Profiler", "icon": "$(search)" },
      { "command": "mariadbProfiler.clearFilter",    "title": "Clear Filters",         "category": "MariaDB Profiler", "icon": "$(clear-all)" },
      { "command": "mariadbProfiler.showQuerySql",   "title": "Show Full SQL",         "category": "MariaDB Profiler", "icon": "$(open-preview)" },
      { "command": "mariadbProfiler.startLiveTail",  "title": "Start Live Tail",       "category": "MariaDB Profiler", "icon": "$(eye)" },
      { "command": "mariadbProfiler.stopLiveTail",   "title": "Stop Live Tail",        "category": "MariaDB Profiler", "icon": "$(eye-closed)" }
    ],

    // ツールバーボタン
    "menus": {
      "view/title": [
        { "command": "mariadbProfiler.startJob",     "when": "view == mariadbProfiler.jobs",    "group": "navigation" },
        { "command": "mariadbProfiler.stopJob",      "when": "view == mariadbProfiler.jobs",    "group": "navigation" },
        { "command": "mariadbProfiler.refresh",      "when": "view == mariadbProfiler.jobs",    "group": "navigation" },
        { "command": "mariadbProfiler.filterByType", "when": "view == mariadbProfiler.queries", "group": "navigation" },
        { "command": "mariadbProfiler.filterByTag",  "when": "view == mariadbProfiler.queries", "group": "navigation" },
        { "command": "mariadbProfiler.searchQuery",  "when": "view == mariadbProfiler.queries", "group": "navigation" },
        { "command": "mariadbProfiler.clearFilter",  "when": "view == mariadbProfiler.queries", "group": "navigation" },
        { "command": "mariadbProfiler.startLiveTail","when": "view == mariadbProfiler.queries", "group": "2_liveTail" },
        { "command": "mariadbProfiler.stopLiveTail", "when": "view == mariadbProfiler.queries", "group": "2_liveTail" }
      ],
      "view/item/context": [
        { "command": "mariadbProfiler.showQuerySql", "when": "view == mariadbProfiler.queries && viewItem == queryEntry", "group": "inline" }
      ]
    },

    // 設定
    "configuration": {
      "title": "MariaDB Profiler",
      "properties": {
        "mariadbProfiler.logDirectory": {
          "type": "string",
          "default": "/tmp/mariadb_profiler",
          "description": "Directory where profiler writes jobs.json and *.jsonl files"
        },
        "mariadbProfiler.phpPath": {
          "type": "string",
          "default": "php",
          "description": "Path to PHP executable for CLI operations"
        },
        "mariadbProfiler.cliScriptPath": {
          "type": "string",
          "default": "",
          "description": "Path to mariadb_profiler.php CLI tool (auto-detected from workspace if empty)"
        },
        "mariadbProfiler.maxQueries": {
          "type": "number",
          "default": 10000,
          "description": "Maximum number of queries to display"
        },
        "mariadbProfiler.refreshInterval": {
          "type": "number",
          "default": 5,
          "description": "Auto-refresh interval in seconds"
        },
        "mariadbProfiler.tailBufferSize": {
          "type": "number",
          "default": 500,
          "description": "Number of lines to keep in live tail buffer"
        },
        "mariadbProfiler.pathMappings": {
          "type": "object",
          "default": {},
          "description": "Path mappings for Docker environments (container path -> local path)",
          "additionalProperties": { "type": "string" }
        },
        "mariadbProfiler.frameResolverScript": {
          "type": "string",
          "default": "",
          "description": "JavaScript code for custom frame resolution (receives trace, tag, query variables)"
        }
      }
    }
  }
}
```

---

## 主要データフロー

```
[PHP Extension]
     |
     +-- /var/profiler/jobs.json        --> JobManagerService  --> JobTreeProvider (TreeView)
     |                                                                   |
     |                                                                   +--> QueryTreeProvider (TreeView)
     |                                                                          |
     +-- /var/profiler/<key>.jsonl       --> LogParserService  -+                |
     |                                     (offset read)       |                +--> QueryDocumentProvider
     |                                                         |                |      (Virtual Document .sql)
     |                                                         |                |
     |                                                         |                +--> vscode.openTextDocument
     |                                                         |                       (backtrace jump)
     |                                                         |
     |                                                         +--> StatisticsService
     |                                                                   |
     |                                                                   +--> StatisticsTreeProvider (TreeView)
     |
     +-- /var/profiler/<key>.raw.log    --> FileWatcherService  --> OutputChannel (Live Tail)

[CLI Tool] <-- startJob / stopJob commands (child_process.execFile)
```

---

## UI レイアウト

### サイドバー全体像

```
+--------------------------------------------------------------+
| (i) MariaDB Profiler                                          |
+--------------------------------------------------------------+
| JOBS                                     [>] [#] [refresh]    |
|                                                                |
| * job-abc123  42 queries, 3.2s                                |
| * job-def456  18 queries, 1.1s                                |
| o job-ghi789  156 queries, 45.0s                              |
| o job-jkl012  73 queries, 12.3s                               |
|                                                                |
+--------------------------------------------------------------+
| QUERIES  [filter] [tag] [search] [clear]  Filter: SELECT      |
|                                                                |
| > SELECT  SELECT u.* FROM us...        [api] 14:23:01         |
| v INSERT  INSERT INTO logs ...         [api] 14:23:01         |
|   +-- Tables: logs                                            |
|   +-- Tags: api                                               |
|   +-- Params: ?1 = 1                                          |
|   +-- Backtrace:                                              |
|       > LogService.php:28  log()                 <- click     |
|         Router.php:128     dispatch()            <- click     |
|   +-- [Show Full SQL]                                         |
| > SELECT  SELECT p.*, u.name...        [web] 14:23:02         |
| > UPDATE  UPDATE users SET l...        [web] 14:23:02         |
|                                                                |
+--------------------------------------------------------------+
| STATISTICS                                                     |
|                                                                |
| Total Queries: 156                                            |
|                                                                |
| v Query Types                                                 |
|   SELECT  ||||||||||||||||||||||||          78 (50%)           |
|   INSERT  ||||||||                         12 (8%)            |
|   UPDATE  ||||||                            8 (5%)            |
|   DELETE  ||                                2 (1%)            |
|                                                                |
| v Top Tables                                                  |
|   users      ||||||||||||||||||||           45 (29%)          |
|   posts      ||||||||||||||||              30 (19%)           |
|   comments   ||||||||||                    18 (12%)           |
|   logs       ||||                           7 (4%)            |
|                                                                |
| v Top Tags                                                    |
|   api        ||||||||||||||||||||||||       60 (38%)          |
|   web        ||||||||||||                  30 (19%)           |
|   cron       ||||                          10 (6%)            |
|                                                                |
+--------------------------------------------------------------+
```

### エディタ領域 (Virtual Document)

クエリを選択して "Show Full SQL" すると、エディタタブとして SQL が開く:

```
+--------------------------------------------------------------+
| [x] Query #3 - SELECT (mariadb-profiler)                      |
+--------------------------------------------------------------+
| -- Job: job-abc123                                            |
| -- Time: 2025-01-23 14:23:02.000                             |
| -- Tags: web                                                  |
| -- Status: OK                                                 |
|                                                                |
| SELECT p.*, u.name                                            |
| FROM posts p                                                  |
| JOIN users u ON u.id = p.user_id                              |
| WHERE u.active = ?                                            |
|                                                                |
| -- Bound Parameters:                                          |
| -- ?1 = 1                                                     |
|                                                                |
| -- Backtrace:                                                 |
| -- #0 /app/Http/Controllers/UserController.php:42             |
| -- #1 /vendor/laravel/framework/.../Router.php:128            |
| -- #2 /public/index.php:15                                    |
+--------------------------------------------------------------+
```

- `.sql` として登録するため、VSCode の SQL シンタックスハイライトが自動適用
- メタデータ (ジョブ、タイムスタンプ、パラメータ、バックトレース) は SQL コメント (`--`) として記述
- 読み取り専用 (`TextDocumentContentProvider`)

### OutputChannel (Live Tail)

```
+--------------------------------------------------------------+
| OUTPUT                    [MariaDB Profiler Live Tail v]       |
+--------------------------------------------------------------+
| [2025-01-23 14:23:01.000] OK [api] SELECT u.* FROM users...  |
|   #0 /app/Http/Controllers/UserController.php:42              |
|   #1 /vendor/laravel/framework/.../Router.php:128             |
|                                                                |
| [2025-01-23 14:23:01.050] OK [api] INSERT INTO logs ...       |
|   #0 /app/Services/LogService.php:28                          |
|                                                                |
| [2025-01-23 14:23:02.100] OK [web] UPDATE users SET ...       |
|   #0 /app/Http/Controllers/AuthController.php:95              |
+--------------------------------------------------------------+
```

- `vscode.window.createOutputChannel("MariaDB Profiler Live Tail")` で作成
- `.show(true)` でフォーカスを奪わずに表示
- `appendLine()` でリアルタイム追記
- `clear()` でバッファクリア

---

## 実装詳細

### データモデル

#### QueryEntry.ts

```typescript
export interface BacktraceFrame {
  file: string;
  line: number;
  call: string;
  function?: string;
  class?: string;
}

export interface QueryEntry {
  query: string;
  timestamp: string;
  jobKey?: string;
  status?: string;
  tag?: string;
  params?: string[];
  trace?: BacktraceFrame[];
}

// 算出プロパティはユーティリティ関数として提供
export function getQueryType(entry: QueryEntry): 'SELECT' | 'INSERT' | 'UPDATE' | 'DELETE' | 'OTHER';
export function getBoundQuery(entry: QueryEntry): string;
export function getTables(entry: QueryEntry): string[];
export function getShortSql(entry: QueryEntry, maxLen?: number): string;
export function getSourceFile(entry: QueryEntry): string | null;
```

#### JobInfo.ts

```typescript
export interface JobInfo {
  key: string;
  startedAt: string;
  endedAt?: string;
  queryCount?: number;
  parent?: string;
  isActive: boolean;
}

export interface JobsFile {
  active: Record<string, JobData>;
  completed: Record<string, JobData>;
}
```

### サービス層

#### LogParserService.ts

JetBrains 版と同等のオフセット対応 JSONL パーサ。

```typescript
export class LogParserService {
  // ファイル全体パース
  parseJsonlFile(filePath: string): QueryEntry[];

  // オフセットからの差分パース (インクリメンタル更新用)
  parseJsonlFileFromOffset(filePath: string, offset: number): {
    entries: QueryEntry[];
    newOffset: number;
  };

  // Raw ログの末尾 N 行読み込み
  readRawLogTail(filePath: string, lines: number): string;

  // Raw ログのオフセット読み込み (Live Tail 用)
  tailRawLog(filePath: string, offset: number): {
    content: string;
    newOffset: number;
  };
}
```

**実装ポイント:**
- `fs.readFileSync` / `fs.openSync` + `fs.readSync` でオフセット読み込み
- JSON パースエラーは行単位でスキップし、OutputChannel にログ
- バッファサイズは設定値 (`tailBufferSize`) に従う

#### JobManagerService.ts

```typescript
export class JobManagerService {
  constructor(private context: vscode.ExtensionContext);

  // jobs.json からジョブ一覧読み込み
  loadJobs(): JobInfo[];

  // アクティブ/完了済みジョブ取得
  getActiveJobs(): JobInfo[];
  getCompletedJobs(): JobInfo[];

  // CLI 経由でジョブ開始/停止
  startJob(): Promise<string>;  // returns jobKey
  stopJob(jobKey: string): Promise<void>;

  // パスヘルパー
  getJsonlPath(jobKey: string): string;
  getRawLogPath(jobKey: string): string;

  // 設定値取得
  private getLogDir(): string;
  private getPhpPath(): string;
  private getCliScriptPath(): string;
}
```

**CLI 連携:**
- `child_process.execFile` で PHP CLI を呼び出し
- タイムアウト: 60 秒
- CLI スクリプトパス: 設定値 → ワークスペースルート `cli/mariadb_profiler.php` の順にフォールバック

#### FileWatcherService.ts

```typescript
export class FileWatcherService implements vscode.Disposable {
  // ファイル監視開始
  watchFile(filePath: string, onChange: () => void): void;

  // 監視停止
  unwatchFile(filePath: string): void;

  // 全監視停止
  dispose(): void;
}
```

**実装方針:**
- `fs.watchFile` (ポーリング、1000ms 間隔) を使用
  - `fs.watch` はネットワークマウントや Docker ボリュームで不安定なため
- ファイルサイズと `mtime` の変更を検知
- `Disposable` パターンで Extension 終了時にクリーンアップ

#### StatisticsService.ts

```typescript
export interface QueryStats {
  totalQueries: number;
  byType: Record<string, number>;     // { SELECT: 78, INSERT: 12, ... }
  byTable: Record<string, number>;    // { users: 45, posts: 30, ... }
  byTag: Record<string, number>;      // { api: 60, web: 30, ... }
}

export class StatisticsService {
  computeStats(entries: QueryEntry[]): QueryStats;
}
```

#### FrameResolverService.ts

JetBrains 版は Groovy スクリプトを使用しているが、VSCode 版では JavaScript に置き換える。

```typescript
export class FrameResolverService {
  // ユーザースクリプトによるフレーム解決
  resolve(entry: QueryEntry): number;  // returns frame index

  // スクリプトのキャッシュ無効化
  invalidateCache(): void;
}
```

**実装方針:**
- Node.js `vm` モジュールでサンドボックス実行
- バインド変数: `trace`, `tag`, `query` (JetBrains 版と同じ)
- 戻り値: ハイライトすべきフレームのインデックス (0 始まり)
- コンパイルエラー / 実行エラーは OutputChannel にログ
- スクリプトキャッシュ: テキスト変更時のみ再コンパイル

**デフォルトスクリプト例:**
```javascript
// trace: Array<{file, line, call, function, class_name}>
// tag: string, query: string
// Return: frame index to highlight (0-based)

const tagDepthMap = {
  'api': 1,
  'web': 1,
  'cron': 0,
};

if (tag && tagDepthMap[tag] !== undefined) {
  return tagDepthMap[tag];
}
return 0;
```

### UI プロバイダ

#### JobTreeProvider.ts

VSCode ネイティブ TreeView でジョブ一覧を表示。

```typescript
export class JobTreeProvider implements vscode.TreeDataProvider<JobTreeItem> {
  private _onDidChangeTreeData = new vscode.EventEmitter<void>();
  readonly onDidChangeTreeData = this._onDidChangeTreeData.event;

  refresh(): void;
  getTreeItem(element: JobTreeItem): vscode.TreeItem;
  getChildren(element?: JobTreeItem): JobTreeItem[];
}

export class JobTreeItem extends vscode.TreeItem {
  constructor(public readonly job: JobInfo);
}
```

**表示:**
- アイコン: `$(circle-filled)` (アクティブ) / `$(circle-outline)` (完了)
- ラベル: `job.key` (先頭 12 文字に短縮)
- 説明 (description): `"42 queries, 3.2s"` 形式
- コンテキスト値: `activeJob` / `completedJob` (コンテキストメニュー制御用)
- クリック時: `QueryTreeProvider` と `StatisticsTreeProvider` をそのジョブのデータで更新

#### QueryTreeProvider.ts

クエリ一覧を TreeView で表示。展開すると詳細情報を表示。

```typescript
export class QueryTreeProvider implements vscode.TreeDataProvider<QueryTreeItem> {
  private _onDidChangeTreeData = new vscode.EventEmitter<void>();
  readonly onDidChangeTreeData = this._onDidChangeTreeData.event;

  // ジョブのクエリデータをロード
  loadEntries(entries: QueryEntry[]): void;

  // フィルタ・検索
  setFilter(queryType: string | null): void;
  setTagFilter(tag: string | null): void;
  setSearchText(text: string | null): void;
  clearFilters(): void;

  // TreeDataProvider
  getTreeItem(element: QueryTreeItem): vscode.TreeItem;
  getChildren(element?: QueryTreeItem): QueryTreeItem[];
}

type QueryTreeItem = QueryEntryItem | QueryMetadataItem | BacktraceFrameItem;

// 第1階層: クエリエントリ (折りたたみ可能)
export class QueryEntryItem extends vscode.TreeItem {
  contextValue = 'queryEntry';
  // ラベル:   "SELECT  SELECT u.* FROM us..."
  // 説明:     "[api] 14:23:01"
  // アイコン:  クエリ種別に応じた色 (ThemeIcon)
  //           SELECT=$(database), INSERT=$(add), UPDATE=$(edit), DELETE=$(trash)
}

// 第2階層: メタデータ (展開時に表示)
export class QueryMetadataItem extends vscode.TreeItem {
  // "Tables: users, posts"
  // "Tags: api, v2"
  // "Params: ?1 = 1"
  // "Status: OK"
}

// 第2階層: バックトレースフレーム (クリックでファイルジャンプ)
export class BacktraceFrameItem extends vscode.TreeItem {
  // ラベル:  "UserController.php:42  getUserPosts()"
  // アイコン: $(arrow-right) (通常) / $(arrow-right) + 緑色 (解決済みフレーム)
  // command: vscode.open (クリックでエディタにジャンプ)
}
```

**TreeView の description を活用したカラム風表示:**
```
  [icon] SELECT u.* FROM us...          [api] 14:23:01
  ^^^^^  ^^^^^^^^^^^^^^^^^^^^^^          ^^^^^^^^^^^^^
  icon   label                           description
```

TreeItem の `label` にクエリ SQL (短縮)、`description` にタグ+時刻を設定することで、擬似的な 2 カラム表示を実現。

#### StatisticsTreeProvider.ts

統計情報を TreeView で表示。Unicode バーチャートで視覚化。

```typescript
export class StatisticsTreeProvider implements vscode.TreeDataProvider<StatTreeItem> {
  private _onDidChangeTreeData = new vscode.EventEmitter<void>();
  readonly onDidChangeTreeData = this._onDidChangeTreeData.event;

  // 統計データ更新
  updateStats(stats: QueryStats): void;

  // TreeDataProvider
  getTreeItem(element: StatTreeItem): vscode.TreeItem;
  getChildren(element?: StatTreeItem): StatTreeItem[];
}

type StatTreeItem = StatCategoryItem | StatBarItem;

// 第1階層: カテゴリ (折りたたみ可能)
export class StatCategoryItem extends vscode.TreeItem {
  // "Total Queries: 156"
  // "Query Types" (collapsible)
  // "Top Tables"  (collapsible)
  // "Top Tags"    (collapsible)
}

// 第2階層: 個別統計 (Unicode バーチャート)
export class StatBarItem extends vscode.TreeItem {
  // ラベル: "SELECT  ████████████████████████"
  // 説明:   "78 (50%)"
}
```

**Unicode バーチャート生成:**
```typescript
function generateBar(value: number, max: number, barWidth: number = 24): string {
  const filled = Math.round((value / max) * barWidth);
  return '█'.repeat(filled) + '░'.repeat(barWidth - filled);
}

// 例: generateBar(78, 156, 24) → "████████████░░░░░░░░░░░░"
```

#### QueryDocumentProvider.ts

Virtual Document で SQL 詳細を表示。

```typescript
export class QueryDocumentProvider implements vscode.TextDocumentContentProvider {
  // URI スキーム: "mariadb-profiler"
  static readonly scheme = 'mariadb-profiler';

  provideTextDocumentContent(uri: vscode.Uri): string;

  // クエリエントリを Virtual Document として開く
  showQueryDetail(entry: QueryEntry, index: number): void;
}
```

**URI 設計:**
```
mariadb-profiler:query-3.sql?job=abc123&index=3
```

- `.sql` 拡張子 → VSCode が SQL 言語モードを自動適用
- `TextDocumentContentProvider` のため読み取り専用
- ドキュメント内容: SQL + パラメータ + バックトレース (全て SQL コメントで装飾)

**利点:**
- SQL シンタックスハイライトが無料で得られる (VSCode 組み込み)
- ユーザーが好みの SQL Extension を入れていればそれも適用される
- `editor.wordWrap` など通常のエディタ設定が有効

---

## フィルタ・検索の UI フロー

Webview ではフィルタバーを常時表示できるが、ネイティブ API ではそれができない。
代わりに以下の方法で操作性を確保する:

### 1. QuickPick によるフィルタ

```
[コマンドパレット or ツールバーボタン]
  ↓
+------------------------------------------+
| Filter by Query Type                      |
+------------------------------------------+
| > All (clear filter)                      |
|   SELECT (78 queries)                     |
|   INSERT (12 queries)                     |
|   UPDATE (8 queries)                      |
|   DELETE (2 queries)                      |
+------------------------------------------+
```

### 2. QuickPick によるタグフィルタ

```
+------------------------------------------+
| Filter by Tag                             |
+------------------------------------------+
| > All (clear filter)                      |
|   api (60 queries)                        |
|   web (30 queries)                        |
|   cron (10 queries)                       |
+------------------------------------------+
```

### 3. InputBox による検索

```
+------------------------------------------+
| Search queries (SQL text)                 |
+------------------------------------------+
| users                                     |
+------------------------------------------+
```

### 4. 現在のフィルタ状態表示

TreeView のタイトル (view/title) の `description` でフィルタ状態を表示:
- `"QUERIES (SELECT, tag:api, search:'users')"` のように表示
- StatusBar アイテムでも現在のフィルタ状態を表示可能

---

## 実装ステップ

### Phase 1: 基盤構築

1. **プロジェクトスキャフォールド**
   - `package.json`, `tsconfig.json`, `esbuild.mjs` 作成
   - `.vscodeignore`, `.eslintrc.json` 設定
   - `npm init` + 依存パッケージインストール

2. **Extension エントリポイント**
   - `extension.ts` に `activate()` / `deactivate()` 実装
   - コマンド登録、全 TreeView 登録、OutputChannel 登録
   - `TextDocumentContentProvider` 登録

3. **データモデル定義**
   - `QueryEntry.ts`, `JobInfo.ts`, `BacktraceFrame.ts`
   - ユーティリティ関数 (queryType, boundQuery, tables, shortSql)

4. **設定スキーマ**
   - `package.json` の `contributes.configuration` 定義

### Phase 2: コア機能 - クエリログビューア

5. **LogParserService**
   - JSONL ファイルパーサ実装
   - オフセット対応の差分読み込み

6. **JobManagerService**
   - `jobs.json` 読み込み
   - アクティブ/完了済みジョブ分類

7. **JobTreeProvider**
   - TreeView でジョブ一覧表示
   - ジョブ選択イベント発火

8. **QueryTreeProvider**
   - TreeView でクエリ一覧表示
   - 展開でメタデータ (テーブル、タグ、パラメータ) 表示
   - 展開でバックトレース表示

9. **QueryDocumentProvider**
   - Virtual Document (`.sql`) でフル SQL 表示
   - SQL コメントでメタデータ・バックトレース記述

### Phase 3: フィルタ・ナビゲーション & Live Tail

10. **フィルタ・検索コマンド**
    - `filterByType`: QuickPick でクエリ種別フィルタ
    - `filterByTag`: QuickPick でタグフィルタ
    - `searchQuery`: InputBox でテキスト検索
    - `clearFilter`: 全フィルタクリア

11. **バックトレースナビゲーション**
    - `BacktraceFrameItem` クリックで `vscode.workspace.openTextDocument` + `showTextDocument`
    - パスマッピング適用

12. **FrameResolverService**
    - JavaScript (`vm` モジュール) によるフレーム解決
    - デフォルトスクリプト提供
    - 解決済みフレームに `$(arrow-right)` + 緑色アイコン

13. **FileWatcherService**
    - `fs.watchFile` によるポーリング監視
    - 変更検知コールバック

14. **Live Tail (OutputChannel)**
    - `vscode.window.createOutputChannel("MariaDB Profiler Live Tail")`
    - `startLiveTail` / `stopLiveTail` コマンド
    - `appendLine()` でリアルタイム追記
    - バッファサイズ超過時に `clear()` + 再出力

### Phase 4: 統計 & CLI 連携

15. **StatisticsService**
    - クエリ統計計算

16. **StatisticsTreeProvider**
    - TreeView で統計表示
    - Unicode バーチャート (`████░░░░`) で視覚化
    - カテゴリ: クエリ種別分布、テーブル別頻度、タグ別頻度

17. **CLI コマンド統合**
    - `startJob` / `stopJob` コマンド実装
    - `child_process.execFile` で PHP CLI 呼び出し

18. **OpenLog コマンド**
    - ファイルピッカーで `.jsonl` ファイル選択

### Phase 5: 品質 & 仕上げ

19. **ユニットテスト**
    - LogParserService, JobManagerService, StatisticsService, QueryEntry のテスト
    - Vitest で実行

20. **統合テスト**
    - @vscode/test-electron で Extension 全体テスト

21. **アイコン・UI 微調整**
    - SVG アイコン作成 (JetBrains 版を参考)
    - ThemeIcon カラー設定

22. **ドキュメント**
    - README.md (Marketplace 用)
    - CHANGELOG.md

---

## Webview 版との比較

### メリット (ネイティブ API 方式)

| 項目 | 詳細 |
|------|------|
| **依存ゼロ** | HTML/CSS/JS ビルドパイプライン不要。Webview フレームワーク (Preact/React) 不要 |
| **軽量** | Chromium プロセスを起動しないため省メモリ |
| **テーマ完全統合** | ネイティブ UI は VSCode テーマに自動追従。CSS 変数のマッピング不要 |
| **実装が速い** | Extension ↔ Webview 間の `postMessage` 通信プロトコル設計が不要 |
| **Remote SSH / WSL** | Webview より安定動作 |
| **ビルド簡易** | esbuild で Extension のみバンドル。Webview 用の別ビルド不要 |
| **セキュリティ** | CSP 設定、nonce 管理、`localResourceRoots` 管理が不要 |

### デメリット・制約

| 項目 | 詳細 | 緩和策 |
|------|------|--------|
| **テーブル表示** | TreeView にカラム幅調整・ソート・水平スクロールがない | `label` + `description` で擬似 2 カラム表示 |
| **統計チャート** | 棒グラフは Unicode 文字 (`████`) での近似表現 | 十分視覚的に分かりやすい |
| **フィルタ UI** | 常時表示のフィルタバーが作れない | QuickPick + ツールバーボタン + ステータスバー表示 |
| **レイアウト** | スプリットペイン・複雑なレイアウト不可 | サイドバー 3 セクション構成で十分 |
| **仮想スクロール** | TreeView は VSCode が管理 (10,000 件でもパフォーマンス良好) | `maxQueries` 設定で上限制御 |

---

## JetBrains Plugin との差異・VSCode 固有の考慮事項

### 1. UI アーキテクチャ

| 観点 | JetBrains | VSCode |
|------|-----------|--------|
| UI 描画 | Swing (ネイティブ Java UI) | VSCode TreeView + Virtual Document + OutputChannel |
| テーブル | JTable + TableModel | TreeView (展開式) |
| チャート | Graphics2D | Unicode バーチャート (`████░░░░`) |
| スプリットペイン | JSplitPane | サイドバー 3 セクション |
| SQL 表示 | JTextArea + HTML | Virtual Document (.sql) - エディタタブ |
| Live Tail | カスタムパネル | OutputChannel |
| フィルタ | テーブル上のフィルタバー | QuickPick (コマンドパレット) |
| ファイル選択 | JFileChooser | `vscode.window.showOpenDialog` |
| 通知 | Messages.showInfoMessage | `vscode.window.showInformationMessage` |

### 2. パフォーマンス

- **TreeView**: VSCode が内部で仮想化しているため、大量アイテムでもパフォーマンス良好
- **Virtual Document**: エディタの標準パスで動作するため、大きな SQL でも問題なし
- **OutputChannel**: ネイティブテキスト出力のため、高速にログ追記可能

### 3. フレームリゾルバ

- JetBrains 版: **Groovy** スクリプト (JVM 上で実行)
- VSCode 版: **JavaScript** スクリプト (Node.js `vm` モジュール)
- API は同等 (`trace`, `tag`, `query` 変数を提供)

### 4. ファイル監視

- JetBrains 版: IntelliJ VFS イベント + Timer ポーリング
- VSCode 版: `fs.watchFile` ポーリング (Docker ボリューム対応)
- `vscode.workspace.FileSystemWatcher` はワークスペース内のみ対象のため、外部ディレクトリには `fs.watchFile` を使用

### 5. 設定管理

- JetBrains 版: `PersistentStateComponent` + カスタム設定ダイアログ
- VSCode 版: `contributes.configuration` (VSCode 標準 Settings UI で編集)

---

## リスク・課題

| リスク | 影響度 | 対策 |
|--------|--------|------|
| TreeView で大量クエリ表示時のパフォーマンス | 低 | VSCode の TreeView は内部で遅延読み込み。`maxQueries` で上限設定 |
| Docker ボリュームのファイル監視 | 中 | `fs.watchFile` ポーリング (1秒間隔) |
| フィルタ操作の手数 (QuickPick 呼び出し) | 中 | ツールバーボタンで 1 クリックアクセス。キーボードショートカット設定可能 |
| TreeView のカラム表示制限 | 低 | `label` + `description` で十分な情報表示可能 |
| Remote SSH / WSL 環境 | 低 | ネイティブ API のみ使用のため、Webview より安定 |

---

## 将来の拡張可能性

1. **CodeLens 統合** - PHP ファイル上でクエリ実行元行にインラインでクエリ情報表示
2. **Diagnostic 統合** - 重いクエリを Warning として表示
3. **Testing Integration** - テスト実行時の自動プロファイリング
4. **TreeView Drag & Drop** - クエリをエディタにドラッグして SQL 挿入
5. **StatusBar 統合** - アクティブジョブのクエリ数をリアルタイム表示
