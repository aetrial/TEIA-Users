# TEIA-users REST API Documentation

Complete API reference for the TEIA-users service. This service manages platform users and their third-party service credentials (OpenAI, ElevenLabs, Twilio, Cal.com).

**Base URL:** `http://localhost:8080` (default)

**Total Endpoints:** 39 across 7 controllers

---

## Table of Contents

1. [Users](#1-users--user-management)
2. [Contacts](#2-contacts--contact-management)
3. [ElevenLabs Info](#3-eleven-labs-info--elevenlabs-configuration)
4. [Twilio Info](#4-twilio-info--twilio-configuration)
5. [OpenAI Info](#5-openai-info--openai-configuration)
6. [Cal Info](#6-cal-info--calcom-configuration)
7. [Threads](#7-threads--thread-management)
8. [Entity Relationships](#entity-relationships)
9. [Enums](#enums)

---

## 1. `/users` — User Management

Manages TEIA platform users with their associated credentials and contacts.

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/users` | Create user with all credentials |
| `PUT` | `/users/{id}` | Update user (partial) |
| `DELETE` | `/users/{id}` | Delete user (cascades to related entities) |
| `GET` | `/users/{id}` | Get user by ID |
| `GET` | `/users` | Get all users |
| `GET` | `/users/{id}/contacts` | Get user's contacts |
| `POST` | `/users/{id}/contacts` | Add contact to user |
| `DELETE` | `/users/{userId}/contacts/{contactId}` | Remove contact from user |

### Create User

`POST /users`

Creates a new user with all associated credential configurations.

**Request Body:**

```json
{
  "name": "string",
  "email": "string",
  "phoneNumber": "string",
  "elevenLabsInfo": {
    "elevenLabsAgentId": "string",
    "elevenLabsApiKey": "string"
  },
  "twilioInfo": {
    "twilioAccSID": "string",
    "twilioAuthToken": "string",
    "twilioPhoneNumberCalls": "string",
    "twilioPhoneNumberWhatsapp": "string"
  },
  "openAIInfo": {
    "openAIApiKey": "string",
    "openAIAssistantKey": "string"
  },
  "calInfo": {
    "apiKey": "string",
    "userId": "string",
    "eventId": "string"
  },
  "contacts": []
}
```

**Response:** `201 Created` with the created user object

### Update User

`PUT /users/{id}`

Partially updates an existing user. Only include fields you want to modify.

**Path Parameters:**
- `id` (Long) — User ID

**Request Body:** Same structure as create, but all fields optional

**Response:** `200 OK` with the updated user object

### Delete User

`DELETE /users/{id}`

Deletes a user and cascades deletion to all related entities (credentials, contacts).

**Path Parameters:**
- `id` (Long) — User ID

**Response:** `204 No Content`

### Get User

`GET /users/{id}`

**Path Parameters:**
- `id` (Long) — User ID

**Response:** `200 OK` with user object

### Get All Users

`GET /users`

**Response:** `200 OK` with array of user objects

### Get User Contacts

`GET /users/{id}/contacts`

**Path Parameters:**
- `id` (Long) — User ID

**Response:** `200 OK` with array of contact objects

### Add Contact to User

`POST /users/{id}/contacts`

**Path Parameters:**
- `id` (Long) — User ID

**Request Body:**

```json
{
  "phoneNumber": "string",
  "name": "string",
  "channel": "CALL | WHATSAPP",
  "status": "ACTIVE | INACTIVE"
}
```

**Response:** `201 Created` with the created contact object

### Remove Contact from User

`DELETE /users/{userId}/contacts/{contactId}`

**Path Parameters:**
- `userId` (Long) — User ID
- `contactId` (Long) — Contact ID

**Response:** `204 No Content`

---

## 2. `/contacts` — Contact Management

Standalone contact CRUD operations.

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/contacts` | Create contact (requires teiaUser.id) |
| `PUT` | `/contacts/{id}` | Update contact (partial) |
| `DELETE` | `/contacts/{id}` | Delete contact |
| `GET` | `/contacts/{id}` | Get contact by ID |
| `GET` | `/contacts` | Get all contacts |

### Create Contact

`POST /contacts`

**Request Body:**

```json
{
  "phoneNumber": "string",
  "name": "string",
  "channel": "CALL | WHATSAPP",
  "status": "ACTIVE | INACTIVE",
  "teiaUser": {
    "id": 1
  }
}
```

**Response:** `201 Created` with the created contact object

### Update Contact

`PUT /contacts/{id}`

**Path Parameters:**
- `id` (Long) — Contact ID

**Request Body:** Same structure as create, all fields optional

**Response:** `200 OK` with the updated contact object

### Delete Contact

`DELETE /contacts/{id}`

**Path Parameters:**
- `id` (Long) — Contact ID

**Response:** `204 No Content`

### Get Contact

`GET /contacts/{id}`

**Path Parameters:**
- `id` (Long) — Contact ID

**Response:** `200 OK` with contact object

### Get All Contacts

`GET /contacts`

**Response:** `200 OK` with array of contact objects

---

## 3. `/eleven-labs-info` — ElevenLabs Configuration

Manages ElevenLabs voice AI credentials.

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/eleven-labs-info` | Create ElevenLabs config |
| `PUT` | `/eleven-labs-info/{id}` | Update ElevenLabs config |
| `DELETE` | `/eleven-labs-info/{id}` | Delete ElevenLabs config |
| `GET` | `/eleven-labs-info/{id}` | Get ElevenLabs config by ID |
| `GET` | `/eleven-labs-info` | Get all ElevenLabs configs |

### Request Body

```json
{
  "elevenLabsAgentId": "string",
  "elevenLabsApiKey": "string"
}
```

---

## 4. `/twilio-info` — Twilio Configuration

Manages Twilio telephony credentials.

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/twilio-info` | Create Twilio config |
| `PUT` | `/twilio-info/{id}` | Update Twilio config |
| `DELETE` | `/twilio-info/{id}` | Delete Twilio config |
| `GET` | `/twilio-info/{id}` | Get Twilio config by ID |
| `GET` | `/twilio-info` | Get all Twilio configs |

### Request Body

```json
{
  "twilioAccSID": "string",
  "twilioAuthToken": "string",
  "twilioPhoneNumberCalls": "string",
  "twilioPhoneNumberWhatsapp": "string"
}
```

---

## 5. `/openai-info` — OpenAI Configuration

Manages OpenAI API and assistant credentials.

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/openai-info` | Create OpenAI config |
| `PUT` | `/openai-info/{id}` | Update OpenAI config |
| `DELETE` | `/openai-info/{id}` | Delete OpenAI config |
| `GET` | `/openai-info/{id}` | Get OpenAI config by ID |
| `GET` | `/openai-info` | Get all OpenAI configs |

### Request Body

```json
{
  "openAIApiKey": "string",
  "openAIAssistantKey": "string"
}
```

---

## 6. `/cal-info` — Cal.com Configuration

Manages Cal.com calendar integration credentials.

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/cal-info` | Create Cal.com config |
| `PUT` | `/cal-info/{id}` | Update Cal.com config |
| `DELETE` | `/cal-info/{id}` | Delete Cal.com config |
| `GET` | `/cal-info/{id}` | Get Cal.com config by ID |
| `GET` | `/cal-info` | Get all Cal.com configs |

### Request Body

```json
{
  "apiKey": "string",
  "userId": "string",
  "eventId": "string"
}
```

---

## 7. `/threads` — Thread Management

Manages OpenAI conversation thread records, indexed by phone number pairs.

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/threads` | Create thread record |
| `DELETE` | `/threads` | Delete by composite key (query params) |
| `GET` | `/threads` | Get all threads |
| `GET` | `/threads/lookup` | Find thread by composite key |
| `GET` | `/threads/by-to-phone` | Find threads by destination phone |
| `GET` | `/threads/by-from-phone` | Find threads by source phone |

### Create Thread

`POST /threads`

**Request Body:**

```json
{
  "id": {
    "toPhoneNumber": "string",
    "fromPhoneNumber": "string",
    "threadId": "string"
  }
}
```

**Response:** `201 Created` with the created thread object

### Delete Thread

`DELETE /threads`

**Query Parameters:**
- `toPhoneNumber` (String) — Destination phone number
- `fromPhoneNumber` (String) — Source phone number
- `threadId` (String) — OpenAI thread ID

**Response:** `204 No Content`

### Get All Threads

`GET /threads`

**Response:** `200 OK` with array of thread objects

### Lookup Thread

`GET /threads/lookup`

Find a specific thread by its composite key.

**Query Parameters:**
- `toPhoneNumber` (String) — Destination phone number
- `fromPhoneNumber` (String) — Source phone number
- `threadId` (String) — OpenAI thread ID

**Response:** `200 OK` with thread object or `404 Not Found`

### Find Threads by Destination Phone

`GET /threads/by-to-phone`

**Query Parameters:**
- `toPhoneNumber` (String) — Destination phone number

**Response:** `200 OK` with array of thread objects

### Find Threads by Source Phone

`GET /threads/by-from-phone`

**Query Parameters:**
- `fromPhoneNumber` (String) — Source phone number

**Response:** `200 OK` with array of thread objects

---

## Entity Relationships

```
TeiaUser (1)
  |
  +-- (1) ElevenLabsInfo    [voice AI credentials]
  |
  +-- (1) TwilioInfo        [telephony credentials]
  |
  +-- (1) OpenAIInfo        [assistant credentials]
  |
  +-- (1) CalInfo           [calendar integration]
  |
  +-- (*) Contact           [user's contacts list]

Thread [standalone]
  |
  +-- Tracks OpenAI conversation threads
  +-- Indexed by phone number pair (to/from)
```

**Cascade behavior:** Deleting a TeiaUser cascades deletion to all related ElevenLabsInfo, TwilioInfo, OpenAIInfo, CalInfo, and Contact records.

---

## Enums

### Channel

Communication channel type for contacts.

| Value | Description |
|-------|-------------|
| `CALL` | Voice call channel |
| `WHATSAPP` | WhatsApp messaging channel |

### Contact Status

Contact availability status.

| Value | Description |
|-------|-------------|
| `ACTIVE` | Contact is active and can be reached |
| `INACTIVE` | Contact is inactive |

---

## Error Responses

Standard HTTP error codes are used:

| Status | Description |
|--------|-------------|
| `400 Bad Request` | Invalid request body or parameters |
| `404 Not Found` | Resource not found |
| `500 Internal Server Error` | Server-side error |

---

## Swagger UI

Interactive API documentation is available at:

```
http://localhost:8080/swagger-ui.html
```

OpenAPI specification:

```
http://localhost:8080/v3/api-docs
```
