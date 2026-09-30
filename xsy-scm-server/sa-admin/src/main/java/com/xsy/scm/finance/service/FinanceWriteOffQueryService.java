package com.xsy.scm.finance.service;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import com.xsy.scm.common.error.ScmCommonErrorCode;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.common.time.ScmDateTimeRange;
import com.xsy.scm.common.time.ScmDateTimeRangeResolver;
import com.xsy.scm.finance.constant.ScmFinanceReverseEntryTypeEnum;
import com.xsy.scm.finance.dao.FinanceWriteOffDao;
import com.xsy.scm.finance.domain.form.FinanceWriteOffQueryForm;
import com.xsy.scm.finance.domain.vo.FinanceWriteOffVO;
import com.xsy.scm.finance.support.FinanceExportGuard;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 核销分页查询；每条记录的可见性跟随目标单据范围。 */
@Service
@RequiredArgsConstructor
public class FinanceWriteOffQueryService {

    private final FinanceWriteOffDao financeWriteOffDao;
    private final ScmDataScopeService dataScopeService;

    @Transactional(readOnly = true)
    public PageResult<FinanceWriteOffVO> query(FinanceWriteOffQueryForm form) {
        if (form.getSortItemList() != null && !form.getSortItemList().isEmpty()) {
            throw new ScmBusinessException(ScmCommonErrorCode.VALIDATION_ERROR);
        }
        if (!StringUtils.isBlank(form.getEntryType()) && !isEntryType(StringUtils.trimToNull(form.getEntryType()))) {
            throw new ScmBusinessException(ScmCommonErrorCode.VALIDATION_ERROR);
        }
        form.setSourceNo(StringUtils.trimToNull(form.getSourceNo()));
        form.setTargetNo(StringUtils.trimToNull(form.getTargetNo()));
        if (!StringUtils.isBlank(form.getEntryType())) {
            form.setEntryType(StringUtils.trimToNull(form.getEntryType()));
        }
        ScmDateTimeRange timeRange = ScmDateTimeRangeResolver.resolve(form.getStartDate(), form.getEndDate());

        ScmDataScopeContext scope = dataScopeService.resolve();
        if (scope.getOrderSellerScope().isEmpty() && scope.getPurchaserScope().isEmpty()) {
            return ScmDataScopeService.emptyPage(form);
        }
        var page = SmartPageUtil.convert2PageQuery(form);
        page.setOptimizeCountSql(false);
        List<FinanceWriteOffVO> rows = financeWriteOffDao.queryPage(page, form, scope, timeRange);
        return SmartPageUtil.convert2PageResult(page, rows);
    }

    @Transactional(readOnly = true)
    public List<FinanceWriteOffVO> exportRows(FinanceWriteOffQueryForm form) {
        ScmDataScopeContext scope = dataScopeService.resolve();
        if (scope.getOrderSellerScope().isEmpty() && scope.getPurchaserScope().isEmpty()) {
            return List.of();
        }
        normalize(form);
        ScmDateTimeRange timeRange = ScmDateTimeRangeResolver.resolve(form.getStartDate(), form.getEndDate());
        return FinanceExportGuard.exportRows(pageSize -> {
            var page = SmartPageUtil.convert2PageQuery(form);
            page.setCurrent(1L);
            page.setSize(pageSize);
            page.setSearchCount(false);
            return financeWriteOffDao.queryPage(page, form, scope, timeRange);
        });
    }

    public List<FinanceWriteOffVO> byTarget(String targetType, Long targetId, ScmDataScopeContext scope) {
        return financeWriteOffDao.selectForTarget(targetType, targetId, scope);
    }

    public List<FinanceWriteOffVO> bySource(String sourceType, Long sourceId, ScmDataScopeContext scope) {
        return financeWriteOffDao.selectForSource(sourceType, sourceId, scope);
    }

    private static void normalize(FinanceWriteOffQueryForm form) {
        if (form.getSortItemList() != null && !form.getSortItemList().isEmpty()) {
            throw new ScmBusinessException(ScmCommonErrorCode.VALIDATION_ERROR);
        }
        if (form.getEntryType() != null && !isEntryType(StringUtils.trimToNull(form.getEntryType()))) {
            throw new ScmBusinessException(ScmCommonErrorCode.VALIDATION_ERROR);
        }
        form.setSourceNo(StringUtils.trimToNull(form.getSourceNo()));
        form.setTargetNo(StringUtils.trimToNull(form.getTargetNo()));
        form.setEntryType(StringUtils.trimToNull(form.getEntryType()));
    }

    private static boolean isEntryType(String value) {
        for (ScmFinanceReverseEntryTypeEnum candidate : ScmFinanceReverseEntryTypeEnum.values()) {
            if (candidate.name().equals(value)) {
                return true;
            }
        }
        return false;
    }

}
