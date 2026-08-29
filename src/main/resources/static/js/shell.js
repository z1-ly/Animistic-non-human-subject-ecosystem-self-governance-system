window.EcoVoice = (function () {
  const ENTITY_KEY = 'ecovoice_selected_entity';
  const HUMAN_NODE_KEY = 'ecovoice_human_node';
  const PLATFORM_TITLE = '万物有灵—— 非人类实体AI主权平台';
  const PLATFORM_SUB = '物联网感知 · AI 分析 · 链上主权 · 生态币(ECO)价值流通';

  const NAV_ITEMS = [
    { href: '/', label: '平台概览', id: 'home' },
    { href: '/register.html', label: '身份管理', id: 'register' },
    { href: '/entities.html', label: '权利主体', id: 'entities' },
    { href: '/vitals.html', label: '生命体征', id: 'vitals' },
    { href: '/appeals.html', label: '主权诉求', id: 'appeals' },
    { href: '/tasks.html', label: '任务大厅', id: 'tasks' },
    { href: '/enterprise.html', label: '修复工程与生态流通', id: 'enterprise' },
    { href: '/ecosphere.html', label: '生态币', id: 'ecosphere' },
    { href: '/redeem.html', label: '兑换生态资源', id: 'redeem' },
    { href: '/ledger.html', label: '链上账本', id: 'ledger' },
    { href: '/network.html', label: '物联网络', id: 'network' },
    { href: '/stream.html', label: '实时数据流', id: 'stream' }
  ];

  const PROTOCOL_LABELS = {
    MQTT: 'MQTT',
    HTTP_REST: 'HTTP/REST',
    WEBSOCKET: 'WebSocket',
    COAP: 'CoAP',
    MODBUS_RTU: 'Modbus RTU'
  };

  const PROTOCOL_ENDPOINTS = {
    MQTT: 'mqtt://broker.ecovoice.local:1883',
    HTTP_REST: '/api/ingest/http',
    WEBSOCKET: '/ws/vitals',
    COAP: 'coap://gateway.ecovoice.local/vitals',
    MODBUS_RTU: 'COM3@9600'
  };

  const PROTOCOL_INGEST = {
    MQTT: '/api/ingest/mqtt',
    HTTP_REST: '/api/ingest/http',
    WEBSOCKET: '/api/ingest/ws',
    COAP: '/api/ingest/coap',
    MODBUS_RTU: '/api/ingest/modbus'
  };

  const SENSOR_LABELS = {
    WATER_PH: '水质pH',
    SOIL_MOISTURE: '土壤湿度',
    AIR_TEMPERATURE: '空气温度',
    AIR_HUMIDITY: '空气湿度',
    SOUND_DECIBEL: '声音分贝'
  };

  const CHART_MAP = {
    WATER_PH: 'chartPH',
    SOIL_MOISTURE: 'chartSoil',
    AIR_TEMPERATURE: 'chartTemp',
    AIR_HUMIDITY: 'chartHumid',
    SOUND_DECIBEL: 'chartSound'
  };

  const EMOTION_COLORS = {
    PLEASANT: '#76ff03',
    DISCOMFORT: '#ff9100',
    CRISIS: '#ff5252'
  };

  const PROTOCOL_DESC = {
    MQTT: '轻量发布/订阅，适合低功耗节点',
    HTTP_REST: 'RESTful 直传，易于集成',
    WEBSOCKET: '全双工实时通道',
    COAP: '受限设备 UDP 协议',
    MODBUS_RTU: '工业现场总线'
  };

  async function api(path, options) {
    const res = await fetch(path, {
      headers: { 'Content-Type': 'application/json' },
      ...options
    });
    if (!res.ok) throw new Error(await res.text());
    return res.json();
  }

  function formatTime(iso) {
    if (!iso) return '--:--:--';
    return new Date(iso).toLocaleTimeString('zh-CN', { hour12: false });
  }

  function getSelectedEntityId() {
    const v = localStorage.getItem(ENTITY_KEY);
    return v ? Number(v) : null;
  }

  function setSelectedEntityId(id) {
    if (id == null) localStorage.removeItem(ENTITY_KEY);
    else localStorage.setItem(ENTITY_KEY, String(id));
  }

  function updateClock() {
    const el = document.getElementById('clock');
    if (el) el.textContent = new Date().toLocaleTimeString('zh-CN', { hour12: false });
  }

  async function loadStats() {
    const s = await api('/api/stats');
    const map = {
      statEntities: s.entityCount,
      statNodes: s.nodeCount,
      statReadings: s.readingCount,
      statAppeals: s.activeAppeals != null ? s.activeAppeals : '-',
      statNetworks: s.activeNetworks
    };
    Object.entries(map).forEach(([id, val]) => {
      const el = document.getElementById(id);
      if (el) el.textContent = val;
    });
    return s;
  }

  function renderShell(activePage) {
    const navHtml = NAV_ITEMS.map(item =>
      '<a href="' + item.href + '" class="nav-link' +
      (item.id === activePage ? ' active' : '') + '">' + item.label + '</a>'
    ).join('');

    return '<header class="header">' +
      '<div class="logo-block">' +
      '<div class="logo-icon">灵</div>' +
      '<div><div class="logo-title">' + PLATFORM_TITLE + '</div>' +
      '<div class="logo-sub">' + PLATFORM_SUB + '</div></div></div>' +
      '<div class="header-stats" id="headerStats">' +
      '<div class="stat-chip">权利主体 <span id="statEntities">-</span></div>' +
      '<div class="stat-chip">传感节点 <span id="statNodes">-</span></div>' +
      '<div class="stat-chip">体征上报 <span id="statStatReadings" style="display:none"></span><span id="statReadings">-</span></div>' +
      '<div class="stat-chip">活跃诉求 <span id="statAppeals">-</span></div>' +
      '<div class="stat-chip">活跃网络 <span id="statNetworks">-</span></div></div>' +
      '<div class="header-navline">' +
      '<nav class="main-nav" aria-label="平台导航" data-nav-count="' + NAV_ITEMS.length + '">' + navHtml + '</nav>' +
      '<div class="header-clock" id="clock">--:--:--</div></div></header>';
  }

  function mountShell(activePage) {
    const mount = document.getElementById('appShell');
    if (mount) mount.innerHTML = renderShell(activePage);
    updateClock();
    setInterval(updateClock, 1000);
    loadStats().catch(console.error);
    setInterval(() => loadStats().catch(console.error), 10000);
  }

  async function loadEntities() {
    return api('/api/entities');
  }

  async function ensureEntitySelected() {
    let entities = await loadEntities();
    let id = getSelectedEntityId();
    if (!id || !entities.some(e => e.id === id)) {
      const earth = entities.find(e => e.name.includes('地球'));
      id = earth ? earth.id : (entities[0] ? entities[0].id : null);
      if (id) setSelectedEntityId(id);
    }
    return { entities, selectedId: id };
  }

  function renderEntityPicker(entities, selectedId, containerId) {
    const container = document.getElementById(containerId);
    if (!container) return;
    container.innerHTML = entities.map(e => {
      const active = e.id === selectedId ? 'active' : '';
      const color = e.avatarColor || '#00e5ff';
      const badge = e.category === '行星权利主体' ? ' rights-subject' : '';
      return '<div class="entity-card ' + active + badge + '" data-id="' + e.id +
        '" style="--accent:' + color + '">' +
        '<div class="entity-name">' + e.name + '</div>' +
        '<div class="entity-meta">' + e.category + ' · ' + e.location + '</div>' +
        '<span class="entity-badge">' + e.status + '</span></div>';
    }).join('');

    container.querySelectorAll('.entity-card').forEach(card => {
      card.addEventListener('click', () => {
        setSelectedEntityId(Number(card.dataset.id));
        container.querySelectorAll('.entity-card').forEach(c =>
          c.classList.toggle('active', Number(c.dataset.id) === Number(card.dataset.id)));
        document.dispatchEvent(new CustomEvent('entity-changed', {
          detail: { entityId: Number(card.dataset.id) }
        }));
      });
    });
  }

  function renderEntitySelect(entities, selectedId, selectId) {
    const sel = document.getElementById(selectId);
    if (!sel) return;
    sel.innerHTML = entities.map(e =>
      '<option value="' + e.id + '"' + (e.id === selectedId ? ' selected' : '') + '>' +
      e.name + ' (' + e.category + ')</option>'
    ).join('');
    sel.onchange = () => {
      setSelectedEntityId(Number(sel.value));
      document.dispatchEvent(new CustomEvent('entity-changed', {
        detail: { entityId: Number(sel.value) }
      }));
    };
  }

  function getHumanNode() {
    const raw = localStorage.getItem(HUMAN_NODE_KEY);
    if (!raw) return null;
    try { return JSON.parse(raw); } catch (e) { return null; }
  }

  function setHumanNode(node) {
    if (!node) localStorage.removeItem(HUMAN_NODE_KEY);
    else localStorage.setItem(HUMAN_NODE_KEY, JSON.stringify(node));
    document.dispatchEvent(new CustomEvent('human-node-changed', { detail: { node } }));
  }

  async function registerHumanNode(displayName) {
    const node = await api('/api/human-nodes/register', {
      method: 'POST',
      body: JSON.stringify({ displayName })
    });
    setHumanNode(node);
    return node;
  }

  async function ensureHumanNode() {
    const node = getHumanNode();
    if (!node) return null;
    try {
      return await api('/api/human-nodes/' + node.id);
    } catch (e) {
      setHumanNode(null);
      return null;
    }
  }

  function renderHumanNodeBar(containerId) {
    const el = document.getElementById(containerId);
    if (!el) return;
    const node = getHumanNode();
    if (!node) {
      el.innerHTML = '<div class="human-bar guest">' +
        '<span>您尚未注册为人类节点</span>' +
        '<a href="/register.html" class="btn btn-sm">注册为人类节点</a></div>';
      return;
    }
    el.innerHTML = '<div class="human-bar registered">' +
      '<div class="human-bar-info">' +
      '<span class="tag tag-human">人类节点</span>' +
      '<strong>' + node.displayName + '</strong>' +
      '<code>' + node.peerNodeId + '</code>' +
      '<span class="human-balance">' + (node.walletBalance || 0).toFixed(2) + ' 生态币(ECO)</span>' +
      '</div>' +
      '<button class="btn btn-sm btn-secondary" id="btnLogoutHuman">切换身份</button></div>';
    const logout = document.getElementById('btnLogoutHuman');
    if (logout) logout.onclick = () => {
      if (confirm('退出当前人类节点身份？')) {
        setHumanNode(null);
        renderHumanNodeBar(containerId);
      }
    };
  }

  function alert(options) {
    if (typeof options === 'string') {
      options = { message: options, type: 'info' };
    }
    const { message, type = 'info', title = '', onClose } = options;
    
    const icons = {
      success: '✓',
      error: '✕',
      warning: '⚠',
      info: '✦'
    };
    
    const overlay = document.createElement('div');
    overlay.className = 'ec-alert-overlay';
    overlay.innerHTML = `
      <div class="ec-alert-box">
        <div class="ec-alert-header">
          <div class="ec-alert-icon ${type}">${icons[type]}</div>
          ${title ? '<div class="ec-alert-title">' + title + '</div>' : ''}
        </div>
        <div class="ec-alert-content">${message}</div>
        <div class="ec-alert-actions">
          <button class="ec-alert-btn primary">确定</button>
        </div>
      </div>
    `;
    
    const btn = overlay.querySelector('.ec-alert-btn');
    const close = () => {
      overlay.remove();
      if (onClose) onClose();
    };
    btn.onclick = close;
    overlay.onclick = (e) => {
      if (e.target === overlay) close();
    };
    
    document.body.appendChild(overlay);
    btn.focus();
  }

  function confirm(options) {
    return new Promise((resolve) => {
      if (typeof options === 'string') {
        options = { message: options, type: 'warning' };
      }
      const { message, type = 'warning', title = '确认操作' } = options;
      
      const icons = {
        success: '✓',
        error: '✕',
        warning: '⚠',
        info: '✦'
      };
      
      const overlay = document.createElement('div');
      overlay.className = 'ec-alert-overlay';
      overlay.innerHTML = `
        <div class="ec-alert-box">
          <div class="ec-alert-header">
            <div class="ec-alert-icon ${type}">${icons[type]}</div>
            <div class="ec-alert-title">${title}</div>
          </div>
          <div class="ec-alert-content">${message}</div>
          <div class="ec-alert-actions">
            <button class="ec-alert-btn secondary">取消</button>
            <button class="ec-alert-btn primary">确认</button>
          </div>
        </div>
      `;
      
      const cancelBtn = overlay.querySelectorAll('.ec-alert-btn')[0];
      const confirmBtn = overlay.querySelectorAll('.ec-alert-btn')[1];
      
      const close = (result) => {
        overlay.remove();
        resolve(result);
      };
      
      cancelBtn.onclick = () => close(false);
      confirmBtn.onclick = () => close(true);
      overlay.onclick = (e) => {
        if (e.target === overlay) close(false);
      };
      
      document.body.appendChild(overlay);
      confirmBtn.focus();
    });
  }

  return {
    PLATFORM_TITLE,
    PLATFORM_SUB,
    NAV_ITEMS,
    PROTOCOL_LABELS,
    PROTOCOL_ENDPOINTS,
    PROTOCOL_INGEST,
    SENSOR_LABELS,
    CHART_MAP,
    EMOTION_COLORS,
    PROTOCOL_DESC,
    api,
    formatTime,
    getSelectedEntityId,
    setSelectedEntityId,
    loadStats,
    mountShell,
    loadEntities,
    ensureEntitySelected,
    renderEntityPicker,
    renderEntitySelect,
    getHumanNode,
    setHumanNode,
    registerHumanNode,
    ensureHumanNode,
    renderHumanNodeBar,
    alert,
    confirm
  };
})();
