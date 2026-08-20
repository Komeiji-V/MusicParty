<template>
  <!-- 与其他弹窗一致：全屏遮罩（含底部播放条） -->
  <div class="fixed inset-0 z-[95] flex items-center justify-center bg-medical-900/70 backdrop-blur-sm p-4" @click.self="emit('close')">
    <div class="w-full max-w-lg bg-medical-50 shadow-2xl border border-medical-200 chamfer-br max-h-[85vh] flex flex-col overflow-hidden">
      <!-- 头部 -->
      <div class="p-3 bg-medical-900 text-white flex justify-between items-center flex-shrink-0">
        <div class="flex items-center gap-2">
          <div class="w-2 h-2 bg-accent"></div>
          <span class="text-xs font-bold uppercase tracking-widest font-mono">CHANNEL CONTROL / 频道管理</span>
          <span class="text-xs font-mono text-white/50">{{ channelStore.currentChannel?.name || '' }}</span>
        </div>
        <button @click="emit('close')" class="text-white hover:text-accent transition-colors">
          <X class="w-5 h-5" />
        </button>
      </div>

      <!-- 内容 -->
      <div class="flex-1 overflow-y-auto p-4 space-y-4">
        <div v-if="loading" class="text-center py-10 text-medical-400 font-mono text-sm">> LOADING...</div>

        <template v-else>
          <!-- 播放锁定 -->
          <div class="bg-white border border-medical-200 chamfer-br overflow-hidden">
            <div class="p-2.5 bg-medical-900 text-white flex justify-between items-center">
              <span class="text-xs font-mono font-bold tracking-widest uppercase">播放锁定</span>
              <button
                @click="toggleAllLocks"
                class="text-xs font-mono text-accent hover:text-white transition-colors"
              >{{ allLocked ? '[全部解锁]' : '[全部锁定]' }}</button>
            </div>
            <div class="p-3 space-y-2">
              <div v-for="item in lockItems" :key="item.key" class="flex items-center justify-between py-1">
                <span class="text-sm font-bold text-medical-800">{{ item.label }}</span>
                <button
                  @click="toggleLock(item.key)"
                  class="relative w-10 h-5 rounded-full transition-colors duration-300"
                  :class="state[item.key] ? 'bg-accent' : 'bg-medical-200'"
                >
                  <div class="absolute left-0.5 top-0.5 w-4 h-4 bg-white rounded-full transition-transform duration-300"
                       :style="{ transform: state[item.key] ? 'translateX(20px)' : 'translateX(0)' }"></div>
                </button>
              </div>
            </div>
          </div>

          <!-- 公平随机 -->
          <div class="bg-white border border-medical-200 chamfer-br overflow-hidden">
            <div class="p-2.5 bg-medical-900 text-white">
              <span class="text-xs font-mono font-bold tracking-widest uppercase">随机播放</span>
            </div>
            <div class="p-3">
              <div class="flex items-center justify-between">
                <div>
                  <div class="text-sm font-bold text-medical-800">公平随机（按成员轮替）</div>
                  <div class="text-xs font-mono text-medical-400">每人一首轮流随机，防止单人霸榜</div>
                </div>
                <button
                  @click="toggleFairShuffle"
                  class="relative w-10 h-5 rounded-full transition-colors duration-300 flex-shrink-0"
                  :class="state.isFairShuffle ? 'bg-accent' : 'bg-medical-200'"
                >
                  <div class="absolute left-0.5 top-0.5 w-4 h-4 bg-white rounded-full transition-transform duration-300"
                       :style="{ transform: state.isFairShuffle ? 'translateX(20px)' : 'translateX(0)' }"></div>
                </button>
              </div>
            </div>
          </div>

          <!-- 投票切歌 -->
          <div class="bg-white border border-medical-200 chamfer-br overflow-hidden">
            <div class="p-2.5 bg-medical-900 text-white">
              <span class="text-xs font-mono font-bold tracking-widest uppercase">投票切歌</span>
            </div>
            <div class="p-3 space-y-3">
              <div class="flex items-center justify-between">
                <span class="text-sm font-bold text-medical-800">启用投票切歌</span>
                <button
                  @click="toggleVoteSkip"
                  class="relative w-10 h-5 rounded-full transition-colors duration-300"
                  :class="state.isVoteSkipEnabled ? 'bg-accent' : 'bg-medical-200'"
                >
                  <div class="absolute left-0.5 top-0.5 w-4 h-4 bg-white rounded-full transition-transform duration-300"
                       :style="{ transform: state.isVoteSkipEnabled ? 'translateX(20px)' : 'translateX(0)' }"></div>
                </button>
              </div>
              <div class="flex gap-3">
                <div class="flex-1">
                  <label class="block text-xs font-mono text-medical-400 mb-1">通过阈值（% ，10 的整数倍）</label>
                  <input
                    v-model.number="voteThresholdPct"
                    type="number" min="10" max="90" step="10"
                    @change="saveVoteSkip"
                    class="w-full border border-medical-200 p-2 text-sm bg-medical-50 outline-none focus:border-accent"
                  />
                </div>
                <div class="flex-1">
                  <label class="block text-xs font-mono text-medical-400 mb-1">投票等待（秒）</label>
                  <select v-model.number="voteWaitTime" @change="saveVoteSkip"
                          class="w-full border border-medical-200 p-2 text-sm bg-medical-50 outline-none focus:border-accent">
                    <option :value="5">5 秒</option>
                    <option :value="10">10 秒</option>
                    <option :value="15">15 秒</option>
                    <option :value="30">30 秒</option>
                  </select>
                </div>
              </div>
            </div>
          </div>

          <!-- Cookie 池（本频道使用的 Cookie） -->
          <div class="bg-white border border-medical-200 chamfer-br overflow-hidden">
            <div class="p-2.5 bg-medical-900 text-white flex justify-between items-center">
              <span class="text-xs font-mono font-bold tracking-widest uppercase">Cookie 池（本频道取流）</span>
              <button @click="loadCookiePool" class="text-xs font-mono text-accent hover:text-white transition-colors">[REFRESH]</button>
            </div>
            <div class="p-3 space-y-3">
              <div v-for="group in cookiePool" :key="group.platform" class="border border-medical-100 p-2">
                <div class="flex items-center justify-between mb-1.5">
                  <span class="text-xs font-bold text-medical-900 uppercase">{{ group.platform }}</span>
                  <span class="text-xs font-mono text-medical-400">
                    {{ group.items.filter(i => i.enabled).length }} 可用
                    <span v-if="group.selectedId" class="text-accent"> · 已指定使用</span>
                  </span>
                </div>
                <div v-for="item in group.items" :key="item.id" class="flex items-center justify-between py-1 border-b border-medical-50 last:border-0 gap-2">
                  <div class="flex items-center gap-1.5 min-w-0 flex-wrap">
                    <span
                      v-if="item.selected"
                      class="flex-shrink-0 px-1.5 py-0.5 text-[11px] leading-none font-bold text-white rounded-[2px] bg-accent"
                      title="本频道当前使用这个 Cookie"
                    >使用中</span>
                    <span
                      v-if="!item.enabled"
                      class="flex-shrink-0 px-1.5 py-0.5 text-[11px] leading-none font-bold text-white rounded-[2px] bg-medical-500"
                    >已禁用</span>
                    <span class="text-xs font-mono text-medical-500 truncate">{{ item.cookie }}</span>
                    <span v-if="item.vipType > 0" class="flex-shrink-0 px-1 py-0.5 text-[10px] leading-none font-bold text-white rounded-[2px] bg-yellow-500">VIP{{ item.vipType }}</span>
                    <span v-else-if="item.vipType === 0" class="flex-shrink-0 px-1 py-0.5 text-[10px] leading-none font-bold text-medical-500 bg-medical-100 rounded-[2px]">普通</span>
                    <span v-else-if="item.vipType === -1" class="flex-shrink-0 px-1 py-0.5 text-[10px] leading-none font-bold text-white bg-medical-500 rounded-[2px]" title="未检测到 VIP（Cookie 无效/未登录）">无VIP</span>
                    <span v-if="item.errorMark" class="flex-shrink-0 px-1 py-0.5 text-[10px] leading-none font-bold text-white bg-red-500 rounded-[2px]" :title="item.errorReason">错误</span>
                    <span v-if="item.submittedBy" class="text-[11px] font-mono text-medical-400">提交者：{{ item.submittedBy }}</span>
                  </div>
                  <button
                    v-if="item.enabled"
                    @click="selectPoolCookie(group.platform, item)"
                    class="text-xs font-mono flex-shrink-0"
                    :class="item.selected ? 'text-medical-400 hover:underline' : 'text-blue-600 hover:underline'"
                    :title="item.selected ? '点击取消选择，恢复自动轮询' : '点击后本频道取流优先使用这个 Cookie'"
                  >{{ item.selected ? '[取消选择]' : '[选择使用]' }}</button>
                </div>
                <div v-if="group.items.length === 0" class="text-xs font-mono text-medical-400 py-1">池内暂无 Cookie（可到总后台添加）</div>
              </div>
            </div>
          </div>

          <!-- 队列管理 -->
          <div class="bg-white border border-medical-200 chamfer-br overflow-hidden">
            <div class="p-2.5 bg-medical-900 text-white">
              <span class="text-xs font-mono font-bold tracking-widest uppercase">队列管理</span>
            </div>
            <div class="p-3 grid grid-cols-2 gap-2">
              <button @click="doForceSkip" class="py-2 bg-medical-900 text-white text-sm font-bold hover:bg-accent transition-colors">强制切歌</button>
              <button @click="doClearQueue" class="py-2 border border-medical-200 text-sm font-bold text-medical-700 hover:bg-medical-100 transition-colors">清空队列</button>
              <button @click="doClearOffline" class="py-2 border border-medical-200 text-sm font-bold text-medical-700 hover:bg-medical-100 transition-colors">清理离线点播</button>
              <button @click="doReset" class="py-2 border border-red-200 text-sm font-bold text-red-500 hover:bg-red-50 transition-colors">重置系统</button>
            </div>
          </div>

          <!-- 在线用户 / 踢出 -->
          <div class="bg-white border border-medical-200 chamfer-br overflow-hidden">
            <div class="p-2.5 bg-medical-900 text-white">
              <span class="text-xs font-mono font-bold tracking-widest uppercase">在线用户（{{ onlineUsers.length }}）</span>
            </div>
            <div class="p-3 space-y-1">
              <div v-if="onlineUsers.length === 0" class="text-center py-4 text-xs font-mono text-medical-400">NO USERS ONLINE</div>
              <div
                v-for="u in onlineUsers"
                :key="u.token"
                class="flex items-center justify-between py-1.5 px-2 border border-medical-100"
              >
                <span class="text-sm font-bold text-medical-800 truncate">
                  {{ u.name }}
                  <span v-if="u.token === userStore.userToken" class="text-xs font-mono text-accent">(我)</span>
                </span>
                <button
                  v-if="u.token !== userStore.userToken"
                  @click="doKick(u.name)"
                  class="text-xs font-mono text-red-400 hover:text-red-600 transition-colors"
                >[踢出]</button>
              </div>
              <div class="text-xs font-mono text-medical-400 pt-1">频道管理员不可被踢出</div>
            </div>
          </div>

          <!-- 直播流 -->
          <div class="bg-white border border-medical-200 chamfer-br overflow-hidden">
            <div class="p-2.5 bg-medical-900 text-white">
              <span class="text-xs font-mono font-bold tracking-widest uppercase">直播流</span>
            </div>
            <div class="p-3 space-y-3">
              <div class="flex items-center justify-between">
                <div>
                  <div class="text-sm font-bold text-medical-800">启用直播流</div>
                  <div class="text-xs font-mono text-medical-400">当前 {{ state.streamListenerCount || 0 }} 个收听</div>
                </div>
                <button
                  @click="toggleStream"
                  class="relative w-10 h-5 rounded-full transition-colors duration-300"
                  :class="state.streamEnabled ? 'bg-accent' : 'bg-medical-200'"
                >
                  <div class="absolute left-0.5 top-0.5 w-4 h-4 bg-white rounded-full transition-transform duration-300"
                       :style="{ transform: state.streamEnabled ? 'translateX(20px)' : 'translateX(0)' }"></div>
                </button>
              </div>
              <div v-if="state.streamEnabled">
                <button @click="fetchStreamLink" class="w-full py-2 bg-medical-900 text-white text-sm font-bold hover:bg-accent transition-colors chamfer-br">
                  获取我的直播流链接
                </button>
                <div v-if="streamLink" class="mt-2">
                  <div class="flex gap-2">
                    <input :value="streamLink" readonly class="flex-1 border border-medical-200 p-2 text-xs font-mono bg-medical-50" />
                    <button @click="copyLink" class="px-3 py-2 bg-medical-100 text-sm font-bold hover:bg-medical-200 transition-colors">复制</button>
                  </div>
                  <p class="text-xs font-mono text-medical-400 mt-1">链接 24 小时有效（闲置 4 小时失效）</p>
                </div>
              </div>
            </div>
          </div>

          <!-- 频道设置入口 -->
          <button
            @click="goSettings"
            class="w-full py-2.5 border border-accent text-accent text-sm font-bold hover:bg-accent hover:text-white transition-colors chamfer-br"
          >
            ⚙ 频道详细设置（成员 / Cookie / 音源）
          </button>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { X } from 'lucide-vue-next'
import { useChannelStore } from '../stores/channel'
import { useUserStore } from '../stores/user'
import { useToast } from '../composables/useToast'
import client from '../api/client'

import { useConfirmStore } from '../stores/confirm'

const confirmStore = useConfirmStore()
const confirm = (message, title = '确认操作', danger = true) => confirmStore.ask({ title, message, danger })

const emit = defineEmits(['close'])

const router = useRouter()
const channelStore = useChannelStore()
const userStore = useUserStore()
const { success, error } = useToast()

const loading = ref(true)
const streamLink = ref('')

const onlineUsers = computed(() => userStore.onlineUsers || [])

const state = reactive({
  isPauseLocked: false,
  isSkipLocked: false,
  isPlayModeLocked: false,
  isVoteSkipEnabled: false,
  voteSkipThreshold: 0.5,
  voteSkipWaitTime: 15,
  streamEnabled: false,
  streamListenerCount: 0,
  isFairShuffle: true
})
const voteThresholdPct = ref(50)
const voteWaitTime = ref(15)

const lockItems = [
  { key: 'isPauseLocked', label: '锁定暂停' },
  { key: 'isSkipLocked', label: '锁定切歌' },
  { key: 'isPlayModeLocked', label: '锁定播放模式' }
]

const allLocked = () => state.isPauseLocked && state.isSkipLocked && state.isPlayModeLocked

const channelId = () => channelStore.currentChannelId

// 本频道 Cookie 池
const cookiePool = ref([])

async function loadCookiePool() {
  try {
    cookiePool.value = await client.get(`/api/channels/${channelId()}/admin/cookies/pool`)
  } catch (e) {
    console.error('Failed to load cookie pool', e)
  }
}

async function selectPoolCookie(platform, item) {
  try {
    const res = await client.put(`/api/channels/${channelId()}/admin/cookies/select`, {
      platform,
      id: item.selected ? 0 : item.id
    })
    success(res?.message || (item.selected ? '已恢复自动轮询' : '已选择'))
    loadCookiePool()
  } catch (e) {
    error(e.message || '操作失败')
  }
}

async function loadState() {
  try {
    const data = await client.get(`/api/channels/${channelId()}/admin/state`)
    Object.assign(state, data)
    voteThresholdPct.value = Math.round((data.voteSkipThreshold || 0.5) * 100)
    voteWaitTime.value = data.voteSkipWaitTime || 15
    loadCookiePool()
  } catch (e) {
    error(e.message || '加载频道状态失败')
  } finally {
    loading.value = false
  }
}

async function toggleLock(key) {
  const target = state[key]
  try {
    await client.put(`/api/channels/${channelId()}/admin/locks`, { [key.replace('is', '').replace('Locked', '').toLowerCase()]: !target })
    state[key] = !target
  } catch (e) {
    error(e.message || '操作失败')
  }
}

async function toggleAllLocks() {
  const target = !allLocked()
  try {
    await client.put(`/api/channels/${channelId()}/admin/locks`, { all: target })
    state.isPauseLocked = target
    state.isSkipLocked = target
    state.isPlayModeLocked = target
  } catch (e) {
    error(e.message || '操作失败')
  }
}

async function saveVoteSkip() {
  // 阈值必须为 10 的整数倍（10% ~ 90%）
  const pct = voteThresholdPct.value
  if (!Number.isInteger(pct) || pct < 10 || pct > 90 || pct % 10 !== 0) {
    error('阈值需为 10% ~ 90% 之间 10 的整数倍')
    return
  }
  try {
    await client.put(`/api/channels/${channelId()}/admin/vote-skip`, {
      enabled: state.isVoteSkipEnabled,
      threshold: pct / 100,
      waitTime: voteWaitTime.value
    })
    success('投票切歌配置已保存')
  } catch (e) {
    error(e.message || '保存失败')
  }
}

async function toggleFairShuffle() {
  const target = !state.isFairShuffle
  try {
    await client.put(`/api/channels/${channelId()}/admin/fair-shuffle`, { enabled: target })
    state.isFairShuffle = target
  } catch (e) {
    error(e.message || '操作失败')
  }
}

async function toggleVoteSkip() {
  state.isVoteSkipEnabled = !state.isVoteSkipEnabled
  await saveVoteSkip()
}

async function doForceSkip() {
  try {
    await client.post(`/api/channels/${channelId()}/admin/force-skip`)
    success('已强制切歌')
  } catch (e) {
    error(e.message || '操作失败')
  }
}

async function doClearQueue() {
  if (!(await confirm('确定清空当前频道的播放队列吗？'))) return
  try {
    await client.post(`/api/channels/${channelId()}/admin/queue/clear`)
    success('队列已清空')
  } catch (e) {
    error(e.message || '操作失败')
  }
}

async function doClearOffline() {
  if (!(await confirm('确定清理离线成员点播的歌曲吗？'))) return
  try {
    const res = await client.post(`/api/channels/${channelId()}/admin/queue/clear-offline`)
    success(res?.message || '已清理')
  } catch (e) {
    error(e.message || '操作失败')
  }
}

async function doKick(username) {
  if (!(await confirm(`确定将「${username}」踢出频道吗？对方将无法再进入本频道。`))) return
  try {
    const res = await client.post(`/api/channels/${channelId()}/admin/kick`, { username })
    success(res?.message || '已踢出')
  } catch (e) {
    error(e.message || '操作失败')
  }
}

async function doReset() {
  if (!(await confirm('确定重置系统吗？将清空队列并停止当前播放。', '危险操作', true))) return
  try {
    await client.post(`/api/channels/${channelId()}/admin/reset`)
    success('系统已重置')
  } catch (e) {
    error(e.message || '操作失败')
  }
}

async function toggleStream() {
  const target = !state.streamEnabled
  try {
    await client.put(`/api/channels/${channelId()}/admin/stream`, { enabled: target })
    state.streamEnabled = target
    if (!target) streamLink.value = ''
    success(target ? '直播流已开启' : '直播流已关闭')
  } catch (e) {
    error(e.message || '操作失败')
  }
}

async function fetchStreamLink() {
  try {
    const data = await client.get('/api/stream/link')
    if (!data.enabled) {
      error('直播流未开启')
      return
    }
    streamLink.value = data.link || ''
    success('链接已生成')
  } catch (e) {
    error(e.message || '获取失败')
  }
}

async function copyLink() {
  try {
    await navigator.clipboard.writeText(streamLink.value)
    success('已复制到剪贴板')
  } catch (e) {
    error('复制失败，请手动选择复制')
  }
}

function goSettings() {
  emit('close')
  router.push(`/channel/${channelId()}/settings`)
}

onMounted(loadState)
</script>
