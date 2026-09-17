package net.lab1024.sa.admin.module.scm.purchase;

import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseDemandAllocationEntity;
import net.lab1024.sa.admin.module.scm.purchase.manager.PurchaseOrderAllocationChangeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 采购单**分配级**差量契约测试（W5 Target Design §11.1，12 例）—— **Q13 修订的核心防线**。
 *
 * <p>allocation 身份 = {@code (purchase_order_item_id, purchase_demand_id)}。
 * A 源用 {@code Map<itemId, allocation>} 覆盖写，导致「一行多需求」在编辑时只保留最后一条（A-D23）。
 * 本类逐条锁死「N allocations 是独立集合」这件事：
 * 只改一条 / 删一条 / 保留其它条，都必须是**互不干扰的行级操作**。
 */
class PurchaseOrderAllocationChangeSetTest {

    private static int codeOf(Throwable t) {
        return ((ScmBusinessException) t).getErrorCode().getCode();
    }

    private static PurchaseDemandAllocationEntity existing(Long id, Long itemId, Long demandId, String quantity) {
        PurchaseDemandAllocationEntity row = new PurchaseDemandAllocationEntity();
        row.setId(id);
        row.setPurchaseOrderItemId(itemId);
        row.setPurchaseDemandId(demandId);
        row.setAllocatedQuantity(new BigDecimal(quantity));
        row.setVersion(3);
        row.setCreatedAt(OffsetDateTime.parse("2026-09-01T10:00:00+08:00"));
        row.setCreatedBy("creator");
        return row;
    }

    private static PurchaseDemandAllocationEntity requested(Long itemId, Long demandId, String quantity) {
        PurchaseDemandAllocationEntity row = new PurchaseDemandAllocationEntity();
        row.setPurchaseOrderItemId(itemId);
        row.setPurchaseDemandId(demandId);
        row.setAllocatedQuantity(new BigDecimal(quantity));
        return row;
    }

    // ------------------------------------------------------------------
    // Q13 主线：一行多需求
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Q13：一行承接两个需求 → 两条 allocation 都进入 inserted（绝不覆盖成一条）")
    void oneItemWithTwoDemandsKeepsBoth() {
        PurchaseOrderAllocationChangeSet diff = PurchaseOrderAllocationChangeSet.between(
                List.of(),
                List.of(requested(7L, 101L, "3.0000"), requested(7L, 102L, "5.0000")));

        assertThat(diff.inserted()).hasSize(2);
        assertThat(diff.inserted()).extracting(PurchaseDemandAllocationEntity::getPurchaseDemandId)
                .containsExactly(101L, 102L);
        assertThat(diff.updated()).isEmpty();
        assertThat(diff.removed()).isEmpty();
    }

    @Test
    @DisplayName("Q13：同一需求出现在两个采购行 → 身份含 itemId，两条都保留")
    void sameDemandOnTwoItemsAreDistinctIdentities() {
        PurchaseOrderAllocationChangeSet diff = PurchaseOrderAllocationChangeSet.between(
                List.of(),
                List.of(requested(7L, 101L, "3.0000"), requested(8L, 101L, "4.0000")));
        assertThat(diff.inserted()).hasSize(2);
    }

    @Test
    @DisplayName("Q13：只改一条 allocation → 仅该条进 updated，另一条不出现在任何列表")
    void changingOneAllocationLeavesSiblingsUntouched() {
        PurchaseDemandAllocationEntity a = existing(1L, 7L, 101L, "3.0000");
        PurchaseDemandAllocationEntity b = existing(2L, 7L, 102L, "5.0000");

        PurchaseOrderAllocationChangeSet diff = PurchaseOrderAllocationChangeSet.between(
                List.of(a, b),
                List.of(requested(7L, 101L, "3.0000"), requested(7L, 102L, "8.0000")));

        assertThat(diff.updated()).hasSize(1);
        assertThat(diff.updated().get(0).getPurchaseDemandId()).isEqualTo(102L);
        assertThat(diff.updated().get(0).getAllocatedQuantity()).isEqualByComparingTo("8.0000");
        assertThat(diff.inserted()).isEmpty();
        assertThat(diff.removed()).isEmpty();
    }

    @Test
    @DisplayName("Q13：删一条 allocation → 仅该条进 removed，兄弟条保留")
    void deletingOneAllocationKeepsSiblings() {
        PurchaseDemandAllocationEntity a = existing(1L, 7L, 101L, "3.0000");
        PurchaseDemandAllocationEntity b = existing(2L, 7L, 102L, "5.0000");

        PurchaseOrderAllocationChangeSet diff = PurchaseOrderAllocationChangeSet.between(
                List.of(a, b), List.of(requested(7L, 101L, "3.0000")));

        assertThat(diff.removed()).containsExactly(b);
        assertThat(diff.updated()).isEmpty();
        assertThat(diff.inserted()).isEmpty();
    }

    @Test
    @DisplayName("Q13：数量未变 → 不进任何列表（避免无意义写与版本膨胀）")
    void unchangedQuantityIsNoOp() {
        PurchaseDemandAllocationEntity a = existing(1L, 7L, 101L, "3.0000");
        PurchaseOrderAllocationChangeSet diff = PurchaseOrderAllocationChangeSet.between(
                List.of(a), List.of(requested(7L, 101L, "3.0000")));
        assertThat(diff.inserted()).isEmpty();
        assertThat(diff.updated()).isEmpty();
        assertThat(diff.removed()).isEmpty();
    }

    @Test
    @DisplayName("Q13：请求内同一 (item, demand) 出现两次 → 40090")
    void duplicateIdentityInRequestRejected() {
        assertThatThrownBy(() -> PurchaseOrderAllocationChangeSet.between(
                List.of(),
                List.of(requested(7L, 101L, "3.0000"), requested(7L, 101L, "5.0000"))))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40090));
    }

    @Test
    @DisplayName("Q13：同一 (item, demand) 的两条旧行视为同一身份（不会双删）")
    void duplicateIdentityInExistingCollapses() {
        // 旧数据理论上被唯一索引挡住，但算法层不能因此错乱
        PurchaseOrderAllocationChangeSet diff = PurchaseOrderAllocationChangeSet.between(
                List.of(existing(1L, 7L, 101L, "3.0000"), existing(2L, 7L, 101L, "3.0000")),
                List.of(requested(7L, 101L, "3.0000")));
        assertThat(diff.inserted()).isEmpty();
        assertThat(diff.updated()).isEmpty();
        assertThat(diff.removed()).isEmpty();
    }

    @Test
    @DisplayName("Q13：新增第三条 allocation 到已有两行上 → 只 inserted 一条")
    void appendingThirdAllocation() {
        PurchaseOrderAllocationChangeSet diff = PurchaseOrderAllocationChangeSet.between(
                List.of(existing(1L, 7L, 101L, "3.0000"), existing(2L, 7L, 102L, "5.0000")),
                List.of(requested(7L, 101L, "3.0000"), requested(7L, 102L, "5.0000"), requested(7L, 103L, "2.0000")));
        assertThat(diff.inserted()).hasSize(1);
        assertThat(diff.inserted().get(0).getPurchaseDemandId()).isEqualTo(103L);
        assertThat(diff.updated()).isEmpty();
        assertThat(diff.removed()).isEmpty();
    }

    // ------------------------------------------------------------------
    // §7.8 C 段：需求侧重算必须覆盖「旧 ∪ 新」
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Q13：involvedDemandIds 返回旧 ∪ 新（被删空的 demand 也要重算，否则状态不回落）")
    void involvedDemandIdsIsUnionOfOldAndNew() {
        PurchaseDemandAllocationEntity a = existing(1L, 7L, 101L, "3.0000");
        PurchaseDemandAllocationEntity b = existing(2L, 7L, 102L, "5.0000");

        PurchaseOrderAllocationChangeSet diff = PurchaseOrderAllocationChangeSet.between(
                List.of(a, b), List.of(requested(7L, 103L, "1.0000")));

        // 101 / 102 被删空 → 必须重算（allocated 归零 → status 回落 PENDING）
        assertThat(diff.involvedDemandIds(List.of(a, b)))
                .containsExactlyInAnyOrder(101L, 102L, 103L);
    }

    @Test
    @DisplayName("Q13：involvedDemandIds 去重")
    void involvedDemandIdsDeduplicates() {
        PurchaseDemandAllocationEntity a = existing(1L, 7L, 101L, "3.0000");
        PurchaseDemandAllocationEntity b = existing(2L, 8L, 101L, "4.0000");

        PurchaseOrderAllocationChangeSet diff = PurchaseOrderAllocationChangeSet.between(
                List.of(a, b), List.of(requested(7L, 101L, "3.0000"), requested(8L, 101L, "4.0000")));

        assertThat(diff.involvedDemandIds(List.of(a, b))).containsExactly(101L);
    }

    @Test
    @DisplayName("更新行继承旧行的 id / version / 创建审计（分配是修改不是新建）")
    void updatedRowInheritsIdentityAndAudit() {
        PurchaseDemandAllocationEntity a = existing(1L, 7L, 101L, "3.0000");
        PurchaseOrderAllocationChangeSet diff = PurchaseOrderAllocationChangeSet.between(
                List.of(a), List.of(requested(7L, 101L, "9.0000")));
        assertThat(diff.updated()).hasSize(1);
        assertThat(diff.updated().get(0).getId()).isEqualTo(1L);
        assertThat(diff.updated().get(0).getCreatedAt()).isEqualTo(a.getCreatedAt());
        assertThat(diff.updated().get(0).getCreatedBy()).isEqualTo("creator");
        // version 必须来自库中值：请求只带 demandVersion，不带 allocation 自己的版本。
        // 漏继承会让 updateQuantity 的 `WHERE version = ?` 拿到 null → 0 行 → 并发写被误判为冲突。
        assertThat(diff.updated().get(0).getVersion()).isEqualTo(3);
    }

    @Test
    @DisplayName("分配集合可被整单清空：空请求 → 全部 removed（allocated 随之回落）")
    void emptyRequestClearsAllAllocations() {
        PurchaseDemandAllocationEntity a = existing(1L, 7L, 101L, "3.0000");
        PurchaseDemandAllocationEntity b = existing(2L, 7L, 102L, "5.0000");

        PurchaseOrderAllocationChangeSet diff =
                PurchaseOrderAllocationChangeSet.between(List.of(a, b), List.of());

        assertThat(diff.removed()).containsExactlyInAnyOrder(a, b);
        assertThat(diff.inserted()).isEmpty();
        assertThat(diff.updated()).isEmpty();
        assertThat(diff.involvedDemandIds(List.of(a, b))).containsExactlyInAnyOrder(101L, 102L);
    }
}
