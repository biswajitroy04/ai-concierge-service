# Architecture Documentation

## System Architecture

### High-Level Flow

```
Guest Journey:
1. PMS creates reservation → MySQL stores data
2. Front desk generates QR code → JWT token embedded
3. Guest scans QR → Token validated → Chat session created
4. Guest sends message → LangChain4J processes with RAG + Tools
5. AI responds with context-aware, personalized assistance
6. Sentiment monitored → Auto-escalation if needed
```

### Component Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                         CLIENT LAYER                             │
├─────────────┬──────────────┬──────────────┬────────────────────┤
│  Web Chat   │  QR Scanner  │  Admin Panel │  WhatsApp (Twilio) │
└──────┬──────┴──────┬───────┴──────┬───────┴────────┬───────────┘
       │             │              │                │
       └─────────────┴──────────────┴────────────────┘
                              │
                    ┌─────────┴─────────┐
                    │   API Gateway     │
                    │  (Rate Limiting)  │
                    └─────────┬─────────┘
                              │
┌─────────────────────────────┴───────────────────────────────────┐
│                      SERVICE LAYER                                │
├──────────────────────────────────────────────────────────────────┤
│                                                                   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │ Auth Service │  │ Chat Service │  │ Dashboard Service    │  │
│  │ (JWT/RBAC)  │  │ (WebSocket)  │  │ (Real-time stats)    │  │
│  └──────────────┘  └──────┬───────┘  └──────────────────────┘  │
│                            │                                      │
│  ┌─────────────────────────┴─────────────────────────────────┐  │
│  │              AI ORCHESTRATION (LangChain4J)                 │  │
│  │  ┌────────────┐ ┌──────────┐ ┌────────┐ ┌─────────────┐  │  │
│  │  │ Chat Model │ │   RAG    │ │ Tools  │ │   Memory    │  │  │
│  │  │ (GPT-4o)   │ │(Weaviate)│ │(5 tools│ │(20 msg win) │  │  │
│  │  └────────────┘ └──────────┘ └────────┘ └─────────────┘  │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │  QR Service  │  │  Sentiment   │  │  Notification Svc    │  │
│  │  (ZXing+JWT) │  │  Analysis    │  │  (WebSocket push)    │  │
│  └──────────────┘  └──────────────┘  └──────────────────────┘  │
│                                                                   │
│  ┌──────────────┐  ┌──────────────┐                             │
│  │  WhatsApp    │  │  Audit Log   │                             │
│  │  (Twilio)    │  │  (AOP)       │                             │
│  └──────────────┘  └──────────────┘                             │
└──────────────────────────────┬───────────────────────────────────┘
                               │
              ┌────────────────┼────────────────┐
              │                │                │
     ┌────────┴────────┐ ┌────┴─────┐ ┌───────┴───────┐
     │     MySQL       │ │ Weaviate │ │   OpenAI API  │
     │ (Reservations,  │ │ (Vector  │ │  (GPT-4o +    │
     │  Guests, Chats) │ │  Store)  │ │  Embeddings)  │
     └─────────────────┘ └──────────┘ └───────────────┘
```

### AI Tools Architecture

```
ConciergeTools (LangChain4J @Tool annotations)
├── createHousekeepingRequest()  → MySQL housekeeping_requests
├── bookSpaAppointment()         → MySQL spa_bookings
├── checkLateCheckoutEligibility() → Business logic + reservation data
├── recommendNearbyAttractions() → RAG + location context
└── escalateToHumanAgent()       → escalation_tickets + WebSocket notify
```

### RAG Pipeline

```
Document Ingestion (on startup):
  hotel-faqs.txt ─────┐
  amenities.txt ──────┤
  policies.txt ───────┼──→ DocumentSplitter (500 chars, 50 overlap)
  restaurant-menus.txt┤         │
  spa-services.txt ───┘         ▼
                         OpenAI Embeddings (text-embedding-3-small)
                                │
                                ▼
                         Weaviate Vector Store
                         (HotelKnowledge class)

Query Flow:
  User Message → Embedding → Weaviate Similarity Search (top 5, min 0.7)
       │                              │
       └──────────────────────────────┘
                    │
                    ▼
            LangChain4J combines:
            [System Prompt + RAG Context + Chat History + User Message]
                    │
                    ▼
              GPT-4o Response (with optional tool calls)
```

### Security Architecture

```
┌─────────────────────────────────────────┐
│           Security Layers                │
├─────────────────────────────────────────┤
│ 1. Rate Limiting (Bucket4j)             │
│    - 60 requests/minute per IP          │
│    - Burst: 10 requests/second          │
├─────────────────────────────────────────┤
│ 2. JWT Authentication                    │
│    - Admin tokens (24h expiry)          │
│    - QR tokens (7 day expiry)           │
│    - HMAC-SHA256 signing                │
├─────────────────────────────────────────┤
│ 3. Role-Based Access Control            │
│    - ADMIN: Full access                 │
│    - MANAGER: Dashboard + admin ops     │
│    - FRONT_DESK: QR generation + view   │
│    - CONCIERGE: Dashboard view          │
│    - GUEST: Chat only (via QR token)    │
├─────────────────────────────────────────┤
│ 4. Audit Logging (AOP)                  │
│    - All POST/PUT/DELETE operations      │
│    - User, role, IP, timestamp          │
├─────────────────────────────────────────┤
│ 5. PII Masking                          │
│    - Guest emails/phones in logs        │
│    - Credit card data never stored      │
└─────────────────────────────────────────┘
```

### Database Schema (ER Diagram)

```
hotels ──────────< reservations >────────── guests
                       │
          ┌────────────┼────────────┐
          │            │            │
   conversations  spa_bookings  housekeeping_requests
          │                        restaurant_bookings
          │
   ┌──────┴──────┐
   │             │
chat_messages  escalation_tickets

app_users ──────── hotels
audit_logs (standalone)
```

### Deployment Architecture (Kubernetes)

```
┌─────────────────────────────────────────────────┐
│              Kubernetes Cluster                   │
├─────────────────────────────────────────────────┤
│  Namespace: hotel-concierge                      │
│                                                  │
│  ┌─────────────────────────────────────────┐    │
│  │  Deployment: ai-concierge (3 replicas)  │    │
│  │  ┌─────┐ ┌─────┐ ┌─────┐              │    │
│  │  │Pod 1│ │Pod 2│ │Pod 3│              │    │
│  │  └─────┘ └─────┘ └─────┘              │    │
│  └─────────────────────────────────────────┘    │
│                     │                            │
│  ┌──────────────────┴──────────────────────┐    │
│  │  Service: ai-concierge-service (ClusterIP)│   │
│  └──────────────────┬──────────────────────┘    │
│                     │                            │
│  ┌──────────────────┴──────────────────────┐    │
│  │  Ingress (TLS termination + rate limit) │    │
│  └─────────────────────────────────────────┘    │
│                                                  │
│  ConfigMap: concierge-config                     │
│  Secret: concierge-secrets                       │
└─────────────────────────────────────────────────┘
```
