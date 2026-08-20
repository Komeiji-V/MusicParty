package org.thornex.musicparty.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import org.thornex.musicparty.enums.PlayerAction;

@Getter
public class SystemMessageEvent extends ApplicationEvent {

    public enum Level { INFO, WARN, ERROR, SUCCESS }

    private final Level level;
    private final PlayerAction action;
    private final String userId;
    private final String payload;
    private final Long channelId;

    public SystemMessageEvent(Object source, Level level, PlayerAction action, String userId, String payload, Long channelId) {
        super(source);
        this.level = level;
        this.action = action;
        this.userId = userId;
        this.payload = payload;
        this.channelId = channelId != null ? channelId : 1L;
    }

    public SystemMessageEvent(Object source, Level level, PlayerAction action, String userId, String payload) {
        this(source, level, action, userId, payload, 1L);
    }
}
