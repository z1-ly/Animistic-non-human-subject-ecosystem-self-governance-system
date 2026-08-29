(function () {
  const EV = window.EcoVoice;
  let selectedId = null;
  let selectedProtocol = 'MQTT';

  async function loadProtocols() {
    const protocols = await EV.api('/api/protocols');
    const grid = document.getElementById('protocolGrid');
    grid.innerHTML = protocols.map(p => {
      const sel = p === selectedProtocol ? 'selected' : '';
      return '<div class="protocol-item ' + sel + '" data-protocol="' + p + '">' +
        '<div class="protocol-name">' + (EV.PROTOCOL_LABELS[p] || p) + '</div>' +
        '<div class="protocol-desc">' + (EV.PROTOCOL_DESC[p] || '') + '</div></div>';
    }).join('');
    grid.querySelectorAll('.protocol-item').forEach(item => {
      item.addEventListener('click', () => {
        selectedProtocol = item.dataset.protocol;
        grid.querySelectorAll('.protocol-item').forEach(el =>
          el.classList.toggle('selected', el.dataset.protocol === selectedProtocol));
        document.getElementById('endpointInput').value =
          EV.PROTOCOL_ENDPOINTS[selectedProtocol] || '';
      });
    });
    document.getElementById('endpointInput').value =
      EV.PROTOCOL_ENDPOINTS[selectedProtocol] || '';
  }

  async function loadEntityNetwork() {
    if (!selectedId) return;
    const dashboard = await EV.api('/api/entities/' + selectedId + '/dashboard');
    if (dashboard.protocol) selectedProtocol = dashboard.protocol;
    document.getElementById('topicInput').value = 'ecovoice/entity/' + selectedId + '/vitals';
    document.getElementById('endpointInput').value =
      EV.PROTOCOL_ENDPOINTS[selectedProtocol] || '';
    await loadProtocols();
    await loadIngestOptions();
  }

  async function loadIngestOptions() {
    const nodes = await EV.api('/api/entities/' + selectedId + '/nodes');
    document.getElementById('ingestSensor').innerHTML = nodes.map(n =>
      '<option value="' + n.sensorType + '" data-node="' + n.id + '">' +
      n.name + ' (' + n.nodeCode + ')</option>'
    ).join('');
  }

  async function init() {
    EV.mountShell('network');
    const result = await EV.ensureEntitySelected();
    selectedId = result.selectedId;
    EV.renderEntitySelect(result.entities, selectedId, 'entitySelect');
    await loadEntityNetwork();

    document.addEventListener('entity-changed', async (ev) => {
      selectedId = ev.detail.entityId;
      await loadEntityNetwork();
    });

    document.getElementById('btnApplyNetwork').addEventListener('click', async () => {
      if (!selectedId) return EV.alert({ message: '请先选择权利主体', type: 'warning', title: '提示' });
      await EV.api('/api/network/configure', {
        method: 'POST',
        body: JSON.stringify({
          entityId: selectedId,
          protocol: selectedProtocol,
          endpoint: document.getElementById('endpointInput').value,
          topic: document.getElementById('topicInput').value
        })
      });
      await loadEntityNetwork();
      EV.alert({ message: '组网配置已应用', type: 'success', title: '配置成功' });
    });

    document.getElementById('btnManualIngest').addEventListener('click', async () => {
      if (!selectedId) return EV.alert({ message: '请先选择权利主体', type: 'warning', title: '提示' });
      const sel = document.getElementById('ingestSensor');
      const opt = sel.options[sel.selectedIndex];
      const path = EV.PROTOCOL_INGEST[selectedProtocol] || '/api/ingest/http';
      await EV.api(path, {
        method: 'POST',
        body: JSON.stringify({
          entityId: selectedId,
          nodeId: Number(opt.dataset.node),
          sensorType: sel.value,
          value: Number(document.getElementById('ingestValue').value)
        })
      });
      EV.alert({ message: '体征数据已上报', type: 'success', title: '上报成功' });
    });

    document.getElementById('btnSimulate').addEventListener('click', async () => {
      await EV.api('/api/simulate/tick', { method: 'POST' });
      EV.alert({ message: '已触发模拟采集', type: 'success', title: '模拟成功' });
    });
  }

  init().catch(console.error);
})();
