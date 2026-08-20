<template>
  <div class="fixed inset-0 z-[110] flex items-center justify-center bg-medical-900/70 backdrop-blur-sm p-4" @click.self="emit('close')">
    <div class="w-full max-w-md bg-medical-50 border border-medical-200 shadow-2xl chamfer-br max-h-[85vh] flex flex-col overflow-hidden">
      <!-- 头部 -->
      <div class="p-3 bg-medical-900 text-white flex justify-between items-center flex-shrink-0">
        <div class="flex items-center gap-2">
          <div class="w-2 h-2 bg-accent"></div>
          <span class="text-xs font-bold uppercase tracking-widest font-mono">PLAYLIST COVER / 歌单封面</span>
        </div>
        <button @click="emit('close')" class="text-white hover:text-accent transition-colors">
          <X class="w-4 h-4" />
        </button>
      </div>

      <div class="flex-1 overflow-y-auto p-4 space-y-4">
        <!-- 预览 -->
        <div class="flex items-center gap-4">
          <div class="w-20 h-20 bg-medical-200 flex items-center justify-center flex-shrink-0 relative overflow-hidden">
            <img v-if="previewUrl" :src="previewUrl" class="w-full h-full object-cover" alt="封面预览" />
            <ListMusic v-else class="w-8 h-8 text-medical-400" />
          </div>
          <div class="text-xs font-mono text-medical-400">从歌单中选一首歌，用它的封面作为歌单封面</div>
        </div>

        <!-- 歌曲列表 -->
        <div v-if="items.length === 0" class="text-center py-8 text-xs font-mono text-medical-400">
          歌单里还没有歌曲，先点歌后再来设置封面
        </div>
        <div v-else class="space-y-1">
          <div
            v-for="item in items"
            :key="item.itemId"
            @click="applyCover(item)"
            class="flex items-center gap-3 p-2 border border-medical-100 bg-white hover:border-accent cursor-pointer transition-all"
          >
            <div class="w-10 h-10 bg-medical-100 flex-shrink-0 relative overflow-hidden">
              <img v-if="item.music.coverUrl" :src="item.music.coverUrl" class="w-full h-full object-cover" alt="" />
              <Music2 v-else class="w-4 h-4 text-medical-400 absolute inset-0 m-auto" />
            </div>
            <div class="flex-1 min-w-0">
              <div class="text-sm font-bold text-medical-800 truncate">{{ item.music.name }}</div>
              <div class="text-xs text-medical-500 truncate">{{ (item.music.artists || []).join(' / ') || '未知歌手' }}</div>
            </div>
            <span class="text-xs font-mono text-accent opacity-0 group-hover:opacity-100">选用</span>
          </div>
        </div>

        <!-- 或使用图片链接 -->
        <div class="border-t border-medical-100 pt-3">
          <div class="text-xs font-bold text-medical-900 mb-1.5">或使用图片链接</div>
          <div class="flex gap-2">
            <input v-model="urlInput" placeholder="https://example.com/cover.jpg" class="flex-1 border border-medical-200 p-2 text-xs font-mono bg-medical-50 outline-none focus:border-accent" />
            <button @click="saveUrl" :disabled="!urlInput.trim()" class="px-4 py-2 bg-medical-900 text-white text-sm font-bold hover:bg-accent transition-colors disabled:opacity-40 flex-shrink-0">应用</button>
          </div>
        </div>

        <div v-if="errorMsg" class="text-xs font-mono text-red-500">{{ errorMsg }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { X, ListMusic, Music2 } from 'lucide-vue-next'
import { usePlaylistStore } from '../stores/playlist'
import { useToast } from '../composables/useToast'
import client from '../api/client'

const props = defineProps({
  playlistId: { type: [Number, String], required: true },
  currentUrl: { type: String, default: '' }
})
const emit = defineEmits(['close', 'updated'])

const playlistStore = usePlaylistStore()
const { success, error: toastError } = useToast()
const previewUrl = ref(props.currentUrl || '')
const urlInput = ref('')
const errorMsg = ref('')

const items = ref([])

watch(() => props.currentUrl, (v) => { previewUrl.value = v || '' })

onMounted(async () => {
  try {
    await playlistStore.fetchItems(props.playlistId)
    items.value = playlistStore.items || []
  } catch (e) {
    errorMsg.value = e.message || '加载歌单歌曲失败'
  }
})

async function applyCover(item) {
  const cover = item.music.coverUrl
  if (!cover) {
    errorMsg.value = '这首歌没有封面'
    return
  }
  errorMsg.value = ''
  try {
    const res = await client.put(`/api/user/playlists/${props.playlistId}/cover-url`, { url: cover })
    success(`已使用「${item.music.name}」的封面`)
    emit('updated', res.coverUrl)
    emit('close')
  } catch (e) {
    errorMsg.value = e.message || '设置失败'
  }
}

async function saveUrl() {
  const url = urlInput.value.trim()
  if (!url) return
  errorMsg.value = ''
  try {
    const res = await client.put(`/api/user/playlists/${props.playlistId}/cover-url`, { url })
    success('封面已更新')
    emit('updated', res.coverUrl)
    emit('close')
  } catch (e) {
    errorMsg.value = e.message || '应用失败'
  }
}
</script>
