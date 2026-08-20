// ... existing code ...
<template>
  <!-- full 样式：整体 justify-start 让封面左移，为右侧歌词让位 -->
  <div
      class="relative w-full h-full flex items-center overflow-hidden transition-all duration-500"
      :class="player.lyricStyle === 'full' ? 'justify-start pl-[8%] md:pl-[12%]' : 'justify-center'"
  >

    <!-- LAYER 0: 静态背景层 (最底层) -->
    <div class="absolute inset-0 z-0 pointer-events-none">
      <div class="absolute inset-0 bg-[linear-gradient(rgba(17,24,39,0.03)_1px,transparent_1px),linear-gradient(90deg,rgba(17,24,39,0.03)_1px,transparent_1px)] bg-[size:40px_40px]"></div>
      <div class="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 text-[12vw] font-black text-medical-200/40 select-none whitespace-nowrap tracking-tighter blur-sm">
        {{ uiStore.backWords.toUpperCase() }}
      </div>
      <!-- 四角标记 -->
      <div class="absolute top-8 left-8 w-8 h-8 border-t-2 border-l-2 border-medical-300"></div>
      <div class="absolute top-8 right-8 w-8 h-8 border-t-2 border-r-2 border-medical-300"></div>
      <div class="absolute bottom-8 left-8 w-8 h-8 border-b-2 border-l-2 border-medical-300"></div>
      <div class="absolute bottom-8 right-8 w-8 h-8 border-b-2 border-r-2 border-medical-300"></div>
    </div>

    <!-- LAYER 1: 动态视觉层 (Canvas) -->
    <div class="absolute inset-0 z-10 flex items-center justify-center pointer-events-none">
      <canvas
          ref="canvasRef"
          width="1200"
          height="1200"
          class="absolute left-1/2 top-1/2 -translate-x-1/4 -translate-y-1/3 w-[160vw] h-[160vw] md:w-[1000px] md:h-[1000px]"
      ></canvas>

      <!-- 旋转圈圈 (CSS动画) -->
      <div class="absolute inset-0 w-[320px] h-[320px] m-auto border border-medical-200 rounded-full animate-[spin_10s_linear_infinite] opacity-30 border-dashed"></div>
      <div class="absolute inset-0 w-[340px] h-[340px] m-auto border border-medical-200 rounded-full animate-[spin_15s_linear_infinite_reverse] opacity-20"></div>
    </div>

    <!-- LAYER 2: 信息层 (歌词 & 日志) -->
    <div class="absolute inset-0 z-20 pointer-events-none">
      <!-- 歌词：compact=左下角 / full=封面右侧滚动 -->
      <div class="absolute font-mono transition-all duration-300" :class="lyricWrapClass">
        <!-- 标题行：LYRIC_SYSTEM + 翻译/罗马音开关 + 样式切换按钮 -->
        <div class="pointer-events-auto flex items-center gap-2 mb-1 min-h-0 flex-shrink-0">
          <div class="hidden md:block text-xs text-accent/80 tracking-widest border-b border-accent/30 pb-1">
            LYRIC_SYSTEM
          </div>
          <!-- 翻译开关（仅网易云翻译可用时显示） -->
          <button
              v-if="player.lyricStyle === 'full' && player.lyricFull.tlyric"
              @click="player.showTranslation = !player.showTranslation"
              class="px-1.5 py-0.5 text-[10px] font-bold border rounded-sm transition-colors"
              :class="player.showTranslation ? 'bg-accent text-white border-accent' : 'text-medical-400 border-medical-300 hover:text-accent hover:border-accent'"
          >翻译</button>
          <!-- 罗马音开关（仅网易云罗马音可用时显示） -->
          <button
              v-if="player.lyricStyle === 'full' && player.lyricFull.romalrc"
              @click="player.showRoman = !player.showRoman"
              class="px-1.5 py-0.5 text-[10px] font-bold border rounded-sm transition-colors"
              :class="player.showRoman ? 'bg-accent text-white border-accent' : 'text-medical-400 border-medical-300 hover:text-accent hover:border-accent'"
          >罗马音</button>
          <!-- 样式切换 -->
          <button
              @click="toggleLyricStyle"
              :title="player.lyricStyle === 'compact' ? '展开歌词（封面左移+滚动）' : '收起歌词（原样式）'"
              class="ml-auto flex items-center justify-center w-6 h-6 text-medical-400 hover:text-accent transition-colors border border-medical-200 hover:border-accent rounded-sm bg-white/60 backdrop-blur-sm"
          >
            <Maximize2 v-if="player.lyricStyle === 'compact'" class="w-3.5 h-3.5" />
            <Minimize2 v-else class="w-3.5 h-3.5" />
          </button>
        </div>

        <!-- compact：左下角当前行（原样式） -->
        <div
            v-if="player.lyricStyle === 'compact'"
            class="w-full space-y-1 text-xs font-normal text-medical-900 leading-tight mix-blend-normal md:mix-blend-multiply md:text-medical-600 flex flex-col md:justify-end min-h-0"
        >
          <div v-if="parsedLyrics.length === 0" class="opacity-50 flex items-center justify-center md:justify-start">
            <span class="text-accent/50 mr-2 text-xs">></span>NO_DATA_STREAM
          </div>
          <div
              v-else
              v-for="(line, i) in activeLines"
              :key="line.time"
              class="transition-all duration-300 flex items-center md:justify-start justify-center"
              :class="i === activeLines.length - 1 ? 'opacity-100 scale-105 md:scale-100 text-medical-900' : 'opacity-40 blur-[0.5px]'"
          >
            <span class="hidden md:inline text-accent mr-2 text-xs" :class="{'animate-pulse': i === activeLines.length - 1}">></span>
            <span :class="{'bg-medical-900 text-white px-1': i === activeLines.length - 1 && isMobile}">
                     {{ line.text }}
            </span>
          </div>
        </div>

        <!-- full：封面右侧滚动歌词（当前行居中高亮 + 上下渐变） -->
        <div
            v-else
            class="pointer-events-auto relative flex-1 min-h-0 overflow-hidden"
        >
          <!-- Apple Music 风格聚焦歌词：当前行大字居中，前后行小字淡出（行原地切换，不滚动） -->
          <div class="absolute inset-0 flex flex-col items-center justify-center">
            <div v-if="parsedLyrics.length === 0" class="opacity-50 flex items-center justify-center">
              <span class="text-accent/50 mr-2 text-xs">></span>NO_DATA_STREAM
            </div>
            <div
                v-else
                v-for="(line, i) in activeWindow"
                :key="line.time"
                class="w-full text-center px-1 transition-all duration-500 leading-relaxed"
                :class="line._center
                  ? 'text-base md:text-2xl font-bold text-medical-900'
                  : line._near
                    ? 'text-xs md:text-sm text-medical-500 opacity-60'
                    : 'text-[10px] md:text-xs text-medical-300 opacity-30'"
            >
              <div>{{ line.text }}</div>
              <div v-if="line._center && player.showTranslation && line.trans" class="text-[10px] md:text-sm text-accent font-normal mt-0.5">{{ line.trans }}</div>
              <div v-if="line._center && player.showRoman && line.roman" class="text-[10px] md:text-xs text-medical-400 italic font-normal mt-0.5">{{ line.roman }}</div>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧：伪系统日志 -->
      <div class="absolute bottom-10 right-10 font-mono text-xs text-medical-400 text-right space-y-1 hidden md:block opacity-60">
        <div v-for="(log, i) in logs" :key="i" class="animate-pulse">
          {{ log }} <
        </div>
      </div>
    </div>

    <!-- LAYER 3: 核心实体层 (封面)；左移由根 flex 控制 -->
    <div class="relative z-30 flex items-center justify-center pointer-events-auto">
      <div class="relative">
        <div v-if="player.nowPlaying?.enqueuedById" class="absolute -top-4 right-0 text-xs font-mono text-accent flex items-center gap-2 z-20 select-none">
          <span>REQ_BY</span>
          <span class="font-bold text-medical-500 border-b border-medical-300 leading-tight">
            {{ userStore.resolveName(player.nowPlaying.enqueuedById, player.nowPlaying.enqueuedByName) }}
          </span>
        </div>

        <div
            id="tutorial-like"
            class="relative w-64 h-64 md:w-72 md:h-72 bg-medical-50 chamfer-br flex items-center justify-center overflow-hidden transition-all duration-500 cursor-pointer border border-white shadow-2xl"
            :class="[
                 // 仅保留暂停时的缩放/灰度
                 player.isPaused ? 'scale-95 grayscale' : 'scale-100',
                 hasLiked ? 'cursor-default' : 'cursor-pointer'
             ]"
            @mouseenter="!isMobile && (isHovering = true)"
            @mouseleave="!isMobile && (isHovering = false)"
            @click="handleCoverClick"
        >
          <!-- Loading 状态 -->
          <div v-if="player.isLoading" class="absolute inset-0 z-50 bg-medical-900/50 backdrop-blur-sm flex flex-col items-center justify-center text-white">
            <div class="w-12 h-12 border-4 border-white/30 border-t-white animate-spin mb-4"></div>
            <span class="font-mono text-xs animate-pulse tracking-widest">FETCHING_AUDIO...</span>
          </div>

          <!-- [MODIFIED START] 交互遮罩层：全息HUD风格 -->
          <Transition
              enter-active-class="transition-all duration-300 ease-out"
              enter-from-class="opacity-0 scale-90"
              enter-to-class="opacity-100 scale-100"
              leave-active-class="transition-all duration-300 ease-in"
              leave-from-class="opacity-100 scale-100"
              leave-to-class="opacity-0 scale-95"
          >
            <div
                v-if="isBursting || (!hasLiked && (isHovering || mobileLikePending)) || hasLiked"
                class="absolute inset-0 z-40 flex items-center justify-center select-none"
                :class="[
                    // 已点赞状态下，只显示极淡的角落标记，不遮挡封面
                    hasLiked && !isBursting ? 'opacity-100' : '',
                    // 交互或爆发时，增加暗色扫描背景
                    (isBursting || (!hasLiked && (isHovering || mobileLikePending))) ? 'bg-medical-900/40' : ''
                ]"
            >
              <!-- 1. 动态网格背景 (仅在交互时显示) -->
              <div v-if="!hasLiked || isHovering" class="absolute inset-0 bg-[linear-gradient(rgba(255,255,255,0.1)_1px,transparent_1px),linear-gradient(90deg,rgba(255,255,255,0.1)_1px,transparent_1px)] bg-[size:20px_20px] opacity-20"></div>

              <!-- 2. 四角瞄准器 (HUD) -->
              <div class="absolute top-2 left-2 w-2 h-2 border-t border-l border-white/50 transition-all duration-300" :class="isBursting ? 'w-4 h-4 border-accent' : ''"></div>
              <div class="absolute top-2 right-2 w-2 h-2 border-t border-r border-white/50 transition-all duration-300" :class="isBursting ? 'w-4 h-4 border-accent' : ''"></div>
              <div class="absolute bottom-2 left-2 w-2 h-2 border-b border-l border-white/50 transition-all duration-300" :class="isBursting ? 'w-4 h-4 border-accent' : ''"></div>
              <div class="absolute bottom-2 right-2 w-2 h-2 border-b border-r border-white/50 transition-all duration-300" :class="isBursting ? 'w-4 h-4 border-accent' : ''"></div>

              <!-- 3. 中央核心交互区 -->
              <div class="relative flex flex-col items-center justify-center gap-2 group">

                <!-- [MODIFIED START] 爆发动画：方形扩散 (去掉 rounded-full, 增加 border) -->
                <!-- -inset-6 确保方形初始大小包裹住文字和图标 -->
                <div v-if="isBursting" class="absolute -inset-6 border border-accent bg-accent/20 animate-ping duration-700 z-0"></div>
                <!-- [MODIFIED END] -->

                <!-- 图标逻辑: 闪电 -->
                <div
                    class="relative transition-all duration-300 transform z-10"
                    :class="[
                      isBursting ? 'scale-125 text-accent drop-shadow-[0_0_15px_rgba(var(--color-accent),0.9)]' :
                      hasLiked ? 'text-accent scale-100 drop-shadow-[0_0_5px_rgba(var(--color-accent),0.5)]' :
                      'text-white/70 scale-100 group-hover:scale-110 group-hover:text-white'
                  ]"
                >
                  <Activity
                      v-if="!hasLiked && (isHovering || mobileLikePending) && !isBursting"
                      class="w-10 h-10 animate-pulse"
                  />

                  <Zap v-else class="w-10 h-10" :class="hasLiked || isBursting ? 'fill-current stroke-none' : ''" />
                </div>


                <!-- 状态文字 -->
                <div class="flex items-center gap-1 font-mono text-[11px] tracking-[0.2em] transition-colors duration-300"
                     :class="isBursting || hasLiked ? 'text-accent' : 'text-white/70'"
                >
                  <span v-if="isBursting">INJECTING...</span>
                  <span v-else-if="hasLiked">WONDERFUL MUSIC</span>
                  <span v-else>LIKE_THIS</span>
                </div>
              </div>
            </div>
          </Transition>

          <img v-if="currentCover" :src="currentCover" class="absolute inset-0 w-full h-full object-cover opacity-80" :class="player.isPaused ? '' : 'animate-[pulse_4s_ease-in-out_infinite]'" />
          <div v-else class="flex flex-col items-center text-medical-300">
            <div class="w-16 h-16 border-2 border-medical-300 mb-2 rotate-45"></div>
            <span class="font-mono text-xs tracking-widest">NO MEDIA</span>
          </div>
          <div class="absolute inset-0 bg-[url('data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAADCAYAAABS3WWCAAAAE0lEQVQYV2NkYGD4zwABjFAQAwBATgMJy2B8NAAAAABJRU5ErkJggg==')] opacity-20 pointer-events-none z-20"></div>

          <!-- 状态标签：悬停或点赞时隐藏 -->
          <div
              class="absolute top-0 left-0 z-50 px-3 py-1 font-mono text-xs font-bold chamfer-br transition-colors duration-300 bg-medical-900/80 backdrop-blur-sm text-white"
          >
            {{ player.isPaused ? 'PAUSED' : 'PLAYING' }}
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed, watch } from 'vue';
import { usePlayerStore } from '../stores/player';
import { useUserStore } from '../stores/user';
import {useEventListener, useWindowSize} from '@vueuse/core';
import { parseLyrics, parseLyricsFull } from '../utils/parser';
import { AudioVisualizer } from '../logic/AudioVisualizer';
import { useUiStore } from '../stores/ui';
import { Heart, Activity, Zap, Maximize2, Minimize2 } from 'lucide-vue-next';

const userStore = useUserStore();
const player = usePlayerStore();
const uiStore = useUiStore();
const canvasRef = ref(null);
const currentCover = computed(() => player.nowPlaying?.music.coverUrl);
const { width } = useWindowSize();
const isMobile = computed(() => width.value < 768);

// === 交互逻辑 ===
const isHovering = ref(false);       // PC Hover
const mobileLikePending = ref(false);// 移动端第一次点击
const mobileTimer = ref(null);       // 移动端定时器
const isBursting = ref(false);       // 爆发状态（本地+广播）


const hasLiked = computed(() => {
  return player.nowPlaying?.likedUserIds?.includes(userStore.userToken);
});


// 特效冷却 (本地)
const EFFECT_COOLDOWN = 1000;
let lastEffectTime = 0;

const handleCoverClick = () => {
  if (hasLiked.value) return;
  if (isMobile.value) {
    if (!mobileLikePending.value) {
      // 第一次点击
      mobileLikePending.value = true;
      mobileTimer.value = setTimeout(() => {
        mobileLikePending.value = false;
      }, 2000);
    } else {
      // 第二次点击 (确认)
      clearTimeout(mobileTimer.value);
      mobileLikePending.value = false;
      confirmLike();
    }
  } else {
    // PC 直接点击
    confirmLike();
  }
};

const confirmLike = () => {
  player.sendLike();
  triggerBurst(); // 本地先爆发一次，提升手感
};

const triggerBurst = () => {
  const now = Date.now();
  if (now - lastEffectTime < EFFECT_COOLDOWN) return;
  lastEffectTime = now;

  isBursting.value = true;
  visualizer.impulse(); // 触发 Canvas 圆环爆发
  setTimeout(() => {
    isBursting.value = false;
  }, 500); // 边框高亮持续 0.5s
};

// === 歌词逻辑 ===
const parsedLyrics = ref([]);
const currentLineIndex = ref(-1);

const activeLines = computed(() => {
  const idx = currentLineIndex.value;
  if (parsedLyrics.value.length === 0) return [];
  const historyCount = isMobile.value ? 5 : 10;
  const start = Math.max(0, idx - historyCount);
  const end = Math.min(parsedLyrics.value.length, idx + 1);
  if (idx === -1) return parsedLyrics.value.slice(0, 3);
  return parsedLyrics.value.slice(start, end);
});


watch(() => player.lyricText, (newVal) => {
  parsedLyrics.value = parseLyrics(newVal);
  currentLineIndex.value = -1;
});

// 结构化歌词（翻译/罗马音）：优先用 lyric-full 数据，行对象含 trans/roman
watch(() => player.lyricFull, (full) => {
  if (full && full.lrc) {
    parsedLyrics.value = parseLyricsFull(full.lrc, full.tlyric, full.romalrc);
  }
  currentLineIndex.value = -1;
}, { deep: true });

// === 歌词样式切换（compact=左下角 | full=封面左移+Apple Music 风格聚焦） ===
const toggleLyricStyle = () => {
  player.lyricStyle = player.lyricStyle === 'compact' ? 'full' : 'compact';
};

// full 样式：当前行前后各 2 行（当前行固定居中，行原地切换）
const activeWindow = computed(() => {
  const all = parsedLyrics.value;
  if (all.length === 0) return [];
  const idx = currentLineIndex.value;
  const N = 2;
  if (idx < 0) {
    // 未开始播放：显示开头几行
    return all.slice(0, N * 2 + 1).map((l, i) => ({ ...l, _center: i === 0, _near: i === 1 }));
  }
  const start = Math.max(0, idx - N);
  const end = Math.min(all.length, idx + N + 1);
  return all.slice(start, end).map((l, i) => ({
    ...l,
    _center: start + i === idx,
    _near: Math.abs(start + i - idx) === 1
  }));
});

// 歌词容器定位：compact 左下角 / full 封面右侧（移动端底部全宽）
const lyricWrapClass = computed(() => {
  if (player.lyricStyle === 'full') {
    return 'inset-x-0 bottom-7 flex flex-col h-56 pb-2 md:inset-auto md:top-1/2 md:-translate-y-1/2 md:left-[38%] md:right-6 md:h-[58vh]';
  }
  return 'inset-x-0 bottom-7 flex flex-col items-center justify-end h-64 pb-2 md:inset-auto md:bottom-8 md:left-10 md:items-start md:justify-end md:h-auto md:w-80';
});

// === 系统日志逻辑 (Realtime) ===
const logs = ref(['SYS_INIT: COMPLETED', 'LINK_START: OK']);
const mountTime = Date.now();
let logInterval;
let updateInterval;

const visualizer = new AudioVisualizer();
const isVisualizerActive = computed(() => !!player.nowPlaying && !player.isPaused);

// 监听状态变化以控制 Visualizer
watch(isVisualizerActive, (active) => {
  visualizer.setPlaying(active);
});

// 脱敏工具
const maskId = (id) => id ? `...${id.slice(-4).toUpperCase()}` : 'N/A';
const formatMem = () => {
  if (performance && performance.memory) {
    return Math.floor(performance.memory.usedJSHeapSize / 1048576) + 'MB';
  }
  return 'N/A';
};

const pushLog = (msg) => {
  logs.value.push(msg);
  if (logs.value.length > 6) logs.value.shift();
};

onMounted(() => {
  // 1. 挂载 Canvas & Visualizer
  if (canvasRef.value) {
    visualizer.mount(canvasRef.value);
    visualizer.setPlaying(isVisualizerActive.value);
  }

  // 2. 监听全局自定义事件
  useEventListener(window, 'player:like', () => triggerBurst());

  // 3. 启动高频更新循环 (歌词进度 & 视觉同步)
  updateInterval = setInterval(() => {
    // 歌词进度更新
    if (player.nowPlaying && !player.isPaused && parsedLyrics.value.length > 0) {
      const currentTime = player.getCurrentProgress();
      let activeIdx = -1;
      for (let i = 0; i < parsedLyrics.value.length; i++) {
        if (currentTime >= parsedLyrics.value[i].time) activeIdx = i;
        else break;
      }
      if (activeIdx !== currentLineIndex.value) currentLineIndex.value = activeIdx;
    }
  }, 100);

  // 4. 启动系统状态日志循环 (真实数据)
  logInterval = setInterval(() => {
    if (player.isPaused && Math.random() > 0.4) return;

    const gfx = visualizer.getStatus();
    const stateParams = [
      // 网络与连接
      { cond: true, msg: `UPLINK: ${player.connected ? 'ESTABLISHED' : 'SEARCHING'}` },
      { cond: true, msg: `PEERS_ONLINE: ${userStore.onlineUsers.length}` },
      { cond: true, msg: `STREAM_SYNC: ${player.streamListenerCount} NODES` },
      { cond: true, msg: `SYNC_DELTA: ${Date.now() - player.lastSyncTime > 10000 ? '>10s' : (Date.now() - player.lastSyncTime) + 'ms'}` },
      { cond: true, msg: `NET_ONLINE: ${navigator.onLine ? 'YES' : 'NO'}` },

      // 播放器核心状态
      { cond: player.isLoading, msg: `BUFFER_STATE: LOADING...` },
      { cond: !player.isLoading, msg: `BUFFER_STATE: STABLE` },
      { cond: true, msg: `QUEUE_LEN: ${player.queue.length}` },
      { cond: true, msg: `PLAY_MODE: ${player.playMode}` },
      { cond: parsedLyrics.value.length > 0, msg: `LYRIC_SYNC: ${parsedLyrics.value.length} LINES` },
      { cond: !player.isPaused, msg: `CUR_POS: ${Math.floor(player.getCurrentProgress())}MS` },

      // 媒体信息 (脱敏)
      { cond: !!player.nowPlaying, msg: `MEDIA_HASH: ${maskId(player.nowPlaying?.music?.id)}` },
      { cond: !!player.nowPlaying, msg: `REQ_USER: ${maskId(player.nowPlaying?.enqueuedById)}` },
      { cond: true, msg: `SESSION_ID: ${maskId(userStore.currentUser.sessionId)}` },

      // 用户状态
      { cond: true, msg: `USER_ROLE: ${userStore.isGuest ? 'GUEST' : 'AUTHENTICATED'}` },
      { cond: Object.keys(userStore.bindings).length > 0, msg: `BIND_PLATFORMS: ${Object.keys(userStore.bindings).length}` },

      // 视觉引擎状态
      { cond: true, msg: `GFX_INTENSITY: ${gfx.intensity}` },
      { cond: true, msg: `GFX_RINGS: ${gfx.rings}` },
      { cond: gfx.active, msg: `GFX_ALPHA: ${gfx.alpha}` },

      // 环境与性能
      { cond: !!performance?.memory, msg: `JS_HEAP: ${formatMem()}` },
      { cond: !!performance?.memory, msg: `HEAP_LIMIT: ${Math.floor(performance.memory.jsHeapSizeLimit / 1048576)}MB` },
      { cond: true, msg: `CORE_THREADS: ${navigator.hardwareConcurrency || 'N/A'}` },
      { cond: true, msg: `SCREEN_RES: ${window.screen.width}x${window.screen.height}` },
      { cond: true, msg: `OS_PLATFORM: ${navigator.platform}` },
      { cond: true, msg: `DPR_RATIO: ${window.devicePixelRatio}` },
      { cond: true, msg: `UPTIME: ${Math.floor((Date.now() - mountTime) / 1000)}S` },
      { cond: true, msg: `UI_THEME: ${window.matchMedia('(prefers-color-scheme: dark)').matches ? 'DARK' : 'LIGHT'}` },
      { cond: true, msg: `LANG_SET: ${navigator.language.toUpperCase()}` },
      { cond: true, msg: `TOUCH_NODE: ${navigator.maxTouchPoints > 0 ? 'ACTIVE' : 'NONE'}` },
      { cond: true, msg: `LOCAL_TZ: ${Intl.DateTimeFormat().resolvedOptions().timeZone}` }
    ];

    // 随机抽取一条有意义的状态显示
    const validStates = stateParams.filter(s => s.cond);
    if (validStates.length > 0) {
      const item = validStates[Math.floor(Math.random() * validStates.length)];
      if (!logs.value[logs.value.length - 1]?.includes(item.msg.split(':')[0])) {
         pushLog(item.msg);
      }
    }
  }, 1000);

  // 初始化歌词
  if (player.lyricText) parsedLyrics.value = parseLyrics(player.lyricText);
});

onUnmounted(() => {
  visualizer.unmount();
  clearInterval(logInterval);
  clearInterval(updateInterval);
});
</script>
<style scoped>
.lyric-scroll::-webkit-scrollbar {
  width: 4px;
}
.lyric-scroll::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 2px;
}
.lyric-scroll::-webkit-scrollbar-track {
  background: transparent;
}
.lyric-scroll {
  scrollbar-width: thin;
  scrollbar-color: #cbd5e1 transparent;
}
</style>
