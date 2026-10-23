package com.xsy.scm.purchase.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 库存入账状态。收货确认与库存入账是两种状态：直接入库模式在收货确认时完成入账， 仓库二次确认模式须等仓库确认后才变成已入库。
 *
 * <p>
 * {@code desc} 供页面与导出使用，落库仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmPutawayStatusEnum {

    /**
     * 待入库：已收货，尚未记入库存。
     */
    PENDING("待入库"),

    /**
     * 已入库：库存已入账。
     */
    COMPLETED("已入库");

    private final String desc;
}
