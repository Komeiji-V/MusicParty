<template>
  <div
      v-if="chatStore.danmakuEnabled"
      ref="containerRef"
      class="absolute inset-x-0 top-0 h-1/3 overflow-hidden pointer-events-none"
      style="z-index: 25"
  ></div>
</template>

<script setup>
import { ref, watch, onBeforeUnmount } from 'vue';
import { useChatStore } from '../stores/chat';
import { useUserStore } from '../stores/user';

const chatStore = useChatStore();
const userStore = useUserStore();

const containerRef = ref(null);
let seq = 0;

const SPEED = 90; // 固定速度 px/s
const MAX_DANMAKU = 15;
const MAX_TEXT = 40;
const MIN_Y_GAP = 34; // 弹幕间最小垂直间距（px）

// 活跃弹幕（原生 DOM 管理，避免 Vue 响应式更新重启 CSS 动画）
const active = new Map(); // id -> { el, y, w, until }

const removeDanmaku = (id) => {
  const d = active.get(id);
  if (!d) return;
  active.delete(id);
  d.el.remove();
};

const pushDanmaku = (msg) => {
  if (!chatStore.danmakuEnabled) return;
  if (active.size >= MAX_DANMAKU) return;

  const user = userStore.onlineUsers.find(u => u.token === msg.userId) || null;
  const name = user?.name || msg.userName || '匿名';
  const title = user?.title || '';
  const text = String(msg.content || '').slice(0, MAX_TEXT);

  const container = containerRef.value;
  if (!container) return;
  const containerW = container.clientWidth;
  const areaH = container.clientHeight;

  // 随机 y（上方 1/3 内），试 5 次找一个与现存弹幕垂直间距足够的
  const now = performance.now();
  let y = 8;
  for (let i = 0; i < 5; i++) {
    const cand = 8 + Math.random() * Math.max(0, areaH - 44);
    const ok = [...active.values()].every(d => Math.abs(d.y - cand) >= MIN_Y_GAP || now > d.until + 500);
    if (ok) { y = cand; break; }
    y = cand;
  }

  // 原生 DOM：先以无动画状态插入 → 测量宽度 → 设置变量/时长 → 再启用动画（避免动画重启）
  const el = document.createElement('div');
  el.className = 'dk-item dk-noanim';
  el.style.top = y + 'px';
  const titleSpan = document.createElement('span');
  titleSpan.className = 'dk-title';
  if (title) {
    const tc = user?.titleColor || '#ff5722';
    titleSpan.style.backgroundColor = tc;
    // 对比文字色：按亮度选黑/白
    const hex = tc.replace('#', '');
    const r = parseInt(hex.slice(0, 2), 16) || 0;
    const g = parseInt(hex.slice(2, 4), 16) || 0;
    const b = parseInt(hex.slice(4, 6), 16) || 0;
    titleSpan.style.color = (r * 299 + g * 587 + b * 114) / 1000 > 150 ? '#111827' : '#ffffff';
  }
  titleSpan.textContent = title ? title + ' ' : '';
  const textNode = document.createElement('span');
  textNode.textContent = `${name} 说：${text}`;
  el.appendChild(titleSpan);
  el.appendChild(textNode);
  container.appendChild(el);

  const selfW = el.offsetWidth;
  const total = containerW + selfW + 40;
  const dur = Math.max(6, total / SPEED);
  el.style.setProperty('--dk-w', containerW + 'px');
  el.style.setProperty('--dk-self', selfW + 'px');
  el.style.animationDuration = dur + 's';
  el.classList.remove('dk-noanim'); // 此刻动画开始（变量已就绪，不会重启）

  const id = ++seq;
  active.set(id, { el, y, w: selfW, until: now + dur * 1000 });
  el.addEventListener('animationend', () => removeDanmaku(id));
  setTimeout(() => removeDanmaku(id), (dur + 0.5) * 1000);
};

watch(() => chatStore.lastLiveMessage, (msg) => {
  if (msg) pushDanmaku(msg);
});

onBeforeUnmount(() => {
  active.forEach((d, id) => removeDanmaku(id));
});
</script>

<!-- 注意：必须用全局样式（非 scoped）——弹幕元素是 createElement 原生创建，无 data-v 属性，scoped 选择器匹配不到 -->
<style>
.dk-item {
  position: absolute;
  left: 100%;
  padding: 1px 0;
  color: #111827; /* 无背景：弹幕文字黑色 */
  font-size: 14px;
  font-weight: 700;
  white-space: nowrap;
  animation-name: danmaku-fly;
  animation-timing-function: linear;
  animation-fill-mode: forwards;
  will-change: transform;
}
.dk-noanim {
  animation: none !important;
}
/* 称号徽章：挪用在线成员列表的称号外观，自适应弹幕字号 */
.dk-title {
  display: inline-flex;
  align-items: center;
  padding: 1px 6px;
  border-radius: 2px;
  font-size: 11px;
  font-weight: 800;
  line-height: 1.5;
  margin-right: 4px;
  vertical-align: middle;
}
@keyframes danmaku-fly {
  from { transform: translateX(0); }
  to { transform: translateX(calc(-1 * (var(--dk-w) + var(--dk-self) + 40px))); }
}
</style>
