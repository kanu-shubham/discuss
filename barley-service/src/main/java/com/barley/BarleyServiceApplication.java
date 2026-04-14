package com.barley;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Barley Service.
 *
 * <p>Key production features enabled here:
 * <ul>
 *   <li>{@code @EnableAsync}      – activates Spring's async executor (thread pool in AsyncConfig)</li>
 *   <li>{@code @EnableCaching}    – activates Caffeine / Redis cache layer</li>
 *   <li>{@code @EnableScheduling} – activates @Scheduled tasks (e.g., cache warm-up)</li>
 * </ul>
 */
@SpringBootApplication
@EnableAsync
@EnableCaching
@EnableScheduling
public class BarleyServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BarleyServiceApplication.class, args);
    }
}
