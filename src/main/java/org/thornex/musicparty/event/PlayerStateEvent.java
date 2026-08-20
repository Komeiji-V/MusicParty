package org.thornex.musicparty.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import org.thornex.musicparty.dto.PlayerState;

@Getter
public class PlayerStateEvent extends ApplicationEvent {
    private final Long channelId;
    private final PlayerState state;

    public PlayerStateEvent(Object source, Long channelId, PlayerState state) {
        super(source);
        this.channelId = channelId != null ? channelId : 1L;
        this.state = state;
    }

    public PlayerStateEvent(Object source, PlayerState state) {
        this(source, 1L, state);
    }
}
