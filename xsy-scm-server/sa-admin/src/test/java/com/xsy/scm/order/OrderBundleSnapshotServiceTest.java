package com.xsy.scm.order;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.mall.catalog.service.MallStockAvailabilityService;
import com.xsy.scm.order.dao.OrderBundleComponentDao;
import com.xsy.scm.order.domain.entity.OrderBundleComponentEntity;
import com.xsy.scm.order.domain.entity.SalesOrderItemEntity;
import com.xsy.scm.order.service.OrderBundleSnapshotService;
import com.xsy.scm.product.service.ProductBundleComponentQueryService;
import com.xsy.scm.product.service.ProductSkuOptionQueryService;
import com.xsy.scm.product.domain.entity.ProductBundleComponentEntity;
import com.xsy.scm.product.domain.vo.ProductSkuOptionVO;
import java.math.BigDecimal;
import java.util.List;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class OrderBundleSnapshotServiceTest {

    private final ProductBundleComponentQueryService componentDao = mock(ProductBundleComponentQueryService.class);
    private final OrderBundleComponentDao snapshotDao = mock(OrderBundleComponentDao.class);
    private final ProductSkuOptionQueryService skuDao = mock(ProductSkuOptionQueryService.class);
    private final MallStockAvailabilityService stock = mock(MallStockAvailabilityService.class);
    private final OrderBundleSnapshotService service =
            new OrderBundleSnapshotService(componentDao, snapshotDao, skuDao, stock);

    private final SalesOrderItemEntity orderItem = new SalesOrderItemEntity();

    @BeforeEach
    void prepare() {
        var employee = new RequestEmployee();
        employee.setEmployeeId(1L);
        employee.setActualName("Test");
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        SmartRequestUtil.setRequestUser(employee);

        orderItem.setOrderId(50L);
        orderItem.setId(51L);
        orderItem.setSkuId(200L);
        orderItem.setActualQuantity(new BigDecimal("1.0000"));
        orderItem.setLockedUnitPrice(new BigDecimal("80.0000"));
        when(componentDao.listByBundleSku(200L)).thenReturn(List.of(
                component(11L, 301L, "2.0000"),
                component(12L, 302L, "3.0000")));
    }

    @AfterEach
    void clear() {
        SmartRequestUtil.remove();
    }

    @Test
    void freezesMarketPriceShareAndChosenWarehouseForEveryComponent() {
        when(skuDao.byIds(List.of(301L, 302L))).thenReturn(List.of(
                sku(301L, "10.0000"), sku(302L, "10.0000")));
        when(stock.requireConversion(42L, 301L, "kg"))
                .thenReturn(new MallStockAvailabilityService.Conversion("kg", BigDecimal.ONE));
        when(stock.requireConversion(42L, 302L, "kg"))
                .thenReturn(new MallStockAvailabilityService.Conversion("kg", BigDecimal.ONE));

        assertThat(service.freeze(orderItem, 42L)).isTrue();

        ArgumentCaptor<OrderBundleComponentEntity> captured =
                ArgumentCaptor.forClass(OrderBundleComponentEntity.class);
        verify(snapshotDao, times(2)).insert(captured.capture());
        var rows = captured.getAllValues();
        assertThat(rows.get(0).getWarehouseId()).isEqualTo(42L);
        assertThat(rows.get(0).getComponentSaleQuantitySnapshot()).isEqualByComparingTo("2.0000");
        assertThat(rows.get(0).getMarketUnitPriceSnapshot()).isEqualByComparingTo("10.0000");
        assertThat(rows.get(0).getMarketReferenceAmountSnapshot()).isEqualByComparingTo("20.00000000");
        assertThat(rows.get(0).getAllocatedSaleAmountSnapshot()).isEqualByComparingTo("32.0000");
        assertThat(rows.get(1).getMarketReferenceAmountSnapshot()).isEqualByComparingTo("30.00000000");
        assertThat(rows.get(1).getAllocatedSaleAmountSnapshot()).isEqualByComparingTo("48.0000");
        assertThat(rows.get(0).getAllocatedSaleAmountSnapshot()
                .add(rows.get(1).getAllocatedSaleAmountSnapshot())).isEqualByComparingTo("80.0000");
    }

    @Test
    void bundleCannotUseImplicitDefaultWarehouse() {
        assertThatThrownBy(() -> service.freeze(orderItem, null))
                .isInstanceOf(ScmBusinessException.class);
        verifyNoInteractions(skuDao, snapshotDao, stock);
    }

    @Test
    void missingComponentMarketReferenceMustFailWithoutWritingSnapshot() {
        when(skuDao.byIds(List.of(301L, 302L))).thenReturn(List.of(
                sku(301L, "10.0000"), sku(302L, null)));
        when(stock.requireConversion(42L, 301L, "kg"))
                .thenReturn(new MallStockAvailabilityService.Conversion("kg", BigDecimal.ONE));

        assertThatThrownBy(() -> service.freeze(orderItem, 42L))
                .isInstanceOf(ScmBusinessException.class);
        verifyNoInteractions(snapshotDao);
    }

    private static ProductBundleComponentEntity component(Long id, Long skuId, String quantity) {
        var row = new ProductBundleComponentEntity();
        row.setId(id);
        row.setVersion(1);
        row.setBundleSkuId(200L);
        row.setComponentSkuId(skuId);
        row.setQuantityPerBundleSaleUnit(new BigDecimal(quantity));
        return row;
    }

    private static ProductSkuOptionVO sku(Long skuId, String price) {
        var row = new ProductSkuOptionVO();
        row.setSkuId(skuId);
        row.setSkuCode("S-" + skuId);
        row.setProductName("组成商品" + skuId);
        row.setSaleUnit("kg");
        row.setStatus("ON_SHELF");
        row.setSpuStatus("ON_SHELF");
        row.setCategoryStatus("ENABLED");
        row.setMarketPrice(price == null ? null : new BigDecimal(price));
        return row;
    }
}
