import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import AppLayout from '@/components/AppLayout.vue'
import Dashboard from '@/views/Dashboard.vue'
import UserList from '@/views/user/UserList.vue'
import AuditList from '@/views/audit/AuditList.vue'
import MenuList from '@/views/menu/MenuList.vue'
import RoleList from '@/views/role/RoleList.vue'
import Login from '@/views/Login.vue'
import NotFound from '@/views/NotFound.vue'

const componentByPath: Record<string, unknown> = {
  'views/user/UserList.vue': UserList,
  'views/audit/AuditList.vue': AuditList,
  'views/menu/MenuList.vue': MenuList,
  'views/role/RoleList.vue': RoleList,
}

const staticRoutes: RouteRecordRaw[] = [
  { path: '/login', name: 'Login', component: Login },
  {
    path: '/',
    name: 'Home',
    component: AppLayout,
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'Dashboard', component: Dashboard },
      { path: 'users', name: 'Users', component: UserList },
      { path: 'audits', name: 'Audits', component: AuditList },
      { path: 'menus', name: 'Menus', component: MenuList },
      { path: 'roles', name: 'Roles', component: RoleList },
    ],
  },
  { path: '/:pathMatch(.*)*', name: 'NotFound', component: NotFound },
]

export const router = createRouter({
  history: createWebHistory(),
  routes: staticRoutes,
})

router.beforeEach(async (to) => {
  if (to.path === '/login') return true
  const auth = useAuthStore()
  await auth.init().catch(() => undefined)
  installDynamicRoutes(auth.menus)
  return true
})

function installDynamicRoutes(menus: Array<{ id: string; type: number; path: string; component: string }>) {
  menus.forEach((menu) => {
    if (menu.type !== 2 || !menu.path) return
    const path = menu.path.startsWith('/') ? menu.path.slice(1) : menu.path
    if (router.hasRoute('Menu_' + menu.id)) return
    router.addRoute('Home', {
      path,
      name: 'Menu_' + menu.id,
      component: (componentByPath[menu.component] ?? Dashboard) as never,
    })
  })
}
