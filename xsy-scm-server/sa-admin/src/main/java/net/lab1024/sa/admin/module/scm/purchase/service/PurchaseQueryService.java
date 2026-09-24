package net.lab1024.sa.admin.module.scm.purchase.service;

import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeException;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.purchase.dao.PurchaseDemandAllocationDao;
import net.lab1024.sa.admin.module.scm.purchase.dao.PurchaseDemandDao;
import net.lab1024.sa.admin.module.scm.purchase.dao.PurchaseOperationLogDao;
import net.lab1024.sa.admin.module.scm.purchase.dao.PurchaseOrderDao;
import net.lab1024.sa.admin.module.scm.purchase.dao.PurchaseOrderItemDao;
import net.lab1024.sa.admin.module.scm.purchase.dao.PurchaseReceiptDao;
import net.lab1024.sa.admin.module.scm.purchase.dao.PurchaseReceiptItemDao;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseDemandAllocationEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseDemandEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseOrderEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseOrderItemEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseReceiptEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseReceiptItemEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseDemandQueryForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseDemandSummaryPreviewForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseOrderQueryForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseReceiptItemWorkbenchQueryForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseReceiptQueryForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseReceiptItemWorkbenchVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseDemandSummaryVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseDemandVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseOperationLogVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseOrderAllocationVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseOrderItemVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseOrderVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseReceiptItemVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseReceiptVO;
import net.lab1024.sa.admin.module.scm.purchase.manager.PurchaseReceiptQuantityCalculator;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeService;
import net.lab1024.sa.admin.module.scm.common.scope.ScmValueScope;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_DEMAND_NOT_FOUND;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_ORDER_NOT_FOUND;
import static net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode.PURCHASE_RECEIPT_NOT_FOUND;

/**
 * 采购域只读查询（W5 Target Design §7.1 的 7 个只读端点）。
 *
 * <p>**只读**：本类不写任何表、不开写事务，也不抛业务冲突码（只抛 4048x 的「不存在」）。
 * 三个命令服务都通过本类取详情 VO 作为返回值 —— 这样「列表、详情、命令返回值」三处
 * 永远是同一套投影，不会出现「命令返回的 VO 少一个字段」的漂移（W4 的
 * {@code SalesOrderQueryService} 就是这个角色）。
 *
 * <p>**日志只有采购单一个端点**（{@code GET /scm/purchase/log/{orderId}}）：
 * §7.1 的端点清单是 3（需求）+ 11（采购单）+ 8（收货）+ 5（仓库）= **27**，
 * 与 §11.3 / DoD 第 8 条一致 —— 因此本类**不提供** `receiptLogs`（没有端点消费它）。
 * 收货单的日志通过 `purchase_receipt_id` 直接查 `purchase_operation_log`
 * （见 {@code idx_purchase_operation_log_receipt_created}），不需要额外的 HTTP 入口。
 *
 * <p>**派生字段一律在这里算，不在 SQL 里算**：`remaining` / `overReceipt` 走
 * {@link PurchaseReceiptQuantityCalculator} 的同一个纯函数，与 DB 的
 * {@code ck_purchase_receipt_item_reconciliation} 用同一套公式；
 * 若 SQL 与 Java 各写一遍，两边会在边界上悄悄分叉。
 *
 * <p><b>数据范围（P0-F 裁决第 2、7、8 条）</b>：列表与详情按调用者的<b>采购员</b>维度收窄，
 * 由 {@link ScmDataScopeService#resolve()} 一次解析后显式下传 Mapper；越界的详情读抛 30005。
 * 三个 {@code *ForCommand} 方法刻意<b>不</b>收窄：命令服务用它们取返回值与审计前后快照，
 * 「能不能对这张单做这次操作」由写权限与状态机把关，读取范围一旦掺进来，
 * 主管替他人改一张单就会把自己合法的编辑整个回滚。
 * 两条聚合工作台按<b>仓库</b>维度收窄（裁决第 8 条）：{@link #summaryPreview} 的整页数字归属到
 * 入参那一个仓库，{@link #receiptItemWorkbench} 归属到采购单／收货单的仓库；
 * 授权仓库为空时短路成空分页，绝不返回一堆 0 让人误读成「这些仓没有数据」。
 */
@Service
@RequiredArgsConstructor
public class PurchaseQueryService {

    private final PurchaseDemandDao purchaseDemandDao;

    private final PurchaseDemandAllocationDao purchaseDemandAllocationDao;

    private final PurchaseOrderDao purchaseOrderDao;

    private final PurchaseOrderItemDao purchaseOrderItemDao;

    private final PurchaseReceiptDao purchaseReceiptDao;

    private final PurchaseReceiptItemDao purchaseReceiptItemDao;

    private final PurchaseOperationLogDao purchaseOperationLogDao;

    private final ScmDataScopeService scmDataScopeService;

    // ------------------------------------------------------------------
    // 采购需求
    // ------------------------------------------------------------------

    /**
     * 需求列表：按当前调用者的采购员范围收窄。表单里没有采购员筛选项，
     * 因此不存在「用户筛选放大范围」的入口。
     */
    @Transactional(readOnly = true)
    public PageResult<PurchaseDemandVO> demandQuery(PurchaseDemandQueryForm form) {
        ScmValueScope purchaserScope = scmDataScopeService.resolve().getPurchaserScope();
        if (purchaserScope.isEmpty()) {
            return ScmDataScopeService.emptyPage(form);
        }
        var page = SmartPageUtil.convert2PageQuery(form);
        return SmartPageUtil.convert2PageResult(page, purchaseDemandDao.query(page, form, purchaserScope));
    }

    /**
     * 订单汇总 / 库存缺口预览（Wave 2A §6A.4）。
     *
     * <p><b>只读</b>：不写任何表、不改 {@link PurchaseDemandService#generate} 的需求语义，
     * 只是把「已确认订单实发量」与「目标仓可用余额」并排算一个缺口供决策。
     *
     * <p>拒绝客户端排序的理由与库存余额页一致（join + 聚合，裸列名有歧义），排序固定为
     * 「缺口降序 → SKU」；{@code optimizeCountSql=false} 让分页 count 按聚合组数而非明细行数统计。
     *
     * <p><b>仓库范围</b>：{@code warehouseId} 必填且单选，整页数字（余额、预留、缺口）都归属到它，
     * 因此请求仓不在授权范围内时整页给空分页。只把余额左连收窄是错的：那会让调用者读到同一页
     * 订单需求量、只是状态变成 {@code NO_BALANCE}，等于把「无权看这个仓」谎报成「这个仓没货」。
     * 范围谓词同时下传 Mapper 兜底，见 {@code PurchaseDemandDao.xml}。
     */
    @Transactional(readOnly = true)
    public PageResult<PurchaseDemandSummaryVO> summaryPreview(PurchaseDemandSummaryPreviewForm form) {
        if (!form.getStartAt().isBefore(form.getEndAt())) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
        if (form.getSortItemList() != null && !form.getSortItemList().isEmpty()) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
        ScmValueScope warehouseScope = scmDataScopeService.resolve().getWarehouseScope();
        if (!warehouseScope.allows(form.getWarehouseId())) {
            return ScmDataScopeService.emptyPage(form);
        }
        var page = SmartPageUtil.convert2PageQuery(form);
        page.setOptimizeCountSql(false);
        return SmartPageUtil.convert2PageResult(page,
                purchaseDemandDao.summaryPreview(page, form, warehouseScope));
    }

    @Transactional(readOnly = true)
    public PurchaseDemandVO demandDetail(Long id) {
        return demandDetail(id, scmDataScopeService.resolve().getPurchaserScope());
    }

    /**
     * 需求详情，按给定采购员范围判定可见性；越界抛 30005。
     */
    @Transactional(readOnly = true)
    public PurchaseDemandVO demandDetail(Long id, ScmValueScope purchaserScope) {
        PurchaseDemandVO vo = purchaseDemandDao.detail(id);
        if (vo == null) {
            throw new ScmBusinessException(PURCHASE_DEMAND_NOT_FOUND);
        }
        if (!purchaserScope.isAll()) {
            // 详情投影与列表同源、刻意不带 purchaser_id（列表按范围整行过滤，不需要展示归属列），
            // 因此归属从实体行按主键读一次；selectById 不参与范围过滤，只是取归属值。
            PurchaseDemandEntity row = purchaseDemandDao.selectById(id);
            requirePurchaserVisible(purchaserScope, row == null ? null : row.getPurchaserId());
        }
        return vo;
    }

    /**
     * 命令侧投影：不做读取范围判定（见类说明）。
     */
    @Transactional(readOnly = true)
    public PurchaseDemandVO demandDetailForCommand(Long id) {
        return demandDetail(id, ScmValueScope.all());
    }

    /**
     * 采购员维度的可见性判定：范围外的单据按「没有权限」处理，而不是伪装成「不存在」。
     * 未分配（{@code purchaser_id IS NULL}）只有 {@link ScmValueScope#all()} 才可见。
     */
    private static void requirePurchaserVisible(ScmValueScope purchaserScope, Long ownerEmployeeId) {
        if (!purchaserScope.allows(ownerEmployeeId)) {
            throw new ScmDataScopeException();
        }
    }

    /**
     * 采购单的采购员归属；单据不存在或已软删时返回 {@code null}（按未分配处理，只有全量范围可见）。
     */
    private Long orderPurchaserId(Long orderId) {
        PurchaseOrderEntity order = orderId == null ? null : purchaseOrderDao.selectById(orderId);
        return order == null ? null : order.getPurchaserId();
    }

    // ------------------------------------------------------------------
    // 采购单
    // ------------------------------------------------------------------

    /**
     * 采购单列表：先按调用者范围，再与表单的 {@code purchaserId} 取交集
     * （用户筛选只能缩小范围，不能放大；裁决第 4 条）。
     */
    @Transactional(readOnly = true)
    public PageResult<PurchaseOrderVO> orderQuery(PurchaseOrderQueryForm form) {
        ScmValueScope purchaserScope =
                scmDataScopeService.resolve().getPurchaserScope().narrow(form.getPurchaserId());
        if (purchaserScope.isEmpty()) {
            return ScmDataScopeService.emptyPage(form);
        }
        var page = SmartPageUtil.convert2PageQuery(form);
        return SmartPageUtil.convert2PageResult(page, purchaseOrderDao.query(page, form, purchaserScope));
    }

    /**
     * 详情 = 单头 + 全部行（含每行分配）+ 单级分配平铺 + 全量日志。
     */
    @Transactional(readOnly = true)
    public PurchaseOrderVO orderDetail(Long id) {
        return orderDetail(id, scmDataScopeService.resolve().getPurchaserScope());
    }

    /**
     * 采购单详情，按给定采购员范围判定可见性；越界抛 30005。
     */
    @Transactional(readOnly = true)
    public PurchaseOrderVO orderDetail(Long id, ScmValueScope purchaserScope) {
        PurchaseOrderVO vo = purchaseOrderDao.detail(id);
        if (vo == null) {
            throw new ScmBusinessException(PURCHASE_ORDER_NOT_FOUND);
        }
        requirePurchaserVisible(purchaserScope, vo.getPurchaserId());
        // 归属已在本方法判定过，子读不再重复取父行
        List<PurchaseOrderItemVO> items = orderItems(id, ScmValueScope.all());
        vo.setItems(items);
        // 单级平铺：前端「分配明细」区直接消费，不需要自己扁平化 items[].allocations[]
        vo.setAllocations(items.stream()
                .flatMap(item -> item.getAllocations().stream())
                .toList());
        vo.setLogs(orderLogs(id, ScmValueScope.all()));
        return vo;
    }

    /**
     * 命令侧投影：不做读取范围判定（见类说明）。
     */
    @Transactional(readOnly = true)
    public PurchaseOrderVO orderDetailForCommand(Long id) {
        return orderDetail(id, ScmValueScope.all());
    }

    /**
     * 采购单行（含每行的分配集合，**Q13：N allocations**）。
     *
     * <p>它是独立端点 {@code GET /scm/purchase/item/{orderId}} 的实现，因此与详情同样受范围约束：
     * 只挡详情、放行行清单等于把别人单据的价格与数量照样端出去。
     */
    @Transactional(readOnly = true)
    public List<PurchaseOrderItemVO> orderItems(Long orderId) {
        return orderItems(orderId, scmDataScopeService.resolve().getPurchaserScope());
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrderItemVO> orderItems(Long orderId, ScmValueScope purchaserScope) {
        if (!purchaserScope.isAll()) {
            requirePurchaserVisible(purchaserScope, orderPurchaserId(orderId));
        }
        List<PurchaseOrderItemEntity> rows = purchaseOrderItemDao.listByOrderId(orderId);
        return itemVos(rows);
    }

    /**
     * 操作日志：审计数据不越过业务单据的归属范围（权限码另有 {@code scm:purchase:log:query}）。
     */
    @Transactional(readOnly = true)
    public List<PurchaseOperationLogVO> orderLogs(Long orderId) {
        return orderLogs(orderId, scmDataScopeService.resolve().getPurchaserScope());
    }

    @Transactional(readOnly = true)
    public List<PurchaseOperationLogVO> orderLogs(Long orderId, ScmValueScope purchaserScope) {
        if (!purchaserScope.isAll()) {
            requirePurchaserVisible(purchaserScope, orderPurchaserId(orderId));
        }
        return purchaseOperationLogDao.listByOrderId(orderId);
    }

    // ------------------------------------------------------------------
    // 收货单
    // ------------------------------------------------------------------

    /**
     * 收货单列表：采购员维度按父采购单继承（表上没有采购员列），谓词见
     * {@code PurchaseReceiptDao.xml} 的 EXISTS 半连。仓库维度由库存/仓库侧口径负责，这里不重复实现。
     */
    @Transactional(readOnly = true)
    public PageResult<PurchaseReceiptVO> receiptQuery(PurchaseReceiptQueryForm form) {
        ScmValueScope purchaserScope = scmDataScopeService.resolve().getPurchaserScope();
        if (purchaserScope.isEmpty()) {
            return ScmDataScopeService.emptyPage(form);
        }
        var page = SmartPageUtil.convert2PageQuery(form);
        return SmartPageUtil.convert2PageResult(page, purchaseReceiptDao.query(page, form, purchaserScope));
    }

    @Transactional(readOnly = true)
    public PurchaseReceiptVO receiptDetail(Long id) {
        return receiptDetail(id, scmDataScopeService.resolve().getPurchaserScope());
    }

    /**
     * 收货单详情，按父采购单的采购员范围判定可见性；越界抛 30005。
     */
    @Transactional(readOnly = true)
    public PurchaseReceiptVO receiptDetail(Long id, ScmValueScope purchaserScope) {
        PurchaseReceiptVO vo = purchaseReceiptDao.detail(id);
        if (vo == null) {
            throw new ScmBusinessException(PURCHASE_RECEIPT_NOT_FOUND);
        }
        if (!purchaserScope.isAll()) {
            requirePurchaserVisible(purchaserScope, orderPurchaserId(vo.getPurchaseOrderId()));
        }
        // 归属已按父单判定过，明细不再重复取父行
        vo.setItems(receiptItems(id, ScmValueScope.all()));
        return vo;
    }

    /**
     * 命令侧投影：收货的创建 / 确认 / 入库回的是自己刚写的单据，
     * 且父单归属不该让一次合法的收货确认回滚（见类说明）。
     */
    @Transactional(readOnly = true)
    public PurchaseReceiptVO receiptDetailForCommand(Long id) {
        return receiptDetail(id, ScmValueScope.all());
    }

    /**
     * 收货明细：独立端点 {@code GET /scm/purchase/receipt/item/{receiptId}}，与详情同一口径收范围。
     */
    @Transactional(readOnly = true)
    public List<PurchaseReceiptItemVO> receiptItems(Long receiptId) {
        return receiptItems(receiptId, scmDataScopeService.resolve().getPurchaserScope());
    }

    @Transactional(readOnly = true)
    public List<PurchaseReceiptItemVO> receiptItems(Long receiptId, ScmValueScope purchaserScope) {
        if (!purchaserScope.isAll()) {
            // 收货明细挂在收货单上，归属仍按父采购单判定（与列表的 EXISTS 同一口径）
            PurchaseReceiptEntity receipt = purchaseReceiptDao.selectById(receiptId);
            Long parentOrderId = receipt == null ? null : receipt.getPurchaseOrderId();
            requirePurchaserVisible(purchaserScope, orderPurchaserId(parentOrderId));
        }
        return purchaseReceiptItemDao.listByReceiptId(receiptId).stream()
                .map(PurchaseQueryService::receiptItemVo)
                .toList();
    }

    /**
     * 按商品收货工作台（Wave 2B §6.3，只读）。
     *
     * <p>与 {@link #summaryPreview} 同为聚合分页：拒绝客户端排序（join + 聚合下裸列名有歧义、
     * 排序口径已在 SQL 固定），置 {@code optimizeCountSql=false} 让分页 count 按 SKU×单位 组数统计。
     * 只做展示与汇总，绝不写任何表，也不改收货 / 库存事实。
     *
     * <p><b>仓库范围（裁决第 8 条）</b>：计划量与已收量归属的仓库就是采购单的 {@code warehouse_id}
     * （收货单建单时从采购单继承，因此不需要另判收货侧的仓），范围谓词落在采购单事实行上、
     * 分组之前，未授权仓的采购单整单不参与聚合。表单的 {@code warehouseId} 只能进一步缩小范围。
     * 一个授权仓都没有时给空分页：0 会被读成「这些仓没收过货」，而真实原因是「你没有可看的仓」。
     */
    @Transactional(readOnly = true)
    public PageResult<PurchaseReceiptItemWorkbenchVO> receiptItemWorkbench(PurchaseReceiptItemWorkbenchQueryForm form) {
        if (form.getSortItemList() != null && !form.getSortItemList().isEmpty()) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
        ScmValueScope warehouseScope = scmDataScopeService.resolve().getWarehouseScope();
        if (warehouseScope.isEmpty()) {
            return ScmDataScopeService.emptyPage(form);
        }
        var page = SmartPageUtil.convert2PageQuery(form);
        page.setOptimizeCountSql(false);
        return SmartPageUtil.convert2PageResult(page,
                purchaseOrderItemDao.workbench(page, form, warehouseScope));
    }

    // ------------------------------------------------------------------
    // 实体 → VO（显式映射：列名快照与 VO 字段名**故意不同**，不用 BeanUtils 猜）
    // ------------------------------------------------------------------

    /**
     * 批量装配采购单行 VO。
     *
     * <p>一次取全部行的 allocation，再按 `purchaseOrderItemId` 分组 —— 避免逐行查库（N+1）。
     */
    public List<PurchaseOrderItemVO> itemVos(List<PurchaseOrderItemEntity> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> itemIds = rows.stream().map(PurchaseOrderItemEntity::getId).toList();
        List<PurchaseDemandAllocationEntity> allocations =
                purchaseDemandAllocationDao.listActiveByOrderItemIds(itemIds);
        Map<Long, PurchaseDemandEntity> demands = demandMap(allocations);

        Map<Long, List<PurchaseOrderAllocationVO>> byItem = new LinkedHashMap<>();
        for (PurchaseDemandAllocationEntity allocation : allocations) {
            byItem.computeIfAbsent(allocation.getPurchaseOrderItemId(), key -> new ArrayList<>())
                    .add(allocationVo(allocation, demands.get(allocation.getPurchaseDemandId())));
        }

        return rows.stream().sorted(
                        Comparator.comparing(PurchaseOrderItemEntity::getSortOrder,
                                        Comparator.nullsLast(Comparator.naturalOrder()))
                                .thenComparing(PurchaseOrderItemEntity::getId))
                .map(row -> itemVo(row, byItem.getOrDefault(row.getId(), List.of())))
                .toList();
    }

    private static PurchaseOrderItemVO itemVo(PurchaseOrderItemEntity row,
                                              List<PurchaseOrderAllocationVO> allocations) {
        PurchaseOrderItemVO vo = new PurchaseOrderItemVO();
        vo.setId(row.getId());
        vo.setSkuId(row.getSkuId());
        vo.setSpuCode(row.getSpuCodeSnapshot());
        vo.setProductName(row.getProductNameSnapshot());
        vo.setSkuCode(row.getSkuCodeSnapshot());
        vo.setSkuName(row.getSkuNameSnapshot());
        vo.setSpecValues(row.getSpecValuesSnapshot());
        vo.setPurchaseUnit(row.getPurchaseUnitSnapshot());
        vo.setProductType(row.getProductTypeSnapshot());
        vo.setPlannedQuantity(row.getPlannedQuantity());
        vo.setReceivedQuantity(row.getReceivedQuantity());
        // P24：与 DB 的 ck_purchase_receipt_item_reconciliation 用同一个纯函数
        vo.setRemainingQuantity(PurchaseReceiptQuantityCalculator.remaining(
                row.getPlannedQuantity(), row.getReceivedQuantity()));
        vo.setOverReceiptQuantity(PurchaseReceiptQuantityCalculator.overReceipt(
                row.getPlannedQuantity(), row.getReceivedQuantity()));
        vo.setPurchasePrice(row.getPurchasePrice());
        vo.setLineAmount(row.getLineAmount());
        vo.setSortOrder(row.getSortOrder());
        vo.setVersion(row.getVersion());
        vo.setAllocations(allocations);
        return vo;
    }

    /**
     * 分配 VO。
     *
     * <p>`demandUnit` / `demandVersion` / `demandStatus` 取自**需求当前值**（不是分配行上的快照）：
     * 它们表达的是「这条分配现在挂在一个什么状态的需求上」，快照表达不了「需求已补齐」。
     * `quantity` 取分配行自己的数量（Q13：一条分配一个数量）。
     */
    private static PurchaseOrderAllocationVO allocationVo(PurchaseDemandAllocationEntity row,
                                                          PurchaseDemandEntity demand) {
        PurchaseOrderAllocationVO vo = new PurchaseOrderAllocationVO();
        vo.setAllocationId(row.getId());
        vo.setDemandId(row.getPurchaseDemandId());
        vo.setSalesOrderId(row.getSalesOrderId());
        vo.setSalesOrderItemId(row.getSalesOrderItemId());
        vo.setSkuId(row.getSkuId());
        vo.setQuantity(row.getAllocatedQuantity());
        if (demand != null) {
            vo.setSalesOrderNo(demand.getSalesOrderNoSnapshot());
            vo.setDemandUnit(demand.getDemandUnitSnapshot());
            vo.setDemandVersion(demand.getVersion());
            vo.setDemandStatus(demand.getStatus());
        }
        return vo;
    }

    private Map<Long, PurchaseDemandEntity> demandMap(List<PurchaseDemandAllocationEntity> allocations) {
        List<Long> demandIds = allocations.stream()
                .map(PurchaseDemandAllocationEntity::getPurchaseDemandId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (demandIds.isEmpty()) {
            return Map.of();
        }
        return purchaseDemandDao.selectBatchIds(demandIds).stream()
                .collect(Collectors.toMap(PurchaseDemandEntity::getId, demand -> demand, (a, b) -> a));
    }

    private static PurchaseReceiptItemVO receiptItemVo(PurchaseReceiptItemEntity row) {
        PurchaseReceiptItemVO vo = new PurchaseReceiptItemVO();
        vo.setId(row.getId());
        vo.setPurchaseOrderItemId(row.getPurchaseOrderItemId());
        vo.setSkuId(row.getSkuId());
        vo.setSkuCode(row.getSkuCodeSnapshot());
        vo.setSkuName(row.getSkuNameSnapshot());
        vo.setSpecValues(row.getSpecValuesSnapshot());
        vo.setPurchaseUnit(row.getPurchaseUnitSnapshot());
        vo.setProductType(row.getProductTypeSnapshot());
        vo.setPlannedQuantity(row.getPlannedQuantity());
        vo.setReceivedQuantity(row.getReceivedQuantity());
        vo.setCumulativeReceivedQuantity(row.getCumulativeReceivedQuantity());
        vo.setRemainingQuantity(row.getRemainingQuantity());
        vo.setOverReceiptQuantity(row.getOverReceiptQuantity());
        vo.setReceiptDifference(row.getReceiptDifference());
        // 标品为 null（不是 0.0000）：三字段同生同灭
        vo.setActualWeight(row.getActualWeight());
        vo.setWeightUnit(row.getWeightUnit());
        vo.setWeighingSource(row.getWeighingSource());
        vo.setCorrectionReason(row.getCorrectionReason());
        vo.setVersion(row.getVersion());
        return vo;
    }
}
