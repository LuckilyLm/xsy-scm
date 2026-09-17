package net.lab1024.sa.admin.module.scm.purchase;

import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseOrderItemEntity;
import net.lab1024.sa.admin.module.scm.purchase.manager.PurchaseOrderItemChangeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 采购单**行级**差量契约测试（W5 Target Design §11.1，12 例）。
 *
 * <p>行身份 = {@code (purchase_order_id, sku_id)}，由
 * {@code uk_purchase_order_item_order_sku_active} 强制（Q13 修订后**保留**）。
 * 与 allocation 身份 {@code (purchase_order_item_id, purchase_demand_id)} 是两个层次，
 * 本类只测行级；分配级见 {@code PurchaseOrderAllocationChangeSetTest}。
 */
class PurchaseOrderItemChangeSetTest {

    private static int codeOf(Throwable t) {
        return ((ScmBusinessException) t).getErrorCode().getCode();
    }

    /** 已存在的行（id 非空、带版本、带创建审计）。 */
    private static PurchaseOrderItemEntity existing(Long id, Long skuId, int version) {
        PurchaseOrderItemEntity row = new PurchaseOrderItemEntity();
        row.setId(id);
        row.setSkuId(skuId);
        row.setVersion(version);
        row.setReceivedQuantity(new BigDecimal("0.0000"));
        row.setCreatedAt(OffsetDateTime.parse("2026-09-01T10:00:00+08:00"));
        row.setCreatedBy("creator");
        return row;
    }

    /** 请求中的保留行（带 id + version）。 */
    private static PurchaseOrderItemEntity retained(Long id, Long skuId, Integer version) {
        PurchaseOrderItemEntity row = new PurchaseOrderItemEntity();
        row.setId(id);
        row.setSkuId(skuId);
        row.setVersion(version);
        return row;
    }

    /** 请求中的新增行（无 id）。 */
    private static PurchaseOrderItemEntity fresh(Long skuId) {
        PurchaseOrderItemEntity row = new PurchaseOrderItemEntity();
        row.setSkuId(skuId);
        return row;
    }

    @Test
    @DisplayName("保留 + 新增：按 id 归入 updated / inserted，互不混淆")
    void classifiesRetainedAndInserted() {
        PurchaseOrderItemEntity old = existing(7L, 100L, 2);
        PurchaseOrderItemEntity kept = retained(7L, 100L, 2);
        PurchaseOrderItemEntity added = fresh(200L);

        PurchaseOrderItemChangeSet diff =
                PurchaseOrderItemChangeSet.between(List.of(old), List.of(kept, added));

        assertThat(diff.updated()).containsExactly(kept);
        assertThat(diff.inserted()).containsExactly(added);
        assertThat(diff.removed()).isEmpty();
    }

    @Test
    @DisplayName("保留行沿用原有创建审计信息（编辑不是新建）")
    void retainedRowInheritsAudit() {
        PurchaseOrderItemEntity old = existing(7L, 100L, 2);
        PurchaseOrderItemEntity kept = retained(7L, 100L, 2);
        PurchaseOrderItemChangeSet.between(List.of(old), List.of(kept));
        assertThat(kept.getCreatedAt()).isEqualTo(old.getCreatedAt());
        assertThat(kept.getCreatedBy()).isEqualTo("creator");
    }

    @Test
    @DisplayName("旧集合中未被请求引用的行 → removed")
    void unreferencedExistingRowIsRemoved() {
        PurchaseOrderItemEntity a = existing(1L, 100L, 0);
        PurchaseOrderItemEntity b = existing(2L, 200L, 0);
        PurchaseOrderItemChangeSet diff =
                PurchaseOrderItemChangeSet.between(List.of(a, b), List.of(retained(1L, 100L, 0)));
        assertThat(diff.removed()).containsExactly(b);
        assertThat(diff.inserted()).isEmpty();
    }

    @Test
    @DisplayName("请求内同一 skuId 出现两次 → 40997（uk_purchase_order_item_order_sku_active 的前置拦截）")
    void duplicateSkuInRequestRejected() {
        assertThatThrownBy(() -> PurchaseOrderItemChangeSet.between(
                List.of(), List.of(fresh(100L), fresh(100L))))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40997));
    }

    @Test
    @DisplayName("保留行与新增行撞同一 skuId → 40997（跨 id 维度也去重）")
    void duplicateSkuAcrossRetainedAndInsertedRejected() {
        assertThatThrownBy(() -> PurchaseOrderItemChangeSet.between(
                List.of(existing(7L, 100L, 0)), List.of(retained(7L, 100L, 0), fresh(100L))))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40997));
    }

    @Test
    @DisplayName("skuId 为空 → 40997（不允许无商品的行）")
    void nullSkuRejected() {
        assertThatThrownBy(() -> PurchaseOrderItemChangeSet.between(List.of(), List.of(fresh(null))))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40997));
    }

    @Test
    @DisplayName("保留行的 id 不属于本单 → 40983（防止越单改行）")
    void foreignItemIdRejected() {
        assertThatThrownBy(() -> PurchaseOrderItemChangeSet.between(
                List.of(existing(7L, 100L, 0)), List.of(retained(99L, 100L, 0))))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40983));
    }

    @Test
    @DisplayName("保留行的 id 与 skuId 不自洽 → 40983（不允许悄悄换商品）")
    void retainedIdSkuMismatchRejected() {
        assertThatThrownBy(() -> PurchaseOrderItemChangeSet.between(
                List.of(existing(7L, 100L, 0)), List.of(retained(7L, 999L, 0))))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40983));
    }

    @Test
    @DisplayName("保留行缺 version → 40088（乐观锁必须显式携带）")
    void missingVersionRejected() {
        assertThatThrownBy(() -> PurchaseOrderItemChangeSet.between(
                List.of(existing(7L, 100L, 2)), List.of(retained(7L, 100L, null))))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40088));
    }

    @Test
    @DisplayName("保留行 version 过期 → 40984（并发编辑被拒）")
    void staleVersionRejected() {
        assertThatThrownBy(() -> PurchaseOrderItemChangeSet.between(
                List.of(existing(7L, 100L, 2)), List.of(retained(7L, 100L, 1))))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40984));
    }

    @Test
    @DisplayName("删除已收货的行 → 40982（不能抹掉已发生的收货事实）")
    void removingReceivedRowRejected() {
        PurchaseOrderItemEntity received = existing(7L, 100L, 0);
        received.setReceivedQuantity(new BigDecimal("5.0000"));
        assertThatThrownBy(() -> PurchaseOrderItemChangeSet.between(List.of(received), List.of()))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40982));
    }

    @Test
    @DisplayName("删除未收货的行 → 允许；空请求即整单清空行")
    void removingUnreceivedRowAllowed() {
        PurchaseOrderItemEntity untouched = existing(7L, 100L, 0);
        PurchaseOrderItemChangeSet diff =
                PurchaseOrderItemChangeSet.between(List.of(untouched), List.of());
        assertThat(diff.removed()).containsExactly(untouched);
        assertThat(diff.inserted()).isEmpty();
        assertThat(diff.updated()).isEmpty();
    }
}
