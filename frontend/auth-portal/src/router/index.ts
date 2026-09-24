import { createRouter, createWebHistory } from 'vue-router'
import Login from '@/views/Login.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'Login', component: Login },
    { path: '/:pathMatch(.*)*', redirect: '/login' },
  ],
})
