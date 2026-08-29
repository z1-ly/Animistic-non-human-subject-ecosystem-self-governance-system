package com.ecovoice.config;

import com.ecovoice.service.VitalSignStreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final VitalSignStreamService streamService;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new VitalSignWebSocketHandler(streamService), "/ws/vitals")
                .setAllowedOrigins("*");
    }

    @RequiredArgsConstructor
    static class VitalSignWebSocketHandler extends TextWebSocketHandler {

        private final VitalSignStreamService streamService;

        @Override
        public void afterConnectionEstablished(org.springframework.web.socket.WebSocketSession session) {
            streamService.register(session);
        }

        @Override
        public void afterConnectionClosed(org.springframework.web.socket.WebSocketSession session,
                                          org.springframework.web.socket.CloseStatus status) {
            streamService.unregister(session);
        }
    }
}
