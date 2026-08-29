(function () {
  const EV = window.EcoVoice;
  let humanNode = null;
  let tasksData = [];

  function formatDate(iso) {
    if (!iso) return '—';
    return new Date(iso).toLocaleString('zh-CN', { hour12: false });
  }

  function renderTaskCard(task) {
    const statusClass = task.bountyStatus === 'PAID' ? 'paid' :
                        task.bountyStatus === 'SETTLED' ? 'settled' :
                        task.bountyStatus === 'FULFILLED' ? 'submitted' :
                        task.bountyStatus === 'CLAIMED' ? 'in-progress' : 'open';

    return '<div class="enterprise-task-card ' + statusClass + '">' +
      '<div class="task-header">' +
        '<span class="task-entity">' + task.entityName + ' · ' + task.entityCategory + '</span>' +
        '<span class="tag">' + task.bountyStatusLabel + '</span>' +
      '</div>' +
      '<div class="task-type">「' + task.appealLabel + '」</div>' +
      '<div class="task-message">' + task.message + '</div>' +
      (task.baselineData ? '<div class="task-baseline"><strong>基线：</strong>' + task.baselineData + '</div>' : '') +
      (task.actionParameters ? '<div class="task-params"><strong>执行参数：</strong>' + task.actionParameters + '</div>' : '') +
      (task.acceptanceCriteria ? '<div class="task-criteria"><strong>验收标准：</strong>' + task.acceptanceCriteria + '</div>' : '') +
      (task.rewardRules ? '<div class="task-rewards"><strong>奖惩规则：</strong>' + task.rewardRules + '</div>' : '') +
      '<div class="task-bounty-row">' +
        '<span class="bounty-amount">' + (task.bountyAmount ? task.bountyAmount.toFixed(2) + ' ECO' : '—') + '</span>' +
        '<span class="task-progress">' + (task.completionPercentage ? task.completionPercentage + '%' : '0%') + '</span>' +
      '</div>' +
      (task.aiVerificationScore ? '<div class="task-score">AI评分：' + task.aiVerificationScore + '</div>' : '') +
      (task.releasedAmount ? '<div class="task-released">已发放：' + task.releasedAmount.toFixed(2) + ' ECO</div>' : '') +
      '<div class="task-meta">' +
        '<span>接取时间：' + formatDate(task.claimedAt) + '</span>' +
        (task.fulfilledAt ? '<span>提交时间：' + formatDate(task.fulfilledAt) + '</span>' : '') +
        (task.verifiedAt ? '<span>评估时间：' + formatDate(task.verifiedAt) + '</span>' : '') +
      '</div>' +
    '</div>';
  }

  async function loadTasks() {
    if (!humanNode) return;
    const list = document.getElementById('enterpriseTaskList');
    list.innerHTML = '<div class="loading">加载中...</div>';

    try {
      tasksData = await EV.api('/api/enterprise/tasks/' + humanNode.id);
      if (!tasksData.length) {
        list.innerHTML = '<div class="appeal-empty">暂无已接取任务，请前往任务大厅接取任务</div>';
        return;
      }
      list.innerHTML = tasksData.map(renderTaskCard).join('');
      updateEvalSelect();
    } catch (e) {
      list.innerHTML = '<div class="appeal-empty">加载失败：' + e.message + '</div>';
    }
  }

  function updateEvalSelect() {
    const evalSelect = document.getElementById('evalTaskSelect');
    const submittedTasks = tasksData.filter(t => 
      t.bountyStatus === 'FULFILLED' || t.bountyStatus === 'CLAIMED' || 
      t.bountyStatusLabel === '已提交' || t.bountyStatusLabel === '已接取'
    );
    evalSelect.innerHTML = submittedTasks.length
      ? submittedTasks.map(t => '<option value="' + t.appealId + '">' + t.entityName + ' - ' + t.appealLabel + '</option>').join('')
      : '<option value="">暂无待评估任务</option>';

    if (submittedTasks.length > 0) {
      showEvalTaskDetail(submittedTasks[0].appealId);
    } else {
      document.getElementById('evalAction').classList.add('hidden');
    }
  }

  function showEvalTaskDetail(appealId) {
    const task = tasksData.find(t => t.appealId === Number(appealId));
    const detailDiv = document.getElementById('evalTaskDetail');
    const evalAction = document.getElementById('evalAction');

    if (!task) {
      detailDiv.innerHTML = '';
      evalAction.classList.add('hidden');
      return;
    }

    detailDiv.innerHTML = renderTaskCard(task);
    evalAction.classList.remove('hidden');
  }

  async function doEvaluate() {
    const appealId = document.getElementById('evalTaskSelect').value;
    if (!appealId) {
      EV.alert({ message: '请选择待评估任务', type: 'warning', title: '提示' });
      return;
    }

    const confirmed = await EV.confirm({ 
      message: '确定要进行自动化评估吗？系统将基于传感器实时数据自动计算评分和奖励，并自动归还借币给智能合约。', 
      title: '确认评估' 
    });
    if (!confirmed) return;

    try {
      const result = await EV.api('/api/enterprise/tasks/' + appealId + '/evaluate', {
        method: 'POST'
      });

      let msg = '<strong>评估完成</strong>\n\n';
      msg += '基础奖励：<strong>' + result.baseReward + ' ECO</strong>\n';
      msg += '超额奖励：<strong>' + result.bonusReward + ' ECO</strong>\n';
      msg += '扣减惩罚：<strong>' + result.penalty + ' ECO</strong>\n';
      msg += '最终发放：<strong>' + result.finalReward + ' ECO</strong>\n';
      msg += '您的余额：<strong>' + result.contractorBalance + ' ECO</strong>\n\n';
      msg += '<strong>评估详情：</strong>\n';
      msg += '完成率：' + result.completionPercentage + '%\n';
      msg += 'AI评分：' + result.verificationScore + '\n\n';
      
      if (result.autoRepay) {
        msg += '<strong>✦ 自动还款完成：</strong>\n';
        msg += '已归还智能合约：<strong>' + result.repayAmount + ' ECO</strong>\n';
        msg += '智能合约可用余额：<strong>' + result.smartContractAvailableAfter + ' ECO</strong>\n';
      }

      if (result.sensorMetrics && result.sensorMetrics.length) {
        msg += '\n<strong>传感器指标：</strong>\n';
        result.sensorMetrics.forEach(m => {
          msg += '  - ' + m.sensorLabel + ': ' + m.currentValue + m.unit + ' (基线:' + m.baselineValue + m.unit + ')\n';
        });
      }

      EV.alert({ message: msg, type: 'success', title: '评估完成', onClose: refreshAll });
    } catch (e) {
      EV.alert({ message: '评估失败：' + e.message, type: 'error', title: '错误' });
    }
  }

  async function refreshAll() {
    humanNode = await EV.ensureHumanNode();
    if (humanNode) {
      await loadTasks();
    }
  }

  async function init() {
    EV.mountShell('enterprise');
    await refreshAll();

    document.querySelectorAll('.tasks-tabs .stream-tab').forEach(tab => {
      tab.addEventListener('click', () => {
        document.querySelectorAll('.tasks-tabs .stream-tab').forEach(t => t.classList.remove('active'));
        tab.classList.add('active');
        const tabName = tab.dataset.tab;
        document.getElementById('panelTasks').classList.toggle('hidden', tabName !== 'tasks');
        document.getElementById('panelEvaluate').classList.toggle('hidden', tabName !== 'evaluate');
      });
    });

    document.getElementById('evalTaskSelect').addEventListener('change', (e) => {
      showEvalTaskDetail(e.target.value);
    });

    document.getElementById('btnEvaluate').addEventListener('click', doEvaluate);

    setInterval(refreshAll, 30000);
  }

  init().catch(console.error);
})();