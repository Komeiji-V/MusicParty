<template>
  <Transition
    enter-active-class="transition duration-300 ease-out"
    enter-from-class="opacity-0"
    enter-to-class="opacity-100"
    leave-active-class="transition duration-200 ease-in"
    leave-from-class="opacity-100"
    leave-to-class="opacity-0"
  >
    <div v-if="uiStore.showInfo" class="fixed inset-0 z-[100] flex items-center justify-center p-4 bg-medical-900/60 backdrop-blur-sm" @click.self="uiStore.showInfo = false">
      <div class="w-full max-w-2xl max-h-[85vh] bg-white shadow-2xl border border-medical-200 flex flex-col chamfer-br overflow-hidden">
        <div class="p-3 bg-medical-900 text-white flex justify-between items-center flex-shrink-0">
          <div class="flex items-center gap-2">
            <div class="w-2 h-2 bg-accent"></div>
            <span class="text-xs font-bold uppercase tracking-widest font-mono">INFO / HELP</span>
          </div>
          <button @click="uiStore.showInfo = false" class="text-white hover:text-accent transition-colors">
            <X class="w-5 h-5" />
          </button>
        </div>

        <div class="flex-1 overflow-y-auto p-6">
          <div class="mb-6">
            <h2 class="text-2xl font-black text-medical-900 tracking-tighter">{{ uiStore.siteTitle }}</h2>
            <p class="text-xs font-mono text-medical-400 mt-1">by {{ uiStore.authorName }}</p>
          </div>

          <div v-if="uiStore.infoPageContent" class="prose prose-sm max-w-none font-sans text-medical-700" v-html="sanitizedContent"></div>

          <div v-else class="space-y-4">
            <div class="p-4 bg-medical-50 border border-medical-200">
              <h3 class="font-bold text-medical-900 mb-2">关于 MusicParty</h3>
              <p class="text-sm text-medical-600 leading-relaxed">
                MusicParty 是一个多人协同音乐播放平台，支持多平台音源（网易云、B站等），
                允许多人实时同步收听同一首音乐。
              </p>
            </div>
            <div class="p-4 bg-medical-50 border border-medical-200">
              <h3 class="font-bold text-medical-900 mb-2">快捷操作</h3>
              <ul class="text-sm text-medical-600 space-y-1">
                <li><span class="font-mono text-xs text-medical-400">SEARCH</span> 搜索并添加歌曲</li>
                <li><span class="font-mono text-xs text-medical-400">QUEUE</span> 查看和管理播放队列</li>
                <li><span class="font-mono text-xs text-medical-400">CHAT</span> 与其他听众实时聊天</li>
                <li><span class="font-mono text-xs text-medical-400">LITE</span> 精简模式，节省资源</li>
              </ul>
            </div>
          </div>
        </div>
      </div>
    </div>
  </Transition>
</template>

<script setup>
import { computed, watch } from 'vue'
import { X } from 'lucide-vue-next'
import { useUiStore } from '../stores/ui'

const uiStore = useUiStore()

// 允许的标签/属性白名单（其余一律剥离，仅保留文本内容）
const ALLOWED_TAGS = new Set([
    'A', 'P', 'BR', 'B', 'I', 'EM', 'STRONG', 'U', 'S', 'UL', 'OL', 'LI',
    'H1', 'H2', 'H3', 'H4', 'H5', 'H6', 'BLOCKQUOTE', 'CODE', 'PRE',
    'TABLE', 'THEAD', 'TBODY', 'TR', 'TH', 'TD', 'IMG', 'HR', 'DIV', 'SPAN',
    'SECTION', 'DETAILS', 'SUMMARY', 'FIGURE', 'FIGCAPTION', 'MARK', 'SMALL', 'SUB', 'SUP'
])
const ALLOWED_ATTRS = new Set([
    'href', 'title', 'src', 'alt', 'width', 'height', 'target', 'rel',
    'align', 'colspan', 'rowspan', 'start', 'type'
])

// 基于 DOMParser 的严格白名单消毒（正则方案可被无引号事件属性/实体编码绕过）
const sanitizeHtml = (dirty) => {
    if (!dirty) return ''
    const doc = new DOMParser().parseFromString(dirty, 'text/html')
    const elements = []
    const walker = doc.createTreeWalker(doc.body, NodeFilter.SHOW_ELEMENT)
    while (walker.nextNode()) elements.push(walker.currentNode)

    for (const el of elements) {
        if (!ALLOWED_TAGS.has(el.tagName)) {
            el.replaceWith(...el.childNodes) // 剥离危险标签，保留其文本内容
            continue
        }
        for (const attr of [...el.attributes]) {
            const name = attr.name.toLowerCase()
            const value = attr.value
            // 删除所有事件处理器属性
            if (name.startsWith('on')) {
                el.removeAttribute(attr.name)
                continue
            }
            // 删除白名单之外的属性
            if (!ALLOWED_ATTRS.has(name)) {
                el.removeAttribute(attr.name)
                continue
            }
            // URL 属性仅允许 http/https/相对路径/图片 data
            if (name === 'href' || name === 'src') {
                const lower = value.trim().toLowerCase()
                if (lower.startsWith('javascript:') || lower.startsWith('vbscript:')
                    || lower.startsWith('data:text/html') || lower.startsWith('data:image/svg')) {
                    el.removeAttribute(attr.name)
                }
            }
        }
    }
    // 兜底清理（如实体编码残留的 script 结构）
    return doc.body.innerHTML
        .replace(/<\s*script[\s\S]*?<\s*\/\s*script\s*>/gi, '')
        .replace(/<\s*iframe[\s\S]*?<\s*\/\s*iframe\s*>/gi, '')
}

const sanitizedContent = computed(() => sanitizeHtml(uiStore.infoPageContent))

watch(() => uiStore.showInfo, (val) => {
  if (val) {
    uiStore.fetchInfoPage()
  }
})
</script>
