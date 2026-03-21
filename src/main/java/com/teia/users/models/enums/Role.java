package com.teia.users.models.enums;

/**
 * User roles for TEIA platform authorization.
 * ADMIN: Full access to all resources and operations
 * OPERATOR: Can manage contacts, threads, and credentials
 * CLIENT: Read-only access to own data
 */
public enum Role {
    ADMIN,
    OPERATOR,
    CLIENT
}
