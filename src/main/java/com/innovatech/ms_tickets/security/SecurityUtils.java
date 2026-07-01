package com.innovatech.ms_tickets.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class SecurityUtils {

    public AuthenticatedUser requireUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no autenticado");
        }
        return user;
    }

    public void requireOwnerOrAdmin(AuthenticatedUser user, Long ownerUserId) {
        if (user.hasRole("ADMIN")) {
            return;
        }

        if (ownerUserId == null || !ownerUserId.equals(user.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes acceder a registros de otro usuario");
        }
    }
}
