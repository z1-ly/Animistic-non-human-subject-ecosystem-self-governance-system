(function () {
  const EV = window.EcoVoice;
  let catalog = [];
  let activeCategory = '';
  let selectedItem = null;
  let humanNode = null;

  function calcTotal(item, quantity) {
    return Math.round(item.ecoPricePerUnit * quantity * 100) / 100;
  }

  function majorCategories() {
    const cats = [];
    catalog.forEach(v => {
      if (!cats.includes(v.majorCategory)) cats.push(v.majorCategory);
    });
    return cats;
  }

  function filteredCatalog() {
    if (!activeCategory) return catalog;
    return catalog.filter(v => v.majorCategory === activeCategory);
  }

  function renderTabs() {
    const el = document.getElementById('categoryTabs');
    if (!el) return;
    const cats = majorCategories();
    el.innerHTML = '<button class="stream-tab' + (activeCategory === '' ? ' active' : '') +
      '" data-cat="">全部</button>' +
      cats.map(c =>
        '<button class="stream-tab' + (activeCategory === c ? ' active' : '') +
        '" data-cat="' + c + '">' + c + '</button>'
      ).join('');
    el.querySelectorAll('.stream-tab').forEach(btn => {
      btn.onclick = () => {
        activeCategory = btn.dataset.cat || '';
        el.querySelectorAll('.stream-tab').forEach(b =>
          b.classList.toggle('active', b === btn));
        renderCatalog();
      };
    });
  }

  function renderCatalog() {
    const el = document.getElementById('redeemCatalog');
    if (!el) return;
    const list = filteredCatalog();
    if (!list.length) {
      el.innerHTML = '<div class="appeal-empty">暂无资源目录</div>';
      return;
    }
    el.innerHTML = list.map(v => {
      const ents = (v.matchingEntities || []).slice(0, 3).map(e => e.entityName).join('、');
      const more = (v.matchingEntities || []).length > 3
        ? ' 等 ' + v.matchingEntities.length + ' 个主体' : '';
      return '<article class="redeem-card" data-key="' + v.valueTypeKey + '">' +
        '<div class="redeem-card-head">' +
        '<span class="redeem-cat">' + v.majorCategory + '</span>' +
        '<h3 class="redeem-name">' + v.coreValueIndicator + '</h3>' +
        '</div>' +
        '<div class="redeem-price">' +
        '<span class="redeem-price-val">' + v.ecoPricePerUnit + '</span>' +
        '<span class="redeem-price-unit"> ECO / ' + v.unit + '</span>' +
        '</div>' +
        '<p class="redeem-default">默认兑换 ' + v.defaultQuantity + ' ' + v.unit +
        ' ≈ <strong>' + v.defaultTotalEco + ' ECO</strong></p>' +
        '<p class="redeem-desc">' + v.refluxLogic + '</p>' +
        '<div class="redeem-entities">对应主体：' + (ents || '—') + more + '</div>' +
        '<button class="btn btn-sm redeem-btn">兑换此资源 →</button>' +
        '</article>';
    }).join('');

    el.querySelectorAll('.redeem-card').forEach(card => {
      card.querySelector('.redeem-btn').onclick = (e) => {
        e.stopPropagation();
        openRedeemModal(card.dataset.key);
      };
      card.onclick = () => openRedeemModal(card.dataset.key);
    });
  }

  function openRedeemModal(valueTypeKey) {
    const item = catalog.find(v => v.valueTypeKey === valueTypeKey);
    if (!item) return;

    const human = EV.getHumanNode();
    if (!human) {
      EV.alert({ message: '请先在任务大厅注册为人类节点，才能兑换生态资源。', type: 'warning', title: '提示' });
      window.location.href = '/tasks.html';
      return;
    }

    selectedItem = item;
    document.getElementById('modalTitle').textContent = item.coreValueIndicator;
    document.getElementById('modalHint').textContent = item.refluxLogic;
    document.getElementById('modalMeta').innerHTML =
      '<span class="tag">' + item.majorCategory + '</span>' +
      '<span class="redeem-modal-scope">' + item.naturalEntityScope + '</span>';
    document.getElementById('modalUnit').textContent = item.unit;
    document.getElementById('modalQuantity').value = item.defaultQuantity;
    document.getElementById('modalUnitPrice').textContent = item.ecoPricePerUnit + ' ECO / ' + item.unit;
    document.getElementById('modalNote').value = '兑换 ' + item.coreValueIndicator;

    const sel = document.getElementById('modalEntityId');
    const ents = item.matchingEntities || [];
    if (!ents.length) {
      sel.innerHTML = '<option value="">（无匹配自然体）</option>';
    } else {
      sel.innerHTML = ents.map(e =>
        '<option value="' + e.entityId + '">' + e.entityName + ' · ' + e.category + '</option>'
      ).join('');
    }

    updateModalTotal();
    document.getElementById('redeemModal').classList.remove('hidden');
  }

  function updateModalTotal() {
    if (!selectedItem) return;
    const qty = Number(document.getElementById('modalQuantity').value) || selectedItem.defaultQuantity;
    document.getElementById('modalTotalEco').textContent = calcTotal(selectedItem, qty) + ' ECO';
  }

  function closeModal() {
    document.getElementById('redeemModal').classList.add('hidden');
    selectedItem = null;
  }

  function updateBalance() {
    if (humanNode) {
      const el = document.getElementById('ecoBalance');
      if (el) {
        el.textContent = humanNode.walletBalance.toFixed(2);
      }
    }
  }

  async function confirmRedeem() {
    if (!selectedItem) return;
    if (!humanNode) return EV.alert({ message: '请先注册人类节点', type: 'warning', title: '提示' });

    const entityId = Number(document.getElementById('modalEntityId').value);
    const quantity = Number(document.getElementById('modalQuantity').value);
    const note = document.getElementById('modalNote').value.trim();
    const total = calcTotal(selectedItem, quantity);

    if (!entityId) return EV.alert({ message: '请选择服务提供自然主体', type: 'warning', title: '提示' });
    if (!quantity || quantity <= 0) return EV.alert({ message: '请填写有效兑换数量', type: 'warning', title: '提示' });
    if ((humanNode.walletBalance || 0) < total) {
      return EV.alert({ 
        message: '生态币余额不足：需 <strong>' + total + ' ECO</strong>，当前 <strong>' +
          (humanNode.walletBalance || 0).toFixed(2) + ' ECO</strong>。可前往任务大厅赚取生态币。', 
        type: 'error', 
        title: '余额不足' 
      });
    }

    const confirmed = await EV.confirm({ 
      message: '确认支付 <strong>' + total + ' 生态币</strong>，向自然主体兑换「' + selectedItem.coreValueIndicator + '」？', 
      title: '确认兑换' 
    });
    if (!confirmed) return;

    try {
      const r = await EV.api('/api/ecosphere/consume', {
        method: 'POST',
        body: JSON.stringify({
          humanNodeId: humanNode.id,
          targetEntityId: entityId,
          valueTypeKey: selectedItem.valueTypeKey,
          quantity: quantity,
          note: note
        })
      });
      closeModal();
      humanNode = await EV.ensureHumanNode();
      updateBalance();
      
      let msg = '<strong>兑换成功</strong>\n\n';
      msg += '资源：' + r.coreValueIndicator + '\n';
      msg += '支付：<strong>' + r.amount + ' ECO</strong>\n';
      msg += '流向：' + r.grantedByEntity + '\n';
      msg += '余额：<strong>' + (humanNode ? humanNode.walletBalance.toFixed(2) : '—') + ' ECO</strong>';
      
      EV.alert({ message: msg, type: 'success', title: '兑换成功' });
    } catch (e) {
      EV.alert({ message: '兑换失败：' + e.message, type: 'error', title: '错误' });
    }
  }

  async function init() {
    EV.mountShell('redeem');
    humanNode = await EV.ensureHumanNode();
    updateBalance();

    document.getElementById('modalQuantity').oninput = updateModalTotal;
    document.getElementById('btnCancelRedeem').onclick = closeModal;
    document.getElementById('btnConfirmRedeem').onclick = confirmRedeem;
    document.getElementById('redeemModal').onclick = (e) => {
      if (e.target.id === 'redeemModal') closeModal();
    };

    try {
      catalog = await EV.api('/api/ecosphere/redeem-catalog');
      renderTabs();
      renderCatalog();

      const params = new URLSearchParams(window.location.search);
      const key = params.get('type');
      if (key && catalog.some(v => v.valueTypeKey === key)) {
        openRedeemModal(key);
      }
    } catch (e) {
      document.getElementById('redeemCatalog').innerHTML =
        '<div class="appeal-empty">目录加载失败：' + e.message + '</div>';
    }
  }

  init().catch(err => EV.alert({ message: '初始化失败：' + err.message, type: 'error', title: '错误' }));
})();
