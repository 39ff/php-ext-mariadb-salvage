const http = require('http');
const { WebSocketServer } = require('ws');
const { spawn, execSync } = require('child_process');
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

  // Create the file if it doesn't exist yet
  try {
    if (!fs.existsSync(logFile)) {
      fs.writeFileSync(logFile, '', { flag: 'a' });
    }
  } catch (err) {
    ws.send(`\r\n*** Cannot create log file: ${err.message} ***\r\n`);
    ws.close();
    return;
  }

  // Send existing content first, then follow
  const tail = spawn('tail', ['-n', '+1', '-f', logFile]);

  tail.stdout.on('data', (data) => {
    if (ws.readyState === ws.OPEN) {
      // Convert newlines for xterm.js (needs \r\n)
      const text = data.toString().replace(/\n/g, '\r\n');
      ws.send(text);
    }
  });

  tail.stderr.on('data', (data) => {
    console.error(`[tail stderr] job=${jobKey}: ${data}`);
  });

  tail.on('close', (code) => {
    console.log(`[tail exit] job=${jobKey} code=${code}`);
  });

  ws.on('close', () => {
    console.log(`[disconnect] job=${jobKey}`);
    tail.kill('SIGTERM');
  });

  ws.on('error', (err) => {
    console.error(`[ws error] job=${jobKey}: ${err.message}`);
    tail.kill('SIGTERM');
  });
});

server.listen(PORT, () => {
  console.log(`WebSocket server listening on port ${PORT}`);
});
