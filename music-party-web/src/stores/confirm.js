// 全局确认弹窗状态（Promise 风格）
import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useConfirmStore = defineStore('confirm', () => {
    const visible = ref(false)
    const title = ref('确认操作')
    const message = ref('')
    const danger = ref(false)

    let resolver = null

    /** 弹出确认框，返回 Promise<boolean> */
    function ask(opts = {}) {
        title.value = opts.title || '确认操作'
        message.value = opts.message || ''
        danger.value = !!opts.danger
        resolver = null
        visible.value = true
        return new Promise(resolve => {
            resolver = resolve
        })
    }

    function resolveOk() {
        visible.value = false
        resolver?.(true)
        resolver = null
    }

    function resolveCancel() {
        visible.value = false
        resolver?.(false)
        resolver = null
    }

    return { visible, title, message, danger, ask, resolveOk, resolveCancel }
})
