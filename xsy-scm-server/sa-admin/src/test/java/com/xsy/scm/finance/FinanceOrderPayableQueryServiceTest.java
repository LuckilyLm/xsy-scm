package com.xsy.scm.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.xsy.scm.finance.dao.FinanceOrderFundingSourceDao;
import com.xsy.scm.finance.dao.FinanceReceiptDao;
import com.xsy.scm.finance.dao.FinanceReceivableSourceDao;
import com.xsy.scm.finance.domain.dto.FinanceOrderDiscountLineDto;
import com.xsy.scm.finance.domain.dto.FinanceOrderFundingDto;
import com.xsy.scm.finance.domain.dto.FinanceOrderPayableLineDto;
import com.xsy.scm.finance.service.FinanceOrderPayableQueryService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 应付净额与签收应收必须是同一把尺子：实重 × 锁定价，再扣该订单行承担的冻结优惠分摊。
 */
@DisplayName("商城余额支付与签收共用的应付净额口径")
class FinanceOrderPayableQueryServiceTest {
    private static final Long ORDER_ID = 7L;

    private final FinanceReceivableSourceDao sourceDao = mock(FinanceReceivableSourceDao.class);
    private final FinanceOrderFundingSourceDao fundingDao = mock(FinanceOrderFundingSourceDao.class);
    private final FinanceReceiptDao receiptDao = mock(FinanceReceiptDao.class);
    private final FinanceOrderPayableQueryService service = new FinanceOrderPayableQueryService(sourceDao, fundingDao, receiptDao);

    @Test
    void fullQuantityNetMatchesTheFrozenDiscount() {
        // 10.0000 × 2.0000 = 20.0000，本行冻结优惠 5.0000 全额承担
        when(sourceDao.selectOrderPayableLines(ORDER_ID)).thenReturn(List.of(payableLine(1L, "10.0000", "2.0000",
                "20.0000"), payableLine(2L, "5.0000", "2.0000", "10.0000")));
        when(sourceDao.selectOrderDiscountLines(ORDER_ID)).thenReturn(List.of(discountLine(1L, "5.0000")));

        assertThat(service.payableOfConfirmedOrder(ORDER_ID)).isEqualByComparingTo("25.0000");
    }

    @Test
    void partialQuantityProRatesTheDiscountInsteadOfChargingItWhole() {
        // 下单量与实重不同才测得到「等比」：实发只有下单量的一半，本行优惠也要减半，
        // 否则少发的货还要背全额优惠，客户付的钱与签收后的应收就对不上。
        when(sourceDao.selectOrderPayableLines(ORDER_ID)).thenReturn(
                List.of(payableLine(1L, "5.0000", "2.0000", "20.0000")));
        when(sourceDao.selectOrderDiscountLines(ORDER_ID)).thenReturn(List.of(discountLine(1L, "5.0000")));

        assertThat(service.payableOfConfirmedOrder(ORDER_ID)).isEqualByComparingTo("7.5000");
    }

    @Test
    void orderWithoutFrozenDiscountPaysTheGrossAmount() {
        when(sourceDao.selectOrderPayableLines(ORDER_ID)).thenReturn(
                List.of(payableLine(1L, "3.0000", "4.0000", "12.0000")));
        when(sourceDao.selectOrderDiscountLines(ORDER_ID)).thenReturn(List.of());

        assertThat(service.payableOfConfirmedOrder(ORDER_ID)).isEqualByComparingTo("12.0000");
    }

    @Test
    void paidAmountIsTheSumOfRecordedOrderFunding() {
        when(fundingDao.selectOrderFunding(ORDER_ID)).thenReturn(List.of(funding("8.0000"), funding("2.0000")));

        assertThat(service.paidAmountOf(ORDER_ID)).isEqualByComparingTo("10.0000");
    }

    @Test
    void unpaidOrderHasZeroPaidAmount() {
        when(fundingDao.selectOrderFunding(ORDER_ID)).thenReturn(List.of());

        assertThat(service.paidAmountOf(ORDER_ID)).isEqualByComparingTo("0.0000");
    }

    @Test
    void receivedAmountAddsCodCollectionsOnTopOfPaymentFacts() {
        when(fundingDao.selectOrderFunding(ORDER_ID)).thenReturn(List.of(funding("8.0000")));
        when(receiptDao.sumCodCollectedByOrder(ORDER_ID)).thenReturn(new BigDecimal("2.4000"));

        assertThat(service.paidAmountOf(ORDER_ID)).as("支付事实口径不含 COD 收款").isEqualByComparingTo("8.0000");
        assertThat(service.receivedAmountOf(ORDER_ID)).as("客户看到的已付是实收").isEqualByComparingTo("10.4000");
    }

    @Test
    void receivedAmountIsZeroWhenNothingArrivedAndNoCodCollection() {
        when(fundingDao.selectOrderFunding(ORDER_ID)).thenReturn(List.of());
        when(receiptDao.sumCodCollectedByOrder(ORDER_ID)).thenReturn(null);

        assertThat(service.receivedAmountOf(ORDER_ID)).isEqualByComparingTo("0.0000");
    }

    private static FinanceOrderPayableLineDto payableLine(Long orderItemId, String actualQuantity, String price,
            String orderedLineAmount) {
        FinanceOrderPayableLineDto line = new FinanceOrderPayableLineDto();
        line.setOrderItemId(orderItemId);
        line.setActualQuantity(new BigDecimal(actualQuantity));
        line.setLockedUnitPrice(new BigDecimal(price));
        line.setOrderedLineAmount(new BigDecimal(orderedLineAmount));
        return line;
    }

    private static FinanceOrderDiscountLineDto discountLine(Long orderItemId, String discountAmount) {
        FinanceOrderDiscountLineDto line = new FinanceOrderDiscountLineDto();
        line.setOrderItemId(orderItemId);
        line.setDiscountAmount(new BigDecimal(discountAmount));
        return line;
    }

    private static FinanceOrderFundingDto funding(String amount) {
        FinanceOrderFundingDto dto = new FinanceOrderFundingDto();
        dto.setAmount(new BigDecimal(amount));
        return dto;
    }
}
