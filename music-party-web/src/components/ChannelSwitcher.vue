<template>
  <div class="relative">
    <button
      @click="toggleOpen"
      class="flex items-center gap-1.5 h-9 px-2 md:px-3 border border-medical-200 bg-medical-50 hover:bg-medical-100 text-medical-900 text-sm font-bold transition-colors rounded-sm"
    >
      <!-- 基线对齐：CH: 与频道名（不同字号）保持文字基线一致 -->
      <span class="flex items-baseline gap-1.5 min-w-0">
        <span class="text-xs font-mono text-medical-400">CH:</span>
        <span class="truncate max-w-10 md:max-w-[120px]">{{ currentLabel }}</span>
      </span>
      <ChevronDown class="w-3.5 h-3.5 text-medical-400 flex-shrink-0" :class="{ 'rotate-180': isOpen }" />
    </button>

    <Transition
      enter-active-class="transition duration-200 ease-out"
      enter-from-class="opacity-0 scale-95 -translate-y-1"
      enter-to-class="opacity-100 scale-100 translate-y-0"
      leave-active-class="transition duration-150 ease-in"
      leave-from-class="opacity-100 scale-100 translate-y-0"
      leave-to-class="opacity-0 scale-95 -translate-y-1"
    >
      <div
        v-if="isOpen"
        class="absolute top-full right-0 mt-1 w-64 bg-white border border-medical-200 shadow-xl z-50 rounded-sm overflow-hidden"
      >
        <div class="p-2 border-b border-medical-100 bg-medical-50">
          <span class="text-xs font-mono text-medical-500 font-bold">CHANNELS</span>
        </div>
        <div class="max-h-64 overflow-y-auto">
          <div
            v-for="ch in channels"
            :key="ch.id"
            @click="handleSelect(ch)"
            class="flex items-center justify-between px-3 py-2 hover:bg-medical-50 cursor-pointer transition-colors border-b border-medical-50 last:border-0"
            :class="{ 'bg-accent/5 border-l-2 border-l-accent': ch.id === currentChannelId }"
          >
            <div class="flex items-center gap-2 min-w-0">
              <Lock v-if="ch.hasPassword" class="w-3.5 h-3.5 text-medical-400 flex-shrink-0" />
              <UserPlus v-else-if="ch.joinPermission === 'INVITE_ONLY'" class="w-3.5 h-3.5 text-medical-400 flex-shrink-0" />
              <EyeOff v-else-if="ch.joinPermission === 'HIDDEN'" class="w-3.5 h-3.5 text-medical-400 flex-shrink-0" />
              <span class="text-sm font-bold truncate text-medical-800">{{ ch.name }}</span>
            </div>
            <span class="text-xs font-mono text-medical-400 flex-shrink-0 ml-2">{{ ch.onlineCount || 0 }}</span>
          </div>
          <div v-if="channels.length === 0" class="px-3 py-4 text-center text-xs text-medical-400 font-mono">
            NO CHANNELS
          </div>
        </div>
        <div v-if="auth.isChannelAdmin || isChannelAdmin" class="border-t border-medical-100">
          <button
            @click="openAdminPanel"
            class="w-full flex items-center gap-2 px-3 py-2.5 hover:bg-medical-50 transition-colors text-sm font-bold text-medical-700 text-left"
          >
            <Settings class="w-4 h-4 text-medical-400" />
            频道管理
          </button>
        </div>
        <!-- 返回首页 -->
        <div class="border-t border-medical-100">
          <button
            @click="goHome"
            class="w-full flex items-center gap-2 px-3 py-2.5 hover:bg-red-50 transition-colors text-sm font-bold text-red-500 text-left"
          >
            <Home class="w-4 h-4" />
            返回首页
          </button>
        </div>
      </div>
    </Transition>

    <div v-if="showPasswordInput" class="fixed inset-0 z-[160] flex items-center justify-center bg-medical-900/60 backdrop-blur-sm" @click.self="showPasswordInput = false">
      <div class="bg-white p-6 shadow-2xl border border-medical-200 w-80 chamfer-br">
        <div class="mb-4">
          <h3 class="text-lg font-black text-medical-900">频道密码</h3>
          <p class="text-xs font-mono text-medical-400 mt-1">请输入 {{ pendingChannel?.name }} 的密码</p>
        </div>
        <input
          v-model="passwordInput"
          type="password"
          placeholder="密码"
          @keyup.enter="confirmPassword"
          class="w-full bg-medical-50 border border-medical-200 p-3 outline-none focus:border-accent font-mono text-sm tracking-widest mb-3"
          autofocus
        />
        <button
          @click="confirmPassword"
          class="w-full bg-medical-900 text-white font-bold py-2 hover:bg-accent transition-colors chamfer-br"
        >
          JOIN
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ChevronDown, Lock, UserPlus, EyeOff, Home, Settings } from 'lucide-vue-next'
import { useChannelStore } from '../stores/channel'
import { useAuthStore } from '../stores/auth'
import { usePlayerStore } from '../stores/player'
import { useToast } from '../composables/useToast'

const emit = defineEmits(['admin-panel'])

const router = useRouter()
const channelStore = useChannelStore()
const auth = useAuthStore()
const playerStore = usePlayerStore()
const { error } = useToast()

const isOpen = ref(false)
const showPasswordInput = ref(false)
const passwordInput = ref('')
const pendingChannel = ref(null)

// 本频道管理员：当前频道卡片上的 isAdmin 标记（含总管理员）
const isChannelAdmin = computed(() => channelStore.currentChannel?.isAdmin === true)

// 打开下拉时刷新频道列表，保证在线人数最新
function toggleOpen() {
  isOpen.value = !isOpen.value
  if (isOpen.value) {
    channelStore.fetchChannels().catch(() => {})
  }
}

function openAdminPanel() {
  isOpen.value = false
  // 面板由 MainLayout 在根部渲染（避免顶部栏 stacking context 导致遮罩层级失效）
  emit('admin-panel')
}

const channels = computed(() => channelStore.channels)
const currentChannelId = computed(() => channelStore.currentChannelId)

const currentLabel = computed(() => {
  if (!channelStore.currentChannel) return 'Default'
  return channelStore.currentChannel.name || 'Channel'
})

// 返回首页：断开连接并清除频道状态，确保之后还能重新进入同一频道
function goHome() {
  isOpen.value = false
  playerStore.leaveChannel()
  channelStore.clearCurrentChannel()
  router.push('/')
}

async function handleSelect(ch) {
  if (ch.id === channelStore.currentChannelId) {
    isOpen.value = false
    return
  }
  if ((ch.joinPermission === 'INVITE_ONLY' || ch.joinPermission === 'HIDDEN') && !ch.isMember && !ch.isAdmin) {
    error(ch.joinPermission === 'HIDDEN' ? '无权访问该频道' : '该频道仅限受邀成员加入')
    return
  }
  if (ch.hasPassword) {
    pendingChannel.value = ch
    showPasswordInput.value = true
    passwordInput.value = ''
    return
  }
  await switchTo(ch)
}

async function switchTo(ch) {
  try {
    await channelStore.switchChannel(ch)
    isOpen.value = false
  } catch (e) {
    error(e?.response?.data?.message || '加入频道失败')
    console.error('Failed to join channel', e)
  }
}

async function confirmPassword() {
  if (!pendingChannel.value) return
  try {
    await channelStore.switchChannel(pendingChannel.value, passwordInput.value)
    showPasswordInput.value = false
    isOpen.value = false
    pendingChannel.value = null
    passwordInput.value = ''
  } catch (e) {
    error(e?.response?.data?.message || '加入频道失败')
    console.error('Failed to join channel', e)
  }
}
</script>
