package com.xsy.scm.customer.manager;

import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.constant.ScmCreditPeriodTypeEnum;
import com.xsy.scm.common.constant.ScmCreditPeriodUnitEnum;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.customer.dao.CustomerDao;
import com.xsy.scm.customer.dao.CustomerTypeDao;
import com.xsy.scm.customer.domain.entity.CustomerEntity;
import com.xsy.scm.customer.domain.entity.CustomerTypeEntity;
import com.xsy.scm.customer.domain.form.CustomerAddForm;
import org.springframework.stereotype.Component;

import java.util.Objects;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR;
import static com.xsy.scm.customer.constant.CustomerErrorCode.CUSTOMER_NOT_FOUND;
import static com.xsy.scm.customer.constant.CustomerErrorCode.CUSTOMER_PARENT_INVALID;

/**
 * 客户单条规则：账期组合、上级关系、字段归一化。只做判断，不控制事务、不写库。
 */
@Component
@RequiredArgsConstructor
public class CustomerValidator {

    /** 上级客户上溯的最大层数；超过即判定为环，避免脏数据让请求无限遍历。 */
    public static final int MAX_PARENT_DEPTH = 20;

    /** 可以作为上级客户（集团）的类型编码。 */
    public static final String GROUP_TYPE_CODE = "GROUP";

    private final CustomerDao customerDao;

    private final CustomerTypeDao customerTypeDao;

    /** 编码归一化：去空白并转大写，保证「abc 」与「ABC」被视为同一编码。 */
    public static String normalizeCode(String raw) {
        return raw == null ? null : raw.trim().toUpperCase();
    }

    /** 名称归一化：只去首尾空白，保留大小写。 */
    public static String normalizeName(String raw) {
        return raw == null ? null : raw.trim();
    }

    /** 可选文本归一化：去首尾空白，空白视作「未填写」返回 {@code null}，以便真正清空列。 */
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
     * <p>
     * 三种合法组合：不设置 / 按金额 / 按时间。数据库有同样的 {@code ck_customer_credit_period} 约束，这里先拦，错误码才是 40000 而不是数据库异常。
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
     * @param parentCustomerId
     *            上级客户 id，{@code null} 表示独立客户，直接通过
     * @param selfId
     *            当前客户 id；新增时为 {@code null}
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

    /** 上级客户必须属于「集团」类型：只有集团才能作为结算 / 归属上级。 */
    private boolean isGroupType(Long customerTypeId) {
        if (customerTypeId == null) {
            return false;
        }
        CustomerTypeEntity type = customerTypeDao.selectById(customerTypeId);
        return type != null && Objects.equals(GROUP_TYPE_CODE, type.getTypeCode());
    }

    /**
     * 是否共享结算：结算主体不是本人，或结算主体本身就是集团。
     *
     * <p>
     * 客户的资金可见性只看这里：共享结算下不向成员账号展示共享余额和集团授信总额（见 {@code docs/decisions.md §微信小程序账号与退款分摊口径}）。商城余额 / 账期概览和结算方式选项都要调它，别各自比较
     * id 或类型编码，否则同一笔钱在两个入口的可见性会不一致。
     *
     * @param customerId
     *            当前登录的业务客户 id
     * @param settlementCustomerId
     *            解析出的结算主体 id
     * @param settlementTypeCode
     *            结算主体的客户类型编码
     */
    public static boolean sharedSettlement(Long customerId, Long settlementCustomerId, String settlementTypeCode) {
        return !Objects.equals(customerId, settlementCustomerId) || GROUP_TYPE_CODE.equals(settlementTypeCode);
    }
}
