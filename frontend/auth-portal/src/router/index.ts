import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '../views/LoginView.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/login',
    },
    {
      path: '/login',
      name: 'Login',
      component: LoginView,
      meta: { title: '登录', hidden: false, requiresAuth: false },
    },
  ],
})

router.beforeEach((to) => {
  const title = (to.meta.title as string) || '统一认证'
  document.title = title
})
