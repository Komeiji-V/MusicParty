<!-- src/App.vue -->
<template>
  <router-view />
  <ConfirmModal />
</template>

<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from './stores/auth'
import { useUiStore } from './stores/ui'
import ConfirmModal from './components/ConfirmModal.vue'

const router = useRouter()
const auth = useAuthStore()
const ui = useUiStore()

onMounted(async () => {
  ui.fetchConfig()
  auth.fetchConfig()
  try {
    const handled = await auth.handleCallback()
    if (handled) {
      // 已从 music_sso_token cookie 收到凭证：auth-center 已跳回目标页，无需再跳转
      return
    }
    auth.fetchMe()
  } catch (e) {
    console.error('SSO login failed:', e.message)
    router.push('/login')
  }
})
</script>
