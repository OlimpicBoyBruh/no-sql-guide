package dev.nosqlguide.redis.examples.spring;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * Точка входа демо. По умолчанию только поднимает контекст.
 * Сценарий с записью профиля: -Ddemo.runner.enabled=true и запущенный Redis.
 */
@SpringBootApplication
public class RedisSpringDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(RedisSpringDemoApplication.class, args);
    }

    /**
     * Заменяет шаблон по умолчанию: без сериализаторов ключи уходят в Java-serialization
     * и в redis-cli выглядят как мусор. Значение — JSON со служебным полем типа.
     */
    @Bean
    RedisTemplate<String, UserProfile> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, UserProfile> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(RedisSerializer.string());
        // json() — Jackson 3 и поле @class, чтобы снимок читался обратно как UserProfile
        template.setValueSerializer(RedisSerializer.json());
        return template;
    }

    @Bean
    @ConditionalOnProperty(name = "demo.runner.enabled", havingValue = "true")
    CommandLineRunner profileDemo(UserProfileService service) {
        return args -> {
            service.save(new UserProfile("42", "Анна", "Казань", 0));
            service.registerVisit("42");
            UserProfile stored = service.find("42").orElseThrow();
            service.cacheSnapshot(stored);
            System.out.println("hash: " + stored);
            System.out.println("json: " + service.snapshot("42").orElseThrow());
        };
    }
}
