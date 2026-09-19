package net.lab1024.sa.admin.module.scm.inventory.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;

/**
 * 库存预警状态（与参考项目的 `NORMAL / LOW / HIGH` 三态一致）。
 *
 * <p><b>这是派生值，不落库</b>：它完全由 {@code (阈值, 可用量)} 决定，因此没有对应的列、
 * 也不在任何写入路径里维护。参考项目写「余额变动后校验」，但落库就意味着**六条**余额写入路径
 * 都要顺手更新这个状态 —— 那只会多出一个会漂移的副本，而不会多出任何信息。
 *
 * <p><b>比较基准是可用量（现有量 − 预留量），不是现有量</b>：下限的业务含义是「还够不够发货」——
 * 20 kg 在库但 18 kg 已预留给明天的订单时，可用只有 2 kg，这时必须触发补货预警。
 * 上限同理：货已经被订走就不算积压。
 *
 * <p><b>判断规则只有这一处实现</b>（{@link #evaluate}）。列表 SQL 里另有一份**过滤**用的谓词
 * （为了能按状态筛选 + 分页），两者的等价性由 {@code ScmInventoryWarningIT} 交叉验证：
 * 用 {@code status=LOW} 查出来的行，其 Java 侧状态必须全是 {@code LOW}。
 */
@Getter
@RequiredArgsConstructor
public enum ScmInventoryWarningStatusEnum {

    /** 正常：在阈值区间内（或没有配置对应的边界）。 */
    NORMAL("正常"),

    /** 低于下限：触发补货预警。 */
    LOW("低于下限"),

    /** 高于上限：触发积压预警。 */
    HIGH("高于上限");

    private final String desc;

    /**
     * 按可用量与阈值判定状态。
     *
     * <p>{@code available} 为 {@code null} 时按 0 计 —— 配置了阈值却没有余额行，
     * 正是「设了下限却一件没有」，应当预警，这也是本能力唯一能表达
     * 「还没进过货就要补货」的方式。
     *
     * <p>下限优先判定：由于建表约束保证 {@code warn_min <= warn_max}，
     * 一个数量不可能同时低于下限又高于上限，所以这里的顺序只是可读性，不改变结果。
     */
    public static ScmInventoryWarningStatusEnum evaluate(BigDecimal available,
                                                         BigDecimal warnMin,
                                                         BigDecimal warnMax) {
        BigDecimal quantity = available == null ? BigDecimal.ZERO : available;
        if (warnMin != null && quantity.compareTo(warnMin) < 0) {
            return LOW;
        }
        if (warnMax != null && quantity.compareTo(warnMax) > 0) {
            return HIGH;
        }
        return NORMAL;
    }

    /** 是否为异常状态（预警列表默认只看异常）。 */
    public boolean isAbnormal() {
        return this != NORMAL;
    }

    /** 该值是否允许作为查询参数（与前端枚举同源）。 */
    public static boolean isSupported(String value) {
        for (ScmInventoryWarningStatusEnum item : values()) {
            if (item.name().equals(value)) {
                return true;
            }
        }
        return false;
    }
}
