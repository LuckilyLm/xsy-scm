package net.lab1024.sa.admin.module.scm.purchase;

import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.purchase.constant.ScmPurchaseDemandStatusEnum;
import net.lab1024.sa.admin.module.scm.purchase.constant.ScmPurchaseStatusEnum;
import net.lab1024.sa.admin.module.scm.purchase.constant.ScmReceiptStatusEnum;
import net.lab1024.sa.admin.module.scm.purchase.manager.PurchaseOrderStateMachine;
import net.lab1024.sa.admin.module.scm.purchase.manager.PurchaseSnapshotFactory;
import net.lab1024.sa.admin.module.scm.purchase.service.PurchaseNumberGenerator;
import net.lab1024.sa.admin.module.scm.purchase.support.PurchaseDemandSourceGuard;
import net.lab1024.sa.admin.module.scm.order.domain.entity.SalesOrderEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W5 采购规则契约测试（无 DB，纯函数）。
 *
 * <p>设计依据：W5 Target Design §11.1 的 {@code PurchaseRulesTest}（状态机 6 状态全部转换
 * 合法 + 非法 + 权限判定，20 例）。
 *
 * <p>与 W4 的 {@code OrderRulesTest} 同定位：把**不需要 Spring / DB 的规则**压在一处，
 * 让状态图、终态判定、以及 Q6a 的 `demand_date` 派生都能被毫秒级覆盖。
 */
class PurchaseRulesTest {

    private static int codeOf(Throwable t) {
        return ((ScmBusinessException) t).getErrorCode().getCode();
    }

    // ------------------------------------------------------------------
    // 状态图：6 状态 × 6 状态的全覆盖（合法 + 非法）
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "{0} -> {1} = {2}")
    @CsvSource({
            // DRAFT
            "DRAFT,DRAFT,false",
            "DRAFT,SUBMITTED,true",
            "DRAFT,PARTIALLY_RECEIVED,false",
            "DRAFT,RECEIVED,false",
            "DRAFT,SHORT_CLOSED,false",
            "DRAFT,CANCELLED,true",
            // SUBMITTED
            "SUBMITTED,DRAFT,false",
            "SUBMITTED,SUBMITTED,false",
            "SUBMITTED,PARTIALLY_RECEIVED,true",
            "SUBMITTED,RECEIVED,true",
            "SUBMITTED,SHORT_CLOSED,false",
            "SUBMITTED,CANCELLED,true",
            // PARTIALLY_RECEIVED（自身到自身合法：第二次收货后仍是部分收货）
            "PARTIALLY_RECEIVED,DRAFT,false",
            "PARTIALLY_RECEIVED,SUBMITTED,false",
            "PARTIALLY_RECEIVED,PARTIALLY_RECEIVED,true",
            "PARTIALLY_RECEIVED,RECEIVED,true",
            "PARTIALLY_RECEIVED,SHORT_CLOSED,true",
            // P14：已部分收货不允许 cancel，需要终止时用 short-close
            "PARTIALLY_RECEIVED,CANCELLED,false",
            // 终态
            "RECEIVED,RECEIVED,false",
            "RECEIVED,CANCELLED,false",
            "SHORT_CLOSED,RECEIVED,false",
            "CANCELLED,SUBMITTED,false"
    })
    @DisplayName("状态转换表：只有 §4.2 列出的 (from,to) 合法")
    void stateGraph(String from, String to, boolean allowed) {
        assertThat(PurchaseOrderStateMachine.canTransition(from, to)).isEqualTo(allowed);
    }

    @Test
    @DisplayName("未知 / null 状态一律不可转换（白名单防护）")
    void unknownStateNeverTransitions() {
        assertThat(PurchaseOrderStateMachine.canTransition(null, "SUBMITTED")).isFalse();
        assertThat(PurchaseOrderStateMachine.canTransition("", "SUBMITTED")).isFalse();
        assertThat(PurchaseOrderStateMachine.canTransition("DRAFT", null)).isFalse();
        assertThat(PurchaseOrderStateMachine.canTransition("ARCHIVED", "SUBMITTED")).isFalse();
    }

    @Test
    @DisplayName("非法转换抛 40982（不是 40000，也不是 DB 异常）")
    void illegalTransitionThrowsStateInvalid() {
        assertThatThrownBy(() -> PurchaseOrderStateMachine.transition("RECEIVED", "CANCELLED"))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40982));
        assertThatThrownBy(() -> PurchaseOrderStateMachine.transition("PARTIALLY_RECEIVED", "CANCELLED"))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40982));
        assertThatCode(() -> PurchaseOrderStateMachine.transition("DRAFT", "SUBMITTED"))
                .doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------
    // 状态谓词（§4.2 T2 / T4 / T5 / T7）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("只有 DRAFT 可编辑行与需求分配（T2）")
    void editableOnlyDraft() {
        assertThat(PurchaseOrderStateMachine.editable("DRAFT")).isTrue();
        assertThat(PurchaseOrderStateMachine.editable("SUBMITTED")).isFalse();
        assertThat(PurchaseOrderStateMachine.editable("PARTIALLY_RECEIVED")).isFalse();
        assertThat(PurchaseOrderStateMachine.editable("RECEIVED")).isFalse();
        assertThat(PurchaseOrderStateMachine.editable("SHORT_CLOSED")).isFalse();
        assertThat(PurchaseOrderStateMachine.editable("CANCELLED")).isFalse();
        assertThat(PurchaseOrderStateMachine.editable(null)).isFalse();
    }

    @Test
    @DisplayName("可建 / 可确认收货：SUBMITTED 或 PARTIALLY_RECEIVED（T7 / T8）")
    void receivableSubmittedOrPartiallyReceived() {
        assertThat(PurchaseOrderStateMachine.receivable("DRAFT")).isFalse();
        assertThat(PurchaseOrderStateMachine.receivable("SUBMITTED")).isTrue();
        assertThat(PurchaseOrderStateMachine.receivable("PARTIALLY_RECEIVED")).isTrue();
        assertThat(PurchaseOrderStateMachine.receivable("RECEIVED")).isFalse();
        assertThat(PurchaseOrderStateMachine.receivable("SHORT_CLOSED")).isFalse();
        assertThat(PurchaseOrderStateMachine.receivable("CANCELLED")).isFalse();
    }

    @Test
    @DisplayName("可取消：DRAFT / SUBMITTED；P14 明确排除 PARTIALLY_RECEIVED（T4）")
    void cancellableExcludesPartiallyReceived() {
        assertThat(PurchaseOrderStateMachine.cancellable("DRAFT")).isTrue();
        assertThat(PurchaseOrderStateMachine.cancellable("SUBMITTED")).isTrue();
        assertThat(PurchaseOrderStateMachine.cancellable("PARTIALLY_RECEIVED")).isFalse();
        assertThat(PurchaseOrderStateMachine.cancellable("RECEIVED")).isFalse();
        assertThat(PurchaseOrderStateMachine.cancellable("CANCELLED")).isFalse();
    }

    @Test
    @DisplayName("可少收关单：仅 PARTIALLY_RECEIVED（T5 / Q2a）")
    void shortClosableOnlyPartiallyReceived() {
        assertThat(PurchaseOrderStateMachine.shortClosable("DRAFT")).isFalse();
        assertThat(PurchaseOrderStateMachine.shortClosable("SUBMITTED")).isFalse();
        assertThat(PurchaseOrderStateMachine.shortClosable("PARTIALLY_RECEIVED")).isTrue();
        assertThat(PurchaseOrderStateMachine.shortClosable("RECEIVED")).isFalse();
        assertThat(PurchaseOrderStateMachine.shortClosable("SHORT_CLOSED")).isFalse();
    }

    @Test
    @DisplayName("终态：RECEIVED / SHORT_CLOSED / CANCELLED")
    void terminalStates() {
        assertThat(PurchaseOrderStateMachine.terminal("DRAFT")).isFalse();
        assertThat(PurchaseOrderStateMachine.terminal("SUBMITTED")).isFalse();
        assertThat(PurchaseOrderStateMachine.terminal("PARTIALLY_RECEIVED")).isFalse();
        assertThat(PurchaseOrderStateMachine.terminal("RECEIVED")).isTrue();
        assertThat(PurchaseOrderStateMachine.terminal("SHORT_CLOSED")).isTrue();
        assertThat(PurchaseOrderStateMachine.terminal("CANCELLED")).isTrue();
    }

    @Test
    @DisplayName("收货确认后状态由「是否全部行收齐」推导（T7）")
    void afterReceiptDerivesStatus() {
        assertThat(PurchaseOrderStateMachine.afterReceipt(true)).isEqualTo("RECEIVED");
        assertThat(PurchaseOrderStateMachine.afterReceipt(false)).isEqualTo("PARTIALLY_RECEIVED");
    }

    // ------------------------------------------------------------------
    // 三张状态表的取值域（与 V15 的 ck_* 白名单由 ScmPurchaseStatusEnumTest 复核）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("状态枚举取值域固定：采购单 6 / 需求 3 / 收货单 2")
    void statusEnumsAreFrozen() {
        assertThat(ScmPurchaseStatusEnum.values())
                .extracting(Enum::name)
                .containsExactly("DRAFT", "SUBMITTED", "PARTIALLY_RECEIVED", "RECEIVED", "SHORT_CLOSED", "CANCELLED");
        assertThat(ScmPurchaseDemandStatusEnum.values())
                .extracting(Enum::name)
                .containsExactly("PENDING", "PARTIALLY_ALLOCATED", "ALLOCATED");
        assertThat(ScmReceiptStatusEnum.values())
                .extracting(Enum::name)
                .containsExactly("DRAFT", "CONFIRMED");
    }

    // ------------------------------------------------------------------
    // Q6a：demand_date 必须按 Asia/Shanghai 派生，而不是 UTC 日期
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Q6a：demand_date = source_confirmed_at 在 Asia/Shanghai 下的 LocalDate")
    void demandDateUsesAsiaShanghai() {
        // UTC 16:00 == 上海次日 00:00 —— 若实现退化成 UTC 日期，这里会得到 09-16
        assertThat(PurchaseSnapshotFactory.demandDate(OffsetDateTime.parse("2026-09-16T16:00:00Z")))
                .isEqualTo(LocalDate.of(2026, 9, 17));
        // 上海 23:59:59 仍是当日
        assertThat(PurchaseSnapshotFactory.demandDate(OffsetDateTime.parse("2026-09-16T15:59:59Z")))
                .isEqualTo(LocalDate.of(2026, 9, 16));
        // 已带偏移的时间点必须做等时刻换算，而不是丢弃偏移
        assertThat(PurchaseSnapshotFactory.demandDate(OffsetDateTime.parse("2026-09-16T20:30:00+08:00")))
                .isEqualTo(LocalDate.of(2026, 9, 16));
        assertThat(PurchaseSnapshotFactory.demandDate(OffsetDateTime.parse("2026-09-17T04:30:00+08:00")))
                .isEqualTo(LocalDate.of(2026, 9, 17));
    }

    @Test
    @DisplayName("Q6a：跨日窗口不得把所有需求压平成同一天")
    void demandDateIsNotCollapsedToWindowStart() {
        LocalDate day1 = PurchaseSnapshotFactory.demandDate(OffsetDateTime.parse("2026-09-15T18:00:00Z"));
        LocalDate day2 = PurchaseSnapshotFactory.demandDate(OffsetDateTime.parse("2026-09-16T18:00:00Z"));
        assertThat(day1).isEqualTo(LocalDate.of(2026, 9, 16));
        assertThat(day2).isEqualTo(LocalDate.of(2026, 9, 17));
        assertThat(day1).isNotEqualTo(day2);
    }

    // ------------------------------------------------------------------
    // §7.4 来源合法性：40980 的唯一可达入口（PurchaseDemandSourceGuard）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("来源订单必须是 CONFIRMED 且未软删，否则 40980")
    void demandSourceMustBeConfirmed() {
        assertThatCode(() -> PurchaseDemandSourceGuard.requireConfirmed(order("CONFIRMED", false)))
                .doesNotThrowAnyException();

        for (SalesOrderEntity bad : new SalesOrderEntity[]{
                order("DRAFT", false), order("PENDING", false), order("CANCELLED", false),
                order("CONFIRMED", true), null}) {
            assertThatThrownBy(() -> PurchaseDemandSourceGuard.requireConfirmed(bad))
                    .isInstanceOfSatisfying(ScmBusinessException.class,
                            e -> assertThat(codeOf(e)).isEqualTo(40980));
        }

        assertThat(PurchaseDemandSourceGuard.isConfirmed(order("CONFIRMED", false))).isTrue();
        assertThat(PurchaseDemandSourceGuard.isConfirmed(order("CONFIRMED", true))).isFalse();
        assertThat(PurchaseDemandSourceGuard.isConfirmed(null)).isFalse();
    }

    private static SalesOrderEntity order(String status, boolean deleted) {
        SalesOrderEntity entity = new SalesOrderEntity();
        entity.setStatus(status);
        entity.setDeleted(deleted);
        return entity;
    }

    // ------------------------------------------------------------------
    // 单号：全局序列 + yyyyMMdd + 至少 6 位，超 999999 自然扩位
    // ------------------------------------------------------------------

    @Test
    @DisplayName("单号格式：PO / PR + yyyyMMdd + 至少 6 位，超 999999 自然扩位")
    void numberingExpandsNaturally() {
        assertThat(PurchaseNumberGenerator.format("PO", 1)).matches("PO[0-9]{8}000001");
        assertThat(PurchaseNumberGenerator.format("PO", 999999)).matches("PO[0-9]{8}999999");
        // 不截断、不报错：7 位原样输出
        assertThat(PurchaseNumberGenerator.format("PO", 1000000)).matches("PO[0-9]{8}1000000");
        assertThat(PurchaseNumberGenerator.format("PR", 12345678)).matches("PR[0-9]{8}12345678");
        // 日期段取 Asia/Shanghai（与 Q6a 的 demand_date 同一个 ZoneId 常量）
        assertThat(PurchaseNumberGenerator.format("PO", 1))
                .startsWith("PO" + LocalDate.now(PurchaseSnapshotFactory.ASIA_SHANGHAI)
                        .format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE));
    }

    // ------------------------------------------------------------------
    // 收货单状态谓词（§4.3）
    // ------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"DRAFT", "CONFIRMED"})
    @DisplayName("收货单两态都是合法枚举值")
    void receiptStatusWhitelist(String status) {
        assertThat(ScmReceiptStatusEnum.valueOf(status).name()).isEqualTo(status);
    }
}
