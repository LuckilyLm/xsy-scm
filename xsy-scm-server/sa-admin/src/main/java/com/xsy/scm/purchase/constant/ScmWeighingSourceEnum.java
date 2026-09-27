package com.xsy.scm.purchase.constant;

/**
 * 称重来源（2 值）。
 *
 * <p>只允许 {@link #MANUAL}；
 * {@code ck_receipt_weighing_record_source} 与
 * {@code ck_purchase_receipt_item_weight_source} 在 DB 层收紧。
 *
 * <p>{@link #DEVICE} 当前不被收货命令接受；接入电子秤前不得写入该来源。
 */
public enum ScmWeighingSourceEnum {MANUAL, DEVICE}
