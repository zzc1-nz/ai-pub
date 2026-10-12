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
        component: () => import('@/views/UserManage.vue'),
        // 后端 SysUserController 有 @PreAuthorize("hasRole('ADMIN')")，
        // 前端同步限制，避免 OPERATOR 直接输入 URL 进入后拿到 403
        meta: { requiresAdmin: true }
      }
    ]
  },
  {
    // 兜底路由：原先未知路径匹配失败会渲染空白页
    path: '/:pathMatch(.*)*',
    redirect: '/dashboard'
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
  } else if (to.meta && to.meta.requiresAdmin && userStore.userInfo?.role !== 'ADMIN') {
    // 非管理员访问仅管理员可见的页面时退回首页
    next('/dashboard')
  } else {
    next()
  }
})

export default router
