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
