import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/store/user'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue')
  },
  {
    path: '/',
    component: () => import('@/views/Layout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/Dashboard.vue')
      },
      {
        path: 'accounts',
        name: 'AccountList',
        component: () => import('@/views/AccountList.vue')
      },
      {
        path: 'accounts/edit/:id?',
        name: 'AccountEdit',
        component: () => import('@/views/AccountEdit.vue')
      },
      {
        path: 'menus',
        name: 'MenuManage',
        component: () => import('@/views/MenuManage.vue')
      },
      {
        path: 'materials',
        name: 'MaterialManage',
        component: () => import('@/views/MaterialManage.vue')
      },
      {
        path: 'reply-rules',
        name: 'ReplyRule',
        component: () => import('@/views/ReplyRule.vue')
      },
      {
        path: 'messages',
        name: 'MessageList',
        component: () => import('@/views/MessageList.vue')
      },
      {
        path: 'users',
        name: 'UserManage',
        component: () => import('@/views/UserManage.vue')
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const userStore = useUserStore()
  if (to.path !== '/login' && !userStore.token) {
    next('/login')
  } else if (to.path === '/login' && userStore.token) {
    next('/')
  } else {
    next()
  }
})

export default router
