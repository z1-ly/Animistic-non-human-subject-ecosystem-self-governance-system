(function () {
  const EV = window.EcoVoice;
  let pendingAppealId = null;

  function formatDeadline(iso) {
    if (!iso) return '—';
    return new Date(iso).toLocaleString('zh-CN', { hour12: false });
  }

  function renderTaskCard(task, actions) {
  const bounty = task.bountyAmount != null ? task.bountyAmount.toFixed(2) + ' 生态币(ECO)' : '—';
  return '<div class="task-card severity-' + task.severity + '">' +
    '<div class="task-entity">' + task.entityName + ' · ' + task.entityCategory + '</div>' +
    '<div class="appeal-message">「' + task.message + '」</div>' +
    (task.baselineData ? '<div class="appeal-baseline"><strong>基线数据：</strong>' + task.baselineData + '</div>' : '') +
    (task.actionDetail ? '<div class="appeal-action"><strong>具体行动：</strong>' + task.actionDetail + '</div>' : '') +
    (task.actionParameters ? '<div class="appeal-params"><strong>执行参数：</strong>' + task.actionParameters + '</div>' : '') +
    (task.acceptanceCriteria ? '<div class="appeal-criteria"><strong>验收标准：</strong>' + task.acceptanceCriteria + '</div>' : '') +
    (task.measurableTarget ? '<div class="appeal-target"><strong>核心指标：</strong>' + task.measurableTarget + '</div>' : '') +
    (task.rewardRules ? '<div class="appeal-reward"><strong>奖惩规则：</strong>' + task.rewardRules + '</div>' : '') +
    '<div class="appeal-bounty-row">' +
    '<span class="bounty-amount">' + bounty + '</span>' +
    '<span class="tag">' + (task.bountyStatusLabel || '悬赏中') + '</span>' +
    '<span class="appeal-deadline">截止 ' + formatDeadline(task.deadline) + '</span>' +
    (task.claimantName ? '<span class="tag">接取者 ' + task.claimantName + '</span>' : '') +
    '</div>' +
    '<div class="task-actions">' + actions + '</div></div>';
}

  async function loadOpenTasks() {
    const tasks = await EV.api('/api/bounties/open');
    const list = document.getElementById('openTaskList');
    const node = EV.getHumanNode();
    if (!tasks.length) {
      list.innerHTML = '<div class="appeal-empty">暂无开放悬赏，请稍后刷新</div>';
      return;
    }
    list.innerHTML = tasks.map(t => {
      const btn = node
        ? '<button class="btn btn-sm btn-claim" data-id="' + t.appealId + '">接取任务</button>'
        : '<span class="tag">请先注册人类节点</span>';
      return renderTaskCard(t, btn);
    }).join('');
    list.querySelectorAll('.btn-claim').forEach(btn => {
      btn.addEventListener('click', () => claimTask(Number(btn.dataset.id)));
    });
  }

  async function loadMyTasks() {
    const list = document.getElementById('myTaskList');
    const node = await EV.ensureHumanNode();
    if (!node) {
      list.innerHTML = '<div class="appeal-empty">请先注册为人类节点</div>';
      return;
    }
    const tasks = await EV.api('/api/human-nodes/' + node.id + '/tasks');
    const active = tasks.filter(t => t.bountyStatus === 'CLAIMED');
    if (!active.length) {
      list.innerHTML = '<div class="appeal-empty">暂无进行中的任务，去「可接取任务」看看吧</div>';
      return;
    }
    list.innerHTML = active.map(t =>
      renderTaskCard(t,
        '<button class="btn btn-sm btn-complete" data-id="' + t.appealId + '" data-msg="' +
        t.message.replace(/"/g, '&quot;') + '">提交完成</button>')
    ).join('');
    list.querySelectorAll('.btn-complete').forEach(btn => {
      btn.addEventListener('click', () => {
        pendingAppealId = Number(btn.dataset.id);
        document.getElementById('completeHint').textContent = btn.dataset.msg;
        document.getElementById('proofNote').value = '';
        document.getElementById('completeModal').classList.remove('hidden');
      });
    });
  }

  async function loadHumanNodes() {
    const nodes = await EV.api('/api/human-nodes');
    document.getElementById('humanNodeGrid').innerHTML = nodes.length
      ? nodes.map(n =>
        '<div class="human-node-card">' +
        '<div class="node-status"><span class="status-dot"></span>' + (n.online ? '在线' : '离线') + '</div>' +
        '<div class="wallet-card-name">' + n.displayName + '</div>' +
        '<code>' + n.peerNodeId + '</code>' +
        '<div class="wallet-card-meta">完成 ' + n.tasksCompleted + ' 任务 · 接取 ' + n.tasksClaimed + ' 次</div>' +
        '<div class="wallet-card-balance">' + n.walletBalance.toFixed(2) + ' <span>生态币(ECO)</span></div>' +
        '<code class="wallet-card-addr">' + n.walletAddress + '</code></div>'
      ).join('')
      : '<div class="appeal-empty">暂无人类节点，成为第一个入网者吧</div>';
  }

  async function claimTask(appealId) {
    const node = await EV.ensureHumanNode();
    if (!node) return EV.alert({ message: '请先注册为人类节点', type: 'warning', title: '提示' });
    try {
      await EV.api('/api/bounties/' + appealId + '/claim', {
        method: 'POST',
        body: JSON.stringify({ humanNodeId: node.id })
      });
      EV.alert({ message: '任务已接取！修复工程已自动创建，请在「修复工程」页面查看进度和评估结果。', type: 'success', title: '接取成功', onClose: refreshAll });
    } catch (e) {
      EV.alert({ message: '接取失败：' + e.message, type: 'error', title: '错误' });
    }
  }

  async function refreshAll() {
    const node = await EV.ensureHumanNode();
    if (node) EV.setHumanNode(node);
    await loadOpenTasks();
    await loadMyTasks();
    await loadHumanNodes();
  }

  async function loadRedeemMini() {
    const el = document.getElementById('tasksRedeemMini');
    if (!el) return;
    try {
      const list = await EV.api('/api/ecosphere/redeem-catalog');
      el.innerHTML = list.slice(0, 8).map(v =>
        '<a href="/redeem.html?type=' + v.valueTypeKey + '" class="redeem-mini-item">' +
        '<span class="redeem-mini-cat">' + v.majorCategory + '</span>' +
        '<span class="redeem-mini-name">' + v.coreValueIndicator + '</span>' +
        '<span class="redeem-mini-price">' + v.ecoPricePerUnit + ' ECO/' + v.unit + '</span>' +
        '</a>'
      ).join('') +
        (list.length > 8 ? '<a href="/redeem.html" class="redeem-mini-item" style="justify-content:center;align-items:center">' +
        '<span class="redeem-mini-name">查看全部 ' + list.length + ' 类资源 →</span></a>' : '');
    } catch (e) {
      el.innerHTML = '<div class="appeal-empty">资源目录加载失败，请<a href="/redeem.html">直接进入兑换页</a></div>';
    }
  }

  async function init() {
    EV.mountShell('tasks');
    await refreshAll();

    document.querySelectorAll('.tasks-tabs .stream-tab').forEach(tab => {
      tab.addEventListener('click', () => {
        document.querySelectorAll('.tasks-tabs .stream-tab').forEach(t => t.classList.remove('active'));
        tab.classList.add('active');
        const tabName = tab.dataset.tab;
        document.getElementById('panelOpen').classList.toggle('hidden', tabName !== 'open');
        document.getElementById('panelMine').classList.toggle('hidden', tabName !== 'mine');
        document.getElementById('panelNodes').classList.toggle('hidden', tabName !== 'nodes');
      });
    });

    document.addEventListener('human-node-changed', () => refreshAll());

    document.getElementById('btnCancelComplete').addEventListener('click', () => {
      document.getElementById('completeModal').classList.add('hidden');
      pendingAppealId = null;
    });

    document.getElementById('btnConfirmComplete').addEventListener('click', async () => {
      const node = await EV.ensureHumanNode();
      if (!node || !pendingAppealId) return;
      try {
        await EV.api('/api/blockchain/appeals/' + pendingAppealId + '/fulfill', {
          method: 'POST',
          body: JSON.stringify({
            humanNodeId: node.id,
            proofNote: document.getElementById('proofNote').value.trim()
          })
        });
        document.getElementById('completeModal').classList.add('hidden');
        pendingAppealId = null;
        EV.alert({ message: '任务已提交！修复工程将进行AI评估，评估完成后将根据完成情况发放生态币奖励。请在「修复工程」页面查看评估进度。', type: 'success', title: '提交成功', onClose: refreshAll });
      } catch (e) {
        EV.alert({ message: '提交失败：' + e.message, type: 'error', title: '错误' });
      }
    });

    setInterval(refreshAll, 15000);
  }

  init().catch(console.error);
})();
