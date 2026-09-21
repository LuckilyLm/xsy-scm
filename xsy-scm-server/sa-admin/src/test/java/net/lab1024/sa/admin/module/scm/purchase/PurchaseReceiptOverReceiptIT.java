package net.lab1024.sa.admin.module.scm.purchase;

import net.lab1024.sa.admin.module.scm.common.ScmW5PgITBase;
import net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseConfigKey;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseReceiptItemVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseReceiptVO;
import net.lab1024.sa.base.module.support.config.ConfigService;
import net.lab1024.sa.base.module.support.config.domain.ConfigUpdateForm;
import net.lab1024.sa.base.module.support.config.domain.ConfigVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 超收容差（W5 Target Design §7.5 / Q3a，7 例）。
 *
 * <pre>
 * tolerance = t_config("scm.purchase.over_receipt_tolerance_percent")   默认 10，范围 0–100
 * ceiling   = planned × (1 + tolerance / 100)
 * available = ceiling − purchase_order_item.received_quantity
 * 本次有效数量 &gt; available → 40989（**整笔回滚**）
 * </pre>
 *
 * <p><b>Q3a：载体是 SmartAdmin 原生 Config，不是字典、也不新建 `sys_config`</b>。
 * 因此本类刻意只通过 {@link ConfigService} 读写 —— 这既是用例，也是「不绕过底座」的证明。
 *
 * <p><b>为什么改配置必须走 ConfigService</b>：它的缓存是手写的 {@code ConcurrentHashMap}
 * （不是 Spring Cache），直接 {@code jdbc.update} 改库不会刷新缓存，用例会读到旧值，
 * 表现为「容差怎么改都不生效」——那是测试的 bug，不是实现的 bug。
 *
 * <p><b>为什么 {@code @AfterEach} 必须恢复默认值</b>：库里的值随测试事务回滚，
 * 但缓存活在 JVM 里、不受事务影响。不恢复就会污染后续用例与后续测试类。
 */
@DisplayName("超收容差：Config 载体 / ceiling 语义 / 40989 整笔回滚（PG IT）")
class PurchaseReceiptOverReceiptIT extends ScmW5PgITBase {

    @Autowired
    private ConfigService configService;

    /**
     * 通过 ConfigService 改容差（会同步刷新它自己的缓存）。
     */
    private void setTolerance(String value) {
        ConfigVO current = configService.getConfig(PurchaseConfigKey.OVER_RECEIPT_TOLERANCE_PERCENT);
        assertThat(current).as("V15 必须播种采购容差配置").isNotNull();
        ConfigUpdateForm form = new ConfigUpdateForm();
        form.setConfigId(current.getConfigId());
        form.setConfigKey(current.getConfigKey());
        form.setConfigName(current.getConfigName());
        form.setConfigValue(value);
        form.setRemark(current.getRemark());
        configService.updateConfig(form);
    }

    @AfterEach
    void restoreDefaultTolerance() {
        setTolerance(PurchaseConfigKey.OVER_RECEIPT_TOLERANCE_PERCENT_DEFAULT);
    }

    /**
     * 用指定数量确认收货（自动回读当前版本）。
     */
    private PurchaseReceiptVO confirm(ReceiptFixture fx, String quantity) {
        PurchaseReceiptVO current = reloadReceipt(fx.receipt().getId());
        PurchaseReceiptItemVO line = current.getItems().getFirst();
        return purchaseReceiptService.confirm(
                confirmForm(current.getId(), current.getVersion(),
                        receiptLine(line.getId(), line.getVersion(), quantity)),
                prefix + ":over:" + current.getId() + ":" + quantity);
    }

    // ------------------------------------------------------------------
    // 1. 默认容差 = 10%
    // ------------------------------------------------------------------

    @Test
    @DisplayName("默认容差 10%：planned 10 → ceiling 11.0000，恰好收 11.0000 通过")
    void defaultToleranceAllowsExactlyTenPercentOver() {
        ReceiptFixture fx = receiptFixture("OR1", "10.0000");
        PurchaseReceiptVO confirmed = confirm(fx, "11.0000");

        PurchaseReceiptItemVO line = confirmed.getItems().getFirst();
        assertThat(line.getCumulativeReceivedQuantity()).isEqualByComparingTo("11.0000");
        assertThat(line.getOverReceiptQuantity()).isEqualByComparingTo("1.0000");
        assertThat(reloadOrder(fx.order().getId()).getStatus()).isEqualTo("RECEIVED");
    }

    // ------------------------------------------------------------------
    // 2. 超上限 → 整笔回滚
    // ------------------------------------------------------------------

    @Test
    @DisplayName("超出容差：11.0001 → 40989，且收货单 / 采购行 / 过秤记录全部不动")
    void beyondToleranceRejectedAndRolledBack() {
        ReceiptFixture fx = receiptFixture("OR2", "10.0000");
        PurchaseReceiptVO current = reloadReceipt(fx.receipt().getId());
        PurchaseReceiptItemVO line = current.getItems().getFirst();

        expectCode(() -> purchaseReceiptService.confirm(
                confirmForm(current.getId(), current.getVersion(),
                        receiptLine(line.getId(), line.getVersion(), "11.0001")),
                prefix + ":OR2:over"), 40989);

        // 整笔回滚：不是「收 11 然后报错」
        assertThat(reloadReceipt(fx.receipt().getId()).getStatus()).isEqualTo("DRAFT");
        assertThat(reloadOrder(fx.order().getId()).getItems().getFirst().getReceivedQuantity())
                .isEqualByComparingTo("0.0000");
        assertThat(reloadOrder(fx.order().getId()).getStatus()).isEqualTo("SUBMITTED");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM receipt_weighing_record WHERE purchase_receipt_item_id = ?",
                Integer.class, line.getId())).isZero();

        // 上限内仍然可以收 —— 证明拒绝的原因是数量而不是别的
        assertThat(confirm(fx, "11.0000").getItems().getFirst().getCumulativeReceivedQuantity())
                .isEqualByComparingTo("11.0000");
    }

    // ------------------------------------------------------------------
    // 3. 分次到货时 available 逐次递减
    // ------------------------------------------------------------------

    @Test
    @DisplayName("分次到货：先收 6.0000，第二张收货单只剩 5.0000 可收（5.0001 → 40989）")
    void secondReceiptUsesRemainingAvailable() {
        ReceiptFixture fx = receiptFixture("OR3", "10.0000");
        confirm(fx, "6.0000");
        assertThat(reloadOrder(fx.order().getId()).getStatus()).isEqualTo("PARTIALLY_RECEIVED");

        PurchaseReceiptVO second = createAnotherReceipt(fx.order().getId(), "b");
        PurchaseReceiptItemVO line = second.getItems().getFirst();
        // 第二张单的初始累计量是 0，但 available 是 11 − 6 = 5
        assertThat(line.getCumulativeReceivedQuantity()).isEqualByComparingTo("0.0000");

        expectCode(() -> purchaseReceiptService.confirm(
                confirmForm(second.getId(), second.getVersion(),
                        receiptLine(line.getId(), line.getVersion(), "5.0001")),
                prefix + ":OR3:b1"), 40989);

        PurchaseReceiptVO ok = purchaseReceiptService.confirm(
                confirmForm(second.getId(), second.getVersion(),
                        receiptLine(line.getId(), line.getVersion(), "5.0000")),
                prefix + ":OR3:b2");
        assertThat(ok.getItems().getFirst().getCumulativeReceivedQuantity()).isEqualByComparingTo("11.0000");
        assertThat(reloadOrder(fx.order().getId()).getStatus()).isEqualTo("RECEIVED");
    }

    // ------------------------------------------------------------------
    // 4. 容差 0
    // ------------------------------------------------------------------

    @Test
    @DisplayName("容差 0：多一个最小单位都拒（10.0001 → 40989），恰好 10.0000 通过")
    void zeroToleranceRejectsAnyOverReceipt() {
        setTolerance("0");
        ReceiptFixture fx = receiptFixture("OR4", "10.0000");
        PurchaseReceiptVO current = reloadReceipt(fx.receipt().getId());
        PurchaseReceiptItemVO line = current.getItems().getFirst();

        expectCode(() -> purchaseReceiptService.confirm(
                confirmForm(current.getId(), current.getVersion(),
                        receiptLine(line.getId(), line.getVersion(), "10.0001")),
                prefix + ":OR4:over"), 40989);

        assertThat(confirm(fx, "10.0000").getItems().getFirst().getCumulativeReceivedQuantity())
                .isEqualByComparingTo("10.0000");
    }

    // ------------------------------------------------------------------
    // 5. 容差真的来自配置
    // ------------------------------------------------------------------

    @Test
    @DisplayName("容差 50%：planned 10 → ceiling 15.0000，收 15.0000 通过、15.0001 拒绝")
    void toleranceComesFromSmartAdminConfig() {
        setTolerance("50");
        ReceiptFixture fx = receiptFixture("OR5", "10.0000");
        PurchaseReceiptVO current = reloadReceipt(fx.receipt().getId());
        PurchaseReceiptItemVO line = current.getItems().getFirst();

        expectCode(() -> purchaseReceiptService.confirm(
                confirmForm(current.getId(), current.getVersion(),
                        receiptLine(line.getId(), line.getVersion(), "15.0001")),
                prefix + ":OR5:over"), 40989);

        PurchaseReceiptVO ok = purchaseReceiptService.confirm(
                confirmForm(current.getId(), current.getVersion(),
                        receiptLine(line.getId(), line.getVersion(), "15.0000")),
                prefix + ":OR5:ok");
        assertThat(ok.getItems().getFirst().getOverReceiptQuantity()).isEqualByComparingTo("5.0000");
    }

    // ------------------------------------------------------------------
    // 6. 非法配置
    // ------------------------------------------------------------------

    @Test
    @DisplayName("容差配置非法（abc / 101 / -1）→ 40999，且收货单保持草稿")
    void invalidToleranceConfigRejected() {
        ReceiptFixture fx = receiptFixture("OR6", "10.0000");

        for (String bad : new String[]{"abc", "101", "-1", "10.5", "10%"}) {
            setTolerance(bad);
            PurchaseReceiptVO current = reloadReceipt(fx.receipt().getId());
            PurchaseReceiptItemVO line = current.getItems().getFirst();
            expectCode(() -> purchaseReceiptService.confirm(
                    confirmForm(current.getId(), current.getVersion(),
                            receiptLine(line.getId(), line.getVersion(), "5.0000")),
                    prefix + ":OR6:" + bad), 40999);
        }

        assertThat(reloadReceipt(fx.receipt().getId()).getStatus()).isEqualTo("DRAFT");
    }

    // ------------------------------------------------------------------
    // 7. 空值回退
    // ------------------------------------------------------------------

    @Test
    @DisplayName("容差为空 → 回退默认 10（不是「容差 0」，也不是报错）")
    void blankToleranceFallsBackToDefault() {
        setTolerance("");
        ReceiptFixture fx = receiptFixture("OR7", "10.0000");
        PurchaseReceiptVO confirmed = confirm(fx, "11.0000");
        assertThat(confirmed.getItems().getFirst().getCumulativeReceivedQuantity())
                .isEqualByComparingTo("11.0000");
    }
}
