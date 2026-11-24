package com.savdocrm.modules.identity.application;

import com.savdocrm.modules.identity.domain.TenantMembership;
import com.savdocrm.modules.identity.domain.User;
import com.savdocrm.modules.identity.domain.UserStatus;
import com.savdocrm.modules.identity.domain.exception.CrossTenantAssignmentException;
import com.savdocrm.modules.identity.domain.exception.LastActiveOwnerException;
import com.savdocrm.modules.identity.domain.exception.PermissionDeniedException;
import com.savdocrm.modules.identity.infrastructure.TenantMembershipRepository;
import com.savdocrm.modules.identity.infrastructure.UserRepository;
import com.savdocrm.modules.identity.infrastructure.persistence.UserRoleAssignmentRepository;
import com.savdocrm.modules.tenancy.domain.Tenant;
import com.savdocrm.modules.tenancy.infrastructure.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Application service for user and membership lifecycle operations,
 * enforcing multi-tenant deterministic locking and last active owner protection.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final TenantMembershipRepository tenantMembershipRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;

    /**
     * Suspends a global user across the entire platform.
     * Enforces the multi-tenant deterministic locking protocol:
     * 1. Query all affected active tenants where the user holds an eligible OWNER assignment.
     * 2. Sort tenant UUIDs in ascending deterministic order.
     * 3. Acquire pessimistic write locks sequentially on each parent tenant row.
     * 4. Re-evaluate eligible OWNER count for each tenant after lock acquisition.
     * 5. Reject suspension with LastActiveOwnerException if ANY active tenant would be left without an eligible OWNER.
     * 6. Suspend the user in the same transaction.
     */
    @Transactional
    public void suspendGlobalUser(UUID targetUserId) {
        log.info("Request to suspend global user: {}", targetUserId);

        // 1. Lock the global user row first to serialize against concurrent OWNER assignments
        User user = userRepository.findByIdWithLock(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + targetUserId));

        if (user.getStatus() == UserStatus.SUSPENDED) {
            log.info("User {} is already suspended. Idempotent return.", targetUserId);
            return;
        }

        // 1. Identify all active tenants where target user is an eligible active OWNER
        List<UUID> affectedTenantIds = userRoleAssignmentRepository.findActiveTenantIdsWhereUserIsEligibleOwner(targetUserId);

        // 2. Deterministic UUID ordering to prevent deadlocks
        List<UUID> sortedTenantIds = affectedTenantIds.stream().sorted().toList();

        // 3. Sequentially lock parent tenant rows and re-evaluate owner count
        for (UUID tenantId : sortedTenantIds) {
            Tenant tenant = tenantRepository.findByIdWithLock(tenantId)
                    .orElseThrow(() -> new IllegalStateException("Tenant not found with ID: " + tenantId));

            if (!tenant.isActive()) {
                continue;
            }

            // Re-evaluate eligible owner count excluding target user
            long remainingOwners = userRoleAssignmentRepository.countEligibleOwners(tenantId, null, null, targetUserId);
            if (remainingOwners == 0) {
                log.warn("Cannot suspend user {}: active tenant {} would lose its final active eligible OWNER.", targetUserId, tenantId);
                throw new LastActiveOwnerException(tenantId);
            }
        }

        // 4. Suspend user within the same transaction
        user.suspend();
        userRepository.save(user);
        log.info("Successfully suspended global user: {}", targetUserId);
    }

    /**
     * Activates a suspended global user.
     */
    @Transactional
    public void activateGlobalUser(UUID targetUserId) {
        log.info("Activating global user: {}", targetUserId);

        // 1. Lock the global user row during activation
        User user = userRepository.findByIdWithLock(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + targetUserId));

        user.activate();
        userRepository.save(user);
        log.info("Successfully activated global user: {}", targetUserId);
    }

    /**
     * Suspend a tenant membership with pessimistic lock and last active owner protection.
     */
    @Transactional
    public void suspendTenantMembership(UUID tenantId, UUID targetMembershipId, UUID actorUserId) {
        log.info("Suspending membership {} in tenant {} by actor {}", targetMembershipId, tenantId, actorUserId);

        Tenant tenant = tenantRepository.findByIdWithLock(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found with ID: " + tenantId));

        TenantMembership membership = tenantMembershipRepository.findById(targetMembershipId)
                .orElseThrow(() -> new IllegalArgumentException("Membership not found with ID: " + targetMembershipId));

        if (!membership.getTenantId().equals(tenantId)) {
            throw new CrossTenantAssignmentException("Membership does not belong to tenant: " + tenantId);
        }

        if (!membership.isActive()) {
            return;
        }

        // Re-evaluate eligible owner count excluding this membership
        long remainingOwners = userRoleAssignmentRepository.countEligibleOwners(tenantId, targetMembershipId, null, null);
        if (remainingOwners == 0) {
            log.warn("Cannot suspend membership {}: tenant {} would lose its final active eligible OWNER.", targetMembershipId, tenantId);
            throw new LastActiveOwnerException(tenantId);
        }

        membership.suspend();
        membership.setUpdatedBy(actorUserId);
        tenantMembershipRepository.save(membership);
        log.info("Successfully suspended membership {}", targetMembershipId);
    }

    /**
     * Activates a suspended tenant membership.
     */
    @Transactional
    public void activateTenantMembership(UUID tenantId, UUID targetMembershipId, UUID actorUserId) {
        log.info("Activating membership {} in tenant {} by actor {}", targetMembershipId, tenantId, actorUserId);

        TenantMembership membership = tenantMembershipRepository.findById(targetMembershipId)
                .orElseThrow(() -> new IllegalArgumentException("Membership not found with ID: " + targetMembershipId));

        if (!membership.getTenantId().equals(tenantId)) {
            throw new CrossTenantAssignmentException("Membership does not belong to tenant: " + tenantId);
        }

        membership.activate();
        membership.setUpdatedBy(actorUserId);
        tenantMembershipRepository.save(membership);
        log.info("Successfully activated membership {}", targetMembershipId);
    }
}
