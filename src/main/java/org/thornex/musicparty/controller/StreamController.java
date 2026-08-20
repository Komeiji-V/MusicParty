package org.thornex.musicparty.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.thornex.musicparty.service.stream.LiveStreamService;
import org.thornex.musicparty.service.stream.StreamTokenService;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
public class StreamController {

    private final LiveStreamService liveStreamService;
    private final StreamTokenService streamTokenService;
    private final org.thornex.musicparty.config.AppProperties appProperties;

    @GetMapping(value = "/radio/stream", produces = "audio/mpeg")
    public void streamAudio(HttpServletRequest request, HttpServletResponse response, @RequestParam(name = "key", required = false) String key) {
        if (!liveStreamService.isEnabled()) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }

        if (!streamTokenService.validateToken(key)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        response.setContentType("audio/mpeg");
        response.setHeader("Transfer-Encoding", "chunked");
        response.setHeader("Connection", "keep-alive");
        // 这是一个伪直播，不应该被缓存
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");

        String remoteAddr = getClientIp(request);
        OutputStream os = null;
        try {
            os = response.getOutputStream();
            liveStreamService.addListener(os, remoteAddr);

            // 阻塞保持连接：客户端断开后 broadcaster 写失败会移除该客户端，
            // 因此定期唤醒检查活跃状态即可自愈，避免永久占用 Tomcat 线程（旧实现 os.wait() 无任何 notify，可被刷满线程池）。
            while (liveStreamService.isClientActive(os)) {
                synchronized (os) {
                    os.wait(15000);
                }
            }
        } catch (IOException e) {
            log.debug("Stream client disconnected: {}", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.debug("Stream thread interrupted for client {}", remoteAddr);
        } finally {
            if (os != null) {
                liveStreamService.removeListener(os, remoteAddr);
            }
        }
    }

    /** 获取自己的直播流链接（//stream 命令的可视化替代） */
    @GetMapping("/api/stream/link")
    public Map<String, Object> getStreamLink() {
        Long userId = org.thornex.musicparty.config.SecurityConfig.getCurrentUserId();
        if (userId == null) {
            throw new org.springframework.security.access.AccessDeniedException("未登录");
        }
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("enabled", liveStreamService.isEnabled());
        if (liveStreamService.isEnabled()) {
            String token = streamTokenService.generateToken(String.valueOf(userId));
            String base = appProperties.getMusicApi().getBaseUrl();
            if (base == null || base.isEmpty()) base = "";
            else if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
            result.put("link", base + "/radio/stream?key=" + token);
            result.put("expiresIn", "24h（闲置 4h）");
        }
        return result;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 处理多级代理情况，取第一个非 unknown 的 IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
