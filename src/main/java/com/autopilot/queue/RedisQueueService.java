package com.autopilot.queue;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RedisQueueService {

    private final RedisTemplate<String, String> redisTemplate;
    private static final String DEPLOYMENT_QUEUE = "autopilot:deployments";
    public void enqueue(String deploymentId) {

        redisTemplate.opsForList().rightPush(DEPLOYMENT_QUEUE, deploymentId);
    }

    public String dequeueBlocking() {

        return redisTemplate.opsForList()
                .leftPop(DEPLOYMENT_QUEUE, Duration.ofSeconds(0));
    }
}
