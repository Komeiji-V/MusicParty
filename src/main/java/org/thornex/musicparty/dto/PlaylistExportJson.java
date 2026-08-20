package org.thornex.musicparty.dto;

import java.util.List;

public record PlaylistExportJson(String name, String category, List<Music> songs) {}
