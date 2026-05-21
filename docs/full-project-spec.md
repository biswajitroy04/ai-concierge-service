# AI Concierge Service - Complete Project Specification

## 1. PROJECT OVERVIEW

**Name:** AI Concierge Service  
**Description:** Enterprise AI-powered concierge platform for luxury hotels  
**Group ID:** com.hotel  
**Artifact ID:** ai-concierge-service  
**Version:** 1.0.0-SNAPSHOT  

### 1.1 What It Does

An AI-powered hotel concierge accessible via web chat (QR code onboarding) and WhatsApp (Twilio). Guests scan a QR code linked to their reservation, which opens a chat with a GPT-4o-mini powered assistant that has full context about their stay, hotel amenities, and can execute actions (book spa, request housekeeping, reserve restaurants, check late checkout eligibility, escalate to humans).

### 1.2 Key Features

- **Guest Chat**: Multilingual AI concierge with RAG-enhanced responses from hotel knowledge base
- **QR Onboarding**: Per-reservation QR codes with embedded JWT tokens
- **WhatsApp Integration**: Twilio-powered messaging channel
- **AI Tool Calls**: Housekeeping, spa bookings, restaurant reservations, late checkout, recommendations, escalation
- **Sentiment Analysis**: Real-time monitoring with auto-escalation
- **Admin Dashboard**: Live conversation monitoring, escalation management, housekeeping queue via WebSocket
- **RAG Document Management**: Upload/ingest/delete documents into Weaviate vector store via UI or API

### 1.3 User Roles

| Role | Enum Value | Access |
|------|-----------|--------|
| Admin | ADMIN | Full system access |
| Manager | MANAGER | Dashboard + admin operations |
| Front Desk | FRONT_DESK | QR generation + view |
| Concierge | CONCIERGE | Dashboard view only |
| Guest | GUEST (via QR token) | Chat only |
| Housekeeping | HOUSEKEEPING | (staff role) |
| Spa Staff | SPA_STAFF | (staff role) |

---

## 2. TECH STACK & DEPENDENCIES

### 2.1 Core

| Component | Technology | Version |
|-----------|-----------|---------|
| Language | Java | 17 |
| Framework | Spring Boot | 3.2.5 |
| Build | Maven | 3.9+ |
| Database | MySQL | 8.0 |
| Vector DB | Weaviate | 1.24.8 |
| AI/LLM | LangChain4J + OpenAI | 0.35.0 |
| Container | Docker + docker-compose | 3.8 |
| Orchestration | Kubernetes | - |
| CI/CD | GitHub Actions | - |

### 2.2 Maven Dependencies (pom.xml)

**Properties:**
```xml
<java.version>17</java.version>
<langchain4j.version>0.35.0</langchain4j.version>
<jjwt.version>0.12.5</jjwt.version>
<zxing.version>3.5.3</zxing.version>
<springdoc.version>2.5.0</springdoc.version>
<testcontainers.version>1.19.7</testcontainers.version>
<tika.version>2.9.2</tika.version>
```

**Spring Boot Starters:** web, data-jpa, security, validation, websocket, actuator, cache, aop

**Key Libraries:**
- `dev.langchain4j:langchain4j` + `langchain4j-open-ai` + `langchain4j-spring-boot-starter` (0.35.0)
- `io.weaviate:client` (4.8.0)
- `io.jsonwebtoken:jjwt-api/impl/jackson` (0.12.5)
- `com.google.zxing:core` + `javase` (3.5.3)
- `org.springdoc:springdoc-openapi-starter-webmvc-ui` (2.5.0)
- `org.projectlombok:lombok`
- `com.github.ben-manes.caffeine:caffeine`
- `com.bucket4j:bucket4j-core` (8.10.1)
- `com.twilio.sdk:twilio` (10.1.5)
- `org.apache.tika:tika-core` + `tika-parsers-standard-package` (2.9.2)
- `com.mysql:mysql-connector-j` (runtime)
- `com.fasterxml.jackson.datatype:jackson-datatype-jsr310`

**Test Dependencies:**
- `spring-boot-starter-test`
- `spring-security-test`
- `org.testcontainers:mysql` + `junit-jupiter` (1.19.7)

---

## 3. PROJECT STRUCTURE

```
ai-concierge-service/
├── .env.example
├── .gitignore
├── .github/workflows/ci-cd.yml
├── pom.xml
├── README.md
├── docker-compose.yml
├── docker/Dockerfile
├── k8s/
│   ├── namespace.yml
│   └── deployment.yml
├── docs/
│   ├── api-contracts.yml
│   ├── architecture.md
│   ├── twilio-whatsapp-setup.md
│   └── weaviate-setup.md
├── backend/                          (empty - sources at root level)
└── src/main/
    ├── java/com/hotel/concierge/
    │   ├── AiConciergeApplication.java
    │   ├── config/
    │   │   ├── AuditAspect.java
    │   │   ├── LangChainConfig.java
    │   │   ├── RateLimitConfig.java
    │   │   ├── SecurityConfig.java
    │   │   ├── WeaviateConfig.java
    │   │   ├── WebConfig.java
    │   │   └── WebSocketConfig.java
    │   ├── controller/
    │   │   ├── AdminController.java
    │   │   ├── AuthController.java
    │   │   ├── ChatController.java
    │   │   ├── DashboardController.java
    │   │   ├── QrCodeController.java
    │   │   ├── RagController.java
    │   │   └── WebhookController.java
    │   ├── dto/
    │   │   ├── AuthRequest.java
    │   │   ├── AuthResponse.java
    │   │   ├── ChatRequest.java
    │   │   ├── ChatResponse.java
    │   │   ├── DashboardStats.java
    │   │   └── QrCodeResponse.java
    │   ├── exception/
    │   │   └── GlobalExceptionHandler.java
    │   ├── model/
    │   │   ├── AppUser.java
    │   │   ├── AuditLog.java
    │   │   ├── ChatMessage.java
    │   │   ├── Conversation.java
    │   │   ├── EscalationTicket.java
    │   │   ├── Guest.java
    │   │   ├── Hotel.java
    │   │   ├── HousekeepingRequest.java
    │   │   ├── Reservation.java
    │   │   ├── RestaurantBooking.java
    │   │   └── SpaBooking.java
    │   ├── repository/
    │   │   ├── AppUserRepository.java
    │   │   ├── AuditLogRepository.java
    │   │   ├── ChatMessageRepository.java
    │   │   ├── ConversationRepository.java
    │   │   ├── EscalationTicketRepository.java
    │   │   ├── GuestRepository.java
    │   │   ├── HotelRepository.java
    │   │   ├── HousekeepingRequestRepository.java
    │   │   ├── ReservationRepository.java
    │   │   ├── RestaurantBookingRepository.java
    │   │   └── SpaBookingRepository.java
    │   ├── security/
    │   │   ├── CustomUserDetailsService.java
    │   │   ├── JwtAuthenticationFilter.java
    │   │   └── JwtTokenProvider.java
    │   └── service/
    │       ├── DashboardService.java
    │       ├── RagIngestionService.java
    │       ├── ai/
    │       │   ├── ConciergeAiService.java
    │       │   ├── ConciergeAssistant.java
    │       │   ├── VectorSearchService.java
    │       │   └── tools/
    │       │       ├── ConciergeTools.java
    │       │       └── ConciergeToolsFactory.java
    │       ├── notification/
    │       │   └── NotificationService.java
    │       ├── qr/
    │       │   └── QrCodeService.java
    │       ├── sentiment/
    │       │   └── SentimentAnalysisService.java
    │       └── whatsapp/
    │           └── WhatsAppService.java
    └── resources/
        ├── application.yml
        ├── db/
        │   ├── schema.sql
        │   └── seed-data.sql
        ├── prompts/
        │   ├── concierge-system-prompt.txt
        │   ├── escalation-classifier-prompt.txt
        │   ├── recommendation-engine-prompt.txt
        │   └── sentiment-analysis-prompt.txt
        ├── rag/
        │   ├── amenities.txt
        │   ├── hotel-faqs.txt
        │   ├── policies.txt
        │   ├── restaurant-menus.txt
        │   └── spa-services.txt
        └── static/
            ├── index.html
            ├── css/styles.css
            └── js/app.js
```

---

## 4. DATABASE SCHEMA (MySQL 8.0)

Database name: `hotel_concierge` (charset: utf8mb4, collation: utf8mb4_unicode_ci)

### 4.1 hotels
```sql
CREATE TABLE hotels (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    address VARCHAR(500) NOT NULL,
    city VARCHAR(100),
    country VARCHAR(100),
    phone VARCHAR(20),
    email VARCHAR(200),
    star_rating INT,
    timezone VARCHAR(50) DEFAULT 'UTC',
    check_in_time VARCHAR(10) DEFAULT '15:00',
    check_out_time VARCHAR(10) DEFAULT '11:00',
    late_checkout_fee DOUBLE DEFAULT 50.0,
    max_late_checkout_hour INT DEFAULT 14,
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;
```

### 4.2 guests
```sql
CREATE TABLE guests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(200),
    phone VARCHAR(20),
    title VARCHAR(10),
    preferred_language VARCHAR(10) DEFAULT 'en',
    nationality VARCHAR(100),
    loyalty_tier VARCHAR(50),
    loyalty_points INT DEFAULT 0,
    total_stays INT DEFAULT 0,
    preferences TEXT,
    dietary_restrictions VARCHAR(500),
    special_occasions VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_guest_email (email),
    INDEX idx_guest_phone (phone)
) ENGINE=InnoDB;
```

### 4.3 app_users
```sql
CREATE TABLE app_users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(200) NOT NULL,
    full_name VARCHAR(200),
    role VARCHAR(30) NOT NULL,
    hotel_id BIGINT,
    active BOOLEAN DEFAULT TRUE,
    last_login TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (hotel_id) REFERENCES hotels(id),
    INDEX idx_user_username (username)
) ENGINE=InnoDB;
```

### 4.4 reservations
```sql
CREATE TABLE reservations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    confirmation_number VARCHAR(50) NOT NULL UNIQUE,
    hotel_id BIGINT NOT NULL,
    guest_id BIGINT NOT NULL,
    room_number VARCHAR(20),
    room_type VARCHAR(100),
    floor_number INT,
    check_in_date DATE NOT NULL,
    check_out_date DATE NOT NULL,
    number_of_guests INT DEFAULT 1,
    rate_per_night DOUBLE,
    currency VARCHAR(10) DEFAULT 'USD',
    status VARCHAR(30) NOT NULL DEFAULT 'CONFIRMED',
    special_requests TEXT,
    is_vip BOOLEAN DEFAULT FALSE,
    late_checkout_approved BOOLEAN DEFAULT FALSE,
    late_checkout_time VARCHAR(10),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (hotel_id) REFERENCES hotels(id),
    FOREIGN KEY (guest_id) REFERENCES guests(id),
    INDEX idx_reservation_confirmation (confirmation_number),
    INDEX idx_reservation_hotel_status (hotel_id, status),
    INDEX idx_reservation_dates (check_in_date, check_out_date)
) ENGINE=InnoDB;
```

**Reservation statuses:** CONFIRMED, CHECKED_IN, CHECKED_OUT, CANCELLED, NO_SHOW

### 4.5 housekeeping_requests
```sql
CREATE TABLE housekeeping_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reservation_id BIGINT NOT NULL,
    request_type VARCHAR(100) NOT NULL,
    description TEXT,
    priority VARCHAR(30) NOT NULL DEFAULT 'NORMAL',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    assigned_staff VARCHAR(100),
    estimated_completion_minutes INT,
    completed_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (reservation_id) REFERENCES reservations(id),
    INDEX idx_housekeeping_status (status),
    INDEX idx_housekeeping_priority (priority)
) ENGINE=InnoDB;
```

**Priority:** LOW, NORMAL, HIGH, URGENT  
**Status:** PENDING, ASSIGNED, IN_PROGRESS, COMPLETED, CANCELLED

### 4.6 spa_bookings
```sql
CREATE TABLE spa_bookings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reservation_id BIGINT NOT NULL,
    service_name VARCHAR(200) NOT NULL,
    therapist_name VARCHAR(100),
    booking_date DATE NOT NULL,
    start_time TIME NOT NULL,
    duration_minutes INT NOT NULL,
    price DOUBLE NOT NULL,
    currency VARCHAR(10) DEFAULT 'USD',
    status VARCHAR(30) NOT NULL DEFAULT 'CONFIRMED',
    special_notes VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (reservation_id) REFERENCES reservations(id),
    INDEX idx_spa_date (booking_date)
) ENGINE=InnoDB;
```

**Status:** CONFIRMED, IN_PROGRESS, COMPLETED, CANCELLED, NO_SHOW

### 4.7 restaurant_bookings
```sql
CREATE TABLE restaurant_bookings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reservation_id BIGINT NOT NULL,
    restaurant_name VARCHAR(200) NOT NULL,
    booking_date DATE NOT NULL,
    booking_time TIME NOT NULL,
    party_size INT NOT NULL,
    seating_preference VARCHAR(100),
    special_requests VARCHAR(500),
    status VARCHAR(30) NOT NULL DEFAULT 'CONFIRMED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (reservation_id) REFERENCES reservations(id),
    INDEX idx_restaurant_date (booking_date)
) ENGINE=InnoDB;
```

**Status:** CONFIRMED, SEATED, COMPLETED, CANCELLED, NO_SHOW

### 4.8 conversations
```sql
CREATE TABLE conversations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(100) NOT NULL UNIQUE,
    reservation_id BIGINT NOT NULL,
    channel VARCHAR(20) DEFAULT 'WEB',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    sentiment_score DOUBLE,
    sentiment_label VARCHAR(30),
    is_escalated BOOLEAN DEFAULT FALSE,
    escalation_reason VARCHAR(500),
    assigned_agent VARCHAR(100),
    language VARCHAR(10) DEFAULT 'en',
    message_count INT DEFAULT 0,
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ended_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (reservation_id) REFERENCES reservations(id),
    INDEX idx_conversation_session (session_id),
    INDEX idx_conversation_status (status),
    INDEX idx_conversation_reservation (reservation_id)
) ENGINE=InnoDB;
```

**Status:** ACTIVE, ESCALATED, RESOLVED, CLOSED

### 4.9 chat_messages
```sql
CREATE TABLE chat_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    tool_call VARCHAR(200),
    tool_result TEXT,
    sentiment_score DOUBLE,
    tokens_used INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (conversation_id) REFERENCES conversations(id),
    INDEX idx_message_conversation (conversation_id),
    INDEX idx_message_created (created_at)
) ENGINE=InnoDB;
```

**Role:** USER, ASSISTANT, SYSTEM, TOOL

### 4.10 escalation_tickets
```sql
CREATE TABLE escalation_tickets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    reservation_id BIGINT NOT NULL,
    reason VARCHAR(200) NOT NULL,
    description TEXT,
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    assigned_to VARCHAR(100),
    sentiment_score DOUBLE,
    resolution_notes TEXT,
    resolved_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (conversation_id) REFERENCES conversations(id),
    FOREIGN KEY (reservation_id) REFERENCES reservations(id),
    INDEX idx_escalation_status (status),
    INDEX idx_escalation_priority (priority)
) ENGINE=InnoDB;
```

**Priority:** LOW, MEDIUM, HIGH, CRITICAL  
**Status:** OPEN, IN_PROGRESS, RESOLVED, CLOSED

### 4.11 audit_logs
```sql
CREATE TABLE audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id BIGINT,
    user_id VARCHAR(100),
    user_role VARCHAR(50),
    ip_address VARCHAR(50),
    details TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_action (action),
    INDEX idx_audit_entity (entity_type, entity_id),
    INDEX idx_audit_created (created_at)
) ENGINE=InnoDB;
```

### 4.12 Seed Data

**Hotels (3):**
- The Grand Meridian (New York, 5-star, check-in 15:00, check-out 11:00, late fee $75, max 14:00)
- Azure Palace Resort (Miami, 5-star, check-in 16:00, check-out 12:00, late fee $50, max 15:00)
- Imperial Sakura Hotel (Tokyo, 5-star, check-in 15:00, check-out 11:00, late fee ¥8000, max 14:00)

**Guests (5):**
- Arjun Roy (PLATINUM, 12 stays, vegetarian, anniversary May 22)
- Sarah Mitchell (GOLD, 8 stays, gluten-free)
- Takeshi Yamamoto (SILVER, 4 stays, Japanese language)
- Elena Petrova (PLATINUM, 18 stays, birthday May 25)
- James Chen (no tier, 2 stays, nut allergy)

**Reservations (5):**
- GM-2024-001: Arjun Roy, Room 1708, Executive Suite, Floor 17, $850/night, CHECKED_IN, VIP
- GM-2024-002: Sarah Mitchell, Room 1205, Deluxe King, Floor 12, $550/night, CHECKED_IN
- GM-2024-003: James Chen, Room 0803, Superior Double, Floor 8, $420/night, CHECKED_IN
- AP-2024-001: Elena Petrova, Room 2201, Presidential Suite, Floor 22, $2200/night, CHECKED_IN, VIP
- IS-2024-001: Takeshi Yamamoto, Room 1501, Imperial Suite, Floor 15, ¥180000/night, CONFIRMED

**App Users (4):** (all passwords: `admin123`, bcrypt: `$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy`)
- admin (ADMIN, hotel 1)
- manager.smith (MANAGER, hotel 1)
- frontdesk.jones (FRONT_DESK, hotel 1)
- concierge.wilson (CONCIERGE, hotel 1)

---

## 5. APPLICATION CONFIGURATION (application.yml)

```yaml
server:
  port: 9090

spring:
  application:
    name: ai-concierge-service
  datasource:
    url: jdbc:mysql://localhost:3306/hotel_concierge?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:RoySoft@2024}
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      idle-timeout: 300000
      connection-timeout: 20000
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false
    open-in-view: true
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQLDialect
        format_sql: true
  jackson:
    serialization:
      write-dates-as-timestamps: false
    default-property-inclusion: non_null

langchain4j:
  open-ai:
    chat-model:
      api-key: ${OPENAI_API_KEY:demo}
      model-name: gpt-4o-mini
      temperature: 0.7
      max-tokens: 2048
      log-requests: true
      log-responses: false
    embedding-model:
      api-key: ${OPENAI_API_KEY:demo}
      model-name: text-embedding-3-small

app:
  weaviate:
    scheme: http
    host: localhost:8081
    class-name: HotelDocumentChunk
    top-k: 5

jwt:
  secret: ${JWT_SECRET:YWktY29uY2llcmdlLXNlcnZpY2Utc2VjcmV0LWtleS1mb3ItaG90ZWwtcGxhdGZvcm0tMjAyNA==}
  expiration-ms: 86400000       # 24 hours
  qr-expiration-ms: 604800000   # 7 days

qr:
  base-url: ${QR_BASE_URL:http://localhost:9090}
  width: 300
  height: 300

twilio:
  account-sid: ${TWILIO_ACCOUNT_SID:}
  auth-token: ${TWILIO_AUTH_TOKEN:}
  whatsapp-number: ${TWILIO_WHATSAPP_NUMBER:+14155238886}

rate-limit:
  requests-per-minute: 60
  burst-capacity: 10

logging:
  level:
    com.hotel.concierge: DEBUG
    dev.langchain4j: INFO
    org.springframework.security: INFO
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when-authorized

springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html
    tags-sorter: alpha
    operations-sorter: alpha
```

---

## 6. API ENDPOINTS

### 6.1 Authentication
| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | /auth/login | No | Login with username/password, returns JWT |
| GET | /auth/validate | JWT | Validate token |

### 6.2 Chat
| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | /chat/guest?token= | No (QR token in param) | Initialize guest chat session |
| POST | /chat/message | JWT (Bearer) | Send message to AI concierge |
| GET | /chat/history/{sessionId} | JWT | Get chat history |

### 6.3 QR Code
| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | /qr/reservations | No | List all reservations for QR generation |
| POST | /qr/generate/{reservationId} | No | Generate QR code for reservation |
| GET | /qr/validate?token= | No | Validate a QR token |

### 6.4 Dashboard
| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | /dashboard/stats/{hotelId} | No* | Get dashboard statistics |

### 6.5 Admin
| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | /admin/reservations/hotel/{hotelId} | No* | Get checked-in reservations |
| GET | /admin/conversations/hotel/{hotelId} | No* | Get active conversations |
| GET | /admin/escalations/hotel/{hotelId} | No* | Get active escalation tickets |
| PUT | /admin/escalations/{ticketId}/resolve?resolutionNotes= | No* | Resolve escalation |
| GET | /admin/housekeeping/hotel/{hotelId} | No* | Get pending housekeeping |
| PUT | /admin/housekeeping/{requestId}/status?status= | No* | Update housekeeping status |
| GET | /admin/spa-bookings/hotel/{hotelId} | No* | Get today's spa bookings |
| GET | /admin/spa-bookings/all/{hotelId} | No* | Get all spa bookings |
| GET | /admin/restaurant-bookings/hotel/{hotelId} | No* | Get restaurant bookings |

### 6.6 RAG Document Management
| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | /rag/upload (multipart) | No | Upload document to vector store |
| POST | /rag/ingest-defaults | No | Ingest bundled RAG documents |
| GET | /rag/documents | No | List uploaded documents |
| GET | /rag/stats | No | Get vector store statistics |
| DELETE | /rag/documents?fileName= | No | Delete document and chunks |

### 6.7 Webhooks
| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | /webhook/whatsapp | No | Twilio WhatsApp incoming webhook |
| GET | /webhook/whatsapp/status | No | Webhook health check |

*Note: SecurityConfig permits /admin/** and /dashboard/** without auth for development. In production, these should require ADMIN/MANAGER roles.

---

## 7. SECURITY CONFIGURATION

### 7.1 Spring Security Filter Chain

**Permitted paths (no auth required):**
- `/`, `/index.html`, `/css/**`, `/js/**`, `/assets/**`, `/favicon.ico`
- `/auth/**`
- `/chat/guest/**`
- `/qr/**`, `/rag/**`
- `/webhook/**`
- `/health`, `/actuator/**`
- `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`
- `/admin/**`, `/dashboard/**` (permitAll for dev)
- All OPTIONS requests

**All other requests:** require authentication

### 7.2 JWT Token Types

**Admin Token:**
- Subject: username
- Claims: `role` (e.g., "ADMIN")
- Expiry: 24 hours
- Signed with HMAC-SHA256

**QR Token:**
- Subject: `guest-{guestId}`
- Claims: `reservationId`, `guestId`, `hotelId`, `type: "QR_ACCESS"`
- Expiry: 7 days
- Signed with HMAC-SHA256

### 7.3 JWT Filter Behavior

The `JwtAuthenticationFilter` extracts tokens from:
1. `Authorization: Bearer <token>` header
2. `?token=<token>` query parameter (for QR access)

If token type is `QR_ACCESS`, grants `ROLE_GUEST`. Otherwise grants `ROLE_{role}`.

### 7.4 Rate Limiting (Bucket4j)

- 60 requests/minute per IP (greedy refill)
- Burst: 10 requests/second (intervally refill)
- Applied in ChatController per client IP

### 7.5 CORS

- Allowed origins: `*`
- Allowed methods: GET, POST, PUT, DELETE, OPTIONS
- Allowed headers: `*`
- Exposed headers: Authorization

---

## 8. AI / LLM ARCHITECTURE

### 8.1 LangChain4J Configuration

- **Chat Model:** OpenAI GPT-4o-mini (temperature 0.7, max 2048 tokens)
- **Embedding Model:** OpenAI text-embedding-3-small
- **Chat Memory:** MessageWindowChatMemory with 20 messages max (per session)
- **Tools:** Per-session ConciergeTools instance bound to a specific reservation

### 8.2 ConciergeAssistant Interface

```java
public interface ConciergeAssistant {
    String chat(String userMessage);
}
```

Built dynamically per session using `AiServices.builder()` with:
- chatLanguageModel
- chatMemory (20 message window)
- tools (ConciergeTools instance)

### 8.3 AI Tools (7 tools)

Each tool is annotated with `@Tool` and bound per-session to a reservation:

1. **createHousekeepingRequest(requestType, description, priority)** - Creates housekeeping request
2. **bookSpaAppointment(serviceName, preferredDate, preferredTime)** - Books spa treatment
3. **checkLateCheckoutEligibility()** - Checks loyalty tier and VIP status for late checkout pricing
4. **confirmLateCheckout(checkoutTime, fee)** - Records late checkout after guest agrees
5. **recommendNearbyAttractions(category, preferences)** - Returns hardcoded recommendations by category
6. **escalateToHumanAgent(reason, priority)** - Creates escalation ticket, marks conversation escalated
7. **bookRestaurantReservation(restaurantName, preferredDate, preferredTime, partySize, specialRequests)** - Books restaurant table

### 8.4 RAG Pipeline

**Ingestion:**
1. Documents split using `DocumentSplitters.recursive(500, 50)` (500 chars, 50 overlap)
2. Each chunk embedded via OpenAI text-embedding-3-small
3. Stored in Weaviate class `HotelDocumentChunk` with properties: content, fileName, category, chunkIndex, uploadTime

**Retrieval:**
1. User query embedded via same embedding model
2. Weaviate nearVector search (top-k=5)
3. Retrieved chunks concatenated as context
4. Injected into user message before sending to LLM

**Weaviate Schema:**
- Class: `HotelDocumentChunk`
- Vectorizer: none (embeddings provided externally)
- Properties: text (text), fileName (text), category (text), chunkIndex (int), uploadedAt (text)

### 8.5 Sentiment Analysis

Uses the same ChatLanguageModel to analyze each user message:
- Returns score (-1.0 to 1.0) and label (VERY_POSITIVE, POSITIVE, NEUTRAL, NEGATIVE, VERY_NEGATIVE)
- Keyword-based escalation detection for: manager, supervisor, complaint, unacceptable, terrible, worst, disgusting, lawsuit, refund, furious, outraged, speak to someone, human, real person, not acceptable
- Auto-escalation if sentiment score < -0.6 or escalation keywords detected

### 8.6 System Prompt (Dynamic)

Built per-session with guest context:
- Hotel name, star rating
- Guest name, loyalty tier, total stays
- Room number, type, floor
- Stay dates
- Special requests, preferred language, dietary restrictions
- Hotel check-in/out times, late checkout fee/max hour
- Instructions for tool usage, personality, escalation triggers

---

## 9. WEBSOCKET CONFIGURATION

- **Broker prefixes:** `/topic`, `/queue`
- **Application destination prefix:** `/app`
- **STOMP endpoint:** `/ws` (with SockJS fallback, allowed origins: `*`)

**Dashboard topics:**
- `/topic/dashboard` - General dashboard events
- `/topic/escalations` - Escalation notifications
- `/topic/housekeeping` - Housekeeping updates
- `/topic/chat/{sessionId}` - Per-session chat updates

---

## 10. FRONTEND (Single Page Application)

### 10.1 Technology

- Pure HTML/CSS/JavaScript (no framework)
- Fonts: Playfair Display (headings), Inter (body) via Google Fonts
- Icons: Font Awesome 6.5.1 (CDN)
- Design: Dark navy + gold glassmorphism luxury theme

### 10.2 Screens

1. **Onboarding Screen** - QR code generation, token entry, demo mode button, services showcase
2. **Chat Screen** - Message bubbles, typing indicator, quick action buttons, chat input
3. **Dashboard Screen** - Stats cards, active conversations, escalation alerts, sentiment monitor, housekeeping/spa/restaurant panels
4. **RAG Screen** - Document upload (drag & drop), ingest defaults button, document list with delete, vector store stats

### 10.3 Navigation

Bottom nav bar with 3 tabs: Chat, RAG, Dashboard. Hidden on onboarding screen.

### 10.4 Key Frontend Behaviors

- Token/session persisted in localStorage (`concierge_token`, `concierge_session`)
- Demo mode available without backend (simulated responses)
- QR generation calls `/qr/generate/{id}` and displays base64 PNG
- Chat messages sent to `/chat/message` with Bearer token
- Dashboard polls `/dashboard/stats/1` on screen show
- RAG management: upload via multipart POST, ingest defaults, list, delete

---

## 11. DOCKER & DEPLOYMENT

### 11.1 docker-compose.yml

Three services:
- **mysql** (mysql:8.0) - port 3306, auto-runs schema.sql and seed-data.sql
- **weaviate** (semitechnologies/weaviate:1.24.8) - port 8081→8080, anonymous access, no vectorizer module
- **app** (built from docker/Dockerfile) - port 8080, depends on mysql+weaviate healthy

### 11.2 Dockerfile (Multi-stage)

```dockerfile
# Build stage
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app
COPY backend/pom.xml .
RUN mvn dependency:go-offline -B
COPY backend/src ./src
RUN mvn package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S concierge && adduser -S concierge -G concierge
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
COPY frontend/ /app/static/
RUN chown -R concierge:concierge /app
USER concierge
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/health || exit 1
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=docker", "-Xmx512m", "app.jar"]
```

### 11.3 Kubernetes (k8s/)

**namespace.yml:** Creates `hotel-concierge` namespace

**deployment.yml:** Contains:
- Deployment: 3 replicas, image `ai-concierge-service:latest`
- Resource limits: 512Mi-1Gi memory, 250m-1000m CPU
- Liveness/readiness probes on `/api/actuator/health`
- Environment from ConfigMap (`concierge-config`) and Secret (`concierge-secrets`)
- Service: ClusterIP on port 80→8080
- Ingress: TLS on `concierge.hotel.com`, nginx rate-limit annotation
- ConfigMap: database-url, weaviate-host
- Secret: db-username, db-password, openai-api-key, jwt-secret (base64 encoded)

### 11.4 CI/CD (.github/workflows/ci-cd.yml)

**Triggers:** push to main/develop, PR to main

**Jobs:**
1. `build-and-test` - JDK 21, MySQL service container, compile → test → verify → package → upload artifact
2. `docker-build` - (main only) Build and push to ghcr.io
3. `deploy` - (main only, production environment) kubectl apply

---

## 12. KEY IMPLEMENTATION DETAILS

### 12.1 Application Entry Point

```java
@SpringBootApplication
@EnableCaching
@EnableAsync
@EnableScheduling
public class AiConciergeApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiConciergeApplication.class, args);
    }
}
```

### 12.2 Audit Logging (AOP)

`AuditAspect` intercepts all `@PostMapping`, `@PutMapping`, `@DeleteMapping` methods. Logs: action name, entity type (controller class), user ID, role, IP address, method signature.

### 12.3 Web Configuration

`WebConfig` serves static resources from `classpath:/static/` and forwards `/` to `/index.html`.

### 12.4 QR Code Service

- Generates JWT QR token with reservationId, guestId, hotelId
- Builds chat URL: `{baseUrl}/chat/guest?token={token}`
- Generates QR image using ZXing (300x300, margin 2, UTF-8)
- Returns base64-encoded PNG

### 12.5 WhatsApp Service

- Maps phone numbers to sessions (in-memory ConcurrentHashMap)
- First message with `TOKEN:` prefix initializes session
- Subsequent messages routed through ConciergeAiService
- Outbound messages via Twilio SDK

### 12.6 Dashboard Service

Aggregates stats per hotel:
- Active conversations count
- Total conversations today
- Pending escalations count
- Pending housekeeping count
- Average sentiment score
- Sentiment distribution map
- Recent conversations (top 10)
- Active escalations (top 10)

### 12.7 RAG Ingestion Service

- On startup: queries Weaviate to rebuild uploaded documents list
- Supports file upload (.txt, .md, .csv, .pdf, .docx)
- PDF/DOCX extraction via Apache Tika
- Duplicate detection by filename
- Classpath ingestion of 5 bundled RAG files
- Delete: paginated removal of all chunks by fileName

### 12.8 Global Exception Handler

Handles:
- `RuntimeException` → 400 Bad Request
- `AccessDeniedException` → 403 Forbidden
- `MethodArgumentNotValidException` → 400 with field errors
- `Exception` (catch-all) → 500 Internal Server Error

Response format: `{ timestamp, status, error, message }`

---

## 13. REPOSITORY CUSTOM QUERIES

### ConversationRepository
- `findBySessionId(sessionId)` - Find by unique session
- `findActiveByHotel(hotelId)` - WHERE status='ACTIVE' AND hotel matches
- `findEscalatedByHotel(hotelId)` - WHERE isEscalated=true AND status!='CLOSED'
- `countActiveByHotel(hotelId)` - Count active conversations
- `countConversationsSince(hotelId, since)` - Count since timestamp
- `averageSentimentSince(hotelId, since)` - AVG sentiment score

### ReservationRepository
- `findByConfirmationNumber(confirmationNumber)`
- `findActiveByGuestId(guestId)` - WHERE status IN (CONFIRMED, CHECKED_IN)
- `findCurrentGuestsByHotel(hotelId, date)` - Checked-in on date
- `findCheckedInByHotel(hotelId)` - All checked-in
- `countEligibleForLateCheckout(hotelId, date)` - Checkout today, not approved

### EscalationTicketRepository
- `findActiveByHotel(hotelId)` - WHERE status IN (OPEN, IN_PROGRESS), ordered by priority DESC
- `countOpenByHotel(hotelId)` - Count OPEN tickets

### HousekeepingRequestRepository
- `findPendingByHotel(hotelId)` - WHERE status IN (PENDING, ASSIGNED, IN_PROGRESS)
- `countPendingByHotel(hotelId)` - Count PENDING
- `findByReservationId(reservationId)`

### ChatMessageRepository
- `findBySessionIdOrdered(sessionId)` - Messages by session, ASC
- `findRecentByConversation(conversationId)` - Messages by conversation, DESC

### SpaBookingRepository
- `findByReservationId(reservationId)`
- `findConfirmedByDateAndHotel(date, hotelId)` - Confirmed bookings for date

### GuestRepository
- `findByEmail(email)`
- `findByPhone(phone)`

### HotelRepository
- `findByActiveTrue()`
- `findByName(name)`

### AppUserRepository
- `findByUsername(username)`
- `existsByUsername(username)`

---

## 14. AI PROMPTS (resources/prompts/)

### 14.1 concierge-system-prompt.txt

Defines the AI personality as an elite luxury hotel concierge named "Your Concierge". Key rules:
- Warm, professional, anticipate needs
- Use guest name naturally
- Capabilities: housekeeping, spa, restaurant, late checkout, recommendations, transport, special occasions
- Escalation triggers: explicit request for human, 3+ negative messages, safety concerns, billing disputes, medical emergencies
- Keep responses under 200 words unless detailed info requested
- Default English, switch to guest's preferred language

### 14.2 sentiment-analysis-prompt.txt

Scoring guide: -1.0 to 1.0 mapped to VERY_NEGATIVE through VERY_POSITIVE. Escalation triggers include threats of legal action, health/safety issues, extreme profanity, repeated complaints, requests for management, social media threats.

### 14.3 escalation-classifier-prompt.txt

5 classification levels: NO_ESCALATION, MONITOR, SOFT_ESCALATION, HARD_ESCALATION, EMERGENCY. Each with specific criteria and decision rules.

### 14.4 recommendation-engine-prompt.txt

Generates personalized recommendations across 6 categories (dining, spa, attractions, shopping, entertainment, day trips). Considers loyalty tier, dietary restrictions, special occasions, time of day, weather, stay duration.

---

## 15. RAG KNOWLEDGE BASE (resources/rag/)

### 15.1 hotel-faqs.txt
15 Q&A pairs covering: check-in/out times, late checkout fees, cancellation policy, parking, business center, WiFi, airport shuttle, pool hours, pets, gym, restaurant locations, room service, dress code, extra towels, concierge availability.

### 15.2 amenities.txt
Comprehensive listing: room amenities (65" TV, Nespresso, L'Occitane, Dyson), hotel facilities (rooftop pool, 24/7 gym, spa, business center, EV charging), dining (4 restaurants + room service), meeting/events (ballroom 500 guests, 6 meeting rooms), wellness (yoga, meditation, personal training), family amenities, accessibility.

### 15.3 policies.txt
Hotel policies for: check-in/out, cancellation, payment, smoking ($500 fee), pets (under 25 lbs, $150), noise, pool/spa, safety, loyalty tiers (Silver/Gold/Platinum benefits), damage, lost & found.

### 15.4 restaurant-menus.txt
Full menus with prices for: The Grand Brasserie (breakfast $22-45, lunch $18-45, dinner $38-150), Sky Lounge (cocktails $20-28, small plates $18-95), Sakura Japanese (sushi $28-120, hot dishes $28-180, sake $15-45), Room Service (24h, $8 delivery fee waived for suites). Dietary accommodations listed.

### 15.5 spa-services.txt
Complete spa menu: massage therapies ($150-350), facial treatments ($90-180), body treatments ($130-190), packages ($380-800), wellness facilities (complimentary sauna/steam/whirlpool), fitness classes, spa etiquette, products.

---

## 16. ENVIRONMENT VARIABLES

```
OPENAI_API_KEY=sk-your-openai-api-key-here
DB_USERNAME=concierge_user
DB_PASSWORD=concierge_pass
JWT_SECRET=YWktY29uY2llcmdlLXNlcnZpY2Utc2VjcmV0LWtleS1mb3ItaG90ZWwtcGxhdGZvcm0tMjAyNA==
WEAVIATE_HOST=localhost
WEAVIATE_PORT=8081
QR_BASE_URL=http://localhost:9090
TWILIO_ACCOUNT_SID=
TWILIO_AUTH_TOKEN=
TWILIO_WHATSAPP_NUMBER=+14155238886
```

---

## 17. WEAVIATE VECTOR STORE

### Schema
- Class: `HotelDocumentChunk`
- Vectorizer: `none` (embeddings provided by application)
- Properties: content (text), fileName (text), category (text), chunkIndex (int), uploadTime (text)

### Categories
- faq, amenities, policies, menu, spa, general

### Chunking Strategy
- Recursive splitter: 500 characters, 50 character overlap
- Embedding model: OpenAI text-embedding-3-small

### Search
- nearVector query with top-k=5
- Results concatenated as RAG context injected into user message

---

## 18. WHATSAPP INTEGRATION (Twilio)

### Flow
1. Guest sends `TOKEN:<qr-token>` to Twilio WhatsApp number
2. Twilio POSTs to `/webhook/whatsapp` with From, Body, ProfileName
3. Backend validates token, creates session, returns welcome message
4. Subsequent messages routed through AI concierge
5. Response returned as TwiML XML

### Session Management
- In-memory ConcurrentHashMap: phone → sessionId, phone → Reservation
- Session persists until app restart

### Outbound Messages
- Uses `com.twilio.rest.api.v2010.account.Message.creator()`
- Skipped if Twilio credentials not configured

---

## 19. CODING CONVENTIONS

- **Package structure:** `com.hotel.concierge.{layer}`
- **Entities:** `@Entity` + `@Data` + `@NoArgsConstructor` + `@AllArgsConstructor` + `@Builder`
- **Repositories:** Extend `JpaRepository<Entity, Long>`, annotated `@Repository`
- **Controllers:** `@RestController` + `@RequestMapping("/{path}")` + `@RequiredArgsConstructor` + `@Tag` (OpenAPI)
- **Services:** `@Service` + `@RequiredArgsConstructor` + `@Slf4j`
- **DTOs:** `@Data` + `@NoArgsConstructor` + `@AllArgsConstructor` + `@Builder`
- **Validation:** Jakarta `@NotBlank`, `@Valid` on request bodies
- **Lazy loading:** `FetchType.LAZY` on all `@ManyToOne` relationships
- **JSON:** `@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})` on lazy entities
- **Timestamps:** `@PrePersist` / `@PreUpdate` lifecycle callbacks (not DB defaults in JPA)
- **Enums:** Defined as inner classes within entities, stored as `@Enumerated(EnumType.STRING)`

---

## 20. COMMANDS REFERENCE

```bash
# Start infrastructure
docker-compose up mysql weaviate -d

# Run application
mvn spring-boot:run

# Run tests
mvn test
mvn verify
mvn test -Dtest=ClassName

# Package
mvn package -DskipTests

# Full Docker deployment
docker-compose up --build -d

# Health check
curl http://localhost:9090/api/health

# Initialize database manually
mysql -u concierge_user -pconcierge_pass hotel_concierge < src/main/resources/db/schema.sql
mysql -u concierge_user -pconcierge_pass hotel_concierge < src/main/resources/db/seed-data.sql
```

---

## 21. GITIGNORE

```
*.class, *.jar, *.war, *.ear, target/
.idea/, *.iml, .vscode/, .settings/, .project, .classpath
.DS_Store, Thumbs.db
.env, *.env.local
*.log, logs/
docker-compose.override.yml
node_modules/
build/, dist/, out/
```

---

## END OF SPECIFICATION

This document contains all information needed to regenerate the AI Concierge Service project from scratch, including:
- Complete database schema with all 11 tables
- All JPA entity definitions with field types, relationships, and enums
- All repository interfaces with custom queries
- All controller endpoints with request/response shapes
- All service implementations with business logic
- Complete AI/LLM configuration (LangChain4J, tools, RAG pipeline)
- Full frontend SPA (HTML structure, CSS theme, JavaScript logic)
- Infrastructure (Docker, Kubernetes, CI/CD)
- All AI prompts and RAG knowledge base content
- Security configuration (JWT, rate limiting, CORS)
- Seed data for demo/testing
