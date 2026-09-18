package net.lab1024.sa.admin.module.scm.purchase.service;

import net.lab1024.sa.admin.module.scm.purchase.support.PurchaseInventoryContract;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 余额加锁顺序（W6 Target Design §8.1 / §12.2）。
 *
 * <p>被测的是 {@link PurchaseReceiptService#inboundLockOrder()}。它的正确性直接决定
 * 「多 SKU 采购单并发确认会不会死锁」：两个事务若按相反的 (warehouse, sku) 顺序去锁余额，
 * 等待环就形成了，PostgreSQL 只能靠死锁检测牺牲其中一个事务。
 *
 * <p><b>为什么测试放在 {@code ...purchase.service} 子包</b>：被测方法刻意保持包私有
 * （它不是给外部用的 API，只是把一条纪律变成可断言的东西）。放在同一个包里测试，
 * 就不必为了可测性把它抬成 {@code public}。
 */
@DisplayName("W6 余额加锁顺序（单元）")
class PurchaseInboundLockOrderTest {

    private static PurchaseInventoryContract.InboundFact fact(Long warehouseId, Long skuId) {
        return new PurchaseInventoryContract.InboundFact(
                1L, 1L, skuId, warehouseId, skuId, null, null, null, null,
                "kg", BigDecimal.ONE, BigDecimal.ONE, "lock-order-probe",
                OffsetDateTime.parse("2026-01-01T00:00:00+08:00"), "W6 unit");
    }

    private static List<String> keys(List<PurchaseInventoryContract.InboundFact> facts) {
        return facts.stream().map(f -> f.warehouseId() + "/" + f.skuId()).toList();
    }

    @Test
    @DisplayName("按 (warehouseId, skuId) 升序：仓库优先，同仓库内按 SKU 升序")
    void ordersByWarehouseThenSku() {
        List<PurchaseInventoryContract.InboundFact> facts = new ArrayList<>(List.of(
                fact(2L, 1L),
                fact(1L, 9L),
                fact(1L, 3L),
                fact(2L, 2L),
                fact(1L, 1L)));

        facts.sort(PurchaseReceiptService.inboundLockOrder());

        assertThat(keys(facts)).containsExactly("1/1", "1/3", "1/9", "2/1", "2/2");
    }

    @Test
    @DisplayName("方向是升序：与 reversed() 的结果恰好相反（防止有人把方向写反）")
    void directionIsAscending() {
        List<PurchaseInventoryContract.InboundFact> ascending = new ArrayList<>(List.of(
                fact(1L, 1L), fact(1L, 2L), fact(2L, 1L)));
        List<PurchaseInventoryContract.InboundFact> descending = new ArrayList<>(ascending);

        descending.sort(PurchaseReceiptService.inboundLockOrder().reversed());

        assertThat(keys(descending)).containsExactly("2/1", "1/2", "1/1");
        assertThat(keys(descending)).isEqualTo(keys(ascending).reversed());
    }

    @Test
    @DisplayName("已升序的输入保持不变；单元素与空集合不抛错")
    void isIdempotentOnSortedInputAndSafeOnDegenerateInput() {
        List<PurchaseInventoryContract.InboundFact> sorted = new ArrayList<>(List.of(
                fact(1L, 1L), fact(1L, 2L), fact(3L, 7L)));
        List<String> before = keys(sorted);

        sorted.sort(PurchaseReceiptService.inboundLockOrder());
        assertThat(keys(sorted)).isEqualTo(before);

        List<PurchaseInventoryContract.InboundFact> single = new ArrayList<>(List.of(fact(5L, 5L)));
        single.sort(PurchaseReceiptService.inboundLockOrder());
        assertThat(single).hasSize(1);

        List<PurchaseInventoryContract.InboundFact> empty = new ArrayList<>();
        empty.sort(PurchaseReceiptService.inboundLockOrder());
        assertThat(empty).isEmpty();
    }
}
