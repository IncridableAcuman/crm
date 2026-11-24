package com.savdocrm.modules.identity.domain;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Canonical 33-permission catalog definitions for SAVDOCRM.
 * Format strictly enforces module.resource.action dot notation.
 */
public final class Permissions {

    private Permissions() {
    }

    // Analytics module
    public static final String ANALYTICS_DASHBOARD_READ = "analytics.dashboard.read";
    public static final String ANALYTICS_REPORTS_READ = "analytics.reports.read";

    // Catalog module
    public static final String CATALOG_PRODUCTS_READ = "catalog.products.read";
    public static final String CATALOG_PRODUCTS_CREATE = "catalog.products.create";
    public static final String CATALOG_PRODUCTS_UPDATE = "catalog.products.update";
    public static final String CATALOG_PRODUCTS_DELETE = "catalog.products.delete";
    public static final String CATALOG_CATEGORIES_READ = "catalog.categories.read";
    public static final String CATALOG_CATEGORIES_MANAGE = "catalog.categories.manage";

    // Inventory module
    public static final String INVENTORY_STOCK_READ = "inventory.stock.read";
    public static final String INVENTORY_ADJUSTMENTS_CREATE = "inventory.adjustments.create";
    public static final String INVENTORY_TRANSFERS_CREATE = "inventory.transfers.create";

    // Sales module
    public static final String SALES_ORDERS_READ = "sales.orders.read";
    public static final String SALES_ORDERS_CREATE = "sales.orders.create";
    public static final String SALES_REFUNDS_CREATE = "sales.refunds.create";

    // Purchases module
    public static final String PURCHASES_ORDERS_READ = "purchases.orders.read";
    public static final String PURCHASES_ORDERS_CREATE = "purchases.orders.create";
    public static final String PURCHASES_RECEIPTS_CREATE = "purchases.receipts.create";

    // Parties module
    public static final String PARTIES_CUSTOMERS_READ = "parties.customers.read";
    public static final String PARTIES_CUSTOMERS_MANAGE = "parties.customers.manage";
    public static final String PARTIES_SUPPLIERS_READ = "parties.suppliers.read";
    public static final String PARTIES_SUPPLIERS_MANAGE = "parties.suppliers.manage";

    // Finance module
    public static final String FINANCE_ACCOUNTS_READ = "finance.accounts.read";
    public static final String FINANCE_EXPENSES_READ = "finance.expenses.read";
    public static final String FINANCE_EXPENSES_CREATE = "finance.expenses.create";

    // Tenancy module
    public static final String TENANCY_BRANCHES_READ = "tenancy.branches.read";
    public static final String TENANCY_BRANCHES_MANAGE = "tenancy.branches.manage";

    // Identity module
    public static final String IDENTITY_USERS_READ = "identity.users.read";
    public static final String IDENTITY_USERS_MANAGE = "identity.users.manage";
    public static final String IDENTITY_ROLES_READ = "identity.roles.read";
    public static final String IDENTITY_ROLES_MANAGE = "identity.roles.manage";

    // System module
    public static final String SYSTEM_AUDIT_LOGS_READ = "system.audit_logs.read";
    public static final String SYSTEM_SETTINGS_READ = "system.settings.read";
    public static final String SYSTEM_SETTINGS_MANAGE = "system.settings.manage";

    public static final Set<String> ALL_PERMISSIONS = Collections.unmodifiableSet(Set.of(
            ANALYTICS_DASHBOARD_READ, ANALYTICS_REPORTS_READ,
            CATALOG_PRODUCTS_READ, CATALOG_PRODUCTS_CREATE, CATALOG_PRODUCTS_UPDATE, CATALOG_PRODUCTS_DELETE,
            CATALOG_CATEGORIES_READ, CATALOG_CATEGORIES_MANAGE,
            INVENTORY_STOCK_READ, INVENTORY_ADJUSTMENTS_CREATE, INVENTORY_TRANSFERS_CREATE,
            SALES_ORDERS_READ, SALES_ORDERS_CREATE, SALES_REFUNDS_CREATE,
            PURCHASES_ORDERS_READ, PURCHASES_ORDERS_CREATE, PURCHASES_RECEIPTS_CREATE,
            PARTIES_CUSTOMERS_READ, PARTIES_CUSTOMERS_MANAGE, PARTIES_SUPPLIERS_READ, PARTIES_SUPPLIERS_MANAGE,
            FINANCE_ACCOUNTS_READ, FINANCE_EXPENSES_READ, FINANCE_EXPENSES_CREATE,
            TENANCY_BRANCHES_READ, TENANCY_BRANCHES_MANAGE,
            IDENTITY_USERS_READ, IDENTITY_USERS_MANAGE, IDENTITY_ROLES_READ, IDENTITY_ROLES_MANAGE,
            SYSTEM_AUDIT_LOGS_READ, SYSTEM_SETTINGS_READ, SYSTEM_SETTINGS_MANAGE
    ));

    public static boolean isValidCode(String code) {
        return code != null && ALL_PERMISSIONS.contains(code);
    }
}
