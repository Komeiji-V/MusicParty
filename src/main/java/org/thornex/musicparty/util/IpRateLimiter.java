package org.thornex.musicparty.util;

import org.springframework.stereotype.Component;

import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 简易 IP 滑动窗口限流（内存实现，单实例部署足够）。
 * 用于登录交换、第三方搜索代理、流连接等被循环调用即可打爆资源的端点。
 */
@Component
public class IpRateLimiter {

    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    /**
     * @param key 限流键（一般传 IP）
     * @param maxRequests 窗口内最大请求数
     * @param windowMs 窗口长度（毫秒）
     * @return true=放行，false=超限
     */
    public boolean allow(String key, int maxRequests, long windowMs) {
        if (key == null || key.isBlank()) key = "unknown";
        long now = System.currentTimeMillis();
        Deque<Long> q = hits.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > windowMs) {
                q.pollFirst();
            }
            if (q.size() >= maxRequests) {
                return false;
            }
            q.addLast(now);
            return true;
        }
    }

    public void clear(String key) {
        if (key != null) hits.remove(key);
    }
}
