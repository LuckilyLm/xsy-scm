package net.lab1024.sa.admin.module.scm.report.service;

import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeContext;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeService;
import net.lab1024.sa.admin.module.scm.inventory.constant.ScmInventoryMovementTypeEnum;
import net.lab1024.sa.admin.module.scm.report.dao.ReportDao;
import net.lab1024.sa.admin.module.scm.report.domain.form.ScmInventoryReportQueryForm;
import net.lab1024.sa.admin.module.scm.report.domain.vo.InventoryReportVO;
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
 *
 * <p><b>仓库授权范围</b>：五条查询的事实表都带 {@code warehouse_id}，因此与库存域的余额 / 流水页
 * 同一口径（{@code employee_warehouse_scope} 授权行或 {@code scm:inventory:scope:all:query}）。
 * 范围为空即返回空结果，不去跑一次恒假查询换回一张全 0 的汇总表。
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

    private final ScmDataScopeService dataScopeService;

    public PageResult<InventoryReportVO.MovementRow> movementList(ScmInventoryReportQueryForm form) {
        SalesReportService.rejectClientSort(form);
        ScmReportTimeRange range = ScmReportTimeRangeResolver.resolve(form);
        ScmDataScopeContext context = dataScopeService.resolve();
        if (context.warehouseNowhere()) {
            return ScmDataScopeService.emptyPage(form);
        }
        var page = SmartPageUtil.convert2PageQuery(form);
        PageResult<InventoryReportVO.MovementRow> result = SmartPageUtil.convert2PageResult(page,
                reportDao.movementList(page, range.startAt(), range.endAt(), INBOUND_MOVEMENT_TYPES, form,
                        context.getWarehouseScope()));
        if (result != null && result.getList() != null) {
            result.getList().forEach(row -> {
                ScmInventoryMovementTypeEnum type = ScmInventoryMovementTypeEnum.of(row.getMovementType());
                row.setDirection(type == null ? null : (type.isInbound() ? "入库" : "出库"));
                if (!context.isCostVisible()) {
                    row.setUnitCost(null);
                    row.setCostAmount(null);
                }
            });
        }
        return result;
    }

    public InventoryReportVO.LossSummary lossSummary(ScmInventoryReportQueryForm form) {
        ScmReportTimeRange range = ScmReportTimeRangeResolver.resolve(form);
        ScmDataScopeContext context = dataScopeService.resolve();
        if (context.warehouseNowhere()) {
            // 损耗汇总的每个指标都是流水上的仓库事实：无授权仓库时一个都不可知，给空对象而不是 0。
            return new InventoryReportVO.LossSummary();
        }
        InventoryReportVO.LossSummary vo = reportDao.lossSummary(range.startAt(), range.endAt(), form,
                context.getWarehouseScope());
        if (vo != null && !context.isCostVisible()) {
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
        ScmDataScopeContext context = dataScopeService.resolve();
        if (context.warehouseNowhere()) {
            return ScmDataScopeService.emptyPage(form);
        }
        var page = SmartPageUtil.convert2PageQuery(form);
        PageResult<InventoryReportVO.LossRow> result = SmartPageUtil.convert2PageResult(page,
                reportDao.lossList(page, range.startAt(), range.endAt(), form, context.getWarehouseScope()));
        if (!context.isCostVisible() && result != null && result.getList() != null) {
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
        ScmDataScopeContext context = dataScopeService.resolve();
        if (context.warehouseNowhere()) {
            return ScmDataScopeService.emptyPage(form);
        }
        var page = SmartPageUtil.convert2PageQuery(form);
        return SmartPageUtil.convert2PageResult(page,
                reportDao.inventoryValueList(page, form, context.getWarehouseScope()));
    }

    /** 收发存是数量口径，不需要成本权限；但仍与其余库存查询共用同一份仓库范围。 */
    public PageResult<InventoryReportVO.FlowSummaryRow> flowSummary(ScmInventoryReportQueryForm form) {
        SalesReportService.rejectClientSort(form);
        ScmReportTimeRange range = ScmReportTimeRangeResolver.resolve(form);
        ScmDataScopeContext context = dataScopeService.resolve();
        if (context.warehouseNowhere()) {
            return ScmDataScopeService.emptyPage(form);
        }
        var page = SmartPageUtil.convert2PageQuery(form);
        page.setOptimizeCountSql(false);
        return SmartPageUtil.convert2PageResult(page,
                reportDao.flowSummary(page, range.startAt(), range.endAt(), INBOUND_MOVEMENT_TYPES, form,
                        context.getWarehouseScope()));
    }
}
