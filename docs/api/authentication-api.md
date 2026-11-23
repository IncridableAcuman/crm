# SAVDOCRM — Authentication API Specification

Base Path: `/api/v1/auth`

---

## 1. Authentication & Cookie Delivery Model

SAVDOCRM uses an **In-Memory Access Token + HttpOnly Cookie** architecture:
- **Access Token**: Emitted in the JSON response body (`accessToken`). Stored exclusively in-memory by the frontend client; never persisted to `localStorage` or `sessionStorage`.
- **Refresh Token**: Transmitted via an **HttpOnly, Secure (in production), SameSite=Lax** cookie named `savdocrm_refresh_token` scoped to `Path=/api/v1/auth`. The raw refresh token is omitted from JSON response bodies.

All API responses follow the standard SAVDOCRM wrapper format:
```json
{
  "success": true,
  "data": { ... },
  "message": "Optional confirmation message",
  "timestamp": "2026-11-23T16:30:00Z"
}
```

Errors follow the RFC 7807 `application/problem+json` standard:
```json
{
  "type": "https://savdocrm.com/errors/unauthorized",
  "title": "Authentication Failed",
  "status": 401,
  "detail": "Invalid credentials",
  "instance": "/api/v1/auth/login",
  "timestamp": "2026-11-23T16:30:00Z"
}
```

---

## 2. Endpoints

### 2.1 Register User / Store
Creates a new user account with optional initial store setup.

- **Method**: `POST`
- **Path**: `/api/v1/auth/register`
- **Authentication**: None (Public)
- **Headers Returned**:
  - `Set-Cookie: savdocrm_refresh_token=<rawToken>; Path=/api/v1/auth; Max-Age=2592000; HttpOnly; SameSite=Lax`

#### Request Body
```json
{
  "email": "retailer@example.com",
  "username": "retailer_bob",
  "password": "StrongPassword123!",
  "firstName": "Bob",
  "lastName": "Smith",
  "phoneNumber": "+1234567890",
  "storeName": "Bob Grocery Store"
}
```

#### Response `201 Created`
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "user": {
      "id": "e4a5d892-7489-4bc2-92a0-4131557d19c3",
      "email": "retailer@example.com",
      "username": "retailer_bob",
      "firstName": "Bob",
      "lastName": "Smith",
      "phoneNumber": "+1234567890"
    },
    "activeTenant": {
      "tenantId": "c92fb380-087e-4071-8bc6-90518389366d",
      "tenantName": "Bob Grocery Store",
      "tenantCode": "BOBGROCERY",
      "membershipStatus": "ACTIVE",
      "current": true
    },
    "availableTenants": [
      {
        "tenantId": "c92fb380-087e-4071-8bc6-90518389366d",
        "tenantName": "Bob Grocery Store",
        "tenantCode": "BOBGROCERY",
        "membershipStatus": "ACTIVE",
        "current": true
      }
    ]
  },
  "message": "Account registered successfully",
  "timestamp": "2026-11-23T16:30:00Z"
}
```

---

### 2.2 Login
Authenticates credentials and returns a Global Access Token in JSON and an HttpOnly refresh cookie.

- **Method**: `POST`
- **Path**: `/api/v1/auth/login`
- **Authentication**: None (Public)
- **Headers Returned**:
  - `Set-Cookie: savdocrm_refresh_token=<rawToken>; Path=/api/v1/auth; Max-Age=2592000; HttpOnly; SameSite=Lax`

#### Request Body
```json
{
  "emailOrUsername": "retailer@example.com",
  "password": "StrongPassword123!"
}
```

#### Response `200 OK`
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "user": {
      "id": "e4a5d892-7489-4bc2-92a0-4131557d19c3",
      "email": "retailer@example.com",
      "username": "retailer_bob",
      "firstName": "Bob",
      "lastName": "Smith",
      "phoneNumber": "+1234567890"
    },
    "activeTenant": null,
    "availableTenants": [
      {
        "tenantId": "c92fb380-087e-4071-8bc6-90518389366d",
        "tenantName": "Bob Grocery Store",
        "tenantCode": "BOBGROCERY",
        "membershipStatus": "ACTIVE",
        "current": false
      }
    ]
  },
  "message": "Login successful",
  "timestamp": "2026-11-23T16:30:00Z"
}
```

---

### 2.3 Select Tenant Store
Activates a store context and issues a Tenant-Scoped Access Token and updated refresh cookie.

- **Method**: `POST`
- **Path**: `/api/v1/auth/select-tenant`
- **Authentication**: Required (`Authorization: Bearer <global_or_tenant_token>`)
- **Headers Returned**:
  - `Set-Cookie: savdocrm_refresh_token=<newRawToken>; Path=/api/v1/auth; Max-Age=2592000; HttpOnly; SameSite=Lax`

#### Request Body
```json
{
  "tenantId": "c92fb380-087e-4071-8bc6-90518389366d"
}
```

#### Response `200 OK`
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "user": { ... },
    "activeTenant": {
      "tenantId": "c92fb380-087e-4071-8bc6-90518389366d",
      "tenantName": "Bob Grocery Store",
      "tenantCode": "BOBGROCERY",
      "membershipStatus": "ACTIVE",
      "current": true
    },
    "availableTenants": [ ... ]
  },
  "message": "Tenant selected successfully",
  "timestamp": "2026-11-23T16:30:00Z"
}
```

---

### 2.4 Refresh Token Rotation
Rotates the session refresh token and emits a new in-memory access token. The request reads the refresh token from the `savdocrm_refresh_token` cookie (or optional fallback body).

- **Method**: `POST`
- **Path**: `/api/v1/auth/refresh`
- **Authentication**: None (Reads HttpOnly Cookie)
- **Headers Returned**:
  - `Set-Cookie: savdocrm_refresh_token=<rotatedToken>; Path=/api/v1/auth; Max-Age=2592000; HttpOnly; SameSite=Lax`

#### Request Body (Optional / Empty)
```json
{}
```

#### Response `200 OK`
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "user": { ... },
    "activeTenant": { ... },
    "availableTenants": [ ... ]
  },
  "message": "Token refreshed successfully",
  "timestamp": "2026-11-23T16:30:00Z"
}
```

#### Error Responses
- `401 Unauthorized`: Token expired or invalid.
- `401 Unauthorized`: Token reuse detected — entire session family revoked.

---

### 2.5 Logout
Revokes the refresh token session in the database and instructs the browser to clear the cookie.

- **Method**: `POST`
- **Path**: `/api/v1/auth/logout`
- **Authentication**: None (Public; extracts cookie automatically)
- **Headers Returned**:
  - `Set-Cookie: savdocrm_refresh_token=; Path=/api/v1/auth; Max-Age=0; HttpOnly; SameSite=Lax`

#### Response `200 OK`
```json
{
  "success": true,
  "data": null,
  "message": "Logged out successfully",
  "timestamp": "2026-11-23T16:30:00Z"
}
```

---

### 2.6 Get Current User Profile
Retrieves current identity profile and available store memberships.

- **Method**: `GET`
- **Path**: `/api/v1/auth/me`
- **Authentication**: Required (`Authorization: Bearer <token>`)

#### Response `200 OK`
```json
{
  "success": true,
  "data": {
    "user": {
      "id": "e4a5d892-7489-4bc2-92a0-4131557d19c3",
      "email": "retailer@example.com",
      "username": "retailer_bob",
      "firstName": "Bob",
      "lastName": "Smith",
      "phoneNumber": "+1234567890"
    },
    "activeTenant": {
      "tenantId": "c92fb380-087e-4071-8bc6-90518389366d",
      "tenantName": "Bob Grocery Store",
      "tenantCode": "BOBGROCERY",
      "membershipStatus": "ACTIVE",
      "current": true
    },
    "availableTenants": [ ... ]
  },
  "timestamp": "2026-11-23T16:30:00Z"
}
```

---

## 3. Token Scope & Authorization Boundaries

### 3.1 Global Login Token vs Tenant-Scoped Token
- **Global Token** (`POST /login`): Contains `userId`, `email`, but `tenantId: null` and `membershipId: null`. Permitted to access `/api/v1/auth/me` and `/api/v1/auth/select-tenant`. Fails closed (`403 Forbidden: Tenant Context Missing`) on tenant business endpoints (e.g. `/api/v1/branches`).
- **Tenant-Scoped Token** (`POST /select-tenant`): Contains `userId`, `email`, `tenantId`, and `membershipId`. Establishes `TenantContextHolder` and accesses tenant-scoped business APIs.
- **Client Header Isolation**: `X-Tenant-Id` header is never trusted or parsed for authorization context.

### 3.2 CSRF Protection on Cookie Endpoints
- Requests using refresh cookies (`POST /refresh`, `POST /logout`) validate the HTTP `Origin` header against explicit whitelist `cors.allowed-origins`. Unlisted cross-site origins receive `403 Forbidden`.

### 3.3 Strict Single-Use Refresh Token Protocol & Compromise Quarantine
- **Strict Single-Use**: Each refresh token is consumed exactly once. Successful rotation revokes the old token (`revoked_reason = 'Rotated'`) and emits exactly one successor.
- **Zero Grace Window**: Revoked tokens are never accepted and never return their successor.
- **Compromise Quarantine**: Presenting an already-revoked refresh token is treated as an active replay attack. The entire `token_family` is immediately revoked in PostgreSQL, invalidating all associated tokens and returning `401 Unauthorized: token reuse detected`.
- **Lost Response Behavior**: If the backend successfully commits token rotation but the HTTP response is lost over the network:
  - The client still possesses the old token.
  - Retrying the old token is treated as reuse.
  - The entire token family is revoked.
  - The client must re-authenticate (`POST /api/v1/auth/login`).
  This is an intentional security-first trade-off prioritizing replay protection over transient network recovery.
- **Client-Side Cross-Tab Coordination**: Handled via the browser **Web Locks API** (`savdocrm_refresh_lock`) and **BroadcastChannel** (`savdocrm_auth_channel`). Only one tab contacts `/refresh`; waiting tabs acquire the lock and reuse the new access token. Refresh tokens are NEVER broadcast and NEVER stored in `localStorage` or `sessionStorage`.
