package com.savdocrm.modules.identity.application.dto;

import com.savdocrm.modules.identity.domain.MembershipStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserTenantMembershipDto {

    private UUID tenantId;
    private String tenantName;
    private String tenantCode;
    private MembershipStatus membershipStatus;
    private boolean current;
}
