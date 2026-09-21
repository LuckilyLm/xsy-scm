package net.lab1024.sa.admin.module.scm.inventory;

import net.lab1024.sa.admin.module.scm.common.ScmW6PgITBase;
import net.lab1024.sa.admin.module.scm.inventory.constant.ScmInventoryWarningStatusEnum;
import net.lab1024.sa.admin.module.scm.inventory.domain.ReserveInventoryFact;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryWarningQueryForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryWarningThresholdAddForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryWarningThresholdQueryForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.vo.InventoryWarningVO;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryReservationService;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryWarningQueryService;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryWarningThresholdService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 库存阈值预警的 PostgreSQL 集成测试（阈值预警波次）。
 *
 * <p>覆盖四件在单测里验证不了的事：
 * <ol>
 *   <li><b>预警列表由配置驱动</b> —— 只列配置了阈值的 (仓库, SKU)；
 *       没有配置就没有预警，否则每个 SKU × 每个仓库都会因为「没有余额行 = 0 < 下限」而刷屏；</li>
 *   <li><b>判定基准是可用量</b> —— 现有量够但已被预留时仍然要预警，
 *       这是本波次最容易被实现错的一条；</li>
 *   <li><b>没有余额行 + 设了下限 → 预警</b>（数量按 0 计），
 *       这是本能力唯一能表达「还没进过货就要补货」的方式；</li>
 *   <li><b>SQL 过滤与 Java 判定等价</b> —— 状态判定的规则在 Java 枚举里只实现一次，
 *       列表 SQL 里另有一份过滤谓词，这里交叉验证两者不会漂移。</li>
 * </ol>
 */
@DisplayName("库存阈值预警（PG IT）")
class ScmInventoryWarningIT extends ScmW6PgITBase {

    @Autowired
    private InventoryWarningThresholdService thresholdService;

    @Autowired
    private InventoryWarningQueryService warningQueryService;

    @Autowired
    private InventoryReservationService reservations;

    private Long stocked(String suffix, String quantity) {
        Long skuId = newSkuOfType(suffix, "NON_STANDARD", "ON_SHELF");
        W6Fixture fixture = inboundFixture(suffix, skuId, quantity);
        confirmReceipt(fixture.receipt().getId(), quantity);
        return skuId;
    }

    private static InventoryWarningThresholdAddForm threshold(Long warehouseId, Long skuId,
                                                              String min, String max) {
        InventoryWarningThresholdAddForm form = new InventoryWarningThresholdAddForm();
        form.setWarehouseId(warehouseId);
        form.setSkuId(skuId);
        form.setWarnMin(min == null ? null : new BigDecimal(min));
        form.setWarnMax(max == null ? null : new BigDecimal(max));
        return form;
    }

    private InventoryWarningQueryForm warningQuery(Long warehouseId) {
        InventoryWarningQueryForm form = new InventoryWarningQueryForm();
        form.setPageNum(1L);
        form.setPageSize(100L);
        form.setWarehouseId(warehouseId);
        return form;
    }

    /**
     * 当前仓库的预警行（默认只看异常）。
     */
    private List<InventoryWarningVO> warnings(Long warehouseId) {
        return warningQueryService.queryWarningPage(warningQuery(warehouseId)).getList();
    }

    private InventoryWarningVO warningOf(Long warehouseId, Long skuId) {
        return warnings(warehouseId).stream()
                .filter(row -> skuId.equals(row.getSkuId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("预警列表里没有 SKU " + skuId));
    }

    // ------------------------------------------------------------------
    // 阈值配置
    // ------------------------------------------------------------------

    @Test
    @DisplayName("阈值配置 CRUD：新建 → 查询 → 编辑（含清空下限）→ 删除")
    void thresholdCrud() {
        Long wh = seedWarehouseId();
        Long sku = newSkuOfType("wn1", "NON_STANDARD", "ON_SHELF");

        Long id = thresholdService.create(threshold(wh, sku, "10.0000", "100.0000"));

        InventoryWarningThresholdQueryForm query = new InventoryWarningThresholdQueryForm();
        query.setPageNum(1L);
        query.setPageSize(10L);
        query.setWarehouseId(wh);
        assertThat(warningQueryService.queryThresholdPage(query).getList())
                .anySatisfy(row -> {
                    assertThat(row.getId()).isEqualTo(id);
                    assertThat(row.getWarnMin()).isEqualByComparingTo("10.0000");
                    assertThat(row.getWarnMax()).isEqualByComparingTo("100.0000");
                    assertThat(row.getSkuId()).isEqualTo(sku);
                });

        // 编辑：清空下限（只留上限）。实体上的 updateStrategy=ALWAYS 保证 null 会被真正写入 ——
        // 用默认策略会静默保留旧值，那是这一条最容易踩的坑。
        thresholdService.update(id, threshold(wh, sku, null, "50.0000"));
        assertThat(warningQueryService.detail(id).getWarnMin()).isNull();
        assertThat(warningQueryService.detail(id).getWarnMax()).isEqualByComparingTo("50.0000");

        thresholdService.delete(id);
        expectCode(() -> warningQueryService.detail(id), 41049);
    }

    @Test
    @DisplayName("同一 (仓库, SKU) 重复配置被拒（41050）—— 两条配置会让「按哪条判断」没有答案")
    void duplicateThresholdIsRejected() {
        Long wh = seedWarehouseId();
        Long sku = newSkuOfType("wn2", "NON_STANDARD", "ON_SHELF");

        thresholdService.create(threshold(wh, sku, "10.0000", null));
        expectCode(() -> thresholdService.create(threshold(wh, sku, "20.0000", null)), 41050);
    }

    @Test
    @DisplayName("阈值区间非法被拒（41051）：都没有 / 为负 / 下限大于上限")
    void invalidThresholdRangeIsRejected() {
        Long wh = seedWarehouseId();
        Long sku = newSkuOfType("wn3", "NON_STANDARD", "ON_SHELF");

        // 上下限都没有 → 没有任何判断依据
        expectCode(() -> thresholdService.create(threshold(wh, sku, null, null)), 41051);
        // 下限为负
        expectCode(() -> thresholdService.create(threshold(wh, sku, "-1", null)), 41051);
        // 上限为负
        expectCode(() -> thresholdService.create(threshold(wh, sku, null, "-1")), 41051);
        // 下限大于上限 → 所有状态都会异常，预警失去意义
        expectCode(() -> thresholdService.create(threshold(wh, sku, "10", "5")), 41051);
    }

    @Test
    @DisplayName("SKU 不存在被拒（41052）—— 配置表长期驻留，指向不存在 SKU 会成为永久噪声")
    void unknownSkuIsRejected() {
        Long wh = seedWarehouseId();
        expectCode(() -> thresholdService.create(
                threshold(wh, 9_999_999_999L, "10.0000", null)), 41052);
    }

    // ------------------------------------------------------------------
    // 预警列表
    // ------------------------------------------------------------------

    @Test
    @DisplayName("预警列表由配置驱动：没有配置阈值的 (仓库, SKU) 不出现")
    void warningListIsDrivenByConfiguration() {
        Long wh = seedWarehouseId();
        Long sku = stocked("wn4", "10.0000");

        // 有库存但没有阈值配置 → 不出现在预警列表
        assertThat(warnings(wh)).noneMatch(row -> sku.equals(row.getSkuId()));

        thresholdService.create(threshold(wh, sku, "5.0000", null));
        // 配置后 10 >= 5 → 正常项，默认（只看异常）仍然不出现
        assertThat(warnings(wh)).noneMatch(row -> sku.equals(row.getSkuId()));
        // 显式看正常项才出现
        InventoryWarningQueryForm normal = warningQuery(wh);
        normal.setStatus("NORMAL");
        assertThat(warningQueryService.queryWarningPage(normal).getList())
                .anyMatch(row -> sku.equals(row.getSkuId()));

        // 把下限抬到 20 → 10 < 20 → 触发 LOW
        InventoryWarningThresholdQueryForm q = new InventoryWarningThresholdQueryForm();
        q.setPageNum(1L);
        q.setPageSize(10L);
        q.setSkuId(sku);
        Long thresholdId = warningQueryService.queryThresholdPage(q).getList().getFirst().getId();
        thresholdService.update(thresholdId, threshold(wh, sku, "20.0000", null));

        InventoryWarningVO row = warningOf(wh, sku);
        assertThat(row.getStatus()).isEqualTo("LOW");
        assertThat(row.getQuantity()).isEqualByComparingTo("10.0000");
        assertThat(row.getAvailableQuantity()).isEqualByComparingTo("10.0000");
        assertThat(row.getWarnMin()).isEqualByComparingTo("20.0000");

        // 删除配置后不再预警
        thresholdService.delete(thresholdId);
        assertThat(warnings(wh)).noneMatch(r -> sku.equals(r.getSkuId()));
    }

    @Test
    @DisplayName("HIGH：可用量高于上限触发积压预警")
    void highWarningIsTriggeredByAvailableAboveMax() {
        Long wh = seedWarehouseId();
        Long sku = stocked("wn5", "100.0000");
        thresholdService.create(threshold(wh, sku, null, "50.0000"));

        InventoryWarningVO row = warningOf(wh, sku);
        assertThat(row.getStatus()).isEqualTo("HIGH");
        assertThat(row.getStatusDesc()).isEqualTo("高于上限");
    }

    @Test
    @DisplayName("判定基准是**可用量**：现有量够但已被预留时仍然预警")
    void warningBasisIsAvailableQuantityNotOnHand() {
        Long wh = seedWarehouseId();
        Long sku = stocked("wn6", "20.0000");

        // 下限 10：现有量 20 明显够
        thresholdService.create(threshold(wh, sku, "10.0000", null));
        assertThat(warnings(wh)).as("现有量 20 >= 下限 10 → 不预警")
                .noneMatch(row -> sku.equals(row.getSkuId()));

        // 但把 18 预留出去之后，可用量只剩 2 → 必须预警。
        // 这正是「下限的业务含义是还够不够发货」：货已经被订走就不算有货。
        reservations.reserve(new ReserveInventoryFact(
                wh, sku, "SALES_ORDER_ITEM", 760001L, 860001L,
                new BigDecimal("18.0000"), OffsetDateTime.now(), null));

        InventoryWarningVO row = warningOf(wh, sku);
        assertThat(row.getStatus()).as("可用量 2 < 下限 10 → LOW").isEqualTo("LOW");
        assertThat(row.getQuantity()).as("现有量仍然够").isEqualByComparingTo("20.0000");
        assertThat(row.getReservedQuantity()).isEqualByComparingTo("18.0000");
        assertThat(row.getAvailableQuantity()).isEqualByComparingTo("2.0000");
    }

    @Test
    @DisplayName("边界取等号算正常；没有余额行时数量按 0 计并触发下限预警")
    void boundaryAndMissingBalance() {
        Long wh = seedWarehouseId();
        Long stockedSku = stocked("wn7", "10.0000");
        Long emptySku = newSkuOfType("wn8", "NON_STANDARD", "ON_SHELF");

        // 恰好等于下限 → 正常（「不低于下限」）
        thresholdService.create(threshold(wh, stockedSku, "10.0000", null));
        assertThat(warnings(wh)).noneMatch(row -> stockedSku.equals(row.getSkuId()));

        // 没有余额行（从未入库）+ 设了下限 1 → 数量按 0 计 → LOW。
        // 这是本能力唯一能表达「还没进过货就要补货」的方式。
        assertThat(balanceRow(wh, emptySku)).isNull();
        thresholdService.create(threshold(wh, emptySku, "1.0000", null));

        InventoryWarningVO row = warningOf(wh, emptySku);
        assertThat(row.getStatus()).isEqualTo("LOW");
        assertThat(row.getQuantity()).isEqualByComparingTo("0.0000");
        assertThat(row.getAvailableQuantity()).isEqualByComparingTo("0.0000");
        assertThat(row.getUnit()).as("没有余额行时没有记账单位").isNull();
    }

    @Test
    @DisplayName("SQL 过滤与 Java 判定等价：按 status 查出来的行，服务层算出的状态必须一致")
    void sqlFilterAgreesWithJavaClassification() {
        Long wh = seedWarehouseId();
        Long lowSku = stocked("wn9", "1.0000");
        Long highSku = stocked("wn10", "500.0000");
        Long normalSku = stocked("wn11", "50.0000");

        thresholdService.create(threshold(wh, lowSku, "10.0000", null));   // 1 < 10 → LOW
        thresholdService.create(threshold(wh, highSku, null, "100.0000")); // 500 > 100 → HIGH
        thresholdService.create(threshold(wh, normalSku, "10.0000", "100.0000")); // 正常

        // 状态判定的规则在 Java 枚举里只实现一次；列表 SQL 里另有一份**过滤**谓词。
        // 逐个状态查一遍，断言「SQL 筛出来的行」与「Java 算出来的状态」完全一致 ——
        // 两份实现一旦漂移，这里会立刻失败。
        for (String status : List.of("LOW", "HIGH", "NORMAL")) {
            InventoryWarningQueryForm form = warningQuery(wh);
            form.setStatus(status);
            List<InventoryWarningVO> rows = warningQueryService.queryWarningPage(form).getList();
            assertThat(rows).as("status=%s 至少应有一行", status).isNotEmpty();
            for (InventoryWarningVO row : rows) {
                assertThat(row.getStatus()).as("status=%s 的行 %s", status, row.getSkuId())
                        .isEqualTo(status);
                // 再用枚举独立算一次，确认服务层填的值本身就是对的
                assertThat(ScmInventoryWarningStatusEnum.evaluate(
                        row.getAvailableQuantity(), row.getWarnMin(), row.getWarnMax()).name())
                        .isEqualTo(status);
            }
        }

        // 为空 → 只看异常：正常项不得出现
        List<InventoryWarningVO> abnormal = warnings(wh);
        assertThat(abnormal).noneMatch(row -> "NORMAL".equals(row.getStatus()));
        assertThat(abnormal).anyMatch(row -> lowSku.equals(row.getSkuId()));
        assertThat(abnormal).anyMatch(row -> highSku.equals(row.getSkuId()));
        assertThat(abnormal).noneMatch(row -> normalSku.equals(row.getSkuId()));
    }
}
