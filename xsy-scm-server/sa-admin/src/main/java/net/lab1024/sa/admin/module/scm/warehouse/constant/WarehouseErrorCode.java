package net.lab1024.sa.admin.module.scm.warehouse.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.error.ScmErrorCode;

/**
 * 仓库域错误码（2 个）。
 *
 * <p>设计依据：W5 Target Design §7.7（Q11 / F5）。**必须**独立于 {@code PurchaseErrorCode}：
 * {@code module/scm/warehouse/**} 要能独立抛错，否则会形成 {@code warehouse → purchase}
 * 的反向依赖 —— 而把 warehouse 独立成域的唯一理由就是避免这种反向依赖（W6 库存域要直接依赖它）。
 *
 * <p>与 {@code PurchaseErrorCode}（38 个）合计 **40** 个，与 W1–W4 全部错误码零交集
 * （由 {@code PurchaseErrorCodeTest} 门禁强制）。
 */
@Getter
@RequiredArgsConstructor
public enum WarehouseErrorCode implements ScmErrorCode {

    /** 40485：落在已占用段 40464–40499 的空档内。 */
    WAREHOUSE_NOT_FOUND(40485, "仓库不存在"),

    /** 40996：落在已占用段（最大 40970）之后的空档内。 */
    WAREHOUSE_CODE_DUPLICATE(40996, "仓库编码已存在");

    private final int code;
    private final String msg;
}
