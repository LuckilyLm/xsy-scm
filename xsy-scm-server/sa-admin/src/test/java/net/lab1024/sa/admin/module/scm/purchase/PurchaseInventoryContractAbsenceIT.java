package net.lab1024.sa.admin.module.scm.purchase;

import net.lab1024.sa.admin.module.scm.common.ScmW5PgITBase;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseReceiptItemVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseReceiptVO;
import net.lab1024.sa.admin.module.scm.purchase.support.PurchaseInventoryContract;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W5 的库存边界（W5 Target Design §4.3 第 15 步 / Q5 / G-04，2 例）。
 *
 * <p><b>W5 不实现库存</b>：不建库存表、不写库存、契约**零调用点**。
 * 但 W6 需要知道「收货确认之后该往哪儿接」，因此 W5 定义
 * {@link PurchaseInventoryContract} 并提供一个空实现 —— 关键是**两者都不注册为 Bean**。
 *
 * <p><b>为什么不注册为 Bean 也要写成用例</b>：只要它进了 Spring 容器，
 * 就会有人（或某个自动装配）拿到它并调用 —— 那时「W5 零库存写入」这条边界
 * 就从「架构约束」退化成「大家记得别用」。注册与否是一个**可断言的事实**，
 * 比写一段注释可靠。
 *
 * <p>本类同时验证：跑完整收货流程（含实重、含超收）之后，全库依然没有任何库存类表 ——
 * 这条断言的价值在于它**不依赖实现意图**，只看数据库实际状态。
 */
@DisplayName("W5 库存边界：契约零 Bean、收货流程零库存写入（PG IT）")
class PurchaseInventoryContractAbsenceIT extends ScmW5PgITBase {

    @Autowired
    private ApplicationContext applicationContext;

    // ------------------------------------------------------------------
    // 1. 契约不注册为 Bean
    // ------------------------------------------------------------------

    @Test
    @DisplayName("PurchaseInventoryContract 存在但**不注册为 Bean**（W5 零调用点，W6 才接线）")
    void inventoryContractIsNotRegisteredAsBean() {
        assertThat(applicationContext.getBeanNamesForType(PurchaseInventoryContract.class))
                .as("W5 刻意不注册库存契约 Bean：一旦注册就会有调用点，边界立刻失守")
                .isEmpty();
    }

    // ------------------------------------------------------------------
    // 2. 完整流程零库存写入
    // ------------------------------------------------------------------

    @Test
    @DisplayName("完整收货流程（含实重与超收）之后，全库仍无任何库存类表")
    void fullReceiptFlowWritesNoInventory() {
        ReceiptFixture fx = receiptFixture("INV2", "10.0000");
        PurchaseReceiptVO current = reloadReceipt(fx.receipt().getId());
        PurchaseReceiptItemVO line = current.getItems().getFirst();

        // 收满 + 超收（planned 10 → 11，容差 10% 内），把收货路径上所有分支都走一遍
        PurchaseReceiptVO confirmed = purchaseReceiptService.confirm(
                confirmForm(current.getId(), current.getVersion(),
                        receiptLine(line.getId(), line.getVersion(), "11.0000")),
                prefix + ":INV2:confirm");
        assertThat(confirmed.getStatus()).isEqualTo("CONFIRMED");
        assertThat(reloadOrder(fx.order().getId()).getStatus()).isEqualTo("RECEIVED");

        Integer inventoryTables = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'xsy_v2' "
                        + "AND (table_name LIKE 'inventory%' OR table_name LIKE '%_inventory' "
                        + "OR table_name LIKE '%_movement%' OR table_name LIKE '%_stock%' "
                        + "OR table_name LIKE '%_balance%')", Integer.class);
        assertThat(inventoryTables).as("W5 不建任何库存表（G-04）").isZero();

        // 收货的全部落点就是这两张表：收货行 + 过秤记录（外加采购行上的累计量）
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM receipt_weighing_record WHERE purchase_receipt_item_id = ?",
                Integer.class, line.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM purchase_receipt_item WHERE purchase_receipt_id = ? AND deleted = FALSE",
                Integer.class, fx.receipt().getId())).isEqualTo(1);
    }
}
