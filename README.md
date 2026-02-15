# MariaDB Query Profiler for PHP

mysqlnd プラグインによるクエリプロファイリング PHP 拡張 + CLI ツール。

mysqli / PDO MySQL 経由で実行されるすべての SQL クエリを自動的にインターセプトし、ログに記録します。アプリケーションコードの変更は不要です。

## 特徴

- **ゼロコード変更** -- INI 設定のみで有効化。PHP コードの修正は不要
- **mysqlnd フック** -- mysqli・PDO MySQL のクエリを透過的にキャプチャ
- **複数ジョブの同時実行** -- 複数のプロファイリングセッションを並列で実行可能
- **2 種類のログ形式** -- JSONL（機械処理向け）と Raw テキスト（人間向け）
- **SQL 解析** -- キャプチャしたクエリからテーブル名・カラム名を自動抽出
- **幅広い PHP バージョン対応** -- PHP 5.3 〜 8.4+

## 必要要件

- PHP >= 5.3.2（mysqlnd 有効）
- PHP 開発ヘッダー（`php-dev` / `php-devel`）
- phpize, autoconf, make
- Composer（CLI ツール用）

## インストール

### 1. リポジトリのクローン

```bash
git clone https://github.com/39ff/php-ext-mariadb-salvage.git
cd php-ext-mariadb-salvage
```

### 2. PHP 拡張のビルドとインストール

```bash
make ext-build
sudo make ext-install
```

### 3. php.ini に設定を追加

```ini
extension=mariadb_profiler.so
mariadb_profiler.enabled = 1
mariadb_profiler.log_dir = /tmp/mariadb_profiler
mariadb_profiler.raw_log = 1
mariadb_profiler.job_check_interval = 1
```

### 4. CLI ツールのセットアップ

```bash
composer install
```

必要に応じてシンボリックリンクを作成:

```bash
ln -sf $(pwd)/cli/mariadb_profiler.php /usr/local/bin/mariadb-profiler
```

## INI 設定

| 設定名 | デフォルト値 | 説明 |
|--------|-------------|------|
| `mariadb_profiler.enabled` | `0` | プロファイラの有効/無効 |
| `mariadb_profiler.log_dir` | `/tmp/mariadb_profiler` | ログファイルの出力先ディレクトリ |
| `mariadb_profiler.raw_log` | `1` | Raw テキストログの出力有無 |
| `mariadb_profiler.job_check_interval` | `1` | アクティブジョブ一覧の再読み込み間隔（秒） |

## 使い方

### プロファイリングの開始

```bash
php cli/mariadb_profiler.php job start my-session
```

この状態でアプリケーションに対してリクエストを送ると、実行された SQL クエリが自動的にログに記録されます。

### プロファイリングの終了

```bash
php cli/mariadb_profiler.php job end my-session
```

### 結果の確認

```bash
# クエリ一覧と解析結果（テーブル・カラム情報付き）
php cli/mariadb_profiler.php job show my-session

# Raw ログの表示
php cli/mariadb_profiler.php job raw my-session

# 解析結果をファイルにエクスポート
php cli/mariadb_profiler.php job export my-session
```

### その他のコマンド

```bash
# ジョブ一覧の表示
php cli/mariadb_profiler.php job list

# 完了済みジョブデータの一括削除
php cli/mariadb_profiler.php job purge
```

`--log-dir` オプションでログディレクトリを指定できます:

```bash
php cli/mariadb_profiler.php --log-dir /var/log/profiler job start my-session
```

## 仕組み

1. PHP が `mariadb_profiler.so` を読み込み、mysqlnd の `query()` / `send_query()` メソッドをフックする
2. CLI で `job start` を実行すると `jobs.json` にアクティブジョブとして登録される
3. アプリケーションが SQL を実行すると、拡張がアクティブジョブの有無を確認し、ログファイルに書き込む
   - `{key}.jsonl` -- JSON Lines 形式（クエリ文字列 + タイムスタンプ）
   - `{key}.raw.log` -- 人間が読める形式（`[timestamp] query`）
4. CLI で `job end` を実行するとジョブが完了状態に移行し、クエリ数が記録される
5. `job show` で SqlAnalyzer がクエリを解析し、テーブル名・カラム名を抽出する

## プロジェクト構成

```
php-ext-mariadb-salvage/
├── ext/mariadb_profiler/    # C 拡張（mysqlnd プラグイン）
│   ├── config.m4
│   ├── mariadb_profiler.c
│   ├── profiler_mysqlnd_plugin.c
│   ├── profiler_job.c
│   └── profiler_log.c
├── cli/                     # CLI ツール（PHP）
│   ├── mariadb_profiler.php
│   └── src/
│       ├── JobManager.php
│       └── SqlAnalyzer.php
├── tests/                   # テスト
├── demo/                    # Docker デモアプリケーション
├── composer.json
└── Makefile
```

## テスト

```bash
# ユニットテスト
make test

# C 拡張テスト（phpize テストスイート）
make test-extension
```

## ライセンス

MIT
