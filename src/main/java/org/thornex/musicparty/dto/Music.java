package org.thornex.musicparty.dto;

import java.util.List;

public record Music(
        String id,
        String name,
        List<String> artists,
        long duration, // in milliseconds
        String platform,
        String coverUrl,
        String album,
        Integer fee // 网易云收费标识：0/无=免费，>0=VIP/付费（可为 null=未知）
) {
    public Music(String id, String name, List<String> artists, long duration, String platform, String coverUrl) {
        this(id, name, artists, duration, platform, coverUrl, null, null);
    }

    public Music(String id, String name, List<String> artists, long duration, String platform, String coverUrl, String album) {
        this(id, name, artists, duration, platform, coverUrl, album, null);
    }
}
