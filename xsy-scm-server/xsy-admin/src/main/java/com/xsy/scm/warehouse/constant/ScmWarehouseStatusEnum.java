package com.xsy.scm.warehouse.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 仓库状态，与 {@code ck_warehouse_status} 的取值一致。
 *
 * <p>
 * 取值与 {@code ScmEnableStatusEnum} 相同，但仓库域保留自己的枚举，库存域不用依赖通用主数据枚举。 {@code desc} 供页面与导出使用，数据库仍存枚举名。
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
