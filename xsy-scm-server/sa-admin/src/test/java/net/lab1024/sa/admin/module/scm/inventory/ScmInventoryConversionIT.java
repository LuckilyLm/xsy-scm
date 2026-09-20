package net.lab1024.sa.admin.module.scm.inventory;

import net.lab1024.sa.admin.module.scm.common.ScmW6PgITBase;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryConversionAddForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryConversionAuditForm;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryConversionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 规格转换的 PostgreSQL 集成测试（规格转换波次）。
 *
 * <p>覆盖六件在单测里验证不了的事：
 * <ol>
 *   <li><b>跨 SKU 的两行余额在同一事务里被改动</b> —— 这是本能力与既有六条写入路径的
 *       唯一实质差异（它们每个事务只碰一行余额）；</li>
 *   <li><b>链式转换不死锁</b> —— 同一张单里 A→B 且 B→C，B 既是目标又是源。
 *       若「逐行先源后目标」地拿锁，两行会以相反顺序被锁而死锁；
 *       正确实现是先把所有腿按 {@code (skuId, 方向)} 排序再逐条执行；</li>
 *   <li><b>两个单位各自与余额比对</b> —— 源单位不一致 41059、目标单位不一致 41060，
 *       都不做隐式换算（Q13）；</li>
 *   <li><b>入方向才建余额行</b> —— 目标 SKU 没有余额行时由转换建立（单位取声明值），
 *       源 SKU 没有则失败（41058）；</li>
 *   <li><b>append-only</b> —— 转换的两条流水同样不可改删。</li>
 * </ol>
 */
@DisplayName("规格转换（PG IT）")
class ScmInventoryConversionIT extends ScmW6PgITBase {

    @Autowired
    private InventoryConversionService conversionService;

    /** 造一个按指定**采购单位**入库的 SKU，返回 skuId（余额行的记账单位 = 该采购单位）。 */
    private Long stockedWithUnit(String suffix, String quantity, String purchaseUnit) {
        Long skuId = newSkuOfType(suffix, "NON_STANDARD", "ON_SHELF");
        W6Fixture fixture = freeInboundFixture(suffix, skuId, quantity, purchaseUnit);
        confirmReceipt(fixture.receipt().getId(), quantity);
        return skuId;
    }

    private static InventoryConversionAddForm.Item item(Long sourceSku, String sourceQty, String sourceUnit,
                                                       Long targetSku, String targetQty, String targetUnit) {
        InventoryConversionAddForm.Item row = new InventoryConversionAddForm.Item();
        row.setSourceSkuId(sourceSku);
        row.setSourceQuantity(new BigDecimal(sourceQty));
        row.setSourceUnit(sourceUnit);
        row.setTargetSkuId(targetSku);
        row.setTargetQuantity(new BigDecimal(targetQty));
        row.setTargetUnit(targetUnit);
        return row;
    }

    private InventoryConversionAddForm form(String type, Long warehouseId,
                                            InventoryConversionAddForm.Item... items) {
        InventoryConversionAddForm form = new InventoryConversionAddForm();
        form.setWarehouseId(warehouseId);
        form.setConvertType(type);
        form.setItems(new ArrayList<>(List.of(items)));
        return form;
    }

    private int versionOf(Long id) {
        return jdbc.queryForObject(
                "SELECT version FROM inventory_conversion WHERE id = ?", Integer.class, id);
    }

    private InventoryConversionAuditForm audit(Long id) {
        InventoryConversionAuditForm form = new InventoryConversionAuditForm();
        form.setVersion(versionOf(id));
        return form;
    }

    private String statusOf(Long id) {
        return jdbc.queryForObject(
                "SELECT status FROM inventory_conversion WHERE id = ?", String.class, id);
    }

    private List<Map<String, Object>> convertMovements(Long wh, Long sku) {
        return movementsOf(wh, sku).stream()
                .filter(m -> {
                    String type = String.valueOf(m.get("movement_type"));
                    return "CONVERT_OUT".equals(type) || "CONVERT_IN".equals(type);
                })
                .toList();
    }

    // ------------------------------------------------------------------
    // 核心：跨 SKU 两行余额 + 链式转换
    // ------------------------------------------------------------------

    @Test
    @DisplayName("整件拆零：源 SKU 出、目标 SKU 入（目标无余额行时由转换建立）")
    void splitConvertsAcrossTwoSkus() {
        Long wh = seedWarehouseId();
        Long source = stockedWithUnit("cv1", "5.0000", "箱");
        Long target = newSkuOfType("cv1t", "NON_STANDARD", "ON_SHELF");
        assertThat(balanceRow(wh, source).getUnit()).isEqualTo("箱");
        assertThat(balanceRow(wh, target)).as("目标 SKU 尚未入库").isNull();

        // 1 箱 → 10 kg（折算关系由单据声明）
        Long id = conversionService.create(form("SPLIT", wh,
                item(source, "2.0000", "箱", target, "20.0000", "kg")));
        assertThat(statusOf(id)).isEqualTo("PENDING");
        assertThat(balanceRow(wh, source).getQuantity()).as("待审核不动库存").isEqualByComparingTo("5.0000");

        conversionService.approve(id, audit(id));

        assertThat(statusOf(id)).isEqualTo("COMPLETED");
        assertThat(balanceRow(wh, source).getQuantity()).isEqualByComparingTo("3.0000");
        assertThat(balanceRow(wh, target).getQuantity()).isEqualByComparingTo("20.0000");
        assertThat(balanceRow(wh, target).getUnit()).as("目标行由转换建立，单位取声明值")
                .isEqualTo("kg");

        Map<String, Object> out = convertMovements(wh, source).getFirst();
        assertThat(out.get("movement_type")).isEqualTo("CONVERT_OUT");
        assertThat(out.get("source_document_type")).isEqualTo("CONVERT_OUT_ITEM");
        assertThat(new BigDecimal(String.valueOf(out.get("quantity")))).isEqualByComparingTo("2.0000");
        assertThat(new BigDecimal(String.valueOf(out.get("after_quantity")))).isEqualByComparingTo("3.0000");

        Map<String, Object> in = convertMovements(wh, target).getFirst();
        assertThat(in.get("movement_type")).isEqualTo("CONVERT_IN");
        assertThat(in.get("source_document_type")).isEqualTo("CONVERT_IN_ITEM");
        assertThat(new BigDecimal(String.valueOf(in.get("quantity")))).isEqualByComparingTo("20.0000");

        // **成本守恒的是总额，不是单价**（折算率是人工声明的，跨单位单价必然不同）：
        // 2 箱 × 6.20 = 12.40，摊到 20 kg → 0.6200 / kg。
        assertThat(new BigDecimal(String.valueOf(out.get("unit_cost"))))
                .as("转出腿 = 源 SKU 当时的均价").isEqualByComparingTo("6.2000");
        assertThat(new BigDecimal(String.valueOf(in.get("unit_cost"))))
                .as("转入腿 = 转出腿总成本 ÷ 转入数量").isEqualByComparingTo("0.6200");
        assertThat(balanceRow(wh, source).getAvgCost()).as("转出腿不改变源均价")
                .isEqualByComparingTo("6.2000");
        assertThat(balanceRow(wh, target).getAvgCost())
                .as("目标行由转换建立，按转入成本入账而不是留在 0")
                .isEqualByComparingTo("0.6200");
        assertThat(balanceRow(wh, source).getQuantity().multiply(new BigDecimal("6.2000"))
                        .add(balanceRow(wh, target).getQuantity()
                                .multiply(balanceRow(wh, target).getAvgCost())))
                .as("转换前后总金额不变：3 箱 × 6.20 + 20 kg × 0.62 = 5 箱 × 6.20")
                .isEqualByComparingTo("31.0000");
    }

    @Test
    @DisplayName("链式转换：同一单里 A→B 且 B→C（B 既是目标又是源）不死锁且结果正确")
    void chainedConversionDoesNotDeadlock() {
        Long wh = seedWarehouseId();
        Long a = stockedWithUnit("cv2a", "10.0000", "kg");
        Long b = newSkuOfType("cv2b", "NON_STANDARD", "ON_SHELF");
        Long c = newSkuOfType("cv2c", "NON_STANDARD", "ON_SHELF");

        // 两行：A→B（5），B→C（3）。B 在第一行是目标、在第二行是源 ——
        // 「逐行先源后目标」的实现会先锁 A 再锁 B、然后先锁 B 再锁 C，
        // 看起来没问题；但只要两行的 SKU 顺序反过来就会以相反顺序拿锁。
        // 正确实现把所有腿按 (skuId, 方向) 全局排序，因此这里能稳定通过。
        Long id = conversionService.create(form("SPLIT", wh,
                item(a, "5.0000", "kg", b, "5.0000", "kg"),
                item(b, "3.0000", "kg", c, "3.0000", "kg")));
        conversionService.approve(id, audit(id));

        assertThat(statusOf(id)).isEqualTo("COMPLETED");
        assertThat(balanceRow(wh, a).getQuantity()).isEqualByComparingTo("5.0000");
        // B：先入 5 再出 3 → 净 2。若实现成「先出后入」，B 的「出」会因为还没入而失败
        assertThat(balanceRow(wh, b).getQuantity()).as("链式：先入后出，净 2").isEqualByComparingTo("2.0000");
        assertThat(balanceRow(wh, c).getQuantity()).isEqualByComparingTo("3.0000");

        // 成本沿链条走完：A→B→C 全程 1:1 同单位，所以三行的均价都是 A 的 6.2000。
        // 基准若只取各 SKU 的**期初**均价，B 没有期初行 → 0，C 于是被记成零成本 ——
        // 与调拨清零是同一个缺陷，因此链式场景必须在测试里钉住。
        assertThat(balanceRow(wh, b).getAvgCost()).as("B 按本单转入腿加权，不是期初的 0")
                .isEqualByComparingTo("6.2000");
        assertThat(balanceRow(wh, c).getAvgCost()).as("成本穿过中间的 B 到达 C")
                .isEqualByComparingTo("6.2000");
        assertThat(convertMovements(wh, b).stream()
                        .filter(m -> "CONVERT_OUT".equals(String.valueOf(m.get("movement_type"))))
                        .map(m -> new BigDecimal(String.valueOf(m.get("unit_cost"))))
                        .findFirst().orElseThrow())
                .as("B 的转出腿 = 它进完之后的均价").isEqualByComparingTo("6.2000");
    }

    // ------------------------------------------------------------------
    // 单位与下限
    // ------------------------------------------------------------------

    @Test
    @DisplayName("源单位与余额记账单位不一致被拒（41059），不做隐式换算")
    void sourceUnitMismatchIsRejected() {
        Long wh = seedWarehouseId();
        Long source = stockedWithUnit("cv3", "5.0000", "箱");
        Long target = newSkuOfType("cv3t", "NON_STANDARD", "ON_SHELF");

        // 源实际按「箱」记账，单据却声明「kg」
        Long id = conversionService.create(form("SPLIT", wh,
                item(source, "1.0000", "kg", target, "10.0000", "kg")));
        expectCode(() -> conversionService.approve(id, audit(id)), 41059);

        assertThat(balanceRow(wh, source).getQuantity()).isEqualByComparingTo("5.0000");
        assertThat(balanceRow(wh, target)).isNull();
        assertThat(statusOf(id)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("目标单位与目标 SKU 既有余额单位不一致被拒（41060）")
    void targetUnitMismatchIsRejected() {
        Long wh = seedWarehouseId();
        Long source = stockedWithUnit("cv4", "5.0000", "kg");
        Long target = stockedWithUnit("cv4t", "1.0000", "箱");

        Long id = conversionService.create(form("SPLIT", wh,
                item(source, "1.0000", "kg", target, "10.0000", "kg")));
        expectCode(() -> conversionService.approve(id, audit(id)), 41060);

        assertThat(balanceRow(wh, target).getQuantity()).isEqualByComparingTo("1.0000");
        assertThat(statusOf(id)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("源 SKU 可用量不足被拒（41061）")
    void insufficientSourceIsRejected() {
        Long wh = seedWarehouseId();
        Long source = stockedWithUnit("cv5", "5.0000", "kg");
        Long target = newSkuOfType("cv5t", "NON_STANDARD", "ON_SHELF");

        Long id = conversionService.create(form("SPLIT", wh,
                item(source, "6.0000", "kg", target, "60.0000", "kg")));
        expectCode(() -> conversionService.approve(id, audit(id)), 41061);
        assertThat(balanceRow(wh, source).getQuantity()).isEqualByComparingTo("5.0000");
    }

    @Test
    @DisplayName("源 SKU 无余额行被拒（41058）—— 出方向不建行")
    void missingSourceBalanceIsRejected() {
        Long wh = seedWarehouseId();
        Long source = newSkuOfType("cv6", "NON_STANDARD", "ON_SHELF");
        Long target = newSkuOfType("cv6t", "NON_STANDARD", "ON_SHELF");
        assertThat(balanceRowCount(wh, source)).isZero();

        Long id = conversionService.create(form("SPLIT", wh,
                item(source, "1.0000", "kg", target, "10.0000", "kg")));
        expectCode(() -> conversionService.approve(id, audit(id)), 41058);
        assertThat(balanceRowCount(wh, source)).isZero();
        assertThat(balanceRowCount(wh, target)).isZero();
    }

    // ------------------------------------------------------------------
    // 入口与状态机
    // ------------------------------------------------------------------

    @Test
    @DisplayName("同一行源 SKU 与目标 SKU 相同被拒（41057）")
    void sameSkuIsRejected() {
        Long wh = seedWarehouseId();
        Long sku = stockedWithUnit("cv7", "5.0000", "kg");
        expectCode(() -> conversionService.create(form("SPLIT", wh,
                item(sku, "1.0000", "kg", sku, "1.0000", "kg"))), 41057);
    }

    @Test
    @DisplayName("状态机：已完成的单据不可再审批 / 改 / 删；审批乐观锁生效")
    void conversionStatusMachineAndOptimisticLock() {
        Long wh = seedWarehouseId();
        Long source = stockedWithUnit("cv8", "5.0000", "kg");
        Long target = newSkuOfType("cv8t", "NON_STANDARD", "ON_SHELF");

        Long id = conversionService.create(form("SPLIT", wh,
                item(source, "1.0000", "kg", target, "10.0000", "kg")));

        // 乐观锁：审批人看到的是 version 0，但录单人先改了一次
        int seen = versionOf(id);
        conversionService.update(id, form("SPLIT", wh,
                item(source, "2.0000", "kg", target, "20.0000", "kg")));
        InventoryConversionAuditForm stale = new InventoryConversionAuditForm();
        stale.setVersion(seen);
        expectCode(() -> conversionService.approve(id, stale), 40921);

        // 用新版本审批通过，按**改后**的数量执行
        conversionService.approve(id, audit(id));
        assertThat(balanceRow(wh, source).getQuantity()).isEqualByComparingTo("3.0000");
        assertThat(balanceRow(wh, target).getQuantity()).isEqualByComparingTo("20.0000");

        // 终态
        expectCode(() -> conversionService.approve(id, audit(id)), 41054);
        expectCode(() -> conversionService.update(id, form("SPLIT", wh,
                item(source, "1.0000", "kg", target, "1.0000", "kg"))), 41054);
        expectCode(() -> conversionService.delete(id), 41054);
    }

    @Test
    @DisplayName("驳回必须填审核意见（41063），驳回后不动库存且不可再审")
    void rejectRequiresOpinion() {
        Long wh = seedWarehouseId();
        Long source = stockedWithUnit("cv9", "5.0000", "kg");
        Long target = newSkuOfType("cv9t", "NON_STANDARD", "ON_SHELF");

        Long id = conversionService.create(form("SPLIT", wh,
                item(source, "1.0000", "kg", target, "10.0000", "kg")));

        expectCode(() -> conversionService.reject(id, audit(id)), 41063);

        InventoryConversionAuditForm withOpinion = audit(id);
        withOpinion.setAuditOpinion("折算率与供应商确认的不一致");
        conversionService.reject(id, withOpinion);

        assertThat(statusOf(id)).isEqualTo("REJECTED");
        assertThat(balanceRow(wh, source).getQuantity()).isEqualByComparingTo("5.0000");
        assertThat(balanceRow(wh, target)).isNull();
        expectCode(() -> conversionService.approve(id, audit(id)), 41054);
    }

    @Test
    @DisplayName("Q7：规格转换的两条流水同样不可改删")
    void conversionMovementsAreStillAppendOnly() {
        Long wh = seedWarehouseId();
        Long source = stockedWithUnit("cv10", "5.0000", "kg");
        Long target = newSkuOfType("cv10t", "NON_STANDARD", "ON_SHELF");

        Long id = conversionService.create(form("SPLIT", wh,
                item(source, "1.0000", "kg", target, "10.0000", "kg")));
        conversionService.approve(id, audit(id));

        for (Long movementId : List.of(
                ((Number) convertMovements(wh, source).getFirst().get("id")).longValue(),
                ((Number) convertMovements(wh, target).getFirst().get("id")).longValue())) {
            expectSqlFailure("UPDATE inventory_movement SET deleted = TRUE WHERE id = ?", movementId);
            expectSqlFailure("DELETE FROM inventory_movement WHERE id = ?", movementId);
        }
    }
}
