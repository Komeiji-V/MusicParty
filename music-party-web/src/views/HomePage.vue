<template>
  <div class="min-h-screen bg-medical-50 font-sans flex flex-col relative overflow-x-hidden">
    <!-- 网格背景 -->
    <div class="fixed inset-0 z-0 pointer-events-none">
      <div class="absolute inset-0 bg-[linear-gradient(rgba(17,24,39,0.03)_1px,transparent_1px),linear-gradient(90deg,rgba(17,24,39,0.03)_1px,transparent_1px)] bg-[size:32px_32px]"></div>
      <div class="absolute inset-0 bg-[repeating-linear-gradient(45deg,transparent,transparent_20px,rgba(0,0,0,0.02)_20px,rgba(0,0,0,0.02)_21px)]"></div>
    </div>

    <!-- 1. 顶部栏 -->
    <header class="relative z-20 h-14 bg-white border-b border-medical-200 flex justify-between items-center px-4 md:px-6 flex-shrink-0">
      <div class="flex items-center gap-2 flex-shrink-0">
        <div class="w-2.5 h-2.5 md:w-3 md:h-3 bg-accent flex-shrink-0"></div>
        <div class="flex items-baseline gap-1">
          <button
            @click="uiStore.showInfo = true"
            class="font-black text-base md:text-xl tracking-tighter text-medical-900 whitespace-nowrap hover:text-accent transition-colors bg-transparent border-0 cursor-pointer"
          >
            {{ uiStore.siteTitle }}
          </button>
          <span class="text-medical-300 font-mono font-normal text-xs md:text-xs whitespace-nowrap">by {{ uiStore.authorName }}</span>
          <span
            v-if="uiStore.hasInfoPage"
            class="w-1.5 h-1.5 bg-accent rounded-full animate-pulse"
            title="点击标题查看站点信息"
          ></span>
        </div>
      </div>

      <!-- 用户菜单 / 登录按钮 -->
      <div class="relative">
        <template v-if="auth.isLoggedIn">
          <button
            @click="userMenuOpen = !userMenuOpen"
            class="flex items-center gap-2 px-3 h-9 border border-medical-200 bg-medical-50 hover:bg-medical-100 transition-colors rounded-sm"
          >
            <span class="w-2 h-2 bg-accent"></span>
            <span class="text-sm font-bold text-medical-900 max-w-[120px] truncate">{{ displayName }}</span>
            <ChevronDown class="w-3.5 h-3.5 text-medical-400" :class="{ 'rotate-180': userMenuOpen }" />
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
              v-if="userMenuOpen"
              class="absolute top-full right-0 mt-1 w-56 bg-white border border-medical-200 shadow-xl z-50 rounded-sm overflow-hidden"
            >
              <div class="p-2 border-b border-medical-100 bg-medical-50">
                <span class="text-xs font-mono text-medical-500 font-bold">ACCOUNT</span>
              </div>
              <button
                @click="openProfile"
                class="w-full flex items-center gap-2 px-3 py-2.5 hover:bg-medical-50 cursor-pointer transition-colors border-b border-medical-50 text-sm font-bold text-medical-800 text-left"
              >
                <UserRound class="w-4 h-4 text-medical-400" />
                个人空间
              </button>
              <button
                @click="go('/playlists')"
                class="w-full flex items-center gap-2 px-3 py-2.5 hover:bg-medical-50 cursor-pointer transition-colors border-b border-medical-50 text-sm font-bold text-medical-800 text-left"
              >
                <ListMusic class="w-4 h-4 text-medical-400" />
                我的歌单
              </button>
              <button
                v-if="auth.isSuperAdmin"
                @click="go('/admin')"
                class="w-full flex items-center gap-2 px-3 py-2.5 hover:bg-medical-50 cursor-pointer transition-colors border-b border-medical-50 text-sm font-bold text-medical-800 text-left"
              >
                <Shield class="w-4 h-4 text-medical-400" />
                管理后台
              </button>
              <button
                @click="handleLogout"
                class="w-full flex items-center gap-2 px-3 py-2.5 hover:bg-red-50 cursor-pointer transition-colors text-sm font-bold text-red-500 text-left"
              >
                <LogOut class="w-4 h-4" />
                登出
              </button>
            </div>
          </Transition>
        </template>
        <template v-else>
          <button
            @click="goLogin"
            class="flex items-center gap-2 px-5 h-9 bg-medical-900 text-white text-sm font-bold hover:bg-accent transition-colors chamfer-br"
          >
            <LogIn class="w-4 h-4" />
            LOGIN
          </button>
        </template>
      </div>
    </header>

    <!-- 2. 主体内容 -->
    <main class="relative z-10 flex-1 w-full max-w-6xl mx-auto px-4 md:px-6 py-10 md:py-16">
      <!-- Hero 区 -->
      <section class="relative mb-12 md:mb-16">
        <div
          class="absolute -top-10 left-1/2 -translate-x-1/2 whitespace-nowrap font-black text-[22vw] md:text-[200px] leading-none text-medical-900/[0.04] select-none pointer-events-none"
        >
          {{ uiStore.backWords }}
        </div>

        <div class="relative flex flex-col items-center text-center">
          <div class="flex items-center gap-3 text-xs font-mono text-medical-400 tracking-[0.3em] uppercase mb-4">
            <div class="w-2 h-2 bg-accent"></div>
            <span>SYSTEM ONLINE</span>
          </div>
          <h1 class="text-4xl md:text-7xl font-black tracking-tighter text-medical-900 mb-3">{{ uiStore.siteTitle }}</h1>
          <p class="font-mono text-xs md:text-sm text-accent tracking-[0.25em] uppercase mb-8">Select Channel To Begin</p>
        </div>
      </section>

      <!-- 频道大厅 / 个人播放室 入口（居中，首屏可见） -->
      <section class="mb-10 md:mb-14 flex justify-center">
        <div class="w-full max-w-2xl grid grid-cols-1 sm:grid-cols-2 gap-3">
          <button
            @click="openChannels"
            class="group relative bg-medical-900 text-white py-4 md:py-5 px-8 chamfer-br hover:bg-accent transition-colors flex items-center justify-center gap-4"
          >
            <span class="w-2 h-2 bg-accent group-hover:bg-white transition-colors"></span>
            <span class="font-mono text-sm md:text-base tracking-[0.3em] uppercase font-bold">ENTER CHANNEL LOBBY / 查看频道</span>
            <DoorOpen class="w-5 h-5 transition-transform duration-300 group-hover:translate-x-1" />
          </button>
          <button
            @click="openPersonalRoom"
            class="group relative bg-white border border-medical-200 text-medical-900 py-4 md:py-5 px-8 chamfer-br hover:border-accent hover:text-accent transition-colors flex items-center justify-center gap-4"
          >
            <span class="w-2 h-2 bg-accent group-hover:bg-white transition-colors"></span>
            <span class="font-mono text-sm md:text-base tracking-[0.3em] uppercase font-bold">MY ROOM / 个人播放室</span>
            <Headphones class="w-5 h-5 transition-transform duration-300 group-hover:translate-x-1" />
          </button>
        </div>
      </section>

      <!-- 关于 / 自叙 -->
      <section class="mb-12 md:mb-16">
        <div class="bg-white border border-medical-200 chamfer-br p-6 md:p-10 relative overflow-hidden">
          <!-- 四角修饰 -->
          <div class="absolute top-2 left-2 w-3 h-3 border-t border-l border-accent"></div>
          <div class="absolute top-2 right-2 w-3 h-3 border-t border-r border-accent"></div>
          <div class="absolute bottom-2 left-2 w-3 h-3 border-b border-l border-accent"></div>
          <div class="absolute bottom-2 right-2 w-3 h-3 border-b border-r border-accent"></div>

          <div class="flex items-center gap-3 mb-5">
            <span class="font-mono text-xs text-medical-400 tracking-[0.25em]">ABOUT / 关于本站</span>
            <span class="w-1.5 h-1.5 bg-accent"></span>
          </div>

          <!-- 关于介绍：全部内容由管理后台 → 站点品牌 → 站点简介（支持 HTML）自定义 -->
          <div class="text-sm md:text-base text-medical-700 leading-7 md:leading-8" v-html="aboutHtml"></div>
        </div>
      </section>

      <!-- 频道列表弹窗 -->
      <div
        v-if="showChannels"
        class="fixed inset-0 z-[150] flex items-center justify-center bg-medical-900/60 backdrop-blur-sm p-4"
        @click.self="closeChannels"
      >
        <div class="bg-white border border-medical-200 shadow-2xl chamfer-br w-full max-w-4xl max-h-[85vh] flex flex-col overflow-hidden">
          <!-- 弹窗头部 -->
          <div class="p-3 bg-medical-900 text-white flex justify-between items-center flex-shrink-0">
            <div class="flex items-center gap-2">
              <div class="w-2 h-2 bg-accent"></div>
              <span class="text-xs font-bold uppercase tracking-widest font-mono">CHANNELS / 频道大厅</span>
              <span class="text-xs font-mono text-white/50">{{ channelStore.channels.length }} AVAILABLE</span>
            </div>
            <button @click="closeChannels" class="text-white hover:text-accent transition-colors">
              <X class="w-5 h-5" />
            </button>
          </div>

          <!-- 弹窗内容（可滚动） -->
          <div class="flex-1 overflow-y-auto p-4 md:p-6">
            <div v-if="channelStore.isLoading" class="text-center py-16 text-medical-400 font-mono text-sm">> LOADING CHANNELS...</div>

            <div v-else-if="channelStore.channels.length === 0" class="text-center py-10 md:py-16">
              <div class="text-2xl md:text-3xl font-black text-medical-900 tracking-tighter mb-3">NO CHANNELS AVAILABLE</div>
              <p class="font-mono text-xs text-medical-400 mb-6 tracking-wider">
                <template v-if="auth.isSuperAdmin">
                  创建第一个频道，开启音乐派对
                </template>
                <template v-else>
                  暂无可用频道，请联系管理员创建
                </template>
              </p>
              <button
                v-if="auth.isSuperAdmin"
                @click="go('/admin')"
                class="px-8 py-3 bg-accent text-white font-bold text-sm hover:bg-accent-hover transition-colors chamfer-br"
              >
                CREATE CHANNEL
              </button>
            </div>

            <div v-else class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4 md:gap-6">
              <button
                v-for="ch in channels"
                :key="ch.id"
                @click="handleChannelClick(ch)"
                class="group bg-white border border-medical-200 hover:border-accent hover:shadow-lg transition-all duration-300 chamfer-br p-5 text-left flex flex-col gap-3 relative"
              >
                <!-- 四角修饰 -->
                <div class="absolute top-2 left-2 w-2 h-2 border-t border-l border-medical-300 group-hover:border-accent transition-colors"></div>
                <div class="absolute top-2 right-2 w-2 h-2 border-t border-r border-medical-300 group-hover:border-accent transition-colors"></div>
                <div class="absolute bottom-2 left-2 w-2 h-2 border-b border-l border-medical-300 group-hover:border-accent transition-colors"></div>
                <div class="absolute bottom-2 right-2 w-2 h-2 border-b border-r border-medical-300 group-hover:border-accent transition-colors"></div>

                <div class="flex items-start justify-between gap-2">
                  <h3 class="text-lg font-black text-medical-900 tracking-tighter truncate group-hover:text-accent transition-colors">
                    {{ ch.name }}
                  </h3>
                  <div class="flex items-center gap-1.5 flex-shrink-0">
                    <span v-if="ch.isAdmin" class="px-1.5 py-0.5 bg-medical-900 text-white text-[11px] font-mono font-bold tracking-widest">ADMIN</span>
                    <Lock v-if="ch.hasPassword" class="w-4 h-4 text-medical-400" title="PASSWORD" />
                    <UserPlus v-else-if="ch.joinPermission === 'INVITE_ONLY'" class="w-4 h-4 text-medical-400" title="INVITE_ONLY" />
                    <EyeOff v-else-if="ch.joinPermission === 'HIDDEN'" class="w-4 h-4 text-medical-400" title="HIDDEN" />
                  </div>
                </div>

                <p class="text-sm text-medical-500 leading-relaxed line-clamp-2 flex-1">{{ ch.description || '暂无描述' }}</p>

                <div class="flex items-center justify-between border-t border-medical-100 pt-3">
                  <span class="text-xs font-mono text-medical-400 tracking-wider">{{ ch.onlineCount || 0 }} ONLINE</span>
                  <span class="text-xs font-mono text-accent opacity-0 group-hover:opacity-100 transition-opacity tracking-widest">ENTER &gt;</span>
                </div>
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- 信息页提示条 -->
      <div v-if="uiStore.hasInfoPage" class="mt-12">
        <button
          @click="uiStore.showInfo = true"
          class="w-full py-3 bg-white/60 backdrop-blur-sm border border-medical-200 text-xs font-mono text-medical-500 hover:text-accent hover:border-accent transition-colors chamfer-br"
        >
          &gt; 点击标题查看站点信息 <span class="text-accent">[INFO]</span>
        </button>
      </div>

      <!-- 底部 -->
      <footer class="mt-12 border-t border-medical-200 pt-6 flex flex-col md:flex-row items-center justify-between gap-2">
        <span class="text-xs font-mono text-medical-400 tracking-wider">© {{ year }} {{ uiStore.siteTitle }} · by {{ uiStore.authorName }}</span>
        <span class="text-xs font-mono text-medical-300 tracking-[0.25em]">POWERED BY MUSIC PARTY</span>
      </footer>
    </main>

    <!-- 密码弹窗 -->
    <div
      v-if="showPasswordInput"
      class="fixed inset-0 z-[160] flex items-center justify-center bg-medical-900/60 backdrop-blur-sm p-4"
      @click.self="closePasswordInput"
    >
      <div class="bg-white p-6 shadow-2xl border border-medical-200 w-full max-w-sm chamfer-br">
        <div class="mb-4">
          <h3 class="text-lg font-black text-medical-900 tracking-tighter">频道密码</h3>
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
          :disabled="submitting"
          class="w-full bg-medical-900 text-white font-bold py-2 hover:bg-accent transition-colors chamfer-br disabled:opacity-50"
        >
          JOIN
        </button>
      </div>
    </div>

    <InfoModal />
    <ToastNotification />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ChevronDown, Lock, UserPlus, EyeOff, ListMusic, Shield, LogOut, LogIn, UserRound, DoorOpen, Headphones, X } from 'lucide-vue-next'
import { useUiStore } from '../stores/ui'
import { useAuthStore } from '../stores/auth'
import { useChannelStore } from '../stores/channel'
import { useToast } from '../composables/useToast'
import client from '../api/client'
import { sanitizeHtml } from '../utils/sanitize'
import InfoModal from '../components/InfoModal.vue'
import ToastNotification from '../components/ToastNotification.vue'

const router = useRouter()
const uiStore = useUiStore()
const auth = useAuthStore()
const channelStore = useChannelStore()
const { error, success, info } = useToast()

const year = new Date().getFullYear()

const displayName = computed(() => auth.user?.username || 'GUEST')

const userMenuOpen = ref(false)
const showPasswordInput = ref(false)
const passwordInput = ref('')
const pendingChannel = ref(null)
const submitting = ref(false)

// 首页 ABOUT 介绍文字：管理后台可编辑；未设置时用内置默认（支持站点标题/作者名占位）
const DEFAULT_ABOUT = `{{siteTitle}} 是一个多人实时在线听歌平台：
和朋友在同一个虚拟频道里，搜索、点播、同步收听同一首歌——无论大家相隔多远，听到的都是同一秒的旋律。

频道就像一个个小小的音乐房间，每个房间都有独立的队列、播放器和聊天。想安静就找一个角落，想热闹就喊朋友进来——戴上耳机，把耳朵借给彼此。

—— 作者自述 ——
这个站最初只是我的一点小想法：想和朋友一起听歌，却找不到顺手的工具。于是有了它——一个可以自己部署、自己掌控的听歌小站。它由 {{authorName}} 搭建维护，借鉴了开源社区 MusicParty 的创意并做了大量改造。技术栈是 Vue + Spring Boot + PostgreSQL，代码开源，欢迎交流。

如果这里有一首歌曾让你停下脚步，那它就完成了使命。戴上耳机，开始吧 🎧`

const aboutHtml = computed(() => {
  const text = (uiStore.aboutText && uiStore.aboutText.trim())
    ? uiStore.aboutText
    : DEFAULT_ABOUT
        .replace('{{siteTitle}}', uiStore.siteTitle)
        .replace('{{authorName}}', uiStore.authorName)
  // 换行转 <br/>：纯文本换行生效；HTML 标签（<b>/<i>/<a>/<p> 等）原样保留
  // M6：渲染前经过白名单消毒（该内容可由管理员在后台写入，防存储型 XSS）
  return sanitizeHtml(text.replace(/\n/g, '<br/>'))
})

// 频道大厅：默认收起，点击按钮弹出频道列表弹窗
const showChannels = ref(false)

async function openChannels() {
  // 游客（未登录）无使用权限：只能浏览首页，进入频道大厅需先登录
  if (!auth.isLoggedIn) {
    window.location.href = auth.getLoginUrl('/')
    return
  }
  showChannels.value = true
  // 每次打开都刷新，保证在线人数等数据最新
  await loadChannels()
}

function openPersonalRoom() {
  // 个人播放室尚未开发
  if (!auth.isLoggedIn) {
    window.location.href = auth.getLoginUrl('/')
    return
  }
  info('个人播放室尚未开放，敬请期待')
}

function closeChannels() {
  showChannels.value = false
}

const publicChannels = ref([])

// 频道数据源：未登录用公开 API，已登录用完整 API（含权限信息）
const channels = computed(() => auth.isLoggedIn ? channelStore.channels : publicChannels.value)

function go(path) {
  userMenuOpen.value = false
  router.push(path)
}

function openProfile() {
  userMenuOpen.value = false
  // MusicParty 个人空间（本站页面），不再跳转认证中心
  router.push('/profile')
}

function goLogin() {
  window.location.href = auth.getLoginUrl('/')
}

async function loadChannels() {
  if (auth.isLoggedIn) {
    await channelStore.fetchChannels()
  } else {
    try {
      const data = await client.get('/api/public/channels')
      publicChannels.value = Array.isArray(data) ? data : []
    } catch (e) {
      console.error('Failed to fetch public channels', e)
    }
  }
}

function handleLogout() {
  userMenuOpen.value = false
  auth.logout()
  closeChannels()
  channelStore.clearCurrentChannel()
  publicChannels.value = []
  loadChannels()
}

async function handleChannelClick(ch) {
  // 未登录：跳转认证中心登录，登录后回跳直达该频道
  if (!auth.isLoggedIn) {
    window.location.href = auth.getLoginUrl('/room?ch=' + ch.id)
    return
  }
  // 已在当前频道：直接回到房间，而不是静默无响应
  if (ch.id === channelStore.currentChannelId) {
    closeChannels()
    router.push('/room')
    return
  }
  if ((ch.joinPermission === 'INVITE_ONLY' || ch.joinPermission === 'HIDDEN') && !ch.isMember && !ch.isAdmin) {
    error(ch.joinPermission === 'HIDDEN' ? '无权访问该频道' : '该频道仅限受邀成员加入')
    return
  }
  if (ch.hasPassword) {
    pendingChannel.value = ch
    passwordInput.value = ''
    showPasswordInput.value = true
    return
  }
  await switchTo(ch)
}

function closePasswordInput() {
  showPasswordInput.value = false
  pendingChannel.value = null
  passwordInput.value = ''
}

async function switchTo(ch) {
  try {
    await channelStore.switchChannel(ch)
    success('已进入频道 ' + ch.name)
    router.push('/room')
  } catch (e) {
    error(e?.response?.data?.message || e.message || '加入频道失败')
    console.error('Failed to join channel', e)
  }
}

async function confirmPassword() {
  if (!pendingChannel.value || submitting.value) return
  submitting.value = true
  try {
    await channelStore.switchChannel(pendingChannel.value, passwordInput.value)
    closePasswordInput()
    success('已进入频道 ' + pendingChannel.value.name)
    router.push('/room')
  } catch (e) {
    error(e?.response?.data?.message || e.message || '加入频道失败')
    console.error('Failed to join channel', e)
  } finally {
    submitting.value = false
  }
}

function onKeydown(e) {
  if (e.key === 'Escape') {
    closePasswordInput()
    closeChannels()
  }
}

onMounted(() => {
  uiStore.fetchConfig()
  document.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown)
})
</script>
