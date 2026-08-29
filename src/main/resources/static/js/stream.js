(function () {
  const EV = window.EcoVoice;
  const streamItems = [];
  const MAX_STREAM = 100;
  let filterEntityId = null;
  let ws = null;
  const charts = {};

  function prependStream(item) {
    if (filterEntityId && item.entityId !== filterEntityId) return;
    streamItems.unshift(item);
    if (streamItems.length > MAX_STREAM) streamItems.length = MAX_STREAM;
    renderStreamList();
  }

  function renderStreamList() {
    const filtered = filterEntityId
      ? streamItems.filter(v => v.entityId === filterEntityId)
      : streamItems;
    document.getElementById('streamList').innerHTML = filtered.map(v => {
      const alert = v.status === 'ALERT';
      return '<div class="stream-item ' + (alert ? 'alert' : '') + '">' +
        '<span class="stream-time">' + EV.formatTime(v.recordedAt) + '</span>' +
        '<span>' + v.entityName + '</span>' +
        '<span class="tag">' + v.protocolLabel + '</span>' +
        '<span>' + v.sensorLabel + ' · ' + v.nodeCode + '</span>' +
        '<span class="stream-val ' + (alert ? 'alert' : '') + '">' + v.value + ' ' + v.unit + '</span>' +
        '<span>' + v.status + '</span></div>';
    }).join('');
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

  async function loadChartsForFilter() {
    const id = filterEntityId || EV.getSelectedEntityId();
    if (!id) return;
    const dashboard = await EV.api('/api/entities/' + id + '/dashboard');
    updateCharts(dashboard.chartSeries);
  }

  async function loadStream() {
    const url = filterEntityId
      ? '/api/vitals/stream?entityId=' + filterEntityId + '&limit=60'
      : '/api/vitals/stream?limit=60';
    const items = await EV.api(url);
    streamItems.length = 0;
    items.slice().reverse().forEach(item => streamItems.push(item));
    renderStreamList();
  }

  function connectWebSocket() {
    const proto = location.protocol === 'https:' ? 'wss:' : 'ws:';
    ws = new WebSocket(proto + '//' + location.host + '/ws/vitals');
    ws.onopen = () => { document.getElementById('wsLabel').textContent = '实时通道已连接'; };
    ws.onclose = () => {
      document.getElementById('wsLabel').textContent = '重连中...';
      setTimeout(connectWebSocket, 3000);
    };
    ws.onmessage = (ev) => {
      prependStream(JSON.parse(ev.data));
      loadChartsForFilter();
    };
  }

  async function init() {
    EV.mountShell('stream');
    initCharts();
    const result = await EV.ensureEntitySelected();
    const filter = document.getElementById('entityFilter');
    filter.innerHTML = '<option value="">全部权利主体</option>' +
      result.entities.map(e =>
        '<option value="' + e.id + '">' + e.name + '</option>'
      ).join('');
    filterEntityId = EV.getSelectedEntityId();
    filter.value = filterEntityId || '';
    filter.onchange = async () => {
      filterEntityId = filter.value ? Number(filter.value) : null;
      await loadStream();
      await loadChartsForFilter();
    };

    await loadStream();
    await loadChartsForFilter();
    connectWebSocket();
    setInterval(loadStream, 12000);
  }

  init().catch(console.error);
})();
