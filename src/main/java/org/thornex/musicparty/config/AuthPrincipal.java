package org.thornex.musicparty.config;

public record AuthPrincipal(Long userId, String username, String role) {}
