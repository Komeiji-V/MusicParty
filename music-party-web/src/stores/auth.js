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

  // 处理认证中心回跳 ?token= 参数
  async function handleCallback() {
    const q = new URLSearchParams(window.location.search)
    const t = q.get('token')
    if (!t) return false
    q.delete('token')
    const cleanUrl = window.location.pathname + (q.toString() ? '?' + q.toString() : '')
    window.history.replaceState({}, '', cleanUrl)

    const res = await fetch('/api/auth/sso', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token: t })
    })
    if (!res.ok) {
      const err = await res.json().catch(() => ({}))
      throw new Error(err.message || '登录失败')
    }
    const data = await res.json()
    token.value = data.token
    user.value = data.user
    localStorage.setItem('mp_token', data.token)
    return true
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
