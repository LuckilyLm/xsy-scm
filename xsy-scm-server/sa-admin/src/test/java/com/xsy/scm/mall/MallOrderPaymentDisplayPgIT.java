package com.xsy.scm.mall;

import static org.assertj.core.api.Assertions.assertThat;

import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.finance.domain.form.FinanceCodCollectionForm;
import com.xsy.scm.finance.service.FinanceReceiptService;
import com.xsy.scm.mall.account.domain.vo.RequestMallCustomer;
import com.xsy.scm.mall.order.domain.vo.MallOrderVO;
import com.xsy.scm.mall.order.service.MallOrderQueryService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 商城客户看到的资金展示（真实 PostgreSQL）。
 *
 * <p>
 * 钉住一条客户能直接看见的口径：<b>「已付」是实收</b> —— 余额 / 在线走支付事实，货到付款走收款事实。
 * 只算支付事实的话，COD 订单在交付收款之后仍会一直显示全额待付，客户以为钱没交上去。
 *
 * <p>
 * <b>夹具为什么直接把订单来源改成 MALL</b>：本类测的是「钱到账之后客户看到什么」，
 * 商城下单链（账号 → 登录 → 购物车 → 结算 → 下单）由 {@code MallWalletPaymentPgIT} 与
 * {@code MallPromotionCheckoutPgIT} 覆盖。客户侧详情只按 {@code customer_id + order_source='MALL'} 收窄归属，
 * 因此这里把来源改成 MALL 就足以让这条读路径成立，不必再铺一遍下单链。
 */
@DisplayName("商城订单资金展示：已付按实收（PG IT）")
class MallOrderPaymentDisplayPgIT extends ScmW5PgITBase {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    /** 下单 2.0000 × 客户价 1.2000 = 应付 2.4000。 */
    private static final BigDecimal PAYABLE = new BigDecimal("2.4000");

    @Autowired
    private MallOrderQueryService mallOrderQueryService;

    @Autowired
    private FinanceReceiptService financeReceiptService;

    @Test
    @DisplayName("COD 订单收款后，客户详情的已付反映实收、待付归零")
    void collectedCodShowsUpAsPaidInTheCustomerOrderDetail() {
        CodFixture fixture = mallCodOrder("display");

        MallOrderVO before = customerView(fixture);
        assertThat(before.getPaidAmount()).as("收款前一分钱都还没到").isEqualByComparingTo("0.0000");
        assertThat(due(before)).isEqualByComparingTo(PAYABLE);

        collect(fixture, PAYABLE.toPlainString(), "display");

        MallOrderVO after = customerView(fixture);
        assertThat(after.getPaidAmount()).as("收了多少就是已付多少").isEqualByComparingTo(PAYABLE);
        assertThat(due(after)).as("收清后待付归零").isEqualByComparingTo("0.0000");
    }

    @Test
    @DisplayName("少收时已付是实收额，待付保留差额")
    void partialCollectionLeavesTheRemainderAsDue() {
        CodFixture fixture = mallCodOrder("partial");

        collect(fixture, "1.0000", "partial");

        MallOrderVO view = customerView(fixture);
        assertThat(view.getPaidAmount()).isEqualByComparingTo("1.0000");
        assertThat(due(view)).isEqualByComparingTo("1.4000");
    }

    // ------------------------------------------------------------------
    // 夹具
    // ------------------------------------------------------------------

    private record CodFixture(Long orderId, Long customerId, Long receivableId) {
    }

    /** 一张「来源 MALL + 约定 COD + 已签收（有正常应收）」的订单。 */
    private CodFixture mallCodOrder(String tag) {
        Long customerId = newCustomer();
        Long skuId = newOnShelfSku("display-" + tag);
        Long orderId = confirmedSalesOrder(customerId, skuId, "2.0000", "2.0000");
        jdbc.update("UPDATE xsy_sales_order SET order_source = 'MALL', settlement_method = 'COD' WHERE id = ?",
                orderId);
        evictMybatisCache();
        Long receivableId = normalReceivable(orderId, PAYABLE.toPlainString());
        return new CodFixture(orderId, customerId, receivableId);
    }

    private void collect(CodFixture fixture, String amount, String tag) {
        FinanceCodCollectionForm form = new FinanceCodCollectionForm();
        form.setOrderId(fixture.orderId());
        form.setAmount(amount);
        form.setMethod("CASH");
        form.setReceivedAt(OffsetDateTime.now());
        form.setExternalReference("COD-DISPLAY-" + UUID.randomUUID().toString().substring(0, 8));
        financeReceiptService.collectCod(form, prefix + ":display-" + tag);
        evictMybatisCache();
    }

    private MallOrderVO customerView(CodFixture fixture) {
        RequestMallCustomer identity = new RequestMallCustomer();
        identity.setCustomerId(fixture.customerId());
        return mallOrderQueryService.detail(identity, fixture.orderId());
    }

    private static BigDecimal due(MallOrderVO order) {
        return order.getPayableAmount().subtract(order.getPaidAmount());
    }

    private Long normalReceivable(Long orderId, String amount) {
        OffsetDateTime eventAt = LocalDate.now(BUSINESS_ZONE).atStartOfDay(BUSINESS_ZONE).toOffsetDateTime();
        return jdbc.queryForObject("""
                INSERT INTO xsy_finance_receivable (receivable_no, source_type, source_id, order_id,
                    customer_id, customer_name_snapshot, settlement_customer_id,
                    settlement_customer_name_snapshot, entry_type, amount, event_at)
                SELECT ?, 'SALES_ORDER', id, id, customer_id, customer_name_snapshot,
                    settlement_customer_id, settlement_customer_name_snapshot, 'NORMAL', CAST(? AS numeric), ?
                FROM xsy_sales_order WHERE id = ? RETURNING id
                """, Long.class, "AR-DISPLAY-" + UUID.randomUUID(), new BigDecimal(amount), eventAt, orderId);
    }
}
