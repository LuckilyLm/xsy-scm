package com.xsy.scm.inventory.permission;

import com.xsy.scm.common.permission.ScmCrossDomainPermission;

/** Stable permission identifiers published by the inventory API. */
public final class InventoryPermission {

    public static final String BALANCE_QUERY = ScmCrossDomainPermission.INVENTORY_BALANCE_QUERY;

    private InventoryPermission() {
    }
}
