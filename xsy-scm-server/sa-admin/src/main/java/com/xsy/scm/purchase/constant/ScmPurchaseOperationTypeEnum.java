package com.xsy.scm.purchase.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 采购操作类型，与数据库白名单保持一致。生成需求、需求分配和收货操作的归属字段不同， 必须按操作类型写入并由数据库约束校验。
 *
 * <p>
 * {@code desc} 供页面与导出使用，落库仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmPurchaseOperationTypeEnum {

    // ---- 采购单 ----
    /**
     * 新建采购单。
     */
    CREATE("建单"),

    /**
     * 修改采购单。
     */
    UPDATE("修改"),

    /**
     * 提交采购单。
     */
    SUBMIT("提交"),

    /**
     * 取消采购单。
     */
    CANCEL("取消"),

    /**
     * 短关：按实际到货量提前结案。
     */
    SHORT_CLOSE("短关"),

    /**
     * 删除采购单。
     */
    DELETE("删除"),

    // ---- 采购需求（无采购单 / 收货单可挂）----
    /**
     * 生成采购需求。
     */
    DEMAND_GENERATE("生成需求"),

    /**
     * 需求分配到采购单。
     */
    DEMAND_ALLOCATE("需求分配"),

    // ---- 采购收货（必属采购单）----
    /**
     * 新建收货单。
     */
    RECEIPT_CREATE("新建收货"),

    /**
     * 修改收货单。
     */
    RECEIPT_UPDATE("修改收货"),

    /**
     * 确认收货。
     */
    RECEIPT_CONFIRM("确认收货"),

    /**
     * 删除收货单。
     */
    RECEIPT_DELETE("删除收货"),

    // ---- 入库确认（必须关联采购单与收货单）----
    /**
     * 确认入库。
     */
    RECEIPT_PUTAWAY("确认入库");

    private final String desc;
}
