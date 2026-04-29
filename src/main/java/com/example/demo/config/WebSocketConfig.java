package com.example.demo.config;

import com.example.demo.websocket.QualityStreamWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final QualityStreamWebSocketHandler qualityStreamWebSocketHandler;

    public WebSocketConfig(QualityStreamWebSocketHandler qualityStreamWebSocketHandler) {
        this.qualityStreamWebSocketHandler = qualityStreamWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(qualityStreamWebSocketHandler, "/ws/quality-stream")
            .setAllowedOriginPatterns("*");
    }
}
