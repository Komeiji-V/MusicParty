<template>
  <div class="p-4">
    <div class="mb-4 flex items-center justify-between">
      <h3 class="text-sm font-bold text-medical-400">在线成员</h3>
      <div class="text-xs font-mono bg-accent/10 text-accent px-1">{{ users.length }}</div>
    </div>

    <div class="space-y-3">
      <!-- 自己 -->
      <div
          id="tutorial-rename"
          class="flex items-center gap-3 pb-3 border-b border-medical-200 border-dashed transition-all duration-300 p-2 -mx-2 rounded"
          :class="[
              isEnqueuerById(userStore.userToken) ? 'bg-accent/10 border-accent/30 shadow-sm' :
              isLikedUser(userStore.userToken) ? 'bg-accent/5' : ''
          ]"
      >
        <div class="flex-1 min-w-0">
          <div class="flex items-center gap-1.5 min-w-0">
            <div
                class="text-sm font-bold truncate"
                :class="isEnqueuerById(userStore.userToken) ? 'text-accent' : 'text-medical-900'"
                :title="me.name"
            >
              {{ me.name }}
            </div>
            <!-- 自己：名字旁「我」正方形小徽标 -->
            <span class="flex-shrink-0 w-4 h-4 flex items-center justify-center text-[11px] leading-none font-bold bg-medical-900 text-white rounded-[2px]">我</span>
            <!-- 称号：矩形标签，高度与「我」徽标一致，背景填充色 + 自适应文字 -->
            <span
              v-if="myTitle"
              class="flex-shrink-0 px-2 h-4 flex items-center text-xs leading-none font-bold rounded-[2px]"
              :style="{ backgroundColor: myTitleColor || '#ff5722', color: titleTextColor(myTitleColor) }"
            >{{ myTitle }}</span>
          </div>
        </div>

        <!-- 使用 CSS 类控制动画 -->
        <div v-if="isEnqueuerById(userStore.userToken)" class="flex gap-0.5 items-end h-4">
          <div class="bar bar-1 bg-accent"></div>
          <div class="bar bar-2 bg-accent"></div>
          <div class="bar bar-3 bg-accent"></div>
        </div>
        <div v-else class="w-2 h-2 bg-accent rounded-full animate-pulse"></div>
      </div>

      <!-- 其他人（点击查看个人空间） -->
      <div
          v-for="u in others"
          :key="u.sessionId"
          @click="openUserProfile(u)"
          class="flex items-center gap-3 transition-all duration-300 p-2 -mx-2 rounded cursor-pointer hover:bg-medical-50"
          :class="[
              isEnqueuerById(u.token) ? 'bg-accent/5' :
              isLikedUser(u.token) ? 'bg-accent/5' : ''
          ]"
          :title="u.username ? ('查看 ' + u.name + ' 的个人空间') : u.name + '（游客，无个人空间）'"
      >
        <div class="flex-1 min-w-0">
          <div class="flex items-center gap-1.5 min-w-0">
            <div
                class="text-sm font-bold truncate"
                :class="isEnqueuerById(u.token) ? 'text-accent' : 'text-medical-900'"
            >
              {{ u.name }}
            </div>
            <!-- 称号：矩形标签，高度与「我」徽标一致，放用户名右侧 -->
            <span
              v-if="u.title"
              class="flex-shrink-0 px-2 h-4 flex items-center text-xs leading-none font-bold rounded-[2px]"
              :style="{ backgroundColor: u.titleColor || '#ff5722', color: titleTextColor(u.titleColor) }"
            >{{ u.title }}</span>
          </div>
        </div>

        <!-- 使用 CSS 类控制动画 -->
        <div v-if="isEnqueuerById(u.token)" class="flex gap-0.5 items-end h-4">
          <div class="bar bar-1 bg-accent"></div>
          <div class="bar bar-2 bg-accent"></div>
          <div class="bar bar-3 bg-accent"></div>
        </div>
        <div v-else class="w-2 h-2 bg-accent rounded-full"></div>
      </div>
    </div>

    <!-- 用户个人空间弹窗 -->
    <UserProfileModal
      v-if="profileUser"
      :username="profileUser.username || profileUser.name"
      :auth-uid="profileUser.authUid"
      :title="profileUser.title || ''"
      :title-color="profileUser.titleColor || ''"
      @close="profileUser = null"
    />

    <!-- 直播流人数 -->
    <div v-if="playerStore.streamListenerCount > 0" class="mt-4 pt-3 border-t border-medical-200 border-dashed">
      <div class="flex items-center gap-3 p-2 -mx-2 opacity-60">
        <div class="w-8 h-8 flex items-center justify-center bg-medical-200 text-medical-500 font-bold text-xs rounded-none">
          LIVE
        </div>
        <div class="text-xs font-bold text-medical-800">
          流在线 [{{ playerStore.streamListenerCount }}]
        </div>
        <div class="flex-1"></div>
        <div class="w-2 h-2 bg-medical-400 rounded-full animate-pulse"></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';
import { useUserStore } from '../stores/user';
import UserProfileModal from './UserProfileModal.vue';
import { usePlayerStore } from '../stores/player';
import { titleTextColor } from '../utils/titleColor';

const userStore = useUserStore();
const playerStore = usePlayerStore();
const users = computed(() => userStore.onlineUsers);
const me = computed(() => userStore.currentUser);

// 自己的称号（从在线列表中取）
const myTitle = computed(() => users.value.find(u => u.token === userStore.userToken)?.title || '');
const myTitleColor = computed(() => users.value.find(u => u.token === userStore.userToken)?.titleColor || '');

const isLikedUser = (token) => {
  if (!playerStore.nowPlaying) return false;
  return playerStore.nowPlaying.likedUserIds?.includes(token);
};

const others = computed(() => users.value.filter(u => u.token !== userStore.userToken));

const profileUser = ref(null);

function openUserProfile(u) {
  if (!u.username) return; // 游客无个人空间
  profileUser.value = u;
}

const isEnqueuerById = (token) => {
  if (!playerStore.nowPlaying) return false;
  // 后端 NowPlayingInfo 现在存的是 enqueuedById (Token)
  // 我们比较：这首歌的Token === 列表里该用户的Token
  return playerStore.nowPlaying.enqueuedById === token;
};
</script>

<style scoped>
/* 使用标准 CSS 实现跳动效果 */
.bar {
  width: 3px;
  border-radius: 1px;
  /* 使用 transform 性能更好 */
  transform-origin: bottom;
  animation: bounce infinite ease-in-out;
}

.bar-1 { animation-duration: 0.6s; height: 60%; }
.bar-2 { animation-duration: 0.8s; height: 100%; }
.bar-3 { animation-duration: 0.5s; height: 40%; }

@keyframes bounce {
  0%, 100% { transform: scaleY(0.4); }
  50% { transform: scaleY(1); }
}
</style>