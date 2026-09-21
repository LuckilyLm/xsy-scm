package net.lab1024.sa.admin.module.scm.inventory;

import net.lab1024.sa.admin.module.scm.common.ScmW6PgITBase;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryTransferAddForm;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryTransferService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 调拨**发出**失败时的真实回滚（调拨波次）。
 *
 * <p><b>为什么这个类必须关掉测试事务</b>：W1–W5 的 IT 基类把整个用例包在一个事务里，
 * 于是 Service 上 {@code @Transactional(rollbackFor = Exception.class)} 只是**加入**这个事务；
 * 抛异常时 Spring 只把事务标记成 rollback-only，**不会**把已经写下的行撤掉。
 * 在这种环境下断言「失败后零残留」，看到的其实是「失败前写下的行还在」。
 * （与 {@code ScmInventoryRollbackIT} 同一取舍。）
 *
 * <p><b>本类会向开发库提交数据</b>（造数与 {@code seedWarehouseId} 之外的一切）。
 * 因此它必须自己收拾干净：调拨需要**第二个仓库**，而 `G-03` 的口径是「唯一启用仓库」，
 * 多留一个启用仓库会让后续任何依赖 {@code defaultEnabledWarehouse} 的用例拿到 41018。
 * 所以在 {@link #cleanupWarehouses()} 里把本类建的仓库软删掉 —— 见下方注释。
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("调拨失败的真实回滚（PG IT，无外层事务）")
class ScmInventoryTransferRollbackIT extends ScmW6PgITBase {

    @Autowired
    private InventoryTransferService transferService;

    /**
     * 本类建过的仓库 id，用例结束后软删（否则会破坏「唯一启用仓库」的口径）。
     */
    private final List<Long> createdWarehouses = new ArrayList<>();

    @Override
    protected void evictMybatisCache() {
        // 无外层事务 → 每次调用都是新 session → 一级缓存天然为空
    }

    @AfterEach
    void cleanupWarehouses() {
        for (Long warehouseId : createdWarehouses) {
            // 软删即可：warehouse 走 @TableLogic，all() / defaultEnabledWarehouse() 会自动过滤。
            // 硬删会破坏「历史记录保留可查」的口径，也没有必要。
            jdbc.update("UPDATE warehouse SET deleted = TRUE, status = 'DISABLED' WHERE id = ?", warehouseId);
        }
        createdWarehouses.clear();
    }

    private Long trackedWarehouse(String suffix) {
        Long id = newWarehouse(suffix);
        createdWarehouses.add(id);
        return id;
    }

    private Long stocked(String suffix, String quantity) {
        Long skuId = newSkuOfType(suffix, "NON_STANDARD", "ON_SHELF");
        W6Fixture fixture = inboundFixture(suffix, skuId, quantity);
        confirmReceipt(fixture.receipt().getId(), quantity);
        return skuId;
    }

    private static InventoryTransferAddForm.Item item(Long skuId, String quantity) {
        InventoryTransferAddForm.Item row = new InventoryTransferAddForm.Item();
        row.setSkuId(skuId);
        row.setQuantity(new BigDecimal(quantity));
        return row;
    }

    private InventoryTransferAddForm twoLineForm(Long from, Long to, Long first, String firstQty,
                                                 Long second, String secondQty) {
        InventoryTransferAddForm form = new InventoryTransferAddForm();
        form.setFromWarehouseId(from);
        form.setToWarehouseId(to);
        form.setItems(new ArrayList<>(List.of(item(first, firstQty), item(second, secondQty))));
        return form;
    }

    private String statusOf(Long id) {
        return jdbc.queryForObject(
                "SELECT status FROM inventory_transfer WHERE id = ?", String.class, id);
    }

    @Test
    @DisplayName("前一行已写转出流水、后一行可用量不足 → 整单回滚：余额零变化、流水零残留、单据仍是草稿")
    void failingLineRollsBackTheWholeShip() {
        Long wh1 = seedWarehouseId();
        Long wh2 = trackedWarehouse("RB4B");

        // 先建的先处理（服务端按 skuId 升序），因此 first 是「已经成功写入」的那一行
        Long first = stocked("RB4A", "10.0000");
        Long second = stocked("RB4C", "10.0000");

        // first 要 5（够），second 要 11（不够）→ 第二行抛 41043
        Long id = transferService.create(
                twoLineForm(wh1, wh2, first, "5.0000", second, "11.0000"));

        expectCode(() -> transferService.ship(id), 41043);

        // first 那一行本已在同一事务内写下了转出流水与余额扣减，必须随整单一起消失
        assertThat(balanceRow(wh1, first).getQuantity()).isEqualByComparingTo("10.0000");
        assertThat(movementCount(wh1, first)).as("只剩入库那一条流水").isEqualTo(1);
        assertThat(balanceRow(wh1, second).getQuantity()).isEqualByComparingTo("10.0000");
        assertThat(movementCount(wh1, second)).isEqualTo(1);

        // 单据仍是草稿、明细仍在 —— 用户修掉冲突行之后可以直接重试
        assertThat(statusOf(id)).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM inventory_transfer_item WHERE transfer_id = ? AND deleted = FALSE",
                Integer.class, id)).isEqualTo(2);

        // 失败原因是那一行，不是别的东西坏了：改成可行数量后整单可以正常发出
        transferService.update(id, twoLineForm(wh1, wh2, first, "5.0000", second, "10.0000"));
        transferService.ship(id);

        assertThat(statusOf(id)).isEqualTo("SHIPPED");
        assertThat(balanceRow(wh1, first).getQuantity()).isEqualByComparingTo("5.0000");
        assertThat(balanceRow(wh1, second).getQuantity()).isEqualByComparingTo("0.0000");
        // 发出只动源仓，目标仓要等收货
        assertThat(balanceRow(wh2, first)).isNull();
    }
}
