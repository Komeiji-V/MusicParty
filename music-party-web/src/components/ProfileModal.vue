<template>
  <div class="fixed inset-0 z-[90] flex items-center justify-center bg-medical-900/70 backdrop-blur-sm p-4" @click.self="emit('close')">
    <div class="w-full max-w-2xl bg-medical-50 shadow-2xl border border-medical-200 chamfer-br max-h-[85vh] flex flex-col overflow-hidden">
      <!-- 头部 -->
      <div class="p-3 bg-medical-900 text-white flex justify-between items-center flex-shrink-0">
        <div class="flex items-center gap-2">
          <div class="w-2 h-2 bg-accent"></div>
          <span class="text-xs font-bold uppercase tracking-widest font-mono">MY SPACE / 个人空间</span>
        </div>
        <button @click="emit('close')" class="text-white hover:text-accent transition-colors">
          <X class="w-5 h-5" />
        </button>
      </div>

      <!-- 内容区 -->
      <div class="flex-1 overflow-y-auto p-4 md:p-6">
        <!-- 用户信息卡 -->
        <div class="bg-white border border-medical-200 chamfer-br p-5 md:p-6 relative overflow-hidden mb-4">
          <div class="absolute top-2 left-2 w-3 h-3 border-t border-l border-accent"></div>
          <div class="absolute top-2 right-2 w-3 h-3 border-t border-r border-accent"></div>
          <div class="absolute bottom-2 left-2 w-3 h-3 border-b border-l border-accent"></div>
          <div class="absolute bottom-2 right-2 w-3 h-3 border-b border-r border-accent"></div>

          <div class="flex items-center gap-4">
            <div class="w-16 h-16 md:w-20 md:h-20 bg-medical-900 text-white flex items-center justify-center chamfer-br flex-shrink-0 select-none">
              <span class="text-2xl md:text-3xl font-black tracking-tighter">{{ avatarLetter }}</span>
            </div>
            <div class="flex-1 min-w-0">
              <div class="flex items-center gap-2 flex-wrap">
                <h2 class="text-xl md:text-2xl font-black text-medical-900 tracking-tighter">{{ auth.user?.username || 'GUEST' }}</h2>
                <span
                  class="px-2 py-0.5 text-xs font-mono font-bold tracking-widest"
                  :class="isSuperAdmin ? 'bg-accent text-white' : 'bg-medical-100 text-medical-500'"
                >{{ isSuperAdmin ? 'SUPER ADMIN' : 'MEMBER' }}</span>
              </div>
              <p class="text-xs font-mono text-medical-400 mt-1 tracking-wider break-all">{{ auth.user?.email || '—' }}</p>
              <p class="text-xs font-mono text-medical-300 mt-1 tracking-wider">
                <span v-if="auth.user?.emailVerified" class="text-green-600">EMAIL VERIFIED</span>
                <span v-else class="text-orange-500">EMAIL UNVERIFIED</span>
                <span class="mx-2">·</span>
                ID: {{ auth.user?.id ?? '—' }}
              </p>
            </div>
            <!-- 快速跳转设置页 -->
            <button
              @click="goSettings"
              class="self-start flex-shrink-0 px-3 py-2 border border-accent text-accent text-xs font-bold hover:bg-accent hover:text-white transition-colors chamfer-br"
              title="编辑主页设置（Cookie / 展示 / 歌单）"
            >
              <Settings class="w-3.5 h-3.5 inline mr-1" />主页设置
            </button>
          </div>
        </div>

        <!-- 公开展示：最喜欢的歌曲/专辑 + 歌词 -->
        <div v-if="featured.song || featured.album || featured.lyric" class="bg-white border border-medical-200 chamfer-br overflow-hidden mb-4">
          <div class="p-2.5 bg-medical-900 text-white">
            <span class="text-xs font-mono font-bold tracking-widest uppercase">个人展示</span>
          </div>
          <div class="p-3">
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-2 mb-2">
              <div v-if="featured.song" class="flex items-center gap-3 bg-medical-50 border border-medical-200 p-2.5">
                <div class="w-14 h-14 bg-medical-200 flex-shrink-0 overflow-hidden flex items-center justify-center">
                  <img v-if="featured.song.coverUrl" :src="featured.song.coverUrl" class="w-full h-full object-cover" alt="" />
                  <Music2 v-else class="w-5 h-5 text-medical-400" />
                </div>
                <div class="min-w-0">
                  <div class="text-xs font-mono text-accent">最喜欢的歌曲</div>
                  <div class="text-sm font-bold text-medical-900 truncate">{{ featured.song.name }}</div>
                  <div class="text-xs text-medical-500 truncate">{{ (featured.song.artists || []).join(' / ') }}</div>
                </div>
              </div>
              <div v-if="featured.album" class="flex items-center gap-3 bg-medical-50 border border-medical-200 p-2.5">
                <div class="w-14 h-14 bg-medical-200 flex-shrink-0 overflow-hidden flex items-center justify-center">
                  <img v-if="featured.album.coverUrl" :src="featured.album.coverUrl" class="w-full h-full object-cover" alt="" />
                  <Disc3 v-else class="w-5 h-5 text-medical-400" />
                </div>
                <div class="min-w-0">
                  <div class="text-xs font-mono text-accent">最喜欢的专辑</div>
                  <div class="text-sm font-bold text-medical-900 truncate">{{ featured.album.name }}</div>
                </div>
              </div>
            </div>
            <div v-if="featured.lyric" class="bg-medical-900/5 border-l-2 border-accent px-3 py-2">
              <div class="text-xs font-mono text-medical-400 mb-1">最喜欢的一段歌词</div>
              <div class="text-center text-sm text-medical-700 leading-7 whitespace-pre-line italic">{{ featured.lyric }}</div>
            </div>
          </div>
        </div>

        <!-- 统计 -->
        <div class="grid grid-cols-2 gap-3 mb-4">
          <div class="bg-white border border-medical-200 p-4 chamfer-br">
            <div class="text-2xl font-black text-medical-900">{{ stats.likes }}</div>
            <div class="text-xs font-mono text-medical-400 mt-1 tracking-wider">收到的赞 / LIKES</div>
          </div>
          <div class="bg-white border border-medical-200 p-4 chamfer-br">
            <div class="text-2xl font-black text-medical-900">{{ playlists.length }}</div>
            <div class="text-xs font-mono text-medical-400 mt-1 tracking-wider">歌单 / PLAYLISTS</div>
          </div>
        </div>

        <!-- 我的直播流链接（所有登录用户可用） -->
        <div class="bg-white border border-medical-200 chamfer-br overflow-hidden mb-4">
          <div class="p-2.5 bg-medical-900 text-white flex justify-between items-center">
            <span class="text-xs font-mono font-bold tracking-widest uppercase">直播流</span>
            <span class="text-xs font-mono text-white/50">我的收听链接</span>
          </div>
          <div class="p-3">
            <button
              @click="fetchStreamLink"
              class="w-full py-2 bg-medical-900 text-white text-sm font-bold hover:bg-accent transition-colors chamfer-br"
            >获取我的直播流链接</button>
            <div v-if="streamLink" class="mt-2">
              <div class="flex gap-2">
                <input :value="streamLink" readonly class="flex-1 border border-medical-200 p-2 text-xs font-mono bg-medical-50" />
                <button @click="copyLink" class="px-3 py-2 bg-medical-100 text-sm font-bold hover:bg-medical-200 transition-colors">复制</button>
              </div>
              <p class="text-xs font-mono text-medical-400 mt-1">链接 24 小时有效（闲置 4 小时失效）；需频道管理员开启直播流</p>
            </div>
          </div>
        </div>

        <!-- 我的称号 -->
        <div class="bg-white border border-medical-200 chamfer-br overflow-hidden mb-4">
          <div class="p-2.5 bg-medical-900 text-white flex justify-between items-center">
            <span class="text-xs font-mono font-bold tracking-widest uppercase">称号（展示时最多一个）</span>
          </div>
          <div class="p-3">
            <div v-if="titles.length === 0" class="text-xs font-mono text-medical-400 py-1">
              暂无称号。贡献音源 Cookie 并通过审核可获得「音源提供者」称号
            </div>
            <div v-else class="flex flex-wrap gap-2">
              <button
                v-for="t in titles" :key="t.title"
                @click="chooseTitle(t.title)"
                class="px-3 py-1.5 text-sm leading-none font-bold rounded-[3px] transition-opacity"
                :style="{ backgroundColor: t.color || '#ff5722', color: titleTextColor(t.color), opacity: currentTitle && currentTitle !== t.title ? 0.45 : 1 }"
                :title="currentTitle === t.title ? '展示中' : '点击选用'"
              >{{ t.title }}</button>
              <button
                v-if="currentTitle"
                @click="chooseTitle('')"
                class="px-3 py-1.5 text-sm leading-none font-bold border border-medical-200 text-medical-400 hover:text-red-500 transition-colors"
              >取消展示</button>
            </div>
          </div>
        </div>

        <!-- 我的歌单 -->
        <div class="flex items-center justify-between gap-3 mb-3 border-b border-medical-200 pb-3">
          <div class="flex items-center gap-2">
            <span class="font-mono text-xs text-medical-400 tracking-[0.25em]">MY PLAYLISTS / 我的歌单</span>
            <span class="w-1.5 h-1.5 bg-accent"></span>
          </div>
          <button
            @click="managePlaylists"
            class="text-xs font-mono text-accent hover:text-accent-hover tracking-widest flex items-center gap-1"
          >
            管理歌单 <ArrowRight class="w-3.5 h-3.5" />
          </button>
        </div>

        <div v-if="loading" class="text-center py-8 text-medical-400 font-mono text-sm">> LOADING...</div>

        <div v-else-if="playlists.length === 0" class="bg-white border border-medical-200 chamfer-br p-8 text-center">
          <div class="text-base font-black text-medical-900 tracking-tighter mb-2">NO PLAYLISTS YET</div>
          <p class="font-mono text-xs text-medical-400 mb-4 tracking-wider">还没有歌单，去创建一个吧</p>
          <button
            @click="managePlaylists"
            class="px-5 py-2 bg-accent text-white font-bold text-sm hover:bg-accent-hover transition-colors chamfer-br"
          >CREATE PLAYLIST</button>
        </div>

        <div v-else class="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div
            v-for="pl in playlists"
            :key="pl.id"
            @click="managePlaylists"
            class="bg-white border border-medical-200 hover:border-accent hover:shadow-lg transition-all duration-300 chamfer-br p-3.5 cursor-pointer group"
          >
            <div class="flex items-center gap-3">
              <div class="w-11 h-11 bg-medical-100 flex items-center justify-center flex-shrink-0 relative overflow-hidden">
                <img v-if="pl.coverUrl" :src="pl.coverUrl" class="w-full h-full object-cover" alt="" />
                <ListMusic v-else class="w-5 h-5 text-medical-400" />
              </div>
              <div class="flex-1 min-w-0">
                <div class="text-sm font-bold text-medical-900 truncate group-hover:text-accent transition-colors">{{ pl.name }}</div>
                <div class="flex items-center gap-1.5 mt-1 flex-wrap">
                  <span v-if="pl.category" class="px-1.5 py-0.5 text-xs font-mono font-bold text-accent border border-accent/40 bg-accent/10 rounded-sm">{{ pl.category }}</span>
                  <span class="px-1.5 py-0.5 text-xs font-mono font-bold text-medical-500 bg-medical-100 rounded-sm">{{ pl.itemCount }} TRACKS</span>
                  <span v-if="pl.isPublic" class="px-1.5 py-0.5 text-xs font-mono font-bold text-green-700 bg-green-100 rounded-sm">PUBLIC</span>
                </div>
              </div>
              <ArrowRight class="w-4 h-4 text-medical-300 group-hover:text-accent transition-colors flex-shrink-0" />
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { X, ArrowRight, ListMusic, Music2, Disc3, Settings } from 'lucide-vue-next'
import { useAuthStore } from '../stores/auth'
import { usePlaylistStore } from '../stores/playlist'
import { useToast } from '../composables/useToast'
import client from '../api/client'
import { titleTextColor } from '../utils/titleColor'

const emit = defineEmits(['close', 'playlists'])
const router = useRouter()

const auth = useAuthStore()
const playlistStore = usePlaylistStore()
const { success, error } = useToast()

const loading = ref(false)
const playlists = ref([])
const stats = ref({ likes: 0 })
const streamLink = ref('')

// 称号
const titles = ref([])
const currentTitle = ref('')

// 个人展示
const featured = reactive({ song: null, album: null, lyric: '' })

function goSettings() {
  emit('close')
  router.push('/profile')
}

const isSuperAdmin = computed(() => auth.user?.role === 'SUPER_ADMIN')
const avatarLetter = computed(() => (auth.user?.username || '?').charAt(0).toUpperCase())

async function loadTitles() {
  try {
    const data = await client.get('/api/titles/mine')
    titles.value = data.titles || []
    currentTitle.value = data.current || ''
  } catch (e) { /* ignore */ }
}

async function chooseTitle(title) {
  try {
    await client.put('/api/titles/current', { title })
    currentTitle.value = title
    success(title ? `称号已切换为「${title}」` : '已取消称号')
  } catch (e) {
    error(e.message || '操作失败')
  }
}

async function fetchStreamLink() {
  try {
    const data = await client.get('/api/stream/link')
    if (!data.enabled) {
      error('直播流未开启，请联系频道管理员开启')
      return
    }
    streamLink.value = data.link || ''
  } catch (e) {
    error(e.message || '获取失败')
  }
}

async function copyLink() {
  try {
    await navigator.clipboard.writeText(streamLink.value)
    success('已复制到剪贴板')
  } catch (e) {
    error('复制失败，请手动选择复制')
  }
}

async function loadProfile() {
  loading.value = true
  try {
    const [playlistData, likeData] = await Promise.all([
      client.get('/api/user/playlists').catch(() => []),
      client.get(`/api/public/users/${encodeURIComponent(auth.user?.username || '')}/likes`).catch(() => ({ likes: 0 }))
    ])
    playlists.value = Array.isArray(playlistData) ? playlistData : []
    stats.value.likes = Number(likeData?.likes) || 0
  } finally {
    loading.value = false
  }
}

function managePlaylists() {
  emit('close')
  emit('playlists')
}

async function loadFeatured() {
  try {
    const data = await client.get(`/api/public/users/${encodeURIComponent(auth.user?.username || '')}/featured`)
    featured.song = (typeof data.featuredSong === 'object' && data.featuredSong) ? data.featuredSong
      : (typeof data.featuredSong === 'string' && data.featuredSong) ? { name: data.featuredSong, artists: [], coverUrl: '' } : null
    featured.album = (typeof data.featuredAlbum === 'object' && data.featuredAlbum) ? data.featuredAlbum
      : (typeof data.featuredAlbum === 'string' && data.featuredAlbum) ? { name: data.featuredAlbum, coverUrl: '' } : null
    featured.lyric = data.favoriteLyric || ''
  } catch (e) { /* ignore */ }
}

onMounted(() => {
  if (!auth.user) auth.fetchMe()
  loadProfile()
  loadTitles()
  loadFeatured()
})
</script>
