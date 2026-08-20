<template>
  <div
    v-if="store.visible"
    class="fixed inset-0 z-[200] flex items-center justify-center bg-medical-900/70 backdrop-blur-sm p-4"
    @click.self="store.resolveCancel()"
  >
    <div class="w-full max-w-sm bg-white border border-medical-200 shadow-2xl chamfer-br overflow-hidden">
      <!-- 头部 -->
      <div class="p-3 bg-medical-900 text-white flex justify-between items-center">
        <div class="flex items-center gap-2">
          <div class="w-2 h-2 bg-accent"></div>
          <span class="text-xs font-bold uppercase tracking-widest font-mono">{{ store.title }}</span>
        </div>
        <button @click="store.resolveCancel()" class="text-white/60 hover:text-white transition-colors">
          <X class="w-4 h-4" />
        </button>
      </div>

      <!-- 内容 -->
      <div class="p-5">
        <p class="text-sm text-medical-700 leading-6 whitespace-pre-line">{{ store.message }}</p>
      </div>

      <!-- 操作 -->
      <div class="px-5 pb-5 flex gap-2">
        <button
          @click="store.resolveCancel()"
          class="flex-1 py-2.5 border border-medical-200 text-sm font-bold text-medical-500 hover:bg-medical-100 transition-colors"
        >取消</button>
        <button
          @click="store.resolveOk()"
          class="flex-1 py-2.5 text-sm font-bold text-white transition-colors chamfer-br"
          :class="store.danger ? 'bg-red-500 hover:bg-red-600' : 'bg-accent hover:bg-accent-hover'"
        >确认</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { X } from 'lucide-vue-next'
import { useConfirmStore } from '../stores/confirm'

const store = useConfirmStore()
</script>
