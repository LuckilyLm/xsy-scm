package net.lab1024.sa.admin.module.scm.customer.manager;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.constant.ScmCreditPeriodTypeEnum;
import net.lab1024.sa.admin.module.scm.common.constant.ScmCreditPeriodUnitEnum;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.customer.dao.CustomerDao;
import net.lab1024.sa.admin.module.scm.customer.dao.CustomerTypeDao;
import net.lab1024.sa.admin.module.scm.customer.domain.entity.CustomerEntity;
import net.lab1024.sa.admin.module.scm.customer.domain.entity.CustomerTypeEntity;
import net.lab1024.sa.admin.module.scm.customer.domain.form.CustomerAddForm;
import org.springframework.stereotype.Component;

import java.util.Objects;

import static net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR;
import static net.lab1024.sa.admin.module.scm.customer.constant.CustomerErrorCode.CUSTOMER_NOT_FOUND;
import static net.lab1024.sa.admin.module.scm.customer.constant.CustomerErrorCode.CUSTOMER_PARENT_INVALID;

/**
 * 客户单条业务规则：账期组合、上级关系、字段归一化。
 *
 * <p>只做「一条规则一件事」的判断，不控制事务、不写库——事务边界在 {@code CustomerService}。
 */
@Component
@RequiredArgsConstructor
public class CustomerValidator {

    /** 上级客户上溯的最大层数；超过即判定为环，避免脏数据把请求拖成死循环（Target Design R4）。 */
    public static final int MAX_PARENT_DEPTH = 20;

    /** 可以作为上级客户（集团）的类型编码。 */
    public static final String GROUP_TYPE_CODE = "GROUP";

    private final CustomerDao customerDao;

    private final CustomerTypeDao customerTypeDao;

    /** 编码归一化：去空白 + 转大写，保证「abc 」与「ABC」被视为同一编码。 */
    public static String normalizeCode(String raw) {
        return raw == null ? null : raw.trim().toUpperCase();
    }

    /** 名称归一化：仅去首尾空白，保留大小写（中文名称大小写无意义，英文名称有意义）。 */
    public static String normalizeName(String raw) {
        return raw == null ? null : raw.trim();
    }

    /** 可选文本归一化：去首尾空白，空白视作「未填写」返回 {@code null}，以便真正清空列（R7）。 */
    public static String normalizeOptional(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 账期组合校验。
     *
     * <p>三种合法形态互斥且穷尽：不设置 / 按金额 / 按时间。DB 侧有等价的
     * {@code ck_customer_credit_period} 约束；这里先拦一次，让错误码是 40000 而不是数据库异常。
     */
    public void validateCreditPeriod(CustomerAddForm form) {
        String type = form.getCreditPeriodType();
        boolean hasThreshold = form.getCreditAmountThreshold() != null;
        boolean hasValue = form.getCreditPeriodValue() != null;
        boolean hasUnit = form.getCreditPeriodUnit() != null;
        boolean hasSettleDay = form.getSettleDay() != null;

        if (type == null) {
            if (hasThreshold || hasValue || hasUnit || hasSettleDay) {
                throw new ScmBusinessException(VALIDATION_ERROR);
            }
            return;
        }

        if (ScmCreditPeriodTypeEnum.BY_AMOUNT.name().equals(type)) {
            if (!hasThreshold || hasValue || hasUnit || hasSettleDay) {
                throw new ScmBusinessException(VALIDATION_ERROR);
            }
            return;
        }

        if (ScmCreditPeriodTypeEnum.BY_TIME.name().equals(type)) {
            if (hasThreshold || !hasValue || form.getCreditPeriodValue() <= 0 || !hasUnit) {
                throw new ScmBusinessException(VALIDATION_ERROR);
            }
            if (ScmCreditPeriodUnitEnum.DAY.name().equals(form.getCreditPeriodUnit())) {
                if (hasSettleDay) {
                    throw new ScmBusinessException(VALIDATION_ERROR);
                }
                return;
            }
            if (ScmCreditPeriodUnitEnum.MONTH.name().equals(form.getCreditPeriodUnit())) {
                // 结算日上限 28：保证 2 月也存在该日期，账期才可计算
                if (hasSettleDay && (form.getSettleDay() < 1 || form.getSettleDay() > 28)) {
                    throw new ScmBusinessException(VALIDATION_ERROR);
                }
                return;
            }
            throw new ScmBusinessException(VALIDATION_ERROR);
        }

        throw new ScmBusinessException(VALIDATION_ERROR);
    }

    /**
     * 上级客户关系校验：不得是自身、必须存在、其类型必须是「集团」、上溯不得成环。
     *
     * @param parentCustomerId 上级客户 id，{@code null} 表示独立客户，直接通过
     * @param selfId           当前客户 id；新增时为 {@code null}
     */
    public void validateParent(Long parentCustomerId, Long selfId) {
        if (parentCustomerId == null) {
            return;
        }
        if (selfId != null && parentCustomerId.equals(selfId)) {
            throw new ScmBusinessException(CUSTOMER_PARENT_INVALID);
        }

        CustomerEntity parent = customerDao.selectById(parentCustomerId);
        if (parent == null) {
            throw new ScmBusinessException(CUSTOMER_NOT_FOUND);
        }
        if (!isGroupType(parent.getCustomerTypeId())) {
            throw new ScmBusinessException(CUSTOMER_PARENT_INVALID);
        }

        // 逐级上溯检测环：命中自身、或超过最大深度（脏数据）都判定为非法
        Long cursor = parent.getParentCustomerId();
        int depth = 1;
        while (cursor != null) {
            if (selfId != null && cursor.equals(selfId)) {
                throw new ScmBusinessException(CUSTOMER_PARENT_INVALID);
            }
            if (++depth > MAX_PARENT_DEPTH) {
                throw new ScmBusinessException(CUSTOMER_PARENT_INVALID);
            }
            CustomerEntity ancestor = customerDao.selectById(cursor);
            if (ancestor == null) {
                throw new ScmBusinessException(CUSTOMER_NOT_FOUND);
            }
            cursor = ancestor.getParentCustomerId();
        }
    }

    /** 上级客户必须属于「集团」类型（业务语义：只有集团才能作为结算/归属上级）。 */
    private boolean isGroupType(Long customerTypeId) {
        if (customerTypeId == null) {
            return false;
        }
        CustomerTypeEntity type = customerTypeDao.selectById(customerTypeId);
        return type != null && Objects.equals(GROUP_TYPE_CODE, type.getTypeCode());
    }
}
