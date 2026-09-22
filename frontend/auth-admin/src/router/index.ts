import { createRouter, createWebHistory } from 'vue-router'
import { getAccessToken } from '../utils/token'
import AdminLayout from '../views/layout/AdminLayout.vue'
import ClientListView from '../views/clients/ClientListView.vue'
import GrantView from '../views/session/GrantView.vue'
import AuditView from '../views/audit/AuditView.vue'
import CallbackView from '../views/CallbackView.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/callback', name: 'Callback', component: CallbackView, meta: { title: '登录回调', hidden: true, requiresAuth: false } },
    {
      path: '/',
      component: AdminLayout,
      meta: { title: '客户端管理', requiresAuth: true },
      children: [
        { path: '', redirect: '/clients' },
        { path: 'clients', name: 'Clients', component: ClientListView, meta: { title: '客户端列表', icon: 'List', hidden: false, requiresAuth: true } },
        { path: 'grants', name: 'Grants', component: GrantView, meta: { title: '令牌与会话', icon: 'Connection', hidden: false, requiresAuth: true } },
        { path: 'audit', name: 'Audit', component: AuditView, meta: { title: '登录审计', icon: 'Document', hidden: false, requiresAuth: true } },
      ],
    },
  ],
})

router.beforeEach((to) => {
  document.title = (to.meta.title as string) || '认证客户端管理'
  if (to.meta.requiresAuth && !getAccessToken()) {
    window.location.href = '/oauth2/authorize'
    return false
  }
})
