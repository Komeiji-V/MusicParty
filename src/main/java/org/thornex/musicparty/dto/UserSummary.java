package org.thornex.musicparty.dto;

/** 在线用户摘要；username 为真实账户名（游客为 null），title/titleColor 为当前展示称号（彩色矩形标签，可为空） */
public record UserSummary(String token, String sessionId, String name, boolean isGuest, String username, String title, String titleColor) {
    public UserSummary(String token, String sessionId, String name, boolean isGuest) {
        this(token, sessionId, name, isGuest, null, null, null);
    }
    public UserSummary(String token, String sessionId, String name, boolean isGuest, String title, String titleColor) {
        this(token, sessionId, name, isGuest, null, title, titleColor);
    }
}