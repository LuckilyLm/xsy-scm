package net.lab1024.sa.admin.module.scm.report.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.report.dao.ReportDao;
import net.lab1024.sa.admin.module.scm.report.domain.form.ScmSalesReportQueryForm;
import net.lab1024.sa.admin.module.scm.report.domain.vo.SalesReportVO;
import net.lab1024.sa.admin.module.scm.report.support.ScmReportTimeRange;
import net.lab1024.sa.admin.module.scm.report.support.ScmReportTimeRangeResolver;
import net.lab1024.sa.base.common.domain.PageParam;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;

import static net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR;

/**
 * 销售分析（只读）。全部维度固定使用 {@code CONFIRMED + confirmed_at + settlement_*} 口径。
 *
 * <p>不接受客户端排序：这些查询都是 join + group by，裸列名在多张表里同名，交给框架拼
 * ORDER BY 会产生歧义列；排序语义由 SQL 固定，因此对 {@code sortItemList} 显式报错而不是静默忽略。
 *
 * <p><b>本类刻意不按仓库范围收窄</b>：销售事实 {@code sales_order} / {@code sales_order_item} /
 * {@code order_refund} 上没有仓库列（订单可跨仓履约，见 {@code ScmSalesReportQueryForm} 类注释），
 * 而 {@code created_by} 是审计字段、不得当作数据范围依据（裁决第 3 条），
 * 按 {@code seller_id} 收窄又会把「财务看全组织销售额」变成「只看自己名下」——那是角色口径，
 * 不是仓库范围。因此销售侧报表的可见性只由页面权限
 * （{@code scm:report:sales:query}）承担；等订单有真实履约仓库事实后再纳入仓库范围。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalesReportService {

    /** TOP 图由 DB 直接聚合取前 N，不取全量回 Java 排序。 */
    private static final int TOP_LIMIT = 5;

    private final ReportDao reportDao;

    public PageResult<SalesReportVO.ProductRow> byProduct(ScmSalesReportQueryForm form) {
        rejectClientSort(form);
        ScmReportTimeRange range = ScmReportTimeRangeResolver.resolve(form);
        var page = SmartPageUtil.convert2PageQuery(form);
        page.setOptimizeCountSql(false);
        return SmartPageUtil.convert2PageResult(page,
                reportDao.salesByProduct(page, range.startAt(), range.endAt(), form));
    }

    public List<SalesReportVO.TopItem> topProduct(ScmSalesReportQueryForm form) {
        ScmReportTimeRange range = ScmReportTimeRangeResolver.resolve(form);
        return reportDao.topSalesProduct(range.startAt(), range.endAt(), TOP_LIMIT, form);
    }

    public PageResult<SalesReportVO.CategoryRow> byCategory(ScmSalesReportQueryForm form) {
        rejectClientSort(form);
        ScmReportTimeRange range = ScmReportTimeRangeResolver.resolve(form);
        var page = SmartPageUtil.convert2PageQuery(form);
        page.setOptimizeCountSql(false);
        return SmartPageUtil.convert2PageResult(page,
                reportDao.salesByCategory(page, range.startAt(), range.endAt(), form));
    }

    public List<SalesReportVO.TopItem> topCategory(ScmSalesReportQueryForm form) {
        ScmReportTimeRange range = ScmReportTimeRangeResolver.resolve(form);
        return reportDao.topSalesCategory(range.startAt(), range.endAt(), TOP_LIMIT, form);
    }

    public PageResult<SalesReportVO.CustomerRow> byCustomer(ScmSalesReportQueryForm form) {
        rejectClientSort(form);
        ScmReportTimeRange range = ScmReportTimeRangeResolver.resolve(form);
        var page = SmartPageUtil.convert2PageQuery(form);
        page.setOptimizeCountSql(false);
        return SmartPageUtil.convert2PageResult(page,
                reportDao.salesByCustomer(page, range.startAt(), range.endAt(), form));
    }

    public List<SalesReportVO.TopItem> topCustomer(ScmSalesReportQueryForm form) {
        ScmReportTimeRange range = ScmReportTimeRangeResolver.resolve(form);
        return reportDao.topSalesCustomer(range.startAt(), range.endAt(), TOP_LIMIT, form);
    }

    public PageResult<SalesReportVO.SellerRow> bySeller(ScmSalesReportQueryForm form) {
        rejectClientSort(form);
        ScmReportTimeRange range = ScmReportTimeRangeResolver.resolve(form);
        var page = SmartPageUtil.convert2PageQuery(form);
        page.setOptimizeCountSql(false);
        return SmartPageUtil.convert2PageResult(page,
                reportDao.salesBySeller(page, range.startAt(), range.endAt(), form));
    }

    public PageResult<SalesReportVO.ItemRow> itemList(ScmSalesReportQueryForm form) {
        rejectClientSort(form);
        ScmReportTimeRange range = ScmReportTimeRangeResolver.resolve(form);
        var page = SmartPageUtil.convert2PageQuery(form);
        return SmartPageUtil.convert2PageResult(page,
                reportDao.salesItemList(page, range.startAt(), range.endAt(), form));
    }

    static void rejectClientSort(PageParam form) {
        if (form.getSortItemList() != null && !form.getSortItemList().isEmpty()) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
    }
}
