package dev.nosqlguide.redis.examples.spring;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Три способа записать одно и то же: строки в Hash, счётчик через HINCRBY
 * и весь объект одним JSON-значением с TTL.
 */
@Service
public class UserProfileService {

    private final StringRedisTemplate strings;
    private final RedisTemplate<String, UserProfile> profiles;

    public UserProfileService(StringRedisTemplate strings, RedisTemplate<String, UserProfile> profiles) {
        this.strings = strings;
        this.profiles = profiles;
    }

    /** Hash demo:profile:{id}. Поля — обычные строки, их видно в redis-cli. */
    public void save(UserProfile profile) {
        strings.opsForHash().putAll(hashKey(profile.id()), Map.of(
                "id", profile.id(),
                "name", profile.name(),
                "city", profile.city(),
                "visits", Integer.toString(profile.visits())));
    }

    public Optional<UserProfile> find(String id) {
        Map<Object, Object> fields = strings.opsForHash().entries(hashKey(id));
        if (fields.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new UserProfile(
                (String) fields.get("id"),
                (String) fields.get("name"),
                (String) fields.get("city"),
                Integer.parseInt((String) fields.get("visits"))));
    }

    /** HINCRBY по полю visits. Если поля не было, Redis начинает с нуля. */
    public long registerVisit(String id) {
        Long visits = strings.opsForHash().increment(hashKey(id), "visits", 1);
        return visits == null ? 0 : visits;
    }

    /** JSON-снимок живёт 5 минут. Это отдельный ключ, Hash он не заменяет. */
    public void cacheSnapshot(UserProfile profile) {
        profiles.opsForValue().set(jsonKey(profile.id()), profile, Duration.ofMinutes(5));
    }

    public Optional<UserProfile> snapshot(String id) {
        return Optional.ofNullable(profiles.opsForValue().get(jsonKey(id)));
    }

    private static String hashKey(String id) {
        return "demo:profile:" + id;
    }

    private static String jsonKey(String id) {
        return "demo:profile-json:" + id;
    }
}
