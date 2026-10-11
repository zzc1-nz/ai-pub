import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import router from '@/router'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000
})

/** 清理登录态并跳转登录页（避免重复跳转） */
const redirectToLogin = () => {
  const userStore = useUserStore()
  userStore.logout()
  if (router.currentRoute.value.path !== '/login') {
    router.push('/login')
  }
}

request.interceptors.request.use(
  (config) => {
    const userStore = useUserStore()
    if (userStore.token) {
      config.headers.Authorization = `Bearer ${userStore.token}`
    }
    // 对于 FormData 请求，删除 Content-Type，让浏览器自动设置 boundary
    if (config.data instanceof FormData) {
      delete config.headers['Content-Type']
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      if (res.code === 401) {
        redirectToLogin()
      }
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res
  },
  (error) => {
    // HTTP 非 2xx 会走这里。后端 SecurityConfig 的 AuthenticationEntryPoint /
    // AccessDeniedHandler 现在会返回 401 / 403 + { code, message } 结构，
    // 必须在此处理，否则令牌过期后只会提示"请求失败"而不会跳回登录页。
    const status = error.response && error.response.status
    const data = error.response && error.response.data
    const message = (data && data.message) || error.message || '网络错误'

    if (status === 401) {
      ElMessage.error(message || '未登录或登录已过期')
      redirectToLogin()
    } else if (status === 403) {
      ElMessage.error(message || '无权限访问该资源')
    } else {
      ElMessage.error(message)
    }
    return Promise.reject(error)
  }
)

export default request
