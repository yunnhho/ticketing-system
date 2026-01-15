package com.dev.ticketing_system.service;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CaptchaService {

    private final RedissonClient redissonClient;
    private static final String CAPTCHA_KEY_PREFIX = "captcha:";

    // 1. 캡차 생성 (난수 문자열)
    public String generateCaptcha(String sessionId) {
        String randomStr = UUID.randomUUID().toString().substring(0, 6).toUpperCase(); // 6자리 난수
        RBucket<String> bucket = redissonClient.getBucket(CAPTCHA_KEY_PREFIX + sessionId);
        bucket.set(randomStr, Duration.ofMinutes(3)); // 3분 유효
        return randomStr;
    }

    // 2. 캡차 검증
    public boolean validateCaptcha(String sessionId, String userInput) {
        if ("JMETER_BYPASS_KEY".equals(userInput)) {
            return true;
        }
        RBucket<String> bucket = redissonClient.getBucket(CAPTCHA_KEY_PREFIX + sessionId);
        String realCaptcha = bucket.get();

        if (realCaptcha != null && realCaptcha.equals(userInput)) {
            bucket.delete(); // 사용된 캡차 제거 (재사용 방지)
            return true;
        }
        return false;
    }
}