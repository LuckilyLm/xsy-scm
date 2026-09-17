package net.lab1024.sa.admin.module.scm.purchase;

import net.lab1024.sa.admin.module.scm.common.ScmW5PgITBase;
import net.lab1024.sa.admin.module.scm.purchase.dao.PurchaseDemandAllocationDao;
import net.lab1024.sa.admin.module.scm.purchase.dao.PurchaseDemandDao;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseDemandAllocationEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseDemandEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseDemandGenerateForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseOrderAddForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseOrderUpdateForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseOrderAllocationVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseOrderItemVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseOrderVO;
import net.lab1024.sa.admin.module.scm.purchase.service.PurchaseDemandService;
import net.lab1024.sa.admin.module.scm.purchase.service.PurchaseOrderService;
import net.lab1024.sa.admin.module.scm.purchase.service.PurchaseQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 采购单**分配集合**（Q13 / Q17）端到端验收（W5 Target Design §11.2，9 例）。
 *
 * <p>这是 **Q13 修订的主防线**：allocation 身份 = {@code (purchase_order_item_id, purchase_demand_id)}，
 * 因此「一行承接两个需求」「只改其中一条」「删其中一条」必须是**互不干扰的行级操作**。
 * A 源用 {@code Map<itemId, allocation>} 覆盖写（A-D23），一行两需求在编辑时只剩最后一条 ——
 * 本类的每个用例都在防这件事回归。
 *
 * <p>同时覆盖 **Q17**：需求单位（销售单位）与采购单位（{@code supplier_sku.purchase_unit}）
 * 不一致时必须拒绝自动分配（40971），**不允许**猜换算系数、也不允许只换单位字符串。
 *
 * <p><b>「一行两 demand」怎么造</b>：需求来自 {@code sales_order_item}，一行一个需求
 * （{@code uk_purchase_demand_source_active}）。因此同一 SKU 要有两个需求，
 * 需要**两张已确认订单**各含该 SKU，然后一次汇总生成两条需求，
 * 再把这两条需求分配到**同一个采购行**上。
 */
@DisplayName("采购单分配集合：Q13 一行多需求 + Q17 单位一致（PG IT）")
class PurchaseDemandAllocationIT extends ScmW5PgITBase {

    @Autowired
    private PurchaseDemandService demandService;

    @Autowired
    private PurchaseOrderService orderService;

    @Autowired
    private PurchaseQueryService queryService;

    @Autowired
    private PurchaseDemandDao purchaseDemandDao;

    @Autowired
    private PurchaseDemandAllocationDao purchaseDemandAllocationDao;

    /** 一个 SKU + 供应商 + 两条需求（两张已确认订单）+ 一张草稿采购单。 */
    private record TwoDemandFixture(Long skuId, Long supplierId,
                                    Long demandA, Long demandB,
                                    Long salesOrderItemA, Long salesOrderItemB,
                                    Long purchaseOrderId, Long purchaseOrderItemId) {
    }

    private PurchaseDemandEntity demandOf(Long salesOrderItemId) {
        List<PurchaseDemandEntity> rows =
                purchaseDemandDao.listActiveBySourceItemIds(List.of(salesOrderItemId));
        assertThat(rows).hasSize(1);
        return rows.getFirst();
    }

    /**
     * 造出「一个 SKU 行挂两条需求」的完整前置数据，并按给定数量建单。
     *
     * @param purchaseUnit       供应商侧的采购单位（传 {@code 箱} 即可构造 Q17 的单位不一致）
     * @param quantityA          分配到需求 A 的数量（4 位定点字符串）
     * @param quantityB          分配到需求 B 的数量
     */
    private TwoDemandFixture twoDemands(String suffix, String purchaseUnit,
                                        String quantityA, String quantityB) {
        Long skuId = newOnShelfSku(suffix);
        Long supplierId = newSupplier(suffix);
        linkSupplierSku(supplierId, skuId, purchaseUnit);
        Long customerId = newCustomer();

        // 需求 A：订购 5、实收 3；需求 B：订购 8、实收 4 —— 需求量取实数量
        Long salesOrderA = confirmedSalesOrder(customerId, skuId, "5.0000", "3.0000");
        Long salesOrderB = confirmedSalesOrder(customerId, skuId, "8.0000", "4.0000");
        Long itemA = confirmedSalesOrderItemId(salesOrderA);
        Long itemB = confirmedSalesOrderItemId(salesOrderB);
        OffsetDateTime from = salesOrderConfirmedAt(salesOrderA).minusSeconds(1);
        OffsetDateTime to = salesOrderConfirmedAt(salesOrderB).plusSeconds(1);

        PurchaseDemandGenerateForm generate = new PurchaseDemandGenerateForm();
        generate.setStartAt(from);
        generate.setEndAt(to);
        generate.setWarehouseId(seedWarehouseId());
        generate.setSupplierId(supplierId);
        demandService.generate(generate, prefix + ":" + suffix + ":gen");

        PurchaseDemandEntity demandA = demandOf(itemA);
        PurchaseDemandEntity demandB = demandOf(itemB);

        PurchaseOrderVO order = orderService.create(
                orderForm(supplierId, skuId, demandA, quantityA, demandB, quantityB),
                prefix + ":" + suffix + ":po");
        PurchaseOrderItemVO item = order.getItems().getFirst();
        return new TwoDemandFixture(skuId, supplierId, demandA.getId(), demandB.getId(),
                itemA, itemB, order.getId(), item.getId());
    }

    private PurchaseOrderAddForm orderForm(Long supplierId, Long skuId,
                                           PurchaseDemandEntity demandA, String quantityA,
                                           PurchaseDemandEntity demandB, String quantityB) {
        PurchaseOrderAddForm form = new PurchaseOrderAddForm();
        form.setSupplierId(supplierId);
        form.setWarehouseId(seedWarehouseId());
        form.setRemark("W5 分配用例");
        PurchaseOrderAddForm.Item item = new PurchaseOrderAddForm.Item();
        item.setSkuId(skuId);
        item.setQuantity("10.0000");
        item.setPrice("6.2000");
        List<PurchaseOrderAddForm.Allocation> allocations = new ArrayList<>();
        if (demandA != null) {
            allocations.add(allocation(demandA, quantityA));
        }
        if (demandB != null) {
            allocations.add(allocation(demandB, quantityB));
        }
        item.setAllocations(allocations);
        form.setItems(new ArrayList<>(List.of(item)));
        return form;
    }

    /**
     * 把既有采购单读成一次编辑请求，**目标分配集合完全由参数决定**。
     *
     * <p>服务端对分配做的是**集合差量同步**（Q13），请求体就是「这一行最终应该有哪些分配」。
     * 因此「保留另一条」必须在请求里显式带上它 —— 不写就等于要求删除，
     * 这正是 {@link #updateDeletesOneAllocationAndDemandFallsBack} 依赖的语义。
     *
     * <p><b>需求版本取「当前值」而不是建单前的值</b>：`create` 里的 `recomputeDemands` 会把
     * 需求的 `version` 推进一次，因此建单时读到的版本在编辑时已经过期，
     * 直接用它会得到 40972（需求版本冲突）而不是被测的行为。
     */
    private PurchaseOrderUpdateForm updateForm(TwoDemandFixture fx, Map<Long, String> target) {
        PurchaseOrderVO current = queryService.orderDetail(fx.purchaseOrderId());
        PurchaseOrderItemVO item = current.getItems().getFirst();

        PurchaseOrderUpdateForm form = new PurchaseOrderUpdateForm();
        form.setId(current.getId());
        form.setVersion(current.getVersion());
        form.setSupplierId(current.getSupplierId());
        form.setWarehouseId(current.getWarehouseId());
        form.setRemark(current.getRemark());

        PurchaseOrderAddForm.Item row = new PurchaseOrderAddForm.Item();
        row.setId(item.getId());
        row.setVersion(item.getVersion());
        row.setSkuId(item.getSkuId());
        row.setQuantity("10.0000");
        row.setPrice("6.2000");
        List<PurchaseOrderAddForm.Allocation> allocations = new ArrayList<>();
        for (Map.Entry<Long, String> entry : target.entrySet()) {
            PurchaseOrderAddForm.Allocation one = new PurchaseOrderAddForm.Allocation();
            one.setDemandId(entry.getKey());
            one.setQuantity(entry.getValue());
            one.setDemandVersion(demandById(entry.getKey()).getVersion());
            allocations.add(one);
        }
        row.setAllocations(allocations);
        form.setItems(new ArrayList<>(List.of(row)));
        return form;
    }

    /** 目标态**只含一条**分配（同行的其它分配一律被删）。 */
    private PurchaseOrderUpdateForm updateForm(TwoDemandFixture fx, Long demandId, String quantity) {
        return updateForm(fx, Map.of(demandId, quantity));
    }

    /** 重新读需求。**必须先清一级缓存**：整个用例跑在一个事务里，MyBatis 的 SqlSession 与事务同生命周期。 */
    private PurchaseDemandEntity demandById(Long demandId) {
        evictMybatisCache();
        return purchaseDemandDao.selectById(demandId);
    }

    // ------------------------------------------------------------------
    // 1 / 8. 一行两需求 + 单位一致时通过
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Q13 + Q17：单位一致时，一个采购行同时承接两个需求（两条独立 allocation）")
    void oneItemCarriesTwoDemandsWhenUnitsMatch() {
        TwoDemandFixture fx = twoDemands("AL1", DEFAULT_PURCHASE_UNIT, "3.0000", "4.0000");

        PurchaseOrderVO order = queryService.orderDetail(fx.purchaseOrderId());
        List<PurchaseOrderItemVO> items = order.getItems();
        assertThat(items).hasSize(1);   // 行身份 = (order, sku)：两个需求**不**拆成两行
        assertThat(items.getFirst().getAllocations())
                .extracting(PurchaseOrderAllocationVO::getDemandId)
                .containsExactlyInAnyOrder(fx.demandA(), fx.demandB());

        // 两个需求各自补齐 → 都是 ALLOCATED（不是「只保留最后一条」）
        assertThat(demandById(fx.demandA()).getAllocatedQuantity()).isEqualByComparingTo("3.0000");
        assertThat(demandById(fx.demandA()).getStatus()).isEqualTo("ALLOCATED");
        assertThat(demandById(fx.demandB()).getAllocatedQuantity()).isEqualByComparingTo("4.0000");
        assertThat(demandById(fx.demandB()).getStatus()).isEqualTo("ALLOCATED");
        // 首次分配时把 supplier 固定到需求上（§7.4）
        assertThat(demandById(fx.demandA()).getSupplierId()).isEqualTo(fx.supplierId());
    }

    // ------------------------------------------------------------------
    // 2. 只改其中一条
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Q13：编辑只改其中一条 allocation —— 另一条的**行 id 与数量都不变**")
    void updateChangesOnlyOneAllocation() {
        TwoDemandFixture fx = twoDemands("AL2", DEFAULT_PURCHASE_UNIT, "3.0000", "4.0000");
        Long allocationIdOfA = allocationOf(fx.purchaseOrderItemId(), fx.demandA()).getId();

        // 只改需求 B：4.0000 → 3.0000（B 变成部分分配）；A 在请求里**原样带上** 3.0000。
        // 请求体是目标态，所以「保留 A」必须写出来 —— 见 updateForm 的注释。
        PurchaseOrderUpdateForm form = updateForm(fx, Map.of(
                fx.demandA(), "3.0000",
                fx.demandB(), "3.0000"));
        orderService.update(form);

        PurchaseOrderItemVO item = queryService.orderDetail(fx.purchaseOrderId()).getItems().getFirst();
        assertThat(item.getAllocations()).hasSize(2);

        PurchaseDemandAllocationEntity a = allocationOf(fx.purchaseOrderItemId(), fx.demandA());
        // A 是**同一行**（id 未变）+ 数量未变 → 证明「只改一条」没有重建兄弟行
        assertThat(a.getId()).isEqualTo(allocationIdOfA);
        assertThat(a.getAllocatedQuantity()).isEqualByComparingTo("3.0000");
        assertThat(demandById(fx.demandA()).getAllocatedQuantity()).isEqualByComparingTo("3.0000");
        assertThat(demandById(fx.demandA()).getStatus()).isEqualTo("ALLOCATED");

        assertThat(allocationOf(fx.purchaseOrderItemId(), fx.demandB()).getAllocatedQuantity())
                .isEqualByComparingTo("3.0000");
        assertThat(demandById(fx.demandB()).getAllocatedQuantity()).isEqualByComparingTo("3.0000");
        assertThat(demandById(fx.demandB()).getStatus()).isEqualTo("PARTIALLY_ALLOCATED");
    }

    // ------------------------------------------------------------------
    // 3. 删其中一条（需求侧必须回落）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Q13：编辑删掉一条 allocation —— 该需求 allocated 归零、status 从 ALLOCATED 退回 PENDING")
    void updateDeletesOneAllocationAndDemandFallsBack() {
        TwoDemandFixture fx = twoDemands("AL3", DEFAULT_PURCHASE_UNIT, "3.0000", "4.0000");

        // 请求里只留需求 A → 需求 B 的分配被删（且只删这一条）
        orderService.update(updateForm(fx, fx.demandA(), "3.0000"));

        PurchaseOrderItemVO item = queryService.orderDetail(fx.purchaseOrderId()).getItems().getFirst();
        assertThat(item.getAllocations()).extracting(PurchaseOrderAllocationVO::getDemandId)
                .containsExactly(fx.demandA());
        assertThat(purchaseDemandAllocationDao.listActiveByDemandIds(List.of(fx.demandB()))).isEmpty();

        // B 的 allocated 回落、状态回落 —— §7.8 C 段「必须遍历旧 ∪ 新」的直接证据
        assertThat(demandById(fx.demandB()).getAllocatedQuantity()).isEqualByComparingTo("0.0000");
        assertThat(demandById(fx.demandB()).getStatus()).isEqualTo("PENDING");
        // A 不受影响
        assertThat(demandById(fx.demandA()).getAllocatedQuantity()).isEqualByComparingTo("3.0000");
        assertThat(demandById(fx.demandA()).getStatus()).isEqualTo("ALLOCATED");
    }

    // ------------------------------------------------------------------
    // 4. 同一行内重复 (item, demand)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Q13：同一采购行内重复关联同一需求 → 40090")
    void duplicateItemDemandRejected() {
        Long skuId = newOnShelfSku("AL4");
        Long supplierId = newPurchasableSupplier("AL4", skuId);
        Long customerId = newCustomer();
        Long salesOrder = confirmedSalesOrder(customerId, skuId, "5.0000", "3.0000");
        Long sourceItem = confirmedSalesOrderItemId(salesOrder);

        PurchaseDemandGenerateForm generate = new PurchaseDemandGenerateForm();
        generate.setStartAt(salesOrderConfirmedAt(salesOrder));
        generate.setEndAt(salesOrderConfirmedAt(salesOrder).plusSeconds(1));
        generate.setWarehouseId(seedWarehouseId());
        generate.setSupplierId(supplierId);
        demandService.generate(generate, prefix + ":AL4:gen");
        PurchaseDemandEntity demand = demandOf(sourceItem);

        PurchaseOrderAddForm form = orderForm(supplierId, skuId, demand, "1.0000", demand, "2.0000");
        expectCode(() -> orderService.create(form, prefix + ":AL4:po"), 40090);
    }

    // ------------------------------------------------------------------
    // 5. 多需求合计超限
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Q13：同一需求被本次请求累计超过 required → 40082（且不产生任何采购单）")
    void allocationExceedingRequiredRejected() {
        TwoDemandFixture fx = twoDemands("AL5", DEFAULT_PURCHASE_UNIT, "3.0000", "4.0000");

        // 需求 A 的 required = 3.0000，本次再要 1.0000 → 3.0000 + 1.0000 > 3.0000
        PurchaseOrderUpdateForm form = updateForm(fx, fx.demandA(), "4.0000");
        expectCode(() -> orderService.update(form), 40082);
        // 回滚：A 的 allocated 保持原值
        assertThat(demandById(fx.demandA()).getAllocatedQuantity()).isEqualByComparingTo("3.0000");
    }

    // ------------------------------------------------------------------
    // 6. 编辑后 status 重算
    // ------------------------------------------------------------------

    @Test
    @DisplayName("编辑后需求侧重算：PARTIALLY_ALLOCATED → ALLOCATED（补齐），再 → PENDING（清空）")
    void demandStatusIsRecalculatedAfterEachEdit() {
        TwoDemandFixture fx = twoDemands("AL6", DEFAULT_PURCHASE_UNIT, "1.5000", "1.0000");
        assertThat(demandById(fx.demandA()).getStatus()).isEqualTo("PARTIALLY_ALLOCATED");
        assertThat(demandById(fx.demandB()).getStatus()).isEqualTo("PARTIALLY_ALLOCATED");

        // 需求 A 补齐到 required = 3.0000；需求 B 在请求里带上原值 1.0000 表示「保持不变」
        orderService.update(updateForm(fx, Map.of(
                fx.demandA(), "3.0000",
                fx.demandB(), "1.0000")));
        assertThat(demandById(fx.demandA()).getAllocatedQuantity()).isEqualByComparingTo("3.0000");
        assertThat(demandById(fx.demandA()).getStatus()).isEqualTo("ALLOCATED");
        assertThat(demandById(fx.demandB()).getStatus()).isEqualTo("PARTIALLY_ALLOCATED");

        // 清空全部分配（allocations 缺失 = 空集合 = 清空，与 items 的整体替换语义一致）
        PurchaseOrderVO current = queryService.orderDetail(fx.purchaseOrderId());
        PurchaseOrderItemVO item = current.getItems().getFirst();
        PurchaseOrderUpdateForm clear = new PurchaseOrderUpdateForm();
        clear.setId(current.getId());
        clear.setVersion(current.getVersion());
        clear.setSupplierId(current.getSupplierId());
        clear.setWarehouseId(current.getWarehouseId());
        PurchaseOrderAddForm.Item row = new PurchaseOrderAddForm.Item();
        row.setId(item.getId());
        row.setVersion(item.getVersion());
        row.setSkuId(item.getSkuId());
        row.setQuantity("10.0000");
        row.setPrice("6.2000");
        clear.setItems(new ArrayList<>(List.of(row)));
        orderService.update(clear);

        assertThat(demandById(fx.demandA()).getAllocatedQuantity()).isEqualByComparingTo("0.0000");
        assertThat(demandById(fx.demandA()).getStatus()).isEqualTo("PENDING");
        assertThat(demandById(fx.demandB()).getAllocatedQuantity()).isEqualByComparingTo("0.0000");
        assertThat(demandById(fx.demandB()).getStatus()).isEqualTo("PENDING");
        assertThat(purchaseDemandAllocationDao.listActiveByOrderId(fx.purchaseOrderId())).isEmpty();
    }

    // ------------------------------------------------------------------
    // 7. Q17：单位不一致
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Q17：采购单位（箱）≠ 需求单位（kg）→ 40971，绝不猜换算系数")
    void unitMismatchRejected() {
        Long skuId = newOnShelfSku("AL7");
        Long supplierId = newSupplier("AL7");
        linkSupplierSku(supplierId, skuId, "箱");      // 销售单位是 kg（见基类造数）
        Long customerId = newCustomer();
        Long salesOrder = confirmedSalesOrder(customerId, skuId, "5.0000", "3.0000");
        Long sourceItem = confirmedSalesOrderItemId(salesOrder);

        PurchaseDemandGenerateForm generate = new PurchaseDemandGenerateForm();
        generate.setStartAt(salesOrderConfirmedAt(salesOrder));
        generate.setEndAt(salesOrderConfirmedAt(salesOrder).plusSeconds(1));
        generate.setWarehouseId(seedWarehouseId());
        generate.setSupplierId(supplierId);
        demandService.generate(generate, prefix + ":AL7:gen");

        PurchaseDemandEntity demand = demandOf(sourceItem);
        // 需求单位来自销售单位，与 supplier_sku.purchase_unit 独立（Q17 的核心）
        assertThat(demand.getDemandUnitSnapshot()).isEqualTo(DEFAULT_PURCHASE_UNIT);

        PurchaseOrderAddForm form = orderForm(supplierId, skuId, demand, "3.0000", null, null);
        expectCode(() -> orderService.create(form, prefix + ":AL7:po"), 40971);
        // 拒绝之后需求侧一个字节都没动
        assertThat(demandById(demand.getId()).getAllocatedQuantity()).isEqualByComparingTo("0.0000");
        assertThat(demandById(demand.getId()).getStatus()).isEqualTo("PENDING");
    }

    // ------------------------------------------------------------------
    // 9. 分配唯一键
    // ------------------------------------------------------------------

    @Test
    @DisplayName("uk_purchase_demand_allocation_source_active：同一 (item, demand) 的第二条活动分配被拒")
    void allocationUniqueIndexBlocksDuplicateIdentity() {
        TwoDemandFixture fx = twoDemands("AL9", DEFAULT_PURCHASE_UNIT, "3.0000", "4.0000");
        PurchaseDemandAllocationEntity existing =
                allocationOf(fx.purchaseOrderItemId(), fx.demandA());

        expectSqlFailure(
                "INSERT INTO purchase_demand_allocation (purchase_demand_id, purchase_order_item_id, "
                        + "sales_order_id, sales_order_item_id, sku_id, allocated_quantity, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, 1.0000, 'dup')",
                existing.getPurchaseDemandId(), existing.getPurchaseOrderItemId(),
                existing.getSalesOrderId(), existing.getSalesOrderItemId(), existing.getSkuId());
    }
}
