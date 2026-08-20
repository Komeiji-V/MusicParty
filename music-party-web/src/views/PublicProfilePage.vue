<template>
  <div class="min-h-screen bg-medical-50 font-sans flex flex-col relative overflow-x-hidden">
    <!-- 背景装饰 -->
    <div class="fixed inset-0 z-0 pointer-events-none">
      <div class="absolute inset-0 bg-[linear-gradient(rgba(17,24,39,0.03)_1px,transparent_1px),linear-gradient(90deg,rgba(17,24,39,0.03)_1px,transparent_1px)] bg-[size:32px_32px]"></div>
      <div class="absolute inset-0 bg-[repeating-linear-gradient(45deg,transparent,transparent_20px,rgba(0,0,0,0.02)_20px,rgba(0,0,0,0.02)_21px)]"></div>
    </div>

    <!-- 顶部栏 -->
    <header class="relative z-20 h-14 bg-white border-b border-medical-200 flex justify-between items-center px-4 md:px-6 flex-shrink-0">
      <div class="flex items-center gap-3 min-w-0">
        <div class="flex items-center gap-2 flex-shrink-0">
          <div class="w-2.5 h-2.5 md:w-3 md:h-3 bg-accent flex-shrink-0"></div>
          <h1 class="font-black text-base md:text-xl tracking-tighter text-medical-900 truncate">PUBLIC PROFILE / 公开主页</h1>
        </div>
      </div>

      <button
        v-if="loaded"
        @click="copyLink"
        class="flex items-center gap-2 px-4 py-2 border border-medical-200 bg-medical-50 hover:border-accent hover:text-accent text-medical-700 text-sm font-bold transition-colors rounded-sm"
      >
        <Share2 class="w-4 h-4" /> <span class="hidden sm:inline">复制链接</span>
      </button>
    </header>

    <main class="relative z-10 flex-1 w-full max-w-4xl mx-auto px-4 md:px-6 py-8 md:py-12">
      <!-- 加载中 -->
      <div v-if="loading" class="text-center py-24 font-mono text-sm text-medical-400 tracking-widest">> LOADING...</div>

      <!-- 加载失败（网络等） -->
      <div v-else-if="loadError" class="bg-white border border-medical-200 chamfer-br p-12 text-center">
        <div class="text-2xl font-black text-medical-900 tracking-tighter mb-2">LOAD FAILED</div>
        <p class="font-mono text-xs text-medical-400 tracking-wider mb-6">加载失败，请稍后重试</p>
        <button
          @click="load"
          class="px-6 py-2.5 bg-accent text-white text-sm font-bold hover:bg-accent-hover transition-colors chamfer-br"
        >重试</button>
      </div>

      <!-- 用户不存在 -->
      <div v-else-if="notFound" class="bg-white border border-medical-200 chamfer-br p-12 text-center">
        <div class="text-2xl font-black text-medical-900 tracking-tighter mb-2">USER NOT FOUND</div>
        <p class="font-mono text-xs text-medical-400 tracking-wider mb-2">用户不存在，请检查链接是否正确</p>
        <p class="text-xs text-medical-500 leading-6 mb-6 max-w-md mx-auto">
          公开主页使用不可变的数字 ID 访问（如 <span class="font-mono text-accent">/u/95</span>），
          也可以从频道成员列表或个人空间页的「预览主页」按钮打开正确链接。
        </p>
        <button
          @click="go('/')"
          class="px-6 py-2.5 bg-accent text-white text-sm font-bold hover:bg-accent-hover transition-colors chamfer-br"
        >返回首页</button>
      </div>

      <template v-else>
        <!-- 用户信息卡 -->
        <section class="mb-8">
          <div class="bg-white border border-medical-200 chamfer-br p-6 md:p-8 relative overflow-hidden">
            <div class="absolute top-2 left-2 w-3 h-3 border-t border-l border-accent"></div>
            <div class="absolute top-2 right-2 w-3 h-3 border-t border-r border-accent"></div>
            <div class="absolute bottom-2 left-2 w-3 h-3 border-b border-l border-accent"></div>
            <div class="absolute bottom-2 right-2 w-3 h-3 border-b border-r border-accent"></div>

            <div class="flex flex-col sm:flex-row items-center sm:items-start gap-6">
              <!-- 头像占位 -->
              <div class="w-20 h-20 md:w-24 md:h-24 bg-medical-900 text-white flex items-center justify-center chamfer-br flex-shrink-0 select-none">
                <span class="text-3xl md:text-4xl font-black tracking-tighter">{{ avatarLetter }}</span>
              </div>

              <div class="flex-1 min-w-0 text-center sm:text-left">
                <div class="flex items-center justify-center sm:justify-start gap-2 flex-wrap">
                  <h2 class="text-2xl md:text-3xl font-black text-medical-900 tracking-tighter">{{ displayName || identifier }}</h2>
                </div>
                <p class="text-xs font-mono text-medical-300 mt-2 tracking-wider">
                  <span class="text-green-600">PUBLIC PROFILE</span>
                </p>
                <!-- 获得的全部称号（佩戴中的带标记） -->
                <div v-if="allTitles.length > 0" class="flex flex-wrap items-center gap-1.5 mt-2">
                  <span
                    v-for="t in allTitles"
                    :key="t.title"
                    class="px-2.5 py-1 text-xs leading-none font-bold rounded-[2px]"
                    :style="{ backgroundColor: t.color || '#ff5722', color: titleTextColor(t.color) }"
                    :class="{ 'ring-2 ring-offset-1 ring-medical-900/30': t.current }"
                  >{{ t.title }}<span v-if="t.current" class="ml-1 text-[11px] font-mono tracking-widest opacity-90">佩戴中</span></span>
                </div>
              </div>
            </div>

            <!-- 公开展示：左专辑大图 + 右专辑歌曲列表；无专辑但有歌曲/歌词时只显示喜爱歌曲区 -->
            <div v-if="firstAlbum || songWidgets.length" class="mt-5 border-t border-medical-100 pt-4">
              <FeaturedDisplay :album="firstAlbum" :album-data="albumData" :song-widgets="songWidgets" :loading="albumSongsLoading" />
            </div>

            <div v-else class="mt-5 border-t border-medical-100 pt-4">
              <div class="text-xs font-mono text-medical-400 tracking-widest mb-2">FEATURED / 个人展示</div>
              <p class="text-xs font-mono text-medical-400">该用户还没有设置个人展示</p>
            </div>
          </div>
        </section>

        <!-- 统计 -->
        <section class="grid grid-cols-2 gap-3 md:gap-4 mb-8">
          <div class="bg-white border border-medical-200 p-4 md:p-5 chamfer-br">
            <div class="text-2xl md:text-3xl font-black text-medical-900">{{ stats.likes }}</div>
            <div class="text-xs font-mono text-medical-400 mt-1 tracking-wider">收到的赞 / LIKES</div>
          </div>
          <div class="bg-white border border-medical-200 p-4 md:p-5 chamfer-br">
            <div class="text-2xl md:text-3xl font-black text-medical-900">{{ publicPlaylists.length }}</div>
            <div class="text-xs font-mono text-medical-400 mt-1 tracking-wider">公开歌单 / PLAYLISTS</div>
          </div>
        </section>

        <!-- 公开歌单 -->
        <section>
          <div class="flex items-center gap-3 mb-4 border-b border-medical-200 pb-3">
            <span class="font-mono text-xs text-medical-400 tracking-[0.25em]">PUBLIC PLAYLISTS / 公开歌单</span>
            <span class="w-1.5 h-1.5 bg-accent"></span>
          </div>

          <div v-if="publicPlaylists.length === 0" class="bg-white border border-medical-200 chamfer-br p-8 text-center">
            <div class="text-base font-black text-medical-900 tracking-tighter mb-2">NO PUBLIC PLAYLISTS</div>
            <p class="font-mono text-xs text-medical-400 tracking-wider">该用户还没有公开歌单</p>
          </div>

          <div v-else class="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div
              v-for="pl in publicPlaylists"
              :key="pl.id"
              class="bg-white border border-medical-200 chamfer-br p-4 group"
            >
              <div class="flex items-center gap-3">
                <div class="w-14 h-14 bg-medical-100 flex items-center justify-center flex-shrink-0 relative overflow-hidden">
                  <img v-if="pl.coverUrl" :src="pl.coverUrl" class="w-full h-full object-cover" alt="" />
                  <ListMusic v-else class="w-5 h-5 text-medical-400" />
                </div>
                <div class="flex-1 min-w-0">
                  <div class="text-sm font-bold text-medical-900 truncate group-hover:text-accent transition-colors">{{ pl.name }}</div>
                  <div class="flex items-center gap-1.5 mt-1 flex-wrap">
                    <span v-if="pl.category" class="px-1.5 py-0.5 text-xs font-mono font-bold text-accent border border-accent/40 bg-accent/10 rounded-sm">{{ pl.category }}</span>
                    <span class="px-1.5 py-0.5 text-xs font-mono font-bold text-medical-500 bg-medical-100 rounded-sm">{{ pl.songCount || pl.itemCount || 0 }} TRACKS</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>
      </template>

      <!-- 底部 -->
      <footer class="mt-12 border-t border-medical-200 pt-6 flex flex-col md:flex-row items-center justify-between gap-2">
        <span class="text-xs font-mono text-medical-400 tracking-wider">© {{ year }} {{ uiStore.siteTitle }} · by {{ uiStore.authorName }}</span>
        <span class="text-xs font-mono text-medical-300 tracking-[0.25em]">POWERED BY MUSIC PARTY</span>
      </footer>
    </main>

    <ToastNotification />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Share2, ListMusic } from 'lucide-vue-next'
import { useAuthStore } from '../stores/auth'
import { useUiStore } from '../stores/ui'
import client from '../api/client'
import { useToast } from '../composables/useToast'
import ToastNotification from '../components/ToastNotification.vue'
import { titleTextColor } from '../utils/titleColor'
import FeaturedDisplay from '../components/FeaturedDisplay.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const uiStore = useUiStore()
const { success, error } = useToast()

const year = new Date().getFullYear()
// 路由参数：不可变 authUid（纯数字，推荐）或 username（兼容旧链接），后端统一解析
const identifier = computed(() => route.params.authUid || '')
// 页面展示用真实用户名（来自 API，随 auth-center 改名同步）
const displayName = ref('')
const loading = ref(true)
const notFound = ref(false)
const loadError = ref(false)
const loaded = ref(false)
const publicPlaylists = ref([])
const allTitles = ref([])
const stats = ref({ likes: 0 })
const featured = reactive({ widgets: [] })
const albumData = ref({ id: '', name: '', songs: [] })
const albumSongsLoading = ref(false)
const firstAlbum = computed(() => featured.widgets.find(w => w.kind === 'album') || null)
const songWidgets = computed(() => featured.widgets.filter(w => w.kind === 'song' || w.kind === 'lyric'))

const avatarLetter = computed(() => (displayName.value || identifier.value || '?').charAt(0).toUpperCase())

async function loadAlbumSongs() {
  const album = firstAlbum.value
  if (!album) {
    albumData.value = { id: '', name: '', songs: [] }
    return
  }
  albumSongsLoading.value = true
  try {
    const platform = album.data?.platform || 'netease'
    const res = await client.get(`/api/public/album-songs/${platform}/${encodeURIComponent(album.data?.name || '')}`)
    albumData.value = (res && typeof res === 'object' && Array.isArray(res.songs)) ? res : { id: '', name: '', songs: [] }
  } catch (e) {
    albumData.value = { id: '', name: '', songs: [] }
  } finally {
    albumSongsLoading.value = false
  }
}

async function load() {
  loading.value = true
  notFound.value = false
  loadError.value = false
  let f = null
  try {
    f = await client.get(`/api/public/users/${encodeURIComponent(identifier.value)}/featured`)
  } catch (e) {
    if (e.response?.status === 404) {
      notFound.value = true
    } else {
      loadError.value = true
    }
    loading.value = false
    return
  }
  displayName.value = f?.username || identifier.value
  try {
    const [pl, likeData, titlesData] = await Promise.all([
      client.get(`/api/public/users/${encodeURIComponent(identifier.value)}/playlists`).catch(() => []),
      client.get(`/api/public/users/${encodeURIComponent(identifier.value)}/likes`).catch(() => ({ likes: 0 })),
      client.get(`/api/public/users/${encodeURIComponent(identifier.value)}/titles`).catch(() => ({ titles: [] }))
    ])
    featured.widgets = Array.isArray(f.widgets) ? f.widgets : []
    publicPlaylists.value = Array.isArray(pl) ? pl : []
    allTitles.value = Array.isArray(titlesData?.titles) ? titlesData.titles : []
    stats.value.likes = Number(likeData?.likes) || 0
    loaded.value = true
    loadAlbumSongs()
  } catch (e) {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

async function copyLink() {
  try {
    await navigator.clipboard.writeText(window.location.href)
    success('链接已复制，可分享给朋友')
  } catch (e) {
    error('复制失败，请手动复制地址栏链接')
  }
}

function go(path) {
  router.push(path)
}

onMounted(async () => {
  uiStore.fetchConfig()
  if (!auth.user) await auth.fetchMe().catch(() => {})
  load()
})
</script>
