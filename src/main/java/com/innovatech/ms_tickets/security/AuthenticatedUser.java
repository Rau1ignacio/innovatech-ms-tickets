package com.innovatech.ms_tickets.security;

public record AuthenticatedUser(Long userId, String email, String role) {

    public boolean hasRole(String expectedRole) {
        return role != null && role.equalsIgnoreCase(expectedRole);
    }
}
