# ADR-006: Stateless Dual-Phase JWT Authentication with Refresh Token Family Rotation

- **Status**: Accepted
- **Date**: 2026-11-23
- **Context**:
  SAVDOCRM is a multi-tenant retail SaaS platform where users (business owners, managers, cashiers) may belong to one or multiple independent stores.
  Traditional single-token architectures either:
  1. Trust client-sent headers like `X-Tenant-Id`, creating high-risk cross-tenant data vulnerability.
  2. Embed a single tenant ID into the token at login, preventing seamless store switching without re-authenticating and failing when a user belongs to multiple retail locations.
  3. Rely on server-side HTTP sessions, which break horizontal scalability and introduce sticky session dependencies.

- **Decision**:
  Implement a **Stateless Dual-Phase JWT Architecture** backed by cryptographic Refresh Token Rotation (RTR):
  1. **Phase 1 (Global Identity Token)**: Emitted upon initial credential verification (`POST /login`). Carries `sub` (user ID) and user claims, without a tenant scope. Permits identity operations (`/me`, `/select-tenant`).
  2. **Phase 2 (Tenant-Scoped Token)**: Emitted upon explicit store context activation (`POST /select-tenant`). Carries `tenantId` and `membershipId`. Evaluated on every request by `JwtAuthenticationFilter`, which verifies active status and binds `TenantContextHolder` in a fail-closed pattern with `try ... finally { clear(); }`.
  3. **Refresh Token Families & Reuse Detection**: Raw refresh tokens (48 random bytes) are hashed with SHA-256 before persistence. Each session belongs to a `token_family` (UUID). Presenting an already-revoked refresh token immediately invalidates the entire token family, defending against session hijacking.
  4. **Password Security**: BCrypt adaptive password hashing with work factor 12.

- **Consequences**:
  - *Positive*:
    - Zero trust in client headers; active tenant is cryptographically verified from the signed token.
    - Zero server-side session memory on access tokens; fully horizontally scalable.
    - Automatic compromise isolation through token family revocation.
    - Supports multi-store owners seamlessly.
  - *Negative*:
    - Immediate access token revocation requires waiting out the 15-minute expiration or checking a distributed denylist (mitigated by short 15-minute TTL and live membership status verification on each request).
