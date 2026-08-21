package org.thornex.musicparty.dto;

import java.util.List;

public record PlayerState(
        NowPlayingInfo nowPlaying,
        List<MusicQueueItem> queue,
        String playMode,
        boolean isShuffle,
        boolean isFairShuffle,
        boolean allowOfflineShuffle,
        List<UserSummary> onlineUsers,
        boolean isPaused,
        boolean isPauseLocked,
        boolean isSkipLocked,
        boolean isPlayModeLocked,
        boolean isLoading,
        int streamListenerCount,
        boolean isStreamEnabled,
        boolean isVoteSkipEnabled,
        double voteSkipThreshold,
        int voteSkipWaitTime,
        int currentVotes,
        int eligibleUsers,
        AppConfigSummary config
) {
    public record AppConfigSummary(
            int maxQueueSize,
            int maxHistorySize,
            int maxUserSongs,
            int maxPlaylistImportSize,
            int maxChatHistorySize,
            long minChatIntervalMs,
            int maxChatMessageLength,
            boolean neteaseEnabled,
            boolean bilibiliEnabled,
            boolean qqEnabled,
            boolean kugouEnabled,
            // 频道级音源开关（ChannelConfig source_xxx_enabled；频道管理里切换后广播，搜索弹窗按此过滤）
            boolean neteaseSourceEnabled,
            boolean bilibiliSourceEnabled,
            boolean qqSourceEnabled,
            boolean kugouSourceEnabled,
            boolean voteSkipEnabled,
            double voteSkipThreshold,
            int voteSkipWaitTime
    ) {}
}