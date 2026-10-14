import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import HomeView from '@/views/HomeView.vue'
import ArticleListView from '@/views/ArticleListView.vue'
import ArticleDetailView from '@/views/ArticleDetailView.vue'
import CategoryView from '@/views/CategoryView.vue'
import TagView from '@/views/TagView.vue'
import AboutView from '@/views/AboutView.vue'
import NotFoundView from '@/views/NotFoundView.vue'

const routes: RouteRecordRaw[] = [
  { path: '/', name: 'Home', component: HomeView },
  { path: '/articles', name: 'Articles', component: ArticleListView },
  { path: '/articles/:idOrSlug', name: 'ArticleDetail', component: ArticleDetailView },
  { path: '/categories', name: 'Categories', component: CategoryView },
  { path: '/tags', name: 'Tags', component: TagView },
  { path: '/about', name: 'About', component: AboutView },
  { path: '/:pathMatch(.*)*', name: 'NotFound', component: NotFoundView },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
})
