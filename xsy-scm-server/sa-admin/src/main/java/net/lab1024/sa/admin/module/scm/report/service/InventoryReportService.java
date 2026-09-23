package net.lab1024.sa.admin.module.scm.report.service;

import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.inventory.constant.ScmInventoryMovementTypeEnum;
import net.lab1024.sa.admin.module.scm.report.dao.ReportDao;
import net.lab1024.sa.admin.module.scm.report.domain.form.ScmInventoryReportQueryForm;
import net.lab1024.sa.admin.module.scm.report.domain.vo.InventoryReportVO;
import net.lab1024.sa.admin.module.scm.report.support.ScmReportAccess;
import net.lab1024.sa.admin.module.scm.report.support.ScmReportTimeRange;
import net.lab1024.sa.admin.module.scm.report.support.ScmReportTimeRangeResolver;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;

/**
 * 库存分析（只读）。事实源是 append-only 的 {@code inventory_movement} 与当前 {@code inventory_balance}。
 *
 * <p><b>方向不重复定义</b>：入 / 出 由 {@link ScmInventoryMovementTypeEnum#isInbound()} 派生后传入 SQL，
 * 报表不自建第二套 IN / OUT 清单 —— 一旦出现两份，新增流水类型时必然忘记其中一份。
 *
 * <p><b>不提供历史期初 / 期末</b>：流水存的是本次 {@code unit_cost}，不是每次变动后的 {@code avg_cost}，
 * 且账本不是从库存起点完整覆盖的，因此历史期初期末均价无法还原，宁可不给。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryReportService {

    private static final List<String> INBOUND_MOVEMENT_TYPES = Arrays
            .stream(ScmInventoryMovementTypeEnum.values())
            .filter(ScmInventoryMovementTypeEnum::isInbound)
            .map(Enum::name)
            .toList();

    private final ReportDao reportDao;

    public PageResult<InventoryReportVO.MovementRow> movementList(ScmInventoryReportQueryForm form) {
        SalesReportService.rejectClientSort(form);
        ScmReportTimeRange range = ScmReportTimeRangeResolver.resolve(form);
        var page = SmartPageUtil.convert2PageQuery(form);
        PageResult<InventoryReportVO.MovementRow> result = SmartPageUtil.convert2PageResult(page,
                reportDao.movementList(page, range.startAt(), range.endAt(), INBOUND_MOVEMENT_TYPES, form));
        boolean costVisible = ScmReportAccess.canViewCost();
        if (result != null && result.getList() != null) {
            result.getList().forEach(row -> {
                ScmInventoryMovementTypeEnum type = ScmInventoryMovementTypeEnum.of(row.getMovementType());
                row.setDirection(type == null ? null : (type.isInbound() ? "入库" : "出库"));
                if (!costVisible) {
                    row.setUnitCost(null);
                    row.setCostAmount(null);
                }
            });
        }
        return result;
    }

    public InventoryReportVO.LossSummary lossSummary(ScmInventoryReportQueryForm form) {
        ScmReportTimeRange range = ScmReportTimeRangeResolver.resolve(form);
        InventoryReportVO.LossSummary vo = reportDao.lossSummary(range.startAt(), range.endAt(), form);
        if (vo != null && !ScmReportAccess.canViewCost()) {
            vo.setStocktakeLossCostAmount(null);
            vo.setLossReportCostAmount(null);
            vo.setTotalLossCostAmount(null);
            vo.setCostMissingCount(null);
        }
        return vo;
    }

    public PageResult<InventoryReportVO.LossRow> lossList(ScmInventoryReportQueryForm form) {
        SalesReportService.rejectClientSort(form);
        ScmReportTimeRange range = ScmReportTimeRangeResolver.resolve(form);
        var page = SmartPageUtil.convert2PageQuery(form);
        PageResult<InventoryReportVO.LossRow> result = SmartPageUtil.convert2PageResult(page,
                reportDao.lossList(page, range.startAt(), range.endAt(), form));
        if (!ScmReportAccess.canViewCost() && result != null && result.getList() != null) {
            result.getList().forEach(row -> {
                row.setUnitCost(null);
                row.setCostAmount(null);
            });
        }
        return result;
    }

    /**
     * 当前库存账面价值。整个页面就是成本视图，因此由 Controller 用
     * {@code scm:report:cost:query} 直接拦住，这里不再做字段级抹除。
     */
    public PageResult<InventoryReportVO.ValueRow> valueList(ScmInventoryReportQueryForm form) {
        SalesReportService.rejectClientSort(form);
        ScmReportTimeRangeResolver.resolve(form);
        var page = SmartPageUtil.convert2PageQuery(form);
        return SmartPageUtil.convert2PageResult(page, reportDao.inventoryValueList(page, form));
    }

    public PageResult<InventoryReportVO.FlowSummaryRow> flowSummary(ScmInventoryReportQueryForm form) {
        SalesReportService.rejectClientSort(form);
        ScmReportTimeRange range = ScmReportTimeRangeResolver.resolve(form);
        var page = SmartPageUtil.convert2PageQuery(form);
        page.setOptimizeCountSql(false);
        return SmartPageUtil.convert2PageResult(page,
                reportDao.flowSummary(page, range.startAt(), range.endAt(), INBOUND_MOVEMENT_TYPES, form));
    }
}
