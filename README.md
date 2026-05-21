# 🏨 AI Concierge Service - Enterprise Hotel Platform

An enterprise-grade AI-powered concierge platform for luxury hotels, built with Spring Boot 3, LangChain4J, and OpenAI.

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    FRONTEND (HTML/CSS/JS)                     │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
│  │QR Onboard│  │Guest Chat│  │Dashboard │  │Escalation│   │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘   │
└─────────────────────────┬───────────────────────────────────┘
                          │ REST / WebSocket
┌─────────────────────────┴───────────────────────────────────┐
│                 SPRING BOOT 3 BACKEND                         │
│  ┌────────────┐  ┌────────────┐  ┌────────────────────┐    │
│  │Auth Service│  │Chat Control│  │Dashboard Controller│    │
│  └────────────┘  └────────────┘  └────────────────────┘    │
│  ┌────────────────────────────────────────────────────┐     │
│  │              LangChain4J AI Layer                    │     │
│  │  ┌──────────┐ ┌─────┐ ┌──────────┐ ┌──────────┐  │     │
│  │  │Chat Model│ │ RAG │ │Tool Calls│ │  Memory  │  │     │
│  │  └──────────┘ └─────┘ └──────────┘ └──────────┘  │     │
│  └────────────────────────────────────────────────────┘     │
│  ┌────────────┐  ┌────────────┐  ┌────────────────────┐    │
│  │QR Service  │  │WhatsApp Svc│  │Sentiment Analysis │    │
│  └────────────┘  └────────────┘  └────────────────────┘    │
└──────────┬──────────────────┬───────────────────────────────┘
           │                  │
    ┌──────┴──────┐    ┌──────┴──────┐
    │   MySQL     │    │  Weaviate   │
    │(Reservations│    │ (Embeddings/│
    │ Guests, etc)│    │    RAG)     │
    └─────────────┘    └─────────────┘
```

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 21 |
| Framework | Spring Boot 3.2.5 |
| AI/LLM | LangChain4J + OpenAI GPT-4o |
| Vector DB | Weaviate |
| Database | MySQL 8.0 |
| Auth | JWT (jjwt) |
| QR Code | ZXing |
| WhatsApp | Twilio API |
| Build | Maven |
| Container | Docker + docker-compose |
| Orchestration | Kubernetes |
| CI/CD | GitHub Actions |

## Features

### Guest Experience
- 🤖 AI-powered multilingual concierge
- 📱 QR code onboarding (scan → chat)
- 💬 WhatsApp integration via Twilio
- 🛎️ Housekeeping request management
- 💆 Spa booking
- 🍽️ Restaurant reservations
- 🕐 Late checkout requests
- 🗺️ Nearby attraction recommendations
- 🎯 Intelligent upselling

### Admin Dashboard
- 📊 Real-time conversation monitoring
- 😊 Guest sentiment analysis
- 🚨 Auto-escalation alerts
- 🧹 Housekeeping ticket management
- 📈 Upsell analytics

### Security
- 🔐 JWT authentication
- 👥 Role-based access control
- 🔒 PII masking
- 📝 Audit logging
- ⏱️ API rate limiting

## Quick Start

### Prerequisites
- Java 21+
- Maven 3.9+
- Docker & Docker Compose
- OpenAI API Key

### Local Development

1. **Clone and configure:**
```bash
git clone <repository-url>
cd ai-concierge-service
```

2. **Set environment variables:**
```bash
export OPENAI_API_KEY=your-openai-api-key
export JWT_SECRET=YWktY29uY2llcmdlLXNlcnZpY2Utc2VjcmV0LWtleS1mb3ItaG90ZWwtcGxhdGZvcm0tMjAyNA==
```

3. **Start infrastructure with Docker:**
```bash
docker-compose up mysql weaviate -d
```

4. **Initialize database:**
```bash
mysql -u concierge_user -pconcierge_pass hotel_concierge < backend/src/main/resources/db/schema.sql
mysql -u concierge_user -pconcierge_pass hotel_concierge < backend/src/main/resources/db/seed-data.sql
```

5. **Run the application:**
```bash
cd backend
mvn spring-boot:run
```

6. **Access the application:**
- Frontend: http://localhost:8080/api/static/index.html
- Swagger UI: http://localhost:8080/api/swagger-ui.html
- API Docs: http://localhost:8080/api/v3/api-docs

### Full Docker Deployment

```bash
# Set your OpenAI API key
export OPENAI_API_KEY=your-key-here

# Start all services
docker-compose up --build -d

# Check health
curl http://localhost:8080/api/health
```

### Running Tests

```bash
cd backend
mvn test                    # Unit tests
mvn verify                  # Integration tests
mvn test -Dtest=JwtTokenProviderTest  # Specific test
```

## API Endpoints

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | /api/auth/login | Admin login | No |
| GET | /api/chat/guest?token= | Init guest chat | QR Token |
| POST | /api/chat/message | Send chat message | JWT |
| POST | /api/qr/generate/{id} | Generate QR code | Admin |
| GET | /api/qr/validate?token= | Validate QR token | No |
| GET | /api/dashboard/stats/{hotelId} | Dashboard stats | Admin |
| POST | /api/webhook/whatsapp | Twilio webhook | No |
| GET | /api/admin/escalations/hotel/{id} | Get escalations | Admin |

## Project Structure

```
ai-concierge-service/
├── backend/
│   └── src/main/java/com/hotel/concierge/
│       ├── config/          # Spring configurations
│       ├── controller/      # REST controllers
│       ├── dto/             # Data transfer objects
│       ├── exception/       # Global exception handling
│       ├── model/           # JPA entities
│       ├── repository/      # Data access layer
│       ├── security/        # JWT auth & filters
│       └── service/
│           ├── ai/          # LangChain4J AI service
│           │   └── tools/   # AI tool implementations
│           ├── qr/          # QR code generation
│           ├── sentiment/   # Sentiment analysis
│           ├── whatsapp/    # Twilio integration
│           └── notification/# WebSocket notifications
├── frontend/
│   ├── css/                 # Luxury hotel styling
│   ├── js/                  # Application logic
│   └── index.html           # Main SPA
├── docker/                  # Dockerfile
├── k8s/                     # Kubernetes manifests
├── docs/                    # Documentation
└── docker-compose.yml       # Local development stack
```

## Demo Credentials

| Username | Password | Role |
|----------|----------|------|
| admin | admin123 | ADMIN |
| manager.smith | admin123 | MANAGER |
| frontdesk.jones | admin123 | FRONT_DESK |

## Configuration

Key environment variables:

| Variable | Description | Default |
|----------|-------------|---------|
| OPENAI_API_KEY | OpenAI API key | demo |
| DB_USERNAME | MySQL username | concierge_user |
| DB_PASSWORD | MySQL password | concierge_pass |
| JWT_SECRET | JWT signing key | (built-in) |
| WEAVIATE_HOST | Weaviate host | localhost |
| TWILIO_ACCOUNT_SID | Twilio SID | (empty) |
| TWILIO_AUTH_TOKEN | Twilio token | (empty) |

## License

Proprietary - All rights reserved.
