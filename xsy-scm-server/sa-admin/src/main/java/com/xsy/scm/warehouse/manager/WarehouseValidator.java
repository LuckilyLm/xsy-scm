package com.xsy.scm.warehouse.manager;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.warehouse.constant.ScmWarehouseStatusEnum;
import com.xsy.scm.warehouse.domain.form.WarehouseAddForm;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR;

/**
 * 仓库单条业务规则（纯函数，无 Spring 依赖）。
 *
 * <p>
 * 编码由服务端生成，不参与表单校验；重复编码由 {@code uk_warehouse_code_active} 唯一索引拒绝。
 */
public final class WarehouseValidator {

    private WarehouseValidator() {
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
     * <p>
     * 覆盖不经过 MVC Bean Validation 的内部调用。
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
     * 名称必填校验（非 MVC 入口用）。
     */
    public static void validateRequired(WarehouseAddForm form) {
        if (form == null || form.getName() == null || form.getName().trim().isEmpty()) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
    }
}
