<template>
  <div>
    <div class="text-xs font-mono text-medical-400 tracking-widest mb-3">FEATURED / 个人展示</div>
    <!-- 左栏：专辑大图（点击跳转官方专辑页）；无专辑时整块隐藏，歌曲区照常显示 -->
    <div v-if="album" class="flex gap-5 items-start">
      <!-- 左栏：专辑大图（点击跳转官方专辑页） -->
      <div ref="leftRef" class="w-[40%] max-w-[280px] flex-shrink-0">
        <div
          class="rounded-xl overflow-hidden shadow-lg cursor-pointer group relative"
          :title="albumPageUrl() ? '点击打开官方专辑页' : ''"
          @click="openUrl(albumPageUrl())"
        >
          <img v-if="album.data.coverUrl" :src="album.data.coverUrl" class="w-full aspect-square object-cover group-hover:scale-[1.02] transition-transform duration-300" alt="" />
          <div v-else class="w-full aspect-square bg-gradient-to-br from-medical-700 to-medical-900 flex items-center justify-center">
            <Disc3 class="w-12 h-12 text-white/30" />
          </div>
          <div v-if="albumPageUrl()" class="absolute inset-0 bg-black/0 group-hover:bg-black/20 transition-colors flex items-center justify-center opacity-0 group-hover:opacity-100">
            <span class="text-xs font-bold text-white bg-black/50 px-2.5 py-1 rounded-full">打开专辑页 ↗</span>
          </div>
        </div>
        <div class="text-center mt-3">
          <div class="font-black text-medical-900">{{ album.data.name }}</div>
          <div class="text-xs font-mono text-accent mt-0.5">{{ album.note || '最喜欢的专辑' }}</div>
        </div>
      </div>

      <!-- 右栏：专辑歌曲列表，与左栏等高，超出内部滚动（整体保持矩形），点击歌曲跳官方歌曲页 -->
      <div class="flex-1 min-w-0" :style="rightMaxH ? { maxHeight: rightMaxH } : undefined">
        <div class="flex items-center justify-between mb-2">
          <div class="text-xs font-mono text-medical-400 tracking-widest">专辑歌曲</div>
          <div class="text-[10px] font-mono text-medical-300">{{ songCount }} 首</div>
        </div>
        <div class="overflow-y-auto pr-1.5 album-song-scroll" :style="rightMaxH ? { maxHeight: `calc(${rightMaxH} - 1.75rem)` } : undefined">
          <div v-if="loading" class="py-10 text-center text-xs font-mono text-medical-400">> LOADING...</div>
          <div v-else-if="songCount === 0" class="py-10 text-center">
            <div class="text-sm font-mono text-medical-300 mb-1">暂无歌曲</div>
            <p class="text-[11px] text-medical-400">未找到该专辑的歌曲</p>
          </div>
          <div v-else class="space-y-0.5">
            <div
              v-for="(s, i) in songs"
              :key="s.id"
              class="flex items-center gap-3 py-2 px-2 rounded hover:bg-medical-50 transition-colors group cursor-pointer"
              :title="songUrl(s) ? '点击打开官方歌曲页' : ''"
              @click="openUrl(songUrl(s))"
            >
              <div class="w-6 text-right text-[11px] font-mono text-medical-300 flex-shrink-0">{{ i + 1 }}</div>
              <div class="flex-1 min-w-0">
                <div class="text-sm font-bold text-medical-900 truncate group-hover:text-accent transition-colors">{{ s.name }}</div>
                <div class="text-xs text-medical-500 truncate">{{ (s.artists || []).join(' / ') }}</div>
              </div>
              <div class="text-[11px] font-mono text-medical-300 flex-shrink-0">{{ fmtDuration(s.duration) }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 歌曲模块区：两排分开滚动，每排各自无限循环（各自无缝），第一排优先填满 -->
    <div v-if="songWidgets.length" class="mt-6">
      <div class="flex items-center justify-between mb-2">
        <div class="text-xs font-mono text-medical-400 tracking-widest">{{ hasLyric ? '喜爱歌曲与歌词' : '喜爱歌曲' }}</div>
        <div class="text-[10px] font-mono text-medical-300">{{ songWidgets.length }} {{ hasLyric ? '件' : '首' }}</div>
      </div>
      <div
        :ref="onSongArea"
        class="overflow-hidden relative"
        @mouseenter="paused = true"
        @mouseleave="paused = false"
      >
        <div
          v-for="(row, ri) in rows"
          :key="ri"
          class="flex song-track"
          :class="{ 'mt-2': ri > 0 }"
          :style="{ animationDuration: row.dur, animationPlayState: paused ? 'paused' : 'running', '--scroll-dist': row.dist }"
        >
          <div
            v-for="copy in row.copies"
            :key="copy"
            class="relative flex-shrink-0"
            :style="{ width: row.w + 'px', height: row.h + 'px' }"
          >
            <div
              v-for="w in row.placed"
              :key="copy + '-' + w.id"
              class="absolute rounded-lg overflow-hidden bg-white border border-medical-100 group cursor-pointer"
              
              :style="{ left: w.left + 'px', top: '0px', width: w.pxW + 'px', height: w.pxH + 'px' }"
              :title="w.kind === 'lyric' ? '点击查看完整歌词' : (songPageUrl(w.data.musicId, w.data.platform) ? '点击打开官方歌曲页' : '')"
              @click="w.kind === 'lyric' ? openLyricModal(w) : openUrl(songPageUrl(w.data.musicId, w.data.platform))"
            >
              <!-- 歌词（1x3）：左封面 + 右侧居中标题 + 歌词前两行，点击弹完整浮层 -->
              <template v-if="w.kind === 'lyric' && w.data">
                <div class="flex items-stretch h-full">
                  <div class="h-full aspect-square overflow-hidden flex-shrink-0">
                    <img v-if="w.data.coverUrl" :src="w.data.coverUrl" class="w-full h-full object-cover" alt="" />
                    <div v-else class="w-full h-full bg-gradient-to-br from-medical-700 to-medical-900 flex items-center justify-center">
                      <Disc3 class="w-5 h-5 text-white/40" />
                    </div>
                  </div>
                  <div class="flex-1 min-w-0 px-2.5 py-2 flex flex-col justify-center bg-white">
                    <div class="text-[11px] sm:text-sm font-bold text-medical-900 leading-tight truncate text-center">{{ w.data.song }}</div>
                    <div class="text-[10px] sm:text-xs text-medical-600 italic leading-4 sm:leading-5 mt-1 whitespace-pre-line line-clamp-2 text-center">{{ lyricFirstLines(w) }}</div>
                    <div v-if="w.note" class="text-[9px] font-mono text-accent truncate mt-1 text-center">{{ w.note }}</div>
                  </div>
                </div>
              </template>
              <!-- 方形（1x1）：封面铺满 + 底部渐变遮罩 + 白色文字（不遮挡封面） -->
              <template v-else-if="sizeW(w) === sizeH(w)">
                <div class="absolute inset-0 overflow-hidden">
                  <img v-if="w.data.coverUrl" :src="w.data.coverUrl" class="w-full h-full object-cover group-hover:scale-[1.02] transition-transform duration-300" alt="" />
                  <div v-else class="w-full h-full bg-gradient-to-br from-medical-700 to-medical-900"></div>
                </div>
                <div class="absolute inset-0 bg-gradient-to-t from-black/75 via-transparent to-transparent"></div>
                <div class="absolute inset-x-0 bottom-0 p-1.5">
                  <div class="text-[10px] sm:text-xs font-bold text-white truncate leading-tight drop-shadow">{{ w.data.name }}</div>
                  <div v-if="w.data.artists?.length" class="text-[8px] sm:text-[10px] text-white/75 truncate mt-0.5">{{ w.data.artists.join(' / ') }}</div>
                  <div v-if="w.note" class="text-[8px] sm:text-[10px] font-mono text-amber-300 truncate mt-0.5">{{ w.note }}</div>
                </div>
              </template>
              <!-- 1x2 横条：左封面方形 + 右侧白色文字区垂直居中 -->
              <template v-else>
                <div class="flex items-stretch h-full">
                  <div class="h-full aspect-square overflow-hidden flex-shrink-0">
                    <img v-if="w.data.coverUrl" :src="w.data.coverUrl" class="w-full h-full object-cover group-hover:scale-[1.02] transition-transform duration-300" alt="" />
                    <div v-else class="w-full h-full bg-gradient-to-br from-medical-100 to-medical-200"></div>
                  </div>
                  <div class="flex-1 min-w-0 px-2.5 flex flex-col items-start justify-center bg-white">
                    <div class="text-[11px] sm:text-sm font-bold text-medical-900 leading-tight line-clamp-2">{{ w.data.name }}</div>
                    <div v-if="w.data.artists?.length" class="text-[9px] sm:text-xs text-medical-500 truncate mt-1">{{ w.data.artists.join(' / ') }}</div>
                    <div v-if="w.note" class="text-[9px] sm:text-xs font-mono text-accent truncate mt-1">{{ w.note }}</div>
                  </div>
                </div>
              </template>
              <!-- hover 淡黑蒙版 + 文字（与专辑封面同款）；歌词显示"查看歌词" -->
              <div
                v-if="w.kind === 'lyric' || songPageUrl(w.data.musicId, w.data.platform)"
                class="absolute inset-0 bg-black/0 group-hover:bg-black/20 transition-colors duration-300 flex items-center justify-center opacity-0 group-hover:opacity-100 z-20"
              >
                <span class="text-xs font-bold text-white bg-black/50 px-2.5 py-1 rounded-full">{{ w.kind === 'lyric' ? '查看歌词 ↗' : '打开歌曲页 ↗' }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 歌词完整浮层（点击歌词卡展开）：浅色与页面一致；封面可点击跳官方歌曲页 -->
    <Teleport to="body">
      <div v-if="lyricModal" class="fixed inset-0 z-[200] bg-black/60 flex items-center justify-center p-4" @click.self="lyricModal = null">
        <div class="bg-white rounded-2xl px-6 py-8 max-w-lg w-full relative overflow-hidden shadow-2xl border border-medical-100">
          <div class="absolute -top-16 -right-16 w-48 h-48 rounded-full bg-accent/10 blur-3xl pointer-events-none"></div>
          <button class="absolute top-3 right-3 text-medical-400 hover:text-medical-900 transition-colors" @click="lyricModal = null">
            <X class="w-5 h-5" />
          </button>
          <!-- 封面 + 标题区（居中竖排）：封面可点击跳官方歌曲页 -->
          <div class="flex flex-col items-center mb-5">
            <div
              v-if="songPageUrl(lyricModal.data?.musicId, lyricModal.data?.platform)"
              class="w-32 h-32 rounded-xl overflow-hidden cursor-pointer flex-shrink-0 group relative shadow-md"
              :title="'打开官方歌曲页'"
              @click="openUrl(songPageUrl(lyricModal.data?.musicId, lyricModal.data?.platform))"
            >
              <img v-if="lyricModal.data?.coverUrl" :src="lyricModal.data.coverUrl" class="w-full h-full object-cover group-hover:scale-[1.02] transition-transform duration-300" alt="" />
              <div v-else class="w-full h-full bg-gradient-to-br from-medical-100 to-medical-200 flex items-center justify-center">
                <Disc3 class="w-10 h-10 text-medical-300" />
              </div>
              <div class="absolute inset-0 bg-black/0 group-hover:bg-black/20 transition-colors flex items-center justify-center opacity-0 group-hover:opacity-100">
                <span class="text-[10px] font-bold text-white bg-black/50 px-2 py-0.5 rounded-full">打开歌曲页 ↗</span>
              </div>
            </div>
            <div v-else class="w-32 h-32 rounded-xl overflow-hidden flex-shrink-0 bg-gradient-to-br from-medical-100 to-medical-200 flex items-center justify-center">
              <Disc3 class="w-10 h-10 text-medical-300" />
            </div>
            <div class="min-w-0 text-center">
              <div class="text-medical-900 font-mono font-bold text-lg leading-tight mt-4">{{ lyricModal.data?.song }}</div>
              <div class="text-[10px] font-mono text-accent mt-1.5 uppercase">{{ lyricModal.data?.platform || '' }}</div>
            </div>
          </div>
          <div class="text-medical-600 italic whitespace-pre-line leading-7 text-sm max-h-[55vh] overflow-y-auto pr-1 text-center album-song-scroll">{{ lyricModal.data?.text }}</div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { Disc3, X } from 'lucide-vue-next'

const props = defineProps({
  album: { type: Object, default: null },
  albumData: { type: Object, default: () => ({ id: '', name: '', songs: [] }) },
  songWidgets: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false }
})

const songs = computed(() => (Array.isArray(props.albumData?.songs) ? props.albumData.songs : []))
const songCount = computed(() => songs.value.length)
const platform = computed(() => props.album?.data?.platform || 'netease')
// 用户内容池中的歌曲（模块区）
const songWidgets = computed(() => (Array.isArray(props.songWidgets) ? props.songWidgets : []))
const hasLyric = computed(() => songWidgets.value.some(w => w.kind === 'lyric'))

// 歌曲模块区：按用户添加顺序排列
const songWidgetsSorted = computed(() => [...songWidgets.value])

// 两排分开滚动：第一排优先填满 6 列 → 第二排填满 → 之后均衡扩展；每排独立循环（各自无缝）
const SONG_GAP = 8 // 左右/列间距
const SONG_ROW_H = 126
const ROW_GAP = 8 // 上下两排间距
const songAreaW = ref(782)
// 函数 ref + ResizeObserver：元素挂载后持续测量实际宽度（布局稳定后自动校正）
let songRO = null
function onSongArea(el) {
  if (!el) return
  songAreaW.value = el.clientWidth
  if (songRO) songRO.disconnect()
  songRO = new ResizeObserver(() => { songAreaW.value = el.clientWidth })
  songRO.observe(el)
}
// 列宽最小 = 行高（126px）→ 1x1 模块恒为正方形（窄屏/小窗下模块最小 1×1，不会被压成细条）；
// 列数随容器宽度自适应（窄屏自动减少列数）
const colCount = computed(() => Math.max(1, Math.floor((songAreaW.value + SONG_GAP) / (SONG_ROW_H + SONG_GAP))))
const colW = computed(() => Math.max(SONG_ROW_H, (songAreaW.value - (colCount.value - 1) * SONG_GAP) / colCount.value))

const rows = computed(() => {
  const cw = colW.value
  // 滚动开关：第一页 2x6（12 格）放满才滚动；静态时上下对齐，滚动时才启用第二排错缝
  let cells = 0
  for (const w of songWidgetsSorted.value) cells += sizeW(w) * sizeH(w)
  const overflow = cells > colCount.value * 2
  const halfShift = overflow ? cw / 2 : 0 // 错缝只在滚动时启用
  const row0 = [] // 第一排模块
  const row1 = [] // 第二排模块
  let x0 = 0
  let x1 = 0
  for (const w of songWidgetsSorted.value) {
    const mw = sizeW(w)
    const mh = sizeH(w)
    const mod = { ...w, left: 0, pxW: mw * cw + (mw - 1) * SONG_GAP, pxH: mh * SONG_ROW_H }
    if (x0 + mw <= colCount.value) {
      // 第一排优先填满（前 6 列）
      mod.left = x0 * (cw + SONG_GAP)
      row0.push(mod)
      x0 += mw
    } else if (x1 + mw <= colCount.value) {
      // 第二排填满（前 6 列）
      mod.left = x1 * (cw + SONG_GAP) + halfShift
      row1.push(mod)
      x1 += mw
    } else if (x0 <= x1) {
      // 均衡扩展：第一排
      mod.left = x0 * (cw + SONG_GAP)
      row0.push(mod)
      x0 += mw
    } else {
      // 均衡扩展：第二排
      mod.left = x1 * (cw + SONG_GAP) + halfShift
      row1.push(mod)
      x1 += mw
    }
  }
  const row0W = x0 * (cw + SONG_GAP) - SONG_GAP
  const row1W = x1 * (cw + SONG_GAP) - SONG_GAP + halfShift
  // 每排独立循环：内容复制 3 份、滚动 2 份宽度（多播一会）
  // 循环点处可见内容与起始完全一致（含偏移空白），避免瞬跳时开头内容断层"消失"
  const build = (placed, wpx, offset) => {
    const loopW = wpx - offset + SONG_GAP
    return {
      placed,
      w: loopW,
      h: SONG_ROW_H, // 轨道高度固定一排
      copies: overflow ? [0, 1, 2] : [0],
      dist: overflow ? `-${2 * loopW}px` : '0px',
      dur: overflow ? `${Math.max(16, 2 * loopW / 60)}s` : '0s'
    }
  }
  return [build(row0, row0W, 0), build(row1, row1W, halfShift)]
})
const paused = ref(false)

function sizeW(w) {
  // 歌词固定 1x3（3 列宽横条，比 1x2 更能装歌词）
  if (w.kind === 'lyric') return 3
  const s = w.size || '1x1'
  return s === '1x2' || s === '2x2' ? 2 : 1
}
function sizeH(w) {
  // 已移除 2x2：旧 2x2 数据降级为 1x2 横条（不再跨排）；歌词 1 行高
  return 1
}

// 歌词完整浮层（A1）
const lyricModal = ref(null)
function openLyricModal(w) {
  lyricModal.value = w
}

// 歌词卡只显示前两行（按换行切分，保持行结构）
function lyricFirstLines(w) {
  const t = (w.data && w.data.text) || ''
  return t.split('\n').slice(0, 2).join('\n')
}

// 右栏高度跟随左栏（整体保持矩形），超出内部滚动
const leftRef = ref(null)
const rightMaxH = ref(null)
let ro = null

onMounted(() => {
  ro = new ResizeObserver(() => {
    if (leftRef.value) rightMaxH.value = leftRef.value.offsetHeight + 'px'
  })
  if (leftRef.value) ro.observe(leftRef.value)
})

onBeforeUnmount(() => {
  if (ro) ro.disconnect()
  if (songRO) songRO.disconnect()
})

// 官方页面跳转链接
function albumPageUrl() {
  const id = props.albumData?.id
  if (!id) return ''
  const p = platform.value
  if (p === 'netease') return `https://music.163.com/#/album?id=${id}`
  if (p === 'qq') return `https://y.qq.com/n/ryqq/albumDetail/${id}`
  if (p === 'kugou') return `https://www.kugou.com/album/${id}.html`
  return ''
}

function songUrl(s) {
  if (!s?.id) return ''
  const p = platform.value
  if (p === 'netease') return `https://music.163.com/#/song?id=${s.id}`
  if (p === 'qq') return `https://y.qq.com/n/ryqq/songDetail/${s.id}`
  if (p === 'kugou') return `https://www.kugou.com/song/#hash=${s.id}`
  return ''
}

// 内容池歌曲的官方页链接（歌曲自带平台）
function songPageUrl(musicId, p) {
  if (!musicId) return ''
  const plat = p || 'netease'
  if (plat === 'netease') return `https://music.163.com/#/song?id=${musicId}`
  if (plat === 'qq') return `https://y.qq.com/n/ryqq/songDetail/${musicId}`
  if (plat === 'kugou') return `https://www.kugou.com/song/#hash=${musicId}`
  return ''
}

function openUrl(url) {
  if (url) window.open(url, '_blank')
}

function fmtDuration(ms) {
  if (!ms) return '--:--'
  const s = Math.floor(ms / 1000)
  const m = Math.floor(s / 60)
  const r = s % 60
  return `${m}:${String(r).padStart(2, '0')}`
}
</script>

<style scoped>
.album-song-scroll::-webkit-scrollbar {
  width: 6px;
}
.album-song-scroll::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 3px;
}
.album-song-scroll::-webkit-scrollbar-track {
  background: transparent;
}
.song-track {
  animation-name: song-scroll-x;
  animation-timing-function: linear;
  animation-iteration-count: infinite;
}
@keyframes song-scroll-x {
  from { transform: translateX(0); }
  to { transform: translateX(var(--scroll-dist)); }
}
</style>
