package com.xsy.scm.inventory.permission;

import com.xsy.scm.common.permission.ScmCrossDomainPermission;

/** Stable permission identifiers published by the inventory API. */
public final class InventoryPermission {

    public static final String BALANCE_QUERY = ScmCrossDomainPermission.INVENTORY_BALANCE_QUERY;

    public static final String SCOPE_ALL_QUERY = ScmCrossDomainPermission.INVENTORY_SCOPE_ALL_QUERY;

    public static final String MOVEMENT_QUERY = "scm:inventory:movement:query";

    public static final String CONVERSION_QUERY = "scm:inventory:conversion:query";

    public static final String CONVERSION_ADD = "scm:inventory:conversion:add";

    public static final String CONVERSION_UPDATE = "scm:inventory:conversion:update";

    public static final String CONVERSION_DELETE = "scm:inventory:conversion:delete";

    public static final String CONVERSION_APPROVE = "scm:inventory:conversion:approve";

    public static final String CONVERSION_REJECT = "scm:inventory:conversion:reject";

    public static final String LOSS_GAIN_QUERY = "scm:inventory:loss-gain:query";

    public static final String LOSS_GAIN_ADD = "scm:inventory:loss-gain:add";

    public static final String LOSS_GAIN_UPDATE = "scm:inventory:loss-gain:update";

    public static final String LOSS_GAIN_DELETE = "scm:inventory:loss-gain:delete";

    public static final String LOSS_GAIN_APPROVE = "scm:inventory:loss-gain:approve";

    public static final String LOSS_GAIN_REJECT = "scm:inventory:loss-gain:reject";

    public static final String OUTBOUND_QUERY = "scm:inventory:outbound:query";

    public static final String OUTBOUND_ADD = "scm:inventory:outbound:add";

    public static final String OUTBOUND_UPDATE = "scm:inventory:outbound:update";

    public static final String OUTBOUND_DELETE = "scm:inventory:outbound:delete";

    public static final String OUTBOUND_CONFIRM = "scm:inventory:outbound:confirm";

    public static final String RESERVATION_QUERY = "scm:inventory:reservation:query";

    public static final String RESERVATION_RELEASE = "scm:inventory:reservation:release";

    public static final String STOCKTAKE_QUERY = "scm:inventory:stocktake:query";

    public static final String STOCKTAKE_ADD = "scm:inventory:stocktake:add";

    public static final String STOCKTAKE_UPDATE = "scm:inventory:stocktake:update";

    public static final String STOCKTAKE_DELETE = "scm:inventory:stocktake:delete";

    public static final String STOCKTAKE_CONFIRM = "scm:inventory:stocktake:confirm";

    public static final String STOCKTAKE_IMPORT = "scm:inventory:stocktake:import";

    public static final String THRESHOLD_QUERY = "scm:inventory:threshold:query";

    public static final String THRESHOLD_ADD = "scm:inventory:threshold:add";

    public static final String THRESHOLD_UPDATE = "scm:inventory:threshold:update";

    public static final String THRESHOLD_DELETE = "scm:inventory:threshold:delete";

    public static final String WARNING_QUERY = "scm:inventory:warning:query";

    public static final String TRANSFER_QUERY = "scm:inventory:transfer:query";

    public static final String TRANSFER_ADD = "scm:inventory:transfer:add";

    public static final String TRANSFER_UPDATE = "scm:inventory:transfer:update";

    public static final String TRANSFER_DELETE = "scm:inventory:transfer:delete";

    public static final String TRANSFER_SHIP = "scm:inventory:transfer:ship";

    public static final String TRANSFER_RECEIVE = "scm:inventory:transfer:receive";

    private InventoryPermission() {
    }
}
