package net.lab1024.sa.admin.module.scm.inventory;

import net.lab1024.sa.admin.module.scm.common.ScmW6PgITBase;
import net.lab1024.sa.admin.module.scm.inventory.domain.InventoryLossGainFact;
import net.lab1024.sa.admin.module.scm.inventory.domain.ReserveInventoryFact;
import net.lab1024.sa.admin.module.scm.inventory.domain.entity.InventoryBalanceEntity;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryLossGainAddForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryLossGainAuditForm;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryLossGainService;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryReservationService;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.domain.RequestUser;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 报损报溢的 PostgreSQL 集成测试（报损报溢波次）。
 *
 * <p>覆盖五件在单测里验证不了的事：
 * <ol>
 *   <li><b>审批才动库存</b> —— 创建 / 改待审核不产生任何流水与余额变化，
 *       这是「审批」这个环节存在的全部意义；</li>
 *   <li><b>方向由单据类型决定</b> —— 报损写 {@code LOSS_REPORT} 且快照为减、
 *       报溢写 {@code GAIN_REPORT} 且快照为加，与六方向快照约束一致；</li>
 *   <li><b>报损的两条下限</b> —— 不得推成负数（41033）、不得吃掉预留（41034）；</li>
 *   <li><b>状态守卫</b> —— 已审核（通过 / 驳回）的单据不可再改 / 再删 / 再审；</li>
 *   <li><b>审批乐观锁</b> —— 审批人必须批准自己读到的内容（版本不符 → 40921）。</li>
 * </ol>
 */
@DisplayName("报损报溢（PG IT）")
class ScmInventoryLossGainIT extends ScmW6PgITBase {

    /**
     * 审批人身份：与基类的录单人（employee 1）刻意不同，用于满足「禁止自建自审」。
     */
    private static final Long AUDITOR_EMPLOYEE_ID = 90011L;

    @Autowired
    private InventoryLossGainService lossGainService;

    @Autowired
    private InventoryReservationService reservations;

    /**
     * 造一个已入库指定数量的 SKU，返回 {@code (warehouseId, skuId)}。
     */
    private Object[] stocked(String suffix, String quantity) {
        Long skuId = newSkuOfType(suffix, "NON_STANDARD", "ON_SHELF");
        W6Fixture fixture = inboundFixture(suffix, skuId, quantity);
        confirmReceipt(fixture.receipt().getId(), quantity);
        return new Object[]{seedWarehouseId(), skuId};
    }

    private static InventoryLossGainAddForm.Item item(Long skuId, String quantity) {
        InventoryLossGainAddForm.Item row = new InventoryLossGainAddForm.Item();
        row.setSkuId(skuId);
        row.setQuantity(new BigDecimal(quantity));
        return row;
    }

    private InventoryLossGainAddForm form(String adjustType, Long wh, Long sku, String qty, String reason) {
        InventoryLossGainAddForm form = new InventoryLossGainAddForm();
        form.setAdjustType(adjustType);
        form.setWarehouseId(wh);
        form.setReason(reason);
        form.setItems(new ArrayList<>(List.of(item(sku, qty))));
        return form;
    }

    /**
     * 单据当前版本号 —— 审批必须带上它（读库而不是读缓存，避免 MyBatis 一级缓存干扰）。
     */
    private int versionOf(Long id) {
        return jdbc.queryForObject(
                "SELECT version FROM inventory_loss_gain WHERE id = ?", Integer.class, id);
    }

    private InventoryLossGainAuditForm audit(Long id, String opinion) {
        return audit(id, opinion, versionOf(id));
    }

    private InventoryLossGainAuditForm audit(Long id, String opinion, int version) {
        InventoryLossGainAuditForm form = new InventoryLossGainAuditForm();
        form.setVersion(version);
        form.setAuditOpinion(opinion);
        return form;
    }

    private String statusOf(Long id) {
        return jdbc.queryForObject(
                "SELECT status FROM inventory_loss_gain WHERE id = ?", String.class, id);
    }

    /**
     * 审批必须换一个人：报损报溢禁止自建自审（41065，P0 基线收口裁决第 8 条），
     * 而本类通篇是「录单人建单 → 审批」，因此把审批动作切到另一个员工身份、跑完立即恢复，
     * 其余断言仍按录单人视角执行。库里没有指向 {@code t_employee} 的外键，
     * 审批人用合成 id 即可（它只落进审计字段）。
     */
    private void asAuditor(Runnable auditAction) {
        RequestUser maker = SmartRequestUtil.getRequestUser();
        RequestEmployee auditor = new RequestEmployee();
        auditor.setEmployeeId(AUDITOR_EMPLOYEE_ID);
        auditor.setActualName("W6 IT 审核员");
        auditor.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        // 与基类同一取向：夹具操作者不受数据范围约束，本用例只测「审批人 != 录单人」这条身份规则，
        // 不顺带把仓库授权也当被测对象（那属于数据范围用例）。
        auditor.setAdministratorFlag(true);
        SmartRequestUtil.setRequestUser(auditor);
        try {
            auditAction.run();
        } finally {
            if (maker == null) {
                SmartRequestUtil.remove();
            } else {
                SmartRequestUtil.setRequestUser(maker);
            }
        }
    }

    private void approveAsAuditor(Long id, InventoryLossGainAuditForm form) {
        asAuditor(() -> lossGainService.approve(id, form));
    }

    private void rejectAsAuditor(Long id, InventoryLossGainAuditForm form) {
        asAuditor(() -> lossGainService.reject(id, form));
    }

    /**
     * 某个 (仓库, SKU) 的报损报溢流水，按业务时刻升序。
     */
    private List<Map<String, Object>> lossGainMovements(Long wh, Long sku) {
        return movementsOf(wh, sku).stream()
                .filter(m -> {
                    String type = String.valueOf(m.get("movement_type"));
                    return "LOSS_REPORT".equals(type) || "GAIN_REPORT".equals(type);
                })
                .toList();
    }

    private static BigDecimal decimal(Map<String, Object> row, String column) {
        return new BigDecimal(String.valueOf(row.get(column)));
    }

    // ------------------------------------------------------------------
    // 审批才动库存
    // ------------------------------------------------------------------

    @Test
    @DisplayName("报损：创建与改待审核都不动库存，审批通过才写 LOSS_REPORT 并扣减")
    void lossOnlyTouchesStockAtApproval() {
        Object[] s = stocked("lg1", "10.0000");
        Long wh = (Long) s[0];
        Long sku = (Long) s[1];

        Long id = lossGainService.create(form("LOSS", wh, sku, "3.0000", "到货变质"));

        // 创建之后：单据待审核、余额与流水都没动 —— 这正是「审批」这个环节的意义
        assertThat(statusOf(id)).isEqualTo("PENDING");
        assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("10.0000");
        assertThat(lossGainMovements(wh, sku)).isEmpty();

        approveAsAuditor(id, audit(id, null));

        assertThat(statusOf(id)).isEqualTo("COMPLETED");
        assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("7.0000");

        List<Map<String, Object>> rows = lossGainMovements(wh, sku);
        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.getFirst();
        assertThat(row.get("movement_type")).isEqualTo("LOSS_REPORT");
        assertThat(row.get("source_document_type")).isEqualTo("LOSS_GAIN_ITEM");
        assertThat(decimal(row, "quantity")).isEqualByComparingTo("3.0000");
        // 方向感知快照：报损是减
        assertThat(decimal(row, "before_quantity")).isEqualByComparingTo("10.0000");
        assertThat(decimal(row, "after_quantity")).isEqualByComparingTo("7.0000");
        // 单位以余额记账单位为准（Q13），由服务端取
        assertThat(String.valueOf(row.get("unit_snapshot"))).isEqualTo(balanceRow(wh, sku).getUnit());
        // V34 起：**出库方向的流水必须带成本**，写的是出库那一刻的余额均价。
        // 此前这里断言 `isNull()`（「报损没有成本依据」）—— 那是成本核算上线前的语义。
        // 移动加权平均的性质是「出库不改变均价」，所以事后再读余额拿到的仍是同一个值。
        assertThat(decimal(row, "unit_cost"))
                .isEqualByComparingTo(balanceRow(wh, sku).getAvgCost());
    }

    @Test
    @DisplayName("报溢：审批通过写 GAIN_REPORT 并累加余额，快照方向为加")
    void overflowAddsStockAtApproval() {
        Object[] s = stocked("lg2", "10.0000");
        Long wh = (Long) s[0];
        Long sku = (Long) s[1];

        Long id = lossGainService.create(form("OVERFLOW", wh, sku, "5.0000", "盘点外发现多出一批"));
        approveAsAuditor(id, audit(id, null));

        assertThat(statusOf(id)).isEqualTo("COMPLETED");
        assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("15.0000");

        Map<String, Object> row = lossGainMovements(wh, sku).getFirst();
        assertThat(row.get("movement_type")).isEqualTo("GAIN_REPORT");
        assertThat(decimal(row, "quantity")).isEqualByComparingTo("5.0000");
        assertThat(decimal(row, "before_quantity")).isEqualByComparingTo("10.0000");
        assertThat(decimal(row, "after_quantity")).isEqualByComparingTo("15.0000");
    }

    @Test
    @DisplayName("报损后低于预留量被拒（41034），余额与状态都不变 —— 已预留的货不能被报损吃掉")
    void lossBelowReservedIsRejected() {
        Object[] s = stocked("lg3", "10.0000");
        Long wh = (Long) s[0];
        Long sku = (Long) s[1];

        reservations.reserve(new ReserveInventoryFact(
                wh, sku, "SALES_ORDER_ITEM", 730001L, 830001L,
                new BigDecimal("8.0000"), OffsetDateTime.now(), null));

        // 10 − 5 = 5 < 已预留 8
        Long id = lossGainService.create(form("LOSS", wh, sku, "5.0000", "破损"));
        expectCode(() -> approveAsAuditor(id, audit(id, null)), 41034);

        assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("10.0000");
        assertThat(balanceRow(wh, sku).getReservedQuantity()).isEqualByComparingTo("8.0000");
        assertThat(lossGainMovements(wh, sku)).isEmpty();
        // 失败后单据仍是待审核，可以改数量后重试
        assertThat(statusOf(id)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("报损超过现有库存被拒（41033），不引入负库存")
    void lossBeyondOnHandIsRejected() {
        Object[] s = stocked("lg4", "5.0000");
        Long wh = (Long) s[0];
        Long sku = (Long) s[1];

        Long id = lossGainService.create(form("LOSS", wh, sku, "6.0000", "变质"));
        expectCode(() -> approveAsAuditor(id, audit(id, null)), 41033);

        assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("5.0000");
        assertThat(lossGainMovements(wh, sku)).isEmpty();
    }

    @Test
    @DisplayName("从未入库的 (仓库, SKU)：可建单但审批被拒（41032），且不会建出零余额行")
    void lossGainWithoutAnyBalanceIsRejectedAtApprovalWithoutCreatingRow() {
        Long skuId = newSkuOfType("lg5", "NON_STANDARD", "ON_SHELF");
        Long wh = seedWarehouseId();
        assertThat(balanceRowCount(wh, skuId)).isZero();

        // 建单不依赖余额（它只是记录意图），因此这里成功 —— 与出库单同一取向
        Long id = lossGainService.create(form("LOSS", wh, skuId, "1.0000", "丢失"));

        // 审批时才失败：记账单位只能来自余额行，从未入库的 SKU 无账可调
        expectCode(() -> approveAsAuditor(id, audit(id, null)), 41032);
        assertThat(balanceRowCount(wh, skuId)).isZero();
        assertThat(statusOf(id)).isEqualTo("PENDING");
    }

    // ------------------------------------------------------------------
    // 状态守卫与乐观锁
    // ------------------------------------------------------------------

    @Test
    @DisplayName("已完成的单据是终态：不可再审批、不可改、不可删，余额不会被调整两次")
    void completedDocumentIsTerminal() {
        Object[] s = stocked("lg6", "10.0000");
        Long wh = (Long) s[0];
        Long sku = (Long) s[1];

        Long id = lossGainService.create(form("LOSS", wh, sku, "3.0000", "变质"));
        approveAsAuditor(id, audit(id, null));
        assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("7.0000");

        // 再审批：状态已变（不是版本问题）→ 41029
        expectCode(() -> approveAsAuditor(id, audit(id, null)), 41029);
        expectCode(() -> lossGainService.update(id, form("LOSS", wh, sku, "99.0000", "改大一点")), 41029);
        expectCode(() -> lossGainService.delete(id), 41029);

        assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("7.0000");
        assertThat(lossGainMovements(wh, sku)).hasSize(1);
    }

    @Test
    @DisplayName("审批乐观锁：待审核期间单据被改过 → 审批以 40921 失败，不写流水")
    void staleVersionIsRejectedAtApproval() {
        Object[] s = stocked("lg7", "10.0000");
        Long wh = (Long) s[0];
        Long sku = (Long) s[1];

        Long id = lossGainService.create(form("LOSS", wh, sku, "3.0000", "变质"));
        int seenByAuditor = versionOf(id);

        // 审批人打开单据之后，录单人把数量改大了
        lossGainService.update(id, form("LOSS", wh, sku, "9.0000", "变质"));

        // 审批人用他看到的版本提交 → 必须失败，否则他批准的是一个自己没看过的数量
        expectCode(() -> approveAsAuditor(id, audit(id, null, seenByAuditor)), 40921);
        assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("10.0000");
        assertThat(lossGainMovements(wh, sku)).isEmpty();
        assertThat(statusOf(id)).isEqualTo("PENDING");

        // 刷新后重新审批（用新版本）即可通过，且按**改后**的数量执行
        approveAsAuditor(id, audit(id, null));
        assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("1.0000");
    }

    @Test
    @DisplayName("驳回：必须填审核意见（41037），驳回后不动库存且不可再审")
    void rejectRequiresOpinionAndLeavesStockUntouched() {
        Object[] s = stocked("lg8", "10.0000");
        Long wh = (Long) s[0];
        Long sku = (Long) s[1];

        Long id = lossGainService.create(form("LOSS", wh, sku, "3.0000", "变质"));

        // 空意见的驳回必须被拒 —— 驳回是唯一把「为什么不行」传达给录单人的渠道
        expectCode(() -> rejectAsAuditor(id, audit(id, null)), 41037);
        expectCode(() -> rejectAsAuditor(id, audit(id, "   ")), 41037);

        rejectAsAuditor(id, audit(id, "请附变质照片"));

        assertThat(statusOf(id)).isEqualTo("REJECTED");
        assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("10.0000");
        assertThat(lossGainMovements(wh, sku)).isEmpty();
        // 驳回通知制单人：与状态变更同事务写入原生消息表，恰一条，dataId 指向本单
        assertThat(rejectMessagesFor(id)).isEqualTo(1);

        // 已驳回是终态
        expectCode(() -> approveAsAuditor(id, audit(id, null)), 41029);
        expectCode(() -> rejectAsAuditor(id, audit(id, "再驳一次")), 41029);
        expectCode(() -> lossGainService.delete(id), 41029);
        // 终态后的再次驳回被状态守卫拒绝，事务回滚，不产生第二条通知
        assertThat(rejectMessagesFor(id)).isEqualTo(1);
    }

    private int rejectMessagesFor(Long lossGainId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM t_message WHERE data_id = ? AND receiver_user_id = 1",
                Integer.class, String.valueOf(lossGainId));
    }

    @Test
    @DisplayName("待审核可改明细，审批按改后的内容执行；改后版本前进")
    void pendingDocumentIsEditable() {
        Object[] s = stocked("lg9", "10.0000");
        Long wh = (Long) s[0];
        Long sku = (Long) s[1];

        Long id = lossGainService.create(form("LOSS", wh, sku, "3.0000", "变质"));
        int v0 = versionOf(id);

        lossGainService.update(id, form("LOSS", wh, sku, "4.0000", "变质（数量核对后修正）"));

        assertThat(versionOf(id)).isGreaterThan(v0);
        approveAsAuditor(id, audit(id, null));
        assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("6.0000");
        assertThat(decimal(lossGainMovements(wh, sku).getFirst(), "quantity")).isEqualByComparingTo("4.0000");
    }

    @Test
    @DisplayName("同一 SKU 在报损报溢单里出现两次被拒（41036）—— 数量不得被调整两次")
    void duplicateSkuInOneDocumentIsRejected() {
        Object[] s = stocked("lg10", "10.0000");
        Long wh = (Long) s[0];
        Long sku = (Long) s[1];

        InventoryLossGainAddForm form = form("LOSS", wh, sku, "3.0000", "变质");
        form.getItems().add(item(sku, "2.0000"));

        expectCode(() -> lossGainService.create(form), 41036);
        assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("10.0000");
    }

    @Test
    @DisplayName("草稿删除不产生任何库存影响")
    void deletingPendingDocumentHasNoStockEffect() {
        Object[] s = stocked("lg11", "10.0000");
        Long wh = (Long) s[0];
        Long sku = (Long) s[1];

        Long id = lossGainService.create(form("LOSS", wh, sku, "3.0000", "变质"));
        lossGainService.delete(id);

        assertThat(jdbc.queryForObject(
                "SELECT deleted FROM inventory_loss_gain WHERE id = ?", Boolean.class, id)).isTrue();
        expectCode(() -> approveAsAuditor(id, audit(id, null)), 41028);
        assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("10.0000");
    }

    // ------------------------------------------------------------------
    // 命令侧契约
    // ------------------------------------------------------------------

    @Test
    @DisplayName("同一来源行重复报损报溢被拒（41035），不会调整两次")
    void duplicateLossGainFromSameSourceLineIsRejected() {
        Object[] s = stocked("lg12", "10.0000");
        Long wh = (Long) s[0];
        Long sku = (Long) s[1];

        InventoryLossGainFact fact = new InventoryLossGainFact(
                wh, sku, 0L, 930001L, "LOSS", new BigDecimal("2.0000"),
                OffsetDateTime.now(), "test:1");
        assertThat(inventoryCommandService.postLossGainAdjust(fact))
                .isEqualTo(balanceRow(wh, sku).getUnit());

        expectCode(() -> inventoryCommandService.postLossGainAdjust(fact), 41035);
        assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("8.0000");
        assertThat(lossGainMovements(wh, sku)).hasSize(1);
    }

    @Test
    @DisplayName("未知单据类型被拒（41031）—— 方向无法确定，不能猜")
    void unknownAdjustTypeIsRejected() {
        Object[] s = stocked("lg13", "10.0000");
        Long wh = (Long) s[0];
        Long sku = (Long) s[1];

        expectCode(() -> inventoryCommandService.postLossGainAdjust(new InventoryLossGainFact(
                wh, sku, 0L, 930002L, "CONVERT", new BigDecimal("1.0000"),
                OffsetDateTime.now(), "test:1")), 41031);
        assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("10.0000");
    }

    @Test
    @DisplayName("Q7：报损报溢流水同样不可改删（新增类型不是绕过 append-only 的口子）")
    void lossGainMovementsAreStillAppendOnly() {
        Object[] s = stocked("lg14", "10.0000");
        Long wh = (Long) s[0];
        Long sku = (Long) s[1];

        Long id = lossGainService.create(form("LOSS", wh, sku, "3.0000", "变质"));
        approveAsAuditor(id, audit(id, null));

        Long movementId = ((Number) lossGainMovements(wh, sku).getFirst().get("id")).longValue();
        expectSqlFailure("UPDATE inventory_movement SET deleted = TRUE WHERE id = ?", movementId);
        expectSqlFailure("UPDATE inventory_movement SET quantity = 1, after_quantity = 9 WHERE id = ?", movementId);
        expectSqlFailure("DELETE FROM inventory_movement WHERE id = ?", movementId);
    }

    @Test
    @DisplayName("报溢不改变预留量：报损报溢只动 quantity，不动 reserved_quantity")
    void overflowLeavesReservedUntouched() {
        Object[] s = stocked("lg15", "10.0000");
        Long wh = (Long) s[0];
        Long sku = (Long) s[1];

        reservations.reserve(new ReserveInventoryFact(
                wh, sku, "SALES_ORDER_ITEM", 730002L, 830002L,
                new BigDecimal("4.0000"), OffsetDateTime.now(), null));

        Long id = lossGainService.create(form("OVERFLOW", wh, sku, "5.0000", "多出一批"));
        approveAsAuditor(id, audit(id, null));

        InventoryBalanceEntity balance = balanceRow(wh, sku);
        assertThat(balance.getQuantity()).isEqualByComparingTo("15.0000");
        assertThat(balance.getReservedQuantity()).isEqualByComparingTo("4.0000");
    }
}
