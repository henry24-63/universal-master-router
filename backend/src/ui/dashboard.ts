/**
 * Embedded Glassmorphism SPA Dashboard HTML/CSS/JS
 * Served directly by the Cloudflare Worker at GET / and GET /admin
 */

export function renderDashboardHtml(): string {
  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Universal Master Router — Cloud Control Plane</title>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&family=JetBrains+Mono:wght@400;500;600&display=swap" rel="stylesheet">
  <style>
    :root {
      --bg: #090D16;
      --card-bg: rgba(18, 24, 38, 0.7);
      --card-border: rgba(255, 255, 255, 0.08);
      --primary: #6366F1;
      --primary-hover: #4F46E5;
      --accent-teal: #14B8A6;
      --accent-cyan: #06B6D4;
      --text-main: #F8FAFC;
      --text-muted: #94A3B8;
      --danger: #EF4444;
      --success: #10B981;
      --warning: #F59E0B;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      font-family: 'Plus Jakarta Sans', sans-serif;
      background-color: var(--bg);
      background-image: 
        radial-gradient(at 0% 0%, rgba(99, 102, 241, 0.15) 0px, transparent 50%),
        radial-gradient(at 100% 100%, rgba(20, 184, 166, 0.12) 0px, transparent 50%);
      color: var(--text-main);
      min-height: 100vh;
      display: flex;
      flex-direction: column;
    }
    .glass {
      background: var(--card-bg);
      backdrop-filter: blur(16px);
      -webkit-backdrop-filter: blur(16px);
      border: 1px solid var(--card-border);
      border-radius: 16px;
    }
    /* Layout */
    header {
      padding: 16px 32px;
      display: flex;
      justify-content: space-between;
      align-items: center;
      border-bottom: 1px solid var(--card-border);
    }
    .brand {
      display: flex;
      align-items: center;
      gap: 12px;
      font-weight: 800;
      font-size: 1.15rem;
      letter-spacing: -0.5px;
    }
    .brand-badge {
      background: linear-gradient(135deg, var(--primary), var(--accent-teal));
      color: white;
      font-size: 0.75rem;
      font-weight: 700;
      padding: 4px 8px;
      border-radius: 6px;
      text-transform: uppercase;
    }
    .user-pill {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 6px 16px;
      background: rgba(255, 255, 255, 0.04);
      border: 1px solid var(--card-border);
      border-radius: 9999px;
      font-size: 0.85rem;
    }
    .btn {
      cursor: pointer;
      padding: 8px 16px;
      border-radius: 8px;
      font-weight: 600;
      font-size: 0.85rem;
      transition: all 0.2s;
      border: none;
      display: inline-flex;
      align-items: center;
      gap: 8px;
      text-decoration: none;
    }
    .btn-primary {
      background: var(--primary);
      color: white;
    }
    .btn-primary:hover { background: var(--primary-hover); transform: translateY(-1px); }
    .btn-danger {
      background: rgba(239, 68, 68, 0.15);
      color: var(--danger);
      border: 1px solid rgba(239, 68, 68, 0.25);
    }
    .btn-danger:hover { background: rgba(239, 68, 68, 0.25); }
    .btn-outline {
      background: transparent;
      color: var(--text-main);
      border: 1px solid var(--card-border);
    }
    .btn-outline:hover { background: rgba(255, 255, 255, 0.05); }

    /* Main Container */
    main {
      flex: 1;
      padding: 32px;
      max-width: 1400px;
      width: 100%;
      margin: 0 auto;
    }
    .grid-metrics {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
      gap: 20px;
      margin-bottom: 32px;
    }
    .metric-card {
      padding: 24px;
    }
    .metric-title {
      font-size: 0.8rem;
      text-transform: uppercase;
      letter-spacing: 0.5px;
      color: var(--text-muted);
      margin-bottom: 8px;
    }
    .metric-val {
      font-size: 2rem;
      font-weight: 800;
      letter-spacing: -1px;
    }
    .tabs-nav {
      display: flex;
      gap: 8px;
      margin-bottom: 24px;
      border-bottom: 1px solid var(--card-border);
      padding-bottom: 8px;
    }
    .tab-btn {
      padding: 8px 16px;
      background: transparent;
      color: var(--text-muted);
      border: none;
      cursor: pointer;
      font-weight: 600;
      font-size: 0.9rem;
      border-radius: 6px;
      transition: all 0.2s;
    }
    .tab-btn.active {
      background: rgba(99, 102, 241, 0.15);
      color: var(--primary);
    }

    /* Tables */
    .table-container {
      overflow-x: auto;
    }
    table {
      width: 100%;
      border-collapse: collapse;
      text-align: left;
      font-size: 0.875rem;
    }
    th {
      padding: 14px 16px;
      color: var(--text-muted);
      font-weight: 600;
      border-bottom: 1px solid var(--card-border);
      font-size: 0.75rem;
      text-transform: uppercase;
    }
    td {
      padding: 16px;
      border-bottom: 1px solid rgba(255, 255, 255, 0.03);
    }
    .badge {
      display: inline-block;
      padding: 3px 8px;
      border-radius: 4px;
      font-size: 0.75rem;
      font-weight: 700;
    }
    .badge-success { background: rgba(16, 185, 129, 0.15); color: var(--success); }
    .badge-warning { background: rgba(245, 158, 11, 0.15); color: var(--warning); }
    .badge-danger { background: rgba(239, 68, 68, 0.15); color: var(--danger); }

    /* Modals & Forms */
    .modal-overlay {
      position: fixed;
      inset: 0;
      background: rgba(0, 0, 0, 0.7);
      backdrop-filter: blur(8px);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 100;
      padding: 16px;
    }
    .modal {
      width: 100%;
      max-width: 440px;
      padding: 32px;
    }
    .form-group {
      margin-bottom: 18px;
    }
    label {
      display: block;
      font-size: 0.8rem;
      font-weight: 600;
      color: var(--text-muted);
      margin-bottom: 6px;
    }
    input, select {
      width: 100%;
      padding: 12px 14px;
      background: rgba(0, 0, 0, 0.3);
      border: 1px solid var(--card-border);
      border-radius: 8px;
      color: white;
      font-family: inherit;
      font-size: 0.9rem;
      outline: none;
    }
    input:focus, select:focus {
      border-color: var(--primary);
    }
    .mono { font-family: 'JetBrains Mono', monospace; font-size: 0.8rem; }
    .hidden { display: none !important; }
  </style>
</head>
<body>

  <!-- App Header -->
  <header class="glass">
    <div class="brand">
      <span>Universal Master Router</span>
      <span class="brand-badge">Enterprise Control Plane</span>
    </div>
    <div id="header-auth" class="hidden">
      <div class="user-pill">
        <span id="user-display">admin@universal.internal</span>
        <button class="btn btn-outline" style="padding:4px 10px; font-size:0.75rem;" onclick="logout()">Logout</button>
      </div>
    </div>
  </header>

  <!-- Main View -->
  <main id="main-view">
    <!-- Authenticated Dashboard -->
    <div id="dashboard-view" class="hidden">
      <div class="grid-metrics">
        <div class="glass metric-card">
          <div class="metric-title">Total Read Requests</div>
          <div class="metric-val" id="metric-reads">-</div>
        </div>
        <div class="glass metric-card">
          <div class="metric-title">Total Write Requests</div>
          <div class="metric-val" id="metric-writes">-</div>
        </div>
        <div class="glass metric-card">
          <div class="metric-title">Active Projects</div>
          <div class="metric-val" id="metric-projects">-</div>
        </div>
        <div class="glass metric-card">
          <div class="metric-title">Connected Accounts</div>
          <div class="metric-val" id="metric-accounts">-</div>
        </div>
      </div>

      <div class="tabs-nav">
        <button class="tab-btn active" onclick="switchTab('accounts')">Connected Accounts</button>
        <button class="tab-btn" onclick="switchTab('projects')">Projects & Modes</button>
        <button class="tab-btn" onclick="switchTab('health')">Account Health & Quotas</button>
        <button class="tab-btn" onclick="switchTab('rules')">Routing Rules</button>
        <button class="tab-btn" onclick="switchTab('keys')">Master API Keys</button>
      </div>

      <!-- Tab Content: Accounts -->
      <div id="tab-accounts" class="glass" style="padding: 24px;">
        <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px;">
          <h3>Connected Appwrite Accounts</h3>
          <button class="btn btn-primary" onclick="openAddAccountModal()">+ Connect Account</button>
        </div>
        <div class="table-container">
          <table>
            <thead>
              <tr>
                <th>Account Label</th>
                <th>Endpoint</th>
                <th>Region</th>
                <th>Projects</th>
                <th>Status</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody id="accounts-tbody"></tbody>
          </table>
        </div>
      </div>

      <!-- Tab Content: Projects -->
      <div id="tab-projects" class="glass hidden" style="padding: 24px;">
        <h3 style="margin-bottom:16px;">Project Nodes & Allocation</h3>
        <div class="table-container">
          <table>
            <thead>
              <tr>
                <th>Node Name</th>
                <th>Appwrite Project ID</th>
                <th>Region</th>
                <th>Routing Mode</th>
              </tr>
            </thead>
            <tbody id="projects-tbody"></tbody>
          </table>
        </div>
      </div>

      <!-- Tab Content: Health -->
      <div id="tab-health" class="glass hidden" style="padding: 24px;">
        <h3 style="margin-bottom:16px;">Real-Time Health & Upstream Quotas</h3>
        <div class="table-container">
          <table>
            <thead>
              <tr>
                <th>Account</th>
                <th>Latency</th>
                <th>Quota Used</th>
                <th>Storage</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody id="health-tbody"></tbody>
          </table>
        </div>
      </div>

      <!-- Tab Content: Rules -->
      <div id="tab-rules" class="glass hidden" style="padding: 24px;">
        <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px;">
          <h3>Tenant Routing Strategy</h3>
          <button class="btn btn-primary" onclick="openAddRuleModal()">+ New Rule</button>
        </div>
        <div class="table-container">
          <table>
            <thead>
              <tr>
                <th>Tenant ID</th>
                <th>Strategy</th>
                <th>Target Project</th>
              </tr>
            </thead>
            <tbody id="rules-tbody"></tbody>
          </table>
        </div>
      </div>

      <!-- Tab Content: Keys -->
      <div id="tab-keys" class="glass hidden" style="padding: 24px;">
        <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px;">
          <h3>Master Router API Keys</h3>
          <button class="btn btn-primary" onclick="openCreateKeyModal()">+ Generate API Key</button>
        </div>
        <div class="table-container">
          <table>
            <thead>
              <tr>
                <th>Name</th>
                <th>Prefix</th>
                <th>Role</th>
                <th>Rate Limit</th>
                <th>Created</th>
                <th>Status</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody id="keys-tbody"></tbody>
          </table>
        </div>
      </div>
    </div>
  </main>

  <!-- Error Modal with Retry -->
  <div id="error-modal" class="modal-overlay hidden">
    <div class="glass modal">
      <div style="display:flex; align-items:center; gap:10px; margin-bottom:12px;">
        <span style="background:rgba(239,68,68,0.2); color:var(--danger); padding:6px; border-radius:8px; font-size:1.2rem;">⚠️</span>
        <h2 style="font-size:1.2rem;">Master Router Setup Notice</h2>
      </div>
      <p id="error-modal-message" style="color:var(--text-muted); font-size:0.85rem; margin-bottom:16px; line-height:1.5;">
        Unable to verify administrator setup status.
      </p>
      <div id="error-modal-warning" class="hidden" style="background:rgba(245,158,11,0.1); border:1px solid rgba(245,158,11,0.25); border-radius:8px; padding:12px; margin-bottom:16px; font-size:0.8rem; color:#FDE68A;"></div>
      <button type="button" class="btn btn-primary" style="width:100%; justify-content:center;" onclick="checkAuthStatus()">Retry Connection</button>
    </div>
  </div>

  <!-- Login Modal -->
  <div id="login-modal" class="modal-overlay hidden">
    <div class="glass modal">
      <h2 style="margin-bottom: 8px;">Administrator Login</h2>
      <p style="color:var(--text-muted); font-size:0.85rem; margin-bottom:24px;">Enter your administrator credentials to manage the Master Router control plane.</p>
      <form id="login-form" onsubmit="handleLogin(event)">
        <div class="form-group">
          <label>Email Address</label>
          <input type="email" id="login-email" required placeholder="admin@domain.com">
        </div>
        <div class="form-group">
          <label>Password</label>
          <input type="password" id="login-password" required placeholder="••••••••••••">
        </div>
        <div id="login-error" style="color:var(--danger); font-size:0.85rem; margin-bottom:12px;"></div>
        <button type="submit" id="login-submit-btn" class="btn btn-primary" style="width:100%; justify-content:center;">Sign In</button>
      </form>
    </div>
  </div>

  <!-- First-Time Owner Setup Modal -->
  <div id="setup-modal" class="modal-overlay hidden">
    <div class="glass modal">
      <div style="display:flex; align-items:center; gap:10px; margin-bottom:8px;">
        <span style="background:rgba(99,102,241,0.2); color:var(--primary); padding:6px; border-radius:8px; font-size:1.2rem;">🛡️</span>
        <h2>First-Time Administrator Setup</h2>
      </div>
      <p style="color:var(--text-muted); font-size:0.85rem; margin-bottom:20px; line-height:1.4;">
        No administrator account exists yet. Create your platform owner account to secure and initialize the Universal Master Router.
      </p>
      <form id="setup-form" onsubmit="handleSetup(event)">
        <div class="form-group">
          <label>Full Name</label>
          <input type="text" id="setup-name" required placeholder="Platform Administrator">
        </div>
        <div class="form-group">
          <label>Email Address</label>
          <input type="email" id="setup-email" required placeholder="admin@domain.com">
        </div>
        <div class="form-group">
          <label>Password (minimum 12 characters)</label>
          <input type="password" id="setup-password" required minlength="12" placeholder="••••••••••••">
        </div>
        <div class="form-group">
          <label>Confirm Password</label>
          <input type="password" id="setup-confirm-password" required minlength="12" placeholder="••••••••••••">
        </div>
        <div id="setup-error" style="color:var(--danger); font-size:0.85rem; margin-bottom:12px;"></div>
        <button type="submit" id="setup-submit-btn" class="btn btn-primary" style="width:100%; justify-content:center;">Create Platform Owner</button>
      </form>
    </div>
  </div>

  <!-- Single-Display Key Reveal Modal -->
  <div id="reveal-modal" class="modal-overlay hidden">
    <div class="glass modal">
      <h2 style="margin-bottom: 8px; color:var(--success);">API Key Generated</h2>
      <p style="color:var(--text-muted); font-size:0.85rem; margin-bottom:16px;">This secret is displayed <strong>ONLY ONCE</strong> and stored as a secure SHA-256 hash. Copy and save it immediately.</p>
      <div style="background:rgba(0,0,0,0.5); padding:16px; border-radius:8px; border:1px solid var(--card-border); margin-bottom:20px;">
        <span class="mono" id="reveal-key-text" style="word-break:break-all; color:var(--accent-teal);"></span>
      </div>
      <button class="btn btn-primary" style="width:100%; justify-content:center;" onclick="closeRevealModal()">I Have Saved My Secret</button>
    </div>
  </div>

  <script>
    let currentCsrfToken = '';
    let currentUser = null;

    function hideAllModals() {
      document.getElementById('error-modal').classList.add('hidden');
      document.getElementById('setup-modal').classList.add('hidden');
      document.getElementById('login-modal').classList.add('hidden');
    }

    async function checkAuthStatus() {
      hideAllModals();
      try {
        const setupRes = await fetch('/v1/auth/setup-status');
        if (!setupRes.ok) {
          throw new Error('Server returned HTTP ' + setupRes.status + ' on setup check');
        }
        const setupData = await setupRes.json();
        if (!setupData || !setupData.success) {
          throw new Error(setupData?.error || 'Failed to parse setup status response');
        }

        // If D1 tables need migration
        if (setupData.data.db_initialized === false && setupData.data.warning) {
          document.getElementById('error-modal-message').innerText = 'Cloudflare D1 tables require initialization.';
          const warnBox = document.getElementById('error-modal-warning');
          warnBox.innerText = setupData.data.warning;
          warnBox.classList.remove('hidden');
          document.getElementById('error-modal').classList.remove('hidden');
          return;
        }

        // Conditional Flow: Has Admin vs First-Time Setup
        if (!setupData.data.has_admin) {
          document.getElementById('setup-modal').classList.remove('hidden');
          return;
        }

        // Administrator already exists — check session
        const meRes = await fetch('/v1/auth/me');
        const meData = await meRes.json();
        if (meData.success && meData.data) {
          currentUser = meData.data.user;
          currentCsrfToken = meData.data.csrf_token;
          showDashboard();
        } else {
          document.getElementById('login-modal').classList.remove('hidden');
        }
      } catch (e) {
        document.getElementById('error-modal-message').innerText = 'Unable to connect to Master Router API: ' + (e.message || 'Network error');
        document.getElementById('error-modal-warning').classList.add('hidden');
        document.getElementById('error-modal').classList.remove('hidden');
      }
    }

    async function handleSetup(e) {
      e.preventDefault();
      const errBox = document.getElementById('setup-error');
      errBox.innerText = '';

      const name = document.getElementById('setup-name').value.trim();
      const email = document.getElementById('setup-email').value.trim();
      const password = document.getElementById('setup-password').value;
      const confirmPassword = document.getElementById('setup-confirm-password').value;
      const submitBtn = document.getElementById('setup-submit-btn');

      // Client-side validations
      if (!name) {
        errBox.innerText = 'Please enter your full name.';
        return;
      }
      const emailRegex = /^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$/;
      if (!emailRegex.test(email)) {
        errBox.innerText = 'Please enter a valid email address.';
        return;
      }
      if (password.length < 12) {
        errBox.innerText = 'Password must be at least 12 characters.';
        return;
      }
      if (password !== confirmPassword) {
        errBox.innerText = 'Passwords do not match. Please re-enter.';
        return;
      }

      // Prevent duplicate submissions
      submitBtn.disabled = true;
      submitBtn.innerText = 'Initializing Platform Owner...';

      try {
        const res = await fetch('/v1/auth/setup', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ name, email, password })
        });
        const data = await res.json();
        if (data.success) {
          document.getElementById('setup-modal').classList.add('hidden');
          await checkAuthStatus();
        } else {
          errBox.innerText = data.error || 'Failed to initialize platform owner.';
        }
      } catch (err) {
        errBox.innerText = 'Network error during setup: ' + err.message;
      } finally {
        submitBtn.disabled = false;
        submitBtn.innerText = 'Create Platform Owner';
      }
    }

    async function handleLogin(e) {
      e.preventDefault();
      const errBox = document.getElementById('login-error');
      errBox.innerText = '';
      const email = document.getElementById('login-email').value.trim();
      const password = document.getElementById('login-password').value;
      const submitBtn = document.getElementById('login-submit-btn');

      submitBtn.disabled = true;
      submitBtn.innerText = 'Signing In...';

      try {
        const res = await fetch('/v1/auth/login', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ email, password })
        });
        const data = await res.json();
        if (data.success) {
          document.getElementById('login-modal').classList.add('hidden');
          await checkAuthStatus();
        } else {
          errBox.innerText = data.error || 'Invalid administrator credentials.';
        }
      } catch (err) {
        errBox.innerText = 'Network error during login: ' + err.message;
      } finally {
        submitBtn.disabled = false;
        submitBtn.innerText = 'Sign In';
      }
    }

    async function logout() {
      await fetch('/v1/auth/logout', {
        method: 'POST',
        headers: { 'X-CSRF-Token': currentCsrfToken }
      });
      location.reload();
    }

    function showDashboard() {
      document.getElementById('header-auth').classList.remove('hidden');
      document.getElementById('user-display').innerText = currentUser.email;
      document.getElementById('dashboard-view').classList.remove('hidden');
      loadAllData();
    }

    async function loadAllData() {
      const [usageRes, accountsRes, projectsRes, healthRes, rulesRes, keysRes] = await Promise.all([
        fetch('/v1/usage'),
        fetch('/v1/accounts'),
        fetch('/v1/projects'),
        fetch('/v1/health'),
        fetch('/v1/routing/rules'),
        fetch('/v1/keys')
      ]);

      const usage = await usageRes.json();
      if (usage.data) {
        document.getElementById('metric-reads').innerText = Number(usage.data.total_read_requests).toLocaleString();
        document.getElementById('metric-writes').innerText = Number(usage.data.total_write_requests).toLocaleString();
        document.getElementById('metric-projects').innerText = usage.data.active_projects;
        document.getElementById('metric-accounts').innerText = usage.data.connected_accounts;
      }

      const accounts = await accountsRes.json();
      renderAccounts(accounts.data || []);

      const projects = await projectsRes.json();
      renderProjects(projects.data || []);

      const health = await healthRes.json();
      renderHealth(health.data || []);

      const rules = await rulesRes.json();
      renderRules(rules.data || []);

      const keys = await keysRes.json();
      renderKeys(keys.data || []);
    }

    function renderAccounts(list) {
      const tbody = document.getElementById('accounts-tbody');
      tbody.innerHTML = list.map(a => \`
        <tr>
          <td><strong>\${a.label}</strong></td>
          <td class="mono">\${a.endpoint}</td>
          <td>\${a.region.toUpperCase()}</td>
          <td>\${a.projects_count}</td>
          <td><span class="badge badge-success">\${a.status}</span></td>
          <td>
            <button class="btn btn-outline" style="padding:4px 8px; font-size:0.75rem;" onclick="testAccount('\${a.id}')">Test</button>
            <button class="btn btn-danger" style="padding:4px 8px; font-size:0.75rem;" onclick="deleteAccount('\${a.id}')">Delete</button>
          </td>
        </tr>
      \`).join('');
    }

    function renderProjects(list) {
      const tbody = document.getElementById('projects-tbody');
      tbody.innerHTML = list.map(p => \`
        <tr>
          <td><strong>\${p.name}</strong></td>
          <td class="mono">\${p.appwrite_project_id || p.id}</td>
          <td>\${p.region.toUpperCase()}</td>
          <td><span class="badge badge-warning">\${p.mode}</span></td>
        </tr>
      \`).join('');
    }

    function renderHealth(list) {
      const tbody = document.getElementById('health-tbody');
      tbody.innerHTML = list.map(h => \`
        <tr>
          <td><strong>\${h.label}</strong></td>
          <td>\${h.latency_ms} ms</td>
          <td>\${h.quota_usage_percent}%</td>
          <td>\${h.storage_used_gb} GB</td>
          <td><span class="badge \${h.status === 'HEALTHY' ? 'badge-success' : 'badge-danger'}">\${h.status}</span></td>
        </tr>
      \`).join('');
    }

    function renderRules(list) {
      const tbody = document.getElementById('rules-tbody');
      tbody.innerHTML = list.map(r => \`
        <tr>
          <td><strong>\${r.tenant_id}</strong></td>
          <td><span class="badge badge-warning">\${r.strategy}</span></td>
          <td class="mono">\${r.target_project_id}</td>
        </tr>
      \`).join('');
    }

    function renderKeys(list) {
      const tbody = document.getElementById('keys-tbody');
      tbody.innerHTML = list.map(k => \`
        <tr>
          <td><strong>\${k.name}</strong></td>
          <td class="mono">\${k.prefix}...</td>
          <td>\${k.is_admin ? '<span class="badge badge-success">Admin</span>' : '<span class="badge badge-warning">Tenant</span>'}</td>
          <td>\${k.rate_limit_rpm} rpm</td>
          <td>\${new Date(k.created_at).toLocaleDateString()}</td>
          <td>\${k.revoked_at ? '<span class="badge badge-danger">Revoked</span>' : '<span class="badge badge-success">Active</span>'}</td>
          <td>
            \${k.revoked_at ? '' : \`<button class="btn btn-danger" style="padding:4px 8px; font-size:0.75rem;" onclick="revokeKey('\${k.id}')">Revoke</button>\`}
          </td>
        </tr>
      \`).join('');
    }

    function switchTab(name) {
      ['accounts', 'projects', 'health', 'rules', 'keys'].forEach(t => {
        document.getElementById('tab-' + t).classList.add('hidden');
      });
      document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
      document.getElementById('tab-' + name).classList.remove('hidden');
      event.target.classList.add('active');
    }

    async function openCreateKeyModal() {
      const name = prompt('Enter a label for this API Key:');
      if (!name) return;
      const res = await fetch('/v1/keys', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-CSRF-Token': currentCsrfToken
        },
        body: JSON.stringify({ name, scopes: ['*'], rate_limit_rpm: 600, is_admin: true })
      });
      const data = await res.json();
      if (data.success && data.data) {
        document.getElementById('reveal-key-text').innerText = data.data.api_key;
        document.getElementById('reveal-modal').classList.remove('hidden');
        loadAllData();
      }
    }

    function closeRevealModal() {
      document.getElementById('reveal-modal').classList.add('hidden');
    }

    async function revokeKey(id) {
      if (!confirm('Are you sure you want to revoke this API key? This cannot be undone.')) return;
      await fetch('/v1/keys/' + id + '/revoke', {
        method: 'POST',
        headers: { 'X-CSRF-Token': currentCsrfToken }
      });
      loadAllData();
    }

    async function deleteAccount(id) {
      if (!confirm('Are you sure you want to disconnect this account?')) return;
      await fetch('/v1/accounts/' + id, {
        method: 'DELETE',
        headers: { 'X-CSRF-Token': currentCsrfToken }
      });
      loadAllData();
    }

    async function testAccount(id) {
      const res = await fetch('/v1/accounts/' + id + '/test', {
        method: 'POST',
        headers: { 'X-CSRF-Token': currentCsrfToken }
      });
      const data = await res.json();
      alert(data.success ? 'Connectivity Verified! Latency: ' + data.data.latency_ms + 'ms' : 'Connection failed: ' + data.error);
    }

    // Start
    checkAuthStatus();
  </script>
</body>
</html>`;
}
