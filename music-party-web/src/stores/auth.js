import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('mp_token'))
  const user = ref(null)
  const authCenterUrl = ref('')

  const isLoggedIn = computed(() => !!token.value)
  const isSuperAdmin = computed(() => user.value?.role === 'SUPER_ADMIN')
  const isChannelAdmin = computed(() => user.value?.role === 'CHANNEL_ADMIN' || isSuperAdmin.value)

  async function fetchConfig() {
    try {
      const res = await fetch('/api/config')
      const data = await res.json()
      authCenterUrl.value = data.authCenterUrl || ''
    } catch (e) {
      console.error('fetch config failed', e)
    }
  }

  function getLoginUrl(redirectPath = '/') {
    const origin = window.location.origin
    return `${authCenterUrl.value}/login?redirect=${encodeURIComponent(origin + redirectPath)}`
  }

  // 处理认证中心登录回跳：auth-center 登录页已跨域 POST /api/auth/sso
  // 并种入 60s 一次性 music_sso_token cookie，这里收 token → localStorage → 清除 cookie
  async function handleCallback() {
    const m = document.cookie.match(/(?:^|;\s*)music_sso_token=([^;]*)/)
    if (!m) return false
    const t = m[1] ? decodeURIComponent(m[1]) : ''
    // 兜底：无论后续是否成功，cookie 必须清除（一次性搬运通道）
    document.cookie = 'music_sso_token=; Max-Age=0; path=/'
    if (!t) return false

    token.value = t
    localStorage.setItem('mp_token', t)

    // 验证 token 有效性并刷新用户信息（含 authUid）；无效则按未登录处理
    try {
      const res = await fetch('/api/auth/me', {
        headers: { 'Authorization': `Bearer ${t}` }
      })
      if (res.ok) {
        user.value = await res.json()
        return true
      }
    } catch (e) {
      console.error('SSO token verification failed', e)
    }
    token.value = null
    user.value = null
    localStorage.removeItem('mp_token')
    return false
  }

  async function fetchMe() {
    if (!token.value) return
    try {
      const res = await fetch('/api/auth/me', {
        headers: { 'Authorization': `Bearer ${token.value}` }
      })
      if (res.ok) {
        user.value = await res.json()
      }
    } catch (e) {
      console.error('fetchMe failed', e)
    }
  }

  function logout() {
    fetch('/api/auth/logout', { method: 'POST' }).catch(() => {})
    token.value = null
    user.value = null
    localStorage.removeItem('mp_token')
  }

  return { token, user, isLoggedIn, isSuperAdmin, isChannelAdmin, authCenterUrl, fetchConfig, getLoginUrl, handleCallback, fetchMe, logout }
})
