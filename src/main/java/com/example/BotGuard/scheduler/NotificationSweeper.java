package com.example.BotGuard.scheduler;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;


@Component
@RequiredArgsConstructor
public class NotificationSweeper {
    private final StringRedisTemplate redisTemplate;

    @Scheduled(fixedRate = 300000)
    public void sweep() {
        Set<String> keys = redisTemplate.keys("user:*:pending_notifs");
        if (keys == null) return;

        for (String key : keys) {
            String userId = key.split(":")[1];
            List<String> notifs = redisTemplate.opsForList().range(key, 0, -1);
            if (notifs != null && !notifs.isEmpty()) {

                System.out.println("Summarized Push Notification: Bot " + notifs.size() + " interactions for User " + userId);
                redisTemplate.delete(key);
            }
        }
    }
}
