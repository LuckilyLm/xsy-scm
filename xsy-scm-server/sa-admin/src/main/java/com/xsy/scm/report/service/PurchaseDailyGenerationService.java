package com.xsy.scm.report.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.xsy.scm.purchase.constant.ScmPurchaseStatusEnum;
import com.xsy.scm.report.dao.PurchaseDailyReportDao;
import com.xsy.scm.report.dao.PurchaseDailySourceDao;
import com.xsy.scm.report.domain.dto.PurchaseDailySnapshotRow;
import com.xsy.scm.report.support.ScmReportTimeRangeResolver;
import lombok.RequiredArgsConstructor;

/** 只由后台任务调用；生成全量快照，读取时再应用登录用户的数据范围。 */
@Service
@RequiredArgsConstructor
public class PurchaseDailyGenerationService {
    private static final int INSERT_BATCH_SIZE = 500;
    private static final List<ScmPurchaseStatusEnum> INCLUDED_STATUSES = List.of(
            ScmPurchaseStatusEnum.SUBMITTED, ScmPurchaseStatusEnum.PARTIALLY_RECEIVED,
            ScmPurchaseStatusEnum.RECEIVED, ScmPurchaseStatusEnum.SHORT_CLOSED);

    private final PurchaseDailyReportDao purchaseDailyReportDao;
    private final PurchaseDailySourceDao purchaseDailySourceDao;

    @Transactional(rollbackFor = Exception.class)
    public boolean generate(LocalDate reportDate) {
        if (reportDate == null || !reportDate.isBefore(LocalDate.now(ScmReportTimeRangeResolver.BUSINESS_ZONE))) {
            throw new IllegalArgumentException("只能生成北京时间今天之前的采购清单");
        }
        // 日期唯一约束是跨实例幂等屏障，表头与全部明细同事务提交；失败不会留下半张清单。
        if (purchaseDailyReportDao.claimDate(reportDate) == 0) {
            return false;
        }
        List<PurchaseDailySnapshotRow> rows = purchaseDailySourceDao.aggregateSubmittedOrders(
                reportDate.atStartOfDay(ScmReportTimeRangeResolver.BUSINESS_ZONE).toOffsetDateTime(),
                reportDate.plusDays(1).atStartOfDay(ScmReportTimeRangeResolver.BUSINESS_ZONE).toOffsetDateTime(),
                INCLUDED_STATUSES);
        for (int start = 0; start < rows.size(); start += INSERT_BATCH_SIZE) {
            purchaseDailyReportDao.insertRows(reportDate,
                    rows.subList(start, Math.min(start + INSERT_BATCH_SIZE, rows.size())));
        }
        // 无采购的日期仍保存表头，以区分“已生成空清单”和“任务未执行”。
        return true;
    }
}
