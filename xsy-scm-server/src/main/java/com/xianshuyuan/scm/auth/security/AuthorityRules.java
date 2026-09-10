package com.xianshuyuan.scm.auth.security;

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.Authentication;

import java.util.function.Supplier;

public final class AuthorityRules {

    private static final String ADMINISTRATOR_AUTHORITY = "system.administrator";

    private AuthorityRules() {
    }

    public static boolean isAdministrator(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return false;
        Object principal = authentication.getPrincipal();
        if (principal instanceof SystemUserDetails details) return details.getUser().administrator();
        return principal instanceof AuthenticatedUser user && user.administrator();
    }

    public static boolean isReservedPermission(String code, boolean systemPermission) {
        String normalized = code.strip().toLowerCase(java.util.Locale.ROOT);
        // Legacy core identities come from SecurityConfig's explicit route registry, not
        // dotted syntax. Migrations identify newer core permissions with system_permission.
        return systemPermission || normalized.startsWith("system:") || java.util.Set.of(
                "system.administrator", "system.manage", "product.read", "product.manage",
                "customer.read", "customer.manage", "order.read", "order.manage",
                "supplier.read", "supplier.manage", "purchase.read", "purchase.manage",
                "inventory.read").contains(normalized);
    }

    public static boolean isReservedRole(String code, boolean systemRole) {
        return systemRole || java.util.Set.of("admin", "administrator", "system", "root", "superadmin")
                .contains(code.strip().toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * Losing any current capability through one's own destructive security write is forbidden.
     * Flag-based administrators retain their independent central bypass.
     */
    public static boolean canRemoveOwnPermissions(Authentication actor, java.util.Collection<String> lost) {
        return isAdministrator(actor) || lost.isEmpty();
    }

    public static AuthorizationDecision hasAuthority(
            Supplier<? extends Authentication> authenticationSupplier,
            String authority
    ) {
        Authentication authentication = authenticationSupplier.get();
        boolean granted = authentication != null
                && authentication.isAuthenticated()
                && (isAdministrator(authentication)
                || (!ADMINISTRATOR_AUTHORITY.equals(authority)
                && authentication.getAuthorities().stream().anyMatch(candidate ->
                authority.equals(candidate.getAuthority()))));
        return new AuthorizationDecision(granted);
    }
}
