package org.thornex.musicparty.config;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.thornex.musicparty.entity.User;
import org.thornex.musicparty.repository.UserRepository;
import org.thornex.musicparty.util.JwtUtil;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

// 注意：不标注 @Component，避免被 Spring Boot 自动注册为全局 Servlet Filter
// 造成与 Security 过滤器链内的注册冲突（双重注册导致认证不生效）
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    // 保存认证上下文到 request attribute，供 Mono 异步 dispatch 阶段恢复
    private final SecurityContextRepository securityContextRepository = new RequestAttributeSecurityContextRepository();

    public JwtAuthFilter(JwtUtil jwtUtil, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);

        if (StringUtils.hasText(token)) {
            try {
                Claims claims = jwtUtil.validateToken(token);
                Long uid = claims.get("uid", Long.class);

                // 本地账号关联（uid -> 本地 User），用于频道管理员/歌单归属等
                User user = uid != null ? userRepository.findByAuthUid(uid).orElse(null) : null;
                if (user != null) {
                    AuthPrincipal principal = new AuthPrincipal(user.getId(), user.getUsername(), user.getRole().name());
                    List<SimpleGrantedAuthority> authorities = Collections.singletonList(
                            new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
                    );
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(principal, null, authorities);

                    SecurityContext context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(authentication);
                    SecurityContextHolder.setContext(context);
                    // 关键：保存上下文，使 Mono 异步 dispatch 时 SecurityContextHolderFilter 能恢复认证
                    securityContextRepository.saveContext(context, request, response);
                } else {
                    log.warn("JWT uid {} not linked to local user", uid);
                }
            } catch (Exception e) {
                log.error("JWT validation failed for {} {}: {}", request.getMethod(), request.getRequestURI(), e.toString());
            }
        } else {
            // L4：匿名请求是常态（公开接口/静态资源），WARN 会刷日志盘，降为 DEBUG
            log.debug("No auth token for {}", request.getRequestURI());
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
