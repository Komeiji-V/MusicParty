package org.thornex.musicparty.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.thornex.musicparty.util.MediaSigner;

import java.io.IOException;

/**
 * 媒体缓存鉴权（M1）：/media/** 上的签名 URL 校验。
 * 无有效签名（exp + sig 参数）一律 403，防止未登录用户直接枚举/下载缓存音频文件。
 */
@Component
public class MediaAuthFilter extends OncePerRequestFilter {

    private final MediaSigner mediaSigner;

    public MediaAuthFilter(MediaSigner mediaSigner) {
        this.mediaSigner = mediaSigner;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/media/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // 仅校验 GET/HEAD（下载/播放）；OPTIONS 预检放行
        String method = request.getMethod();
        if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)) {
            String mediaPath = request.getRequestURI(); // /media/xxx.m4a
            String exp = request.getParameter("exp");
            String sig = request.getParameter("sig");
            if (!mediaSigner.verify(mediaPath, exp, sig)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"message\":\"媒体链接无效或已过期\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
