// ... existing code ...
<template>
  <!-- full 样式：整体 justify-start 让封面左移，为右侧歌词让位 -->
  <div
      class="relative w-full h-full flex items-center overflow-hidden transition-all duration-500"
      :class="player.lyricStyle === 'full' ? 'justify-center' : 'justify-center'"
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

    <!-- LAYER 2: 信息层 (compact 歌词 & 日志) -->
    <div class="absolute inset-0 z-20 pointer-events-none">
      <!-- compact 歌词：左下角（原样式）；full 模式或窗口过窄时隐藏 -->
      <div
          v-if="player.lyricStyle === 'compact' && !lyricHidden"
          class="absolute font-mono transition-all duration-300 inset-x-0 bottom-7 flex flex-col items-center justify-end h-64 pb-2 md:inset-auto md:bottom-8 md:left-10 md:items-start md:justify-end md:h-auto md:w-80"
      >
        <!-- 标题行：LYRIC_SYSTEM + 样式切换按钮 -->
        <div class="pointer-events-auto flex items-center gap-2 mb-1 min-h-0 flex-shrink-0">
          <div class="hidden md:block text-xs text-accent/80 tracking-widest border-b border-accent/30 pb-1">
            LYRIC_SYSTEM
          </div>
          <!-- 样式切换 -->
          <button
              @click="toggleLyricStyle"
              :disabled="lyricFullDisabled"
              :title="lyricFullDisabled ? '歌词区域过窄，无法展开歌词模式' : '展开歌词（封面左移+聚焦歌词）'"
              :class="{ 'opacity-40 cursor-not-allowed': lyricFullDisabled }"
              class="ml-auto flex items-center justify-center w-6 h-6 text-medical-400 hover:text-accent transition-colors border border-medical-200 hover:border-accent rounded-sm bg-white/60 backdrop-blur-sm"
          >
            <Maximize2 class="w-3.5 h-3.5" />
          </button>
        </div>

        <div
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
      </div>

      <!-- 右侧：伪系统日志 -->
      <div class="absolute bottom-10 right-10 font-mono text-xs text-medical-400 text-right space-y-1 hidden md:block opacity-60">
        <div v-for="(log, i) in logs" :key="i" class="animate-pulse">
          {{ log }} <
        </div>
      </div>
    </div>

    <!-- LAYER 3: 核心实体层 (封面)；左移由根 flex 控制 -->
    <div
        class="z-30 flex items-center justify-center pointer-events-auto flex-shrink-0"
        :class="player.lyricStyle === 'full' ? 'absolute' : 'relative'"
        :style="player.lyricStyle === 'full' ? { left: 'calc(50% - 304px)', top: '50%', transform: 'translateY(-50%)' } : {}"
    >
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

      <!-- full 歌词区：与封面同排（间距固定 ml，缩放不变）、贴紧封面右侧 -->
      <div
          v-if="player.lyricStyle === 'full' && !lyricHidden"
          ref="lyricAreaRef"
          class="z-30 flex flex-col flex-shrink-0 h-[224px] md:h-[280px] overflow-hidden absolute"
          :style="{ left: 'calc(50% + 16px)', top: '50%', transform: 'translateY(-50%)', width: 'min(520px, 38vw, calc(50% - 16px))' }"
      >
        <!-- 标题行：LYRIC_SYSTEM + 翻译/罗马音开关 + 收起按钮 -->
        <div class="pointer-events-auto flex items-center gap-2 mb-1 flex-shrink-0">
          <div class="hidden md:block text-xs text-accent/80 tracking-widest border-b border-accent/30 pb-1">
            LYRIC_SYSTEM
          </div>
          <button
              v-if="player.lyricFull.tlyric"
              @click="player.showTranslation = !player.showTranslation"
              class="px-2 py-0.5 text-[11px] font-bold border rounded-sm transition-colors"
              :class="player.showTranslation ? 'bg-accent text-white border-accent' : 'text-medical-400 border-medical-300 hover:text-accent hover:border-accent'"
          >翻译</button>
          <button
              v-if="player.lyricFull.romalrc"
              @click="player.showRoman = !player.showRoman"
              class="px-2 py-0.5 text-[11px] font-bold border rounded-sm transition-colors"
              :class="player.showRoman ? 'bg-accent text-white border-accent' : 'text-medical-400 border-medical-300 hover:text-accent hover:border-accent'"
          >罗马音</button>
          <button
              @click="toggleLyricStyle"
              title="收起歌词（原样式）"
              class="px-2 py-0.5 text-[11px] font-bold border rounded-sm transition-colors text-medical-400 border-medical-300 hover:text-accent hover:border-accent"
          >
            <Minimize2 class="w-3.5 h-3.5" />
          </button>
        </div>
        <!-- Apple Music 风格聚焦歌词：当前行大字靠左、前后行小字淡出（行原地切换，不滚动） -->
        <div class="pointer-events-auto relative flex-1 min-h-0 overflow-hidden">
          <div class="absolute inset-0 flex flex-col justify-center items-stretch gap-2 md:gap-3">
            <div v-if="parsedLyrics.length === 0" class="opacity-50 flex items-center justify-center w-full">
              <span class="text-accent/50 mr-2 text-xs">></span>NO_DATA_STREAM
            </div>
            <!-- 固定 5 个槽位：行不增删，仅内容/字号/透明度过渡（平滑无跳动） -->
            <div
                v-else
                v-for="(slot, i) in slotLines"
                :key="i"
                class="relative w-full flex items-center transition-all duration-500"
                :class="slot
                  ? (slot._center ? 'h-auto py-0.5' : 'h-10 md:h-11')
                  : 'h-10 md:h-11'"
            >
              <!-- 交叉淡入淡出：旧文字上滑淡出 + 新文字下滑淡入同时进行，无空白闪烁；
                   中心槽位用放大/缩小动画（scale 不参与布局 → 换行点固定，无转行跳变） -->
              <Transition :name="slot && slot._center ? 'lyric-swap-center' : 'lyric-swap-side'" mode="out-in">
                <div
                    v-if="slot"
                    :key="slot.time"
                    class="w-full text-left px-1 leading-snug break-words transition-all duration-500"
                    :class="slot._center
                      ? 'text-2xl md:text-[30px] font-black text-medical-900'
                      : slot._near
                        ? 'text-sm md:text-base font-bold text-medical-600 opacity-70'
                        : 'text-xs md:text-sm text-medical-400 opacity-40'"
                >
                  <!-- 罗马音在歌词上方（黑色） -->
                  <div v-if="slot._center && player.showRoman && slot.roman" class="text-sm md:text-lg text-medical-900 font-medium italic mb-0.5">{{ slot.roman }}</div>
                  <div>{{ slot.text }}</div>
                  <div v-if="slot._center && player.showTranslation && slot.trans" class="text-sm md:text-lg text-accent font-medium mt-0.5">{{ slot.trans }}</div>
                </div>
              </Transition>
            </div>
          </div>
        </div>
      </div>
    </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed, watch, nextTick } from 'vue';
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
  if (player.lyricStyle === 'compact' && lyricFullDisabled.value) return; // 超宽不可展开
  player.lyricStyle = player.lyricStyle === 'compact' ? 'full' : 'compact';
};

// full 样式：固定 5 个槽位（当前行居中，前后各 2），行不增删仅内容过渡
const slotLines = computed(() => {
  const all = parsedLyrics.value;
  if (all.length === 0) return [];
  const idx = currentLineIndex.value < 0 ? 0 : currentLineIndex.value;
  const slots = [];
  for (let off = -2; off <= 2; off++) {
    const li = idx + off;
    if (li >= 0 && li < all.length) {
      slots.push({ ...all[li], _center: off === 0, _near: Math.abs(off) === 1 });
    } else {
      slots.push(null); // 空槽位（保持布局稳定）
    }
  }
  return slots;
});

// 歌词框宽 = min(520px, 38vw, 主区半宽-16px)（主区 = 视口 - 左栏256 - 右栏320）；
// <280px（约窗口 <1170px）时整个歌词区（含 compact）自动隐藏，避免放不下
const lyricHidden = computed(() =>
  Math.min(520, width.value * 0.38, width.value / 2 - 304) < 280);

// 超宽检测：歌词区容器过窄（放不下大字歌词）时自动回退默认样式且禁止切换；
// 长歌词行已支持换行（break-words），正常宽度容器不受影响
const lyricAreaRef = ref(null);
const lyricFullDisabled = ref(false);
const measureLyricFit = () => {
  const area = lyricAreaRef.value;
  if (!area) return;
  const fits = area.clientWidth >= 200;
  if (!fits && !lyricFullDisabled.value) {
    lyricFullDisabled.value = true;
    if (player.lyricStyle === 'full') player.lyricStyle = 'compact';
  } else if (fits) {
    lyricFullDisabled.value = false;
  }
};
watch(() => player.lyricStyle, () => { nextTick(measureLyricFit); });
watch(lyricHidden, (hidden) => {
  if (hidden && player.lyricStyle === 'full') player.lyricStyle = 'compact';
});
onMounted(() => { nextTick(measureLyricFit); });



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

<style scoped>
/* 歌词行串行切换（mode="out-in"）：旧文字先淡出完毕，新文字再淡入。
   串行下新旧元素不同时存在 → 无 flex 并排问题，leave 保留在文档流中
   撑住槽位高度（不塌陷）。
   scale 不参与布局 → 行宽/换行点固定，放大动画不会引发转行跳变。
   transform-origin: left center → 左对齐点不动，无横向位移 */
.lyric-swap-center-enter-active,
.lyric-swap-center-leave-active {
  transition: opacity 0.6s cubic-bezier(.22, 1, .36, 1), transform 0.6s cubic-bezier(.22, 1, .36, 1);
  transform-origin: left center;
}
.lyric-swap-center-enter-from {
  opacity: 0;
  transform: translateY(14px) scale(0.75);
}
.lyric-swap-center-leave-to {
  opacity: 0;
  transform: translateY(-14px) scale(0.75);
}
.lyric-swap-side-enter-active,
.lyric-swap-side-leave-active {
  transition: opacity 0.45s ease, transform 0.45s ease;
  transform-origin: left center;
}
.lyric-swap-side-enter-from {
  opacity: 0;
  transform: translateY(6px) scale(0.94);
}
.lyric-swap-side-leave-to {
  opacity: 0;
  transform: translateY(-6px) scale(0.94);
}
</style>
