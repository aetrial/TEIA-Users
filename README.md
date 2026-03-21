# TEIA-Users

User management service for the TEIA voice AI platform. Manages platform users and their third-party service credentials (OpenAI, ElevenLabs, Twilio, Cal.com).

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

Everything below is pre-configured to work out of the box on localhost. Copy and run:

```bash
# 1. Create a .env file with the required secrets (pre-filled for local dev)
cat > .env << 'EOF'
# JWT signing key (min 32 chars)
JWT_SECRET=teia-local-dev-jwt-secret-change-in-prod-abc123

# AES-256-GCM encryption key for credentials at rest (base64, 32 bytes)
ENCRYPTION_KEY=K7gNU3sdo+OL0wNhqoVWhr3g6s1xYv72ol/pe/Unols=

# Shared secret for HMAC signing with TEIA Core (must match TEIA Core's INTERNAL_SECRET)
INTERNAL_SECRET=teia-local-dev-secret-change-in-prod
EOF

# 2. Export env vars into your shell
export $(grep -v '^#' .env | xargs)

# 3. Start the service
./mvnw spring-boot:run
```

**Prerequisites:**
- Java 17+
- PostgreSQL running locally with a database named `teiacore` (user: `postgres`, password: `password`)
  ```bash
  # Quick Postgres setup with Docker if needed
  docker run -d --name teia-pg -e POSTGRES_PASSWORD=password -e POSTGRES_DB=teiacore -p 5432:5432 postgres:15
  ```
- TEIA Core running on `http://localhost:8000` (for inter-service calls)

The app will be available at: `http://localhost:8099/teiaUsers`
Swagger UI: `http://localhost:8099/teiaUsers/swagger-ui.html`

> **Note:** `JWT_SECRET` and `ENCRYPTION_KEY` are the only required env vars without defaults. All other values (DB, timeouts, token expiry) use sensible defaults from `application.properties`.

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

**Total: 39 endpoints across 7 controllers**

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

## Relationship to TEIA

This service is the **source of truth** for user configuration. The TEIA voice platform (`../TEIA/`) queries this API at runtime to retrieve:

- OpenAI assistant ID and API key
- ElevenLabs voice agent ID and API key
- Twilio credentials for telephony
- Cal.com credentials for scheduling

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

## TEIA Core Integration

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
