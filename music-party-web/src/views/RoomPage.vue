<template>
  <div class="h-screen w-screen overflow-hidden font-sans">
    <AudioEngine />

    <MainLayout @search="handleSearchClick" @playlists="showPlaylists = true">
      <CenterConsole />
      <template #player>
        <PlayerControl />
      </template>
    </MainLayout>

    <SearchModal :isOpen="showSearch" @close="showSearch = false" />
    <PlaylistsModal :isOpen="showPlaylists" @close="showPlaylists = false" />
    <ChatOverlay v-if="!uiStore.isLiteMode" />
    <TutorialOverlay v-if="!uiStore.isLiteMode" />
    <InfoModal />
    <ToastNotification ref="toastInstance" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useEventListener } from '@vueuse/core'
import { usePlayerStore } from '../stores/player'
import { useUserStore } from '../stores/user'
import { useUiStore } from '../stores/ui'
import { useChannelStore } from '../stores/channel'
import { useToast } from '../composables/useToast'

import MainLayout from '../components/layout/MainLayout.vue'
import CenterConsole from '../components/CenterConsole.vue'
import PlayerControl from '../components/PlayerControl.vue'
import AudioEngine from '../components/AudioEngine.vue'
import SearchModal from '../components/SearchModal.vue'
import PlaylistsModal from '../components/PlaylistsModal.vue'
import ChatOverlay from '../components/ChatOverlay.vue'
import ToastNotification from '../components/ToastNotification.vue'
import TutorialOverlay from '../components/TutorialOverlay.vue'
import InfoModal from '../components/InfoModal.vue'

const player = usePlayerStore()
const userStore = useUserStore()
const uiStore = useUiStore()
const channelStore = useChannelStore()
const router = useRouter()
const route = useRoute()
const showSearch = ref(false)
const showPlaylists = ref(false)
const toastInstance = ref(null)
const { register } = useToast()

useEventListener(document, 'visibilitychange', () => {
  if (document.visibilityState === 'hidden' && !player.isPaused && uiStore.autoLiteMode) {
    uiStore.isLiteMode = true
  }
})

const handleSearchClick = () => {
  if (userStore.isGuest) {
    userStore.setPostNameAction(() => { showSearch.value = true })
    userStore.showNameModal = true
  } else {
    showSearch.value = true
  }
}

onMounted(async () => {
  if (toastInstance.value) register(toastInstance.value)
  await channelStore.fetchChannels()

  // 支持 ?ch={id} 直达：登录回跳后自动加入指定频道（公开频道）
  const targetChId = Number(route.query.ch)
  if (targetChId && targetChId !== channelStore.currentChannelId) {
    const target = channelStore.channels.find(c => c.id === targetChId)
    if (target && !target.hasPassword) {
      try {
        await channelStore.switchChannel(target)
        router.replace({ query: {} })
        player.connect()
        return
      } catch (e) {
        console.error('Auto join channel failed:', e)
      }
    }
    // 密码频道或加入失败 → 回首页选择
    router.replace('/')
    return
  }

  // 刷新后恢复频道授权（WebSocket 需要已授权才能连接）
  const chId = channelStore.currentChannelId
  if (!chId) {
    router.push('/')
    return
  }
  try {
    await channelStore.joinChannel(chId)
  } catch (e) {
    console.error('Channel access restored failed:', e)
    router.push('/')
    return
  }
  // 进入房间即自动连接，无需再手动 CONNECT
  player.connect()
})
</script>
