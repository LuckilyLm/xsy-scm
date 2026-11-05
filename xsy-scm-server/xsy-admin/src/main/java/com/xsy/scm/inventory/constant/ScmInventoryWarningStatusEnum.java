package com.xsy.scm.inventory.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;

/**
 * 库存预警状态：正常、低于下限或高于上限。按可用量（实际库存减预留量）计算，不在数据库中保存状态。 查询 SQL 的筛选结果应与本枚举的计算结果一致。
 */
@Getter
@RequiredArgsConstructor
public enum ScmInventoryWarningStatusEnum {

    /**
     * 正常：在阈值区间内（或没有配置对应的边界）。
     */
    NORMAL("正常"),

    /**
     * 低于下限：触发补货预警。
     */
    LOW("低于下限"),

    /**
     * 高于上限：触发积压预警。
     */
    HIGH("高于上限");

    private final String desc;

    /**
     * 按可用量与阈值判定状态。
     *
     * <p>
     * {@code available} 为 {@code null} 时按 0 计：配置了阈值却没有余额行，正是「设了下限却一件没有」，应当预警。 这也是本能力唯一能表达「还没进过货就要补货」的方式。
     *
     * <p>
     * 下限优先判定：由于建表约束保证 {@code warn_min <= warn_max}，一个数量不可能同时低于下限又高于上限，所以这里的顺序只是可读性，不改变结果。
     */
    public static ScmInventoryWarningStatusEnum evaluate(BigDecimal available, BigDecimal warnMin, BigDecimal warnMax) {
        BigDecimal quantity = available == null ? BigDecimal.ZERO : available;
        if (warnMin != null && quantity.compareTo(warnMin) < 0) {
            return LOW;
        }
        if (warnMax != null && quantity.compareTo(warnMax) > 0) {
            return HIGH;
        }
        return NORMAL;
    }

    /**
     * 是否为异常状态（预警列表默认只看异常）。
     */
    public boolean isAbnormal() {
        return this != NORMAL;
    }

    /**
     * 该值是否允许作为查询参数（与前端枚举同源）。
     */
    public static boolean isSupported(String warningStatus) {
        for (ScmInventoryWarningStatusEnum item : values()) {
            if (item.name().equals(warningStatus)) {
                return true;
            }
        }
        return false;
    }
}
