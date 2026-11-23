package com.savdocrm.modules.identity.application.dto;

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
public class UserProfileDto {

    private UUID id;
    private String email;
    private String username;
    private String firstName;
    private String lastName;
    private String phoneNumber;
}
