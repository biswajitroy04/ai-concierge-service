package com.hotel.concierge.service.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    public void notifyDashboard(String event, Object data) {
        messagingTemplate.convertAndSend("/topic/dashboard", Map.of(
                "event", event,
                "data", data,
                "timestamp", System.currentTimeMillis()
        ));
    }

    public void notifyEscalation(Object escalationData) {
        messagingTemplate.convertAndSend("/topic/escalations", escalationData);
        log.info("Escalation notification sent to dashboard");
    }

    public void notifyHousekeeping(Object requestData) {
        messagingTemplate.convertAndSend("/topic/housekeeping", requestData);
    }

    public void notifyChatUpdate(String sessionId, Object messageData) {
        messagingTemplate.convertAndSend("/topic/chat/" + sessionId, messageData);
    }
}
