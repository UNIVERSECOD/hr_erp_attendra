package com.hic.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-lived in-memory cache for device event pictures fetched right after search.
 * Hikvision {@code @WEB…} picture tokens can expire quickly.
 */
@Service
public class DevicePictureCache {

    private static final long TTL_SECONDS = 30 * 60;

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public void put(String pictureUrl, byte[] bytes) {
        if (pictureUrl == null || pictureUrl.isBlank() || bytes == null || bytes.length == 0) {
            return;
        }
        cache.put(pictureUrl, new CacheEntry(bytes, Instant.now()));
    }

    public Optional<byte[]> get(String pictureUrl) {
        if (pictureUrl == null || pictureUrl.isBlank()) {
            return Optional.empty();
        }
        CacheEntry entry = cache.get(pictureUrl);
        if (entry == null) {
            return Optional.empty();
        }
        if (Instant.now().isAfter(entry.storedAt().plusSeconds(TTL_SECONDS))) {
            cache.remove(pictureUrl);
            return Optional.empty();
        }
        return Optional.of(entry.bytes());
    }

    private record CacheEntry(byte[] bytes, Instant storedAt) {
    }
}
