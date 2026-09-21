package net.lab1024.sa.admin.module.scm.purchase.constant;

/**
 * 称重来源（2 值）。
 *
 * <p>设计依据：W5 Target Design §5.9 与 G-05。W5 **只允许** {@link #MANUAL}，
 * 由 V15 的 {@code ck_receipt_weighing_record_source} 与
 * {@code ck_purchase_receipt_item_weight_source} 在 DB 层收紧。
 *
 * <p>{@link #DEVICE} 是**扩展点**：等 W6+ 接入电子秤（G-05）时放宽 CHECK 即可启用，
 * W5 不写、不判、不落库。
 */
public enum ScmWeighingSourceEnum {MANUAL, DEVICE}
