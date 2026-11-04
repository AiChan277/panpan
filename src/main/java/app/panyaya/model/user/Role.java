package app.panyaya.model.user;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * User roles. A role is <i>data</i> attached to a {@link User} (composition), not a subclass
 * such as "Cashier extends Employee": a person's role can change without creating a new object.
 * Each role owns an {@link EnumSet} of permissions (a {@link Set} implementation for enums).
 */
public enum Role {
    OWNER("Owner / Manager", EnumSet.allOf(Permission.class)),

    BAKER("Baker", EnumSet.of(
            Permission.VIEW_PRODUCTS, Permission.VIEW_RECIPES, Permission.MANAGE_RECIPES,
            Permission.VIEW_INVENTORY, Permission.RECEIVE_STOCK, Permission.MANAGE_PRODUCTION,
            Permission.RECORD_PRODUCT_WASTE, Permission.RECORD_INGREDIENT_WASTE, Permission.VIEW_REPORTS)),

    CASHIER("Cashier", EnumSet.of(
            Permission.VIEW_PRODUCTS, Permission.USE_POS, Permission.RECORD_PRODUCT_WASTE));

    private final String label;
    private final Set<Permission> permissions;

    Role(String label, Set<Permission> permissions) {
        this.label = label;
        this.permissions = Collections.unmodifiableSet(permissions);
    }

    public boolean hasPermission(Permission permission) {
        return permissions.contains(permission);
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }

    @Override
    public String toString() {
        return label;
    }
}
