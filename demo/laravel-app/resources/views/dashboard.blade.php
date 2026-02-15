<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="csrf-token" content="{{ csrf_token() }}">
    <title>MariaDB Query Profiler - Demo</title>
    <script src="https://cdn.tailwindcss.com"></script>
    <script defer src="https://cdn.jsdelivr.net/npm/alpinejs@3.x.x/dist/cdn.min.js"></script>
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/@xterm/xterm@5.5.0/css/xterm.min.css">
    <script src="https://cdn.jsdelivr.net/npm/@xterm/xterm@5.5.0/lib/xterm.min.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/@xterm/addon-fit@0.10.0/lib/addon-fit.min.js"></script>
    <style>
        [x-cloak] { display: none !important; }
        .xterm { height: 100%; }
    </style>
</head>
<body class="bg-gray-900 text-gray-100 min-h-screen">

<div x-data="profilerApp()" x-init="init()" class="max-w-7xl mx-auto px-4 py-6">

    <!-- Header -->
    <div class="mb-6">
        <h1 class="text-2xl font-bold text-white">MariaDB Query Profiler</h1>
        <p class="text-gray-400 text-sm mt-1">
            Real-time database query profiling demo &mdash; php-ext-mariadb-salvage
        </p>
    </div>

    <!-- Controls -->
    <div class="flex flex-wrap gap-3 mb-6">
        <button
            @click="startSession()"
            :disabled="starting"
            class="px-4 py-2 bg-green-600 hover:bg-green-700 disabled:bg-green-800 disabled:cursor-wait text-white rounded font-medium transition flex items-center gap-2"
        >
            <svg x-show="!starting" class="w-4 h-4" fill="currentColor" viewBox="0 0 20 20"><polygon points="5,3 19,10 5,17"/></svg>
            <svg x-show="starting" class="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24"><circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/><path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"/></svg>
            <span x-text="starting ? 'Starting...' : 'Start Session'"></span>
        </button>

        <button
            @click="runDemoQueries()"
            :disabled="runningQueries || sessions.filter(s => s.active).length === 0"
            class="px-4 py-2 bg-blue-600 hover:bg-blue-700 disabled:bg-gray-700 disabled:cursor-not-allowed text-white rounded font-medium transition flex items-center gap-2"
        >
            <svg x-show="!runningQueries" class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 7v10c0 2.21 3.582 4 8 4s8-1.79 8-4V7M4 7c0 2.21 3.582 4 8 4s8-1.79 8-4M4 7c0-2.21 3.582-4 8-4s8 1.79 8 4"/></svg>
            <svg x-show="runningQueries" class="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24"><circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/><path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"/></svg>
            <span x-text="runningQueries ? 'Running...' : 'Run Demo Queries'"></span>
        </button>

        <div x-show="queryResult" x-cloak
             class="flex items-center gap-2 px-3 py-2 bg-gray-800 rounded text-sm text-green-400">
            <svg class="w-4 h-4" fill="currentColor" viewBox="0 0 20 20"><path fill-rule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clip-rule="evenodd"/></svg>
            <span x-text="queryResult"></span>
        </div>
    </div>

    <!-- Tabs -->
    <div x-show="sessions.length > 0" x-cloak>
        <!-- Tab bar -->
        <div class="flex border-b border-gray-700 overflow-x-auto">
            <template x-for="(session, index) in sessions" :key="session.key">
                <button
                    @click="activeTab = session.key"
                    :class="{
                        'border-b-2 border-blue-500 text-white bg-gray-800': activeTab === session.key,
                        'text-gray-400 hover:text-gray-200 hover:bg-gray-800/50': activeTab !== session.key
                    }"
                    class="px-4 py-2 text-sm font-mono whitespace-nowrap transition flex items-center gap-2"
                >
                    <!-- Status dot -->
                    <span
                        :class="session.active ? 'bg-green-500 animate-pulse' : 'bg-gray-500'"
                        class="w-2 h-2 rounded-full inline-block"
                    ></span>
                    <span x-text="session.key.substring(0, 8)"></span>
                    <span x-show="!session.active && session.queryCount !== null"
                          class="text-xs text-gray-500"
                          x-text="'(' + session.queryCount + ' queries)'"></span>
                </button>
            </template>
        </div>

        <!-- Tab content -->
        <template x-for="session in sessions" :key="session.key">
            <div x-show="activeTab === session.key" class="bg-gray-800 rounded-b-lg">
                <!-- Session toolbar -->
                <div class="flex items-center gap-3 px-4 py-2 border-b border-gray-700">
                    <span class="text-xs font-mono text-gray-400" x-text="'Job: ' + session.key"></span>
                    <span
                        :class="session.active ? 'text-green-400' : 'text-gray-500'"
                        class="text-xs"
                        x-text="session.active ? 'RECORDING' : 'STOPPED'"
                    ></span>
                    <div class="flex-1"></div>
                    <button
                        x-show="session.active"
                        @click="stopSession(session.key)"
                        :disabled="session.stopping"
                        class="px-3 py-1 bg-red-600 hover:bg-red-700 disabled:bg-red-800 text-white text-xs rounded font-medium transition flex items-center gap-1"
                    >
                        <svg class="w-3 h-3" fill="currentColor" viewBox="0 0 20 20"><rect x="4" y="4" width="12" height="12" rx="1"/></svg>
                        <span x-text="session.stopping ? 'Stopping...' : 'Stop'"></span>
                    </button>
                </div>

                <!-- Terminal -->
                <div
                    :id="'terminal-' + session.key"
                    class="h-96"
                    x-init="$nextTick(() => initTerminal(session))"
                ></div>
            </div>
        </template>
    </div>

    <!-- Empty state -->
    <div x-show="sessions.length === 0" class="text-center py-20 text-gray-500">
        <svg class="w-16 h-16 mx-auto mb-4 opacity-30" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1" d="M4 7v10c0 2.21 3.582 4 8 4s8-1.79 8-4V7M4 7c0 2.21 3.582 4 8 4s8-1.79 8-4M4 7c0-2.21 3.582-4 8-4s8 1.79 8 4m0 5c0 2.21-3.582 4-8 4s-8-1.79-8-4"/>
        </svg>
        <p class="text-lg">No active profiling sessions</p>
        <p class="text-sm mt-1">Click "Start Session" to begin capturing queries</p>
    </div>
</div>

<script>
function profilerApp() {
    return {
        sessions: [],
        activeTab: null,
        starting: false,
        runningQueries: false,
        queryResult: null,

        init() {
            this.loadJobs();
        },

        async loadJobs() {
            try {
                const res = await fetch('/api/profiler/jobs');
                const data = await res.json();
                // Restore active sessions from server state
                for (const [key, job] of Object.entries(data.active_jobs || {})) {
                    if (!this.sessions.find(s => s.key === key)) {
                        this.sessions.push({
                            key,
                            active: true,
                            stopping: false,
                            queryCount: null,
                            terminal: null,
                            ws: null,
                        });
                    }
                }
                if (this.sessions.length > 0 && !this.activeTab) {
                    this.activeTab = this.sessions[0].key;
                }
            } catch (e) {
                console.error('Failed to load jobs:', e);
            }
        },

        async startSession() {
            this.starting = true;
            this.queryResult = null;
            try {
                const res = await fetch('/api/profiler/start', {
                    method: 'POST',
                    headers: {
                        'X-CSRF-TOKEN': document.querySelector('meta[name="csrf-token"]').content,
                    },
                });
                const data = await res.json();
                if (data.success) {
                    const session = {
                        key: data.job_key,
                        active: true,
                        stopping: false,
                        queryCount: null,
                        terminal: null,
                        ws: null,
                    };
                    this.sessions.push(session);
                    this.activeTab = session.key;
                }
            } catch (e) {
                console.error('Failed to start session:', e);
            }
            this.starting = false;
        },

        async stopSession(key) {
            const session = this.sessions.find(s => s.key === key);
            if (!session) return;
            session.stopping = true;

            try {
                const res = await fetch(`/api/profiler/${key}/stop`, {
                    method: 'POST',
                    headers: {
                        'X-CSRF-TOKEN': document.querySelector('meta[name="csrf-token"]').content,
                    },
                });
                const data = await res.json();

                if (res.ok) {
                    session.active = false;
                    session.queryCount = data.query_count ?? null;

                    // Close WebSocket (tail -f will be killed server-side)
                    if (session.ws) {
                        session.ws.close();
                    }

                    // Write final message to terminal
                    if (session.terminal) {
                        session.terminal.writeln('');
                        session.terminal.writeln(
                            '\x1b[33m--- Session stopped' +
                            (data.query_count !== undefined ? ` (${data.query_count} queries captured)` : '') +
                            ' ---\x1b[0m'
                        );
                    }
                } else {
                    console.error('Stop session failed:', data.error || res.statusText);
                }
            } catch (e) {
                console.error('Failed to stop session:', e);
            } finally {
                session.stopping = false;
            }
        },

        async runDemoQueries() {
            this.runningQueries = true;
            this.queryResult = null;
            try {
                const res = await fetch('/api/demo/queries', {
                    method: 'POST',
                    headers: {
                        'X-CSRF-TOKEN': document.querySelector('meta[name="csrf-token"]').content,
                    },
                });
                const data = await res.json();
                if (data.success) {
                    this.queryResult = `${data.queries_executed} queries executed`;
                    // Auto-clear message after 5 seconds
                    setTimeout(() => { this.queryResult = null; }, 5000);
                }
            } catch (e) {
                console.error('Failed to run queries:', e);
            }
            this.runningQueries = false;
        },

        initTerminal(session) {
            const container = document.getElementById('terminal-' + session.key);
            if (!container || session.terminal) return;

            const term = new Terminal({
                cursorBlink: false,
                disableStdin: true,
                fontSize: 13,
                fontFamily: "'JetBrains Mono', 'Fira Code', 'Cascadia Code', monospace",
                theme: {
                    background: '#1f2937',
                    foreground: '#e5e7eb',
                    cursor: '#e5e7eb',
                    selectionBackground: '#374151',
                },
                scrollback: 10000,
                convertEol: true,
            });

            const fitAddon = new FitAddon.FitAddon();
            term.loadAddon(fitAddon);
            term.open(container);
            fitAddon.fit();

            session.terminal = term;

            // Fit on resize
            const resizeObserver = new ResizeObserver(() => fitAddon.fit());
            resizeObserver.observe(container);

            // Welcome message
            term.writeln('\x1b[36m--- Profiler session: ' + session.key + ' ---\x1b[0m');
            term.writeln('\x1b[90mConnecting to log stream...\x1b[0m');
            term.writeln('');

            // Connect WebSocket
            this.connectWebSocket(session);
        },

        connectWebSocket(session) {
            const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
            const wsUrl = `${protocol}//${location.host}/ws/logs/${session.key}`;

            const ws = new WebSocket(wsUrl);
            session.ws = ws;

            ws.onopen = () => {
                if (session.terminal) {
                    session.terminal.writeln('\x1b[32m--- Connected ---\x1b[0m');
                    session.terminal.writeln('');
                }
            };

            ws.onmessage = (event) => {
                if (session.terminal) {
                    session.terminal.write(event.data);
                }
            };

            ws.onclose = () => {
                if (session.terminal && session.active) {
                    session.terminal.writeln('');
                    session.terminal.writeln('\x1b[31m--- Disconnected ---\x1b[0m');
                }
            };

            ws.onerror = (err) => {
                console.error('WebSocket error:', err);
            };
        },
    };
}
</script>

</body>
</html>
