package org.thornex.musicparty.service.api;

import org.springframework.stereotype.Component;
import org.thornex.musicparty.exception.ApiRequestException;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class MusicProviderFactory {

    private final Map<String, MusicProvider> providerMap;

    public MusicProviderFactory(List<MusicProvider> providers) {
        this.providerMap = providers.stream()
                .collect(Collectors.toMap(MusicProvider::getPlatformName, Function.identity()));
    }

    public MusicProvider getProvider(String platform) {
        MusicProvider provider = providerMap.get(platform);
        if (provider == null) {
            throw new ApiRequestException("Unsupported platform: " + platform);
        }
        return provider;
    }

    public List<MusicProvider> getEnabledProviders() {
        return providerMap.values().stream()
                .filter(MusicProvider::isEnabled)
                .collect(Collectors.toList());
    }

    public boolean isProviderEnabled(String platform) {
        MusicProvider provider = providerMap.get(platform);
        return provider != null && provider.isEnabled();
    }

    public Map<String, MusicProvider> getProviderMap() {
        return providerMap;
    }
}
