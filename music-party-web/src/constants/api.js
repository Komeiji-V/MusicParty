// WebSocket 目的地
export const WS_DEST = {
    // 发送指令 (Publish)
    CHAT_SEND: '/app/chat',
    CHAT_HISTORY_FETCH: '/app/chat/history/fetch',
    PLAYER_NEXT: '/app/control/next',
    PLAYER_PAUSE: '/app/control/toggle-pause',
    PLAYER_SHUFFLE: '/app/control/toggle-shuffle',
    PLAYER_LIKE: '/app/control/like',
    ENQUEUE: '/app/enqueue',
    ENQUEUE_PLAYLIST: '/app/enqueue/playlist',
    QUEUE_TOP: '/app/queue/top',
    QUEUE_REMOVE: '/app/queue/remove',
    QUEUE_CLEAR_MINE: '/app/queue/clear-mine',
    USER_BIND: '/app/user/bind',
    USER_RENAME: '/app/user/rename',
    RESYNC: '/app/player/resync',

    // 订阅频道 (Subscribe) —— 频道级动态 topic 由 socketHandler 按 channelId 拼接
    // TOPIC_EVENTS/STATE/QUEUE/CHAT 已改为 /topic/channel/{channelId}/...

    // 个人频道
    USER_ME: '/app/user/me',
    USER_ME_UPDATE: '/user/queue/me',
    APP_CHAT_HISTORY: '/app/chat/history',
    USER_STATE: '/user/queue/player/state',
    USER_CHAT_HISTORY: '/user/queue/chat/history',
    USER_EVENTS: '/user/queue/events',
    USER_PRIVATE_CHAT: '/user/queue/chat/private'
};