package com.hotel.concierge.controller;

import com.hotel.concierge.service.whatsapp.WhatsAppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/webhook")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Webhooks", description = "External service webhook endpoints")
public class WebhookController {

    private final WhatsAppService whatsAppService;

    @PostMapping("/whatsapp")
    @Operation(summary = "Twilio WhatsApp webhook endpoint")
    public ResponseEntity<String> handleWhatsAppWebhook(
            @RequestParam("From") String from,
            @RequestParam("Body") String body,
            @RequestParam(value = "ProfileName", required = false) String profileName) {

        log.info("WhatsApp webhook received from: {}", from);

        String response = whatsAppService.handleIncomingMessage(from, body, profileName);

        // Return TwiML response
        String twiml = String.format("""
                <?xml version="1.0" encoding="UTF-8"?>
                <Response>
                    <Message>%s</Message>
                </Response>
                """, escapeXml(response));

        return ResponseEntity.ok()
                .header("Content-Type", "application/xml")
                .body(twiml);
    }

    @GetMapping("/whatsapp/status")
    @Operation(summary = "WhatsApp webhook status check")
    public ResponseEntity<String> whatsappStatus() {
        return ResponseEntity.ok("WhatsApp webhook active");
    }

    private String escapeXml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
