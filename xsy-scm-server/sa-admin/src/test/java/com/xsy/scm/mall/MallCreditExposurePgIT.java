package com.xsy.scm.mall;

import static org.assertj.core.api.Assertions.assertThat;

import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.finance.domain.dto.FinanceCreditExposureDto;
import com.xsy.scm.finance.domain.vo.CustomerCreditCheckVO;
import com.xsy.scm.finance.service.FinanceCreditExposureQueryService;
import com.xsy.scm.finance.service.FinanceOrderPayableQueryService;
import com.xsy.scm.mall.account.domain.vo.RequestMallCustomer;
import com.xsy.scm.mall.order.domain.vo.MallSettlementOptionVO;
import com.xsy.scm.mall.order.service.MallSettlementService;
import com.xsy.scm.order.service.OrderCreditService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 账期授信占用的正式净额口径（PAY-02，真实 PostgreSQL）。
 *
 * <p>
 * 钉住一条不变量：<b>授信占用的金额必须与最终开出的应收同口径</b> —— 实重 × 锁定价 − 冻结优惠的等比分摊。
 * 此前占用按订单的 {@code settlement_total_amount}（优惠前）加总，优惠越大占用越虚高，客户会被自己的优惠挡住：
 * 额度明明够付优惠后的钱，却因为「优惠前」超限而确认不了、下不了单。
 *
 * <p>
 * <b>为什么必须跑真实库</b>：占用是「已确认且还没开应收」的订单集合上的一笔聚合，集合本身由 SQL 谓词决定
 * （{@code status='CONFIRMED'} + {@code NOT EXISTS 应收}），金额由行级实重与 JSONB 优惠分摊算出。两者都在库里。
 *
 * <p>
 * <b>夹具为什么直插优惠事实</b>：冻结优惠的正式路径（领券 → 预览 → 下单 → 确认时冻结）由
 * {@code MallPromotionCheckoutPgIT} 覆盖；本类要测的是「优惠已经冻结之后，占用怎么算」，因此把
 * {@code xsy_order_discount} 作为输入固定下来。
 */
@DisplayName("账期授信占用的正式净额口径（PAY-02，PG IT）")
class MallCreditExposurePgIT extends ScmW5PgITBase {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    /** 下单 3.0000、实重 2.0000、客户价 1.2000 → 优惠前毛额 2.4000。 */
    private static final String ORDERED_QUANTITY = "3.0000";
    private static final String ACTUAL_QUANTITY = "2.0000";

    /** 冻结优惠 0.5000 按下单金额 3.6000 等比分摊到实重金额 2.4000 上 = 0.3333，净额 2.0667。 */
    private static final String FROZEN_DISCOUNT = "0.5000";
    private static final BigDecimal GROSS_AMOUNT = new BigDecimal("2.4000");
    private static final BigDecimal NET_AMOUNT = new BigDecimal("2.0667");

    @Autowired
    private FinanceOrderPayableQueryService payableQueryService;

    @Autowired
    private FinanceCreditExposureQueryService exposureQueryService;

    @Autowired
    private OrderCreditService orderCreditService;

    @Autowired
    private MallSettlementService settlementService;

    @Test
    @DisplayName("已确认未开应收的订单按净额占用，不按优惠前毛额")
    void confirmedOrdersOccupyTheNetAmountNotTheGrossAmount() {
        Long customerId = newCustomer();
        Long skuId = newOnShelfSku("expo-net");
        Long orderId = confirmedSalesOrder(customerId, skuId, ORDERED_QUANTITY, ACTUAL_QUANTITY);
        freezeDiscount(orderId, FROZEN_DISCOUNT);
        assertThat(jdbc.queryForObject("SELECT settlement_total_amount FROM xsy_sales_order WHERE id = ?",
                BigDecimal.class, orderId)).as("订单上的结算额是优惠前毛额").isEqualByComparingTo(GROSS_AMOUNT);

        assertThat(payableQueryService.payableOfConfirmedOrder(orderId))
                .as("净额 = 实重金额 2.4000 − 优惠等比分摊 0.3333")
                .isEqualByComparingTo(NET_AMOUNT);

        FinanceCreditExposureDto exposure = exposure(customerId);
        assertThat(exposure.getConfirmedOrderAmount())
                .as("占用取净额；取毛额会让客户被自己的优惠挡住")
                .isEqualByComparingTo(NET_AMOUNT);
        assertThat(exposure.getOpenReceivableAmount()).as("还没开应收").isEqualByComparingTo("0.0000");
    }

    @Test
    @DisplayName("开出应收后订单移出占用，改由应收的未结净额计入")
    void openedReceivableReplacesTheOrderInTheExposure() {
        Long customerId = newCustomer();
        Long skuId = newOnShelfSku("expo-open");
        Long orderId = confirmedSalesOrder(customerId, skuId, ORDERED_QUANTITY, ACTUAL_QUANTITY);
        freezeDiscount(orderId, FROZEN_DISCOUNT);

        Long receivableId = normalReceivable(orderId, NET_AMOUNT.toPlainString());

        FinanceCreditExposureDto exposure = exposure(customerId);
        assertThat(exposure.getConfirmedOrderAmount())
                .as("已开应收的订单不再计入占用，否则同一笔钱占两次额度")
                .isEqualByComparingTo("0.0000");
        assertThat(exposure.getOpenReceivableAmount()).isEqualByComparingTo(NET_AMOUNT);
        assertThat(jdbc.queryForObject("SELECT amount FROM xsy_finance_receivable WHERE id = ?", BigDecimal.class,
                receivableId)).isEqualByComparingTo(NET_AMOUNT);
    }

    @Test
    @DisplayName("结算页的账期剩余额度按净额占用扣减，额度落在净额与毛额之间时账期仍可选")
    void creditOptionFitsBetweenTheNetAndTheGrossAmount() {
        Long customerId = newCustomer();
        Long skuId = newOnShelfSku("expo-option");
        Long orderId = confirmedSalesOrder(customerId, skuId, ORDERED_QUANTITY, ACTUAL_QUANTITY);
        freezeDiscount(orderId, FROZEN_DISCOUNT);
        // 额度 2.2000：够覆盖净额占用 2.0667，不够覆盖毛额 2.4000 —— 正是「被自己的优惠挡住」的那个区间
        jdbc.update("UPDATE xsy_customer SET credit_limit = ? WHERE id = ?", new BigDecimal("2.2000"), customerId);
        evictMybatisCache();

        // 本单金额取小额，看的是「已占用之后还剩多少」；占用本身的口径在上一个用例里已钉住
        BigDecimal newOrderAmount = new BigDecimal("0.1000");
        CustomerCreditCheckVO check = orderCreditService.checkSelfSettlement(customerId, newOrderAmount);
        assertThat(check.getConfirmedOrderAmount()).isEqualByComparingTo(NET_AMOUNT);
        assertThat(check.getAllowed()).as("剩余额度 0.1333 够付这笔小额单").isTrue();

        MallSettlementOptionVO credit = settlementOptions(customerId, newOrderAmount).get("CREDIT");
        assertThat(credit.getEnabled()).isTrue();
        assertThat(credit.getAvailableAmount())
                .as("剩余额度 = 额度 2.2000 − 净额占用 2.0667")
                .isEqualByComparingTo("0.1333");
    }

    // ------------------------------------------------------------------
    // 夹具
    // ------------------------------------------------------------------

    private FinanceCreditExposureDto exposure(Long settlementCustomerId) {
        return exposureQueryService.summarize(settlementCustomerId, ScmValueScope.all());
    }

    private Map<String, MallSettlementOptionVO> settlementOptions(Long customerId, BigDecimal orderAmount) {
        RequestMallCustomer identity = new RequestMallCustomer();
        identity.setCustomerId(customerId);
        List<MallSettlementOptionVO> rows = settlementService.options(identity, orderAmount);
        return rows.stream().collect(Collectors.toMap(MallSettlementOptionVO::getMethod, Function.identity()));
    }

    /**
     * 直接落一条冻结优惠事实（含行级等比分摊），模拟「确认命令已经冻结过优惠」。
     *
     * <p>行基础金额取 {@code ordered_line_amount}（下单量 × 锁定价），与正式冻结路径同口径。
     */
    private void freezeDiscount(Long orderId, String discountAmount) {
        jdbc.update("""
                INSERT INTO xsy_order_discount (sales_order_id, base_amount, discount_amount, allocations, created_by)
                SELECT o.id, o.ordered_total_amount, CAST(? AS numeric),
                       jsonb_build_array(jsonb_build_object('orderItemId', i.id,
                           'baseAmount', i.ordered_line_amount, 'discountAmount', CAST(? AS numeric))),
                       ?
                FROM xsy_sales_order o
                JOIN xsy_sales_order_item i ON i.order_id = o.id AND i.deleted = FALSE
                WHERE o.id = ?
                """, discountAmount, discountAmount, operator(), orderId);
        evictMybatisCache();
    }

    private Long normalReceivable(Long orderId, String amount) {
        OffsetDateTime eventAt = LocalDate.now(BUSINESS_ZONE).plusDays(1).atStartOfDay(BUSINESS_ZONE)
                .toOffsetDateTime();
        return jdbc.queryForObject("""
                INSERT INTO xsy_finance_receivable (receivable_no, source_type, source_id, order_id,
                    customer_id, customer_name_snapshot, settlement_customer_id,
                    settlement_customer_name_snapshot, entry_type, amount, event_at)
                SELECT ?, 'SALES_ORDER', id, id, customer_id, customer_name_snapshot,
                    settlement_customer_id, settlement_customer_name_snapshot, 'NORMAL', CAST(? AS numeric), ?
                FROM xsy_sales_order WHERE id = ? RETURNING id
                """, Long.class, "AR-PAY02-" + UUID.randomUUID(), new BigDecimal(amount), eventAt, orderId);
    }

    private String operator() {
        return "IT-PAY02";
    }
}
