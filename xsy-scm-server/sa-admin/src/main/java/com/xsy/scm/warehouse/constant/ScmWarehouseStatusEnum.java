package com.xsy.scm.warehouse.constant;

/**
 * 仓库状态，与 {@code ck_warehouse_status} 的 ENABLED / DISABLED 白名单一致。
 *
 * <p>
 * 它与 {@code ScmEnableStatusEnum} 取值相同，但仓库域拥有自己的状态词汇，使库存域可以依赖仓库状态而不依赖通用主数据枚举。
 */
public enum ScmWarehouseStatusEnum {
    ENABLED,
    DISABLED
}
