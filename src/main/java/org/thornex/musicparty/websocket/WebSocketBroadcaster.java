package org.thornex.musicparty.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.thornex.musicparty.dto.PlayerEvent;
import org.thornex.musicparty.dto.User;
import org.thornex.musicparty.enums.PlayerAction;
import org.thornex.musicparty.event.PlayerStateEvent;
import org.thornex.musicparty.event.QueueUpdateEvent;
import org.thornex.musicparty.event.SystemMessageEvent;
import org.thornex.musicparty.service.UserService;
import org.thornex.musicparty.util.MessageFormatter;

/**
 * 频道级广播：使用频道专属 topic（/topic/channel/{channelId}/...）全局广播，
 * 客户端按自己所在频道的 topic 订阅，实现频道隔离。
 */
@Component
@RequiredArgsConstructor
public class WebSocketBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;
    private final UserService userService;

    @EventListener
    public void onPlayerStateChanged(PlayerStateEvent event) {
        messagingTemplate.convertAndSend(channelTopic(event.getChannelId(), "player/state"), event.getState());
    }

    @EventListener
    public void onQueueChanged(QueueUpdateEvent event) {
        messagingTemplate.convertAndSend(channelTopic(event.getChannelId(), "player/queue"), event.getQueue());
    }

    @EventListener
    public void onSystemMessage(SystemMessageEvent event) {
        String actionCode = event.getAction() != null ? event.getAction().name() : "";
        String type = event.getLevel().name();

        String userName = "SYSTEM";
        if (!"SYSTEM".equals(event.getUserId())) {
            userName = userService.getUserByToken(event.getUserId())
                    .map(User::getName)
                    .orElse("Unknown");
        }

        String formattedMessage = MessageFormatter.format(event, userName);

        PlayerEvent playerEvent = new PlayerEvent(
                type,
                actionCode,
                event.getUserId(),
                formattedMessage,
                event.getPayload()
        );
        messagingTemplate.convertAndSend(channelTopic(event.getChannelId(), "events"), playerEvent);
    }

    private String channelTopic(Long channelId, String suffix) {
        return "/topic/channel/" + channelId + "/" + suffix;
    }
}
