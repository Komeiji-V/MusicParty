import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import client from '../api/client'
import { STORAGE_KEYS } from '../constants/keys'
import { usePlayerStore } from './player'

export const useChannelStore = defineStore('channel', () => {
  const channels = ref([])
  const currentChannel = ref(null)
  const isLoading = ref(false)

  const currentChannelId = computed(() => currentChannel.value?.id)

  async function fetchChannels() {
    isLoading.value = true
    try {
      const data = await client.get('/api/channels')
      channels.value = data || []
      // 当前频道被过滤掉（如 HIDDEN 频道失去成员资格）时重置
      if (currentChannel.value && !channels.value.some(c => c.id === currentChannel.value.id)) {
        currentChannel.value = null
        localStorage.removeItem(STORAGE_KEYS.CHANNEL_ID)
      }
      // 恢复上次所在频道
      const savedId = Number(localStorage.getItem(STORAGE_KEYS.CHANNEL_ID))
      if (savedId && !currentChannel.value) {
        const saved = channels.value.find(c => c.id === savedId)
        if (saved) currentChannel.value = saved
      }
    } catch (e) {
      console.error('Failed to fetch channels', e)
    } finally {
      isLoading.value = false
    }
  }

  async function joinChannel(id, password) {
    return client.post(`/api/channels/${id}/join`, password ? { password } : {})
  }

  function persistChannelId(id) {
    localStorage.setItem(STORAGE_KEYS.CHANNEL_ID, String(id))
  }

  // 退出频道：清除当前频道与本地记录（返回首页时调用，否则同频道无法再次进入）
  function clearCurrentChannel() {
    currentChannel.value = null
    localStorage.removeItem(STORAGE_KEYS.CHANNEL_ID)
  }

  // 切换频道：REST 加入 + 持久化 + 重连 WebSocket
  async function switchChannel(channel, password) {
    await joinChannel(channel.id, password)
    currentChannel.value = channel
    persistChannelId(channel.id)
    const playerStore = usePlayerStore()
    playerStore.reconnectForChannel()
    // 刷新频道列表，让在线人数等数据保持最新
    fetchChannels().catch(() => {})
    return true
  }

  async function createChannel(name, description, password, joinPermission) {
    const data = await client.post('/api/channels', { name, description, password, joinPermission })
    channels.value.push(data)
    return data
  }

  async function updateChannel(id, updates) {
    const data = await client.put(`/api/channels/${id}`, updates)
    const idx = channels.value.findIndex(c => c.id === id)
    if (idx !== -1) channels.value[idx] = data
    if (currentChannel.value?.id === id) currentChannel.value = data
    return data
  }

  async function deleteChannel(id) {
    await client.delete(`/api/channels/${id}`)
    channels.value = channels.value.filter(c => c.id !== id)
    if (currentChannel.value?.id === id) currentChannel.value = null
  }

  async function setCookie(platform, cookieValue) {
    await client.post(`/api/channels/${currentChannel.value.id}/cookies`, { platform, cookieValue })
  }

  async function toggleSource(platform, enabled) {
    await client.post(`/api/channels/${currentChannel.value.id}/sources`, { platform, enabled })
  }

  return {
    channels, currentChannel, currentChannelId, isLoading,
    fetchChannels, joinChannel, switchChannel, createChannel, updateChannel, deleteChannel,
    setCookie, toggleSource, clearCurrentChannel
  }
})
