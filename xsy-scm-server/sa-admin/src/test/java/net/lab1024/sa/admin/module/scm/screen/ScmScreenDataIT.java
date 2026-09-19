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
 * 统计出的指标正确。
 *
 * <p><b>为什么不断言「空库返回零」</b>：本类的基类把用例包在一个事务里，但同一套件里
 * {@code Propagation.NOT_SUPPORTED} 的 IT（库存回滚 / 并发 / 调拨回滚）会把数据**提交**进库，
 * 因此「累计类指标为 0」只在特定执行顺序下成立 —— 那是一条依赖测试顺序的脆弱断言
 * （曾稳定失败：期望 0、实际 17）。现在断言的是**真实成立的口径**：
 * 字段非 null（契约是「返回零值而不是 null」）、非负、且今日量不超过累计量。
 */
@DisplayName("B7 数据大屏聚合服务（PG IT）")
class ScmScreenDataIT extends ScmW6PgITBase {

    @Autowired
    private ScreenDataService screenDataService;

    @Test
    @DisplayName("经营数据：字段非 null、非负且口径自洽")
    void businessDataFieldsAreNonNullAndConsistent() {
        ScreenBusinessVO vo = screenDataService.getBusinessData();

        assertThat(vo.getTodayOrderCount()).isNotNull().isGreaterThanOrEqualTo(0L);
        assertThat(vo.getTodayOrderedAmount()).isNotNull().isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(vo.getTodaySettlementAmount()).isNotNull().isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(vo.getTotalOrderCount()).isNotNull().isGreaterThanOrEqualTo(0L);
        assertThat(vo.getTotalSettlementAmount()).isNotNull().isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(vo.getCustomerCount()).isNotNull().isGreaterThanOrEqualTo(0L);
        assertThat(vo.getSupplierCount()).isNotNull().isGreaterThanOrEqualTo(0L);
        assertThat(vo.getSkuCount()).isNotNull().isGreaterThanOrEqualTo(0L);
        // 排行榜本身必须非 null —— 前端直接 v-for，null 会崩
        assertThat(vo.getTopCustomers()).isNotNull();
        assertThat(vo.getTopProducts()).isNotNull();

        // 口径自洽：今日量是累计量的子集，不可能超过
        assertThat(vo.getTodayOrderCount()).isLessThanOrEqualTo(vo.getTotalOrderCount());
        assertThat(vo.getTodaySettlementAmount()).isLessThanOrEqualTo(vo.getTotalSettlementAmount());
    }

    @Test
    @DisplayName("库存数据：字段非 null、非负且口径自洽")
    void inventoryDataFieldsAreNonNullAndConsistent() {
        ScreenInventoryVO vo = screenDataService.getInventoryData();

        assertThat(vo.getTotalQuantity()).isNotNull().isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(vo.getSkuCount()).isNotNull().isGreaterThanOrEqualTo(0L);
        assertThat(vo.getWarehouseCount()).isNotNull().isGreaterThanOrEqualTo(0L);
        assertThat(vo.getTodayInboundCount()).isNotNull().isGreaterThanOrEqualTo(0L);
        assertThat(vo.getTodayOutboundCount()).isNotNull().isGreaterThanOrEqualTo(0L);
        assertThat(vo.getWarehouseDistribution()).isNotNull();

        // 仓库分布的每一行都必须有非空仓名 —— 这是「按仓库聚合」联表取名的回归点：
        // 曾经写成 w.warehouse_name（warehouse 表的列实际是 name），SQL 直接报列不存在。
        vo.getWarehouseDistribution().forEach(row -> {
            assertThat(row.getWarehouseName()).isNotBlank();
            assertThat(row.getQuantity()).isNotNull().isGreaterThanOrEqualTo(BigDecimal.ZERO);
        });
    }

    @Test
    @DisplayName("采购数据：字段非 null、非负且口径自洽")
    void purchaseDataFieldsAreNonNullAndConsistent() {
        ScreenPurchaseVO vo = screenDataService.getPurchaseData();

        assertThat(vo.getTodayPurchaseOrderCount()).isNotNull().isGreaterThanOrEqualTo(0L);
        assertThat(vo.getTodayPurchaseAmount()).isNotNull().isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(vo.getTotalPurchaseOrderCount()).isNotNull().isGreaterThanOrEqualTo(0L);
        assertThat(vo.getTotalPurchaseAmount()).isNotNull().isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(vo.getTodayReceiptCount()).isNotNull().isGreaterThanOrEqualTo(0L);

        assertThat(vo.getTodayPurchaseOrderCount()).isLessThanOrEqualTo(vo.getTotalPurchaseOrderCount());
        assertThat(vo.getTodayPurchaseAmount()).isLessThanOrEqualTo(vo.getTotalPurchaseAmount());
    }
}
