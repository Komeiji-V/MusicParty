<template>
  <div class="min-h-screen bg-medical-50 font-sans flex items-center justify-center p-4">
    <div class="w-full max-w-5xl flex flex-col md:flex-row shadow-2xl border border-medical-200 rounded-sm overflow-hidden bg-white/30 backdrop-blur-md">
      <div class="md:w-1/2 bg-medical-900 text-white p-8 md:p-12 flex flex-col justify-center relative overflow-hidden">
        <div class="absolute inset-0 bg-[linear-gradient(rgba(249,115,22,0.05)_1px,transparent_1px),linear-gradient(90deg,rgba(249,115,22,0.05)_1px,transparent_1px)] bg-[size:40px_40px]"></div>
        <div class="relative z-10">
          <div class="w-3 h-3 bg-accent mb-6"></div>
          <h1 class="text-4xl md:text-5xl font-black tracking-tighter leading-none mb-4">{{ siteTitle }}<br/>PARTY</h1>
          <p class="font-mono text-xs text-medical-400 tracking-widest mb-8">UNIFIED ACCOUNT ACCESS</p>
          <div class="space-y-2 text-sm text-medical-300 font-sans">
            <div class="flex items-center gap-2"><span class="w-1 h-1 bg-accent"></span> 多人协同音乐播放</div>
            <div class="flex items-center gap-2"><span class="w-1 h-1 bg-accent"></span> 多平台音源支持</div>
            <div class="flex items-center gap-2"><span class="w-1 h-1 bg-accent"></span> 统一账号体系登录</div>
          </div>
        </div>
      </div>

      <div class="md:w-1/2 bg-white p-8 md:p-12 flex flex-col justify-center">
        <div class="mb-6">
          <h2 class="text-2xl font-black text-medical-900 tracking-tighter">LOGIN</h2>
          <p class="text-xs font-mono text-medical-500 mt-1">AUTHENTICATE VIA UNIFIED AUTH CENTER.</p>
        </div>

        <button
          @click="goLogin"
          :disabled="loading"
          class="w-full bg-medical-900 text-white font-bold py-3 hover:bg-accent transition-colors disabled:opacity-50 chamfer-br"
        >
          {{ loading ? 'REDIRECTING...' : 'CONTINUE WITH AUTH CENTER' }}
        </button>

        <p class="text-xs text-medical-400 mt-4 leading-relaxed">
          将跳转到统一认证中心完成登录。登录成功后自动返回本站。
          账号在其他项目通用，无需重复注册。
        </p>

        <div v-if="errorMsg" class="mt-4 text-red-500 font-mono text-xs animate-pulse">
          > ERROR: {{ errorMsg }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const siteTitle = ref('MUSIC')
const loading = ref(false)
const errorMsg = ref('')

onMounted(async () => {
  try {
    const res = await fetch('/api/config')
    const data = await res.json()
    siteTitle.value = data.siteTitle?.replace(' MUSIC', '') || 'MUSIC'
    auth.authCenterUrl = data.authCenterUrl || ''
  } catch (e) {
    console.error('Failed to load config', e)
  }
})

function goLogin() {
  if (!auth.authCenterUrl) {
    errorMsg.value = '认证中心未配置'
    return
  }
  loading.value = true
  window.location.href = auth.getLoginUrl('/')
}
</script>
