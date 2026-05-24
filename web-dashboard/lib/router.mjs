import crypto from 'node:crypto';

const DEFAULT_DEVICE_ID = 'fallback-device-id';
const DEFAULT_KEY = 'a2ffa5c9be07488bbb04a3a47d3c5f6a';

export function sha1(value) {
  return crypto.createHash('sha1').update(value).digest('hex');
}

export function buildLoginPayload({ password, key, deviceId, timestamp, random }) {
  const nonce = `0_${deviceId}_${timestamp}_${random}`;
  return {
    username: 'admin',
    password: sha1(`${nonce}${sha1(`${password}${key}`)}`),
    logtype: '2',
    nonce,
  };
}

function bytesPerSecondToMbps(value) {
  const numeric = Number(value || 0);
  if (!Number.isFinite(numeric)) return 0;
  return Math.round((numeric * 8 / 1_000_000) * 1000) / 1000;
}

function pickDisplayName(device) {
  return device.name || device.oname || device.mac || '未知设备';
}

function pickIp(device) {
  const active = (device.ip || []).find((row) => row.active && row.ip);
  const first = (device.ip || []).find((row) => row.ip);
  return active?.ip || first?.ip || '';
}

export function normalizeDevices(payload) {
  const list = Array.isArray(payload?.list) ? payload.list : [];
  return list.map((device) => {
    const stats = device.statistics || {};
    const ipRows = Array.isArray(device.ip) ? device.ip : [];
    const downRaw = stats.downspeed ?? ipRows.reduce((sum, row) => sum + Number(row.downspeed || 0), 0);
    const upRaw = stats.upspeed ?? ipRows.reduce((sum, row) => sum + Number(row.upspeed || 0), 0);
    const parent = device.parent || '';
    const isMeshNode = Number(device.isap || 0) > 0;

    return {
      mac: String(device.mac || '').toUpperCase(),
      displayName: pickDisplayName(device),
      originalName: device.oname || '',
      ip: pickIp(device),
      ips: ipRows.map((row) => row.ip).filter(Boolean),
      downBps: Number(downRaw || 0),
      upBps: Number(upRaw || 0),
      downMbps: bytesPerSecondToMbps(downRaw),
      upMbps: bytesPerSecondToMbps(upRaw),
      onlineSeconds: Number(stats.online ?? ipRows[0]?.online ?? device.online ?? 0),
      type: Number(device.type ?? -1),
      isap: Number(device.isap || 0),
      parent: parent.toUpperCase(),
      connectionLabel: isMeshNode ? 'Mesh 节点' : parent ? `via ${parent.toUpperCase()}` : '主路由直连',
      isMeshNode,
      isActive: Number(downRaw || 0) > 0 || Number(upRaw || 0) > 0,
      authority: device.authority || {},
    };
  }).sort((a, b) => (b.downBps + b.upBps) - (a.downBps + a.upBps));
}

export function mergeStatusRates(devices, statusPayload) {
  const byMac = new Map((statusPayload?.devStatistics || []).map((row) => [String(row.mac || '').toUpperCase(), row]));
  return devices.map((device) => {
    const row = byMac.get(device.mac);
    if (!row) return device;
    const downBps = Number(row.downspeed || 0);
    const upBps = Number(row.upspeed || 0);
    return {
      ...device,
      downBps,
      upBps,
      downMbps: bytesPerSecondToMbps(downBps),
      upMbps: bytesPerSecondToMbps(upBps),
      maxDownMbps: bytesPerSecondToMbps(row.maxdownloadspeed),
      maxUpMbps: bytesPerSecondToMbps(row.maxuploadspeed),
      totalDownloadBytes: Number(row.download || 0),
      totalUploadBytes: Number(row.upload || 0),
      isActive: downBps > 0 || upBps > 0,
    };
  }).sort((a, b) => (b.downBps + b.upBps) - (a.downBps + a.upBps));
}

export function summarizeWan(statusPayload) {
  const wan = statusPayload?.wanStatistics || statusPayload?.wan || {};
  return {
    downMbps: bytesPerSecondToMbps(wan.downspeed),
    upMbps: bytesPerSecondToMbps(wan.upspeed),
    maxDownMbps: bytesPerSecondToMbps(wan.maxdownloadspeed),
    maxUpMbps: bytesPerSecondToMbps(wan.maxuploadspeed),
    totalDownloadBytes: Number(wan.download || 0),
    totalUploadBytes: Number(wan.upload || 0),
  };
}

function extractLoginMeta(html) {
  const deviceId = html.match(/var deviceId = '([^']+)'/)?.[1] || DEFAULT_DEVICE_ID;
  const key = html.match(/key:\s*'([^']+)'/)?.[1] || DEFAULT_KEY;
  return { deviceId, key };
}

function buildParentNames(devices, topoPayload) {
  const names = new Map();
  for (const device of devices) {
    if (device.mac) names.set(device.mac, device.displayName);
  }
  const leafs = topoPayload?.graph?.leafs || [];
  for (const leaf of leafs) {
    const mac = String(leaf.mac || '').toUpperCase();
    if (mac) names.set(mac, leaf.locale || leaf.name || leaf.ssid || mac);
  }
  return names;
}

function annotateConnections(devices, topoPayload) {
  const parentNames = buildParentNames(devices, topoPayload);
  return devices.map((device) => {
    if (device.isMeshNode) {
      return { ...device, connectionLabel: device.displayName.includes('MiWiFi') ? 'Mesh 节点' : `${device.displayName} Mesh 节点` };
    }
    if (!device.parent) return device;
    const parentName = parentNames.get(device.parent) || device.parent;
    return { ...device, connectionLabel: `经由 ${parentName}` };
  });
}

async function fetchText(url, options = {}) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), options.timeoutMs || 12000);
  try {
    const response = await fetch(url, { ...options, signal: controller.signal });
    const text = await response.text();
    return { response, text };
  } finally {
    clearTimeout(timeout);
  }
}

async function fetchJson(url, options = {}) {
  const { response, text } = await fetchText(url, options);
  try {
    return JSON.parse(text);
  } catch {
    throw new Error(`Router returned non-JSON response from ${url}: ${text.slice(0, 120)}`);
  }
}

export class XiaomiRouterClient {
  constructor({ host, password }) {
    if (!host) throw new Error('ROUTER_HOST is required');
    if (!password) throw new Error('ROUTER_PASSWORD is required');
    this.host = host;
    this.password = password;
    this.base = `http://${host}`;
    this.token = '';
    this.tokenCreatedAt = 0;
    this.loginPromise = null;
  }

  async login() {
    const { text } = await fetchText(`${this.base}/cgi-bin/luci/web`);
    const { deviceId, key } = extractLoginMeta(text);
    const payload = buildLoginPayload({
      password: this.password,
      key,
      deviceId,
      timestamp: Math.floor(Date.now() / 1000),
      random: Math.floor(Math.random() * 10000),
    });
    const body = new URLSearchParams(payload);
    const json = await fetchJson(`${this.base}/cgi-bin/luci/api/xqsystem/login`, {
      method: 'POST',
      body,
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    });
    if (json.code !== 0 || !json.token) {
      throw new Error(json.msg || 'Router login failed');
    }
    this.token = json.token;
    this.tokenCreatedAt = Date.now();
    return this.token;
  }

  async ensureToken() {
    if (!this.token || Date.now() - this.tokenCreatedAt > 20 * 60 * 1000) {
      this.loginPromise ||= this.login().finally(() => {
        this.loginPromise = null;
      });
      await this.loginPromise;
    }
    return this.token;
  }

  async api(path, retry = true) {
    const token = await this.ensureToken();
    const json = await fetchJson(`${this.base}/cgi-bin/luci/;stok=${token}/${path}`);
    if (json.code === 401 && retry) {
      this.token = '';
      return this.api(path, false);
    }
    return json;
  }

  async snapshot() {
    await this.ensureToken();
    const [devicelist, status, topo] = await Promise.all([
      this.api('api/misystem/devicelist'),
      this.api('api/xqsystem/status'),
      this.api('api/misystem/topo_graph').catch(() => ({})),
    ]);

    let devices = normalizeDevices(devicelist);
    devices = mergeStatusRates(devices, status);
    devices = annotateConnections(devices, topo);

    const activeDevices = devices.filter((device) => device.isActive);
    return {
      ok: true,
      routerHost: this.host,
      routerName: topo?.graph?.name || topo?.graph?.ssid || '',
      sampledAt: new Date().toISOString(),
      counts: {
        total: devices.length,
        online: Number(status?.count || devicelist?.list?.length || devices.length),
        active: activeDevices.length,
        wifi: devices.filter((device) => !device.isMeshNode).length,
        meshNodes: devices.filter((device) => device.isMeshNode).length,
      },
      wan: summarizeWan(status),
      devices,
    };
  }
}
