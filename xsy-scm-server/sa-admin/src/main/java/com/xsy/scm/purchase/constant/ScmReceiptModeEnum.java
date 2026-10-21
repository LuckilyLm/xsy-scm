package com.xsy.scm.purchase.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 收货入库模式。直接入库在确认收货时记库存；仓库二次确认先只记收货事实，等待入库操作。 不允许用收货完成状态代替入库状态。
 *
 * <p>
 * {@code desc} 供页面与导出使用，落库仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmReceiptModeEnum {

    /** 直接入库：确认收货即记库存。 */
    DIRECT("直接入库"),

    /** 仓库二次确认：收货后等仓库确认才入库。 */
    WAREHOUSE_CONFIRM("仓库二次确认");

    private final String desc;
}
