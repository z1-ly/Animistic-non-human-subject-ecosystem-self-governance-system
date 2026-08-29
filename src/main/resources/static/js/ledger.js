(function () {
  const EV = window.EcoVoice;

  function shortAddr(addr) {
    if (!addr || addr.length < 12) return addr || '—';
    return addr.slice(0, 10) + '…' + addr.slice(-4);
  }

  async function loadWallets() {
    const identities = await EV.api('/api/blockchain/identities');
    document.getElementById('walletCards').innerHTML = identities.map(id =>
      '<div class="wallet-card">' +
      '<div class="wallet-card-name">' + id.entityName + '</div>' +
      '<div class="wallet-card-balance">' + id.borrowedBalance.toFixed(2) + ' <span>生态币(ECO)</span></div>' +
      '<code class="wallet-card-addr">' + shortAddr(id.walletAddress) + '</code>' +
      '<div class="wallet-card-did">' + id.did + '</div></div>'
    ).join('');
  }

  async function loadChain() {
    const blocks = await EV.api('/api/blockchain/chain?limit=12');
    document.getElementById('blockList').innerHTML = blocks.map(b =>
      '<div class="block-item">' +
      '<div class="block-head">#' + b.blockIndex + ' · ' + EV.formatTime(b.minedAt) + ' · ' + b.miner + '</div>' +
      '<div class="block-hash">hash ' + shortAddr(b.hash) + '</div>' +
      '<div class="block-hash">prev ' + shortAddr(b.previousHash) + '</div>' +
      '<div class="block-tx-count">' + (b.transactions ? b.transactions.length : 0) + ' 笔交易</div></div>'
    ).join('') || '<div class="appeal-empty">暂无区块</div>';
  }

  async function loadTransactions() {
    const txs = await EV.api('/api/blockchain/transactions?limit=25');
    document.getElementById('txList').innerHTML = txs.map(tx =>
      '<div class="tx-item type-' + tx.type + '">' +
      '<div class="tx-head">' + EV.formatTime(tx.createdAt) + ' · ' + tx.typeLabel + '</div>' +
      '<div class="tx-amount">' + (tx.amount > 0 ? tx.amount.toFixed(2) + ' 生态币(ECO)' : '—') + '</div>' +
      '<div class="tx-flow">' + shortAddr(tx.fromAddress) + ' → ' + shortAddr(tx.toAddress) + '</div>' +
      '<div class="tx-payload">' + (tx.payload || '') + '</div>' +
      '<code class="tx-hash">' + shortAddr(tx.txHash) + '</code></div>'
    ).join('') || '<div class="appeal-empty">暂无交易</div>';
  }

  async function loadChainStatus() {
    const v = await EV.api('/api/blockchain/verify');
    const el = document.getElementById('chainStatus');
    el.textContent = v.valid ? '✓ 账本完整性验证通过' : '✗ 账本异常';
    el.className = 'ledger-status ' + (v.valid ? 'valid' : 'invalid');
  }

  async function loadHumanNodes() {
    const nodes = await EV.api('/api/human-nodes');
    const grid = document.getElementById('humanNodeGrid');
    if (!grid) return;
    grid.innerHTML = nodes.length ? nodes.map(n =>
      '<div class="human-node-card">' +
      '<div class="node-status"><span class="status-dot"></span>人类节点 · 在线</div>' +
      '<div class="wallet-card-name">' + n.displayName + '</div>' +
      '<code>' + n.peerNodeId + '</code>' +
      '<div class="wallet-card-meta">完成 ' + n.tasksCompleted + ' · 收益 ' + n.totalEarned.toFixed(2) + ' 生态币(ECO)</div>' +
      '<code class="wallet-card-addr">' + shortAddr(n.walletAddress) + '</code></div>'
    ).join('') : '<div class="appeal-empty">暂无人类节点 · <a href="/tasks.html">前往注册</a></div>';
  }

  async function loadContractStatus() {
    try {
      const contract = await EV.api('/api/eco-loan/contract-status');
      if (!contract) return;
      
      document.getElementById('contractTotalBalance').textContent = contract.totalSupply.toFixed(2) + ' ECO';
      document.getElementById('contractAvailableBalance').textContent = contract.availableBalance.toFixed(2) + ' ECO';
      document.getElementById('contractLentBalance').textContent = contract.lentBalance.toFixed(2) + ' ECO';
      
      const lentRate = contract.totalSupply > 0 ? (contract.lentBalance / contract.totalSupply * 100).toFixed(2) : '0.00';
      document.getElementById('contractLentRate').textContent = lentRate + '%';
      document.getElementById('contractAddress').textContent = shortAddr(contract.contractAddress);
    } catch (e) {
      console.error('加载合约状态失败:', e);
    }
  }

  async function init() {
    EV.mountShell('ledger');
    await Promise.all([loadContractStatus(), loadWallets(), loadHumanNodes(), loadChain(), loadTransactions(), loadChainStatus()]);
    setInterval(() => {
      loadContractStatus();
      loadWallets();
      loadHumanNodes();
      loadChain();
      loadTransactions();
    }, 12000);
  }

  init().catch(console.error);
})();
