package net.lab1024.sa.admin.module.scm.warehouse.manager;

import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.warehouse.constant.ScmWarehouseStatusEnum;
import net.lab1024.sa.admin.module.scm.warehouse.domain.form.WarehouseAddForm;

import static net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR;

/**
 * 仓库单条业务规则（纯函数，无 Spring 依赖，可被单测直接覆盖）。
 *
 * <p>仓库是薄主数据，规则只有三条：编码归一化、名称归一化、状态取值域。
 * 「编码重复」不是本类的职责 —— 它由 {@code uk_warehouse_code_active} 唯一索引 +
 * {@code WarehouseService} 的显式查重共同保证。
 */
public final class WarehouseValidator {

    private WarehouseValidator() {
    }

    /**
     * 编码归一化：去首尾空白 + 转大写（与 W1/W2 的编码口径一致）。
     */
    public static String normalizeCode(String raw) {
        return raw == null ? null : raw.trim().toUpperCase(java.util.Locale.ROOT);
    }

    /**
     * 名称归一化：去首尾空白。
     */
    public static String normalizeName(String raw) {
        return raw == null ? null : raw.trim();
    }

    /**
     * 状态取值域校验。
     *
     * <p>MVC 入口已有 {@code @Pattern}；这里覆盖非 MVC 入口（内部调用、种子脚本、W6 库存域）。
     */
    public static void validateStatus(String status) {
        if (status == null) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
        for (ScmWarehouseStatusEnum candidate : ScmWarehouseStatusEnum.values()) {
            if (candidate.name().equals(status)) {
                return;
            }
        }
        throw new ScmBusinessException(VALIDATION_ERROR);
    }

    /**
     * 编码 / 名称必填校验（非 MVC 入口用）。
     */
    public static void validateRequired(WarehouseAddForm form) {
        if (form == null
                || form.getWarehouseCode() == null || form.getWarehouseCode().trim().isEmpty()
                || form.getName() == null || form.getName().trim().isEmpty()) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
    }
}
