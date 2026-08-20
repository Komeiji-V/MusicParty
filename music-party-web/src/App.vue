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
      router.push('/')
      return
    }
    auth.fetchMe()
  } catch (e) {
    console.error('SSO login failed:', e.message)
    router.push('/login')
  }
})
</script>
