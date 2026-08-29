(function () {
  const EV = window.EcoVoice;
  let entities = [];
  let humans = [];

  function fillOptions(selId, list, valueKey, labelFn) {
    const sel = document.getElementById(selId);
    if (!sel) return;
    sel.innerHTML = list.map(item =>
      '<option value="' + item[valueKey] + '">' + labelFn(item) + '</option>'
    ).join('');
  }

  function renderStats(dto) {
    const el = document.getElementById('ecoStats');
    if (!el) return;
    if (!dto || !dto.tokenName) {
      el.innerHTML = '<div class="appeal-empty">未获取生态币数据，请先通过 POST /ecosphere/genesis 初始化创世分配。</div>';
      return;
    }
    el.innerHTML =
      '<div class="eco-stat"><div class="eco-val">' + (dto.totalSupply || 0).toFixed(2) + '</div><div class="eco-lbl">总量（' + (dto.tokenSymbol || 'ECO') + '）</div></div>' +
      '<div class="eco-stat"><div class="eco-val">' + (dto.entityReserve || 0).toFixed(2) + '</div><div class="eco-lbl">合约可用余额</div></div>' +
      '<div class="eco-stat"><div class="eco-val">' + dto.entityCount + '</div><div class="eco-lbl">自然体数量</div></div>' +
      '<div class="eco-stat"><div class="eco-val">' + dto.humanNodeCount + '</div><div class="eco-lbl">人类节点数</div></div>';
  }

  function renderTxs(txs) {
    const el = document.getElementById('txList');
    if (!el) return;
    if (!txs || txs.length === 0) {
      el.innerHTML = '<div class="appeal-empty">暂无交易</div>';
      return;
    }
    el.innerHTML = txs.map(tx =>
      '<div class="tx-item type-' + tx.type + '">' +
      '<div class="tx-head">' + EV.formatTime(tx.createdAt) + ' · ' + (tx.typeLabel || tx.type) + '</div>' +
      '<div class="tx-amount">' + (tx.amount > 0 ? tx.amount.toFixed(2) + ' 生态币(ECO)' : '—') + '</div>' +
      '<div class="tx-flow">' + (tx.fromAddress || '—') + ' → ' + (tx.toAddress || '—') + '</div>' +
      '<div class="tx-payload">' + (tx.payload || '') + '</div>' +
      '<code class="tx-hash">' + (tx.txHash || '') + '</code></div>'
    ).join('');
  }

  function showResult(payload) {
    const box = document.getElementById('resultBox');
    const content = document.getElementById('resultContent');
    if (box) box.classList.remove('hidden');
    if (content) content.textContent = JSON.stringify(payload, null, 2);
  }

  async function loadEcoSphere() {
    try {
      const dto = await EV.api('/api/ecosphere');
      renderStats(dto);
    } catch (e) {
      const el = document.getElementById('ecoStats');
      if (el) el.innerHTML = '<div class="appeal-empty">请先点击「执行创世分配」以首次生成生态币储备。</div>';
    }
  }

  async function loadTxs() {
    try {
      const txs = await EV.api('/api/blockchain/transactions?limit=40');
      renderTxs(txs);
    } catch (e) {
      renderTxs([]);
    }
  }

  async function genesis() {
    try {
      const r = await EV.api('/api/ecosphere/genesis', { method: 'POST' });
      showResult(r);
      EV.alert({ message: '生态币创世分配已完成。', type: 'success', title: '创世完成', onClose: () => { loadEcoSphere(); loadTxs(); } });
    } catch (e) {
      EV.alert({ message: '创世分配失败：' + e.message, type: 'error', title: '错误' });
    }
  }

  async function regenerate() {
    const id = document.getElementById('regenEntityId').value;
    const amount = Number(document.getElementById('regenAmount').value);
    const reason = document.getElementById('regenReason').value.trim();
    if (!id || !amount || amount <= 0) return EV.alert({ message: '请选择自然体并填写有效数量。', type: 'warning', title: '提示' });
    try {
      const r = await EV.api('/api/entities/' + id + '/regenerate', {
        method: 'POST',
        body: JSON.stringify({ amount, reason })
      });
      showResult(r);
      loadEcoSphere();
      loadTxs();
      EV.alert({ message: '生态增量增发成功：' + r.amount + ' ' + r.token + ' → ' + r.entityId, type: 'success', title: '增发成功' });
    } catch (e) {
      EV.alert({ message: '增发失败：' + e.message, type: 'error', title: '错误' });
    }
  }

  async function spend() {
    const humanNodeId = Number(document.getElementById('spendHumanId').value);
    const entityId = Number(document.getElementById('spendEntityId').value);
    const spendCategory = document.getElementById('spendCategory').value;
    const baseAmount = Number(document.getElementById('spendBaseAmount').value);
    const severityMultiplier = Number(document.getElementById('spendSeverity').value);
    const note = document.getElementById('spendNote').value.trim();
    if (!humanNodeId || !entityId || !baseAmount || baseAmount <= 0) {
      return EV.alert({ message: '请选择人类节点、受影响自然体并填写基础金额。', type: 'warning', title: '提示' });
    }
    try {
      const r = await EV.api('/api/ecosphere/spend', {
        method: 'POST',
        body: JSON.stringify({
          spenderType: 'HUMAN_NODE',
          humanNodeId, entityId,
          spendCategory, baseAmount, severityMultiplier, note
        })
      });
      showResult(r);
      loadEcoSphere();
      loadTxs();
      EV.alert({ message: '资源消耗结算成功，总扣款 ' + r.totalCharged + ' 生态币(ECO)', type: 'success', title: '结算成功' });
    } catch (e) {
      EV.alert({ message: '扣款失败：' + e.message, type: 'error', title: '错误' });
    }
  }

  async function transfer() {
    const from = Number(document.getElementById('tfFromId').value);
    const to = Number(document.getElementById('tfToId').value);
    const amount = Number(document.getElementById('tfAmount').value);
    const reason = document.getElementById('tfReason').value.trim();
    if (!from || !to || from === to) return EV.alert({ message: '请选择不同的转出/转入自然体。', type: 'warning', title: '提示' });
    if (!amount || amount <= 0) return EV.alert({ message: '金额必须大于 0', type: 'warning', title: '提示' });
    try {
      const r = await EV.api('/api/ecosphere/transfer', {
        method: 'POST',
        body: JSON.stringify({ fromEntityId: from, toEntityId: to, amount, reason })
      });
      showResult(r);
      loadEcoSphere();
      loadTxs();
      EV.alert({ message: '跨自然体转账成功：' + r.amount + ' ECO', type: 'success', title: '转账成功' });
    } catch (e) {
      EV.alert({ message: '转账失败：' + e.message, type: 'error', title: '错误' });
    }
  }

  async function loadRedeemCatalogMini() {
    const el = document.getElementById('redeemCatalogMini');
    if (!el) return;
    try {
      const list = await EV.api('/api/ecosphere/redeem-catalog');
      el.innerHTML = list.map(v =>
        '<a href="/redeem.html?type=' + v.valueTypeKey + '" class="redeem-mini-item">' +
        '<span class="redeem-mini-cat">' + v.majorCategory + '</span>' +
        '<span class="redeem-mini-name">' + v.coreValueIndicator + '</span>' +
        '<span class="redeem-mini-price">' + v.ecoPricePerUnit + ' ECO/' + v.unit + '</span>' +
        '</a>'
      ).join('');
    } catch (e) {
      el.innerHTML = '<div class="appeal-empty">目录加载失败</div>';
    }
  }

  function renderSelects() {
    const human = humans.length ? humans : [];
    ['spendHumanId'].forEach(id => {
      const sel = document.getElementById(id);
      if (!sel) return;
      sel.innerHTML = human.length ? human.map(h =>
        '<option value="' + h.id + '">' + h.displayName + ' (' + h.peerNodeId + ')</option>'
      ).join('') : '<option value="">（尚无人类节点，请在任务大厅先注册）</option>';
    });
    ['regenEntityId', 'spendEntityId', 'tfFromId', 'tfToId'].forEach(id => {
      const sel = document.getElementById(id);
      if (!sel) return;
      sel.innerHTML = entities.length ? entities.map(e =>
        '<option value="' + e.id + '">' + e.name + ' · ' + e.category + '</option>'
      ).join('') : '<option value="">（尚无自然体）</option>';
    });
  }

  async function init() {
    EV.mountShell('ecosphere');
    try {
      entities = await EV.api('/api/entities');
      humans = await EV.api('/api/human-nodes');
      renderSelects();
      loadEcoSphere();
      loadTxs();
      loadRedeemCatalogMini();
    } catch (e) {
      EV.alert({ message: '数据加载失败：' + e.message, type: 'error', title: '错误' });
    }

    document.getElementById('btnRegenerate').onclick = regenerate;
    document.getElementById('btnSpend').onclick = spend;
    document.getElementById('btnTransfer').onclick = transfer;

    // 顶部一个快捷操作：若总量仍为 0，可提供一个创世按钮
    const header = document.querySelector('.eco-stats');
    const parent = header && header.parentElement;
    if (parent) {
      const b = document.createElement('button');
      b.className = 'btn btn-sm btn-secondary';
      b.style.marginTop = '8px';
      b.textContent = '⚡ 执行创世分配（首次）';
      b.onclick = genesis;
      parent.appendChild(b);
    }
  }

  init().catch(err => { EV.alert({ message: '初始化失败：' + err.message, type: 'error', title: '错误' }); });
})();
