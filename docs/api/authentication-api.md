# SAVDOCRM — Authentication API Specification

Base Path: `/api/v1/auth`

All API responses follow the standard SAVDOCRM wrapper format:
```json
{
  "success": true,
  "data": { ... },
  "message": "Optional human-readable confirmation message",
  "timestamp": "2026-11-23T16:30:00Z"
}
```

Errors follow the RFC 7807 `application/problem+json` standard:
```json
{
  "type": "https://savdocrm.com/errors/bad-request",
  "title": "Bad Request",
  "status": 400,
  "detail": "Detailed failure explanation",
  "instance": "/api/v1/auth/login",
  "timestamp": "2026-11-23T16:30:00Z"
}
```

---

## 1. Register User / Store

Creates a new user account with optional initial store setup.

- **Method**: `POST`
- **Path**: `/api/v1/auth/register`
- **Authentication**: None (Public)
- **Content-Type**: `application/json`

### Request Body
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

### Validation Rules
- `email`: Required, valid email format, max 150 chars. Unique case-insensitively.
- `username`: Required, 3–50 chars, alphanumeric + `_`, `.`, `-`. Unique.
- `password`: Required, minimum 8 characters.
- `firstName`: Required, max 100 chars.
- `lastName`: Required, max 100 chars.
- `phoneNumber`: Optional, max 30 chars.
- `storeName`: Optional, max 150 chars. If provided, creates a Tenant store, Main Branch, and assigns ACTIVE membership.

### Response `201 Created`
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "xH4n9Y7_...",
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

### Error Responses
- `400 Bad Request`: Duplicate email or duplicate username (`"An account with email '...' already exists."`).
- `400 Bad Request`: Validation failure on required fields.

---

## 2. Login

Authenticates user credentials and returns a Global Access Token along with the list of available store memberships.

- **Method**: `POST`
- **Path**: `/api/v1/auth/login`
- **Authentication**: None (Public)
- **Content-Type**: `application/json`

### Request Body
```json
{
  "emailOrUsername": "retailer@example.com",
  "password": "StrongPassword123!"
}
```

### Response `200 OK`
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "kQ3p8Z1_...",
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

### Error Responses
- `401 Unauthorized`: Invalid email/username or password (`"Invalid credentials"`).
- `403 Forbidden`: Account is disabled or suspended.

---

## 3. Select Tenant Store

Activates a store context and issues a Tenant-Scoped Access Token.

- **Method**: `POST`
- **Path**: `/api/v1/auth/select-tenant`
- **Authentication**: Required (`Authorization: Bearer <global_or_tenant_token>`)
- **Content-Type**: `application/json`

### Request Body
```json
{
  "tenantId": "c92fb380-087e-4071-8bc6-90518389366d"
}
```

### Response `200 OK`
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "wE5t2M8_...",
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
    "availableTenants": [ ... ]
  },
  "message": "Tenant selected successfully",
  "timestamp": "2026-11-23T16:30:00Z"
}
```

### Error Responses
- `401 Unauthorized`: Missing or invalid Bearer token.
- `403 Forbidden`: User does not have an active membership in the requested store, or store is suspended.

---

## 4. Refresh Token Rotation

Rotates the refresh token and generates a new access token within the active session family.

- **Method**: `POST`
- **Path**: `/api/v1/auth/refresh`
- **Authentication**: None (Public)
- **Content-Type**: `application/json`

### Request Body
```json
{
  "refreshToken": "wE5t2M8_..."
}
```

### Response `200 OK`
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "vN9s1K4_...",
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

### Error Responses
- `401 Unauthorized`: Token is expired, invalid, or already revoked.
- `401 Unauthorized`: Token reuse detected — entire session family revoked.

---

## 5. Logout

Revokes the refresh token and ends the active session.

- **Method**: `POST`
- **Path**: `/api/v1/auth/logout`
- **Authentication**: None (Public; accepts optional refresh token in payload)
- **Content-Type**: `application/json`

### Request Body
```json
{
  "refreshToken": "vN9s1K4_..."
}
```

### Response `200 OK`
```json
{
  "success": true,
  "data": null,
  "message": "Logged out successfully",
  "timestamp": "2026-11-23T16:30:00Z"
}
```

---

## 6. Get Current User Profile

Retrieves current authenticated identity and available store memberships.

- **Method**: `GET`
- **Path**: `/api/v1/auth/me`
- **Authentication**: Required (`Authorization: Bearer <token>`)

### Response `200 OK`
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
