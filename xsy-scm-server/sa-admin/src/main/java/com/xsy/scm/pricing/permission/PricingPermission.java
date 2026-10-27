package com.xsy.scm.pricing.permission;

/**
 * 定价域对外发布的稳定权限标识。
 */
public final class PricingPermission {

    public static final String AGREEMENT_QUERY = "scm:pricing:agreement:query";
    public static final String AGREEMENT_ADD = "scm:pricing:agreement:add";
    public static final String AGREEMENT_UPDATE = "scm:pricing:agreement:update";
    public static final String AGREEMENT_DELETE = "scm:pricing:agreement:delete";
    public static final String TYPE_PRICE_QUERY = "scm:pricing:type-price:query";
    public static final String TYPE_PRICE_ADD = "scm:pricing:type-price:add";
    public static final String TYPE_PRICE_UPDATE = "scm:pricing:type-price:update";
    public static final String TYPE_PRICE_DELETE = "scm:pricing:type-price:delete";
    public static final String GROUP_PRICE_QUERY = "scm:pricing:group-price:query";
    public static final String GROUP_PRICE_ADD = "scm:pricing:group-price:add";
    public static final String GROUP_PRICE_UPDATE = "scm:pricing:group-price:update";
    public static final String GROUP_PRICE_DELETE = "scm:pricing:group-price:delete";

    /** 时价维护（增删改查同一把权限）。 */
    public static final String TIME_PRICE_MANAGE = "scm:pricing:time-price:manage";
    public static final String TYPE_PRICE_BATCH = "scm:pricing:type-price:batch";
    public static final String HISTORY_QUERY = "scm:pricing:history:query";
    public static final String RESOLVE_QUERY = "scm:pricing:resolve:query";

    private PricingPermission() {
    }
}
