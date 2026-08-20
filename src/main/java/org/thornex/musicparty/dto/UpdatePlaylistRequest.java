package org.thornex.musicparty.dto;

public record UpdatePlaylistRequest(String name, String category, Boolean isPublic) {}
