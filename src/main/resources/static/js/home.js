(function () {
  const EV = window.EcoVoice;

  async function init() {
    EV.mountShell('home');
    const entities = await EV.loadEntities();
    const earth = entities.find(e => e.name.includes('地球'));
    if (earth) {
      EV.setSelectedEntityId(earth.id);
      document.getElementById('earthName').textContent = earth.name;
      document.getElementById('earthDesc').textContent = earth.description;
    }
  }

  init().catch(console.error);
})();
