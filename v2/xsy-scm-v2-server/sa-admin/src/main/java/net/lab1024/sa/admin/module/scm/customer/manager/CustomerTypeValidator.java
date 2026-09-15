package net.lab1024.sa.admin.module.scm.customer.manager;

import net.lab1024.sa.admin.module.scm.common.constant.ScmEnableStatusEnum;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.customer.domain.form.CustomerTypeAddForm;

import static net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR;

/**
 * 客户类型单条业务规则。
 *
 * <p>客户类型是薄字典，规则只有两条：编码归一化、状态取值域。名称允许重复（legacy 不变量 T2，
 * 不建唯一索引），因此这里不做重名检查。
 */
public final class CustomerTypeValidator {

    private CustomerTypeValidator() {
    }

    /** 编码归一化：去空白 + 转大写。 */
    public static String normalizeCode(String raw) {
        return CustomerValidator.normalizeCode(raw);
    }

    /** 名称归一化：去首尾空白。 */
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

    /** 编码 / 名称必填校验（非 MVC 入口用）。 */
    public static void validateRequired(CustomerTypeAddForm form) {
        if (form.getTypeCode() == null || form.getTypeCode().trim().isEmpty()
                || form.getName() == null || form.getName().trim().isEmpty()) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
        validateStatus(form.getStatus());
    }
}
