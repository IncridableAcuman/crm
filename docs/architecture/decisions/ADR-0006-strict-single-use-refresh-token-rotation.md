# ADR-0006: Strict Single-Use Refresh Token Rotation and Cross-Tab Coordination

- **Status**: Accepted
- **Date**: 2026-11-23
- **Context**:
  In Milestone 03, SAVDOCRM initially introduced a 10-second grace window with AES-256-GCM encrypted successor recovery to handle concurrent tab refreshes. However, architectural security review revealed that client IP and User-Agent fingerprints cannot cryptographically prove sender identity over shared public NATs or corporate proxies. An attacker holding a stolen bearer refresh token could replay it from behind the same public IP with spoofed headers to recover the successor token during the grace window.
  Furthermore, storing encrypted successor tokens in PostgreSQL created unnecessary cryptographic key management overhead and lingering sensitive ciphertext in persistent storage.

- **Decision**:
  Implement **Strict Single-Use Refresh Token Rotation (RTR)** with absolute zero grace window:
  1. **Strict Single-Use Consumption**: Every refresh token can be successfully consumed exactly once.
  2. **Zero-Grace Rotation**: A revoked refresh token is NEVER accepted and NEVER returns its successor under any circumstance.
  3. **Immediate Family Revocation on Replay**: Any presentation of an already-revoked refresh token is treated as an active session hijacking signal and immediately invalidates the entire `token_family` in PostgreSQL with row-level locking (`PESSIMISTIC_WRITE`).
  4. **Intentional Lost Response Behavior**: If the server commits token rotation but the HTTP response is lost over the network, retrying the old token is considered reuse and triggers full family revocation. The user must re-authenticate. This is an intentional security-first trade-off prioritizing compromise quarantine over transient network recovery.
  5. **Decommissioning of Token Encryption**: Encrypted successor token fields (`successor_token_encrypted`) and `TokenEncryptionService` are removed via a safe Liquibase migration (`v1.0.0-07-remove-successor-token-encrypted.xml`). The cryptographic hash of the successor (`successor_token_hash`) is preserved for forensic lineage tracking.
  6. **Frontend Mutual Exclusion**: Cross-tab concurrency is handled client-side using the **Web Locks API** (`navigator.locks.request('savdocrm_refresh_lock')`) and **BroadcastChannel** (`savdocrm_auth_channel`). Only one tab initiates rotation; peer tabs wait for the lock, detect the refreshed access token, and avoid redundant server requests. Crashed tabs automatically release browser locks.
  7. **Missed Broadcast Defense**: A non-token shared session metadata marker in `localStorage` (`savdocrm_session_state`) prevents any tab from reusing stale in-memory tokens or firing invalid refreshes if it was asleep during a broadcast.
  8. **Zero Token Persistence**: Neither access tokens nor refresh tokens are EVER stored in `localStorage` or `sessionStorage`. Refresh tokens are stored exclusively in HttpOnly cookies, and access tokens in memory.
  9. **Trusted Reverse Proxy IP Verification**: `ClientIpResolver` ensures `X-Forwarded-For` is only parsed when the connection originates from configured trusted reverse proxies (`security.trusted-proxies`).
  10. **Automated Scheduled Retention Cleanup**: Scheduled background task (`RefreshTokenCleanupTask` via `@EnableScheduling`) purges expired revoked tokens exceeding the 90-day retention cutoff, backed by composite index `idx_refresh_tokens_cleanup`.
  11. **Cross-Tab Storage Event Synchronization**: `storage` event listeners guarantee cross-tab session termination in real time, while `authApi` automatically synchronizes session metadata across login, register, refresh, and logout lifecycles.

- **Consequences**:
  - *Positive*:
    - Eliminates replay vulnerability during grace windows.
    - Removes sensitive encrypted token ciphertext from PostgreSQL storage.
    - Strong transactional consistency through row-level locking and atomic family revocation (`noRollbackFor = BadCredentialsException.class`).
    - Eliminates multi-tab race conditions through native Web Locks API.
  - *Negative*:
    - Severe network drops after server commit require re-login (intentional security-first trade-off).
