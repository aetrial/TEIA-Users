# TEIA-Users

Traffic controller and credit gatekeeper for the TEIA voice AI platform. Receives all Twilio webhooks, validates clients, manages credits, and forwards authorized requests to TEIA Core.

## Architecture Overview

**TEIA-users is the single entry point for all external traffic.** Twilio webhooks, operator APIs, and client requests all route through this service first.

```
Traffic Flow:

                           +------------------+
                           |     Twilio       |
                           | (calls/messages) |
                           +--------+---------+
                                    |
                                    v
+----------------+         +-----------------+         +-------------+
|   Operators    | ------> |   TEIA-users    | ------> |  TEIA Core  |
| (config/admin) |         | (Java/Spring)   |         | (Node.js)   |
+----------------+         +--------+--------+         +------+------+
                                    |                         |
                                    |  Responsibilities:      |  Responsibilities:
                                    |  - Twilio webhooks      |  - Audio streaming
                                    |  - Credit validation    |  - ElevenLabs AI
                                    |  - Session management   |  - OpenAI processing
                                    |  - Token generation     |  - Session callbacks
                                    v                         v
                             +-------------+            +-------------+
                             |  Database   |            |   Twilio    |
                             | (Postgres/  |            | Media Stream|
                             |    H2)      |            +-------------+
                             +-------------+

Webhook Flow (Inbound Call):

  1. Twilio --> POST /twilio/inbound/call --> TEIA-users
  2. TEIA-users: lookup client by phone number
  3. TEIA-users: check credit balance (reject if insufficient)
  4. TEIA-users: reserve credits, create session, generate token
  5. TEIA-users --> TwiML <Connect><Stream> --> Twilio
  6. Twilio --> WebSocket /media-stream --> TEIA Core
  7. TEIA Core: validate token, process audio, stream to ElevenLabs
  8. On disconnect: TEIA Core --> POST /internal/session/complete --> TEIA-users
  9. TEIA-users: reconcile credits (refund unused, charge overage)
```

**Key Responsibilities:**

| This Service (TEIA-users) | TEIA Core (downstream) |
|---------------------------|------------------------|
| Receives Twilio webhooks | Does not receive external traffic |
| Client identification | Trusts client ID from TEIA-users |
| Credit balance checks | No credit logic |
| Session token generation | Session token validation |
| Returns TwiML to Twilio | Handles WebSocket audio |
| Credit reconciliation | Reports session completion |

---

## Stack

- **Runtime:** Java 17
- **Framework:** Spring Boot 2.7.18
- **Build:** Maven
- **ORM:** Spring Data JPA
- **Security:** Spring Security
- **Docs:** Swagger / OpenAPI

## Quick Start

```bash
# Run the application
./mvnw spring-boot:run

# Build
./mvnw clean package

# Run tests
./mvnw test
```

Default port: `8080`

## Local Dev Setup

**One command startup (uses H2 in-memory database, no external dependencies):**

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

The `local` profile:
- Uses H2 in-memory database (no PostgreSQL needed)
- Disables Twilio signature validation (test webhooks without real Twilio)
- Uses local dev secrets (matches TEIA Core's `.env.example`)
- Enables H2 Console for debugging

**Prerequisites:**
- Java 17+

**That's it.** No database setup, no environment variables, no configuration.

The app will be available at: `http://localhost:8099/teiaUsers`
Swagger UI: `http://localhost:8099/teiaUsers/swagger-ui.html`
H2 Console: `http://localhost:8099/teiaUsers/h2-console` (JDBC URL: `jdbc:h2:mem:teiadb`)

### Alternative: Production-like Setup

For testing with PostgreSQL (closer to production):

```bash
# 1. Start PostgreSQL
docker run -d --name teia-pg -e POSTGRES_PASSWORD=password -e POSTGRES_DB=teiacore -p 5432:5432 postgres:15

# 2. Create .env with required secrets
cat > .env << 'EOF'
JWT_SECRET=teia-local-dev-jwt-secret-change-in-prod-abc123
ENCRYPTION_KEY=K7gNU3sdo+OL0wNhqoVWhr3g6s1xYv72ol/pe/Unols=
INTERNAL_SECRET=teia-local-dev-secret-change-in-prod
EOF

# 3. Export and run (no profile = uses PostgreSQL from application.properties)
export $(grep -v '^#' .env | xargs)
./mvnw spring-boot:run
```

> **Note:** For the full two-service test flow, see `../LOCAL_TESTING.md`.

## API Documentation

Full API reference: **[API_DOCUMENTATION.md](./API_DOCUMENTATION.md)**

- **Swagger UI:** http://localhost:8099/teiaUsers/swagger-ui.html
- **OpenAPI JSON:** http://localhost:8099/teiaUsers/api-docs

### Endpoints Summary

| Controller | Path | Endpoints | Purpose |
|------------|------|-----------|---------|
| Users | `/users` | 8 | User CRUD + contact management |
| Contacts | `/contacts` | 5 | Contact CRUD |
| ElevenLabs | `/eleven-labs-info` | 5 | Voice AI credentials |
| Twilio | `/twilio-info` | 5 | Telephony credentials |
| OpenAI | `/openai-info` | 5 | Assistant credentials |
| Cal.com | `/cal-info` | 5 | Calendar integration |
| Threads | `/threads` | 6 | Conversation thread tracking |
| Operator | `/operator` | 4 | Client config management (OPERATOR/ADMIN only) |
| Metrics | `/metrics` | 4 | Usage metrics (calls, messages, credits, over-time) |
| Provider Metrics | `/metrics/providers` | 5 | Per-provider consumption (Twilio, OpenAI, ElevenLabs, Stripe) |

**Total: 57 endpoints across 10 controllers**

---

## Operator API (Client Configuration)

The Operator API provides centralized management of client configurations. These endpoints are restricted to users with `OPERATOR` or `ADMIN` roles.

### ClientConfig Resource

`ClientConfig` stores all third-party service credentials for a TEIA client in a single entity. Sensitive fields are encrypted at rest using AES-256-GCM.

| Field | Type | Sensitive | Description |
|-------|------|-----------|-------------|
| `twilioAccountSid` | String | No | Twilio account identifier |
| `twilioAuthToken` | String | **Yes** | Twilio auth token (encrypted) |
| `twilioCallNumber` | String | No | Twilio phone number for calls |
| `twilioWhatsappNumber` | String | No | Twilio phone number for WhatsApp |
| `elevenLabsApiKey` | String | **Yes** | ElevenLabs API key (encrypted) |
| `elevenLabsAgentId` | String | No | ElevenLabs voice agent ID |
| `openAiApiKey` | String | **Yes** | OpenAI API key (encrypted) |
| `openAiAssistantId` | String | No | OpenAI assistant ID |
| `calApiKey` | String | **Yes** | Cal.com API key (encrypted) |
| `calUserId` | String | No | Cal.com user ID |
| `calEventId` | String | No | Cal.com event type ID |

**Sensitive field masking:** When retrieving configurations via GET endpoints, sensitive fields are masked in the response (e.g., `****abcd`). The full values are never exposed through the API.

### Operator Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/operator/clients` | List all clients with config status and credit balance |
| `POST` | `/operator/clients/{clientId}/config` | Create or overwrite full configuration for a client |
| `GET` | `/operator/clients/{clientId}/config` | Retrieve configuration (sensitive fields masked) |
| `PATCH` | `/operator/clients/{clientId}/config` | Partial update (only provided fields are changed) |

**Required roles:** `OPERATOR` or `ADMIN`

### Example Requests

```bash
# List all clients with config status
curl -X GET http://localhost:8099/teiaUsers/operator/clients \
  -H "Authorization: Bearer <access_token>"

# Create or update full config
curl -X POST http://localhost:8099/teiaUsers/operator/clients/123/config \
  -H "Authorization: Bearer <access_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "twilioAccountSid": "AC...",
    "twilioAuthToken": "your-auth-token",
    "twilioCallNumber": "+15551234567",
    "elevenLabsApiKey": "your-eleven-labs-key",
    "elevenLabsAgentId": "agent-id",
    "openAiApiKey": "sk-...",
    "openAiAssistantId": "asst_..."
  }'

# Get config (sensitive fields will be masked)
curl -X GET http://localhost:8099/teiaUsers/operator/clients/123/config \
  -H "Authorization: Bearer <access_token>"

# Partial update (only update specific fields)
curl -X PATCH http://localhost:8099/teiaUsers/operator/clients/123/config \
  -H "Authorization: Bearer <access_token>" \
  -H "Content-Type: application/json" \
  -d '{"elevenLabsAgentId": "new-agent-id"}'
```

## Billing API (Stripe Integration)

TEIA-users supports credit purchasing via Stripe Checkout. Clients buy credit bundles that are consumed during voice AI sessions.

### Required Environment Variables

| Variable | Description |
|----------|-------------|
| `STRIPE_SECRET_KEY` | Your Stripe API key (`sk_test_...` for test, `sk_live_...` for production) |
| `STRIPE_WEBHOOK_SECRET` | Webhook signing secret (`whsec_...`) from Stripe Dashboard or Stripe CLI |

### Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/billing/checkout` | JWT (CLIENT) | Create Stripe checkout session |
| `POST` | `/billing/webhook` | Stripe Signature | Handle Stripe payment events |
| `GET` | `/billing/history` | JWT (CLIENT) | Get transaction history |

### Credit Bundles

| Bundle ID | Credits | Price (USD) |
|-----------|---------|-------------|
| `BUNDLE_100` | 100 | $10.00 |
| `BUNDLE_500` | 500 | $40.00 |
| `BUNDLE_1000` | 1000 | $70.00 |

### Example: Create Checkout Session

```bash
# First, get a JWT token for a CLIENT role user
TOKEN=$(curl -s -X POST http://localhost:8099/teiaUsers/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "client@example.com", "password": "password123"}' \
  | jq -r '.accessToken')

# Create a checkout session
curl -X POST http://localhost:8099/teiaUsers/billing/checkout \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"bundleId": "BUNDLE_100"}'
```

**Response:**
```json
{
  "checkoutUrl": "https://checkout.stripe.com/pay/cs_test_..."
}
```

Open the `checkoutUrl` in a browser to complete payment (use Stripe test card `4242 4242 4242 4242`).

### Local Testing with Stripe CLI

The Stripe CLI lets you forward real Stripe webhook events to your local server.

**1. Install Stripe CLI**

```bash
# macOS
brew install stripe/stripe-cli/stripe

# Or download from https://stripe.com/docs/stripe-cli
```

**2. Forward webhooks to local server**

```bash
stripe listen --forward-to localhost:8099/teiaUsers/billing/webhook
```

The CLI will print a webhook signing secret like:
```
> Ready! Your webhook signing secret is whsec_abc123... (^C to quit)
```

**3. Update application-local.properties**

Copy the `whsec_...` value and update your local properties:

```properties
stripe.webhook-secret=whsec_abc123...
stripe.secret-key=sk_test_your_actual_test_key
```

**4. Trigger a test event**

```bash
stripe trigger checkout.session.completed
```

### Complete Local End-to-End Test

```bash
# Terminal 1 — start TEIA-users with local profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# Terminal 2 — start Stripe CLI webhook forwarding
stripe listen --forward-to localhost:8099/teiaUsers/billing/webhook
# Copy the whsec_... secret it prints, update application-local.properties, restart Terminal 1

# Terminal 3 — run the test flow

# 1. Create a test user in H2 Console (http://localhost:8099/teiaUsers/h2-console)
#    JDBC URL: jdbc:h2:mem:teiadb  User: sa  Password: (empty)
#    Run the SQL from the "Create Test Data" section below with role = 'CLIENT'

# 2. Get a JWT token
TOKEN=$(curl -s -X POST http://localhost:8099/teiaUsers/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "test@example.com", "password": "password123"}' \
  | jq -r '.accessToken')

# 3. Create a checkout session
CHECKOUT_URL=$(curl -s -X POST http://localhost:8099/teiaUsers/billing/checkout \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"bundleId": "BUNDLE_100"}' \
  | jq -r '.checkoutUrl')

echo "Visit: $CHECKOUT_URL"
# Or trigger via CLI:
stripe trigger checkout.session.completed

# 4. Verify credits were added
curl -X GET http://localhost:8099/teiaUsers/billing/history \
  -H "Authorization: Bearer $TOKEN"
```

---

## Metrics API

Clients can query their own usage data. OPERATOR/ADMIN roles can view any client's data by adding `?clientId=<uuid>`.

### Usage Metrics Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/metrics/summary` | JWT | Total calls, messages, credits used, current balance, active sessions |
| `GET` | `/metrics/calls?from=&to=` | JWT | Per-call breakdown with duration, credits, status |
| `GET` | `/metrics/messages?from=&to=` | JWT | Per-message breakdown with credits and status |
| `GET` | `/metrics/usage-over-time?period=day\|week\|month` | JWT | Credits consumed grouped by time period |

### Provider Metrics Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/metrics/providers/summary?from=&to=` | JWT | Aggregated totals across Twilio, OpenAI, ElevenLabs, Stripe |
| `GET` | `/metrics/providers/twilio?from=&to=` | JWT | Per-session call minutes and SMS count |
| `GET` | `/metrics/providers/openai?from=&to=` | JWT | Per-session input/output token counts |
| `GET` | `/metrics/providers/elevenlabs?from=&to=` | JWT | Per-session characters synthesized |
| `GET` | `/metrics/providers/stripe` | JWT | Payment history merged with Stripe PaymentIntents |

### Access Control

| Role | Behavior |
|------|----------|
| `CLIENT` | Always sees only their own data. `?clientId=` for another client → **403** |
| `OPERATOR` | Can pass `?clientId=<uuid>` to view any client's data |
| `ADMIN` | Same as OPERATOR |

### Provider Summary Response

```json
{
  "twilio":     { "totalCallMinutes": 12.5, "totalSmsSent": 3 },
  "openAi":     { "totalInputTokens": 48200, "totalOutputTokens": 12100 },
  "elevenLabs": { "totalCharacters": 98400 },
  "stripe":     { "totalPaid": 4000, "currency": "usd", "lastPaymentAt": "2026-03-01T10:00:00Z" }
}
```

### Example Requests

```bash
# Get your own metrics summary
curl -X GET http://localhost:8099/teiaUsers/metrics/summary \
  -H "Authorization: Bearer $TOKEN"

# Get Twilio usage for a date range
curl -X GET "http://localhost:8099/teiaUsers/metrics/providers/twilio?from=2026-03-01&to=2026-03-31" \
  -H "Authorization: Bearer $TOKEN"

# OPERATOR viewing a client's provider summary
curl -X GET "http://localhost:8099/teiaUsers/metrics/providers/summary?clientId=abc-123" \
  -H "Authorization: Bearer $OPERATOR_TOKEN"

# View payment history
curl -X GET http://localhost:8099/teiaUsers/metrics/providers/stripe \
  -H "Authorization: Bearer $TOKEN"
```

---

### Database Migration

The Operator API requires the following schema changes:

1. **New table:** `client_config` - stores client configurations
2. **New column:** `credit_balance` on `teia_user` table (DECIMAL)

These are created automatically by JPA/Hibernate if `ddl-auto` is set to `update` or `create`. For production, run the appropriate migration scripts before deploying.

## Entity Relationships

```
TeiaUser (1)
  +-- (1) ElevenLabsInfo    [voice AI credentials]
  +-- (1) TwilioInfo        [telephony credentials]
  +-- (1) OpenAIInfo        [assistant credentials]
  +-- (1) CalInfo           [calendar integration]
  +-- (*) Contact           [user's contacts list]

Thread [standalone - tracks OpenAI conversation threads by phone number pair]
```

## Project Structure

```
src/main/java/com/teia/users/
  TeiaUsersApplication.java         -- Entry point
  controllers/                      -- REST endpoints
  services/                         -- Business logic
  repositories/                     -- Data access
  models/                           -- JPA entities
  config/                           -- Security, Swagger config
```

## Relationship to TEIA Core

This service is the **traffic controller and credit gatekeeper**. TEIA Core (`../TEIA/`) is the **pure execution engine**.

**TEIA-users provides to TEIA Core:**
- Pre-authorized session tokens
- Decrypted client credentials (OpenAI, ElevenLabs, Twilio, Cal.com)
- Session IDs for tracking

**TEIA Core provides back:**
- Session completion callbacks with actual duration
- Status (COMPLETED or FAILED)

**API Contract:**
- `/internal/call` - Forward inbound call with credentials
- `/internal/message` - Forward inbound message with credentials
- `/internal/outbound-ws` - Forward outbound WhatsApp template
- `/internal/outbound-call-np` - Forward outbound call request
- `/internal/session/complete` - Receive session completion callback (includes provider telemetry: `twilioCallDurationSeconds`, `twilioSmsCount`, `openAiInputTokens`, `openAiOutputTokens`, `elevenLabsCharacters`)
- `/internal/usage` - Receive direct usage report (deducts credits without prior reservation)
- `/internal/clients/{clientId}/credits` - Credit balance check

API contract changes must be coordinated between both projects.

## Authentication

TEIA-users uses JWT-based authentication with access and refresh tokens. All protected endpoints require a valid access token in the `Authorization` header.

### Auth Flow Overview

1. Client calls `POST /auth/login` with email and password
2. Server returns an access token (short-lived) and a refresh token (long-lived)
3. Client includes access token in subsequent requests: `Authorization: Bearer <access_token>`
4. When access token expires, client calls `POST /auth/refresh` with the refresh token to get a new access token
5. On logout, client calls `POST /auth/logout` to revoke the refresh token

### Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `JWT_SECRET` | **Yes** | - | Secret key for signing JWTs. Must be at least 32 characters (256 bits for HS256). Application fails to start if missing or too short. |
| `JWT_ACCESS_EXPIRY_MS` | No | `900000` (15 min) | Access token expiry in milliseconds |
| `JWT_REFRESH_EXPIRY_MS` | No | `604800000` (7 days) | Refresh token expiry in milliseconds |
| `REFRESH_TOKEN_CLEANUP_CRON` | No | `0 0 2 * * *` (daily 2 AM) | Cron expression for expired token cleanup job |

**Example:**
```bash
export JWT_SECRET="your-secure-random-string-at-least-32-chars"
export JWT_ACCESS_EXPIRY_MS=900000
export JWT_REFRESH_EXPIRY_MS=604800000
```

### Roles

Access control is role-based. The role is embedded in the JWT access token.

| Role | Description |
|------|-------------|
| `ADMIN` | Full access to all resources and operations |
| `OPERATOR` | Can manage contacts, threads, and credentials |
| `CLIENT` | Read-only access to own data |

### Auth Endpoints

All auth endpoints are publicly accessible (no token required).

#### POST /auth/login

Authenticate a user and receive tokens.

**Request:**
```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

**Response (200):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 900000
}
```

**Response (401):** Invalid credentials

#### POST /auth/refresh

Exchange a valid refresh token for a new access token.

**Request:**
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Response (200):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 900000
}
```

**Response (401):** Invalid or expired refresh token

#### POST /auth/logout

Revoke a refresh token. The token becomes invalid immediately.

**Request:**
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Response (200):** Empty body (logout successful)

### Access Token Claims

The access token JWT contains:

| Claim | Description |
|-------|-------------|
| `sub` | User ID (string) |
| `userId` | User ID (number) |
| `email` | User email |
| `role` | User role (`ADMIN`, `OPERATOR`, `CLIENT`) |
| `iss` | Issuer: `teia-users` |
| `iat` | Issued at timestamp |
| `exp` | Expiration timestamp |
| `jti` | Unique token ID |

### Refresh Token Storage

Refresh tokens are stored in the database and can be revoked server-side. This allows:

- Immediate logout (token revocation)
- Listing active sessions per user
- Forced logout of all sessions

Expired tokens are automatically cleaned up by a scheduled job (default: daily at 2:00 AM).

### Example curl Commands

```bash
# Login
curl -X POST http://localhost:8099/teiaUsers/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "user@example.com", "password": "password123"}'

# Access a protected endpoint
curl -X GET http://localhost:8099/teiaUsers/users \
  -H "Authorization: Bearer <access_token>"

# Refresh the access token
curl -X POST http://localhost:8099/teiaUsers/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken": "<refresh_token>"}'

# Logout
curl -X POST http://localhost:8099/teiaUsers/auth/logout \
  -H "Content-Type: application/json" \
  -d '{"refreshToken": "<refresh_token>"}'
```

## Inter-Service Authentication

TEIA-users uses HMAC-SHA256 authentication for all service-to-service communication with TEIA Core. This applies in both directions:
- **Inbound:** TEIA Core calling TEIA-users `/internal/**` endpoints
- **Outbound:** TEIA-users calling TEIA Core endpoints

### Inbound Requests (from TEIA Core)

The `InternalRequestFilter` protects all `/internal/**` endpoints. TEIA Core must sign requests using the shared `INTERNAL_SECRET`.

**Required Headers:**

| Header | Description |
|--------|-------------|
| `X-Internal-Key` | HMAC-SHA256(requestBody + timestamp, INTERNAL_SECRET) hex-encoded |
| `X-Timestamp` | Unix timestamp in milliseconds |
| `X-Client-ID` | TEIA Core instance identifier (UUID) |

**Signature Computation:**
```
signature = HMAC-SHA256(requestBody + timestamp, INTERNAL_SECRET)
```
- For POST/PUT requests: serialize body to JSON, concatenate with timestamp
- For GET/DELETE requests: use empty string + timestamp
- Signature is hex-encoded (lowercase)

**Validation:**
1. All three headers must be present
2. Timestamp must be within 30 seconds of server time (prevents replay attacks)
3. Signature must match the expected HMAC computation

**Response on Failure:** HTTP 401 with JSON error body

**Configuration:**
```properties
# application.properties
internal.secret=${INTERNAL_SECRET:teia-local-dev-secret-change-in-prod}
```

### Implementation Details

**Filter:** `com.teia.users.security.InternalRequestFilter`
- Extends `OncePerRequestFilter`
- Only applies to `/internal/**` paths (see `shouldNotFilter`)
- Uses `ContentCachingRequestWrapper` to read body for signature verification
- Uses `MessageDigest.isEqual` for constant-time signature comparison

**Security Config:** `/internal/**` is marked `permitAll()` in Spring Security because authentication is handled by `InternalRequestFilter`, not Spring Security's JWT filter.

## Outbound Requests (to TEIA Core)

TEIA-users can make outbound requests to TEIA Core (the Node.js voice platform). All requests are automatically signed with HMAC-SHA256 authentication.

### How It Works

The `TeiaCoreSigningInterceptor` automatically adds authentication headers to every outbound request:

| Header | Value |
|--------|-------|
| `X-Internal-Key` | HMAC-SHA256(requestBody + timestamp, INTERNAL_SECRET) |
| `X-Timestamp` | Unix timestamp in milliseconds |
| `X-Client-ID` | TeiaUser ID (passed by caller) |

### Configuration Properties

| Property | Env Var | Default | Description |
|----------|---------|---------|-------------|
| `teia.core.internal-secret` | `INTERNAL_SECRET` | `teia-local-dev-secret-change-in-prod` | Shared secret for HMAC signing. Must match TEIA Core. |
| `teia.core.base-url` | `TEIA_CORE_BASE_URL` | `http://localhost:8000` | Base URL of TEIA Core service |
| `teia.core.connect-timeout-ms` | `TEIA_CORE_CONNECT_TIMEOUT_MS` | `5000` | Connection timeout |
| `teia.core.read-timeout-ms` | `TEIA_CORE_READ_TIMEOUT_MS` | `30000` | Read timeout |

### Local Development

The default values work out of the box with TEIA Core's `.env.example`. Just run both services:

```bash
# Terminal 1 - TEIA Core
cd ../TEIA
cp .env.example .env
node index.js

# Terminal 2 - TEIA-users
./mvnw spring-boot:run
```

Both services will use `teia-local-dev-secret-change-in-prod` as the shared secret.

### Using TeiaCoreClient

Inject `TeiaCoreClient` into your service to call TEIA Core:

```java
@Service
public class MyService {

    private final TeiaCoreClient teiaCoreClient;

    public MyService(TeiaCoreClient teiaCoreClient) {
        this.teiaCoreClient = teiaCoreClient;
    }

    public void triggerCall(Long userId, CallRequest request) {
        // The client ID is the TeiaUser.id (Long)
        String clientId = userId.toString();

        ResponseEntity<CallResponse> response = teiaCoreClient.post(
            "/outbound-call-np",
            clientId,
            request,
            CallResponse.class
        );

        // Handle response...
    }
}
```

### Available Methods

| Method | Description |
|--------|-------------|
| `post(path, clientId, body, responseType)` | POST request with JSON body |
| `get(path, clientId, responseType)` | GET request |
| `put(path, clientId, body, responseType)` | PUT request with JSON body |
| `delete(path, clientId, responseType)` | DELETE request |

All methods:
- Automatically sign the request with HMAC
- Set `Content-Type: application/json`
- Include `X-Client-ID` header with the provided client ID
- Throw `RestClientException` on failure

### Production Configuration

In production, set the `INTERNAL_SECRET` environment variable to a strong, unique value:

```bash
export INTERNAL_SECRET="your-production-secret-here"
export TEIA_CORE_BASE_URL="https://teia-core.yourcompany.com"
```

The secret must match exactly between TEIA Core and TEIA-users.

## Production Deployment Checklist

### First deploy
- [ ] Generate a strong `JWT_SECRET` — `openssl rand -base64 32`
- [ ] Generate a strong `ENCRYPTION_KEY` — `openssl rand -base64 32`
- [ ] Generate a strong `INTERNAL_SECRET` — `openssl rand -base64 32` (must match TEIA Core)
- [ ] Store all secrets in a secrets manager (AWS Secrets Manager, Vault, etc.) — never in env files
- [ ] Set `TEIA_CORE_BASE_URL` to the production TEIA Core URL
- [ ] Back up the database before starting the service for the first time

### Credential encryption migration (first deploy only)
- [ ] Set `ENCRYPTION_MIGRATION_ENABLED=true` before starting
- [ ] Start the service — `DataMigrationRunner` will encrypt all existing plaintext credentials automatically
- [ ] Confirm in logs that migration completed successfully
- [ ] Set `ENCRYPTION_MIGRATION_ENABLED=false` (or remove the var) and redeploy

### Every deploy
- [ ] Confirm `JWT_SECRET`, `ENCRYPTION_KEY`, and `INTERNAL_SECRET` are set in the environment
- [ ] Confirm `ENCRYPTION_KEY` has NOT changed — changing it will break decryption of stored credentials
- [ ] Confirm `INTERNAL_SECRET` matches the value deployed to TEIA Core
- [ ] Verify the app starts and `/teiaUsers/actuator/health` (or equivalent) returns OK
- [ ] Confirm Swagger UI loads: `/teiaUsers/swagger-ui.html`

### Security reminders
- `ENCRYPTION_KEY` must never change after credentials are stored — rotate it only with a full re-encryption migration
- `INTERNAL_SECRET` must be identical on both TEIA-users and TEIA Core at all times
- Default local dev values (`teia-local-dev-*`) must never be used in production

---

## Twilio Webhook Endpoints

These endpoints receive webhooks from Twilio and route them to TEIA Core. All inbound webhooks validate Twilio's signature header unless disabled via configuration.

### Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/twilio/inbound/call` | Twilio Signature | Inbound call webhook - returns TwiML connecting to TEIA Core |
| `POST` | `/twilio/inbound/message` | Twilio Signature | Inbound SMS/WhatsApp webhook - forwards to TEIA Core |
| `POST` | `/twilio/outbound/call` | None (internal use) | Initiate outbound call via TEIA Core |
| `POST` | `/internal/session/complete` | Internal HMAC | Session completion callback from TEIA Core |

### Flow: Inbound Call

1. Twilio receives call to client's number
2. Twilio sends webhook to `POST /twilio/inbound/call`
3. TEIA-users validates signature (if enabled), looks up client by phone number
4. Checks credit balance, reserves credits, generates session token
5. Returns TwiML with `<Connect><Stream>` pointing to TEIA Core WebSocket
6. Twilio connects audio stream to TEIA Core
7. When call ends, TEIA Core calls `POST /internal/session/complete`
8. TEIA-users reconciles credits (refunds unused, charges overage)

### Configuration

| Property | Env Var | Default | Description |
|----------|---------|---------|-------------|
| `teia.twilio.signature-validation.enabled` | `TWILIO_SIGNATURE_VALIDATION_ENABLED` | `true` | Enable/disable Twilio signature validation |
| `teia.core.base-url` | `TEIA_CORE_BASE_URL` | `http://localhost:8000` | TEIA Core HTTP URL |
| `teia.core.ws-base-url` | `TEIA_CORE_WS_BASE_URL` | `wss://localhost:8000` | TEIA Core WebSocket URL |
| `teia.credits.call-reservation` | `CREDITS_CALL_RESERVATION` | `5.00` | Credits reserved per call |
| `teia.credits.message-reservation` | `CREDITS_MESSAGE_RESERVATION` | `1.00` | Credits reserved per message |
| `teia.credits.per-minute` | `CREDITS_PER_MINUTE` | `1.00` | Credits charged per call minute |
| `teia.session-token.validity-seconds` | `SESSION_TOKEN_VALIDITY_SECONDS` | `60` | Session token TTL (seconds) |

---

## Local Testing Guide

Test the full TEIA flow locally without real Twilio traffic.

### Prerequisites

- Java 17+
- Node.js 18+ (for TEIA Core)
- No external database needed (H2 in-memory for TEIA-users)

### 1. Start TEIA-users (local profile)

```bash
cd TEIA-users

# Option A: Command line
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# Option B: Environment variable
export SPRING_PROFILES_ACTIVE=local
./mvnw spring-boot:run
```

Verify startup:
- Swagger UI: http://localhost:8099/teiaUsers/swagger-ui.html
- H2 Console: http://localhost:8099/teiaUsers/h2-console (JDBC URL: `jdbc:h2:mem:teiadb`)

### 2. Start TEIA Core

```bash
cd TEIA

# Copy example env (already configured for local dev)
cp .env.example .env

# Start server
node index.js
```

Verify: http://localhost:8000/health

### 3. Create Test Data

Open H2 Console at http://localhost:8099/teiaUsers/h2-console

**JDBC URL:** `jdbc:h2:mem:teiadb`
**User:** `sa`
**Password:** (leave empty)

Insert test user with credits:

```sql
-- Create related info records first
INSERT INTO eleven_labs_info (id, eleven_labs_api_key, eleven_labs_agent_id) VALUES (1, 'test-eleven-labs-key', 'test-agent-id');
INSERT INTO twilio_info (id, twilio_acc_sid, twilio_auth_token, twilio_phone_number_calls, twilio_phone_number_whatsapp) VALUES (1, 'ACtest123', 'test-auth-token', '+15559876543', '+15559876543');
INSERT INTO open_ai_info (id, open_ai_api_key, open_ai_assistant_key) VALUES (1, 'sk-test-key', 'asst_test123');
INSERT INTO cal_info (id, api_key, user_id, event_id) VALUES (1, 'test-cal-key', 'test-user', 'test-event');

-- Create user with 100 credits
INSERT INTO teia_user (id, name, email, password, role, phone_number, eleven_labs_info_id, twilio_info_id, open_ai_info_id, cal_info_id, credit_balance, create_date, update_date)
VALUES (1, 'Test User', 'test@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMye', 'CLIENT', '+15551234567', 1, 1, 1, 1, 100.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
```

### 4. Test Curl Commands

#### Simulate inbound call (sufficient credits)

```bash
curl -X POST http://localhost:8099/teiaUsers/twilio/inbound/call \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "From=%2B15551234567&To=%2B15559876543&CallSid=CA123abc&CallStatus=ringing"
```

**Expected:** TwiML with `<Connect><Stream>` element containing session parameters.

#### Simulate inbound call (zero balance)

First, update user's credit balance to 0:

```sql
UPDATE teia_user SET credit_balance = 0 WHERE id = 1;
```

Then test:

```bash
curl -X POST http://localhost:8099/teiaUsers/twilio/inbound/call \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "From=%2B15551234567&To=%2B15559876543&CallSid=CA456def&CallStatus=ringing"
```

**Expected:** TwiML with "Service unavailable" message.

#### Initiate outbound call

```bash
curl -X POST http://localhost:8099/teiaUsers/twilio/outbound/call \
  -H "Content-Type: application/json" \
  -d '{
    "clientId": "1",
    "to": "+15551112222",
    "customPrompt": "You are calling to confirm an appointment."
  }'
```

**Expected:** JSON with sessionId, status "initiated" (will fail connecting to TEIA Core if not running, but creates session).

#### Session completion callback (with HMAC signature)

This simulates TEIA Core calling back after a session ends:

```bash
# Generate timestamp and signature
TIMESTAMP=$(date +%s)000
BODY='{"sessionId":"sess_test123","clientId":1,"type":"CALL","actualDurationSeconds":120,"status":"COMPLETED"}'
SECRET="teia-local-dev-secret-change-in-prod"

# Compute HMAC-SHA256 signature
SIGNATURE=$(echo -n "${BODY}${TIMESTAMP}" | openssl dgst -sha256 -hmac "$SECRET" | cut -d' ' -f2)

curl -X POST http://localhost:8099/teiaUsers/internal/session/complete \
  -H "Content-Type: application/json" \
  -H "X-Internal-Key: $SIGNATURE" \
  -H "X-Timestamp: $TIMESTAMP" \
  -H "X-Client-ID: teia-core-local" \
  -d "$BODY"
```

**Expected:** JSON with reconciled credit amounts (will 404 if session doesn't exist).

#### Report usage directly (with HMAC signature)

This simulates TEIA Core reporting usage after a call/message ends (direct deduction, no prior reservation):

```bash
# Generate timestamp and signature
TIMESTAMP=$(date +%s)000
BODY='{"clientId":1,"type":"CALL","durationSeconds":120,"sessionId":"sess_usage_test123"}'
SECRET="teia-local-dev-secret-change-in-prod"

# Compute HMAC-SHA256 signature
SIGNATURE=$(echo -n "${BODY}${TIMESTAMP}" | openssl dgst -sha256 -hmac "$SECRET" | cut -d' ' -f2)

curl -X POST http://localhost:8099/teiaUsers/internal/usage \
  -H "Content-Type: application/json" \
  -H "X-Internal-Key: $SIGNATURE" \
  -H "X-Timestamp: $TIMESTAMP" \
  -H "X-Client-ID: teia-core-local" \
  -d "$BODY"
```

**Expected:** JSON with `sessionId`, `creditsUsed`, and `newBalance`. Returns 402 if insufficient credits.

#### Check client credit balance (with HMAC signature)

```bash
# Generate timestamp and signature (GET request - body is empty string)
TIMESTAMP=$(date +%s)000
SECRET="teia-local-dev-secret-change-in-prod"

# Compute HMAC-SHA256 signature (empty body for GET)
SIGNATURE=$(echo -n "${TIMESTAMP}" | openssl dgst -sha256 -hmac "$SECRET" | cut -d' ' -f2)

curl -X GET http://localhost:8099/teiaUsers/internal/clients/1/credits \
  -H "X-Internal-Key: $SIGNATURE" \
  -H "X-Timestamp: $TIMESTAMP" \
  -H "X-Client-ID: teia-core-local"
```

**Expected:** JSON with `clientId`, `creditBalance`, and `hasCredits` (true if balance >= 1.00).

#### Test session token expiry

1. Trigger an inbound call to create a session (see above)
2. Note the session token from the TwiML response
3. Wait longer than `teia.session-token.validity-seconds` (300s in local profile)
4. The token will be rejected when TEIA Core tries to validate it

For faster testing, temporarily set a shorter validity:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local \
  -Dspring-boot.run.arguments="--teia.session-token.validity-seconds=10"
```

### Troubleshooting

**H2 Console won't connect:**
- Make sure JDBC URL is exactly `jdbc:h2:mem:teiadb`
- Check that the application started successfully

**"No client found for number":**
- Verify the test user was inserted correctly
- Check that the `To` parameter matches `twilio_phone_number_calls`

**TEIA Core connection refused:**
- Start TEIA Core with `node index.js`
- Verify it's running on port 8000

**HMAC signature rejected:**
- Ensure `INTERNAL_SECRET` matches between both services
- Check that timestamp is within 30 seconds of server time
- Verify the signature computation (body + timestamp, no separator)
