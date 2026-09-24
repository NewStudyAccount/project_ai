import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { hasValidAccessToken, redirectForLogin } from '@/utils/oidc'
import AppLayout from '@/components/AppLayout.vue'
import Dashboard from '@/views/Dashboard.vue'
import ClientList from '@/views/client/ClientList.vue'
import GrantList from '@/views/grant/GrantList.vue'
import LoginAttemptList from '@/views/audit/LoginAttemptList.vue'
import AuditLogList from '@/views/audit/AuditLogList.vue'
import MenuList from '@/views/menu/MenuList.vue'
import RoleList from '@/views/role/RoleList.vue'
import UserRoleList from '@/views/user/UserRoleList.vue'
import OidcCallback from '@/views/OidcCallback.vue'
import LoggedOut from '@/views/LoggedOut.vue'
import NotFound from '@/views/NotFound.vue'

const componentByPath: Record<string, unknown> = {
  'views/Dashboard.vue': Dashboard,
  'views/client/ClientList.vue': ClientList,
  'views/grant/GrantList.vue': GrantList,
  'views/audit/LoginAttemptList.vue': LoginAttemptList,
  'views/audit/AuditLogList.vue': AuditLogList,
  'views/menu/MenuList.vue': MenuList,
  'views/role/RoleList.vue': RoleList,
  'views/user/UserRoleList.vue': UserRoleList,
}

const staticRoutes: RouteRecordRaw[] = [
  { path: '/callback', name: 'OidcCallback', component: OidcCallback },
  { path: '/logged-out', name: 'LoggedOut', component: LoggedOut },
  {
    path: '/',
    name: 'Home',
    component: AppLayout,
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'Dashboard', component: Dashboard },
      { path: 'clients', name: 'Clients', component: ClientList },
      { path: 'grants', name: 'Grants', component: GrantList },
      { path: 'login-attempts', name: 'LoginAttempts', component: LoginAttemptList },
      { path: 'audit-logs', name: 'AuditLogs', component: AuditLogList },
      { path: 'menus', name: 'Menus', component: MenuList },
      { path: 'roles', name: 'Roles', component: RoleList },
      { path: 'user-roles', name: 'UserRoles', component: UserRoleList },
    ],
  },
  { path: '/:pathMatch(.*)*', name: 'NotFound', component: NotFound },
]

export const router = createRouter({
  history: createWebHistory(),
  routes: staticRoutes,
})

router.beforeEach(async (to) => {
  if (to.path === '/callback' || to.path === '/logged-out') return true
  if (!hasValidAccessToken()) {
    await redirectForLogin(to.fullPath)
    return false
  }
  const auth = useAuthStore()
  try {
    await auth.init()
  } catch {
    await redirectForLogin(to.fullPath)
    return false
  }
  installDynamicRoutes(auth.menus)
  return true
})

function installDynamicRoutes(menus: Array<{ id: string; type: number; path: string; component: string; children?: unknown[] }>) {
  const walk = (nodes: typeof menus) => {
    nodes.forEach((menu) => {
      if (menu.type === 2 && menu.path) {
        const path = menu.path.startsWith('/') ? menu.path.slice(1) : menu.path
        if (!router.hasRoute('Menu_' + menu.id)) {
          router.addRoute('Home', {
            path,
            name: 'Menu_' + menu.id,
            component: (componentByPath[menu.component] ?? Dashboard) as never,
          })
        }
      }
      if (Array.isArray(menu.children) && menu.children.length) {
        walk(menu.children as typeof menus)
      }
    })
  }
  walk(menus)
}
