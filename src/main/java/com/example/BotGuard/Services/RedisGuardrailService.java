package com.example.BotGuard.Services;


import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RedisGuardrailService {

    private final StringRedisTemplate redisTemplate;
    public void updateVirality(Long postId, String type) {
        String key = "post:" + postId + ":virality_score";
        int points = switch (type) {
            case "BOT_REPLY" -> 1;
            case "HUMAN_LIKE" -> 20;
            case "HUMAN_COMMENT" -> 50;
            default -> 0;
        };
        redisTemplate.opsForValue().increment(key, points);
    }

    public boolean validateBotGuardrails(Long botId, Long postId, Long humanId, int depth) {
        if (depth > 20) return false;

        String botCountKey = "post:" + postId + ":bot_count";
        Long count = redisTemplate.opsForValue().increment(botCountKey);

        if (count != null && count > 100) {
            return false;
        }

        String cooldownKey = "cooldown:bot_" + botId + ":human_" + humanId;
        Boolean success = redisTemplate.opsForValue().setIfAbsent(cooldownKey, "active", Duration.ofMinutes(10));

        return Boolean.TRUE.equals(success);
    }
}
