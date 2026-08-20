<template>
  <div class="min-h-screen bg-medical-50 font-sans">
    <header class="h-14 bg-white border-b border-medical-200 flex justify-between items-center px-4 md:px-6">
      <div class="flex items-center gap-2">
        <div class="w-2.5 h-2.5 bg-accent"></div>
        <span class="font-bold text-medical-900">ADMIN PANEL</span>
      </div>
      <button @click="$router.push('/')" class="text-xs font-mono text-medical-500 hover:text-medical-900">[HOME]</button>
    </header>

    <div class="max-w-6xl mx-auto p-4 md:p-6">
      <div class="flex gap-2 mb-6 border-b border-medical-200 pb-2">
        <button
          v-for="tab in tabs" :key="tab.key"
          @click="activeTab = tab.key"
          class="px-4 py-2 text-sm font-bold transition-colors"
          :class="activeTab === tab.key ? 'bg-medical-900 text-white' : 'bg-medical-100 text-medical-500 hover:bg-medical-200'"
        >
          {{ tab.label }}
        </button>
      </div>

      <div class="space-y-6">
        <template v-if="activeTab === 'channels'">
          <div class="bg-white border border-medical-200 chamfer-br overflow-hidden">
            <div class="p-3 bg-medical-900 text-white flex justify-between items-center">
              <span class="text-xs font-bold uppercase tracking-widest font-mono">频道管理</span>
              <button @click="showCreateChannel = !showCreateChannel" class="text-accent text-xs font-mono">[{{ showCreateChannel ? 'CANCEL' : 'NEW' }}]</button>
            </div>
            <div class="p-4">
              <div v-if="showCreateChannel" class="flex gap-2 mb-4 pb-4 border-b border-medical-100">
                <input v-model="newChannelName" placeholder="频道名称" class="flex-1 border border-medical-200 p-2 text-sm outline-none focus:border-accent bg-medical-50" />
                <button @click="createChannel" class="bg-accent text-white px-4 py-2 text-sm font-bold hover:bg-accent-hover transition-colors">创建</button>
              </div>
              <div v-if="channelsLoading" class="text-center py-4 text-medical-400 font-mono text-sm">> LOADING...</div>
              <div v-else v-for="ch in channels" :key="ch.id" class="flex justify-between items-center py-3 border-b border-medical-100 last:border-0">
                <div>
                  <span class="text-sm font-bold text-medical-800">{{ ch.name }}</span>
                  <span class="text-xs font-mono text-medical-400 ml-2">{{ ch.onlineCount || 0 }} online</span>
                </div>
                <div class="flex gap-2">
                  <button @click="$router.push(`/channel/${ch.id}/settings`)" class="text-xs font-mono text-medical-500 hover:text-accent">[EDIT]</button>
                  <button @click="deleteChannel(ch.id)" class="text-xs text-red-400 hover:underline font-mono">[DELETE]</button>
                </div>
              </div>
            </div>
          </div>
        </template>

        <template v-if="activeTab === 'brand'">
          <div class="bg-white border border-medical-200 chamfer-br overflow-hidden">
            <div class="p-3 bg-medical-900 text-white">
              <span class="text-xs font-bold uppercase tracking-widest font-mono">站点品牌</span>
            </div>
            <div class="p-4 space-y-4">
              <div>
                <label class="block text-xs font-mono text-medical-500 mb-1">站点标题</label>
                <input v-model="brandForm.siteTitle" class="w-full border border-medical-200 p-2 text-sm outline-none focus:border-accent bg-medical-50" />
              </div>
              <div>
                <label class="block text-xs font-mono text-medical-500 mb-1">作者</label>
                <input v-model="brandForm.authorName" class="w-full border border-medical-200 p-2 text-sm outline-none focus:border-accent bg-medical-50" />
              </div>
              <div>
                <label class="block text-xs font-mono text-medical-500 mb-1">背景词</label>
                <input v-model="brandForm.backWords" class="w-full border border-medical-200 p-2 text-sm outline-none focus:border-accent bg-medical-50" />
              </div>
              <div>
                <label class="block text-xs font-mono text-medical-500 mb-1">信息页 HTML</label>
                <textarea v-model="brandForm.infoPageContent" rows="10" class="w-full border border-medical-200 p-2 text-sm outline-none focus:border-accent bg-medical-50 font-mono"></textarea>
              </div>
              <div>
                <label class="block text-xs font-mono text-medical-500 mb-1">首页 ABOUT 内容（留空 = 默认文案；支持 HTML：&lt;b&gt;加粗&lt;/b&gt;、&lt;i&gt;斜体&lt;/i&gt;、&lt;a href&gt;链接&lt;/a&gt;、&lt;br&gt; 换行）</label>
                <textarea v-model="brandForm.aboutText" rows="8" class="w-full border border-medical-200 p-2 text-sm outline-none focus:border-accent bg-medical-50 font-mono"></textarea>
              </div>
              <button @click="saveBrand" :disabled="saving" class="bg-accent text-white px-6 py-2 text-sm font-bold hover:bg-accent-hover transition-colors disabled:opacity-50 chamfer-br">{{ saving ? 'SAVING...' : '保存品牌设置' }}</button>
            </div>
          </div>
        </template>

        <template v-if="activeTab === 'data'">
          <div class="grid grid-cols-1 md:grid-cols-4 gap-4 mb-6">
            <div v-for="stat in stats" :key="stat.label" class="bg-white border border-medical-200 p-4 chamfer-br">
              <div class="text-xs font-mono text-medical-400">{{ stat.label }}</div>
              <div class="text-3xl font-black text-medical-900">{{ stat.value }}</div>
            </div>
          </div>

          <div class="bg-white border border-medical-200 chamfer-br overflow-hidden">
            <div class="p-3 bg-medical-900 text-white">
              <span class="text-xs font-bold uppercase tracking-widest font-mono">定时清理</span>
            </div>
            <div class="p-4 space-y-4">
              <div class="flex items-center justify-between">
                <span class="text-sm font-bold text-medical-800">启用定时清理</span>
                <button
                  @click="cleanupForm.enabled = !cleanupForm.enabled"
                  class="w-10 h-5 rounded-full relative transition-colors"
                  :class="cleanupForm.enabled ? 'bg-accent' : 'bg-medical-300'"
                >
                  <div class="absolute top-0.5 left-0.5 w-4 h-4 bg-white rounded-full transition-transform duration-300" :style="{ transform: cleanupForm.enabled ? 'translateX(20px)' : 'translateX(0)' }"></div>
                </button>
              </div>

              <div class="grid grid-cols-2 gap-4">
                <div>
                  <label class="block text-xs font-mono text-medical-500 mb-1">执行间隔（小时）</label>
                  <input v-model.number="cleanupForm.intervalHours" type="number" min="1" class="w-full border border-medical-200 p-2 text-sm outline-none focus:border-accent bg-medical-50" />
                </div>
                <div>
                  <label class="block text-xs font-mono text-medical-500 mb-1">保留天数（天）</label>
                  <input v-model.number="cleanupForm.olderThanDays" type="number" min="1" class="w-full border border-medical-200 p-2 text-sm outline-none focus:border-accent bg-medical-50" />
                </div>
              </div>

              <div>
                <label class="block text-xs font-mono text-medical-500 mb-2">清理目标</label>
                <div class="flex flex-wrap gap-4">
                  <label v-for="target in cleanupTargetOptions" :key="target.key" class="flex items-center gap-2 text-sm font-bold text-medical-800 cursor-pointer">
                    <input type="checkbox" v-model="cleanupForm.targets[target.key]" class="accent-accent" />
                    {{ target.label }}
                  </label>
                </div>
              </div>

              <button
                @click="saveCleanupConfig"
                :disabled="cleanupSaving"
                class="bg-accent text-white px-6 py-2 text-sm font-bold hover:bg-accent-hover transition-colors disabled:opacity-50 chamfer-br"
              >
                {{ cleanupSaving ? 'SAVING...' : '保存定时清理配置' }}
              </button>
            </div>
          </div>

          <div class="bg-white border border-medical-200 chamfer-br overflow-hidden">
            <div class="p-3 bg-medical-900 text-white">
              <span class="text-xs font-bold uppercase tracking-widest font-mono">数据清理</span>
            </div>
            <div class="p-4 space-y-3">
              <div v-for="cleanup in cleanups" :key="cleanup.key" class="flex justify-between items-center py-2 border-b border-medical-100 last:border-0">
                <div>
                  <span class="text-sm font-bold text-medical-800">{{ cleanup.label }}</span>
                  <span class="text-xs font-mono text-medical-400 ml-2">{{ cleanup.desc }}</span>
                </div>
                <button @click="runCleanup(cleanup.key)" class="bg-red-50 text-red-600 border border-red-200 px-4 py-1.5 text-xs font-bold hover:bg-red-100 transition-colors font-mono">
                  CLEANUP
                </button>
              </div>
            </div>
          </div>
        </template>

        <template v-if="activeTab === 'cookies'">
          <!-- Cookie 池 -->
          <div class="bg-white border border-medical-200 chamfer-br overflow-hidden">
            <div class="p-3 bg-medical-900 text-white flex justify-between items-center">
              <span class="text-xs font-bold uppercase tracking-widest font-mono">Cookie 池（各音源）</span>
              <button @click="reloadPool" class="text-accent text-xs font-mono">[REFRESH]</button>
            </div>
            <div class="p-4 space-y-4">
              <div v-for="group in pool" :key="group.platform" class="border border-medical-100 p-3">
                <div class="flex items-center justify-between mb-2">
                  <span class="text-sm font-bold text-medical-900 uppercase">{{ group.platform }}</span>
                  <span class="text-xs font-mono text-medical-400">{{ group.items.length }} 个</span>
                </div>
                <div v-for="item in group.items" :key="item.id" class="flex items-center justify-between py-1.5 border-b border-medical-50 last:border-0">
                  <div class="flex items-center gap-2 min-w-0">
                    <!-- 禁用状态标识 -->
                    <span
                      v-if="!item.enabled"
                      class="flex-shrink-0 px-1.5 py-0.5 text-[11px] leading-none font-bold text-white rounded-[2px] bg-medical-500"
                      title="该 Cookie 已禁用，不会参与取流"
                    >已禁用</span>
                    <span class="text-xs font-mono text-medical-500 truncate">{{ item.cookie }}</span>
                    <!-- 提交者 -->
                    <span v-if="item.submittedBy" class="flex-shrink-0 text-xs font-mono text-medical-400" title="提交者">提交者：{{ item.submittedBy }}</span>
                    <!-- VIP 徽章 -->
                    <span
                      v-if="item.vipType > 0"
                      class="flex-shrink-0 px-1.5 py-0.5 text-[11px] leading-none font-bold text-white rounded-[2px] bg-yellow-500"
                      title="VIP 会员"
                    >VIP{{ item.vipType }}</span>
                    <span
                      v-else-if="item.vipType === 0"
                      class="flex-shrink-0 px-1.5 py-0.5 text-[11px] leading-none font-bold text-medical-500 bg-medical-100 rounded-[2px]"
                      title="已检测：非 VIP 账号"
                    >普通</span>
                    <span
                      v-else-if="item.vipType === -1"
                      class="flex-shrink-0 px-1.5 py-0.5 text-[11px] leading-none font-bold text-white bg-medical-500 rounded-[2px]"
                      title="未检测到 VIP（Cookie 无效/未登录，无法判定）"
                    >无VIP</span>
                    <!-- 错误标记 -->
                    <span
                      v-if="item.errorMark"
                      class="flex-shrink-0 px-1.5 py-0.5 text-[11px] leading-none font-bold text-white bg-red-500 rounded-[2px]"
                      :title="item.errorReason + (item.lastErrorAt ? ' @ ' + item.lastErrorAt : '')"
                    >错误</span>
                    <span v-if="item.failCount > 0 && !item.errorMark" class="text-xs font-mono text-orange-500">失败×{{ item.failCount }}</span>
                  </div>
                  <div class="flex items-center gap-2 flex-shrink-0">
                    <button v-if="item.errorMark" @click="clearCookieError(item)" class="text-xs font-mono text-orange-500 hover:underline">[清除标记]</button>
                    <button @click="checkCookieVip(item)" class="text-xs font-mono text-yellow-600 hover:underline">[检测VIP]</button>
                    <button
                      @click="togglePoolItem(item)"
                      class="text-xs font-mono"
                      :class="item.enabled ? 'text-red-500 hover:underline' : 'text-green-600 hover:underline'"
                      :title="item.enabled ? '点击后该 Cookie 将停止使用' : '点击后该 Cookie 将参与取流'"
                    >{{ item.enabled ? '[点击禁用]' : '[点击启用]' }}</button>
                    <button @click="removePoolItem(item)" class="text-xs font-mono text-red-400 hover:underline">[删除]</button>
                  </div>
                </div>
                <div v-if="group.items.length === 0" class="text-xs font-mono text-medical-400 py-1">空</div>
                <div class="flex gap-2 mt-2">
                  <input v-model="newPoolCookies[group.platform]" placeholder="粘贴 Cookie" class="flex-1 border border-medical-200 p-1.5 text-xs font-mono bg-medical-50 outline-none focus:border-accent" />
                  <button @click="addPoolItem(group.platform)" class="px-3 py-1.5 bg-accent text-white text-xs font-bold hover:bg-accent-hover transition-colors">添加</button>
                </div>
              </div>
            </div>
          </div>

          <!-- 用户提交审核 -->
          <div class="bg-white border border-medical-200 chamfer-br overflow-hidden">
            <div class="p-3 bg-medical-900 text-white flex justify-between items-center">
              <span class="text-xs font-bold uppercase tracking-widest font-mono">用户提交审核（待审 {{ pendingSubmissions.length }}）</span>
              <button @click="loadSubmissions" class="text-accent text-xs font-mono">[REFRESH]</button>
            </div>
            <div class="p-4">
              <div v-if="pendingSubmissions.length === 0" class="text-xs font-mono text-medical-400 py-2">暂无待审核的提交</div>
              <div v-for="sub in pendingSubmissions" :key="sub.id" class="flex items-center justify-between py-2 border-b border-medical-100 last:border-0">
                <div class="min-w-0">
                  <span class="text-sm font-bold text-medical-800">{{ sub.username }}</span>
                  <span class="text-xs font-mono text-medical-400 ml-2 uppercase">{{ sub.platform }}</span>
                  <div class="text-xs font-mono text-medical-500 truncate">{{ sub.cookie }}</div>
                </div>
                <div class="flex gap-2 flex-shrink-0">
                  <button @click="approveSubmission(sub)" class="px-3 py-1.5 bg-green-600 text-white text-xs font-bold hover:bg-green-700 transition-colors">通过</button>
                  <button @click="rejectSubmission(sub)" class="px-3 py-1.5 border border-red-200 text-red-500 text-xs font-bold hover:bg-red-50 transition-colors">拒绝</button>
                </div>
              </div>
            </div>
          </div>
        </template>

        <template v-if="activeTab === 'titles'">
          <!-- 称号定义（先制作，后下发） -->
          <div class="bg-white border border-medical-200 chamfer-br overflow-hidden mb-4">
            <div class="p-3 bg-medical-900 text-white flex justify-between items-center">
              <span class="text-xs font-bold uppercase tracking-widest font-mono">称号定义（先制作）</span>
              <button @click="loadTitleDefs" class="text-accent text-xs font-mono">[REFRESH]</button>
            </div>
            <div class="p-4">
              <div class="flex gap-2 mb-4">
                <input v-model="defForm.name" placeholder="称号名称（如：点歌之王）" class="flex-1 border border-medical-200 p-2 text-sm bg-medical-50 outline-none focus:border-accent" />
                <input v-model="defForm.color" type="color" class="w-12 h-10 border border-medical-200 bg-medical-50 cursor-pointer flex-shrink-0" title="标签颜色" />
                <button @click="createTitleDef" class="px-5 py-2 bg-accent text-white text-sm font-bold hover:bg-accent-hover transition-colors flex-shrink-0">创建称号</button>
              </div>
              <div v-if="titleDefs.length === 0" class="text-xs font-mono text-medical-400 py-2">暂无称号定义</div>
              <div v-for="d in titleDefs" :key="d.id" class="flex items-center justify-between py-2 border-b border-medical-100 last:border-0">
                <div class="flex items-center gap-2">
                  <span class="px-2 py-0.5 text-xs leading-none font-bold text-white rounded-[2px]" :style="{ backgroundColor: d.color }">{{ d.name }}</span>
                  <span class="text-xs font-mono text-medical-400">{{ d.color }}</span>
                </div>
                <div class="flex items-center gap-2 flex-shrink-0">
                  <!-- 改色：选完即保存，全站持有者即时变色 -->
                  <label class="flex items-center gap-1 text-xs font-mono text-medical-500 cursor-pointer hover:text-accent" title="修改颜色（即时生效）">
                    <input type="color" :value="d.color" @change="updateDefColor(d, $event.target.value)" class="w-6 h-6 border border-medical-200 bg-medical-50 cursor-pointer" />
                    改色
                  </label>
                  <button @click="deleteTitleDef(d)" class="text-xs font-mono text-red-400 hover:underline">[删除]</button>
                </div>
              </div>
            </div>
          </div>

          <!-- 称号下发 -->
          <div class="bg-white border border-medical-200 chamfer-br overflow-hidden">
            <div class="p-3 bg-medical-900 text-white flex justify-between items-center">
              <span class="text-xs font-bold uppercase tracking-widest font-mono">授予称号（后下发）</span>
              <button @click="loadAllTitles" class="text-accent text-xs font-mono">[REFRESH]</button>
            </div>
            <div class="p-4">
              <div class="flex gap-2 mb-4">
                <input v-model="grantForm.username" placeholder="用户名" class="flex-1 border border-medical-200 p-2 text-sm bg-medical-50 outline-none focus:border-accent" />
                <select v-model="grantForm.title" class="flex-1 border border-medical-200 p-2 text-sm bg-medical-50 outline-none focus:border-accent">
                  <option value="" disabled>选择称号…</option>
                  <option v-for="d in titleDefs" :key="d.id" :value="d.name">{{ d.name }}</option>
                </select>
                <button @click="grantTitle" class="px-5 py-2 bg-medical-900 text-white text-sm font-bold hover:bg-accent transition-colors flex-shrink-0">授予</button>
              </div>

              <div v-if="allTitles.length === 0" class="text-xs font-mono text-medical-400 py-2">暂无已授予的称号</div>
              <div v-for="t in allTitles" :key="t.id" class="flex items-center justify-between py-2 border-b border-medical-100 last:border-0">
                <div class="flex items-center gap-2 min-w-0">
                  <span class="px-2 py-0.5 text-xs leading-none font-bold rounded-[2px] flex-shrink-0" :style="{ backgroundColor: t.color || '#ff5722', color: titleTextColor(t.color) }">{{ t.title }}</span>
                  <span class="text-sm text-medical-800">→ {{ t.username }}</span>
                  <span class="text-xs font-mono text-medical-400 truncate">{{ t.source }}</span>
                </div>
                <button @click="revokeTitle(t)" class="text-xs font-mono text-red-400 hover:underline flex-shrink-0">[收回]</button>
              </div>
            </div>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, reactive } from 'vue'
import client from '../api/client'
import { useToast } from '../composables/useToast'
import { useConfirmStore } from '../stores/confirm'
import { titleTextColor } from '../utils/titleColor'

const tabs = [
  { key: 'channels', label: '频道管理' },
  { key: 'brand', label: '站点品牌' },
  { key: 'data', label: '数据管理' },
  { key: 'cookies', label: 'Cookie 池' },
  { key: 'titles', label: '称号管理' }
]

const activeTab = ref('channels')
const saving = ref(false)
const channelsLoading = ref(false)

const channels = ref([])
const showCreateChannel = ref(false)
const newChannelName = ref('')

const brandForm = reactive({
  siteTitle: 'MUSIC PARTY',
  authorName: 'ThorNex',
  backWords: 'THORNEX',
  infoPageContent: '',
  aboutText: ''
})

const stats = ref([
  { label: '总用户数', value: 0 },
  { label: '总频道数', value: 0 },
  { label: '在线用户', value: 0 },
  { label: '总歌曲', value: 0 }
])

const cleanups = [
  { key: 'chat', label: '聊天记录', desc: '删除所有频道聊天记录' },
  { key: 'history', label: '播放历史', desc: '删除所有播放历史记录' },
  { key: 'queue', label: '播放队列', desc: '清空所有频道播放队列' },
  { key: 'cache', label: '系统缓存', desc: '清理系统缓存文件' }
]

const cleanupSaving = ref(false)
const { success, error } = useToast()
const confirmStore = useConfirmStore()
const confirm = (message, title = '确认操作', danger = true) => confirmStore.ask({ title, message, danger })

const pool = ref([])
const pendingSubmissions = ref([])
const newPoolCookies = reactive({ netease: '', qq: '', kugou: '', bilibili: '' })

// 称号管理
const allTitles = ref([])
const grantForm = reactive({ username: '', title: '' })
const titleDefs = ref([])
const defForm = reactive({ name: '', color: '#ff5722' })

async function loadTitleDefs() {
  try {
    titleDefs.value = await client.get('/api/admin/titles/defs')
  } catch (e) {
    console.error('Failed to load title defs', e)
  }
}

async function createTitleDef() {
  if (!defForm.name.trim()) return
  try {
    const res = await client.post('/api/admin/titles/defs', { name: defForm.name.trim(), color: defForm.color })
    success(res?.message || '已创建')
    defForm.name = ''
    loadTitleDefs()
  } catch (e) {
    error(e.message || '创建失败')
  }
}

async function deleteTitleDef(d) {
  if (!(await confirm(`确定删除称号定义「${d.name}」吗？已授予的称号保留但颜色恢复默认。`))) return
  try {
    await client.delete(`/api/admin/titles/defs/${d.id}`)
    loadTitleDefs()
    loadAllTitles()
  } catch (e) {
    console.error('Failed to delete def', e)
  }
}

async function updateDefColor(d, color) {
  try {
    const res = await client.put(`/api/admin/titles/defs/${d.id}/color`, { color })
    success(res?.message || '颜色已更新')
    loadTitleDefs()
  } catch (e) {
    error(e.message || '更新失败')
  }
}

async function loadAllTitles() {
  try {
    allTitles.value = await client.get('/api/admin/titles')
  } catch (e) {
    console.error('Failed to load titles', e)
  }
}

async function grantTitle() {
  if (!grantForm.username.trim() || !grantForm.title) return
  try {
    const res = await client.post('/api/admin/titles/grant', {
      username: grantForm.username.trim(),
      title: grantForm.title
    })
    success(res?.message || '已授予')
    grantForm.username = ''
    grantForm.title = ''
    loadAllTitles()
  } catch (e) {
    error(e.message || '授予失败')
  }
}

async function revokeTitle(t) {
  if (!(await confirm(`确定收回「${t.title}」（${t.username}）吗？`))) return
  try {
    await client.post('/api/admin/titles/revoke', { username: t.username, title: t.title })
    loadAllTitles()
  } catch (e) {
    console.error('Failed to revoke', e)
  }
}

async function reloadPool() {
  try {
    pool.value = await client.get('/api/admin/cookies/pool')
  } catch (e) {
    console.error('Failed to load cookie pool', e)
  }
}

async function addPoolItem(platform) {
  const cookie = (newPoolCookies[platform] || '').trim()
  if (!cookie) return
  try {
    await client.post('/api/admin/cookies/pool', { platform, cookie })
    newPoolCookies[platform] = ''
    reloadPool()
  } catch (e) {
    console.error('Failed to add cookie', e)
  }
}

async function removePoolItem(item) {
  if (!(await confirm('确定从 Cookie 池删除该条目吗？'))) return
  try {
    await client.delete(`/api/admin/cookies/pool/${item.id}`)
    reloadPool()
  } catch (e) {
    console.error('Failed to remove cookie', e)
  }
}

async function togglePoolItem(item) {
  try {
    await client.put(`/api/admin/cookies/pool/${item.id}/enabled`, { enabled: !item.enabled })
    reloadPool()
  } catch (e) {
    console.error('Failed to toggle cookie', e)
  }
}

async function clearCookieError(item) {
  try {
    await client.post(`/api/admin/cookies/pool/${item.id}/clear-error`)
    reloadPool()
  } catch (e) {
    console.error('Failed to clear error', e)
  }
}

async function checkCookieVip(item) {
  try {
    const res = await client.post(`/api/admin/cookies/pool/${item.id}/check-vip`)
    success(res?.message || '检测完成')
    reloadPool()
  } catch (e) {
    error(e.message || '检测失败')
  }
}

async function loadSubmissions() {
  try {
    pendingSubmissions.value = await client.get('/api/admin/cookies/submissions?status=PENDING')
  } catch (e) {
    console.error('Failed to load submissions', e)
  }
}

async function approveSubmission(sub) {
  try {
    const res = await client.post(`/api/admin/cookies/submissions/${sub.id}/approve`)
    success(res?.message || '已通过')
    loadSubmissions()
    reloadPool()
  } catch (e) {
    console.error('Failed to approve', e)
  }
}

async function rejectSubmission(sub) {
  try {
    await client.post(`/api/admin/cookies/submissions/${sub.id}/reject`)
    loadSubmissions()
  } catch (e) {
    console.error('Failed to reject', e)
  }
}

const cleanupTargetOptions = [  { key: 'chat', label: '聊天记录' },
  { key: 'history', label: '播放历史' },
  { key: 'queue', label: '播放队列' },
  { key: 'cache', label: '系统缓存' }
]
const cleanupForm = reactive({
  enabled: false,
  intervalHours: 24,
  olderThanDays: 30,
  targets: { chat: true, history: true, queue: true, cache: true }
})

async function loadChannels() {
  channelsLoading.value = true
  try {
    const data = await client.get('/api/admin/channels')
    channels.value = data || []
  } catch (e) {
    console.error('Failed to load channels', e)
  } finally {
    channelsLoading.value = false
  }
}

async function loadStats() {
  try {
    const data = await client.get('/api/admin/stats')
    stats.value = [
      { label: '总用户数', value: data.totalUsers ?? data.users ?? 0 },
      { label: '总频道数', value: data.totalChannels ?? data.channels ?? 0 },
      { label: '在线用户', value: data.onlineUsers ?? 0 },
      { label: '总歌曲', value: data.totalSongs ?? data.queueItems ?? 0 }
    ]
  } catch (e) {
    console.error('Failed to load stats', e)
  }
}

async function loadBrand() {
  try {
    const data = await client.get('/api/config')
    brandForm.siteTitle = data.siteTitle || 'MUSIC PARTY'
    brandForm.authorName = data.authorName || 'ThorNex'
    brandForm.backWords = data.backWords || 'THORNEX'
    brandForm.aboutText = data.aboutText || ''
    try {
      const infoData = await client.get('/api/config/info')
      brandForm.infoPageContent = infoData.content || ''
    } catch (e) { /* info page may not exist */ }
  } catch (e) {
    console.error('Failed to load brand', e)
  }
}

async function createChannel() {
  if (!newChannelName.value.trim()) return
  try {
    await client.post('/api/admin/channels', { name: newChannelName.value.trim() })
    newChannelName.value = ''
    showCreateChannel.value = false
    loadChannels()
  } catch (e) {
    console.error('Failed to create channel', e)
  }
}

async function deleteChannel(id) {
  const ch = channels.value.find(c => c.id === id)
  if (!(await confirm(`确定删除频道「${ch?.name || id}」吗？此操作不可撤销，频道的队列/配置/成员将一并清除。`))) return
  try {
    await client.delete(`/api/admin/channels/${id}`)
    channels.value = channels.value.filter(c => c.id !== id)
  } catch (e) {
    console.error('Failed to delete channel', e)
  }
}

async function saveBrand() {
  saving.value = true
  try {
    await client.put('/api/admin/config/site', {
      siteTitle: brandForm.siteTitle,
      authorName: brandForm.authorName,
      backWords: brandForm.backWords,
      infoPageContent: brandForm.infoPageContent,
      aboutText: brandForm.aboutText
    })
  } catch (e) {
    console.error('Failed to save brand', e)
  } finally {
    saving.value = false
  }
}

async function runCleanup(type) {
  if (!(await confirm(`确定要清理 ${cleanups.find(c => c.key === type)?.label} 吗？此操作不可撤销。`))) return
  try {
    await client.delete(`/api/admin/cleanup/${type}`)
  } catch (e) {
    console.error('Failed to run cleanup', e)
  }
}

async function loadCleanupConfig() {
  try {
    const data = await client.get('/api/admin/cleanup/config')
    if (!data) return
    cleanupForm.enabled = !!data.enabled
    if (data.intervalHours) cleanupForm.intervalHours = Number(data.intervalHours)
    if (data.olderThanDays) cleanupForm.olderThanDays = Number(data.olderThanDays)
    if (Array.isArray(data.targets)) {
      const targets = {}
      cleanupTargetOptions.forEach(t => { targets[t.key] = data.targets.includes(t.key) })
      cleanupForm.targets = targets
    }
  } catch (e) {
    console.error('Failed to load cleanup config', e)
  }
}

async function saveCleanupConfig() {
  cleanupSaving.value = true
  try {
    const targets = cleanupTargetOptions.filter(t => cleanupForm.targets[t.key]).map(t => t.key)
    await client.put('/api/admin/cleanup/config', {
      enabled: cleanupForm.enabled,
      intervalHours: cleanupForm.intervalHours,
      olderThanDays: cleanupForm.olderThanDays,
      targets
    })
  } catch (e) {
    console.error('Failed to save cleanup config', e)
  } finally {
    cleanupSaving.value = false
  }
}

onMounted(() => {
  loadChannels()
  loadStats()
  loadBrand()
  loadCleanupConfig()
  reloadPool()
  loadSubmissions()
  loadAllTitles()
  loadTitleDefs()
})
</script>
