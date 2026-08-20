import { usePlayerStore } from '../stores/player';
import { useUserStore } from '../stores/user';
import { useChatStore } from '../stores/chat';
import { useToast } from '../composables/useToast';
import { useChannelStore } from '../stores/channel';
import { WS_DEST } from '../constants/api';
import { socketService } from './socket';

/**
 * 处理游戏/播放器事件通知 (Toast)
 * 这里集中管理所有的业务通知文案
 */
function handleGameEvent(event) {
    const userStore = useUserStore();
    const chatStore = useChatStore();
    const { show, error } = useToast(); // This now uses the Pinia store wrapper
    const userName = event.userId === 'SYSTEM' ? '系统' : userStore.resolveName(event.userId);

    // 1. 处理特殊业务逻辑 (非 UI 展示)
    if (event.action === 'LIKE') {
        window.dispatchEvent(new CustomEvent('player:like', { detail: { userId: event.userId } }));
    }

    // 被管理员踢出频道：清理状态并回首页
    if (event.action === 'KICKED') {
        const playerStore = usePlayerStore();
        playerStore.leaveChannel();
        useChannelStore().clearCurrentChannel();
        window.location.href = '/';
        return;
    }

    if (event.action === 'RESET') {
        chatStore.messages = []; // 清空聊天
    }

    if (event.action === 'RENAME_FAILED' || (event.type === 'ERROR' && event.message && (event.message.includes('taken') || event.message.includes('占用')))) {
        error(event.message || '该名称已被占用，请更换。');
        userStore.showNameModal = true;
        return;
    }

    // 过滤掉用户进入/离开、以及歌曲开始播放的系统内部通知，避免弹窗干扰
    // 这些事件已经在 Chat Log 中展示，Toast 只展示关键交互
    if (event.action === 'USER_JOIN' || event.action === 'USER_LEAVE' || event.action === 'PLAY_START') {
        return;
    }

    // 2. 使用后端传来的格式化消息
    let msgText = event.message || event.payload || `${userName} ${event.action}`;

    // 将后端 Level 枚举映射为 toast 类型
    const typeMap = { 'error': 'error', 'warn': 'warning', 'success': 'success' };
    let type = typeMap[event.type?.toLowerCase()] || 'info';
    if (event.action === 'ERROR_LOAD') type = 'error';

    show({
        title: event.action === 'ERROR_LOAD' ? 'PLAYBACK ERROR'
             : event.action === 'SYSTEM_MESSAGE' ? 'SYSTEM'
             : event.action === 'MODE_CHANGE' ? '播放模式'
             : event.action,
        message: msgText,
        type: type,
        duration: 3000
    });
}

/**
 * 创建并返回 Socket 订阅配置（按频道动态 topic）
 * @param {number|string} channelId 当前频道 ID
 * @returns {Object} 订阅路径 -> 回调函数 的映射
 */
export const createSocketSubscriptions = (channelId = 1) => {
    const playerStore = usePlayerStore();
    const userStore = useUserStore();
    const chatStore = useChatStore();

    const ch = (suffix) => `/topic/channel/${channelId}/${suffix}`;

    return {
        // 1. 状态同步
        [ch('player/state')]: (state) => playerStore.syncState(state),
        [WS_DEST.USER_STATE]: (state) => playerStore.syncState(state),

        // 2. 队列更新
        [ch('player/queue')]: (data) => { playerStore.queue = data; },

        // 3. 事件通知 (Toast)
        [ch('events')]: handleGameEvent,
        [WS_DEST.USER_EVENTS]: handleGameEvent,

        // 4. 聊天相关
        [ch('chat')]: (msg) => chatStore.addMessage(msg),
        [WS_DEST.USER_PRIVATE_CHAT]: (msg) => chatStore.addMessage(msg),

        // 初始历史记录
        [WS_DEST.APP_CHAT_HISTORY]: (history) => chatStore.setHistory(history),

        // 分页历史记录回调
        [WS_DEST.USER_CHAT_HISTORY]: (moreMessages) => chatStore.prependHistory(moreMessages)
    };
};

/**
 * [新增] 创建 Socket 生命周期回调
 * 包含：连接成功处理、断连处理、错误处理
 */
export const createSocketCallbacks = () => {
    const playerStore = usePlayerStore();
    const userStore = useUserStore();

    return {
        // 连接成功
        onConnect: () => {
            playerStore.connected = true;
            // WS 已建立：刷新频道列表，让在线人数等数据准确（此时会话已注册到服务器）
            useChannelStore().fetchChannels().catch(() => {});
            // 发起同步
            setTimeout(() => {
                socketService.send(WS_DEST.RESYNC);
            }, 300);
            // 恢复绑定
            Object.entries(userStore.bindings).forEach(([platform, id]) => {
                if (id) playerStore.bindAccount(platform, id);
            });
        },

        // 连接断开 (含异常断开)
        onDisconnect: () => {
            playerStore.connected = false;
        },

        // STOMP 协议层错误 (如 Token失效、频道无权限、服务器内部错误等)
        onStompError: (frame) => {
            console.error('STOMP Error:', frame);

            const body = typeof frame.body === 'string' ? frame.body : JSON.stringify(frame.body || '');
            if (body.includes('AUTH_REQUIRED') || body.includes('CHANNEL_ACCESS_DENIED')) {
                // 登录失效或频道无权限：清除本地 token 或回到首页重新选择频道
                if (body.includes('AUTH_REQUIRED')) {
                    localStorage.removeItem('mp_token');
                    // 直接跳认证中心重新登录（带回跳），避免 /login 中间页二次点击
                    fetch('/api/config').then(r => r.json()).then(cfg => {
                        const authUrl = cfg.authCenterUrl || '';
                        const redirect = encodeURIComponent(window.location.origin + window.location.pathname);
                        window.location.href = authUrl ? `${authUrl}/login?redirect=${redirect}` : '/login';
                    }).catch(() => { window.location.href = '/login'; });
                } else {
                    window.location.href = '/';
                }
                return;
            }

            // 强制刷新页面 (STOMP ERROR 帧通常意味着连接已不可用)
            window.location.reload();
        }
    };
};

// 为了兼容旧代码命名，导出这个别名
const handleEventMessage = handleGameEvent;