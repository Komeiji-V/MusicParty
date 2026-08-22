<template>
  <div class="flex flex-col min-h-0 w-full">
    <!-- 控件行：排序组合 + 平台筛选 + 搜索 -->
    <div class="flex flex-wrap items-center gap-2 p-2 border-b border-medical-200 bg-white flex-shrink-0">
      <select v-model="sortMode" title="排序方式"
              class="border border-medical-200 bg-medical-50 p-1.5 text-xs font-bold text-medical-700 outline-none focus:border-accent font-sans">
        <option value="az-all">首字母 正序 · 不分平台</option>
        <option value="za-all">首字母 倒序 · 不分平台</option>
        <option value="az-group">首字母 正序 · 分平台</option>
        <option value="za-group">首字母 倒序 · 分平台</option>
      </select>
      <select v-model="platformFilter" title="平台筛选"
              class="border border-medical-200 bg-medical-50 p-1.5 text-xs font-bold text-medical-700 outline-none focus:border-accent font-sans">
        <option value="">全部平台</option>
        <option value="netease">网易云</option>
        <option value="qq">QQ音乐</option>
        <option value="kugou">酷狗</option>
        <option value="bilibili">B站</option>
      </select>
      <input v-model="keyword" placeholder="搜索歌名 / 歌手..."
             class="flex-1 min-w-[120px] border border-medical-200 bg-medical-50 p-1.5 text-xs outline-none focus:border-accent font-sans" />
      <span class="text-[10px] font-mono text-medical-400">{{ filtered.length }} 首</span>
    </div>

    <div class="flex-1 flex min-h-0">
      <!-- 分组列表 -->
      <div ref="scrollRef" class="flex-1 overflow-y-auto min-w-0">
        <div v-if="filtered.length === 0" class="text-center py-10 text-xs font-mono text-medical-400">NO DATA FOUND</div>
        <template v-for="g in groups" :key="g.letter + g.platform">
          <div :ref="el => { if (el) letterEls[g.letter] = el }"
               class="sticky top-0 z-10 bg-medical-100/95 backdrop-blur-sm px-2 py-1 text-[11px] font-bold text-medical-500 tracking-widest border-b border-medical-200">
            {{ g.platform ? platformLabel(g.platform) + ' · ' : '' }}{{ g.letter }}
          </div>
          <div v-for="(item, i) in g.items" :key="item.itemId" class="border-b border-medical-50">
            <slot name="row" :item="item" :index="i" />
          </div>
        </template>
      </div>

      <!-- 首字母索引 -->
      <div class="w-6 flex-shrink-0 flex flex-col items-center py-1 gap-px bg-white border-l border-medical-200">
        <button v-for="l in letters" :key="l" @click="scrollToLetter(l)"
                class="w-5 h-4 text-[10px] leading-none font-bold text-medical-400 hover:text-accent hover:bg-medical-100 rounded-sm transition-colors"
                :class="activeLetter === l ? 'text-accent bg-medical-100' : ''">
          {{ l }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, nextTick } from 'vue';
import { pinyin } from 'pinyin-pro';

const props = defineProps({
  items: { type: Array, default: () => [] }, // [{ itemId, music: { name, artists, platform, album, coverUrl } }]
});

const sortMode = ref('az-all'); // az-all | za-all | az-group | za-group
const platformFilter = ref('');
const keyword = ref('');
const scrollRef = ref(null);
const letterEls = ref({});
const activeLetter = ref('');

const PLATFORM_ORDER = ['netease', 'qq', 'kugou', 'bilibili'];

const platformLabel = (p) => ({
  netease: '网易云', qq: 'QQ音乐', kugou: '酷狗', bilibili: 'B站',
}[p] || p);

// 首字母：中文取拼音首字母，英文取首字母，其他归 '#'
const firstLetter = (name) => {
  const n = (name || '').trim();
  if (!n) return '#';
  const ch = n.charAt(0);
  if (/[a-zA-Z]/.test(ch)) return ch.toUpperCase();
  if (/[\u4e00-\u9fa5]/.test(ch)) {
    const py = pinyin(ch, { pattern: 'first', toneType: 'none' });
    const L = (py || '').charAt(0).toUpperCase();
    return /[A-Z]/.test(L) ? L : '#';
  }
  return '#';
};

// 筛选：平台 + 关键字（歌名/歌手）
const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase();
  return props.items.filter(it => {
    const m = it.music || {};
    if (platformFilter.value && m.platform !== platformFilter.value) return false;
    if (!kw) return true;
    const name = (m.name || '').toLowerCase();
    const artists = (Array.isArray(m.artists) ? m.artists.join(' ') : String(m.artists || '')).toLowerCase();
    return name.includes(kw) || artists.includes(kw);
  });
});

// 排序 + 分组
const groups = computed(() => {
  const desc = sortMode.value.startsWith('za');
  const grouped = sortMode.value.endsWith('group');
  const cmp = (a, b) => {
    const r = String(a.music?.name || '').localeCompare(String(b.music?.name || ''), 'zh');
    return desc ? -r : r;
  };
  const out = [];
  const pushGroup = (letter, platform, items) => {
    const last = out[out.length - 1];
    if (last && last.letter === letter && last.platform === platform) {
      last.items.push(...items);
    } else {
      out.push({ letter, platform, items: [...items] });
    }
  };
  if (grouped) {
    // 分平台：平台段固定顺序，段内按首字母分组
    for (const pf of PLATFORM_ORDER) {
      const seg = filtered.value.filter(it => (it.music?.platform || '') === pf).sort(cmp);
      for (const it of seg) pushGroup(firstLetter(it.music?.name), pf, [it]);
    }
  } else {
    const sorted = [...filtered.value].sort(cmp);
    for (const it of sorted) pushGroup(firstLetter(it.music?.name), '', [it]);
  }
  return out;
});

const letters = computed(() => {
  const set = new Set();
  for (const g of groups.value) set.add(g.letter);
  return [...set].sort((a, b) => (a === '#' ? 1 : b === '#' ? -1 : a < b ? -1 : 1));
});

const scrollToLetter = async (l) => {
  const el = letterEls.value[l];
  if (!el) return;
  activeLetter.value = l;
  scrollRef.value?.scrollTo({ top: el.offsetTop - scrollRef.value.offsetTop, behavior: 'smooth' });
  setTimeout(() => { activeLetter.value = ''; }, 800);
};
</script>
