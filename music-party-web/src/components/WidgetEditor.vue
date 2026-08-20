<template>
  <div>
    <!-- 网格画布（4x4 固定，与展示区同比例） -->
    <div
      ref="boardRef"
      class="relative bg-medical-50 border border-medical-200 select-none touch-none"
      :style="{ aspectRatio: '1 / 1' }"
      @pointermove="onPointerMove"
      @pointerup="onPointerUp"
      @pointerleave="onPointerUp"
    >
      <!-- 网格线 -->
      <div v-for="i in 4" :key="'v' + i" class="absolute top-0 bottom-0 w-px bg-medical-200/70 pointer-events-none" :style="{ left: i * 25 + '%' }"></div>
      <div v-for="i in 4" :key="'h' + i" class="absolute left-0 right-0 h-px bg-medical-200/70 pointer-events-none" :style="{ top: i * 25 + '%' }"></div>

      <!-- 部件 -->
      <div
        v-for="w in widgets"
        :key="w.id"
        class="absolute border bg-white cursor-grab overflow-visible"
        :class="draggingType === w.id ? 'border-accent shadow-lg ring-2 ring-accent/40 z-10' : 'border-medical-300 hover:border-accent z-[5]'"
        :style="{ left: w.x * 25 + '%', top: w.y * 25 + '%', width: w.w * 25 + '%', height: w.h * 25 + '%', padding: '2px' }"
        @pointerdown="onWidgetDown($event, w)"
      >
        <div class="w-full h-full rounded overflow-hidden bg-medical-800 relative">
          <!-- 歌曲/专辑预览：封面 + 渐变遮罩 + 底部文字 -->
          <template v-if="(w.kind === 'song' || w.kind === 'album') && w.data">
            <img v-if="w.data.coverUrl" :src="w.data.coverUrl" class="absolute inset-0 w-full h-full object-cover" alt="" />
            <div v-else class="absolute inset-0 bg-gradient-to-br from-medical-700 via-medical-800 to-medical-900 flex items-center justify-center">
              <Music2 v-if="w.kind === 'song'" class="w-3 h-3 text-white/40" />
              <Disc3 v-else class="w-3 h-3 text-white/40" />
            </div>
            <div class="absolute inset-0 bg-gradient-to-t from-black/70 via-transparent to-transparent"></div>
            <div class="absolute inset-x-0 bottom-0 p-0.5">
              <div class="text-[8px] sm:text-[9px] font-bold text-white truncate">{{ w.data.name }}</div>
              <div v-if="w.note" class="text-[7px] font-mono text-amber-300 truncate">{{ w.note }}</div>
            </div>
          </template>
          <!-- 歌词预览 -->
          <div v-else-if="w.kind === 'lyric' && w.data" class="w-full h-full bg-gradient-to-br from-medical-800 via-medical-900 to-black flex flex-col items-center justify-center px-1">
            <div v-if="w.data.song" class="text-[8px] font-mono text-amber-300 truncate w-full text-center">「{{ w.data.song }}」</div>
            <div class="text-[8px] leading-3 text-white/90 italic text-center line-clamp-3 w-full">{{ w.data.text }}</div>
          </div>
          <!-- 空 -->
          <div v-else class="w-full h-full flex items-center justify-center text-[10px] font-mono text-white/40">空</div>

          <!-- 删除按钮 -->
          <button
            class="absolute top-0 right-0 w-4 h-4 bg-red-500 text-white text-[10px] leading-none flex items-center justify-center hover:bg-red-600 z-30"
            title="删除该部件"
            @pointerdown.stop
            @click.stop="removeWidget(w.id)"
          >×</button>
        </div>

        <!-- 尺寸手柄（右下角） -->
        <div
          class="absolute bottom-0 right-0 w-3 h-3 cursor-nwse-resize z-20"
          style="background: linear-gradient(135deg, transparent 50%, rgba(255,255,255,0.9) 50%);"
          @pointerdown.stop="onSizeDown($event, w)"
        ></div>
      </div>
    </div>
    <p class="text-[11px] font-mono text-medical-400 mt-1">拖动部件调整位置，拖动右下角手柄调整大小（可自由摆放，允许重叠）</p>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { Music2, Disc3 } from 'lucide-vue-next'

const props = defineProps({
  widgets: { type: Array, default: () => [] }
})
const emit = defineEmits(['update:widgets'])

const GRID = 4
const boardRef = ref(null)
const draggingType = ref(null)
const drag = ref(null)

function removeWidget(id) {
  emit('update:widgets', props.widgets.filter(w => w.id !== id))
}

function onWidgetDown(e, w) {
  if (e.button !== 0) return
  e.preventDefault()
  const rect = boardRef.value.getBoundingClientRect()
  const gx = Math.floor(((e.clientX - rect.left) / rect.width) * GRID)
  const gy = Math.floor(((e.clientY - rect.top) / rect.height) * GRID)
  drag.value = {
    id: w.id,
    mode: 'move',
    offGX: gx - w.x,
    offGY: gy - w.y
  }
  draggingType.value = w.id
  boardRef.value.setPointerCapture(e.pointerId)
}

function onSizeDown(e, w) {
  if (e.button !== 0) return
  e.preventDefault()
  const rect = boardRef.value.getBoundingClientRect()
  const gx = Math.floor(((e.clientX - rect.left) / rect.width) * GRID)
  const gy = Math.floor(((e.clientY - rect.top) / rect.height) * GRID)
  drag.value = {
    id: w.id,
    mode: 'size',
    offGX: gx - (w.x + w.w - 1),
    offGY: gy - (w.y + w.h - 1)
  }
  draggingType.value = w.id
  boardRef.value.setPointerCapture(e.pointerId)
}

function onPointerMove(e) {
  const d = drag.value
  if (!d || !boardRef.value) return
  const rect = boardRef.value.getBoundingClientRect()
  const gx = Math.floor(((e.clientX - rect.left) / rect.width) * GRID)
  const gy = Math.floor(((e.clientY - rect.top) / rect.height) * GRID)
  const idx = props.widgets.findIndex(w => w.id === d.id)
  if (idx < 0) return
  const target = { ...props.widgets[idx] }

  if (d.mode === 'move') {
    let nx = gx - d.offGX
    let ny = gy - d.offGY
    // 允许重叠：仅限制在画布范围内
    target.x = Math.max(0, Math.min(GRID - target.w, nx))
    target.y = Math.max(0, Math.min(GRID - target.h, ny))
  } else {
    let nw = gx - target.x + 1 - d.offGX
    let nh = gy - target.y + 1 - d.offGY
    target.w = Math.max(1, Math.min(GRID - target.x, nw))
    target.h = Math.max(1, Math.min(GRID - target.y, nh))
  }

  const next = props.widgets.map(w => (w.id === d.id ? target : w))
  emit('update:widgets', next)
}

function onPointerUp() {
  if (drag.value) drag.value = null
  draggingType.value = null
}
</script>
