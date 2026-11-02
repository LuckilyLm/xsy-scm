package com.xsy.scm.print;

import com.xsy.scm.order.domain.vo.SalesOrderDetailVO;
import com.xsy.scm.order.domain.vo.SalesOrderItemVO;
import com.xsy.scm.order.service.SalesOrderQueryService;
import com.xsy.scm.print.support.CustomerDeliveryNotePrintSourceProvider;
import com.xsy.scm.promotion.domain.vo.OrderDiscountVO;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomerDeliveryDiscountTest {
    @Test
    void discountFollowsActualQuantityForFullPartialZeroAndUnsortedDelivery() {
        var query = mock(SalesOrderQueryService.class);
        var order = new SalesOrderDetailVO();
        order.setOrderId(1L);
        order.setSettlementTotalAmount(new BigDecimal("100"));
        var line = new SalesOrderItemVO();
        line.setItemId(2L);
        line.setOrderedQuantity(new BigDecimal("10"));
        line.setLockedUnitPrice(new BigDecimal("10"));
        line.setOrderedLineAmount(new BigDecimal("100"));
        line.setSettlementLineAmount(new BigDecimal("100"));
        order.setItems(List.of(line));
        var discount = new OrderDiscountVO();
        var allocation = new OrderDiscountVO.Allocation();
        allocation.setOrderItemId(2L);
        allocation.setDiscountAmount(new BigDecimal("10"));
        discount.setAllocations(List.of(allocation));
        order.setDiscount(discount);
        when(query.detail(1L)).thenReturn(order);
        var provider = new CustomerDeliveryNotePrintSourceProvider(query);
        when(query.sortedQuantities(1L)).thenReturn(Map.of(2L, new BigDecimal("10")));
        assertThat(provider.load(1L).totalValues().get("settlementTotalAmount")).isEqualTo("90.0000");
        when(query.sortedQuantities(1L)).thenReturn(Map.of(2L, new BigDecimal("5")));
        assertThat(provider.load(1L).rows().getFirst().get("settlementLineAmount")).isEqualTo("45.0000");
        when(query.sortedQuantities(1L)).thenReturn(Map.of(2L, BigDecimal.ZERO));
        assertThat(provider.load(1L).totalValues().get("settlementTotalAmount")).isEqualTo("0.0000");
        when(query.sortedQuantities(1L)).thenReturn(Map.of());
        assertThat(provider.load(1L).totalValues().get("settlementTotalAmount")).isEqualTo("90.0000");
    }
}
