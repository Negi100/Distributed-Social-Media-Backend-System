package com.example.BotGuard.Services;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final StringRedisTemplate redisTemplate;

    public void handleNotification(Long userId, String botName) {
        String cooldownKey = "notif_cooldown:" + userId;
        String listKey = "user:" + userId + ":pending_notifs";

        if (Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey))) {
            redisTemplate.opsForList().rightPush(listKey, "Bot " + botName + " replied to your post");
        } else {
            System.out.println("Push Notification Sent to User: " + userId);
            redisTemplate.opsForValue().set(cooldownKey, "active", Duration.ofMinutes(15));
        }
    }
}
