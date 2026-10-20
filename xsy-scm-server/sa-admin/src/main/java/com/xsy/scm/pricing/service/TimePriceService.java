package com.xsy.scm.pricing.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.pricing.constant.PricingErrorCode;
import com.xsy.scm.pricing.constant.ScmPriceOperationTypeEnum;
import com.xsy.scm.pricing.dao.TimePriceDao;
import com.xsy.scm.pricing.domain.entity.TimePriceEntity;
import com.xsy.scm.pricing.domain.form.TimePriceForm;
import com.xsy.scm.pricing.domain.form.TimePriceQueryForm;
import com.xsy.scm.pricing.domain.vo.TimePriceVO;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 时价维护。
 *
 * <p>
 * 时价是商品规格级的短期价格，生效区间半开 [start, end)。同一规格的区间不允许重叠： 重叠会让「现在按哪条算」没有唯一答案，而价格不能靠猜。
 */
@Service
@RequiredArgsConstructor
public class TimePriceService {

    private final TimePriceDao timePriceDao;

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PageResult<TimePriceVO> query(TimePriceQueryForm form) {
        Page<TimePriceVO> page = new Page<>(form.getPageNum(), form.getPageSize());
        List<TimePriceVO> rows = timePriceDao.queryPage(page, form);
        OffsetDateTime now = OffsetDateTime.now();
        rows.forEach(row -> row.setEffectiveNow(row.getStartAt() != null && row.getEndAt() != null
                && !row.getStartAt().isAfter(now) && row.getEndAt().isAfter(now)));
        PageResult<TimePriceVO> result = new PageResult<>();
        result.setPageNum(page.getCurrent());
        result.setPageSize(page.getSize());
        result.setTotal(page.getTotal());
        result.setPages(page.getPages());
        result.setList(rows);
        result.setEmptyFlag(rows.isEmpty());
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public TimePriceVO create(TimePriceForm form, String operator) {
        requireValidWindow(form);
        if (timePriceDao.countOverlapping(form.getSkuId(), form.getStartAt(), form.getEndAt(), null) > 0) {
            throw new ScmBusinessException(PricingErrorCode.TIME_PRICE_OVERLAP);
        }
        TimePriceEntity entity = new TimePriceEntity();
        entity.setSkuId(form.getSkuId());
        entity.setUnitPrice(form.getUnitPrice());
        entity.setStartAt(form.getStartAt());
        entity.setEndAt(form.getEndAt());
        entity.setRemark(trimToNull(form.getRemark()));
        entity.setCreatedBy(operator);
        entity.setUpdatedBy(operator);
        timePriceDao.insert(entity);
        log(entity, ScmPriceOperationTypeEnum.CREATE, null);
        return timePriceDao.detail(entity.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public TimePriceVO update(TimePriceForm form, String operator) {
        if (form.getId() == null || form.getVersion() == null) {
            throw new ScmBusinessException(PricingErrorCode.TIME_PRICE_NOT_FOUND);
        }
        requireValidWindow(form);
        TimePriceEntity existing = timePriceDao.selectById(form.getId());
        if (existing == null || Boolean.TRUE.equals(existing.getDeleted())) {
            throw new ScmBusinessException(PricingErrorCode.TIME_PRICE_NOT_FOUND);
        }
        if (timePriceDao.countOverlapping(form.getSkuId(), form.getStartAt(), form.getEndAt(), form.getId()) > 0) {
            throw new ScmBusinessException(PricingErrorCode.TIME_PRICE_OVERLAP);
        }
        String beforeSnapshot = snapshot(existing);
        TimePriceEntity entity = new TimePriceEntity();
        entity.setId(form.getId());
        entity.setVersion(form.getVersion());
        entity.setSkuId(form.getSkuId());
        entity.setUnitPrice(form.getUnitPrice());
        entity.setStartAt(form.getStartAt());
        entity.setEndAt(form.getEndAt());
        entity.setRemark(trimToNull(form.getRemark()));
        entity.setUpdatedBy(operator);
        if (timePriceDao.updateById(entity) == 0) {
            throw new ScmBusinessException(PricingErrorCode.TIME_PRICE_UPDATE_CONFLICT);
        }
        log(entity, ScmPriceOperationTypeEnum.UPDATE, beforeSnapshot);
        return timePriceDao.detail(form.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Integer version, String operator) {
        TimePriceEntity existing = timePriceDao.selectById(id);
        if (existing == null) {
            throw new ScmBusinessException(PricingErrorCode.TIME_PRICE_NOT_FOUND);
        }
        String beforeSnapshot = snapshot(existing);
        if (timePriceDao.softDelete(id, version, operator) == 0) {
            throw new ScmBusinessException(PricingErrorCode.TIME_PRICE_UPDATE_CONFLICT);
        }
        log(existing, ScmPriceOperationTypeEnum.DELETE, beforeSnapshot);
    }

    /** 审计快照：只放人能读懂的业务字段，不放数据库内部键。 */
    private String snapshot(TimePriceEntity entity) {
        var fields = new java.util.LinkedHashMap<String, Object>();
        fields.put("id", entity.getId());
        fields.put("skuId", entity.getSkuId());
        fields.put("unitPrice",
                entity.getUnitPrice() == null ? null : entity.getUnitPrice().setScale(4).toPlainString());
        fields.put("startAt", entity.getStartAt() == null ? null : entity.getStartAt().toString());
        fields.put("endAt", entity.getEndAt() == null ? null : entity.getEndAt().toString());
        fields.put("remark", entity.getRemark());
        fields.put("version", entity.getVersion());
        fields.put("deleted", entity.getDeleted());
        try {
            return objectMapper.writeValueAsString(fields);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize time price audit", ex);
        }
    }

    private void log(TimePriceEntity entity, ScmPriceOperationTypeEnum operationType, String beforeSnapshot) {
        timePriceDao.log(entity.getId(), operationType.name(), com.xsy.scm.common.constant.ScmOperator.current(),
                beforeSnapshot, snapshot(entity));
    }

    private void requireValidWindow(TimePriceForm form) {
        if (!form.getStartAt().isBefore(form.getEndAt())) {
            throw new ScmBusinessException(PricingErrorCode.TIME_PRICE_WINDOW_INVALID);
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
