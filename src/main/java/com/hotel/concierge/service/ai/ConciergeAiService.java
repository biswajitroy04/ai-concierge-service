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
                "• ✈️ Book airport shuttle\n" +
                "• 🗺️ Local recommendations\n\n" +
                "How may I assist you today?",
                guest.getFullName(), checkIn, checkOut, reservation.getRoomNumber()
        );
    }

    private ConciergeAssistant getOrCreateAssistant(String sessionId, Reservation reservation) {
        return assistantCache.computeIfAbsent(sessionId, key -> {
            try {
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
            } catch (Exception e) {
                log.error("Failed to create AI assistant for session {}: {}", sessionId, e.getMessage(), e);
                throw new RuntimeException("AI assistant initialization failed", e);
            }
        });
    }

    private String buildSystemPrompt(Reservation reservation) {
        Guest guest = reservation.getGuest();
        Hotel hotel = reservation.getHotel();

        String template = """
                You are an AI concierge for {{HOTEL_NAME}}, a luxury {{STAR_RATING}}-star hotel.
                
                CURRENT GUEST CONTEXT:
                - Guest: {{GUEST_NAME}} (Loyalty: {{LOYALTY_TIER}}, Total stays: {{TOTAL_STAYS}})
                - Room: {{ROOM_NUMBER}} ({{ROOM_TYPE}}, Floor {{FLOOR_NUMBER}})
                - Stay: {{CHECK_IN_DATE}} to {{CHECK_OUT_DATE}}
                - Special Requests: {{SPECIAL_REQUESTS}}
                - Preferred Language: {{PREFERRED_LANGUAGE}}
                - Dietary Restrictions: {{DIETARY_RESTRICTIONS}}
                
                HOTEL DETAILS:
                - Check-in: {{CHECK_IN_TIME}}, Check-out: {{CHECK_OUT_TIME}}
                - Late checkout fee: ${{LATE_CHECKOUT_FEE}} (max until {{MAX_LATE_HOUR}}:00)
                
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
                
                SCOPE & BOUNDARIES:
                You are ONLY a hotel concierge. You can help with:
                ✓ Hotel services (housekeeping, room issues, amenities, WiFi, parking)
                ✓ Dining (restaurant reservations, menus, menu items, prices, hours, dietary needs)
                ✓ Spa & wellness (bookings, services, pricing, hours, treatments)
                ✓ Local recommendations (restaurants, attractions, transport, shopping)
                ✓ Stay management (late checkout, check-in/out info, billing questions)
                ✓ Special occasions (celebrations, arrangements)
                ✓ Hotel policies (cancellation, pets, smoking, pool hours, etc.)
                ✓ General travel tips relevant to the guest's stay
                
                These are ALL in-scope and you MUST answer them using the hotel knowledge provided in context.
                
                You CANNOT and MUST NOT help with:
                ✗ General knowledge questions unrelated to the hotel (history, science, math, trivia)
                ✗ Personal advice (medical, legal, financial, relationship)
                ✗ Technical help (coding, software, IT support)
                ✗ Political, religious, or controversial topics
                ✗ Content completely unrelated to hospitality or the guest's stay
                
                OFF-TOPIC HANDLING:
                ONLY use this response if the question is COMPLETELY unrelated to the hotel, dining, spa, or guest services:
                "I appreciate your curiosity! However, as your hotel concierge, I'm best equipped to help with your stay experience — dining, spa, local recommendations, room services, and more. Is there anything I can assist you with for your time at {{HOTEL_NAME}}?"
                
                IMPORTANT: Questions about menus, restaurant hours, spa services, hotel amenities, policies, or anything in the RELEVANT HOTEL KNOWLEDGE section are ALWAYS in-scope. Answer them fully using the provided context.
                
                FOOD & MENU QUESTIONS:
                Any question about food, dishes, ingredients, dietary options, menu items, or "do you have X" where X is a food item is ALWAYS a dining question. Search the provided hotel knowledge for relevant menu items and answer helpfully. If the specific item isn't on the menu, say so and suggest alternatives from the menu.
                
                ACCURACY:
                - Use the hotel knowledge base context (provided below your messages) to answer guest questions.
                - If the knowledge base contains the answer, provide it confidently with details.
                - If you genuinely don't have specific information, say so and offer to connect with the relevant department.
                - Never invent information that contradicts the provided context.
                
                AVAILABLE TOOLS:
                - createHousekeepingRequest: For towels, cleaning, amenities, maintenance
                - bookSpaAppointment: ONLY for spa/wellness/massage bookings (NOT restaurants)
                - bookRestaurantReservation: For restaurant/dining reservations at hotel restaurants
                - checkLateCheckoutEligibility: Check eligibility first
                - confirmLateCheckout: After guest agrees, confirm and record the late checkout
                - recommendNearbyAttractions: For local recommendations
                - escalateToHumanAgent: When guest needs human assistance
                - bookAirportShuttle: Book an airport shuttle (pickup location, dropoff location, date, time, passenger count, optional special instructions)
                - cancelAirportShuttle: Cancel an existing airport shuttle booking by booking reference
                
                IMPORTANT: Use bookRestaurantReservation for dining/restaurant requests. Use bookSpaAppointment ONLY for spa treatments and massages.
                """;

        return template
                .replace("{{HOTEL_NAME}}", safe(hotel.getName()))
                .replace("{{STAR_RATING}}", String.valueOf(hotel.getStarRating()))
                .replace("{{GUEST_NAME}}", safe(guest.getFullName()))
                .replace("{{LOYALTY_TIER}}", safe(guest.getLoyaltyTier()))
                .replace("{{TOTAL_STAYS}}", String.valueOf(guest.getTotalStays()))
                .replace("{{ROOM_NUMBER}}", safe(reservation.getRoomNumber()))
                .replace("{{ROOM_TYPE}}", safe(reservation.getRoomType()))
                .replace("{{FLOOR_NUMBER}}", String.valueOf(reservation.getFloorNumber()))
                .replace("{{CHECK_IN_DATE}}", safe(reservation.getCheckInDate()))
                .replace("{{CHECK_OUT_DATE}}", safe(reservation.getCheckOutDate()))
                .replace("{{SPECIAL_REQUESTS}}", reservation.getSpecialRequests() != null ? reservation.getSpecialRequests() : "None")
                .replace("{{PREFERRED_LANGUAGE}}", safe(guest.getPreferredLanguage()))
                .replace("{{DIETARY_RESTRICTIONS}}", guest.getDietaryRestrictions() != null ? guest.getDietaryRestrictions() : "None")
                .replace("{{CHECK_IN_TIME}}", safe(hotel.getCheckInTime()))
                .replace("{{CHECK_OUT_TIME}}", safe(hotel.getCheckOutTime()))
                .replace("{{LATE_CHECKOUT_FEE}}", String.valueOf(hotel.getLateCheckoutFee() != null ? hotel.getLateCheckoutFee().intValue() : 0))
                .replace("{{MAX_LATE_HOUR}}", String.valueOf(hotel.getMaxLateCheckoutHour() != null ? hotel.getMaxLateCheckoutHour() : 14));
    }

    private String safe(Object value) {
        return value != null ? value.toString() : "N/A";
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
        actions.add(ChatResponse.QuickAction.builder().label("Book airport shuttle").action("airport_shuttle").icon("✈️").build());
        actions.add(ChatResponse.QuickAction.builder().label("Attractions").action("attractions").icon("🗺️").build());
        return actions;
    }

    private List<ChatResponse.RecommendationCard> generateRecommendations(String response, Reservation reservation) {
        // Return empty by default, populated when AI suggests recommendations
        return new ArrayList<>();
    }
}
