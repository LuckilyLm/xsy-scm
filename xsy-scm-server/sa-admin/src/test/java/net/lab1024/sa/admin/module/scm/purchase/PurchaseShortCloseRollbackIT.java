package net.lab1024.sa.admin.module.scm.purchase;

import net.lab1024.sa.admin.module.scm.common.ScmW5PgITBase;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseOrderVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave 2B §9.1：批量少收关单的**整批原子回滚**取证。
 *
 * <p><b>为什么必须关掉测试事务</b>：{@code PurchaseEfficiencyIT} 里的批量用例裹在同一层测试事务中，
 * 非法成员抛错后，前一个合法成员的写入仍留在未提交事务里，回读只能看到「写过了」，
 * 看不到最终是否回滚 —— 那条断言在这个边界下要么恒真、要么误测。本类用
 * {@code Propagation.NOT_SUPPORTED}：{@code batchShortClose} 的 {@code @Transactional} 成为真实边界，
 * 失败即整批回滚并落库，随后用 {@code JdbcTemplate} 的独立自动提交查询读到的就是回滚后的事实。
 *
 * <p>批量按采购单 id 升序处理，两个用例都把**先被写入**的那张放在低位 id：
 * 状态机拒绝与乐观锁拒绝各一例，只要回滚漏掉一次，先写入的那张就会以
 * {@code SHORT_CLOSED} 留在库里并被本类当场抓到。日志同样逐张数：
 * 关单必须先有 SUBMIT 日志（否则零条只是没写对查询），再有 SHORT_CLOSE 日志即回滚失败。
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("W2B 批量少收关单整批回滚（PG IT，无外层事务）")
class PurchaseShortCloseRollbackIT extends ScmW5PgITBase {

    @Override
    protected void evictMybatisCache() {
        // 无外层事务 → 每次 Service / DAO 调用都是新 session → 一级缓存天然为空
    }

    @Test
    @DisplayName("合法欠收单先被关单、非法全收单抛 40982 → 两张单与 SHORT_CLOSE 日志全部回滚")
    void stateMachineFailureRollsBackMembersAlreadyWrittenInSameBatch() {
        PurchaseOrderVO legal = partiallyReceivedOrder("RB1");
        PurchaseOrderVO illegal = fullyReceivedOrder("RB1r");
        // 低位 id 先处理：合法那张确实被写过，才谈得上「回滚是否生效」
        assertThat(legal.getId()).isLessThan(illegal.getId());
        Map<String, Object> before = orderRow(legal.getId());

        expectCode(() -> purchaseOrderService.batchShortClose(batchForm("整批关单", legal, illegal)), 40982);

        assertThat(orderRow(legal.getId())).isEqualTo(before);
        assertThat(orderRow(legal.getId())).containsEntry("status", "PARTIALLY_RECEIVED")
                .containsEntry("short_close_reason", null).containsEntry("short_closed_at", null);
        assertThat(orderRow(illegal.getId())).containsEntry("status", "RECEIVED")
                .containsEntry("short_closed_at", null);
        assertThat(shortCloseLogs(legal.getId())).isZero();
        assertThat(shortCloseLogs(illegal.getId())).isZero();
    }

    @Test
    @DisplayName("合法欠收单先被关单、兄弟单版本过期抛 40921 → 整批同样不留部分成功")
    void versionConflictRollsBackMembersAlreadyWrittenInSameBatch() {
        PurchaseOrderVO written = partiallyReceivedOrder("RB2");
        PurchaseOrderVO stale = partiallyReceivedOrder("RB3");
        assertThat(written.getId()).isLessThan(stale.getId());
        Map<String, Object> before = orderRow(written.getId());
        // 兄弟单带一个不存在的版本：按 id 升序时先写完 written，再在 stale 上撞乐观锁
        stale.setVersion(stale.getVersion() + 999);

        expectCode(() -> purchaseOrderService.batchShortClose(batchForm("整批关单", written, stale)), 40921);

        assertThat(orderRow(written.getId())).isEqualTo(before);
        assertThat(orderRow(written.getId())).containsEntry("status", "PARTIALLY_RECEIVED");
        assertThat(orderRow(stale.getId())).containsEntry("status", "PARTIALLY_RECEIVED");
        // 断言非空洞：同一张单的其他操作日志确实入了库，只是这次关单没有留下记录
        assertThat(logCount(written.getId(), "SUBMIT")).isEqualTo(1);
        assertThat(shortCloseLogs(written.getId())).isZero();
        assertThat(shortCloseLogs(stale.getId())).isZero();
    }

    // ---- 独立查询上下文 ----

    /**
     * 关单三要素 + 乐观锁版本：一次读全，回滚是否彻底由整行等值比较判定。
     */
    private Map<String, Object> orderRow(Long orderId) {
        return jdbc.queryForMap("SELECT status, version, short_close_reason, short_closed_at"
                + " FROM purchase_order WHERE id=?", orderId);
    }

    private int shortCloseLogs(Long orderId) {
        return logCount(orderId, "SHORT_CLOSE");
    }

    private int logCount(Long orderId, String operationType) {
        return jdbc.queryForObject("SELECT count(*) FROM purchase_operation_log"
                + " WHERE purchase_order_id=? AND operation_type=?", Integer.class, orderId, operationType);
    }
}
