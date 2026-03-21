package com.teia.users.security;

import com.teia.users.models.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

/**
 * Principal object representing the authenticated user from JWT claims.
 * Used in the SecurityContext to provide user information to controllers.
 */
@Data
@AllArgsConstructor
public class JwtUserPrincipal implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private String email;
    private Role role;
}
