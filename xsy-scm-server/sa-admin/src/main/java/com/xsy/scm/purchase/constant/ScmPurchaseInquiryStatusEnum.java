package com.xsy.scm.purchase.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 采购询价单状态，与 {@code xsy_purchase_inquiry} 的 CHECK 约束逐值对应。
 *
 * <p>
 * 只有两种状态：询价中可继续报价，接受某个报价后询价即关闭。 {@code desc} 供页面与导出使用，落库仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmPurchaseInquiryStatusEnum {

    /** 询价中：可继续报价与改价。 */
    OPEN("询价中"),

    /** 已接受：已选定报价，询价关闭。 */
    ACCEPTED("已接受");

    private final String desc;
}
