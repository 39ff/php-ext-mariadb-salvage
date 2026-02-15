# JetBrains Plugin 実装計画: MariaDB Profiler Viewer

## 概要

`php-ext-mariadb-salvage` が生成するクエリプロファイリングデータを JetBrains IDE (PhpStorm / IntelliJ) 上でわかりやすく可視化・操作するプラグインを作成する。

---

## プラグインが提供する機能

### 1. クエリログビューア (Query Log Viewer)
- `.jsonl` ファイルをパースし、テーブル形式で一覧表示
- カラム: タイムスタンプ / SQL (短縮表示) / タグ / 実行元ファイル:行番号
- クエリをクリックで詳細パネル (フル SQL, バックトレース, タグ一覧)
- フィルタ: クエリ種別 (SELECT/INSERT/UPDATE/DELETE), テーブル名, タグ
- 検索: SQL テキスト全文検索

### 2. ジョブマネージャ (Job Manager)
- `jobs.json` を読み込み、アクティブ/完了済みジョブを一覧表示
- ジョブ選択でそのジョブのクエリログを自動ロード
- CLI (`cli/mariadb_profiler.php`) 連携によるジョブ開始/停止操作
- ジョブごとのクエリ数・期間サマリ表示

### 3. SQL 分析パネル (SQL Analysis Panel)
- 選択クエリの SQL を解析し、アクセスするテーブル・カラムを構造化表示
- テーブル依存関係のツリービュー
- クエリ種別 (SELECT/INSERT/UPDATE/DELETE) のアイコン表示

### 4. バックトレースナビゲーション (Backtrace Navigation)
- クエリに紐づく PHP バックトレースを表示
- ファイルパス:行番号をクリックで IDE エディタにジャンプ
- コールスタックのツリー表示

### 5. リアルタイムモニタリング (Real-time Monitoring)
- `.raw.log` ファイルの tail -f 相当を IDE 内ツールウィンドウで実行
- 新規クエリをリアルタイムでストリーミング表示
- 一時停止/再開ボタン

### 6. 統計ダッシュボード (Statistics Dashboard)
- ジョブ内クエリ数の集計
- テーブル別アクセス頻度の棒グラフ
- クエリ種別の円グラフ
- 時間軸でのクエリ発行タイムライン

---

## 技術スタック

| 項目 | 選択 |
|------|------|
| 言語 | Kotlin |
| ビルド | Gradle + IntelliJ Platform Gradle Plugin |
| 最小対応 IDE | IntelliJ 2023.3+ (PhpStorm 互換) |
| UI フレームワーク | IntelliJ Platform UI (Swing + JetBrains UI DSL) |
| チャート | JFreeChart (軽量) |
| JSON パース | kotlinx.serialization |
| ファイル監視 | IntelliJ VFS (VirtualFileSystem) イベント |

---

## ディレクトリ構成

```
jetbrains-plugin/
├── build.gradle.kts                    # Gradle ビルド設定
├── settings.gradle.kts                 # プロジェクト設定
├── gradle.properties                   # バージョン定数
├── gradle/
│   └── wrapper/
│       └── gradle-wrapper.properties
├── src/
│   └── main/
│       ├── kotlin/
│       │   └── com/mariadbprofiler/plugin/
│       │       ├── MariaDbProfilerPlugin.kt        # プラグインエントリ
│       │       │
│       │       ├── model/                           # データモデル
│       │       │   ├── QueryEntry.kt                # クエリログエントリ
│       │       │   ├── JobInfo.kt                   # ジョブ情報
│       │       │   ├── BacktraceFrame.kt            # バックトレースフレーム
│       │       │   └── ProfilerSettings.kt          # プラグイン設定
│       │       │
│       │       ├── service/                          # サービス層
│       │       │   ├── LogParserService.kt          # JSONL パーサ
│       │       │   ├── JobManagerService.kt         # jobs.json 読み書き
│       │       │   ├── SqlAnalysisService.kt        # SQL 解析 (CLI 連携)
│       │       │   ├── FileWatcherService.kt        # ログファイル監視
│       │       │   └── StatisticsService.kt         # 統計計算
│       │       │
│       │       ├── ui/                               # UI コンポーネント
│       │       │   ├── toolwindow/
│       │       │   │   ├── ProfilerToolWindowFactory.kt  # ツールウィンドウ登録
│       │       │   │   └── ProfilerToolWindow.kt         # メインツールウィンドウ
│       │       │   ├── panel/
│       │       │   │   ├── QueryLogPanel.kt              # クエリ一覧テーブル
│       │       │   │   ├── QueryDetailPanel.kt           # クエリ詳細表示
│       │       │   │   ├── JobListPanel.kt               # ジョブ一覧
│       │       │   │   ├── BacktracePanel.kt             # バックトレース表示
│       │       │   │   ├── StatisticsPanel.kt            # 統計ダッシュボード
│       │       │   │   └── LiveTailPanel.kt              # リアルタイム監視
│       │       │   ├── table/
│       │       │   │   ├── QueryTableModel.kt            # テーブルモデル
│       │       │   │   └── QueryCellRenderer.kt          # セルレンダラ
│       │       │   └── dialog/
│       │       │       └── ProfilerSettingsDialog.kt     # 設定ダイアログ
│       │       │
│       │       ├── action/                           # IDE アクション
│       │       │   ├── StartJobAction.kt             # ジョブ開始
│       │       │   ├── StopJobAction.kt              # ジョブ停止
│       │       │   └── OpenLogAction.kt              # ログファイルを開く
│       │       │
│       │       └── settings/                         # プラグイン設定
│       │           ├── ProfilerConfigurable.kt       # 設定画面
│       │           └── ProfilerState.kt              # 永続化状態
│       │
│       └── resources/
│           ├── META-INF/
│           │   └── plugin.xml                        # プラグイン定義
│           ├── icons/                                # アイコンリソース
│           │   ├── profiler.svg
│           │   ├── query_select.svg
│           │   ├── query_insert.svg
│           │   ├── query_update.svg
│           │   ├── query_delete.svg
│           │   ├── job_active.svg
│           │   └── job_completed.svg
│           └── messages/
│               └── ProfilerBundle.properties          # i18n メッセージ
│
└── src/
    └── test/
        └── kotlin/
            └── com/mariadbprofiler/plugin/
                ├── service/
                │   ├── LogParserServiceTest.kt
                │   └── JobManagerServiceTest.kt
                └── model/
                    └── QueryEntryTest.kt
```

---

## 実装ステップ

### Phase 1: 基盤構築
1. Gradle プロジェクトのセットアップ (IntelliJ Platform Plugin テンプレート)
2. `plugin.xml` にツールウィンドウ・アクション・設定を登録
3. データモデル定義 (`QueryEntry`, `JobInfo`, `BacktraceFrame`)
4. プラグイン設定画面 (log_dir パス指定)

### Phase 2: コア機能 - クエリログビューア
5. `LogParserService` - JSONL ファイルパーサ実装
6. `JobManagerService` - jobs.json 読み込み
7. `QueryLogPanel` - テーブル形式でクエリ一覧表示
8. `QueryDetailPanel` - クエリ詳細 (フル SQL, メタデータ)
9. フィルタ・検索 UI

### Phase 3: ナビゲーション
10. `BacktracePanel` - バックトレース表示
11. ファイル:行番号クリックで IDE エディタにジャンプ (OpenFileDescriptor)
12. `JobListPanel` - ジョブ選択 UI

### Phase 4: リアルタイム & 統計
13. `FileWatcherService` - VFS イベントによるログ変更検知
14. `LiveTailPanel` - リアルタイムクエリストリーム
15. `StatisticsService` - クエリ統計計算
16. `StatisticsPanel` - チャート表示 (テーブル別頻度, クエリ種別分布)

### Phase 5: CLI 連携 & アクション
17. `StartJobAction` / `StopJobAction` - CLI 経由でジョブ管理
18. ツールバーボタン・メニュー登録

### Phase 6: 品質 & 仕上げ
19. ユニットテスト
20. アイコン・UI 微調整
21. README・使い方ドキュメント

---

## 主要データフロー

```
[PHP Extension]
     │
     ├── /var/profiler/jobs.json          ──→  JobManagerService  ──→  JobListPanel
     │
     ├── /var/profiler/<key>.jsonl         ──→  LogParserService   ──→  QueryLogPanel
     │                                                                     │
     │                                                                     ├──→ QueryDetailPanel
     │                                                                     └──→ BacktracePanel
     │                                                                              │
     │                                                                              └──→ IDE Editor (ジャンプ)
     │
     └── /var/profiler/<key>.raw.log       ──→  FileWatcherService ──→  LiveTailPanel

[CLI Tool] ←── StartJobAction / StopJobAction
```

---

## UI モックアップ (テキスト)

```
┌─────────────────────────────────────────────────────────────────────┐
│ MariaDB Profiler                                           [⚙] [▶] │
├──────────┬──────────────────────────────────────────────────────────┤
│ Jobs     │  Query Log    │ Statistics │ Live Tail                   │
│          ├───────────────┴────────────┴─────────────────────────────┤
│ ● job-1  │ #   Time       Type    SQL                   Tags       │
│ ● job-2  │ 1   14:23:01   SELECT  SELECT u.* FROM us…  [api]      │
│ ○ job-3  │ 2   14:23:01   INSERT  INSERT INTO logs …   [api]      │
│ ○ job-4  │ 3   14:23:02   SELECT  SELECT p.*, u.name…  [web]      │
│          │ 4   14:23:02   UPDATE  UPDATE users SET l…  [web]      │
│          ├──────────────────────────────────────────────────────────┤
│          │ Query Detail                                             │
│          │ ┌────────────────────────────────────────────────────┐  │
│          │ │ SELECT u.*, p.title, p.content                     │  │
│          │ │ FROM users u                                        │  │
│          │ │ JOIN posts p ON p.user_id = u.id                   │  │
│          │ │ WHERE u.active = 1                                  │  │
│          │ └────────────────────────────────────────────────────┘  │
│          │                                                         │
│          │ Tables: users, posts                                    │
│          │ Tags: [api] [v2]                                        │
│          │                                                         │
│          │ Backtrace:                                              │
│          │   → UserController.php:42  getUserPosts()     [↗]       │
│          │   → Router.php:128         dispatch()         [↗]       │
│          │   → index.php:15           main()             [↗]       │
└──────────┴─────────────────────────────────────────────────────────┘
```

● = Active job, ○ = Completed job, [↗] = エディタジャンプリンク

---

## 設定項目

| 設定名 | 説明 | デフォルト |
|--------|------|-----------|
| Profiler Log Directory | プロファイラログの保存ディレクトリ | `/tmp/mariadb_profiler` |
| PHP Executable | CLI 連携用 PHP パス | `php` (PATH から自動検出) |
| CLI Script Path | `mariadb_profiler.php` のパス | プロジェクトルートから自動検出 |
| Max Queries Display | 一覧表示の最大件数 | 10000 |
| Auto-refresh Interval | ジョブリスト自動更新間隔 (秒) | 5 |
| Tail Buffer Size | リアルタイム表示のバッファ行数 | 500 |
