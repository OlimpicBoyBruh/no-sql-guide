package dev.nosqlguide.redis.tasks.task08;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.Duration;

/**
 * Два кэша с разным TTL и общим префиксом. Условие: redis/tasks/08-spring-cache.md
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        // В Spring Data Redis 4 запись в кэш по умолчанию уходит в фон. Тест читает ключ
        // сразу после метода, поэтому без immediateWrites TTL и повторное чтение промахиваются.
        RedisCacheWriter writer = RedisCacheWriter.create(factory, c -> c.immediateWrites());
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .prefixCacheNameWith("catalog:")
                .disableCachingNullValues()
                .serializeValuesWith(SerializationPair.fromSerializer(RedisSerializer.json()));
        // entryTtl возвращает новую копию: из одной базы получаются разные сроки
        return RedisCacheManager.builder(writer)
                .cacheDefaults(base)
                .withCacheConfiguration("products", base.entryTtl(Duration.ofMinutes(10)))
                .withCacheConfiguration("categories", base.entryTtl(Duration.ofMinutes(1)))
                .build();
    }
}
