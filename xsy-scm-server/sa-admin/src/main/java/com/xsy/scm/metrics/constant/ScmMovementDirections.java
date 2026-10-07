package com.xsy.scm.metrics.constant;

import com.xsy.scm.inventory.constant.ScmInventoryMovementTypeEnum;

import java.util.Arrays;
import java.util.List;

/**
 * 流水方向类型清单，<b>由枚举的方向位派生</b>。
 *
 * <p>
 * 多处口径都表达「入库」或「出库」：今日出入库次数、趋势的出入库量、大屏供应链网络节点的今日出库量。若各自在 SQL 里抄一份类型清单， 新增流水类型时会<b>静默少算</b>（数字看起来正常，只是偏小）——
 * 这是本项目最忌讳的失败方式。
 *
 * <p>
 * 方向位本身已被 {@code ScmInventoryConstantTest} 钉住，从这里派生等于把这几处口径一并接进那道契约守卫。
 */
public final class ScmMovementDirections {

    private static final List<String> INBOUND = Arrays.stream(ScmInventoryMovementTypeEnum.values())
            .filter(ScmInventoryMovementTypeEnum::isInbound).map(Enum::name).toList();

    private static final List<String> OUTBOUND = Arrays.stream(ScmInventoryMovementTypeEnum.values())
            .filter(type -> !type.isInbound()).map(Enum::name).toList();

    private ScmMovementDirections() {
    }

    public static List<String> inboundTypes() {
        return INBOUND;
    }

    public static List<String> outboundTypes() {
        return OUTBOUND;
    }
}
