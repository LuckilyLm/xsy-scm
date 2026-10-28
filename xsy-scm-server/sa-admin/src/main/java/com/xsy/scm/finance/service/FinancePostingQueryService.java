package com.xsy.scm.finance.service;

import java.util.List;

import com.xsy.scm.common.error.ScmCommonErrorCode;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.common.time.ScmDateTimeRange;
import com.xsy.scm.common.time.ScmDateTimeRangeResolver;
import com.xsy.scm.finance.constant.FinanceErrorCode;
import com.xsy.scm.finance.constant.ScmFinancePostingRefundStatusEnum;
import com.xsy.scm.finance.constant.ScmFinancePostingStatusEnum;
import com.xsy.scm.finance.dao.FinancePostingDao;
import com.xsy.scm.finance.domain.form.FinancePostingQueryForm;
import com.xsy.scm.finance.domain.vo.FinancePostingVO;
import com.xsy.scm.finance.support.FinanceExportGuard;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 过账列表、详情与导出；可见性按过账客户的归属业务员判定。
 *
 * <p>
 * 这就是现场口径里的「内部财务报表可单独筛选查看所有过账记录」：日期、客户、返款状态、有效状态 与单号关键字都在同一个查询里，导出的行与列表完全一致。
 */
@Service
@RequiredArgsConstructor
public class FinancePostingQueryService {

    private final FinancePostingDao financePostingDao;
    private final ScmDataScopeService dataScopeService;

    @Transactional(readOnly = true)
    public PageResult<FinancePostingVO> query(FinancePostingQueryForm form) {
        normalize(form);
        ScmDateTimeRange timeRange = ScmDateTimeRangeResolver.resolve(form.getStartDate(), form.getEndDate());
        ScmDataScopeContext scope = dataScopeService.resolve();
        if (scope.getCustomerSellerScope().isEmpty()) {
            return ScmDataScopeService.emptyPage(form);
        }
        var page = SmartPageUtil.convert2PageQuery(form);
        page.setOptimizeCountSql(false);
        List<FinancePostingVO> rows = financePostingDao.queryPage(page, form, scope, timeRange);
        return SmartPageUtil.convert2PageResult(page, rows);
    }

    @Transactional(readOnly = true)
    public FinancePostingVO detail(Long postingId) {
        ScmDataScopeContext scope = dataScopeService.resolve();
        FinancePostingVO detail = scope.getCustomerSellerScope().isEmpty()
            ? null
            : financePostingDao.selectDetail(postingId, scope);
        if (detail == null) {
            throw new ScmBusinessException(FinanceErrorCode.POSTING_NOT_FOUND);
        }
        return detail;
    }

    @Transactional(readOnly = true)
    public List<FinancePostingVO> exportRows(FinancePostingQueryForm form) {
        normalize(form);
        ScmDataScopeContext scope = dataScopeService.resolve();
        if (scope.getCustomerSellerScope().isEmpty()) {
            return List.of();
        }
        ScmDateTimeRange timeRange = ScmDateTimeRangeResolver.resolve(form.getStartDate(), form.getEndDate());
        return FinanceExportGuard.exportRows(pageSize -> {
            var page = SmartPageUtil.convert2PageQuery(form);
            page.setCurrent(1L);
            page.setSize(pageSize);
            page.setSearchCount(false);
            return financePostingDao.queryPage(page, form, scope, timeRange);
        });
    }

    /**
     * 白名单与排序项在查询与导出共用同一段校验，避免「列表报错、导出静默通过」。
     */
    private static void normalize(FinancePostingQueryForm form) {
        if (form.getSortItemList() != null && !form.getSortItemList().isEmpty()) {
            throw new ScmBusinessException(ScmCommonErrorCode.VALIDATION_ERROR);
        }
        form.setKeyword(StringUtils.trimToNull(form.getKeyword()));
        String refundStatus = StringUtils.trimToNull(form.getRefundStatus());
        if (refundStatus != null && !isRefundStatus(refundStatus)) {
            throw new ScmBusinessException(ScmCommonErrorCode.VALIDATION_ERROR);
        }
        form.setRefundStatus(refundStatus);
        String status = StringUtils.trimToNull(form.getStatus());
        if (status != null && !isStatus(status)) {
            throw new ScmBusinessException(ScmCommonErrorCode.VALIDATION_ERROR);
        }
        form.setStatus(status);
    }

    private static boolean isRefundStatus(String value) {
        for (ScmFinancePostingRefundStatusEnum candidate : ScmFinancePostingRefundStatusEnum.values()) {
            if (candidate.name().equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isStatus(String value) {
        for (ScmFinancePostingStatusEnum candidate : ScmFinancePostingStatusEnum.values()) {
            if (candidate.name().equals(value)) {
                return true;
            }
        }
        return false;
    }
}
