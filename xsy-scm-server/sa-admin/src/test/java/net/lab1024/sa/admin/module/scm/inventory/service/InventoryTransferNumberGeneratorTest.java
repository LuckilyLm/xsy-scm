package net.lab1024.sa.admin.module.scm.inventory.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 调拨单号拼接（纯函数，不需要 DB）。
 *
 * <p>锁住三个口径：前缀、日期段、**至少 6 位且超过 999999 自然扩位**（不截断、不报错）。
 *
 * <p>另外锁住「四类单据前缀互不相同」：出库 / 盘点 / 报损报溢 / 调拨都带日期段，
 * 若前缀撞了，它们在列表里会长得一样，而单号是人工核对时唯一的抓手。
 */
class InventoryTransferNumberGeneratorTest {

    // 拼接规则本身（补零 / 扩位 / 前缀）已在 ScmDocumentNumbersTest 统一断言；
    // 这里只留每个单据各自的前缀防撞检查。

    @Test
    @DisplayName("单据号前缀在出库 / 盘点 / 报损报溢 / 调拨之间互不相同")
    void prefixDoesNotCollideWithOtherDocuments() {
        assertThat(InventoryTransferNumberGenerator.PREFIX)
                .isEqualTo("TRF")
                .isNotEqualTo(InventoryOutboundNumberGenerator.PREFIX)
                .isNotEqualTo(InventoryStocktakeNumberGenerator.PREFIX)
                .isNotEqualTo(InventoryLossGainNumberGenerator.PREFIX);
    }
}
