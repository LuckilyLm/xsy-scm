package net.lab1024.sa.admin.module.scm.report.controller;

import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.report.constant.ReportErrorCode;
import net.lab1024.sa.admin.module.scm.report.domain.form.ScmInventoryReportQueryForm;
import net.lab1024.sa.admin.module.scm.report.domain.form.ScmOverviewReportQueryForm;
import net.lab1024.sa.admin.module.scm.report.domain.form.ScmPurchaseReportQueryForm;
import net.lab1024.sa.admin.module.scm.report.domain.form.ScmReceiptReportQueryForm;
import net.lab1024.sa.admin.module.scm.report.domain.form.ScmSalesReportQueryForm;
import net.lab1024.sa.admin.module.scm.report.domain.vo.InventoryReportVO;
import net.lab1024.sa.admin.module.scm.report.domain.vo.PurchaseReportVO;
import net.lab1024.sa.admin.module.scm.report.domain.vo.ReceiptReportVO;
import net.lab1024.sa.admin.module.scm.report.domain.vo.ReportDailyStatVO;
import net.lab1024.sa.admin.module.scm.report.domain.vo.ReportOverviewVO;
import net.lab1024.sa.admin.module.scm.report.domain.vo.SalesReportVO;
import net.lab1024.sa.admin.module.scm.report.service.InventoryReportService;
import net.lab1024.sa.admin.module.scm.report.service.OverviewReportService;
import net.lab1024.sa.admin.module.scm.report.service.PurchaseReportService;
import net.lab1024.sa.admin.module.scm.report.service.ReceiptReportService;
import net.lab1024.sa.admin.module.scm.report.service.SalesReportService;
import net.lab1024.sa.admin.module.scm.report.support.ScmReportExcel;
import net.lab1024.sa.admin.module.scm.report.support.ScmReportExportGuard;
import net.lab1024.sa.base.common.domain.PageParam;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;

/**
 * Finance R0 报表中心（只读）。
 *
 * <p><b>本类没有任何写端点</b>，也不出现「营业收入 / 已收款 / 应收 / 应付 / 毛利」：
 * 当前系统还没有签收、应收、应付与核销事实，报表只能展示已经成立的事实。
 *
 * <p>权限分三层，彼此不隐含：
 * <ul>
 *   <li>{@code scm:report:<page>:query} —— 页面与查询；</li>
 *   <li>{@code scm:report:cost:query} —— 成本字段。数量流水与成本分属两类岗位，
 *       因此成本是<b>字段级</b>抹除（返回 null → 页面显示 {@code —}），
 *       而「当前库存价值」整页都是成本，直接由接口拦住；</li>
 *   <li>{@code scm:report:export} —— 导出，且必须 AND 上对应查询权限。</li>
 * </ul>
 *
 * <p>导出与列表调用<b>同一个</b>查询方法，口径不可能分叉；超过行数上限时明确拒绝而不是静默截断。
 */
@RestController
@RequestMapping("/scm/report")
@Tag(name = "SCM 报表中心")
@RequiredArgsConstructor
public class ScmReportController {

    private static final int TOP_LIMIT = 5;

    private final OverviewReportService overviewReportService;
    private final SalesReportService salesReportService;
    private final PurchaseReportService purchaseReportService;
    private final ReceiptReportService receiptReportService;
    private final InventoryReportService inventoryReportService;

    // ==================== 经营概览 ====================

    @PostMapping("/overview")
    @SaCheckPermission("scm:report:overview:query")
    public ResponseDTO<ReportOverviewVO> overview(@Valid @RequestBody ScmOverviewReportQueryForm form) {
        return ResponseDTO.ok(overviewReportService.overview(form));
    }

    @PostMapping("/overview/trend")
    @SaCheckPermission("scm:report:overview:query")
    public ResponseDTO<List<ReportDailyStatVO>> overviewTrend(@Valid @RequestBody ScmOverviewReportQueryForm form) {
        return ResponseDTO.ok(overviewReportService.dailyStat(form));
    }

    @PostMapping("/overview/daily")
    @SaCheckPermission("scm:report:overview:query")
    public ResponseDTO<List<ReportDailyStatVO>> overviewDaily(@Valid @RequestBody ScmOverviewReportQueryForm form) {
        return ResponseDTO.ok(overviewReportService.dailyStat(form));
    }

    // ==================== 销售分析 ====================

    @PostMapping("/sales/product")
    @SaCheckPermission("scm:report:sales:query")
    public ResponseDTO<PageResult<SalesReportVO.ProductRow>> salesProduct(
            @Valid @RequestBody ScmSalesReportQueryForm form) {
        return ResponseDTO.ok(salesReportService.byProduct(form));
    }

    @PostMapping("/sales/product/top")
    @SaCheckPermission("scm:report:sales:query")
    public ResponseDTO<List<SalesReportVO.TopItem>> salesProductTop(@RequestBody ScmSalesReportQueryForm form) {
        return ResponseDTO.ok(salesReportService.topProduct(form));
    }

    @PostMapping("/sales/category")
    @SaCheckPermission("scm:report:sales:query")
    public ResponseDTO<PageResult<SalesReportVO.CategoryRow>> salesCategory(
            @Valid @RequestBody ScmSalesReportQueryForm form) {
        return ResponseDTO.ok(salesReportService.byCategory(form));
    }

    @PostMapping("/sales/category/top")
    @SaCheckPermission("scm:report:sales:query")
    public ResponseDTO<List<SalesReportVO.TopItem>> salesCategoryTop(@RequestBody ScmSalesReportQueryForm form) {
        return ResponseDTO.ok(salesReportService.topCategory(form));
    }

    @PostMapping("/sales/customer")
    @SaCheckPermission("scm:report:sales:query")
    public ResponseDTO<PageResult<SalesReportVO.CustomerRow>> salesCustomer(
            @Valid @RequestBody ScmSalesReportQueryForm form) {
        return ResponseDTO.ok(salesReportService.byCustomer(form));
    }

    @PostMapping("/sales/customer/top")
    @SaCheckPermission("scm:report:sales:query")
    public ResponseDTO<List<SalesReportVO.TopItem>> salesCustomerTop(@RequestBody ScmSalesReportQueryForm form) {
        return ResponseDTO.ok(salesReportService.topCustomer(form));
    }

    @PostMapping("/sales/seller")
    @SaCheckPermission("scm:report:sales:query")
    public ResponseDTO<PageResult<SalesReportVO.SellerRow>> salesSeller(@Valid @RequestBody ScmSalesReportQueryForm form) {
        return ResponseDTO.ok(salesReportService.bySeller(form));
    }

    @PostMapping("/sales/item/query")
    @SaCheckPermission("scm:report:sales:query")
    public ResponseDTO<PageResult<SalesReportVO.ItemRow>> salesItem(
            @Valid @RequestBody ScmSalesReportQueryForm form) {
        return ResponseDTO.ok(salesReportService.itemList(form));
    }

    @PostMapping("/sales/product/export")
    @SaCheckPermission(value = {"scm:report:sales:query", "scm:report:export"}, mode = SaMode.AND)
    @OperateLog
    public void exportSalesProduct(@RequestBody ScmSalesReportQueryForm form,
                                   HttpServletResponse response) throws IOException {
        List<String> titles = List.of("商品名称", "SKU 编码", "规格", "销售单位", "一级分类", "末级分类",
                "订单笔数", "客户数", "确认数量", "成交均价", "确认订单金额", "金额排名");
        List<List<Object>> rows = exportRows(form, () -> salesReportService.byProduct(form)).stream()
                .map(r -> ScmReportExcel.row(titles, r.getProductName(), r.getSkuCode(), r.getSpecName(),
                        r.getSaleUnit(), r.getRootCategoryName(), r.getLeafCategoryName(), r.getOrderCount(),
                        r.getCustomerCount(), r.getConfirmedQuantity(), r.getAvgTransactionPrice(),
                        r.getSettlementAmount(), r.getAmountRank()))
                .toList();
        ScmReportExcel.write(response, "销售分析-按商品.xlsx", "按商品", titles, rows);
    }

    @PostMapping("/sales/customer/export")
    @SaCheckPermission(value = {"scm:report:sales:query", "scm:report:export"}, mode = SaMode.AND)
    @OperateLog
    public void exportSalesCustomer(@RequestBody ScmSalesReportQueryForm form,
                                    HttpServletResponse response) throws IOException {
        List<String> titles = List.of("客户编码", "客户名称", "销售员", "订单笔数", "SKU 种类数",
                "确认订单金额", "已完成退款金额", "最近确认时间", "金额排名");
        List<List<Object>> rows = exportRows(form, () -> salesReportService.byCustomer(form)).stream()
                .map(r -> ScmReportExcel.row(titles, r.getCustomerCode(), r.getCustomerName(), r.getSellerName(),
                        r.getOrderCount(), r.getSkuKindCount(), r.getSettlementAmount(), r.getCompletedRefundAmount(),
                        r.getLastConfirmedAt(), r.getAmountRank()))
                .toList();
        ScmReportExcel.write(response, "销售分析-按客户.xlsx", "按客户", titles, rows);
    }

    @PostMapping("/sales/item/export")
    @SaCheckPermission(value = {"scm:report:sales:query", "scm:report:export"}, mode = SaMode.AND)
    @OperateLog
    public void exportSalesItem(@RequestBody ScmSalesReportQueryForm form,
                                HttpServletResponse response) throws IOException {
        List<String> titles = List.of("确认时间", "订单号", "客户编码", "客户名称", "销售员", "订单来源", "结算方式",
                "SPU 编码", "商品名称", "SKU 编码", "规格", "商品类型", "销售单位",
                "订购数量", "实际数量", "锁定成交单价", "价格来源", "结算金额", "是否手工改价", "手工改价原因");
        List<List<Object>> rows = exportRows(form, () -> salesReportService.itemList(form)).stream()
                .map(r -> ScmReportExcel.row(titles, r.getConfirmedAt(), r.getOrderNo(), r.getCustomerCode(),
                        r.getCustomerName(), r.getSellerName(), r.getOrderSource(), r.getSettleMode(),
                        r.getSpuCode(), r.getProductName(), r.getSkuCode(), r.getSpecName(), r.getProductType(),
                        r.getSaleUnit(), r.getOrderedQuantity(), r.getActualQuantity(), r.getLockedUnitPrice(),
                        r.getLockedPriceSource(), r.getSettlementLineAmount(), yesNo(r.getManualPriceOverride()),
                        r.getManualPriceReason()))
                .toList();
        ScmReportExcel.write(response, "销售分析-订单明细.xlsx", "订单明细", titles, rows);
    }

    // ==================== 采购分析 ====================

    @PostMapping("/purchase/overview")
    @SaCheckPermission("scm:report:purchase:query")
    public ResponseDTO<PurchaseReportVO.Overview> purchaseOverview(
            @Valid @RequestBody ScmPurchaseReportQueryForm form) {
        return ResponseDTO.ok(purchaseReportService.overview(form));
    }

    @PostMapping("/purchase/product")
    @SaCheckPermission("scm:report:purchase:query")
    public ResponseDTO<PageResult<PurchaseReportVO.ProductRow>> purchaseProduct(
            @Valid @RequestBody ScmPurchaseReportQueryForm form) {
        return ResponseDTO.ok(purchaseReportService.byProduct(form));
    }

    @PostMapping("/purchase/supplier")
    @SaCheckPermission("scm:report:purchase:query")
    public ResponseDTO<PageResult<PurchaseReportVO.SupplierRow>> purchaseSupplier(
            @Valid @RequestBody ScmPurchaseReportQueryForm form) {
        return ResponseDTO.ok(purchaseReportService.bySupplier(form));
    }

    @PostMapping("/purchase/supplier/top")
    @SaCheckPermission(value = {"scm:report:purchase:query", "scm:report:cost:query"}, mode = SaMode.AND)
    public ResponseDTO<List<SalesReportVO.TopItem>> purchaseSupplierTop(
            @RequestBody ScmPurchaseReportQueryForm form) {
        return ResponseDTO.ok(purchaseReportService.topSupplierInbound(form));
    }

    @PostMapping("/purchase/purchaser")
    @SaCheckPermission("scm:report:purchase:query")
    public ResponseDTO<PageResult<PurchaseReportVO.PurchaserRow>> purchasePurchaser(
            @Valid @RequestBody ScmPurchaseReportQueryForm form) {
        return ResponseDTO.ok(purchaseReportService.byPurchaser(form));
    }

    @PostMapping("/purchase/item/query")
    @SaCheckPermission("scm:report:purchase:query")
    public ResponseDTO<PageResult<PurchaseReportVO.ItemRow>> purchaseItem(
            @Valid @RequestBody ScmPurchaseReportQueryForm form) {
        return ResponseDTO.ok(purchaseReportService.itemList(form));
    }

    @PostMapping("/purchase/price-trend")
    @SaCheckPermission("scm:report:purchase:query")
    public ResponseDTO<List<PurchaseReportVO.PriceTrendPoint>> purchasePriceTrend(
            @Valid @RequestBody ScmPurchaseReportQueryForm form) {
        return ResponseDTO.ok(purchaseReportService.priceTrend(form));
    }

    @PostMapping("/purchase/product/export")
    @SaCheckPermission(value = {"scm:report:purchase:query", "scm:report:export"}, mode = SaMode.AND)
    @OperateLog
    public void exportPurchaseProduct(@RequestBody ScmPurchaseReportQueryForm form,
                                      HttpServletResponse response) throws IOException {
        List<String> titles = List.of("商品名称", "SKU 编码", "规格", "采购单位", "采购单数",
                "计划采购数量", "已收数量", "采购订单金额", "采购成交均价", "采购入库数量", "采购入库成本金额");
        List<List<Object>> rows = exportRows(form, () -> purchaseReportService.byProduct(form)).stream()
                .map(r -> ScmReportExcel.row(titles, r.getProductName(), r.getSkuCode(), r.getSkuName(),
                        r.getPurchaseUnit(), r.getOrderCount(), r.getPlannedQuantity(), r.getReceivedQuantity(),
                        r.getOrderAmount(), r.getAvgPurchasePrice(), r.getInboundQuantityText(),
                        r.getInboundCostAmount()))
                .toList();
        ScmReportExcel.write(response, "采购分析-按商品.xlsx", "按商品", titles, rows);
    }

    @PostMapping("/purchase/supplier/export")
    @SaCheckPermission(value = {"scm:report:purchase:query", "scm:report:export"}, mode = SaMode.AND)
    @OperateLog
    public void exportPurchaseSupplier(@RequestBody ScmPurchaseReportQueryForm form,
                                       HttpServletResponse response) throws IOException {
        List<String> titles = List.of("供应商编码", "供应商名称", "采购单数", "SKU 种类数", "采购订单金额",
                "已收参考金额", "采购入库成本金额", "最近采购时间", "金额排名");
        List<List<Object>> rows = exportRows(form, () -> purchaseReportService.bySupplier(form)).stream()
                .map(r -> ScmReportExcel.row(titles, r.getSupplierCode(), r.getSupplierName(), r.getOrderCount(),
                        r.getSkuKindCount(), r.getOrderAmount(), r.getReceiptReferenceAmount(),
                        r.getInboundCostAmount(), r.getLastSubmittedAt(), r.getAmountRank()))
                .toList();
        ScmReportExcel.write(response, "采购分析-按供应商.xlsx", "按供应商", titles, rows);
    }

    @PostMapping("/purchase/item/export")
    @SaCheckPermission(value = {"scm:report:purchase:query", "scm:report:export"}, mode = SaMode.AND)
    @OperateLog
    public void exportPurchaseItem(@RequestBody ScmPurchaseReportQueryForm form,
                                   HttpServletResponse response) throws IOException {
        List<String> titles = List.of("提交时间", "采购单号", "状态", "供应商", "采购员", "仓库", "计划到货日期",
                "SPU 编码", "商品名称", "SKU 编码", "规格", "采购单位", "计划数量", "累计收货数量",
                "采购单价", "采购行金额");
        List<List<Object>> rows = exportRows(form, () -> purchaseReportService.itemList(form)).stream()
                .map(r -> ScmReportExcel.row(titles, r.getSubmittedAt(), r.getOrderNo(), r.getStatus(),
                        r.getSupplierName(), r.getPurchaserName(), r.getWarehouseName(), r.getPlannedArrivalDate(),
                        r.getSpuCode(), r.getProductName(), r.getSkuCode(), r.getSkuName(), r.getPurchaseUnit(),
                        r.getPlannedQuantity(), r.getReceivedQuantity(), r.getPurchasePrice(), r.getLineAmount()))
                .toList();
        ScmReportExcel.write(response, "采购分析-采购明细.xlsx", "采购明细", titles, rows);
    }

    // ==================== 收货与入库 ====================

    @PostMapping("/receipt/query")
    @SaCheckPermission("scm:report:purchase:query")
    public ResponseDTO<PageResult<ReceiptReportVO.ReceiptRow>> receiptList(
            @Valid @RequestBody ScmReceiptReportQueryForm form) {
        return ResponseDTO.ok(receiptReportService.receiptList(form));
    }

    @PostMapping("/inbound/query")
    @SaCheckPermission("scm:report:inventory:query")
    public ResponseDTO<PageResult<ReceiptReportVO.InboundRow>> inboundList(
            @Valid @RequestBody ScmReceiptReportQueryForm form) {
        return ResponseDTO.ok(receiptReportService.inboundList(form));
    }

    @PostMapping("/pending-putaway/query")
    @SaCheckPermission("scm:report:inventory:query")
    public ResponseDTO<PageResult<ReceiptReportVO.PendingPutawayRow>> pendingPutaway(
            @RequestBody ScmReceiptReportQueryForm form) {
        return ResponseDTO.ok(receiptReportService.pendingPutawayList(form));
    }

    @PostMapping("/receipt/export")
    @SaCheckPermission(value = {"scm:report:purchase:query", "scm:report:export"}, mode = SaMode.AND)
    @OperateLog
    public void exportReceipt(@RequestBody ScmReceiptReportQueryForm form,
                              HttpServletResponse response) throws IOException {
        List<String> titles = List.of("收货确认时间", "收货单号", "采购单号", "供应商", "仓库", "收货模式", "入库状态",
                "商品名称", "SKU 编码", "规格", "采购单位", "本次收货数量", "累计收货数量", "剩余数量",
                "超收数量", "收货差异", "采购单价", "收货参考金额");
        List<List<Object>> rows = exportRows(form, () -> receiptReportService.receiptList(form)).stream()
                .map(r -> ScmReportExcel.row(titles, r.getConfirmedAt(), r.getReceiptNo(), r.getPurchaseOrderNo(),
                        r.getSupplierName(), r.getWarehouseName(), r.getReceiptMode(), r.getPutawayStatus(),
                        r.getProductName(), r.getSkuCode(), r.getSkuName(), r.getPurchaseUnit(),
                        r.getReceivedQuantity(), r.getCumulativeReceivedQuantity(), r.getRemainingQuantity(),
                        r.getOverReceiptQuantity(), r.getReceiptDifference(), r.getPurchasePrice(),
                        r.getReceiptReferenceAmount()))
                .toList();
        ScmReportExcel.write(response, "收货明细.xlsx", "收货明细", titles, rows);
    }

    @PostMapping("/inbound/export")
    @SaCheckPermission(value = {"scm:report:inventory:query", "scm:report:export"}, mode = SaMode.AND)
    @OperateLog
    public void exportInbound(@RequestBody ScmReceiptReportQueryForm form,
                              HttpServletResponse response) throws IOException {
        List<String> titles = List.of("入库时间", "仓库", "收货单号", "采购单号", "供应商", "商品名称",
                "SKU 编码", "单位", "入库数量", "入库单位成本", "入库成本金额", "操作人");
        List<List<Object>> rows = exportRows(form, () -> receiptReportService.inboundList(form)).stream()
                .map(r -> ScmReportExcel.row(titles, r.getOccurredAt(), r.getWarehouseName(), r.getReceiptNo(),
                        r.getPurchaseOrderNo(), r.getSupplierName(), r.getProductName(), r.getSkuCode(), r.getUnit(),
                        r.getQuantity(), r.getUnitCost(), r.getCostAmount(), r.getOperator()))
                .toList();
        ScmReportExcel.write(response, "入库明细.xlsx", "入库明细", titles, rows);
    }

    // ==================== 库存分析 ====================

    @PostMapping("/inventory/movement/query")
    @SaCheckPermission("scm:report:inventory:query")
    public ResponseDTO<PageResult<InventoryReportVO.MovementRow>> movementList(
            @Valid @RequestBody ScmInventoryReportQueryForm form) {
        return ResponseDTO.ok(inventoryReportService.movementList(form));
    }

    @PostMapping("/inventory/loss/summary")
    @SaCheckPermission("scm:report:inventory:query")
    public ResponseDTO<InventoryReportVO.LossSummary> lossSummary(
            @Valid @RequestBody ScmInventoryReportQueryForm form) {
        return ResponseDTO.ok(inventoryReportService.lossSummary(form));
    }

    @PostMapping("/inventory/loss/query")
    @SaCheckPermission("scm:report:inventory:query")
    public ResponseDTO<PageResult<InventoryReportVO.LossRow>> lossList(
            @Valid @RequestBody ScmInventoryReportQueryForm form) {
        return ResponseDTO.ok(inventoryReportService.lossList(form));
    }

    /** 整页都是成本视图，因此在接口层就要成本权限，而不是返回一堆 null 让人猜为什么是空的。 */
    @PostMapping("/inventory/value/query")
    @SaCheckPermission(value = {"scm:report:inventory:query", "scm:report:cost:query"}, mode = SaMode.AND)
    public ResponseDTO<PageResult<InventoryReportVO.ValueRow>> inventoryValue(
            @Valid @RequestBody ScmInventoryReportQueryForm form) {
        return ResponseDTO.ok(inventoryReportService.valueList(form));
    }

    @PostMapping("/inventory/flow-summary/query")
    @SaCheckPermission("scm:report:inventory:query")
    public ResponseDTO<PageResult<InventoryReportVO.FlowSummaryRow>> flowSummary(
            @Valid @RequestBody ScmInventoryReportQueryForm form) {
        return ResponseDTO.ok(inventoryReportService.flowSummary(form));
    }

    @PostMapping("/inventory/movement/export")
    @SaCheckPermission(value = {"scm:report:inventory:query", "scm:report:export"}, mode = SaMode.AND)
    @OperateLog
    public void exportMovement(@RequestBody ScmInventoryReportQueryForm form,
                               HttpServletResponse response) throws IOException {
        List<String> titles = List.of("发生时间", "仓库", "商品名称", "SKU 编码", "流水类型", "方向",
                "来源单据类型", "来源单号", "来源单据 ID", "来源单据行 ID", "数量", "单位",
                "单位成本", "成本金额", "变动前数量", "变动后数量", "操作人");
        List<List<Object>> rows = exportRows(form, () -> inventoryReportService.movementList(form)).stream()
                .map(r -> ScmReportExcel.row(titles, r.getOccurredAt(), r.getWarehouseName(), r.getProductName(),
                        r.getSkuCode(), r.getMovementType(), r.getDirection(), r.getSourceDocumentType(),
                        r.getSourceDocumentNo(), r.getSourceDocumentId(), r.getSourceDocumentItemId(), r.getQuantity(),
                        r.getUnit(), r.getUnitCost(), r.getCostAmount(), r.getBeforeQuantity(), r.getAfterQuantity(),
                        r.getOperator()))
                .toList();
        ScmReportExcel.write(response, "库存流水.xlsx", "库存流水", titles, rows);
    }

    @PostMapping("/inventory/loss/export")
    @SaCheckPermission(value = {"scm:report:inventory:query", "scm:report:export"}, mode = SaMode.AND)
    @OperateLog
    public void exportLoss(@RequestBody ScmInventoryReportQueryForm form,
                           HttpServletResponse response) throws IOException {
        List<String> titles = List.of("商品名称", "SKU 编码", "仓库", "损耗类型", "数量", "单位",
                "单位成本", "损耗成本金额", "来源单号", "发生时间", "操作人");
        List<List<Object>> rows = exportRows(form, () -> inventoryReportService.lossList(form)).stream()
                .map(r -> ScmReportExcel.row(titles, r.getProductName(), r.getSkuCode(), r.getWarehouseName(),
                        r.getLossType(), r.getQuantity(), r.getUnit(), r.getUnitCost(), r.getCostAmount(),
                        r.getSourceDocumentNo(), r.getOccurredAt(), r.getOperator()))
                .toList();
        ScmReportExcel.write(response, "损耗分析.xlsx", "损耗分析", titles, rows);
    }

    @PostMapping("/inventory/value/export")
    @SaCheckPermission(value = {"scm:report:inventory:query", "scm:report:cost:query", "scm:report:export"},
            mode = SaMode.AND)
    @OperateLog
    public void exportInventoryValue(@RequestBody ScmInventoryReportQueryForm form,
                                     HttpServletResponse response) throws IOException {
        List<String> titles = List.of("仓库", "商品名称", "SKU 编码", "当前数量", "预留数量", "可用数量",
                "单位", "当前移动平均成本", "当前账面金额");
        List<List<Object>> rows = exportRows(form, () -> inventoryReportService.valueList(form)).stream()
                .map(r -> ScmReportExcel.row(titles, r.getWarehouseName(), r.getProductName(), r.getSkuCode(),
                        r.getQuantity(), r.getReservedQuantity(), r.getAvailableQuantity(), r.getUnit(),
                        r.getAvgCost(), r.getBookAmount()))
                .toList();
        ScmReportExcel.write(response, "当前库存价值.xlsx", "当前库存价值", titles, rows);
    }

    // ==================== 内部 ====================

    /**
     * 导出与列表走<b>同一个</b>查询方法，只是把分页换成「第 1 页 + 上限多一行」；
     * 多出一行即判定超限并抛 41112，避免静默只导前 N 行。
     */
    private <T> List<T> exportRows(PageParam form, Supplier<PageResult<T>> query) {
        form.setPageNum(1L);
        form.setPageSize(ScmReportExportGuard.PROBE_PAGE_SIZE);
        List<T> rows = query.get().getList();
        if (rows != null && rows.size() > ScmReportExportGuard.EXPORT_MAX_ROWS) {
            throw new ScmBusinessException(ReportErrorCode.REPORT_EXPORT_ROW_LIMIT_EXCEEDED);
        }
        return rows == null ? List.of() : rows;
    }

    private String yesNo(Boolean value) {
        return value == null ? "" : (value ? "是" : "否");
    }
}
