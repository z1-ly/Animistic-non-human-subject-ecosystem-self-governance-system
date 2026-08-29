(function () {
  const EV = window.EcoVoice;
  let selectedId = null;
  let pendingAppealId = null;
  let pendingAction = null;

  function formatDeadline(iso) {
    if (!iso) return '—';
    return new Date(iso).toLocaleString('zh-CN', { hour12: false });
  }

  function shortAddr(addr) {
    if (!addr || addr.length < 12) return addr || '—';
    return addr.slice(0, 8) + '…' + addr.slice(-6);
  }

  function renderWallet(identity) {
    document.getElementById('walletBody').innerHTML =
      '<div class="wallet-grid">' +
      '<div class="wallet-stat"><div class="wallet-val">' + identity.borrowedBalance.toFixed(2) + '</div><div class="wallet-lbl">生态币(ECO)</div></div>' +
      '</div>' +
      '<div class="identity-rows">' +
      '<div class="identity-row"><span class="id-label">数字身份 DID</span><code>' + identity.did + '</code></div>' +
      '<div class="identity-row"><span class="id-label">链上钱包</span><code>' + identity.walletAddress + '</code></div>' +
      '<div class="identity-row"><span class="id-label">法人登记号</span><code>' + identity.legalPersonId + '</code></div>' +
      '<div class="identity-row"><span class="id-label">AI 代理 ID</span><code>' + identity.aiAgentId + '</code></div>' +
      '</div>';
  }

  function renderEmotion(emotion) {
    if (!emotion) return;
    document.getElementById('emotionScore').textContent = emotion.index;
    document.getElementById('emotionScore').style.color = emotion.stateColor || EV.EMOTION_COLORS[emotion.state] || '#00e5ff';
    document.getElementById('emotionState').textContent = '「' + emotion.stateLabel + '」';
    document.getElementById('emotionState').style.color = emotion.stateColor || EV.EMOTION_COLORS[emotion.state];
    document.getElementById('emotionSummary').textContent = emotion.summary + (emotion.factors && emotion.factors.length ? ' · ' + emotion.factors.join('；') : '');
  }

  function renderAppeals(appeals) {
    console.log('[appeals] renderAppeals called with:', appeals);
    const container = document.getElementById('appealCards');
    console.log('[appeals] container found:', !!container);
    const human = EV.getHumanNode();

    if (!appeals || appeals.length === 0) {
      container.innerHTML = '<div class="appeal-empty">暂无诉求，权利主体状态平稳</div>';
      return;
    }

    container.innerHTML = appeals.map(a => {
      const bounty = a.bountyAmount != null ? a.bountyAmount.toFixed(0) + ' ECO' : '—';
      const status = a.bountyStatusLabel || '—';
      let actionBtn = '';
      let urgencyIcon = '';
      let severityColor = 'var(--color-info)';
      
      if (a.severity === 'HIGH') {
        urgencyIcon = '⚠';
        severityColor = 'var(--color-danger)';
      } else if (a.severity === 'MEDIUM') {
        urgencyIcon = '⚡';
        severityColor = 'var(--color-warning)';
      }

      if (a.bountyStatus === 'OPEN' && a.active && human) {
        actionBtn = '<button class="btn btn-sm btn-fulfill btn-claim-local" data-appeal="' + a.id + '" style="background:' + severityColor + '">接取任务</button>';
      } else if (a.bountyStatus === 'CLAIMED' && human && a.claimantNodeId === human.id) {
        actionBtn = '<button class="btn btn-sm btn-fulfill btn-complete-local" data-appeal="' + a.id + '" data-msg="' + a.message.replace(/"/g, '&quot;') + '" style="background:' + severityColor + '">提交完成</button>';
      } else if (a.bountyStatus === 'FULFILLED') {
        actionBtn = '<span class="tag tag-green">已由 ' + (a.fulfillerName || '—') + ' 完成</span>';
      } else if (a.bountyStatus === 'CLAIMED' && a.claimantName) {
        actionBtn = '<span class="tag">接取者 ' + a.claimantName + '</span>';
      } else if (a.bountyStatus === 'OPEN') {
        actionBtn = '<a href="/tasks.html" class="tag" style="border-color:' + severityColor + ';color:' + severityColor + '">前往任务大厅</a>';
      }

      const shortMessage = a.message.length > 80 ? a.message.substring(0, 80) + '…' : a.message;

      return '<div class="appeal-card severity-' + a.severity + '" style="border-color:' + severityColor + '">' +
        '<div class="appeal-header">' +
        '<span class="appeal-urgency">' + urgencyIcon + '</span>' +
        '<span class="appeal-type">' + a.appealLabel + '</span>' +
        '<span class="tag tag-mini" style="background:rgba(0,0,0,0.2);border-color:' + severityColor + ';">' + status + '</span>' +
        '</div>' +
        '<div class="appeal-message">' + shortMessage + '</div>' +
        '<div class="appeal-footer">' +
        '<span class="bounty-amount" style="color:' + severityColor + '">' + bounty + '</span>' +
        actionBtn + '</div></div>';
    }).join('');

    container.querySelectorAll('.btn-claim-local').forEach(btn => {
      btn.addEventListener('click', () => claimLocal(Number(btn.dataset.appeal)));
    });

    container.querySelectorAll('.btn-complete-local').forEach(btn => {
      btn.addEventListener('click', () => {
        pendingAppealId = Number(btn.dataset.appeal);
        pendingAction = 'complete';
        document.getElementById('fulfillHint').textContent = btn.dataset.msg;
        document.getElementById('proofNote').value = '';
        document.getElementById('fulfillModal').classList.remove('hidden');
      });
    });
  }

  async function claimLocal(appealId) {
    const node = await EV.ensureHumanNode();
    if (!node) return EV.alert({ message: '请先在任务大厅注册为人类节点', type: 'warning', title: '提示' });
    try {
      await EV.api('/api/bounties/' + appealId + '/claim', { method: 'POST', body: JSON.stringify({ humanNodeId: node.id }) });
      await loadInsight();
      EV.alert({ message: '任务已接取！完成后点击「提交完成」。', type: 'success', title: '接取成功' });
    } catch (e) {
      EV.alert({ message: '接取失败：' + e.message, type: 'error', title: '错误' });
    }
  }

  function renderEmotionLogs(logs) {
    const list = document.getElementById('emotionLogList');
    if (!logs || logs.length === 0) {
      list.innerHTML = '<div class="appeal-empty">暂无情绪日志</div>';
      return;
    }
    list.innerHTML = logs.map(log => '<div class="emotion-log-item state-' + log.emotionState + '"><div class="log-time">' + EV.formatTime(log.createdAt) + ' · ' + log.stateLabel + ' · 指数 ' + log.emotionIndex + '</div><div>' + log.narrative + '</div></div>').join('');
  }

  async function findEntitiesWithAppeals(entities) {
    const results = [];
    for (const entity of entities) {
      try {
        const insight = await EV.api('/api/entities/' + entity.id + '/insight');
        if (insight.appeals && insight.appeals.length > 0) {
          results.push(entity);
        }
      } catch (e) {
        console.error('[appeals] check appeal for entity ' + entity.id + ':', e);
      }
    }
    return results;
  }

  async function loadInsight() {
    if (!selectedId) return;
    console.log('[appeals] loadInsight called with selectedId:', selectedId);
    try {
      const insight = await EV.api('/api/entities/' + selectedId + '/insight');
      console.log('[appeals] insight API response:', insight);
      console.log('[appeals] appeals count:', insight.appeals ? insight.appeals.length : 'undefined/null');
      renderEmotion(insight.emotion);
      renderAppeals(insight.appeals);
      renderEmotionLogs(insight.logs);
    } catch (e) {
      console.error('[appeals] insight API error:', e);
    }
    try {
      const dashboard = await EV.api('/api/entities/' + selectedId + '/dashboard');
      console.log('[appeals] dashboard API response:', dashboard);
      document.getElementById('entityHero').innerHTML = '<h2 style="color:' + dashboard.avatarColor + '">' + dashboard.name + '</h2><p>' + dashboard.category + ' · ' + dashboard.location + '</p>';
    } catch (e) {
      console.error('[appeals] dashboard API error:', e);
    }
  }

  async function init() {
    console.log('[appeals] init called');
    EV.mountShell('appeals');
    const result = await EV.ensureEntitySelected();
    console.log('[appeals] ensureEntitySelected result:', result);
    selectedId = result.selectedId;
    console.log('[appeals] selectedId set to:', selectedId);
    EV.renderEntitySelect(result.entities, selectedId, 'entitySelect');
    await loadInsight();

    const container = document.getElementById('appealCards');
    if (container && container.querySelector('.appeal-empty')) {
      const entitiesWithAppeals = await findEntitiesWithAppeals(result.entities);
      if (entitiesWithAppeals.length > 0) {
        selectedId = entitiesWithAppeals[0].id;
        EV.setSelectedEntityId(selectedId);
        EV.renderEntitySelect(result.entities, selectedId, 'entitySelect');
        await loadInsight();
      }
    }

    document.addEventListener('entity-changed', async (ev) => {
      selectedId = ev.detail.entityId;
      await loadInsight();
    });

    document.getElementById('btnCancelFulfill').addEventListener('click', () => {
      document.getElementById('fulfillModal').classList.add('hidden');
      pendingAppealId = null;
    });

    document.getElementById('btnConfirmFulfill').addEventListener('click', async () => {
      const node = await EV.ensureHumanNode();
      if (!node) return EV.alert({ message: '请先注册为人类节点', type: 'warning', title: '提示' });
      if (!pendingAppealId) return;
      try {
        await EV.api('/api/blockchain/appeals/' + pendingAppealId + '/fulfill', {
          method: 'POST',
          body: JSON.stringify({ humanNodeId: node.id, proofNote: document.getElementById('proofNote').value.trim() })
        });
        document.getElementById('fulfillModal').classList.add('hidden');
        pendingAppealId = null;
        const updated = await EV.ensureHumanNode();
        if (updated) EV.setHumanNode(updated);
        await loadInsight();
        EV.alert({ message: '任务完成！生态币(ECO) 已打入您的节点钱包。', type: 'success', title: '完成成功' });
      } catch (e) {
        EV.alert({ message: '提交失败：' + e.message, type: 'error', title: '错误' });
      }
    });

    setInterval(loadInsight, 15000);
  }

  init().catch(console.error);
})();