package dev.nosqlguide.redis.examples.spring;

import dev.nosqlguide.redis.examples.support.RedisContainers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = RedisSpringDemoApplication.class)
class UserProfileServiceSmokeTest {

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", RedisContainers::host);
        registry.add("spring.data.redis.port", RedisContainers::port);
    }

    @Autowired
    UserProfileService service;
    @Autowired
    StringRedisTemplate redis;

    @Test
    void hashRoundTripAndIncrement() {
        service.save(new UserProfile("42", "Анна", "Казань", 0));
        service.registerVisit("42");
        service.registerVisit("42");

        assertThat(service.find("42")).contains(new UserProfile("42", "Анна", "Казань", 2));
        assertThat(redis.type("demo:profile:42").code()).isEqualTo("hash");
    }

    @Test
    void jsonSnapshotHasTtl() {
        var profile = new UserProfile("7", "Олег", "Москва", 3);
        service.cacheSnapshot(profile);

        assertThat(service.snapshot("7")).contains(profile);
        assertThat(redis.getExpire("demo:profile-json:7")).isBetween(290L, 300L);
    }
}
