package com.hotel.concierge.controller;

import com.hotel.concierge.service.whatsapp.WhatsAppService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/whatsapp")
@RequiredArgsConstructor
@Slf4j
public class WhatsAppController {

    private final WhatsAppService whatsAppService;

    @Value("${twilio.whatsapp-number:}")
    private String whatsappNumber;

    @GetMapping("/link")
    @Operation(summary = "Generate a WhatsApp deep link for the current QR token")
    public ResponseEntity<Map<String, String>> getWhatsAppLink(
            @RequestParam String token,
            @RequestParam(required = false) String sessionId) {
        if (whatsappNumber == null || whatsappNumber.isBlank()) {
            log.warn("WhatsApp number is not configured");
            return ResponseEntity.badRequest().body(Map.of("error", "WhatsApp number is not configured"));
        }

        try {
            WhatsAppService.WhatsAppLink link = whatsAppService.createHandoff(token, sessionId);

            return ResponseEntity.ok(Map.of(
                    "whatsappLink", link.whatsappLink(),
                    "whatsappNumber", link.whatsappNumber(),
                    "sessionId", link.sessionId()
            ));
        } catch (Exception e) {
            log.error("Failed to create WhatsApp handoff: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid or expired token"));
        }
    }

    @PostMapping("/session")
    @Operation(summary = "Register guest WhatsApp number and start the WhatsApp session")
    public ResponseEntity<Map<String, Object>> startWhatsAppSession(@RequestBody Map<String, String> request) {
        if (whatsappNumber == null || whatsappNumber.isBlank()) {
            log.warn("WhatsApp number is not configured");
            return ResponseEntity.badRequest().body(Map.of("error", "WhatsApp number is not configured"));
        }

        try {
            WhatsAppService.WhatsAppSession session = whatsAppService.startSession(
                    request.get("token"),
                    request.get("sessionId"),
                    request.get("whatsappNumber")
            );

            return ResponseEntity.ok(Map.of(
                    "whatsappLink", session.whatsappLink(),
                    "whatsappNumber", session.whatsappNumber(),
                    "sessionId", session.sessionId(),
                    "welcomeSent", session.welcomeSent()
            ));
        } catch (Exception e) {
            log.error("Failed to start WhatsApp session: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/session/status")
    @Operation(summary = "Check the database-backed WhatsApp session for a guest number")
    public ResponseEntity<Map<String, Object>> getWhatsAppSessionStatus(@RequestParam String whatsappNumber) {
        try {
            return ResponseEntity.ok(whatsAppService.getSessionStatus(whatsappNumber));
        } catch (Exception e) {
            log.error("Failed to get WhatsApp session status: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
