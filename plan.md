# 計画: クエリログにDB接続情報を追加

## 概要
クエリログ（JSONL / raw）に接続ユーザ名・ホスト名・ポート・DB名を含める。
テーブルプレフィックスはmysqlndレベルでは取得不可のため、INI設定として提供する。

## mysqlndで取得可能な接続メタデータ
`MYSQLND_CONN_DATA` 構造体のフィールド（`mysqlnd_structs.h:893-907`）:

| フィールド | 型 | 内容 |
|---|---|---|
| `username` | `MYSQLND_STRING` | 接続ユーザ名 |
| `hostname` | `MYSQLND_STRING` | ホスト名 |
| `port` | `unsigned int` | TCPポート |
| `connect_or_select_db` | `MYSQLND_STRING` | 現在のDB名 |
| `unix_socket` | `MYSQLND_STRING` | Unixソケットパス |

## テーブルプレフィックスについて
テーブルプレフィックス（WordPress `wp_`、Laravel prefix等）はアプリケーション層の概念であり、
mysqlndドライバからは取得できない。INI設定 `mariadb_profiler.table_prefix` として提供する。

---

## 変更対象ファイル

### 1. `ext/mariadb_profiler/profiler_log.h` / `php_mariadb_profiler.h`
- 接続情報を保持する構造体 `profiler_conn_info` を定義:
  ```c
  typedef struct {
      const char *user;
      size_t user_len;
      const char *host;
      size_t host_len;
      unsigned int port;
      const char *db;
      size_t db_len;
  } profiler_conn_info;
  ```
- `profiler_log_query`, `profiler_log_query_with_params`, `profiler_log_raw` のシグネチャに
  `const profiler_conn_info *conn_info` パラメータを追加
- モジュールグローバルに `char *table_prefix` を追加

### 2. `ext/mariadb_profiler/profiler_mysqlnd_plugin.c`
- **各フックで接続オブジェクトから情報を抽出:**
  - `profiler_conn_query` / `profiler_conn_send_query`: `conn` パラメータから直接取得
    ```c
    profiler_conn_info ci;
    ci.user = conn->username.s;  ci.user_len = conn->username.l;
    ci.host = conn->hostname.s;  ci.host_len = conn->hostname.l;
    ci.port = conn->port;
    ci.db   = conn->connect_or_select_db.s;
    ci.db_len = conn->connect_or_select_db.l;
    ```
  - `profiler_stmt_execute`: `stmt->data->conn` 経由で取得
  - `profiler_stmt_prepare` (PHP 5.x / PHP 7+ failure path): `stmt->data->conn` 経由で取得
- **PHP 5.3対応**: `PROFILER_CONN_T` は PHP 5.3 では `MYSQLND` 型。
  PHP 5.3 の `MYSQLND` 構造体にも同じフィールド名があるか確認が必要。
  ない場合は `#if PHP_VERSION_ID >= 50400` でガードし、PHP 5.3 では NULL を渡す。

### 3. `ext/mariadb_profiler/profiler_log.c`
- **`profiler_log_query_internal`**: `const profiler_conn_info *conn_info` を受け取り、
  各ログ関数に渡す
- **`profiler_log_jsonl`**: 接続情報をJSON出力に追加:
  ```json
  {"k":"...","q":"...","db":"mydb","user":"root","host":"localhost","port":3306,...}
  ```
  短縮キー: `db`, `user`, `host`, `port`
- **`profiler_log_raw`**: 接続情報をrawログに追加:
  ```
  [2024-01-15 10:30:00] [ok] [root@localhost:3306/mydb] SELECT ...
  ```
- テーブルプレフィックスが設定されている場合、`"prefix":"wp_"` も出力

### 4. `ext/mariadb_profiler/mariadb_profiler.c`（INI登録）
- `STD_PHP_INI_ENTRY("mariadb_profiler.table_prefix", "", ...)` を追加
- モジュールグローバルの初期化/クリーンアップに対応

### 5. `ext/mariadb_profiler/php_mariadb_profiler_compat.h`
- PHP 5.3 で接続メタデータにアクセスするためのヘルパーマクロを追加（必要に応じて）
- `PROFILER_CONN_GET_DATA(conn)` マクロ:
  PHP 5.3 では `conn` 自体、PHP 5.4+ では `conn` そのまま（両方とも直接アクセス可）

### 6. `jetbrains-plugin/.../model/QueryEntry.kt`
- フィールド追加:
  ```kotlin
  @SerialName("db")   val database: String? = null,
  @SerialName("user") val user: String? = null,
  @SerialName("host") val host: String? = null,
  @SerialName("port") val port: Int? = null,
  @SerialName("prefix") val tablePrefix: String? = null,
  ```
- `connectionInfo` computed property を追加（UI表示用）:
  ```kotlin
  val connectionInfo: String
      get() = buildString {
          user?.let { append("$it@") }
          host?.let { append(it) }
          port?.let { if (it != 3306) append(":$it") }
          database?.let { append("/$it") }
      }
  ```

### 7. `jetbrains-plugin/.../ui/table/QueryTableModel.kt`
- テーブルカラムに「DB」列を追加（`entry.database` を表示）

### 8. `jetbrains-plugin/.../ui/panel/QueryDetailPanel.kt`
- 詳細パネルに接続情報セクションを追加

### 9. テストファイル
- `QueryEntryTest.kt`: 接続情報フィールドのパーステスト追加
- C拡張のCLIテスト: テーブルプレフィックスINI設定のテスト

---

## JSONL出力例（変更後）
```json
{"k":"abc123","q":"SELECT * FROM users","db":"myapp","user":"webapp","host":"db-server","port":3306,"tag":"api","params":["42"],"s":"ok","ts":1705970401.123456}
```

## rawログ出力例（変更後）
```
[2024-01-22 10:30:00.123456] [ok] [webapp@db-server/myapp] [api] SELECT * FROM users
  params: ["42"]
```

---

## 実装順序
1. C構造体定義とシグネチャ変更 (`php_mariadb_profiler.h`, `profiler_log.c`)
2. フック修正 (`profiler_mysqlnd_plugin.c`) — 接続情報の抽出と渡し
3. INI設定追加 (`mariadb_profiler.c`)
4. Kotlin モデル更新 (`QueryEntry.kt`)
5. JetBrains Plugin UI更新
6. テスト追加

## リスク・注意点
- **PHP 5.3**: `MYSQLND` 構造体のレイアウトがPHP 5.4+と異なる可能性。
  接続メタデータフィールドへのアクセスは `#if PHP_VERSION_ID >= 50400` でガードし、
  PHP 5.3では `NULL` を渡す（安全なフォールバック）
- **パフォーマンス**: 接続情報はポインタ参照のみで文字列コピーなし。
  JSONL書き込み時のみエスケープ処理が発生（既存パターンと同じ）
- **ABI安定性**: `hostname`/`username`/`connect_or_select_db` は mysqlnd の安定したフィールド
  （PHP 5.4〜8.4で変更なし）。`PROFILER_MYSQLND_PARAM_ACCESS_SAFE` と同様のガードを検討
