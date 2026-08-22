<template>
  <div class="fixed inset-0 z-[110] flex items-center justify-center bg-medical-900/70 backdrop-blur-sm p-4" @click.self="emit('close')">
    <div class="w-full max-w-md bg-medical-50 border border-medical-200 shadow-2xl chamfer-br max-h-[85vh] flex flex-col overflow-hidden">
      <!-- 头部 -->
      <div class="p-3 bg-medical-900 text-white flex justify-between items-center flex-shrink-0">
        <div class="flex items-center gap-2">
          <div class="w-2 h-2 bg-accent"></div>
          <span class="text-xs font-bold uppercase tracking-widest font-mono">{{ title }}</span>
        </div>
        <button @click="emit('close')" class="text-white hover:text-accent transition-colors">
          <X class="w-4 h-4" />
        </button>
      </div>

      <div class="flex-1 overflow-y-auto p-4">
        <!-- 歌单选择 -->
        <select v-model="selectedPlaylistId" @change="loadItems" class="w-full border border-medical-200 p-2 text-sm bg-medical-50 outline-none focus:border-accent mb-3">
          <option value="" disabled>选择歌单…</option>
          <option v-for="pl in playlists" :key="pl.id" :value="pl.id">{{ pl.name }}</option>
        </select>

        <!-- 歌曲模式 -->
        <template v-if="mode === 'song'">
          <div v-if="!selectedPlaylistId" class="text-center py-10 text-xs font-mono text-medical-400">请先选择一个歌单</div>
          <div v-else-if="items.length === 0" class="text-center py-10 text-xs font-mono text-medical-400">该歌单还没有歌曲</div>
          <div v-else class="min-h-0 flex-1">
            <SongListBrowser :items="items" class="h-full">
              <template #row="{ item }">
                <div
                  @click="emit('select', item)"
                  class="flex items-center gap-3 p-2 bg-white hover:bg-accent/5 cursor-pointer transition-colors group"
                >
                  <div class="w-10 h-10 bg-medical-100 flex-shrink-0 relative overflow-hidden">
                    <img v-if="item.music.coverUrl" :src="item.music.coverUrl" class="w-full h-full object-cover" alt="" />
                    <Music2 v-else class="w-4 h-4 text-medical-400 absolute inset-0 m-auto" />
                  </div>
                  <div class="flex-1 min-w-0">
                    <div class="text-sm font-bold text-medical-800 truncate group-hover:text-accent">{{ item.music.name }}</div>
                    <div class="text-xs text-medical-500 truncate">{{ (item.music.artists || []).join(' / ') || '未知歌手' }}</div>
                    <div v-if="item.music.album" class="text-xs font-mono text-medical-400 truncate">专辑：{{ item.music.album }}</div>
                  </div>
                  <Check class="w-4 h-4 text-accent opacity-0 group-hover:opacity-100 flex-shrink-0" />
                </div>
              </template>
            </SongListBrowser>
          </div>
        </template>

        <!-- 专辑模式：从歌单歌曲提取专辑列表（去重） -->
        <template v-else>
          <div v-if="!selectedPlaylistId" class="text-center py-10 text-xs font-mono text-medical-400">请先选择一个歌单</div>
          <div v-else-if="items.length === 0" class="text-center py-10 text-xs font-mono text-medical-400">该歌单还没有歌曲</div>
          <div v-else-if="albums.length === 0" class="text-center py-10 text-xs font-mono text-medical-400">该歌单的歌曲没有专辑信息，换一个歌单试试</div>
          <div v-else class="space-y-1">
            <div
              v-for="alb in albums"
              :key="alb.music.name"
              @click="emit('select', alb)"
              class="flex items-center gap-3 p-2 border border-medical-100 bg-white hover:border-accent cursor-pointer transition-all group"
            >
              <div class="w-10 h-10 bg-medical-100 flex-shrink-0 relative overflow-hidden">
                <img v-if="alb.music.coverUrl" :src="alb.music.coverUrl" class="w-full h-full object-cover" alt="" />
                <Disc3 v-else class="w-4 h-4 text-medical-400 absolute inset-0 m-auto" />
              </div>
              <div class="flex-1 min-w-0">
                <div class="text-sm font-bold text-medical-800 truncate group-hover:text-accent">{{ alb.music.name }}</div>
                <div class="text-xs text-medical-500 truncate">{{ (alb.music.artists || []).join(' / ') || '未知歌手' }}</div>
              </div>
              <Check class="w-4 h-4 text-accent opacity-0 group-hover:opacity-100 flex-shrink-0" />
            </div>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { X, Music2, Disc3, Check } from 'lucide-vue-next'
import { usePlaylistStore } from '../stores/playlist'
import SongListBrowser from './SongListBrowser.vue';

const props = defineProps({
  title: { type: String, default: '选择歌曲' },
  mode: { type: String, default: 'song' } // 'song' | 'album'
})
const emit = defineEmits(['close', 'select'])

const playlistStore = usePlaylistStore()
const playlists = ref([])
const selectedPlaylistId = ref('')
const items = ref([])

async function loadItems() {
  if (!selectedPlaylistId.value) return
  try {
    await playlistStore.fetchItems(selectedPlaylistId.value)
    items.value = playlistStore.items || []
  } catch (e) {
    items.value = []
  }
}

// 专辑模式：按专辑名去重，取第一首的封面/歌手
const albums = computed(() => {
  const seen = new Set()
  const out = []
  for (const item of items.value) {
    const name = (item.music?.album || '').trim()
    if (!name || seen.has(name)) continue
    seen.add(name)
    out.push({
      music: {
        name,
        album: name,
        coverUrl: item.music?.coverUrl || '',
        artists: item.music?.artists || []
      }
    })
  }
  return out
})

onMounted(async () => {
  try {
    await playlistStore.fetchPlaylists()
    playlists.value = playlistStore.playlists || []
  } catch (e) { /* ignore */ }
})
</script>
