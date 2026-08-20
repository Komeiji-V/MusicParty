<template>
  <div v-if="isOpen" class="fixed inset-0 z-[70] bg-medical-900/70 backdrop-blur-sm flex items-center justify-center p-2 md:p-6" @click.self="handleClose">
    <div class="w-full h-full md:h-[85vh] max-w-6xl bg-medical-50 shadow-2xl relative chamfer-br flex flex-col overflow-hidden">
      <!-- 顶部栏 -->
      <header class="h-12 bg-white border-b border-medical-200 flex items-center justify-between px-4 flex-shrink-0">
        <h2 class="font-black text-base md:text-lg tracking-tighter text-medical-900 flex items-center gap-2">
          <ListMusic class="w-5 h-5 text-accent" /> MY PLAYLISTS
        </h2>
        <div class="flex items-center gap-2">
          <button @click="openCreateModal" class="flex items-center gap-1.5 px-3 py-1.5 bg-accent hover:bg-accent-hover text-white text-xs font-bold transition-colors rounded-sm font-sans">
            <Plus class="w-3.5 h-3.5" /> <span class="hidden sm:inline">新建歌单</span>
          </button>
          <button @click="handleClose" class="p-2 text-medical-400 hover:text-medical-900 hover:bg-medical-100 transition-colors rounded-sm" title="关闭">
            <X class="w-5 h-5" />
          </button>
        </div>
      </header>

      <div class="flex-1 flex overflow-hidden">
        <!-- 左侧：分类 + 歌单列表 -->
        <aside class="w-56 md:w-64 bg-white border-r border-medical-200 flex flex-col flex-shrink-0 hidden md:flex">
          <div class="p-3 border-b border-medical-200 bg-medical-50 flex-shrink-0">
            <div class="text-xs font-mono text-medical-400 mb-2 tracking-widest">CATEGORY FILTER</div>
            <div class="flex flex-wrap gap-1">
              <button
                  @click="categoryFilter = ''"
                  class="px-2.5 py-1 text-xs font-bold transition-colors font-sans"
                  :class="categoryFilter === '' ? 'bg-medical-900 text-white' : 'bg-medical-100 text-medical-500 hover:bg-medical-200'"
              >全部</button>
              <button
                  v-for="cat in playlistStore.categories" :key="cat"
                  @click="categoryFilter = cat"
                  class="px-2.5 py-1 text-xs font-bold transition-colors font-sans"
                  :class="categoryFilter === cat ? 'bg-accent text-white' : 'bg-medical-100 text-medical-500 hover:bg-medical-200'"
              >{{ cat }}</button>
            </div>
          </div>

          <div class="flex-1 overflow-y-auto p-3 space-y-2">
            <div v-if="playlistStore.loading && playlistStore.playlists.length === 0" class="flex justify-center py-10">
              <Loader2 class="w-6 h-6 animate-spin text-accent" />
            </div>

            <div v-else-if="filteredPlaylists.length === 0" class="text-center py-10 text-medical-400 text-xs font-mono px-4">
              <div v-if="playlistStore.playlists.length === 0">NO PLAYLISTS FOUND</div>
              <div v-else>该分类下暂无歌单</div>
            </div>

            <div
                v-for="pl in filteredPlaylists" :key="pl.id"
                @click="selectPlaylist(pl)"
                class="p-3 border transition-all cursor-pointer group"
                :class="selected?.id === pl.id ? 'border-accent bg-medical-50 shadow-sm' : 'border-medical-200 hover:border-medical-300 bg-white'"
            >
              <div class="flex items-center gap-3">
                <div class="w-11 h-11 bg-medical-200 flex-shrink-0 overflow-hidden relative">
                  <CoverImage :src="pl.coverUrl" class="w-full h-full" />
                  <div v-if="!pl.coverUrl" class="absolute inset-0 flex items-center justify-center">
                    <ListMusic class="w-5 h-5 text-medical-400" />
                  </div>
                </div>
                <div class="flex-1 min-w-0">
                  <div class="text-sm font-bold truncate group-hover:text-accent">{{ pl.name }}</div>
                  <div class="flex items-center gap-1.5 mt-1 flex-wrap">
                    <span v-if="pl.category" class="px-1.5 py-0.5 text-xs font-mono font-bold text-accent border border-accent/40 bg-accent/10 rounded-sm">{{ pl.category }}</span>
                    <span class="px-1.5 py-0.5 text-xs font-mono font-bold text-medical-500 bg-medical-100 rounded-sm">{{ pl.itemCount }} TRACKS</span>
                    <span v-if="pl.isPublic" class="px-1.5 py-0.5 text-xs font-mono font-bold text-green-700 bg-green-100 rounded-sm">PUBLIC</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </aside>

        <!-- 移动端：分类横向滚动 -->
        <div class="md:hidden flex-shrink-0 bg-white border-b border-medical-200 px-3 py-2 flex gap-1 overflow-x-auto absolute top-12 left-0 right-0 z-10">
          <button @click="categoryFilter = ''" class="px-3 py-1 text-xs font-bold whitespace-nowrap font-sans"
                  :class="categoryFilter === '' ? 'bg-medical-900 text-white' : 'bg-medical-100 text-medical-500'">全部</button>
          <button v-for="cat in playlistStore.categories" :key="cat" @click="categoryFilter = cat"
                  class="px-3 py-1 text-xs font-bold whitespace-nowrap font-sans"
                  :class="categoryFilter === cat ? 'bg-accent text-white' : 'bg-medical-100 text-medical-500'">{{ cat }}</button>
        </div>

        <!-- 右侧：歌单详情 -->
        <main class="flex-1 bg-medical-100/30 flex flex-col min-w-0 relative">
          <!-- 未选中：选择提示 -->
          <div v-if="!selected" class="flex-1 flex flex-col items-center justify-center gap-4 p-6 text-center">
            <div class="w-16 h-16 bg-white border border-medical-200 flex items-center justify-center chamfer-br">
              <ListMusic class="w-8 h-8 text-medical-300" />
            </div>
            <div class="font-mono text-xs text-medical-400">SELECT A PLAYLIST TO VIEW DETAILS</div>
            <button v-if="playlistStore.playlists.length === 0" @click="openCreateModal"
                    class="px-6 py-2.5 bg-accent text-white font-bold text-sm hover:bg-accent-hover transition-colors font-sans">
              创建你的第一个歌单
            </button>
          </div>

          <!-- 选中：详情 -->
          <template v-else>
            <div class="p-3 md:p-5 bg-white border-b border-medical-200 flex-shrink-0 flex flex-wrap items-center gap-3">
              <div class="w-12 h-12 md:w-14 md:h-14 bg-medical-200 flex-shrink-0 overflow-hidden relative chamfer-br">
                <CoverImage :src="selected.coverUrl" class="w-full h-full" />
                <div v-if="!selected.coverUrl" class="absolute inset-0 flex items-center justify-center">
                  <ListMusic class="w-6 h-6 text-medical-400" />
                </div>
              </div>
              <div class="flex-1 min-w-0">
                <div class="text-xs font-mono text-medical-400 tracking-widest">PLAYLIST DETAILS</div>
                <h2 class="text-lg md:text-xl font-black text-medical-900 truncate">{{ selected.name }}</h2>
                <div class="flex items-center gap-2 mt-0.5 flex-wrap">
                  <span v-if="selected.category" class="px-1.5 py-0.5 text-xs font-mono font-bold text-accent border border-accent/40 bg-accent/10 rounded-sm">{{ selected.category }}</span>
                  <span class="text-xs font-mono text-medical-400">{{ playlistStore.items.length }} LOADED</span>
                  <span v-if="selected.isPublic" class="text-xs font-mono text-green-600 font-bold">PUBLIC</span>
                </div>
              </div>
              <div class="flex items-center gap-1.5 flex-wrap">
                <button @click="enqueueAll" :disabled="playlistStore.items.length === 0"
                        class="px-3 py-2 bg-medical-900 text-white text-xs font-bold hover:bg-accent transition-colors disabled:opacity-40 flex items-center gap-1.5 rounded-sm font-sans">
                  <Play class="w-3.5 h-3.5" /> 全部点歌
                </button>
                <button @click="openEditModal" title="编辑歌单" class="px-3 py-2 border border-medical-200 text-medical-600 text-xs font-bold hover:bg-medical-100 transition-colors flex items-center gap-1.5 rounded-sm font-sans">
                  <Pencil class="w-3.5 h-3.5" />
                </button>
                <button @click="showCoverModal = true" title="设置封面" class="px-2.5 py-2 border border-medical-200 text-medical-600 text-xs font-bold hover:bg-medical-100 transition-colors flex items-center gap-1 rounded-sm font-sans">
                  <ImageIcon class="w-3.5 h-3.5" />
                </button>
                <button @click="handleExport('json')" title="导出 JSON" class="px-2.5 py-2 border border-medical-200 text-medical-600 text-xs font-bold hover:bg-medical-100 transition-colors flex items-center gap-1 rounded-sm font-sans">
                  <FileJson class="w-3.5 h-3.5" />
                </button>
                <button @click="handleExport('txt')" title="导出 TXT" class="px-2.5 py-2 border border-medical-200 text-medical-600 text-xs font-bold hover:bg-medical-100 transition-colors flex items-center gap-1 rounded-sm font-sans">
                  <FileText class="w-3.5 h-3.5" />
                </button>
                <button @click="confirmDelete" class="px-2.5 py-2 border border-red-200 text-red-500 text-xs font-bold hover:bg-red-50 transition-colors flex items-center gap-1 rounded-sm font-sans">
                  <Trash2 class="w-3.5 h-3.5" />
                </button>
              </div>
            </div>

            <div class="flex-1 overflow-y-auto p-3">
              <div v-if="playlistStore.loading" class="flex justify-center py-12">
                <Loader2 class="w-6 h-6 animate-spin text-accent" />
              </div>

              <div v-else-if="playlistStore.items.length === 0" class="text-center py-16 text-medical-400 text-xs font-mono">
                EMPTY PLAYLIST — 从搜索页收藏歌曲到此处
              </div>

              <div v-else class="space-y-1">
                <div v-for="(item, idx) in playlistStore.items" :key="item.itemId"
                     class="flex items-center p-2.5 bg-white border border-medical-200 hover:border-medical-300 hover:shadow-sm transition-all group">
                  <span class="w-7 text-right text-xs font-mono text-medical-300 flex-shrink-0">{{ idx + 1 }}</span>
                  <div class="w-10 h-10 bg-medical-200 flex-shrink-0 relative overflow-hidden ml-2">
                    <CoverImage :src="item.music.coverUrl" class="w-full h-full" />
                  </div>
                  <div class="flex-1 min-w-0 ml-3">
                    <div class="text-sm font-bold truncate">{{ item.music.name }}</div>
                    <div class="text-xs text-medical-500 truncate">{{ (item.music.artists || []).join(' / ') || '未知歌手' }}</div>
                  </div>
                  <div class="flex-shrink-0 ml-2">
                    <span class="px-1.5 py-0.5 text-xs font-mono font-bold rounded-sm" :class="platformBadge(item.music.platform).cls">
                      {{ platformBadge(item.music.platform).label }}
                    </span>
                  </div>
                  <div class="hidden md:block w-16 text-right text-xs font-mono text-medical-400 ml-2 flex-shrink-0">
                    {{ formatDuration(item.music.duration) }}
                  </div>
                  <button @click="enqueueOne(item.music)" title="点歌"
                          class="ml-2 p-2 text-medical-300 hover:text-accent transition-colors flex-shrink-0">
                    <PlusCircle class="w-5 h-5" />
                  </button>
                  <button @click="handleRemove(item)" title="移除"
                          class="p-2 text-medical-300 hover:text-red-500 transition-colors flex-shrink-0">
                    <X class="w-5 h-5" />
                  </button>
                </div>
              </div>
            </div>
          </template>
        </main>
      </div>

      <!-- 创建弹窗 -->
      <div v-if="showCreate" class="absolute inset-0 z-[75] bg-medical-900/80 backdrop-blur-sm flex items-center justify-center p-4">
        <div class="w-full max-w-md bg-medical-50 shadow-2xl relative chamfer-br p-5">
          <button @click="showCreate = false" class="absolute top-2 right-2 p-2 hover:text-accent"><X class="w-5 h-5" /></button>
          <h3 class="text-lg font-bold font-mono text-medical-900 mb-4 flex items-center gap-2">
            <Plus class="w-5 h-5 text-accent" /> 新建歌单
          </h3>
          <div class="flex flex-col gap-3">
            <div>
              <label class="text-xs font-mono text-medical-400 mb-1 block">名称 *</label>
              <input v-model="form.name" placeholder="歌单名称"
                     class="w-full border border-medical-200 bg-white p-2.5 outline-none focus:border-accent text-sm font-sans" />
            </div>
            <div>
              <label class="text-xs font-mono text-medical-400 mb-1 block">分类</label>
              <div class="flex gap-2">
                <select v-model="form.category" class="flex-1 border border-medical-200 bg-white p-2.5 outline-none focus:border-accent text-sm font-sans">
                  <option value="">未分类</option>
                  <option v-for="cat in playlistStore.categories" :key="cat" :value="cat">{{ cat }}</option>
                </select>
                <input v-model="customCategory" placeholder="自定义分类"
                       class="flex-1 border border-medical-200 bg-white p-2.5 outline-none focus:border-accent text-sm font-sans" />
              </div>
            </div>
            <label class="flex items-center gap-2 cursor-pointer select-none">
              <input type="checkbox" v-model="form.isPublic" class="accent-medical-900 w-4 h-4" />
              <span class="text-sm font-sans text-medical-600">公开歌单</span>
            </label>
            <div class="flex gap-2 mt-2">
              <button @click="handleCreate" :disabled="!form.name.trim()"
                      class="flex-1 bg-accent text-white py-2.5 font-bold text-sm hover:bg-accent-hover transition-colors disabled:opacity-40 font-sans">
                创建
              </button>
              <button @click="showCreate = false" class="px-5 py-2.5 text-sm font-bold text-medical-500 hover:bg-medical-100 font-sans">取消</button>
            </div>
          </div>
        </div>
      </div>

      <!-- 编辑弹窗 -->
      <div v-if="showEdit" class="absolute inset-0 z-[75] bg-medical-900/80 backdrop-blur-sm flex items-center justify-center p-4">
        <div class="w-full max-w-md bg-medical-50 shadow-2xl relative chamfer-br p-5">
          <button @click="showEdit = false" class="absolute top-2 right-2 p-2 hover:text-accent"><X class="w-5 h-5" /></button>
          <h3 class="text-lg font-bold font-mono text-medical-900 mb-4 flex items-center gap-2">
            <Pencil class="w-5 h-5 text-accent" /> 编辑歌单
          </h3>
          <div class="flex flex-col gap-3">
            <div>
              <label class="text-xs font-mono text-medical-400 mb-1 block">名称</label>
              <input v-model="form.name" placeholder="歌单名称"
                     class="w-full border border-medical-200 bg-white p-2.5 outline-none focus:border-accent text-sm font-sans" />
            </div>
            <div>
              <label class="text-xs font-mono text-medical-400 mb-1 block">分类</label>
              <div class="flex gap-2">
                <select v-model="form.category" class="flex-1 border border-medical-200 bg-white p-2.5 outline-none focus:border-accent text-sm font-sans">
                  <option value="">未分类</option>
                  <option v-for="cat in playlistStore.categories" :key="cat" :value="cat">{{ cat }}</option>
                </select>
                <input v-model="customCategory" placeholder="自定义分类"
                       class="flex-1 border border-medical-200 bg-white p-2.5 outline-none focus:border-accent text-sm font-sans" />
              </div>
            </div>
            <label class="flex items-center gap-2 cursor-pointer select-none">
              <input type="checkbox" v-model="form.isPublic" class="accent-medical-900 w-4 h-4" />
              <span class="text-sm font-sans text-medical-600">公开歌单</span>
            </label>
            <div class="flex gap-2 mt-2">
              <button @click="handleUpdate" :disabled="!form.name.trim()"
                      class="flex-1 bg-medical-900 text-white py-2.5 font-bold text-sm hover:bg-accent transition-colors disabled:opacity-40 font-sans">
                保存
              </button>
              <button @click="showEdit = false" class="px-5 py-2.5 text-sm font-bold text-medical-500 hover:bg-medical-100 font-sans">取消</button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 封面设置弹窗 -->
    <PlaylistCoverModal
      v-if="showCoverModal && selected"
      :playlist-id="selected.id"
      :current-url="selected.coverUrl"
      @close="showCoverModal = false"
      @updated="onCoverUpdated"
    />
  </div>
</template>

<script setup>
import { ref, computed, watch, onBeforeUnmount } from 'vue';
import {
  ListMusic, Plus, X, Pencil, Trash2, Play, PlusCircle, FileJson, FileText, Loader2, Image as ImageIcon
} from 'lucide-vue-next';
import { usePlaylistStore } from '../stores/playlist';
import { usePlayerStore } from '../stores/player';
import PlaylistCoverModal from './PlaylistCoverModal.vue';
import { useToast } from '../composables/useToast';
import { formatDuration } from '../utils/format';
import CoverImage from './CoverImage.vue';

import { useConfirmStore } from '../stores/confirm'

const confirmStore = useConfirmStore()
const confirm = (message, title = '确认操作', danger = true) => confirmStore.ask({ title, message, danger })

const props = defineProps({
  isOpen: { type: Boolean, default: false }
});
const emit = defineEmits(['close']);

const playlistStore = usePlaylistStore();
const playerStore = usePlayerStore();
const { success, error, info } = useToast();

const selected = ref(null);
const categoryFilter = ref('');
const showCoverModal = ref(false);
const showCreate = ref(false);
const showEdit = ref(false);
const customCategory = ref('');
const form = ref({ name: '', category: '', isPublic: false });

const filteredPlaylists = computed(() => {
  if (!categoryFilter.value) return playlistStore.playlists;
  return playlistStore.playlists.filter(p => p.category === categoryFilter.value);
});

const platformBadge = (platform) => {
  switch (platform) {
    case 'netease': return { label: 'N', cls: 'text-red-600 bg-red-100' };
    case 'qq': return { label: 'Q', cls: 'text-green-700 bg-green-100' };
    case 'kugou': return { label: 'K', cls: 'text-blue-600 bg-blue-100' };
    case 'bilibili': return { label: 'B', cls: 'text-pink-600 bg-pink-100' };
    default: return { label: '?', cls: 'text-medical-500 bg-medical-100' };
  }
};

const selectPlaylist = async (pl) => {
  selected.value = pl;
  await playlistStore.fetchItems(pl.id);
};

const openCreateModal = () => {
  form.value = { name: '', category: '', isPublic: false };
  customCategory.value = '';
  showCreate.value = true;
};

const openEditModal = () => {
  if (!selected.value) return;
  form.value = {
    name: selected.value.name,
    category: selected.value.category || '',
    isPublic: selected.value.isPublic
  };
  customCategory.value = '';
  showEdit.value = true;
};

const resolveCategory = () => customCategory.value.trim() || form.value.category;

const handleCreate = async () => {
  try {
    await playlistStore.createPlaylist(form.value.name.trim(), resolveCategory(), form.value.isPublic);
    showCreate.value = false;
    success('歌单已创建');
    await playlistStore.fetchCategories();
    if (!selected.value && playlistStore.playlists.length > 0) {
      await selectPlaylist(playlistStore.playlists[0]);
    }
  } catch (e) {
    error(e.message || '创建失败');
  }
};

const handleUpdate = async () => {
  if (!selected.value) return;
  try {
    await playlistStore.updatePlaylist(selected.value.id, {
      name: form.value.name.trim(),
      category: resolveCategory(),
      isPublic: form.value.isPublic
    });
    showEdit.value = false;
    success('歌单已更新');
  } catch (e) {
    error(e.message || '更新失败');
  }
};

const confirmDelete = async () => {
  if (!selected.value) return;
  if (!(await confirm(`确定删除歌单「${selected.value.name}」？其中的歌曲将被移除`))) return;
  try {
    await playlistStore.deletePlaylist(selected.value.id);
    selected.value = null;
    success('歌单已删除');
  } catch (e) {
    error(e.message || '删除失败');
  }
};

const handleExport = async (format) => {
  if (!selected.value) return;
  try {
    await playlistStore.exportPlaylist(selected.value.id, format);
    info(`已导出 ${format.toUpperCase()} 文件`);
  } catch (e) {
    error(e.message || '导出失败');
  }
};

const enqueueOne = (music) => {
  if (!playerStore.connected) {
    error('未连接到房间，请先在首页进入房间');
    return;
  }
  playerStore.enqueue(music.platform, music.id);
  info(`已点歌: ${music.name}`);
};

const enqueueAll = () => {
  if (!playerStore.connected) {
    error('未连接到房间，请先在首页进入房间');
    return;
  }
  if (playlistStore.items.length === 0) return;
  playlistStore.items.forEach(item => {
    playerStore.enqueue(item.music.platform, item.music.id);
  });
  info(`已全部点歌: ${playlistStore.items.length} 首`);
};

const handleRemove = async (item) => {
  if (!selected.value) return;
  try {
    await playlistStore.removeItem(selected.value.id, item.itemId);
    playlistStore.items = playlistStore.items.filter(i => i.itemId !== item.itemId);
    success('歌曲已移除');
  } catch (e) {
    error(e.message || '移除失败');
  }
};

function onCoverUpdated(url) {
  if (selected.value) selected.value.coverUrl = url
}

const handleClose = () => {
  showCreate.value = false;
  showEdit.value = false;
  emit('close');
};

// 打开时强制刷新数据（避免新添加的歌曲/新建的歌单不显示）
watch(() => props.isOpen, async (open) => {
  if (open) {
    await playlistStore.fetchPlaylists();
    playlistStore.fetchCategories();
    if (selected.value) {
      await selectPlaylist(selected.value);
    } else if (playlistStore.playlists.length > 0) {
      await selectPlaylist(playlistStore.playlists[0]);
    }
  }
});

function onKeydown(e) {
  if (e.key === 'Escape' && props.isOpen) handleClose();
}

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown);
});

// Esc 关闭
watch(() => props.isOpen, (open) => {
  if (open) document.addEventListener('keydown', onKeydown);
  else document.removeEventListener('keydown', onKeydown);
});
</script>
