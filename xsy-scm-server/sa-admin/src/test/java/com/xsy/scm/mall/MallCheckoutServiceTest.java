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
import com.xsy.scm.mall.order.domain.form.MallCheckoutPreviewForm;
import com.xsy.scm.mall.order.domain.form.MallOrderSubmitForm;
import com.xsy.scm.mall.order.domain.vo.MallOrderSubmitVO;
import com.xsy.scm.mall.order.domain.vo.MallWarehouseOptionVO;
import com.xsy.scm.mall.order.service.MallCheckoutService;
import com.xsy.scm.mall.order.service.MallWarehouseService;
import com.xsy.scm.order.domain.dto.MallOrderCreationResult;
import com.xsy.scm.order.domain.form.SalesOrderAddForm;
import com.xsy.scm.order.domain.vo.SalesOrderDetailVO;
import com.xsy.scm.order.domain.vo.SalesOrderItemVO;
import com.xsy.scm.order.service.SalesOrderService;
import com.xsy.scm.promotion.domain.vo.PromotionDiscountVO;
import com.xsy.scm.promotion.service.PromotionDiscountService;
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
    private final PromotionDiscountService promotionDiscountService = mock(PromotionDiscountService.class);
    private final MallWarehouseService warehouseService = mock(MallWarehouseService.class);
    private final MallCheckoutService service = new MallCheckoutService(catalogService, addressDao, cartDao,
            salesOrderService, promotionDiscountService, warehouseService);
    private final RequestMallCustomer customer = customer();
    private final MallOrderSubmitForm form = form();

    @BeforeEach
    void setUp() {
        SmartRequestUtil.setRequestUser(customer);
        when(addressDao.detailById(9L, 3L)).thenReturn(address());
        when(warehouseService.require(9L, 3L, 8L)).thenReturn(8L);
        when(catalogService.visibleProducts(eq(customer), any(), eq(3L), eq(8L)))
                .thenReturn(List.of(product("5.0000")));
        PromotionDiscountVO discount = new PromotionDiscountVO();
        discount.setSpecialDiscount(new BigDecimal("0.0000"));
        discount.setActivityDiscount(new BigDecimal("0.0000"));
        discount.setCouponDiscount(new BigDecimal("0.0000"));
        discount.setDiscountAmount(new BigDecimal("0.0000"));
        discount.setFinalAmount(new BigDecimal("10.0000"));
        when(promotionDiscountService.preview(any())).thenReturn(discount);
    }

    @Test
    void newOrderDoesNotRequirePaymentMethodAndDoesNotForwardLegacySelection() {
        when(salesOrderService.createFromMall(any(), any(), eq("key")))
                .thenReturn(new MallOrderCreationResult(order(), false));
        form.setSettlementMethod(null);
        service.submit(customer, form, "key");
        when(salesOrderService.createFromMall(any(), any(), eq("legacy-selection")))
                .thenReturn(new MallOrderCreationResult(order(), false));
        form.setSettlementMethod("WALLET");
        service.submit(customer, form, "legacy-selection");
        ArgumentCaptor<SalesOrderAddForm> saved = ArgumentCaptor.forClass(SalesOrderAddForm.class);
        verify(salesOrderService, times(2)).createFromMall(saved.capture(), any(), any());
        assertThat(saved.getAllValues()).allSatisfy(order -> assertThat(order.getSettlementMethod()).isNull());
        assertThat(saved.getValue().getOrderSource()).isEqualTo("MALL");
        assertThat(saved.getValue().getCustomerId()).isEqualTo(customer.getCustomerId());
    }

    @AfterEach
    void tearDown() {
        SmartRequestUtil.remove();
    }

    @Test
    void outOfStockButSellableLineStillSubmits() {
        // 先接单后采购：库存为 0 只影响展示（预订），不能把已经加入购物车的行拦在结算提交。
        MallProductVO outOfStock = product("5.0000");
        outOfStock.setStockStatus("OUT_OF_STOCK");
        outOfStock.setAvailableQty(new BigDecimal("0.0000"));
        when(catalogService.visibleProducts(eq(customer), any(), eq(3L), eq(8L)))
                .thenReturn(List.of(outOfStock));
        when(salesOrderService.createFromMall(any(), any(), eq("key")))
                .thenReturn(new MallOrderCreationResult(order(), false));

        service.submit(customer, form, "key");

        verify(salesOrderService).createFromMall(any(), any(), eq("key"));
    }

    @Test
    void zeroPricedLineIsRejectedInsteadOfBeingFree() {
        // 商品页把 0 元显示为「询价」；提交也必须按未报价拒绝，不能生成 0 元订单。
        when(catalogService.visibleProducts(eq(customer), any(), eq(3L), eq(8L)))
                .thenReturn(List.of(product("0.0000")));

        assertThatThrownBy(() -> service.submit(customer, form, "key"))
                .isInstanceOfSatisfying(ScmBusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(MallErrorCode.CHECKOUT_ITEM_UNAVAILABLE));
        verify(salesOrderService, never()).createFromMall(any(), any(), any());
    }

    @Test
    void unsellableLineIsRejectedBeforeOrderCreation() {
        MallProductVO unsellable = product("5.0000");
        unsellable.setSellable(false);
        when(catalogService.visibleProducts(eq(customer), any(), eq(3L), eq(8L)))
                .thenReturn(List.of(unsellable));

        assertThatThrownBy(() -> service.submit(customer, form, "key"))
                .isInstanceOfSatisfying(ScmBusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(MallErrorCode.CHECKOUT_ITEM_UNAVAILABLE));
        verify(salesOrderService, never()).createFromMall(any(), any(), any());
    }

    @Test
    void previewShowsOutOfStockLineAsAvailableAndZeroPriceAsNoPrice() {
        MallProductVO outOfStock = product("5.0000");
        outOfStock.setStockStatus("OUT_OF_STOCK");
        outOfStock.setAvailableQty(new BigDecimal("0.0000"));
        MallProductVO zeroPrice = new MallProductVO();
        zeroPrice.setSkuId(12L);
        zeroPrice.setUnitPrice(new BigDecimal("0.0000"));
        zeroPrice.setSellable(true);
        zeroPrice.setStockStatus("AVAILABLE");
        zeroPrice.setAvailableQty(new BigDecimal("10.0000"));
        when(warehouseService.options(9L, 3L)).thenReturn(List.of(warehouseOption(8L)));
        when(catalogService.visibleProducts(eq(customer), any(), eq(3L), eq(8L)))
                .thenReturn(List.of(outOfStock, zeroPrice));

        MallCheckoutPreviewForm previewForm = new MallCheckoutPreviewForm();
        MallCheckoutItemForm first = new MallCheckoutItemForm();
        first.setSkuId(11L);
        first.setQuantity("2.0000");
        MallCheckoutItemForm second = new MallCheckoutItemForm();
        second.setSkuId(12L);
        second.setQuantity("1.0000");
        previewForm.setItems(List.of(first, second));
        previewForm.setAddressId(3L);
        previewForm.setWarehouseId(8L);

        var preview = service.preview(customer, previewForm);

        assertThat(preview.getItems()).extracting(item -> item.getSkuId() + ":" + item.getAvailability())
                .containsExactly("11:AVAILABLE", "12:NO_PRICE");
        assertThat(preview.getUnavailableSkuIds()).containsExactly(12L);
        assertThat(preview.getEstimatedAmount()).isEqualByComparingTo("10.0000");
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
    void finalLockedPriceChangeRejectsBeforeCartCleanup() {
        when(salesOrderService.createFromMall(any(), any(), eq("key")))
                .thenReturn(new MallOrderCreationResult(order("6.0000"), false));

        assertThatThrownBy(() -> service.submit(customer, form, "key"))
                .isInstanceOfSatisfying(ScmBusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(MallErrorCode.CHECKOUT_PRICE_CHANGED));
        verify(cartDao, never()).removeBySkus(any(), any(), any());
    }

    @Test
    void duplicateFinalOrderLineRejectsBeforeCartCleanup() {
        SalesOrderDetailVO order = order();
        order.setItems(List.of(order.getItems().get(0), order.getItems().get(0)));
        when(salesOrderService.createFromMall(any(), any(), eq("key")))
                .thenReturn(new MallOrderCreationResult(order, false));

        assertThatThrownBy(() -> service.submit(customer, form, "key"))
                .isInstanceOfSatisfying(ScmBusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(MallErrorCode.CHECKOUT_PRICE_CHANGED));
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
        when(catalogService.visibleProducts(eq(customer), any(), eq(3L), eq(8L)))
                .thenReturn(List.of(product("9.0000")));

        MallOrderSubmitVO result = service.submit(customer, form, "key");

        assertThat(result.getOrderId()).isEqualTo(21L);
        // 命中重放：不再读商品、不再按当前价校验、也不再清购物车（首次提交已清过）
        verify(catalogService, never()).visibleProducts(any(), any());
        verify(cartDao, never()).removeBySkus(any(), any(), any());
    }

    @Test
    void newRequestStillFailsWhenThePriceChanged() {
        when(salesOrderService.replayMallOrder(any(), eq("key"))).thenReturn(Optional.empty());
        when(catalogService.visibleProducts(eq(customer), any(), eq(3L), eq(8L)))
                .thenReturn(List.of(product("9.0000")));

        assertThatThrownBy(() -> service.submit(customer, form, "key"))
                .isInstanceOfSatisfying(ScmBusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(MallErrorCode.CHECKOUT_PRICE_CHANGED));
        verify(salesOrderService, never()).createFromMall(any(), any(), any());
    }

    @Test
    void replayedRequestStillReturnsWhenTheAddressWasDeleted() {
        // 第一次下单成功后，同一客户的其他采购账号把该收货地址删了。
        // 重试必须先回放原订单；若先读地址，就会抛「地址不存在」，把一笔已成功的订单藏起来。
        when(salesOrderService.replayMallOrder(any(), eq("key")))
                .thenReturn(Optional.of(new MallOrderCreationResult(order(), true)));
        when(addressDao.detailById(9L, 3L)).thenReturn(null);

        MallOrderSubmitVO result = service.submit(customer, form, "key");

        assertThat(result.getOrderId()).isEqualTo(21L);
        verify(addressDao, never()).detailById(any(), any());
        verify(catalogService, never()).visibleProducts(any(), any());
    }

    @Test
    void newRequestStillFailsWhenTheAddressIsGone() {
        // 幂等预检提前不能把地址校验弄丢：没有已成功的原订单时，地址不存在仍要拒绝
        when(salesOrderService.replayMallOrder(any(), eq("key"))).thenReturn(Optional.empty());
        when(addressDao.detailById(9L, 3L)).thenReturn(null);

        assertThatThrownBy(() -> service.submit(customer, form, "key"))
                .isInstanceOfSatisfying(ScmBusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(MallErrorCode.ADDRESS_NOT_FOUND));
        verify(salesOrderService, never()).createFromMall(any(), any(), any());
    }

    @Test
    void newOrderWithoutSettlementStillChecksAddressAndCreatesOrder() {
        form.setSettlementMethod(null);
        when(salesOrderService.createFromMall(any(), any(), eq("new-key")))
                .thenReturn(new MallOrderCreationResult(order(), false));
        assertThat(service.submit(customer, form, "new-key").getOrderId()).isEqualTo(21L);
        verify(addressDao).detailById(9L, 3L);
        verify(salesOrderService).createFromMall(any(), any(), eq("new-key"));
    }

    @Test
    void oldSuccessfulRequestWithoutMethodStillReplays() {
        form.setSettlementMethod(null);
        when(salesOrderService.replayMallOrder(any(), eq("old-key")))
                .thenReturn(Optional.of(new MallOrderCreationResult(order(), true)));

        assertThat(service.submit(customer, form, "old-key").getOrderId()).isEqualTo(21L);
        verify(addressDao, never()).detailById(any(), any());
    }

    @Test
    void legacyCreditSelectionDoesNotBlockOrderIntake() {
        form.setSettlementMethod("CREDIT");
        when(salesOrderService.createFromMall(any(), any(), eq("new-key")))
                .thenReturn(new MallOrderCreationResult(order(), false));
        assertThat(service.submit(customer, form, "new-key").getOrderId()).isEqualTo(21L);
        ArgumentCaptor<SalesOrderAddForm> saved = ArgumentCaptor.forClass(SalesOrderAddForm.class);
        verify(salesOrderService).createFromMall(saved.capture(), any(), eq("new-key"));
        assertThat(saved.getValue().getSettlementMethod()).isNull();
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
        form.setWarehouseId(8L);
        form.setPriceFingerprint("f97b78895d41dd981ee076cff835a4a47c828df484b389cc9c86f423b8972793");
        form.setSettlementMethod("COD");
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
        product.setSellable(true);
        product.setStockStatus("AVAILABLE");
        product.setAvailableQty(new BigDecimal("20.0000"));
        return product;
    }

    private static MallWarehouseOptionVO warehouseOption(Long warehouseId) {
        MallWarehouseOptionVO option = new MallWarehouseOptionVO();
        option.setWarehouseId(warehouseId);
        option.setWarehouseName("测试仓");
        return option;
    }

    private static SalesOrderDetailVO order() {
        return order("5.0000");
    }

    private static SalesOrderDetailVO order(String lockedUnitPrice) {
        SalesOrderDetailVO order = new SalesOrderDetailVO();
        order.setOrderId(21L);
        order.setOrderNo("SO21");
        order.setOrderedTotalAmount(new BigDecimal("10.0000"));
        SalesOrderItemVO item = new SalesOrderItemVO();
        item.setSkuId(11L);
        item.setOrderedQuantity(new BigDecimal("2.0000"));
        item.setLockedUnitPrice(new BigDecimal(lockedUnitPrice));
        order.setItems(List.of(item));
        return order;
    }
}
