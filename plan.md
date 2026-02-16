# 計画: クエリログにDB接続情報を追加

## 概要
クエリログ（JSONL / raw）に接続ユーザ名・ホスト名・ポート・DB名を含める。
すべてmysqlndの `MYSQLND_CONN_DATA` 構造体から直接取得可能。

## mysqlndで取得可能な接続メタデータ
`MYSQLND_CONN_DATA` 構造体のフィールド（`mysqlnd_structs.h:893-907`）:

| フィールド | 型 | 内容 |
|---|---|---|
| `username` | `MYSQLND_STRING` | 接続ユーザ名 |
| `hostname` | `MYSQLND_STRING` | ホスト名 |
| `port` | `unsigned int` | TCPポート |
| `connect_or_select_db` | `MYSQLND_STRING` | 現在のDB名 |
| `unix_socket` | `MYSQLND_STRING` | Unixソケットパス |

---

## 変更対象ファイル

### 1. `ext/mariadb_profiler/profiler_log.h`
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

### 2. `ext/mariadb_profiler/php_mariadb_profiler.h`
- `profiler_log_query`, `profiler_log_query_with_params` のシグネチャに
  `const profiler_conn_info *conn_info` パラメータを追加
- `profiler_log_raw` にも同パラメータを追加

### 3. `ext/mariadb_profiler/profiler_mysqlnd_plugin.c`
- **各フックで接続オブジェクトから情報を抽出し、ログ関数に渡す:**
  - `profiler_conn_query` / `profiler_conn_send_query` (全PHP版):
    `conn` パラメータ（= `PROFILER_CONN_T *` = `MYSQLND_CONN_DATA *`）から直接取得
    ```c
    profiler_conn_info ci;
    ci.user = conn->username.s;       ci.user_len = conn->username.l;
    ci.host = conn->hostname.s;       ci.host_len = conn->hostname.l;
    ci.port = conn->port;
    ci.db   = conn->connect_or_select_db.s;
    ci.db_len = conn->connect_or_select_db.l;
    ```
  - `profiler_stmt_execute` (PHP 7.0+): `stmt->data->conn` 経由で取得
  - `profiler_stmt_prepare` のfailure path (PHP 7.0+): `stmt->data->conn` 経由で取得
  - `profiler_stmt_prepare` (PHP 5.x): `stmt` からconnへのアクセスはPHP 5.x ABIが不明なため
    `NULL` を渡す（安全なフォールバック）

### 4. `ext/mariadb_profiler/profiler_log.c`
- **`profiler_log_query_internal`**: `const profiler_conn_info *conn_info` を受け取り、
  各ログ関数に渡す
- **`profiler_log_jsonl`**: 接続情報をJSON出力に追加:
  ```json
  {"k":"...","q":"...","db":"mydb","user":"root","host":"localhost","port":3306,...}
  ```
  短縮キー: `db`, `user`, `host`, `port`
  `conn_info` が NULL の場合はこれらのフィールドを省略（後方互換性）
- **`profiler_log_raw`**: 接続情報をrawログに追加:
  ```
  [2024-01-15 10:30:00] [ok] [root@localhost:3306/mydb] SELECT ...
  ```

### 5. `jetbrains-plugin/.../model/QueryEntry.kt`
- フィールド追加:
  ```kotlin
  @SerialName("db")   val database: String? = null,
  @SerialName("user") val user: String? = null,
  @SerialName("host") val host: String? = null,
  @SerialName("port") val port: Int? = null,
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

### 6. `jetbrains-plugin/.../ui/table/QueryTableModel.kt`
- テーブルカラムに「DB」列を追加（`entry.database` を表示）
- テキストフィルタの対象にDB名も含める

### 7. `jetbrains-plugin/.../ui/panel/QueryDetailPanel.kt`
- 詳細パネルのDetailsセクションに接続情報を追加
  （`user@host:port/db` 形式で表示）

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
1. `profiler_conn_info` 構造体定義 (`profiler_log.h`)
2. ログ関数シグネチャ変更 (`php_mariadb_profiler.h`, `profiler_log.c`)
3. フック修正 (`profiler_mysqlnd_plugin.c`) — 接続情報の抽出と渡し
4. Kotlin モデル更新 (`QueryEntry.kt`)
5. JetBrains Plugin UI更新 (`QueryTableModel.kt`, `QueryDetailPanel.kt`)

## リスク・注意点
- **PHP 5.3**: `MYSQLND` 構造体（PHP 5.3でのconn型）のレイアウトがPHP 5.4+と異なる。
  フィールドアクセスは `#if PHP_VERSION_ID >= 50400` でガードし、
  PHP 5.3 stmtのfailure pathでは `NULL` を渡す
- **パフォーマンス**: 接続情報はポインタ参照のみで文字列コピーなし。
  JSONL書き込み時のみエスケープ処理が発生（既存パターンと同じ）
- **ABI安定性**: `hostname`/`username`/`connect_or_select_db` は mysqlnd の安定したフィールド
  （PHP 5.4〜8.4で変更なし）。既存の `PROFILER_MYSQLND_PARAM_ACCESS_SAFE` と同様のガードを検討
- **後方互換性**: `conn_info` が NULL の場合は接続情報フィールドを省略するため、
  古い形式のログを読むツールも影響なし
