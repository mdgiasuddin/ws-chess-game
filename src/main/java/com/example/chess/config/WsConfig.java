package com.example.chess.config;

import com.example.chess.game.ChessHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WsConfig implements WebSocketConfigurer {

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Tighten setAllowedOrigins for production.
        registry.addHandler(new ChessHandler(), "/ws/chess").setAllowedOrigins("*");
    }
}
