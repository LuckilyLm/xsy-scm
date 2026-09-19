package net.lab1024.sa.admin.module.scm.inventory.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 盘点单号拼接（纯函数，不需要 DB）。
 *
 * <p>锁住三个口径：前缀、日期段、**至少 6 位且超过 999999 自然扩位**（不截断、不报错）。
 * 与出库单号 {@code InventoryOutboundNumberGenerator} 同一纪律。
 *
 * <p>另外锁住「前缀互不相同」：盘点单号与出库单号都带日期段，若前缀撞了，
 * 两类单据会在列表里长得一样，而单号是人工核对时唯一的抓手。
 */
class InventoryStocktakeNumberGeneratorTest {

    @Test
    @DisplayName("单号 = STK + yyyyMMdd + 6 位补零")
    void formatPadsToSixDigits() {
        String no = InventoryStocktakeNumberGenerator.format("STK", 1L);
        assertThat(no).startsWith("STK");
        assertThat(no).hasSize("STK".length() + 8 + 6);
        assertThat(no).endsWith("000001");
    }

    @Test
    @DisplayName("超过 999999 自然扩位，不截断")
    void formatExpandsBeyondSixDigits() {
        String no = InventoryStocktakeNumberGenerator.format("STK", 1_000_000L);
        assertThat(no).endsWith("1000000");
        assertThat(no).hasSize("STK".length() + 8 + 7);
    }

    @Test
    @DisplayName("前缀可配置，便于未来复用到其他单据")
    void formatHonoursPrefix() {
        assertThat(InventoryStocktakeNumberGenerator.format("XX", 42L)).contains("000042");
    }

    @Test
    @DisplayName("盘点单号前缀与出库单号前缀不得相同")
    void prefixDoesNotCollideWithOutbound() {
        assertThat(InventoryStocktakeNumberGenerator.PREFIX)
                .isEqualTo("STK")
                .isNotEqualTo(InventoryOutboundNumberGenerator.PREFIX);
    }
}
