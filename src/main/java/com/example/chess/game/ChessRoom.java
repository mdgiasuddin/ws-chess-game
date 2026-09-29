package com.example.chess.game;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One match. Chess is turn-based, so there is no ticker: every incoming message is handled
 * under this object's lock, validated by the engine, and the result is pushed to both players.
 */
public class ChessRoom {
    private static final Logger log = LoggerFactory.getLogger(ChessRoom.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final ChessEngine engine = new ChessEngine();
    private final WebSocketSession[] sessions = new WebSocketSession[2]; // 0 = WHITE, 1 = BLACK
    private boolean closed;

    public ChessRoom(WebSocketSession white, WebSocketSession black) {
        sessions[0] = white;
        sessions[1] = black;
        send(0, "{\"t\":\"welcome\",\"color\":\"WHITE\"}");
        send(1, "{\"t\":\"welcome\",\"color\":\"BLACK\"}");
        broadcastState();
    }

    public synchronized void handle(int seat, String text) {
        if (closed) return;
        try {
            JsonNode m = objectMapper.readTree(text);
            switch (m.path("t").asString()) {
                case "select" -> {
                    // Selection highlight is cosmetic: relay to the opponent, but only from the side to move.
                    int r = m.path("r").asInt(-2), c = m.path("c").asInt(-2);
                    boolean clear = r == -1 && c == -1;
                    boolean onBoard = r >= 0 && r < 8 && c >= 0 && c < 8;
                    if ((clear || onBoard) && !engine.isOver() && engine.isWhiteTurn() == (seat == 0)) {
                        send(1 - seat, "{\"t\":\"select\",\"r\":" + r + ",\"c\":" + c + "}");
                    }
                }
                case "move" -> {
                    String err = engine.move(seat == 0,
                            m.path("sr").asInt(-1), m.path("sc").asInt(-1),
                            m.path("tr").asInt(-1), m.path("tc").asInt(-1));
                    if (err != null) sendError(seat, err);
                    else broadcastState();
                }
                case "reset" -> {
                    if (engine.isOver()) {
                        engine.reset();
                        broadcastState();
                    }
                }
                default -> {
                }
            }
        } catch (Exception e) {
            log.warn("Bad message from seatId {}: {}", seat, e.toString());
        }
    }

    public synchronized void close(int leavingSeat) {
        closed = true;
        send(1 - leavingSeat, "{\"t\":\"left\"}");
    }

    private void broadcastState() {
        try {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("t", "state");
            s.put("board", engine.rows());
            s.put("turn", engine.isWhiteTurn() ? "WHITE" : "BLACK");
            s.put("status", engine.status().name());
            s.put("winner", engine.winner());
            s.put("last", engine.lastMove());
            String json = objectMapper.writeValueAsString(s);
            send(0, json);
            send(1, json);
        } catch (Exception e) {
            log.error("State serialisation failed", e);
        }
    }

    private void sendError(int seat, String msg) {
        try {
            send(seat, objectMapper.writeValueAsString(Map.of("t", "error", "msg", msg)));
        } catch (Exception e) {
            log.debug("Error message failed: {}", e.toString());
        }
    }

    private void send(int seat, String msg) {
        WebSocketSession session = sessions[seat];
        try {
            if (session != null && session.isOpen()) session.sendMessage(new TextMessage(msg));
        } catch (Exception e) {
            log.debug("Send to seatId {} failed: {}", seat, e.toString());
        }
    }
}
