package com.hotel.concierge.service.ai;

import com.hotel.concierge.dto.ChatResponse;
import com.hotel.concierge.model.*;
import com.hotel.concierge.repository.*;
import com.hotel.concierge.service.ai.tools.ConciergeToolsFactory;
import com.hotel.concierge.service.sentiment.SentimentAnalysisService;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConciergeAiService {

    private final ChatLanguageModel chatLanguageModel;
    private final ConciergeToolsFactory conciergeToolsFactory;
    private final SentimentAnalysisService sentimentService;
    private final VectorSearchService vectorSearchService;
    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ReservationRepository reservationRepository;

    private final Map<String, ConciergeAssistant> assistantCache = new HashMap<>();

    public ChatResponse processMessage(String sessionId, String userMessage, Reservation reservation) {
        log.info("Processing message for session: {}, reservation: {}", sessionId, reservation.getConfirmationNumber());

        // Get or create conversation
        Conversation conversation = conversationRepository.findBySessionId(sessionId)
                .orElseGet(() -> createConversation(sessionId, reservation));

        // Analyze sentiment
        var sentimentResult = sentimentService.analyzeSentiment(userMessage);
        double sentimentScore = sentimentResult.getScore();
        String sentimentLabel = sentimentResult.getLabel();

        // Save user message
        saveMessage(conversation, ChatMessage.MessageRole.USER, userMessage, sentimentScore);

        // Get AI assistant for this session
        ConciergeAssistant assistant = getOrCreateAssistant(sessionId, reservation);

        // RAG: Search vector store for relevant context
        String ragContext = vectorSearchService.buildRagContext(userMessage);

        // Build enriched message with RAG context
        String enrichedMessage = userMessage;
        if (!ragContext.isBlank()) {
            enrichedMessage = userMessage + ragContext;
            log.debug("RAG context injected for session {}", sessionId);
        }

        // Generate AI response
        String aiResponse;
        try {
            aiResponse = assistant.chat(enrichedMessage);
        } catch (Exception e) {
            log.error("AI processing error for session {}: {}", sessionId, e.getMessage());
            aiResponse = "I apologize for the inconvenience. Let me connect you with our front desk team who can assist you further.";
        }

        // Save AI response
        saveMessage(conversation, ChatMessage.MessageRole.ASSISTANT, aiResponse, null);

        // Update conversation metrics
        conversation.setMessageCount(conversation.getMessageCount() + 2);
        conversation.setSentimentScore(sentimentScore);
        conversation.setSentimentLabel(sentimentLabel);

        // Check for escalation
        boolean escalated = false;
        if (sentimentScore < -0.6 || sentimentService.requiresEscalation(userMessage)) {
            escalated = handleEscalation(conversation, sentimentScore, userMessage);
        }

        conversationRepository.save(conversation);

        // Build response with quick actions
        List<ChatResponse.QuickAction> quickActions = generateQuickActions(aiResponse, reservation);
        List<ChatResponse.RecommendationCard> recommendations = generateRecommendations(aiResponse, reservation);

        return ChatResponse.builder()
                .sessionId(sessionId)
                .message(aiResponse)
                .sentiment(sentimentLabel)
                .sentimentScore(sentimentScore)
                .quickActions(quickActions)
                .recommendations(recommendations)
                .escalated(escalated)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public String generateWelcomeMessage(Reservation reservation) {
        Guest guest = reservation.getGuest();
        String checkIn = reservation.getCheckInDate().format(DateTimeFormatter.ofPattern("MMM dd"));
        String checkOut = reservation.getCheckOutDate().format(DateTimeFormatter.ofPattern("MMM dd"));

        return String.format(
                "Welcome %s! 🌟\n\nYour stay is from %s to %s in Room %s.\n\n" +
                "I'm your AI concierge, here to make your stay exceptional. " +
                "I can help you with:\n" +
                "• 🛎️ Housekeeping requests\n" +
                "• 💆 Spa bookings\n" +
                "• 🍽️ Restaurant reservations\n" +
                "• 🕐 Late checkout requests\n" +
                "• 🗺️ Local recommendations\n\n" +
                "How may I assist you today?",
                guest.getFullName(), checkIn, checkOut, reservation.getRoomNumber()
        );
    }

    private ConciergeAssistant getOrCreateAssistant(String sessionId, Reservation reservation) {
        return assistantCache.computeIfAbsent(sessionId, key -> {
            String systemPrompt = buildSystemPrompt(reservation);

            MessageWindowChatMemory memory = MessageWindowChatMemory.withMaxMessages(20);
            memory.add(dev.langchain4j.data.message.SystemMessage.from(systemPrompt));

            // Create per-session tools bound to this reservation
            var tools = conciergeToolsFactory.createForSession(reservation.getId(), sessionId);

            return AiServices.builder(ConciergeAssistant.class)
                    .chatLanguageModel(chatLanguageModel)
                    .chatMemory(memory)
                    .tools(tools)
                    .build();
        });
    }

    private String buildSystemPrompt(Reservation reservation) {
        Guest guest = reservation.getGuest();
        Hotel hotel = reservation.getHotel();

        return String.format("""
                You are an AI concierge for %s, a luxury %d-star hotel.
                
                CURRENT GUEST CONTEXT:
                - Guest: %s (Loyalty: %s, Total stays: %d)
                - Room: %s (%s, Floor %d)
                - Stay: %s to %s
                - Special Requests: %s
                - Preferred Language: %s
                - Dietary Restrictions: %s
                
                HOTEL DETAILS:
                - Check-in: %s, Check-out: %s
                - Late checkout fee: $%.0f (max until %d:00)
                
                INSTRUCTIONS:
                1. Be warm, professional, and personalized. Use the guest's name naturally.
                2. Proactively offer relevant services based on context (time of day, stay duration, etc.)
                3. For housekeeping, spa, restaurant bookings - use the available tools.
                4. For late checkout - check eligibility first before confirming.
                5. Provide nearby attraction recommendations when asked.
                6. If the guest seems frustrated or angry, acknowledge their feelings and offer to escalate.
                7. Suggest upsells naturally (spa treatments, room upgrades, dining experiences).
                8. Always respond in the guest's preferred language if not English.
                9. Keep responses concise but helpful. Use emojis sparingly for warmth.
                10. Never reveal you are an AI unless directly asked. Present as "your concierge."
                
                AVAILABLE TOOLS:
                - createHousekeepingRequest: For towels, cleaning, amenities, maintenance
                - bookSpaAppointment: ONLY for spa/wellness/massage bookings (NOT restaurants)
                - bookRestaurantReservation: For restaurant/dining reservations at hotel restaurants
                - checkLateCheckoutEligibility: Check eligibility first
                - confirmLateCheckout: After guest agrees, confirm and record the late checkout
                - recommendNearbyAttractions: For local recommendations
                - escalateToHumanAgent: When guest needs human assistance
                
                IMPORTANT: Use bookRestaurantReservation for dining/restaurant requests. Use bookSpaAppointment ONLY for spa treatments and massages.
                """,
                hotel.getName(), hotel.getStarRating(),
                guest.getFullName(), guest.getLoyaltyTier(), guest.getTotalStays(),
                reservation.getRoomNumber(), reservation.getRoomType(), reservation.getFloorNumber(),
                reservation.getCheckInDate(), reservation.getCheckOutDate(),
                reservation.getSpecialRequests() != null ? reservation.getSpecialRequests() : "None",
                guest.getPreferredLanguage(),
                guest.getDietaryRestrictions() != null ? guest.getDietaryRestrictions() : "None",
                hotel.getCheckInTime(), hotel.getCheckOutTime(),
                hotel.getLateCheckoutFee(), hotel.getMaxLateCheckoutHour()
        );
    }

    private Conversation createConversation(String sessionId, Reservation reservation) {
        Conversation conversation = Conversation.builder()
                .sessionId(sessionId)
                .reservation(reservation)
                .channel("WEB")
                .status(Conversation.ConversationStatus.ACTIVE)
                .language(reservation.getGuest().getPreferredLanguage())
                .build();
        return conversationRepository.save(conversation);
    }

    private void saveMessage(Conversation conversation, ChatMessage.MessageRole role, String content, Double sentiment) {
        ChatMessage message = ChatMessage.builder()
                .conversation(conversation)
                .role(role)
                .content(content)
                .sentimentScore(sentiment)
                .build();
        chatMessageRepository.save(message);
    }

    private boolean handleEscalation(Conversation conversation, double sentimentScore, String reason) {
        conversation.setIsEscalated(true);
        conversation.setStatus(Conversation.ConversationStatus.ESCALATED);
        conversation.setEscalationReason("Negative sentiment detected: " + reason);
        log.warn("Conversation {} escalated due to sentiment score: {}", conversation.getSessionId(), sentimentScore);
        return true;
    }

    private List<ChatResponse.QuickAction> generateQuickActions(String response, Reservation reservation) {
        List<ChatResponse.QuickAction> actions = new ArrayList<>();
        actions.add(ChatResponse.QuickAction.builder().label("Housekeeping").action("housekeeping").icon("🛎️").build());
        actions.add(ChatResponse.QuickAction.builder().label("Spa").action("spa").icon("💆").build());
        actions.add(ChatResponse.QuickAction.builder().label("Restaurant").action("restaurant").icon("🍽️").build());
        actions.add(ChatResponse.QuickAction.builder().label("Late Checkout").action("late_checkout").icon("🕐").build());
        actions.add(ChatResponse.QuickAction.builder().label("Attractions").action("attractions").icon("🗺️").build());
        return actions;
    }

    private List<ChatResponse.RecommendationCard> generateRecommendations(String response, Reservation reservation) {
        // Return empty by default, populated when AI suggests recommendations
        return new ArrayList<>();
    }
}
