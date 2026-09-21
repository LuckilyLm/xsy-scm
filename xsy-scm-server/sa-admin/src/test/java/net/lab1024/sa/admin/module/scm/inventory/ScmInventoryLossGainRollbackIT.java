package net.lab1024.sa.admin.module.scm.inventory;

import net.lab1024.sa.admin.module.scm.common.ScmW6PgITBase;
import net.lab1024.sa.admin.module.scm.inventory.domain.ReserveInventoryFact;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryLossGainAddForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryLossGainAuditForm;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryLossGainService;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryReservationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 报损报溢审批失败时的**真实回滚**（报损报溢波次）。
 *
 * <p><b>为什么这个类必须关掉测试事务</b>：W1–W5 的 IT 基类把整个用例包在一个事务里，
 * 于是 Service 上 {@code @Transactional(rollbackFor = Exception.class)} 只是**加入**这个事务；
 * 抛异常时 Spring 只把事务标记成 rollback-only，**不会**把已经写下的行撤掉。
 * 在这种环境下断言「失败后零残留」，看到的其实是「失败前写下的行还在」——
 * 断言会因为「检查发生在任何写入之前」而碰巧通过，但完全没有验证到原子性。
 * （与 {@code ScmInventoryRollbackIT} / {@code ScmInventoryStocktakeRollbackIT} 同一取舍。）
 *
 * <p>本类用 {@code Propagation.NOT_SUPPORTED} 关掉外层事务，让每次 Service 调用
 * 自己开事务、自己提交或回滚。于是每个断言都是**独立事务里的已提交读**，
 * 一张报损单要么整单生效，要么整单不生效。
 *
 * <p><b>代价</b>：造数会提交到开发库。所有编码都带 {@link #prefix} 的随机后缀，
 * 不会与其它用例或既有数据冲突。
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("报损报溢审批失败的真实回滚（PG IT，无外层事务）")
class ScmInventoryLossGainRollbackIT extends ScmW6PgITBase {

    @Autowired
    private InventoryLossGainService lossGainService;

    @Autowired
    private InventoryReservationService reservations;

    @Override
    protected void evictMybatisCache() {
        // 无外层事务 → 每次调用都是新 session → 一级缓存天然为空
    }

    private Long stocked(String suffix, String quantity) {
        Long skuId = newSkuOfType(suffix, "NON_STANDARD", "ON_SHELF");
        W6Fixture fixture = inboundFixture(suffix, skuId, quantity);
        confirmReceipt(fixture.receipt().getId(), quantity);
        return skuId;
    }

    private static InventoryLossGainAddForm.Item item(Long skuId, String quantity) {
        InventoryLossGainAddForm.Item row = new InventoryLossGainAddForm.Item();
        row.setSkuId(skuId);
        row.setQuantity(new BigDecimal(quantity));
        return row;
    }

    private InventoryLossGainAddForm form(Long wh, Long firstSku, String firstQty,
                                          Long secondSku, String secondQty) {
        InventoryLossGainAddForm form = new InventoryLossGainAddForm();
        form.setAdjustType("LOSS");
        form.setWarehouseId(wh);
        form.setReason("同一批到货一起变质");
        form.setItems(new ArrayList<>(List.of(item(firstSku, firstQty), item(secondSku, secondQty))));
        return form;
    }

    private InventoryLossGainAuditForm audit(Long id) {
        InventoryLossGainAuditForm form = new InventoryLossGainAuditForm();
        form.setVersion(jdbc.queryForObject(
                "SELECT version FROM inventory_loss_gain WHERE id = ?", Integer.class, id));
        return form;
    }

    private List<Map<String, Object>> lossGainMovements(Long wh, Long sku) {
        return movementsOf(wh, sku).stream()
                .filter(m -> {
                    String type = String.valueOf(m.get("movement_type"));
                    return "LOSS_REPORT".equals(type) || "GAIN_REPORT".equals(type);
                })
                .toList();
    }

    @Test
    @DisplayName("前一行已写流水、后一行失败 → 整单回滚：余额零变化、流水零残留、单据仍是待审核")
    void failingLineRollsBackTheWholeDocument() {
        Long wh = seedWarehouseId();

        // 先建的先处理（服务端按 skuId 升序），因此 first 是「已经成功写入」的那一行
        Long first = stocked("RB3A", "10.0000");
        Long second = stocked("RB3B", "10.0000");

        // second 已全部预留 10 → 报损 1 会让 after = 9 < 已预留 10 → 41034
        // 来源标识取本次新建的 skuId：本类无外层事务，预留会提交到开发库，
        // 固定 id 会让同一个库上的第二次运行撞 uk 而假红（41016）。
        Long reservationId = reservations.reserve(new ReserveInventoryFact(
                wh, second, "SALES_ORDER_ITEM", second, second,
                new BigDecimal("10.0000"), OffsetDateTime.now(), null));

        Long id = lossGainService.create(form(wh, first, "2.0000", second, "1.0000"));
        expectCode(() -> lossGainService.approve(id, audit(id)), 41034);

        // first 那一行本已在同一事务内写下了报损流水与余额扣减，必须随整单一起消失
        assertThat(balanceRow(wh, first).getQuantity()).isEqualByComparingTo("10.0000");
        assertThat(lossGainMovements(wh, first)).isEmpty();
        assertThat(balanceRow(wh, second).getQuantity()).isEqualByComparingTo("10.0000");
        assertThat(lossGainMovements(wh, second)).isEmpty();
        assertThat(jdbc.queryForObject(
                "SELECT status FROM inventory_loss_gain WHERE id = ?", String.class, id))
                .isEqualTo("PENDING");

        // 失败原因是那一行，不是别的东西坏了：释放预留后重试即通过
        reservations.release(reservationId);
        lossGainService.approve(id, audit(id));

        assertThat(balanceRow(wh, first).getQuantity()).isEqualByComparingTo("8.0000");
        assertThat(balanceRow(wh, second).getQuantity()).isEqualByComparingTo("9.0000");
        assertThat(lossGainMovements(wh, first)).hasSize(1);
        assertThat(lossGainMovements(wh, second)).hasSize(1);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM inventory_loss_gain WHERE id = ?", String.class, id))
                .isEqualTo("COMPLETED");
    }
}
