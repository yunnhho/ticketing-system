package com.dev.ticketing_system.config;

import com.dev.ticketing_system.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper; // JSON 응답용

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 클라이언트 IP 추출 (실제 운영 환경에선 X-Forwarded-For 헤더 확인 필요)
        String clientIp = request.getRemoteAddr();
        String requestUri = request.getRequestURI();

        // 2. Redis Key 생성 (IP별 + API별 제한)
        // 예: rate_limit:127.0.0.1:/api/seats/1/occupy
        String limiterKey = "rate_limit:" + clientIp + ":" + requestUri;

        RRateLimiter rateLimiter = redissonClient.getRateLimiter(limiterKey);

        // 3. 규칙 설정: 1분(60초)에 최대 100회 요청 허용 (상황에 따라 조절)
        // trySetRate는 이미 설정되어 있으면 false를 반환하므로 성능 영향 거의 없음
        // rateLimiter.trySetRate(RateType.OVERALL, 100, 60, RateIntervalUnit.SECONDS);

        // 테스트용 설정
        rateLimiter.trySetRate(RateType.OVERALL, 10000, 60, RateIntervalUnit.SECONDS);


        // 4. 토큰 획득 시도 (Non-blocking)
        if (!rateLimiter.tryAcquire()) {
            log.warn("Rate Limit Exceeded - IP: {}, URI: {}", clientIp, requestUri);

            // 429 Too Many Requests 응답 반환
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");

            // 아까 만든 ApiResponse 활용
            String jsonResponse = objectMapper.writeValueAsString(
                    ApiResponse.error("요청 횟수가 너무 많습니다. 잠시 후 다시 시도해주세요.")
            );
            response.getWriter().write(jsonResponse);

            return false; // 컨트롤러 진입 차단
        }

        return true; // 통과
    }
}