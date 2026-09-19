package net.lab1024.sa.admin.module.scm.inventory.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 报损报溢单号拼接（纯函数，不需要 DB）。
 *
 * <p>锁住三个口径：前缀、日期段、**至少 6 位且超过 999999 自然扩位**（不截断、不报错）。
 * 与出库 / 盘点单号同一纪律。
 *
 * <p>另外锁住「前缀互不相同」：三类单据都带日期段，若前缀撞了，
 * 它们在列表里会长得一样，而单号是人工核对时唯一的抓手。
 */
class InventoryLossGainNumberGeneratorTest {

    @Test
    @DisplayName("单号 = LGR + yyyyMMdd + 6 位补零")
    void formatPadsToSixDigits() {
        String no = InventoryLossGainNumberGenerator.format("LGR", 1L);
        assertThat(no).startsWith("LGR");
        assertThat(no).hasSize("LGR".length() + 8 + 6);
        assertThat(no).endsWith("000001");
    }

    @Test
    @DisplayName("超过 999999 自然扩位，不截断")
    void formatExpandsBeyondSixDigits() {
        String no = InventoryLossGainNumberGenerator.format("LGR", 1_000_000L);
        assertThat(no).endsWith("1000000");
        assertThat(no).hasSize("LGR".length() + 8 + 7);
    }

    @Test
    @DisplayName("前缀可配置，便于未来复用到其他单据")
    void formatHonoursPrefix() {
        assertThat(InventoryLossGainNumberGenerator.format("XX", 42L)).contains("000042");
    }

    @Test
    @DisplayName("单据号前缀在出库 / 盘点 / 报损报溢之间互不相同")
    void prefixDoesNotCollideWithOtherDocuments() {
        assertThat(InventoryLossGainNumberGenerator.PREFIX)
                .isEqualTo("LGR")
                .isNotEqualTo(InventoryOutboundNumberGenerator.PREFIX)
                .isNotEqualTo(InventoryStocktakeNumberGenerator.PREFIX);
    }
}
