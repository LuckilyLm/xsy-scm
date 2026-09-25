package net.lab1024.sa.admin.module.scm.finance;

import com.fasterxml.jackson.core.type.TypeReference;
import net.lab1024.sa.admin.module.scm.common.ScmW6PgITBase;
import net.lab1024.sa.admin.module.scm.common.constant.ScmOperator;
import net.lab1024.sa.admin.module.scm.finance.service.FinancePayableService;
import net.lab1024.sa.admin.module.scm.purchase.constant.ScmReceiptModeEnum;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseOrderAddForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseReceiptConfirmForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseReceiptCreateForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseReceiptPutawayForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseOrderVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseReceiptItemVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseReceiptVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 收货确认 → 正常应付（Finance R1 F1-2A，PG IT）。
 *
 * <p>本类盯的是「财务事实由收货事实派生」这条口径本身，而不是采购域的收货规则（那些已由
 * {@code PurchaseReceipt*IT} 覆盖）：金额必须 = 实际确认的有效量 × 采购行结算单价，
 * 时点必须 = {@code purchase_receipt.confirmed_at}，且两条入库模式下口径完全一致。
 *
 * <p><b>为什么用真实 PostgreSQL</b>：{@code NUMERIC(18,4)} 的舍入、{@code ON CONFLICT} 与部分唯一
 * 索引谓词的逐字匹配、单头 {@code amount > 0} 与明细 {@code quantity > 0} 的 CHECK，
 * 全都只在真实数据库里有语义（设计稿 §22）。
 *
 * <p><b>断言一律按来源作用域，不用全表计数</b>：本仓库的 IT 可能跑在长驻开发库上，
 * 「整张 {@code finance_payable} 只有一行」这种断言会把别的用例的数据算进来，
 * 变成一个随执行顺序忽绿忽红的假阴性。
 *
 * <p><b>同事务失败的回滚证明不在本类</b>：要断言「财务写失败后收货单没留下 CONFIRMED」，
 * 每次 Service 调用必须自己提交或回滚，见 {@link ScmFinancePayableRollbackPgIT}。
 */
@DisplayName("收货确认生成应付（Finance R1 F1-2A，PG IT）")
class ScmFinancePayablePgIT extends ScmW6PgITBase {

    @Autowired
    private FinancePayableService financePayableService;

    // ------------------------------------------------------------------
    // 夹具
    // ------------------------------------------------------------------

    /**
     * 单行采购单 + 指定入库方式的草稿收货单。
     *
     * <p>刻意不复用 {@link ScmW6PgITBase#inboundFixture}：那条链带需求来源，需求分配又要求
     * 采购单位与销售单位一致，而本类需要的是**单价**与**入库方式**两个自由度。
     */
    private PurchaseReceiptVO draftReceipt(String suffix, Long skuId, String planned,
                                           String price, ScmReceiptModeEnum mode) {
        Long supplierId = newSupplier(suffix);
        linkSupplierSku(supplierId, skuId, DEFAULT_PURCHASE_UNIT);
        PurchaseOrderVO order = createDraftOrder(suffix, supplierId, skuId, planned, price);
        submitOrder(order.getId());
        return draftReceiptOf(suffix, order.getId(), mode);
    }

    private PurchaseReceiptVO draftReceiptOf(String suffix, Long orderId, ScmReceiptModeEnum mode) {
        PurchaseReceiptCreateForm form = new PurchaseReceiptCreateForm();
        form.setPurchaseOrderId(orderId);
        form.setReceiptMode(mode.name());
        form.setRemark("F1-2A IT 收货单 " + suffix);
        // 幂等键必须逐张唯一：同一采购单的多张收货单是分次到货的正常业务（W5 不限张数），
        // 复用同一个键会因为**内容不同**而得到 40990，那看起来像本类的断言错了。
        return purchaseReceiptService.create(form, prefix + ":receipt:" + orderId + ":" + suffix);
    }

    /**
     * 两行采购单（两个不同 SKU）的 DIRECT 草稿收货单；行顺序 = {@code skuA} 在前。
     */
    private PurchaseReceiptVO draftReceiptTwoLines(String suffix, Long skuA, String priceA,
                                                   Long skuB, String priceB) {
        Long supplierId = newSupplier(suffix);
        linkSupplierSkus(supplierId, skuA, skuB);

        PurchaseOrderAddForm form = orderForm(supplierId, seedWarehouseId(), skuA, "10.0000", priceA);
        form.setItems(new ArrayList<>(List.of(
                item(skuA, "10.0000", priceA),
                item(skuB, "10.0000", priceB))));
        PurchaseOrderVO order = purchaseOrderService.create(form, prefix + ":" + suffix + ":po2");
        submitOrder(order.getId());
        return draftReceiptOf(suffix, order.getId(), ScmReceiptModeEnum.DIRECT);
    }

    /**
     * 按收货行录入顺序提交**全部**活动行（W5 要求请求行集合 == 活动行集合，少一行即 40998）。
     */
    private PurchaseReceiptVO confirmAllLines(Long receiptId, String... quantities) {
        PurchaseReceiptVO current = reloadReceipt(receiptId);
        List<PurchaseReceiptItemVO> lines = current.getItems();
        assertThat(lines).as("提交数量数必须与收货行数一致").hasSize(quantities.length);

        List<PurchaseReceiptConfirmForm.Item> items = new ArrayList<>(lines.size());
        for (int index = 0; index < lines.size(); index++) {
            PurchaseReceiptItemVO line = lines.get(index);
            items.add(receiptLine(line.getId(), line.getVersion(), quantities[index]));
        }
        return purchaseReceiptService.confirm(
                confirmForm(current.getId(), current.getVersion(),
                        items.toArray(PurchaseReceiptConfirmForm.Item[]::new)),
                prefix + ":confirm:" + receiptId + ":" + UUID.randomUUID());
    }

    private PurchaseReceiptVO putaway(Long receiptId) {
        PurchaseReceiptVO current = reloadReceipt(receiptId);
        PurchaseReceiptPutawayForm form = new PurchaseReceiptPutawayForm();
        form.setId(receiptId);
        form.setVersion(current.getVersion());
        return purchaseReceiptService.putaway(
                form, prefix + ":putaway:" + receiptId + ":" + UUID.randomUUID());
    }

    // ------------------------------------------------------------------
    // 断言辅助
    // ------------------------------------------------------------------

    private Map<String, Object> payableOf(Long receiptId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM finance_payable WHERE source_type = 'PURCHASE_RECEIPT' AND source_id = ?",
                receiptId);
        assertThat(rows).as("收货单 %s 的应付单（恰好一张）", receiptId).hasSize(1);
        return rows.getFirst();
    }

    private List<Map<String, Object>> payablesOfReceipt(Long receiptId) {
        return jdbc.queryForList(
                "SELECT * FROM finance_payable WHERE source_type = 'PURCHASE_RECEIPT' AND source_id = ?",
                receiptId);
    }

    private int payablesOfOrder(Long purchaseOrderId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM finance_payable WHERE purchase_order_id = ?",
                Integer.class, purchaseOrderId);
    }

    private List<Map<String, Object>> payableItemsOf(Long payableId) {
        return jdbc.queryForList(
                "SELECT * FROM finance_payable_item WHERE payable_id = ? ORDER BY id", payableId);
    }

    /**
     * 这张收货单在财务侧留下的明细行数（作用域到行，不用全表计数）。
     */
    private int payableItemCountOfReceipt(Long receiptId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM finance_payable_item i WHERE i.source_type = 'PURCHASE_RECEIPT_ITEM' "
                        + "AND i.source_id IN (SELECT id FROM purchase_receipt_item WHERE purchase_receipt_id = ?)",
                Integer.class, receiptId);
    }

    private List<Map<String, Object>> payableLogsOf(Long payableId) {
        return jdbc.queryForList(
                "SELECT * FROM finance_operation_log WHERE business_type = 'PAYABLE' AND business_id = ?",
                payableId);
    }

    private static Long longOf(Map<String, Object> row, String column) {
        return ((Number) row.get(column)).longValue();
    }

    private static BigDecimal decimalOf(Map<String, Object> row, String column) {
        return (BigDecimal) row.get(column);
    }

    private static OffsetDateTime timeOf(Map<String, Object> row, String column) {
        return timestampOf(row.get(column));
    }

    // ------------------------------------------------------------------
    // A / I. DIRECT 收货确认 → 单头 + 明细 + 操作日志
    // ------------------------------------------------------------------

    @Test
    @DisplayName("DIRECT：确认后一张 NORMAL 应付，量=有效收货量、价=采购行单价、日志同事务")
    void directConfirmGeneratesPayableWithItemsAndLog() throws Exception {
        Long skuId = newOnShelfSku("FPA");
        PurchaseReceiptVO receipt = draftReceipt("FPA", skuId, "8.0000", "6.2000", ScmReceiptModeEnum.DIRECT);
        confirmAllLines(receipt.getId(), "8.0000");

        Map<String, Object> payable = payableOf(receipt.getId());
        Long payableId = longOf(payable, "id");
        assertThat(payable.get("entry_type")).isEqualTo("NORMAL");
        assertThat(longOf(payable, "source_id")).isEqualTo(receipt.getId());
        assertThat(longOf(payable, "purchase_order_id")).isEqualTo(receipt.getPurchaseOrderId());
        assertThat(longOf(payable, "supplier_id")).isEqualTo(receipt.getSupplierId());
        // 供应商名取收货事实上的快照，不回读供应商主档（主档改名不会改历史账）
        assertThat(payable.get("supplier_name_snapshot")).isEqualTo(receipt.getSupplierName());
        assertThat(decimalOf(payable, "amount")).isEqualByComparingTo("49.6000");
        assertThat(payable.get("reason")).isNull();
        assertThat(payable.get("original_payable_id")).isNull();
        assertThat(payable.get("deleted")).isEqualTo(false);
        assertThat(String.valueOf(payable.get("payable_no"))).matches("AP\\d{14,}");
        // 应付时点 = 收货确认时刻（DIRECT 下也等于物理入库时刻，但口径取的是 confirmed_at）
        assertThat(timeOf(payable, "event_at").toInstant())
                .isEqualTo(receiptConfirmedAt(receipt.getId()).toInstant());

        List<Map<String, Object>> items = payableItemsOf(payableId);
        assertThat(items).hasSize(1);
        Map<String, Object> line = items.getFirst();
        Long receiptItemId = receipt.getItems().getFirst().getId();
        assertThat(line.get("source_type")).isEqualTo("PURCHASE_RECEIPT_ITEM");
        assertThat(longOf(line, "source_id")).isEqualTo(receiptItemId);
        assertThat(longOf(line, "purchase_order_item_id"))
                .isEqualTo(receipt.getItems().getFirst().getPurchaseOrderItemId());
        assertThat(longOf(line, "sku_id")).isEqualTo(skuId);
        assertThat(line.get("sku_name_snapshot")).isEqualTo(jdbc.queryForObject(
                "SELECT sku_name_snapshot FROM purchase_receipt_item WHERE id = ?", String.class, receiptItemId));
        assertThat(line.get("unit_snapshot")).isEqualTo(DEFAULT_PURCHASE_UNIT);
        assertThat(decimalOf(line, "quantity")).isEqualByComparingTo("8.0000");
        assertThat(decimalOf(line, "unit_price")).isEqualByComparingTo("6.2000");
        assertThat(decimalOf(line, "amount")).isEqualByComparingTo("49.6000");
        assertThat(line.get("deleted")).isEqualTo(false);

        // 日志与应付同事务：GENERATE 没有「改前」，改后是单头快照，operator 是本次收货确认的人
        List<Map<String, Object>> logs = payableLogsOf(payableId);
        assertThat(logs).hasSize(1);
        Map<String, Object> log = logs.getFirst();
        assertThat(log.get("operation_type")).isEqualTo("GENERATE");
        assertThat(log.get("reason")).isNull();
        assertThat(log.get("before_data")).isNull();
        assertThat(log.get("operator")).isEqualTo(ScmOperator.current());
        // after_data 走 JSON 解析而不是字符串包含：JSONB 的存储形态会重排键序与空白，
        // 按子串断言等于把测试绑在序列化器的排版细节上。
        Map<String, Object> after = json.readValue(String.valueOf(log.get("after_data")),
                new TypeReference<Map<String, Object>>() {
                });
        assertThat(after)
                .containsEntry("payableNo", payable.get("payable_no"))
                .containsEntry("amount", "49.6000")
                .containsEntry("entryType", "NORMAL")
                .containsEntry("sourceType", "PURCHASE_RECEIPT")
                .containsEntry("itemCount", 1);
        // JSON 数字读回来是 Integer 还是 Long 取决于值的大小，因此按数值比较而不是直接比对象
        assertThat(((Number) after.get("sourceId")).longValue()).isEqualTo(receipt.getId());
        assertThat(((Number) after.get("purchaseOrderId")).longValue())
                .isEqualTo(receipt.getPurchaseOrderId());
    }

    // ------------------------------------------------------------------
    // B / C. WAREHOUSE_CONFIRM 的应付时点是「确认」，不是「上架」
    // ------------------------------------------------------------------

    @Test
    @DisplayName("WAREHOUSE_CONFIRM：确认即生成应付且 event_at=confirmed_at；putaway 不追加也不改时点")
    void warehouseConfirmGeneratesAtConfirmTimeNotPutawayTime() {
        Long skuId = newOnShelfSku("FPB");
        PurchaseReceiptVO receipt = draftReceipt("FPB", skuId, "8.0000", "6.2000",
                ScmReceiptModeEnum.WAREHOUSE_CONFIRM);
        confirmAllLines(receipt.getId(), "8.0000");

        Map<String, Object> payable = payableOf(receipt.getId());
        Long payableId = longOf(payable, "id");
        OffsetDateTime confirmedAt = receiptConfirmedAt(receipt.getId());
        // 库存还没入账（putaway 仍 PENDING、无流水），应付已经成立 —— 这就是 Q9 的全部内容
        assertThat(reloadReceipt(receipt.getId()).getPutawayStatus()).isEqualTo("PENDING");
        assertThat(movementCount(seedWarehouseId(), skuId)).isZero();
        assertThat(timeOf(payable, "event_at").toInstant()).isEqualTo(confirmedAt.toInstant());
        assertThat(payablesOfOrder(receipt.getPurchaseOrderId())).isEqualTo(1);

        assertThat(putaway(receipt.getId()).getPutawayStatus()).isEqualTo("COMPLETED");

        assertThat(payablesOfOrder(receipt.getPurchaseOrderId())).as("上架不追加应付").isEqualTo(1);
        Map<String, Object> afterPutaway = payableOf(receipt.getId());
        assertThat(longOf(afterPutaway, "id")).isEqualTo(payableId);
        assertThat(timeOf(afterPutaway, "event_at").toInstant())
                .as("上架也不改时点").isEqualTo(confirmedAt.toInstant());
        assertThat(payableItemsOf(payableId)).hasSize(1);
        assertThat(payableLogsOf(payableId)).hasSize(1);
    }

    // ------------------------------------------------------------------
    // D. 金额 = 量 × 价，scale 4、HALF_UP
    // ------------------------------------------------------------------

    @Test
    @DisplayName("金额口径：3.3333 × 2.2222 = 7.40725926 → 7.4073（HALF_UP 到 4 位，不是截断）")
    void itemAmountRoundsHalfUpAtScaleFour() {
        Long skuId = newOnShelfSku("FPC");
        PurchaseReceiptVO receipt = draftReceipt("FPC", skuId, "4.0000", "2.2222", ScmReceiptModeEnum.DIRECT);
        confirmAllLines(receipt.getId(), "3.3333");

        Map<String, Object> payable = payableOf(receipt.getId());
        Map<String, Object> line = payableItemsOf(longOf(payable, "id")).getFirst();
        assertThat(decimalOf(line, "quantity")).isEqualByComparingTo("3.3333");
        assertThat(decimalOf(line, "unit_price")).isEqualByComparingTo("2.2222");
        assertThat(decimalOf(line, "amount")).isEqualByComparingTo("7.4073");
        assertThat(decimalOf(payable, "amount")).as("单头 = 已舍入的行金额之和").isEqualByComparingTo("7.4073");
    }

    // ------------------------------------------------------------------
    // E. 合法容差内超收：全额进应付
    // ------------------------------------------------------------------

    @Test
    @DisplayName("超收：planned 10 收 11（默认容差 10%）→ 应付按 11.0000 全额计")
    void overReceiptWithinToleranceIsFullyPayable() {
        Long skuId = newOnShelfSku("FPD");
        PurchaseReceiptVO receipt = draftReceipt("FPD", skuId, "10.0000", "6.2000", ScmReceiptModeEnum.DIRECT);
        PurchaseReceiptVO confirmed = confirmAllLines(receipt.getId(), "11.0000");

        assertThat(confirmed.getItems().getFirst().getOverReceiptQuantity())
                .isEqualByComparingTo("1.0000");

        Map<String, Object> payable = payableOf(receipt.getId());
        Map<String, Object> line = payableItemsOf(longOf(payable, "id")).getFirst();
        assertThat(decimalOf(line, "quantity")).isEqualByComparingTo("11.0000");
        assertThat(decimalOf(payable, "amount")).isEqualByComparingTo("68.2000");
    }

    // ------------------------------------------------------------------
    // F. 少收：只按实际确认量形成，不产生任何差异事实
    // ------------------------------------------------------------------

    @Test
    @DisplayName("少收：planned 10 收 4 → 应付 24.8000；采购侧的差异/剩余量一个都不进财务表")
    void shortReceiptPaysOnlyTheConfirmedQuantity() {
        Long skuId = newOnShelfSku("FPE");
        PurchaseReceiptVO receipt = draftReceipt("FPE", skuId, "10.0000", "6.2000", ScmReceiptModeEnum.DIRECT);
        confirmAllLines(receipt.getId(), "4.0000");

        Map<String, Object> payable = payableOf(receipt.getId());
        List<Map<String, Object>> items = payableItemsOf(longOf(payable, "id"));
        assertThat(items).as("一条收货行只有一行应付明细").hasSize(1);
        assertThat(decimalOf(items.getFirst(), "quantity")).isEqualByComparingTo("4.0000");
        assertThat(decimalOf(payable, "amount")).isEqualByComparingTo("24.8000");

        // 「不复制差异数据」（第二批 Q12）：这张收货单在财务侧只有这一张单、这一行。
        // 尤其不得出现一条表达「还欠 6.0000」的差异行，也不得有 RED 单。
        assertThat(payableItemCountOfReceipt(receipt.getId()))
                .as("本张收货单只留下一行应付明细").isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM finance_payable WHERE purchase_order_id = ? AND entry_type = 'RED'",
                Integer.class, receipt.getPurchaseOrderId())).isZero();
    }

    // ------------------------------------------------------------------
    // G. 整单 0 元：不产生 0 元财务事实
    // ------------------------------------------------------------------

    @Test
    @DisplayName("单价合法为 0：有效量存在但金额为 0 → 成功跳过，不产生 0 元应付也不留日志")
    void zeroAmountFactsAreNotGenerated() {
        Long skuId = newOnShelfSku("FPF");
        PurchaseReceiptVO receipt = draftReceipt("FPF", skuId, "5.0000", "0.0000", ScmReceiptModeEnum.DIRECT);
        PurchaseReceiptVO confirmed = confirmAllLines(receipt.getId(), "5.0000");

        // 前置事实：收货本身成功了（跳过财务事实不是「整笔失败」的副作用）
        assertThat(confirmed.getStatus()).isEqualTo("CONFIRMED");
        assertThat(movementCount(seedWarehouseId(), skuId)).isEqualTo(1);

        assertThat(payablesOfReceipt(receipt.getId())).as("0 元收货不产生应付单头").isEmpty();
        assertThat(payableItemCountOfReceipt(receipt.getId())).as("也没有任何应付明细").isZero();

        // 重放同样什么都不产生
        financePayableService.generateOnReceiptConfirm(receipt.getId());
        assertThat(payablesOfReceipt(receipt.getId())).isEmpty();
    }

    // ------------------------------------------------------------------
    // H. 0 元明细留在应付单里，只要单头金额为正
    // ------------------------------------------------------------------

    @Test
    @DisplayName("两行（赠品行 0 元 + 正常行）：0 元明细照实入账不丢弃，单头为行之和 14.0000")
    void zeroPriceLineStaysInPayableWhileHeaderStaysPositive() {
        Long freeSku = newOnShelfSku("FPG-A");
        Long paidSku = newOnShelfSku("FPG-B");
        PurchaseReceiptVO receipt = draftReceiptTwoLines("FPG", freeSku, "0.0000", paidSku, "3.5000");
        confirmAllLines(receipt.getId(), "2.0000", "4.0000");

        Map<String, Object> payable = payableOf(receipt.getId());
        List<Map<String, Object>> items = payableItemsOf(longOf(payable, "id"));
        assertThat(items).as("两行收货 → 两行应付明细，金额为 0 的那一行也在").hasSize(2);

        Map<String, Object> free = items.get(0);
        assertThat(longOf(free, "sku_id")).isEqualTo(freeSku);
        assertThat(decimalOf(free, "quantity")).isEqualByComparingTo("2.0000");
        assertThat(decimalOf(free, "unit_price")).isEqualByComparingTo("0.0000");
        assertThat(decimalOf(free, "amount")).isEqualByComparingTo("0.0000");

        Map<String, Object> paid = items.get(1);
        assertThat(longOf(paid, "sku_id")).isEqualTo(paidSku);
        assertThat(decimalOf(paid, "quantity")).isEqualByComparingTo("4.0000");
        assertThat(decimalOf(paid, "amount")).isEqualByComparingTo("14.0000");

        assertThat(decimalOf(payable, "amount")).isEqualByComparingTo("14.0000");
    }

    // ------------------------------------------------------------------
    // I. 重复触发只有一张应付
    // ------------------------------------------------------------------

    @Test
    @DisplayName("重复生成：第二次命中来源唯一索引 → 仍是一张单、一份日志，且不报错")
    void duplicateGenerationKeepsSinglePayableAndSingleLog() {
        Long skuId = newOnShelfSku("FPH");
        PurchaseReceiptVO receipt = draftReceipt("FPH", skuId, "8.0000", "6.2000", ScmReceiptModeEnum.DIRECT);
        confirmAllLines(receipt.getId(), "8.0000");

        Long payableId = longOf(payableOf(receipt.getId()), "id");

        financePayableService.generateOnReceiptConfirm(receipt.getId());
        financePayableService.generateOnReceiptConfirm(receipt.getId());

        Map<String, Object> payable = payableOf(receipt.getId());
        assertThat(longOf(payable, "id")).as("重放不换单也不换号").isEqualTo(payableId);
        assertThat(payableItemsOf(payableId)).hasSize(1);
        assertThat(payableLogsOf(payableId)).as("已生成不是新事实，不重复留痕").hasSize(1);
    }

    // ------------------------------------------------------------------
    // J. 一张采购单多次收货 = 多张应付（第一批 Q10）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("分次到货：同一采购单的两张收货单各生成一张应付，互不合并、单号不重复")
    void secondReceiptOnTheSameOrderGeneratesItsOwnPayable() {
        Long skuId = newOnShelfSku("FPI");
        Long supplierId = newSupplier("FPI");
        linkSupplierSku(supplierId, skuId, DEFAULT_PURCHASE_UNIT);
        PurchaseOrderVO order = createDraftOrder("FPI", supplierId, skuId, "10.0000", "6.2000");
        submitOrder(order.getId());

        PurchaseReceiptVO first = draftReceiptOf("FPI-1", order.getId(), ScmReceiptModeEnum.DIRECT);
        confirmAllLines(first.getId(), "6.0000");
        PurchaseReceiptVO second = draftReceiptOf("FPI-2", order.getId(), ScmReceiptModeEnum.DIRECT);
        confirmAllLines(second.getId(), "4.0000");

        assertThat(payablesOfOrder(order.getId())).isEqualTo(2);
        Map<String, Object> firstPayable = payableOf(first.getId());
        Map<String, Object> secondPayable = payableOf(second.getId());
        assertThat(decimalOf(firstPayable, "amount")).isEqualByComparingTo("37.2000");
        assertThat(decimalOf(secondPayable, "amount")).isEqualByComparingTo("24.8000");
        assertThat(longOf(firstPayable, "id")).isNotEqualTo(longOf(secondPayable, "id"));
        // 两条单号来自同一条全局非重置序列
        assertThat(firstPayable.get("payable_no")).isNotEqualTo(secondPayable.get("payable_no"));
        // 两行明细各归各的单，来源都是自己的收货行
        assertThat(payableItemsOf(longOf(firstPayable, "id"))).hasSize(1);
        assertThat(payableItemsOf(longOf(secondPayable, "id"))).hasSize(1);
    }

    // ------------------------------------------------------------------
    // K. 未确认的收货单不产生应付
    // ------------------------------------------------------------------

    @Test
    @DisplayName("草稿收货单调用生成器 → 抛错且不产生应付（状态守卫在 SQL 谓词里，不靠调用方自觉）")
    void draftReceiptCannotGeneratePayable() {
        Long skuId = newOnShelfSku("FPK");
        PurchaseReceiptVO receipt = draftReceipt("FPK", skuId, "8.0000", "6.2000", ScmReceiptModeEnum.DIRECT);

        assertThatThrownBy(() -> financePayableService.generateOnReceiptConfirm(receipt.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不能生成应付");

        assertThat(payablesOfReceipt(receipt.getId())).isEmpty();
        assertThat(reloadReceipt(receipt.getId()).getStatus()).isEqualTo("DRAFT");
    }
}
