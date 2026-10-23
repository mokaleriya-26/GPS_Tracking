package com.gps.tracking.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static UserPrincipal getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal) {
            return (UserPrincipal) auth.getPrincipal();
        }
        return null;
    }

    public static Long getCurrentDriverId() {
        UserPrincipal user = getCurrentUser();
        return user != null ? user.getDriverId() : null;
    }

    public static boolean isAdmin() {
        UserPrincipal user = getCurrentUser();
        return user != null && user.isAdmin();
    }

    public static boolean isDriver() {
        UserPrincipal user = getCurrentUser();
        return user != null && user.isDriver();
    }

    /**
     * Enforces data isolation:
     * If the current user is a driver (USER role), verifies that the requested driverId
     * strictly matches the authenticated driver's ID.
     * Admins are permitted full access.
     */
    public static void validateDriverAccess(Long requestedDriverId) {
        UserPrincipal user = getCurrentUser();
        if (user == null) {
            // Unauthenticated or system access
            return;
        }

        if (user.isAdmin()) {
            return; // Admins have fleet-wide access
        }

        if (user.isDriver()) {
            if (requestedDriverId == null || !requestedDriverId.equals(user.getDriverId())) {
                throw new AccessDeniedException("Data isolation violation: You are not authorized to view or access records for another driver.");
            }
        }
    }

    /**
     * Enforces admin-only access on management operations.
     */
    public static void requireAdmin() {
        UserPrincipal user = getCurrentUser();
        if (user == null || !user.isAdmin()) {
            throw new AccessDeniedException("Administrative privileges required for this operation.");
        }
    }
}
