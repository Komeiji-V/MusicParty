package org.thornex.musicparty.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import jakarta.servlet.http.HttpServletResponse;
import org.thornex.musicparty.repository.UserRepository;
import org.thornex.musicparty.util.JwtUtil;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Bean
    public JwtAuthFilter jwtAuthFilter(JwtUtil jwtUtil, UserRepository userRepository) {
        return new JwtAuthFilter(jwtUtil, userRepository);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(AppProperties appProperties) {
        CorsConfiguration config = new CorsConfiguration();
        // CORS 白名单（M2）：不再回显任意 Origin + 凭据；默认仅本机开发端口
        List<String> origins = new java.util.ArrayList<>(Arrays.stream(appProperties.getCors().getAllowedOrigins().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList());
        // SSO 新协议：auth-center 登录页跨域 POST /api/auth/sso，其 origin 自动加入白名单
        String authOrigin = extractOrigin(appProperties.getAuthCenter().getUrl());
        if (authOrigin != null && !origins.contains(authOrigin)) {
            origins.add(authOrigin);
        }
        config.setAllowedOriginPatterns(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/public/**", config);
        // SSO 登录回跳（auth-center → 小站）需要跨域 POST
        source.registerCorsConfiguration("/api/auth/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter,
                                                   MediaAuthFilter mediaAuthFilter,
                                                   AppProperties appProperties) throws Exception {
        // CSP connect-src：同源 + WS + 认证中心（SSO 交换为跨源 fetch，需放行其 origin）
        String authOrigin = extractOrigin(appProperties.getAuthCenter().getUrl());
        String csp = "default-src 'self'; " +
                "img-src 'self' data: http: https:; " +
                "media-src 'self' blob: http: https:; " +
                "style-src 'self' 'unsafe-inline'; " +
                "script-src 'self'; " +
                "connect-src 'self' ws: wss:" + (authOrigin != null ? " " + authOrigin : "") + "; " +
                "font-src 'self' data:; " +
                "object-src 'none'; " +
                "base-uri 'self'; " +
                "frame-ancestors 'none'";
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> {})
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // L5：安全响应头（CSP / HSTS / X-Frame-Options / nosniff）
                .headers(headers -> headers
                        .contentSecurityPolicy(cspCfg -> cspCfg.policyDirectives(csp))
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
                        .frameOptions(frame -> frame.deny()))
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"message\":\"未登录或登录已过期\"}");
                }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/config/**").permitAll()
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers("/static/**").permitAll()
                        .requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico", "/vite.svg").permitAll()
                        // SPA 前端路由（history 模式直访/刷新）；新增前端路由需同步加入
                        .requestMatchers("/login", "/room", "/room/**", "/profile", "/playlists", "/channel/**", "/admin", "/u/**").permitAll()
                        // 媒体缓存：签名 URL 鉴权由 MediaAuthFilter 处理（M1）
                        .requestMatchers("/media/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("SUPER_ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        // 默认拒绝（M1）：未显式放行的路径一律 401/403，避免新端点漏配
                        .anyRequest().denyAll()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(mediaAuthFilter, org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    public static Long getCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthPrincipal principal) {
            return principal.userId();
        }
        return null;
    }

    public static String getCurrentUserRole() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthPrincipal principal) {
            return principal.role();
        }
        return null;
    }

    /** 从配置 URL 提取 origin（用于 CSP connect-src 放行认证中心） */
    private static String extractOrigin(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            java.net.URI uri = java.net.URI.create(url);
            if (uri.getScheme() == null || uri.getHost() == null) return null;
            int port = uri.getPort();
            return uri.getScheme() + "://" + uri.getHost() + (port > 0 ? ":" + port : "");
        } catch (Exception e) {
            return null;
        }
    }
}
