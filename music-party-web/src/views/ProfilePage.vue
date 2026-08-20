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
        <button
          @click="channelStore.currentChannelId ? go('/room') : go('/')"
          class="flex items-center gap-2 px-3 py-2 border border-medical-200 bg-medical-50 hover:bg-medical-100 text-medical-600 transition-colors text-sm font-bold rounded-sm"
        >
          <ArrowLeft class="w-4 h-4" /> <span class="hidden sm:inline">{{ channelStore.currentChannelId ? '回到频道' : '返回首页' }}</span>
        </button>
        <div class="flex items-center gap-2 flex-shrink-0">
          <div class="w-2.5 h-2.5 md:w-3 md:h-3 bg-accent flex-shrink-0"></div>
          <h1 class="font-black text-base md:text-xl tracking-tighter text-medical-900 truncate">MY SPACE / 个人空间</h1>
        </div>
      </div>

      <div class="flex items-center gap-2">
        <button
          @click="openPublicPreview"
          class="flex items-center gap-2 px-4 py-2 border border-accent text-accent hover:bg-accent hover:text-white text-sm font-bold transition-colors rounded-sm"
        >
          <Eye class="w-4 h-4" /> <span class="hidden sm:inline">预览主页</span>
        </button>
      </div>
    </header>

    <main class="relative z-10 flex-1 w-full max-w-4xl mx-auto px-4 md:px-6 py-8 md:py-12">
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
                <h2 class="text-2xl md:text-3xl font-black text-medical-900 tracking-tighter">{{ auth.user?.username || 'GUEST' }}</h2>
                <span
                  class="px-2 py-0.5 text-xs font-mono font-bold tracking-widest"
                  :class="isSuperAdmin ? 'bg-accent text-white' : 'bg-medical-100 text-medical-500'"
                >{{ isSuperAdmin ? 'SUPER ADMIN' : 'MEMBER' }}</span>
              </div>
              <p class="text-xs font-mono text-medical-400 mt-2 tracking-wider break-all">{{ auth.user?.email || '—' }}</p>
              <p class="text-xs font-mono text-medical-300 mt-1 tracking-wider">
                <span v-if="auth.user?.emailVerified" class="text-green-600">EMAIL VERIFIED</span>
                <span v-else class="text-orange-500">EMAIL UNVERIFIED</span>
                <span class="mx-2">·</span>
                ID: {{ auth.user?.id ?? '—' }}
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

          <!-- 公开展示：左专辑大图 + 右专辑歌曲列表；无专辑时只显示喜爱歌曲区 -->
          <div v-if="firstAlbum || songWidgets.length" class="mt-5 border-t border-medical-100 pt-4">
            <FeaturedDisplay :album="firstAlbum" :album-data="albumData" :song-widgets="songWidgets" :loading="albumSongsLoading" />
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
          <div class="text-2xl md:text-3xl font-black text-medical-900">{{ playlists.length }}</div>
          <div class="text-xs font-mono text-medical-400 mt-1 tracking-wider">歌单 / PLAYLISTS</div>
        </div>
      </section>

      <!-- 个人主页设置 -->
      <section class="mb-8">
        <div class="bg-white border border-medical-200 chamfer-br p-5 md:p-6 relative overflow-hidden">
          <div class="absolute top-2 left-2 w-3 h-3 border-t border-l border-accent"></div>
          <div class="absolute top-2 right-2 w-3 h-3 border-t border-r border-accent"></div>
          <div class="flex items-center gap-3 mb-4">
            <span class="font-mono text-xs text-medical-400 tracking-[0.25em]">PROFILE SETTINGS / 个人主页设置</span>
            <span class="w-1.5 h-1.5 bg-accent"></span>
          </div>

          <!-- 贡献音源 Cookie -->
          <div class="mb-5">
            <div class="text-sm font-bold text-medical-900 mb-2">贡献音源 Cookie</div>
            <p class="text-xs text-medical-500 mb-2">审核通过后汇入全局 Cookie 池，并获得「音源提供者」称号</p>
            <div class="flex gap-2">
              <select v-model="submitPlatform" class="flex-shrink-0 border border-medical-200 p-2 text-sm bg-medical-50 outline-none focus:border-accent">
                <option value="netease">网易云</option>
                <option value="qq">QQ音乐</option>
                <option value="kugou">酷狗</option>
                <option value="bilibili">B站</option>
              </select>
              <input v-model="submitCookie" placeholder="粘贴 Cookie / SESSDATA" class="flex-1 border border-medical-200 p-2 text-sm font-mono bg-medical-50 outline-none focus:border-accent" />
              <button @click="submitCookieReq" class="px-5 py-2 bg-accent text-white text-sm font-bold hover:bg-accent-hover transition-colors flex-shrink-0">提交审核</button>
            </div>
            <div v-if="mySubmissions.length > 0" class="text-xs font-mono text-medical-400 mt-2">
              我的提交：<span v-for="s in mySubmissions" :key="s.id" class="mr-2">{{ s.platform }} · {{ statusText(s.status) }}</span>
            </div>
          </div>

          <div class="border-t border-medical-100 pt-4">
            <!-- 添加内容 -->
            <div class="flex flex-wrap gap-2 mb-4">
              <button @click="openSongPicker('song')" class="px-4 py-2 bg-accent text-white text-xs font-bold hover:bg-accent-hover transition-colors">+ 添加歌曲</button>
              <button @click="openSongPicker('album')" :disabled="hasAlbum" class="px-4 py-2 bg-medical-900 text-white text-xs font-bold hover:bg-accent transition-colors disabled:opacity-40 disabled:cursor-not-allowed">+ 添加专辑</button>
              <button @click="openSongPicker('lyric')" class="px-4 py-2 border border-medical-300 text-medical-700 text-xs font-bold hover:border-accent hover:text-accent transition-colors">+ 添加歌词</button>
            </div>

            <!-- 内容池 -->
            <div v-if="featured.widgets.length" class="space-y-2 mb-4">
              <div v-for="(w, idx) in featured.widgets" :key="w.id" class="border border-medical-200 bg-medical-50 p-2.5">
                <div class="flex items-center gap-2 mb-1.5">
                  <span class="px-1.5 py-0.5 text-[10px] font-bold text-white rounded-[2px]" :class="kindBadgeClass(w.kind)">{{ kindLabel(w.kind) }}</span>
                  <span class="text-xs font-bold text-medical-900 truncate flex-1">{{ widgetTitle(w) }}</span>
                  <button @click="moveWidget(idx, -1)" :disabled="idx === 0 || w.kind === 'album'" class="text-[11px] font-mono text-medical-400 hover:text-accent disabled:opacity-30 flex-shrink-0">↑</button>
                  <button @click="moveWidget(idx, 1)" :disabled="w.kind === 'album' || idx === featured.widgets.length - 1" class="text-[11px] font-mono text-medical-400 hover:text-accent disabled:opacity-30 flex-shrink-0">↓</button>
                  <button @click="removeWidget(w.id)" class="text-[10px] font-mono text-red-400 hover:underline flex-shrink-0">删除</button>
                </div>
                <!-- 歌曲：模块尺寸选择 -->
                <div v-if="w.kind === 'song'" class="flex items-center gap-1.5 mb-1.5">
                  <span class="text-[10px] font-mono text-medical-400 flex-shrink-0">模块尺寸</span>
                  <button
                    v-for="sz in ['1x1', '1x2']"
                    :key="sz"
                    @click="w.size = sz"
                    class="px-2 py-0.5 text-[10px] font-bold border transition-colors"
                    :class="(w.size || '1x1') === sz ? 'bg-accent text-white border-accent' : 'bg-white text-medical-500 border-medical-200 hover:border-accent'"
                  >{{ sz }}</button>
                </div>
                <!-- 歌曲/专辑：备注（黑色文字，点击直接编辑） -->
                <input
                  v-if="w.kind !== 'lyric'"
                  v-model="w.note"
                  type="text"
                  class="w-full p-1.5 text-xs font-mono text-medical-900 bg-transparent border border-transparent outline-none focus:bg-white focus:border-accent hover:border-medical-200 rounded-sm transition-colors"
                  placeholder="点这里写备注"
                />
                <!-- 歌词：照搬歌曲行（标题行固定歌名），备注位置换成歌词直编，备注保留 -->
                <template v-else>
                  <textarea v-model="w.data.text" rows="3" class="w-full p-1.5 text-xs leading-5 font-mono text-medical-900 bg-transparent border border-transparent outline-none focus:bg-white focus:border-accent hover:border-medical-200 rounded-sm transition-colors resize-y" placeholder="歌词内容"></textarea>
                  <input v-model="w.note" type="text" class="mt-1.5 w-full p-1.5 text-xs font-mono text-medical-900 bg-transparent border border-transparent outline-none focus:bg-white focus:border-accent hover:border-medical-200 rounded-sm transition-colors" placeholder="点这里写备注" />
                </template>
              </div>
            </div>
            <div v-else class="text-xs font-mono text-medical-400 mb-4">还没有内容，点上方按钮添加歌曲/专辑/歌词</div>

            <button @click="saveFeatured" class="px-6 py-2 bg-medical-900 text-white text-sm font-bold hover:bg-accent transition-colors chamfer-br">保存展示</button>
          </div>
        </div>
      </section>

      <!-- 我的歌单 -->
      <section>
        <div class="flex items-center justify-between gap-3 mb-4 border-b border-medical-200 pb-3">
          <div class="flex items-center gap-3">
            <span class="font-mono text-xs text-medical-400 tracking-[0.25em]">MY PLAYLISTS / 我的歌单</span>
            <span class="w-1.5 h-1.5 bg-accent"></span>
          </div>
          <button
            @click="go('/playlists')"
            class="text-xs font-mono text-accent hover:text-accent-hover tracking-widest flex items-center gap-1"
          >
            管理歌单 <ArrowRight class="w-3.5 h-3.5" />
          </button>
        </div>

        <div v-if="loading" class="text-center py-12 text-medical-400 font-mono text-sm">> LOADING...</div>

        <div v-else-if="playlists.length === 0" class="bg-white border border-medical-200 chamfer-br p-10 text-center">
          <div class="text-lg font-black text-medical-900 tracking-tighter mb-2">NO PLAYLISTS YET</div>
          <p class="font-mono text-xs text-medical-400 mb-5 tracking-wider">还没有歌单，去创建一个吧</p>
          <button
            @click="go('/playlists')"
            class="px-6 py-2.5 bg-accent text-white font-bold text-sm hover:bg-accent-hover transition-colors chamfer-br"
          >CREATE PLAYLIST</button>
        </div>

        <div v-else class="grid grid-cols-1 sm:grid-cols-2 gap-3 md:gap-4">
          <div
            v-for="pl in playlists"
            :key="pl.id"
            @click="go('/playlists')"
            class="bg-white border border-medical-200 hover:border-accent hover:shadow-lg transition-all duration-300 chamfer-br p-4 cursor-pointer group"
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
                  <span class="px-1.5 py-0.5 text-xs font-mono font-bold text-medical-500 bg-medical-100 rounded-sm">{{ pl.itemCount }} TRACKS</span>
                  <span v-if="pl.isPublic" class="px-1.5 py-0.5 text-xs font-mono font-bold text-green-700 bg-green-100 rounded-sm">PUBLIC</span>
                </div>
              </div>
              <ArrowRight class="w-4 h-4 text-medical-300 group-hover:text-accent transition-colors flex-shrink-0" />
            </div>
          </div>
        </div>
      </section>

      <!-- 底部 -->
      <footer class="mt-12 border-t border-medical-200 pt-6 flex flex-col md:flex-row items-center justify-between gap-2">
        <span class="text-xs font-mono text-medical-400 tracking-wider">© {{ year }} {{ uiStore.siteTitle }} · by {{ uiStore.authorName }}</span>
        <span class="text-xs font-mono text-medical-300 tracking-[0.25em]">POWERED BY MUSIC PARTY</span>
      </footer>
    </main>

    <InfoModal />
    <ToastNotification />

    <!-- 从歌单选择歌曲 / 专辑 / 歌词歌曲 -->
    <SongPickerModal
      v-if="pickerOpen"
      :mode="pickerMode === 'album' ? 'album' : 'song'"
      :title="pickerMode === 'album' ? '选择一张专辑作为最喜欢的专辑' : (pickerMode === 'lyric' ? '选择一首歌作为歌词出处' : '选择一首歌作为最喜欢的歌曲')"
      @close="pickerOpen = false"
      @select="onSongPicked"
    />
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, ArrowRight, ListMusic, Eye } from 'lucide-vue-next'
import { useAuthStore } from '../stores/auth'
import { useUiStore } from '../stores/ui'
import { useChannelStore } from '../stores/channel'
import { usePlayerStore } from '../stores/player'
import client from '../api/client'
import { useToast } from '../composables/useToast'
import InfoModal from '../components/InfoModal.vue'
import SongPickerModal from '../components/SongPickerModal.vue'
import ToastNotification from '../components/ToastNotification.vue'
import FeaturedDisplay from '../components/FeaturedDisplay.vue'
import { titleTextColor } from '../utils/titleColor'

const router = useRouter()
const auth = useAuthStore()
const uiStore = useUiStore()
const channelStore = useChannelStore()
const playerStore = usePlayerStore()
const { success, error } = useToast()

const year = new Date().getFullYear()
const loading = ref(false)
const playlists = ref([])
const stats = ref({ likes: 0 })
const featured = reactive({ widgets: [] })
const allTitles = ref([])
const pickerOpen = ref(false)
const pickerMode = ref('song') // 'song' | 'album' | 'lyric'：本次添加的内容类型

// 专辑歌曲列表（公开主页右栏）
const albumData = ref({ id: '', name: '', songs: [] })
const albumSongsLoading = ref(false)
const firstAlbum = computed(() => featured.widgets.find(w => w.kind === 'album') || null)
const hasAlbum = computed(() => featured.widgets.some(w => w.kind === 'album'))
const songWidgets = computed(() => featured.widgets.filter(w => w.kind === 'song' || w.kind === 'lyric'))

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

watch(featured.widgets, () => { loadAlbumSongs() }, { deep: true })

let widgetSeq = 0
function nextWidgetId(kind) {
  return `${kind}-${Date.now()}-${++widgetSeq}`
}

function openSongPicker(mode) {
  pickerMode.value = mode
  pickerOpen.value = true
}

function onSongPicked(item) {
  const music = item.music
  if (pickerMode.value === 'album') {
    // 添加专辑内容（限制：只能一个，且固定在第一个）
    if (featured.widgets.some(w => w.kind === 'album')) {
      error('只能添加一个专辑')
      pickerOpen.value = false
      return
    }
    featured.widgets.push({
      id: nextWidgetId('album'),
      kind: 'album',
      data: { name: music.album || music.name, coverUrl: music.coverUrl || '', album: music.album || '', platform: music.platform || 'netease' },
      note: '我最喜欢的专辑'
    })
    featured.widgets = sortAlbumFirst(featured.widgets)
  } else if (pickerMode.value === 'lyric') {
    // 添加歌词内容（歌名 + 歌词由用户填写；存 musicId/platform/coverUrl 供浮层跳官方歌曲页、卡片显示封面）
    featured.widgets.push({
      id: nextWidgetId('lyric'),
      kind: 'lyric',
      data: { song: music.name, text: '', musicId: music.id || '', platform: music.platform || 'netease', coverUrl: music.coverUrl || '' },
      note: ''
    })
  } else {
    // 添加歌曲内容
    featured.widgets.push({
      id: nextWidgetId('song'),
      kind: 'song',
      data: {
        name: music.name,
        artists: music.artists || [],
        coverUrl: music.coverUrl || '',
        musicId: music.id,
        platform: music.platform,
        album: music.album || ''
      },
      size: '1x1',
      note: ''
    })
  }
  pickerOpen.value = false
}

const kindLabel = (kind) => ({ song: '歌曲', album: '专辑', lyric: '歌词' }[kind] || kind)
const kindBadgeClass = (kind) => ({ song: 'bg-accent', album: 'bg-medical-700', lyric: 'bg-medical-500' }[kind] || 'bg-medical-500')
const widgetTitle = (w) => {
  if (w.kind === 'lyric') return w.data?.song || '歌词'
  return w.data?.name || w.kind
}

function removeWidget(id) {
  featured.widgets = featured.widgets.filter(w => w.id !== id)
}

// 专辑固定排第一（排序 + 移动限制）
function sortAlbumFirst(list) {
  const album = list.find(w => w.kind === 'album')
  if (!album) return list
  return [album, ...list.filter(w => w.kind !== 'album')]
}

function moveWidget(idx, dir) {
  const target = idx + dir
  if (target < 0 || target >= featured.widgets.length) return
  const w = featured.widgets[idx]
  // 专辑固定第一：专辑不可移动，其他行不能移到专辑前面
  if (w.kind === 'album') return
  if (featured.widgets[target].kind === 'album') return
  const arr = [...featured.widgets]
  ;[arr[idx], arr[target]] = [arr[target], arr[idx]]
  featured.widgets = arr
}

const isSuperAdmin = computed(() => auth.user?.role === 'SUPER_ADMIN')
const avatarLetter = computed(() => (auth.user?.username || '?').charAt(0).toUpperCase())

async function loadFeatured() {
  try {
    const data = await client.get(`/api/public/users/${encodeURIComponent(auth.user?.authUid || '')}/featured`)
    featured.widgets = sortAlbumFirst(Array.isArray(data.widgets) ? data.widgets : [])
    const me = await client.get('/api/titles/mine').catch(() => null)
    allTitles.value = me?.titles || []
    loadAlbumSongs()
  } catch (e) { /* ignore */ }
}

// Cookie 贡献（独立设置页，防止频道弹窗误触）
const submitPlatform = ref('netease')
const submitCookie = ref('')
const mySubmissions = ref([])

const statusText = (s) => ({ PENDING: '待审核', APPROVED: '已通过', REJECTED: '已拒绝' }[s] || s)

async function loadSubmissions() {
  try {
    mySubmissions.value = await client.get('/api/cookies/my')
  } catch (e) { /* ignore */ }
}

async function submitCookieReq() {
  const cookie = submitCookie.value.trim()
  if (!cookie) {
    error('请粘贴 Cookie')
    return
  }
  try {
    const res = await client.post('/api/cookies/submit', { platform: submitPlatform.value, cookie })
    submitCookie.value = ''
    success(res.message || '提交成功')
    loadSubmissions()
  } catch (e) {
    error(e.message || '提交失败')
  }
}

async function saveFeatured() {
  try {
    await client.put('/api/profile/featured', {
      widgets: sortAlbumFirst(featured.widgets)
    })
    success('主页展示已更新')
  } catch (e) {
    error(e.message || '保存失败')
  }
}

function go(path) {
  // 返回首页时清理频道状态，否则无法再次进入同一频道
  if (path === '/' && channelStore.currentChannelId) {
    playerStore.leaveChannel()
    channelStore.clearCurrentChannel()
  }
  router.push(path)
}

// 预览自己的公开主页（新标签打开，不打断编辑状态）
function openPublicPreview() {
  // 用不可变 authUid 拼公开主页链接（用户改名后链接仍有效）
  const uid = auth.user?.authUid
  if (!uid) return
  window.open(`/u/${encodeURIComponent(uid)}`, '_blank')
}

async function loadProfile() {
  loading.value = true
  try {
    const [playlistData, likeData] = await Promise.all([
      client.get('/api/user/playlists').catch(() => []),
      client.get(`/api/public/users/${encodeURIComponent(auth.user?.authUid || '')}/likes`).catch(() => ({ likes: 0 }))
    ])
    playlists.value = Array.isArray(playlistData) ? playlistData : []
    stats.value.likes = Number(likeData?.likes) || 0
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  uiStore.fetchConfig()
  if (!auth.user) {
    await auth.fetchMe()
  }
  loadProfile()
  loadFeatured()
  loadSubmissions()
})
</script>
