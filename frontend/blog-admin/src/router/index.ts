import { createRouter, createWebHistory } from 'vue-router'
import { getAccessToken } from '@/utils/token'
import { redirectToSso } from '@/utils/sso'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/sso/callback',
      name: 'sso-callback',
      component: () => import('@/views/SsoCallbackView.vue'),
      meta: { title: 'SSO', hidden: true, requiresAuth: false },
    },
    {
      path: '/',
      component: () => import('@/views/layout/AdminLayout.vue'),
      meta: { title: '博客管理', requiresAuth: true },
      children: [
        {
          path: '',
          name: 'home',
          component: () => import('@/views/HomeView.vue'),
          meta: { title: '首页', icon: 'HomeFilled', requiresAuth: true },
        },
        {
          path: 'rbac/roles',
          name: 'rbac-roles',
          component: () => import('@/views/rbac/RoleView.vue'),
          meta: { title: '角色管理', icon: 'User', requiresAuth: true },
        },
        {
          path: 'rbac/permissions',
          name: 'rbac-permissions',
          component: () => import('@/views/rbac/PermissionView.vue'),
          meta: { title: '权限码', icon: 'Key', requiresAuth: true },
        },
        {
          path: 'rbac/user-roles',
          name: 'rbac-user-roles',
          component: () => import('@/views/rbac/UserRoleView.vue'),
          meta: { title: '用户授权', icon: 'Connection', requiresAuth: true },
        },
      ],
    },
  ],
})

router.beforeEach((to) => {
  const authRequired = to.meta.requiresAuth !== false
  if (authRequired && !getAccessToken()) {
    // 统一认证 SSO：禁止 blog 自建账密页
    redirectToSso()
    return false
  }
  return true
})

export default router
