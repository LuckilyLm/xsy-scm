package net.lab1024.sa.admin.module.scm.purchase.manager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 仓库确认入库前置判定（B1，HD-B1-03）。
 *
 * <p>把状态迁移矩阵钉住：只有「已确认 + WAREHOUSE_CONFIRM + 待入库」三者同时成立才放行，
 * 其余任何组合（草稿、DIRECT、已入库、空值）一律拒绝。
 */
@DisplayName("入库确认前置判定（单元）")
class PurchaseReceiptPutawayGuardTest {

    @Test
    @DisplayName("仅 CONFIRMED + WAREHOUSE_CONFIRM + PENDING 放行")
    void onlyTheExactPreconditionIsAllowed() {
        assertThat(PurchaseReceiptPutawayGuard.putawayAllowed(
                "CONFIRMED", "WAREHOUSE_CONFIRM", "PENDING")).isTrue();
    }

    @Test
    @DisplayName("其余组合一律拒绝")
    void everyOtherCombinationIsRejected() {
        // 草稿（即使方式与状态正确）
        assertThat(PurchaseReceiptPutawayGuard.putawayAllowed(
                "DRAFT", "WAREHOUSE_CONFIRM", "PENDING")).isFalse();
        // DIRECT 模式（确认即入库，不存在二次确认）
        assertThat(PurchaseReceiptPutawayGuard.putawayAllowed(
                "CONFIRMED", "DIRECT", "PENDING")).isFalse();
        // 已入库（幂等/状态冲突，不能重复入库）
        assertThat(PurchaseReceiptPutawayGuard.putawayAllowed(
                "CONFIRMED", "WAREHOUSE_CONFIRM", "COMPLETED")).isFalse();
        assertThat(PurchaseReceiptPutawayGuard.putawayAllowed(
                "CONFIRMED", "DIRECT", "COMPLETED")).isFalse();
        // 空值（字段缺失）
        assertThat(PurchaseReceiptPutawayGuard.putawayAllowed(
                null, "WAREHOUSE_CONFIRM", "PENDING")).isFalse();
        assertThat(PurchaseReceiptPutawayGuard.putawayAllowed(
                "CONFIRMED", null, "PENDING")).isFalse();
        assertThat(PurchaseReceiptPutawayGuard.putawayAllowed(
                "CONFIRMED", "WAREHOUSE_CONFIRM", null)).isFalse();
    }
}
