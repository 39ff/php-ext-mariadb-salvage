# Demo Application Plan: MariaDB Profiler Dashboard

## 概要

Docker Compose環境でMariaDB + Laravel + WebSocketサーバーを起動し、
php-ext-mariadb-salvageのプロファイリング機能をブラウザから操作するデモUI。

- **Start/Stop** ボタンでプロファイリングジョブを制御
- **タブUI** でセッション(ジョブ)ごとにログを表示
- **xterm.js** で `tail -f` のリアルタイムログストリーミング

---

## アーキテクチャ

```
┌─────────────────────────────────────────────────────┐
│  Browser (localhost:8080)                            │
│  ┌────────────────────────────────────────────────┐  │
│  │  Dashboard (Blade + xterm.js)                  │  │
│  │  [Start] [Stop] [Run Demo Queries]             │  │
│  │  ┌──────┐ ┌──────┐ ┌──────┐                   │  │
│  │  │Tab 1 │ │Tab 2 │ │Tab 3 │  ← セッションタブ  │  │
│  │  └──────┘ └──────┘ └──────┘                   │  │
│  │  ┌──────────────────────────┐                  │  │
│  │  │  xterm.js terminal       │  ← tail -f ログ  │  │
│  │  │  [2025-01-23 10:00:01]   │                  │  │
│  │  │  SELECT * FROM users...  │                  │  │
│  │  └──────────────────────────┘                  │  │
│  └────────────────────────────────────────────────┘  │
└─────────┬──────────────────────┬────────────────────┘
          │ HTTP (API/HTML)       │ WebSocket
          ▼                      ▼
┌─────────────────┐   ┌──────────────────┐
│  Nginx (:8080)  │   │                  │
│  ├─ /           │──▶│  PHP-FPM (app)   │
│  │  Laravel UI  │   │  + extension.so  │
│  ├─ /api/*      │──▶│  + CLI profiler  │
│  └─ /ws/*       │   └──────────────────┘
│      WebSocket  │──▶┌──────────────────┐
│      proxy      │   │  Node.js (:3000) │
└─────────────────┘   │  WebSocket server│
                      │  tail -f logs    │
                      └──────────────────┘
                              │
┌─────────────────┐   ┌──────┴───────────┐
│  MariaDB (:3306)│◀──│  Shared Volume   │
│  demo database  │   │  /var/profiler/  │
└─────────────────┘   │  - jobs.json     │
                      │  - *.jsonl       │
                      │  - *.raw.log     │
                      └──────────────────┘
```

---

## Docker Compose サービス構成

### 1. `mariadb` - データベース
- Image: `mariadb:11`
- Port: 3306 (internal)
- DB: `demo`, User: `demo`/`demo`
- Volume: persistent data

### 2. `app` - PHP-FPM + Extension + Laravel
- Dockerfile.app (PHP 8.3-fpm ベース)
- ビルドステップ:
  1. phpize + configure + make で extension をコンパイル
  2. php.ini に extension 設定追加
  3. composer create-project laravel/laravel
  4. カスタムファイル(Controllers, Views, Routes, Migrations)をオーバーレイ
  5. composer install (profiler CLI依存関係含む)
  6. migration + seed 実行 (entrypoint)
- Volume: `/var/profiler` (ログ共有)

### 3. `websocket` - xterm.js バックエンド
- Dockerfile.ws (Node.js 20 ベース)
- 軽量 WebSocket サーバー (`ws` パッケージ)
- エンドポイント: `ws://host/ws/logs/<jobKey>`
- 機能: `tail -f /var/profiler/<jobKey>.raw.log` を spawn して WebSocket にストリーム
- Volume: `/var/profiler` (ログ共有、読み取り専用)

### 4. `nginx` - リバースプロキシ
- Image: `nginx:alpine`
- Port: 8080 (外部公開)
- Config:
  - `/` → PHP-FPM (Laravel)
  - `/ws/` → WebSocket proxy (Node.js :3000)

---

## ファイル構成

```
demo/
├── docker-compose.yml
├── .env                          # DB接続情報等
│
├── docker/
│   ├── app/
│   │   ├── Dockerfile            # PHP 8.3-fpm + extension build
│   │   ├── php.ini               # extension設定
│   │   └── entrypoint.sh         # migration, seed, php-fpm起動
│   ├── nginx/
│   │   └── default.conf          # Nginx設定
│   └── websocket/
│       ├── Dockerfile            # Node.js
│       ├── package.json
│       └── server.js             # WebSocket + tail -f
│
├── laravel-app/                  # Laravel カスタムファイルのみ
│   ├── app/Http/Controllers/
│   │   ├── DashboardController.php   # UI表示
│   │   ├── ProfilerApiController.php # Job制御 API
│   │   └── DemoQueryController.php   # デモクエリ実行
│   ├── database/
│   │   ├── migrations/
│   │   │   └── create_demo_tables.php  # users, posts, comments
│   │   └── seeders/
│   │       └── DemoSeeder.php          # サンプルデータ
│   ├── resources/views/
│   │   └── dashboard.blade.php         # メインUI (xterm.js統合)
│   └── routes/
│       └── web.php                     # ルート定義
│
└── README.md                     # 使い方ドキュメント
```

---

## 実装詳細

### Step 1: Docker基盤 (docker-compose.yml + Dockerfiles)

**docker-compose.yml:**
```yaml
services:
  mariadb:
    image: mariadb:11
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: demo
      MYSQL_USER: demo
      MYSQL_PASSWORD: demo
    volumes:
      - mariadb_data:/var/lib/mysql

  app:
    build:
      context: .
      dockerfile: docker/app/Dockerfile
    volumes:
      - profiler_logs:/var/profiler
    depends_on:
      - mariadb

  websocket:
    build:
      context: docker/websocket
    volumes:
      - profiler_logs:/var/profiler:ro

  nginx:
    image: nginx:alpine
    ports:
      - "8080:80"
    volumes:
      - ./docker/nginx/default.conf:/etc/nginx/conf.d/default.conf
    depends_on:
      - app
      - websocket

volumes:
  mariadb_data:
  profiler_logs:
```

**Dockerfile.app の主要ステップ:**
```dockerfile
FROM php:8.3-fpm

# mysqlnd は PHP に組み込み済み
# 必要パッケージ: autoconf, gcc, make (phpize用)
RUN apt-get update && apt-get install -y autoconf gcc make git unzip

# Extension ビルド
COPY ext/mariadb_profiler /tmp/ext
WORKDIR /tmp/ext
RUN phpize && ./configure && make && make install

# PHP設定
COPY demo/docker/app/php.ini /usr/local/etc/php/conf.d/mariadb_profiler.ini

# Composer + Laravel
COPY --from=composer:2 /usr/bin/composer /usr/bin/composer
RUN composer create-project laravel/laravel /var/www/html --prefer-dist

# CLI profiler tool
COPY cli /opt/profiler/cli
COPY composer.json /opt/profiler/
RUN cd /opt/profiler && composer install --no-dev

# カスタム Laravel ファイル
COPY demo/laravel-app/ /var/www/html/
```

### Step 2: WebSocket サーバー (server.js)

```javascript
const WebSocket = require('ws');
const { spawn } = require('child_process');
const http = require('http');

const server = http.createServer();
const wss = new WebSocket.Server({ server });

wss.on('connection', (ws, req) => {
  // URL: /ws/logs/<jobKey>
  const jobKey = req.url.replace('/ws/logs/', '');
  const logFile = `/var/profiler/${jobKey}.raw.log`;

  // touch file if not exists, then tail -f
  const tail = spawn('tail', ['-f', logFile]);

  tail.stdout.on('data', (data) => {
    if (ws.readyState === WebSocket.OPEN) {
      ws.send(data.toString());
    }
  });

  ws.on('close', () => tail.kill());
});

server.listen(3000);
```

### Step 3: Laravel API (ProfilerApiController)

```
POST   /api/profiler/start          → ジョブ開始 (returns jobKey)
POST   /api/profiler/{key}/stop     → ジョブ停止
GET    /api/profiler/jobs            → ジョブ一覧
POST   /api/demo/queries             → デモクエリ実行
```

**ProfilerApiController.php:**
- `start()`: CLI経由 `php mariadb_profiler.php job start` を実行、jobKeyを返す
- `stop($key)`: CLI経由 `php mariadb_profiler.php job end $key` を実行
- `list()`: CLI経由 `php mariadb_profiler.php job list` → JSONパース

**DemoQueryController.php:**
- `run()`: 各種クエリを実行してプロファイラーにキャプチャさせる
  - SELECT with JOIN
  - INSERT
  - UPDATE
  - Subqueries
  - Aggregate queries

### Step 4: フロントエンド (dashboard.blade.php)

**UI構成:**
```
┌─────────────────────────────────────────────┐
│  MariaDB Query Profiler Demo                │
│                                             │
│  [▶ Start New Session] [🔄 Run Demo Queries]│
│                                             │
│  ┌─────────┬─────────┬─────────┐           │
│  │ abc123  │ def456  │ ghi789  │  ← タブ   │
│  │ (active)│ (active)│ (done)  │           │
│  └─────────┴─────────┴─────────┘           │
│  [■ Stop] [📊 Stats]                       │
│                                             │
│  ┌─────────────────────────────────────────┐│
│  │ $ tail -f abc123.raw.log                ││
│  │ [2025-01-23 10:00:01.000] SELECT ...    ││
│  │ [2025-01-23 10:00:01.050] INSERT ...    ││
│  │ [2025-01-23 10:00:01.100] UPDATE ...    ││
│  │ █                                       ││
│  └─────────────────────────────────────────┘│
└─────────────────────────────────────────────┘
```

**使用ライブラリ (CDN):**
- xterm.js (v5) + xterm-addon-fit
- Tailwind CSS (CDN)
- Alpine.js (軽量なリアクティブUI)

**JavaScript 処理フロー:**
1. Start クリック → `POST /api/profiler/start` → jobKey取得
2. 新タブ作成 → xterm.js Terminal インスタンス生成
3. WebSocket接続 `ws://host/ws/logs/<jobKey>`
4. WebSocket からデータ受信 → `terminal.write(data)`
5. Stop クリック → `POST /api/profiler/{key}/stop`
6. タブ状態を "done" に更新

### Step 5: Nginx 設定

```nginx
server {
    listen 80;

    # Laravel
    root /var/www/html/public;
    index index.php;

    location / {
        try_files $uri $uri/ /index.php?$query_string;
    }

    location ~ \.php$ {
        fastcgi_pass app:9000;
        fastcgi_param SCRIPT_FILENAME $document_root$fastcgi_script_name;
        include fastcgi_params;
    }

    # WebSocket proxy
    location /ws/ {
        proxy_pass http://websocket:3000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
```

---

## 実装順序 (ステップ)

1. **docker-compose.yml + Dockerfiles** を作成
2. **Nginx設定** を作成
3. **WebSocket server.js** を作成
4. **Laravel Controllers** (ProfilerApi, DemoQuery, Dashboard) を作成
5. **Laravel Migrations + Seeders** を作成
6. **Laravel Routes** (web.php) を作成
7. **dashboard.blade.php** (メインUI: タブ + xterm.js + Start/Stop) を作成
8. **entrypoint.sh** (migration実行 + php-fpm起動) を作成
9. **動作確認用 README.md** を作成

---

## 技術的考慮事項

### 実現可能性: ✅ 完全に可能

1. **Extension のビルド**: Dockerfile内で phpize + make で問題なくビルド可能
2. **mysqlnd 連携**: PHP-FPM で mysqlnd はデフォルト有効。Laravel の PDO MySQL ドライバは mysqlnd を使用
3. **ログ共有**: Docker named volume で app ↔ websocket 間のログファイル共有
4. **xterm.js + WebSocket**: Node.js で `tail -f` をストリーミングする標準パターン
5. **CLI 呼び出し**: Laravel から `Process::run()` でプロファイラーCLIを呼び出し可能

### 注意点

- `tail -f` は対象ファイルが存在しない場合に備え、`touch` してから `tail -f` する
- WebSocket 接続切断時に `tail` プロセスを確実に kill する
- jobs.json のファイルロック競合: app (PHP-FPM) と CLI が同時アクセスするが、既存の flock 実装で対応済み
- Laravel の `.env` で DB接続先を Docker サービス名 (`mariadb`) に設定
