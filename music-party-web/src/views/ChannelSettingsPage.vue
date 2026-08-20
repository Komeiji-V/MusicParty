<template>
  <div class="min-h-screen bg-medical-50 font-sans">
    <header class="h-14 bg-white border-b border-medical-200 flex justify-between items-center px-4 md:px-6">
      <div class="flex items-center gap-2">
        <div class="w-2.5 h-2.5 bg-accent"></div>
        <span class="font-bold text-medical-900">CHANNEL SETTINGS</span>
      </div>
      <button @click="$router.back()" class="text-xs font-mono text-medical-500 hover:text-medical-900">[BACK]</button>
    </header>

    <div class="max-w-3xl mx-auto p-4 md:p-6 space-y-6">
      <div v-if="loading" class="text-center py-10">
        <div class="text-sm text-medical-400 font-mono animate-pulse">> LOADING CHANNEL DATA...</div>
      </div>

      <template v-else>
        <div class="bg-white border border-medical-200 shadow-sm chamfer-br overflow-hidden">
          <div class="p-3 bg-medical-900 text-white">
            <span class="text-xs font-bold uppercase tracking-widest font-mono">基本设置</span>
          </div>
          <div class="p-4 space-y-4">
            <div>
              <label class="block text-xs font-mono text-medical-500 mb-1">频道名称</label>
              <input v-model="form.name" class="w-full border border-medical-200 p-2 text-sm outline-none focus:border-accent bg-medical-50" />
            </div>
            <div>
              <label class="block text-xs font-mono text-medical-500 mb-1">描述</label>
              <textarea v-model="form.description" rows="3" class="w-full border border-medical-200 p-2 text-sm outline-none focus:border-accent bg-medical-50"></textarea>
            </div>
            <div v-if="form.joinPermission === 'PASSWORD'">
              <label class="block text-xs font-mono text-medical-500 mb-1">密码 (留空则无密码)</label>
              <input v-model="form.password" type="password" class="w-full border border-medical-200 p-2 text-sm outline-none focus:border-accent bg-medical-50" />
            </div>
            <div>
              <label class="block text-xs font-mono text-medical-500 mb-1">加入权限</label>
              <select v-model="form.joinPermission" class="w-full border border-medical-200 p-2 text-sm outline-none focus:border-accent bg-medical-50">
                <option value="PUBLIC">公开 - 所有人可直接加入</option>
                <option value="PASSWORD">密码 - 需输入密码加入</option>
                <option value="INVITE_ONLY">邀请制 - 仅成员可加入</option>
                <option value="HIDDEN">仅成员可见 - 非成员不可见</option>
              </select>
            </div>
            <button @click="saveBasic" :disabled="saving" class="bg-accent text-white px-6 py-2 text-sm font-bold hover:bg-accent-hover transition-colors disabled:opacity-50 chamfer-br">
              {{ saving ? 'SAVING...' : '保存设置' }}
            </button>
          </div>
        </div>

        <div class="bg-white border border-medical-200 shadow-sm chamfer-br overflow-hidden">
          <div class="p-3 bg-medical-900 text-white">
            <span class="text-xs font-bold uppercase tracking-widest font-mono">音源开关</span>
          </div>
          <div class="p-4 space-y-3">
            <div v-for="source in sources" :key="source.key" class="flex items-center justify-between py-2 border-b border-medical-100 last:border-0">
              <span class="text-sm font-bold text-medical-800">{{ source.label }}</span>
              <button @click="toggleSource(source.key)" class="w-10 h-5 rounded-full relative transition-colors" :class="form.sources[source.key] ? 'bg-accent' : 'bg-medical-300'">
                <div class="absolute top-0.5 left-0.5 w-4 h-4 bg-white rounded-full transition-transform duration-300" :style="{ transform: form.sources[source.key] ? 'translateX(20px)' : 'translateX(0)' }"></div>
              </button>
            </div>
            <div class="text-xs font-mono text-medical-400">音源 Cookie 为全局 Cookie 池（管理后台统一管理），不再按频道配置</div>
          </div>
        </div>

        <div class="bg-white border border-medical-200 shadow-sm chamfer-br overflow-hidden">
          <div class="p-3 bg-medical-900 text-white flex justify-between items-center">
            <span class="text-xs font-bold uppercase tracking-widest font-mono">管理员列表</span>
            <button v-if="auth.isSuperAdmin" @click="showAddAdmin = !showAddAdmin" class="text-accent text-xs font-mono">[{{ showAddAdmin ? 'CANCEL' : 'ADD' }}]</button>
          </div>
          <div class="p-4 space-y-3">
            <div v-if="showAddAdmin && auth.isSuperAdmin" class="flex gap-2 pb-3 border-b border-medical-100">
              <input v-model="newAdminName" placeholder="用户名" class="flex-1 border border-medical-200 p-2 text-sm outline-none focus:border-accent bg-medical-50" />
              <button @click="addAdmin" class="bg-accent text-white px-4 py-2 text-sm font-bold hover:bg-accent-hover transition-colors">添加</button>
            </div>
            <div v-for="admin in admins" :key="admin.id" class="flex justify-between items-center py-2 border-b border-medical-100 last:border-0">
              <div>
                <span class="text-sm font-bold text-medical-800">{{ admin.username }}</span>
                <span class="text-xs font-mono text-medical-400 ml-2">{{ admin.role }}</span>
              </div>
              <button v-if="auth.isSuperAdmin" @click="removeAdmin(admin.id)" class="text-xs text-red-400 hover:underline font-mono">[REMOVE]</button>
            </div>
            <div v-if="!auth.isSuperAdmin" class="text-xs font-mono text-medical-400">仅总管理员可添加/移除管理员</div>
          </div>
        </div>

        <div class="bg-white border border-medical-200 shadow-sm chamfer-br overflow-hidden">
          <div class="p-3 bg-medical-900 text-white flex justify-between items-center">
            <span class="text-xs font-bold uppercase tracking-widest font-mono">频道成员</span>
            <button @click="showAddMember = !showAddMember" class="text-accent text-xs font-mono">[{{ showAddMember ? 'CANCEL' : 'ADD' }}]</button>
          </div>
          <div class="p-4 space-y-3">
            <div v-if="showAddMember" class="flex gap-2 pb-3 border-b border-medical-100">
              <input v-model="newMemberName" placeholder="用户名" class="flex-1 border border-medical-200 p-2 text-sm outline-none focus:border-accent bg-medical-50" />
              <button @click="addMember" class="bg-accent text-white px-4 py-2 text-sm font-bold hover:bg-accent-hover transition-colors">添加</button>
            </div>
            <div v-for="member in members" :key="member.id" class="flex justify-between items-center py-2 border-b border-medical-100 last:border-0">
              <span class="text-sm font-bold text-medical-800">{{ member.username }}</span>
              <button @click="removeMember(member.id)" class="text-xs text-red-400 hover:underline font-mono">[REMOVE]</button>
            </div>
            <div v-if="members.length === 0" class="text-xs text-medical-400 font-mono py-1">NO MEMBERS</div>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, reactive } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import client from '../api/client'

import { useConfirmStore } from '../stores/confirm'

const confirmStore = useConfirmStore()
const confirm = (message, title = '确认操作', danger = true) => confirmStore.ask({ title, message, danger })

const route = useRoute()
const channelId = route.params.id
const auth = useAuthStore()

const loading = ref(true)
const saving = ref(false)
const showAddAdmin = ref(false)
const newAdminName = ref('')
const admins = ref([])
const showAddMember = ref(false)
const newMemberName = ref('')
const members = ref([])

const form = reactive({
  name: '',
  description: '',
  password: '',
  joinPermission: 'PUBLIC',
  sources: {
    netease: true,
    bilibili: true,
    qq: true,
    kugou: true
  }
})

const sources = [
  { key: 'netease', label: '网易云音乐' },
  { key: 'bilibili', label: 'Bilibili' },
  { key: 'qq', label: 'QQ音乐' },
  { key: 'kugou', label: '酷狗音乐' }
]

async function loadChannel() {
  try {
    const data = await client.get(`/api/channels/${channelId}/config`)
    form.name = data.name || ''
    form.description = data.description || ''
    form.password = ''
    form.joinPermission = data.joinPermission || 'PUBLIC'
    form.sources = { ...form.sources, ...data.sources }
    admins.value = data.admins || []
    members.value = data.members || []
  } catch (e) {
    console.error('Failed to load channel', e)
  } finally {
    loading.value = false
  }
}

async function saveBasic() {
  saving.value = true
  try {
    await client.put(`/api/channels/${channelId}`, {
      name: form.name,
      description: form.description,
      password: form.password || '',
      joinPermission: form.joinPermission
    })
    form.password = ''
  } catch (e) {
    console.error('Failed to save', e)
  } finally {
    saving.value = false
  }
}


async function toggleSource(platform) {
  const enabled = !form.sources[platform]
  form.sources[platform] = enabled
  try {
    await client.post(`/api/channels/${channelId}/sources`, { platform, enabled })
  } catch (e) {
    form.sources[platform] = !enabled
  }
}

async function addAdmin() {
  if (!newAdminName.value.trim()) return
  try {
    await client.post(`/api/channels/${channelId}/admins`, { username: newAdminName.value.trim() })
    newAdminName.value = ''
    showAddAdmin.value = false
    const data = await client.get(`/api/channels/${channelId}/config`)
    admins.value = data.admins || []
  } catch (e) {
    console.error('Failed to add admin', e)
  }
}

async function removeAdmin(adminId) {
  const a = admins.value.find(x => x.id === adminId)
  if (!(await confirm(`确定移除管理员「${a?.username || adminId}」吗？`))) return
  try {
    await client.delete(`/api/channels/${channelId}/admins/${adminId}`)
    admins.value = admins.value.filter(a => a.id !== adminId)
  } catch (e) {
    console.error('Failed to remove admin', e)
  }
}

async function addMember() {
  if (!newMemberName.value.trim()) return
  try {
    await client.post(`/api/channels/${channelId}/members`, { username: newMemberName.value.trim() })
    newMemberName.value = ''
    showAddMember.value = false
    const data = await client.get(`/api/channels/${channelId}/config`)
    members.value = data.members || []
  } catch (e) {
    console.error('Failed to add member', e)
  }
}

async function removeMember(memberId) {
  const m = members.value.find(x => x.id === memberId)
  if (!(await confirm(`确定将成员「${m?.username || memberId}」移出频道吗？`))) return
  try {
    await client.delete(`/api/channels/${channelId}/members/${memberId}`)
    members.value = members.value.filter(m => m.id !== memberId)
  } catch (e) {
    console.error('Failed to remove member', e)
  }
}

onMounted(loadChannel)
</script>
