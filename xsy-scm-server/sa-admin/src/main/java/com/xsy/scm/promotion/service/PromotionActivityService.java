package com.xsy.scm.promotion.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.error.ScmCommonErrorCode;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.promotion.constant.PromotionErrorCode;
import com.xsy.scm.promotion.constant.ScmPromotionActivityTypeEnum;
import com.xsy.scm.promotion.constant.ScmPromotionStatusEnum;
import com.xsy.scm.promotion.dao.PromotionActivityDao;
import com.xsy.scm.promotion.domain.entity.PromotionActivityEntity;
import com.xsy.scm.promotion.domain.form.PromotionActivityForm;
import com.xsy.scm.promotion.domain.form.PromotionActivityQueryForm;
import com.xsy.scm.promotion.domain.form.PromotionStatusForm;
import com.xsy.scm.promotion.domain.vo.PromotionActivityVO;
import com.xsy.scm.promotion.support.PromotionRuleValidator;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 营销活动维护。
 *
 * <p>
 * 活动是**版本化的规则**：每次编辑 {@code version} 自增，订单优惠冻结时一并记录，
 * 因此「这单当时按哪一版算的」永远可查。
 *
 * <p>
 * 只有 {@code DRAFT} 可以编辑：已生效的活动被改内容，会让同一时间窗内的两笔订单按两套规则
 * 计算而界面上看不出来。要改就先停用再改。
 */
@Service
@RequiredArgsConstructor
public class PromotionActivityService {

    private static final Set<String> EDITABLE_STATUS = Set.of(ScmPromotionStatusEnum.DRAFT.name(),
            ScmPromotionStatusEnum.STOPPED.name());

    private final PromotionActivityDao promotionActivityDao;

    @Transactional(readOnly = true)
    public PageResult<PromotionActivityVO> queryPage(PromotionActivityQueryForm query) {
        Page<?> page = SmartPageUtil.convert2PageQuery(query);
        List<PromotionActivityVO> rows = promotionActivityDao.queryPage(page, query).stream()
                .map(PromotionActivityService::toVO).toList();
        return SmartPageUtil.convert2PageResult(page, rows);
    }

    @Transactional(readOnly = true)
    public PromotionActivityVO detail(Long id) {
        PromotionActivityEntity row = promotionActivityDao.selectById(id);
        if (row == null || Boolean.TRUE.equals(row.getDeleted())) {
            throw new ScmBusinessException(PromotionErrorCode.ACTIVITY_NOT_FOUND);
        }
        return toVO(row);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long save(PromotionActivityForm form) {
        ScmPromotionActivityTypeEnum type = requireType(form.getActivityType());
        if (!form.getValidFrom().isBefore(form.getValidTo())) {
            throw new ScmBusinessException(PromotionErrorCode.ACTIVITY_WINDOW_INVALID);
        }
        var rule = PromotionRuleValidator.validate(type, form.getRule());
        String operator = ScmOperator.current();

        if (form.getId() == null) {
            requireCodeAvailable(form.getActivityCode(), null);
            PromotionActivityEntity row = new PromotionActivityEntity();
            row.setActivityCode(form.getActivityCode().trim());
            apply(row, form, rule, type);
            row.setStatus(ScmPromotionStatusEnum.DRAFT.name());
            row.setCreatedBy(operator);
            row.setUpdatedBy(operator);
            promotionActivityDao.insert(row);
            return row.getId();
        }

        PromotionActivityEntity row = promotionActivityDao.lockById(form.getId());
        if (row == null) {
            throw new ScmBusinessException(PromotionErrorCode.ACTIVITY_NOT_FOUND);
        }
        if (!EDITABLE_STATUS.contains(row.getStatus())) {
            // 生效中的活动不能改内容：同一时间窗内会出现两套规则，而界面上看不出来
            throw new ScmBusinessException(PromotionErrorCode.ACTIVITY_STATE_INVALID);
        }
        requireCodeAvailable(form.getActivityCode(), row.getId());
        apply(row, form, rule, type);
        row.setUpdatedBy(operator);
        if (promotionActivityDao.updateById(row) != 1) {
            throw new ScmBusinessException(ScmCommonErrorCode.VERSION_CONFLICT);
        }
        return row.getId();
    }

    /**
     * 启停：{@code DRAFT/STOPPED → ACTIVE}，{@code ACTIVE → STOPPED}。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, PromotionStatusForm form) {
        if (!Set.of(ScmPromotionStatusEnum.ACTIVE.name(), ScmPromotionStatusEnum.STOPPED.name())
                .contains(form.getStatus())) {
            throw new ScmBusinessException(PromotionErrorCode.ACTIVITY_STATE_INVALID);
        }
        PromotionActivityEntity row = promotionActivityDao.lockById(id);
        if (row == null) {
            throw new ScmBusinessException(PromotionErrorCode.ACTIVITY_NOT_FOUND);
        }
        if (form.getStatus().equals(row.getStatus())) {
            return;
        }
        if (ScmPromotionStatusEnum.ACTIVE.name().equals(form.getStatus())
                && !row.getValidTo().isAfter(OffsetDateTime.now())) {
            // 已过期的活动上线只会得到一条永远不生效的规则
            throw new ScmBusinessException(PromotionErrorCode.ACTIVITY_WINDOW_INVALID);
        }
        if (promotionActivityDao.updateStatus(id, form.getStatus(), form.getVersion(),
                ScmOperator.current()) != 1) {
            throw new ScmBusinessException(ScmCommonErrorCode.VERSION_CONFLICT);
        }
    }

    private void apply(PromotionActivityEntity row, PromotionActivityForm form, java.util.Map<String, Object> rule,
            ScmPromotionActivityTypeEnum type) {
        row.setActivityName(form.getActivityName().trim());
        row.setActivityType(type.name());
        row.setExclusiveGroup(StringUtils.isBlank(form.getExclusiveGroup()) ? null
                : form.getExclusiveGroup().trim());
        row.setPriority(form.getPriority() == null ? 0 : form.getPriority());
        row.setValidFrom(form.getValidFrom());
        row.setValidTo(form.getValidTo());
        row.setRule(rule);
        row.setRemark(StringUtils.isBlank(form.getRemark()) ? null : form.getRemark().trim());
    }

    private void requireCodeAvailable(String code, Long selfId) {
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PromotionActivityEntity>()
                .eq(PromotionActivityEntity::getActivityCode, code.trim())
                .eq(PromotionActivityEntity::getDeleted, false);
        if (selfId != null) {
            wrapper.ne(PromotionActivityEntity::getId, selfId);
        }
        if (promotionActivityDao.selectCount(wrapper) > 0) {
            throw new ScmBusinessException(PromotionErrorCode.ACTIVITY_CODE_DUPLICATED);
        }
    }

    private static ScmPromotionActivityTypeEnum requireType(String activityType) {
        if (!ScmPromotionActivityTypeEnum.isSupported(activityType)) {
            throw new ScmBusinessException(PromotionErrorCode.RULE_INVALID);
        }
        return ScmPromotionActivityTypeEnum.valueOf(activityType);
    }

    static PromotionActivityVO toVO(PromotionActivityEntity row) {
        String label = ScmPromotionActivityTypeEnum.isSupported(row.getActivityType())
                ? ScmPromotionActivityTypeEnum.valueOf(row.getActivityType()).getDesc()
                : row.getActivityType();
        return PromotionActivityVO.of(row, label);
    }
}
