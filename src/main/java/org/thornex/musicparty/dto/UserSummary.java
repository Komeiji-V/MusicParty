package org.thornex.musicparty.dto;

/** 在线用户摘要；username 为真实账户名（游客为 null），authUid 为不可变认证中心 ID（公开主页路由用），title/titleColor 为当前展示称号（彩色矩形标签，可为空） */
public record UserSummary(String token, String sessionId, String name, boolean isGuest, String username, String title, String titleColor, Long authUid) {
    public UserSummary(String token, String sessionId, String name, boolean isGuest) {
        this(token, sessionId, name, isGuest, null, null, null, null);
    }
    public UserSummary(String token, String sessionId, String name, boolean isGuest, String title, String titleColor) {
        this(token, sessionId, name, isGuest, null, title, titleColor, null);
    }
}
