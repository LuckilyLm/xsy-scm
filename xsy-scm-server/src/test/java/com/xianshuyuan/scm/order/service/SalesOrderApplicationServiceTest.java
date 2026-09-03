package com.xianshuyuan.scm.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xianshuyuan.scm.customer.entity.CustomerEntity;
import com.xianshuyuan.scm.customer.service.CustomerPriceResolver;
import com.xianshuyuan.scm.customer.service.CustomerService;
import com.xianshuyuan.scm.customer.service.ResolvedCustomerPrice;
import com.xianshuyuan.scm.order.dto.SalesOrderItemSaveRequest;
import com.xianshuyuan.scm.order.dto.SalesOrderSaveRequest;
import com.xianshuyuan.scm.order.entity.OrderSource;
import com.xianshuyuan.scm.order.entity.OrderStatus;
import com.xianshuyuan.scm.order.entity.SalesOrderEntity;
import com.xianshuyuan.scm.order.entity.SalesOrderItemEntity;
import com.xianshuyuan.scm.order.mapper.IdempotencyRecordMapper;
import com.xianshuyuan.scm.order.mapper.OrderOperationLogMapper;
import com.xianshuyuan.scm.order.mapper.SalesOrderItemMapper;
import com.xianshuyuan.scm.order.mapper.SalesOrderMapper;
import com.xianshuyuan.scm.product.entity.ProductSkuEntity;
import com.xianshuyuan.scm.product.entity.ProductSpuEntity;
import com.xianshuyuan.scm.product.entity.ProductType;
import com.xianshuyuan.scm.product.mapper.ProductSkuMapper;
import com.xianshuyuan.scm.product.mapper.ProductSpuMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SalesOrderApplicationServiceTest {
    @Test
    void updateWithNoRemovedLinesNeverInvokesEmptySoftDeleteAndUpdatesHeaderOnce() {
        SalesOrderMapper orders=mock(SalesOrderMapper.class);
        SalesOrderItemMapper items=mock(SalesOrderItemMapper.class);
        CustomerService customers=mock(CustomerService.class);
        CustomerPriceResolver pricing=mock(CustomerPriceResolver.class);
        ProductSkuMapper skus=mock(ProductSkuMapper.class);
        ProductSpuMapper spus=mock(ProductSpuMapper.class);
        SalesOrderEntity order=order(); SalesOrderItemEntity existing=item();
        when(orders.selectActiveByIdForUpdate(1L)).thenReturn(order);
        when(orders.updateById(any(SalesOrderEntity.class))).thenReturn(1);
        when(items.selectActiveByOrderIdForUpdate(1L)).thenReturn(List.of(existing));
        when(items.selectActiveByOrderId(1L)).thenReturn(List.of(existing));
        when(items.updateById(any(SalesOrderItemEntity.class))).thenReturn(1);
        when(customers.requireEnabled(2L)).thenReturn(customer());
        when(pricing.resolve(eq(2L),anyList(),any())).thenReturn(List.of(new ResolvedCustomerPrice(3L,new BigDecimal("8.0000"),com.xianshuyuan.scm.customer.service.PriceSource.MARKET,null)));
        when(skus.selectById(3L)).thenReturn(sku()); when(spus.selectById(4L)).thenReturn(spu());
        var service=service(orders,items,customers,pricing,skus,spus);

        service.update(1L,new SalesOrderSaveRequest(0,2L,OrderSource.NORMAL,null,null,List.of(new SalesOrderItemSaveRequest(5L,0,3L,"2.0000",null,false,null))));

        verify(items,never()).softDeleteByIds(anyLong(),anyList());
        verify(orders,times(1)).updateById(any(SalesOrderEntity.class));
    }

    private static SalesOrderApplicationService service(SalesOrderMapper orders,SalesOrderItemMapper items,CustomerService customers,CustomerPriceResolver pricing,ProductSkuMapper skus,ProductSpuMapper spus){
        var logs=mock(OrderOperationLogMapper.class); var numbers=mock(SalesOrderNumberGenerator.class); var query=mock(SalesOrderQueryService.class); var idMapper=mock(IdempotencyRecordMapper.class);
        return new SalesOrderApplicationService(orders,items,logs,customers,pricing,skus,spus,numbers,new SalesOrderValidator(),query,new IdempotencyService(idMapper,new ObjectMapper()),new ObjectMapper());
    }
    private static SalesOrderEntity order(){var x=new SalesOrderEntity();x.setId(1L);x.setCustomerId(2L);x.setStatus(OrderStatus.DRAFT);x.setVersion(0);return x;}
    private static SalesOrderItemEntity item(){var x=new SalesOrderItemEntity();x.setId(5L);x.setOrderId(1L);x.setSkuId(3L);x.setVersion(0);x.setOrderedLineAmount(new BigDecimal("16.0000"));return x;}
    private static CustomerEntity customer(){var x=new CustomerEntity();x.setId(2L);x.setCustomerCode("C2");x.setName("客户");return x;}
    private static ProductSkuEntity sku(){var x=new ProductSkuEntity();x.setId(3L);x.setSpuId(4L);x.setSkuCode("SKU3");x.setSpecName("规格");x.setSpecValues(java.util.Map.of());x.setSaleUnit("斤");x.setProductType(ProductType.STANDARD);return x;}
    private static ProductSpuEntity spu(){var x=new ProductSpuEntity();x.setId(4L);x.setSpuCode("SPU4");x.setName("商品");return x;}
}
