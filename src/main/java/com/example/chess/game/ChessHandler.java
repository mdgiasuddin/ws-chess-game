package com.example.chess.game;

import org.jspecify.annotations.NonNull;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChessHandler extends TextWebSocketHandler {

    private final Object lock = new Object();
    private WebSocketSession waiting;
    private final Map<String, Seat> seatMap = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession raw) throws Exception {
        // Serialises sends so two threads can never write to one session at once.
        WebSocketSession session = new ConcurrentWebSocketSessionDecorator(raw, 5000, 512 * 1024);

        synchronized (lock) {
            if (waiting == null) {
                waiting = session;
                session.sendMessage(new TextMessage("{\"t\":\"wait\"}"));
                return;
            }
            WebSocketSession white = waiting;
            waiting = null;
            ChessRoom room = new ChessRoom(white, session);
            seatMap.put(white.getId(), new Seat(room, 0));
            seatMap.put(session.getId(), new Seat(room, 1));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession raw, @NonNull TextMessage message) {
        Seat seat = seatMap.get(raw.getId());
        if (seat != null) seat.room().handle(seat.seatId(), message.getPayload());
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession raw, @NonNull CloseStatus status) {
        synchronized (lock) {
            if (waiting != null && waiting.getId().equals(raw.getId())) waiting = null;
        }
        Seat seat = seatMap.remove(raw.getId());
        if (seat != null) {
            seat.room().close(seat.seatId());
            seatMap.values().removeIf(other -> other.room() == seat.room());
        }
    }
}
