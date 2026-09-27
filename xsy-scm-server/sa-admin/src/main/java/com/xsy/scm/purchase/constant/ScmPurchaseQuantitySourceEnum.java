package com.xsy.scm.purchase.constant;

/**
 * 本次收货有效数量的来源（2 值）。
 *
 * <p>标准品按申报数量计量；非标品按人工实重计量。
 *
 * <pre>
 * STANDARD      有效数量 = 本次声明数量（declaredQuantity），由系统取值     → SYSTEM
 * NON_STANDARD  有效数量 = 本次实重（actualWeight），由人工称重录入          → MANUAL
 * </pre>
 *
 * <p>取值集合与 {@code ScmOrderQuantitySourceEnum} 对齐（{@code SYSTEM} / {@code MANUAL}），
 * 保持 SCM 内「数量来源」词汇一致；本枚举描述的是**有效数量的取值方式**，
 * 与 {@link ScmWeighingSourceEnum}（称重设备来源）是两个不同维度。
 */
public enum ScmPurchaseQuantitySourceEnum {SYSTEM, MANUAL}
