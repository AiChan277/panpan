package app.panyaya.model.user;

/** Operations that are protected by role. Services call {@code session.require(permission)}. */
public enum Permission {
    VIEW_DASHBOARD,
    MANAGE_PRODUCTS,
    VIEW_PRODUCTS,
    MANAGE_RECIPES,
    VIEW_RECIPES,
    MANAGE_INGREDIENTS,
    VIEW_INVENTORY,
    RECEIVE_STOCK,
    ADJUST_STOCK,
    MANAGE_SUPPLIERS,
    MANAGE_PRODUCTION,
    USE_POS,
    VIEW_ALL_SALES,
    VOID_SALE,
    RECORD_PRODUCT_WASTE,
    RECORD_INGREDIENT_WASTE,
    VIEW_REPORTS,
    MANAGE_USERS,
    MANAGE_SETTINGS,
    MANAGE_BACKUP
}
