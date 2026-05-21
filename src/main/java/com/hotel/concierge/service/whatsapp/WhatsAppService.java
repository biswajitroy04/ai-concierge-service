package com.hotel.concierge.service.whatsapp;

import com.hotel.concierge.dto.ChatResponse;
import com.hotel.concierge.model.Reservation;
import com.hotel.concierge.service.ai.ConciergeAiService;
import com.hotel.concierge.service.qr.QrCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppService {

    private final ConciergeAiService conciergeAiService;
    private final QrCodeService qrCodeService;

    @Value("${twilio.account-sid:}")
    private String accountSid;

    @Value("${twilio.auth-token:}")
    private String authToken;

    @Value("${twilio.whatsapp-number:}")
    private String whatsappNumber;

    // Map WhatsApp phone numbers to session IDs
    private final Map<String, String> phoneToSession = new ConcurrentHashMap<>();
    private final Map<String, Reservation> phoneToReservation = new ConcurrentHashMap<>();

    public String handleIncomingMessage(String from, String body, String profileName) {
        log.info("WhatsApp message from {}: {}", from, body);

        // Check if this is a new session with a token
        if (body.startsWith("TOKEN:")) {
            return handleTokenMessage(from, body.substring(6).trim());
        }

        // Check if we have an active session
        String sessionId = phoneToSession.get(from);
        Reservation reservation = phoneToReservation.get(from);

        if (sessionId == null || reservation == null) {
            return "Welcome to our hotel concierge! Please scan your room QR code to get started, " +
                    "or send your confirmation number to connect.";
        }

        // Process through AI
        ChatResponse response = conciergeAiService.processMessage(sessionId, body, reservation);
        return response.getMessage();
    }

    private String handleTokenMessage(String from, String token) {
        try {
            Reservation reservation = qrCodeService.validateQrToken(token);
            String sessionId = "whatsapp-" + from + "-" + System.currentTimeMillis();

            phoneToSession.put(from, sessionId);
            phoneToReservation.put(from, reservation);

            return conciergeAiService.generateWelcomeMessage(reservation);
        } catch (Exception e) {
            log.error("Invalid token from WhatsApp user {}: {}", from, e.getMessage());
            return "Sorry, that token appears to be invalid or expired. Please scan a new QR code or contact the front desk.";
        }
    }

    public void sendMessage(String to, String message) {
        if (accountSid == null || accountSid.isBlank()) {
            log.warn("Twilio not configured, skipping WhatsApp message to {}", to);
            return;
        }

        try {
            // Twilio WhatsApp API integration
            com.twilio.Twilio.init(accountSid, authToken);
            com.twilio.rest.api.v2010.account.Message.creator(
                    new com.twilio.type.PhoneNumber("whatsapp:" + to),
                    new com.twilio.type.PhoneNumber("whatsapp:" + whatsappNumber),
                    message
            ).create();
            log.info("WhatsApp message sent to {}", to);
        } catch (Exception e) {
            log.error("Failed to send WhatsApp message to {}: {}", to, e.getMessage());
        }
    }
}
