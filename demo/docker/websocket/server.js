const http = require('http');
const { WebSocketServer } = require('ws');
const { spawn } = require('child_process');
const path = require('path');
const fs = require('fs');

const LOG_DIR = '/var/profiler';
const PORT = 3000;

const server = http.createServer((req, res) => {
  res.writeHead(200, { 'Content-Type': 'text/plain' });
  res.end('WebSocket server running');
});

const wss = new WebSocketServer({ server });

wss.on('connection', (ws, req) => {
  // Extract job key from URL: /ws/logs/<jobKey>
  const match = req.url.match(/^\/ws\/logs\/([a-zA-Z0-9_-]+)$/);
  if (!match) {
    ws.send('\r\n*** Invalid job key ***\r\n');
    ws.close();
    return;
  }

  const jobKey = match[1];
  const logFile = path.join(LOG_DIR, `${jobKey}.raw.log`);

  console.log(`[connect] job=${jobKey}`);

  // Wait for the log file to appear (created by PHP extension), then stream it.
  // Use a polling approach since the volume is read-only for this container.
  let tail = null;

  function startTail() {
    // tail -F retries if the file doesn't exist yet (--follow=name --retry)
    tail = spawn('tail', ['-n', '+1', '-F', logFile]);

    tail.stdout.on('data', (data) => {
      if (ws.readyState === ws.OPEN) {
        // Convert newlines for xterm.js (needs \r\n)
        const text = data.toString().replace(/\n/g, '\r\n');
        ws.send(text);
      }
    });

    tail.stderr.on('data', (data) => {
      // tail -F prints warnings to stderr when file doesn't exist yet; ignore them
      const msg = data.toString();
      if (!msg.includes('has become accessible') && !msg.includes('cannot open')) {
        console.error(`[tail stderr] job=${jobKey}: ${msg}`);
      }
    });

    tail.on('close', (code) => {
      console.log(`[tail exit] job=${jobKey} code=${code}`);
    });
  }

  // If file exists, start immediately; otherwise poll until it appears
  if (fs.existsSync(logFile)) {
    startTail();
  } else {
    ws.send('\x1b[90mWaiting for profiler log file...\x1b[0m\r\n');
    const pollInterval = setInterval(() => {
      if (ws.readyState !== ws.OPEN) {
        clearInterval(pollInterval);
        return;
      }
      if (fs.existsSync(logFile)) {
        clearInterval(pollInterval);
        ws.send('\x1b[90mLog file found, streaming...\x1b[0m\r\n');
        startTail();
      }
    }, 500);

    // Clean up poll on disconnect
    ws.on('close', () => clearInterval(pollInterval));
  }

  ws.on('close', () => {
    console.log(`[disconnect] job=${jobKey}`);
    if (tail) tail.kill('SIGTERM');
  });

  ws.on('error', (err) => {
    console.error(`[ws error] job=${jobKey}: ${err.message}`);
    if (tail) tail.kill('SIGTERM');
  });
});

server.listen(PORT, () => {
  console.log(`WebSocket server listening on port ${PORT}`);
});
