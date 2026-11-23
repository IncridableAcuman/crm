# Milestone 03 — Authentication & Session Management Architecture

## 1. Executive Summary

SAVDOCRM Milestone 03 delivers a production-grade, multi-tenant authentication and session management subsystem for retail store management. It implements a **Dual-Phase JWT Authentication Model** with cryptographically secure refresh token rotation, token reuse detection, server-side tenant isolation enforcement, and a reactive frontend session slice.

---

## 2. Identity Domain Model

The Identity domain defines three core persistent entities:

```
┌────────────────┐          ┌──────────────────────┐          ┌────────────────┐
│      User      │ 1      * │   TenantMembership   │ *      1 │     Tenant     │
│  (Global Identity)◄───────┤ (Membership Context) ├─────────►│(Tenancy Module)│
└───────┬────────┘          └──────────────────────┘          └────────────────┘
        │ 1
        │
        │ *
┌───────▼────────┐
│  RefreshToken  │
│(Session Family)│
└────────────────┘
```

### 2.1 User
A global account entity that can belong to multiple independent tenants.
- **Table**: `users`
- **Fields**: `id` (UUIDv4), `username` (unique), `email` (unique case-insensitive), `password_hash` (BCrypt cost 12), `first_name`, `last_name`, `phone_number`, `status` (`ACTIVE`, `SUSPENDED`), `email_verified` (boolean), audit timestamps (`created_at`, `updated_at`), optimistic locking (`version`).
- **Security Constraints**: Emails are trimmed and converted to lowercase upon entry; plaintext passwords are never logged, stored, or returned in DTOs.

### 2.2 TenantMembership
Links a global `User` to a specific `Tenant`.
- **Table**: `tenant_memberships`
- **Fields**: `id` (UUIDv4), `tenant_id` (UUIDv4 FK), `user_id` (UUIDv4 FK), `status` (`ACTIVE`, `INVITED`, `SUSPENDED`), audit timestamps, `version`.
- **Constraints**:
  - `uq_memberships_tenant_user`: Ensures a user can only have one membership per tenant.
  - `uq_memberships_tenant_id`: Composite candidate key for tenant-partitioned references.

### 2.3 RefreshToken
Opaque cryptographically secure session record with token family tracking.
- **Table**: `refresh_tokens`
- **Fields**: `id` (UUIDv4), `user_id` (UUIDv4 FK), `token_hash` (CHAR(64) SHA-256), `token_family` (UUIDv4), `tenant_id` (UUIDv4 nullable), `revoked` (boolean), `revoked_at`, `revoked_reason`, `expires_at`, `created_at`, `created_ip`, `user_agent`.
- **Security**: Raw refresh tokens (48 random bytes, base64url encoded) are never persisted in the database; only their SHA-256 hashes are stored.

---

## 3. Dual-Phase Authentication Flow

SAVDOCRM separates identity verification from tenant context activation to prevent privilege elevation and cross-store data leakage:

```mermaid
sequenceDiagram
    autonumber
    actor User as Store Associate / Owner
    participant Client as Frontend (React 19)
    participant Auth as AuthController / AuthService
    participant DB as PostgreSQL 16
    participant Filter as JwtAuthenticationFilter

    Note over User,Client: Phase 1: Global Identity Authentication
    User->>Client: Enters credentials (email/username + password)
    Client->>Auth: POST /api/v1/auth/login
    Auth->>DB: Query user by email (case-insensitive) or username
    DB-->>Auth: User record (passwordHash, status)
    Auth->>Auth: Verify password (BCrypt cost 12) & check ACTIVE status
    Auth->>DB: Query user's available tenant memberships
    Auth->>Auth: Mint Global Access Token (userId, email, no tenantId)
    Auth->>DB: Persist SHA-256 hashed RefreshToken (new tokenFamily)
    Auth-->>Client: Returns 200 OK (Global Access Token, Refresh Token, availableTenants[])

    Note over User,Client: Phase 2: Active Store Selection
    Client->>User: Renders Store Selector (/select-tenant)
    User->>Client: Selects store (tenantId)
    Client->>Auth: POST /api/v1/auth/select-tenant (Bearer GlobalToken)
    Auth->>DB: Verify membership ACTIVE & Tenant ACTIVE
    Auth->>Auth: Mint Tenant-Scoped Access Token (userId, email, tenantId, membershipId)
    Auth->>DB: Persist new RefreshToken linked to tenantId
    Auth-->>Client: Returns 200 OK (Tenant Access Token, Refresh Token, activeTenant)

    Note over Client,Filter: Request Execution with Tenant Scope
    Client->>Filter: GET /api/v1/branches (Bearer TenantToken)
    Filter->>Filter: Validate JWT signature & claims
    Filter->>DB: Verify Tenant ACTIVE and Membership ACTIVE (live check)
    Filter->>Filter: TenantContextHolder.setContext(TenantContext.of(tenantId))
    Filter-->>Client: Protected business response (strictly filtered to active tenant)
    Filter->>Filter: finally { TenantContextHolder.clear(); }
```

---

## 4. Token Lifecycle, Rotation, and Reuse Detection

### 4.1 Access Token Architecture
- **Format**: Signed JWT (JWS HMAC-SHA256).
- **Expiration**: 15 minutes (900 seconds) by default.
- **Claims**:
  - Global Token: `sub` (userId), `email`, `tokenType: "ACCESS"`, `iss`, `iat`, `exp`, `jti`.
  - Tenant Token: Above claims plus `tenantId`, `membershipId`.

### 4.2 Refresh Token Rotation (RTR)
- Every call to `POST /api/v1/auth/refresh` immediately revokes the presented refresh token (`revoked = true`, `revoked_reason = "Rotated"`).
- A newly generated refresh token is minted within the **same `token_family`**.
- An updated access token (matching previous scope) is returned.

### 4.3 Automated Reuse Detection (Compromise Quarantine)
If an attacker intercepts an old, already-rotated refresh token and attempts to replay it:
1. SAVDOCRM detects `refreshToken.isRevoked() == true`.
2. The entire token family (`token_family = refreshToken.tokenFamily`) is immediately invalidated in the database (`UPDATE refresh_tokens SET revoked = true, revoked_reason = 'Token reuse detected'`).
3. An audit warning is logged.
4. A 401 UNAUTHORIZED RFC 7807 response is returned.
5. All concurrent valid sessions in that family are immediately terminated, isolating the compromise.

---

## 5. Spring Security Architecture & Filter Chain

### 5.1 Security Filter Order
1. `CorsFilter`: Configured with explicit allowed origins (`localhost:5173`, `localhost:3000`), credentials permitted, custom headers allowed.
2. `CsrfFilter`: Disabled (stateless bearer token authentication; no cookie-based state vulnerabilities).
3. `SessionManagementFilter`: Set to `SessionCreationPolicy.STATELESS` (zero `HttpSession` creation).
4. `JwtAuthenticationFilter`: Extends `OncePerRequestFilter`:
   - Extracts `Bearer <token>` from `Authorization` header.
   - Zero-trust client header verification (no client `X-Tenant-Id` header is ever trusted).
   - Validates HMAC-SHA256 signature and expiration via `JwtTokenProvider`.
   - On tenant-scoped tokens: executes live database verification to confirm `Tenant.status == ACTIVE` and `TenantMembership.status == ACTIVE`.
   - Sets `SecurityContextHolder` with `UserPrincipal`.
   - Sets `TenantContextHolder` with `TenantContext.of(tenantId, "JWT_AUTH")`.
   - MDC logging correlation for `userId` and `tenantId`.
   - **Guaranteed Cleanup**: `try ... finally { TenantContextHolder.clear(); MDC.remove(...); }`.
5. `RestAuthenticationEntryPoint`: RFC 7807 ProblemDetail response for 401 Unauthorized.
6. `RestAccessDeniedHandler`: RFC 7807 ProblemDetail response for 403 Forbidden.

### 5.2 Public Endpoints
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `GET /api/v1/health/**`
- `GET /v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`
- `GET /actuator/health/**`

All other endpoints require authenticated JWT tokens.

---

## 6. Frontend Authentication Integration

### 6.1 Architecture (Feature-Sliced Design)
- **`src/entities/user/`**: Types for `User` and `TenantMembership`.
- **`src/entities/session/`**: Types for `AuthResponse` and `ApiResponse<T>`.
- **`src/features/auth/model/useAuthStore.ts`**: Zustand store persisted to `localStorage` (`savdocrm_auth`).
- **`src/shared/api/client.ts`**:
  - Request interceptor adds `Authorization: Bearer <accessToken>`.
  - Response interceptor implements concurrency-safe 401 token refresh queue (`isRefreshing`, `failedQueue`).
- **`src/app/routes/ProtectedRoute.tsx`**: Route guard supporting dual-phase requirements:
  - If unauthenticated -> redirects to `/login`.
  - If route requires active tenant and none is selected -> redirects to `/select-tenant`.
