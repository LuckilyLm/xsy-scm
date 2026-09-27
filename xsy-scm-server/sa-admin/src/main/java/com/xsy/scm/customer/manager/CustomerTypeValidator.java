package com.xsy.scm.customer.manager;

import com.xsy.scm.common.constant.ScmEnableStatusEnum;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.customer.domain.form.CustomerTypeAddForm;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR;

/**
 * 客户类型单条业务规则。
 *
 * <p>客户类型是薄字典：编码需归一化且唯一，状态必须属于受支持的取值；名称允许重复。
 */
public final class CustomerTypeValidator {

    private CustomerTypeValidator() {
    }

    /**
     * 编码归一化：去空白 + 转大写。
     */
    public static String normalizeCode(String raw) {
        return CustomerValidator.normalizeCode(raw);
    }

    /**
     * 名称归一化：去首尾空白。
     */
    public static String normalizeName(String raw) {
        return CustomerValidator.normalizeName(raw);
    }

    /**
     * 状态取值域校验。
     *
     * <p>MVC 入口已有 {@code @Pattern}；这里覆盖非 MVC 入口（内部调用、种子脚本）。
     */
    public static void validateStatus(String status) {
        if (status == null) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
        for (ScmEnableStatusEnum candidate : ScmEnableStatusEnum.values()) {
            if (candidate.name().equals(status)) {
                return;
            }
        }
        throw new ScmBusinessException(VALIDATION_ERROR);
    }

    /**
     * 编码 / 名称必填校验（非 MVC 入口用）。
     */
    public static void validateRequired(CustomerTypeAddForm form) {
        if (form.getTypeCode() == null || form.getTypeCode().trim().isEmpty()
                || form.getName() == null || form.getName().trim().isEmpty()) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
        validateStatus(form.getStatus());
    }
}
