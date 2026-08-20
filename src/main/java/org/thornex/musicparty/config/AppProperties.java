package org.thornex.musicparty.config;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
@Data
public class AppProperties {

    private JwtConfig jwt = new JwtConfig();
    private AuthCenterConfig authCenter = new AuthCenterConfig();
    private MusicApiConfig musicApi = new MusicApiConfig();

    @Data
    public static class JwtConfig {
        private String secret = "musicparty-jwt-secret-change-in-production";
        private long expirationMs = 604800000;
    }

    @Data
    public static class AuthCenterConfig {
        private String url = "http://localhost:8000";
    }

    @ConfigurationProperties(prefix = "app.music-api")
    @Data
    public static class MusicApiConfig {
        private NeteaseApiConfig netease;
        private QqApiConfig qq;
        private KugouApiConfig kugou;
        private BilibiliApiConfig bilibili;
        private String adminPassword;
        private String baseUrl;
        private String authorName = "ThorNex";
        private String backWords = "THORNEX";
        private String ffmpegPath = "ffmpeg";
        private QueueConfig queue = new QueueConfig();
        private PlayerConfig player = new PlayerConfig();
        private ChatConfig chat = new ChatConfig();
        private CacheConfig cache = new CacheConfig();
        private AuthConfig auth = new AuthConfig();
    }

    @Data
    public static class QueueConfig {
        private int maxSize = 1000;
        private int historySize = 50;
        private int maxUserSongs = 100;
    }

    @Data
    public static class PlayerConfig {
        private int maxPlaylistImportSize = 100;
        private boolean voteSkipEnabled = false;
        private double voteSkipThreshold = 0.5;
        private int voteSkipWaitTime = 15;
    }

    @Data
    public static class ChatConfig {
        private int maxHistorySize = 1000;
        private long minIntervalMs = 1000;
        private int maxMessageLength = 200;
    }

    @Data
    public static class CacheConfig {
        private org.springframework.util.unit.DataSize maxSize = org.springframework.util.unit.DataSize.ofGigabytes(1);
    }

    @Data
    public static class AuthConfig {
        private boolean rateLimitEnabled = true;
        private int maxAttempts = 5;
        private int windowSeconds = 60;
        private int blockDurationSeconds = 300;
    }

    @Data
    public static class ApiConfig {
        private String baseUrl;
    }

    @EqualsAndHashCode(callSuper = true)
    @Data
    public static class BilibiliApiConfig extends ApiConfig {
        private String sessdata;
        private boolean enabled = true;
    }

    @EqualsAndHashCode(callSuper = true)
    @Data
    public static class NeteaseApiConfig extends ApiConfig {
        private String cookie;
        private String quality = "exhigh";
        private boolean enabled = true;
    }

    @EqualsAndHashCode(callSuper = true)
    @Data
    public static class QqApiConfig extends ApiConfig {
        private String cookie;
        private String quality = "320";
        private boolean enabled = true;
    }

    @Data
    public static class KugouApiConfig {
        private String cookie;
        private boolean enabled = true;
    }
}