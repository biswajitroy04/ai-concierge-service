# Business Logic Documentation

This document explains the end-to-end flow of each use case in the AI Concierge Service, tracing from controller through service layer, including how LangChain4J and AI tools are invoked.


---

## Table of Contents

1. [Guest Onboarding (QR Code Flow)](#1-guest-onboarding-qr-code-flow)
2. [Guest Chat (AI Conversation)](#2-guest-chat-ai-conversation)
3. [AI Tools — When & How They Fire](#3-ai-tools--when--how-they-fire)
4. [Sentiment Analysis & Auto-Escalation](#4-sentiment-analysis--auto-escalation)
5. [WhatsApp Channel (Twilio)](#5-whatsapp-channel-twilio)
6. [RAG Document Management](#6-rag-document-management)
7. [Admin Dashboard](#7-admin-dashboard)
8. [Authentication & Security](#8-authentication--security)

---

## 1. Guest Onboarding (QR Code Flow)

### Use Case
Front desk generates a QR code for a guest's reservation. Guest scans it to start chatting with the AI concierge.

### Flow

```
[Frontend] POST /qr/generate/{reservationId}
    → QrCodeController.generateQrCode()
        → QrCodeService.generateQrCode(reservationId)
            1. Loads Reservation from DB
            2. Calls JwtTokenProvider.generateQrToken(reservationId, guestId, hotelId)
               - Creates JWT with claims: reservationId, guestId, hotelId, type="QR_ACCESS"
               - Expiry: 7 days
            3. Builds chat URL: "{baseUrl}/chat/guest?token={jwt}"
            4. Generates QR image using ZXing (300x300 PNG)
            5. Returns base64 image + token + URL + guest info
```

### Token Validation (when guest scans QR)

```
[Frontend] GET /chat/guest?token={jwt}
    → ChatController.initGuestChat(token)
        → QrCodeService.validateQrToken(token)
            1. JwtTokenProvider.validateToken(token) — checks signature + expiry
            2. JwtTokenProvider.isQrToken(token) — confirms type="QR_ACCESS"
            3. Extracts reservationId, loads Reservation from DB
        → ConciergeAiService.generateWelcomeMessage(reservation)
            - Builds personalized welcome with guest name, dates, room number
        → Returns ChatResponse with sessionId + welcome message
```

---

## 2. Guest Chat (AI Conversation)

### Use Case
Guest sends a message, AI processes it with RAG context and tools, returns a response.

### Flow

```
[Frontend] POST /chat/message
    Headers: Authorization: Bearer {qr-token}
    Body: { "message": "...", "sessionId": "..." }

    → ChatController.sendMessage()
        1. Rate limiting check (Bucket4j, 60 req/min per IP)
        2. Extract reservationId from JWT token
        3. Load Reservation from DB
        → ConciergeAiService.processMessage(sessionId, message, reservation)
```

### Inside ConciergeAiService.processMessage()

```
Step 1: Get or Create Conversation
    - conversationRepo.findBySessionId(sessionId)
    - If not found → create new Conversation entity (status=ACTIVE)

Step 2: Sentiment Analysis
    - sentimentService.analyzeSentiment(userMessage)
    - Calls OpenAI GPT-4o-mini with sentiment prompt
    - Parses JSON response → score (-1.0 to 1.0) + label

Step 3: Save User Message
    - ChatMessage entity saved (role=USER, content, sentimentScore)

Step 4: Get or Create AI Assistant (per session)
    - assistantCache.computeIfAbsent(sessionId, ...)
    - Builds LangChain4J AiServices instance with:
        a. ChatLanguageModel (GPT-4o-mini)
        b. MessageWindowChatMemory (20 messages)
        c. System prompt (dynamic, includes guest context)
        d. ConciergeTools instance (bound to this reservation)

Step 5: RAG Context Retrieval
    - vectorSearchService.buildRagContext(userMessage)
        1. Embed user query via text-embedding-3-small
        2. Weaviate nearVector search (top-5 chunks)
        3. Concatenate chunks as context string
    - Append RAG context to user message

Step 6: AI Generation
    - assistant.chat(enrichedMessage)
    - LangChain4J handles:
        a. Sends system prompt + memory + enriched message to GPT-4o-mini
        b. If model returns tool_call → executes matching @Tool method
        c. Sends tool result back to model for final response
        d. Returns final text response

Step 7: Save AI Response
    - ChatMessage entity saved (role=ASSISTANT)

Step 8: Update Conversation Metrics
    - Set sentimentScore, sentimentLabel, increment messageCount

Step 9: Check Escalation
    - If sentimentScore < -0.6 OR escalation keywords detected
    - Mark conversation as ESCALATED

Step 10: Return ChatResponse
    - sessionId, message, sentiment, quickActions, escalated flag
```

---

## 3. AI Tools — When & How They Fire

### Architecture

Tools are defined in `ConciergeTools.java` with `@Tool` annotations. A new instance is created per session via `ConciergeToolsFactory`, bound to a specific `reservationId` and `sessionId`.

**LangChain4J decides when to call tools** — the developer doesn't explicitly invoke them. The LLM sees the tool descriptions in its context and decides to call them based on the user's request.

### Tool: createHousekeepingRequest

**Triggered when:** Guest asks for towels, cleaning, amenities, minibar, maintenance.

```
Guest: "Can I get extra towels please?"
    → LLM decides to call createHousekeepingRequest("towels", "Extra towels requested", "NORMAL")
        1. Loads Reservation from DB
        2. Creates HousekeepingRequest entity (status=PENDING)
        3. Estimates completion time based on type (towels=15min, cleaning=30min)
        4. Saves to DB
        5. Returns confirmation string with request ID + estimated time
    → LLM incorporates tool result into final response to guest
```

### Tool: bookSpaAppointment

**Triggered when:** Guest wants to book a spa treatment or massage.

```
Guest: "I'd like a Swedish massage tomorrow at 3pm"
    → LLM calls bookSpaAppointment("Swedish Massage", "2026-05-22", "15:00")
        1. Loads Reservation
        2. Parses date/time (with fallbacks for ambiguous formats)
        3. Looks up duration (60min) and price ($150) from internal mapping
        4. Creates SpaBooking entity (status=CONFIRMED)
        5. Returns confirmation with service, date, time, duration, price, booking ID
```

### Tool: bookRestaurantReservation

**Triggered when:** Guest wants to reserve a table at a hotel restaurant.

```
Guest: "Book a table for 2 at Sky Lounge tonight at 8pm"
    → LLM calls bookRestaurantReservation("Sky Lounge", "2026-05-21", "20:00", 2, "")
        1. Loads Reservation
        2. Parses date/time
        3. Creates RestaurantBooking entity (status=CONFIRMED)
        4. Returns confirmation with restaurant, date, time, party size, booking ID
```

### Tool: checkLateCheckoutEligibility

**Triggered when:** Guest asks about late checkout.

```
Guest: "Can I get a late checkout?"
    → LLM calls checkLateCheckoutEligibility()
        1. Loads Reservation + Guest + Hotel
        2. Checks if already approved
        3. Checks loyalty tier (PLATINUM/GOLD → free until 2PM)
        4. Returns pricing options:
           - VIP/Platinum/Gold: complimentary until 2PM, $150 until 4PM
           - Others: $75 until 2PM, $150 until 4PM
    → LLM presents options to guest, waits for decision
```

### Tool: confirmLateCheckout

**Triggered when:** Guest agrees to a late checkout option (called AFTER checkLateCheckoutEligibility).

```
Guest: "Yes, I'll take the 2pm checkout"
    → LLM calls confirmLateCheckout("14:00", 0)  // 0 = complimentary for VIP
        1. Loads Reservation
        2. Sets lateCheckoutApproved=true, lateCheckoutTime="14:00"
        3. Saves to DB
        4. Returns confirmation with new checkout time + fee info
```

### Tool: recommendNearbyAttractions

**Triggered when:** Guest asks for local recommendations.

```
Guest: "What restaurants are nearby?"
    → LLM calls recommendNearbyAttractions("restaurants", "")
        1. Returns hardcoded recommendations by category
        2. Categories: restaurants, attractions, nightlife, shopping
        3. Each with name, distance, description
```

### Tool: escalateToHumanAgent

**Triggered when:** Guest is frustrated, has complex issue, or explicitly requests human help.

```
Guest: "This is unacceptable, get me a manager!"
    → LLM calls escalateToHumanAgent("Guest frustrated with service", "HIGH")
        1. Loads Reservation + Conversation
        2. Creates EscalationTicket entity (status=OPEN)
        3. Marks Conversation as ESCALATED
        4. Returns message: "I've connected you with our guest services team..."
```

---

## 4. Sentiment Analysis & Auto-Escalation

### How Sentiment Works

Every user message goes through `SentimentAnalysisService.analyzeSentiment()`:

```
1. Builds prompt asking GPT-4o-mini to return JSON: { "score": float, "label": string }
2. Sends to LLM
3. Parses response:
   - Flattens to single line
   - Regex extracts score (handles negative numbers)
   - String matching for label (VERY_POSITIVE, POSITIVE, NEUTRAL, NEGATIVE, VERY_NEGATIVE)
4. Returns SentimentResult(score, label)
```

### Auto-Escalation Triggers

After sentiment analysis, `ConciergeAiService` checks:

```java
if (sentimentScore < -0.6 || sentimentService.requiresEscalation(userMessage)) {
    handleEscalation(conversation, sentimentScore, userMessage);
}
```

**Keyword-based escalation** (`requiresEscalation`):
- manager, supervisor, complaint, unacceptable, terrible, worst, disgusting
- lawsuit, refund, furious, outraged, speak to someone, human, real person, not acceptable

**Score-based escalation:** sentiment score below -0.6

**Note:** Auto-escalation only marks the conversation as ESCALATED. The AI tool `escalateToHumanAgent` additionally creates an `EscalationTicket` record.

---

## 5. WhatsApp Channel (Twilio)

### Flow

```
[Twilio] POST /webhook/whatsapp?From=whatsapp:+1234&Body=...
    → WebhookController.handleWhatsAppWebhook(from, body, profileName)
        → WhatsAppService.handleIncomingMessage(from, body, profileName)
```

### Session Initialization

```
Guest sends: "TOKEN:eyJhbGciOi..."
    → WhatsAppService.handleTokenMessage(from, token)
        1. QrCodeService.validateQrToken(token)
        2. Creates sessionId: "whatsapp-{phone}-{timestamp}"
        3. Maps phone → sessionId and phone → Reservation (in-memory)
        4. Returns welcome message via ConciergeAiService.generateWelcomeMessage()
```

### Subsequent Messages

```
Guest sends: "Book me a spa appointment"
    → WhatsAppService checks phoneToSession map
    → If session exists: ConciergeAiService.processMessage(sessionId, body, reservation)
    → Returns AI response
    → WebhookController wraps in TwiML XML: <Response><Message>...</Message></Response>
```

### Outbound Messages

```
WhatsAppService.sendMessage(to, message)
    → Twilio SDK: Message.creator(to, from, body).create()
    → Skipped if Twilio credentials not configured
```

---

## 6. RAG Document Management

### Document Upload

```
[Frontend] POST /rag/upload (multipart: file + category)
    → RagController.uploadDocument(file, category)
        → RagIngestionService.ingestFile(file, category)
            1. Check for duplicate (by filename)
            2. Extract text:
               - .txt/.md/.csv → plain read
               - .pdf/.docx → Apache Tika extraction
            3. ingestContent(content, fileName, category):
               a. Split text: DocumentSplitters.recursive(500, 50)
               b. For each chunk:
                  - Generate embedding via OpenAI text-embedding-3-small
                  - Store in Weaviate with properties: content, fileName, category, chunkIndex, uploadTime
            4. Track in uploadedDocuments list
            5. Return result: fileName, chunksCreated, status
```

### Default Documents Ingestion

```
[Frontend] POST /rag/ingest-defaults
    → RagController.ingestDefaults()
        → RagIngestionService.ingestClasspathDocuments()
            - Reads 5 files from classpath: hotel-faqs.txt, amenities.txt, policies.txt, restaurant-menus.txt, spa-services.txt
            - Each processed same as upload (split → embed → store)
            - Skips duplicates
```

### RAG Retrieval (during chat)

```
VectorSearchService.buildRagContext(userQuery)
    1. Embed query via text-embedding-3-small
    2. Weaviate GraphQL nearVector query (top-5)
    3. Extract content from results
    4. Format as: "RELEVANT HOTEL KNOWLEDGE:\n---\n{chunk1}\n---\n{chunk2}..."
    5. Appended to user message before sending to LLM
```

### Document Deletion

```
[Frontend] DELETE /rag/documents?fileName=...
    → RagIngestionService.deleteDocument(fileName)
        1. Query Weaviate for all chunks with matching fileName
        2. Delete each by ID (paginated, 200 at a time)
        3. Remove from local tracking list
```

---

## 7. Admin Dashboard

### Stats Endpoint

```
[Frontend] GET /dashboard/stats/{hotelId}
    → DashboardController.getStats(hotelId)
        → DashboardService.getStats(hotelId)
            1. Count active conversations (status=ACTIVE)
            2. Count conversations created today
            3. Count open escalation tickets
            4. Count pending housekeeping requests
            5. Calculate average sentiment (excluding 0.0 scores)
            6. Build sentiment distribution (from ALL conversations with sentiment)
            7. Build recent conversations list (top 10 active)
            8. Build escalation summaries (top 10 active)
            → Returns DashboardStats DTO
```

### Admin Operations

```
GET  /admin/reservations/hotel/{hotelId}     → All checked-in reservations
GET  /admin/conversations/hotel/{hotelId}    → Active conversations
GET  /admin/escalations/hotel/{hotelId}      → Open/in-progress escalation tickets
PUT  /admin/escalations/{id}/resolve         → Mark ticket resolved + add notes
GET  /admin/housekeeping/hotel/{hotelId}     → Pending housekeeping requests
PUT  /admin/housekeeping/{id}/status         → Update request status
GET  /admin/spa-bookings/hotel/{hotelId}     → Today's confirmed spa bookings
GET  /admin/restaurant-bookings/hotel/{id}   → All restaurant bookings
```

### Real-time Updates (WebSocket)

```
NotificationService sends to STOMP topics:
    /topic/dashboard      → General dashboard events
    /topic/escalations    → New escalation alerts
    /topic/housekeeping   → New housekeeping requests
    /topic/chat/{session} → Per-session chat updates
```

---

## 8. Authentication & Security

### Admin Login

```
[Frontend] POST /auth/login { username, password }
    → AuthController.login(request)
        1. Load AppUser by username
        2. BCrypt password verification
        3. Check user is active
        4. Generate JWT: JwtTokenProvider.generateToken(username, role)
           - Claims: subject=username, role=ADMIN/MANAGER/etc
           - Expiry: 24 hours
        5. Return AuthResponse: token, tokenType, username, role, expiresIn
```

### Request Authentication (JwtAuthenticationFilter)

```
Every request passes through JwtAuthenticationFilter:
    1. Extract token from:
       - Authorization: Bearer {token} header
       - ?token={token} query parameter
    2. Validate token (signature + expiry)
    3. Parse claims:
       - If type="QR_ACCESS" → grant ROLE_GUEST
       - Otherwise → grant ROLE_{role}
    4. Set SecurityContext authentication
```

### Audit Logging (AOP)

```
AuditAspect intercepts all @PostMapping, @PutMapping, @DeleteMapping:
    1. Extract method name (action)
    2. Extract controller class name (entityType)
    3. Get authenticated user + role from SecurityContext
    4. Get client IP (X-Forwarded-For or remoteAddr)
    5. Execute method
    6. Save AuditLog entity to DB
```

---

## LangChain4J Integration Summary

| Component | How It's Used |
|-----------|--------------|
| `ChatLanguageModel` | OpenAI GPT-4o-mini bean, used for chat + sentiment analysis |
| `EmbeddingModel` | OpenAI text-embedding-3-small, used for RAG embeddings |
| `AiServices.builder()` | Builds per-session assistant with memory + tools |
| `MessageWindowChatMemory` | 20-message sliding window per session |
| `@Tool` annotations | 7 tools on ConciergeTools, LLM decides when to call |
| `DocumentSplitters.recursive()` | Chunks documents (500 chars, 50 overlap) for RAG |
| `SystemMessage` | Dynamic system prompt with guest context injected |

### Key Design Decisions

1. **Per-session tools**: `ConciergeTools` is instantiated per session (not singleton) so each instance is bound to a specific reservation — the AI never needs to ask "which room?"
2. **RAG before tools**: Vector search happens on every message, context is appended to the user message. The LLM sees both RAG context and tool descriptions simultaneously.
3. **Sentiment is separate from chat**: Sentiment analysis uses a separate LLM call (not the chat assistant) to avoid polluting conversation memory.
4. **Memory is in-process**: Chat memory lives in a HashMap keyed by sessionId. Lost on restart. DB stores messages for history but doesn't reload into memory.
