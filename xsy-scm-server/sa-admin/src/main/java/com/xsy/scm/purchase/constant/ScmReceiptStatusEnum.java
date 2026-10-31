package com.xsy.scm.purchase.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 采购收货单状态。
 *
 * <p>
 * 取值与 {@code ck_purchase_receipt_status} 一致：PENDING / CONFIRMED。{@code CONFIRMED} 表示本收货单已提交，不由采购单的整体收货进度决定；
 * 一张采购单可以对应多张收货单。
 *
 * <p>
 * {@code desc} 供页面与导出使用，保存仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmReceiptStatusEnum {

    /**
     * 待确认：新建成单，可改备注或删除；确认后才写入应付与库存。
     */
    PENDING("待确认"),

    /**
     * 已确认：本收货单已提交。
     */
    CONFIRMED("已确认");

    private final String desc;
}
