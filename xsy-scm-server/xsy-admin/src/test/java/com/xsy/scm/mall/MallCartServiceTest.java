package com.xsy.scm.mall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.xsy.scm.mall.account.domain.vo.RequestMallCustomer;
import com.xsy.scm.mall.cart.dao.MallCartDao;
import com.xsy.scm.mall.cart.domain.entity.MallCartItemEntity;
import com.xsy.scm.mall.cart.domain.vo.MallCartItemVO;
import com.xsy.scm.mall.cart.service.MallCartService;
import com.xsy.scm.mall.catalog.domain.vo.MallProductVO;
import com.xsy.scm.mall.catalog.service.MallCatalogService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class MallCartServiceTest {
    private final MallCartDao cartDao = mock(MallCartDao.class);
    private final MallCatalogService catalogService = mock(MallCatalogService.class);
    private final MallCartService service = new MallCartService(cartDao, catalogService);
    private final RequestMallCustomer customer = customer();

    @Test
    void outOfStockLineStaysAvailableForPreorder() {
        MallProductVO product = product("5.0000");
        product.setStockStatus("OUT_OF_STOCK");
        product.setAvailableQty(BigDecimal.ZERO);
        when(cartDao.listByAccount(7L)).thenReturn(List.of(row()));
        when(catalogService.visibleProducts(customer, List.of(11L))).thenReturn(List.of(product));

        MallCartItemVO item = service.cart(customer).getItems().getFirst();

        assertThat(item.getAvailability()).isEqualTo("AVAILABLE");
        assertThat(item.getLineAmount()).isEqualByComparingTo("10.0000");
    }

    @Test
    void zeroPriceLineIsNoPriceNotFree() {
        when(cartDao.listByAccount(7L)).thenReturn(List.of(row()));
        when(catalogService.visibleProducts(customer, List.of(11L))).thenReturn(List.of(product("0.0000")));

        MallCartItemVO item = service.cart(customer).getItems().getFirst();

        assertThat(item.getAvailability()).isEqualTo("NO_PRICE");
        assertThat(item.getUnavailableReason()).isEqualTo("NO_PRICE_SOURCE");
        assertThat(service.cart(customer).getEstimatedAmount()).isEqualByComparingTo("0.0000");
    }

    @Test
    void unsellableLineIsUnavailable() {
        MallProductVO product = product("5.0000");
        product.setSellable(false);
        product.setUnavailableReason("NOT_VISIBLE");
        when(cartDao.listByAccount(7L)).thenReturn(List.of(row()));
        when(catalogService.visibleProducts(customer, List.of(11L))).thenReturn(List.of(product));

        MallCartItemVO item = service.cart(customer).getItems().getFirst();

        assertThat(item.getAvailability()).isEqualTo("UNAVAILABLE");
        assertThat(item.getUnavailableReason()).isEqualTo("NOT_VISIBLE");
    }

    private static RequestMallCustomer customer() {
        RequestMallCustomer customer = new RequestMallCustomer();
        customer.setAccountId(7L);
        customer.setCustomerId(9L);
        return customer;
    }

    private static MallCartItemEntity row() {
        MallCartItemEntity row = new MallCartItemEntity();
        row.setSkuId(11L);
        row.setQuantity(new BigDecimal("2.0000"));
        return row;
    }

    private static MallProductVO product(String unitPrice) {
        MallProductVO product = new MallProductVO();
        product.setSkuId(11L);
        product.setUnitPrice(new BigDecimal(unitPrice));
        product.setSellable(true);
        product.setStockStatus("AVAILABLE");
        product.setAvailableQty(new BigDecimal("20.0000"));
        return product;
    }
}
