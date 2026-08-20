package org.thornex.musicparty.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * SPA 回退：前端路由（vue-router history 模式）直接访问或刷新时返回 index.html。
 * 仅显式覆盖前端路由路径，不干扰 /api、/ws 与静态资源。
 */
@Controller
public class SpaRedirectController {

    @RequestMapping(value = {
            "/login",
            "/room",
            "/profile",
            "/playlists",
            "/admin",
            "/channel/**",
            "/u/**"
    })
    public String redirect() {
        return "forward:/index.html";
    }
}
