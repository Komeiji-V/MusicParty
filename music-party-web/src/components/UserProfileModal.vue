<template>
  <div class="fixed inset-0 z-[120] flex items-center justify-center bg-medical-900/70 backdrop-blur-sm p-4" @click.self="emit('close')">
    <div class="w-full max-w-md bg-medical-50 border border-medical-200 shadow-2xl chamfer-br max-h-[85vh] flex flex-col overflow-hidden">
      <!-- 头部 -->
      <div class="p-3 bg-medical-900 text-white flex justify-between items-center flex-shrink-0">
        <div class="flex items-center gap-2">
          <div class="w-2 h-2 bg-accent"></div>
          <span class="text-xs font-bold uppercase tracking-widest font-mono">USER SPACE / {{ username }}</span>
        </div>
        <button @click="emit('close')" class="text-white hover:text-accent transition-colors">
          <X class="w-4 h-4" />
        </button>
      </div>

      <div class="flex-1 overflow-y-auto p-4 space-y-4">
        <div v-if="loading" class="text-center py-10 text-medical-400 font-mono text-sm">> LOADING...</div>

        <template v-else>
          <!-- 打开公开主页（不可变 authUid；无 authUid 时隐藏——只有数字 ID 才能路由） -->
          <button
            v-if="props.authUid != null && props.authUid !== ''"
            @click="openPublicProfile"
            class="w-full flex items-center justify-center gap-2 py-2 border border-accent text-accent text-xs font-bold hover:bg-accent hover:text-white transition-colors chamfer-br"
          >
            <ExternalLink class="w-3.5 h-3.5" /> 查看完整公开主页
          </button>

          <!-- 称号 -->
          <div v-if="title" class="flex items-center gap-2">
            <span class="px-3 py-1 text-sm leading-none font-bold rounded-[3px]" :style="{ backgroundColor: titleColor || '#ff5722', color: titleTextColor(titleColor) }">{{ title }}</span>
          </div>

          <!-- 最喜欢的歌曲 / 专辑 -->
          <div v-if="featured.song || featured.album" class="grid grid-cols-1 sm:grid-cols-2 gap-2">
            <div v-if="featured.song" class="flex items-center gap-3 bg-white border border-medical-200 p-2.5">
              <div class="w-16 h-16 bg-medical-200 flex-shrink-0 overflow-hidden flex items-center justify-center">
                <img v-if="featured.song.coverUrl" :src="featured.song.coverUrl" class="w-full h-full object-cover" alt="" />
                <Music2 v-else class="w-6 h-6 text-medical-400" />
              </div>
              <div class="min-w-0">
                <div class="text-xs font-mono text-accent">最喜欢的歌曲</div>
                <div class="text-sm font-bold text-medical-900 truncate">{{ featured.song.name }}</div>
                <div class="text-xs text-medical-500 truncate">{{ (featured.song.artists || []).join(' / ') }}</div>
              </div>
            </div>
            <div v-if="featured.album" class="flex items-center gap-3 bg-white border border-medical-200 p-2.5">
              <div class="w-16 h-16 bg-medical-200 flex-shrink-0 overflow-hidden flex items-center justify-center">
                <img v-if="featured.album.coverUrl" :src="featured.album.coverUrl" class="w-full h-full object-cover" alt="" />
                <Disc3 v-else class="w-6 h-6 text-medical-400" />
              </div>
              <div class="min-w-0">
                <div class="text-xs font-mono text-accent">最喜欢的专辑</div>
                <div class="text-sm font-bold text-medical-900 truncate">{{ featured.album.name }}</div>
              </div>
            </div>
          </div>

          <!-- 歌词 -->
          <div v-if="featured.lyric" class="bg-medical-900/5 border-l-2 border-accent px-4 py-3">
            <div class="text-xs font-mono text-medical-400 mb-2">最喜欢的一段歌词</div>
            <div class="text-center text-sm text-medical-700 leading-7 whitespace-pre-line italic">{{ featured.lyric }}</div>
          </div>

          <!-- 公开歌单 -->
          <div v-if="publicPlaylists.length > 0">
            <div class="text-xs font-mono text-medical-400 tracking-widest mb-2">公开歌单</div>
            <div class="space-y-1">
              <div v-for="pl in publicPlaylists" :key="pl.id" class="flex items-center gap-2 bg-white border border-medical-200 p-2">
                <div class="w-9 h-9 bg-medical-200 flex-shrink-0 overflow-hidden">
                  <img v-if="pl.coverUrl" :src="pl.coverUrl" class="w-full h-full object-cover" alt="" />
                  <ListMusic v-else class="w-4 h-4 text-medical-400 m-auto mt-2.5" />
                </div>
                <div class="min-w-0">
                  <div class="text-sm font-bold text-medical-800 truncate">{{ pl.name }}</div>
                  <div class="text-xs font-mono text-medical-400">{{ pl.songCount || 0 }} 首</div>
                </div>
              </div>
            </div>
          </div>

          <div v-if="!title && !featured.song && !featured.album && !featured.lyric && publicPlaylists.length === 0" class="text-center py-8 text-xs font-mono text-medical-400">
            该用户还没有公开的个人展示
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { X, Music2, Disc3, ListMusic, ExternalLink } from 'lucide-vue-next'
import client from '../api/client'
import { titleTextColor } from '../utils/titleColor'

const props = defineProps({
  username: { type: String, required: true },
  authUid: { type: [Number, String], default: null },
  title: { type: String, default: '' },
  titleColor: { type: String, default: '' }
})
const emit = defineEmits(['close'])

const loading = ref(true)
const featured = ref({ song: null, album: null, lyric: '' })
const publicPlaylists = ref([])

function openPublicProfile() {
  // 只允许不可变 authUid（数字 ID）路由；无 authUid 时按钮已隐藏
  if (props.authUid == null || props.authUid === '') return
  window.open(`/u/${encodeURIComponent(String(props.authUid))}`, '_blank')
}

onMounted(async () => {
  try {
    // 无 authUid 时不请求公开接口（后端只接受数字 ID）
    if (props.authUid == null || props.authUid === '') {
      loading.value = false
      return
    }
    const id = String(props.authUid)
    const [f, pl] = await Promise.all([
      client.get(`/api/public/users/${encodeURIComponent(id)}/featured`).catch(() => ({})),
      client.get(`/api/public/users/${encodeURIComponent(id)}/playlists`).catch(() => [])
    ])
    featured.value = {
      song: (typeof f.featuredSong === 'object' && f.featuredSong) ? f.featuredSong : null,
      album: (typeof f.featuredAlbum === 'object' && f.featuredAlbum) ? f.featuredAlbum : null,
      lyric: f.favoriteLyric || ''
    }
    publicPlaylists.value = Array.isArray(pl) ? pl : []
  } finally {
    loading.value = false
  }
})
</script>
