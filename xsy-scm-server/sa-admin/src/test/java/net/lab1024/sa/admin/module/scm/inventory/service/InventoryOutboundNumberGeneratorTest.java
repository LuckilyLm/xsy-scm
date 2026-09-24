package net.lab1024.sa.admin.module.scm.inventory.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 出库单号前缀。
 *
 * <p>拼接规则（补零 / 扩位 / 前缀）已收敛到 {@code ScmDocumentNumbers} 并在
 * {@code ScmDocumentNumbersTest} 统一断言，这里只留本单据独有的前缀事实：
 * 其余三个库存单据的测试各自钉住了自己的前缀字面值并与出库比不等，
 * 因此出库自身的字面值也必须在这里钉住，否则改掉它没人报错。
 */
@DisplayName("出库单号前缀")
class InventoryOutboundNumberGeneratorTest {

    @Test
    @DisplayName("前缀恒为 OUT，且不与其余库存单据撞车")
    void prefixIsOutAndDistinct() {
        assertThat(InventoryOutboundNumberGenerator.PREFIX).isEqualTo("OUT");
        assertThat(InventoryOutboundNumberGenerator.PREFIX)
                .isNotEqualTo(InventoryStocktakeNumberGenerator.PREFIX)
                .isNotEqualTo(InventoryTransferNumberGenerator.PREFIX)
                .isNotEqualTo(InventoryLossGainNumberGenerator.PREFIX)
                .isNotEqualTo(InventoryConversionNumberGenerator.PREFIX);
    }
}
