package net.lab1024.sa.admin.module.scm.report.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.report.dao.ReportDao;
import net.lab1024.sa.admin.module.scm.report.domain.form.ScmReceiptReportQueryForm;
import net.lab1024.sa.admin.module.scm.report.domain.vo.ReceiptReportVO;
import net.lab1024.sa.admin.module.scm.report.support.ScmReportAccess;
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
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReceiptReportService {

    private final ReportDao reportDao;

    public PageResult<ReceiptReportVO.ReceiptRow> receiptList(ScmReceiptReportQueryForm form) {
        SalesReportService.rejectClientSort(form);
        ScmReportTimeRange range = ScmReportTimeRangeResolver.resolve(form);
        var page = SmartPageUtil.convert2PageQuery(form);
        return SmartPageUtil.convert2PageResult(page,
                reportDao.receiptItemList(page, range.startAt(), range.endAt(), form));
    }

    public PageResult<ReceiptReportVO.InboundRow> inboundList(ScmReceiptReportQueryForm form) {
        SalesReportService.rejectClientSort(form);
        ScmReportTimeRange range = ScmReportTimeRangeResolver.resolve(form);
        var page = SmartPageUtil.convert2PageQuery(form);
        PageResult<ReceiptReportVO.InboundRow> result = SmartPageUtil.convert2PageResult(page,
                reportDao.inboundList(page, range.startAt(), range.endAt(), form));
        if (!ScmReportAccess.canViewCost() && result != null && result.getList() != null) {
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
        var page = SmartPageUtil.convert2PageQuery(form);
        page.setOptimizeCountSql(false);
        return SmartPageUtil.convert2PageResult(page, reportDao.pendingPutawayList(page, form));
    }
}
