// ============================================================
// Selenium Recorder & Replayer - SPA Application
// ============================================================

const API = '/api';
let currentUser = null;
let currentPage = 'dashboard';
let sidebarVisibility = {};
let inactivityTimer = null;
const INACTIVITY_TIMEOUT = 30 * 60 * 1000; // 30 min

// --- API Helper ---
async function api(path, options = {}) {
    const token = localStorage.getItem('token');
    const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
    if (token) headers['Authorization'] = 'Bearer ' + token;
    const res = await fetch(API + path, { ...options, headers });
    if (res.status === 401) { logout(); throw new Error('Unauthorized'); }
    const text = await res.text();
    try { return { ok: res.ok, status: res.status, data: JSON.parse(text) }; }
    catch { return { ok: res.ok, status: res.status, data: text }; }
}

// --- Toast ---
function toast(msg, type = 'success') {
    const c = document.getElementById('toast-container');
    const el = document.createElement('div');
    el.className = 'toast toast-' + type;
    el.textContent = msg;
    c.appendChild(el);
    setTimeout(() => el.remove(), 4000);
}

// --- Auth ---
function getToken() { return localStorage.getItem('token'); }
function isLoggedIn() { return !!getToken(); }
function logout() {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    currentUser = null;
    render();
}
function getUser() {
    if (currentUser) return currentUser;
    try { currentUser = JSON.parse(localStorage.getItem('user')); } catch {}
    return currentUser;
}
function isAdmin() { return getUser()?.role === 'ADMIN'; }

// --- Inactivity auto-logout ---
function resetInactivity() {
    clearTimeout(inactivityTimer);
    if (isLoggedIn()) {
        inactivityTimer = setTimeout(() => { toast('Session expired due to inactivity', 'error'); logout(); }, INACTIVITY_TIMEOUT);
    }
}
document.addEventListener('mousemove', resetInactivity);
document.addEventListener('keydown', resetInactivity);

// --- Router ---
function navigate(page) { currentPage = page; window.location.hash = '#' + page; render(); }
window.addEventListener('hashchange', () => {
    const hash = window.location.hash.slice(1) || 'dashboard';
    currentPage = hash; render();
});

// --- SVG Icons ---
const icons = {
    dashboard: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/></svg>',
    reports: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><path d="M14 2v6h6"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>',
    projects: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 19a2 2 0 01-2 2H4a2 2 0 01-2-2V5a2 2 0 012-2h5l2 3h9a2 2 0 012 2z"/></svg>',
    recordings: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><circle cx="12" cy="12" r="3"/></svg>',
    recorder: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><circle cx="12" cy="12" r="6" fill="currentColor" opacity="0.3"/></svg>',
    replayer: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="5 3 19 12 5 21 5 3"/></svg>',
    apiTesting: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="16 18 22 12 16 6"/><polyline points="8 6 2 12 8 18"/></svg>',
    users: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 00-3-3.87"/><path d="M16 3.13a4 4 0 010 7.75"/></svg>',
    settings: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 010 2.83 2 2 0 01-2.83 0l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-2 2 2 2 0 01-2-2v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83 0 2 2 0 010-2.83l.06-.06A1.65 1.65 0 004.68 15a1.65 1.65 0 00-1.51-1H3a2 2 0 01-2-2 2 2 0 012-2h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 010-2.83 2 2 0 012.83 0l.06.06A1.65 1.65 0 009 4.68a1.65 1.65 0 001-1.51V3a2 2 0 012-2 2 2 0 012 2v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 012.83 0 2 2 0 010 2.83l-.06.06a1.65 1.65 0 00-.33 1.82V9c.26.604.852.997 1.51 1H21a2 2 0 012 2 2 2 0 01-2 2h-.09a1.65 1.65 0 00-1.51 1z"/></svg>',
};

const pageConfig = {
    dashboard: { title: 'Dashboard', subtitle: 'Overview and statistics', icon: 'dashboard' },
    reports: { title: 'Reports', subtitle: 'Test run history and results', icon: 'reports' },
    projects: { title: 'Projects', subtitle: 'Manage projects and modules', icon: 'projects' },
    recordings: { title: 'Recordings', subtitle: 'Saved test recordings', icon: 'recordings' },
    recorder: { title: 'Recorder', subtitle: 'Record browser interactions', icon: 'recorder' },
    replayer: { title: 'Replayer', subtitle: 'Execute test recordings', icon: 'replayer' },
    'api-testing': { title: 'API Testing', subtitle: 'REST API test builder', icon: 'apiTesting' },
    users: { title: 'Users', subtitle: 'User management', icon: 'users' },
    settings: { title: 'Settings', subtitle: 'Sidebar configuration', icon: 'settings' },
};

// --- Main render ---
function render() {
    const app = document.getElementById('app');
    if (!isLoggedIn()) { renderLogin(app); return; }
    resetInactivity();
    const user = getUser();
    if (!user) { logout(); return; }
    const page = pageConfig[currentPage] || pageConfig.dashboard;
    const navItems = getNavItems();

    app.innerHTML = `
        <div class="sidebar-backdrop" id="sidebar-backdrop"></div>
        <aside class="sidebar" id="sidebar">
            <div class="sidebar-header">
                <div class="logo">SR</div>
                <div><h2>Selenium R&R</h2><div class="version">v2.0.0</div></div>
            </div>
            <nav class="sidebar-nav">
                ${navItems.map(n => `
                    <div class="nav-item ${currentPage === n.id ? 'active' : ''}" onclick="navigate('${n.id}')">
                        ${icons[n.icon] || ''}<span>${n.label}</span>
                    </div>
                `).join('')}
            </nav>
        </aside>
        <header class="topbar">
            <div class="topbar-left">
                <button class="hamburger" onclick="toggleSidebar()">&#9776;</button>
                <div>
                    <div class="topbar-title">${page.title}</div>
                    <div class="topbar-subtitle">${page.subtitle}</div>
                </div>
            </div>
            <div class="topbar-right">
                <div class="user-badge">${user.username} <span class="badge badge-${user.role.toLowerCase()}">${user.role}</span></div>
                <button class="btn-logout" onclick="logout()">Logout</button>
            </div>
        </header>
        <main class="main-content" id="page-content"></main>
    `;

    document.getElementById('sidebar-backdrop').onclick = () => toggleSidebar(false);
    renderPage();
}

function getNavItems() {
    const all = [
        { id: 'dashboard', label: 'Dashboard', icon: 'dashboard' },
        { id: 'reports', label: 'Reports', icon: 'reports' },
        { id: 'projects', label: 'Projects', icon: 'projects' },
        { id: 'recordings', label: 'Recordings', icon: 'recordings' },
        { id: 'recorder', label: 'Recorder', icon: 'recorder' },
        { id: 'replayer', label: 'Replayer', icon: 'replayer' },
        { id: 'api-testing', label: 'API Testing', icon: 'apiTesting' },
    ];
    if (isAdmin()) {
        all.push({ id: 'users', label: 'Users', icon: 'users' });
        all.push({ id: 'settings', label: 'Settings', icon: 'settings' });
    }
    return all;
}

function toggleSidebar(show) {
    const sb = document.getElementById('sidebar');
    const bd = document.getElementById('sidebar-backdrop');
    if (show === undefined) show = !sb.classList.contains('open');
    sb.classList.toggle('open', show);
    bd.classList.toggle('show', show);
}

// --- Page rendering ---
function renderPage() {
    const el = document.getElementById('page-content');
    if (!el) return;
    // Clean up polling intervals when navigating away
    if (currentPage !== 'recorder' && recorderInterval) { clearInterval(recorderInterval); recorderInterval = null; }
    if (currentPage !== 'replayer' && replayInterval) { clearInterval(replayInterval); replayInterval = null; }
    switch (currentPage) {
        case 'dashboard': renderDashboard(el); break;
        case 'reports': renderReports(el); break;
        case 'projects': renderProjects(el); break;
        case 'recordings': renderRecordings(el); break;
        case 'recorder': renderRecorder(el); break;
        case 'replayer': renderReplayer(el); break;
        case 'api-testing': renderApiTesting(el); break;
        case 'users': isAdmin() ? renderUsers(el) : navigate('dashboard'); break;
        case 'settings': isAdmin() ? renderSettings(el) : navigate('dashboard'); break;
        default: renderDashboard(el);
    }
}

// ============================================================
// LOGIN / REGISTER
// ============================================================
function renderLogin(app) {
    let isRegister = false;
    function draw() {
        app.innerHTML = `
            <div class="login-page">
                <div class="login-card">
                    <div class="logo-big">SR</div>
                    <h1>${isRegister ? 'Create Account' : 'Welcome Back'}</h1>
                    <p class="subtitle">${isRegister ? 'Register to get started' : 'Sign in to your account'}</p>
                    <div class="form-group">
                        <label>Username</label>
                        <input class="form-control" id="auth-user" placeholder="Enter username" autocomplete="username">
                    </div>
                    <div class="form-group">
                        <label>Password</label>
                        <input class="form-control" id="auth-pass" type="password" placeholder="Enter password" autocomplete="current-password">
                    </div>
                    <button class="btn btn-primary" id="auth-btn">${isRegister ? 'Register' : 'Login'}</button>
                    <div class="login-toggle">
                        ${isRegister ? 'Already have an account?' : "Don't have an account?"}
                        <a id="auth-toggle">${isRegister ? 'Login' : 'Register'}</a>
                    </div>
                </div>
            </div>
        `;
        document.getElementById('auth-toggle').onclick = () => { isRegister = !isRegister; draw(); };
        document.getElementById('auth-btn').onclick = doAuth;
        document.getElementById('auth-pass').addEventListener('keydown', e => { if (e.key === 'Enter') doAuth(); });
    }
    async function doAuth() {
        const username = document.getElementById('auth-user').value.trim();
        const password = document.getElementById('auth-pass').value;
        if (!username || !password) { toast('Please fill all fields', 'error'); return; }
        const endpoint = isRegister ? '/auth/register' : '/auth/login';
        try {
            const r = await api(endpoint, { method: 'POST', body: JSON.stringify({ username, password }) });
            if (r.ok) {
                localStorage.setItem('token', r.data.token);
                localStorage.setItem('user', JSON.stringify(r.data));
                currentUser = r.data;
                toast(isRegister ? 'Account created!' : 'Welcome back!');
                navigate('dashboard');
            } else {
                toast(r.data.error || 'Authentication failed', 'error');
            }
        } catch (e) { toast('Connection error: ' + e.message, 'error'); }
    }
    draw();
}

// ============================================================
// DASHBOARD
// ============================================================
async function renderDashboard(el) {
    el.innerHTML = '<div style="text-align:center;padding:40px"><div class="loading-spinner"></div> Loading dashboard...</div>';
    try {
        const [dashRes, projRes] = await Promise.all([api('/dashboard'), api('/projects')]);
        const d = dashRes.data;
        const projects = projRes.ok ? projRes.data : [];
        const passRate = d.pass_rate || 0;
        const ringColor = passRate >= 80 ? 'var(--green)' : passRate >= 50 ? 'var(--yellow)' : 'var(--red)';
        const circumference = 2 * Math.PI * 56;
        const offset = circumference - (passRate / 100) * circumference;

        el.innerHTML = `
            <div class="grid-6" style="margin-bottom:20px">
                <div class="stat-card"><div class="stat-value">${d.total_runs || 0}</div><div class="stat-label">Total Runs</div></div>
                <div class="stat-card"><div class="stat-value" style="color:var(--green)">${d.passed || 0}</div><div class="stat-label">Passed</div></div>
                <div class="stat-card"><div class="stat-value" style="color:var(--red)">${d.failed || 0}</div><div class="stat-label">Failed</div></div>
                <div class="stat-card"><div class="stat-value" style="color:var(--yellow)">${d.stopped || 0}</div><div class="stat-label">Stopped</div></div>
                <div class="stat-card"><div class="stat-value" style="color:var(--blue)">${d.running || 0}</div><div class="stat-label">Running</div></div>
                <div class="stat-card"><div class="stat-value">${d.recordings || 0}</div><div class="stat-label">Recordings</div></div>
            </div>

            <div class="grid-3" style="margin-bottom:20px">
                <div class="card">
                    <div class="card-header"><h3>Pass Rate</h3></div>
                    <div class="pass-ring">
                        <svg viewBox="0 0 120 120">
                            <circle class="ring-bg" cx="60" cy="60" r="56"/>
                            <circle class="ring-fg" cx="60" cy="60" r="56"
                                stroke="${ringColor}"
                                stroke-dasharray="${circumference}"
                                stroke-dashoffset="${offset}"/>
                        </svg>
                        <div class="ring-text">
                            <div class="ring-pct" style="color:${ringColor}">${passRate}%</div>
                            <div class="ring-label">Pass Rate</div>
                        </div>
                    </div>
                    <div style="margin-top:12px;text-align:center;font-size:12px;color:var(--text2)">
                        ${d.passed || 0} passed / ${d.failed || 0} failed / ${d.stopped || 0} stopped
                    </div>
                </div>

                <div class="card">
                    <div class="card-header"><h3>Metrics</h3></div>
                    <div style="display:grid;grid-template-columns:1fr 1fr;gap:12px">
                        <div class="stat-card"><div class="stat-value" style="font-size:18px">${d.avg_duration ? (d.avg_duration / 1000).toFixed(1) + 's' : '-'}</div><div class="stat-label">Avg Duration</div></div>
                        <div class="stat-card"><div class="stat-value" style="font-size:18px">${d.recordings || 0}</div><div class="stat-label">Recordings</div></div>
                        <div class="stat-card"><div class="stat-value" style="font-size:18px">${d.running || 0}</div><div class="stat-label">Active Now</div></div>
                        <div class="stat-card"><div class="stat-value" style="font-size:18px">${d.failure_rate || 0}%</div><div class="stat-label">Failure Rate</div></div>
                    </div>
                </div>

                <div class="card">
                    <div class="card-header"><h3>Quick Actions</h3></div>
                    <div class="quick-actions">
                        <div class="quick-action" onclick="navigate('replayer')">Run a Test</div>
                        <div class="quick-action" onclick="navigate('recorder')">New Recording</div>
                        <div class="quick-action" onclick="navigate('reports')">View Reports</div>
                        <div class="quick-action" onclick="navigate('projects')">Manage Projects</div>
                    </div>
                </div>
            </div>

            <div class="grid-2">
                <div class="card">
                    <div class="card-header"><h3>Projects Overview</h3></div>
                    ${(d.projects_overview || []).length === 0 ? '<div class="empty-state"><p>No projects yet</p></div>' :
                        (d.projects_overview || []).map(p => `
                            <div style="display:flex;justify-content:space-between;align-items:center;padding:8px 0;border-bottom:1px solid var(--border)">
                                <span style="font-size:13px;font-weight:500">${esc(p.name)}</span>
                                <span style="font-size:12px;color:var(--text2)">${p.recording_count} recordings</span>
                            </div>
                        `).join('')}
                </div>

                <div class="card">
                    <div class="card-header"><h3>Recent Activity</h3></div>
                    ${(d.recent_activity || []).length === 0 ? '<div class="empty-state"><p>No recent activity</p></div>' :
                        (d.recent_activity || []).map(r => `
                            <div class="timeline-item">
                                <div class="timeline-dot" style="background:${statusColor(r.status)}"></div>
                                <div class="timeline-content">
                                    <div class="name">${esc(r.recording_name || 'Unknown')} <span class="badge badge-${r.status.toLowerCase()}">${r.status}</span></div>
                                    <div class="meta">${timeAgo(r.started_at)} ${r.duration_ms ? '- ' + (r.duration_ms/1000).toFixed(1) + 's' : ''}</div>
                                </div>
                            </div>
                        `).join('')}
                </div>
            </div>
        `;
    } catch (e) { el.innerHTML = '<div class="card">Error loading dashboard: ' + e.message + '</div>'; }
}

// ============================================================
// REPORTS
// ============================================================
let reportsPage = 0;
async function renderReports(el) {
    el.innerHTML = '<div style="text-align:center;padding:40px"><div class="loading-spinner"></div></div>';
    try {
        const r = await api(`/runs?limit=8&offset=${reportsPage * 8}`);
        const runs = r.ok ? r.data : [];
        el.innerHTML = `
            <div class="card">
                <div class="card-header">
                    <h3>Test Run History</h3>
                    <div class="filter-bar" style="margin:0">
                        <select id="rpt-status" class="form-control" style="width:auto" onchange="filterReports()">
                            <option value="">All Status</option>
                            <option value="PASSED">Passed</option><option value="FAILED">Failed</option>
                            <option value="RUNNING">Running</option><option value="STOPPED">Stopped</option>
                        </select>
                    </div>
                </div>
                <div class="table-container">
                    <table>
                        <thead><tr><th>Recording</th><th>Status</th><th>Started</th><th>Duration</th><th>Error</th><th>Actions</th></tr></thead>
                        <tbody>
                            ${runs.length === 0 ? '<tr><td colspan="6" style="text-align:center;color:var(--text2)">No runs found</td></tr>' :
                                runs.map(run => `<tr>
                                    <td style="font-weight:500">${esc(run.recording_name || '-')}</td>
                                    <td><span class="badge badge-${run.status.toLowerCase()}">${run.status}</span></td>
                                    <td style="font-size:12px;color:var(--text2)">${timeAgo(run.started_at)}</td>
                                    <td style="font-size:12px">${run.duration_ms ? (run.duration_ms/1000).toFixed(1) + 's' : '-'}</td>
                                    <td style="font-size:12px;color:var(--red);max-width:200px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">${esc(run.error_message || '')}</td>
                                    <td>
                                        <button class="btn btn-sm btn-secondary" onclick="viewRunDetail(${run.id})">Details</button>
                                        ${run.report_path ? `<button class="btn btn-sm btn-secondary" onclick="window.open('/api/runs/${run.id}/report','_blank')">Report</button>` : ''}
                                    </td>
                                </tr>`).join('')}
                        </tbody>
                    </table>
                </div>
                <div class="pagination">
                    <button class="page-btn" onclick="reportsPage=Math.max(0,reportsPage-1);renderReports(document.getElementById('page-content'))" ${reportsPage===0?'disabled':''}>Prev</button>
                    <span style="padding:6px 12px;font-size:12px;color:var(--text2)">Page ${reportsPage+1}</span>
                    <button class="page-btn" onclick="reportsPage++;renderReports(document.getElementById('page-content'))" ${runs.length<8?'disabled':''}>Next</button>
                </div>
            </div>
            <div id="run-detail"></div>
        `;
    } catch (e) { el.innerHTML = '<div class="card">Error: ' + e.message + '</div>'; }
}

async function viewRunDetail(runId) {
    const det = document.getElementById('run-detail');
    if (!det) return;
    det.innerHTML = '<div class="card"><div class="loading-spinner"></div> Loading run details...</div>';
    const r = await api(`/runs/${runId}`);
    if (!r.ok) { det.innerHTML = '<div class="card">Run not found</div>'; return; }
    const run = r.data;
    const steps = run.steps || [];
    det.innerHTML = `
        <div class="card">
            <div class="card-header">
                <h3>Run #${run.id} - ${esc(run.recording_name || '')} <span class="badge badge-${run.status.toLowerCase()}">${run.status}</span></h3>
                <span style="font-size:12px;color:var(--text2)">${run.duration_ms ? (run.duration_ms/1000).toFixed(1) + 's' : ''}</span>
            </div>
            ${steps.map((s, i) => `
                <div class="step-item step-${s.status.toLowerCase()}">
                    <div class="step-num">${i+1}</div>
                    <div style="flex:1">
                        <div style="font-weight:500">${esc(s.title || s.action || '-')}</div>
                        <div style="font-size:11px;color:var(--text2)">${esc(s.locator || '')} ${s.value ? '= ' + esc(s.value) : ''}</div>
                        ${s.error_message ? `<div style="font-size:11px;color:var(--red);margin-top:2px">${esc(s.error_message)}</div>` : ''}
                    </div>
                    <div style="font-size:11px;color:var(--text2)">${s.duration_ms ? s.duration_ms + 'ms' : ''}</div>
                    ${s.screenshot_path ? `<img src="/api/screenshots/${s.screenshot_path.split('/').pop()}" style="width:60px;height:40px;object-fit:cover;border-radius:4px;cursor:pointer" onclick="openGallery(${JSON.stringify(steps.filter(x=>x.screenshot_path).map(x=>'/api/screenshots/'+x.screenshot_path.split('/').pop()))}, ${steps.filter(x=>x.screenshot_path).indexOf(s)})">` : ''}
                </div>
            `).join('')}
        </div>
    `;
}

// ============================================================
// PROJECTS
// ============================================================
async function renderProjects(el) {
    el.innerHTML = '<div style="text-align:center;padding:40px"><div class="loading-spinner"></div></div>';
    const r = await api('/projects');
    const projects = r.ok ? r.data : [];
    el.innerHTML = `
        <div class="card">
            <div class="card-header">
                <h3>Projects</h3>
                ${isAdmin() ? '<button class="btn btn-primary btn-sm" onclick="showNewProjectModal()">+ New Project</button>' : ''}
            </div>
            ${projects.length === 0 ? '<div class="empty-state"><p>No projects yet</p></div>' : `
                <div class="table-container"><table>
                    <thead><tr><th>Name</th><th>Description</th><th>Recordings</th><th>Modules</th><th>Access</th><th>Actions</th></tr></thead>
                    <tbody>${projects.map(p => `<tr>
                        <td style="font-weight:500">${esc(p.name)}</td>
                        <td style="font-size:12px;color:var(--text2)">${esc(p.description || '-')}</td>
                        <td>${p.recording_count || 0}</td>
                        <td>${(p.modules || []).length}</td>
                        <td>${(p.access_users || []).map(u => `<span class="badge badge-resource" style="margin:1px">${esc(u.username)}</span>`).join(' ') || '-'}</td>
                        <td>
                            <button class="btn btn-sm btn-secondary" onclick="expandProject(${p.id})">Manage</button>
                            ${isAdmin() ? `<button class="btn btn-sm btn-danger" onclick="deleteProject(${p.id})">Delete</button>` : ''}
                        </td>
                    </tr>`).join('')}</tbody>
                </table></div>
            `}
        </div>
        <div id="project-detail"></div>
    `;
}

async function expandProject(id) {
    const det = document.getElementById('project-detail');
    const r = await api(`/projects/${id}/modules`);
    const modules = r.ok ? r.data : [];
    const pr = await api('/projects');
    const project = pr.ok ? pr.data.find(p => p.id === id) : null;
    if (!project) return;

    const usersRes = isAdmin() ? await api('/users') : { data: [] };
    const allUsers = usersRes.data || [];

    det.innerHTML = `
        <div class="card">
            <div class="card-header"><h3>${esc(project.name)} - Modules & Access</h3></div>

            <h4 style="font-size:13px;margin-bottom:8px">Modules</h4>
            ${modules.map(m => `<div style="display:flex;justify-content:space-between;align-items:center;padding:6px 0;border-bottom:1px solid var(--border)">
                <span style="font-size:13px">${esc(m.name)}</span>
                ${isAdmin() ? `<button class="btn btn-sm btn-danger" onclick="deleteModule(${m.id})">Delete</button>` : ''}
            </div>`).join('')}
            ${isAdmin() ? `
                <div style="display:flex;gap:8px;margin-top:12px">
                    <input class="form-control" id="new-mod-name" placeholder="Module name" style="flex:1">
                    <button class="btn btn-primary btn-sm" onclick="createModule(${id})">Add Module</button>
                </div>
            ` : ''}

            ${isAdmin() ? `
                <h4 style="font-size:13px;margin-top:20px;margin-bottom:8px">User Access</h4>
                ${(project.access_users || []).map(u => `<div style="display:flex;justify-content:space-between;align-items:center;padding:6px 0;border-bottom:1px solid var(--border)">
                    <span style="font-size:13px">${esc(u.username)} <span class="badge badge-${u.role.toLowerCase()}">${u.role}</span></span>
                    <button class="btn btn-sm btn-danger" onclick="revokeAccess(${id},${u.id})">Revoke</button>
                </div>`).join('')}
                <div style="display:flex;gap:8px;margin-top:12px">
                    <select class="form-control" id="grant-user" style="flex:1">
                        <option value="">Select user...</option>
                        ${allUsers.filter(u => u.role === 'RESOURCE' && !(project.access_users || []).find(a => a.id === u.id))
                            .map(u => `<option value="${u.id}">${esc(u.username)}</option>`).join('')}
                    </select>
                    <button class="btn btn-primary btn-sm" onclick="grantAccess(${id})">Grant Access</button>
                </div>
            ` : ''}
        </div>
    `;
}

function showNewProjectModal() {
    showModal('New Project', `
        <div class="form-group"><label>Name</label><input class="form-control" id="proj-name"></div>
        <div class="form-group"><label>Description</label><textarea class="form-control" id="proj-desc"></textarea></div>
    `, async () => {
        const name = document.getElementById('proj-name').value.trim();
        if (!name) { toast('Name required', 'error'); return; }
        await api('/projects', { method: 'POST', body: JSON.stringify({ name, description: document.getElementById('proj-desc').value }) });
        closeModal(); toast('Project created!'); renderProjects(document.getElementById('page-content'));
    });
}

async function deleteProject(id) { if (!confirm('Delete this project?')) return; await api(`/projects/${id}`, { method: 'DELETE' }); toast('Deleted'); renderProjects(document.getElementById('page-content')); }
async function createModule(projectId) {
    const name = document.getElementById('new-mod-name').value.trim();
    if (!name) return;
    await api(`/projects/${projectId}/modules`, { method: 'POST', body: JSON.stringify({ name }) });
    toast('Module created'); expandProject(projectId);
}
async function deleteModule(id) { if (!confirm('Delete this module?')) return; await api(`/modules/${id}`, { method: 'DELETE' }); toast('Deleted'); renderProjects(document.getElementById('page-content')); }
async function grantAccess(projectId) {
    const userId = document.getElementById('grant-user').value;
    if (!userId) return;
    await api(`/projects/${projectId}/access`, { method: 'POST', body: JSON.stringify({ user_id: parseInt(userId) }) });
    toast('Access granted'); renderProjects(document.getElementById('page-content'));
}
async function revokeAccess(projectId, userId) {
    await api(`/projects/${projectId}/access/${userId}`, { method: 'DELETE' });
    toast('Access revoked'); renderProjects(document.getElementById('page-content'));
}

// ============================================================
// RECORDINGS
// ============================================================
async function renderRecordings(el) {
    el.innerHTML = '<div style="text-align:center;padding:40px"><div class="loading-spinner"></div></div>';
    const [recRes, projRes] = await Promise.all([api('/recordings'), api('/projects')]);
    const recordings = recRes.ok ? recRes.data : [];
    const projects = projRes.ok ? projRes.data : [];

    window._allProjects = projects;
    window._projectMap = {};
    projects.forEach(p => { window._projectMap[p.id] = p.name; });

    el.innerHTML = `
        <div class="card">
            <div class="card-header"><h3>Recordings Library</h3></div>
            <div class="filter-bar">
                <input class="form-control" placeholder="Search..." id="rec-search" oninput="filterRecList()" style="width:200px">
                <select class="form-control" id="rec-proj-filter" onchange="filterRecList()" style="width:auto">
                    <option value="">All Projects</option>
                    ${projects.map(p => `<option value="${p.id}">${esc(p.name)}</option>`).join('')}
                </select>
                <label style="font-size:12px;color:var(--text2);display:flex;align-items:center;gap:4px">
                    <input type="checkbox" id="rec-show-deleted" onchange="reloadRecordings()"> Show Deleted
                </label>
            </div>
            <div class="table-container" id="rec-table-container">
                ${renderRecTable(recordings)}
            </div>
        </div>
        <div id="recording-detail"></div>
    `;
    window._allRecordings = recordings;
}

function renderRecTable(recs) {
    if (recs.length === 0) return '<div class="empty-state"><p>No recordings found</p></div>';
    const pm = window._projectMap || {};
    return `<table><thead><tr><th>Name</th><th>Project</th><th>Steps</th><th>Size</th><th>Created</th><th>Actions</th></tr></thead>
        <tbody>${recs.map(r => `<tr style="${r.deleted_at ? 'opacity:0.5' : ''}">
            <td style="font-weight:500">${esc(r.name)} ${r.deleted_at ? '<span class="badge badge-stopped">Deleted</span>' : ''}</td>
            <td style="font-size:12px;color:var(--text2)">${r.project_id ? esc(pm[r.project_id] || 'Project #' + r.project_id) : '<span style="color:var(--text3)">Unassigned</span>'}</td>
            <td>${r.step_count}</td>
            <td style="font-size:12px">${formatBytes(r.file_size)}</td>
            <td style="font-size:12px;color:var(--text2)">${timeAgo(r.created_at)}</td>
            <td>
                <button class="btn btn-sm btn-secondary" onclick="viewRecording(${r.id})">View</button>
                ${r.deleted_at ? `<button class="btn btn-sm btn-success" onclick="restoreRecording(${r.id})">Restore</button>`
                    : `<button class="btn btn-sm btn-danger" onclick="deleteRecording(${r.id})">Delete</button>`}
            </td>
        </tr>`).join('')}</tbody></table>`;
}

function filterRecList() {
    const search = (document.getElementById('rec-search')?.value || '').toLowerCase();
    const projId = document.getElementById('rec-proj-filter')?.value;
    let recs = window._allRecordings || [];
    if (search) recs = recs.filter(r => (r.name || '').toLowerCase().includes(search));
    if (projId) recs = recs.filter(r => String(r.project_id) === projId);
    document.getElementById('rec-table-container').innerHTML = renderRecTable(recs);
}

async function reloadRecordings() {
    const del = document.getElementById('rec-show-deleted')?.checked;
    const r = await api(`/recordings?includeDeleted=${del}`);
    window._allRecordings = r.ok ? r.data : [];
    filterRecList();
}

async function viewRecording(id) {
    const det = document.getElementById('recording-detail');
    if (!det) return;
    det.innerHTML = '<div class="card"><div class="loading-spinner"></div></div>';
    const [recRes, stepsRes] = await Promise.all([api(`/recordings/${id}`), api(`/recordings/${id}/steps`)]);
    const rec = recRes.ok ? recRes.data : null;
    const steps = stepsRes.ok ? stepsRes.data : [];
    if (!rec) { det.innerHTML = ''; return; }

    // Store current view state on window for tab switching
    window._recDetailState = { id, rec, steps, activeTab: 'steps' };
    drawRecordingDetail();
}

function drawRecordingDetail() {
    const det = document.getElementById('recording-detail');
    if (!det) return;
    const state = window._recDetailState;
    if (!state) return;
    const { id, rec, steps, activeTab } = state;

    det.innerHTML = `
        <div class="detail-panel" style="margin-top:16px">
            <div class="detail-header">
                <h3 style="font-size:15px">${esc(rec.name)}</h3>
                <div style="display:flex;gap:6px">
                    <button class="btn btn-sm btn-secondary" onclick="showAssignModal(${id})">Assign</button>
                    <button class="btn btn-sm btn-secondary" onclick="showRenameModal(${id},'${esc(rec.name).replace(/'/g, "\\'")}')">Rename</button>
                </div>
            </div>
            <div class="tabs" id="rec-detail-tabs"></div>
            <div class="detail-body" id="rec-tab-content"></div>
        </div>
    `;

    // Attach tab click handlers properly
    const tabsEl = document.getElementById('rec-detail-tabs');
    ['steps','json','gherkin','runs'].forEach(t => {
        const tabEl = document.createElement('div');
        tabEl.className = 'tab' + (activeTab === t ? ' active' : '');
        tabEl.textContent = t.charAt(0).toUpperCase() + t.slice(1);
        tabEl.onclick = () => { window._recDetailState.activeTab = t; drawRecordingDetail(); };
        tabsEl.appendChild(tabEl);
    });

    const tc = document.getElementById('rec-tab-content');
    if (activeTab === 'steps') {
        tc.innerHTML = steps.length === 0 ? '<div class="empty-state"><p>No steps</p></div>' :
            steps.map((s, i) => `<div class="step-item"><div class="step-num" style="background:var(--primary-light);color:var(--primary)">${i+1}</div>
                <div><div style="font-weight:500">${esc(s.raw_gherkin || s.action || '-')}</div>
                <div style="font-size:11px;color:var(--text2)">${esc(s.selector || '')} ${s.value ? '= '+esc(s.value) : ''}</div></div></div>`).join('');
    } else if (activeTab === 'json') {
        tc.innerHTML = `<div class="code-block">${esc(JSON.stringify(steps, null, 2))}</div>`;
    } else if (activeTab === 'gherkin') {
        tc.innerHTML = `
            <textarea class="form-control" id="gherkin-text" style="min-height:200px;font-family:monospace">${esc(rec.gherkin_text || '')}</textarea>
            <div style="margin-top:8px;display:flex;gap:8px">
                <button class="btn btn-primary btn-sm" onclick="saveGherkin(${id})">Save Gherkin</button>
            </div>`;
    } else if (activeTab === 'runs') {
        loadRecordingRuns(id, tc);
    }
}

async function loadRecordingRuns(recordingId, container) {
    const r = await api(`/recordings/${recordingId}/runs?limit=10`);
    const runs = r.ok ? r.data : [];
    container.innerHTML = runs.length === 0 ? '<div class="empty-state"><p>No runs yet</p></div>' :
        runs.map(run => `<div class="timeline-item">
            <div class="timeline-dot" style="background:${statusColor(run.status)}"></div>
            <div class="timeline-content">
                <div class="name"><span class="badge badge-${run.status.toLowerCase()}">${run.status}</span> ${run.duration_ms ? (run.duration_ms/1000).toFixed(1) + 's' : ''}</div>
                <div class="meta">${timeAgo(run.started_at)}</div>
            </div>
            <button class="btn btn-sm btn-secondary" onclick="navigate('reports');setTimeout(()=>viewRunDetail(${run.id}),300)">Details</button>
        </div>`).join('');
}

async function deleteRecording(id) { if (!confirm('Delete?')) return; await api(`/recordings/${id}`, { method: 'DELETE' }); toast('Deleted'); reloadRecordings(); }
async function restoreRecording(id) { await api(`/recordings/${id}/restore`, { method: 'PUT' }); toast('Restored'); reloadRecordings(); }
async function saveGherkin(id) {
    const text = document.getElementById('gherkin-text')?.value || '';
    await api(`/recordings/${id}/gherkin`, { method: 'PUT', body: JSON.stringify({ gherkin_text: text }) });
    toast('Gherkin saved');
}

function showAssignModal(id) {
    api('/projects').then(r => {
        const projects = r.ok ? r.data : [];
        showModal('Assign Recording', `
            <div class="form-group"><label>Project</label><select class="form-control" id="assign-proj">
                <option value="">None</option>${projects.map(p => `<option value="${p.id}">${esc(p.name)}</option>`).join('')}
            </select></div>
        `, async () => {
            const pid = document.getElementById('assign-proj').value;
            await api(`/recordings/${id}/assign`, { method: 'PUT', body: JSON.stringify({ project_id: pid ? parseInt(pid) : null, module_id: null }) });
            closeModal(); toast('Assigned'); reloadRecordings();
        });
    });
}
function showRenameModal(id, name) {
    showModal('Rename Recording', `<div class="form-group"><label>Name</label><input class="form-control" id="rename-val" value="${esc(name)}"></div>`,
        async () => { await api(`/recordings/${id}`, { method: 'PUT', body: JSON.stringify({ name: document.getElementById('rename-val').value }) }); closeModal(); toast('Renamed'); reloadRecordings(); });
}

// ============================================================
// RECORDER
// ============================================================
let recorderInterval = null;
async function renderRecorder(el) {
    // Clean up any previous polling interval
    if (recorderInterval) { clearInterval(recorderInterval); recorderInterval = null; }

    const projRes = await api('/projects');
    const projects = projRes.ok ? projRes.data : [];

    el.innerHTML = `
        <div class="grid-2">
            <div class="card">
                <div class="card-header"><h3>Start Recording</h3></div>
                <div class="form-group"><label>URL</label><input class="form-control" id="rec-url" placeholder="https://example.com"></div>
                <div class="form-group"><label>Recording Name</label><input class="form-control" id="rec-name" placeholder="My Test Recording"></div>
                <div class="form-row">
                    <div class="form-group"><label>Project</label><select class="form-control" id="rec-project">
                        <option value="">None</option>${projects.map(p => `<option value="${p.id}">${esc(p.name)}</option>`).join('')}
                    </select></div>
                    <div class="form-group"><label>Mode</label><select class="form-control" id="rec-mode">
                        <option value="local">Local</option>
                    </select></div>
                </div>
                <div style="display:flex;gap:8px;margin-top:12px">
                    <button class="btn btn-primary" id="rec-start-btn" onclick="startRecording()">Start Recording</button>
                    <button class="btn btn-danger hidden" id="rec-stop-btn" onclick="stopRecording()">Stop & Save</button>
                    <button class="btn btn-secondary hidden" id="rec-abort-btn" onclick="abortRecording()">Abort</button>
                </div>
            </div>
            <div>
                <div class="live-console">
                    <div class="live-console-header">
                        <div class="title"><span class="status-dot idle" id="rec-status-dot"></span> RECORDER CONSOLE</div>
                        <div class="meta" id="rec-console-meta">Idle</div>
                    </div>
                    <div class="live-console-body" id="rec-console-body">
                        <div class="console-empty">Start a recording to see live steps here</div>
                    </div>
                    <div class="console-info-bar">
                        <div class="info-item">Steps: <span class="info-val" id="rec-info-steps">0</span></div>
                        <div class="info-item">URL: <span class="info-val" id="rec-info-url">-</span></div>
                    </div>
                </div>
            </div>
        </div>
    `;

    // Auto-reconnect: check if recording is active and resume polling
    try {
        const statusRes = await api('/recorder/status');
        if (statusRes.ok && statusRes.data.active) {
            document.getElementById('rec-start-btn').classList.add('hidden');
            document.getElementById('rec-stop-btn').classList.remove('hidden');
            document.getElementById('rec-abort-btn').classList.remove('hidden');
            resumeRecorderConsole();
        }
    } catch (e) { /* ignore */ }
}

function resumeRecorderConsole() {
    _recPrevStepCount = 0;
    const dot = document.getElementById('rec-status-dot');
    const meta = document.getElementById('rec-console-meta');
    const body = document.getElementById('rec-console-body');
    const infoSteps = document.getElementById('rec-info-steps');
    const infoUrl = document.getElementById('rec-info-url');

    if (dot) dot.className = 'status-dot active';
    if (meta) meta.textContent = 'Recording...';
    if (body) body.innerHTML = '';

    recorderInterval = setInterval(async () => {
        const sr = await api('/recorder/steps');
        if (!sr.ok) return;
        const steps = sr.data || [];
        const count = steps.length;
        if (infoSteps) infoSteps.textContent = count;

        const statusRes = await api('/recorder/status');
        if (statusRes.ok) {
            const curUrl = statusRes.data.url || '';
            if (infoUrl) infoUrl.textContent = curUrl.length > 50 ? curUrl.substring(0, 50) + '...' : (curUrl || '-');
            if (!statusRes.data.active) {
                if (dot) dot.className = 'status-dot idle';
                if (meta) meta.textContent = 'Stopped';
                clearInterval(recorderInterval);
                recorderInterval = null;
            }
        }

        if (count > _recPrevStepCount) {
            for (let i = _recPrevStepCount; i < count; i++) {
                const step = steps[i];
                const action = step.action || step.type || 'event';
                const gherkin = step.raw_gherkin || '';
                const selector = step.selector || '';
                const title = step.title || '';
                const text = gherkin || title || selector || action;

                const entry = document.createElement('div');
                entry.className = 'console-entry';
                entry.innerHTML = `
                    <span class="entry-idx">${i + 1}</span>
                    <span class="entry-badge rec">REC</span>
                    <span class="entry-text">
                        <span class="action-tag">${esc(action)}</span> ${esc(text)}
                        ${selector && gherkin ? `<span class="selector-tag">${esc(selector)}</span>` : ''}
                    </span>
                `;
                if (body) body.appendChild(entry);
            }
            _recPrevStepCount = count;
            if (body) body.scrollTop = body.scrollHeight;
        }
    }, 2000);
}

let _recPrevStepCount = 0;
async function startRecording() {
    const url = document.getElementById('rec-url').value.trim();
    if (!url) { toast('URL required', 'error'); return; }
    document.getElementById('rec-start-btn').classList.add('hidden');
    document.getElementById('rec-stop-btn').classList.remove('hidden');
    document.getElementById('rec-abort-btn').classList.remove('hidden');

    const r = await api('/recorder/start', { method: 'POST', body: JSON.stringify({ url }) });
    if (!r.ok) {
        toast(r.data.error || 'Failed to start', 'error');
        document.getElementById('rec-start-btn').classList.remove('hidden');
        document.getElementById('rec-stop-btn').classList.add('hidden');
        document.getElementById('rec-abort-btn').classList.add('hidden');
        return;
    }
    toast('Recording started!');
    resumeRecorderConsole();
}

async function stopRecording() {
    clearInterval(recorderInterval);
    const name = document.getElementById('rec-name').value.trim() || 'Recording';
    const projectId = document.getElementById('rec-project').value || null;

    const r = await api('/recorder/stop', { method: 'POST', body: JSON.stringify({
        name, filename: name, project_id: projectId ? parseInt(projectId) : null
    }) });
    if (r.ok) { toast('Recording saved!'); }
    document.getElementById('rec-start-btn').classList.remove('hidden');
    document.getElementById('rec-stop-btn').classList.add('hidden');
    document.getElementById('rec-abort-btn').classList.add('hidden');

    const dot = document.getElementById('rec-status-dot');
    const meta = document.getElementById('rec-console-meta');
    if (dot) dot.className = 'status-dot idle';
    if (meta) meta.textContent = 'Saved - ' + _recPrevStepCount + ' steps';
}

async function abortRecording() {
    clearInterval(recorderInterval);
    await api('/recorder/abort', { method: 'POST' });
    toast('Recording aborted');
    document.getElementById('rec-start-btn').classList.remove('hidden');
    document.getElementById('rec-stop-btn').classList.add('hidden');
    document.getElementById('rec-abort-btn').classList.add('hidden');

    const dot = document.getElementById('rec-status-dot');
    const meta = document.getElementById('rec-console-meta');
    if (dot) dot.className = 'status-dot error';
    if (meta) meta.textContent = 'Aborted';
}

// ============================================================
// REPLAYER
// ============================================================
let replayInterval = null;
async function renderReplayer(el) {
    // Clean up any previous polling interval
    if (replayInterval) { clearInterval(replayInterval); replayInterval = null; }

    const [recRes, projRes] = await Promise.all([api('/recordings'), api('/projects')]);
    const recordings = recRes.ok ? recRes.data : [];
    const projects = projRes.ok ? projRes.data : [];

    el.innerHTML = `
        <div class="split-layout">
            <div>
                <div class="card">
                    <div class="card-header"><h3>Run Test</h3></div>
                    <div class="form-row">
                        <div class="form-group"><label>Project Filter</label><select class="form-control" id="rpl-proj" onchange="filterReplayRecs()">
                            <option value="">All Projects</option>${projects.map(p => `<option value="${p.id}">${esc(p.name)}</option>`).join('')}
                        </select></div>
                        <div class="form-group"><label>Mode</label><select class="form-control" id="rpl-mode">
                            <option value="LOCAL">Local</option>
                        </select></div>
                    </div>
                    <div class="form-group"><label>Recording</label><select class="form-control" id="rpl-recording">
                        <option value="">Select recording...</option>${recordings.map(r => `<option value="${r.id}" data-project="${r.project_id}">${esc(r.name)} (${r.step_count} steps)</option>`).join('')}
                    </select></div>
                    <div style="display:flex;gap:8px">
                        <button class="btn btn-primary" id="rpl-run-btn" onclick="startReplay()">Run</button>
                        <button class="btn btn-danger hidden" id="rpl-stop-btn" onclick="stopReplay()">Stop</button>
                    </div>
                </div>
                <div class="live-console">
                    <div class="live-console-header">
                        <div class="title"><span class="status-dot idle" id="rpl-status-dot"></span> EXECUTION CONSOLE</div>
                        <div class="meta" id="rpl-console-meta">Idle</div>
                    </div>
                    <div class="live-console-body" id="rpl-console-body">
                        <div class="console-empty">Select a recording and click Run to see execution progress</div>
                    </div>
                    <div class="console-info-bar">
                        <div class="info-item">Progress: <span class="info-val" id="rpl-info-progress">0/0</span></div>
                        <div class="info-item">Passed: <span class="info-val" id="rpl-info-passed" style="color:#4ade80">0</span></div>
                        <div class="info-item">Failed: <span class="info-val" id="rpl-info-failed" style="color:#f87171">0</span></div>
                    </div>
                </div>
            </div>
            <div>
                <div class="card">
                    <div class="card-header"><h3>Run History</h3></div>
                    <div id="rpl-history"></div>
                </div>
            </div>
        </div>
    `;

    // Load run history
    loadReplayHistory();

    // Auto-reconnect: check if replay is running and resume polling
    try {
        const statusRes = await api('/replay/status');
        if (statusRes.ok && statusRes.data.running) {
            document.getElementById('rpl-run-btn').classList.add('hidden');
            document.getElementById('rpl-stop-btn').classList.remove('hidden');
            resumeReplayConsole();
        }
    } catch (e) { /* ignore */ }
}

async function loadReplayHistory() {
    const histEl = document.getElementById('rpl-history');
    if (!histEl) return;
    try {
        const res = await api('/runs?limit=10');
        if (!res.ok || !res.data.length) {
            histEl.innerHTML = '<div class="empty-state"><p>No runs yet</p></div>';
            return;
        }
        histEl.innerHTML = res.data.map(r => {
            const statusCls = (r.status || '').toLowerCase();
            const dur = r.duration_ms ? (r.duration_ms / 1000).toFixed(1) + 's' : '-';
            return `<div style="display:flex;justify-content:space-between;align-items:center;padding:8px 0;border-bottom:1px solid var(--border);font-size:12px">
                <div>
                    <span class="badge badge-${statusCls}">${esc(r.status || '-')}</span>
                    <span style="margin-left:6px;color:var(--text1)">${esc(r.recording_name || 'Run #' + r.id)}</span>
                </div>
                <div style="color:var(--text2)">${dur} &middot; ${r.created_at ? timeAgo(r.created_at) : '-'}</div>
            </div>`;
        }).join('');
    } catch (e) {
        histEl.innerHTML = '<div class="empty-state"><p>Failed to load history</p></div>';
    }
}

function filterReplayRecs() {
    const projId = document.getElementById('rpl-proj')?.value;
    const sel = document.getElementById('rpl-recording');
    Array.from(sel.options).forEach(opt => {
        if (!opt.value) return;
        opt.hidden = projId && opt.dataset.project !== projId;
    });
}

let _rplRenderedIndex = -1;
let _rplStepEntries = [];
let _rplPassCount = 0;
let _rplFailCount = 0;

function resumeReplayConsole() {
    _rplRenderedIndex = -1;
    _rplStepEntries = [];
    _rplPassCount = 0;
    _rplFailCount = 0;

    const dot = document.getElementById('rpl-status-dot');
    const meta = document.getElementById('rpl-console-meta');
    const body = document.getElementById('rpl-console-body');
    const infoProgress = document.getElementById('rpl-info-progress');
    const infoPassed = document.getElementById('rpl-info-passed');
    const infoFailed = document.getElementById('rpl-info-failed');

    if (dot) dot.className = 'status-dot active';
    if (meta) meta.textContent = 'Running...';
    if (body) body.innerHTML = '';

    replayInterval = setInterval(async () => {
        const sr = await api('/replay/status');
        if (!sr.ok) return;
        const s = sr.data;

        if (infoProgress) infoProgress.textContent = s.currentIndex + '/' + s.totalSteps;

        const idx = s.currentIndex;
        if (idx > 0 && idx > _rplRenderedIndex) {
            // Finalize previous step
            if (_rplRenderedIndex > 0 && body) {
                const prevEntry = body.querySelector(`[data-step-idx="${_rplRenderedIndex}"]`);
                if (prevEntry) {
                    const badge = prevEntry.querySelector('.entry-badge');
                    if (badge && badge.classList.contains('run')) {
                        if (s.lastStepSuccess !== false || idx > _rplRenderedIndex + 1) {
                            badge.className = 'entry-badge pass';
                            badge.textContent = 'PASS';
                            _rplPassCount++;
                        } else {
                            badge.className = 'entry-badge fail';
                            badge.textContent = 'FAIL';
                            _rplFailCount++;
                            if (s.lastError) {
                                const errSpan = document.createElement('span');
                                errSpan.className = 'error-msg';
                                errSpan.textContent = s.lastError;
                                prevEntry.querySelector('.entry-text').appendChild(errSpan);
                            }
                        }
                    }
                }
            }

            const gherkin = s.currentGherkin || '';
            const action = s.currentAction || '';
            const title = s.currentTitle || '';
            const text = gherkin || title || action || 'Step ' + idx;

            const entry = document.createElement('div');
            entry.className = 'console-entry';
            entry.setAttribute('data-step-idx', idx);
            entry.innerHTML = `
                <span class="entry-idx">${idx}</span>
                <span class="entry-badge run">RUN</span>
                <span class="entry-text">
                    <span class="action-tag">${esc(action)}</span> ${esc(text)}
                </span>
            `;
            if (body) { body.appendChild(entry); body.scrollTop = body.scrollHeight; }
            _rplRenderedIndex = idx;

            if (infoPassed) infoPassed.textContent = _rplPassCount;
            if (infoFailed) infoFailed.textContent = _rplFailCount;
        }

        if (!s.running) {
            clearInterval(replayInterval);
            replayInterval = null;
            const runBtn = document.getElementById('rpl-run-btn');
            const stopBtn = document.getElementById('rpl-stop-btn');
            if (runBtn) runBtn.classList.remove('hidden');
            if (stopBtn) stopBtn.classList.add('hidden');

            // Final step result
            if (body) {
                const lastEntry = body.querySelector(`[data-step-idx="${_rplRenderedIndex}"]`);
                if (lastEntry) {
                    const badge = lastEntry.querySelector('.entry-badge');
                    if (badge && badge.classList.contains('run')) {
                        if (s.lastStepSuccess) {
                            badge.className = 'entry-badge pass';
                            badge.textContent = 'PASS';
                            _rplPassCount++;
                        } else {
                            badge.className = 'entry-badge fail';
                            badge.textContent = 'FAIL';
                            _rplFailCount++;
                            if (s.lastError) {
                                const errSpan = document.createElement('span');
                                errSpan.className = 'error-msg';
                                errSpan.textContent = s.lastError;
                                lastEntry.querySelector('.entry-text').appendChild(errSpan);
                            }
                        }
                    }
                }
            }

            // Fetch final run steps for complete results
            if (s.run_id && s.run_id > 0) {
                const runRes = await api(`/runs/${s.run_id}`);
                if (runRes.ok && body) {
                    const steps = runRes.data.steps || [];
                    _rplPassCount = 0;
                    _rplFailCount = 0;
                    body.innerHTML = '';
                    steps.forEach((st, i) => {
                        const status = (st.status || 'UNKNOWN').toUpperCase();
                        const badgeCls = status === 'PASSED' ? 'pass' : status === 'FAILED' ? 'fail' : status === 'SKIPPED' ? 'skip' : 'run';
                        if (status === 'PASSED') _rplPassCount++;
                        if (status === 'FAILED') _rplFailCount++;
                        const durationStr = st.duration_ms ? (st.duration_ms / 1000).toFixed(1) + 's' : '';
                        const entry = document.createElement('div');
                        entry.className = 'console-entry';
                        entry.innerHTML = `
                            <span class="entry-idx">${i + 1}</span>
                            <span class="entry-badge ${badgeCls}">${status}</span>
                            <span class="entry-text">
                                <span class="action-tag">${esc(st.action || '')}</span> ${esc(st.title || st.action || '-')}
                                ${st.error_message ? `<span class="error-msg">${esc(st.error_message)}</span>` : ''}
                            </span>
                            ${durationStr ? `<span class="entry-time">${durationStr}</span>` : ''}
                        `;
                        body.appendChild(entry);
                    });
                    body.scrollTop = body.scrollHeight;
                }
            }

            if (infoPassed) infoPassed.textContent = _rplPassCount;
            if (infoFailed) infoFailed.textContent = _rplFailCount;
            if (infoProgress) infoProgress.textContent = s.totalSteps + '/' + s.totalSteps;

            const allPassed = _rplFailCount === 0;
            if (dot) dot.className = allPassed ? 'status-dot idle' : 'status-dot error';
            if (meta) meta.textContent = allPassed ? 'Completed - All Passed' : 'Completed - ' + _rplFailCount + ' Failed';
            toast(allPassed ? 'Replay completed!' : 'Replay finished with errors', allPassed ? 'success' : 'error');

            // Refresh run history
            loadReplayHistory();
        }
    }, 1500);
}

async function startReplay() {
    const recordingId = document.getElementById('rpl-recording').value;
    if (!recordingId) { toast('Select a recording', 'error'); return; }
    const mode = document.getElementById('rpl-mode').value;

    document.getElementById('rpl-run-btn').classList.add('hidden');
    document.getElementById('rpl-stop-btn').classList.remove('hidden');

    const r = await api('/replay', { method: 'POST', body: JSON.stringify({ recording_id: parseInt(recordingId), mode }) });
    if (!r.ok) {
        toast(r.data.error || 'Failed to start', 'error');
        document.getElementById('rpl-run-btn').classList.remove('hidden');
        document.getElementById('rpl-stop-btn').classList.add('hidden');
        return;
    }
    toast('Replay started!');
    resumeReplayConsole();
}

async function stopReplay() {
    clearInterval(replayInterval);
    replayInterval = null;
    await api('/replay/stop', { method: 'POST' });
    toast('Replay stopped');
    document.getElementById('rpl-run-btn').classList.remove('hidden');
    document.getElementById('rpl-stop-btn').classList.add('hidden');

    const dot = document.getElementById('rpl-status-dot');
    const meta = document.getElementById('rpl-console-meta');
    if (dot) dot.className = 'status-dot error';
    if (meta) meta.textContent = 'Stopped by user';
}

// ============================================================
// API TESTING
// ============================================================
async function renderApiTesting(el) {
    const [testsRes, projRes] = await Promise.all([api('/api-tests'), api('/projects')]);
    const tests = testsRes.ok ? testsRes.data : [];
    const projects = projRes.ok ? projRes.data : [];

    el.innerHTML = `
        <div class="split-layout">
            <div>
                <div class="card">
                    <div class="card-header">
                        <h3>API Tests</h3>
                        <button class="btn btn-primary btn-sm" onclick="showNewApiTestModal()">+ New Test</button>
                    </div>
                    ${tests.length === 0 ? '<div class="empty-state"><p>No API tests yet</p></div>' :
                        tests.map(t => `<div style="display:flex;justify-content:space-between;align-items:center;padding:10px 0;border-bottom:1px solid var(--border);cursor:pointer" onclick="editApiTest(${t.id})">
                            <div>
                                <div style="font-weight:500;font-size:13px">${esc(t.name)}</div>
                                <div style="font-size:11px;color:var(--text2)">${esc(t.description || '')}</div>
                            </div>
                            <div style="display:flex;gap:4px">
                                <button class="btn btn-sm btn-primary" onclick="event.stopPropagation();runApiTest(${t.id})">Run</button>
                                <button class="btn btn-sm btn-danger" onclick="event.stopPropagation();deleteApiTest(${t.id})">Del</button>
                            </div>
                        </div>`).join('')}
                </div>
            </div>
            <div>
                <div class="card" id="api-test-detail">
                    <div class="empty-state"><p>Select a test to view details</p></div>
                </div>
                <div class="card" id="api-test-result" style="display:none"></div>
            </div>
        </div>
    `;
}

function showNewApiTestModal() {
    showModal('New API Test', `
        <div class="form-group"><label>Name</label><input class="form-control" id="apitest-name"></div>
        <div class="form-group"><label>Base URL</label><input class="form-control" id="apitest-baseurl" placeholder="https://api.example.com"></div>
        <div class="form-group"><label>Description</label><textarea class="form-control" id="apitest-desc"></textarea></div>
    `, async () => {
        const name = document.getElementById('apitest-name').value.trim();
        if (!name) { toast('Name required', 'error'); return; }
        await api('/api-tests', { method: 'POST', body: JSON.stringify({
            name, description: document.getElementById('apitest-desc').value,
            base_url: document.getElementById('apitest-baseurl').value,
            test_data: { requests: [{ method: 'GET', url: '', headers: [], assertions: [] }] }
        }) });
        closeModal(); toast('Test created!'); renderApiTesting(document.getElementById('page-content'));
    });
}

async function editApiTest(id) {
    const r = await api(`/api-tests/${id}`);
    if (!r.ok) return;
    const test = r.data;
    let testData;
    try { testData = typeof test.test_data === 'string' ? JSON.parse(test.test_data) : test.test_data; } catch { testData = {}; }

    document.getElementById('api-test-detail').innerHTML = `
        <div class="card-header"><h3>${esc(test.name)}</h3></div>
        <div class="form-group"><label>Test Configuration (JSON)</label>
            <textarea class="form-control" id="apitest-data" style="min-height:300px;font-family:monospace;font-size:12px">${esc(JSON.stringify(testData, null, 2))}</textarea>
        </div>
        <button class="btn btn-primary" onclick="saveApiTest(${id})">Save</button>
        <button class="btn btn-success" onclick="runApiTest(${id})" style="margin-left:8px">Run Test</button>
    `;
}

async function saveApiTest(id) {
    const data = document.getElementById('apitest-data').value;
    try { JSON.parse(data); } catch { toast('Invalid JSON', 'error'); return; }
    const r = await api(`/api-tests/${id}`);
    if (!r.ok) return;
    const test = r.data;
    await api(`/api-tests/${id}`, { method: 'PUT', body: JSON.stringify({
        name: test.name, description: test.description, base_url: test.base_url, test_data: JSON.parse(data)
    }) });
    toast('Test saved!');
}

async function runApiTest(id) {
    const resultEl = document.getElementById('api-test-result');
    if (resultEl) { resultEl.style.display = 'block'; resultEl.innerHTML = '<div class="loading-spinner"></div> Running...'; }
    const r = await api(`/api-tests/${id}/run`, { method: 'POST' });
    if (!r.ok) { toast('Run failed', 'error'); return; }
    const result = r.data;
    if (resultEl) {
        resultEl.innerHTML = `
            <div class="card-header"><h3>Result <span class="badge badge-${result.status.toLowerCase()}">${result.status}</span></h3>
                <span style="font-size:12px;color:var(--text2)">${result.duration_ms}ms</span></div>
            ${(result.requests || []).map((req, i) => `
                <div style="padding:8px 0;border-bottom:1px solid var(--border)">
                    <div style="font-weight:500;font-size:13px">Request ${i+1} <span class="badge badge-${req.status?.toLowerCase() || 'failed'}">${req.status || 'ERROR'}</span></div>
                    <div style="font-size:12px;color:var(--text2)">Status: ${req.status_code || 'N/A'} - ${req.duration_ms || 0}ms</div>
                    ${req.error ? `<div style="font-size:12px;color:var(--red)">${esc(req.error)}</div>` : ''}
                    ${(req.assertions || []).map(a => `<div style="font-size:11px;margin-top:2px">
                        <span class="badge badge-${a.status.toLowerCase()}" style="font-size:10px">${a.status}</span>
                        ${esc(a.target)} ${esc(a.operator)} "${esc(a.expected)}" (got: "${esc(a.actual || '')}")
                    </div>`).join('')}
                </div>
            `).join('')}
        `;
    }
}

async function deleteApiTest(id) { if (!confirm('Delete?')) return; await api(`/api-tests/${id}`, { method: 'DELETE' }); toast('Deleted'); renderApiTesting(document.getElementById('page-content')); }

// ============================================================
// USERS
// ============================================================
async function renderUsers(el) {
    const r = await api('/users?includeDeleted=true');
    const users = r.ok ? r.data : [];
    el.innerHTML = `
        <div class="card">
            <div class="card-header">
                <h3>User Management</h3>
                <button class="btn btn-primary btn-sm" onclick="showNewUserModal()">+ New User</button>
            </div>
            <div class="table-container"><table>
                <thead><tr><th>Username</th><th>Role</th><th>Status</th><th>Created</th><th>Actions</th></tr></thead>
                <tbody>${users.map(u => `<tr style="${u.deleted_at ? 'opacity:0.5' : ''}">
                    <td style="font-weight:500">${esc(u.username)}</td>
                    <td><span class="badge badge-${u.role.toLowerCase()}">${u.role}</span></td>
                    <td><span class="badge badge-${u.status.toLowerCase()}">${u.status}</span></td>
                    <td style="font-size:12px;color:var(--text2)">${timeAgo(u.created_at)}</td>
                    <td style="display:flex;gap:4px;flex-wrap:wrap">
                        ${u.deleted_at ? `<button class="btn btn-sm btn-success" onclick="restoreUser(${u.id})">Restore</button>` : `
                            <button class="btn btn-sm btn-secondary" onclick="toggleUserRole(${u.id},'${u.role}')">${u.role==='ADMIN'?'Demote':'Promote'}</button>
                            <button class="btn btn-sm btn-secondary" onclick="toggleUserStatus(${u.id},'${u.status}')">${u.status==='ACTIVE'?'Deactivate':'Activate'}</button>
                            <button class="btn btn-sm btn-secondary" onclick="showChangePasswordModal(${u.id})">Password</button>
                            <button class="btn btn-sm btn-danger" onclick="deleteUser(${u.id})">Delete</button>
                        `}
                    </td>
                </tr>`).join('')}</tbody>
            </table></div>
        </div>
    `;
}

function showNewUserModal() {
    showModal('New User', `
        <div class="form-group"><label>Username</label><input class="form-control" id="new-user-name"></div>
        <div class="form-group"><label>Password</label><input class="form-control" id="new-user-pass" type="password"></div>
        <div class="form-group"><label>Role</label><select class="form-control" id="new-user-role"><option>RESOURCE</option><option>ADMIN</option></select></div>
    `, async () => {
        const r = await api('/users', { method: 'POST', body: JSON.stringify({
            username: document.getElementById('new-user-name').value,
            password: document.getElementById('new-user-pass').value,
            role: document.getElementById('new-user-role').value
        }) });
        if (r.ok) { closeModal(); toast('User created!'); renderUsers(document.getElementById('page-content')); }
        else toast(r.data.error || 'Error', 'error');
    });
}

function showChangePasswordModal(id) {
    showModal('Change Password', `<div class="form-group"><label>New Password</label><input class="form-control" id="chg-pass" type="password"></div>`,
        async () => {
            await api(`/users/${id}/password`, { method: 'PUT', body: JSON.stringify({ new_password: document.getElementById('chg-pass').value }) });
            closeModal(); toast('Password changed!');
        });
}

async function toggleUserRole(id, current) { await api(`/users/${id}/role`, { method: 'PUT', body: JSON.stringify({ role: current === 'ADMIN' ? 'RESOURCE' : 'ADMIN' }) }); toast('Role updated'); renderUsers(document.getElementById('page-content')); }
async function toggleUserStatus(id, current) { await api(`/users/${id}/status`, { method: 'PUT', body: JSON.stringify({ status: current === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE' }) }); toast('Status updated'); renderUsers(document.getElementById('page-content')); }
async function deleteUser(id) { if (!confirm('Delete user?')) return; await api(`/users/${id}`, { method: 'DELETE' }); toast('Deleted'); renderUsers(document.getElementById('page-content')); }
async function restoreUser(id) { await api(`/users/${id}/restore`, { method: 'PUT' }); toast('Restored'); renderUsers(document.getElementById('page-content')); }

// ============================================================
// SETTINGS
// ============================================================
async function renderSettings(el) {
    const r = await api('/settings/sidebar');
    const users = r.ok ? r.data : [];
    const pages = ['dashboard', 'reports', 'projects', 'recordings', 'recorder', 'replayer', 'api-testing'];

    el.innerHTML = `
        <div class="card">
            <div class="card-header"><h3>Sidebar Visibility Settings</h3></div>
            <p style="font-size:13px;color:var(--text2);margin-bottom:16px">Control which pages are visible in the sidebar for each Resource user.</p>
            ${users.length === 0 ? '<div class="empty-state"><p>No resource users</p></div>' :
                users.map(u => `<div style="margin-bottom:20px;padding-bottom:16px;border-bottom:1px solid var(--border)">
                    <div style="font-weight:600;font-size:14px;margin-bottom:8px">${esc(u.username)}</div>
                    <div style="display:flex;gap:16px;flex-wrap:wrap">
                        ${pages.map(p => `<label style="display:flex;align-items:center;gap:6px;font-size:13px">
                            <label class="toggle-switch">
                                <input type="checkbox" data-user="${u.id}" data-page="${p}" ${u.sidebar[p] !== false ? 'checked' : ''}>
                                <span class="toggle-slider"></span>
                            </label>
                            ${p}
                        </label>`).join('')}
                    </div>
                    <button class="btn btn-primary btn-sm" style="margin-top:8px" onclick="saveSidebarSettings(${u.id})">Save</button>
                </div>`).join('')}
        </div>
    `;
}

async function saveSidebarSettings(userId) {
    const checks = document.querySelectorAll(`input[data-user="${userId}"]`);
    const settings = Array.from(checks).map(c => ({ page_name: c.dataset.page, visible: c.checked }));
    await api(`/settings/sidebar/${userId}`, { method: 'PUT', body: JSON.stringify({ settings }) });
    toast('Settings saved!');
}

// ============================================================
// MODALS & UTILITIES
// ============================================================
function showModal(title, bodyHtml, onSave) {
    const overlay = document.createElement('div');
    overlay.className = 'modal-overlay';
    overlay.id = 'modal-overlay';
    overlay.innerHTML = `<div class="modal">
        <div class="modal-header"><h3>${title}</h3><button class="modal-close" onclick="closeModal()">&times;</button></div>
        <div class="modal-body">${bodyHtml}</div>
        <div class="modal-footer"><button class="btn btn-secondary" onclick="closeModal()">Cancel</button><button class="btn btn-primary" id="modal-save-btn">Save</button></div>
    </div>`;
    document.body.appendChild(overlay);
    overlay.querySelector('#modal-save-btn').onclick = onSave;
    overlay.onclick = e => { if (e.target === overlay) closeModal(); };
}
function closeModal() { document.getElementById('modal-overlay')?.remove(); }

function openGallery(images, index) {
    let idx = index || 0;
    function draw() {
        let overlay = document.getElementById('gallery-overlay');
        if (!overlay) {
            overlay = document.createElement('div');
            overlay.id = 'gallery-overlay';
            overlay.className = 'gallery-overlay';
            document.body.appendChild(overlay);
        }
        overlay.innerHTML = `
            <button class="gallery-close" onclick="document.getElementById('gallery-overlay').remove()">&times;</button>
            <button class="gallery-nav gallery-prev" onclick="galleryNav(-1)">&#8249;</button>
            <img class="gallery-img" src="${images[idx]}">
            <button class="gallery-nav gallery-next" onclick="galleryNav(1)">&#8250;</button>
            <div class="gallery-counter">${idx+1} of ${images.length}</div>
        `;
        overlay.onclick = e => { if (e.target === overlay) overlay.remove(); };
    }
    window.galleryNav = d => { idx = (idx + d + images.length) % images.length; draw(); };
    draw();
    document.addEventListener('keydown', function handler(e) {
        if (e.key === 'Escape') { document.getElementById('gallery-overlay')?.remove(); document.removeEventListener('keydown', handler); }
        if (e.key === 'ArrowLeft') window.galleryNav(-1);
        if (e.key === 'ArrowRight') window.galleryNav(1);
    });
}

function esc(s) { if (s == null) return ''; const d = document.createElement('div'); d.textContent = String(s); return d.innerHTML; }
function statusColor(s) { return { PASSED: 'var(--green)', FAILED: 'var(--red)', RUNNING: 'var(--blue)', STOPPED: 'var(--yellow)' }[s] || 'var(--text3)'; }
function formatBytes(b) { if (!b || b === 0) return '0 B'; const k = 1024; const s = ['B', 'KB', 'MB']; const i = Math.floor(Math.log(b) / Math.log(k)); return (b / Math.pow(k, i)).toFixed(1) + ' ' + s[i]; }
function timeAgo(ts) {
    if (!ts) return '-';
    const d = new Date(ts.replace(' ', 'T'));
    const s = Math.floor((Date.now() - d.getTime()) / 1000);
    if (s < 60) return s + 's ago';
    if (s < 3600) return Math.floor(s / 60) + 'm ago';
    if (s < 86400) return Math.floor(s / 3600) + 'h ago';
    return Math.floor(s / 86400) + 'd ago';
}

// --- Init ---
window.addEventListener('load', () => {
    const hash = window.location.hash.slice(1);
    if (hash) currentPage = hash;
    render();
});
