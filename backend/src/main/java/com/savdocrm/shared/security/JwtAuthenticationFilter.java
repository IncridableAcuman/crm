package com.savdocrm.shared.security;

import com.savdocrm.modules.identity.domain.MembershipStatus;
import com.savdocrm.modules.identity.domain.TenantMembership;
import com.savdocrm.modules.identity.domain.User;
import com.savdocrm.modules.identity.infrastructure.TenantMembershipRepository;
import com.savdocrm.modules.identity.infrastructure.UserRepository;
import com.savdocrm.modules.tenancy.domain.Tenant;
import com.savdocrm.modules.tenancy.domain.TenantStatus;
import com.savdocrm.modules.tenancy.infrastructure.TenantRepository;
import com.savdocrm.shared.multitenancy.TenantContext;
import com.savdocrm.shared.multitenancy.TenantContextHolder;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final TenantMembershipRepository membershipRepository;
    private final TenantRepository tenantRepository;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String jwt = resolveToken(request);

            if (StringUtils.hasText(jwt)) {
                Optional<Claims> claimsOpt = tokenProvider.parseAndValidateToken(jwt);

                if (claimsOpt.isPresent()) {
                    Claims claims = claimsOpt.get();
                    UUID userId = tokenProvider.extractUserId(claims).orElse(null);
                    String email = claims.get("email", String.class);
                    UUID tenantId = tokenProvider.extractTenantId(claims).orElse(null);
                    UUID membershipId = tokenProvider.extractMembershipId(claims).orElse(null);

                    if (userId != null && email != null) {
                        // 1. Live User status verification
                        User user = userRepository.findById(userId).orElse(null);
                        if (user == null || !user.isActive()) {
                            sendForbiddenError(request, response, "User account is suspended, disabled, or no longer exists.");
                            return;
                        }

                        UserPrincipal principal = UserPrincipal.builder()
                                .id(userId)
                                .email(email)
                                .password("") // Not stored in principal post-auth
                                .activeTenantId(tenantId)
                                .activeMembershipId(membershipId)
                                .active(user.isActive())
                                .build();

                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);

                        MDC.put("userId", userId.toString());

                        // 2. Tenant-scoped verification
                        if (tenantId != null) {
                            Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
                            if (tenant == null || tenant.getStatus() != TenantStatus.ACTIVE) {
                                sendForbiddenError(request, response, "Tenant store is suspended, inactive, or not found.");
                                return;
                            }

                            TenantMembership membership = membershipRepository
                                    .findByTenantIdAndUserId(tenantId, userId)
                                    .orElse(null);

                            if (membership == null || membership.getStatus() != MembershipStatus.ACTIVE) {
                                sendForbiddenError(request, response, "User does not have an active membership in the active store.");
                                return;
                            }

                            // Verify ownership and claim consistency
                            if (!membership.getUserId().equals(userId) || !membership.getTenantId().equals(tenantId)) {
                                sendForbiddenError(request, response, "Tenant membership ownership inconsistency detected.");
                                return;
                            }

                            if (membershipId != null && !membership.getId().equals(membershipId)) {
                                sendForbiddenError(request, response, "Tenant membership claim mismatch.");
                                return;
                            }

                            // Bound only after all checks pass
                            TenantContextHolder.setContext(TenantContext.of(tenantId, "JWT_AUTH"));
                            MDC.put("tenantId", tenantId.toString());
                        }
                    }
                }
            }

            filterChain.doFilter(request, response);
        } finally {
            // Absolute guarantee: ThreadLocal cleaned up after every request
            TenantContextHolder.clear();
            MDC.remove("tenantId");
            MDC.remove("userId");
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private void sendForbiddenError(HttpServletRequest request, HttpServletResponse response, String detail)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, detail);
        problem.setType(URI.create("https://savdocrm.com/errors/forbidden"));
        problem.setTitle("Access Forbidden");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now().toString());

        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }
}
