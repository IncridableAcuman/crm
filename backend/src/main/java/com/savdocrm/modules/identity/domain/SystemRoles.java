package com.savdocrm.modules.identity.domain;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Built-in system role codes and canonical permission grant templates.
 */
public final class SystemRoles {

    private SystemRoles() {
    }

    public static final String OWNER = "OWNER";
    public static final String ADMIN = "ADMIN";
    public static final String MANAGER = "MANAGER";
    public static final String CASHIER = "CASHIER";
    public static final String WAREHOUSE_MANAGER = "WAREHOUSE_MANAGER";
    public static final String ACCOUNTANT = "ACCOUNTANT";
    public static final String VIEWER = "VIEWER";

    public static final Set<String> ALL_SYSTEM_ROLES = Collections.unmodifiableSet(Set.of(
            OWNER, ADMIN, MANAGER, CASHIER, WAREHOUSE_MANAGER, ACCOUNTANT, VIEWER
    ));

    public static boolean isSystemRole(String code) {
        return code != null && ALL_SYSTEM_ROLES.contains(code.toUpperCase());
    }

    public static boolean isOwner(String code) {
        return OWNER.equalsIgnoreCase(code);
    }

    /**
     * Role template definitions for initial seeding.
     */
    public record SystemRoleTemplate(
            String code,
            String name,
            String description,
            RoleScopeType defaultScope,
            Set<String> permissions
    ) {}

    public static final Set<String> OWNER_PERMISSIONS = Permissions.ALL_PERMISSIONS;

    public static final Set<String> ADMIN_PERMISSIONS = Collections.unmodifiableSet(Set.of(
            Permissions.ANALYTICS_DASHBOARD_READ, Permissions.ANALYTICS_REPORTS_READ,
            Permissions.CATALOG_PRODUCTS_READ, Permissions.CATALOG_PRODUCTS_CREATE, Permissions.CATALOG_PRODUCTS_UPDATE, Permissions.CATALOG_PRODUCTS_DELETE,
            Permissions.CATALOG_CATEGORIES_READ, Permissions.CATALOG_CATEGORIES_MANAGE,
            Permissions.INVENTORY_STOCK_READ, Permissions.INVENTORY_ADJUSTMENTS_CREATE, Permissions.INVENTORY_TRANSFERS_CREATE,
            Permissions.SALES_ORDERS_READ, Permissions.SALES_ORDERS_CREATE, Permissions.SALES_REFUNDS_CREATE,
            Permissions.PURCHASES_ORDERS_READ, Permissions.PURCHASES_ORDERS_CREATE, Permissions.PURCHASES_RECEIPTS_CREATE,
            Permissions.PARTIES_CUSTOMERS_READ, Permissions.PARTIES_CUSTOMERS_MANAGE,
            Permissions.PARTIES_SUPPLIERS_READ, Permissions.PARTIES_SUPPLIERS_MANAGE,
            Permissions.FINANCE_ACCOUNTS_READ, Permissions.FINANCE_EXPENSES_READ, Permissions.FINANCE_EXPENSES_CREATE,
            Permissions.TENANCY_BRANCHES_READ, Permissions.TENANCY_BRANCHES_MANAGE,
            Permissions.IDENTITY_USERS_READ, Permissions.IDENTITY_USERS_MANAGE,
            Permissions.IDENTITY_ROLES_READ, Permissions.IDENTITY_ROLES_MANAGE,
            Permissions.SYSTEM_AUDIT_LOGS_READ, Permissions.SYSTEM_SETTINGS_READ
    ));

    public static final Set<String> MANAGER_PERMISSIONS = Collections.unmodifiableSet(Set.of(
            Permissions.ANALYTICS_DASHBOARD_READ, Permissions.ANALYTICS_REPORTS_READ,
            Permissions.CATALOG_PRODUCTS_READ, Permissions.CATALOG_PRODUCTS_CREATE, Permissions.CATALOG_PRODUCTS_UPDATE,
            Permissions.CATALOG_CATEGORIES_READ,
            Permissions.INVENTORY_STOCK_READ, Permissions.INVENTORY_TRANSFERS_CREATE,
            Permissions.SALES_ORDERS_READ, Permissions.SALES_ORDERS_CREATE, Permissions.SALES_REFUNDS_CREATE,
            Permissions.PURCHASES_ORDERS_READ, Permissions.PURCHASES_ORDERS_CREATE,
            Permissions.PARTIES_CUSTOMERS_READ, Permissions.PARTIES_CUSTOMERS_MANAGE,
            Permissions.PARTIES_SUPPLIERS_READ,
            Permissions.TENANCY_BRANCHES_READ,
            Permissions.IDENTITY_USERS_READ,
            Permissions.IDENTITY_ROLES_READ
    ));

    public static final Set<String> CASHIER_PERMISSIONS = Collections.unmodifiableSet(Set.of(
            Permissions.CATALOG_PRODUCTS_READ,
            Permissions.CATALOG_CATEGORIES_READ,
            Permissions.SALES_ORDERS_READ, Permissions.SALES_ORDERS_CREATE,
            Permissions.PARTIES_CUSTOMERS_READ,
            Permissions.TENANCY_BRANCHES_READ
    ));

    public static final Set<String> WAREHOUSE_MANAGER_PERMISSIONS = Collections.unmodifiableSet(Set.of(
            Permissions.CATALOG_PRODUCTS_READ,
            Permissions.CATALOG_CATEGORIES_READ,
            Permissions.INVENTORY_STOCK_READ, Permissions.INVENTORY_ADJUSTMENTS_CREATE, Permissions.INVENTORY_TRANSFERS_CREATE,
            Permissions.PURCHASES_ORDERS_READ, Permissions.PURCHASES_RECEIPTS_CREATE,
            Permissions.PARTIES_SUPPLIERS_READ,
            Permissions.TENANCY_BRANCHES_READ
    ));

    public static final Set<String> ACCOUNTANT_PERMISSIONS = Collections.unmodifiableSet(Set.of(
            Permissions.ANALYTICS_DASHBOARD_READ, Permissions.ANALYTICS_REPORTS_READ,
            Permissions.SALES_ORDERS_READ,
            Permissions.PURCHASES_ORDERS_READ,
            Permissions.PARTIES_CUSTOMERS_READ, Permissions.PARTIES_SUPPLIERS_READ,
            Permissions.FINANCE_ACCOUNTS_READ, Permissions.FINANCE_EXPENSES_READ, Permissions.FINANCE_EXPENSES_CREATE,
            Permissions.TENANCY_BRANCHES_READ,
            Permissions.SYSTEM_AUDIT_LOGS_READ
    ));

    public static final Set<String> VIEWER_PERMISSIONS = Collections.unmodifiableSet(Set.of(
            Permissions.ANALYTICS_DASHBOARD_READ, Permissions.ANALYTICS_REPORTS_READ,
            Permissions.CATALOG_PRODUCTS_READ,
            Permissions.CATALOG_CATEGORIES_READ,
            Permissions.INVENTORY_STOCK_READ,
            Permissions.SALES_ORDERS_READ,
            Permissions.PURCHASES_ORDERS_READ,
            Permissions.PARTIES_CUSTOMERS_READ, Permissions.PARTIES_SUPPLIERS_READ,
            Permissions.FINANCE_EXPENSES_READ,
            Permissions.TENANCY_BRANCHES_READ,
            Permissions.IDENTITY_USERS_READ,
            Permissions.IDENTITY_ROLES_READ
    ));

    public static final List<SystemRoleTemplate> TEMPLATES = List.of(
            new SystemRoleTemplate(OWNER, "Owner", "Store Owner / Founder with unrestricted administrative access", RoleScopeType.TENANT, OWNER_PERMISSIONS),
            new SystemRoleTemplate(ADMIN, "Administrator", "Store Administrator with operational management across all modules", RoleScopeType.TENANT, ADMIN_PERMISSIONS),
            new SystemRoleTemplate(MANAGER, "Store Manager", "Branch / Store Manager overseeing daily sales, inventory, and staff", RoleScopeType.BRANCH, MANAGER_PERMISSIONS),
            new SystemRoleTemplate(CASHIER, "Cashier", "Cashier / POS Operator focused on order checkout and customer lookup", RoleScopeType.BRANCH, CASHIER_PERMISSIONS),
            new SystemRoleTemplate(WAREHOUSE_MANAGER, "Warehouse Manager", "Warehouse / Stockroom Lead managing stock receipts and adjustments", RoleScopeType.BRANCH, WAREHOUSE_MANAGER_PERMISSIONS),
            new SystemRoleTemplate(ACCOUNTANT, "Accountant", "Financial Specialist handling expenses, reconciliation, and audit logs", RoleScopeType.TENANT, ACCOUNTANT_PERMISSIONS),
            new SystemRoleTemplate(VIEWER, "Viewer", "Read-only Auditor with view access across store records", RoleScopeType.TENANT, VIEWER_PERMISSIONS)
    );
}
