<template>
  <div class="fixed inset-0 z-[80] bg-medical-900/80 backdrop-blur-sm flex items-center justify-center p-4">
    <div class="w-full max-w-md bg-medical-50 shadow-2xl relative chamfer-br max-h-[80vh] flex flex-col">
      <button @click="emit('close')" class="absolute top-0 right-0 p-3 hover:text-accent z-10">
        <X class="w-5 h-5" />
      </button>

      <!-- 头部 -->
      <div class="p-4 md:p-5 border-b border-medical-200 bg-white flex-shrink-0">
        <h3 class="text-lg font-bold font-mono text-medical-900 flex items-center gap-2">
          <BookmarkPlus class="w-5 h-5 text-accent" /> 收藏到歌单
        </h3>
        <div class="text-xs text-medical-500 mt-1 truncate font-sans">
          {{ music?.name }} <span v-if="music?.artists?.length" class="text-medical-400">- {{ music.artists.join(' / ') }}</span>
        </div>
      </div>

      <!-- 歌单列表 -->
      <div class="flex-1 overflow-y-auto p-3 space-y-1.5">
        <div v-if="loading" class="flex justify-center py-8">
          <Loader2 class="w-6 h-6 animate-spin text-accent" />
        </div>

        <div v-else-if="playlistStore.playlists.length === 0" class="p-4 border border-dashed border-medical-300 bg-white text-center text-xs text-medical-400 font-mono py-8">
          暂无歌单，请先创建一个
        </div>

        <div
            v-for="pl in playlistStore.playlists" :key="pl.id"
            @click="handleAdd(pl)"
            class="flex items-center gap-3 p-2.5 bg-white border border-medical-200 hover:border-accent cursor-pointer transition-all group"
        >
          <div class="w-10 h-10 bg-medical-200 flex-shrink-0 overflow-hidden">
            <CoverImage :src="pl.coverUrl" class="w-full h-full" />
          </div>
          <div class="flex-1 min-w-0">
            <div class="text-sm font-bold truncate group-hover:text-accent">{{ pl.name }}</div>
            <div class="text-xs font-mono text-medical-400">{{ pl.category || '未分类' }} · {{ pl.itemCount }} TRACKS</div>
          </div>
          <Plus class="w-4 h-4 text-medical-300 group-hover:text-accent flex-shrink-0" />
        </div>
      </div>

      <!-- 新建歌单快速入口 -->
      <div class="border-t border-medical-200 bg-white p-3 flex-shrink-0">
        <div v-if="!creating" class="flex items-center gap-2">
          <button @click="creating = true" class="flex-1 flex items-center justify-center gap-2 border border-dashed border-accent text-accent hover:bg-accent hover:text-white transition-colors py-2 text-sm font-bold font-sans">
            <ListPlus class="w-4 h-4" /> 新建歌单
          </button>
        </div>
        <div v-else class="flex flex-col gap-2">
          <input
              v-model="newName" placeholder="歌单名称"
              @keyup.enter="handleCreateAndAdd"
              class="border border-medical-200 p-2 outline-none focus:border-accent text-sm bg-medical-50 font-sans"
          />
          <input
              v-model="newCategory" placeholder="分类（可选，如：华语 / 日系 / 游戏OST）"
              class="border border-medical-200 p-2 outline-none focus:border-accent text-sm bg-medical-50 font-sans"
          />
          <div class="flex gap-2">
            <button @click="handleCreateAndAdd" :disabled="!newName.trim()" class="flex-1 bg-accent text-white py-2 text-sm font-bold transition-colors disabled:opacity-40 font-sans">
              创建并加入
            </button>
            <button @click="creating = false" class="px-4 py-2 text-sm font-bold text-medical-500 hover:bg-medical-100 font-sans">
              取消
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue';
import { X, BookmarkPlus, Plus, ListPlus, Loader2 } from 'lucide-vue-next';
import { usePlaylistStore } from '../stores/playlist';
import { useToast } from '../composables/useToast';
import CoverImage from './CoverImage.vue';

const props = defineProps({
  music: { type: Object, required: true }
});
const emit = defineEmits(['close']);

const playlistStore = usePlaylistStore();
const { success, error } = useToast();

const loading = ref(false);
const creating = ref(false);
const newName = ref('');
const newCategory = ref('');

watch(() => props.music, () => {
  creating.value = false;
  newName.value = '';
  newCategory.value = '';
  if (playlistStore.playlists.length === 0) {
    loading.value = true;
    playlistStore.fetchPlaylists()
        .then(() => {
          // 没有任何歌单时直接展开"新建歌单"表单，避免用户以为没反应
          if (playlistStore.playlists.length === 0) {
            creating.value = true;
          }
        })
        .catch(e => error(e.message || '加载歌单失败'))
        .finally(() => { loading.value = false; });
  }
}, { immediate: true });

const handleAdd = async (pl) => {
  try {
    await playlistStore.addItem(pl.id, props.music);
    success(`已收藏到「${pl.name}」`);
    emit('close');
  } catch (e) {
    error(e.message || '收藏失败');
  }
};

const handleCreateAndAdd = async () => {
  if (!newName.value.trim()) return;
  try {
    const pl = await playlistStore.createPlaylist(newName.value.trim(), newCategory.value.trim());
    await playlistStore.addItem(pl.id, props.music);
    success(`已创建并收藏到「${pl.name}」`);
    emit('close');
  } catch (e) {
    error(e.message || '创建失败');
  }
};
</script>
