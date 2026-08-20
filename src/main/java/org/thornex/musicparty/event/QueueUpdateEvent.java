package org.thornex.musicparty.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import org.thornex.musicparty.dto.MusicQueueItem;

import java.util.List;

@Getter
public class QueueUpdateEvent extends ApplicationEvent {
    private final Long channelId;
    private final List<MusicQueueItem> queue;

    public QueueUpdateEvent(Object source, Long channelId, List<MusicQueueItem> queue) {
        super(source);
        this.channelId = channelId != null ? channelId : 1L;
        this.queue = queue;
    }

    public QueueUpdateEvent(Object source, List<MusicQueueItem> queue) {
        this(source, 1L, queue);
    }
}
