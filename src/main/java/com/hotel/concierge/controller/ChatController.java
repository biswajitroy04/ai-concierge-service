package com.hotel.concierge.controller;

import com.hotel.concierge.config.RateLimitConfig;
import com.hotel.concierge.dto.ChatRequest;
import com.hotel.concierge.dto.ChatResponse;
import com.hotel.concierge.model.Reservation;
import com.hotel.concierge.repository.ReservationRepository;
import com.hotel.concierge.security.JwtTokenProvider;
import com.hotel.concierge.service.ai.ConciergeAiService;
import com.hotel.concierge.service.qr.QrCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Chat", description = "AI Concierge chat endpoints")
public class ChatController {

    private final ConciergeAiService conciergeAiService;
    private final QrCodeService qrCodeService;
    private final JwtTokenProvider jwtTokenProvider;
    private final ReservationRepository reservationRepository;
    private final RateLimitConfig rateLimitConfig;

    @PostMapping("/message")
    @Operation(summary = "Send a message to the AI concierge")
    public ResponseEntity<ChatResponse> sendMessage(
            @Valid @RequestBody ChatRequest request,
            @RequestHeader("Authorization") String authHeader,
            HttpServletRequest httpRequest) {

        // Rate limiting
        String clientIp = httpRequest.getRemoteAddr();
        if (!rateLimitConfig.resolveBucket(clientIp).tryConsume(1)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }

        String token = authHeader.replace("Bearer ", "");
        Long reservationId = jwtTokenProvider.getReservationIdFromQrToken(token);

        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reservation not found"));

        String sessionId = request.getSessionId() != null ? request.getSessionId() : UUID.randomUUID().toString();

        ChatResponse response = conciergeAiService.processMessage(sessionId, request.getMessage(), reservation);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/guest")
    @Operation(summary = "Initialize guest chat session via QR token")
    public ResponseEntity<ChatResponse> initGuestChat(@RequestParam String token) {
        try {
            Reservation reservation = qrCodeService.validateQrToken(token);
            String sessionId = "web-" + UUID.randomUUID().toString();
            String welcomeMessage = conciergeAiService.generateWelcomeMessage(reservation);

            ChatResponse response = ChatResponse.builder()
                    .sessionId(sessionId)
                    .message(welcomeMessage)
                    .sentiment("POSITIVE")
                    .sentimentScore(0.8)
                    .escalated(false)
                    .build();

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to initialize guest chat: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ChatResponse.builder().message("Invalid or expired token. Please scan a new QR code.").build());
        }
    }

    @GetMapping("/history/{sessionId}")
    @Operation(summary = "Get chat history for a session")
    public ResponseEntity<?> getChatHistory(@PathVariable String sessionId) {
        // Return chat history from database
        return ResponseEntity.ok().build();
    }
}
