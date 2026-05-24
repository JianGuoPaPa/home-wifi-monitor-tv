import http from 'node:http';
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { XiaomiRouterClient } from './lib/router.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const publicDir = path.join(__dirname, 'public');
const port = Number(process.env.PORT || 8788);
const bindHost = process.env.BIND_HOST || 'localhost';
const routerHost = process.env.ROUTER_HOST;
const routerPassword = process.env.ROUTER_PASSWORD;

let client;
try {
  client = new XiaomiRouterClient({ host: routerHost, password: routerPassword });
} catch (error) {
  client = null;
}

let snapshotCache = null;
let snapshotCacheAt = 0;
let snapshotPromise = null;
const snapshotTtlMs = Number(process.env.SNAPSHOT_TTL_MS || 1200);

const contentTypes = new Map([
  ['.html', 'text/html; charset=utf-8'],
  ['.css', 'text/css; charset=utf-8'],
  ['.js', 'text/javascript; charset=utf-8'],
  ['.json', 'application/json; charset=utf-8'],
  ['.svg', 'image/svg+xml'],
]);

function sendJson(response, status, data) {
  response.writeHead(status, { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' });
  response.end(JSON.stringify(data));
}

async function serveStatic(request, response) {
  const requestUrl = new URL(request.url, `http://${request.headers.host || 'localhost'}`);
  const pathname = requestUrl.pathname === '/' ? '/index.html' : requestUrl.pathname;
  const safePath = path.normalize(pathname).replace(/^(\.\.[/\\])+/, '');
  const filePath = path.join(publicDir, safePath);
  if (!filePath.startsWith(publicDir)) {
    response.writeHead(403);
    response.end('Forbidden');
    return;
  }

  try {
    const body = await fs.readFile(filePath);
    const type = contentTypes.get(path.extname(filePath)) || 'application/octet-stream';
    response.writeHead(200, { 'content-type': type });
    response.end(body);
  } catch {
    response.writeHead(404, { 'content-type': 'text/plain; charset=utf-8' });
    response.end('Not found');
  }
}

const server = http.createServer(async (request, response) => {
  const requestUrl = new URL(request.url, `http://${request.headers.host || 'localhost'}`);

  if (requestUrl.pathname === '/api/health') {
    sendJson(response, 200, {
      ok: Boolean(client),
      routerHost,
      hasHost: Boolean(routerHost),
      hasPassword: Boolean(routerPassword),
      bindHost,
      port,
    });
    return;
  }

  if (requestUrl.pathname === '/api/snapshot') {
    if (!client) {
      sendJson(response, 500, {
        ok: false,
        error: 'ROUTER_HOST and ROUTER_PASSWORD must be set before requesting snapshots.',
      });
      return;
    }

    try {
      if (!snapshotCache || Date.now() - snapshotCacheAt > snapshotTtlMs) {
        snapshotPromise ||= client.snapshot().then((snapshot) => {
          snapshotCache = snapshot;
          snapshotCacheAt = Date.now();
          return snapshot;
        }).finally(() => {
          snapshotPromise = null;
        });
        await snapshotPromise;
      }
      const snapshot = snapshotCache;
      sendJson(response, 200, snapshot);
    } catch (error) {
      sendJson(response, 502, { ok: false, error: error.message });
    }
    return;
  }

  await serveStatic(request, response);
});

server.listen(port, bindHost, () => {
  console.log(`Home Wi-Fi monitor listening at http://${bindHost}:${port}`);
});
