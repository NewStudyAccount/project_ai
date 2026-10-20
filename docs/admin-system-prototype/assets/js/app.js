/**
 * 原型公共壳与交互（纯原生 JS，无构建依赖）
 * - renderShell()：侧栏 240 纸白 + 顶栏 56 白（admin-ui-design.md §2）
 * - Proto.toast / Proto.openDialog / closeDialog / confirm / paginate
 * 菜单数据模拟后端下发（RBAC：type 1 目录 / 2 菜单；过滤 hidden/status）
 */
window.Proto = (function () {
  /* ---------------- 图标（内置单色 SVG，不引第三方图标包） ---------------- */
  const icon = (path) =>
    `<svg class="nav-icon" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round">${path}</svg>`;

  const ICONS = {
    home: icon('<path d="M2.5 6.8 8 2.5l5.5 4.3V13a.5.5 0 0 1-.5.5h-3v-4h-4v4H3a.5.5 0 0 1-.5-.5V6.8Z"/>'),
    system: icon('<circle cx="8" cy="8" r="2.2"/><path d="M8 1.8v1.6M8 12.6v1.6M14.2 8h-1.6M3.4 8H1.8M12.4 3.6l-1.1 1.1M4.7 11.3l-1.1 1.1M12.4 12.4l-1.1-1.1M4.7 4.7 3.6 3.6"/>'),
    audit: icon('<path d="M3.5 2.5h6.2L12.5 5.3V13a.5.5 0 0 1-.5.5H4a.5.5 0 0 1-.5-.5V3a.5.5 0 0 1 .5-.5Z"/><path d="M9.3 2.6v3h3M5.8 8.6h4.4M5.8 11h3"/>'),
    auth: icon('<circle cx="5.8" cy="8" r="2.6"/><path d="M8.4 8H14M12 8v2.2M10 8v1.6"/>'),
    chev: '<svg class="chev" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"><path d="M6 4l4 4-4 4"/></svg>',
    mark: '<svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"><path d="M3 12.5V6l5-3.5L13 6v6.5"/><path d="M6 12.5V9h4v3.5"/></svg>'
  };

  /* ---------------- 菜单（模拟后端下发） ---------------- */
  const NAV = [
    { key: 'index', label: '首页', href: 'index.html', icon: 'home' },
    {
      key: 'system', label: '系统管理', icon: 'system', children: [
        { key: 'user-list', label: '账号管理', href: 'user-list.html' },
        { key: 'role-list', label: '角色管理', href: 'role-list.html' },
        { key: 'menu-list', label: '菜单管理', href: 'menu-list.html' }
      ]
    },
    {
      key: 'audit', label: '审计中心', icon: 'audit', children: [
        { key: 'audit-log', label: '操作审计', href: 'audit-log.html' }
      ]
    },
    {
      key: 'auth', label: '认证中心', icon: 'auth', children: [
        { key: 'client-list', label: 'Client 管理', href: 'client-list.html' }
      ]
    }
  ];

  function renderShell() {
    const active = document.body.dataset.nav || '';
    const host = document.getElementById('app');
    if (!host) return;

    const navHtml = NAV.map((group) => {
      if (!group.children) {
        return `<a class="nav-item${group.key === active ? ' is-active' : ''}" href="${group.href}">
          ${ICONS[group.icon] || ''}<span class="nav-text">${group.label}</span></a>`;
      }
      const open = group.children.some((c) => c.key === active);
      const subs = group.children.map((c) =>
        `<a class="nav-item${c.key === active ? ' is-active' : ''}" href="${c.href}"><span class="nav-text">${c.label}</span></a>`
      ).join('');
      return `<div class="nav-item js-group${open ? ' is-open' : ''}">
          ${ICONS[group.icon] || ''}<span class="nav-text">${group.label}</span>${ICONS.chev}
        </div>
        <div class="nav-sub${open ? ' is-open' : ''}">${subs}</div>`;
    }).join('');

    host.innerHTML = `
      <div class="app">
        <aside class="sidebar">
          <div class="logo">
            <div class="logo-mark">${ICONS.mark}</div>
            <div>
              <div class="logo-name">管理控制台</div>
              <div class="logo-sub">云白原型 · Mock 数据</div>
            </div>
          </div>
          <nav class="nav">${navHtml}</nav>
        </aside>
        <div class="main-wrap">
          <header class="topbar">
            <div class="topbar-title">后端管理系统</div>
            <div class="topbar-right">
              <span class="proto-badge">原型演示 · 数据均为 Mock</span>
              <div class="topbar-user">
                <span class="avatar">张</span>
                <span>张伟（超级管理员）</span>
              </div>
              <a class="btn-text" href="login.html">退出</a>
            </div>
          </header>
          <main class="main">${host.innerHTML}</main>
        </div>
      </div>`;

    host.querySelectorAll('.js-group').forEach((el) => {
      el.addEventListener('click', () => {
        el.classList.toggle('is-open');
        el.nextElementSibling.classList.toggle('is-open');
      });
    });
  }

  /* ---------------- Toast ---------------- */
  let toastTimer = null;
  function toast(msg, type) {
    let el = document.querySelector('.toast');
    if (!el) {
      el = document.createElement('div');
      el.className = 'toast';
      el.innerHTML = '<span class="toast-dot"></span><span class="toast-msg"></span>';
      document.body.appendChild(el);
    }
    el.classList.toggle('is-error', type === 'error');
    el.querySelector('.toast-msg').textContent = msg;
    requestAnimationFrame(() => el.classList.add('is-show'));
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => el.classList.remove('is-show'), 2000);
  }

  /* ---------------- 弹窗（D-Form / D-Panel） ---------------- */
  function openDialog(id) {
    const mask = document.getElementById(id);
    if (mask) mask.classList.add('is-open');
  }

  function closeDialog(id) {
    const mask = document.getElementById(id);
    if (mask) mask.classList.remove('is-open');
  }

  /** 注入通用二次确认弹窗（全局唯一，页面无需自带标记） */
  function ensureConfirmMask() {
    if (document.getElementById('confirm-mask')) return;
    const div = document.createElement('div');
    div.className = 'dialog-mask';
    div.id = 'confirm-mask';
    div.innerHTML = `
      <div class="dialog" style="width:420px">
        <div class="dialog-hd">确认操作</div>
        <div class="dialog-bd"><div class="js-confirm-text" style="font-size:14px;color:var(--text-regular);line-height:1.7"></div></div>
        <div class="dialog-ft">
          <button class="btn js-confirm-cancel">取消</button>
          <button class="btn btn-danger js-confirm-ok">确定</button>
        </div>
      </div>`;
    document.body.appendChild(div);
  }

  /** 危险操作二次确认（ElMessageBox.confirm 对应） */
  function confirm(opts) {
    return new Promise((resolve) => {
      const { title = '确认操作', text = '', okText = '确定' } = opts || {};
      ensureConfirmMask();
      const mask = document.getElementById('confirm-mask');
      mask.querySelector('.dialog-hd').textContent = title;
      mask.querySelector('.js-confirm-text').textContent = text;
      const okBtn = mask.querySelector('.js-confirm-ok');
      okBtn.textContent = okText;
      mask.classList.add('is-open');

      const cleanup = (result) => {
        mask.classList.remove('is-open');
        okBtn.replaceWith(okBtn.cloneNode(true));
        resolve(result);
      };
      mask.querySelector('.js-confirm-cancel').onclick = () => cleanup(false);
      mask.querySelector('.js-confirm-ok').onclick = () => cleanup(true);
    });
  }

  /* ---------------- 分页（PageQuery: current 从 1 起） ---------------- */
  function paginate(container, opts) {
    const { total, current, size, onChange } = opts;
    const pages = Math.max(1, Math.ceil(total / size));
    const btn = (p, label, disabled, active) =>
      `<button class="page-btn${active ? ' is-active' : ''}" data-p="${p}"${disabled ? ' disabled' : ''}>${label}</button>`;

    let nums = '';
    for (let p = 1; p <= pages; p++) {
      if (pages > 7 && p > 2 && p < pages - 1 && Math.abs(p - current) > 1) {
        if (!nums.endsWith('…')) nums += '<span style="padding:0 2px">…</span>';
        continue;
      }
      nums += btn(p, String(p), false, p === current);
    }

    container.innerHTML = `
      <span class="total">共 ${total} 条</span>
      ${btn(current - 1, '上一页', current <= 1, false)}
      ${nums}
      ${btn(current + 1, '下一页', current >= pages, false)}`;

    container.querySelectorAll('.page-btn').forEach((b) => {
      b.addEventListener('click', () => {
        const p = Number(b.dataset.p);
        if (p >= 1 && p <= pages && p !== current) onChange(p);
      });
    });
  }

  /* ---------------- 工具 ---------------- */
  const esc = (s) =>
    String(s == null ? '' : s).replace(/[&<>"']/g, (c) =>
      ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

  const statusPill = (status) =>
    status === 1
      ? '<span class="status-pill is-on">启用</span>'
      : '<span class="status-pill is-off">停用</span>';

  const resultPill = (result) =>
    result === 1
      ? '<span class="status-pill is-on">成功</span>'
      : '<span class="status-pill is-bad">失败</span>';

  document.addEventListener('DOMContentLoaded', renderShell);

  return { toast, openDialog, closeDialog, confirm, paginate, esc, statusPill, resultPill, renderShell };
})();
