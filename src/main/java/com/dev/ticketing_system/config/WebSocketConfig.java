package com.dev.ticketing_system.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 서버 -> 클라이언트로 메시지 보낼 때 붙일 주소 (구독용)
        config.enableSimpleBroker("/topic");
        // 클라이언트 -> 서버로 메시지 보낼 때 붙일 주소 (이번엔 안 씀)
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 클라이언트가 웹소켓 연결할 때 사용할 주소: ws://localhost:8083/ws-queue
        registry.addEndpoint("/ws-queue")
                .setAllowedOriginPatterns("*") // CORS 허용
                .withSockJS(); // SockJS 지원 (브라우저 호환성)
    }
}