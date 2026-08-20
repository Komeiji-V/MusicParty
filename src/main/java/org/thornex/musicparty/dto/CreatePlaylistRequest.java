package org.thornex.musicparty.dto;

public record CreatePlaylistRequest(String name, String category, String coverUrl, Boolean isPublic) {}
