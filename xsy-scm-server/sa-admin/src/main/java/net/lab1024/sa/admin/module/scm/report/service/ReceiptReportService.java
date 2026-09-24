package net.lab1024.sa.admin.module.scm.report.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeContext;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeService;
import net.lab1024.sa.admin.module.scm.report.dao.ReportDao;
import net.lab1024.sa.admin.module.scm.report.domain.form.ScmReceiptReportQueryForm;
import net.lab1024.sa.admin.module.scm.report.domain.vo.ReceiptReportVO;
import net.lab1024.sa.admin.module.scm.report.support.ScmReportTimeRange;
import net.lab1024.sa.admin.module.scm.report.support.ScmReportTimeRangeResolver;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;

/**
 * 收货与入库（只读）。三张表分别对应两条独立生命周期：
 * 收货明细看 {@code purchase_receipt.status=CONFIRMED}（商业确认），
 * 入库明细看 {@code PURCHASE_IN} 流水（库存真正入账），
 * 待入库看 {@code WAREHOUSE_CONFIRM + PENDING}。
 *
 * <p>本页不提供任何入库写入口：确认入库属于库存域的写流程与权限（822），
 * 在报表页开写入口会让同一动作出现两个入口与两套权限。
 *
 * <p>三张事实表都带 {@code warehouse_id}，因此三条查询一律按调用者的仓库授权范围收窄；
 * 范围为空即空分页。入库明细的成本列在无成本权限时抹成 null，且 {@code costMissing}
 * 必须同时置真，不能让「无权限」被读成「成本已知为 0」。
 */
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
                reportDao.receiptItemList(page, range.startAt(), range.endAt(), form,
                        context.getWarehouseScope()));
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
