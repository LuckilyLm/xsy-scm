package com.xsy.scm.report.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.report.dao.ReportDao;
import com.xsy.scm.report.domain.form.ScmReceiptReportQueryForm;
import com.xsy.scm.report.domain.vo.ReceiptReportVO;
import com.xsy.scm.report.support.ScmReportTimeRange;
import com.xsy.scm.report.support.ScmReportTimeRangeResolver;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;

/** 收货报表分开展示已确认收货、已入库流水和待入库记录。查询受仓库范围约束，成本无权限时不返回金额，也不把未知成本当作零。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReceiptReportService {

    private final ReportDao reportDao;

    private final ScmDataScopeService dataScopeService;

    public PageResult<ReceiptReportVO.ReceiptRow> receiptList(ScmReceiptReportQueryForm form) {
        SalesReportService.rejectClientSort(form);
        ScmReportTimeRange range = ScmReportTimeRangeResolver.resolve(form);
        ScmDataScopeContext context = dataScopeService.resolve();
        if (context.warehouseNowhere()) {
            return ScmDataScopeService.emptyPage(form);
        }
        var page = SmartPageUtil.convert2PageQuery(form);
        return SmartPageUtil.convert2PageResult(page,
                reportDao.receiptItemList(page, range.startAt(), range.endAt(), form, context.getWarehouseScope()));
    }

    public PageResult<ReceiptReportVO.InboundRow> inboundList(ScmReceiptReportQueryForm form) {
        SalesReportService.rejectClientSort(form);
        ScmReportTimeRange range = ScmReportTimeRangeResolver.resolve(form);
        ScmDataScopeContext context = dataScopeService.resolve();
        if (context.warehouseNowhere()) {
            return ScmDataScopeService.emptyPage(form);
        }
        var page = SmartPageUtil.convert2PageQuery(form);
        PageResult<ReceiptReportVO.InboundRow> result = SmartPageUtil.convert2PageResult(page,
                reportDao.inboundList(page, range.startAt(), range.endAt(), form, context.getWarehouseScope()));
        if (!context.isCostVisible() && result != null && result.getList() != null) {
            result.getList().forEach(row -> {
                row.setUnitCost(null);
                row.setCostAmount(null);
                // 无权限与「成本确实缺失」在页面上同样显示 —，但 costMissing 不能谎报为已知成本。
                row.setCostMissing(Boolean.TRUE);
            });
        }
        return result;
    }

    public PageResult<ReceiptReportVO.PendingPutawayRow> pendingPutawayList(ScmReceiptReportQueryForm form) {
        SalesReportService.rejectClientSort(form);
        ScmDataScopeContext context = dataScopeService.resolve();
        if (context.warehouseNowhere()) {
            return ScmDataScopeService.emptyPage(form);
        }
        var page = SmartPageUtil.convert2PageQuery(form);
        page.setOptimizeCountSql(false);
        return SmartPageUtil.convert2PageResult(page,
                reportDao.pendingPutawayList(page, form, context.getWarehouseScope()));
    }
}
