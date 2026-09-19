package net.lab1024.sa.admin.module.scm.inventory.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 出库单号拼接（纯函数，不需要 DB）。
 *
 * <p>锁住三个口径：前缀、日期段、**至少 6 位且超过 999999 自然扩位**（不截断、不报错）。
 * 与采购单号 {@code PurchaseNumberGenerator} 同一纪律。
 */
class InventoryOutboundNumberGeneratorTest {

    @Test
    @DisplayName("单号 = OUT + yyyyMMdd + 6 位补零")
    void formatPadsToSixDigits() {
        String no = InventoryOutboundNumberGenerator.format("OUT", 1L);
        assertThat(no).startsWith("OUT");
        assertThat(no).hasSize("OUT".length() + 8 + 6);
        assertThat(no).endsWith("000001");
    }

    @Test
    @DisplayName("超过 999999 自然扩位，不截断")
    void formatExpandsBeyondSixDigits() {
        String no = InventoryOutboundNumberGenerator.format("OUT", 1_000_000L);
        assertThat(no).endsWith("1000000");
        assertThat(no).hasSize("OUT".length() + 8 + 7);
    }

    @Test
    @DisplayName("前缀可配置，便于未来复用到其他单据")
    void formatHonoursPrefix() {
        assertThat(InventoryOutboundNumberGenerator.format("XX", 42L)).contains("000042");
    }
}
