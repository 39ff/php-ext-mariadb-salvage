# MariaDB Profiler for PHP

PHP拡張モジュールとして動作するMariaDB/MySQLクエリプロファイラです。PHPの`mysqlnd`ドライバにフックし、実行される全SQLクエリをインターセプトして記録・分析します。

PDO、mysqli、Laravel Eloquentなど、mysqlndを利用するすべてのデータベースアクセス方法に対応しています。

## 構成

| コンポーネント | 概要 |
|---|---|
| `ext/mariadb_profiler/` | PHP拡張モジュール (C言語) |
| `cli/` | CLIプロファイラ管理ツール (PHP) |
| `demo/` | Docker環境のWebデモ (Laravel + WebSocket) |
| `jetbrains-plugin/` | JetBrains IDEプラグイン (Kotlin) |

## 機能

- **クエリインターセプト** — mysqlndレベルで全SQLクエリをキャプチャ
- **コンテキストタグ** — スタック型タグでクエリをビジネスロジック単位にグループ化
- **PHPバックトレース** — 任意の深さでコールスタックを記録
- **プリペアドステートメント対応** — バインドパラメータも記録 (PHP 7.0+)
- **SQL解析** — テーブル名・カラム名の自動抽出
- **ジョブ管理** — 複数プロファイリングセッションの同時実行・親子関係管理
- **クロスプラットフォーム** — Linux / macOS / Windows対応

## 要件

| コンポーネント | 要件 |
|---|---|
| PHP拡張 | PHP 5.3 〜 8.4+、mysqlnd |
| CLIツール | PHP 5.3+、Composer |
| デモ | Docker、Docker Compose |

## インストール

### PHP拡張のビルド

```bash
cd ext/mariadb_profiler
phpize
./configure --enable-mariadb_profiler
make
sudo make install
```

php.iniに以下を追加:

```ini
extension=mariadb_profiler.so
mariadb_profiler.enabled=1
mariadb_profiler.log_dir=/var/log/mariadb_profiler
```

### CLIツール

```bash
composer install
```

## 設定 (php.ini)

```ini
mariadb_profiler.enabled = 1            ; 拡張の有効化
mariadb_profiler.log_dir = /tmp/mariadb_profiler  ; ログ出力先
mariadb_profiler.raw_log = 1            ; テキスト形式のログ出力
mariadb_profiler.job_check_interval = 1 ; jobs.jsonのチェック間隔 (秒)
mariadb_profiler.trace_depth = 0        ; バックトレースの深さ (0=無効)
```

## 使い方

### プロファイリングジョブの操作

```bash
# ジョブの開始
php cli/mariadb_profiler.php job start [<key>]

# ジョブの終了
php cli/mariadb_profiler.php job end <key>

# ジョブ一覧
php cli/mariadb_profiler.php job list

# 解析済みクエリの表示
php cli/mariadb_profiler.php job show <key> [--tag=<tag>]

# 生ログの表示
php cli/mariadb_profiler.php job raw <key>

# JSON形式でエクスポート
php cli/mariadb_profiler.php job export <key>

# タグ別サマリー
php cli/mariadb_profiler.php job tags <key>

# 呼び出し元サマリー
php cli/mariadb_profiler.php job callers <key>

# 完了済みジョブの削除
php cli/mariadb_profiler.php job purge
```

### PHPコード内でのタグ付け

```php
// タグをプッシュ
mariadb_profiler_tag('checkout_flow');

// ここで実行されるクエリに 'checkout_flow' タグが付与される
$db->query('SELECT * FROM orders WHERE user_id = ?');

// 現在のタグを取得
$tag = mariadb_profiler_get_tag(); // 'checkout_flow'

// タグをポップ
mariadb_profiler_untag();
```

### デモ環境

```bash
cd demo
docker compose up --build
# http://localhost:8080 にアクセス
```

## PHP関数リファレンス

| 関数 | 説明 |
|---|---|
| `mariadb_profiler_tag(string $tag): void` | コンテキストタグをスタックにプッシュ |
| `mariadb_profiler_untag(?string $tag = null): ?string` | タグをポップ (指定タグまで巻き戻し可) |
| `mariadb_profiler_get_tag(): ?string` | 現在のタグを取得 (未設定時はnull) |

## ログ形式

ジョブごとに2種類のファイルが生成されます:

- `{job_key}.raw.log` — 1行1クエリのテキスト形式 (タイムスタンプ、ステータス、タグ、トレース付き)
- `{job_key}.jsonl` — テーブル名・カラム名を含む解析済みJSON形式
