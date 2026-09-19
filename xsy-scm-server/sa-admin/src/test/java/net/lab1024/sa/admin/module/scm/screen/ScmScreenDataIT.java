package net.lab1024.sa.admin.module.scm.screen;

import net.lab1024.sa.admin.module.scm.common.ScmW6PgITBase;
import net.lab1024.sa.admin.module.scm.screen.domain.vo.ScreenBusinessVO;
import net.lab1024.sa.admin.module.scm.screen.domain.vo.ScreenInventoryVO;
import net.lab1024.sa.admin.module.scm.screen.domain.vo.ScreenPurchaseVO;
import net.lab1024.sa.admin.module.scm.screen.service.ScreenDataService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B7 数据大屏聚合服务集成测试。
 *
 * <p>验证只读聚合接口从现有业务表（sales_order / purchase_order / inventory_balance 等）
 * 统计出的指标正确，且空库时返回零值而不是 null。
 */
@DisplayName("B7 数据大屏聚合服务（PG IT）")
class ScmScreenDataIT extends ScmW6PgITBase {

    @Autowired
    private ScreenDataService screenDataService;

    @Test
    @DisplayName("经营数据：空库返回零值，字段非 null")
    void businessDataOnEmptyDatabaseReturnsZeros() {
        ScreenBusinessVO vo = screenDataService.getBusinessData();

        assertThat(vo.getTodayOrderCount()).isZero();
        assertThat(vo.getTodayOrderedAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(vo.getTodaySettlementAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(vo.getTotalOrderCount()).isZero();
        assertThat(vo.getTotalSettlementAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(vo.getCustomerCount()).isZero();
        assertThat(vo.getSupplierCount()).isZero();
        assertThat(vo.getSkuCount()).isZero();
        assertThat(vo.getTopCustomers()).isEmpty();
        assertThat(vo.getTopProducts()).isEmpty();
    }

    @Test
    @DisplayName("库存数据：空库返回零值")
    void inventoryDataOnEmptyDatabaseReturnsZeros() {
        ScreenInventoryVO vo = screenDataService.getInventoryData();

        assertThat(vo.getTotalQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(vo.getSkuCount()).isZero();
        assertThat(vo.getWarehouseCount()).isZero();
        assertThat(vo.getTodayInboundCount()).isZero();
        assertThat(vo.getTodayOutboundCount()).isZero();
        assertThat(vo.getWarehouseDistribution()).isEmpty();
    }

    @Test
    @DisplayName("采购数据：空库返回零值")
    void purchaseDataOnEmptyDatabaseReturnsZeros() {
        ScreenPurchaseVO vo = screenDataService.getPurchaseData();

        assertThat(vo.getTodayPurchaseOrderCount()).isZero();
        assertThat(vo.getTodayPurchaseAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(vo.getTotalPurchaseOrderCount()).isZero();
        assertThat(vo.getTotalPurchaseAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(vo.getTodayReceiptCount()).isZero();
    }
}
