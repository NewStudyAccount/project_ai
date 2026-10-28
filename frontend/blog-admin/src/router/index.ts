import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { hasValidAccessToken, redirectForLogin } from '@/utils/oidc'
import AppLayout from '@/components/AppLayout.vue'
import Dashboard from '@/views/Dashboard.vue'
import AuditList from '@/views/audit/AuditList.vue'
import MenuList from '@/views/menu/MenuList.vue'
import RoleList from '@/views/role/RoleList.vue'
import FileList from '@/views/file/FileList.vue'
import StorageProviderList from '@/views/storage/StorageProviderList.vue'
import KnowledgeDocList from '@/views/knowledge/DocList.vue'
import KnowledgeArticleIngest from '@/views/knowledge/ArticleIngest.vue'
import KnowledgeImportDoc from '@/views/knowledge/ImportDoc.vue'
import KnowledgeAiProviderList from '@/views/knowledge/AiProviderList.vue'
import KnowledgeTaskList from '@/views/knowledge/TaskList.vue'
import KnowledgeStats from '@/views/knowledge/KnowledgeStats.vue'
import CategoryList from '@/views/content/CategoryList.vue'
import TagList from '@/views/content/TagList.vue'
import ArticleList from '@/views/content/ArticleList.vue'
import ArticleMainForm from '@/views/content/ArticleMainForm.vue'
import ArticleContentEditor from '@/views/content/ArticleContentEditor.vue'
import SiteConfig from '@/views/content/SiteConfig.vue'
import OidcCallback from '@/views/OidcCallback.vue'
import LoggedOut from '@/views/LoggedOut.vue'
import NotFound from '@/views/NotFound.vue'

const componentByPath: Record<string, unknown> = {
  'views/audit/AuditList.vue': AuditList,
  'views/menu/MenuList.vue': MenuList,
  'views/role/RoleList.vue': RoleList,
  'views/file/FileList.vue': FileList,
  'views/storage/StorageProviderList.vue': StorageProviderList,
  'views/knowledge/DocList.vue': KnowledgeDocList,
  'views/knowledge/ArticleIngest.vue': KnowledgeArticleIngest,
  'views/knowledge/ImportDoc.vue': KnowledgeImportDoc,
  'views/knowledge/AiProviderList.vue': KnowledgeAiProviderList,
  'views/content/CategoryList.vue': CategoryList,
  'views/content/TagList.vue': TagList,
  'views/content/ArticleList.vue': ArticleList,
  'views/content/SiteConfig.vue': SiteConfig,
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
      { path: 'audits', name: 'Audits', component: AuditList },
      { path: 'menus', name: 'Menus', component: MenuList },
      { path: 'roles', name: 'Roles', component: RoleList },
      { path: 'files', name: 'Files', component: FileList },
      { path: 'storage/providers', name: 'StorageProviders', component: StorageProviderList },
      { path: 'categories', name: 'Categories', component: CategoryList },
      { path: 'tags', name: 'Tags', component: TagList },
      { path: 'articles', name: 'Articles', component: ArticleList },
      { path: 'articles/new', name: 'ArticleCreate', component: ArticleMainForm },
      { path: 'articles/:id/main', name: 'ArticleMain', component: ArticleMainForm },
      { path: 'articles/:id/content', name: 'ArticleContent', component: ArticleContentEditor },
      { path: 'site-config', name: 'SiteConfig', component: SiteConfig },
      // 知识库运维页（按钮权限 blog:knowledge:manage，无独立菜单）
      { path: 'knowledge/tasks', name: 'KnowledgeTasks', component: KnowledgeTaskList },
      { path: 'knowledge/stats', name: 'KnowledgeStats', component: KnowledgeStats },
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
