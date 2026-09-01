package com.ecovoice.service;

import com.ecovoice.domain.VitalSignRecord;
import com.ecovoice.dto.VitalSignDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@RequiredArgsConstructor
public class VitalSignStreamService {

    private final ObjectMapper objectMapper;
    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    public void register(WebSocketSession session) {
        sessions.add(session);
    }

    public void unregister(WebSocketSession session) {
        sessions.remove(session);
    }

    public void broadcast(VitalSignDto dto) {
        try {
            String payload = objectMapper.writeValueAsString(dto);
            TextMessage message = new TextMessage(payload);
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    session.sendMessage(message);
                }
            }
        } catch (Exception ignored) {
            // best-effort broadcast for training sandbox
        }
    }

    public void broadcastRecord(VitalSignRecord record, String entityName, String nodeCode) {
        broadcast(VitalSignDto.from(record, entityName, nodeCode));
    }
}
