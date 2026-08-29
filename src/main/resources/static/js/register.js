(function () {
  const EV = window.EcoVoice;

  function showLoggedIn(node) {
    document.getElementById('loggedInPanel').style.display = 'block';
    document.getElementById('notLoggedInPanel').style.display = 'none';
    document.getElementById('regName').textContent = node.displayName;
    document.getElementById('regPeerId').textContent = node.peerNodeId;
    document.getElementById('regWallet').textContent = node.walletAddress || '—';
    document.getElementById('regBalance').textContent = (node.walletBalance || 0).toFixed(2) + ' 生态币(ECO)';
  }

  function showNotLoggedIn() {
    document.getElementById('loggedInPanel').style.display = 'none';
    document.getElementById('notLoggedInPanel').style.display = 'block';
  }

  async function init() {
    EV.mountShell('register');

    const existing = EV.getHumanNode();
    if (existing) {
      showLoggedIn(existing);
    } else {
      showNotLoggedIn();
    }

    // 防止表单回车自动提交
    const inputName = document.getElementById('registerName');
    if (inputName) {
      inputName.addEventListener('keypress', (e) => {
        if (e.key === 'Enter') {
          e.preventDefault();
          document.getElementById('btnRegister').click();
        }
      });
    }

    document.getElementById('btnRegister').addEventListener('click', async () => {
      const name = document.getElementById('registerName').value.trim();
      if (!name) return EV.alert({ message: '请输入姓名或组织名称', type: 'warning', title: '提示' });

      try {
        const node = await EV.registerHumanNode(name);
        showLoggedIn(node);
      } catch (e) {
        EV.alert({ message: '注册失败：' + e.message, type: 'error', title: '错误' });
      }
    });

    document.getElementById('btnLogout').addEventListener('click', async () => {
      const confirmed = await EV.confirm({ message: '确定要退出当前身份吗？', title: '确认退出' });
      if (confirmed) {
        EV.setHumanNode(null);
        showNotLoggedIn();
      }
    });
  }

  init();
})();