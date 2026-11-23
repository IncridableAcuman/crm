package com.savdocrm.modules.identity.application;

import com.savdocrm.modules.identity.application.dto.AuthResponse;
import com.savdocrm.modules.identity.application.dto.LoginRequest;
import com.savdocrm.modules.identity.application.dto.RefreshTokenRequest;
import com.savdocrm.modules.identity.application.dto.RegisterRequest;
import com.savdocrm.modules.identity.application.dto.SelectTenantRequest;
import com.savdocrm.modules.identity.application.dto.UserProfileDto;
import com.savdocrm.modules.identity.application.dto.UserTenantMembershipDto;
import com.savdocrm.modules.identity.domain.MembershipStatus;
import com.savdocrm.modules.identity.domain.RefreshToken;
import com.savdocrm.modules.identity.domain.User;
import com.savdocrm.modules.identity.domain.UserStatus;
import com.savdocrm.modules.identity.infrastructure.RefreshTokenRepository;
import com.savdocrm.modules.identity.infrastructure.TenantMembershipRepository;
import com.savdocrm.modules.identity.infrastructure.UserRepository;
import com.savdocrm.modules.identity.domain.TenantMembership;
import com.savdocrm.modules.tenancy.application.BranchService;
import com.savdocrm.modules.tenancy.application.dto.CreateBranchCommand;
import com.savdocrm.modules.tenancy.domain.Tenant;
import com.savdocrm.modules.tenancy.domain.TenantStatus;
import com.savdocrm.modules.tenancy.infrastructure.TenantRepository;
import com.savdocrm.shared.multitenancy.TenantExecutionTemplate;
import com.savdocrm.shared.security.JwtProperties;
import com.savdocrm.shared.security.JwtTokenProvider;
import com.savdocrm.shared.security.TokenEncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final TenantMembershipRepository tenantMembershipRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TenantRepository tenantRepository;
    private final BranchService branchService;
    private final TenantExecutionTemplate tenantExecutionTemplate;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final PasswordEncoder passwordEncoder;
    private final TokenEncryptionService tokenEncryptionService;

    @Transactional
    public AuthResponse register(RegisterRequest request, String ipAddress, String userAgent) {
        String normalizedEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);
        String username = request.getUsername().trim();

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new IllegalArgumentException("An account with email '" + normalizedEmail + "' already exists.");
        }

        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username '" + username + "' is already taken.");
        }

        User user = User.builder()
                .email(normalizedEmail)
                .username(username)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .phoneNumber(request.getPhoneNumber() != null ? request.getPhoneNumber().trim() : null)
                .status(UserStatus.ACTIVE)
                .emailVerified(false)
                .build();

        User savedUser = userRepository.save(user);

        UUID createdTenantId = null;
        UUID createdMembershipId = null;

        if (request.getStoreName() != null && !request.getStoreName().trim().isBlank()) {
            String storeName = request.getStoreName().trim();
            String tenantCode = generateTenantCode(storeName);

            Tenant tenant = Tenant.builder()
                    .name(storeName)
                    .code(tenantCode)
                    .currencyCode("USD")
                    .timezone("UTC")
                    .status(TenantStatus.ACTIVE)
                    .build();

            Tenant savedTenant = tenantRepository.save(tenant);
            createdTenantId = savedTenant.getId();

            // Create main branch using tenant execution context
            tenantExecutionTemplate.executeAsTenant(createdTenantId, () -> {
                branchService.createBranch(new CreateBranchCommand(
                        "Main Store",
                        "MAIN",
                        null,
                        null,
                        true
                ));
            });

            TenantMembership membership = TenantMembership.builder()
                    .userId(savedUser.getId())
                    .tenantId(createdTenantId)
                    .status(MembershipStatus.ACTIVE)
                    .build();

            TenantMembership savedMembership = tenantMembershipRepository.save(membership);
            createdMembershipId = savedMembership.getId();
        }

        UUID tokenFamily = UUID.randomUUID();
        String rawRefreshToken = jwtTokenProvider.generateRawRefreshToken();
        createAndSaveRefreshToken(savedUser.getId(), createdTenantId, tokenFamily, rawRefreshToken, ipAddress, userAgent);

        String accessToken;
        if (createdTenantId != null) {
            accessToken = jwtTokenProvider.generateTenantAccessToken(
                    savedUser.getId(), savedUser.getEmail(), createdTenantId, createdMembershipId);
        } else {
            accessToken = jwtTokenProvider.generateGlobalAccessToken(savedUser.getId(), savedUser.getEmail());
        }

        List<UserTenantMembershipDto> availableTenants = buildAvailableTenants(savedUser.getId(), createdTenantId);
        UserTenantMembershipDto activeTenant = availableTenants.stream()
                .filter(UserTenantMembershipDto::isCurrent)
                .findFirst()
                .orElse(null);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getAccessTokenExpirationSeconds())
                .user(toUserProfileDto(savedUser))
                .activeTenant(activeTenant)
                .availableTenants(availableTenants)
                .build();
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String ipAddress, String userAgent) {
        String identifier = request.getEmailOrUsername().trim();

        User user = userRepository.findByEmailIgnoreCase(identifier)
                .or(() -> userRepository.findByUsername(identifier))
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        if (!user.isActive()) {
            throw new DisabledException("User account is disabled or suspended.");
        }

        UUID tokenFamily = UUID.randomUUID();
        String rawRefreshToken = jwtTokenProvider.generateRawRefreshToken();
        createAndSaveRefreshToken(user.getId(), null, tokenFamily, rawRefreshToken, ipAddress, userAgent);

        String accessToken = jwtTokenProvider.generateGlobalAccessToken(user.getId(), user.getEmail());
        List<UserTenantMembershipDto> availableTenants = buildAvailableTenants(user.getId(), null);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getAccessTokenExpirationSeconds())
                .user(toUserProfileDto(user))
                .activeTenant(null)
                .availableTenants(availableTenants)
                .build();
    }

    @Transactional
    public AuthResponse selectTenant(UUID userId, SelectTenantRequest request, String ipAddress, String userAgent) {
        UUID tenantId = request.getTenantId();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        if (!user.isActive()) {
            throw new DisabledException("User account is disabled or suspended.");
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant store not found"));

        if (tenant.getStatus() != TenantStatus.ACTIVE) {
            throw new AccessDeniedException("Tenant store is currently suspended or inactive.");
        }

        TenantMembership membership = tenantMembershipRepository.findByTenantIdAndUserId(tenantId, userId)
                .orElseThrow(() -> new AccessDeniedException("User does not have membership in this store."));

        if (membership.getStatus() != MembershipStatus.ACTIVE) {
            throw new AccessDeniedException("User membership in this store is currently not active.");
        }

        UUID tokenFamily = UUID.randomUUID();
        String rawRefreshToken = jwtTokenProvider.generateRawRefreshToken();
        createAndSaveRefreshToken(user.getId(), tenantId, tokenFamily, rawRefreshToken, ipAddress, userAgent);

        String accessToken = jwtTokenProvider.generateTenantAccessToken(
                user.getId(), user.getEmail(), tenantId, membership.getId());

        List<UserTenantMembershipDto> availableTenants = buildAvailableTenants(user.getId(), tenantId);
        UserTenantMembershipDto activeTenant = availableTenants.stream()
                .filter(UserTenantMembershipDto::isCurrent)
                .findFirst()
                .orElse(null);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getAccessTokenExpirationSeconds())
                .user(toUserProfileDto(user))
                .activeTenant(activeTenant)
                .availableTenants(availableTenants)
                .build();
    }

    @Transactional(noRollbackFor = BadCredentialsException.class)
    public AuthResponse refreshToken(RefreshTokenRequest request, String ipAddress, String userAgent) {
        String rawToken = request != null ? request.getRefreshToken() : null;
        return refreshToken(rawToken, ipAddress, userAgent);
    }

    @Transactional(noRollbackFor = BadCredentialsException.class)
    public AuthResponse refreshToken(String rawToken, String ipAddress, String userAgent) {
        if (rawToken == null || rawToken.trim().isBlank()) {
            throw new BadCredentialsException("Refresh token is required.");
        }

        String tokenHash = jwtTokenProvider.hashToken(rawToken.trim());

        // Pessimistic write row-level lock serializes concurrent refresh attempts
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHashWithLock(tokenHash)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token."));

        // Refresh token reuse detection with concurrency grace window
        if (refreshToken.isRevoked()) {
            boolean isRecentRotation = "Rotated".equals(refreshToken.getRevokedReason())
                    && refreshToken.getRevokedAt() != null
                    && refreshToken.getRevokedAt().isAfter(Instant.now().minusSeconds(10));

            if (isRecentRotation && refreshToken.getSuccessorTokenEncrypted() != null) {
                log.debug("Concurrent refresh request within grace window for token family {}", refreshToken.getTokenFamily());

                try {
                    String decryptedSuccessorToken = tokenEncryptionService.decrypt(refreshToken.getSuccessorTokenEncrypted());
                    String expectedSuccessorHash = refreshToken.getSuccessorTokenHash();

                    if (decryptedSuccessorToken != null && expectedSuccessorHash != null) {
                        String actualDecryptedHash = jwtTokenProvider.hashToken(decryptedSuccessorToken);
                        if (expectedSuccessorHash.equals(actualDecryptedHash)) {
                            Optional<RefreshToken> successorOpt = refreshTokenRepository.findByTokenHash(expectedSuccessorHash);
                            if (successorOpt.isPresent() && successorOpt.get().isValid()
                                    && successorOpt.get().getTokenFamily().equals(refreshToken.getTokenFamily())) {
                                RefreshToken activeSuccessor = successorOpt.get();
                                User user = userRepository.findById(activeSuccessor.getUserId())
                                        .orElseThrow(() -> new BadCredentialsException("User not found."));

                                if (!user.isActive()) {
                                    throw new DisabledException("User account is disabled or suspended.");
                                }

                                UUID tenantId = activeSuccessor.getTenantId();
                                String accessToken;
                                if (tenantId != null) {
                                    TenantMembership membership = tenantMembershipRepository
                                            .findByTenantIdAndUserId(tenantId, user.getId())
                                            .filter(TenantMembership::isActive)
                                            .orElseThrow(() -> new AccessDeniedException("User membership is inactive."));

                                    accessToken = jwtTokenProvider.generateTenantAccessToken(
                                            user.getId(), user.getEmail(), tenantId, membership.getId());
                                } else {
                                    accessToken = jwtTokenProvider.generateGlobalAccessToken(user.getId(), user.getEmail());
                                }

                                List<UserTenantMembershipDto> availableTenants = buildAvailableTenants(user.getId(), tenantId);
                                UserTenantMembershipDto activeTenant = availableTenants.stream()
                                        .filter(UserTenantMembershipDto::isCurrent)
                                        .findFirst()
                                        .orElse(null);

                                return AuthResponse.builder()
                                        .accessToken(accessToken)
                                        .refreshToken(decryptedSuccessorToken)
                                        .tokenType("Bearer")
                                        .expiresIn(jwtProperties.getAccessTokenExpirationSeconds())
                                        .user(toUserProfileDto(user))
                                        .activeTenant(activeTenant)
                                        .availableTenants(availableTenants)
                                        .build();
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to recover successor token during grace window: {}", e.getMessage());
                }
            }

            log.warn("Revoked refresh token presented outside grace window! Revoking entire token family {}", refreshToken.getTokenFamily());
            refreshTokenRepository.revokeTokenFamily(
                    refreshToken.getTokenFamily(), Instant.now(), "Token reuse detected");
            throw new BadCredentialsException("Invalid refresh token: token reuse detected.");
        }

        if (refreshToken.isExpired()) {
            refreshToken.revoke("Expired");
            refreshTokenRepository.save(refreshToken);
            throw new BadCredentialsException("Refresh token has expired.");
        }

        User user = userRepository.findById(refreshToken.getUserId())
                .orElseThrow(() -> new BadCredentialsException("User not found."));

        if (!user.isActive()) {
            throw new DisabledException("User account is disabled or suspended.");
        }

        UUID tenantId = refreshToken.getTenantId();
        UUID membershipId = null;

        if (tenantId != null) {
            Tenant tenant = tenantRepository.findById(tenantId)
                    .orElseThrow(() -> new AccessDeniedException("Associated tenant store no longer exists."));

            if (tenant.getStatus() != TenantStatus.ACTIVE) {
                throw new AccessDeniedException("Tenant store is currently suspended.");
            }

            TenantMembership membership = tenantMembershipRepository.findByTenantIdAndUserId(tenantId, user.getId())
                    .orElseThrow(() -> new AccessDeniedException("User no longer has membership in this store."));

            if (membership.getStatus() != MembershipStatus.ACTIVE) {
                throw new AccessDeniedException("User membership in this store is not active.");
            }

            membershipId = membership.getId();
        }

        // Issue new refresh token within the same token family
        String newRawRefreshToken = jwtTokenProvider.generateRawRefreshToken();
        String newSuccessorHash = jwtTokenProvider.hashToken(newRawRefreshToken);
        createAndSaveRefreshToken(
                user.getId(), tenantId, refreshToken.getTokenFamily(), newRawRefreshToken, ipAddress, userAgent);

        // Encrypt the successor token using AES-256-GCM for idempotent grace window recovery
        String encryptedSuccessor = tokenEncryptionService.encrypt(newRawRefreshToken);
        refreshToken.rotate(encryptedSuccessor, newSuccessorHash);
        refreshTokenRepository.save(refreshToken);

        String newAccessToken;
        if (tenantId != null) {
            newAccessToken = jwtTokenProvider.generateTenantAccessToken(
                    user.getId(), user.getEmail(), tenantId, membershipId);
        } else {
            newAccessToken = jwtTokenProvider.generateGlobalAccessToken(user.getId(), user.getEmail());
        }

        List<UserTenantMembershipDto> availableTenants = buildAvailableTenants(user.getId(), tenantId);
        UserTenantMembershipDto activeTenant = availableTenants.stream()
                .filter(UserTenantMembershipDto::isCurrent)
                .findFirst()
                .orElse(null);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRawRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getAccessTokenExpirationSeconds())
                .user(toUserProfileDto(user))
                .activeTenant(activeTenant)
                .availableTenants(availableTenants)
                .build();
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            String tokenHash = jwtTokenProvider.hashToken(rawRefreshToken.trim());
            refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
                refreshTokenRepository.revokeTokenFamily(token.getTokenFamily(), Instant.now(), "User logout");
            });
        }
    }

    @Transactional(readOnly = true)
    public AuthResponse getCurrentUser(UUID userId, UUID activeTenantId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        List<UserTenantMembershipDto> availableTenants = buildAvailableTenants(user.getId(), activeTenantId);
        UserTenantMembershipDto activeTenant = availableTenants.stream()
                .filter(UserTenantMembershipDto::isCurrent)
                .findFirst()
                .orElse(null);

        return AuthResponse.builder()
                .accessToken(null)
                .refreshToken(null)
                .tokenType("Bearer")
                .expiresIn(0)
                .user(toUserProfileDto(user))
                .activeTenant(activeTenant)
                .availableTenants(availableTenants)
                .build();
    }

    private void createAndSaveRefreshToken(UUID userId, UUID tenantId, UUID tokenFamily,
                                           String rawToken, String ipAddress, String userAgent) {
        String tokenHash = jwtTokenProvider.hashToken(rawToken);
        Instant expiresAt = Instant.now().plusSeconds(jwtProperties.getRefreshTokenExpirationSeconds());

        RefreshToken refreshToken = RefreshToken.builder()
                .userId(userId)
                .tenantId(tenantId)
                .tokenFamily(tokenFamily)
                .tokenHash(tokenHash)
                .expiresAt(expiresAt)
                .createdIp(ipAddress)
                .userAgent(userAgent)
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshToken);
    }

    private List<UserTenantMembershipDto> buildAvailableTenants(UUID userId, UUID activeTenantId) {
        List<TenantMembership> memberships = tenantMembershipRepository.findAllByUserId(userId);
        List<UserTenantMembershipDto> list = new ArrayList<>();

        for (TenantMembership membership : memberships) {
            tenantRepository.findById(membership.getTenantId()).ifPresent(tenant -> {
                boolean isCurrent = activeTenantId != null && activeTenantId.equals(tenant.getId());
                list.add(UserTenantMembershipDto.builder()
                        .tenantId(tenant.getId())
                        .tenantName(tenant.getName())
                        .tenantCode(tenant.getCode())
                        .membershipStatus(membership.getStatus())
                        .current(isCurrent)
                        .build());
            });
        }

        return list;
    }

    private UserProfileDto toUserProfileDto(User user) {
        return UserProfileDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .username(user.getUsername())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phoneNumber(user.getPhoneNumber())
                .build();
    }

    private String generateTenantCode(String storeName) {
        String base = storeName.replaceAll("[^a-zA-Z0-9]", "").toUpperCase(Locale.ROOT);
        if (base.length() < 3) {
            base = "STORE";
        }
        if (base.length() > 8) {
            base = base.substring(0, 8);
        }

        String code = base;
        int counter = 1;
        while (tenantRepository.existsByCode(code)) {
            code = base + counter;
            counter++;
        }
        return code;
    }
}
