(function () {
  const EV = window.EcoVoice;
  let selectedId = null;
  let dashboard = null;
  const charts = {};

  function renderTopology(sensors) {
    const topo = document.getElementById('topology');
    topo.innerHTML = '<div class="topo-hub">AI<br>汇聚</div>';
    const cx = topo.clientWidth / 2 || 160;
    const cy = topo.clientHeight / 2 || 120;
    const r = Math.min(cx, cy) - 50;
    const n = Math.max(sensors.length, 1);
    sensors.forEach((s, i) => {
      const angle = (2 * Math.PI * i) / n - Math.PI / 2;
      const x = cx + r * Math.cos(angle) - 28;
      const y = cy + r * Math.sin(angle) - 28;
      const line = document.createElement('div');
      const dx = cx - (x + 28);
      const dy = cy - (y + 28);
      const len = Math.sqrt(dx * dx + dy * dy);
      const deg = Math.atan2(dy, dx) * 180 / Math.PI;
      line.className = 'topo-line';
      line.style.width = len + 'px';
      line.style.left = (x + 28) + 'px';
      line.style.top = (y + 28) + 'px';
      line.style.transform = 'rotate(' + deg + 'deg)';
      topo.insertBefore(line, topo.firstChild);
      const node = document.createElement('div');
      node.className = 'topo-node';
      node.style.left = x + 'px';
      node.style.top = y + 'px';
      node.textContent = s.nodeCode;
      topo.appendChild(node);
    });
  }

  function renderDashboard() {
    if (!dashboard) return;
    document.getElementById('entityHero').innerHTML =
      '<h2 style="color:' + dashboard.avatarColor + '">' + dashboard.name + '</h2>' +
      '<p>' + dashboard.description + '</p>';
    document.getElementById('currentProtocol').textContent =
      '协议: ' + (dashboard.protocolLabel || '未配置');
    document.getElementById('sensorGrid').innerHTML = (dashboard.sensors || []).map(s => {
      const alertCls = s.status === 'ALERT' ? 'alert' : '';
      const val = s.value != null ? s.value : '--';
      return '<div class="sensor-card ' + alertCls + '">' +
        '<div class="sensor-label">' + s.label + '</div>' +
        '<div class="sensor-value ' + alertCls + '">' + val + '</div>' +
        '<div class="sensor-unit">' + s.unit + '</div>' +
        '<div class="sensor-pos">' + s.nodeCode + ' · ' + (s.position || '') + '</div>' +
        '<div style="margin-top:6px;font-size:0.65rem">' +
        '<span class="status-dot ' + (s.online ? '' : 'offline') + '"></span>' +
        (s.online ? s.status : 'OFFLINE') + '</div></div>';
    }).join('');
    renderTopology(dashboard.sensors || []);
    updateCharts(dashboard.chartSeries);
  }

  function initCharts() {
    const colors = ['#00e5ff', '#76ff03', '#ff9100', '#b388ff', '#ff5252'];
    let i = 0;
    Object.entries(EV.CHART_MAP).forEach(([type, canvasId]) => {
      const el = document.getElementById(canvasId);
      if (!el) return;
      charts[type] = new Chart(el.getContext('2d'), {
        type: 'line',
        data: {
          labels: [],
          datasets: [{
            label: EV.SENSOR_LABELS[type],
            data: [],
            borderColor: colors[i % colors.length],
            backgroundColor: colors[i % colors.length] + '22',
            borderWidth: 1.5,
            tension: 0.35,
            pointRadius: 0,
            fill: true
          }]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          animation: false,
          plugins: { legend: { display: false } },
          scales: {
            x: { ticks: { color: '#80cbc4', maxTicksLimit: 5, font: { size: 9 } }, grid: { color: 'rgba(0,229,255,0.08)' } },
            y: { ticks: { color: '#80cbc4', font: { size: 9 } }, grid: { color: 'rgba(0,229,255,0.08)' } }
          }
        }
      });
      i++;
    });
  }

  function updateCharts(series) {
    if (!series) return;
    Object.keys(EV.CHART_MAP).forEach(type => {
      const points = series[type] || [];
      const chart = charts[type];
      if (!chart) return;
      chart.data.labels = points.map(p => p.time);
      chart.data.datasets[0].data = points.map(p => p.value);
      chart.update('none');
    });
  }

  async function loadDashboard() {
    if (!selectedId) return;
    dashboard = await EV.api('/api/entities/' + selectedId + '/dashboard');
    renderDashboard();
  }

  async function init() {
    EV.mountShell('vitals');
    initCharts();
    const result = await EV.ensureEntitySelected();
    selectedId = result.selectedId;
    EV.renderEntitySelect(result.entities, selectedId, 'entitySelect');
    await loadDashboard();

    document.addEventListener('entity-changed', async (ev) => {
      selectedId = ev.detail.entityId;
      await loadDashboard();
    });

    setInterval(loadDashboard, 8000);
  }

  init().catch(console.error);
})();
