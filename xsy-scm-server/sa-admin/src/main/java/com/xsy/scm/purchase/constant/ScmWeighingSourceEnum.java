package com.xsy.scm.purchase.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 称重来源。
 *
 * <p>
 * 只允许 {@link #MANUAL}；{@code ck_receipt_weighing_record_source} 与 {@code ck_purchase_receipt_item_weight_source} 在 DB
 * 层收紧。
 *
 * <p>
 * {@link #DEVICE} 当前不被收货命令接受；接入电子秤前不得写入该来源。
 *
 * <p>
 * {@code desc} 供页面与导出使用，保存仍用枚举名。
 */
@Getter
@RequiredArgsConstructor
public enum ScmWeighingSourceEnum {

    /**
     * 人工录入。
     */
    MANUAL("人工录入"),

    /**
     * 电子秤设备上报（尚未开放）。
     */
    DEVICE("电子秤");

    private final String desc;
}
