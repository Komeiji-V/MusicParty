package org.thornex.musicparty.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class UserCountChangeEvent extends ApplicationEvent {
    private final Long channelId;
    private final int onlineUserCount;

    public UserCountChangeEvent(Object source, Long channelId, int onlineUserCount) {
        super(source);
        this.channelId = channelId != null ? channelId : 1L;
        this.onlineUserCount = onlineUserCount;
    }

    public UserCountChangeEvent(Object source, int onlineUserCount) {
        this(source, 1L, onlineUserCount);
    }
}
