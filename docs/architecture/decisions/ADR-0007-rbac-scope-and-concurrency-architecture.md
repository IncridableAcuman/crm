# ADR-0007: RBAC Scope Isolation, Concurrency-Safe Owner Protection, and Permission Revocation

- **Status**: Accepted & Implemented (M04.3)
- **Date**: 2026-11-24
- **Context**:
  In Milestone 04, SAVDOCRM transitions from identity authentication to authorization and access management. Initial design discussions revealed four critical architectural challenges:
  1. **Platform vs. Tenant Scope Mixing**: The proposal to represent `PLATFORM` scope within tenant-scoped role tables risked introducing nullable `tenant_id` columns, breaking tenant isolation and threatening cross-tenant data boundaries.
  2. **Premature Warehouse Scope**: Proposing a `warehouse_id` reference in `user_role_assignments` when no `warehouses` table or Inventory bounded context exists in Milestone 01–03 would introduce an unvalidated UUID column without relational integrity checks.
  3. **Concurrent Last Active Owner Race Condition**: Simple `COUNT(*)` checks prior to revoking or suspending `OWNER` roles suffer from Time-Of-Check to Time-Of-Use (TOCTOU) race conditions. Two concurrent requests could both observe `count == 2` and simultaneously remove both owners, leaving an active tenant permanently orphaned with zero active owners.
  4. **Permission Caching vs. Revocation Staleness**: Introducing multi-minute TTL in-memory or distributed caches risks a "stale authorization window" where revoked users retain privileged capabilities. In multi-instance deployments, cache invalidation requires complex distributed pub/sub mechanisms.

- **Decision**:
  1. **Strict Tenant Isolation and Platform Scope Deferral**:
     - `roles.tenant_id` is strictly `UUID NOT NULL`. Every role in the tenant IAM schema belongs to exactly one tenant.
     - Platform-level authorization (SaaS super-admin operations) is isolated from tenant authorization and deferred to a dedicated platform operations milestone with separate tables and bounded contexts.
     - Tenant APIs (`/api/v1/...`) execute strictly within tenant context and have zero access to platform roles.
  2. **Warehouse Scope Deferral**:
     - In Milestone 04, the assignable scope types are strictly **`TENANT`** and **`BRANCH`**.
     - `user_role_assignments` includes `branch_id UUID` constrained by `fk_assignments_branch` referencing `branches(tenant_id, id)`.
     - Database `CHECK` constraints enforce that `branch_id` is null for `TENANT` scope and non-null for `BRANCH` scope.
     - `WAREHOUSE` scope is deferred until the Inventory and Warehousing bounded context is introduced.
  3. **Pessimistic Locking on Parent Tenant for Owner Protection & 6-Predicate Invariant**:
     - Any operation capable of reducing the count of active owners for a tenant (role revocation, assignment deactivation, membership suspension, user suspension) must acquire an exclusive row-level lock on the **parent `Tenant` row** (`SELECT * FROM tenants WHERE id = :tenantId FOR UPDATE;` / `@Lock(LockModeType.PESSIMISTIC_WRITE)`).
     - Because all mutating transactions for a given tenant serialize on that tenant's row, concurrent owner modifications cannot interleave.
     - **The 6-Predicate Eligible Owner Invariant**: An identity satisfies the owner requirement if and only if:
       1. `tenants.status = 'ACTIVE'`
       2. `tenant_memberships.status = 'ACTIVE'`
       3. `users.status = 'ACTIVE'` (global user status)
       4. `roles.code = 'OWNER'` AND `roles.is_active = true`
       5. `user_role_assignments.is_active = true`
       6. `user_role_assignments.scope_type = 'TENANT'`
     - **Multi-Tenant Global User Suspension & Canonical Lock Order (User -> Tenant)**:
       - To serialize global user suspension against concurrent OWNER role assignments without deadlocks, SAVDOCRM enforces a strict global lock hierarchy: **`User` (by userId) FIRST, then `Tenant` (by tenantId in ascending alphanumeric order) SECOND**.
       - `UserService.suspendGlobalUser(userId)` acquires a `PESSIMISTIC_WRITE` lock on the global `users` row first, queries active tenants where the user is an eligible OWNER, sorts tenant UUIDs in ascending order, locks each tenant row sequentially, re-evaluates the 6-predicate count, and aborts with `LastActiveOwnerException` if ANY active tenant would be left without an eligible owner.
       - `RoleAssignmentService.assignRole` (when assigning OWNER) and `TenantBootstrapService.bootstrapExistingTenantOwner` acquire a `PESSIMISTIC_WRITE` lock on the target `users` row first, verify the user is ACTIVE, and then acquire a `PESSIMISTIC_WRITE` lock on the parent `tenants` row.
       - This eliminates the race condition where a global user is suspended while concurrently receiving an OWNER assignment that leaves an active tenant without an eligible owner.
  4. **Request-Scoped Memoization (No Persistent Cache)**:
     - For Milestone 04, permissions are queried from PostgreSQL and memoized strictly within the scope of a single HTTP request (`HttpServletRequest` attribute).
     - Because indexed queries on `(tenant_id, membership_id, is_active)` take < 0.5ms in PostgreSQL 16, this approach achieves sub-millisecond latency without persistent caching overhead.
     - **Revocation Semantics**: Zero stale window. Role revocations, permission modifications, and membership suspensions take effect on the very next HTTP request across all application instances.
  5. **System Role Immutability & Application-Level Enforcement Architecture**:
     - Built-in system roles (`is_system = true`: `OWNER`, `ADMIN`, `MANAGER`, `CASHIER`, `WAREHOUSE_MANAGER`, `ACCOUNTANT`, `VIEWER`) cannot be deleted, and their codes cannot be modified.
     - **Enforcement Layer Distinction**: The `is_system` column is a database-persisted boolean attribute. Immutability is enforced strictly by the Application/Domain layer (`RoleService`, `AuthorizationService` in M04.4).
     - **Trigger Avoidance**: Database-level triggers are intentionally avoided for the modular monolith MVP to prevent hidden business logic, preserve migration predictability, and keep domain invariants in testable Java domain aggregates.
     - `OWNER` permissions are strictly immutable and always grant all system permissions.
     - **The Containment Rule**: An administrator creating/updating custom roles or assigning roles can only grant permissions that they themselves actively hold.
     - **Owner Protection**: Only an active `OWNER` can assign or revoke the `OWNER` role. Self-assignment and self-elevation are prohibited.
  6. **Additive Permissions and Dual Partial Unique Indexes**:
     - Permissions are purely additive capabilities (positive grants with no "deny" rules). Effective permissions are the union of all active assigned roles for a target scope.
     - Uniqueness is enforced via dual partial unique indexes tailored to scope access patterns:
       - `uq_assignments_active_tenant`: `(tenant_id, membership_id, role_id) WHERE is_active = true AND scope_type = 'TENANT'`
       - `uq_assignments_active_branch`: `(tenant_id, membership_id, role_id, branch_id) WHERE is_active = true AND scope_type = 'BRANCH'`
       This eliminates NULL ambiguity, avoids sentinel UUIDs, and optimizes query execution.
  7. **Canonical Permission Catalog and Existing Tenant Bootstrap**:
     - Canonical dot notation `<module>.<resource>.<action>` is enforced at the database level via CHECK constraint (`chk_permissions_code_format`).
     - Existing tenants receive system roles via migration `v1.0.0-14-seed-system-roles`, but the migration does not invent or guess an owner identity. Existing tenants lacking an active OWNER assignment are safely detected via fail-closed queries (checking all 6 invariant predicates) and require explicit administrative bootstrap.
  8. **Foreign Key Deletion Policy and Historical Audit Preservation**:
     - **Preservation of Audit History**: `fk_assignments_role` (`user_role_assignments -> roles`) and `fk_assignments_branch` (`user_role_assignments -> branches`) use `ON DELETE RESTRICT` (migration 15). Physical deletion of a role or branch that possesses active or historical assignments is rejected at the database level, preventing silent erasure of past privilege records. Roles and branches must be soft-deactivated (`is_active = false`).
     - **Permission Catalog Integrity**: `fk_role_permissions_perm` (`role_permissions -> permissions`) uses `ON DELETE RESTRICT`, physically preventing global permission definitions from being deleted while referenced by any role. Permissions use an inactivation/deprecation lifecycle rather than physical deletion.
     - **Tenant Decommissioning**: Foreign keys from child tables directly to `tenants(id)` (`roles`, `branches`, `tenant_memberships`, `role_permissions`, `user_role_assignments`) retain `ON DELETE CASCADE` to enable clean hard deletion when an entire tenant is decommissioned.

- **Consequences**:
  - *Positive*:
    - Guarantees 100% database-level tenant isolation with zero risk of cross-tenant leakage.
    - Prevents orphan tenant lockouts under high-concurrency administrative actions.
    - Eliminates stale authorization vulnerabilities with zero distributed cache complexity.
    - Preserves historical security audit trails against accidental role or branch deletion.
    - Protects the global permissions catalog against silent cascade deletion.
    - Maintains clean aggregate boundaries without premature database dependencies.
    - Completely prevents administrative privilege escalation.
  - *Negative*:
    - Minor serialization delay on the parent `Tenant` row during owner role assignment mutations (acceptable because owner role changes are rare administrative events).
    - Database query executed once per unique HTTP request for permission resolution (benchmarked at < 0.3ms with indexed scans).
    - Roles and branches with historical assignments cannot be physically purged (must remain as soft-deactivated records).
    - Warehouse-specific role assignments cannot be configured until the Inventory module is implemented in a future milestone.
