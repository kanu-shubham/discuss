package com.barley.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Cache configuration using Caffeine (in-process, high-performance).
 *
 * <p><b>Interview talking points:</b>
 * <ul>
 *   <li>Caffeine uses a W-TinyLFU eviction policy — better hit rate than LRU under typical workloads.</li>
 *   <li>expireAfterWrite vs expireAfterAccess: use expireAfterWrite for data freshness guarantees.</li>
 *   <li>In a multi-instance deployment, replace Caffeine with Redis (spring-boot-starter-data-redis)
 *       so all pods share the same cache state.</li>
 *   <li>Cache-aside pattern: app checks cache → if miss, load from DB → put into cache.</li>
 * </ul>
 */
@Configuration
public class CacheConfig {

    public static final String BARLEY_CACHE       = "barleyCache";
    public static final String BARLEY_LIST_CACHE  = "barleyListCache";
    public static final String BARLEY_STATS_CACHE = "barleyStatsCache";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(
                BARLEY_CACHE, BARLEY_LIST_CACHE, BARLEY_STATS_CACHE);
        manager.setCaffeine(caffeineCacheBuilder());
        return manager;
    }

    private Caffeine<Object, Object> caffeineCacheBuilder() {
        return Caffeine.newBuilder()
                .maximumSize(1_000)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .recordStats();   // exposes hit/miss metrics to Micrometer
    }
}
