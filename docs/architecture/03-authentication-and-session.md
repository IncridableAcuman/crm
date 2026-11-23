# Milestone 03 — Authentication & Session Management Architecture

## 1. Executive Summary

SAVDOCRM Milestone 03 delivers a production-grade, multi-tenant authentication and session management subsystem for retail store management. It implements a **Dual-Phase JWT Authentication Model** with cryptographically secure refresh token rotation, token reuse detection with concurrency grace handling, server-side tenant isolation enforcement, and an in-memory frontend session architecture powered by HttpOnly cookies and TanStack Query.

---

## 2. Identity Domain Model

The Identity domain defines three persistent entities:

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
A global account entity that can belong to multiple independent retail tenants.
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

## 3. Token Storage Architecture

| Token Type | Storage Medium | Delivery Mechanism | Lifetime | Scope / Purpose |
| :--- | :--- | :--- | :--- | :--- |
| **Access Token** | **In-Memory Only** (Zustand state variable) | JSON response body (`accessToken`) | 15 minutes (900s) | Stateless API authorization header (`Bearer <token>`). Cleared on tab close / reload. |
| **Refresh Token** | **HttpOnly Cookie** (`savdocrm_refresh_token`) | `Set-Cookie` HTTP header | 30 days (2,592,000s) | Token rotation and silent session restoration. Inaccessible to JavaScript / XSS. |

### Cookie Configuration: Development vs Production

```yaml
# Development (application-dev.yml)
jwt:
  cookie-name: savdocrm_refresh_token
  cookie-secure: false          # Permitted for plain HTTP on localhost
  cookie-same-site: Lax         # Allows top-level navigation while preventing CSRF
  cookie-path: /api/v1/auth     # Restricts cookie scope exclusively to auth endpoints

# Production (application-prod.yml)
jwt:
  cookie-name: savdocrm_refresh_token
  cookie-secure: true           # Enforces HTTPS transmission only
  cookie-same-site: Lax
  cookie-path: /api/v1/auth
```

---

## 4. Dual-Phase Authentication Flow

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
    Auth-->>Client: Returns 200 OK + Set-Cookie (HttpOnly savdocrm_refresh_token)
    Note over Client: Stores accessToken strictly in memory (Zustand)

    Note over User,Client: Phase 2: Active Store Selection
    Client->>User: Renders Store Selector (/select-tenant)
    User->>Client: Selects store (tenantId)
    Client->>Auth: POST /api/v1/auth/select-tenant (Bearer GlobalToken)
    Auth->>DB: Verify membership ACTIVE & Tenant ACTIVE
    Auth->>Auth: Mint Tenant-Scoped Access Token (userId, email, tenantId, membershipId)
    Auth->>DB: Persist new RefreshToken linked to tenantId
    Auth-->>Client: Returns 200 OK (Tenant Access Token in JSON) + Set-Cookie (Rotated HttpOnly Cookie)
    Note over Client: Clears queryClient cache & stores new in-memory accessToken

    Note over Client,Filter: Request Execution with Tenant Scope
    Client->>Filter: GET /api/v1/branches (Bearer TenantToken)
    Filter->>Filter: Validate JWT signature & claims
    Filter->>DB: Live check: User ACTIVE, Tenant ACTIVE, Membership ACTIVE
    Filter->>Filter: TenantContextHolder.setContext(TenantContext.of(tenantId))
    Filter-->>Client: Protected business response (strictly filtered to active tenant)
    Filter->>Filter: finally { TenantContextHolder.clear(); }
```

---

## 5. Refresh Token Security & Concurrency Control

### 5.1 Row-Level Locking (`PESSIMISTIC_WRITE`)
When `/api/v1/auth/refresh` is called:
1. `RefreshTokenRepository.findByTokenHashWithLock(tokenHash)` acquires a PostgreSQL row-level lock (`SELECT ... FOR UPDATE`).
2. Concurrent requests on the same session are serialized, eliminating race conditions between simultaneous network calls (e.g. multiple tabs or simultaneous 401s).

### 5.2 Concurrency Grace Window (OAuth 2.0 Security BCP)
- When a refresh token is rotated, it is marked `revoked = true` with reason `"Rotated"` and timestamp `revoked_at`.
- If a truly concurrent request presents that same token within **10 seconds** of rotation:
  - It identifies that `revokedReason == "Rotated"` and `revokedAt > now - 10s`.
  - It retrieves the latest valid active session in that token family and returns the valid token without revoking the family.
- If a revoked token is presented **outside the 10-second grace window** (or revoked for any other reason):
  - **Automated Compromise Quarantine**: The entire token family is immediately revoked in PostgreSQL (`UPDATE refresh_tokens SET revoked = true, revoked_reason = 'Token reuse detected' WHERE token_family = :family`).
  - Returns RFC 7807 401 Unauthorized (`"Invalid refresh token: token reuse detected."`).
  - All attacker and user sessions within that family are terminated.

---

## 6. Tenant Switching & Query Cache Isolation

When a user switches stores:
1. Client calls `POST /api/v1/auth/select-tenant` with `tenantId`.
2. Backend verifies user membership and tenant status, emitting a new tenant-scoped access token and updated refresh cookie.
3. Client executes `queryClient.clear()`:
   - **Zero Cross-Tenant Leakage**: All cached queries (branches, products, orders, customers, financial balances) from the previous tenant are immediately purged from memory.
   - TanStack Query refetches clean data for the newly selected store.
4. Zustand store updates in-memory `accessToken` and `activeTenant` metadata.

---

## 7. Request Authentication & Live Verification

[`JwtAuthenticationFilter`](file:///home/izzatbek/Desktop/crm/backend/src/main/java/com/savdocrm/shared/security/JwtAuthenticationFilter.java) validates on **every request**:
1. **JWT Signature & Expiration**: HMAC-SHA256 signature verification with JJWT.
2. **User Status**: Live lookup in `UserRepository` confirming `user.isActive() == true`.
3. **Tenant Status**: If tenant-scoped, live lookup confirming `tenant.getStatus() == TenantStatus.ACTIVE`.
4. **Membership Status**: Live lookup confirming `membership.getStatus() == MembershipStatus.ACTIVE`.
5. **Membership Ownership & Consistency**:
   - `membership.getUserId().equals(userId)`
   - `membership.getTenantId().equals(tenantId)`
   - If `membershipId` claim is present in JWT, `membership.getId().equals(membershipId)`
6. **Fail-Closed Population**:
   - `TenantContextHolder` is populated ONLY after all verifications succeed.
   - MDC logging correlation for `userId` and `tenantId`.
   - Guaranteed cleanup in `try ... finally { TenantContextHolder.clear(); MDC.remove(...); }`.

---

## 8. Implemented Guarantees vs Remaining Limitations

### Implemented Guarantees (Milestone 03)
- **Zero LocalStorage Tokens**: Access token is held in-memory; refresh token is in an HttpOnly cookie.
- **Fail-Closed Tenancy**: Zero trust in client headers; tenant context strictly bound from verified JWT claims.
- **Compromise Quarantine**: Refresh token reuse revokes the entire session family.
- **Concurrency Safety**: Row-level locking and 10-second rotation grace window prevent tab race conditions.
- **Cache Isolation**: Tenant switching completely clears TanStack Query cache.
- **Live Account Status**: Suspended users, suspended tenants, and suspended memberships are immediately blocked even with unexpired JWTs.

### Remaining Scope Boundaries (To be addressed in Milestone 04+)
- **RBAC & Method Permissions**: Fine-grained role permissions (Owner, Manager, Cashier) and branch-level authority evaluators are deferred to Milestone 04.
- **CSRF Tokens for Mutating Operations**: Refresh cookie is restricted to SameSite=Lax and Path=/api/v1/auth. In Milestone 04, CSRF double-submit cookies or custom header checks can be evaluated for browser environments that require SameSite=None.
- **Multi-Factor Authentication (MFA)**: Optional future enhancement for admin tier accounts.
