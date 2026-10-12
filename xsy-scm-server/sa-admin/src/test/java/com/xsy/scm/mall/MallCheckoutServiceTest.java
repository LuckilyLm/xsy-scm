package com.xsy.scm.mall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.mall.account.constant.MallErrorCode;
import com.xsy.scm.mall.account.domain.vo.RequestMallCustomer;
import com.xsy.scm.mall.address.dao.MallCustomerAddressDao;
import com.xsy.scm.mall.address.domain.vo.MallAddressVO;
import com.xsy.scm.mall.cart.dao.MallCartDao;
import com.xsy.scm.mall.catalog.domain.vo.MallProductVO;
import com.xsy.scm.mall.catalog.service.MallCatalogService;
import com.xsy.scm.mall.order.domain.form.MallCheckoutItemForm;
import com.xsy.scm.mall.order.domain.form.MallOrderSubmitForm;
import com.xsy.scm.mall.order.domain.vo.MallOrderSubmitVO;
import com.xsy.scm.mall.order.service.MallCheckoutService;
import com.xsy.scm.order.domain.dto.MallOrderCreationResult;
import com.xsy.scm.order.domain.form.SalesOrderAddForm;
import com.xsy.scm.order.domain.vo.SalesOrderDetailVO;
import com.xsy.scm.order.service.SalesOrderService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
        when(catalogService.visibleProducts(eq(customer), any())).thenReturn(List.of(product("5.0000")));
    }

    @AfterEach
    void tearDown() {
        SmartRequestUtil.remove();
    }

    @Test
    void firstCreationRemovesSubmittedSkuFromCart() {
        when(salesOrderService.createFromMall(any(), any(), eq("key")))
                .thenReturn(new MallOrderCreationResult(order(), false));

        service.submit(customer, form, "key");

        verify(cartDao).removeBySkus(7L, List.of(11L), "2:7");
    }

    @Test
    void replayDoesNotRemoveAReaddedCartItem() {
        when(salesOrderService.createFromMall(any(), any(), eq("key")))
                .thenReturn(new MallOrderCreationResult(order(), true));

        service.submit(customer, form, "key");

        verify(cartDao, never()).removeBySkus(any(), any(), any());
    }

    @Test
    void idempotencyIdentitySurvivesAnAddressEditedByAnotherAccount() {
        // 第一次下单成功后，同一客户的其他采购账号把共享地址改了内容。
        // 若幂等指纹直接取订单表单（含地址快照），重试时哈希就变了，原订单回放不出来。
        when(salesOrderService.replayMallOrder(any(), eq("key"))).thenReturn(Optional.empty());
        when(salesOrderService.createFromMall(any(), any(), eq("key")))
                .thenReturn(new MallOrderCreationResult(order(), false));
        when(addressDao.detailById(9L, 3L)).thenReturn(address()).thenReturn(editedAddress());

        service.submit(customer, form, "key");
        service.submit(customer, form, "key");

        ArgumentCaptor<SalesOrderAddForm> forms = ArgumentCaptor.forClass(SalesOrderAddForm.class);
        ArgumentCaptor<Object> requests = ArgumentCaptor.forClass(Object.class);
        verify(salesOrderService, times(2)).createFromMall(forms.capture(), requests.capture(), eq("key"));
        // 指纹不受地址内容影响：两次请求的幂等身份相同
        assertThat(requests.getAllValues().get(0)).isEqualTo(requests.getAllValues().get(1));
        // 而建单用的地址快照确实变了，说明指纹没有跟着地址内容走
        assertThat(forms.getAllValues().get(0).getAddress().getReceiverName()).isEqualTo("张三");
        assertThat(forms.getAllValues().get(1).getAddress().getReceiverName()).isEqualTo("李四");
    }

    @Test
    void alreadySucceededRequestIsReturnedBeforeThePriceCheck() {
        // 第一次下单已成功但响应丢失，客户重试前商品变了价：此时价格指纹与当前价都对不上，
        // 但幂等预检必须先把原订单还回去，否则客户会收到「价格已变化」而以为没下成。
        when(salesOrderService.replayMallOrder(any(), eq("key")))
                .thenReturn(Optional.of(new MallOrderCreationResult(order(), true)));
        when(catalogService.visibleProducts(eq(customer), any())).thenReturn(List.of(product("9.0000")));

        MallOrderSubmitVO result = service.submit(customer, form, "key");

        assertThat(result.getOrderId()).isEqualTo(21L);
        // 命中重放：不再读商品、不再按当前价校验、也不再清购物车（首次提交已清过）
        verify(catalogService, never()).visibleProducts(any(), any());
        verify(cartDao, never()).removeBySkus(any(), any(), any());
    }

    @Test
    void newRequestStillFailsWhenThePriceChanged() {
        when(salesOrderService.replayMallOrder(any(), eq("key"))).thenReturn(Optional.empty());
        when(catalogService.visibleProducts(eq(customer), any())).thenReturn(List.of(product("9.0000")));

        assertThatThrownBy(() -> service.submit(customer, form, "key"))
                .isInstanceOfSatisfying(ScmBusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(MallErrorCode.CHECKOUT_PRICE_CHANGED));
        verify(salesOrderService, never()).createFromMall(any(), any(), any());
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

    /** 同一个地址 ID，但内容已被其他采购账号编辑 */
    private static MallAddressVO editedAddress() {
        MallAddressVO address = new MallAddressVO();
        address.setAddressId(3L);
        address.setReceiverName("李四");
        address.setReceiverPhone("13900000000");
        address.setDetailAddress("改过的地址");
        return address;
    }

    private static MallProductVO product(String unitPrice) {
        MallProductVO product = new MallProductVO();
        product.setSkuId(11L);
        product.setUnitPrice(new BigDecimal(unitPrice));
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
