import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import HomeView from '@/views/HomeView.vue'
import ArticleListView from '@/views/ArticleListView.vue'
import ArticleDetailView from '@/views/ArticleDetailView.vue'
import CategoryView from '@/views/CategoryView.vue'
import TagView from '@/views/TagView.vue'
import AboutView from '@/views/AboutView.vue'
import AskView from '@/views/AskView.vue'
import OidcCallback from '@/views/OidcCallback.vue'
import LoggedOutView from '@/views/LoggedOutView.vue'
import NotFoundView from '@/views/NotFoundView.vue'
import { hasValidAccessToken, redirectForLogin } from '@/utils/oidc'

const routes: RouteRecordRaw[] = [
  { path: '/', name: 'Home', component: HomeView },
  { path: '/articles', name: 'Articles', component: ArticleListView },
  { path: '/articles/:idOrSlug', name: 'ArticleDetail', component: ArticleDetailView },
  { path: '/categories', name: 'Categories', component: CategoryView },
  { path: '/tags', name: 'Tags', component: TagView },
  { path: '/about', name: 'About', component: AboutView },
  // 知识问答（登录态守卫；blog-knowledge-base 提案 tasks 11.5）
  { path: '/ask', name: 'Ask', component: AskView, meta: { requiresAuth: true } },
  { path: '/callback', name: 'OidcCallback', component: OidcCallback },
  // 登出回跳落点（oidc.ts postLogoutRedirectUri = {origin}/logged-out）
  { path: '/logged-out', name: 'LoggedOut', component: LoggedOutView },
  { path: '/:pathMatch(.*)*', name: 'NotFound', component: NotFoundView },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach(async (to) => {
  if (to.path === '/callback') return true
  if (to.meta.requiresAuth && !hasValidAccessToken()) {
    await redirectForLogin(to.fullPath)
    return false
  }
  return true
})
