package com.xsy.scm.report.service;

import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.inventory.constant.ScmInventoryMovementTypeEnum;
import com.xsy.scm.report.dao.ReportDao;
import com.xsy.scm.report.domain.form.ScmInventoryReportQueryForm;
import com.xsy.scm.report.domain.form.ScmNegativeStockReportQueryForm;
import com.xsy.scm.report.domain.vo.InventoryReportVO;
import com.xsy.scm.report.support.ScmReportTimeRange;
import com.xsy.scm.report.support.ScmReportTimeRangeResolver;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;

/**
 * 库存分析以库存流水与当前余额为数据来源。历史出入库按流水统计，当前库存按余额统计，不能混用时间范围。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryReportService {

    private static final List<String> INBOUND_MOVEMENT_TYPES = Arrays.stream(ScmInventoryMovementTypeEnum.values())
        .filter(ScmInventoryMovementTypeEnum::isInbound).map(Enum::name).toList();

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
        PageResult<
            InventoryReportVO.MovementRow> result = SmartPageUtil.convert2PageResult(page, reportDao.movementList(page,
                range.startAt(), range.endAt(), INBOUND_MOVEMENT_TYPES, form, context.getWarehouseScope()));
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
            // 损耗汇总的每个指标都来自仓库流水：无授权仓库时一个都不可知，给空对象而不是 0。
            return new InventoryReportVO.LossSummary();
        }
        InventoryReportVO.LossSummary vo = reportDao.lossSummary(range.startAt(), range.endAt(), form,
            context.getWarehouseScope());
        if (vo == null)
            vo = new InventoryReportVO.LossSummary();
        List<InventoryReportVO.LossUnitRow> units = reportDao.lossQuantitySummary(range.startAt(), range.endAt(), form,
            context.getWarehouseScope());
        vo.setStocktakeLossQuantityText(lossQuantityText(units, ScmInventoryMovementTypeEnum.STOCKTAKE_LOSS.name()));
        vo.setLossReportQuantityText(lossQuantityText(units, ScmInventoryMovementTypeEnum.LOSS_REPORT.name()));
        if (!context.isCostVisible()) {
            vo.setStocktakeLossCostAmount(null);
            vo.setLossReportCostAmount(null);
            vo.setTotalLossCostAmount(null);
            vo.setCostMissingCount(null);
            vo.setDailyTrend(List.of());
        } else {
            vo.setDailyTrend(
                reportDao.lossDailyTrend(range.startAt(), range.endAt(), form, context.getWarehouseScope()));
        }
        return vo;
    }

    /**
     * 数量精度来自库存流水，先按单位汇总后拼文字，不将箱/公斤相加。
     */
    static String lossQuantityText(List<InventoryReportVO.LossUnitRow> units, String movementType) {
        if (units == null)
            return null;
        String grouped = units.stream()
            .filter(
                row -> row != null && Objects.equals(movementType, row.getMovementType()) && row.getQuantity() != null)
            .map(row -> row.getQuantity().setScale(4, RoundingMode.UNNECESSARY).toPlainString() + " "
                + (row.getUnit() == null ? "单位缺失" : row.getUnit()))
            .collect(Collectors.joining("、"));
        return grouped.isEmpty() ? null : grouped;
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
     * 当前库存账面价值。整个页面就是成本视图，因此由 Controller 用 {@code scm:report:cost:query} 直接拦住，这里不再做字段级抹除。
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

    /**
     * 负库存损耗：查询时点的余额快照。金额列跟随成本权限抹除，数量列照常显示。
     *
     * <p>
     * 只读余额与历史流水，不生成报损单、不改库存。负库存本身不是损耗事实，它的用途是月末让负责人 知道「卖出去但没入成本」的缺口有多大，真实损耗仍走报损 / 盘亏审批。
     */
    public PageResult<InventoryReportVO.NegativeStockRow> negativeStockList(ScmNegativeStockReportQueryForm form) {
        SalesReportService.rejectClientSort(form);
        ScmDataScopeContext context = dataScopeService.resolve();
        if (context.warehouseNowhere()) {
            return ScmDataScopeService.emptyPage(form);
        }
        var page = SmartPageUtil.convert2PageQuery(form);
        // LATERAL 取最近出库的语句交给分页插件自动优化 COUNT 容易把 join 裁掉，这里用完整包一层计数
        page.setOptimizeCountSql(false);
        PageResult<InventoryReportVO.NegativeStockRow> result = SmartPageUtil.convert2PageResult(page,
            reportDao.negativeStockList(page, form, context.getWarehouseScope()));
        if (!context.isCostVisible() && result != null && result.getList() != null) {
            result.getList().forEach(row -> {
                row.setAvgCost(null);
                row.setEstimatedAmount(null);
            });
        }
        return result;
    }

    /**
     * 汇总与列表共用同一份范围与筛选，指标卡不是拿一页明细自己加出来的。
     */
    public InventoryReportVO.NegativeStockSummary negativeStockSummary(ScmNegativeStockReportQueryForm form) {
        ScmDataScopeContext context = dataScopeService.resolve();
        if (context.warehouseNowhere()) {
            // 无授权仓库时一个负库存行都不可知，给空对象而不是「0 条」。
            return new InventoryReportVO.NegativeStockSummary();
        }
        InventoryReportVO.NegativeStockSummary summary = reportDao.negativeStockSummary(form,
            context.getWarehouseScope());
        if (summary == null) {
            summary = new InventoryReportVO.NegativeStockSummary();
        }
        summary.setSnapshotAt(OffsetDateTime.now());
        if (!context.isCostVisible()) {
            summary.setZeroCostRowCount(null);
            summary.setTotalEstimatedAmount(null);
        }
        return summary;
    }

    /**
     * 收发存是数量报表，不需要成本权限；但仍与其余库存查询共用同一份仓库范围。
     */
    public PageResult<InventoryReportVO.FlowSummaryRow> flowSummary(ScmInventoryReportQueryForm form) {
        SalesReportService.rejectClientSort(form);
        ScmReportTimeRange range = ScmReportTimeRangeResolver.resolve(form);
        ScmDataScopeContext context = dataScopeService.resolve();
        if (context.warehouseNowhere()) {
            return ScmDataScopeService.emptyPage(form);
        }
        var page = SmartPageUtil.convert2PageQuery(form);
        page.setOptimizeCountSql(false);
        return SmartPageUtil.convert2PageResult(page, reportDao.flowSummary(page, range.startAt(), range.endAt(),
            INBOUND_MOVEMENT_TYPES, form, context.getWarehouseScope()));
    }
}
