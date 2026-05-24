const state = {
  filter: 'all',
  search: '',
  snapshot: null,
};

const els = {
  subtitle: document.querySelector('#subtitle'),
  statusText: document.querySelector('#statusText'),
  sampledAt: document.querySelector('#sampledAt'),
  pulse: document.querySelector('#pulse'),
  wanDown: document.querySelector('#wanDown'),
  wanUp: document.querySelector('#wanUp'),
  wanPeak: document.querySelector('#wanPeak'),
  wanUpPeak: document.querySelector('#wanUpPeak'),
  onlineCount: document.querySelector('#onlineCount'),
  activeCount: document.querySelector('#activeCount'),
  meshCount: document.querySelector('#meshCount'),
  routerName: document.querySelector('#routerName'),
  rows: document.querySelector('#deviceRows'),
  searchInput: document.querySelector('#searchInput'),
  tabs: [...document.querySelectorAll('.tab')],
};

function formatMbps(value) {
  const numeric = Number(value || 0);
  if (numeric >= 100) return `${Math.round(numeric)} Mbps`;
  if (numeric >= 10) return `${numeric.toFixed(1)} Mbps`;
  if (numeric >= 1) return `${numeric.toFixed(2)} Mbps`;
  if (numeric > 0) return `${Math.round(numeric * 1000)} Kbps`;
  return '0 Kbps';
}

function formatTime(iso) {
  if (!iso) return '--';
  return new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).format(new Date(iso));
}

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, (char) => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#039;',
  })[char]);
}

function deviceGlyph(device) {
  if (device.isMeshNode) return '路';
  const name = `${device.displayName} ${device.originalName}`.toLowerCase();
  if (name.includes('电视') || name.includes('tv')) return 'TV';
  if (name.includes('mac')) return 'Mac';
  if (name.includes('iphone')) return 'iP';
  if (name.includes('ipad')) return 'iPad';
  if (name.includes('camera')) return 'Cam';
  return 'Wi';
}

function visibleDevices() {
  const query = state.search.trim().toLowerCase();
  return (state.snapshot?.devices || []).filter((device) => {
    if (state.filter === 'active' && !device.isActive) return false;
    if (state.filter === 'heavy' && Math.max(device.downMbps, device.upMbps) < 5) return false;
    if (state.filter === 'mesh' && !device.isMeshNode) return false;
    if (!query) return true;
    return [
      device.displayName,
      device.originalName,
      device.ip,
      device.mac,
      device.connectionLabel,
    ].join(' ').toLowerCase().includes(query);
  });
}

function renderRows() {
  const devices = visibleDevices();
  if (!devices.length) {
    els.rows.innerHTML = '<tr><td colspan="6" class="empty">没有匹配的在线设备</td></tr>';
    return;
  }

  els.rows.innerHTML = devices.map((device) => {
    const heavy = Math.max(device.downMbps, device.upMbps) >= 5;
    const downClass = device.downMbps >= 5 ? 'speed high' : device.downMbps > 0 ? 'speed' : 'speed idle';
    const upClass = device.upMbps >= 5 ? 'speed high' : device.upMbps > 0 ? 'speed' : 'speed idle';
    return `
      <tr class="${heavy ? 'heavy' : ''}">
        <td>
          <div class="device-name">
            <div class="avatar">${escapeHtml(deviceGlyph(device))}</div>
            <div class="name-text">
              <strong>${escapeHtml(device.displayName)}</strong>
              <small>${escapeHtml(device.originalName || (device.isActive ? '正在使用网络' : '空闲'))}</small>
            </div>
          </div>
        </td>
        <td><span class="badge ${device.isMeshNode ? 'mesh' : ''}">${escapeHtml(device.connectionLabel)}</span></td>
        <td><span class="${downClass}">${formatMbps(device.downMbps)}</span></td>
        <td><span class="${upClass}">${formatMbps(device.upMbps)}</span></td>
        <td>
          <span class="muted">下 ${formatMbps(device.maxDownMbps || 0)}</span>
          <span class="muted">上 ${formatMbps(device.maxUpMbps || 0)}</span>
        </td>
        <td>
          <span class="mono">${escapeHtml(device.ip || '-')}</span>
          <span class="mono muted">${escapeHtml(device.mac || '-')}</span>
        </td>
      </tr>
    `;
  }).join('');
}

function renderSnapshot(snapshot) {
  state.snapshot = snapshot;
  els.pulse.className = 'pulse ok';
  els.statusText.textContent = '实时连接';
  els.sampledAt.textContent = formatTime(snapshot.sampledAt);
  els.subtitle.textContent = `${snapshot.routerName || '小米路由器'} / ${snapshot.routerHost}`;
  els.wanDown.textContent = formatMbps(snapshot.wan.downMbps);
  els.wanUp.textContent = formatMbps(snapshot.wan.upMbps);
  els.wanPeak.textContent = `历史峰值 ${formatMbps(snapshot.wan.maxDownMbps)}`;
  els.wanUpPeak.textContent = `历史峰值 ${formatMbps(snapshot.wan.maxUpMbps)}`;
  els.onlineCount.textContent = `${snapshot.counts.online}`;
  els.activeCount.textContent = `活跃 ${snapshot.counts.active} / 列表 ${snapshot.counts.total}`;
  els.meshCount.textContent = `${snapshot.counts.meshNodes}`;
  els.routerName.textContent = snapshot.routerName || '主路由';
  renderRows();
}

function renderError(error) {
  els.pulse.className = 'pulse error';
  els.statusText.textContent = '连接失败';
  els.sampledAt.textContent = formatTime(new Date().toISOString());
  els.subtitle.textContent = error.message;
  els.rows.innerHTML = `<tr><td colspan="6" class="empty">${escapeHtml(error.message)}</td></tr>`;
}

async function refresh() {
  try {
    const response = await fetch('/api/snapshot', { cache: 'no-store' });
    const data = await response.json();
    if (!response.ok || !data.ok) throw new Error(data.error || '路由器数据读取失败');
    renderSnapshot(data);
  } catch (error) {
    renderError(error);
  }
}

els.tabs.forEach((tab) => {
  tab.addEventListener('click', () => {
    state.filter = tab.dataset.filter;
    els.tabs.forEach((item) => item.classList.toggle('active', item === tab));
    renderRows();
  });
});

els.searchInput.addEventListener('input', (event) => {
  state.search = event.target.value;
  renderRows();
});

refresh();
setInterval(refresh, 3000);
