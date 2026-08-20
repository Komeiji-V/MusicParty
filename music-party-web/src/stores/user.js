import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { STORAGE_KEYS } from '../constants/keys';

// 安全修复（H1）：不再本地生成 user-token（原实现可被任意伪造，且随连接头发给服务端）。
// 身份键由服务端派生，登录后在 /user/me 回调里下发到 userToken。
const userToken = ref('');

const storageName = localStorage.getItem(STORAGE_KEYS.USERNAME);
const currentUser = ref({
    name: storageName || '游客',
    sessionId: ''
});

export const useUserStore = defineStore('user', () => {
    const onlineUsers = ref([]);

    // 启动时：严格从 LocalStorage 读取，默认值只在这里设定一次
    const storageName = localStorage.getItem('mp_username');
    const currentUser = ref({
        name: storageName || '游客',
        sessionId: ''
    });

    const bindings = ref(JSON.parse(localStorage.getItem(STORAGE_KEYS.BINDINGS) || '{}'));
    // 全局状态：控制改名弹窗显示
    const showNameModal = ref(false);

    const onNameSetCallback = ref(null);

    const isGuest = ref(!storageName);

    // 核心方法：将 SessionID 翻译成名字
    const resolveName = (id, fallbackName) => {
        if (!id) return 'Unknown';
        if (id === 'ADMIN') return 'AUTO_DJ';

        // 如果 ID 是我自己 (比较服务端下发的身份键)
        if (id === userToken.value) return currentUser.value.name;

        // 否则去在线列表里找 (通过 u.token 匹配)
        const u = onlineUsers.value.find(u => u.token === id);

        return u ? u.name : (fallbackName || 'Unknown Agent');
    };

    /**
     * 2. 初始化用户身份 (来自 /app/user/me)
     * 逻辑：对比服务器认为的名字 (serverName) 和我本地存储的名字
     * serverIsGuest: 后端返回的当前是否为游客状态
     * serverToken: 服务端派生的身份键（登录后用于 "是否我自己" 的判断）
     */
    const initUser = (sessionId, serverName, serverIsGuest, serverToken) => {
        currentUser.value.sessionId = sessionId;
        if (serverToken) {
            userToken.value = serverToken;
        }

        // 1. 同步名字
        if (serverName) {
            currentUser.value.name = serverName;
        }

        // 2. 同步身份状态 (以服务端为准)
        if (serverIsGuest !== undefined) {
            // 状态变更检测: Guest -> User (转正)
            if (isGuest.value && !serverIsGuest) {
                console.log("Identity upgraded to User");
                isGuest.value = false;
                localStorage.setItem(STORAGE_KEYS.USERNAME, serverName);
                showNameModal.value = false; // 成功改名后自动关闭弹窗

                // 执行待办回调 (如打开搜索框)
                if (onNameSetCallback.value) {
                    onNameSetCallback.value();
                    onNameSetCallback.value = null;
                }
            }
            // 状态变更检测: User -> Guest (降级/重置)
            else if (!isGuest.value && serverIsGuest) {
                console.log("Identity degraded to Guest");
                isGuest.value = true;
                localStorage.removeItem(STORAGE_KEYS.USERNAME);
            }
        }

        // 3. 如果是正式用户，确保本地存储名字与服务端一致 (处理去重后缀)
        if (!isGuest.value && serverName) {
            localStorage.setItem(STORAGE_KEYS.USERNAME, serverName);
        }

        return false;
    };

    const setOnlineUsers = (users) => {
        onlineUsers.value = users;
    };

    const updateBinding = (platform, accountId) => {
        bindings.value[platform] = accountId;
        localStorage.setItem(STORAGE_KEYS.BINDINGS, JSON.stringify(bindings.value));
    };

    // 废弃: 不再直接修改本地状态，改为等待 initUser 的后端回调
    const saveName = (newName) => {
        // Logic moved to initUser response handling
    }

    const setPostNameAction = (fn) => {
        onNameSetCallback.value = fn;
    }

    return {
        onlineUsers,
        currentUser,
        bindings,
        initUser,
        setOnlineUsers,
        updateBinding,
        saveName,
        isGuest,
        showNameModal,
        resolveName,
        userToken,
        setPostNameAction
    };
});
