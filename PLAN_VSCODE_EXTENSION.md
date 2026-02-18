# VSCode Extension 実装計画: MariaDB Profiler Viewer

## 概要

`php-ext-mariadb-salvage` が生成するクエリプロファイリングデータを Visual Studio Code 上でわかりやすく可視化・操作する拡張機能を作成する。

既存の JetBrains Plugin (`jetbrains-plugin/`) と同等の機能を VSCode で実現し、PhpStorm 以外の開発環境でもプロファイリングデータを活用可能にする。

---

## JetBrains Plugin との機能対応表

| 機能 | JetBrains Plugin | VSCode Extension |
|------|------------------|------------------|
| クエリログビューア | Swing JTable (QueryLogPanel) | Webview (React/Preact テーブル) |
| クエリ詳細表示 | Swing JTextArea + HTML (QueryDetailPanel) | Webview (シンタックスハイライト付き SQL 表示) |
| ジョブマネージャ | Swing JList (JobListPanel) | TreeView (Native VSCode API) |
| バックトレースナビゲーション | OpenFileDescriptor (BacktracePanel) | `vscode.workspace.openTextDocument` + `showTextDocument` |
| リアルタイム監視 | Timer + FileWatcher (LiveTailPanel) | `fs.watch` / `vscode.workspace.FileSystemWatcher` |
| 統計ダッシュボード | Graphics2D バーチャート (StatisticsPanel) | Webview (CSS/SVG バーチャート) |
| 設定 | IntelliJ Configurable (ProfilerConfigurable) | `contributes.configuration` (VSCode Settings) |
| IDE アクション | AnAction (Start/Stop/Open) | `contributes.commands` + コマンドパレット |
| フレームリゾルバ | Groovy スクリプト (FrameResolverService) | JavaScript スクリプト (`vm` モジュール) |
| エラーログ | ErrorLogPanel | OutputChannel (`vscode.window.createOutputChannel`) |
| パスマッピング | ProfilerState テキスト設定 | VSCode Settings (JSON 形式) |

---

## 技術スタック

| 項目 | 選択 | 理由 |
|------|------|------|
| 言語 | TypeScript | VSCode Extension 標準言語 |
| ビルド | esbuild (バンドル) + tsc (型チェック) | 高速ビルド & 小バンドルサイズ |
| UI フレームワーク | Webview (Preact + htm) | 軽量、JSX 不要、ビルド簡易 |
| チャート | CSS/SVG ベース | 依存ゼロ、軽量 |
| JSON パース | ネイティブ `JSON.parse` | 追加依存不要 |
| ファイル監視 | `vscode.workspace.FileSystemWatcher` + `fs.watch` | VSCode ネイティブ API |
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
│   │   └── ProfilerWebviewProvider.ts    # WebviewView: メインパネル
│   │
│   ├── command/                          # VSCode コマンド
│   │   ├── startJob.ts                   # ジョブ開始
│   │   ├── stopJob.ts                    # ジョブ停止
│   │   └── openLog.ts                    # ログファイルを開く
│   │
│   └── util/                             # ユーティリティ
│       ├── pathMapping.ts                # Docker パスマッピング
│       └── queryUtils.ts                 # SQL 短縮・パラメータバインド
│
├── webview/                              # Webview UI ソース
│   ├── index.html                        # Webview エントリ HTML
│   ├── main.ts                           # Webview メインスクリプト
│   ├── style.css                         # スタイルシート (VSCode テーマ連動)
│   │
│   ├── components/                       # UI コンポーネント
│   │   ├── App.ts                        # ルートコンポーネント (タブ管理)
│   │   ├── QueryLogTable.ts              # クエリ一覧テーブル
│   │   ├── QueryDetail.ts               # クエリ詳細パネル
│   │   ├── StatisticsView.ts            # 統計ダッシュボード
│   │   ├── LiveTailView.ts              # リアルタイム監視
│   │   └── FilterBar.ts                 # フィルタ・検索バー
│   │
│   └── lib/                              # Webview ユーティリティ
│       ├── vscodeApi.ts                  # VSCode Webview API ラッパー
│       └── sqlHighlight.ts              # 簡易 SQL シンタックスハイライト
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
    // ジョブ一覧 TreeView
    "viewsContainers": {
      "activitybar": [{
        "id": "mariadb-profiler",
        "title": "MariaDB Profiler",
        "icon": "resources/icons/profiler.svg"
      }]
    },
    "views": {
      "mariadb-profiler": [
        {
          "id": "mariadbProfiler.jobs",
          "name": "Jobs",
          "type": "tree"
        },
        {
          "id": "mariadbProfiler.main",
          "name": "Profiler",
          "type": "webview"
        }
      ]
    },

    // コマンド
    "commands": [
      { "command": "mariadbProfiler.startJob",  "title": "Start Profiling Job",  "category": "MariaDB Profiler", "icon": "$(play)" },
      { "command": "mariadbProfiler.stopJob",   "title": "Stop Profiling Job",   "category": "MariaDB Profiler", "icon": "$(debug-stop)" },
      { "command": "mariadbProfiler.openLog",   "title": "Open Profiler Log",    "category": "MariaDB Profiler", "icon": "$(folder-opened)" },
      { "command": "mariadbProfiler.refresh",   "title": "Refresh",              "category": "MariaDB Profiler", "icon": "$(refresh)" }
    ],

    // ツールバーボタン
    "menus": {
      "view/title": [
        { "command": "mariadbProfiler.startJob", "when": "view == mariadbProfiler.jobs", "group": "navigation" },
        { "command": "mariadbProfiler.stopJob",  "when": "view == mariadbProfiler.jobs", "group": "navigation" },
        { "command": "mariadbProfiler.refresh",  "when": "view == mariadbProfiler.jobs", "group": "navigation" }
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
          "description": "Path mappings for Docker environments (container path → local path)",
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
     │
     ├── /var/profiler/jobs.json        ──→  JobManagerService  ──→  JobTreeProvider (TreeView)
     │                                                                     │
     │                                                                     └──→ Webview (タブ切替)
     │
     ├── /var/profiler/<key>.jsonl       ──→  LogParserService   ──→  Webview: QueryLogTable
     │                                        (オフセット読み込み)         │
     │                                                                     ├──→ QueryDetail
     │                                                                     │       │
     │                                                                     │       └──→ vscode.openTextDocument (ジャンプ)
     │                                                                     │
     │                                                                     └──→ StatisticsView
     │
     └── /var/profiler/<key>.raw.log    ──→  FileWatcherService  ──→  Webview: LiveTailView

[CLI Tool] ←── startJob / stopJob コマンド (child_process.execFile)
```

---

## Extension ⇔ Webview 通信プロトコル

VSCode Extension (Host) と Webview 間は `postMessage` で通信する。

### Extension → Webview メッセージ

```typescript
// ジョブ選択時にクエリデータを送信
{ type: 'loadEntries', entries: QueryEntry[], jobKey: string }

// 差分エントリ追加 (インクリメンタル更新)
{ type: 'appendEntries', entries: QueryEntry[] }

// 統計データ更新
{ type: 'updateStats', stats: QueryStats }

// Live Tail データ追加
{ type: 'tailData', lines: string }

// Live Tail クリア
{ type: 'clearTail' }

// フレーム解決結果
{ type: 'resolvedFrame', entryIndex: number, frameIndex: number }
```

### Webview → Extension メッセージ

```typescript
// クエリ選択 (詳細表示 & フレーム解決要求)
{ type: 'selectEntry', index: number }

// バックトレースフレームクリック (エディタジャンプ)
{ type: 'openFile', file: string, line: number }

// フィルタ変更
{ type: 'filterChanged', queryType: string | null, searchText: string }

// タブ切替
{ type: 'tabChanged', tab: 'queryLog' | 'statistics' | 'liveTail' }

// Live Tail 開始/停止
{ type: 'startTail' | 'stopTail' }
```

---

## UI レイアウト

### サイドバー (Activity Bar)

```
┌──────────────────────┐
│ ☰ MariaDB Profiler   │
├──────────────────────┤
│ JOBS        [▶][■][↻]│
│                      │
│ ● job-abc123  (42)   │
│ ● job-def456  (18)   │
│ ○ job-ghi789  (156)  │
│ ○ job-jkl012  (73)   │
│                      │
├──────────────────────┤
│ PROFILER             │
│ (Webview - 下記参照)  │
│                      │
└──────────────────────┘
```

`●` = Active job, `○` = Completed job, `(N)` = クエリ数

### メイン Webview パネル

```
┌──────────────────────────────────────────────────────────────┐
│  [Query Log]  [Statistics]  [Live Tail]                      │
├──────────────────────────────────────────────────────────────┤
│  Filter: [All ▼] [SELECT] [INSERT] [UPDATE] [DELETE]         │
│  Search: [________________________] 🔍                       │
├──────────────────────────────────────────────────────────────┤
│  #   Time       Type    SQL                     Tags         │
│  1   14:23:01   SELECT  SELECT u.* FROM us…     [api]        │
│  2   14:23:01   INSERT  INSERT INTO logs …      [api]        │
│  3   14:23:02   SELECT  SELECT p.*, u.name…     [web]        │
│  4   14:23:02   UPDATE  UPDATE users SET l…     [web]        │
├──────────────────────────────────────────────────────────────┤
│  ▾ Query Detail                                              │
│  ┌────────────────────────────────────────────────────────┐  │
│  │ SELECT u.*, p.title, p.content                         │  │
│  │ FROM users u                                            │  │
│  │ JOIN posts p ON p.user_id = u.id                       │  │
│  │ WHERE u.active = ?                                      │  │
│  └────────────────────────────────────────────────────────┘  │
│                                                              │
│  Bound Parameters: ?1 = 1                                    │
│  Tables: users, posts                                        │
│  Tags: [api] [v2]                                            │
│                                                              │
│  Backtrace:                                                  │
│  ▸ UserController.php:42  getUserPosts()           [↗ Open]  │ ← 緑ハイライト
│    Router.php:128         dispatch()               [↗ Open]  │
│    index.php:15           main()                   [↗ Open]  │
└──────────────────────────────────────────────────────────────┘
```

### Statistics タブ

```
┌──────────────────────────────────────────────────────────────┐
│  [Query Log]  [Statistics]  [Live Tail]                      │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  Total Queries: 156                                          │
│                                                              │
│  Query Types                                                 │
│  ┌──────────────────────────────────────────────────┐       │
│  │ SELECT  ████████████████████████████░░░░  78%     │       │
│  │ INSERT  ████████░░░░░░░░░░░░░░░░░░░░░░░  12%     │       │
│  │ UPDATE  █████░░░░░░░░░░░░░░░░░░░░░░░░░░   8%     │       │
│  │ DELETE  ██░░░░░░░░░░░░░░░░░░░░░░░░░░░░░   2%     │       │
│  └──────────────────────────────────────────────────┘       │
│                                                              │
│  Top Tables                                                  │
│  ┌──────────────────────────────────────────────────┐       │
│  │ users      ████████████████████████░░░░░░  45%    │       │
│  │ posts      ████████████████░░░░░░░░░░░░░░  30%    │       │
│  │ comments   ██████████░░░░░░░░░░░░░░░░░░░░  18%    │       │
│  │ logs       ████░░░░░░░░░░░░░░░░░░░░░░░░░░   7%    │       │
│  └──────────────────────────────────────────────────┘       │
│                                                              │
│  Top Tags                                                    │
│  ┌──────────────────────────────────────────────────┐       │
│  │ api        ████████████████████░░░░░░░░░░  60%    │       │
│  │ web        ████████████░░░░░░░░░░░░░░░░░░  30%    │       │
│  │ cron       ████░░░░░░░░░░░░░░░░░░░░░░░░░░  10%    │       │
│  └──────────────────────────────────────────────────┘       │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### Live Tail タブ

```
┌──────────────────────────────────────────────────────────────┐
│  [Query Log]  [Statistics]  [Live Tail]                      │
├──────────────────────────────────────────────────────────────┤
│  Status: 🟢 Watching job-abc123          [Clear] [Pause]     │
├──────────────────────────────────────────────────────────────┤
│  [2025-01-23 14:23:01.000] OK [api] SELECT u.* FROM users…  │
│  #0 /var/www/app/Http/Controllers/UserController.php:42      │
│  #1 /var/www/vendor/laravel/framework/.../Router.php:128     │
│                                                              │
│  [2025-01-23 14:23:01.050] OK [api] INSERT INTO logs …       │
│  #0 /var/www/app/Services/LogService.php:28                  │
│                                                              │
│  [2025-01-23 14:23:02.100] OK [web] UPDATE users SET …       │
│  #0 /var/www/app/Http/Controllers/AuthController.php:95      │
│  █                                                           │
└──────────────────────────────────────────────────────────────┘
```

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

VSCode ネイティブ TreeView で ジョブ一覧を表示。

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

#### ProfilerWebviewProvider.ts

メイン UI を Webview で提供。

```typescript
export class ProfilerWebviewProvider implements vscode.WebviewViewProvider {
  resolveWebviewView(webviewView: vscode.WebviewView): void;

  // ジョブ選択時の処理
  selectJob(jobKey: string): void;

  // 定期更新タイマー
  private startRefreshTimer(): void;
  private stopRefreshTimer(): void;

  // Webview ↔ Extension メッセージハンドリング
  private handleWebviewMessage(message: any): void;
  private postMessage(message: any): void;
}
```

**Webview セキュリティ:**
- `localResourceRoots` で Webview がアクセスできるファイルを制限
- CSP (Content Security Policy) を適切に設定
- `nonce` ベースのスクリプト実行許可

---

## Webview 実装詳細

### テーマ連動

VSCode のカラーテーマに自動適応するため、CSS 変数を活用:

```css
:root {
  /* VSCode テーマ変数を継承 */
  --vscode-editor-background: var(--vscode-editor-background);
  --vscode-editor-foreground: var(--vscode-editor-foreground);
  --vscode-list-activeSelectionBackground: var(--vscode-list-activeSelectionBackground);
  /* ... */
}
```

### 仮想スクロール

大量クエリ (10,000+) のパフォーマンス対策:
- テーブルは仮想スクロールで描画 (表示領域 + バッファ行のみ DOM に存在)
- 全件データは Extension 側で保持し、フィルタ結果を Webview に送信
- ページネーションは不要 (仮想スクロールで対応)

### SQL シンタックスハイライト

軽量な正規表現ベースのハイライター:
- キーワード: `SELECT`, `FROM`, `WHERE`, `JOIN`, `INSERT`, `UPDATE`, `DELETE` etc.
- 文字列リテラル: `'...'`
- 数値リテラル
- コメント: `--`, `/* */`
- パラメータプレースホルダー: `?`

---

## 実装ステップ

### Phase 1: 基盤構築

1. **プロジェクトスキャフォールド**
   - `package.json`, `tsconfig.json`, `esbuild.mjs` 作成
   - `.vscodeignore`, `.eslintrc.json` 設定
   - `npm init` + 依存パッケージインストール

2. **Extension エントリポイント**
   - `extension.ts` に `activate()` / `deactivate()` 実装
   - コマンド登録、TreeView 登録、WebviewProvider 登録

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

8. **ProfilerWebviewProvider + Webview UI**
   - Webview 基本構成 (HTML + CSS + JS)
   - QueryLogTable コンポーネント
   - QueryDetail コンポーネント
   - フィルタ・検索 UI

### Phase 3: ナビゲーション & Live Tail

9. **バックトレースナビゲーション**
   - `openFile` メッセージハンドラ
   - `vscode.workspace.openTextDocument` + `showTextDocument` でジャンプ
   - パスマッピング適用

10. **FrameResolverService**
    - JavaScript (`vm` モジュール) によるフレーム解決
    - デフォルトスクリプト提供

11. **FileWatcherService**
    - `fs.watchFile` によるポーリング監視
    - 変更検知コールバック

12. **LiveTailView**
    - Raw ログのリアルタイム表示
    - 一時停止/再開/クリア機能

### Phase 4: 統計 & CLI 連携

13. **StatisticsService**
    - クエリ統計計算

14. **StatisticsView**
    - CSS/SVG バーチャート
    - クエリ種別分布、テーブル別頻度、タグ別頻度

15. **CLI コマンド統合**
    - `startJob` / `stopJob` コマンド実装
    - `child_process.execFile` で PHP CLI 呼び出し

16. **OpenLog コマンド**
    - ファイルピッカーで `.jsonl` ファイル選択
    - 選択ファイルをエディタで開く

### Phase 5: 品質 & 仕上げ

17. **ユニットテスト**
    - LogParserService, JobManagerService, StatisticsService, QueryEntry のテスト
    - Vitest で実行

18. **統合テスト**
    - @vscode/test-electron で Extension 全体テスト

19. **アイコン・UI 微調整**
    - SVG アイコン作成 (JetBrains 版を参考)
    - CSS テーマ最適化 (ライト/ダーク両対応)

20. **ドキュメント**
    - README.md (Marketplace 用)
    - CHANGELOG.md

---

## JetBrains Plugin との差異・VSCode 固有の考慮事項

### 1. UI アーキテクチャ

| 観点 | JetBrains | VSCode |
|------|-----------|--------|
| UI 描画 | Swing (ネイティブ Java UI) | Webview (HTML/CSS/JS) |
| テーブル | JTable + TableModel | HTML `<table>` + 仮想スクロール |
| チャート | Graphics2D / JFreeChart | CSS/SVG |
| スプリットペイン | JSplitPane | CSS flexbox / resizable divider |
| ファイル選択 | JFileChooser | `vscode.window.showOpenDialog` |
| 通知 | Messages.showInfoMessage | `vscode.window.showInformationMessage` |

### 2. パフォーマンス

- **Webview の制約**: DOM 操作は JTable より重いため、仮想スクロール必須
- **メッセージパッシング**: Extension ↔ Webview 間は非同期 `postMessage`、大量データは分割送信
- **メモリ**: Webview はブラウザプロセスで動作するため、大量データは Extension 側でフィルタしてから送信

### 3. フレームリゾルバ

- JetBrains 版: **Groovy** スクリプト (JVM 上で実行)
- VSCode 版: **JavaScript** スクリプト (Node.js `vm` モジュール)
- API は同等 (`trace`, `tag`, `query` 変数を提供)
- ユーザーへの影響: 既存の Groovy スクリプトは JavaScript に書き換えが必要 (構文は類似)

### 4. ファイル監視

- JetBrains 版: IntelliJ VFS イベント + Timer ポーリング
- VSCode 版: `fs.watchFile` ポーリング (Docker ボリューム対応)
- `vscode.workspace.FileSystemWatcher` はワークスペース内のみ対象のため、外部ディレクトリには `fs.watchFile` を使用

### 5. 設定管理

- JetBrains 版: `PersistentStateComponent` + カスタム設定ダイアログ
- VSCode 版: `contributes.configuration` (VSCode 標準 Settings UI で編集)
- JSON 形式のパスマッピングは VSCode Settings の JSON エディタで編集可能

---

## リスク・課題

| リスク | 影響度 | 対策 |
|--------|--------|------|
| Webview パフォーマンス (大量クエリ) | 中 | 仮想スクロール、フィルタ済みデータのみ送信 |
| Docker ボリュームのファイル監視 | 中 | `fs.watchFile` ポーリング (1秒間隔) |
| Extension ↔ Webview 通信オーバーヘッド | 低 | バッチ送信、差分更新 |
| Remote SSH / WSL 環境 | 低 | Remote 環境では Extension がリモートで動作するため自然対応 |
| Webview 永続性 (タブ非表示時) | 中 | `retainContextWhenHidden: true` で Webview 状態保持 |

---

## 将来の拡張可能性

1. **CodeLens 統合** - PHP ファイル上でクエリ実行元行にインラインでクエリ情報表示
2. **Diagnostic 統合** - 重いクエリを Warning として表示
3. **Notebook 統合** - プロファイリング結果を Jupyter Notebook 形式でエクスポート
4. **Language Server** - SQL 補完・バリデーション
5. **Testing Integration** - テスト実行時の自動プロファイリング
