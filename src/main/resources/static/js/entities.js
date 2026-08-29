(function () {
  const EV = window.EcoVoice;
  let entities = [];
  let selectedId = null;

  async function loadDetail(id) {
    const entity = entities.find(e => e.id === id);
    if (!entity) return;
    document.getElementById('entityCategory').textContent = entity.category;
    const [dashboard, nodes, identity] = await Promise.all([
      EV.api('/api/entities/' + id + '/dashboard'),
      EV.api('/api/entities/' + id + '/nodes'),
      EV.api('/api/blockchain/entities/' + id + '/identity').catch(() => null)
    ]);
    const isEarth = entity.category === '行星权利主体';
    const identityHtml = identity
      ? '<div class="section-label" style="margin-top:16px">数字法人身份 · 链上钱包</div>' +
        '<div class="identity-rows compact">' +
        '<div class="identity-row"><span class="id-label">DID</span><code>' + identity.did + '</code></div>' +
        '<div class="identity-row"><span class="id-label">钱包</span><code>' + identity.walletAddress + '</code></div>' +
        '<div class="identity-row"><span class="id-label">法人号</span><code>' + identity.legalPersonId + '</code></div>' +
        '<div class="identity-row"><span class="id-label">生态币</span><strong style="color:var(--green)">' +
        identity.borrowedBalance.toFixed(2) + ' ECO</strong></div></div>'
      : '';
    document.getElementById('entityDetail').innerHTML =
      '<div class="entity-hero' + (isEarth ? ' earth-profile' : '') + '">' +
      '<h2 style="color:' + entity.avatarColor + '">' + entity.name + '</h2>' +
      '<p>' + entity.description + '</p>' +
      '<p style="margin-top:10px;font-size:0.82rem">' +
      '<span class="tag">' + entity.category + '</span>' +
      '<span class="tag" style="margin-left:6px">' + entity.location + '</span>' +
      '<span class="tag" style="margin-left:6px">' + entity.status + '</span></p></div>' +
      identityHtml +
      '<div class="section-label" style="margin-top:16px">关联传感节点 · ' + nodes.length + ' 个</div>' +
      '<div class="node-table">' + nodes.map(n =>
        '<div class="node-row">' +
        '<span class="tag">' + n.nodeCode + '</span>' +
        '<span>' + n.name + '</span>' +
        '<span class="node-type">' + (EV.SENSOR_LABELS[n.sensorType] || n.sensorType) + '</span>' +
        '<span class="node-pos">' + (n.position || '') + '</span></div>'
      ).join('') + '</div>' +
      '<div class="entity-actions" style="margin-top:16px">' +
      '<a href="/vitals.html" class="btn btn-inline">查看体征</a>' +
      '<a href="/appeals.html" class="btn btn-inline btn-secondary">查看诉求</a>' +
      '<a href="/ledger.html" class="btn btn-inline">链上账本</a></div>';
  }

  async function init() {
    EV.mountShell('entities');
    const result = await EV.ensureEntitySelected();
    entities = result.entities;
    selectedId = result.selectedId;
    EV.renderEntityPicker(entities, selectedId, 'entityList');
    if (selectedId) await loadDetail(selectedId);

    document.addEventListener('entity-changed', async (ev) => {
      selectedId = ev.detail.entityId;
      await loadDetail(selectedId);
    });
  }

  init().catch(console.error);
})();
