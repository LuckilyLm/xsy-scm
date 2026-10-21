package com.xsy.scm.warehouse.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 仓库状态，与 {@code ck_warehouse_status} 的 ENABLED / DISABLED 白名单一致。
 *
 * <p>
 * 它与 {@code ScmEnableStatusEnum} 取值相同，但仓库域拥有自己的状态词汇， 使库存域可以依赖仓库状态而不依赖通用主数据枚举。
 *
 * <p>
 * {@code desc} 供页面与导出使用，落库仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmWarehouseStatusEnum {

    /** 启用：可参与采购、分拣与配送。 */
    ENABLED("启用"),

    /** 停用：不再接收新业务，历史数据保留。 */
    DISABLED("停用");

    private final String desc;
}
