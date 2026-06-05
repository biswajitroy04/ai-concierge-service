package com.hotel.concierge.service.whatsapp;

import com.hotel.concierge.dto.ChatResponse;
import com.hotel.concierge.model.Reservation;
import com.hotel.concierge.repository.GuestRepository;
import com.hotel.concierge.repository.ReservationRepository;
import com.hotel.concierge.service.ai.ConciergeAiService;
import com.hotel.concierge.service.qr.QrCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppService {

    private final ConciergeAiService conciergeAiService;
    private final QrCodeService qrCodeService;
    private final ReservationRepository reservationRepository;
    private final GuestRepository guestRepository;

    @Value("${twilio.account-sid:}")
    private String accountSid;

    @Value("${twilio.auth-token:}")
    private String authToken;

    @Value("${twilio.whatsapp-number:}")
    private String whatsappNumber;

    // Map WhatsApp phone numbers to session IDs
    private final Map<String, String> phoneToSession = new ConcurrentHashMap<>();
    private final Map<String, Long> phoneToReservationId = new ConcurrentHashMap<>();
    private final Map<String, String> phoneToToken = new ConcurrentHashMap<>();
    private final Map<String, String> tokenToSession = new ConcurrentHashMap<>();
    private final Map<String, WhatsAppHandoff> handoffs = new ConcurrentHashMap<>();
    private final Map<String, List<ChatResponse.QuickAction>> phoneToQuickActions = new ConcurrentHashMap<>();

    private static final Map<String, String> QUICK_ACTION_MESSAGES = Map.of(
            "housekeeping", "I'd like to request housekeeping service for my room.",
            "spa", "I'm interested in booking a spa treatment. What's available?",
            "restaurant", "I'd like to make a restaurant reservation for tonight.",
            "late_checkout", "Is it possible to get a late checkout?",
            "attractions", "What are some good attractions or restaurants nearby?"
    );

    @Transactional
    public String handleIncomingMessage(String from, String body, String profileName) {
        String normalizedFrom = normalizeWhatsAppAddress(from);
        log.info("WhatsApp message from {} normalized as {}: {}", from, normalizedFrom, body);

        String inboundText = body == null ? "" : body.trim();

        if (inboundText.toUpperCase(Locale.ROOT).startsWith("START ")) {
            return handleHandoffMessage(normalizedFrom, inboundText.substring(6).trim());
        }

        if (inboundText.toUpperCase(Locale.ROOT).startsWith("TOKEN:")) {
            return handleTokenMessage(normalizedFrom, inboundText.substring(6).trim());
        }

        RestoredSession restoredSession = restoreSession(normalizedFrom);
        if (restoredSession == null) {
            log.warn("No active reservation guest phone match for {}", normalizedFrom);
            return "Welcome to our hotel concierge! Please scan your room QR code to get started, " +
                    "or ask the front desk to connect your WhatsApp number.";
        }

        String sessionId = restoredSession.sessionId();
        Reservation reservation = restoredSession.reservation();
        if (isGreetingOrEmpty(inboundText)) {
            return welcomeResponse(sessionId, reservation);
        }

        String messageForBackend = resolveQuickActionMessage(normalizedFrom, inboundText);

        // Process through AI
        ChatResponse response = conciergeAiService.processMessage(sessionId, messageForBackend, reservation);
        rememberQuickActions(normalizedFrom, response.getQuickActions());
        return formatForWhatsApp(response);
    }

    public WhatsAppLink createHandoff(String token, String requestedSessionId) {
        Reservation reservation = qrCodeService.validateQrToken(token);
        String sessionId = requestedSessionId == null || requestedSessionId.isBlank()
                ? tokenToSession.computeIfAbsent(token, ignored -> "whatsapp-" + UUID.randomUUID())
                : requestedSessionId;
        tokenToSession.put(token, sessionId);
        String handoffCode = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);

        handoffs.put(handoffCode, new WhatsAppHandoff(token, sessionId, reservation.getId()));
        log.info("Created WhatsApp handoff {} for session {}, reservation {}", handoffCode, sessionId, reservation.getConfirmationNumber());

        String phone = whatsappNumber.replaceAll("[^0-9]", "");
        String message = "START " + handoffCode;
        String link = "https://api.whatsapp.com/send?phone=" + phone + "&text=" + java.net.URLEncoder.encode(message, java.nio.charset.StandardCharsets.UTF_8);
        return new WhatsAppLink(link, whatsappNumber, sessionId);
    }

    @Transactional
    public WhatsAppSession startSession(String token, String requestedSessionId, String guestWhatsAppNumber) {
        Reservation reservation = qrCodeService.validateQrToken(token);
        String guestAddress = normalizeWhatsAppAddress(guestWhatsAppNumber);
        updateGuestPhone(reservation, guestAddress);
        String sessionId = buildWhatsAppSessionId(guestAddress, reservation);

        rememberSession(guestAddress, sessionId, token, reservation.getId());
        String welcomeMessage = welcomeResponse(sessionId, reservation);
        boolean welcomeSent = sendMessage(guestAddress, welcomeMessage);

        String phone = whatsappNumber.replaceAll("[^0-9]", "");
        String link = "https://api.whatsapp.com/send?phone=" + phone;

        log.info("Started WhatsApp session {} for {}, reservation {}, welcomeSent={}",
                sessionId, guestAddress, reservation.getConfirmationNumber(), welcomeSent);

        return new WhatsAppSession(link, whatsappNumber, sessionId, welcomeSent);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSessionStatus(String guestWhatsAppNumber) {
        String guestAddress = normalizeWhatsAppAddress(guestWhatsAppNumber);
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("whatsappFrom", guestAddress);
        status.put("phoneDigits", digitsOnly(guestAddress));

        findActiveReservationByPhone(guestAddress)
                .ifPresentOrElse(reservation -> {
                    status.put("connected", true);
                    status.put("sessionId", buildWhatsAppSessionId(guestAddress, reservation));
                    status.put("reservationId", reservation.getId());
                    status.put("confirmationNumber", reservation.getConfirmationNumber());
                    status.put("reservationStatus", reservation.getStatus().name());
                    status.put("guestName", reservation.getGuest().getFullName());
                    status.put("guestPhone", reservation.getGuest().getPhone());
                }, () -> {
                    status.put("connected", false);
                    status.put("activeReservations", findActiveReservations().size());
                });

        return status;
    }

    private String handleHandoffMessage(String from, String handoffCode) {
        WhatsAppHandoff handoff = handoffs.remove(handoffCode.trim().toUpperCase(Locale.ROOT));
        if (handoff == null) {
            return "Sorry, that WhatsApp session link has expired or is invalid. Please return to the concierge app and tap Continue in WhatsApp again.";
        }

        Reservation reservation = loadReservation(handoff.reservationId());
        updateGuestPhone(reservation, from);
        String sessionId = buildWhatsAppSessionId(from, reservation);
        rememberSession(from, sessionId, handoff.token(), reservation.getId());
        return welcomeResponse(sessionId, reservation);
    }

    private String handleTokenMessage(String from, String token) {
        try {
            Reservation reservation = qrCodeService.validateQrToken(token);
            updateGuestPhone(reservation, from);
            String sessionId = buildWhatsAppSessionId(from, reservation);

            rememberSession(from, sessionId, token, reservation.getId());
            return welcomeResponse(sessionId, reservation);
        } catch (Exception e) {
            log.error("Invalid token from WhatsApp user {}: {}", from, e.getMessage());
            return "Sorry, that token appears to be invalid or expired. Please scan a new QR code or contact the front desk.";
        }
    }

    private void rememberSession(String from, String sessionId, String token, Long reservationId) {
        phoneToSession.put(from, sessionId);
        if (token != null && !token.isBlank()) {
            phoneToToken.put(from, token);
            tokenToSession.put(token, sessionId);
        }
        phoneToReservationId.put(from, reservationId);
        rememberQuickActions(from, conciergeAiService.defaultQuickActions());
    }

    private String welcomeResponse(String sessionId, Reservation reservation) {
        return formatForWhatsApp(ChatResponse.builder()
                .sessionId(sessionId)
                .message(conciergeAiService.generateWelcomeMessage(reservation))
                .quickActions(conciergeAiService.defaultQuickActions())
                .build());
    }

    private Reservation loadReservation(Long reservationId) {
        return reservationRepository.findWithHotelAndGuestById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reservation not found: " + reservationId));
    }

    private RestoredSession restoreSession(String from) {
        String phoneDigits = digitsOnly(from);
        if (phoneDigits.isBlank()) {
            return null;
        }

        log.debug("Looking up active reservation by guest phone for {} digits={}", from, phoneDigits);
        return findActiveReservationByPhone(from)
                .map(reservation -> {
                    String sessionId = buildWhatsAppSessionId(from, reservation);
                    rememberSession(from, sessionId, null, reservation.getId());
                    log.info("Restored WhatsApp session {} from guest phone lookup for {}, reservation {}",
                            sessionId, from, reservation.getConfirmationNumber());
                    return new RestoredSession(sessionId, reservation);
                })
                .orElseGet(() -> {
                    log.warn("Guest phone lookup miss for {} digits={}. Active reservations={} {}",
                            from,
                            phoneDigits,
                            findActiveReservations().size(),
                            describeActiveReservationPhones());
                    return null;
                });
    }

    private boolean isReservationChatActive(Reservation reservation) {
        return reservation.getStatus() == Reservation.ReservationStatus.CONFIRMED
                || reservation.getStatus() == Reservation.ReservationStatus.CHECKED_IN;
    }

    private List<Reservation> findActiveReservations() {
        List<Reservation> activeReservations = new java.util.ArrayList<>();
        activeReservations.addAll(reservationRepository.findByStatus(Reservation.ReservationStatus.CHECKED_IN));
        activeReservations.addAll(reservationRepository.findByStatus(Reservation.ReservationStatus.CONFIRMED));
        return activeReservations;
    }

    private java.util.Optional<Reservation> findActiveReservationByPhone(String from) {
        String phoneDigits = digitsOnly(from);
        if (phoneDigits.isBlank()) {
            return java.util.Optional.empty();
        }

        return findActiveReservations().stream()
                .filter(reservation -> phoneMatches(phoneDigits, digitsOnly(reservation.getGuest().getPhone())))
                .findFirst();
    }

    private void updateGuestPhone(Reservation reservation, String whatsappAddress) {
        String updatedPhone = toPhoneNumber(whatsappAddress);
        if (phoneMatches(digitsOnly(updatedPhone), digitsOnly(reservation.getGuest().getPhone()))) {
            return;
        }

        reservation.getGuest().setPhone(updatedPhone);
        guestRepository.save(reservation.getGuest());
        log.info("Updated guest {} phone to {} from WhatsApp link for reservation {}",
                reservation.getGuest().getId(), updatedPhone, reservation.getConfirmationNumber());
    }

    private String resolveQuickActionMessage(String from, String inboundText) {
        if (inboundText == null || inboundText.isBlank()) {
            return inboundText;
        }

        List<ChatResponse.QuickAction> quickActions = phoneToQuickActions.getOrDefault(from, conciergeAiService.defaultQuickActions());
        String normalized = inboundText.trim().toLowerCase(Locale.ROOT);

        if (normalized.matches("\\d+")) {
            int index = Integer.parseInt(normalized) - 1;
            if (index >= 0 && index < quickActions.size()) {
                return QUICK_ACTION_MESSAGES.getOrDefault(quickActions.get(index).getAction(), inboundText);
            }
        }

        return quickActions.stream()
                .filter(action -> normalized.equals(action.getAction().toLowerCase(Locale.ROOT))
                        || normalized.equals(action.getLabel().toLowerCase(Locale.ROOT)))
                .findFirst()
                .map(action -> QUICK_ACTION_MESSAGES.getOrDefault(action.getAction(), inboundText))
                .orElse(inboundText);
    }

    private String formatForWhatsApp(ChatResponse response) {
        StringBuilder message = new StringBuilder(response.getMessage() == null ? "" : response.getMessage());

        if (response.getRecommendations() != null && !response.getRecommendations().isEmpty()) {
            message.append("\n\nRecommendations:");
            for (ChatResponse.RecommendationCard card : response.getRecommendations()) {
                message.append("\n- ").append(card.getTitle());
                if (card.getDescription() != null && !card.getDescription().isBlank()) {
                    message.append(": ").append(card.getDescription());
                }
                if (card.getPrice() != null) {
                    message.append(" (")
                            .append(card.getCurrency() == null ? "$" : card.getCurrency())
                            .append(card.getPrice())
                            .append(")");
                }
            }
        }

        if (response.getQuickActions() != null && !response.getQuickActions().isEmpty()) {
            message.append("\n\nQuick options:");
            int index = 1;
            for (ChatResponse.QuickAction action : response.getQuickActions()) {
                message.append("\n").append(index++).append(". ").append(action.getLabel());
            }
            message.append("\n\nReply with a number or option name.");
        }

        return message.toString();
    }

    private void rememberQuickActions(String from, List<ChatResponse.QuickAction> quickActions) {
        if (quickActions != null && !quickActions.isEmpty()) {
            phoneToQuickActions.put(from, quickActions);
        }
    }

    public boolean sendMessage(String to, String message) {
        if (accountSid == null || accountSid.isBlank()) {
            log.warn("Twilio not configured, skipping WhatsApp message to {}", to);
            return false;
        }

        try {
            // Twilio WhatsApp API integration
            com.twilio.Twilio.init(accountSid, authToken);
            com.twilio.rest.api.v2010.account.Message.creator(
                    new com.twilio.type.PhoneNumber(normalizeWhatsAppAddress(to)),
                    new com.twilio.type.PhoneNumber("whatsapp:" + whatsappNumber),
                    message
            ).create();
            log.info("WhatsApp message sent to {}", to);
            return true;
        } catch (Exception e) {
            log.error("Failed to send WhatsApp message to {}: {}", to, e.getMessage());
            return false;
        }
    }

    private String normalizeWhatsAppAddress(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new IllegalArgumentException("WhatsApp number is required");
        }

        String trimmed = phoneNumber.trim();
        if (trimmed.startsWith("whatsapp:+")) {
            return trimmed;
        }
        if (trimmed.startsWith("whatsapp:")) {
            return "whatsapp:+" + trimmed.substring("whatsapp:".length()).replaceAll("[^0-9]", "");
        }
        if (trimmed.startsWith("+")) {
            return "whatsapp:" + trimmed;
        }

        String digits = trimmed.replaceAll("[^0-9]", "");
        if (digits.isBlank()) {
            throw new IllegalArgumentException("WhatsApp number is invalid");
        }
        return "whatsapp:+" + digits;
    }

    private String digitsOnly(String value) {
        return value == null ? "" : value.replaceAll("[^0-9]", "");
    }

    private String toPhoneNumber(String whatsappAddress) {
        String digits = digitsOnly(whatsappAddress);
        if (digits.isBlank()) {
            throw new IllegalArgumentException("WhatsApp number is invalid");
        }
        return "+" + digits;
    }

    private boolean phoneMatches(String leftDigits, String rightDigits) {
        if (leftDigits == null || rightDigits == null || leftDigits.isBlank() || rightDigits.isBlank()) {
            return false;
        }
        if (leftDigits.equals(rightDigits)) {
            return true;
        }
        return leftDigits.length() >= 10
                && rightDigits.length() >= 10
                && lastDigits(leftDigits, 10).equals(lastDigits(rightDigits, 10));
    }

    private String describeActiveReservationPhones() {
        return findActiveReservations().stream()
                .limit(10)
                .map(reservation -> reservation.getConfirmationNumber()
                        + ":" + maskPhoneDigits(digitsOnly(reservation.getGuest().getPhone())))
                .toList()
                .toString();
    }

    private String maskPhoneDigits(String digits) {
        if (digits == null || digits.isBlank()) {
            return "blank";
        }
        return "***" + lastDigits(digits, Math.min(4, digits.length()));
    }

    private String lastDigits(String value, int length) {
        return value.substring(Math.max(0, value.length() - length));
    }

    private String buildWhatsAppSessionId(String from, Reservation reservation) {
        return "whatsapp-" + reservation.getId() + "-" + digitsOnly(from);
    }

    private boolean isGreetingOrEmpty(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }

        String normalized = text.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("hi")
                || normalized.equals("hello")
                || normalized.equals("hey")
                || normalized.equals("start")
                || normalized.equals("menu");
    }

    public record WhatsAppLink(String whatsappLink, String whatsappNumber, String sessionId) {
    }

    public record WhatsAppSession(String whatsappLink, String whatsappNumber, String sessionId, boolean welcomeSent) {
    }

    private record WhatsAppHandoff(String token, String sessionId, Long reservationId) {
    }

    private record RestoredSession(String sessionId, Reservation reservation) {
    }
}
