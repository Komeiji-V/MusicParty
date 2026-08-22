<template>
  <div
      v-if="chatStore.danmakuEnabled"
      ref="containerRef"
      class="absolute inset-x-0 top-0 h-1/3 overflow-hidden pointer-events-none"
      style="z-index: 25"
  >
    <div
        v-for="d in danmakus"
        :key="d.id"
        :data-dk="d.id"
        class="dk-item absolute whitespace-nowrap px-2 py-0.5 rounded-sm text-white text-sm font-bold shadow-md"
        :style="{
          top: d.top + 'px',
          '--dk-w': (d.containerW || 0) + 'px',
          '--dk-self': (d.selfW || 0) + 'px',
          animationDuration: d.dur + 's',
          background: 'rgba(17, 24, 39, 0.62)',
          textShadow: '0 1px 2px rgba(0,0,0,0.6)',
        }"
        @animationend="remove(d.id)"
    >
      <span v-if="d.title" class="text-accent">{{ d.title }}</span>{{ d.name }}说：{{ d.text }}
    </div>
  </div>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue';
import { useChatStore } from '../stores/chat';
import { useUserStore } from '../stores/user';

const chatStore = useChatStore();
const userStore = useUserStore();

const danmakus = ref([]);
const containerRef = ref(null);
let seq = 0;

const TRACK_COUNT = 3; // 上方 1/3 内 3 条轨道
const SPEED = 90; // 固定速度 px/s
const MAX_DANMAKU = 15;
const MAX_TEXT = 40;

// 每条轨道的"最后一条弹幕完全进入视口"时刻（ms）
const trackFreeAt = ref(Array(TRACK_COUNT).fill(0));

const remove = (id) => {
  danmakus.value = danmakus.value.filter(d => d.id !== id);
};

const pushDanmaku = async (msg) => {
  if (!chatStore.danmakuEnabled) return;
  // 找空闲轨道（按最早空闲优先）
  const now = performance.now();
  let chosen = -1;
  for (let i = 0; i < TRACK_COUNT; i++) {
    if (now >= trackFreeAt.value[i]) { chosen = i; break; }
  }
  if (chosen < 0) return; // 轨道全忙：丢弃（防刷屏）
  if (danmakus.value.length >= MAX_DANMAKU) return;

  const user = userStore.onlineUsers.find(u => u.token === msg.userId) || null;
  const name = user?.name || msg.userName || '匿名';
  const title = user?.title || '';
  const text = String(msg.content || '').slice(0, MAX_TEXT);

  const id = ++seq;
  const item = {
    id,
    top: chosen * 34 + 8, // 轨道行高 34px，顶部留 8px
    name,
    title,
    text,
    containerW: containerRef.value ? containerRef.value.clientWidth : 800,
    selfW: 0,
    dur: 12,
  };
  danmakus.value.push(item);

  // 等渲染后测量自身宽度，算动画距离与释放时间
  await nextTick();
  const el = document.querySelector(`.dk-item[data-dk="${id}"]`);
  const selfW = el ? el.offsetWidth : 200;
  const total = item.containerW + selfW + 40; // 40px 缓冲
  const dur = Math.max(6, total / SPEED);
  item.selfW = selfW;
  item.dur = dur;
  trackFreeAt.value[chosen] = now + dur * 1000;
  // 超时兜底清理（animationend 可能因暂停/隐藏丢失）
  setTimeout(() => remove(id), (dur + 0.5) * 1000);
};

watch(() => chatStore.lastLiveMessage, (msg) => {
  if (msg) pushDanmaku(msg);
});
</script>

<style scoped>
.dk-item {
  left: 100%;
  animation-name: danmaku-fly;
  animation-timing-function: linear;
  animation-fill-mode: forwards;
  will-change: transform;
}
@keyframes danmaku-fly {
  from { transform: translateX(0); }
  to { transform: translateX(calc(-1 * (var(--dk-w) + var(--dk-self) + 40px))); }
}
</style>
