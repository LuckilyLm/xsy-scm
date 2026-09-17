package net.lab1024.sa.admin.module.scm.purchase;

import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseDemandEntity;
import net.lab1024.sa.admin.module.scm.purchase.manager.PurchaseDemandAllocator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 采购需求分配规则契约测试（W5 Target Design §11.1，12 例）。
 *
 * <p>**Q17 是本类存在的主要理由**：需求单位（销售单位快照）与采购单位
 * （{@code supplier_sku.purchase_unit} 快照）是两个独立事实，不一致时必须
 * 拒绝自动分配（40971），**不允许**把「100 kg」仅替换单位字符串变成「100 箱」。
 */
class PurchaseDemandAllocatorTest {

    private static int codeOf(Throwable t) {
        return ((ScmBusinessException) t).getErrorCode().getCode();
    }

    private static PurchaseDemandEntity demand(Long supplierId, Long warehouseId, String allocated) {
        PurchaseDemandEntity entity = new PurchaseDemandEntity();
        entity.setSupplierId(supplierId);
        entity.setWarehouseId(warehouseId);
        entity.setRequiredQuantity(new BigDecimal("10.0000"));
        entity.setAllocatedQuantity(allocated == null ? null : new BigDecimal(allocated));
        return entity;
    }

    // ------------------------------------------------------------------
    // Q17：单位一致性
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Q17：单位一致（字符串相等）→ 允许自动分配")
    void unitCompatibleWhenEqual() {
        assertThatCode(() -> PurchaseDemandAllocator.unitCompatible("kg", "kg")).doesNotThrowAnyException();
        assertThatCode(() -> PurchaseDemandAllocator.unitCompatible("箱", "箱")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Q17：单位不一致 → 40971（拒绝自动分配，不做静默换算）")
    void unitMismatchRejected() {
        assertThatThrownBy(() -> PurchaseDemandAllocator.unitCompatible("kg", "箱"))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40971));
    }

    @Test
    @DisplayName("Q17：单位缺失 → 40971（不做「空即相等」的静默兜底）")
    void missingUnitRejected() {
        assertThatThrownBy(() -> PurchaseDemandAllocator.unitCompatible(null, "kg"))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40971));
        assertThatThrownBy(() -> PurchaseDemandAllocator.unitCompatible("kg", null))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40971));
        assertThatThrownBy(() -> PurchaseDemandAllocator.unitCompatible(null, null))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40971));
    }

    @Test
    @DisplayName("Q17：大小写敏感 —— kg 与 KG 视为不一致（单位不做归一化，避免掩盖真实差异）")
    void unitComparisonIsCaseSensitive() {
        assertThatThrownBy(() -> PurchaseDemandAllocator.unitCompatible("kg", "KG"))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40971));
    }

    // ------------------------------------------------------------------
    // 行 / 需求归属
    // ------------------------------------------------------------------

    @Test
    @DisplayName("采购行必须与需求同 SKU：不同 → 40995；skuId 缺失 → 40995")
    void itemMustMatchDemandSku() {
        assertThatCode(() -> PurchaseDemandAllocator.itemMatchesDemand(100L, 100L)).doesNotThrowAnyException();
        assertThatThrownBy(() -> PurchaseDemandAllocator.itemMatchesDemand(100L, 200L))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40995));
        assertThatThrownBy(() -> PurchaseDemandAllocator.itemMatchesDemand(null, 200L))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40995));
    }

    @Test
    @DisplayName("需求状态白名单：PENDING / PARTIALLY_ALLOCATED / ALLOCATED 都允许（支持补分配）")
    void assignableWhitelist() {
        assertThatCode(() -> PurchaseDemandAllocator.assignable("PENDING")).doesNotThrowAnyException();
        assertThatCode(() -> PurchaseDemandAllocator.assignable("PARTIALLY_ALLOCATED")).doesNotThrowAnyException();
        assertThatCode(() -> PurchaseDemandAllocator.assignable("ALLOCATED")).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"CLOSED", "draft", "PENDING "})
    @DisplayName("未知 / 大小写不符的需求状态 → 40981")
    void unknownDemandStatusRejected(String status) {
        assertThatThrownBy(() -> PurchaseDemandAllocator.assignable(status))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40981));
    }

    // ------------------------------------------------------------------
    // 需求版本（乐观锁）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("需求版本必填（40091）且必须相等（40972）")
    void demandVersionRules() {
        assertThatCode(() -> PurchaseDemandAllocator.demandVersion(3, 3)).doesNotThrowAnyException();
        assertThatThrownBy(() -> PurchaseDemandAllocator.demandVersion(null, 3))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40091));
        assertThatThrownBy(() -> PurchaseDemandAllocator.demandVersion(2, 3))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40972));
    }

    // ------------------------------------------------------------------
    // (supplier, warehouse) 一致性
    // ------------------------------------------------------------------

    @Test
    @DisplayName("仓库在 generate 时已固定：与采购单仓库不等 → 40981")
    void warehouseMismatchRejected() {
        assertThatThrownBy(() -> PurchaseDemandAllocator.assignmentCompatible(9L, 2L, demand(null, 1L, "0.0000")))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40981));
        assertThatCode(() -> PurchaseDemandAllocator.assignmentCompatible(9L, 1L, demand(null, 1L, "0.0000")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("供应商由首次分配固定：已分配过再换供应商 → 40981；首次分配可自由选择")
    void supplierFixedOnFirstAllocation() {
        // 已分配过 3.0000 且供应商为 7 → 不允许改成 9
        assertThatThrownBy(() -> PurchaseDemandAllocator.assignmentCompatible(9L, 1L, demand(7L, 1L, "3.0000")))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40981));
        // 同一供应商继续分配 → 允许
        assertThatCode(() -> PurchaseDemandAllocator.assignmentCompatible(7L, 1L, demand(7L, 1L, "3.0000")))
                .doesNotThrowAnyException();
        // 尚未分配（allocated == 0）→ 允许换成 9
        assertThatCode(() -> PurchaseDemandAllocator.assignmentCompatible(9L, 1L, demand(7L, 1L, "0.0000")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("首次分配判定与供应商落库判定")
    void firstAllocationDetection() {
        assertThat(PurchaseDemandAllocator.isFirstAllocation(demand(null, 1L, null))).isTrue();
        assertThat(PurchaseDemandAllocator.isFirstAllocation(demand(null, 1L, "0.0000"))).isTrue();
        assertThat(PurchaseDemandAllocator.isFirstAllocation(demand(null, 1L, "0.0001"))).isFalse();

        assertThat(PurchaseDemandAllocator.shouldFixSupplier(demand(null, 1L, "0.0000"))).isTrue();
        assertThat(PurchaseDemandAllocator.shouldFixSupplier(demand(7L, 1L, "0.0000"))).isFalse();
        assertThat(PurchaseDemandAllocator.shouldFixSupplier(demand(null, 1L, "3.0000"))).isFalse();
    }

    // ------------------------------------------------------------------
    // 合计不超需求 + 状态回落
    // ------------------------------------------------------------------

    @Test
    @DisplayName("分配合计必须在 [0, required]：超需求 / 负数 / 缺失 → 40082")
    void allocatedMustStayWithinRequired() {
        assertThatCode(() -> PurchaseDemandAllocator.withinRequired(new BigDecimal("10.0000"), new BigDecimal("10.0000")))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> PurchaseDemandAllocator.withinRequired(new BigDecimal("10.0000"), new BigDecimal("10.0001")))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40082));
        assertThatThrownBy(() -> PurchaseDemandAllocator.withinRequired(new BigDecimal("10.0000"), new BigDecimal("-1.0000")))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40082));
        assertThatThrownBy(() -> PurchaseDemandAllocator.withinRequired(new BigDecimal("10.0000"), null))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40082));
    }

    @Test
    @DisplayName("状态推导必须能回落：allocated 归零 → PENDING（否则删分配后状态卡在 ALLOCATED）")
    void statusDerivationFallsBack() {
        BigDecimal required = new BigDecimal("10.0000");
        assertThat(PurchaseDemandAllocator.statusFor(required, null)).isEqualTo("PENDING");
        assertThat(PurchaseDemandAllocator.statusFor(required, new BigDecimal("0.0000"))).isEqualTo("PENDING");
        assertThat(PurchaseDemandAllocator.statusFor(required, new BigDecimal("4.0000"))).isEqualTo("PARTIALLY_ALLOCATED");
        assertThat(PurchaseDemandAllocator.statusFor(required, new BigDecimal("10.0000"))).isEqualTo("ALLOCATED");
    }

    @Test
    @DisplayName("P12 锁序：demandId 去重后升序，避免与 order.create 路径交叉成环")
    void ascendingDemandIdsForLockOrdering() {
        List<Long> ids = PurchaseDemandAllocator.ascendingDemandIds(
                Arrays.asList(9L, 3L, 9L, 1L, null));
        assertThat(ids).containsExactly(1L, 3L, 9L);
    }
}
