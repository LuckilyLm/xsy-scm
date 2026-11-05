package com.xsy.scm.metrics.constant;

import com.xsy.scm.inventory.constant.ScmInventoryMovementTypeEnum;

import java.util.Arrays;
import java.util.List;

/**
 * 库存出入方向从统一类型枚举派生，首页与大屏不能分别维护两套类型清单。
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
