package net.lab1024.sa.admin.module.scm.screen;

import net.lab1024.sa.admin.module.scm.common.ScmW6PgITBase;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryWarningQueryForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.vo.InventoryWarningVO;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryWarningQueryService;
import net.lab1024.sa.admin.module.scm.screen.domain.vo.ScreenBusinessVO;
import net.lab1024.sa.admin.module.scm.screen.domain.vo.ScreenInventoryVO;
import net.lab1024.sa.admin.module.scm.screen.domain.vo.ScreenPurchaseVO;
import net.lab1024.sa.admin.module.scm.screen.domain.vo.ScreenTrendVO;
import net.lab1024.sa.admin.module.scm.screen.service.ScreenDataService;
import net.lab1024.sa.base.common.domain.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

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

    @Autowired
    private InventoryWarningQueryService inventoryWarningQueryService;

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
        assertThat(vo.getTodayCustomerCount()).isNotNull().isGreaterThanOrEqualTo(0L);
        assertThat(vo.getTodaySupplierCount()).isNotNull().isGreaterThanOrEqualTo(0L);
        // 排行榜本身必须非 null —— 前端直接 v-for，null 会崩
        assertThat(vo.getTopCustomers()).isNotNull();
        assertThat(vo.getTopProducts()).isNotNull();

        // 口径自洽：今日量是累计量的子集，不可能超过
        assertThat(vo.getTodayOrderCount()).isLessThanOrEqualTo(vo.getTotalOrderCount());
        assertThat(vo.getTodaySettlementAmount()).isLessThanOrEqualTo(vo.getTotalSettlementAmount());
        // 「今日成交客户」是客户总表的子集
        assertThat(vo.getTodayCustomerCount()).isLessThanOrEqualTo(vo.getCustomerCount());
        assertThat(vo.getTodaySupplierCount()).isLessThanOrEqualTo(vo.getSupplierCount());
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

    @Test
    @DisplayName("库存健康度：四档互斥且之和等于总数，并与预警列表交叉验证")
    void inventoryHealthMatchesWarningList() {
        ScreenInventoryVO vo = screenDataService.getInventoryData();
        ScreenInventoryVO.InventoryHealth health = vo.getHealth();
        assertThat(health).isNotNull();
        assertThat(health.getTotalSkuCount()).isNotNull().isGreaterThanOrEqualTo(0L);
        assertThat(health.getNormalCount()).isNotNull().isGreaterThanOrEqualTo(0L);
        assertThat(health.getLowCount()).isNotNull().isGreaterThanOrEqualTo(0L);
        assertThat(health.getHighCount()).isNotNull().isGreaterThanOrEqualTo(0L);
        assertThat(health.getUnconfiguredCount()).isNotNull().isGreaterThanOrEqualTo(0L);
        assertThat(health.getOutOfStockCount()).isNotNull().isGreaterThanOrEqualTo(0L);

        // 四档互斥且**之和恰好等于总数**（设计稿把缺货画成与预警并列的一段，占比要凑满 100%）。
        // 这是分档实现的核心不变量：一旦有人把缺货改回「与三档重叠的子集」，
        // 占比之和会超过 100%，而界面上看不出任何异常。
        long sum = health.getNormalCount() + health.getLowCount() + health.getHighCount()
                + health.getOutOfStockCount() + health.getUnconfiguredCount();
        assertThat(sum)
                .as("正常+预警+积压+缺货+未配置 应等于参与评估的总数")
                .isEqualTo(health.getTotalSkuCount());

        // 交叉验证：大屏健康度与预警列表是同一条业务规则的两条实现路径
        // （Java 枚举 vs 列表 SQL 谓词）—— 一旦漂移，两边会对不上。
        //
        // 注意两边**口径并不完全重合**，所以只能断言夹逼而不是相等：
        //   预警列表 FROM inventory_warning_threshold，只包含「配了阈值」的 (仓库,SKU)；
        //   健康度还包含「没配阈值但可用量 ≤ 0」的余额行（这些行列表里根本没有）。
        // 于是：
        //   下界 low+high —— 每个低于下限/高于上限的档都配了阈值且 evaluate ≠ NORMAL，必然在列表里；
        //   上界 low+high+outOfStock —— 列表里的行要么可用量 > 0（落在 low/high），
        //                              要么可用量 ≤ 0（落在缺货），没有第三种。
        InventoryWarningQueryForm form = new InventoryWarningQueryForm();
        form.setPageNum(1L);
        form.setPageSize(100L);
        PageResult<InventoryWarningVO> page = inventoryWarningQueryService.queryWarningPage(form);
        long abnormal = health.getLowCount() + health.getHighCount();
        assertThat(page.getTotal())
                .as("预警列表异常总数应落在 [低于下限+高于上限, 该值+缺货] 之间")
                .isGreaterThanOrEqualTo(abnormal)
                .isLessThanOrEqualTo(abnormal + health.getOutOfStockCount());

        // 供应链网络节点：只含启用仓库，且字段完整（前端直接渲染，null 会崩）
        assertThat(vo.getWarehouseNodes()).isNotNull();
        vo.getWarehouseNodes().forEach(node -> {
            assertThat(node.getWarehouseName()).isNotBlank();
            assertThat(node.getQuantity()).isNotNull().isGreaterThanOrEqualTo(BigDecimal.ZERO);
            assertThat(node.getTodayOutboundQuantity()).isNotNull().isGreaterThanOrEqualTo(BigDecimal.ZERO);
        });
        assertThat(vo.getWarehouseNodes()).hasSize(vo.getWarehouseCount().intValue());
    }

    @Test
    @DisplayName("趋势数据：7d/30d 序列长度一致、日期轴对齐且末点就是今天")
    void trendDataSeriesAreAlignedAndConsistent() {
        assertSeriesAligned(screenDataService.getTrendData("7d"), 7);
        assertSeriesAligned(screenDataService.getTrendData("30d"), 30);

        // 未知/缺失取值一律按 7d 兜底：大屏不应因为一个参数笔误而整块空白
        assertThat(screenDataService.getTrendData("bogus").getRange()).isEqualTo("7d");
        assertThat(screenDataService.getTrendData("bogus").getDates()).hasSize(7);
        assertThat(screenDataService.getTrendData(null).getRange()).isEqualTo("7d");
    }

    /**
     * 断言八条序列与日期轴长度一致，且末点就是**北京时间的今天**。
     *
     * <p>末点日期这条断言是「今日时区」的回归点：服务层曾经用 UTC 日界，
     * 窗口整体平移 8 小时，早上下的单会被算到前一天，而序列长度看起来完全正常 ——
     * 只看长度是抓不到的。
     */
    private void assertSeriesAligned(ScreenTrendVO vo, int expected) {
        assertThat(vo.getRange()).isEqualTo(expected == 7 ? "7d" : "30d");
        assertThat(vo.getDates()).hasSize(expected);
        assertThat(vo.getFullDates()).hasSize(expected);
        assertThat(vo.getSales()).hasSize(expected);
        assertThat(vo.getOrders()).hasSize(expected);
        assertThat(vo.getPurchaseAmounts()).hasSize(expected);
        assertThat(vo.getPurchaseOrders()).hasSize(expected);
        assertThat(vo.getInventoryQuantity()).hasSize(expected);
        assertThat(vo.getInboundQuantity()).hasSize(expected);
        assertThat(vo.getOutboundQuantity()).hasSize(expected);

        assertThat(vo.getFullDates().get(expected - 1))
                .isEqualTo(LocalDate.now(ZoneId.of("Asia/Shanghai")).toString());

        vo.getSales().forEach(v -> assertThat(v).isNotNull().isGreaterThanOrEqualTo(BigDecimal.ZERO));
        vo.getOrders().forEach(v -> assertThat(v).isNotNull().isGreaterThanOrEqualTo(0L));
        vo.getInboundQuantity().forEach(v -> assertThat(v).isNotNull().isGreaterThanOrEqualTo(BigDecimal.ZERO));
        vo.getOutboundQuantity().forEach(v -> assertThat(v).isNotNull().isGreaterThanOrEqualTo(BigDecimal.ZERO));
    }
}
