import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/login', name: 'Login', component: () => import('../views/LoginPage.vue') },
  // 首页门户公开可访问（未登录可浏览频道/统计，点击频道才要求登录）
  { path: '/', name: 'Home', component: () => import('../views/HomePage.vue') },
  { path: '/room', name: 'Room', component: () => import('../views/RoomPage.vue'), meta: { requiresAuth: true } },
  { path: '/profile', name: 'Profile', component: () => import('../views/ProfilePage.vue'), meta: { requiresAuth: true } },
  // 公开主页（任何人可访问，无需登录）
  { path: '/u/:username', name: 'PublicProfile', component: () => import('../views/PublicProfilePage.vue') },
  { path: '/playlists', name: 'Playlists', component: () => import('../views/PlaylistsPage.vue'), meta: { requiresAuth: true } },
  { path: '/channel/:id/settings', name: 'ChannelSettings', component: () => import('../views/ChannelSettingsPage.vue'), meta: { requiresAuth: true } },
  { path: '/admin', name: 'Admin', component: () => import('../views/AdminPage.vue'), meta: { requiresAuth: true, requiresSuperAdmin: true } },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 未登录 → 跳转认证中心登录页（登录成功后回跳原页面）
router.beforeEach(async (to, from, next) => {
  const token = localStorage.getItem('mp_token')
  if (to.meta.requiresAuth && !token) {
    try {
      const res = await fetch('/api/config')
      const cfg = await res.json()
      const authUrl = cfg.authCenterUrl || ''
      if (authUrl) {
        const redirect = encodeURIComponent(window.location.origin + to.fullPath)
        window.location.href = `${authUrl}/login?redirect=${redirect}`
        return
      }
    } catch (e) {
      console.error('Auth center not configured:', e)
    }
    return next('/login')
  }
  // 注意：不再把带 token 访问 /login 的请求弹回首页——
  // token 过期时（localStorage 仍残留）会与 401 跳转形成死循环，表现为"点击无反应"
  if (to.meta.requiresSuperAdmin && token) {
    try {
      const res = await fetch('/api/auth/me', { headers: { 'Authorization': `Bearer ${token}` } })
      if (res.ok) {
        const user = await res.json()
        if (user.role !== 'SUPER_ADMIN') {
          return next('/')
        }
      }
    } catch (e) {
      console.error('Failed to verify admin role:', e)
    }
  }
  next()
})

export default router
