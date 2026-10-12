package com.xsy.scm.mall;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xsy.scm.mall.account.domain.vo.RequestMallCustomer;
import com.xsy.scm.mall.address.dao.MallCustomerAddressDao;
import com.xsy.scm.mall.address.domain.vo.MallAddressVO;
import com.xsy.scm.mall.cart.dao.MallCartDao;
import com.xsy.scm.mall.catalog.domain.vo.MallProductVO;
import com.xsy.scm.mall.catalog.service.MallCatalogService;
import com.xsy.scm.mall.order.domain.form.MallCheckoutItemForm;
import com.xsy.scm.mall.order.domain.form.MallOrderSubmitForm;
import com.xsy.scm.mall.order.service.MallCheckoutService;
import com.xsy.scm.order.domain.dto.MallOrderCreationResult;
import com.xsy.scm.order.domain.vo.SalesOrderDetailVO;
import com.xsy.scm.order.service.SalesOrderService;
import java.math.BigDecimal;
import java.util.List;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MallCheckoutServiceTest {
    private final MallCatalogService catalogService = mock(MallCatalogService.class);
    private final MallCustomerAddressDao addressDao = mock(MallCustomerAddressDao.class);
    private final MallCartDao cartDao = mock(MallCartDao.class);
    private final SalesOrderService salesOrderService = mock(SalesOrderService.class);
    private final MallCheckoutService service = new MallCheckoutService(catalogService, addressDao, cartDao,
            salesOrderService);
    private final RequestMallCustomer customer = customer();
    private final MallOrderSubmitForm form = form();

    @BeforeEach
    void setUp() {
        SmartRequestUtil.setRequestUser(customer);
        when(addressDao.detailById(9L, 3L)).thenReturn(address());
        when(catalogService.visibleProducts(eq(customer), any())).thenReturn(List.of(product()));
    }

    @AfterEach
    void tearDown() {
        SmartRequestUtil.remove();
    }

    @Test
    void firstCreationRemovesSubmittedSkuFromCart() {
        when(salesOrderService.createFromMall(any(), eq("key")))
                .thenReturn(new MallOrderCreationResult(order(), false));

        service.submit(customer, form, "key");

        verify(cartDao).removeBySkus(7L, List.of(11L), "2:7");
    }

    @Test
    void replayDoesNotRemoveAReaddedCartItem() {
        when(salesOrderService.createFromMall(any(), eq("key")))
                .thenReturn(new MallOrderCreationResult(order(), true));

        service.submit(customer, form, "key");

        verify(cartDao, never()).removeBySkus(any(), any(), any());
    }

    private static RequestMallCustomer customer() {
        RequestMallCustomer customer = new RequestMallCustomer();
        customer.setAccountId(7L);
        customer.setCustomerId(9L);
        return customer;
    }

    private static MallOrderSubmitForm form() {
        MallCheckoutItemForm item = new MallCheckoutItemForm();
        item.setSkuId(11L);
        item.setQuantity("2.0000");
        MallOrderSubmitForm form = new MallOrderSubmitForm();
        form.setItems(List.of(item));
        form.setAddressId(3L);
        form.setPriceFingerprint("f97b78895d41dd981ee076cff835a4a47c828df484b389cc9c86f423b8972793");
        return form;
    }

    private static MallAddressVO address() {
        MallAddressVO address = new MallAddressVO();
        address.setAddressId(3L);
        address.setReceiverName("张三");
        address.setReceiverPhone("13800000000");
        address.setDetailAddress("测试地址");
        return address;
    }

    private static MallProductVO product() {
        MallProductVO product = new MallProductVO();
        product.setSkuId(11L);
        product.setUnitPrice(new BigDecimal("5.0000"));
        return product;
    }

    private static SalesOrderDetailVO order() {
        SalesOrderDetailVO order = new SalesOrderDetailVO();
        order.setOrderId(21L);
        order.setOrderNo("SO21");
        order.setOrderedTotalAmount(new BigDecimal("10.0000"));
        return order;
    }
}
