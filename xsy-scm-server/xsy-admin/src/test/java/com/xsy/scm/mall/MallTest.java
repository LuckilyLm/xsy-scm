package com.xsy.scm.mall;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sun.net.httpserver.HttpServer;
import com.xsy.scm.admin.module.system.login.domain.RequestEmployee;
import com.xsy.scm.balance.constant.BalanceErrorCode;
import com.xsy.scm.balance.service.CustomerBalanceService;
import com.xsy.scm.base.common.annoation.NoNeedLogin;
import com.xsy.scm.base.common.domain.ResponseDTO;
import com.xsy.scm.base.common.enumeration.UserTypeEnum;
import com.xsy.scm.base.common.util.XsyRequestUtil;
import com.xsy.scm.base.module.support.file.service.FileService;
import com.xsy.scm.base.module.support.redis.RedisService;
import com.xsy.scm.base.module.support.securityprotect.domain.LoginFailEntity;
import com.xsy.scm.base.module.support.securityprotect.service.SecurityLoginService;
import com.xsy.scm.base.module.support.securityprotect.service.SecurityPasswordService;
import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.common.ScmW6PgITBase;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.customer.dao.CustomerFrequentSkuDao;
import com.xsy.scm.customer.domain.entity.CustomerEntity;
import com.xsy.scm.customer.domain.form.CustomerAddForm;
import com.xsy.scm.customer.domain.form.CustomerStatusForm;
import com.xsy.scm.customer.domain.form.CustomerUpdateForm;
import com.xsy.scm.customer.service.CustomerService;
import com.xsy.scm.finance.domain.dto.FinanceCreditExposureDto;
import com.xsy.scm.finance.domain.form.FinanceCodCollectionForm;
import com.xsy.scm.finance.domain.vo.CustomerCreditCheckVO;
import com.xsy.scm.finance.service.FinanceCreditExposureQueryService;
import com.xsy.scm.finance.service.FinanceOrderPayableQueryService;
import com.xsy.scm.finance.service.FinanceReceiptService;
import com.xsy.scm.finance.support.FinancePaymentReceiptFact;
import com.xsy.scm.inventory.domain.form.InventoryOutboundAddForm;
import com.xsy.scm.inventory.service.InventoryFulfillmentService;
import com.xsy.scm.inventory.service.InventoryOutboundService;
import com.xsy.scm.mall.account.constant.MallErrorCode;
import com.xsy.scm.mall.account.constant.MallLoginMethodEnum;
import com.xsy.scm.mall.account.dao.MallCustomerAccountDao;
import com.xsy.scm.mall.account.domain.entity.MallCustomerAccountEntity;
import com.xsy.scm.mall.account.domain.form.MallAccountCreateForm;
import com.xsy.scm.mall.account.domain.form.MallAccountStatusForm;
import com.xsy.scm.mall.account.domain.form.MallLoginForm;
import com.xsy.scm.mall.account.domain.form.MallPasswordChangeForm;
import com.xsy.scm.mall.account.domain.form.MallPasswordResetForm;
import com.xsy.scm.mall.account.domain.form.MallWechatPhoneLoginForm;
import com.xsy.scm.mall.account.domain.vo.MallAccountVO;
import com.xsy.scm.mall.account.domain.vo.RequestMallCustomer;
import com.xsy.scm.mall.account.service.MallAccountService;
import com.xsy.scm.mall.account.service.MallAuthService;
import com.xsy.scm.mall.account.service.MallWechatService;
import com.xsy.scm.mall.address.dao.MallCustomerAddressDao;
import com.xsy.scm.mall.address.domain.form.MallAddressSaveForm;
import com.xsy.scm.mall.address.domain.vo.MallAddressVO;
import com.xsy.scm.mall.address.domain.vo.MallMapLocationVO;
import com.xsy.scm.mall.address.service.MallAddressService;
import com.xsy.scm.mall.address.service.MallMapService;
import com.xsy.scm.mall.aftersale.constant.MallAfterSaleDisplayStatusEnum;
import com.xsy.scm.mall.aftersale.dao.MallAfterSaleFundingSourceDao;
import com.xsy.scm.mall.aftersale.domain.form.MallAfterSaleApplyForm;
import com.xsy.scm.mall.aftersale.domain.vo.MallAfterSaleFundingTimeVO;
import com.xsy.scm.mall.aftersale.domain.vo.MallAfterSaleRefundVO;
import com.xsy.scm.mall.aftersale.domain.vo.MallAfterSaleVO;
import com.xsy.scm.mall.aftersale.service.MallAfterSaleQueryService;
import com.xsy.scm.mall.aftersale.service.MallAfterSaleService;
import com.xsy.scm.mall.cart.dao.MallCartDao;
import com.xsy.scm.mall.cart.domain.entity.MallCartItemEntity;
import com.xsy.scm.mall.cart.domain.form.MallCartAddForm;
import com.xsy.scm.mall.cart.domain.vo.MallCartItemVO;
import com.xsy.scm.mall.cart.domain.vo.MallCartVO;
import com.xsy.scm.mall.cart.service.MallCartService;
import com.xsy.scm.mall.catalog.dao.MallCatalogDao;
import com.xsy.scm.mall.catalog.dao.MallStockDao;
import com.xsy.scm.mall.catalog.domain.form.MallProductQueryForm;
import com.xsy.scm.mall.catalog.domain.vo.MallCategoryVO;
import com.xsy.scm.mall.catalog.domain.vo.MallProductVO;
import com.xsy.scm.mall.catalog.domain.vo.MallStockFact;
import com.xsy.scm.mall.catalog.service.MallCatalogService;
import com.xsy.scm.mall.catalog.service.MallStockAvailabilityService;
import com.xsy.scm.mall.decoration.controller.MallDecorationController;
import com.xsy.scm.mall.decoration.dao.MallDecorationDao;
import com.xsy.scm.mall.decoration.domain.form.MallPagePublishForm;
import com.xsy.scm.mall.decoration.domain.vo.MallPageVO;
import com.xsy.scm.mall.decoration.domain.vo.MallStoreVO;
import com.xsy.scm.mall.decoration.service.MallDecorationService;
import com.xsy.scm.mall.funds.dao.MallFundsSourceDao;
import com.xsy.scm.mall.funds.domain.form.MallReceivableQueryForm;
import com.xsy.scm.mall.funds.domain.form.MallWalletMovementQueryForm;
import com.xsy.scm.mall.funds.service.MallFundsQueryService;
import com.xsy.scm.mall.home.controller.MallHomeAdminController;
import com.xsy.scm.mall.home.controller.MallHomeController;
import com.xsy.scm.mall.home.domain.form.MallHomeBlockForm;
import com.xsy.scm.mall.home.domain.form.MallHomeDraftSaveForm;
import com.xsy.scm.mall.home.domain.form.MallHomePublishForm;
import com.xsy.scm.mall.home.domain.form.MallHomeRollbackForm;
import com.xsy.scm.mall.home.domain.vo.MallThemeVO;
import com.xsy.scm.mall.home.service.MallHomeLayoutService;
import com.xsy.scm.mall.home.service.MallHomeProjectionService;
import com.xsy.scm.mall.home.service.MallThemeColors;
import com.xsy.scm.mall.order.constant.MallOrderDisplayStatusEnum;
import com.xsy.scm.mall.order.constant.ScmSettlementMethodEnum;
import com.xsy.scm.mall.order.constant.ScmSharedSettlementPolicy;
import com.xsy.scm.mall.order.dao.MallWarehouseDao;
import com.xsy.scm.mall.order.domain.form.MallCheckoutItemForm;
import com.xsy.scm.mall.order.domain.form.MallCheckoutPreviewForm;
import com.xsy.scm.mall.order.domain.form.MallCustomerWarehouseBindingForm;
import com.xsy.scm.mall.order.domain.form.MallOrderCancelForm;
import com.xsy.scm.mall.order.domain.form.MallOrderQueryForm;
import com.xsy.scm.mall.order.domain.form.MallOrderSubmitForm;
import com.xsy.scm.mall.order.domain.form.MallOrderSummaryQueryForm;
import com.xsy.scm.mall.order.domain.vo.MallCheckoutPreviewVO;
import com.xsy.scm.mall.order.domain.vo.MallOrderItemVO;
import com.xsy.scm.mall.order.domain.vo.MallOrderProgressNodeVO;
import com.xsy.scm.mall.order.domain.vo.MallOrderSubmitVO;
import com.xsy.scm.mall.order.domain.vo.MallOrderSummaryVO;
import com.xsy.scm.mall.order.domain.vo.MallOrderTimelineVO;
import com.xsy.scm.mall.order.domain.vo.MallOrderVO;
import com.xsy.scm.mall.order.domain.vo.MallReorderVO;
import com.xsy.scm.mall.order.domain.vo.MallSettlementOptionVO;
import com.xsy.scm.mall.order.domain.vo.MallWarehouseOptionVO;
import com.xsy.scm.mall.order.service.MallCheckoutService;
import com.xsy.scm.mall.order.service.MallOrderQueryService;
import com.xsy.scm.mall.order.service.MallOrderService;
import com.xsy.scm.mall.order.service.MallPaymentService;
import com.xsy.scm.mall.order.service.MallSettlementService;
import com.xsy.scm.mall.order.service.MallWarehouseAdminService;
import com.xsy.scm.mall.order.service.MallWarehouseService;
import com.xsy.scm.mall.permission.MallPermission;
import com.xsy.scm.mall.promotion.service.MallPromotionService;
import com.xsy.scm.order.constant.OrderErrorCode;
import com.xsy.scm.order.constant.ScmOrderSourceEnum;
import com.xsy.scm.order.dao.OrderRefundDao;
import com.xsy.scm.order.dao.OrderRefundFundingSourceDao;
import com.xsy.scm.order.dao.OrderReturnDao;
import com.xsy.scm.order.dao.OrderReturnItemDao;
import com.xsy.scm.order.dao.SalesOrderDao;
import com.xsy.scm.order.dao.SalesOrderItemDao;
import com.xsy.scm.order.domain.dto.MallOrderCreationResult;
import com.xsy.scm.order.domain.dto.OrderRefundBalanceFact;
import com.xsy.scm.order.domain.entity.OrderRefundEntity;
import com.xsy.scm.order.domain.entity.OrderReturnEntity;
import com.xsy.scm.order.domain.entity.OrderReturnItemEntity;
import com.xsy.scm.order.domain.entity.SalesOrderEntity;
import com.xsy.scm.order.domain.entity.SalesOrderItemEntity;
import com.xsy.scm.order.domain.form.OrderActualQuantityForm;
import com.xsy.scm.order.domain.form.OrderCancelForm;
import com.xsy.scm.order.domain.form.OrderConfirmForm;
import com.xsy.scm.order.domain.form.OrderRefundCompleteForm;
import com.xsy.scm.order.domain.form.OrderReturnAddForm;
import com.xsy.scm.order.domain.form.OrderReturnApproveForm;
import com.xsy.scm.order.domain.form.OrderReturnApproveItemForm;
import com.xsy.scm.order.domain.form.OrderReturnDecisionForm;
import com.xsy.scm.order.domain.form.OrderReturnItemForm;
import com.xsy.scm.order.domain.form.OrderReturnReceiveForm;
import com.xsy.scm.order.domain.form.OrderReturnReceiveItemForm;
import com.xsy.scm.order.domain.form.SalesOrderAddForm;
import com.xsy.scm.order.domain.form.SalesOrderQueryForm;
import com.xsy.scm.order.domain.vo.OrderReturnDetailVO;
import com.xsy.scm.order.domain.vo.SalesOrderDetailVO;
import com.xsy.scm.order.domain.vo.SalesOrderItemVO;
import com.xsy.scm.order.domain.vo.SalesOrderVO;
import com.xsy.scm.order.service.OrderCreditService;
import com.xsy.scm.order.service.OrderRefundService;
import com.xsy.scm.order.service.OrderReturnReceiptService;
import com.xsy.scm.order.service.OrderReturnService;
import com.xsy.scm.order.service.SalesOrderDispatchService;
import com.xsy.scm.order.service.SalesOrderQueryService;
import com.xsy.scm.order.service.SalesOrderService;
import com.xsy.scm.payment.constant.PaymentErrorCode;
import com.xsy.scm.payment.constant.ScmPaymentMethodEnum;
import com.xsy.scm.payment.constant.ScmPaymentProviderEnum;
import com.xsy.scm.payment.constant.ScmPaymentSourceTypeEnum;
import com.xsy.scm.payment.dao.RefundFundingAllocationDao;
import com.xsy.scm.payment.domain.entity.RefundFundingAllocationEntity;
import com.xsy.scm.payment.domain.form.PaymentIntentCreateForm;
import com.xsy.scm.payment.domain.form.RefundFundingExecuteForm;
import com.xsy.scm.payment.provider.ScmPaymentProvider;
import com.xsy.scm.payment.service.PaymentIntentService;
import com.xsy.scm.payment.service.PaymentRefundService;
import com.xsy.scm.payment.service.RefundFundingAllocationService;
import com.xsy.scm.pricing.service.PriceResolver;
import com.xsy.scm.product.domain.form.ProductCategoryAddForm;
import com.xsy.scm.product.domain.form.ProductSkuForm;
import com.xsy.scm.product.domain.form.ProductSpuAddForm;
import com.xsy.scm.product.service.ProductBundleComponentQueryService;
import com.xsy.scm.product.service.ProductCategoryService;
import com.xsy.scm.promotion.constant.PromotionErrorCode;
import com.xsy.scm.promotion.constant.ScmPromotionCouponDiscountTypeEnum;
import com.xsy.scm.promotion.constant.ScmPromotionStatusEnum;
import com.xsy.scm.promotion.dao.PromotionCouponInstanceDao;
import com.xsy.scm.promotion.domain.entity.PromotionCouponInstanceEntity;
import com.xsy.scm.promotion.domain.form.PromotionCouponForm;
import com.xsy.scm.promotion.domain.form.PromotionDiscountPreviewForm;
import com.xsy.scm.promotion.domain.form.PromotionStatusForm;
import com.xsy.scm.promotion.domain.vo.PromotionDiscountVO;
import com.xsy.scm.promotion.service.PromotionCouponService;
import com.xsy.scm.promotion.service.PromotionDiscountService;
import com.xsy.scm.promotion.support.PromotionOrderFacts;
import com.xsy.scm.warehouse.domain.entity.WarehouseEntity;
import com.xsy.scm.warehouse.service.WarehouseService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MallTest {

    @Nested
    class MallWechatServiceTest {
        private final RedisService redis = mock(RedisService.class);
        private final RestClient.Builder builder = RestClient.builder().baseUrl("https://api.weixin.qq.com");
        private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        private final MallWechatService service = new MallWechatService(redis, "wx-test", "test-secret", builder.build());

        private void cache(String token) {
            when(redis.generateRedisKey("scm:mall:wechat:access-token:", "wx-test")).thenReturn("test-token-key");
            when(redis.get("test-token-key")).thenReturn(token);
        }

        private String phone(String appId) {
            return "{\"errcode\":0,\"phone_info\":{\"purePhoneNumber\":\"13800000000\",\"countryCode\":\"86\","
                + "\"watermark\":{\"appid\":\"" + appId + "\"}}}";
        }

        @Test
        void realTransportSendsFixedLengthJsonForBothWechatRequests() throws Exception {
            cache(null);
            HttpServer upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            List<Boolean> fixedLengthRequests = new ArrayList<>();
            upstream.createContext("/", exchange -> {
                byte[] body = exchange.getRequestBody().readAllBytes();
                boolean fixedLength = String.valueOf(body.length).equals(exchange.getRequestHeaders().getFirst("Content-Length"))
                    && exchange.getRequestHeaders().getFirst("Transfer-Encoding") == null;
                fixedLengthRequests.add(fixedLength);
                String response = exchange.getRequestURI().getPath().equals("/cgi-bin/stable_token")
                    ? "{\"access_token\":\"new-token\",\"expires_in\":7200}" : phone("wx-test");
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(fixedLength ? 200 : 411, bytes.length);
                try (var output = exchange.getResponseBody()) {
                    output.write(bytes);
                }
                exchange.close();
            });
            upstream.start();
            try {
                var configured = new MallWechatService(redis, "wx-test", "test-secret");
                // 沿用正式构造器的传输配置，仅将固定官方地址替换为本地验收网关。
                var client = ((RestClient) ReflectionTestUtils.getField(configured, "wechatClient")).mutate()
                    .baseUrl("http://127.0.0.1:" + upstream.getAddress().getPort()).build();
                var realTransport = new MallWechatService(redis, "wx-test", "test-secret", client);
                assertThat(realTransport.phone("one-time-code")).isEqualTo("13800000000");
                assertThat(fixedLengthRequests).containsExactly(true, true);
                verify(redis).set("test-token-key", "new-token", 7140L);
            } finally {
                upstream.stop(0);
            }
        }

        @Test
        void cachedTokenExchangesOnlyTheOfficialPhoneAuthorizationCode() {
            cache("cached-token");
            server.expect(requestTo("https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=cached-token"))
                .andExpect(method(HttpMethod.POST)).andExpect(content().json("{\"code\":\"one-time-code\"}"))
                .andRespond(withSuccess(phone("wx-test"), MediaType.APPLICATION_JSON));
            assertThat(service.phone("one-time-code")).isEqualTo("13800000000");
            server.verify();
        }

        @Test
        void stableAccessTokenIsCachedWithExpiryMargin() {
            cache(null);
            server.expect(requestTo("https://api.weixin.qq.com/cgi-bin/stable_token"))
                .andExpect(content().json("{\"grant_type\":\"client_credential\",\"appid\":\"wx-test\","
                    + "\"secret\":\"test-secret\",\"force_refresh\":false}"))
                .andRespond(withSuccess("{\"access_token\":\"new-token\",\"expires_in\":7200}", MediaType.APPLICATION_JSON));
            server.expect(requestTo("https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=new-token"))
                .andRespond(withSuccess(phone("wx-test"), MediaType.APPLICATION_JSON));
            assertThat(service.phone("code")).isEqualTo("13800000000");
            verify(redis).set("test-token-key", "new-token", 7140L);
            server.verify();
        }

        @Test
        void explicitExpiredAccessTokenRefreshesOnce() {
            cache("expired-token");
            server.expect(requestTo("https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=expired-token"))
                .andRespond(withSuccess("{\"errcode\":42001}", MediaType.APPLICATION_JSON));
            server.expect(requestTo("https://api.weixin.qq.com/cgi-bin/stable_token"))
                .andExpect(content().json("{\"force_refresh\":true}", false))
                .andRespond(withSuccess("{\"access_token\":\"fresh-token\",\"expires_in\":7200}", MediaType.APPLICATION_JSON));
            server.expect(requestTo("https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=fresh-token"))
                .andRespond(withSuccess(phone("wx-test"), MediaType.APPLICATION_JSON));
            assertThat(service.phone("code")).isEqualTo("13800000000");
            server.verify();
        }

        @Test
        void consumedAuthorizationIsNotRetried() {
            cache("cached-token");
            server.expect(requestTo("https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=cached-token"))
                .andRespond(withSuccess("{\"errcode\":40163}", MediaType.APPLICATION_JSON));
            assertThatThrownBy(() -> service.phone("used-code"))
                .isInstanceOfSatisfying(ScmBusinessException.class,
                    error -> assertThat(error.getErrorCode()).isEqualTo(MallErrorCode.WECHAT_CODE_INVALID));
            server.verify();
        }

        @Test
        void anotherAppPhoneCannotAuthenticateHere() {
            cache("cached-token");
            server.expect(requestTo("https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=cached-token"))
                .andRespond(withSuccess(phone("another-app"), MediaType.APPLICATION_JSON));
            assertThatThrownBy(() -> service.phone("code"))
                .isInstanceOfSatisfying(ScmBusinessException.class,
                    error -> assertThat(error.getErrorCode()).isEqualTo(MallErrorCode.WECHAT_CODE_INVALID));
            server.verify();
        }

        @Test
        void providerTimeoutDoesNotReplayCodeOrExposeAccessToken() {
            cache("private-token");
            server.expect(requestTo("https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=private-token"))
                .andRespond(request -> { throw new SocketTimeoutException("upstream timeout"); });
            assertThatThrownBy(() -> service.phone("code"))
                .isInstanceOfSatisfying(ScmBusinessException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(MallErrorCode.WECHAT_UNAVAILABLE);
                    assertThat(error.getMessage()).doesNotContain("private-token");
                    assertThat(error.getCause()).isNull();
                });
            server.verify();
        }

        @Test
        void upstreamHttpFailureHasReadableBusinessError() {
            cache("cached-token");
            server.expect(requestTo("https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=cached-token"))
                .andRespond(withServerError());
            assertThatThrownBy(() -> service.phone("code"))
                .isInstanceOfSatisfying(ScmBusinessException.class,
                    error -> assertThat(error.getErrorCode()).isEqualTo(MallErrorCode.WECHAT_UNAVAILABLE));
            server.verify();
        }

        @Test
        void missingCredentialsAndEmptyCodesNeverCallWechat() {
            var unconfigured = new MallWechatService(redis, "", "", builder.build());
            assertThatThrownBy(() -> unconfigured.phone("code"))
                .isInstanceOfSatisfying(ScmBusinessException.class,
                    error -> assertThat(error.getErrorCode()).isEqualTo(MallErrorCode.WECHAT_UNAVAILABLE));
            assertThatThrownBy(() -> service.phone(" "))
                .isInstanceOfSatisfying(ScmBusinessException.class,
                    error -> assertThat(error.getErrorCode()).isEqualTo(MallErrorCode.WECHAT_CODE_INVALID));
            server.verify();
        }
    }

    @Nested
    @DisplayName("商城售后全链路（PG IT）")
    class MallAfterSaleFlowPgIT extends ScmW6PgITBase {
        private static final String TEMPORARY_PASSWORD = "Mall@123456";
        private static final String NEW_PASSWORD = "Mall@654321";

        @Autowired
        private MallAccountService mallAccountService;

        @Autowired
        private MallAuthService mallAuthService;

        @Autowired
        private MallCartService mallCartService;

        @Autowired
        private MallAddressService mallAddressService;

        @Autowired
        private MallCheckoutService mallCheckoutService;

        // 仅隔离高德公网依赖；地址校验、订单与库存仍访问真实服务和 PostgreSQL。
        @MockitoBean
        private MallMapService mapService;

        @BeforeEach
        void mockExternalMapProvider() {
            MallMapLocationVO location = new MallMapLocationVO();
            location.setLongitude(new BigDecimal("121.544000"));
            location.setLatitude(new BigDecimal("31.221000"));
            location.setProvinceCode(310000);
            location.setCityCode(310100);
            location.setDistrictCode(310115);
            location.setProvinceCode(310000);
            location.setCityCode(310100);
            location.setDistrictCode(310115);
            location.setProvinceName("上海市");
            location.setCityName("上海市");
            location.setDistrictName("浦东新区");
            when(mapService.reverse(any(BigDecimal.class), any(BigDecimal.class))).thenReturn(location);
            // 商城按收货地址区县解析配送仓；mock 的高德固定返回 310115，把该区县指到种子仓。
            jdbc.update("INSERT INTO xsy_mall_district_warehouse (district_code, warehouse_id, priority, deleted,"
                            + " created_by, updated_by) VALUES (310115, ?, 0, FALSE, '1:1', '1:1')"
                            + " ON CONFLICT (district_code, warehouse_id) WHERE deleted = FALSE DO NOTHING",
                    seedWarehouseId());
        }

        @Autowired
        private MallAfterSaleService mallAfterSaleService;

        @Autowired
        private MallAfterSaleQueryService mallAfterSaleQueryService;

        @Autowired
        private OrderReturnService orderReturnService;

        @Autowired
        private OrderReturnReceiptService orderReturnReceiptService;

        @Autowired
        private OrderRefundService orderRefundService;

        @Autowired
        private SalesOrderService salesOrderService;

        @Autowired
        private SalesOrderQueryService salesOrderQueryService;

        @Autowired
        private InventoryOutboundService inventoryOutboundService;

        @Autowired
        private RefundFundingAllocationService refundFundingAllocationService;

        @Autowired
        private PaymentRefundService paymentRefundService;

        @Autowired
        private FinanceReceiptService financeReceiptService;

        // ------------------------------------------------------------------
        // 主链路
        // ------------------------------------------------------------------

        @Test
        @DisplayName("客户申请售后到退款完成：配额受限、库存回补、退款单落账、客户看到处理中")
        void mallAfterSaleRunsFromCustomerApplicationToCompletedRefund() {
            AfterSaleFixture fixture = confirmedMallOrderWithCost("flow");

            // 客户申请：归属、状态门槛与可退数量都在正式退货路径里判定
            MallAfterSaleVO applied = apply(fixture, "2.0000", "flow-apply");
            assertThat(applied.getStatus()).isEqualTo("PENDING");
            assertThat(applied.getDisplayStatus()).isEqualTo(MallAfterSaleDisplayStatusEnum.REVIEWING.name());
            assertThat(applied.getCancelable()).as("待审核才允许客户撤销").isTrue();
            assertThat(applied.getRequestedAmount()).as("申请金额 = 申请数量 × 锁定价").isEqualByComparingTo("2.4000");

            // 可退数量上限 = 实数量 3.0000 − 在途申请 2.0000 = 1.0000，再申请 2.0000 必须被拒
            assertThatThrownBy(() -> apply(fixture, "2.0000", "flow-over"))
                    .isInstanceOf(ScmBusinessException.class)
                    .as("累计申请不得超过实数量");

            // 后台审批：生成应退净额的退款单
            asBackOffice();
            OrderReturnDetailVO approved = approve(fixture);
            assertThat(approved.getStatus()).isEqualTo("APPROVED");
            Long refundId = refundIdOf(fixture.orderId());
            assertThat(refundId).as("批准后必须生成退款单").isNotNull();
            assertThat(jdbc.queryForObject("SELECT refund_amount FROM xsy_order_refund WHERE id = ?",
                    BigDecimal.class, refundId)).isEqualByComparingTo("2.4000");

            // 后台收货：库存按历史销售出库成本回补（入库 10 → 出库 3 → 退回 2 = 9）
            receiveReturn(fixture, approved, "2.0000");
            assertThat(balanceRow(fixture.warehouseId(), fixture.skuId()).getQuantity())
                    .as("退回入库必须把数量加回余额").isEqualByComparingTo("9.0000");
            assertThat(jdbc.queryForObject("SELECT i.unit_cost FROM xsy_order_return_receipt_item i"
                            + " JOIN xsy_order_return_receipt r ON r.id = i.receipt_id WHERE r.return_id = ?",
                    BigDecimal.class, approved.getReturnId()))
                    .as("退回成本取历史销售出库成本，而不是当前市价").isEqualByComparingTo("6.2000");

            // 后台完成退款：退款单置为 COMPLETED 并留下订单审计
            completeRefund(refundId);
            assertThat(jdbc.queryForObject("SELECT status FROM xsy_order_refund WHERE id = ?",
                    String.class, refundId)).isEqualTo("COMPLETED");
            assertThat(operationTypesOf(fixture.orderId()))
                    .as("售后申请与退款完成都必须在订单审计里留痕")
                    .contains("RETURN", "REFUND");

            // 客户侧查询结果：正式记录派生展示分类，本夹具无支付 → 资金未到 → 退款处理中
            MallAfterSaleVO finalView = mallAfterSaleView(fixture, approved.getReturnId());
            assertThat(finalView.getStatus()).isEqualTo("APPROVED");
            assertThat(finalView.getDisplayStatus())
                    .as("有退款单但资金未到账，展示分类必须是「退款处理中」而不是「已退款」")
                    .isEqualTo(MallAfterSaleDisplayStatusEnum.REFUNDING.name());
            assertThat(finalView.getCancelable()).as("已审核的售后不能再撤销").isFalse();
            assertThat(finalView.getApprovedAmount()).isEqualByComparingTo("2.4000");
            assertThat(finalView.getRefund()).isNotNull();
            assertThat(finalView.getRefund().getStatus()).isEqualTo("COMPLETED");
            assertThat(finalView.getRefund().getRefundAmount()).isEqualByComparingTo("2.4000");
            assertThat(finalView.getRefund().getCompletedAt())
                    .as("退款单完成但资金未到账时，资金时间必须为空而不是伪造时间")
                    .isNull();
            assertThat(finalView.getItems()).singleElement()
                    .satisfies(item -> assertThat(item.getApprovedQuantity()).isEqualByComparingTo("2.0000"));
            assertThat(progressText(finalView, "REFUND")).isEqualTo("退款处理中");
        }

        // ------------------------------------------------------------------
        // 资金到账：纯余额支付的订单走到 REFUNDED
        // ------------------------------------------------------------------

        @Test
        @DisplayName("纯余额支付的订单：退款完成即把余额原路返还，客户侧展示转为已退款且资金时间不再是空")
        void balancePaidOrderReachesRefundedOnceTheWalletReturnLands() {
            // 余额支付的钱包返还走的是「与退款完成同事务」的旧钱包路径，因此这里不再人工执行资金分摊。
            AfterSaleFixture fixture = confirmedMallOrderWithCost("refunded", this::payByBalance);

            MallAfterSaleVO applied = apply(fixture, "2.0000", "refunded-apply");
            asBackOffice();
            OrderReturnDetailVO approved = approve(fixture);
            receiveReturn(fixture, approved, "2.0000");
            completeRefund(refundIdOf(fixture.orderId()));

            // 退款完成时刻与钱包返还流水的发生时刻必须一致：这条路径由 hasInvalidReturns 强校验
            OffsetDateTime refundCompletedAt = jdbc.queryForObject(
                    "SELECT completed_at FROM xsy_order_refund WHERE order_id = ? AND deleted = FALSE",
                    OffsetDateTime.class, fixture.orderId());
            OffsetDateTime walletOccurredAt = jdbc.queryForObject(
                    "SELECT m.occurred_at FROM xsy_customer_balance_movement m"
                            + " JOIN xsy_order_refund r ON m.source_id = r.id WHERE r.order_id = ?"
                            + " AND m.source_type = 'ORDER_REFUND' AND m.type = 'REFUND' AND m.direction = 'CREDIT'",
                    OffsetDateTime.class, fixture.orderId());
            assertThat(walletOccurredAt)
                    .as("同事务返还的流水时间必须等于退款完成时刻，否则 hasInvalidReturns 会把这条返还判成脏数据")
                    .isEqualTo(refundCompletedAt);

            MallAfterSaleVO finalView = mallAfterSaleView(fixture, approved.getReturnId());
            assertThat(finalView.getStatus()).isEqualTo("APPROVED");
            assertThat(finalView.getDisplayStatus())
                    .as("资金已实际到账，展示分类必须从退款处理中前进到已退款")
                    .isEqualTo(MallAfterSaleDisplayStatusEnum.REFUNDED.name());
            assertThat(finalView.getRefund()).isNotNull();
            assertThat(finalView.getRefund().getStatus()).isEqualTo("COMPLETED");
            assertThat(finalView.getRefund().getCompletedAt())
                    .as("资金到账后必须给出真实到账时间")
                    .isEqualTo(walletOccurredAt);
            assertThat(finalView.getRefund().getAllocations()).singleElement().satisfies(allocation -> {
                assertThat(allocation.getMethod()).as("原路退回：这笔钱本来就是从余额出的").isEqualTo("BALANCE");
                assertThat(allocation.getStatus()).isEqualTo("SUCCEEDED");
                assertThat(allocation.getAllocatedAmount()).isEqualByComparingTo("2.4000");
                assertThat(allocation.getSucceededAt()).isEqualTo(walletOccurredAt);
            });
            assertThat(progressText(finalView, "REFUND")).isEqualTo("退款完成");
            assertThat(applied.getDisplayStatus()).as("申请当时的展示分类是待审核").isEqualTo("REVIEWING");
        }

        // ------------------------------------------------------------------
        // 混合资金：余额 + 在线，两条腿都到账才算退完
        // ------------------------------------------------------------------

        @Test
        @DisplayName("混合资金（余额 + 在线）订单：分摊把两条腿各自原路退回后，客户侧才转为已退款")
        void mixedFundingOrderReachesRefundedOnlyAfterBothLegsLand() {
            AfterSaleFixture fixture = confirmedMallOrderWithCost("mixed", this::payByMixedFunding);

            MallAfterSaleVO applied = apply(fixture, "2.0000", "mixed-apply");
            assertThat(applied.getDisplayStatus()).isEqualTo(MallAfterSaleDisplayStatusEnum.REVIEWING.name());

            asBackOffice();
            OrderReturnDetailVO approved = approve(fixture);
            receiveReturn(fixture, approved, "2.0000");
            Long refundId = refundIdOf(fixture.orderId());
            completeRefund(refundId);

            // 混合资金不由旧钱包路径返还：退款单已完成，但一分钱都还没退出去
            assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_customer_balance_movement"
                    + " WHERE source_type = 'ORDER_REFUND' AND source_id = ?", Integer.class, refundId))
                    .as("有在线来源时旧钱包整体返还必须让位，否则会把 A 的钱退给 B 或退重复")
                    .isZero();
            assertThat(mallAfterSaleView(fixture, approved.getReturnId()).getDisplayStatus())
                    .isEqualTo(MallAfterSaleDisplayStatusEnum.REFUNDING.name());

            // 后台执行资金分摊：余额腿当场返还，在线腿建立渠道退款意图后停在处理中
            // 读客户侧视图会把请求身份换成商城客户，后台命令前必须切回员工身份。
            asBackOffice();
            RefundFundingExecuteForm form = new RefundFundingExecuteForm();
            form.setRefundId(refundId);
            var executed = refundFundingAllocationService.execute(form, prefix + ":mixed-exec");
            assertThat(executed.getStatus()).isEqualTo("PROCESSING");
            assertThat(allocationStatusOf(refundId, "BALANCE")).isEqualTo("SUCCEEDED");
            assertThat(allocationStatusOf(refundId, "ONLINE")).isEqualTo("PROCESSING");

            // 余额已经退回钱包，但在线那笔还没到账 —— 客户侧不能提前显示「已退款」
            assertThat(mallAfterSaleView(fixture, approved.getReturnId()).getDisplayStatus())
                    .as("只有一条腿到账时展示分类仍必须是退款处理中")
                    .isEqualTo(MallAfterSaleDisplayStatusEnum.REFUNDING.name());
            assertThat(financePaymentCount(refundId)).as("来源没有全部成功，不登记财务付款").isZero();

            // 渠道退回成功：回调与恢复任务共用同一条写入数据库路径
            Long onlineAllocationId = allocationIdOf(refundId, "ONLINE");
            BigDecimal onlineAllocated = jdbc.queryForObject(
                    "SELECT allocated_amount FROM xsy_refund_funding_allocation WHERE id = ?", BigDecimal.class,
                    onlineAllocationId);
            Long paymentRefundId = jdbc.queryForObject("SELECT id FROM xsy_payment_refund"
                    + " WHERE source_type = 'REFUND_FUNDING_ALLOCATION' AND source_id = ?", Long.class,
                    onlineAllocationId);
            assertThat(paymentRefundId).as("在线腿必须先建立渠道退款意图").isNotNull();
            paymentRefundService.applyOutcome(paymentRefundId, ScmPaymentProvider.Outcome.SUCCEEDED,
                    "MOCK-REFUND-" + refundId, onlineAllocated, null, null, operator());

            assertThat(allocationStatusOf(refundId, "ONLINE")).isEqualTo("SUCCEEDED");
            assertThat(financePaymentCount(refundId))
                    .as("两条腿都成功后自动登记财务付款，且只登记一笔")
                    .isEqualTo(1);

            MallAfterSaleVO finalView = mallAfterSaleView(fixture, approved.getReturnId());
            assertThat(finalView.getDisplayStatus())
                    .as("两条腿都到账，展示分类必须前进到已退款")
                    .isEqualTo(MallAfterSaleDisplayStatusEnum.REFUNDED.name());
            assertThat(finalView.getRefund()).isNotNull();
            assertThat(finalView.getRefund().getCompletedAt())
                    .as("资金到账后必须给出真实到账时间")
                    .isNotNull();
            assertThat(finalView.getRefund().getAllocations())
                    .as("原路退回明细要同时给出余额与在线两条腿，客户才看得出钱从哪来回哪去")
                    .hasSize(2)
                    .extracting(MallAfterSaleRefundVO.Allocation::getMethod)
                    .containsExactlyInAnyOrder("BALANCE", "ONLINE");
            assertThat(finalView.getRefund().getAllocations()).allSatisfy(allocation -> {
                assertThat(allocation.getStatus()).isEqualTo("SUCCEEDED");
                assertThat(allocation.getSucceededAt()).isNotNull();
            });
            assertThat(finalView.getRefund().getAllocations().stream()
                    .map(MallAfterSaleRefundVO.Allocation::getAllocatedAmount).reduce(BigDecimal.ZERO, BigDecimal::add))
                    .as("两条腿的实退之和必须等于退款单金额")
                    .isEqualByComparingTo("2.4000");
            assertThat(progressText(finalView, "REFUND")).isEqualTo("退款完成");
        }

        // ------------------------------------------------------------------
        // 撤销：释放可退额度
        // ------------------------------------------------------------------

        @Test
        @DisplayName("客户撤销待审核申请后额度回到可申请状态，且撤销同样留审计")
        void customerCancelReleasesTheReturnableQuota() {
            AfterSaleFixture fixture = confirmedMallOrderWithCost("cancel");

            MallAfterSaleVO applied = apply(fixture, "2.0000", "cancel-apply");
            MallAfterSaleVO cancelled = mallAfterSaleService.cancel(fixture.customer(), applied.getReturnId(),
                    prefix + ":cancel");

            assertThat(cancelled.getStatus()).isEqualTo("CANCELLED");
            assertThat(cancelled.getDisplayStatus()).isEqualTo(MallAfterSaleDisplayStatusEnum.CANCELLED.name());
            assertThat(cancelled.getCancelable()).isFalse();

            // 额度已释放：原来超限的 2.0000 现在可以申请
            MallAfterSaleVO reapplied = apply(fixture, "2.0000", "cancel-reapply");
            assertThat(reapplied.getStatus()).isEqualTo("PENDING");
            assertThat(reapplied.getReturnId()).isNotEqualTo(applied.getReturnId());
        }

        // ------------------------------------------------------------------
        // 夹具
        // ------------------------------------------------------------------

        private record AfterSaleFixture(Long skuId, Long warehouseId, Long customerId, Long orderId, Long orderItemId,
                                        RequestMallCustomer customer) {
        }

        /**
         * 一张「已确认且有销售出库成本」的商城订单。
         *
         * <p>商城下单只走到 PENDING（非标品等后台录实重），确认与出库都由后台完成 ——
         * 这正是真实运营顺序，也让退货收货能取到历史出库成本。
         */
        private AfterSaleFixture confirmedMallOrderWithCost(String tag) {
            return confirmedMallOrderWithCost(tag, (orderId, customerId) -> {
            });
        }

        /**
         * 同上，并允许在确认之前登记订单的支付记录。
         *
         * <p>支付记录决定退款完成时走哪条资金路径：纯余额支付由旧钱包路径同事务原路返还，
         * 混合资金才需要人工执行资金分摊。回调拿到订单与客户 id，因为支付意图要挂在订单上、金额取订单结算额。
         */
        private AfterSaleFixture confirmedMallOrderWithCost(String tag, java.util.function.BiConsumer<Long, Long> pay) {
            Long skuId = newOnShelfSku("mas-" + tag);
            W6Fixture inbound = inboundFixture("mas-" + tag, skuId, "10.0000");
            confirmReceipt(inbound.receipt().getId(), "10.0000");

            Long customerId = newCustomer();
            jdbc.update("UPDATE xsy_customer SET cod_allowed = TRUE, mall_delivery_warehouse_id = ? WHERE id = ?",
                    fixtureWarehouseId(), customerId);
            evictMybatisCache();
            MallAccount account = openMallAccount(customerId);
            RequestMallCustomer customer = signIn(account);
            Long addressId = mallAddressService.create(customer, mallAddress()).getFirst().getAddressId();

            XsyRequestUtil.setRequestUser(customer);
            mallCartService.add(customer, cartAdd(skuId, "3.0000"));
            MallCheckoutPreviewVO preview = mallCheckoutService.preview(customer, previewForm(skuId, addressId));
            MallOrderSubmitVO submitted = mallCheckoutService.submit(customer,
                    submitForm(skuId, addressId, preview.getPriceFingerprint()), prefix + ":mall:" + tag);
            Long orderId = submitted.getOrderId();
            assertThat(submitted.getOrderSource()).isEqualTo("MALL");

            asBackOffice();
            confirmOrder(orderId, "3.0000");
            // 支付记录登记在确认之后：结算额要等实重录入才落定，而支付意图挂在已确认的订单上。
            pay.accept(orderId, customerId);
            Long orderItemId = confirmedSalesOrderItemId(orderId);
            confirmSalesOutbound(orderId, orderItemId, skuId, "3.0000");
            assertThat(balanceRow(fixtureWarehouseId(), skuId).getQuantity())
                    .as("出库后余额 = 入库 10 − 出库 3").isEqualByComparingTo("7.0000");

            return new AfterSaleFixture(skuId, fixtureWarehouseId(), customerId, orderId, orderItemId, customer);
        }

        /**
         * 登记一笔「纯余额支付」的订单资金记录：钱包账户 + 余额支付意图 + 交易 + 余额消费流水。
         *
         * <p>三条记录缺一不可：意图与交易是订单资金的取数来源（{@code selectOrderFunding}），
         * 消费流水是钱包返还的本金依据（{@code BalanceRefundService} 按它的金额算可退上限）。
         * 金额取订单结算额，保证返还金额不超过本金。
         */
        private void payByBalance(Long orderId, Long customerId) {
            BigDecimal orderAmount = jdbc.queryForObject(
                    "SELECT settlement_total_amount FROM xsy_sales_order WHERE id = ?", BigDecimal.class, orderId);
            Long settlementCustomerId = jdbc.queryForObject(
                    "SELECT settlement_customer_id FROM xsy_sales_order WHERE id = ?", Long.class, orderId);
            String settlementCustomerName = jdbc.queryForObject(
                    "SELECT settlement_customer_name_snapshot FROM xsy_sales_order WHERE id = ?", String.class, orderId);
            String orderNo = jdbc.queryForObject("SELECT order_no FROM xsy_sales_order WHERE id = ?", String.class,
                    orderId);

            Long accountId = jdbc.queryForObject("""
                    INSERT INTO xsy_customer_balance_account (settlement_customer_id, settlement_customer_name_snapshot,
                        created_by, updated_by)
                    VALUES (?, ?, ?, ?)
                    RETURNING id
                    """, Long.class, settlementCustomerId, settlementCustomerName, operator(), operator());

            // ck_payment_intent_method_provider：BALANCE 只能配 INTERNAL_BALANCE，且来源必须是销售订单。
            Long intentId = jdbc.queryForObject("""
                    INSERT INTO xsy_payment_intent
                        (intent_no, customer_id, customer_name_snapshot, source_type, source_id, source_no_snapshot,
                         amount, method, provider, status, succeeded_at, created_by, updated_by)
                    VALUES (?, ?, ?, 'SALES_ORDER', ?, ?, CAST(? AS numeric), 'BALANCE', 'INTERNAL_BALANCE', 'SUCCEEDED',
                        CURRENT_TIMESTAMP, ?, ?)
                    RETURNING id
                    """, Long.class, prefix + "-BI-" + UUID.randomUUID().toString().substring(0, 8), customerId,
                    settlementCustomerName, orderId, orderNo, orderAmount, operator(), operator());

            jdbc.update("""
                    INSERT INTO xsy_payment_transaction
                        (transaction_no, intent_id, provider, provider_transaction_no, amount, provider_amount, status,
                         paid_at, created_by)
                    VALUES (?, ?, 'INTERNAL_BALANCE', ?, CAST(? AS numeric), CAST(? AS numeric), 'SUCCEEDED',
                        CURRENT_TIMESTAMP, ?)
                    """, prefix + "-BT-" + UUID.randomUUID().toString().substring(0, 8), intentId,
                    prefix + "-BPTN-" + UUID.randomUUID().toString().substring(0, 8), orderAmount, orderAmount,
                    operator());

            jdbc.update("""
                    INSERT INTO xsy_customer_balance_movement
                        (movement_no, account_id, settlement_customer_id, customer_id, type, direction, amount,
                         source_type, source_id, reason, occurred_at, created_by, updated_by)
                    VALUES (?, ?, ?, ?, 'CONSUME', 'DEBIT', CAST(? AS numeric), 'PAYMENT_INTENT', ?, '余额支付',
                        CURRENT_TIMESTAMP, ?, ?)
                    """, prefix + "-BM-" + UUID.randomUUID().toString().substring(0, 8), accountId,
                    settlementCustomerId, customerId, orderAmount, intentId, operator(), operator());
        }

        /**
         * 登记一笔「余额 + 在线」混合支付的订单资金记录。
         *
         * <p>混合资金是旧钱包整体返还明确让位的场景：{@code BalanceRefundService} 一旦发现还有在线来源就直接返回 null，
         * 所以退款完成不会自动返还任何一分钱，钱只能由资金分摊按原资金来源逐条腿退回。
         *
         * <p>在线腿必须同时带上 Finance 收款记录：订单资金完整性校验要求在线来源有对应收款单，否则分摊连计划都保存不出来。
         * 收款单走财务域的正式登记入口，不直插表 —— 它的客户与结算主体保存的数据由财务域自己解析。
         */
        private void payByMixedFunding(Long orderId, Long customerId) {
            BigDecimal orderAmount = jdbc.queryForObject(
                    "SELECT settlement_total_amount FROM xsy_sales_order WHERE id = ?", BigDecimal.class, orderId);
            Long settlementCustomerId = jdbc.queryForObject(
                    "SELECT settlement_customer_id FROM xsy_sales_order WHERE id = ?", Long.class, orderId);
            String settlementCustomerName = jdbc.queryForObject(
                    "SELECT settlement_customer_name_snapshot FROM xsy_sales_order WHERE id = ?", String.class, orderId);
            String orderNo = jdbc.queryForObject("SELECT order_no FROM xsy_sales_order WHERE id = ?", String.class,
                    orderId);
            // 余额腿刻意占大头：它的本金（2.4000）已够覆盖整笔退款，因此「有在线来源就不许走旧钱包整体返还」
            // 这条守卫是本用例唯一能拦住重复返还的东西 —— 去掉它，钱包会把整笔钱再退一次。
            BigDecimal balanceAmount = orderAmount.multiply(new BigDecimal("2")).divide(new BigDecimal("3"), 4,
                    RoundingMode.HALF_UP);
            BigDecimal onlineAmount = orderAmount.subtract(balanceAmount);

            Long accountId = jdbc.queryForObject("""
                    INSERT INTO xsy_customer_balance_account (settlement_customer_id, settlement_customer_name_snapshot,
                        created_by, updated_by)
                    VALUES (?, ?, ?, ?)
                    RETURNING id
                    """, Long.class, settlementCustomerId, settlementCustomerName, operator(), operator());

            Long balanceIntentId = jdbc.queryForObject("""
                    INSERT INTO xsy_payment_intent
                        (intent_no, customer_id, customer_name_snapshot, source_type, source_id, source_no_snapshot,
                         amount, method, provider, status, succeeded_at, created_by, updated_by)
                    VALUES (?, ?, ?, 'SALES_ORDER', ?, ?, CAST(? AS numeric), 'BALANCE', 'INTERNAL_BALANCE', 'SUCCEEDED',
                        CURRENT_TIMESTAMP, ?, ?)
                    RETURNING id
                    """, Long.class, prefix + "-MBI-" + UUID.randomUUID().toString().substring(0, 8), customerId,
                    settlementCustomerName, orderId, orderNo, balanceAmount, operator(), operator());

            jdbc.update("""
                    INSERT INTO xsy_payment_transaction
                        (transaction_no, intent_id, provider, provider_transaction_no, amount, provider_amount, status,
                         paid_at, created_by)
                    VALUES (?, ?, 'INTERNAL_BALANCE', ?, CAST(? AS numeric), CAST(? AS numeric), 'SUCCEEDED',
                        CURRENT_TIMESTAMP, ?)
                    """, prefix + "-MBT-" + UUID.randomUUID().toString().substring(0, 8), balanceIntentId,
                    prefix + "-MBPTN-" + UUID.randomUUID().toString().substring(0, 8), balanceAmount, balanceAmount,
                    operator());

            jdbc.update("""
                    INSERT INTO xsy_customer_balance_movement
                        (movement_no, account_id, settlement_customer_id, customer_id, type, direction, amount,
                         source_type, source_id, reason, occurred_at, created_by, updated_by)
                    VALUES (?, ?, ?, ?, 'CONSUME', 'DEBIT', CAST(? AS numeric), 'PAYMENT_INTENT', ?, '余额支付',
                        CURRENT_TIMESTAMP, ?, ?)
                    """, prefix + "-MBM-" + UUID.randomUUID().toString().substring(0, 8), accountId,
                    settlementCustomerId, customerId, balanceAmount, balanceIntentId, operator(), operator());

            Long onlineIntentId = jdbc.queryForObject("""
                    INSERT INTO xsy_payment_intent
                        (intent_no, customer_id, customer_name_snapshot, source_type, source_id, source_no_snapshot,
                         amount, method, provider, status, succeeded_at, created_by, updated_by)
                    VALUES (?, ?, ?, 'SALES_ORDER', ?, ?, CAST(? AS numeric), 'ONLINE', 'MOCK', 'SUCCEEDED',
                        CURRENT_TIMESTAMP, ?, ?)
                    RETURNING id
                    """, Long.class, prefix + "-MOI-" + UUID.randomUUID().toString().substring(0, 8), customerId,
                    settlementCustomerName, orderId, orderNo, onlineAmount, operator(), operator());

            Long onlineTransactionId = jdbc.queryForObject("""
                    INSERT INTO xsy_payment_transaction
                        (transaction_no, intent_id, provider, provider_transaction_no, amount, provider_amount, status,
                         paid_at, created_by)
                    VALUES (?, ?, 'MOCK', ?, CAST(? AS numeric), CAST(? AS numeric), 'SUCCEEDED',
                        CURRENT_TIMESTAMP, ?)
                    RETURNING id
                    """, Long.class, prefix + "-MOT-" + UUID.randomUUID().toString().substring(0, 8), onlineIntentId,
                    prefix + "-MOPTN-" + UUID.randomUUID().toString().substring(0, 8), onlineAmount, onlineAmount,
                    operator());

            OffsetDateTime paidAt = jdbc.queryForObject(
                    "SELECT paid_at FROM xsy_payment_transaction WHERE id = ?", OffsetDateTime.class, onlineTransactionId);
            String providerTransactionNo = jdbc.queryForObject(
                    "SELECT provider_transaction_no FROM xsy_payment_transaction WHERE id = ?", String.class,
                    onlineTransactionId);
            financeReceiptService.registerFromPaymentTransaction(new FinancePaymentReceiptFact(onlineTransactionId,
                    customerId, onlineAmount, paidAt, providerTransactionNo));
        }

        private String allocationStatusOf(Long refundId, String sourceMethod) {
            return jdbc.queryForObject("SELECT status FROM xsy_refund_funding_allocation"
                    + " WHERE order_refund_id = ? AND source_method = ? AND deleted = FALSE", String.class, refundId,
                    sourceMethod);
        }

        private Long allocationIdOf(Long refundId, String sourceMethod) {
            return jdbc.queryForObject("SELECT id FROM xsy_refund_funding_allocation"
                    + " WHERE order_refund_id = ? AND source_method = ? AND deleted = FALSE", Long.class, refundId,
                    sourceMethod);
        }

        private Integer financePaymentCount(Long refundId) {
            return jdbc.queryForObject("SELECT count(*) FROM xsy_finance_payment"
                    + " WHERE source_type = 'ORDER_REFUND' AND source_id = ? AND entry_type = 'NORMAL'", Integer.class,
                    refundId);
        }

        private String operator() {
            return "IT-MAS";
        }

        private MallAfterSaleVO apply(AfterSaleFixture fixture, String quantity, String keyTag) {
            XsyRequestUtil.setRequestUser(fixture.customer());
            MallAfterSaleApplyForm form = new MallAfterSaleApplyForm();
            form.setOrderId(fixture.orderId());
            form.setReason("商品破损");
            MallAfterSaleApplyForm.Item item = new MallAfterSaleApplyForm.Item();
            item.setOrderItemId(fixture.orderItemId());
            item.setQuantity(quantity);
            form.setItems(List.of(item));
            return mallAfterSaleService.apply(fixture.customer(), form, prefix + ":" + keyTag);
        }

        private MallAfterSaleVO mallAfterSaleView(AfterSaleFixture fixture, Long returnId) {
            XsyRequestUtil.setRequestUser(fixture.customer());
            return mallAfterSaleQueryService.detail(fixture.customer(), returnId);
        }

        private OrderReturnDetailVO approve(AfterSaleFixture fixture) {
            OrderReturnApproveForm form = new OrderReturnApproveForm();
            form.setReturnId(returnIdOf(fixture.orderId()));
            form.setVersion(returnVersion(form.getReturnId()));
            OrderReturnApproveItemForm item = new OrderReturnApproveItemForm();
            item.setOrderItemId(fixture.orderItemId());
            item.setApprovedQuantity("2.0000");
            form.setItems(List.of(item));
            return orderReturnService.approve(form, prefix + ":approve");
        }

        private void receiveReturn(AfterSaleFixture fixture, OrderReturnDetailVO approved, String quantity) {
            OrderReturnReceiveItemForm item = new OrderReturnReceiveItemForm();
            item.setReturnItemId(jdbc.queryForObject("SELECT id FROM xsy_order_return_item WHERE return_id = ?"
                    + " AND deleted = FALSE ORDER BY id LIMIT 1", Long.class, approved.getReturnId()));
            item.setQuantity(quantity);
            item.setDisposition("RETURN_TO_STOCK");
            OrderReturnReceiveForm form = new OrderReturnReceiveForm();
            form.setReturnId(approved.getReturnId());
            form.setVersion(returnVersion(approved.getReturnId()));
            form.setWarehouseId(fixture.warehouseId());
            form.setItems(List.of(item));
            orderReturnReceiptService.receive(form, prefix + ":receive");
        }

        private void completeRefund(Long refundId) {
            OrderRefundCompleteForm form = new OrderRefundCompleteForm();
            form.setRefundId(refundId);
            form.setVersion(jdbc.queryForObject("SELECT version FROM xsy_order_refund WHERE id = ?",
                    Integer.class, refundId));
            form.setExternalReference("MAS-" + UUID.randomUUID());
            orderRefundService.complete(form, prefix + ":refund:" + refundId);
        }

        /**
         * 后台录实重并确认订单（非标品必须先生成实重）。
         */
        private void confirmOrder(Long orderId, String actualQuantity) {
            SalesOrderDetailVO order = salesOrderQueryService.detail(orderId);
            OrderActualQuantityForm actual = new OrderActualQuantityForm();
            actual.setOrderId(order.getOrderId());
            actual.setItemId(order.getItems().getFirst().getItemId());
            actual.setVersion(order.getItems().getFirst().getVersion());
            actual.setActualQuantity(actualQuantity);
            actual.setReason("实重复核");
            SalesOrderDetailVO weighted = salesOrderService.actualQuantity(actual, prefix + ":actual:" + orderId);

            OrderConfirmForm confirm = new OrderConfirmForm();
            confirm.setSettlementMethod(ScmSettlementMethodEnum.COD.name());
            confirm.setOrderId(weighted.getOrderId());
            confirm.setVersion(weighted.getVersion());
            salesOrderService.confirm(confirm, prefix + ":confirm:" + orderId);
        }

        /**
         * 销售出库并把它关联到订单行 —— 退货收货的历史成本就来自这条出库。
         */
        private void confirmSalesOutbound(Long orderId, Long orderItemId, Long skuId, String quantity) {
            InventoryOutboundAddForm outbound = new InventoryOutboundAddForm();
            outbound.setWarehouseId(fixtureWarehouseId());
            InventoryOutboundAddForm.Item item = new InventoryOutboundAddForm.Item();
            item.setSkuId(skuId);
            item.setQuantity(new BigDecimal(quantity));
            outbound.setItems(List.of(item));
            Long outboundId = inventoryOutboundService.create(outbound);
            jdbc.update("UPDATE xsy_inventory_outbound_item SET sales_order_id = ?, sales_order_item_id = ?"
                    + " WHERE outbound_id = ?", orderId, orderItemId, outboundId);
            evictMybatisCache();
            inventoryOutboundService.confirm(outboundId);
        }

        private Long returnIdOf(Long orderId) {
            return jdbc.queryForObject("SELECT id FROM xsy_order_return WHERE order_id = ? AND deleted = FALSE"
                    + " ORDER BY id DESC LIMIT 1", Long.class, orderId);
        }

        private Integer returnVersion(Long returnId) {
            return jdbc.queryForObject("SELECT version FROM xsy_order_return WHERE id = ?", Integer.class, returnId);
        }

        private Long refundIdOf(Long orderId) {
            return jdbc.queryForObject("SELECT id FROM xsy_order_refund WHERE order_id = ? AND deleted = FALSE",
                    Long.class, orderId);
        }

        private List<String> operationTypesOf(Long orderId) {
            return jdbc.queryForList("SELECT operation_type FROM xsy_order_operation_log WHERE order_id = ?",
                    String.class, orderId);
        }

        private static String progressText(MallAfterSaleVO afterSale, String code) {
            return afterSale.getProgress().stream().filter(node -> code.equals(node.getCode())).findFirst()
                    .map(MallOrderProgressNodeVO::getText).orElse(null);
        }

        /**
         * 切回后台身份。
         *
         * <p>登录商城会把请求身份换成 {@link RequestMallCustomer}，而商城身份不是员工，
         * 后台命令的权限闸门与操作人都会取不到人。
         */
        private void asBackOffice() {
            RequestEmployee employee = new RequestEmployee();
            employee.setEmployeeId(1L);
            employee.setActualName("MAS IT");
            employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
            employee.setAdministratorFlag(true);
            XsyRequestUtil.setRequestUser(employee);
        }

        // ------------------------------------------------------------------
        // 商城身份与账号
        // ------------------------------------------------------------------

        private MallAccount openMallAccount(Long customerId) {
            String phone = newPhone();
            MallAccountCreateForm form = new MallAccountCreateForm();
            form.setCustomerId(customerId);
            form.setLoginPhone(phone);
            form.setTemporaryPassword(TEMPORARY_PASSWORD);
            MallAccountVO account = mallAccountService.create(form);
            return new MallAccount(account.getAccountId(), phone);
        }

        private RequestMallCustomer signIn(MallAccount account) {
            MallLoginForm login = new MallLoginForm();
            login.setPhone(account.phone());
            login.setPassword(TEMPORARY_PASSWORD);
            try (MockedStatic<StpUtil> ignored = mockStatic(StpUtil.class)) {
                mallAuthService.login(login, "127.0.0.1");
                MallPasswordChangeForm change = new MallPasswordChangeForm();
                change.setOldPassword(TEMPORARY_PASSWORD);
                change.setNewPassword(NEW_PASSWORD);
                mallAuthService.changePassword(requestCustomer(account, 0), change);
                login.setPassword(NEW_PASSWORD);
                mallAuthService.login(login, "127.0.0.1");
            }
            return requestCustomer(account, 1);
        }

        private RequestMallCustomer requestCustomer(MallAccount account, int version) {
            RequestMallCustomer customer = mallAuthService.requestCustomer(
                    MallAuthService.loginId(account.accountId(), version), new MockHttpServletRequest());
            assertThat(customer).as("登录后必须能解析出商城客户身份").isNotNull();
            return customer;
        }

        private static MallCartAddForm cartAdd(Long skuId, String quantity) {
            MallCartAddForm form = new MallCartAddForm();
            form.setSkuId(skuId);
            form.setQuantity(quantity);
            return form;
        }

        private static MallAddressSaveForm mallAddress() {
            MallAddressSaveForm form = new MallAddressSaveForm();
            form.setReceiverName("售后收货人");
            form.setReceiverPhone("13800000000");
            form.setProvinceCode(310000);
            form.setCityCode(310100);
            form.setDistrictCode(310115);
            form.setProvinceName("上海市");
            form.setCityName("上海市");
            form.setDistrictName("浦东新区");
            form.setDetailAddress("张江路 3 号");
            form.setLongitude(new BigDecimal("121.544000"));
            form.setLatitude(new BigDecimal("31.221000"));
            form.setIsDefault(true);
            form.setLongitude(new BigDecimal("121.544000"));
            form.setLatitude(new BigDecimal("31.221000"));
            return form;
        }

        private static MallCheckoutPreviewForm previewForm(Long skuId, Long addressId) {
            MallCheckoutPreviewForm form = new MallCheckoutPreviewForm();
            form.setItems(List.of(checkoutItem(skuId, "3.0000")));
            form.setAddressId(addressId);
            return form;
        }

        private MallOrderSubmitForm submitForm(Long skuId, Long addressId, String priceFingerprint) {
            MallOrderSubmitForm form = new MallOrderSubmitForm();
            form.setItems(List.of(checkoutItem(skuId, "3.0000")));
            form.setAddressId(addressId);
            form.setPriceFingerprint(priceFingerprint);
            form.setRemark("商城售后 IT");
            // 结算约定改由后台确认时补齐，提交不再带 settlementMethod
            // 商城下单必须给出本次使用的配送仓；夹具统一用本类入库的那个仓。
            form.setWarehouseId(fixtureWarehouseId());
            return form;
        }

        private static MallCheckoutItemForm checkoutItem(Long skuId, String quantity) {
            MallCheckoutItemForm item = new MallCheckoutItemForm();
            item.setSkuId(skuId);
            item.setQuantity(quantity);
            return item;
        }

        private static String newPhone() {
            long suffix = Math.abs(UUID.randomUUID().getMostSignificantBits() % 900_000_000L);
            return "13" + (100_000_000L + suffix);
        }

        private record MallAccount(Long accountId, String phone) {
        }
    }

    @Nested
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    class MallHomePublishConcurrencyPgIT extends ScmW5PgITBase {
        @Autowired
        private MallHomeLayoutService layouts;
        private String schema;

        @Test
        void concurrentPublishCreatesOneReleaseAndOnePointerAdvance() throws Exception {
            schema = "mall_home_it_" + UUID.randomUUID().toString().replace("-", "");
            jdbc.execute("CREATE SCHEMA " + schema);
            try {
                for (String table : List.of("xsy_mall_home_draft", "xsy_mall_home_release", "xsy_mall_home_pointer")) {
                    jdbc.execute("CREATE TABLE " + schema + "." + table + " (LIKE " + table + " INCLUDING ALL)");
                }
                jdbc.execute("INSERT INTO " + schema + ".xsy_mall_home_draft(id) VALUES (1)");
                jdbc.execute("INSERT INTO " + schema + ".xsy_mall_home_pointer(id) VALUES (1)");
                var draft = inSchema(layouts::draft);
                var form = new MallHomeDraftSaveForm();
                form.setVersion(draft.version());
                form.setThemeCode("OCEAN");
                var block = new MallHomeBlockForm();
                block.setKey("notice");
                block.setSchemaVersion(1);
                block.setType("ANNOUNCEMENT");
                block.setSortOrder(0);
                block.setEnabled(true);
                block.setText("并发发布");
                block.setLinkType("NONE");
                form.setBlocks(List.of(block));
                var saved = inSchema(() -> layouts.save(form));
                var publish = new MallHomePublishForm();
                publish.setDraftVersion(saved.version());
                publish.setPointerVersion(saved.pointerVersion());

                var ready = new CountDownLatch(2);
                var go = new CountDownLatch(1);
                var pool = Executors.newFixedThreadPool(2);
                try {
                    var first = pool.submit(() -> publishAsAdmin(ready, go, publish));
                    var second = pool.submit(() -> publishAsAdmin(ready, go, publish));
                    assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                    go.countDown();
                    var outcomes = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
                    assertThat(outcomes.stream().filter(outcome -> outcome.releaseId() != null).toList()).hasSize(1);
                    assertThat(outcomes.stream().filter(outcome -> outcome.error() != null).toList()).hasSize(1)
                            .allSatisfy(outcome -> assertThat(outcome.error()).isInstanceOf(ScmBusinessException.class));
                    var current = inSchema(layouts::draft);
                    assertThat(current.pointerVersion()).isEqualTo(saved.pointerVersion() + 1);
                    assertThat(current.currentReleaseId()).isEqualTo(outcomes.stream()
                            .map(Outcome::releaseId).filter(id -> id != null).findFirst().orElseThrow());
                    assertThat(jdbc.queryForObject("SELECT count(*) FROM " + schema + ".xsy_mall_home_release",
                            Integer.class)).isEqualTo(1);
                } finally {
                    go.countDown();
                    pool.shutdownNow();
                    assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
                }
            } finally {
                jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }

        private Outcome publishAsAdmin(CountDownLatch ready, CountDownLatch go, MallHomePublishForm form) {
            RequestEmployee employee = new RequestEmployee();
            employee.setEmployeeId(1L);
            employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
            employee.setAdministratorFlag(true);
            XsyRequestUtil.setRequestUser(employee);
            try {
                ready.countDown();
                if (!go.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent publish did not start");
                try {
                    return new Outcome(inSchema(() -> layouts.publish(form)), null);
                } catch (RuntimeException failure) {
                    return new Outcome(null, failure);
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return new Outcome(null, new IllegalStateException(interrupted));
            } finally {
                XsyRequestUtil.remove();
            }
        }

        private <T> T inSchema(Supplier<T> operation) {
            return new TransactionTemplate(transactionManager).execute(status -> {
                jdbc.execute("SET LOCAL search_path TO " + schema + ", public");
                return operation.get();
            });
        }

        private record Outcome(Long releaseId, RuntimeException error) {
        }
    }

    @Nested
    class MallOrderFlowPgIT extends ScmW5PgITBase {
        private static final String TEMPORARY_PASSWORD = "Mall@123456";
        private static final String NEW_PASSWORD = "Mall@654321";

        @Autowired
        private MallAccountService mallAccountService;
        @Autowired
        private PromotionCouponService promotionCouponService;
        @Autowired
        private MallAuthService mallAuthService;
        @Autowired
        private MallCatalogService mallCatalogService;
        @Autowired
        private MallCartService mallCartService;
        @Autowired
        private MallAddressService mallAddressService;
        @Autowired
        private MallCheckoutService mallCheckoutService;
        @Autowired
        private MallOrderQueryService mallOrderQueryService;
        @Autowired
        private SalesOrderService salesOrderService;
        @Autowired
        private InventoryFulfillmentService inventoryFulfillmentService;
        @Autowired
        private OrderReturnService orderReturnService;
        @Autowired
        private OrderReturnReceiptService orderReturnReceiptService;
        @Autowired
        private SalesOrderQueryService salesOrderQueryService;
        @Autowired
        private SalesOrderDispatchService salesOrderDispatchService;

        private int customerSequence;

        // 履约与退货夹具先走真实收货，后续预留、出库及原批次退回使用同一份库存记录。
        @Override
        protected Long newOnShelfSku(String suffix) {
            Long skuId = super.newOnShelfSku(suffix);
            Long supplierId = newPurchasableSupplier(suffix + "-stock", skuId);
            var purchase = createDraftOrder(suffix + "-stock", supplierId, skuId, "100.0000", "6.2000");
            var receipt = submittedOrderReceipt(purchase.getId());
            var current = reloadReceipt(receipt.getId());
            var item = current.getItems().getFirst();
            purchaseReceiptService.confirm(
                    confirmForm(current.getId(), current.getVersion(),
                            receiptLine(item.getId(), item.getVersion(), "100.0000")),
                    prefix + ":stock:" + suffix);
            evictMybatisCache();
            return skuId;
        }

        // 仅隔离高德公网依赖；商城地址校验、订单与库存仍访问真实服务和 PostgreSQL。
        @MockitoBean
        private MallMapService mapService;

        @BeforeEach
        void mockExternalMapProvider() {
            MallMapLocationVO location = new MallMapLocationVO();
            location.setLongitude(new BigDecimal("121.544000"));
            location.setLatitude(new BigDecimal("31.221000"));
            location.setProvinceCode(310000);
            location.setCityCode(310100);
            location.setDistrictCode(310115);
            location.setProvinceCode(310000);
            location.setCityCode(310100);
            location.setDistrictCode(310115);
            location.setProvinceName("上海市");
            location.setCityName("上海市");
            location.setDistrictName("浦东新区");
            when(mapService.reverse(any(BigDecimal.class), any(BigDecimal.class))).thenReturn(location);
        }

        @Test
        @DisplayName("标准品无余额授信及COD资格也能订货，后台收到同一待处理订单且不产生资金或库存占用")
        void standardOrderOnlyIntakeIsPendingAndIdempotentWithoutFunds() {
            Long skuId = newOnShelfSku("standard-intake");
            jdbc.update("UPDATE xsy_product_sku SET product_type='STANDARD' WHERE id=?", skuId);
            Long customerId = newCustomerAssignedTo(anyEmployeeId());
            jdbc.update("UPDATE xsy_customer SET cod_allowed=FALSE, credit_limit=0 WHERE id=?", customerId);
            evictMybatisCache();
            RequestMallCustomer customer = signIn(openMallAccount(customerId));
            Long addressId = mallAddressService.create(customer, address()).getFirst().getAddressId();
            MallCheckoutPreviewVO preview = mallCheckoutService.preview(customer, previewForm(skuId, addressId));
            assertThat(preview.getSettlementOptions()).isEmpty();
            MallOrderSubmitForm form = submitForm(skuId, addressId, preview.getPriceFingerprint());
            form.setSettlementMethod(null);
            var submitted = mallCheckoutService.submit(customer, form, prefix + ":standard-only");
            assertThat(submitted.getStatus()).isEqualTo("PENDING");
            assertThat(mallCheckoutService.submit(customer, form, prefix + ":standard-only").getOrderId())
                    .isEqualTo(submitted.getOrderId());
            assertThat(mallOrderQueryService.detail(customer, submitted.getOrderId()).getDisplayStatus())
                    .isEqualTo("PENDING");
            asAdmin();
            var backend = salesOrderQueryService.detail(submitted.getOrderId());
            assertThat(backend.getOrderNo()).isEqualTo(submitted.getOrderNo());
            assertThat(backend.getMallOrderOnly()).isTrue();
            assertThat(backend.getSettlementMethod()).isNull();
            assertThat(backend.getStatus()).isEqualTo("PENDING");
            assertThat(backend.getItems()).allSatisfy(item -> assertThat(item.getProductTypeSnapshot()).isEqualTo("STANDARD"));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_payment_intent WHERE source_type='SALES_ORDER' AND source_id=?",
                    Integer.class, submitted.getOrderId())).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_customer_balance_movement WHERE customer_id=? AND direction='DEBIT'",
                    Integer.class, customerId)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_finance_receivable WHERE order_id=?",
                    Integer.class, submitted.getOrderId())).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_inventory_reservation WHERE source_document_type='SALES_ORDER_ITEM' AND source_document_id=?",
                    Integer.class, submitted.getOrderId())).isZero();
            assertThat(mallOrderIdsVisibleToCurrentActor(customerId)).contains(submitted.getOrderId());
        }

        @Test
        void crossUnitMallOrderFulfillmentAndTwoReceiptsRemainConsistent() {
            Long skuId = newOnShelfSku("full-unit");
            // 采购已在种子配送仓按 kg 入库 100；商品销售改为箱并配置 1 箱 = 10 kg。
            jdbc.update("UPDATE xsy_product_sku SET sale_unit='箱' WHERE id=?", skuId);
            jdbc.update("INSERT INTO xsy_product_sku_stock_conversion "
                    + "(sku_id,sale_unit,stock_unit,stock_units_per_sale_unit,created_by,updated_by) "
                    + "VALUES (?,'箱','kg',10.0000,'1','1')", skuId);
            evictMybatisCache();

            Long customerId = newCustomerAssignedTo(newEmployee("fullunit"));
            RequestMallCustomer customer = signIn(openMallAccount(customerId));
            Long addressId = mallAddressService.create(customer, address()).getFirst().getAddressId();
            MallCheckoutPreviewVO preview = mallCheckoutService.preview(customer, previewForm(skuId, addressId));
            assertThat(preview.getAvailableCount()).isEqualTo(1);
            MallOrderSubmitVO submitted = mallCheckoutService.submit(customer,
                    submitForm(skuId, addressId, preview.getPriceFingerprint()), prefix + ":unit-order");
            assertThat(submitted.getStatus()).isEqualTo("PENDING");
            Long orderId = submitted.getOrderId();
            assertThat(jdbc.queryForObject(
                    "SELECT mall_delivery_warehouse_id FROM xsy_sales_order WHERE id=?", Long.class, orderId))
                    .isEqualTo(seedWarehouseId());

            asAdmin();
            confirmOrder(orderId, "2.0000");
            Long itemId = jdbc.queryForObject("SELECT id FROM xsy_sales_order_item WHERE order_id=?",
                    Long.class, orderId);
            Map<String, Object> frozen = jdbc.queryForMap(
                    "SELECT mall_stock_unit_snapshot, mall_stock_units_per_sale_unit "
                            + "FROM xsy_sales_order_item WHERE id=?", itemId);
            assertThat(frozen.get("mall_stock_unit_snapshot")).isEqualTo("kg");
            assertThat((BigDecimal) frozen.get("mall_stock_units_per_sale_unit")).isEqualByComparingTo("10.0000");
            // 当前正式业务规则是「确认后显式预留」，不是确认动作自动占库。
            salesOrderService.reserveStock(orderId);
            assertThat(jdbc.queryForObject(
                    "SELECT reserved_quantity FROM xsy_inventory_balance WHERE warehouse_id=? AND sku_id=?",
                    BigDecimal.class, seedWarehouseId(), skuId)).isEqualByComparingTo("20.0000");

            var sent = inventoryFulfillmentService.dispatchOutbound(new InventoryFulfillmentService.Command(
                    880000L + skuId, seedWarehouseId(), OffsetDateTime.now(), "W5 IT",
                    List.of(new InventoryFulfillmentService.Line(orderId, itemId, skuId,
                            new BigDecimal("2.0000"), "kg", new BigDecimal("10.0000")))));
            assertThat(sent.shippedLineCount()).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT quantity FROM xsy_inventory_balance WHERE warehouse_id=? AND sku_id=?",
                    BigDecimal.class, seedWarehouseId(), skuId)).isEqualByComparingTo("80.0000");
            assertThat(jdbc.queryForObject(
                    "SELECT reserved_quantity FROM xsy_inventory_balance WHERE warehouse_id=? AND sku_id=?",
                    BigDecimal.class, seedWarehouseId(), skuId)).isEqualByComparingTo("0.0000");

            OrderReturnItemForm requested = new OrderReturnItemForm();
            requested.setOrderItemId(itemId);
            requested.setRequestedQuantity("2.0000");
            OrderReturnAddForm create = new OrderReturnAddForm();
            create.setOrderId(orderId);
            create.setReason("商城两次退货");
            create.setItems(List.of(requested));
            var draft = orderReturnService.create(create, prefix + ":ret-create");
            OrderReturnApproveItemForm approvedItem = new OrderReturnApproveItemForm();
            approvedItem.setOrderItemId(itemId);
            approvedItem.setApprovedQuantity("2.0000");
            OrderReturnApproveForm approve = new OrderReturnApproveForm();
            approve.setReturnId(draft.getReturnId());
            approve.setVersion(draft.getVersion());
            approve.setItems(List.of(approvedItem));
            var approved = orderReturnService.approve(approve, prefix + ":ret-approve");
            for (int index = 1; index <= 2; index++) {
                OrderReturnReceiveItemForm line = new OrderReturnReceiveItemForm();
                line.setReturnItemId(approved.getItems().getFirst().getReturnItemId());
                line.setQuantity("1.0000");
                line.setDisposition("RETURN_TO_STOCK");
                OrderReturnReceiveForm form = new OrderReturnReceiveForm();
                form.setReturnId(approved.getReturnId());
                form.setVersion(approved.getVersion());
                form.setWarehouseId(seedWarehouseId());
                form.setItems(List.of(line));
                var receipt = orderReturnReceiptService.receive(form, prefix + ":return:" + index);
                assertThat(receipt.getItems().getFirst().getQuantity()).isEqualByComparingTo("1.0000");
                assertThat(receipt.getItems().getFirst().getStockQuantity()).isEqualByComparingTo("10.0000");
            }
            assertThat(jdbc.queryForObject(
                    "SELECT quantity FROM xsy_inventory_balance WHERE warehouse_id=? AND sku_id=?",
                    BigDecimal.class, seedWarehouseId(), skuId)).isEqualByComparingTo("100.0000");
        }

        @Test
        @EnabledIfSystemProperty(named = "scm.mall.promotion.confirm-it", matches = "true")
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        void expiredPreselectedCouponBlocksOrderConfirmation() {
            String expectedDatabase = System.getProperty("scm.mall.promotion.confirm-db", "");
            assertThat(expectedDatabase).matches("xsy_(verify|codex_mkt)_[A-Za-z0-9_]+");
            assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo(expectedDatabase);
            Long sellerId = newEmployee("coupon");
            Long skuId = newOnShelfSku("coupon");
            Long customerId = newCustomerAssignedTo(sellerId);
            PromotionCouponForm coupon = new PromotionCouponForm();
            coupon.setCouponCode(prefix + "-MALL-COUPON");
            coupon.setCouponName(prefix + "商城预选券");
            coupon.setDiscountType(ScmPromotionCouponDiscountTypeEnum.AMOUNT.name());
            coupon.setDiscountValue(new BigDecimal("0.5000"));
            coupon.setMinOrderAmount(BigDecimal.ZERO);
            coupon.setClaimable(true);
            coupon.setValidFrom(OffsetDateTime.now().minusMinutes(1));
            coupon.setValidTo(OffsetDateTime.now().plusDays(1));
            Long couponId = promotionCouponService.save(coupon);
            PromotionStatusForm status = new PromotionStatusForm();
            status.setVersion(0);
            status.setStatus(ScmPromotionStatusEnum.ACTIVE.name());
            promotionCouponService.updateStatus(couponId, status);

            RequestMallCustomer customer = signIn(openMallAccount(customerId));
            Long instanceId = promotionCouponService.claimFromMall(customerId, couponId, prefix + ":claim");
            Long addressId = mallAddressService.create(customer, address()).getFirst().getAddressId();
            MallCheckoutPreviewForm previewForm = previewForm(skuId, addressId);
            previewForm.setCouponInstanceId(instanceId);
            MallCheckoutPreviewVO preview = mallCheckoutService.preview(customer, previewForm);
            assertThat(preview.getPromotion().getCouponDiscount()).isEqualByComparingTo("0.5000");
            MallOrderSubmitForm submitForm = submitForm(skuId, addressId, preview.getPriceFingerprint());
            submitForm.setCouponInstanceId(instanceId);
            MallOrderSubmitVO submitted = mallCheckoutService.submit(customer, submitForm, prefix + ":submit-coupon");
            assertThat(submitted.getStatus()).isEqualTo("PENDING");

            asAdmin();
            SalesOrderDetailVO order = salesOrderQueryService.detail(submitted.getOrderId());
            OrderActualQuantityForm actual = new OrderActualQuantityForm();
            actual.setOrderId(order.getOrderId());
            actual.setItemId(order.getItems().getFirst().getItemId());
            actual.setVersion(order.getItems().getFirst().getVersion());
            actual.setActualQuantity("2.0000");
            actual.setReason("券失效确认回归");
            SalesOrderDetailVO weighted = salesOrderService.actualQuantity(actual, prefix + ":actual-coupon");

            assertThat(jdbc.update("UPDATE xsy_promotion_coupon SET valid_from = ?, valid_to = ? WHERE id = ?",
                    java.sql.Timestamp.from(OffsetDateTime.now().minusHours(2).toInstant()),
                    java.sql.Timestamp.from(OffsetDateTime.now().minusHours(1).toInstant()), couponId)).isEqualTo(1);
            evictMybatisCache();
            OrderConfirmForm confirm = new OrderConfirmForm();
            confirm.setSettlementMethod(ScmSettlementMethodEnum.COD.name());
            confirm.setOrderId(weighted.getOrderId());
            confirm.setVersion(weighted.getVersion());
            assertThatThrownBy(() -> salesOrderService.confirm(confirm, prefix + ":confirm-coupon"))
                    .isInstanceOfSatisfying(ScmBusinessException.class,
                            failure -> assertThat(failure.getErrorCode()).isEqualTo(PromotionErrorCode.COUPON_STATE_INVALID));
            evictMybatisCache();
            assertThat(jdbc.queryForObject("SELECT status FROM xsy_sales_order WHERE id = ?", String.class,
                    submitted.getOrderId())).isEqualTo("PENDING");
            assertThat(jdbc.queryForObject("SELECT status FROM xsy_promotion_coupon_instance WHERE id = ?", String.class,
                    instanceId)).isEqualTo("AVAILABLE");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_order_discount WHERE sales_order_id = ?",
                    Integer.class, submitted.getOrderId())).isZero();
        }

        @Test
        void mallOrderRunsFromLoginToAdminConfirmation() {
            Long sellerEmployeeId = newEmployee("s1");
            Long skuId = newOnShelfSku("flow");
            Long customerId = newCustomerAssignedTo(sellerEmployeeId);
            MallAccount account = openMallAccount(customerId);
            RequestMallCustomer customer = signIn(account);

            // 目录：可见性与客户价都由服务端解析（基类 SKU 只有市场价，落 MARKET 兜底档）
            List<MallProductVO> products = mallCatalogService.visibleProducts(customer, List.of(skuId));
            assertThat(products).as("客户应看到刚上架的 SKU").hasSize(1);
            assertThat(products.getFirst().getUnitPrice()).isEqualByComparingTo("1.2000");
            assertThat(products.getFirst().getPriceSource()).isEqualTo("MARKET");
            assertThat(products.getFirst().getNonStandard()).as("基类 SKU 是非标品").isTrue();

            // 购物车
            MallCartVO cart = mallCartService.add(customer, cartAdd(skuId, "2.0000"));
            assertThat(cart.getItems()).hasSize(1);
            assertThat(cart.getAvailableCount()).isEqualTo(1);
            assertThat(cart.getEstimatedAmount()).isEqualByComparingTo("2.4000");

            // 收货地址
            MallAddressSaveForm address = address();
            Long addressId = mallAddressService.create(customer, address).getFirst().getAddressId();

            // 结算预览：金额与价格指纹都来自服务端，且不含不可售项
            MallCheckoutPreviewVO preview = mallCheckoutService.preview(customer, previewForm(skuId, addressId));
            assertThat(preview.getTotalCount()).isEqualTo(1);
            assertThat(preview.getAvailableCount()).isEqualTo(1);
            assertThat(preview.getUnavailableSkuIds()).isEmpty();
            assertThat(preview.getEstimatedAmount()).isEqualByComparingTo("2.4000");
            assertThat(preview.getPriceFingerprint()).isNotBlank();

            // 正式下单
            String idempotencyKey = prefix + ":mall:submit";
            MallOrderSubmitForm submit = submitForm(skuId, addressId, preview.getPriceFingerprint());
            var submitted = mallCheckoutService.submit(customer, submit, idempotencyKey);
            assertThat(submitted.getOrderSource()).isEqualTo(ScmOrderSourceEnum.MALL.name());
            assertThat(jdbc.queryForObject("SELECT mall_delivery_warehouse_id FROM xsy_sales_order WHERE id=?",
                    Long.class, submitted.getOrderId())).isEqualTo(seedWarehouseId());
            assertThat(submitted.getOrderedTotalAmount()).isEqualByComparingTo("2.4000");
            // 所有商城订单都先待处理；非标品还需后台录实重后确认
            assertThat(submitted.getStatus()).isEqualTo("PENDING");

            // 下单成功后，已下单规格从购物车移除
            assertThat(mallCartService.cart(customer).getItems()).isEmpty();

            // 防重复处理：同一键重试拿回原订单，不重复建单
            assertThat(mallCheckoutService.submit(customer, submit, idempotencyKey).getOrderId())
                    .isEqualTo(submitted.getOrderId());

            // 客户侧订单中心：展示分类由订单状态 + 履约记录派生
            MallOrderVO mine = mallOrderQueryService.detail(customer, submitted.getOrderId());
            assertThat(mine.getDisplayStatus()).isEqualTo(MallOrderDisplayStatusEnum.PENDING.name());
            assertThat(mine.getCancelable()).isTrue();
            assertThat(mallOrderQueryService.list(customer, orderQuery(MallOrderDisplayStatusEnum.PENDING.name()))
                    .getList()).extracting(MallOrderVO::getOrderId).contains(submitted.getOrderId());

            // 订单归属取当时的客户资料上的业务员
            Long sellerId = jdbc.queryForObject("SELECT seller_id FROM xsy_sales_order WHERE id = ?", Long.class,
                    submitted.getOrderId());
            assertThat(sellerId).isEqualTo(sellerEmployeeId);

            // 后台：全量范围的操作者能看到 MALL 来源订单
            //
            // 先钉一条容易踩空的规则：商城身份不是员工，ScmDataScopeService.resolve() 对它返回 denied()，
            // 所以拿着商城身份查后台订单列表必然是空 —— 商城身份不会漏进后台范围。
            assertThat(mallOrderIdsVisibleToCurrentActor(customerId))
                    .as("商城客户身份不属于后台订单范围，后台列表必为空").isEmpty();
            asAdmin();
            assertThat(mallOrderIdsVisibleToCurrentActor(customerId)).contains(submitted.getOrderId());

            // 后台接单：该订单的业务员本人（非超管）能看见并处理
            asEmployee(sellerEmployeeId, () -> {
                assertThat(mallOrderIdsVisibleToCurrentActor(customerId))
                        .as("订单业务员本人应能在后台看到该商城订单").contains(submitted.getOrderId());
                confirmOrder(submitted.getOrderId(), "2.0000");
                return null;
            });

            // 后台确认后，客户侧展示分类跟着履约记录前进，且已确认订单不能再取消
            XsyRequestUtil.setRequestUser(customer);
            MallOrderVO confirmed = mallOrderQueryService.detail(customer, submitted.getOrderId());
            assertThat(confirmed.getStatus()).isEqualTo("CONFIRMED");
            assertThat(confirmed.getDisplayStatus()).isEqualTo(MallOrderDisplayStatusEnum.PREPARING.name());
            assertThat(confirmed.getCancelable()).isFalse();

            // 内部审计记录不进客户时间线：超额授信放行的审批原因留在库里，但客户看不到它
            jdbc.update("INSERT INTO xsy_order_operation_log (order_id, operation_type, operator, reason)"
                    + " VALUES (?, 'CREDIT_OVERRIDE', '1', '内部审批：临时放宽额度')", submitted.getOrderId());
            evictMybatisCache();
            MallOrderVO withOverride = mallOrderQueryService.detail(customer, submitted.getOrderId());
            assertThat(withOverride.getTimeline()).extracting(MallOrderTimelineVO::getOperationType)
                    .as("内部授信审批不得出现在客户时间线").doesNotContain("CREDIT_OVERRIDE");
            assertThat(withOverride.getTimeline()).extracting(MallOrderTimelineVO::getOperationType)
                    .as("客户可见的生命周期节点仍在").contains("CREATE", "CONFIRM");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_order_operation_log WHERE order_id = ?"
                    + " AND operation_type = 'CREDIT_OVERRIDE'", Integer.class, submitted.getOrderId()))
                    .as("后台审计记录保持完整，商城只是收窄展示").isEqualTo(1);
        }

        /**
         * 已知风险：订单业务员取自当时的客户资料。客户未分配业务员时订单 {@code seller_id} 为空，
         * 而普通业务员的范围是 {@code seller_id IN (本人)} —— {@code IN} 天然排除 NULL。
         *
         * <p>
         * 本用例把这个规则钉下来：客户自己看得到自己的订单，全量范围的操作者看得到，
         * 普通业务员看不到。若产品要求「未分配单人人可见」，就得改范围规则（而不是改这条测试）。
         */
        @Test
        void mallOrderOfACustomerWithoutASellerIsInvisibleToOrdinaryEmployees() {
            Long skuId = newOnShelfSku("noseller");
            Long customerId = newCustomerAssignedTo(null);
            MallAccount account = openMallAccount(customerId);
            RequestMallCustomer customer = signIn(account);

            Long addressId = mallAddressService.create(customer, address()).getFirst().getAddressId();
            mallCartService.add(customer, cartAdd(skuId, "1.0000"));
            MallCheckoutPreviewVO preview = mallCheckoutService.preview(customer, previewForm(skuId, addressId));
            var submitted = mallCheckoutService.submit(customer, submitForm(skuId, addressId, preview.getPriceFingerprint()),
                    prefix + ":mall:noseller");

            // 根因：客户没有业务员，订单业务员保存的数据跟着为空
            assertThat(jdbc.queryForObject("SELECT seller_id FROM xsy_sales_order WHERE id = ?", Long.class,
                    submitted.getOrderId())).isNull();

            // 客户自己按 customerId 收窄，不受业务员范围影响
            assertThat(mallOrderQueryService.detail(customer, submitted.getOrderId()).getOrderId())
                    .isEqualTo(submitted.getOrderId());

            // 全量范围的操作者看得到
            asAdmin();
            assertThat(mallOrderIdsVisibleToCurrentActor(customerId)).contains(submitted.getOrderId());

            // 普通业务员看不到未分配单
            Long ordinaryEmployeeId = newEmployee("s2");
            asEmployee(ordinaryEmployeeId, () -> {
                assertThat(mallOrderIdsVisibleToCurrentActor(customerId))
                        .as("未分配业务员的商城订单对普通业务员不可见（seller_id IN (...) 排除 NULL）").isEmpty();
                return null;
            });
        }

        /**
         * 后台接单：非标品必须先生成实重再确认（与人工在后台录实重后接单同一条路径）。
         */
        private void confirmOrder(Long orderId, String actualQuantity) {
            SalesOrderDetailVO order = salesOrderQueryService.detail(orderId);
            OrderActualQuantityForm actual = new OrderActualQuantityForm();
            actual.setOrderId(order.getOrderId());
            actual.setItemId(order.getItems().getFirst().getItemId());
            actual.setVersion(order.getItems().getFirst().getVersion());
            actual.setActualQuantity(actualQuantity);
            actual.setReason("实重复核");
            SalesOrderDetailVO weighted = salesOrderService.actualQuantity(actual, prefix + ":actual:" + orderId);
            OrderConfirmForm confirm = new OrderConfirmForm();
            confirm.setSettlementMethod(ScmSettlementMethodEnum.COD.name());
            confirm.setOrderId(weighted.getOrderId());
            confirm.setVersion(weighted.getVersion());
            salesOrderService.confirm(confirm, prefix + ":confirm:" + orderId);
        }

        /**
         * 配送进度：商城的配送详情与进度节点只读配送域记录，不自己推导。
         *
         * <p>
         * 线路的建线 / 定位 / 发车 / 签收正确性由配送域自己的 IT（{@code DeliveryDispatchPgIT}）覆盖，
         * 本用例只验证商城这一侧的<b>查询结果</b>，因此线路与关联行用直插固定输入 —— 否则要把仓库坐标、入库、 分拣完成那一整套夹具复制过来，重复测别人已经测过的部分。
         *
         * <p>
         * 直插之后必须 {@code evictMybatisCache()}：MyBatis 一级缓存与用例事务同生命周期， 否则会读到事务内的旧值，表现为「改了库但断言没变」。
         *
         * <p>
         * <b>正常签收与异常签收各用一张订单</b>：正式签收只允许 {@code PENDING / IN_TRANSIT → SIGNED|EXCEPTION}
         * （见 {@code DeliveryQueryDao.markSigned}），把同一行从 SIGNED 再改成 EXCEPTION 是正式业务做不到的序列。
         */
        @Test
        void deliveryProgressReflectsRealRouteFacts() {
            Long sellerEmployeeId = newEmployee("s3");
            Long skuId = newOnShelfSku("delivery");
            Long customerId = newCustomerAssignedTo(sellerEmployeeId);
            MallAccount account = openMallAccount(customerId);
            RequestMallCustomer customer = signIn(account);
            Long addressId = mallAddressService.create(customer, address()).getFirst().getAddressId();

            MallOrderSubmitVO submitted = placeMallOrder(customer, skuId, addressId, "delivery-ok");
            Long orderId = submitted.getOrderId();

            // 尚未排车：没有配送信息，进度停在「已提交」，不伪造线路
            MallOrderVO beforeRoute = mallOrderQueryService.detail(customer, orderId);
            assertThat(beforeRoute.getDelivery()).as("未排车不得凭空返回线路").isNull();
            assertThat(nodeCodes(beforeRoute)).containsExactly("SUBMITTED", "CONFIRMED", "DISPATCHED", "SIGNED");
            assertThat(doneCodes(beforeRoute)).containsExactly("SUBMITTED");

            // 后台接单后：确认节点点亮，展示分类进入「备货中」；实重与结算金额同时落定
            asEmployee(sellerEmployeeId, () -> {
                // 实重与订货量故意不同：只有不同才验证得到「结算金额 = 锁定价 × 实重」而不是「× 订货量」
                confirmOrder(orderId, "1.2000");
                return null;
            });
            MallOrderVO confirmed = mallOrderQueryService.detail(customer, orderId);
            assertThat(confirmed.getDisplayStatus()).isEqualTo(MallOrderDisplayStatusEnum.PREPARING.name());
            assertThat(doneCodes(confirmed)).containsExactly("SUBMITTED", "CONFIRMED");
            assertThat(confirmed.getItems()).hasSize(1);
            MallOrderItemVO line = confirmed.getItems().getFirst();
            // 订货量 2.0000（结算表单下发的数量）、实重 1.2000、锁定价 1.2000
            assertThat(line.getOrderedQuantity()).as("订货量").isEqualByComparingTo("2.0000");
            assertThat(line.getActualQuantity()).as("实重来自正式订单行，不是前端模拟").isEqualByComparingTo("1.2000");
            assertThat(line.getUnitPrice()).as("结算单价取锁定价").isEqualByComparingTo("1.2000");
            assertThat(line.getOrderedLineAmount()).as("订货金额按订货量算").isEqualByComparingTo("2.4000");
            assertThat(line.getSettlementLineAmount()).as("结算金额按实重算").isEqualByComparingTo("1.4400");
            assertThat(confirmed.getSettlementTotalAmount()).isEqualByComparingTo("1.4400");

            // 排车并已发车：线路信息与发车节点都来自配送记录
            Long routeId = insertDispatchedRoute("ok");
            insertActiveRouteOrder(routeId, orderId, customerId, submitted.getOrderNo(), "IN_TRANSIT");
            evictMybatisCache();

            MallOrderVO inTransit = mallOrderQueryService.detail(customer, orderId);
            assertThat(inTransit.getDisplayStatus()).isEqualTo(MallOrderDisplayStatusEnum.DELIVERING.name());
            assertThat(inTransit.getDelivery()).isNotNull();
            assertThat(inTransit.getDelivery().getRouteName()).isEqualTo(prefix + "线路");
            assertThat(inTransit.getDelivery().getDriverName()).isEqualTo("刘建国");
            assertThat(inTransit.getDelivery().getDispatchedAt()).as("发车时刻来自线路行").isNotNull();
            assertThat(doneCodes(inTransit)).containsExactly("SUBMITTED", "CONFIRMED", "DISPATCHED");

            // 正常签收（在途 → 已签收）：进度走完，展示分类进入「已完成」
            signRouteOrder(routeId, orderId, "SIGNED", "放前台代收");
            MallOrderVO signed = mallOrderQueryService.detail(customer, orderId);
            assertThat(signed.getDisplayStatus()).isEqualTo(MallOrderDisplayStatusEnum.COMPLETED.name());
            assertThat(signed.getSignedAt()).isNotNull();
            assertThat(doneCodes(signed)).containsExactly("SUBMITTED", "CONFIRMED", "DISPATCHED", "SIGNED");
            assertThat(nodeText(signed, "SIGNED")).as("正常签收不写成异常").isEqualTo("已签收");

            // 异常签收另起一张订单，同样从「在途」出发：最后一格显式写成「配送异常」并带原因，不冒充正常签收
            MallOrderSubmitVO abnormalOrder = placeMallOrder(customer, skuId, addressId, "delivery-abnormal");
            asEmployee(sellerEmployeeId, () -> {
                confirmOrder(abnormalOrder.getOrderId(), "2.0000");
                return null;
            });
            Long abnormalRouteId = insertDispatchedRoute("abnormal");
            insertActiveRouteOrder(abnormalRouteId, abnormalOrder.getOrderId(), customerId, abnormalOrder.getOrderNo(),
                    "IN_TRANSIT");
            signRouteOrder(abnormalRouteId, abnormalOrder.getOrderId(), "EXCEPTION", "客户拒收");
            MallOrderVO abnormal = mallOrderQueryService.detail(customer, abnormalOrder.getOrderId());
            assertThat(doneCodes(abnormal)).containsExactly("SUBMITTED", "CONFIRMED", "DISPATCHED", "SIGNED");
            assertThat(nodeText(abnormal, "SIGNED")).isEqualTo("配送异常");
            assertThat(nodeDetail(abnormal, "SIGNED")).isEqualTo("客户拒收");
        }

        /**
         * 走一遍商城下单：加购 → 预览 → 提交，返回提交结果（含订单号，线路保存的数据要用）。
         *
         * <p>
         * 自己先切回商城身份：{@code asEmployee(...)} 结束时会把请求上下文清空，之后直接调商城服务会以
         * {@code SCM mutation requires an authenticated operator} 失败（{@code ScmOperator.current()} 拿不到操作者）。
         */
        private MallOrderSubmitVO placeMallOrder(RequestMallCustomer customer, Long skuId, Long addressId,
                String keySuffix) {
            XsyRequestUtil.setRequestUser(customer);
            mallCartService.add(customer, cartAdd(skuId, "1.0000"));
            MallCheckoutPreviewVO preview = mallCheckoutService.preview(customer, previewForm(skuId, addressId));
            return mallCheckoutService.submit(customer, submitForm(skuId, addressId, preview.getPriceFingerprint()),
                    prefix + ":mall:" + keySuffix);
        }

        // ------------------------------------------------------------------
        // 配送记录（直插固定输入，仅用于验证商城查询结果）
        // ------------------------------------------------------------------

        private Long insertDispatchedRoute(String suffix) {
            String routeNo = prefix + "-DR-" + suffix;
            jdbc.update("INSERT INTO xsy_delivery_route (route_no, route_name, delivery_date, warehouse_id,"
                    + " warehouse_name_snapshot, driver_name_snapshot, driver_phone_snapshot, vehicle_no_snapshot,"
                    + " planned_departure_time, status, dispatched_at, dispatched_by, version, deleted,"
                    + " created_at, updated_at)"
                    + " VALUES (?, ?, CURRENT_DATE, ?, ?, ?, ?, ?, now(), 'DISPATCHED', now(), 'it', 0, FALSE,"
                    + " now(), now())",
                    routeNo, prefix + "线路", fixtureWarehouseId(), prefix + "仓", "刘建国", "13900000000", "沪A00001");
            return jdbc.queryForObject("SELECT id FROM xsy_delivery_route WHERE route_no = ?", Long.class, routeNo);
        }

        private void insertActiveRouteOrder(Long routeId, Long orderId, Long customerId, String orderNo,
                String fulfillmentStatus) {
            jdbc.update("INSERT INTO xsy_delivery_route_stop (route_id, stop_seq, customer_id,"
                    + " customer_name_snapshot, address_snapshot, version, deleted, created_at, updated_at)"
                    + " VALUES (?, 1, ?, ?, ?, 0, FALSE, now(), now())",
                    routeId, customerId, prefix + "客户", "张江路 88 号");
            Long stopId = jdbc.queryForObject(
                    "SELECT id FROM xsy_delivery_route_stop WHERE route_id = ? AND stop_seq = 1", Long.class, routeId);
            jdbc.update("INSERT INTO xsy_delivery_route_order (route_id, stop_id, order_id, customer_id,"
                    + " order_no_snapshot, assignment_status, fulfillment_status, version, deleted,"
                    + " created_at, updated_at)"
                    + " VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, 0, FALSE, now(), now())",
                    routeId, stopId, orderId, customerId, orderNo, fulfillmentStatus);
            // 订单当前状态是正式依据，线路夹具须同步执行订单发车转换。
            assertThat(salesOrderDispatchService.markDispatched(orderId, java.time.OffsetDateTime.now(), "it")).isTrue();
        }

        private void signRouteOrder(Long routeId, Long orderId, String result, String reason) {
            jdbc.update("UPDATE xsy_delivery_route_order SET fulfillment_status = ?, signed_at = now(),"
                    + " signed_by = 'it', sign_reason = ? WHERE route_id = ? AND order_id = ?",
                    result, reason, routeId, orderId);
            assertThat(salesOrderDispatchService.markSigned(orderId, result, "it")).isTrue();
            evictMybatisCache();
        }

        private static List<String> nodeCodes(MallOrderVO order) {
            return order.getProgress().stream().map(MallOrderProgressNodeVO::getCode).toList();
        }

        private static List<String> doneCodes(MallOrderVO order) {
            return order.getProgress().stream().filter(node -> Boolean.TRUE.equals(node.getDone()))
                    .map(MallOrderProgressNodeVO::getCode).toList();
        }

        private static String nodeText(MallOrderVO order, String code) {
            return order.getProgress().stream().filter(node -> code.equals(node.getCode())).findFirst()
                    .map(MallOrderProgressNodeVO::getText).orElse(null);
        }

        private static String nodeDetail(MallOrderVO order, String code) {
            return order.getProgress().stream().filter(node -> code.equals(node.getCode())).findFirst()
                    .map(MallOrderProgressNodeVO::getDetail).orElse(null);
        }

        // ------------------------------------------------------------------
        // 商城侧
        // ------------------------------------------------------------------

        /**
         * 后台开通商城账号，返回账号与登录手机号。
         */
        private MallAccount openMallAccount(Long customerId) {
            String phone = newPhone();
            MallAccountCreateForm form = new MallAccountCreateForm();
            form.setCustomerId(customerId);
            form.setLoginPhone(phone);
            form.setTemporaryPassword(TEMPORARY_PASSWORD);
            MallAccountVO account = mallAccountService.create(form);
            assertThat(account.getFirstPasswordChange()).as("新账号必须强制改临时密码").isTrue();
            return new MallAccount(account.getAccountId(), phone);
        }

        /**
         * 走真实登录动作并完成首次改密，返回该客户的请求身份。
         *
         * <p>
         * 改密不是可省略的仪式：{@code MallRequestSupport.requirePasswordChanged()} 会挡住临时密码未改的账号，
         * 跳过它就等于跳过了商城接口的前置守卫。
         */
        private RequestMallCustomer signIn(MallAccount account) {
            MallLoginForm login = new MallLoginForm();
            login.setPhone(account.phone());
            login.setPassword(TEMPORARY_PASSWORD);
            try (MockedStatic<StpUtil> ignored = mockStatic(StpUtil.class)) {
                mallAuthService.login(login, "127.0.0.1");
                MallPasswordChangeForm change = new MallPasswordChangeForm();
                change.setOldPassword(TEMPORARY_PASSWORD);
                change.setNewPassword(NEW_PASSWORD);
                mallAuthService.changePassword(requestCustomer(account, 0), change);
                login.setPassword(NEW_PASSWORD);
                mallAuthService.login(login, "127.0.0.1");
            }
            RequestMallCustomer customer = requestCustomer(account, 1);
            assertThat(customer.getFirstPasswordChange()).isFalse();
            XsyRequestUtil.setRequestUser(customer);
            return customer;
        }

        private RequestMallCustomer requestCustomer(MallAccount account, int version) {
            RequestMallCustomer customer = mallAuthService.requestCustomer(MallAuthService.loginId(account.accountId(), version),
                    new MockHttpServletRequest());
            assertThat(customer).as("登录后必须能解析出商城客户身份").isNotNull();
            return customer;
        }

        private static MallCartAddForm cartAdd(Long skuId, String quantity) {
            MallCartAddForm form = new MallCartAddForm();
            form.setSkuId(skuId);
            form.setQuantity(quantity);
            return form;
        }

        private static MallAddressSaveForm address() {
            MallAddressSaveForm form = new MallAddressSaveForm();
            form.setReceiverName("商城收货人");
            form.setReceiverPhone("13800000000");
            form.setProvinceCode(310000);
            form.setCityCode(310100);
            form.setDistrictCode(310115);
            form.setProvinceName("上海市");
            form.setCityName("上海市");
            form.setDistrictName("浦东新区");
            form.setDetailAddress("张江路 1 号");
            form.setIsDefault(true);
            form.setLongitude(new BigDecimal("121.544000"));
            form.setLatitude(new BigDecimal("31.221000"));
            return form;
        }

        private static MallCheckoutPreviewForm previewForm(Long skuId, Long addressId) {
            MallCheckoutPreviewForm form = new MallCheckoutPreviewForm();
            form.setItems(List.of(checkoutItem(skuId, "2.0000")));
            form.setAddressId(addressId);
            return form;
        }

        private MallOrderSubmitForm submitForm(Long skuId, Long addressId, String priceFingerprint) {
            MallOrderSubmitForm form = new MallOrderSubmitForm();
            form.setItems(List.of(checkoutItem(skuId, "2.0000")));
            form.setAddressId(addressId);
            form.setPriceFingerprint(priceFingerprint);
            form.setWarehouseId(seedWarehouseId());
            form.setRemark("商城下单");
            return form;
        }

        private static MallCheckoutItemForm checkoutItem(Long skuId, String quantity) {
            MallCheckoutItemForm item = new MallCheckoutItemForm();
            item.setSkuId(skuId);
            item.setQuantity(quantity);
            return item;
        }

        private static MallOrderQueryForm orderQuery(String displayStatus) {
            MallOrderQueryForm form = new MallOrderQueryForm();
            form.setPageNum(1L);
            form.setPageSize(50L);
            form.setDisplayStatus(displayStatus);
            return form;
        }

        // ------------------------------------------------------------------
        // 后台侧
        // ------------------------------------------------------------------

        /**
         * 当前操作者在后台订单列表里能看到的本客户 {@code MALL} 订单。
         *
         * <p>
         * 同时按 {@code customerId} 与 {@code orderSource} 收窄，断言才不会被库里其它用例留下的订单影响；
         * 范围谓词由 {@code SalesOrderMapper} 拼装，因此这条查询本身就是「可见性」的被测对象。
         */
        private List<Long> mallOrderIdsVisibleToCurrentActor(Long customerId) {
            SalesOrderQueryForm form = new SalesOrderQueryForm();
            form.setPageNum(1L);
            form.setPageSize(50L);
            form.setCustomerId(customerId);
            form.setOrderSource(ScmOrderSourceEnum.MALL.name());
            return salesOrderQueryService.query(form).getList().stream().map(SalesOrderVO::getOrderId).toList();
        }

        /**
         * 切回「全量范围的操作者」。
         *
         * <p>
         * 必须在每次做后台断言前显式调用：登录商城会把请求身份换成 {@link RequestMallCustomer}， 而商城身份不是员工，
         * {@code ScmDataScopeService.resolve()} 对它返回 {@code denied()} —— 后台订单列表会静默变空，
         * 断言写成「包含某订单」就会莫名其妙地失败。
         */
        private void asAdmin() {
            RequestEmployee employee = new RequestEmployee();
            employee.setEmployeeId(1L);
            employee.setActualName("W5 IT");
            employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
            employee.setAdministratorFlag(true);
            XsyRequestUtil.setRequestUser(employee);
        }

        /**
         * 以指定员工的身份执行一段动作。
         *
         * <p>
         * {@code administratorFlag=false} 是这里的关键：它让 {@code ScmDataScopeService.resolve()} 走受限分支，
         * 订单范围收敛成 {@code seller_id IN (本人)}。静态 mock 掉 Sa-Token 后权限判定一律为 false， 与「普通业务员没有全量范围权限」的真实形态一致。
         */
        private <T> T asEmployee(Long employeeId, Supplier<T> action) {
            RequestEmployee employee = new RequestEmployee();
            employee.setEmployeeId(employeeId);
            employee.setActualName("业务员" + employeeId);
            employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
            employee.setAdministratorFlag(false);
            employee.setDepartmentId(1L);
            XsyRequestUtil.setRequestUser(employee);
            try (MockedStatic<StpUtil> ignored = mockStatic(StpUtil.class)) {
                return action.get();
            } finally {
                XsyRequestUtil.remove();
            }
        }

        // ------------------------------------------------------------------
        // 造数
        // ------------------------------------------------------------------

        /**
         * 造一个非超管员工（业务员）。直接插 {@code t_employee}：本用例不验证员工域，
         * 而 {@code administrator_flag=FALSE} 正是「普通业务员」的定义。
         */
        private Long newEmployee(String suffix) {
            String login = (prefix + "-" + suffix).toUpperCase(Locale.ROOT);
            jdbc.update("INSERT INTO t_employee (employee_uid, login_name, login_pwd, actual_name, department_id,"
                    + " administrator_flag, deleted_flag) VALUES (?, ?, ?, ?, 1, FALSE, FALSE)",
                    UUID.randomUUID().toString().replace("-", ""), login, "$argon2id$it-placeholder", "业务员" + suffix);
            return jdbc.queryForObject("SELECT employee_id FROM t_employee WHERE login_name = ?", Long.class, login);
        }

        /**
         * 造一个已合作客户，并显式指定业务员；{@code sellerId} 传 {@code null} 即「未分配业务员」。
         *
         * <p>
         * 为什么要借 {@code CUSTOMER_ASSIGN_PERM}：{@code CustomerService.resolveSellerOnCreate} 在调用方没有分配权时
         * 会把业务员落成「当前登录人」，无法造出未分配客户。分配权是真实存在的权限，这里只是以具备该权限的身份造数。
         */
        private Long newCustomerAssignedTo(Long sellerId) {
            CustomerAddForm form = new CustomerAddForm();
            form.setName(prefix + "-M" + (++customerSequence) + "客户");
            form.setSettleMode("INDEPENDENT");
            form.setCodAllowed(true);
            form.setSellerId(sellerId);
            Long customerId;
            try (MockedStatic<StpUtil> ignored = mockStatic(StpUtil.class)) {
                when(StpUtil.hasPermission(ScmDataScopeService.CUSTOMER_ASSIGN_PERM)).thenReturn(true);
                customerId = customerService.add(form);
            }
            CustomerStatusForm status = new CustomerStatusForm();
            status.setCustomerId(customerId);
            status.setVersion(0);
            status.setStatus("COOPERATING");
            customerService.updateStatus(status);
            // 正式商城必需单仓：夹具使用种子配送仓，不以全仓库存替代选仓逻辑。
            jdbc.update("UPDATE xsy_customer SET mall_delivery_warehouse_id = ? WHERE id = ?", seedWarehouseId(), customerId);
            return customerId;
        }

        /** 11 位登录手机号；同一用例内多次开号也不会撞唯一索引。 */
        private static String newPhone() {
            long suffix = Math.abs(UUID.randomUUID().getMostSignificantBits() % 900_000_000L);
            return "13" + (100_000_000L + suffix);
        }

        private record MallAccount(Long accountId, String phone) {
        }
    }

    @Nested
    class MallPromotionCheckoutPgIT extends ScmW5PgITBase {
        private static final String TEMPORARY_PASSWORD = "Mall@123456";
        private static final String NEW_PASSWORD = "Mall@654321";

        @Autowired
        private MallAccountService mallAccountService;
        @Autowired
        private MallAuthService mallAuthService;
        @Autowired
        private MallAddressService mallAddressService;
        @Autowired
        private MallCheckoutService mallCheckoutService;

        @Autowired
        private MallSettlementService mallSettlementService;
        @Autowired
        private MallOrderQueryService mallOrderQueryService;
        @Autowired
        private PromotionCouponService promotionCouponService;
        @Autowired
        private SalesOrderService salesOrderService;
        @Autowired
        private SalesOrderQueryService salesOrderQueryService;
        @Autowired
        private OrderCreditService orderCreditService;

        // 仅隔离高德公网依赖；商城地址校验、订单与库存仍访问真实服务和 PostgreSQL。
        @MockitoBean
        private MallMapService mapService;

        @BeforeEach
        void mockExternalMapProvider() {
            MallMapLocationVO location = new MallMapLocationVO();
            location.setLongitude(new BigDecimal("121.544000"));
            location.setLatitude(new BigDecimal("31.221000"));
            location.setProvinceCode(310000);
            location.setCityCode(310100);
            location.setDistrictCode(310115);
            location.setProvinceCode(310000);
            location.setCityCode(310100);
            location.setDistrictCode(310115);
            location.setProvinceName("上海市");
            location.setCityName("上海市");
            location.setDistrictName("浦东新区");
            when(mapService.reverse(any(BigDecimal.class), any(BigDecimal.class))).thenReturn(location);
            bindMockedDistrictToSeedWarehouse();
        }

        /**
         * 商城按收货地址区县解析配送仓；高德 reverse 已被 mock 成 310115，
         * 这里把该区县指到种子仓，保证地址→仓库这一步有确定的落点。
         */
        private void bindMockedDistrictToSeedWarehouse() {
            jdbc.update("INSERT INTO xsy_mall_district_warehouse (district_code, warehouse_id, priority, deleted,"
                            + " created_by, updated_by) VALUES (?, ?, 0, FALSE, '1:1', '1:1')"
                            + " ON CONFLICT (district_code, warehouse_id) WHERE deleted = FALSE DO NOTHING",
                    310115, seedWarehouseId());
        }

        /** 建一张「可自助领取 + 立即生效」的定额券。 */
        private Long activeClaimableCoupon(String tag, String discountValue) {
            PromotionCouponForm couponForm = new PromotionCouponForm();
            couponForm.setCouponCode("CP-" + tag);
            couponForm.setCouponName(prefix + "商城自助领券");
            couponForm.setDiscountType(ScmPromotionCouponDiscountTypeEnum.AMOUNT.name());
            couponForm.setDiscountValue(new BigDecimal(discountValue));
            couponForm.setMinOrderAmount(BigDecimal.ZERO);
            couponForm.setClaimable(true);
            couponForm.setPerCustomerLimit(1);
            couponForm.setTotalLimit(100);
            couponForm.setValidFrom(OffsetDateTime.now().minusMinutes(1));
            couponForm.setValidTo(OffsetDateTime.now().plusDays(1));
            Long couponId = promotionCouponService.save(couponForm);
            PromotionStatusForm status = new PromotionStatusForm();
            status.setVersion(0);
            status.setStatus(ScmPromotionStatusEnum.ACTIVE.name());
            promotionCouponService.updateStatus(couponId, status);
            return couponId;
        }

        @Test
        @DisplayName("客户领券 → 结算预览 → 下单 → 后台确认 → 客户看到优惠")
        void claimedCouponFlowsThroughPreviewSubmitAndConfirmation() {
            Long skuId = newOnShelfSku("mkt");
            Long customerId = newCustomer();
            jdbc.update("UPDATE xsy_customer SET cod_allowed = TRUE WHERE id = ?", customerId);
            jdbc.update("UPDATE xsy_customer SET credit_limit = 2.0000 WHERE id = ?", customerId);
            evictMybatisCache();

            // 1. 后台建一张可自助领取的券并启用
            Long couponId = activeClaimableCoupon("MKT-CLAIM", "0.5000");

            // 2. 客户登录（含首次改密）
            MallAccount account = openMallAccount(customerId);
            RequestMallCustomer customer = signIn(account);

            // 3. 客户领券
            Long instanceId = promotionCouponService.claimFromMall(customerId, couponId, prefix + ":claim");
            assertThat(instanceId).as("领券必须返回券实例").isNotNull();
            assertThat(instanceStatus(instanceId)).isEqualTo("AVAILABLE");

            // 4. 结算预览带券：只试算，不占用
            Long addressId = mallAddressService.create(customer, address()).getFirst().getAddressId();
            MallCheckoutPreviewForm previewForm = previewForm(skuId, addressId);
            previewForm.setCouponInstanceId(instanceId);
            MallCheckoutPreviewVO preview = mallCheckoutService.preview(customer, previewForm);
            assertThat(preview.getPromotion()).as("预览必须返回优惠明细").isNotNull();
            assertThat(preview.getPromotion().getCouponDiscount()).isEqualByComparingTo("0.5000");
            assertThat(preview.getPromotion().getPayableAmount()).isEqualByComparingTo("1.9000");
            assertThat(preview.getSettlementOptions()).as("订货预览不查询资金资格").isEmpty();
            MallSettlementOptionVO creditOption = mallSettlementService.options(customer, preview.getPromotion().getPayableAmount()).stream()
                    .filter(option -> "CREDIT".equals(option.getMethod())).findFirst().orElseThrow();
            assertThat(creditOption.getEnabled())
                    .as("账期按优惠后 1.9 占用（额度 2.0 够），不再按优惠前 2.4 保守判定")
                    .isTrue();
            assertThat(creditOption.getAvailableAmount()).isEqualByComparingTo("2.0000");
            assertThat(preview.getPromotion().getCouponInstanceId()).isEqualTo(instanceId);
            assertThat(instanceStatus(instanceId)).as("预览不得占用券").isEqualTo("AVAILABLE");

            // 5. 下单携带预选券：写入订单，但券仍不被保存
            MallOrderSubmitForm submitForm = submitForm(skuId, addressId, preview.getPriceFingerprint());
            submitForm.setCouponInstanceId(instanceId);
            var submitted = mallCheckoutService.submit(customer, submitForm, prefix + ":submit");
            assertThat(submitted.getStatus()).isEqualTo("PENDING");
            assertThat(jdbc.queryForObject("SELECT mall_coupon_instance_id FROM xsy_sales_order WHERE id = ?",
                    Long.class, submitted.getOrderId())).isEqualTo(instanceId);
            assertThat(instanceStatus(instanceId)).as("下单不保存券，保存只在确认命令内").isEqualTo("AVAILABLE");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_order_discount WHERE sales_order_id = ?",
                    Integer.class, submitted.getOrderId())).as("下单阶段不得产生优惠记录").isZero();

            // 6. 后台确认：非标品先录实重，再确认（确认命令内已确认优惠）
            asAdmin();
            SalesOrderDetailVO order = salesOrderQueryService.detail(submitted.getOrderId());
            OrderActualQuantityForm actual = new OrderActualQuantityForm();
            actual.setOrderId(order.getOrderId());
            actual.setItemId(order.getItems().getFirst().getItemId());
            actual.setVersion(order.getItems().getFirst().getVersion());
            actual.setActualQuantity("2.0000");
            actual.setReason("营销闭环 IT 实重");
            SalesOrderDetailVO weighted = salesOrderService.actualQuantity(actual, prefix + ":actual");
            OrderConfirmForm confirm = new OrderConfirmForm();
            confirm.setSettlementMethod(ScmSettlementMethodEnum.CREDIT.name());
            confirm.setOrderId(weighted.getOrderId());
            confirm.setVersion(weighted.getVersion());
            salesOrderService.confirm(confirm, prefix + ":confirm");

            assertThat(jdbc.queryForObject("SELECT status FROM xsy_sales_order WHERE id = ?", String.class,
                    submitted.getOrderId())).isEqualTo("CONFIRMED");
            assertThat(instanceStatus(instanceId)).as("确认后券被占用为 RESERVED").isEqualTo("RESERVED");
            assertThat(jdbc.queryForObject("SELECT coupon_instance_id FROM xsy_order_discount WHERE sales_order_id = ?",
                    Long.class, submitted.getOrderId())).as("确认后落正式优惠记录并绑定券实例").isEqualTo(instanceId);

            // 7. 客户侧订单详情：能看到优惠金额与券名，且与后台一致
            XsyRequestUtil.setRequestUser(customer);
            MallOrderVO mine = mallOrderQueryService.detail(customer, submitted.getOrderId());
            assertThat(mine.getStatus()).isEqualTo("CONFIRMED");
            assertThat(mine.getDiscount()).as("客户订单详情必须带回优惠").isNotNull();
            assertThat(mine.getDiscount().getDiscountAmount()).isEqualByComparingTo("0.5000");
            assertThat(mine.getDiscount().getCouponName()).isEqualTo(prefix + "商城自助领券");
            assertThat(mine.getOrderedTotalAmount()).isEqualByComparingTo("2.4000");
        }

        @Test
        @DisplayName("无余额、授信与COD资格仍可订货；结算约定只在后台确认时保存")
        void orderIntakeIsIndependentOfSettlementEligibility() {
            Long skuId = newOnShelfSku("settle");
            Long customerId = newCustomer();
            RequestMallCustomer customer = signIn(openMallAccount(customerId));
            Long addressId = mallAddressService.create(customer, address()).getFirst().getAddressId();
            MallCheckoutPreviewVO preview = mallCheckoutService.preview(customer, previewForm(skuId, addressId));
            assertThat(preview.getSettlementOptions()).isEmpty();

            Map<String, MallSettlementOptionVO> options = mallSettlementService.options(customer, preview.getEstimatedAmount()).stream()
                    .collect(Collectors.toMap(MallSettlementOptionVO::getMethod, Function.identity()));
            assertThat(options).containsOnlyKeys("WALLET", "CREDIT", "COD");
            assertThat(options.get("CREDIT").getEnabled()).isFalse();
            assertThat(options.get("WALLET").getEnabled()).isFalse();
            assertThat(options.get("COD").getEnabled()).isFalse();
            assertThat(options.get("WALLET").getAvailableAmount()).isEqualByComparingTo("0.0000");

            var submitted = mallCheckoutService.submit(customer,
                    submitForm(skuId, addressId, preview.getPriceFingerprint()), prefix + ":order-only");
            assertThat(submitted.getStatus()).isEqualTo("PENDING");
            assertThat(jdbc.queryForObject("SELECT mall_order_only FROM xsy_sales_order WHERE id=?", Boolean.class,
                    submitted.getOrderId())).isTrue();
            assertThat(jdbc.queryForObject("SELECT settlement_method FROM xsy_sales_order WHERE id=?", String.class,
                    submitted.getOrderId())).isNull();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_payment_intent WHERE source_type='SALES_ORDER' AND source_id=?",
                    Integer.class, submitted.getOrderId())).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_order_discount WHERE sales_order_id=?",
                    Integer.class, submitted.getOrderId())).isZero();

            asAdmin();
            jdbc.update("UPDATE xsy_customer SET cod_allowed=TRUE WHERE id=?", customerId);
            evictMybatisCache();
            SalesOrderDetailVO weighted = recordActualQuantity(submitted.getOrderId(), "settle");
            OrderConfirmForm confirm = new OrderConfirmForm();
            confirm.setOrderId(weighted.getOrderId());
            confirm.setVersion(weighted.getVersion());
            confirm.setSettlementMethod(ScmSettlementMethodEnum.COD.name());
            salesOrderService.confirm(confirm, prefix + ":settle-confirm");
            assertThat(jdbc.queryForObject("SELECT settlement_method FROM xsy_sales_order WHERE id=?", String.class,
                    submitted.getOrderId())).isEqualTo("COD");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_payment_intent WHERE source_type='SALES_ORDER' AND source_id=?",
                    Integer.class, submitted.getOrderId())).isZero();
        }

        @Test
        @DisplayName("余额订单确认不受零授信阻断；本阶段仍不产生钱包扣款记录")
        void walletConfirmationDoesNotUseCreditApproval() {
            Long skuId = newOnShelfSku("wallet");
            Long customerId = newCustomer();
            Long walletId = jdbc.queryForObject("""
                    INSERT INTO xsy_customer_balance_account (settlement_customer_id, settlement_customer_name_snapshot)
                    SELECT id, name FROM xsy_customer WHERE id = ? RETURNING id
                    """, Long.class, customerId);
            jdbc.update("""
                    INSERT INTO xsy_customer_balance_movement (movement_no, account_id, settlement_customer_id,
                        customer_id, type, direction, amount, reason, occurred_at)
                    VALUES (?, ?, ?, ?, 'CORRECTION', 'CREDIT', 10.0000, '结算方式回归', now())
                    """, prefix + "-WALLET", walletId, customerId, customerId);
            evictMybatisCache();
            RequestMallCustomer customer = signIn(openMallAccount(customerId));
            Long addressId = mallAddressService.create(customer, address()).getFirst().getAddressId();
            MallCheckoutPreviewVO preview = mallCheckoutService.preview(customer, previewForm(skuId, addressId));
            assertThat(preview.getSettlementOptions()).isEmpty();
            assertThat(mallSettlementService.options(customer, preview.getEstimatedAmount())).filteredOn(option -> "WALLET".equals(option.getMethod()))
                    .singleElement().extracting(MallSettlementOptionVO::getEnabled).isEqualTo(true);
            MallOrderSubmitForm form = submitForm(skuId, addressId, preview.getPriceFingerprint());
            Long orderId = mallCheckoutService.submit(customer, form, prefix + ":wallet-submit").getOrderId();

            SalesOrderDetailVO weighted = recordActualQuantity(orderId, "wallet");
            OrderConfirmForm confirm = new OrderConfirmForm();
            confirm.setSettlementMethod(ScmSettlementMethodEnum.WALLET.name());
            confirm.setOrderId(orderId);
            confirm.setVersion(weighted.getVersion());
            assertThat(salesOrderService.confirm(confirm, prefix + ":wallet-confirm").getStatus())
                    .isEqualTo("CONFIRMED");
            assertThat(jdbc.queryForObject("SELECT settlement_method FROM xsy_sales_order WHERE id = ?", String.class,
                    orderId)).isEqualTo("WALLET");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_customer_balance_movement WHERE account_id = ?"
                    + " AND direction = 'DEBIT'", Integer.class, walletId)).as("支付扣款由 PAY-01 处理").isZero();
        }

        @Test
        @DisplayName("账期订单的确认按净额放行：额度只够优惠后净额、不够优惠前毛额时，确认不再被毛额挡住")
        void creditConfirmationUsesTheNetAmountFrozenInTheSameCommand() {
            Long skuId = newOnShelfSku("mkt-credit");
            Long customerId = newCustomer();
            // 额度 2.0000：够付优惠后 1.9000，不够付优惠前 2.4000 —— 优惠越大，旧规则挡得越狠
            jdbc.update("UPDATE xsy_customer SET credit_limit = 2.0000 WHERE id = ?", customerId);
            evictMybatisCache();
            Long couponId = activeClaimableCoupon("MKT-CREDIT", "0.5000");

            RequestMallCustomer customer = signIn(openMallAccount(customerId));
            Long instanceId = promotionCouponService.claimFromMall(customerId, couponId, prefix + ":credit-claim");
            Long addressId = mallAddressService.create(customer, address()).getFirst().getAddressId();
            MallCheckoutPreviewForm previewForm = previewForm(skuId, addressId);
            previewForm.setCouponInstanceId(instanceId);
            MallCheckoutPreviewVO preview = mallCheckoutService.preview(customer, previewForm);
            assertThat(preview.getPromotion().getPayableAmount()).isEqualByComparingTo("1.9000");

            MallOrderSubmitForm submitForm = submitForm(skuId, addressId, preview.getPriceFingerprint());
            submitForm.setCouponInstanceId(instanceId);
            Long orderId = mallCheckoutService.submit(customer, submitForm, prefix + ":credit-submit").getOrderId();

            SalesOrderDetailVO weighted = recordActualQuantity(orderId, "mkt-credit");
            OrderConfirmForm confirm = new OrderConfirmForm();
            confirm.setSettlementMethod(ScmSettlementMethodEnum.CREDIT.name());
            confirm.setOrderId(orderId);
            confirm.setVersion(weighted.getVersion());
            assertThat(salesOrderService.confirm(confirm, prefix + ":credit-confirm").getStatus())
                    .as("确认按同一命令内保存出来的净额 1.9000 判定，而不是优惠前毛额 2.4000")
                    .isEqualTo("CONFIRMED");
            assertThat(jdbc.queryForObject("SELECT settlement_total_amount FROM xsy_sales_order WHERE id = ?",
                    BigDecimal.class, orderId)).as("订单上的结算额仍然是优惠前毛额").isEqualByComparingTo("2.4000");
            assertThat(instanceStatus(instanceId)).as("确认后券被占用为 RESERVED").isEqualTo("RESERVED");
            assertThat(jdbc.queryForObject("SELECT discount_amount FROM xsy_order_discount WHERE sales_order_id = ?",
                    BigDecimal.class, orderId)).isEqualByComparingTo("0.5000");

            // 确认后的额度占用同样按净额：签收前的这段时间占用不能虚高
            assertThat(orderCreditService.checkSelfSettlement(customerId, BigDecimal.ZERO).getConfirmedOrderAmount())
                    .isEqualByComparingTo("1.9000");
        }

        @Test
        @DisplayName("COD 下单后若后台撤销客户资格，订单确认必须拒绝")
        void codPermissionIsRecheckedAtConfirmation() {
            Long skuId = newOnShelfSku("cod");
            Long customerId = newCustomer();
            jdbc.update("UPDATE xsy_customer SET cod_allowed = TRUE WHERE id = ?", customerId);
            evictMybatisCache();
            RequestMallCustomer customer = signIn(openMallAccount(customerId));
            Long addressId = mallAddressService.create(customer, address()).getFirst().getAddressId();
            MallCheckoutPreviewVO preview = mallCheckoutService.preview(customer, previewForm(skuId, addressId));
            MallOrderSubmitForm form = submitForm(skuId, addressId, preview.getPriceFingerprint());
            Long orderId = mallCheckoutService.submit(customer, form, prefix + ":cod-submit").getOrderId();
            SalesOrderDetailVO weighted = recordActualQuantity(orderId, "cod");
            jdbc.update("UPDATE xsy_customer SET cod_allowed = FALSE WHERE id = ?", customerId);
            evictMybatisCache();
            OrderConfirmForm confirm = new OrderConfirmForm();
            confirm.setSettlementMethod(ScmSettlementMethodEnum.COD.name());
            confirm.setOrderId(orderId);
            confirm.setVersion(weighted.getVersion());
            assertThatThrownBy(() -> salesOrderService.confirm(confirm, prefix + ":cod-confirm"))
                    .isInstanceOfSatisfying(ScmBusinessException.class,
                            failure -> assertThat(failure.getErrorCode())
                                    .isEqualTo(OrderErrorCode.ORDER_SETTLEMENT_METHOD_BLOCKED));
        }

        @Test
        @DisplayName("COD 下单后出现逾期账单，订单确认必须按客户风险限制拒绝")
        void codOverdueIsRecheckedAtConfirmation() {
            Long skuId = newOnShelfSku("cod-overdue");
            Long customerId = newCustomer();
            jdbc.update("UPDATE xsy_customer SET cod_allowed = TRUE WHERE id = ?", customerId);
            evictMybatisCache();
            Long oldOrderId = confirmedSalesOrder(customerId, skuId, "1.0000", "1.0000");
            RequestMallCustomer customer = signIn(openMallAccount(customerId));
            Long addressId = mallAddressService.create(customer, address()).getFirst().getAddressId();
            MallCheckoutPreviewVO preview = mallCheckoutService.preview(customer, previewForm(skuId, addressId));
            MallOrderSubmitForm form = submitForm(skuId, addressId, preview.getPriceFingerprint());
            Long orderId = mallCheckoutService.submit(customer, form, prefix + ":cod-overdue-submit").getOrderId();
            SalesOrderDetailVO weighted = recordActualQuantity(orderId, "cod-overdue");
            Long receivableId = jdbc.queryForObject("""
                    INSERT INTO xsy_finance_receivable (receivable_no, source_type, source_id, order_id,
                        customer_id, customer_name_snapshot, settlement_customer_id,
                        settlement_customer_name_snapshot, entry_type, amount, event_at)
                    SELECT ?, 'SALES_ORDER', id, id, customer_id, customer_name_snapshot,
                        settlement_customer_id, settlement_customer_name_snapshot, 'NORMAL', 1.0000, now()
                    FROM xsy_sales_order WHERE id = ? RETURNING id
                    """, Long.class, prefix + "-COD-OVERDUE", oldOrderId);
            jdbc.update("UPDATE xsy_finance_receivable SET due_date = CURRENT_DATE - 1 WHERE id = ?", receivableId);
            evictMybatisCache();
            OrderConfirmForm confirm = new OrderConfirmForm();
            confirm.setSettlementMethod(ScmSettlementMethodEnum.COD.name());
            confirm.setOrderId(orderId);
            confirm.setVersion(weighted.getVersion());
            assertThatThrownBy(() -> salesOrderService.confirm(confirm, prefix + ":cod-overdue-confirm"))
                    .isInstanceOfSatisfying(ScmBusinessException.class,
                            failure -> assertThat(failure.getErrorCode())
                                    .isEqualTo(OrderErrorCode.ORDER_SETTLEMENT_METHOD_BLOCKED));
        }

        @Test
        @DisplayName("账期下单后额度被撤销，订单确认不能沿用旧预览放行")
        void creditIsRecheckedAtConfirmation() {
            Long skuId = newOnShelfSku("credit");
            Long customerId = newCustomer();
            jdbc.update("UPDATE xsy_customer SET credit_limit = 10.0000 WHERE id = ?", customerId);
            evictMybatisCache();
            RequestMallCustomer customer = signIn(openMallAccount(customerId));
            Long addressId = mallAddressService.create(customer, address()).getFirst().getAddressId();
            MallCheckoutPreviewVO preview = mallCheckoutService.preview(customer, previewForm(skuId, addressId));
            MallOrderSubmitForm form = submitForm(skuId, addressId, preview.getPriceFingerprint());
            Long orderId = mallCheckoutService.submit(customer, form, prefix + ":credit-submit").getOrderId();
            SalesOrderDetailVO weighted = recordActualQuantity(orderId, "credit");
            jdbc.update("UPDATE xsy_customer SET credit_limit = 0 WHERE id = ?", customerId);
            evictMybatisCache();
            OrderConfirmForm confirm = new OrderConfirmForm();
            confirm.setSettlementMethod(ScmSettlementMethodEnum.CREDIT.name());
            confirm.setOrderId(orderId);
            confirm.setVersion(weighted.getVersion());
            assertThatThrownBy(() -> salesOrderService.confirm(confirm, prefix + ":credit-confirm"))
                    .isInstanceOfSatisfying(ScmBusinessException.class,
                            failure -> assertThat(failure.getErrorCode()).isEqualTo(OrderErrorCode.ORDER_CREDIT_BLOCKED));
        }

        private SalesOrderDetailVO recordActualQuantity(Long orderId, String tag) {
            asAdmin();
            SalesOrderDetailVO order = salesOrderQueryService.detail(orderId);
            OrderActualQuantityForm actual = new OrderActualQuantityForm();
            actual.setOrderId(orderId);
            actual.setItemId(order.getItems().getFirst().getItemId());
            actual.setVersion(order.getItems().getFirst().getVersion());
            actual.setActualQuantity("2.0000");
            actual.setReason("结算方式 " + tag + " 回归");
            return salesOrderService.actualQuantity(actual, prefix + ":actual-" + tag);
        }

        // ------------------------------------------------------------------
        // 商城侧辅助
        // ------------------------------------------------------------------
        private MallAccount openMallAccount(Long customerId) {
            jdbc.update("UPDATE xsy_customer SET mall_delivery_warehouse_id = ? WHERE id = ?",
                    seedWarehouseId(), customerId);
            evictMybatisCache();
            String phone = newPhone();
            MallAccountCreateForm form = new MallAccountCreateForm();
            form.setCustomerId(customerId);
            form.setLoginPhone(phone);
            form.setTemporaryPassword(TEMPORARY_PASSWORD);
            MallAccountVO account = mallAccountService.create(form);
            assertThat(account.getFirstPasswordChange()).isTrue();
            return new MallAccount(account.getAccountId(), phone);
        }

        private RequestMallCustomer signIn(MallAccount account) {
            MallLoginForm login = new MallLoginForm();
            login.setPhone(account.phone());
            login.setPassword(TEMPORARY_PASSWORD);
            try (MockedStatic<StpUtil> ignored = mockStatic(StpUtil.class)) {
                mallAuthService.login(login, "127.0.0.1");
                MallPasswordChangeForm change = new MallPasswordChangeForm();
                change.setOldPassword(TEMPORARY_PASSWORD);
                change.setNewPassword(NEW_PASSWORD);
                mallAuthService.changePassword(requestCustomer(account, 0), change);
                login.setPassword(NEW_PASSWORD);
                mallAuthService.login(login, "127.0.0.1");
            }
            RequestMallCustomer customer = requestCustomer(account, 1);
            assertThat(customer.getFirstPasswordChange()).isFalse();
            XsyRequestUtil.setRequestUser(customer);
            return customer;
        }

        private RequestMallCustomer requestCustomer(MallAccount account, int version) {
            RequestMallCustomer customer = mallAuthService.requestCustomer(
                    MallAuthService.loginId(account.accountId(), version), new MockHttpServletRequest());
            assertThat(customer).isNotNull();
            return customer;
        }

        private String instanceStatus(Long instanceId) {
            evictMybatisCache();
            return jdbc.queryForObject("SELECT status FROM xsy_promotion_coupon_instance WHERE id = ?",
                    String.class, instanceId);
        }

        private static MallAddressSaveForm address() {
            MallAddressSaveForm form = new MallAddressSaveForm();
            form.setReceiverName("商城收货人");
            form.setReceiverPhone("13800000000");
            form.setProvinceCode(310000);
            form.setCityCode(310100);
            form.setDistrictCode(310115);
            form.setProvinceName("上海市");
            form.setCityName("上海市");
            form.setDistrictName("浦东新区");
            form.setDetailAddress("张江路 1 号");
            form.setLongitude(new BigDecimal("121.544000"));
            form.setLatitude(new BigDecimal("31.221000"));
            form.setIsDefault(true);
            form.setLongitude(new BigDecimal("121.544000"));
            form.setLatitude(new BigDecimal("31.221000"));
            return form;
        }

        private static MallCheckoutPreviewForm previewForm(Long skuId, Long addressId) {
            MallCheckoutPreviewForm form = new MallCheckoutPreviewForm();
            form.setItems(List.of(checkoutItem(skuId, "2.0000")));
            form.setAddressId(addressId);
            return form;
        }

        private MallOrderSubmitForm submitForm(Long skuId, Long addressId, String priceFingerprint) {
            MallOrderSubmitForm form = new MallOrderSubmitForm();
            form.setItems(List.of(checkoutItem(skuId, "2.0000")));
            form.setAddressId(addressId);
            form.setPriceFingerprint(priceFingerprint);
            form.setWarehouseId(seedWarehouseId());
            form.setRemark("营销闭环 IT");
            // 商城下单必须给出本次使用的配送仓；夹具统一用本类入库的那个仓。
            form.setWarehouseId(seedWarehouseId());
            return form;
        }

        private static MallCheckoutItemForm checkoutItem(Long skuId, String quantity) {
            MallCheckoutItemForm item = new MallCheckoutItemForm();
            item.setSkuId(skuId);
            item.setQuantity(quantity);
            return item;
        }

        private void asAdmin() {
            RequestEmployee employee = new RequestEmployee();
            employee.setEmployeeId(1L);
            employee.setActualName("营销 IT");
            employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
            employee.setAdministratorFlag(true);
            XsyRequestUtil.setRequestUser(employee);
        }

        private static String newPhone() {
            long suffix = Math.abs(UUID.randomUUID().getMostSignificantBits() % 900_000_000L);
            return "13" + (100_000_000L + suffix);
        }

        private record MallAccount(Long accountId, String phone) {
        }
    }

    @Nested
    @DisplayName("商城领券并发与防重复处理（PG IT）")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    class MallPromotionClaimPgIT extends ScmW5PgITBase {
        private static final long TIMEOUT_SECONDS = 90;

        @Autowired
        private PromotionCouponService promotionCouponService;

        @Autowired
        private MallPromotionService mallPromotionService;

        @Autowired
        private TransactionTemplate transactionTemplate;

        @Test
        @DisplayName("并发领券不超每人限领：10 个线程抢 1 张券，只落 1 张实例")
        void concurrentClaimRespectsPerCustomerLimit() throws Exception {
            asAdmin();
            Long couponId = claimableCoupon("并发每人限领", null, 1);
            Long customerId = newCustomer();
            RequestMallCustomer customer = mallCustomer(customerId);

            List<Callable<Object>> tasks = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                tasks.add(() -> transactionTemplate.execute(status ->
                        mallPromotionService.claim(customer, couponId, key("per"))));
            }
            List<Outcome> outcomes = runConcurrently(tasks);

            long succeeded = outcomes.stream().filter(Outcome::ok).count();
            assertThat(succeeded).as("每人限领 1 张，10 个并发请求只允许 1 个成功").isEqualTo(1);
            assertThat(outcomes).filteredOn(outcome -> !outcome.ok()).allSatisfy(outcome ->
                    assertThat(outcome.error()).as("并发失败必须是领取上限业务拒绝")
                            .isInstanceOfSatisfying(ScmBusinessException.class,
                                    failure -> assertThat(failure.getErrorCode())
                                            .isEqualTo(PromotionErrorCode.COUPON_CLAIM_LIMIT_REACHED)));
            assertThat(claimCount(couponId, customerId))
                    .as("并发下该客户最终只落 1 张券实例").isEqualTo(1);
        }

        @Test
        @DisplayName("并发领券不超发放总量：总量 1 张，两个客户并发抢，只落 1 张")
        void concurrentClaimRespectsTotalLimit() throws Exception {
            asAdmin();
            Long couponId = claimableCoupon("并发总量", 1, null);
            Long first = newCustomer();
            Long second = newCustomer();
            RequestMallCustomer c1 = mallCustomer(first);
            RequestMallCustomer c2 = mallCustomer(second);

            List<Outcome> outcomes = runConcurrently(List.of(
                    () -> transactionTemplate.execute(status -> mallPromotionService.claim(c1, couponId, key("t1"))),
                    () -> transactionTemplate.execute(status -> mallPromotionService.claim(c2, couponId, key("t2")))));

            long succeeded = outcomes.stream().filter(Outcome::ok).count();
            assertThat(succeeded).as("发放总量 1 张，两个客户并发抢只允许 1 个成功").isEqualTo(1);
            assertThat(outcomes).filteredOn(outcome -> !outcome.ok()).allSatisfy(outcome ->
                    assertThat(outcome.error()).as("超总量必须是领取上限业务拒绝")
                            .isInstanceOfSatisfying(ScmBusinessException.class,
                                    failure -> assertThat(failure.getErrorCode())
                                            .isEqualTo(PromotionErrorCode.COUPON_CLAIM_LIMIT_REACHED)));
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM xsy_promotion_coupon_instance WHERE coupon_id = ?",
                    Integer.class, couponId)).as("并发下该模板只落 1 张券实例").isEqualTo(1);
        }

        @Test
        @DisplayName("同一防重复请求编号重试拿回首次领券，不生成第二张实例")
        void sameIdempotencyKeyReturnsFirstClaim() {
            asAdmin();
            Long couponId = claimableCoupon("防重复处理", null, null);
            Long customerId = newCustomer();
            RequestMallCustomer customer = mallCustomer(customerId);
            String key = key("idem");

            Long first = mallPromotionService.claim(customer, couponId, key);
            Long replay = mallPromotionService.claim(customer, couponId, key);

            assertThat(replay).as("同一防重复请求编号重试必须拿回首次领取的券实例").isEqualTo(first);
            assertThat(claimCount(couponId, customerId)).as("防重复处理重试不生成第二张券实例").isEqualTo(1);
        }

        @Test
        @DisplayName("未开放领取的券不可自助领取")
        void nonClaimableCouponIsRejected() {
            asAdmin();
            Long couponId = coupon(false, null, null);
            Long customerId = newCustomer();
            RequestMallCustomer customer = mallCustomer(customerId);

            assertThatThrownBy(() -> mallPromotionService.claim(customer, couponId, key("nc")))
                    .isInstanceOfSatisfying(ScmBusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(PromotionErrorCode.COUPON_NOT_CLAIMABLE));
        }

        // ------------------------------------------------------------------
        // 夹具
        // ------------------------------------------------------------------

        private record Outcome(boolean ok, Throwable error) {
        }

        private List<Outcome> runConcurrently(List<Callable<Object>> tasks) throws InterruptedException {
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
            try {
                List<Future<Object>> futures = new ArrayList<>(tasks.size());
                for (Callable<Object> task : tasks) {
                    futures.add(pool.submit(() -> {
                        asAdmin();
                        start.await();
                        try {
                            task.call();
                            return new Outcome(true, null);
                        } catch (Throwable error) {
                            return new Outcome(false, error);
                        } finally {
                            XsyRequestUtil.remove();
                        }
                    }));
                }
                start.countDown();
                List<Outcome> outcomes = new ArrayList<>(futures.size());
                for (Future<Object> future : futures) {
                    try {
                        outcomes.add((Outcome) future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
                    } catch (ExecutionException | TimeoutException e) {
                        outcomes.add(new Outcome(false, e.getCause() == null ? e : e.getCause()));
                    }
                }
                return outcomes;
            } finally {
                pool.shutdownNow();
            }
        }

        /** 造一张开放领取且在有效期内的券模板（默认每人限领与总量都不限）。 */
        private Long claimableCoupon(String suffix, Integer totalLimit, Integer perCustomerLimit) {
            return coupon(true, totalLimit, perCustomerLimit);
        }

        private Long coupon(boolean claimable, Integer totalLimit, Integer perCustomerLimit) {
            PromotionCouponForm form = new PromotionCouponForm();
            form.setCouponCode("CP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            form.setCouponName(prefix + "券" + claimable + totalLimit + perCustomerLimit);
            form.setDiscountType(ScmPromotionCouponDiscountTypeEnum.AMOUNT.name());
            form.setDiscountValue(new BigDecimal("5.0000"));
            form.setMinOrderAmount(BigDecimal.ZERO);
            form.setClaimable(claimable);
            form.setTotalLimit(totalLimit);
            form.setPerCustomerLimit(perCustomerLimit);
            form.setValidFrom(OffsetDateTime.now().minusMinutes(1));
            form.setValidTo(OffsetDateTime.now().plusDays(1));
            Long id = promotionCouponService.save(form);
            PromotionStatusForm status = new PromotionStatusForm();
            status.setVersion(0);
            status.setStatus(ScmPromotionStatusEnum.ACTIVE.name());
            promotionCouponService.updateStatus(id, status);
            return id;
        }

        private RequestMallCustomer mallCustomer(Long customerId) {
            RequestMallCustomer customer = new RequestMallCustomer();
            customer.setAccountId(customerId);
            customer.setCustomerId(customerId);
            customer.setCustomerName(prefix + "客户");
            return customer;
        }

        private int claimCount(Long couponId, Long customerId) {
            evictMybatisCache();
            return jdbc.queryForObject(
                    "SELECT count(*) FROM xsy_promotion_coupon_instance WHERE coupon_id = ? AND customer_id = ?",
                    Integer.class, couponId, customerId);
        }

        private String key(String tag) {
            return prefix + ":" + tag + ":" + UUID.randomUUID();
        }

        private void asAdmin() {
            RequestEmployee employee = new RequestEmployee();
            employee.setEmployeeId(1L);
            employee.setActualName("Promotion claim IT");
            employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
            employee.setAdministratorFlag(true);
            XsyRequestUtil.setRequestUser(employee);
        }
    }

    @Nested
    class MallUnitTest {
        @Nested

        class MallAccountServiceTest {
            private final MallCustomerAccountDao accounts = mock(MallCustomerAccountDao.class);
            private final CustomerService customers = mock(CustomerService.class);
            private final SecurityLoginService securityLogin = mock(SecurityLoginService.class);
            private final MallAccountService service = new MallAccountService(accounts, customers, securityLogin);

            /**
             * 账号写入路径会调 {@code ScmOperator.current()} 记录操作人，没有登录上下文会直接抛
             * {@code SCM mutation requires an authenticated operator}；这里提供后台操作者身份。
             */
            @BeforeEach
            void setUpOperator() {
                RequestEmployee employee = new RequestEmployee();
                employee.setEmployeeId(1L);
                employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
                XsyRequestUtil.setRequestUser(employee);
            }

            @AfterEach
            void tearDownOperator() {
                XsyRequestUtil.remove();
            }

            @Test
            void accountMutationRechecksCurrentCustomerScope() {
                MallCustomerAccountEntity account = new MallCustomerAccountEntity();
                account.setId(7L);
                account.setCustomerId(8L);
                when(accounts.selectByIdForUpdate(7L)).thenReturn(account);
                when(customers.requireInCurrentScope(8L)).thenThrow(new RuntimeException("out of scope"));
                MallAccountStatusForm form = new MallAccountStatusForm();
                form.setAccountId(7L);
                form.setEnabled(false);
                assertThatThrownBy(() -> service.updateStatus(form)).hasMessage("out of scope");
            }

            @Test
            void passwordResetClearsLoginFailureProtection() {
                MallCustomerAccountEntity account = new MallCustomerAccountEntity();
                account.setId(7L);
                account.setCustomerId(8L);
                when(accounts.selectByIdForUpdate(7L)).thenReturn(account);
                MallPasswordResetForm form = new MallPasswordResetForm();
                form.setAccountId(7L);
                form.setTemporaryPassword("temporary-password");

                service.resetPassword(form);

                verify(securityLogin).removeLoginFail(7L,
                        com.xsy.scm.base.common.enumeration.UserTypeEnum.MALL_CUSTOMER);
            }

            @Test
            void missingAccountDoesNotProbeCustomer() {
                MallAccountStatusForm form = new MallAccountStatusForm();
                form.setAccountId(99L);
                form.setEnabled(false);
                assertThatThrownBy(() -> service.updateStatus(form)).isInstanceOfSatisfying(ScmBusinessException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(MallErrorCode.ACCOUNT_NOT_FOUND));
            }
        }

        @Nested

        class MallAfterSaleDisplayStatusEnumTest {
            @Test
            void pendingReturnIsReviewing() {
                assertThat(MallAfterSaleDisplayStatusEnum.derive("PENDING", false, false))
                        .isEqualTo(MallAfterSaleDisplayStatusEnum.REVIEWING);
            }

            @Test
            void rejectedAndCancelledKeepTheirOwnBuckets() {
                assertThat(MallAfterSaleDisplayStatusEnum.derive("REJECTED", false, false))
                        .isEqualTo(MallAfterSaleDisplayStatusEnum.REJECTED);
                assertThat(MallAfterSaleDisplayStatusEnum.derive("CANCELLED", false, false))
                        .isEqualTo(MallAfterSaleDisplayStatusEnum.CANCELLED);
            }

            @Test
            void approvedWithoutRefundMeansNothingToRefund() {
                // 退款净额为 0 时没有退款单：售后已终结，不能停在「退款中」
                assertThat(MallAfterSaleDisplayStatusEnum.derive("APPROVED", false, false))
                        .isEqualTo(MallAfterSaleDisplayStatusEnum.REFUNDED);
            }

            @Test
            void approvedWithPendingRefundIsRefunding() {
                assertThat(MallAfterSaleDisplayStatusEnum.derive("APPROVED", true, false))
                        .isEqualTo(MallAfterSaleDisplayStatusEnum.REFUNDING);
            }

            @Test
            void approvedWithCompletedFundingIsRefunded() {
                assertThat(MallAfterSaleDisplayStatusEnum.derive("APPROVED", true, true))
                        .isEqualTo(MallAfterSaleDisplayStatusEnum.REFUNDED);
            }

            @Test
            void unknownStatusFallsBackToReviewing() {
                // 未知的退货状态宁可显示「审核中」，也不能让客户以为钱已经退了
                assertThat(MallAfterSaleDisplayStatusEnum.derive("SOMETHING_NEW", false, false))
                        .isEqualTo(MallAfterSaleDisplayStatusEnum.REVIEWING);
            }
        }

        @Nested

        class MallAfterSaleQueryServiceTest {
            private static final Long RETURN_ID = 33L;
            private static final Long ORDER_ID = 21L;

            private final OrderReturnDao orderReturnDao = mock(OrderReturnDao.class);
            private final OrderReturnItemDao orderReturnItemDao = mock(OrderReturnItemDao.class);
            private final OrderRefundDao orderRefundDao = mock(OrderRefundDao.class);
            private final OrderRefundFundingSourceDao orderRefundFundingSourceDao = mock(OrderRefundFundingSourceDao.class);
            private final SalesOrderDao salesOrderDao = mock(SalesOrderDao.class);
            private final SalesOrderItemDao salesOrderItemDao = mock(SalesOrderItemDao.class);
            private final RefundFundingAllocationDao refundFundingAllocationDao = mock(RefundFundingAllocationDao.class);
            private final MallAfterSaleFundingSourceDao fundingSourceDao = mock(MallAfterSaleFundingSourceDao.class);
            private final MallOrderQueryService orderQueryService = mock(MallOrderQueryService.class);
            private final MallAfterSaleQueryService service = new MallAfterSaleQueryService(orderReturnDao,
                    orderReturnItemDao, orderRefundDao, orderRefundFundingSourceDao, salesOrderDao, salesOrderItemDao,
                    refundFundingAllocationDao, fundingSourceDao, orderQueryService);
            private final RequestMallCustomer customer = customer();

            @Test
            void detailProjectsAllocationRowsAsTheOriginalFundingBreakdown() {
                OrderRefundEntity refund = refund("PENDING");
                stubDetail(returnEntity("APPROVED"), refund);
                RefundFundingAllocationEntity onlineRow = allocation("ONLINE", "20.0000", "SUCCEEDED");
                onlineRow.setId(71L);
                onlineRow.setSourceSucceededAt(OffsetDateTime.parse("2026-10-01T10:00:00+08:00"));
                RefundFundingAllocationEntity balanceRow = allocation("BALANCE", "10.0000", "PROCESSING");
                balanceRow.setId(72L);
                when(refundFundingAllocationDao.listByRefund(refund.getId())).thenReturn(List.of(onlineRow, balanceRow));
                OffsetDateTime actualRefundAt = OffsetDateTime.parse("2026-10-13T03:00:00+08:00");
                when(fundingSourceDao.allocationSuccessTimes(refund.getId()))
                        .thenReturn(List.of(fundingTime(null, 71L, actualRefundAt)));

                MallAfterSaleVO result = service.detail(customer, RETURN_ID);

                assertThat(result.getDisplayStatus()).isEqualTo("REFUNDING");
                assertThat(result.getRefund().getAllocations()).hasSize(2);
                MallAfterSaleRefundVO.Allocation online = result.getRefund().getAllocations().getFirst();
                assertThat(online.getMethod()).isEqualTo("ONLINE");
                assertThat(online.getAllocatedAmount()).isEqualByComparingTo("20.0000");
                assertThat(online.getStatus()).isEqualTo("SUCCEEDED");
                assertThat(online.getSucceededAt()).isEqualTo(actualRefundAt);
                assertThat(result.getRefund().getAllocations().get(1).getSucceededAt()).isNull();
                // 进度：审核已通过、退款未完结
                assertThat(result.getProgress()).extracting("code").containsExactly("SUBMITTED", "REVIEW", "REFUND");
                assertThat(result.getProgress().get(2).getDone()).isFalse();
            }

            @Test
            void legacyBalanceReturnIsShownAsOneBalanceRow() {
                OrderRefundEntity refund = refund("COMPLETED");
                stubDetail(returnEntity("APPROVED"), refund);
                when(refundFundingAllocationDao.listByRefund(refund.getId())).thenReturn(List.of());
                when(orderRefundFundingSourceDao.selectBalanceReturns(List.of(refund.getId())))
                        .thenReturn(List.of(new OrderRefundBalanceFact(refund.getId(), 55L, new BigDecimal("30.0000"))));
                OffsetDateTime returnedAt = OffsetDateTime.parse("2026-10-13T03:10:00+08:00");
                when(fundingSourceDao.completedRefunds(List.of(refund.getId())))
                        .thenReturn(List.of(fundingTime(refund.getId(), null, returnedAt)));

                MallAfterSaleVO result = service.detail(customer, RETURN_ID);

                assertThat(result.getDisplayStatus()).isEqualTo("REFUNDED");
                assertThat(result.getRefund().getAllocations()).hasSize(1);
                MallAfterSaleRefundVO.Allocation balance = result.getRefund().getAllocations().getFirst();
                assertThat(balance.getMethod()).isEqualTo("BALANCE");
                assertThat(balance.getAllocatedAmount()).isEqualByComparingTo("30.0000");
                assertThat(balance.getStatus()).isEqualTo("SUCCEEDED");
                assertThat(balance.getSucceededAt()).isEqualTo(returnedAt);
                assertThat(result.getRefund().getCompletedAt()).isEqualTo(returnedAt);
                assertThat(result.getProgress().get(2).getText()).isEqualTo("退款完成");
            }

            @Test
            void completedBusinessRefundWithoutFundingStillShowsRefunding() {
                OrderRefundEntity refund = refund("COMPLETED");
                stubDetail(returnEntity("APPROVED"), refund);

                MallAfterSaleVO result = service.detail(customer, RETURN_ID);

                assertThat(result.getDisplayStatus()).isEqualTo("REFUNDING");
                assertThat(result.getRefund().getCompletedAt()).isNull();
                assertThat(result.getProgress().get(2).getDone()).isFalse();
            }

            @Test
            void approvedWithoutRefundEndsAtNothingToRefund() {
                stubDetail(returnEntity("APPROVED"), null);

                MallAfterSaleVO result = service.detail(customer, RETURN_ID);

                assertThat(result.getDisplayStatus()).isEqualTo("REFUNDED");
                assertThat(result.getRefund()).isNull();
                assertThat(result.getProgress()).extracting("text").containsExactly("已提交申请", "审核通过", "无需退款");
            }

            @Test
            void rejectedReturnExposesTheDecisionReasonOnTheTerminalNode() {
                OrderReturnEntity entity = returnEntity("REJECTED");
                entity.setDecisionReason("商品已签收完好");
                entity.setRejectedAt(OffsetDateTime.now());
                stubDetail(entity, null);

                MallAfterSaleVO result = service.detail(customer, RETURN_ID);

                assertThat(result.getDisplayStatus()).isEqualTo("REJECTED");
                assertThat(result.getProgress()).extracting("code").containsExactly("SUBMITTED", "REJECTED");
                assertThat(result.getProgress().get(1).getDetail()).isEqualTo("商品已签收完好");
            }

            @Test
            void returnOwnedByAnotherCustomerIsNotFound() {
                OrderReturnEntity entity = returnEntity("PENDING");
                entity.setCustomerId(999L);
                when(orderReturnDao.selectById(RETURN_ID)).thenReturn(entity);

                assertThatThrownBy(() -> service.detail(customer, RETURN_ID))
                        .isInstanceOfSatisfying(ScmBusinessException.class, exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(MallErrorCode.AFTERSALE_NOT_FOUND));
            }

            private void stubDetail(OrderReturnEntity entity, OrderRefundEntity refund) {
                when(orderReturnDao.selectById(RETURN_ID)).thenReturn(entity);
                when(orderReturnItemDao.listByReturnIds(List.of(RETURN_ID))).thenReturn(List.of(returnItem()));
                SalesOrderEntity order = new SalesOrderEntity();
                order.setId(ORDER_ID);
                order.setOrderNo("SO-1");
                when(salesOrderDao.selectBatchIds(List.of(ORDER_ID))).thenReturn(List.of(order));
                when(salesOrderItemDao.listByOrderIds(List.of(ORDER_ID))).thenReturn(List.of(orderItem()));
                when(orderRefundDao.listByReturnIds(List.of(RETURN_ID)))
                        .thenReturn(refund == null ? List.of() : List.of(refund));
                when(orderQueryService.skuImageUrls(any())).thenReturn(Map.of());
            }

            private static RequestMallCustomer customer() {
                RequestMallCustomer result = new RequestMallCustomer();
                result.setAccountId(7L);
                result.setCustomerId(9L);
                return result;
            }

            private static OrderReturnEntity returnEntity(String status) {
                OrderReturnEntity entity = new OrderReturnEntity();
                entity.setId(RETURN_ID);
                entity.setOrderId(ORDER_ID);
                entity.setCustomerId(9L);
                entity.setStatus(status);
                entity.setReason("商品损坏");
                entity.setApprovedAmount(new BigDecimal("30.0000"));
                entity.setCreatedAt(OffsetDateTime.now());
                return entity;
            }

            private static OrderReturnItemEntity returnItem() {
                OrderReturnItemEntity item = new OrderReturnItemEntity();
                item.setId(301L);
                item.setReturnId(RETURN_ID);
                item.setOrderItemId(101L);
                item.setRequestedQuantity(new BigDecimal("2.0000"));
                item.setLockedUnitPrice(new BigDecimal("15.0000"));
                return item;
            }

            private static SalesOrderItemEntity orderItem() {
                SalesOrderItemEntity item = new SalesOrderItemEntity();
                item.setId(101L);
                item.setOrderId(ORDER_ID);
                item.setSkuId(11L);
                item.setProductNameSnapshot("上海青");
                return item;
            }

            private static OrderRefundEntity refund(String status) {
                OrderRefundEntity refund = new OrderRefundEntity();
                refund.setId(44L);
                refund.setReturnId(RETURN_ID);
                refund.setOrderId(ORDER_ID);
                refund.setCustomerId(9L);
                refund.setRefundAmount(new BigDecimal("30.0000"));
                refund.setStatus(status);
                if ("COMPLETED".equals(status)) {
                    refund.setCompletedAt(OffsetDateTime.now());
                }
                return refund;
            }

            private static RefundFundingAllocationEntity allocation(String method, String amount, String status) {
                RefundFundingAllocationEntity allocation = new RefundFundingAllocationEntity();
                allocation.setSourceMethod(method);
                allocation.setAllocatedAmount(new BigDecimal(amount));
                allocation.setStatus(status);
                return allocation;
            }

            private static MallAfterSaleFundingTimeVO fundingTime(Long refundId, Long allocationId, OffsetDateTime at) {
                MallAfterSaleFundingTimeVO row = new MallAfterSaleFundingTimeVO();
                row.setRefundId(refundId);
                row.setAllocationId(allocationId);
                row.setSucceededAt(at);
                return row;
            }
        }

        @Nested

        class MallAfterSaleServiceTest {
            private static final Long ORDER_ID = 21L;
            private static final Long RETURN_ID = 33L;

            private final MallOrderQueryService orderQueryService = mock(MallOrderQueryService.class);
            private final MallAfterSaleQueryService afterSaleQueryService = mock(MallAfterSaleQueryService.class);
            private final OrderReturnService orderReturnService = mock(OrderReturnService.class);
            private final MallAfterSaleService service = new MallAfterSaleService(orderQueryService, afterSaleQueryService,
                    orderReturnService);
            private final RequestMallCustomer customer = customer();

            @Test
            void applyMapsLinesToTheOfficialReturnFormAfterOwnershipCheck() {
                OrderReturnDetailVO created = new OrderReturnDetailVO();
                created.setReturnId(RETURN_ID);
                when(orderReturnService.createFromMall(any(), eq("key"))).thenReturn(created);
                MallAfterSaleVO projected = new MallAfterSaleVO();
                when(afterSaleQueryService.detail(customer, RETURN_ID)).thenReturn(projected);

                MallAfterSaleVO result = service.apply(customer, applyForm(), "key");

                assertThat(result).isSameAs(projected);
                verify(orderQueryService).requireOwnedOrder(customer, ORDER_ID);
                ArgumentCaptor<OrderReturnAddForm> captor = ArgumentCaptor.forClass(OrderReturnAddForm.class);
                verify(orderReturnService).createFromMall(captor.capture(), eq("key"));
                assertThat(captor.getValue().getOrderId()).isEqualTo(ORDER_ID);
                assertThat(captor.getValue().getReason()).isEqualTo("商品损坏");
                assertThat(captor.getValue().getItems()).hasSize(1);
                assertThat(captor.getValue().getItems().getFirst().getOrderItemId()).isEqualTo(101L);
                assertThat(captor.getValue().getItems().getFirst().getRequestedQuantity()).isEqualTo("1.5");
            }

            @Test
            void cancelPassesCurrentVersionAndFixedCustomerReason() {
                when(afterSaleQueryService.requireOwnedReturn(customer, RETURN_ID)).thenReturn(returnEntity("PENDING", 4));

                service.cancel(customer, RETURN_ID, "key");

                ArgumentCaptor<OrderReturnDecisionForm> captor = ArgumentCaptor.forClass(OrderReturnDecisionForm.class);
                verify(orderReturnService).cancelFromMall(captor.capture(), eq("key"));
                assertThat(captor.getValue().getReturnId()).isEqualTo(RETURN_ID);
                assertThat(captor.getValue().getVersion()).isEqualTo(4);
                assertThat(captor.getValue().getDecisionReason()).isEqualTo("客户自行撤销");
            }

            @Test
            void cancelledRequestStillReachesTheSameIdempotencyKeyAfterVersionAdvances() {
                when(afterSaleQueryService.requireOwnedReturn(customer, RETURN_ID))
                        .thenReturn(returnEntity("PENDING", 4), returnEntity("CANCELLED", 5));
                MallAfterSaleVO projected = new MallAfterSaleVO();
                when(afterSaleQueryService.detail(customer, RETURN_ID)).thenReturn(projected);

                assertThat(service.cancel(customer, RETURN_ID, "same-key")).isSameAs(projected);
                assertThat(service.cancel(customer, RETURN_ID, "same-key")).isSameAs(projected);

                ArgumentCaptor<OrderReturnDecisionForm> captor = ArgumentCaptor.forClass(OrderReturnDecisionForm.class);
                verify(orderReturnService, times(2)).cancelFromMall(captor.capture(), eq("same-key"));
                assertThat(captor.getAllValues()).extracting(OrderReturnDecisionForm::getVersion)
                        .containsExactly(4, 5);
            }

            @Test
            void reviewedReturnCannotBeCancelledByTheCustomer() {
                when(afterSaleQueryService.requireOwnedReturn(customer, RETURN_ID)).thenReturn(returnEntity("APPROVED", 1));

                assertThatThrownBy(() -> service.cancel(customer, RETURN_ID, "key"))
                        .isInstanceOfSatisfying(ScmBusinessException.class, exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(MallErrorCode.AFTERSALE_NOT_CANCELABLE));
                verify(orderReturnService, never()).cancelFromMall(any(), any());
            }

            @Test
            void returnOwnedByAnotherCustomerIsInvisible() {
                when(afterSaleQueryService.requireOwnedReturn(customer, RETURN_ID))
                        .thenThrow(new ScmBusinessException(MallErrorCode.AFTERSALE_NOT_FOUND));

                assertThatThrownBy(() -> service.cancel(customer, RETURN_ID, "key"))
                        .isInstanceOfSatisfying(ScmBusinessException.class, exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(MallErrorCode.AFTERSALE_NOT_FOUND));
                verify(orderReturnService, never()).cancelFromMall(any(), any());
            }

            @Test
            void otherCustomersOrderCannotCreateAnAfterSaleRequest() {
                when(orderQueryService.requireOwnedOrder(customer, ORDER_ID))
                        .thenThrow(new ScmBusinessException(MallErrorCode.ORDER_NOT_FOUND));

                assertThatThrownBy(() -> service.apply(customer, applyForm(), "key"))
                        .isInstanceOfSatisfying(ScmBusinessException.class, exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(MallErrorCode.ORDER_NOT_FOUND));
                verify(orderReturnService, never()).createFromMall(any(), any());
            }

            private static RequestMallCustomer customer() {
                RequestMallCustomer result = new RequestMallCustomer();
                result.setAccountId(7L);
                result.setCustomerId(9L);
                return result;
            }

            private static MallAfterSaleApplyForm applyForm() {
                MallAfterSaleApplyForm form = new MallAfterSaleApplyForm();
                form.setOrderId(ORDER_ID);
                form.setReason("商品损坏");
                MallAfterSaleApplyForm.Item item = new MallAfterSaleApplyForm.Item();
                item.setOrderItemId(101L);
                item.setQuantity("1.5");
                form.setItems(java.util.List.of(item));
                return form;
            }

            private static OrderReturnEntity returnEntity(String status, int version) {
                OrderReturnEntity entity = new OrderReturnEntity();
                entity.setId(RETURN_ID);
                entity.setOrderId(ORDER_ID);
                entity.setCustomerId(9L);
                entity.setStatus(status);
                entity.setVersion(version);
                return entity;
            }
        }

        @Nested

        class MallAuthServiceTest {
            private final MallCustomerAccountDao accounts = mock(MallCustomerAccountDao.class);
            private final CustomerService customers = mock(CustomerService.class);
            private final SecurityLoginService securityLogin = mock(SecurityLoginService.class);
            private final MallWechatService wechat = mock(MallWechatService.class);
            private final MallAuthService service = new MallAuthService(accounts, customers, securityLogin, wechat);

            @Test
            void wechatLoginUsesOfficialPhoneAndDoesNotClearInitialPasswordFlag() {
                MallCustomerAccountEntity account = account(2);
                account.setFirstPasswordChange(true);
                when(wechat.phone("phone-code")).thenReturn("13800000000");
                when(accounts.selectByPhone("13800000000")).thenReturn(account);
                when(accounts.selectByIdForUpdate(7L)).thenReturn(account);
                when(securityLogin.checkLogin(7L, UserTypeEnum.MALL_CUSTOMER)).thenReturn(ResponseDTO.ok());
                CustomerEntity customer = new CustomerEntity();
                customer.setId(9L);
                customer.setName("测试客户");
                when(customers.requireTradable(9L)).thenReturn(customer);

                try (MockedStatic<StpUtil> session = mockStatic(StpUtil.class)) {
                    var result = service.wechatPhoneLogin(wechatForm(), "127.0.0.1");
                    assertThat(result.getProfile().getFirstPasswordChange()).isFalse();
                    session.verify(() -> StpUtil.login(MallAuthService.loginId(7L, 2) + ":WECHAT_PHONE", "MALL"));
                    session.verify(() -> StpUtil.login(MallAuthService.loginId(7L, 2), "MALL"), never());
                }
                assertThat(account.getFirstPasswordChange()).isTrue();
                verify(accounts, never()).changePassword(any(), any());
                verify(accounts).recordLogin(7L, "127.0.0.1");
            }

            @Test
            void authenticationSourcesStaySeparateWhenRestoringProfile() {
                MallCustomerAccountEntity account = account(2);
                account.setFirstPasswordChange(true);
                when(accounts.selectById(7L)).thenReturn(account);
                CustomerEntity customer = new CustomerEntity();
                customer.setId(9L);
                when(customers.requireTradable(9L)).thenReturn(customer);
                var password = service.requestCustomer(MallAuthService.loginId(7L, 2), request());
                var phone = service.requestCustomer(MallAuthService.loginId(7L, 2) + ":WECHAT_PHONE", request());
                assertThat(password.getLoginMethod()).isEqualTo(MallLoginMethodEnum.PASSWORD);
                assertThat(password.getFirstPasswordChange()).isTrue();
                assertThat(phone.getFirstPasswordChange()).isFalse();
                assertThat(service.profile(password).getFirstPasswordChange()).isTrue();
                assertThat(service.profile(phone).getFirstPasswordChange()).isFalse();
                assertThat(account.getFirstPasswordChange()).isTrue();
            }

            @Test
            void invalidSourcesAndStaleWechatCredentialsCannotRestoreSession() {
                when(accounts.selectById(7L)).thenReturn(account(2));
                assertThat(service.requestCustomer(MallAuthService.loginId(7L, 1) + ":WECHAT_PHONE", request())).isNull();
                assertThat(service.requestCustomer(MallAuthService.loginId(7L, 2) + ":OTHER", request())).isNull();
                assertThat(service.requestCustomer(MallAuthService.loginId(7L, 2) + ":WECHAT_PHONE:extra", request())).isNull();
                verify(customers, never()).requireTradable(any());
            }

            @Test
            void disabledAccountCannotUseWechatToBypassAccountStatus() {
                var account = account(2);
                account.setStatus("DISABLED");
                when(wechat.phone("phone-code")).thenReturn("13800000000");
                when(accounts.selectByPhone("13800000000")).thenReturn(account);
                when(accounts.selectByIdForUpdate(7L)).thenReturn(account);
                assertThatThrownBy(() -> service.wechatPhoneLogin(wechatForm(), "127.0.0.1"))
                    .isInstanceOfSatisfying(ScmBusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(MallErrorCode.ACCOUNT_DISABLED));
                verify(accounts, never()).recordLogin(any(), any());
            }

            @Test
            void unopenedPhoneIsRejectedWithoutCreatingAnAccount() {
                when(wechat.phone("phone-code")).thenReturn("13800000000");
                assertThatThrownBy(() -> service.wechatPhoneLogin(wechatForm(), "127.0.0.1"))
                    .isInstanceOfSatisfying(ScmBusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(MallErrorCode.WECHAT_ACCOUNT_NOT_OPENED));
                verify(accounts, never()).recordLogin(any(), any());
            }

            @Test
            void lockedAccountAndNonTradableCustomerCannotSignWechatSession() {
                var account = account(2);
                when(wechat.phone("phone-code")).thenReturn("13800000000");
                when(accounts.selectByPhone("13800000000")).thenReturn(account);
                when(accounts.selectByIdForUpdate(7L)).thenReturn(account);
                when(securityLogin.checkLogin(7L, UserTypeEnum.MALL_CUSTOMER))
                    .thenReturn(new ResponseDTO<>(ResponseDTO.OK_CODE, null, false, "locked"));
                assertThatThrownBy(() -> service.wechatPhoneLogin(wechatForm(), "127.0.0.1"))
                    .isInstanceOfSatisfying(ScmBusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(MallErrorCode.LOGIN_LOCKED));
                when(securityLogin.checkLogin(7L, UserTypeEnum.MALL_CUSTOMER)).thenReturn(ResponseDTO.ok());
                when(customers.requireTradable(9L)).thenThrow(new ScmBusinessException(MallErrorCode.ACCOUNT_DISABLED));
                assertThatThrownBy(() -> service.wechatPhoneLogin(wechatForm(), "127.0.0.1"))
                    .isInstanceOf(ScmBusinessException.class);
                verify(accounts, never()).recordLogin(any(), any());
            }

            private static MallWechatPhoneLoginForm wechatForm() {
                var form = new MallWechatPhoneLoginForm();
                form.setCode("phone-code");
                return form;
            }

            @Test
            void staleCredentialVersionCannotBuildRequestIdentity() {
                when(accounts.selectById(7L)).thenReturn(account(2));

                RequestMallCustomer result = service.requestCustomer(MallAuthService.loginId(7L, 1), request());

                assertThat(result).isNull();
                verify(customers, never()).requireTradable(9L);
            }

            @Test
            void currentCredentialVersionIsCarriedIntoRequestIdentity() {
                when(accounts.selectById(7L)).thenReturn(account(2));
                CustomerEntity customer = new CustomerEntity();
                customer.setId(9L);
                customer.setName("测试客户");
                when(customers.requireTradable(9L)).thenReturn(customer);

                RequestMallCustomer result = service.requestCustomer(MallAuthService.loginId(7L, 2), request());

                assertThat(result).isNotNull();
                assertThat(result.getAccountId()).isEqualTo(7L);
                assertThat(result.getSessionVersion()).isEqualTo(2);
            }

            @Test
            void staleSessionCannotChangePasswordAfterAnotherCredentialMutation() {
                MallCustomerAccountEntity account = account(2);
                account.setPasswordHash(SecurityPasswordService.getEncryptPwd("old-password"));
                when(accounts.selectByIdForUpdate(7L)).thenReturn(account);
                RequestMallCustomer requestCustomer = new RequestMallCustomer();
                requestCustomer.setAccountId(7L);
                requestCustomer.setSessionVersion(1);
                MallPasswordChangeForm form = new MallPasswordChangeForm();
                form.setOldPassword("old-password");
                form.setNewPassword("new-password");

                assertThatThrownBy(() -> service.changePassword(requestCustomer, form))
                        .isInstanceOfSatisfying(ScmBusinessException.class,
                                exception -> assertThat(exception.getErrorCode()).isEqualTo(MallErrorCode.LOGIN_FAILED));
                verify(accounts, never()).changePassword(org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
            }

            @Test
            void newPasswordEqualToOldIsRejectedWithItsOwnCode() {
                MallCustomerAccountEntity account = account(2);
                account.setPasswordHash(SecurityPasswordService.getEncryptPwd("same-password"));
                when(accounts.selectByIdForUpdate(7L)).thenReturn(account);
                when(securityLogin.checkLogin(7L, UserTypeEnum.MALL_CUSTOMER)).thenReturn(ResponseDTO.ok());
                RequestMallCustomer requestCustomer = new RequestMallCustomer();
                requestCustomer.setAccountId(7L);
                requestCustomer.setSessionVersion(2);
                MallPasswordChangeForm form = new MallPasswordChangeForm();
                form.setOldPassword("same-password");
                form.setNewPassword("same-password");

                assertThatThrownBy(() -> service.changePassword(requestCustomer, form))
                        .isInstanceOfSatisfying(ScmBusinessException.class, exception -> {
                            assertThat(exception.getErrorCode()).isEqualTo(MallErrorCode.PASSWORD_SAME_AS_OLD);
                            // 码值固定，客户端才能稳定区分这条提示与密码错误
                            assertThat(exception.getErrorCode().getCode()).isEqualTo(40099);
                        });
                verify(accounts, never()).changePassword(any(), any());
                verify(securityLogin, never()).recordLoginFail(any(), any(), any(), any());
            }

            @Test
            void wrongOldPasswordIsCountedBeforeEqualPasswordCheck() {
                MallCustomerAccountEntity account = account(2);
                account.setPasswordHash(SecurityPasswordService.getEncryptPwd("right-password"));
                when(accounts.selectByIdForUpdate(7L)).thenReturn(account);
                LoginFailEntity previous = new LoginFailEntity();
                previous.setLoginFailCount(1);
                when(securityLogin.checkLogin(7L, UserTypeEnum.MALL_CUSTOMER)).thenReturn(ResponseDTO.ok(previous));
                var request = passwordRequest(2);
                var form = passwordForm("wrong-password", "wrong-password");

                assertThatThrownBy(() -> service.changePassword(request, form))
                    .isInstanceOfSatisfying(com.xsy.scm.mall.account.exception.MallLoginRejectedException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(MallErrorCode.LOGIN_FAILED));
                verify(securityLogin).recordLoginFail(7L, UserTypeEnum.MALL_CUSTOMER, "13800000000", previous);
                verify(accounts, never()).changePassword(any(), any());
            }

            @Test
            void staleSessionWithEqualPasswordsCannotConsumeCurrentFailureCount() {
                when(accounts.selectByIdForUpdate(7L)).thenReturn(account(2));
                assertThatThrownBy(() -> service.changePassword(passwordRequest(1), passwordForm("same-password", "same-password")))
                    .isInstanceOfSatisfying(ScmBusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(MallErrorCode.LOGIN_FAILED));
                verifyNoInteractions(securityLogin);
                verify(accounts, never()).changePassword(any(), any());
            }

            @Test
            void lockedAccountCannotTryOldPasswordOrChangeIt() {
                when(accounts.selectByIdForUpdate(7L)).thenReturn(account(2));
                when(securityLogin.checkLogin(7L, UserTypeEnum.MALL_CUSTOMER))
                    .thenReturn(new ResponseDTO<>(ResponseDTO.OK_CODE, null, false, "locked"));
                // 没有密码哈希；只有先检查限制，才不会继续进入密码核验。
                assertThatThrownBy(() -> service.changePassword(passwordRequest(2), passwordForm("same-password", "same-password")))
                    .isInstanceOfSatisfying(ScmBusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(MallErrorCode.LOGIN_LOCKED));
                verify(securityLogin, never()).recordLoginFail(any(), any(), any(), any());
                verify(accounts, never()).changePassword(any(), any());
            }

            @Test
            void disabledAccountCannotChangePasswordOrConsumeAttempts() {
                var account = account(2);
                account.setStatus("DISABLED");
                when(accounts.selectByIdForUpdate(7L)).thenReturn(account);
                assertThatThrownBy(() -> service.changePassword(passwordRequest(2), passwordForm("old-password", "new-password")))
                    .isInstanceOfSatisfying(ScmBusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(MallErrorCode.ACCOUNT_DISABLED));
                verifyNoInteractions(securityLogin);
                verify(accounts, never()).changePassword(any(), any());
            }

            @Test
            void successfulPasswordChangeClearsFailuresAndWritesTheChosenPassword() {
                var account = account(2);
                account.setPasswordHash(SecurityPasswordService.getEncryptPwd("old-password"));
                when(accounts.selectByIdForUpdate(7L)).thenReturn(account);
                when(securityLogin.checkLogin(7L, UserTypeEnum.MALL_CUSTOMER)).thenReturn(ResponseDTO.ok());
                var customer = new CustomerEntity();
                customer.setId(9L);
                when(customers.requireTradable(9L)).thenReturn(customer);
                service.changePassword(passwordRequest(2), passwordForm("old-password", "new-password"));
                var hash = org.mockito.ArgumentCaptor.forClass(String.class);
                verify(accounts).changePassword(eq(7L), hash.capture());
                assertThat(SecurityPasswordService.matchesPwd("new-password", hash.getValue())).isTrue();
                verify(securityLogin).removeLoginFail(7L, UserTypeEnum.MALL_CUSTOMER);
                verify(securityLogin, never()).recordLoginFail(any(), any(), any(), any());
            }

            private static RequestMallCustomer passwordRequest(int version) {
                var request = new RequestMallCustomer();
                request.setAccountId(7L);
                request.setSessionVersion(version);
                return request;
            }

            @Test
            @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "XSY_PASSWORD_TX_IT_URL", matches = "jdbc:postgresql:.*")
            void passwordFailureCommitsAndUnexpectedBusinessFailureRollsBack() {
                var dataSource = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                    System.getenv("XSY_PASSWORD_TX_IT_URL"),
                    com.xsy.scm.admin.test.PgITDatabase.user(), com.xsy.scm.admin.test.PgITDatabase.password());
                var db = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
                assertThat(db.queryForObject("SELECT current_database()", String.class))
                    .as("只允许独立_it测试库，不能将计数夹具写入开发库").endsWith("_it");
                String schema = "password_" + UUID.randomUUID().toString().replace("-", "");
                db.execute("CREATE SCHEMA " + schema);
                try {
                    // JDBC适配只替换持久层；失败次数算法、Spring事务与正式改密方法保持真实。
                    db.execute("CREATE TABLE " + schema + ".account (id bigint primary key, password_hash text)");
                    db.execute("CREATE TABLE " + schema + ".failure (id bigint primary key, attempts integer, locked_at timestamp)");
                    String initialHash = SecurityPasswordService.getEncryptPwd("old-password");
                    db.update("INSERT INTO " + schema + ".account VALUES (7, ?)", initialHash);
                    var failureDao = mock(com.xsy.scm.base.module.support.securityprotect.dao.LoginFailDao.class);
                    var config = mock(com.xsy.scm.base.module.support.securityprotect.service.Level3ProtectConfigService.class);
                    when(config.getLoginFailMaxTimes()).thenReturn(2);
                    when(config.getLoginFailLockSeconds()).thenReturn(1800);
                    var realSecurity = new SecurityLoginService();
                    org.springframework.test.util.ReflectionTestUtils.setField(realSecurity, "level3ProtectConfigService", config);
                    org.springframework.test.util.ReflectionTestUtils.setField(realSecurity, "loginFailDao", failureDao);
                    when(failureDao.selectByUserIdAndUserType(7L, UserTypeEnum.MALL_CUSTOMER.getValue())).thenAnswer(call -> {
                        var rows = db.query("SELECT * FROM " + schema + ".failure", (row, index) -> {
                            var fail = new LoginFailEntity();
                            fail.setLoginFailId(row.getLong("id"));
                            fail.setLoginFailCount(row.getInt("attempts"));
                            var lockedAt = row.getTimestamp("locked_at");
                            fail.setLoginLockBeginTime(lockedAt == null ? null : lockedAt.toLocalDateTime());
                            return fail;
                        });
                        return rows.isEmpty() ? null : rows.get(0);
                    });
                    when(failureDao.insert(any(LoginFailEntity.class))).thenAnswer(call -> {
                        LoginFailEntity fail = call.getArgument(0);
                        return db.update("INSERT INTO " + schema + ".failure VALUES (1, ?, ?)",
                            fail.getLoginFailCount(), fail.getLoginLockBeginTime());
                    });
                    when(failureDao.updateById(any(LoginFailEntity.class))).thenAnswer(call -> {
                        LoginFailEntity fail = call.getArgument(0);
                        return db.update("UPDATE " + schema + ".failure SET attempts=?, locked_at=? WHERE id=1",
                            fail.getLoginFailCount(), fail.getLoginLockBeginTime());
                    });
                    org.mockito.Mockito.doAnswer(call -> {
                        db.update("DELETE FROM " + schema + ".failure");
                        return null;
                    }).when(failureDao).deleteByUserIdAndUserType(7L, UserTypeEnum.MALL_CUSTOMER.getValue());
                    when(accounts.selectByIdForUpdate(7L)).thenAnswer(call -> {
                        var row = account(2);
                        row.setPasswordHash(db.queryForObject("SELECT password_hash FROM " + schema + ".account WHERE id=7 FOR UPDATE", String.class));
                        return row;
                    });
                    when(accounts.changePassword(eq(7L), any(String.class))).thenAnswer(call ->
                        db.update("UPDATE " + schema + ".account SET password_hash=? WHERE id=7", call.getArgument(1, String.class)));
                    var target = new MallAuthService(accounts, customers, realSecurity, wechat);
                    var proxy = new org.springframework.aop.framework.ProxyFactory(target);
                    proxy.setProxyTargetClass(true);
                    proxy.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(
                        new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource),
                        new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
                    var auth = (MallAuthService) proxy.getProxy();
                    for (int attempt = 1; attempt <= 2; attempt++) {
                        assertThatThrownBy(() -> auth.changePassword(passwordRequest(2), passwordForm("wrong-password", "wrong-password")))
                            .isInstanceOfSatisfying(ScmBusinessException.class,
                                error -> assertThat(error.getErrorCode()).isEqualTo(MallErrorCode.LOGIN_FAILED));
                        assertThat(db.queryForObject("SELECT attempts FROM " + schema + ".failure", Integer.class)).isEqualTo(attempt);
                    }
                    assertThatThrownBy(() -> auth.changePassword(passwordRequest(2), passwordForm("old-password", "new-password")))
                        .isInstanceOfSatisfying(ScmBusinessException.class,
                            error -> assertThat(error.getErrorCode()).isEqualTo(MallErrorCode.LOGIN_LOCKED));
                    assertThat(db.queryForObject("SELECT password_hash FROM " + schema + ".account", String.class)).isEqualTo(initialHash);
                    db.update("DELETE FROM " + schema + ".failure");
                    db.update("INSERT INTO " + schema + ".failure VALUES (1, 1, NULL)");
                    when(customers.requireTradable(9L)).thenThrow(new ScmBusinessException(MallErrorCode.ACCOUNT_DISABLED));
                    assertThatThrownBy(() -> auth.changePassword(passwordRequest(2), passwordForm("old-password", "new-password")))
                        .isInstanceOf(ScmBusinessException.class);
                    assertThat(db.queryForObject("SELECT password_hash FROM " + schema + ".account", String.class)).isEqualTo(initialHash);
                    assertThat(db.queryForObject("SELECT attempts FROM " + schema + ".failure", Integer.class)).isEqualTo(1);
                    var customer = new CustomerEntity();
                    customer.setId(9L);
                    org.mockito.Mockito.doReturn(customer).when(customers).requireTradable(9L);
                    auth.changePassword(passwordRequest(2), passwordForm("old-password", "new-password"));
                    assertThat(db.queryForObject("SELECT count(*) FROM " + schema + ".failure", Integer.class)).isZero();
                    assertThat(SecurityPasswordService.matchesPwd("new-password",
                        db.queryForObject("SELECT password_hash FROM " + schema + ".account", String.class))).isTrue();
                } finally {
                    db.execute("DROP SCHEMA " + schema + " CASCADE");
                }
            }

            private static MallPasswordChangeForm passwordForm(String oldPassword, String newPassword) {
                var form = new MallPasswordChangeForm();
                form.setOldPassword(oldPassword);
                form.setNewPassword(newPassword);
                return form;
            }

            @Test
            void lockedAccountIsRejectedBeforeAnyPasswordCheck() {
                MallCustomerAccountEntity account = account(2);
                when(accounts.selectByPhone("13800000000")).thenReturn(account);
                when(accounts.selectByIdForUpdate(7L)).thenReturn(account);
                // ResponseDTO 没有无参构造，直接用带参构造造一个「未通过」的检查结果
                ResponseDTO<LoginFailEntity> locked = new ResponseDTO<>(ResponseDTO.OK_CODE, null, false, "登录失败次数过多");
                when(securityLogin.checkLogin(7L, UserTypeEnum.MALL_CUSTOMER)).thenReturn(locked);
                MallLoginForm form = new MallLoginForm();
                form.setPhone("13800000000");
                form.setPassword("whatever");

                assertThatThrownBy(() -> service.login(form, "127.0.0.1"))
                        .isInstanceOfSatisfying(ScmBusinessException.class,
                                exception -> assertThat(exception.getErrorCode()).isEqualTo(MallErrorCode.LOGIN_LOCKED));
                // 锁定期内不再累加计数，也不签发会话
                verify(securityLogin, never()).recordLoginFail(any(), any(), any(), any());
                verify(accounts, never()).recordLogin(any(), any());
            }

            @Test
            void wrongPasswordRecordsFailureSoConsecutiveAttemptsCanLock() {
                MallCustomerAccountEntity account = account(2);
                account.setPasswordHash(SecurityPasswordService.getEncryptPwd("right-password"));
                when(accounts.selectByPhone("13800000000")).thenReturn(account);
                when(accounts.selectByIdForUpdate(7L)).thenReturn(account);
                LoginFailEntity fail = new LoginFailEntity();
                fail.setLoginFailCount(1);
                when(securityLogin.checkLogin(7L, UserTypeEnum.MALL_CUSTOMER)).thenReturn(ResponseDTO.ok(fail));
                MallLoginForm form = new MallLoginForm();
                form.setPhone("13800000000");
                form.setPassword("wrong-password");

                assertThatThrownBy(() -> service.login(form, "127.0.0.1"))
                        .isInstanceOfSatisfying(ScmBusinessException.class,
                                exception -> assertThat(exception.getErrorCode()).isEqualTo(MallErrorCode.LOGIN_FAILED));
                // 失败计数必须写入数据库（login 的 noRollbackFor 就是为这条），否则连续输错永远锁不上
                verify(securityLogin).recordLoginFail(7L, UserTypeEnum.MALL_CUSTOMER, "13800000000", fail);
                verify(accounts, never()).recordLogin(any(), any());
            }

            @Test
            void successfulLoginClearsFailureCountAndSignsSessionWithCredentialVersion() {
                MallCustomerAccountEntity account = account(2);
                account.setPasswordHash(SecurityPasswordService.getEncryptPwd("right-password"));
                when(accounts.selectByPhone("13800000000")).thenReturn(account);
                when(accounts.selectByIdForUpdate(7L)).thenReturn(account);
                when(securityLogin.checkLogin(7L, UserTypeEnum.MALL_CUSTOMER)).thenReturn(ResponseDTO.ok());
                CustomerEntity customer = new CustomerEntity();
                customer.setId(9L);
                customer.setName("测试客户");
                when(customers.requireTradable(9L)).thenReturn(customer);
                MallLoginForm form = new MallLoginForm();
                form.setPhone("13800000000");
                form.setPassword("right-password");

                try (MockedStatic<StpUtil> ignored = mockStatic(StpUtil.class)) {
                    service.login(form, "127.0.0.1");
                    // 登录身份带凭证版本，改密后旧令牌解析不出身份
                    ignored.verify(() -> StpUtil.login(MallAuthService.loginId(7L, 2), "MALL"));
                }

                verify(securityLogin).removeLoginFail(7L, UserTypeEnum.MALL_CUSTOMER);
                verify(accounts).recordLogin(7L, "127.0.0.1");
            }

            private static MallCustomerAccountEntity account(int version) {
                MallCustomerAccountEntity account = new MallCustomerAccountEntity();
                account.setId(7L);
                account.setCustomerId(9L);
                account.setLoginPhone("13800000000");
                account.setStatus("ENABLED");
                account.setVersion(version);
                account.setFirstPasswordChange(false);
                return account;
            }

            private static HttpServletRequest request() {
                HttpServletRequest request = mock(HttpServletRequest.class);
                when(request.getRemoteAddr()).thenReturn("127.0.0.1");
                return request;
            }
        }

        @Nested

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

        @Nested

        class MallCatalogServiceTest {
            private final MallCatalogDao catalog = mock(MallCatalogDao.class);
            private final MallCatalogService service = new MallCatalogService(catalog, mock(PriceResolver.class),
                    mock(CustomerFrequentSkuDao.class), mock(FileService.class), mock(MallWarehouseService.class),
                    mock(MallStockAvailabilityService.class));

            @Test
            void invisibleProductUsesTheSameNotFoundResult() {
                RequestMallCustomer customer = new RequestMallCustomer();
                customer.setCustomerId(8L);
                when(catalog.product(8L, 99L)).thenReturn(null);
                assertThatThrownBy(() -> service.product(customer, 99L)).isInstanceOfSatisfying(ScmBusinessException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(MallErrorCode.CATALOG_PRODUCT_NOT_FOUND));
            }
        }

        @Nested

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
                XsyRequestUtil.setRequestUser(customer);
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
                XsyRequestUtil.remove();
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
                // 若防重复处理指纹直接取订单表单（含下单时保存的地址），重试时哈希就变了，无法返回原订单。
                when(salesOrderService.replayMallOrder(any(), eq("key"))).thenReturn(Optional.empty());
                when(salesOrderService.createFromMall(any(), any(), eq("key")))
                        .thenReturn(new MallOrderCreationResult(order(), false));
                when(addressDao.detailById(9L, 3L)).thenReturn(address()).thenReturn(editedAddress());

                service.submit(customer, form, "key");
                service.submit(customer, form, "key");

                ArgumentCaptor<SalesOrderAddForm> forms = ArgumentCaptor.forClass(SalesOrderAddForm.class);
                ArgumentCaptor<Object> requests = ArgumentCaptor.forClass(Object.class);
                verify(salesOrderService, times(2)).createFromMall(forms.capture(), requests.capture(), eq("key"));
                // 指纹不受地址内容影响：两次请求的防重复处理身份相同
                assertThat(requests.getAllValues().get(0)).isEqualTo(requests.getAllValues().get(1));
                // 而建单用的下单时保存的地址确实变了，说明指纹没有跟着地址内容走
                assertThat(forms.getAllValues().get(0).getAddress().getReceiverName()).isEqualTo("张三");
                assertThat(forms.getAllValues().get(1).getAddress().getReceiverName()).isEqualTo("李四");
            }

            @Test
            void alreadySucceededRequestIsReturnedBeforeThePriceCheck() {
                // 第一次下单已成功但响应丢失，客户重试前商品变了价：此时价格指纹与当前价都对不上，
                // 但防重复处理预检必须先把原订单还回去，否则客户会收到「价格已变化」而以为没下成。
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
                // 重试必须先返回原订单；若先读地址，就会抛「地址不存在」，把一笔已成功的订单藏起来。
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
                // 防重复处理预检提前不能把地址校验弄丢：没有已成功的原订单时，地址不存在仍要拒绝
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

        @Nested

        class MallDecorationServiceTest {
            private final MallDecorationDao decorationDao = mock(MallDecorationDao.class);
            private final MallHomeLayoutService layoutService = mock(MallHomeLayoutService.class);
            private final MallDecorationService service = new MallDecorationService(decorationDao, layoutService);

            @Test
            void storefrontIsPublicAndDoesNotExposeAdministrationFields() {
                MallStoreVO store = new MallStoreVO();
                store.setStoreName("鲜蔬源");
                store.setStoreLink("https://example.com/mall");
                store.setVersion(7);
                store.setUpdatedBy("internal-operator");
                when(decorationDao.store()).thenReturn(store);

                var result = service.storefront();

                assertThat(result.storeName()).isEqualTo("鲜蔬源");
                assertThat(result.storeLink()).isEqualTo("https://example.com/mall");
                assertThat(result.getClass().getRecordComponents()).extracting(component -> component.getName())
                    .containsExactly("storeName", "storeLink");
                verify(layoutService, never()).requireGlobalCustomerScope();
            }

            @Test
            void unconfiguredStorefrontUsesPublicDefaults() {
                assertThat(service.storefront().storeName()).isEqualTo("商城");
                assertThat(service.storefront().storeLink()).isNull();
            }

            @BeforeEach
            void setUpOperator() {
                RequestEmployee employee = new RequestEmployee();
                employee.setEmployeeId(1L);
                employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
                XsyRequestUtil.setRequestUser(employee);
            }

            @AfterEach
            void tearDownOperator() {
                XsyRequestUtil.remove();
            }

            @Test
            void publishedPageUsesTheTitleSnapshotFromTheSameRelease() {
                MallPageVO page = page();
                page.setTitle("改过但还没发布的标题");
                page.setPublishedTitle("线上标题");
                page.setPublishedContent("<p>线上正文</p>");
                when(decorationDao.page("ABOUT")).thenReturn(page);

                var published = service.publishedPage("ABOUT");

                assertThat(published.title()).isEqualTo("线上标题");
                assertThat(published.content()).isEqualTo("<p>线上正文</p>");
            }

            @Test
            void imageOnlyPageCanBePublished() {
                MallPageVO page = page();
                page.setContent("<p><img src=\"https://cdn.example.com/about.png\" alt=\"资质说明\"></p>");
                page.setPublishedVersion(1);
                page.setPublishedContent(page.getContent());
                when(decorationDao.lockPage("ABOUT")).thenReturn(page);
                when(decorationDao.publishPage("ABOUT", 1, "1:1")).thenReturn(1);
                when(decorationDao.page("ABOUT")).thenReturn(page);

                service.publishPage(publishForm());

                verify(decorationDao).publishPage("ABOUT", 1, "1:1");
            }

            @Test
            void blankPageIsStillRejectedOnPublish() {
                MallPageVO page = page();
                page.setContent("<p>  </p>");
                when(decorationDao.lockPage("ABOUT")).thenReturn(page);

                assertThatThrownBy(() -> service.publishPage(publishForm()))
                    .isInstanceOf(ScmBusinessException.class);
                verify(decorationDao, never()).publishPage("ABOUT", 1, "1:1");
            }

            private static MallPagePublishForm publishForm() {
                MallPagePublishForm form = new MallPagePublishForm();
                form.setPageCode("ABOUT");
                form.setVersion(1);
                return form;
            }

            private static MallPageVO page() {
                MallPageVO page = new MallPageVO();
                page.setPageCode("ABOUT");
                page.setTitle("关于我们");
                page.setContent("");
                page.setVersion(1);
                return page;
            }
        }

        @Nested

        class MallFundsQueryServiceTest {
            private final CustomerService customers = mock(CustomerService.class);
            private final CustomerBalanceService balances = mock(CustomerBalanceService.class);
            private final MallFundsSourceDao sources = mock(MallFundsSourceDao.class);
            private final MallFundsQueryService service = new MallFundsQueryService(customers, balances, sources);

            @Test
            void childAccountCannotSeeSharedWalletTotalButCanRequestOnlyItsOwnMovements() {
                RequestMallCustomer identity = identity(20L);
                CustomerEntity child = customer(20L, 10L);
                CustomerEntity parent = customer(10L, 10L);
                when(customers.requireTradable(20L)).thenReturn(child);
                when(customers.requireSettlementAccount(child)).thenReturn(parent);

                var summary = service.walletSummary(identity);
                assertThat(summary.getBalanceVisible()).isFalse();
                assertThat(summary.getAvailableBalance()).isNull();
                assertThat(summary.getHasWallet()).isNull();
                verifyNoInteractions(balances);
                verify(sources, never()).walletAccountExists(10L);

                MallWalletMovementQueryForm form = new MallWalletMovementQueryForm();
                form.setPageNum(1L);
                form.setPageSize(20L);
                when(sources.walletMovements(any(), eq(20L), eq(10L), isNull(), isNull())).thenReturn(List.of());
                service.walletMovements(identity, form);
                verify(sources).walletMovements(any(), eq(20L), eq(10L), isNull(), isNull());
            }

            @Test
            void selfSettlingCustomerSeesItsOwnWallet() {
                RequestMallCustomer identity = identity(10L);
                CustomerEntity self = customer(10L, 10L);
                when(customers.requireTradable(10L)).thenReturn(self);
                when(customers.requireSettlementAccount(self)).thenReturn(self);
                when(sources.walletAccountExists(10L)).thenReturn(true);
                when(balances.balanceOfSettlement(10L)).thenReturn(new BigDecimal("88.0000"));

                var summary = service.walletSummary(identity);
                assertThat(summary.getBalanceVisible()).isTrue();
                assertThat(summary.getHasWallet()).isTrue();
                assertThat(summary.getAvailableBalance()).isEqualByComparingTo("88.0000");
            }

            @Test
            void sharedSettlementCreditSummaryAndReceivableListRemainScopedToThePurchasingCustomer() {
                RequestMallCustomer identity = identity(20L);
                CustomerEntity child = customer(20L, 10L);
                CustomerEntity parent = customer(10L, 10L);
                parent.setCreditLimit(new BigDecimal("1000.0000"));
                when(customers.requireTradable(20L)).thenReturn(child);
                when(customers.requireSettlementAccount(child)).thenReturn(parent);
                MallFundsSourceDao.CreditFacts facts = new MallFundsSourceDao.CreditFacts();
                facts.setOpenReceivableAmount(new BigDecimal("20.0000"));
                facts.setConfirmedOrderAmount(new BigDecimal("10.0000"));
                when(sources.creditFacts(20L)).thenReturn(facts);

                var summary = service.creditSummary(identity);
                assertThat(summary.getSharedSettlement()).isTrue();
                assertThat(summary.getCreditLimit()).isNull();
                assertThat(summary.getOpenReceivableAmount()).isEqualByComparingTo("20.0000");
                verify(sources).creditFacts(20L);

                MallReceivableQueryForm form = new MallReceivableQueryForm();
                form.setPageNum(1L);
                form.setPageSize(20L);
                when(sources.receivables(any(), eq(20L), isNull())).thenReturn(List.of());
                service.receivables(identity, form);
                verify(sources).receivables(any(), eq(20L), isNull());
            }

            @Test
            void missingOrOtherCustomerReceivableUsesTheSameNotFoundResult() {
                RequestMallCustomer identity = identity(20L);
                when(customers.requireTradable(20L)).thenReturn(customer(20L, 20L));

                assertThatThrownBy(() -> service.receivable(identity, 99L))
                        .isInstanceOfSatisfying(ScmBusinessException.class,
                                error -> assertThat(error.getErrorCode()).isEqualTo(MallErrorCode.FUNDS_RECEIVABLE_NOT_FOUND));
                verify(sources).receivable(20L, 99L);
            }

            private static RequestMallCustomer identity(Long customerId) {
                RequestMallCustomer identity = new RequestMallCustomer();
                identity.setCustomerId(customerId);
                return identity;
            }

            private static CustomerEntity customer(Long id, Long settlementId) {
                CustomerEntity customer = new CustomerEntity();
                customer.setId(id);
                customer.setSettlementCustomerId(settlementId);
                return customer;
            }
        }

        @Nested

        class MallHomeAdminPathTest {
            @Test
            @DisplayName("管理端路径不落进微信小程序的 /scm/mall/ 拦截范围")
            void adminPathIsNotUnderMallCustomerPrefix() {
                RequestMapping mapping = MallHomeAdminController.class.getAnnotation(RequestMapping.class);
                assertThat(mapping).isNotNull();
                String path = mapping.value()[0];

                assertThat(path).startsWith("/scm/mall-admin");
                assertThat(path).doesNotStartWith("/scm/mall/");
            }
        }

        @Nested

        class MallOrderDisplayStatusEnumTest {
            @Test
            void cancelledOrderStaysCancelledRegardlessOfFulfillment() {
                assertThat(MallOrderDisplayStatusEnum.derive("CANCELLED", null))
                        .isEqualTo(MallOrderDisplayStatusEnum.CANCELLED);
                assertThat(MallOrderDisplayStatusEnum.derive("CANCELLED", "IN_TRANSIT"))
                        .isEqualTo(MallOrderDisplayStatusEnum.CANCELLED);
            }

            @Test
            void unconfirmedOrdersArePending() {
                assertThat(MallOrderDisplayStatusEnum.derive("ARCHIVED", null)).isEqualTo(MallOrderDisplayStatusEnum.PENDING);
                assertThat(MallOrderDisplayStatusEnum.derive("PENDING", null)).isEqualTo(MallOrderDisplayStatusEnum.PENDING);
            }

            @Test
            void displayCategoryFollowsCurrentOrderStatus() {
                assertThat(MallOrderDisplayStatusEnum.derive("CONFIRMED", null))
                        .isEqualTo(MallOrderDisplayStatusEnum.PREPARING);
                assertThat(MallOrderDisplayStatusEnum.derive("CONFIRMED", "PENDING"))
                        .isEqualTo(MallOrderDisplayStatusEnum.PREPARING);
                assertThat(MallOrderDisplayStatusEnum.derive("DISPATCHED", "IN_TRANSIT"))
                        .isEqualTo(MallOrderDisplayStatusEnum.DELIVERING);
                assertThat(MallOrderDisplayStatusEnum.derive("SIGNED", "SIGNED"))
                        .isEqualTo(MallOrderDisplayStatusEnum.COMPLETED);
                assertThat(MallOrderDisplayStatusEnum.derive("CONFIRMED", "SIGNED"))
                        .isEqualTo(MallOrderDisplayStatusEnum.PREPARING);
                // 异常签收（含拒收）也是终态，原因在详情里展示
                assertThat(MallOrderDisplayStatusEnum.derive("EXCEPTION", "EXCEPTION"))
                        .isEqualTo(MallOrderDisplayStatusEnum.COMPLETED);
            }

            @Test
            void unknownOrderStatusFallsBackToPendingInsteadOfFailingTheWholeList() {
                assertThat(MallOrderDisplayStatusEnum.derive("SOMETHING_NEW", null))
                        .isEqualTo(MallOrderDisplayStatusEnum.PENDING);
                assertThat(MallOrderDisplayStatusEnum.derive(null, null)).isEqualTo(MallOrderDisplayStatusEnum.PENDING);
            }

            @Test
            void onlyPendingOrdersMayBeOfferedAsCancelable() {
                assertThat(MallOrderDisplayStatusEnum.PENDING.cancelable()).isTrue();
                assertThat(MallOrderDisplayStatusEnum.PREPARING.cancelable()).isFalse();
                assertThat(MallOrderDisplayStatusEnum.DELIVERING.cancelable()).isFalse();
                assertThat(MallOrderDisplayStatusEnum.COMPLETED.cancelable()).isFalse();
                assertThat(MallOrderDisplayStatusEnum.CANCELLED.cancelable()).isFalse();
            }
        }

        @Nested

        class MallOrderServiceTest {
            private static final Long ORDER_ID = 21L;

            private final MallOrderQueryService queryService = mock(MallOrderQueryService.class);
            private final SalesOrderService salesOrderService = mock(SalesOrderService.class);
            private final SalesOrderItemDao itemDao = mock(SalesOrderItemDao.class);
            private final MallCartService cartService = mock(MallCartService.class);
            private final MallCatalogService catalogService = mock(MallCatalogService.class);
            private final MallOrderService service = new MallOrderService(queryService, salesOrderService, itemDao,
                    cartService, catalogService);
            private final RequestMallCustomer customer = customer();

            @Test
            void cancelPassesTheLockedVersionAndReasonToTheOrderService() {
                when(queryService.requireOwnedOrder(customer, ORDER_ID)).thenReturn(order("PENDING", 4));
                when(queryService.activeFulfillmentStatus(ORDER_ID)).thenReturn(null);

                service.cancel(customer, ORDER_ID, cancelForm("下错单了"), "key");

                ArgumentCaptor<OrderCancelForm> captor = ArgumentCaptor.forClass(OrderCancelForm.class);
                verify(salesOrderService).cancelFromMall(captor.capture(), eq("key"));
                assertThat(captor.getValue().getOrderId()).isEqualTo(ORDER_ID);
                assertThat(captor.getValue().getVersion()).isEqualTo(4);
                assertThat(captor.getValue().getReason()).isEqualTo("下错单了");
            }

            @Test
            void confirmedOrderCannotBeCancelledByTheCustomer() {
                when(queryService.requireOwnedOrder(customer, ORDER_ID)).thenReturn(order("CONFIRMED", 1));

                assertThatThrownBy(() -> service.cancel(customer, ORDER_ID, cancelForm("不需要了"), "key"))
                        .isInstanceOfSatisfying(ScmBusinessException.class,
                                exception -> assertThat(exception.getErrorCode())
                                        .isEqualTo(MallErrorCode.ORDER_NOT_CANCELABLE));
                verify(salesOrderService, never()).cancelFromMall(any(), any());
            }

            @Test
            void orderAlreadyInTransitCannotBeCancelled() {
                when(queryService.requireOwnedOrder(customer, ORDER_ID)).thenReturn(order("PENDING", 1));
                when(queryService.activeFulfillmentStatus(ORDER_ID)).thenReturn("IN_TRANSIT");

                assertThatThrownBy(() -> service.cancel(customer, ORDER_ID, cancelForm("不需要了"), "key"))
                        .isInstanceOfSatisfying(ScmBusinessException.class,
                                exception -> assertThat(exception.getErrorCode())
                                        .isEqualTo(MallErrorCode.ORDER_ALREADY_FULFILLING));
                verify(salesOrderService, never()).cancelFromMall(any(), any());
            }

            @Test
            void replayOfAnAlreadyCancelledOrderStillReachesTheIdempotencyRecord() {
                // 首次取消成功后订单已是 CANCELLED。同一个防重复请求编号重试必须能走到正式订单服务的防重复处理记录，
                // 否则会被商城的前置状态检查挡成「当前状态不允许取消」，与「重复点击返回首次结果」不符。
                when(queryService.requireOwnedOrder(customer, ORDER_ID)).thenReturn(order("CANCELLED", 5));

                service.cancel(customer, ORDER_ID, cancelForm("下错单了"), "key");

                verify(salesOrderService).cancelFromMall(any(), eq("key"));
                // 已取消的单不再做前置判定，也就不该去读履约状态
                verify(queryService, never()).activeFulfillmentStatus(any());
            }

            @Test
            void reorderSetsTheHistoricalQuantityWithoutCopyingTheHistoricalPrice() {
                when(queryService.requireOwnedOrder(customer, ORDER_ID)).thenReturn(order("CONFIRMED", 1));
                when(itemDao.list(ORDER_ID)).thenReturn(List.of(item(11L, "2.5000")));
                when(catalogService.visibleProducts(eq(customer), any())).thenReturn(List.of(product(11L, "6.0000")));

                MallReorderVO result = service.reorder(customer, ORDER_ID);

                assertThat(result.getAddedCount()).isEqualTo(1);
                assertThat(result.getSkipped()).isEmpty();
                // 数量覆盖为历史下单量；单价不参与写入，结算时按当前客户价重新解析
                verify(cartService).setQuantity(customer, 11L, new BigDecimal("2.5000"));
            }

            @Test
            void reorderSkipsItemsThatAreNoLongerVisibleOrPriced() {
                when(queryService.requireOwnedOrder(customer, ORDER_ID)).thenReturn(order("CONFIRMED", 1));
                when(itemDao.list(ORDER_ID)).thenReturn(List.of(item(11L, "1.0000"), item(12L, "1.0000"), item(13L, "1.0000")));
                when(catalogService.visibleProducts(eq(customer), any()))
                        .thenReturn(List.of(product(11L, "6.0000"), product(12L, null)));

                MallReorderVO result = service.reorder(customer, ORDER_ID);

                assertThat(result.getAddedCount()).isEqualTo(1);
                assertThat(result.getSkipped()).extracting(MallReorderVO.Skipped::getSkuId).containsExactly(12L, 13L);
                assertThat(result.getSkipped()).extracting(MallReorderVO.Skipped::getReason)
                        .containsExactly("暂无客户价，请联系业务员询价", "商品已下架或对当前客户不可见");
                verify(cartService).setQuantity(customer, 11L, new BigDecimal("1.0000"));
                verify(cartService, never()).setQuantity(eq(customer), eq(12L), any());
                verify(cartService, never()).setQuantity(eq(customer), eq(13L), any());
            }

            private static RequestMallCustomer customer() {
                RequestMallCustomer result = new RequestMallCustomer();
                result.setAccountId(7L);
                result.setCustomerId(9L);
                return result;
            }

            private static SalesOrderEntity order(String status, int version) {
                SalesOrderEntity order = new SalesOrderEntity();
                order.setId(ORDER_ID);
                order.setStatus(status);
                order.setVersion(version);
                return order;
            }

            private static SalesOrderItemEntity item(Long skuId, String quantity) {
                SalesOrderItemEntity item = new SalesOrderItemEntity();
                item.setSkuId(skuId);
                item.setProductNameSnapshot("商品" + skuId);
                item.setOrderedQuantity(new BigDecimal(quantity));
                return item;
            }

            private static MallProductVO product(Long skuId, String unitPrice) {
                MallProductVO product = new MallProductVO();
                product.setSkuId(skuId);
                product.setUnitPrice(unitPrice == null ? null : new BigDecimal(unitPrice));
                // 称重商品按半斤递增：历史下单量 2.5000 在当前数量规则下仍可再次加入购物车。
                product.setStepQty(new BigDecimal("0.5000"));
                return product;
            }

            private static MallOrderCancelForm cancelForm(String reason) {
                MallOrderCancelForm form = new MallOrderCancelForm();
                form.setReason(reason);
                return form;
            }
        }

        @Nested

        class MallPaymentServiceTest {
            @Test
            void oldClientCannotPayEvenWithAnOrderAndIdempotencyKey() {
                MallPaymentService service = new MallPaymentService();
                assertThatThrownBy(() -> service.pay(new RequestMallCustomer(), 21L, "legacy-pay"))
                    .isInstanceOfSatisfying(ScmBusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(MallErrorCode.ORDER_PAYMENT_DISABLED));
            }
        }

        @Nested

        class MallStockAvailabilityServiceTest {
            private final MallStockDao dao = mock(MallStockDao.class);
            private final ProductBundleComponentQueryService bundleDao = mock(ProductBundleComponentQueryService.class);
            private final MallStockAvailabilityService service = new MallStockAvailabilityService(dao, bundleDao);

            @Test
            void availableSalesQuantityUsesOnlySelectedWarehouseAndFrozenUnitFactor() {
                MallStockFact balance = fact("kg", "30.0000", "10.0000");
                when(dao.bySkus(11L, List.of(99L))).thenReturn(List.of(balance));
                when(dao.bySkus(12L, List.of(99L))).thenReturn(List.of());
                MallProductVO product = product();
                service.enrich(11L, List.of(product));
                assertThat(product.getAvailableQty()).isEqualByComparingTo("3.0000");
                assertThat(product.getStockStatus()).isEqualTo("AVAILABLE");
                assertThat(service.requireConversion(11L, 99L, "箱").stockUnitsPerSaleUnit())
                        .isEqualByComparingTo("10.0000");

                MallProductVO elsewhere = product();
                service.enrich(12L, List.of(elsewhere));
                assertThat(elsewhere.getAvailableQty()).isEqualByComparingTo("0.0000");
                assertThat(elsewhere.getStockStatus()).isEqualTo("OUT_OF_STOCK");
            }

            @Test
            void differentStockUnitWithoutConfiguredFactorIsNotSellable() {
                when(dao.bySkus(11L, List.of(99L))).thenReturn(List.of(fact("kg", "30.0000", null)));
                MallProductVO product = product();
                service.enrich(11L, List.of(product));
                assertThat(product.getStockStatus()).isEqualTo("UNKNOWN");
            }

            private static MallStockFact fact(String unit, String quantity, String factor) {
                MallStockFact fact = new MallStockFact();
                fact.setSkuId(99L);
                fact.setSaleUnit("箱");
                fact.setStockUnit(unit);
                fact.setAvailableStockQuantity(new BigDecimal(quantity));
                if (factor != null) fact.setStockUnitsPerSaleUnit(new BigDecimal(factor));
                return fact;
            }

            private static MallProductVO product() {
                MallProductVO product = new MallProductVO();
                product.setSkuId(99L);
                product.setSaleUnit("箱");
                product.setMinOrderQty(BigDecimal.ONE);
                return product;
            }
        }

        @Nested

        class MallThemeAccessTest {
            @Test
            void onlyLightweightThemeReadIsAnonymous() throws NoSuchMethodException {
                assertThat(MallHomeController.class.getMethod("theme").getAnnotation(NoNeedLogin.class)).isNotNull();
                assertThat(MallHomeController.class.getMethod("published", Long.class)
                        .getAnnotation(NoNeedLogin.class)).isNull();
                assertThat(MallHomeController.class.getAnnotation(NoNeedLogin.class)).isNull();
                assertThat(MallThemeVO.class.getDeclaredFields()).extracting(java.lang.reflect.Field::getName)
                        .containsExactlyInAnyOrder("releaseId", "themeCode", "themeColor", "primary", "dark", "ink", "light",
                                "disabled", "headerStartColor", "headerEndColor");
            }

            @Test
            void publishedDecorationPagesAreAnonymousButAdminPagesAreNot() throws NoSuchMethodException {
                assertThat(MallDecorationController.class.getMethod("page", String.class)
                        .getAnnotation(NoNeedLogin.class)).isNotNull();
                assertThat(MallDecorationController.class.getAnnotation(NoNeedLogin.class)).isNull();
            }
        }

        @Nested

        class MallThemeColorsTest {
            @Test
            void acceptsOnlySixDigitHexAndNormalizesCase() {
                assertThat(MallThemeColors.isValid("#16A34A")).isTrue();
                assertThat(MallThemeColors.isValid("#16a34a")).isTrue();
                assertThat(MallThemeColors.isValid("#abc")).isFalse();
                assertThat(MallThemeColors.isValid("16A34A")).isFalse();
                assertThat(MallThemeColors.isValid(null)).isFalse();
                assertThat(MallThemeColors.normalize(" #16a34a ")).isEqualTo("#16A34A");
            }

            @Test
            void presetColorsAreStableAndUnknownCodesFallBackToFresh() {
                assertThat(MallThemeColors.baseColor("FRESH")).isEqualTo("#16A34A");
                assertThat(MallThemeColors.baseColor("WARM")).isEqualTo("#F97316");
                assertThat(MallThemeColors.baseColor("OCEAN")).isEqualTo("#0284C7");
                assertThat(MallThemeColors.baseColor("UNKNOWN")).isEqualTo("#16A34A");
                assertThat(MallThemeColors.baseColor(null)).isEqualTo("#16A34A");
            }

            @Test
            void derivedPaletteMatchesTheSharedFrontendFormula() {
                assertThat(MallThemeColors.dark("#0284C7")).isEqualTo("#026CA3");
                assertThat(MallThemeColors.ink("#0284C7")).isEqualTo("#015A87");
                assertThat(MallThemeColors.light("#0284C7")).isEqualTo("#E6F3F9");
                assertThat(MallThemeColors.disabled("#0284C7")).isEqualTo("#8DC8E6");
            }

            @Test
            void rejectsColorsWhereWhiteTextWouldDisappear() {
                assertThat(MallThemeColors.readableOnWhite("#16A34A")).isTrue();
                assertThat(MallThemeColors.readableOnWhite("#0284C7")).isTrue();
                // 亮黄配白字几乎看不清，保存时应直接拒绝，而不是等到客户端才发现
                assertThat(MallThemeColors.readableOnWhite("#FFE600")).isFalse();
                assertThat(MallThemeColors.whiteContrast("#FFFFFF")).isEqualTo(1.0);
            }
        }

        @Nested

        class MallWarehouseServiceTest {
            private final CustomerService customers = mock(CustomerService.class);
            private final WarehouseService warehouses = mock(WarehouseService.class);
            private final MallCustomerAddressDao addresses = mock(MallCustomerAddressDao.class);
            private final MallWarehouseDao rules = mock(MallWarehouseDao.class);
            private final MallWarehouseService service = new MallWarehouseService(customers, warehouses, addresses, rules);

            @Test
            void boundCustomerMustNotSwitchToADistrictWarehouse() {
                CustomerEntity customer = new CustomerEntity();
                customer.setMallDeliveryWarehouseId(10L);
                when(customers.require(7L)).thenReturn(customer);
                when(addresses.detailById(7L, 8L)).thenReturn(address());
                WarehouseEntity bound = new WarehouseEntity();
                bound.setId(10L);
                bound.setName("绑定仓");
                when(warehouses.require(10L)).thenReturn(bound);
                when(warehouses.enabled(10L)).thenReturn(true);
                assertThat(service.options(7L, 8L)).extracting(MallWarehouseOptionVO::getWarehouseId).containsExactly(10L);
                assertThat(service.require(7L, 8L, 10L)).isEqualTo(10L);
                assertThatThrownBy(() -> service.require(7L, 8L, 20L)).isInstanceOf(ScmBusinessException.class);
                verify(rules, never()).byDistrict(440305);
            }

            @Test
            void unboundCustomerCanChooseConfiguredDistrictWarehouse() {
                when(customers.require(7L)).thenReturn(new CustomerEntity());
                when(addresses.detailById(7L, 8L)).thenReturn(address());
                MallWarehouseOptionVO option = new MallWarehouseOptionVO();
                option.setWarehouseId(20L);
                option.setWarehouseName("区县仓");
                when(rules.byDistrict(440305)).thenReturn(List.of(option));
                assertThat(service.preferred(7L, 8L)).isEqualTo(20L);
            }

            private static MallAddressVO address() {
                MallAddressVO address = new MallAddressVO();
                address.setDistrictCode(440305);
                return address;
            }
        }

        @Nested

        class ScmSharedSettlementPolicyTest {
            @Test
            @DisplayName("未授权时不代扣集团资金")
            void deniedWithoutGrant() {
                assertThat(ScmSharedSettlementPolicy.sharedCheckoutAllowed(false)).isFalse();
            }

            @Test
            @DisplayName("集团主体授权后才允许代扣")
            void allowedWithGrant() {
                assertThat(ScmSharedSettlementPolicy.sharedCheckoutAllowed(true)).isTrue();
            }
        }
    }

    @Nested
    class MallW5PgIT extends ScmW5PgITBase {
        @Nested

        class MallCategoryNavigationPgIT {
            @Test
            void configuredIconSurvivesEveryLevelIncludingEmptyLeaves() {
                Long root = addCategory("图标根", null);
                Long child = addCategory("图标子类", root);
                Long leaf = addCategory("空图标末级", child);
                String key = "public/image/" + prefix + "-navigation.png";
                jdbc.update("INSERT INTO t_file (folder_type,file_name,file_size,file_key,file_type) VALUES (2,?,100,?,'png')", prefix, key);
                jdbc.update("UPDATE xsy_product_category SET icon_file_key=? WHERE id IN (?,?,?)", key, root, child, leaf);
                evictMybatisCache();
                MallCategoryVO visible = catalog.categories().stream().filter(item -> item.getCategoryId().equals(root)).findFirst().orElseThrow();
                assertThat(visible.getIconUrl()).contains(key);
                assertThat(visible.getChildren().getFirst().getIconFileKey()).isEqualTo(key);
                assertThat(visible.getChildren().getFirst().getChildren().getFirst().getIconUrl()).contains(key);
            }

            @Autowired
            private ProductCategoryService categories;
            @Autowired
            private MallCatalogService catalog;
            @Autowired
            private MallCatalogDao catalogDao;

            @Test
            void enabledEmptyRootAndDescendantsRemainVisible() {
                Long root = addCategory("空水产", null);
                Long child = addCategory("空鱼类", root);
                Long leaf = addCategory("空鱼规格", child);

                MallCategoryVO visible = catalog.categories().stream()
                    .filter(item -> item.getCategoryId().equals(root)).findFirst().orElseThrow();
                assertThat(visible.getChildren()).extracting(MallCategoryVO::getCategoryId).containsExactly(child);
                assertThat(visible.getChildren().getFirst().getChildren())
                    .extracting(MallCategoryVO::getCategoryId).containsExactly(leaf);
                var query = new MallProductQueryForm();
                query.setCategoryId(root);
                assertThat(catalogDao.products(new Page<MallProductVO>(1, 20), newCustomer(), query)).isEmpty();
            }

            @Test
            void disabledAndDeletedAncestorsDoNotPromoteChildrenToRoots() {
                Long disabled = addCategory("停用根", null);
                Long disabledChild = addCategory("停用根的子类", disabled);
                Long deleted = addCategory("删除根", null);
                Long deletedChild = addCategory("删除根的子类", deleted);
                // 模拟存量父级状态异常，读取必须整棵关闭，不把子类当成新的一级入口。
                jdbc.update("UPDATE xsy_product_category SET status='DISABLED' WHERE id=?", disabled);
                jdbc.update("UPDATE xsy_product_category SET deleted=TRUE WHERE id=?", deleted);
                evictMybatisCache();

                assertThat(catalogDao.categories()).extracting(MallCategoryVO::getCategoryId)
                    .doesNotContain(disabled, disabledChild, deleted, deletedChild);
            }

            @Test
            void showingEmptyNavigationDoesNotBroadenProductVisibility() {
                Long customerId = newCustomer();
                Long root = addCategory("可见性根", null);
                Long child = addCategory("可见性子类", root);
                Long leaf = addCategory("可见性叶子", child);
                Long visibleSku = addProduct(leaf, "可见导航商品", "ON_SHELF");
                Long offShelfSku = addProduct(leaf, "下架导航商品", "OFF_SHELF");
                var query = new MallProductQueryForm();
                query.setKeyword(prefix);

                assertThat(catalogDao.products(new Page<MallProductVO>(1, 20), customerId, query))
                    .extracting(MallProductVO::getSkuId).contains(visibleSku).doesNotContain(offShelfSku);
                jdbc.update("UPDATE xsy_customer SET visibility_policy='ALLOWLIST' WHERE id=?", customerId);
                evictMybatisCache();
                assertThat(catalogDao.categories()).isNotEmpty();
                assertThat(catalogDao.products(new Page<MallProductVO>(1, 20), customerId, query)).isEmpty();
            }

            private Long addProduct(Long categoryId, String suffix, String status) {
                // 使用独立的三级分类，不能依赖旧种子FRESH-FRUIT在当前库里的层级。
                var form = new ProductSpuAddForm();
                form.setName(prefix + suffix);
                form.setCategoryId(categoryId);
                form.setStatus(status);
                var sku = new ProductSkuForm();
                sku.setSpecName(suffix);
                sku.setSpecValues(new LinkedHashMap<>(Map.of("规格", suffix)));
                sku.setSaleUnit("kg");
                sku.setProductType("NON_STANDARD");
                sku.setMarketPrice(new BigDecimal("1.2000"));
                sku.setStatus("ON_SHELF");
                sku.setDefaultFlag(true);
                form.setSkuList(new ArrayList<>(List.of(sku)));
                Long spuId = productSpuService.add(form);
                return jdbc.queryForObject("SELECT id FROM xsy_product_sku WHERE spu_id=? AND deleted=FALSE", Long.class, spuId);
            }

            private Long addCategory(String suffix, Long parentId) {
                var form = new ProductCategoryAddForm();
                form.setName(prefix + suffix);
                form.setParentId(parentId);
                form.setSortOrder(0);
                form.setStatus("ENABLED");
                return categories.add(form);
            }
        }

        @Nested
        @DisplayName("账期授信占用的正式净额规则（PAY-02，PG IT）")
        class MallCreditExposurePgIT {
            private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

            /** 下单 3.0000、实重 2.0000、客户价 1.2000 → 优惠前毛额 2.4000。 */
            private static final String ORDERED_QUANTITY = "3.0000";
            private static final String ACTUAL_QUANTITY = "2.0000";

            /** 已确认优惠 0.5000 按下单金额 3.6000 等比分摊到实重金额 2.4000 上 = 0.3333，净额 2.0667。 */
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
            @DisplayName("未开应收只预占账期与历史空方式订单；余额和 COD 不占授信")
            void onlyCreditAndLegacyOrdersReserveCreditBeforeReceivable() {
                Long customerId = newCustomer();
                Long skuId = newOnShelfSku("expo-methods");
                Long creditOrderId = confirmedSalesOrder(customerId, skuId, "2.0000", "2.0000");
                Long legacyOrderId = confirmedSalesOrder(customerId, skuId, "2.0000", "2.0000");
                Long walletOrderId = confirmedSalesOrder(customerId, skuId, "2.0000", "2.0000");
                Long codOrderId = confirmedSalesOrder(customerId, skuId, "2.0000", "2.0000");
                jdbc.update("UPDATE xsy_sales_order SET settlement_method = 'CREDIT' WHERE id = ?", creditOrderId);
                jdbc.update("UPDATE xsy_sales_order SET settlement_method = 'WALLET' WHERE id = ?", walletOrderId);
                jdbc.update("UPDATE xsy_sales_order SET settlement_method = 'COD' WHERE id = ?", codOrderId);
                evictMybatisCache();

                FinanceCreditExposureDto exposure = exposure(customerId);
                assertThat(exposure.getConfirmedOrderAmount())
                        .as("仅 CREDIT 与历史空方式各占 2.4000；余额和 COD 未开应收不预占授信")
                        .isEqualByComparingTo("4.8000");
                assertThat(jdbc.queryForObject("SELECT settlement_method FROM xsy_sales_order WHERE id = ?",
                        String.class, legacyOrderId)).isNull();

                normalReceivable(walletOrderId, "2.4000");
                evictMybatisCache();
                FinanceCreditExposureDto afterReceivable = exposure(customerId);
                assertThat(afterReceivable.getConfirmedOrderAmount()).isEqualByComparingTo("4.8000");
                assertThat(afterReceivable.getOpenReceivableAmount())
                        .as("实际开出的正常应收无论原结算方式如何都按未结额计入")
                        .isEqualByComparingTo("2.4000");
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

                // 本单金额取小额，看的是「已占用之后还剩多少」；占用本身的规则在上一个用例里已钉住
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
             * 直接落一条已确认优惠记录（含行级等比分摊），模拟「确认命令已经保存过优惠」。
             *
             * <p>行基础金额取 {@code ordered_line_amount}（下单量 × 锁定价），与正式保存路径同规则。
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

        @Nested

        class MallFundsQueryPgIT {
            private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

            @Autowired
            private MallFundsQueryService funds;

            @Test
            void groupMembersSeeOnlyTheirOwnMovementsAndReceivables() {
                Long group = groupCustomer(null);
                Long first = groupCustomer(group);
                Long sibling = groupCustomer(group);
                Long skuId = newOnShelfSku("FUNDS-G");
                Long firstReceivable = normalReceivable(confirmedSalesOrder(first, skuId, "1.0000", "1.0000"),
                        "30.0000", LocalDate.now(BUSINESS_ZONE).plusDays(2));
                Long siblingReceivable = normalReceivable(confirmedSalesOrder(sibling, skuId, "1.0000", "1.0000"),
                        "80.0000", LocalDate.now(BUSINESS_ZONE).plusDays(2));
                Long account = wallet(group);
                movement(account, group, first, "CREDIT", "50.0000");
                movement(account, group, first, "DEBIT", "10.0000");
                movement(account, group, sibling, "CREDIT", "80.0000");

                var firstIdentity = identity(first);
                assertThat(funds.walletSummary(firstIdentity).getBalanceVisible()).isFalse();
                assertThat(funds.walletSummary(firstIdentity).getAvailableBalance()).isNull();
                assertThat(funds.walletSummary(identity(group)).getBalanceVisible())
                        .as("集团主体自己结算时能看到自己的钱包").isTrue();
                var ownMovements = funds.walletMovements(firstIdentity, movementPage(1, 1));
                assertThat(ownMovements.getTotal()).isEqualTo(2L);
                assertThat(ownMovements.getPages()).isEqualTo(2L);
                assertThat(ownMovements.getList()).hasSize(1);
                MallWalletMovementQueryForm creditOnly = movementPage(1, 10);
                creditOnly.setType("CORRECTION");
                creditOnly.setDirection("CREDIT");
                var credits = funds.walletMovements(firstIdentity, creditOnly);
                assertThat(credits.getTotal()).isEqualTo(1L);
                assertThat(credits.getList().getFirst().getAmount()).isEqualByComparingTo("50.0000");

                var ownBills = funds.receivables(firstIdentity, receivablePage(1, 10, null));
                assertThat(ownBills.getTotal()).isEqualTo(1L);
                assertThat(ownBills.getList()).extracting("receivableId").containsExactly(firstReceivable);
                assertThat(funds.creditSummary(firstIdentity).getOpenReceivableAmount()).isEqualByComparingTo("30.0000");
                assertThat(funds.creditSummary(firstIdentity).getCreditLimit()).isNull();
                assertThatThrownBy(() -> funds.receivable(firstIdentity, siblingReceivable))
                        .isInstanceOfSatisfying(ScmBusinessException.class,
                                error -> assertThat(error.getErrorCode()).isEqualTo(MallErrorCode.FUNDS_RECEIVABLE_NOT_FOUND));
            }

            @Test
            void redAndWriteOffFactsDriveOpenAmountOverdueAndFilteredPages() {
                Long customerId = newCustomer();
                jdbc.update("UPDATE xsy_customer SET credit_period_type = 'BY_TIME', credit_period_value = 1,"
                        + " credit_period_unit = 'DAY', updated_at = now() WHERE id = ?", customerId);
                evictMybatisCache();
                Long skuId = newOnShelfSku("FUNDS-N");
                LocalDate overdueDate = LocalDate.now(BUSINESS_ZONE).minusDays(1);
                Long adjustedOrder = confirmedSalesOrder(customerId, skuId, "1.0000", "1.0000");
                Long currentOrder = confirmedSalesOrder(customerId, skuId, "1.0000", "1.0000");
                Long adjusted = normalReceivable(adjustedOrder, "100.0000", overdueDate);
                normalReceivable(currentOrder, "25.0000", LocalDate.now(BUSINESS_ZONE).plusDays(1));
                redReceivable(adjusted, "20.0000");
                writeOff(adjusted, "30.0000");
                assertThat(jdbc.queryForObject("SELECT due_date FROM xsy_finance_receivable WHERE id = ?",
                        LocalDate.class, adjusted)).isEqualTo(overdueDate);

                RequestMallCustomer identity = identity(customerId);
                assertThat(funds.walletSummary(identity).getBalanceVisible()).isTrue();
                assertThat(funds.walletSummary(identity).getAvailableBalance()).isEqualByComparingTo("0.0000");
                var detail = funds.receivable(identity, adjusted);
                assertThat(detail.getAmount()).isEqualByComparingTo("100.0000");
                assertThat(detail.getRedAmount()).isEqualByComparingTo("20.0000");
                assertThat(detail.getWrittenOffAmount()).isEqualByComparingTo("30.0000");
                assertThat(detail.getOpenAmount()).isEqualByComparingTo("50.0000");
                assertThat(detail.getSettleState()).isEqualTo("PARTIAL");
                assertThat(detail.getDueDate()).isEqualTo(overdueDate);
                assertThat(detail.getOverdue()).isTrue();
                assertThat(funds.creditSummary(identity).getOpenReceivableAmount()).isEqualByComparingTo("75.0000");
                assertThat(funds.creditSummary(identity).getEarliestOverdueDate()).isEqualTo(overdueDate);

                var firstPage = funds.receivables(identity, receivablePage(1, 1, null));
                assertThat(firstPage.getTotal()).isEqualTo(2L);
                assertThat(firstPage.getPages()).isEqualTo(2L);
                assertThat(firstPage.getList()).hasSize(1);
                var secondPage = funds.receivables(identity, receivablePage(2, 1, null));
                assertThat(secondPage.getList()).hasSize(1);
                assertThat(secondPage.getList().getFirst().getReceivableId())
                        .isNotEqualTo(firstPage.getList().getFirst().getReceivableId());
                assertThat(funds.receivables(identity, receivablePage(1, 10, "PARTIAL")).getList())
                        .extracting("receivableId").containsExactly(adjusted);
                assertThat(funds.receivables(identity, receivablePage(1, 10, "SETTLED")).getList()).isEmpty();
            }

            private Long groupCustomer(Long parentId) {
                CustomerAddForm form = new CustomerAddForm();
                form.setName(prefix + "-GROUP-" + UUID.randomUUID().toString().substring(0, 5));
                form.setSettleMode("GROUP");
                form.setSellerId(1L);
                form.setParentCustomerId(parentId);
                form.setSettlementCustomerId(parentId);
                Long id = customerService.add(form);
                CustomerStatusForm status = new CustomerStatusForm();
                status.setCustomerId(id);
                status.setVersion(customerService.require(id).getVersion());
                status.setStatus("COOPERATING");
                customerService.updateStatus(status);
                return id;
            }

            private Long normalReceivable(Long orderId, String amount, LocalDate dueDate) {
                OffsetDateTime eventAt = dueDate.minusDays(1).atStartOfDay(BUSINESS_ZONE).toOffsetDateTime();
                return jdbc.queryForObject("""
                        INSERT INTO xsy_finance_receivable (receivable_no, source_type, source_id, order_id,
                            customer_id, customer_name_snapshot, settlement_customer_id,
                            settlement_customer_name_snapshot, entry_type, amount, event_at, due_date)
                        SELECT ?, 'SALES_ORDER', id, id, customer_id, customer_name_snapshot,
                            settlement_customer_id, settlement_customer_name_snapshot, 'NORMAL', ?, ?, ?
                        FROM xsy_sales_order WHERE id = ? RETURNING id
                        """, Long.class, "AR-FUNDS-" + UUID.randomUUID(), new BigDecimal(amount), eventAt, dueDate, orderId);
            }

            private void redReceivable(Long originalId, String amount) {
                jdbc.update("""
                        INSERT INTO xsy_finance_receivable (receivable_no, source_type, source_id, order_id,
                            customer_id, customer_name_snapshot, settlement_customer_id,
                            settlement_customer_name_snapshot, entry_type, original_receivable_id, amount,
                            event_at, due_date, reason)
                        SELECT ?, 'ORDER_RETURN', ?, order_id,
                            customer_id, customer_name_snapshot, settlement_customer_id,
                            settlement_customer_name_snapshot, 'RED', id, ?, now(), due_date, '测试红字'
                        FROM xsy_finance_receivable WHERE id = ?
                        """, "AR-FUNDS-RED-" + UUID.randomUUID(),
                        UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE, new BigDecimal(amount), originalId);
            }

            private void writeOff(Long receivableId, String amount) {
                jdbc.update("""
                        INSERT INTO xsy_finance_write_off (write_off_no, source_type, source_id, target_type,
                            target_id, amount, entry_type, written_off_at, operator)
                        VALUES (?, 'RECEIPT', ?, 'RECEIVABLE', ?, ?, 'NORMAL', now(), 'PG-IT')
                        """, "WO-FUNDS-" + UUID.randomUUID(), receivableId, receivableId, new BigDecimal(amount));
            }

            private Long wallet(Long settlementCustomerId) {
                return jdbc.queryForObject("""
                        INSERT INTO xsy_customer_balance_account (settlement_customer_id, settlement_customer_name_snapshot)
                        SELECT id, name FROM xsy_customer WHERE id = ? RETURNING id
                        """, Long.class, settlementCustomerId);
            }

            private void movement(Long accountId, Long settlementCustomerId, Long customerId, String direction,
                    String amount) {
                jdbc.update("""
                        INSERT INTO xsy_customer_balance_movement (movement_no, account_id, settlement_customer_id,
                            customer_id, type, direction, amount, reason, occurred_at)
                        VALUES (?, ?, ?, ?, 'CORRECTION', ?, ?, '测试调整', now())
                        """, "BM-FUNDS-" + UUID.randomUUID(), accountId, settlementCustomerId, customerId, direction,
                        new BigDecimal(amount));
            }

            private static RequestMallCustomer identity(Long customerId) {
                RequestMallCustomer customer = new RequestMallCustomer();
                customer.setCustomerId(customerId);
                return customer;
            }

            private static MallWalletMovementQueryForm movementPage(long pageNum, long pageSize) {
                MallWalletMovementQueryForm form = new MallWalletMovementQueryForm();
                form.setPageNum(pageNum);
                form.setPageSize(pageSize);
                return form;
            }

            private static MallReceivableQueryForm receivablePage(long pageNum, long pageSize, String state) {
                MallReceivableQueryForm form = new MallReceivableQueryForm();
                form.setPageNum(pageNum);
                form.setPageSize(pageSize);
                form.setSettleState(state);
                return form;
            }
        }

        @Nested

        class MallFundsUiFixtureManual {
            @Autowired
            private MallAccountService accountService;

            @Test
            @EnabledIfSystemProperty(named = "scm.mall.funds.ui-fixture", matches = "true")
            @Transactional(propagation = Propagation.NOT_SUPPORTED)
            void createDisposableAccounts() throws IOException {
                String database = jdbc.queryForObject("SELECT current_database()", String.class);
                assertThat(database).startsWith("xsy_verify_funds_");

                Long independent = newCustomer();
                Long group = groupCustomer(null);
                Long memberA = groupCustomer(group);
                Long memberB = groupCustomer(group);
                setSevenDayCredit(independent);
                setSevenDayCredit(group);

                Long skuId = newOnShelfSku("FUNDS-UI");
                Long independentOrder = mallOrder(independent, skuId, "1.0000");
                Long memberAOrder = mallOrder(memberA, skuId, "2.0000");
                Long memberBOrder = mallOrder(memberB, skuId, "3.0000");
                Long independentBill = receivable(independentOrder);
                Long memberABill = receivable(memberAOrder);
                Long memberBBill = receivable(memberBOrder);

                Long ownWallet = wallet(independent);
                movement(ownWallet, independent, independent, "50.0000");
                Long sharedWallet = wallet(group);
                movement(sharedWallet, group, memberA, "20.0000");
                movement(sharedWallet, group, memberB, "30.0000");

                Map<String, Object> accounts = new LinkedHashMap<>();
                accounts.put("independent", account(independent, independentOrder, independentBill, "50.0000"));
                accounts.put("group", account(group, null, null, null));
                accounts.put("memberA", account(memberA, memberAOrder, memberABill, "20.0000"));
                accounts.put("memberB", account(memberB, memberBOrder, memberBBill, "30.0000"));
                Path output = Path.of("../.runtime/funds-ui-accounts.json").toAbsolutePath().normalize();
                Files.createDirectories(output.getParent());
                Files.writeString(output, objectMapper.writeValueAsString(accounts));
            }

            private Long groupCustomer(Long parentId) {
                CustomerAddForm form = new CustomerAddForm();
                form.setName(prefix + "-" + UUID.randomUUID().toString().substring(0, 5));
                form.setSettleMode("GROUP");
                form.setSellerId(1L);
                form.setParentCustomerId(parentId);
                form.setSettlementCustomerId(parentId);
                Long id = customerService.add(form);
                CustomerStatusForm status = new CustomerStatusForm();
                status.setCustomerId(id);
                status.setVersion(customerService.require(id).getVersion());
                status.setStatus("COOPERATING");
                customerService.updateStatus(status);
                return id;
            }

            private void setSevenDayCredit(Long customerId) {
                jdbc.update("UPDATE xsy_customer SET credit_period_type = 'BY_TIME', credit_period_value = 7,"
                        + " credit_period_unit = 'DAY', updated_at = now() WHERE id = ?", customerId);
            }

            private Long mallOrder(Long customerId, Long skuId, String quantity) {
                Long orderId = confirmedSalesOrder(customerId, skuId, quantity, quantity);
                // 订单主体和明细仍由正式命令建立；只为商城订单详情的客户归属查询标记来源。
                assertThat(jdbc.update("UPDATE xsy_sales_order SET order_source = 'MALL' WHERE id = ?", orderId)).isEqualTo(1);
                return orderId;
            }

            private Long receivable(Long orderId) {
                return jdbc.queryForObject("""
                        INSERT INTO xsy_finance_receivable (receivable_no, source_type, source_id, order_id,
                            customer_id, customer_name_snapshot, settlement_customer_id,
                            settlement_customer_name_snapshot, entry_type, amount, event_at)
                        SELECT ?, 'SALES_ORDER', id, id, customer_id, customer_name_snapshot,
                            settlement_customer_id, settlement_customer_name_snapshot, 'NORMAL',
                            settlement_total_amount, now()
                        FROM xsy_sales_order WHERE id = ? RETURNING id
                        """, Long.class, "AR-UI-" + UUID.randomUUID(), orderId);
            }

            private Long wallet(Long settlementCustomerId) {
                return jdbc.queryForObject("""
                        INSERT INTO xsy_customer_balance_account (settlement_customer_id, settlement_customer_name_snapshot)
                        SELECT id, name FROM xsy_customer WHERE id = ? RETURNING id
                        """, Long.class, settlementCustomerId);
            }

            private void movement(Long accountId, Long settlementCustomerId, Long customerId, String amount) {
                jdbc.update("""
                        INSERT INTO xsy_customer_balance_movement (movement_no, account_id, settlement_customer_id,
                            customer_id, type, direction, amount, reason, occurred_at)
                        VALUES (?, ?, ?, ?, 'CORRECTION', 'CREDIT', ?, '隔离页面联调夹具', now())
                        """, "BM-UI-" + UUID.randomUUID(), accountId, settlementCustomerId, customerId,
                        new BigDecimal(amount));
            }

            private Map<String, Object> account(Long customerId, Long orderId, Long receivableId, String ownMovementAmount) {
                String phone = "139" + String.format("%08d", Integer.toUnsignedLong(UUID.randomUUID().hashCode())
                        % 100_000_000);
                String temporaryPassword = "FundsUi@" + UUID.randomUUID().toString().substring(0, 8);
                String newPassword = "FundsUi@" + UUID.randomUUID().toString().substring(0, 8);
                MallAccountCreateForm form = new MallAccountCreateForm();
                form.setCustomerId(customerId);
                form.setLoginPhone(phone);
                form.setTemporaryPassword(temporaryPassword);
                accountService.create(form);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("customerId", customerId);
                result.put("phone", phone);
                result.put("temporaryPassword", temporaryPassword);
                result.put("newPassword", newPassword);
                result.put("orderId", orderId);
                result.put("receivableId", receivableId);
                result.put("receivableAmount", orderId == null ? null
                        : jdbc.queryForObject("SELECT settlement_total_amount FROM xsy_sales_order WHERE id = ?",
                                BigDecimal.class, orderId).toPlainString());
                result.put("ownMovementAmount", ownMovementAmount);
                return result;
            }
        }

        @Nested

        class MallHomeLayoutPgIT {
            @Autowired
            private MallHomeLayoutService layouts;
            @Autowired
            private MallHomeProjectionService projection;

            @Test
            void headerColorsArePublishedAndRestoredWithTheirRelease() {
                var initial = layouts.draft();
                var firstForm = new MallHomeDraftSaveForm();
                firstForm.setVersion(initial.version());
                firstForm.setThemeCode("FRESH");
                firstForm.setHeaderStartColor("#e4f1ff");
                firstForm.setHeaderEndColor("#ffffff");
                firstForm.setBlocks(List.of());
                var first = layouts.save(firstForm);
                assertThat(first.headerStartColor()).isEqualTo("#E4F1FF");
                assertThat(first.headerEndColor()).isEqualTo("#FFFFFF");
                Long firstRelease = layouts.publish(publish(first.version(), first.pointerVersion()));
                assertThat(projection.publishedTheme().getHeaderStartColor()).isEqualTo("#E4F1FF");
                firstForm.setVersion(first.version());
                firstForm.setHeaderStartColor("#FFF1E4");
                var second = layouts.save(firstForm);
                // 保存草稿不能提前改变线上顶部配色。
                assertThat(projection.publishedTheme().getHeaderStartColor()).isEqualTo("#E4F1FF");
                layouts.publish(publish(second.version(), second.pointerVersion()));
                assertThat(projection.publishedTheme().getHeaderStartColor()).isEqualTo("#FFF1E4");
                var rollback = new MallHomeRollbackForm();
                rollback.setTargetReleaseId(firstRelease);
                rollback.setPointerVersion(second.pointerVersion() + 1);
                rollback.setReason("恢复顶部配色");
                layouts.rollback(rollback);
                assertThat(projection.publishedTheme().getHeaderStartColor()).isEqualTo("#E4F1FF");
                assertThat(projection.publishedTheme().getHeaderEndColor()).isEqualTo("#FFFFFF");
                assertThat(jdbc.queryForObject("SELECT header_start_color FROM xsy_mall_home_release WHERE id=?",
                    String.class, firstRelease)).isEqualTo("#E4F1FF");
            }

            @Test
            void headerColorsDefaultToGreenWhiteAndInvalidColorsCannotBeSaved() {
                var initial = layouts.draft();
                var form = new MallHomeDraftSaveForm();
                form.setVersion(initial.version());
                form.setThemeCode("FRESH");
                form.setBlocks(List.of());
                var saved = layouts.save(form);
                assertThat(saved.headerStartColor()).isEqualTo("#E8FAEC");
                assertThat(saved.headerEndColor()).isEqualTo("#FFFFFF");
                form.setVersion(saved.version());
                form.setHeaderStartColor("url(bad)");
                assertThatThrownBy(() -> layouts.save(form)).isInstanceOfSatisfying(ScmBusinessException.class,
                    error -> assertThat(error.getErrorCode().getCode()).isEqualTo(40000));
                assertThat(layouts.draft().version()).isEqualTo(saved.version());
                form.setHeaderStartColor("#E8FAEC");
                form.setHeaderEndColor("#GGGGGG");
                assertThatThrownBy(() -> layouts.save(form)).isInstanceOf(ScmBusinessException.class);
            }

            @Test
            void themeCanBePublishedWithoutHomeBlocks() {
                var initial = layouts.draft();
                var form = new MallHomeDraftSaveForm();
                form.setVersion(initial.version());
                form.setThemeCode("OCEAN");
                form.setThemeColor("#126B85");
                form.setBlocks(List.of());

                var saved = layouts.save(form);
                Long releaseId = layouts.publish(publish(saved.version(), saved.pointerVersion()));

                assertThat(projection.publishedTheme().getReleaseId()).isEqualTo(releaseId);
                assertThat(projection.publishedTheme().getThemeColor()).isEqualTo("#126B85");
                assertThat(layouts.draft().blocks()).isEmpty();
                assertThat(jdbc.queryForObject("SELECT content::text FROM xsy_mall_home_release WHERE id=?",
                    String.class, releaseId)).isEqualTo("[]");
                assertThatThrownBy(() -> layouts.publish(publish(saved.version(), saved.pointerVersion())))
                    .isInstanceOf(ScmBusinessException.class);
            }

            @Test
            void draftPublishRollbackAndCustomerProjectionUseImmutableReleaseFacts() {
                Long visibleCustomer = customerWithWarehouse();
                Long hiddenCustomer = customerWithWarehouse();
                var initial = layouts.draft();
                var first = layouts.save(save(initial.version(), announcement("one", "第一版", visibleCustomer)));
                assertThat(first.version()).isEqualTo(initial.version() + 1);
                assertThatThrownBy(() -> layouts.save(save(initial.version(), announcement("stale", "过期草稿", visibleCustomer))))
                        .isInstanceOf(ScmBusinessException.class);
                assertThat(projection.published(customer(visibleCustomer), seedWarehouseId()).releaseId())
                        .isEqualTo(initial.currentReleaseId());

                Long firstRelease = layouts.publish(publish(first.version(), first.pointerVersion()));
                assertThat(projection.published(customer(visibleCustomer), seedWarehouseId()).blocks())
                        .extracting(block -> block.getText()).containsExactly("第一版");
                assertThat(projection.published(customer(hiddenCustomer), seedWarehouseId()).blocks()).isEmpty();

                var secondDraft = layouts.save(save(first.version(), announcement("two", "第二版", visibleCustomer)));
                Long secondRelease = layouts.publish(publish(secondDraft.version(), secondDraft.pointerVersion()));
                assertThat(secondRelease).isNotEqualTo(firstRelease);
                assertThatThrownBy(() -> layouts.publish(publish(secondDraft.version(), secondDraft.pointerVersion())))
                        .isInstanceOf(ScmBusinessException.class);
                assertThat(jdbc.queryForObject("SELECT content::text FROM xsy_mall_home_release WHERE id=?",
                        String.class, firstRelease)).contains("第一版");

                var rollback = new MallHomeRollbackForm();
                rollback.setTargetReleaseId(firstRelease);
                rollback.setPointerVersion(secondDraft.pointerVersion() + 1);
                rollback.setReason("恢复第一版");
                Long restoredRelease = layouts.rollback(rollback);
                assertThat(restoredRelease).isNotIn(firstRelease, secondRelease);
                assertThat(jdbc.queryForMap("SELECT publish_kind, source_release_id, reason FROM xsy_mall_home_release WHERE id=?",
                        restoredRelease)).containsEntry("publish_kind", "ROLLBACK")
                                .containsEntry("source_release_id", firstRelease)
                                .containsEntry("reason", "恢复第一版");
                assertThat(projection.published(customer(visibleCustomer), seedWarehouseId()).releaseId())
                        .isEqualTo(restoredRelease);
                assertThat(projection.published(customer(visibleCustomer), seedWarehouseId()).blocks())
                        .extracting(block -> block.getText()).containsExactly("第一版");
                assertThat(jdbc.queryForObject("SELECT content::text FROM xsy_mall_home_release WHERE id=?",
                        String.class, secondRelease)).contains("第二版");
                assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_mall_home_release WHERE id=?",
                        Integer.class, firstRelease)).isEqualTo(1);
            }

            @Test
            void nonGlobalCustomerScopeCannotReadOrChangeGlobalLayout() {
                var admin = XsyRequestUtil.getRequestUser();
                RequestEmployee restricted = new RequestEmployee();
                restricted.setEmployeeId(900000001L);
                restricted.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
                restricted.setAdministratorFlag(false);
                XsyRequestUtil.setRequestUser(restricted);
                try {
                    assertThatThrownBy(layouts::draft).isInstanceOf(ScmDataScopeException.class);
                    assertThatThrownBy(layouts::history).isInstanceOf(ScmDataScopeException.class);
                    assertThatThrownBy(() -> projection.draftPreview(1L, seedWarehouseId()))
                            .isInstanceOf(ScmDataScopeException.class);
                    assertThatThrownBy(() -> layouts.save(save(0, announcement("x", "无权", null))))
                            .isInstanceOf(ScmDataScopeException.class);
                    assertThatThrownBy(() -> layouts.publish(publish(0, 0)))
                            .isInstanceOf(ScmDataScopeException.class);
                    var rollback = new MallHomeRollbackForm();
                    rollback.setTargetReleaseId(1L);
                    rollback.setPointerVersion(0);
                    rollback.setReason("无权");
                    assertThatThrownBy(() -> layouts.rollback(rollback)).isInstanceOf(ScmDataScopeException.class);
                } finally {
                    XsyRequestUtil.setRequestUser(admin);
                }
            }

            @Test
            void adminEndpointsRequireTheMatchingFunctionPermissions() throws NoSuchMethodException {
                assertThat(MallHomeAdminController.class.getMethod("draft").getAnnotation(SaCheckPermission.class).value())
                        .containsExactly(MallPermission.HOME_QUERY);
                assertThat(MallHomeAdminController.class.getMethod("history").getAnnotation(SaCheckPermission.class).value())
                        .containsExactly(MallPermission.HOME_QUERY);
                assertThat(MallHomeAdminController.class.getMethod("preview",
                        com.xsy.scm.mall.home.domain.form.MallHomePreviewForm.class)
                        .getAnnotation(SaCheckPermission.class).value()).containsExactly(MallPermission.HOME_QUERY);
                assertThat(MallHomeAdminController.class.getMethod("save", MallHomeDraftSaveForm.class)
                        .getAnnotation(SaCheckPermission.class).value()).containsExactly(MallPermission.HOME_EDIT);
                assertThat(MallHomeAdminController.class.getMethod("publish", MallHomePublishForm.class)
                        .getAnnotation(SaCheckPermission.class).value()).containsExactly(MallPermission.HOME_PUBLISH);
                assertThat(MallHomeAdminController.class.getMethod("rollback", MallHomeRollbackForm.class)
                        .getAnnotation(SaCheckPermission.class).value()).containsExactly(MallPermission.HOME_PUBLISH);
            }

            @Test
            void globalThemeFollowsPublishedPointerAndRollbackWithoutCustomerOrWarehouse() {
                var initial = layouts.draft();
                var before = projection.publishedTheme();
                var warm = save(initial.version(), announcement("warm", "暖色主题", null));
                warm.setThemeCode("WARM");
                var warmDraft = layouts.save(warm);
                assertThat(projection.publishedTheme()).isEqualTo(before);
                Long warmRelease = layouts.publish(publish(warmDraft.version(), warmDraft.pointerVersion()));
                assertThat(projection.publishedTheme().getThemeCode()).isEqualTo("WARM");
                var ocean = save(warmDraft.version(), announcement("ocean", "蓝色主题", null));
                ocean.setThemeCode("OCEAN");
                var oceanDraft = layouts.save(ocean);
                Long oceanRelease = layouts.publish(publish(oceanDraft.version(), oceanDraft.pointerVersion()));
                assertThat(projection.publishedTheme().getThemeCode()).isEqualTo("OCEAN");
                var rollback = new MallHomeRollbackForm();
                rollback.setTargetReleaseId(warmRelease);
                rollback.setPointerVersion(layouts.draft().pointerVersion());
                rollback.setReason("恢复暖色主题");
                Long rollbackRelease = layouts.rollback(rollback);
                var operator = XsyRequestUtil.getRequestUser();
                XsyRequestUtil.remove();
                try {
                    assertThat(projection.publishedTheme().getThemeCode()).isEqualTo("WARM");
                    assertThat(projection.publishedTheme().getReleaseId()).isEqualTo(rollbackRelease);
                } finally {
                    XsyRequestUtil.setRequestUser(operator);
                }
                assertThat(jdbc.queryForObject("SELECT theme_code FROM xsy_mall_home_release WHERE id=?",
                        String.class, oceanRelease)).isEqualTo("OCEAN");
            }

            private static MallHomeDraftSaveForm save(int version, MallHomeBlockForm block) {
                var form = new MallHomeDraftSaveForm();
                form.setVersion(version);
                form.setThemeCode("OCEAN");
                form.setBlocks(List.of(block));
                return form;
            }

            private static MallHomePublishForm publish(int draftVersion, int pointerVersion) {
                var form = new MallHomePublishForm();
                form.setDraftVersion(draftVersion);
                form.setPointerVersion(pointerVersion);
                return form;
            }

            private static MallHomeBlockForm announcement(String key, String text, Long customerId) {
                var block = new MallHomeBlockForm();
                block.setKey(key);
                block.setSchemaVersion(1);
                block.setType("ANNOUNCEMENT");
                block.setSortOrder(0);
                block.setEnabled(true);
                block.setText(text);
                block.setLinkType("NONE");
                block.setCustomerIds(customerId == null ? List.of() : List.of(customerId));
                return block;
            }

            /**
             * 商城首页查询结果要按「客户可选的配送仓」校验仓库；裸 newCustomer() 没有绑定仓也没有地址，
             * 会让查询结果直接报 WAREHOUSE_UNAVAILABLE。这里把种子仓绑到客户上，不依赖环境里的地址数据。
             */
            private Long customerWithWarehouse() {
                Long customerId = newCustomer();
                jdbc.update("UPDATE xsy_customer SET mall_delivery_warehouse_id = ? WHERE id = ?", seedWarehouseId(),
                        customerId);
                evictMybatisCache();
                return customerId;
            }

            private static RequestMallCustomer customer(Long id) {
                var customer = new RequestMallCustomer();
                customer.setCustomerId(id);
                return customer;
            }
        }

        @Nested
        @DisplayName("商城订单资金展示：已付按实收（PG IT）")
        class MallOrderPaymentDisplayPgIT {
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
            @DisplayName("少收后继续分笔补收，客户详情随实际到账变为已付清")
            void partialCollectionLeavesTheRemainderAsDue() {
                CodFixture fixture = mallCodOrder("partial");

                collect(fixture, "1.0000", "partial");

                MallOrderVO view = customerView(fixture);
                assertThat(view.getPaidAmount()).isEqualByComparingTo("1.0000");
                assertThat(due(view)).isEqualByComparingTo("1.4000");

                collect(fixture, "1.4000", "partial-supplement");
                MallOrderVO settled = customerView(fixture);
                assertThat(settled.getPaidAmount()).isEqualByComparingTo(PAYABLE);
                assertThat(due(settled)).isEqualByComparingTo("0.0000");
            }

            @Test
            @DisplayName("历史人工补收计入已付，反向核销后从该订单已付中扣除")
            void manualSupplementTracksEffectiveWriteOffs() {
                CodFixture fixture = mallCodOrder("manual");
                Long receiptId = jdbc.queryForObject("""
                        INSERT INTO xsy_finance_receipt (receipt_no, customer_id, customer_name_snapshot,
                            settlement_customer_id, settlement_customer_name_snapshot, amount, method,
                            received_at, entry_type)
                        SELECT ?, customer_id, customer_name_snapshot, settlement_customer_id,
                            settlement_customer_name_snapshot, 1.0000, 'CASH', now(), 'NORMAL'
                        FROM xsy_finance_receivable WHERE id = ? RETURNING id
                        """, Long.class, "RC-MANUAL-" + UUID.randomUUID(), fixture.receivableId());
                Long writeOffId = jdbc.queryForObject("""
                        INSERT INTO xsy_finance_write_off (write_off_no, source_type, source_id, target_type,
                            target_id, amount, entry_type, written_off_at, operator)
                        VALUES (?, 'RECEIPT', ?, 'RECEIVABLE', ?, 1.0000, 'NORMAL', now(), ?) RETURNING id
                        """, Long.class, "WO-MANUAL-" + UUID.randomUUID(), receiptId, fixture.receivableId(),
                        ScmOperator.current());
                evictMybatisCache();
                assertThat(customerView(fixture).getPaidAmount()).isEqualByComparingTo("1.0000");

                jdbc.update("""
                        INSERT INTO xsy_finance_write_off (write_off_no, source_type, source_id, target_type,
                            target_id, amount, entry_type, reverse_of_id, reason, written_off_at, operator)
                        VALUES (?, 'RECEIPT', ?, 'RECEIVABLE', ?, 1.0000, 'REVERSE', ?,
                            '人工核销撤销', now(), ?)
                        """, "WO-REVERSE-" + UUID.randomUUID(), receiptId, fixture.receivableId(), writeOffId,
                        ScmOperator.current());
                evictMybatisCache();
                assertThat(customerView(fixture).getPaidAmount()).isEqualByComparingTo("0.0000");
                assertThat(due(customerView(fixture))).isEqualByComparingTo(PAYABLE);
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

        @Nested

        class MallOrderSummaryPgIT {
            @Autowired
            private MallOrderQueryService mallOrderQueryService;

            @Test
            @DisplayName("#1 按规格聚合：同规格跨订单相加，取消与非商城订单不计入")
            void aggregatesBySkuAndIgnoresCancelledOrAdminOrders() {
                Long customerId = newCustomer();
                Long skuA = newOnShelfSku("SUMA");
                Long skuB = newOnShelfSku("SUMB");

                Long first = insertOrder(customerId, "MALL", "CONFIRMED", OffsetDateTime.now().minusDays(2));
                insertItem(first, skuA, "2.0000", "20.0000", "甲商品");
                insertItem(first, skuB, "1.0000", "30.0000", "乙商品");

                Long second = insertOrder(customerId, "MALL", "PENDING", OffsetDateTime.now().minusDays(1));
                insertItem(second, skuA, "3.0000", "30.0000", "甲商品");

                // 取消的不算买过；后台代客下单不属于客户自己的商城下单
                Long cancelled = insertOrder(customerId, "MALL", "CANCELLED", OffsetDateTime.now());
                insertItem(cancelled, skuA, "99.0000", "990.0000", "甲商品");
                Long adminOrder = insertOrder(customerId, "ADMIN", "CONFIRMED", OffsetDateTime.now());
                insertItem(adminOrder, skuA, "88.0000", "880.0000", "甲商品");

                MallOrderSummaryVO summary = mallOrderQueryService.summary(customer(customerId), query(null, null));

                assertThat(summary.getItems()).hasSize(2);
                MallOrderSummaryVO.Item rowA = summary.getItems().stream()
                        .filter(item -> item.getSkuId().equals(skuA))
                        .findFirst()
                        .orElseThrow();
                assertThat(rowA.getQuantity()).isEqualByComparingTo("5.0000");
                assertThat(rowA.getAmount()).isEqualByComparingTo("50.0000");
                assertThat(rowA.getOrderCount()).isEqualTo(2);

                assertThat(summary.getTotals().getQuantity()).isEqualByComparingTo("6.0000");
                assertThat(summary.getTotals().getAmount()).isEqualByComparingTo("80.0000");
                assertThat(summary.getTotals().getOrderCount()).isEqualTo(2);
                assertThat(summary.getTotals().getSkuCount()).isEqualTo(2);
                assertThat(summary.getTotal()).isEqualTo(2);
                assertThat(summary.getEmptyFlag()).isFalse();
            }

            @Test
            @DisplayName("#2 日期区间按天收窄：止日当天含在区间内")
            void filtersByInclusiveDateRange() {
                Long customerId = newCustomer();
                Long skuId = newOnShelfSku("SUMC");

                Long january = insertOrder(customerId, "MALL", "CONFIRMED",
                        OffsetDateTime.of(2026, 1, 5, 23, 30, 0, 0, ZoneOffset.ofHours(8)));
                insertItem(january, skuId, "1.0000", "10.0000", "丙商品");
                Long march = insertOrder(customerId, "MALL", "CONFIRMED",
                        OffsetDateTime.of(2026, 3, 5, 0, 30, 0, 0, ZoneOffset.ofHours(8)));
                insertItem(march, skuId, "2.0000", "20.0000", "丙商品");

                MallOrderSummaryVO januaryOnly = mallOrderQueryService.summary(customer(customerId),
                        query(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 5)));
                assertThat(januaryOnly.getItems()).hasSize(1);
                assertThat(januaryOnly.getItems().getFirst().getQuantity()).isEqualByComparingTo("1.0000");

                // 止日当天 00:30 的单必须落在区间内：按「止日加一天」的半开区间比较
                MallOrderSummaryVO marchOnly = mallOrderQueryService.summary(customer(customerId),
                        query(LocalDate.of(2026, 3, 5), LocalDate.of(2026, 3, 5)));
                assertThat(marchOnly.getItems()).hasSize(1);
                assertThat(marchOnly.getItems().getFirst().getQuantity()).isEqualByComparingTo("2.0000");
            }

            private MallOrderSummaryQueryForm query(LocalDate startDate, LocalDate endDate) {
                MallOrderSummaryQueryForm form = new MallOrderSummaryQueryForm();
                form.setPageNum(1L);
                form.setPageSize(20L);
                form.setStartDate(startDate);
                form.setEndDate(endDate);
                return form;
            }

            private RequestMallCustomer customer(Long customerId) {
                RequestMallCustomer customer = new RequestMallCustomer();
                customer.setCustomerId(customerId);
                customer.setCustomerName("W9 汇总客户");
                return customer;
            }

            private Long insertOrder(Long customerId, String orderSource, String status, OffsetDateTime createdAt) {
                Long id = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) + 1 FROM xsy_sales_order", Long.class);
                jdbc.update("""
                        INSERT INTO xsy_sales_order
                          (id, order_no, customer_id, customer_code_snapshot, customer_name_snapshot, order_source,
                           status, settle_mode_snapshot, settlement_customer_id, settlement_customer_name_snapshot,
                           cancel_reason, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, 'INDEPENDENT', ?, ?, ?, ?, ?)
                        """, id, "W9SUM" + id, customerId, "C" + customerId, "W9 汇总客户", orderSource, status, customerId,
                        "W9 汇总客户", "CANCELLED".equals(status) ? "汇总用例取消" : null, createdAt, createdAt);
                return id;
            }

            private void insertItem(Long orderId, Long skuId, String quantity, String amount, String productName) {
                Long id = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) + 1 FROM xsy_sales_order_item", Long.class);
                Long spuId = jdbc.queryForObject("SELECT spu_id FROM xsy_product_sku WHERE id = ?", Long.class, skuId);
                BigDecimal orderedQuantity = new BigDecimal(quantity);
                BigDecimal lineAmount = new BigDecimal(amount);
                // ck_sales_order_item_draft_amount 要求单价与行金额同时有值或同时为空
                BigDecimal unitPrice = lineAmount.divide(orderedQuantity, 4, RoundingMode.HALF_UP);
                jdbc.update("""
                        INSERT INTO xsy_sales_order_item
                          (id, order_id, spu_id, sku_id, spu_code_snapshot, product_name_snapshot, sku_code_snapshot,
                           spec_name_snapshot, sale_unit_snapshot, product_type_snapshot, ordered_quantity,
                           draft_unit_price, draft_price_source, ordered_line_amount, sort_order)
                        VALUES (?, ?, ?, ?, ?, ?, ?, '默认规格', 'kg', 'STANDARD', ?, ?, 'MARKET', ?, 1)
                        """, id, orderId, spuId, skuId, "SPU" + spuId, productName, "SKU" + skuId, orderedQuantity, unitPrice,
                        lineAmount);
            }
        }



        @Nested

        class MallPromotionUiFixtureManual {
            @Autowired
            private MallAccountService accountService;

            @Autowired
            private MallAddressService addressService;

            @Test
            @EnabledIfSystemProperty(named = "scm.mall.promotion.ui-fixture", matches = "true")
            @Transactional(propagation = Propagation.NOT_SUPPORTED)
            void createDisposableCustomerAndProduct() throws IOException {
                String database = jdbc.queryForObject("SELECT current_database()", String.class);
                String expectedDatabase = System.getProperty("scm.mall.promotion.ui-db", "");
                assertThat(expectedDatabase).matches("xsy_(verify|codex_mkt)_[A-Za-z0-9_]+");
                assertThat(database).isEqualTo(expectedDatabase);

                Long customerId = newCustomer();
                Long skuId = newOnShelfSku("MKTUI");
                String phone = "139" + String.format("%08d", Integer.toUnsignedLong(UUID.randomUUID().hashCode())
                        % 100_000_000);
                String temporaryPassword = "MktUi@" + UUID.randomUUID().toString().substring(0, 10);
                String newPassword = "MktUi@" + UUID.randomUUID().toString().substring(0, 10);
                MallAccountCreateForm account = new MallAccountCreateForm();
                account.setCustomerId(customerId);
                account.setLoginPhone(phone);
                account.setTemporaryPassword(temporaryPassword);
                Long accountId = accountService.create(account).getAccountId();

                RequestMallCustomer identity = new RequestMallCustomer();
                identity.setAccountId(accountId);
                identity.setCustomerId(customerId);
                MallAddressSaveForm address = new MallAddressSaveForm();
                address.setReceiverName("营销联调客户");
                address.setReceiverPhone(phone);
                address.setProvinceCode(310000);
                address.setCityCode(310100);
                address.setDistrictCode(310115);
                address.setProvinceName("上海市");
                address.setCityName("上海市");
                address.setDistrictName("浦东新区");
                address.setDetailAddress("隔离库联调路 1 号");
                address.setIsDefault(true);
                Long addressId = addressService.create(identity, address).getFirst().getAddressId();

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("database", database);
                result.put("customerId", customerId);
                result.put("skuId", skuId);
                result.put("addressId", addressId);
                result.put("phone", phone);
                result.put("temporaryPassword", temporaryPassword);
                result.put("newPassword", newPassword);
                Path output = Path.of("../../.runtime/mkt-validation/accounts.json").toAbsolutePath().normalize();
                Files.createDirectories(output.getParent());
                Files.writeString(output, objectMapper.writeValueAsString(result));
            }
        }

        @Nested
        @DisplayName("营销优惠归属与失效校验（PG IT）")
        class MallPromotionUsePgIT {
            @Autowired
            private PromotionCouponService promotionCouponService;

            @Autowired
            private PromotionDiscountService promotionDiscountService;

            @Autowired
            private PromotionCouponInstanceDao promotionCouponInstanceDao;

            @Test
            @DisplayName("跨客户：客户 B 不能用客户 A 的券试算或保存")
            void crossCustomerCouponIsRejected() {
                asAdmin();
                Long couponId = claimableCoupon("归属");
                Long owner = newCustomer();
                Long intruder = newCustomer();
                Long instanceId = claim(owner, couponId, key("own"));

                // 试算：客户 B 用客户 A 的券
                PromotionDiscountPreviewForm preview = previewForm(intruder, instanceId, "100.0000");
                assertThatThrownBy(() -> promotionDiscountService.preview(preview))
                        .isInstanceOfSatisfying(ScmBusinessException.class,
                                e -> assertThat(e.getErrorCode()).isEqualTo(PromotionErrorCode.COUPON_INSTANCE_NOT_FOUND));

                // 保存：客户 B 用客户 A 的券（订单记录的客户是 B）
                PromotionOrderFacts facts = new PromotionOrderFacts(999_000_001L, intruder,
                        List.of(new PromotionOrderFacts.Line(1L, 1L, new BigDecimal("1.0000"), new BigDecimal("100.0000"))));
                assertThatThrownBy(() -> promotionDiscountService.freeze(facts, instanceId))
                        .isInstanceOfSatisfying(ScmBusinessException.class,
                                e -> assertThat(e.getErrorCode()).isEqualTo(PromotionErrorCode.COUPON_INSTANCE_NOT_FOUND));
            }

            @Test
            @DisplayName("失效券（已用）无法保存")
            void usedCouponCannotBeFrozen() {
                asAdmin();
                Long couponId = claimableCoupon("已用");
                Long customerId = newCustomer();
                Long instanceId = claim(customerId, couponId, key("used"));
                markInstanceStatus(instanceId, "USED");

                PromotionOrderFacts facts = new PromotionOrderFacts(999_000_002L, customerId,
                        List.of(new PromotionOrderFacts.Line(1L, 1L, new BigDecimal("1.0000"), new BigDecimal("100.0000"))));
                assertThatThrownBy(() -> promotionDiscountService.freeze(facts, instanceId))
                        .isInstanceOfSatisfying(ScmBusinessException.class,
                                e -> assertThat(e.getErrorCode()).isEqualTo(PromotionErrorCode.COUPON_INSTANCE_STATE_INVALID));
            }

            @Test
            @DisplayName("失效券（已释放）无法保存")
            void releasedCouponCannotBeFrozen() {
                asAdmin();
                Long couponId = claimableCoupon("已释放");
                Long customerId = newCustomer();
                Long instanceId = claim(customerId, couponId, key("rel"));
                markInstanceStatus(instanceId, "RELEASED");

                PromotionOrderFacts facts = new PromotionOrderFacts(999_000_003L, customerId,
                        List.of(new PromotionOrderFacts.Line(1L, 1L, new BigDecimal("1.0000"), new BigDecimal("100.0000"))));
                assertThatThrownBy(() -> promotionDiscountService.freeze(facts, instanceId))
                        .isInstanceOfSatisfying(ScmBusinessException.class,
                                e -> assertThat(e.getErrorCode()).isEqualTo(PromotionErrorCode.COUPON_INSTANCE_STATE_INVALID));
            }

            @Test
            @DisplayName("失效券（模板过期）无法保存")
            void expiredCouponCannotBeFrozen() {
                asAdmin();
                // 先造一张有效期内的券模板并启用、领券，然后把模板有效期改成过去，
                // 模拟「领券时有效、保存前模板已过期」的窗口（过期模板无法直接启用，updateStatus 会拒绝）。
                Long couponId = claimableCoupon("过期");
                Long customerId = newCustomer();
                Long instanceId = claim(customerId, couponId, key("exp"));
                expireCouponTemplate(couponId);

                PromotionOrderFacts facts = new PromotionOrderFacts(999_000_004L, customerId,
                        List.of(new PromotionOrderFacts.Line(1L, 1L, new BigDecimal("1.0000"), new BigDecimal("100.0000"))));
                assertThatThrownBy(() -> promotionDiscountService.freeze(facts, instanceId))
                        .isInstanceOfSatisfying(ScmBusinessException.class,
                                e -> assertThat(e.getErrorCode()).isEqualTo(PromotionErrorCode.COUPON_STATE_INVALID));
            }

            @Test
            @DisplayName("有效券保存成功：占用该券并写入优惠记录")
            void validCouponFreezesSuccessfully() {
                asAdmin();
                Long couponId = claimableCoupon("有效");
                Long customerId = newCustomer();
                Long instanceId = claim(customerId, couponId, key("ok"));

                PromotionOrderFacts facts = new PromotionOrderFacts(999_000_005L, customerId,
                        List.of(new PromotionOrderFacts.Line(1L, 1L, new BigDecimal("1.0000"), new BigDecimal("100.0000"))));
                var frozen = promotionDiscountService.freeze(facts, instanceId);

                assertThat(frozen.isFrozen()).as("有效券保存后返回保存标记").isTrue();
                assertThat(frozen.getCouponInstanceId()).isEqualTo(instanceId);
                assertThat(frozen.getCouponDiscount()).isEqualByComparingTo("5.0000");
                assertThat(frozen.getFinalAmount()).isEqualByComparingTo("95.0000");

                PromotionCouponInstanceEntity instance = promotionCouponInstanceDao.selectById(instanceId);
                assertThat(instance.getStatus()).as("保存后券实例转为 RESERVED").isEqualTo("RESERVED");
                assertThat(instance.getReservedOrderId()).isEqualTo(999_000_005L);
            }

            // ------------------------------------------------------------------
            // 夹具
            // ------------------------------------------------------------------

            private PromotionDiscountPreviewForm previewForm(Long customerId, Long instanceId, String baseAmount) {
                PromotionDiscountPreviewForm form = new PromotionDiscountPreviewForm();
                form.setCustomerId(customerId);
                form.setCouponInstanceId(instanceId);
                PromotionDiscountPreviewForm.Line line = new PromotionDiscountPreviewForm.Line();
                line.setOrderItemId(1L);
                line.setSkuId(1L);
                line.setQuantity(new BigDecimal("1.0000"));
                line.setBaseAmount(new BigDecimal(baseAmount));
                form.setLines(List.of(line));
                return form;
            }

            private Long claim(Long customerId, Long couponId, String key) {
                return promotionCouponService.claimFromMall(customerId, couponId, key);
            }

            private Long claimableCoupon(String suffix) {
                PromotionCouponForm form = new PromotionCouponForm();
                form.setCouponCode("U-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
                form.setCouponName(prefix + "券" + suffix);
                form.setDiscountType(ScmPromotionCouponDiscountTypeEnum.AMOUNT.name());
                form.setDiscountValue(new BigDecimal("5.0000"));
                form.setMinOrderAmount(BigDecimal.ZERO);
                form.setClaimable(true);
                form.setValidFrom(OffsetDateTime.now().minusMinutes(1));
                form.setValidTo(OffsetDateTime.now().plusDays(1));
                Long id = promotionCouponService.save(form);
                PromotionStatusForm status = new PromotionStatusForm();
                status.setVersion(0);
                status.setStatus(ScmPromotionStatusEnum.ACTIVE.name());
                promotionCouponService.updateStatus(id, status);
                return id;
            }

            /** 把券模板有效期整体改成过去，模拟「领券时有效、保存前模板已过期」的窗口（保持 valid_from < valid_to）。 */
            private void expireCouponTemplate(Long couponId) {
                evictMybatisCache();
                int updated = jdbc.update("UPDATE xsy_promotion_coupon SET valid_from = ?, valid_to = ? WHERE id = ?",
                        java.sql.Timestamp.from(OffsetDateTime.now().minusHours(2).toInstant()),
                        java.sql.Timestamp.from(OffsetDateTime.now().minusHours(1).toInstant()), couponId);
                assertThat(updated).isEqualTo(1);
                evictMybatisCache();
            }

            /**
             * 把券实例置为指定终态，满足状态 CHECK 约束（终态必须带时间 / 订单痕迹）。
             *
             * <p>这里直接落完整字段而不是走 {@code markUsed} / {@code markReleased}：后两者要求前置状态是
             * {@code RESERVED}（先保存再核销 / 释放），而本测试要验证的正是「已用 / 已释放的券不能再保存」，
             * 用状态流转 DAO 会把「制造失效券」和「验证拒绝保存」两件事缠在一起。
             */
            private void markInstanceStatus(Long instanceId, String status) {
                evictMybatisCache();
                int updated;
                if ("USED".equals(status)) {
                    updated = jdbc.update("UPDATE xsy_promotion_coupon_instance SET status = 'USED',"
                            + " used_order_id = 999_000_000, used_at = CURRENT_TIMESTAMP WHERE id = ?", instanceId);
                } else {
                    updated = jdbc.update("UPDATE xsy_promotion_coupon_instance SET status = 'RELEASED',"
                            + " released_at = CURRENT_TIMESTAMP WHERE id = ?", instanceId);
                }
                assertThat(updated).isEqualTo(1);
                evictMybatisCache();
            }

            private String key(String tag) {
                return prefix + ":" + tag + ":" + UUID.randomUUID();
            }

            private void asAdmin() {
                RequestEmployee employee = new RequestEmployee();
                employee.setEmployeeId(1L);
                employee.setActualName("Promotion use IT");
                employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
                employee.setAdministratorFlag(true);
                XsyRequestUtil.setRequestUser(employee);
            }
        }

        @Nested

        class MallSettlementOptionsPgIT {
            private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
            private static final BigDecimal PAYABLE = new BigDecimal("2.4000");

            @Autowired
            private MallSettlementService settlementService;

            @Test
            @DisplayName("集团共享结算：成员与集团主体都读不到共享钱包余额与集团授信，且不能选共享方式")
            void sharedSettlementNeverExposesGroupFunds() {
                Long group = groupCustomer(null);
                Long member = groupCustomer(group);
                // 给集团配上大额授信与共享钱包余额：如果实现漏了规则，这里就会把 88888 / 66666 返给成员
                jdbc.update("UPDATE xsy_customer SET credit_limit = ? WHERE id = ?", new BigDecimal("88888.0000"), group);
                Long account = wallet(group);
                movement(account, group, member, "CREDIT", "66666.0000");
                evictMybatisCache();

                Map<String, MallSettlementOptionVO> memberOptions = options(identity(member));
                assertThat(memberOptions.get("WALLET").getAmountVisible()).as("共享结算不返回余额").isFalse();
                assertThat(memberOptions.get("WALLET").getAvailableAmount()).isNull();
                assertThat(memberOptions.get("WALLET").getEnabled()).as("共享资金需独立授权，默认不可选").isFalse();
                assertThat(memberOptions.get("WALLET").getDisabledReason()).isNotBlank();
                assertThat(memberOptions.get("CREDIT").getAmountVisible()).as("共享结算不返回集团授信").isFalse();
                assertThat(memberOptions.get("CREDIT").getAvailableAmount()).isNull();
                assertThat(memberOptions.get("CREDIT").getEnabled()).isFalse();
                assertThat(memberOptions.get("CREDIT").getDisabledReason()).isNotBlank();
                assertThat(memberOptions.get("COD").getEnabled()).as("货到付款未开通时不可选").isFalse();
                jdbc.update("UPDATE xsy_customer SET cod_allowed = TRUE WHERE id = ?", member);
                evictMybatisCache();
                assertThat(options(identity(member)).get("COD").getEnabled())
                        .as("成员获明确授权后可使用货到付款，不需要读取共享资金金额").isTrue();

                // 集团主体自身结算时没有共享关系，可以看到自己的钱包与授信
                Map<String, MallSettlementOptionVO> groupOptions = options(identity(group));
                assertThat(groupOptions.get("WALLET").getAmountVisible()).isTrue();
                assertThat(groupOptions.get("CREDIT").getAmountVisible()).isTrue();
            }

            @Test
            @DisplayName("集团钱包授权只放行余额：账期仍由集团主体统一办理，不随授权打开")
            void sharedWalletGrantDoesNotOpenGroupCredit() {
                Long group = groupCustomer(null);
                Long member = groupCustomer(group);
                jdbc.update("UPDATE xsy_customer SET shared_settlement_enabled = TRUE WHERE id = ?", group);
                evictMybatisCache();

                Map<String, MallSettlementOptionVO> memberOptions = options(identity(member));
                assertThat(memberOptions.get("WALLET").getEnabled()).as("授权后可用集团钱包").isTrue();
                assertThat(memberOptions.get("WALLET").getAmountVisible()).as("授权不等于能看集团余额").isFalse();
                assertThat(memberOptions.get("WALLET").getAvailableAmount()).isNull();
                assertThat(memberOptions.get("CREDIT").getEnabled()).as("钱包授权不覆盖集团账期").isFalse();
                assertThat(memberOptions.get("CREDIT").getDisabledReason()).isNotBlank();
            }

            @Test
            @DisplayName("账期可用性按正式授信规则：占用、超限与逾期都会让账期不可选")
            void creditOptionFollowsExposureOverdueAndLimit() {
                Long customerId = newCustomer();
                Long skuId = newOnShelfSku("SETTLE-C");
                jdbc.update("UPDATE xsy_customer SET credit_limit = ? WHERE id = ?", new BigDecimal("100.0000"),
                        customerId);
                evictMybatisCache();

                // 1. 有额度且无占用：可选，剩余额度即额度本身
                MallSettlementOptionVO free = options(identity(customerId)).get("CREDIT");
                assertThat(free.getEnabled()).isTrue();
                assertThat(free.getAvailableAmount()).isEqualByComparingTo("100.0000");
                assertThat(options(identity(customerId)).get("COD").getEnabled()).as("货到付款默认关闭").isFalse();
                jdbc.update("UPDATE xsy_customer SET cod_allowed = TRUE WHERE id = ?", customerId);
                evictMybatisCache();
                assertThat(options(identity(customerId)).get("COD").getEnabled()).as("后台开通后可选货到付款").isTrue();

                // 2. 已确认但未开应收的订单计入敞口：额度足够时仍可选，剩余额度随之减少。
                //    注意必须先把额度留足，否则确认命令自己的授信校验就会先把这张订单拦下（41201）。
                Long orderId = confirmedSalesOrder(customerId, skuId, "10.0000", "10.0000");
                assertThat(jdbc.queryForObject("SELECT settlement_total_amount FROM xsy_sales_order WHERE id = ?",
                        BigDecimal.class, orderId)).isEqualByComparingTo("12.0000");
                MallSettlementOptionVO occupied = options(identity(customerId)).get("CREDIT");
                assertThat(occupied.getEnabled()).isTrue();
                assertThat(occupied.getAvailableAmount()).as("剩余额度 = 额度 - 已确认未开应收").isEqualByComparingTo("88.0000");

                // 3. 把额度压到已用敞口之下：本单再加 2.4 即超限 → 不可选，且不谎报剩余额度
                jdbc.update("UPDATE xsy_customer SET credit_limit = ? WHERE id = ?", new BigDecimal("10.0000"), customerId);
                evictMybatisCache();
                MallSettlementOptionVO overLimit = options(identity(customerId)).get("CREDIT");
                assertThat(overLimit.getEnabled()).as("超出剩余额度不得显示为可选").isFalse();
                assertThat(overLimit.getDisabledReason()).contains("超出");
                assertThat(overLimit.getAvailableAmount()).as("敞口已超过额度，剩余额度收敛为 0").isEqualByComparingTo("0.0000");

                // 4. 恢复额度后改为「逾期」：账期转为不可选，原因是逾期而不是额度
                jdbc.update("UPDATE xsy_customer SET credit_limit = ? WHERE id = ?", new BigDecimal("100.0000"),
                        customerId);
                normalReceivable(orderId, "12.0000", LocalDate.now(BUSINESS_ZONE).minusDays(1));
                evictMybatisCache();
                MallSettlementOptionVO overdue = options(identity(customerId)).get("CREDIT");
                assertThat(overdue.getEnabled()).isFalse();
                assertThat(overdue.getDisabledReason()).contains("逾期");
                MallSettlementOptionVO overdueCod = options(identity(customerId)).get("COD");
                assertThat(overdueCod.getEnabled()).as("货到付款不占额度，但逾期风险仍应阻断").isFalse();
                assertThat(overdueCod.getDisabledReason()).contains("逾期");
            }

            @Test
            @DisplayName("独立结算客户：金额可见，余额够则余额可选")
            void independentCustomerSeesOwnFunds() {
                Long customerId = newCustomer();
                Long account = wallet(customerId);
                movement(account, customerId, customerId, "CREDIT", "10.0000");
                evictMybatisCache();

                Map<String, MallSettlementOptionVO> options = options(identity(customerId));
                assertThat(options.get("WALLET").getAmountVisible()).isTrue();
                assertThat(options.get("WALLET").getAvailableAmount()).isEqualByComparingTo("10.0000");
                assertThat(options.get("WALLET").getEnabled()).isTrue();
                assertThat(options.get("CREDIT").getEnabled()).as("未配置授信额度不可选").isFalse();
            }

            // ------------------------------------------------------------------
            // 夹具
            // ------------------------------------------------------------------

            private Map<String, MallSettlementOptionVO> options(RequestMallCustomer identity) {
                List<MallSettlementOptionVO> rows = settlementService.options(identity, PAYABLE);
                assertThat(rows).hasSize(3);
                return rows.stream().collect(Collectors.toMap(MallSettlementOptionVO::getMethod, Function.identity()));
            }

            /** 集团（{@code parentId=null}）或集团成员；成员的结算主体指向集团。 */
            private Long groupCustomer(Long parentId) {
                CustomerAddForm form = new CustomerAddForm();
                form.setName(prefix + "-GROUP-" + UUID.randomUUID().toString().substring(0, 5));
                form.setSettleMode("GROUP");
                form.setSellerId(anyEmployeeId());
                form.setParentCustomerId(parentId);
                form.setSettlementCustomerId(parentId);
                Long id = customerService.add(form);
                CustomerStatusForm status = new CustomerStatusForm();
                status.setCustomerId(id);
                status.setVersion(customerService.require(id).getVersion());
                status.setStatus("COOPERATING");
                customerService.updateStatus(status);
                return id;
            }

            private Long normalReceivable(Long orderId, String amount, LocalDate dueDate) {
                OffsetDateTime eventAt = dueDate.minusDays(1).atStartOfDay(BUSINESS_ZONE).toOffsetDateTime();
                Long id = jdbc.queryForObject("""
                        INSERT INTO xsy_finance_receivable (receivable_no, source_type, source_id, order_id,
                            customer_id, customer_name_snapshot, settlement_customer_id,
                            settlement_customer_name_snapshot, entry_type, amount, event_at)
                        SELECT ?, 'SALES_ORDER', id, id, customer_id, customer_name_snapshot,
                            settlement_customer_id, settlement_customer_name_snapshot, 'NORMAL', ?, ?
                        FROM xsy_sales_order WHERE id = ? RETURNING id
                        """, Long.class, "AR-SETTLE-" + UUID.randomUUID(), new BigDecimal(amount), eventAt, orderId);
                jdbc.update("UPDATE xsy_finance_receivable SET due_date = ? WHERE id = ?", dueDate, id);
                return id;
            }

            private Long wallet(Long settlementCustomerId) {
                return jdbc.queryForObject("""
                        INSERT INTO xsy_customer_balance_account (settlement_customer_id, settlement_customer_name_snapshot)
                        SELECT id, name FROM xsy_customer WHERE id = ? RETURNING id
                        """, Long.class, settlementCustomerId);
            }

            private void movement(Long accountId, Long settlementCustomerId, Long customerId, String direction,
                    String amount) {
                jdbc.update("""
                        INSERT INTO xsy_customer_balance_movement (movement_no, account_id, settlement_customer_id,
                            customer_id, type, direction, amount, reason, occurred_at)
                        VALUES (?, ?, ?, ?, 'CORRECTION', ?, ?, '测试调整', now())
                        """, "BM-SETTLE-" + UUID.randomUUID(), accountId, settlementCustomerId, customerId, direction,
                        new BigDecimal(amount));
            }

            private static RequestMallCustomer identity(Long customerId) {
                RequestMallCustomer customer = new RequestMallCustomer();
                customer.setCustomerId(customerId);
                return customer;
            }
        }

        @Nested

        class MallSettlementUiFixtureManual {
            @Autowired
            private MallAccountService accountService;

            @Autowired
            private MallAddressService addressService;

            @Test
            @EnabledIfSystemProperty(named = "scm.mall.settlement.ui-fixture", matches = "true")
            @Transactional(propagation = Propagation.NOT_SUPPORTED)
            void createDisposableCustomer() throws IOException {
                String expectedDatabase = System.getProperty("scm.mall.settlement.ui-db", "");
                assertThat(expectedDatabase).matches("xsy_codex_set_[A-Za-z0-9_]+");
                assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo(expectedDatabase);

                Long customerId = newCustomer();
                Long skuId = newOnShelfSku("SETUI");
                assertThat(jdbc.queryForObject("SELECT cod_allowed FROM xsy_customer WHERE id = ?", Boolean.class, customerId))
                        .isFalse();
                String phone = "139" + String.format("%08d", Integer.toUnsignedLong(UUID.randomUUID().hashCode())
                        % 100_000_000);
                String temporaryPassword = "SetUi@" + UUID.randomUUID().toString().substring(0, 10);
                String newPassword = "SetUi@" + UUID.randomUUID().toString().substring(0, 10);
                MallAccountCreateForm account = new MallAccountCreateForm();
                account.setCustomerId(customerId);
                account.setLoginPhone(phone);
                account.setTemporaryPassword(temporaryPassword);
                Long accountId = accountService.create(account).getAccountId();

                RequestMallCustomer identity = new RequestMallCustomer();
                identity.setCustomerId(customerId);
                identity.setAccountId(accountId);
                MallAddressSaveForm address = new MallAddressSaveForm();
                address.setReceiverName("结算联调客户");
                address.setReceiverPhone(phone);
                address.setProvinceCode(310000);
                address.setCityCode(310100);
                address.setDistrictCode(310115);
                address.setProvinceName("上海市");
                address.setCityName("上海市");
                address.setDistrictName("浦东新区");
                address.setDetailAddress("结算隔离联调路 1 号");
                address.setIsDefault(true);
                Long addressId = addressService.create(identity, address).getFirst().getAddressId();

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("database", expectedDatabase);
                result.put("customerId", customerId);
                result.put("skuId", skuId);
                result.put("addressId", addressId);
                result.put("phone", phone);
                result.put("temporaryPassword", temporaryPassword);
                result.put("newPassword", newPassword);
                Path output = Path.of("../../.runtime/set-v130/accounts.json").toAbsolutePath().normalize();
                Files.createDirectories(output.getParent());
                Files.writeString(output, objectMapper.writeValueAsString(result));
            }

            @Test
            @EnabledIfSystemProperty(named = "scm.mall.settlement.open-cod", matches = "true")
            @Transactional(propagation = Propagation.NOT_SUPPORTED)
            void openCodThroughCustomerCommand() throws IOException {
                String expectedDatabase = System.getProperty("scm.mall.settlement.ui-db", "");
                assertThat(expectedDatabase).matches("xsy_codex_set_[A-Za-z0-9_]+");
                assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo(expectedDatabase);
                Path accountFile = Path.of("../../.runtime/set-v130/accounts.json").toAbsolutePath().normalize();
                @SuppressWarnings("unchecked")
                Map<String, Object> account = objectMapper.readValue(Files.readString(accountFile), Map.class);
                Long customerId = ((Number) account.get("customerId")).longValue();
                CustomerEntity customer = customerService.require(customerId);
                CustomerUpdateForm form = new CustomerUpdateForm();
                form.setCustomerId(customerId);
                form.setVersion(customer.getVersion());
                form.setName(customer.getName());
                form.setSettleMode(customer.getSettleMode());
                form.setSettlementCustomerId(customer.getSettlementCustomerId());
                form.setCreditLimit(customer.getCreditLimit().toPlainString());
                form.setCodAllowed(true);
                customerService.update(form);
                assertThat(customerService.require(customerId).getCodAllowed()).isTrue();
            }
        }

        @Nested

        class MallWarehouseAdminPgIT {
            @Autowired
            private MallWarehouseAdminService adminService;

            @Test
            void bindingAndClearingWarehousePersistThroughCustomerDomain() {
                Long customerId = newCustomer();
                Long warehouseId = seedWarehouseId();
                int version = jdbc.queryForObject("SELECT version FROM xsy_customer WHERE id=?", Integer.class, customerId);

                MallCustomerWarehouseBindingForm bind = new MallCustomerWarehouseBindingForm();
                bind.setCustomerId(customerId);
                bind.setWarehouseId(warehouseId);
                bind.setVersion(version);
                adminService.bindCustomer(bind);
                Map<String, Object> bound = jdbc.queryForMap(
                        "SELECT mall_delivery_warehouse_id, version FROM xsy_customer WHERE id=?", customerId);
                assertThat(bound.get("mall_delivery_warehouse_id")).isEqualTo(warehouseId);
                assertThat((Integer) bound.get("version")).isEqualTo(version + 1);

                MallCustomerWarehouseBindingForm clear = new MallCustomerWarehouseBindingForm();
                clear.setCustomerId(customerId);
                clear.setVersion(version + 1);
                adminService.bindCustomer(clear);
                Map<String, Object> cleared = jdbc.queryForMap(
                        "SELECT mall_delivery_warehouse_id, version FROM xsy_customer WHERE id=?", customerId);
                assertThat(cleared.get("mall_delivery_warehouse_id")).isNull();
                assertThat((Integer) cleared.get("version")).isEqualTo(version + 2);
            }
        }
    }

    @Nested
    @DisplayName("商城订货与后台余额结算（PG IT）")
    class MallWalletPaymentPgIT extends ScmW5PgITBase {
        private static final String TEMPORARY_PASSWORD = "Mall@123456";
        private static final String NEW_PASSWORD = "Mall@654321";

        /** 下单 3.0000、实重 2.0000、客户价 1.2000：应付必须按实重算成 2.4000，而不是按订货量 3.6000。 */
        private static final String ORDERED_QUANTITY = "3.0000";
        private static final String ACTUAL_QUANTITY = "2.0000";
        private static final BigDecimal PAYABLE = new BigDecimal("2.4000");

        @Autowired
        private MallAccountService mallAccountService;

        @Autowired
        private MallAuthService mallAuthService;

        @Autowired
        private MallAddressService mallAddressService;

        @MockitoBean
        private MallMapService mapService;

        @BeforeEach
        void stubExternalGeocode() {
            MallMapLocationVO location = new MallMapLocationVO();
            location.setLongitude(new BigDecimal("121.544000"));
            location.setLatitude(new BigDecimal("31.221000"));
            location.setProvinceCode(310000);
            location.setCityCode(310100);
            location.setDistrictCode(310115);
            location.setProvinceCode(310000);
            location.setCityCode(310100);
            location.setDistrictCode(310115);
            location.setProvinceName("上海市");
            location.setCityName("上海市");
            location.setDistrictName("浦东新区");
            when(mapService.reverse(any(BigDecimal.class), any(BigDecimal.class))).thenReturn(location);
        }

        @Override
        protected Long newOnShelfSku(String suffix) {
            Long skuId = super.newOnShelfSku(suffix);
            Long supplierId = newPurchasableSupplier(suffix + "-stock", skuId);
            var purchase = createDraftOrder(suffix + "-stock", supplierId, skuId, "100.0000", "6.2000");
            var receipt = submittedOrderReceipt(purchase.getId());
            var current = reloadReceipt(receipt.getId());
            var item = current.getItems().getFirst();
            purchaseReceiptService.confirm(
                    confirmForm(current.getId(), current.getVersion(),
                            receiptLine(item.getId(), item.getVersion(), "100.0000")),
                    prefix + ":stock:" + suffix);
            evictMybatisCache();
            return skuId;
        }

        @Autowired
        private MallCheckoutService mallCheckoutService;

        @Autowired
        private MallOrderQueryService mallOrderQueryService;

        @Autowired
        private MallPaymentService mallPaymentService;

        @Autowired
        private PaymentIntentService paymentIntentService;

        @Autowired
        private SalesOrderService salesOrderService;

        @Autowired
        private SalesOrderQueryService salesOrderQueryService;

        @Test
        @DisplayName("后台支付：扣款额等于服务端算出的应付净额，钱包流水与资金记录同事务写入数据库")
        void walletPaymentDeductsExactlyTheServerComputedPayable() {
            WalletOrder fixture = mallOrder("happy", "10.0000", ScmSettlementMethodEnum.WALLET, true);

            MallOrderVO before = view(fixture);
            assertThat(before.getSettlementMethod()).isEqualTo(ScmSettlementMethodEnum.WALLET.name());
            assertThat(before.getPayableAmount())
                    .as("应付净额按实重算：实重 2.0000 × 1.2000，不是按订货量 3.0000")
                    .isEqualByComparingTo(PAYABLE);
            assertThat(before.getPaidAmount()).isEqualByComparingTo("0.0000");

            MallOrderVO paid = pay(fixture, prefix + ":pay-happy");

            assertThat(paid.getPayableAmount()).isEqualByComparingTo(PAYABLE);
            assertThat(paid.getPaidAmount()).isEqualByComparingTo(PAYABLE);
            assertThat(walletDebits(fixture.walletId()))
                    .as("钱包只被扣走应付净额，多一分少一分都说明金额不是服务端算的")
                    .isEqualByComparingTo(PAYABLE);
            assertThat(walletMovementCount(fixture.walletId(), "CONSUME")).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT status FROM xsy_payment_intent WHERE source_type = 'SALES_ORDER'"
                    + " AND source_id = ? AND deleted = FALSE", String.class, fixture.orderId()))
                    .as("余额在本地消费成功即推进到成功，没有外部渠道要等")
                    .isEqualTo("SUCCEEDED");
            assertThat(paymentTransactionAmount(fixture.orderId())).isEqualByComparingTo(PAYABLE);

            assertThatThrownBy(() -> pay(fixture, prefix + ":pay-again"))
                    .isInstanceOfSatisfying(ScmBusinessException.class, failure -> assertThat(failure.getErrorCode())
                            .as("已付清再点必须被拒，而不是再扣一笔")
                            .isEqualTo(PaymentErrorCode.PAYMENT_AMOUNT_EXCEEDS_DUE));
            assertThat(walletMovementCount(fixture.walletId(), "CONSUME")).isEqualTo(1);
        }

        @Test
        @DisplayName("集团共享结算：后台为子客户支付扣的是集团钱包，自己的余额一分不动")
        void sharedSettlementPaysFromGroupWallet() {
            asAdmin();
            // 走正式建档命令而不是改库：上级客户必须是集团类型、结算主体要指向集团，这些校验只在命令里跑
            Long groupId = customerService.add(groupForm(null));
            cooperate(groupId);
            jdbc.update("UPDATE xsy_customer SET shared_settlement_enabled=TRUE WHERE id = ?", groupId);
            Long groupWalletId = fundWallet(groupId, "50.0000");
            Long memberId = customerService.add(groupForm(groupId));
            cooperate(memberId);
            jdbc.update("UPDATE xsy_customer SET mall_delivery_warehouse_id=? WHERE id=?", seedWarehouseId(), memberId);
            Long memberWalletId = fundWallet(memberId, "999.0000");
            evictMybatisCache();

            WalletOrder fixture = mallOrderFor(memberId, memberWalletId, "group", ScmSettlementMethodEnum.WALLET, true);
            MallOrderVO paid = pay(fixture, prefix + ":pay-group");

            assertThat(paid.getPaidAmount()).isEqualByComparingTo(paid.getPayableAmount());
            assertThat(walletDebits(groupWalletId))
                    .as("集团共享结算扣的是集团钱包")
                    .isEqualByComparingTo(paid.getPayableAmount());
            assertThat(walletDebits(memberWalletId))
                    .as("子客户自己的余额不该被动")
                    .isEqualByComparingTo("0.0000");
            assertThat(jdbc.queryForObject("SELECT settlement_customer_id FROM xsy_customer_balance_movement"
                    + " WHERE account_id = ? AND direction = 'DEBIT' AND deleted = FALSE", Long.class, groupWalletId))
                    .as("流水落在集团账上")
                    .isEqualTo(groupId);
        }

        @Test
        @DisplayName("同一防重复请求编号重放返回已付订单，钱包不出现第二笔扣款")
        void replayingTheSameIdempotencyKeyNeverDeductsASecondTime() {
            WalletOrder fixture = mallOrder("replay", "10.0000", ScmSettlementMethodEnum.WALLET, true);
            String key = prefix + ":pay-replay";

            pay(fixture, key);

            MallOrderVO replayed = pay(fixture, key);
            assertThat(replayed.getPaidAmount()).isEqualByComparingTo(PAYABLE);

            assertThat(walletMovementCount(fixture.walletId(), "CONSUME")).isEqualTo(1);
            assertThat(walletDebits(fixture.walletId())).isEqualByComparingTo(PAYABLE);
            assertThat(paymentIntentCount(fixture.orderId()))
                    .as("重放不能造出第二个支付意图")
                    .isEqualTo(1);
        }

        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        @DisplayName("余额不足：在写任何资金记录之前失败，订单仍是未付")
        void insufficientWalletBalanceFailsBeforeAnyFundingFact() {
            // 「整笔回滚」只在真提交时才暴露：测试外层事务会把失败行留在库里等着回滚，断言就没有意义。
            WalletOrder fixture = mallOrder("short", "10.0000", ScmSettlementMethodEnum.WALLET, true);
            spendWallet(fixture, "9.0000");
            assertThat(walletBalance(fixture.walletId())).as("余额已降到应付之下").isEqualByComparingTo("1.0000");

            assertThatThrownBy(() -> pay(fixture, prefix + ":pay-short"))
                    .isInstanceOfSatisfying(ScmBusinessException.class, failure -> assertThat(failure.getErrorCode())
                            .isEqualTo(BalanceErrorCode.BALANCE_INSUFFICIENT));

            assertThat(walletMovementCount(fixture.walletId(), "CONSUME")).isZero();
            assertThat(paymentIntentCount(fixture.orderId()))
                    .as("整笔支付是一个事务：意图插入必须随失败一起回滚")
                    .isZero();
            assertThat(view(fixture).getPaidAmount()).isEqualByComparingTo("0.0000");
        }

        @Test
        @DisplayName("未确认、非余额结算、别人的订单：都在扣款之前被挡下")
        void unconfirmedNonWalletAndForeignOrdersAreRejectedBeforeDeduction() {
            WalletOrder unconfirmed = mallOrder("pending", "10.0000", ScmSettlementMethodEnum.WALLET, false);
            assertThatThrownBy(() -> pay(unconfirmed, prefix + ":pay-pending"))
                    .isInstanceOfSatisfying(ScmBusinessException.class, failure -> assertThat(failure.getErrorCode())
                            .as("确认前实重未齐，没有权威应付规则，也就没有可支付的金额")
                            .isEqualTo(PaymentErrorCode.PAYMENT_INTENT_SOURCE_INVALID));
            assertThat(walletMovementCount(unconfirmed.walletId(), "CONSUME")).isZero();

            WalletOrder credit = mallOrder("credit", "10.0000", ScmSettlementMethodEnum.CREDIT, true);
            assertThatThrownBy(() -> pay(credit, prefix + ":pay-credit"))
                    .isInstanceOfSatisfying(ScmBusinessException.class, failure -> assertThat(failure.getErrorCode())
                            .as("账期订单的钱走应收，不能从钱包扣")
                            .isEqualTo(PaymentErrorCode.PAYMENT_SETTLEMENT_METHOD_BLOCKED));
            assertThat(walletMovementCount(credit.walletId(), "CONSUME")).isZero();

            WalletOrder foreign = mallOrder("foreign", "10.0000", ScmSettlementMethodEnum.WALLET, true);
            RequestMallCustomer stranger = signIn(openMallAccount(newCustomer()));
            assertThatThrownBy(() -> mallOrderQueryService.detail(stranger, foreign.orderId()))
                    .isInstanceOfSatisfying(ScmBusinessException.class,
                            failure -> assertThat(failure.getErrorCode()).isEqualTo(MallErrorCode.ORDER_NOT_FOUND));
            assertThatThrownBy(() -> mallPaymentService.pay(stranger, foreign.orderId(), prefix + ":pay-foreign"))
                    .isInstanceOfSatisfying(ScmBusinessException.class, failure -> assertThat(failure.getErrorCode())
                            .as("停用接口对任何订单都不提供扣款能力")
                            .isEqualTo(MallErrorCode.ORDER_PAYMENT_DISABLED));
            assertThat(walletMovementCount(foreign.walletId(), "CONSUME")).isZero();
        }

        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        @DisplayName("并发双击：订单行锁把两次支付串行化，钱包只被扣一次")
        void concurrentDoubleClickDeductsExactlyOnce() throws Exception {
            // 本用例要真并发，因此关掉测试外层事务：夹具会真实提交，只应在授权的隔离库运行。
            WalletOrder fixture = mallOrder("race", "10.0000", ScmSettlementMethodEnum.WALLET, true);

            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<String>> outcomes = new ArrayList<>();
            try {
                for (int index = 0; index < 2; index++) {
                    final String key = prefix + ":race-" + index;
                    outcomes.add(pool.submit(() -> {
                        XsyRequestUtil.setRequestUser(fixture.customer());
                        ready.countDown();
                        go.await();
                        try {
                            pay(fixture, key);
                            return "PAID";
                        } catch (ScmBusinessException rejected) {
                            return String.valueOf(rejected.getErrorCode().getCode());
                        } finally {
                            XsyRequestUtil.remove();
                        }
                    }));
                }
                assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
                go.countDown();
                List<String> results = new ArrayList<>();
                for (Future<String> outcome : outcomes) {
                    results.add(outcome.get(60, TimeUnit.SECONDS));
                }
                assertThat(results)
                        .as("恰好一侧成功，另一侧必须是被业务规则拒绝，而不是死锁、超时或裸约束异常")
                        .containsExactlyInAnyOrder("PAID", String.valueOf(PaymentErrorCode.PAYMENT_AMOUNT_EXCEEDS_DUE.getCode()));
            } finally {
                pool.shutdownNow();
            }

            assertThat(walletMovementCount(fixture.walletId(), "CONSUME")).isEqualTo(1);
            assertThat(walletDebits(fixture.walletId())).isEqualByComparingTo(PAYABLE);
            assertThat(paymentIntentCount(fixture.orderId())).isEqualTo(1);
        }

        @Test
        @DisplayName("小程序旧支付入口在待处理及已确认时均拒绝，不产生资金记录")
        void miniProgramPaymentIsDisabledForOwnOrders() {
            for (boolean confirmed : List.of(false, true)) {
                WalletOrder fixture = mallOrder("disabled-" + confirmed, "10.0000", ScmSettlementMethodEnum.WALLET, confirmed);
                XsyRequestUtil.setRequestUser(fixture.customer());
                assertThatThrownBy(() -> mallPaymentService.pay(fixture.customer(), fixture.orderId(), prefix + ":disabled:" + confirmed))
                        .isInstanceOfSatisfying(ScmBusinessException.class,
                                failure -> assertThat(failure.getErrorCode()).isEqualTo(MallErrorCode.ORDER_PAYMENT_DISABLED));
                assertThat(walletMovementCount(fixture.walletId(), "CONSUME")).isZero();
                assertThat(paymentIntentCount(fixture.orderId())).isZero();
            }
        }

        // ------------------------------------------------------------------
        // 夹具
        // ------------------------------------------------------------------

        /** 新建客户默认「合作中」，这里保留防重复处理确认，防止夹具后续被改成其它初始状态。 */
        private void cooperate(Long customerId) {
            var status = new com.xsy.scm.customer.domain.form.CustomerStatusForm();
            status.setCustomerId(customerId);
            status.setVersion(0);
            status.setStatus("COOPERATING");
            customerService.updateStatus(status);
        }

        /** 集团（{@code parentId=null}）或集团成员；成员的上级与结算主体都指向集团。 */
        private com.xsy.scm.customer.domain.form.CustomerAddForm groupForm(Long parentId) {
            var form = new com.xsy.scm.customer.domain.form.CustomerAddForm();
            form.setName(prefix + "-G" + java.util.UUID.randomUUID().toString().substring(0, 6));
            form.setSettleMode("GROUP");
            form.setSellerId(anyEmployeeId());
            form.setParentCustomerId(parentId);
            form.setSettlementCustomerId(parentId);
            return form;
        }

        private record WalletOrder(Long orderId, Long customerId, Long walletId, RequestMallCustomer customer) {
        }

        /**
         * 一张指定结算方式的商城订单。
         *
         * <p>走真实商城链路：开账号 → 登录 → 建地址 → 结算预览 → 下单 → 后台录实重 → 确认。
         * 支付能力只认已确认订单，因此 {@code confirm} 为假时得到的是「待确认」这一侧。
         */
        private WalletOrder mallOrder(String tag, String walletCredit, ScmSettlementMethodEnum settlementMethod,
                boolean confirm) {
            // 建客户与挂商品都要员工数据范围；上一次 signIn 留下的商城身份会在这里被拒，因此先切回后台。
            asAdmin();
            Long customerId = newCustomer();
            jdbc.update("UPDATE xsy_customer SET mall_delivery_warehouse_id=? WHERE id=?", seedWarehouseId(),
                    customerId);
            Long walletId = fundWallet(customerId, walletCredit);
            if (settlementMethod == ScmSettlementMethodEnum.CREDIT) {
                // 这里准备的是后台确认所需额度；客户下单本身不要求授信。
                jdbc.update("UPDATE xsy_customer SET credit_limit = ? WHERE id = ?", new BigDecimal("100.0000"),
                        customerId);
                evictMybatisCache();
            }
            return mallOrderFor(customerId, walletId, tag, settlementMethod, confirm);
        }

        /**
         * 用既有客户走完商城链路下单；客户由调用方准备（例如集团共享结算的成员客户）。
         */
        private WalletOrder mallOrderFor(Long customerId, Long walletId, String tag,
                ScmSettlementMethodEnum settlementMethod, boolean confirm) {
            asAdmin();
            Long skuId = newOnShelfSku("pay-" + tag);
            RequestMallCustomer customer = signIn(openMallAccount(customerId));
            Long addressId = mallAddressService.create(customer, address()).getFirst().getAddressId();
            MallCheckoutPreviewVO preview = mallCheckoutService.preview(customer, previewForm(skuId, addressId));
            MallOrderSubmitForm form = submitForm(skuId, addressId, preview.getPriceFingerprint());
            Long orderId = mallCheckoutService.submit(customer, form, prefix + ":submit:" + tag).getOrderId();
            assertThat(jdbc.queryForObject("SELECT status FROM xsy_sales_order WHERE id=?", String.class, orderId))
                    .isEqualTo("PENDING");
            assertThat(jdbc.queryForObject("SELECT settlement_method FROM xsy_sales_order WHERE id=?", String.class, orderId))
                    .isNull();
            assertThat(paymentIntentCount(orderId)).isZero();
            if (confirm) {
                confirmOrder(orderId, tag, settlementMethod);
            }
            return new WalletOrder(orderId, customerId, walletId, customer);
        }

        /**
         * 预置钱包余额。
         *
         * <p>用一笔 {@code CORRECTION} CREDIT 铺余额而不是走充值链：充值到账有自己的用例，
         * 本类要测的是「钱已经在钱包里之后支付怎么扣」；{@code signedBalance} 取的是流水代数和，因此这笔同样构成真实可用余额。
         */
        private Long fundWallet(Long customerId, String credit) {
            Long walletId = jdbc.queryForObject("""
                    INSERT INTO xsy_customer_balance_account (settlement_customer_id, settlement_customer_name_snapshot)
                    SELECT id, name FROM xsy_customer WHERE id = ? RETURNING id
                    """, Long.class, customerId);
            jdbc.update("""
                    INSERT INTO xsy_customer_balance_movement (movement_no, account_id, settlement_customer_id,
                        customer_id, type, direction, amount, reason, occurred_at)
                    VALUES (?, ?, ?, ?, 'CORRECTION', 'CREDIT', ?, 'PAY-01 IT 预置余额', now())
                    """, prefix + "-CR-" + UUID.randomUUID(), walletId, customerId, customerId, new BigDecimal(credit));
            evictMybatisCache();
            return walletId;
        }

        /** 把钱包里的钱花掉一部分：余额是流水代数和，因此一笔 DEBIT 就足以让支付撞上余额不足。 */
        private void spendWallet(WalletOrder fixture, String amount) {
            jdbc.update("""
                    INSERT INTO xsy_customer_balance_movement (movement_no, account_id, settlement_customer_id,
                        customer_id, type, direction, amount, reason, occurred_at)
                    VALUES (?, ?, ?, ?, 'CORRECTION', 'DEBIT', ?, 'PAY-01 IT 花掉余额', now())
                    """, prefix + "-DB-" + UUID.randomUUID(), fixture.walletId(), fixture.customerId(),
                    fixture.customerId(), new BigDecimal(amount));
            evictMybatisCache();
        }

        private BigDecimal walletBalance(Long walletId) {
            BigDecimal credits = jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM xsy_customer_balance_movement"
                    + " WHERE account_id = ? AND direction = 'CREDIT' AND deleted = FALSE", BigDecimal.class, walletId);
            BigDecimal debits = jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM xsy_customer_balance_movement"
                    + " WHERE account_id = ? AND direction = 'DEBIT' AND deleted = FALSE", BigDecimal.class, walletId);
            return credits.subtract(debits).setScale(4, RoundingMode.HALF_UP);
        }

        /** 后台录实重并确认：实重与下单量不同，才能证明应付是按实重算的。 */
        private void confirmOrder(Long orderId, String tag, ScmSettlementMethodEnum settlementMethod) {
            asAdmin();
            SalesOrderDetailVO order = salesOrderQueryService.detail(orderId);
            OrderActualQuantityForm actual = new OrderActualQuantityForm();
            actual.setOrderId(orderId);
            actual.setItemId(order.getItems().getFirst().getItemId());
            actual.setVersion(order.getItems().getFirst().getVersion());
            actual.setActualQuantity(ACTUAL_QUANTITY);
            actual.setReason("PAY-01 IT 实重");
            SalesOrderDetailVO weighted = salesOrderService.actualQuantity(actual, prefix + ":actual:" + tag);
            OrderConfirmForm confirm = new OrderConfirmForm();
            confirm.setSettlementMethod(settlementMethod.name());
            confirm.setOrderId(orderId);
            confirm.setVersion(weighted.getVersion());
            salesOrderService.confirm(confirm, prefix + ":confirm:" + tag);
            evictMybatisCache();
        }

        private MallOrderVO view(WalletOrder fixture) {
            XsyRequestUtil.setRequestUser(fixture.customer());
            return mallOrderQueryService.detail(fixture.customer(), fixture.orderId());
        }

        private MallOrderVO pay(WalletOrder fixture, String idempotencyKey) {
            asAdmin();
            PaymentIntentCreateForm form = new PaymentIntentCreateForm();
            form.setSourceType(ScmPaymentSourceTypeEnum.SALES_ORDER.name());
            form.setSourceId(fixture.orderId());
            form.setCustomerId(fixture.customerId());
            form.setMethod(ScmPaymentMethodEnum.BALANCE.name());
            form.setProvider(ScmPaymentProviderEnum.INTERNAL_BALANCE.name());
            form.setAmount(PAYABLE);
            paymentIntentService.create(form, idempotencyKey);
            return view(fixture);
        }

        private BigDecimal walletDebits(Long walletId) {
            BigDecimal value = jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM xsy_customer_balance_movement"
                    + " WHERE account_id = ? AND direction = 'DEBIT' AND deleted = FALSE", BigDecimal.class, walletId);
            return value.setScale(4, RoundingMode.HALF_UP);
        }

        private Integer walletMovementCount(Long walletId, String type) {
            return jdbc.queryForObject("SELECT count(*) FROM xsy_customer_balance_movement WHERE account_id = ?"
                    + " AND type = ? AND deleted = FALSE", Integer.class, walletId, type);
        }

        private Integer paymentIntentCount(Long orderId) {
            return jdbc.queryForObject("SELECT count(*) FROM xsy_payment_intent WHERE source_type = 'SALES_ORDER'"
                    + " AND source_id = ? AND deleted = FALSE", Integer.class, orderId);
        }

        private BigDecimal paymentTransactionAmount(Long orderId) {
            return jdbc.queryForObject("SELECT t.amount FROM xsy_payment_transaction t JOIN xsy_payment_intent i"
                    + " ON i.id = t.intent_id WHERE i.source_type = 'SALES_ORDER' AND i.source_id = ?"
                    + " AND t.deleted = FALSE", BigDecimal.class, orderId);
        }

        // ------------------------------------------------------------------
        // 商城身份与后台身份
        // ------------------------------------------------------------------

        /** 登录商城会把请求身份换成商城客户，后台命令前必须切回员工身份。 */
        private void asAdmin() {
            RequestEmployee employee = new RequestEmployee();
            employee.setEmployeeId(1L);
            employee.setActualName("PAY-01 IT");
            employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
            employee.setAdministratorFlag(true);
            XsyRequestUtil.setRequestUser(employee);
        }

        private MallAccount openMallAccount(Long customerId) {
            String phone = newPhone();
            MallAccountCreateForm form = new MallAccountCreateForm();
            form.setCustomerId(customerId);
            form.setLoginPhone(phone);
            form.setTemporaryPassword(TEMPORARY_PASSWORD);
            MallAccountVO account = mallAccountService.create(form);
            return new MallAccount(account.getAccountId(), phone);
        }

        private RequestMallCustomer signIn(MallAccount account) {
            MallLoginForm login = new MallLoginForm();
            login.setPhone(account.phone());
            login.setPassword(TEMPORARY_PASSWORD);
            try (MockedStatic<StpUtil> ignored = mockStatic(StpUtil.class)) {
                mallAuthService.login(login, "127.0.0.1");
                MallPasswordChangeForm change = new MallPasswordChangeForm();
                change.setOldPassword(TEMPORARY_PASSWORD);
                change.setNewPassword(NEW_PASSWORD);
                mallAuthService.changePassword(requestCustomer(account, 0), change);
                login.setPassword(NEW_PASSWORD);
                mallAuthService.login(login, "127.0.0.1");
            }
            return requestCustomer(account, 1);
        }

        private RequestMallCustomer requestCustomer(MallAccount account, int version) {
            RequestMallCustomer customer = mallAuthService.requestCustomer(
                    MallAuthService.loginId(account.accountId(), version), new MockHttpServletRequest());
            assertThat(customer).as("登录后必须能解析出商城客户身份").isNotNull();
            return customer;
        }

        private static MallAddressSaveForm address() {
            MallAddressSaveForm form = new MallAddressSaveForm();
            form.setReceiverName("余额支付收货人");
            form.setReceiverPhone("13800000000");
            form.setProvinceCode(310000);
            form.setCityCode(310100);
            form.setDistrictCode(310115);
            form.setProvinceName("上海市");
            form.setCityName("上海市");
            form.setDistrictName("浦东新区");
            form.setDetailAddress("张江路 2 号");
            form.setIsDefault(true);
            form.setLongitude(new BigDecimal("121.544000"));
            form.setLatitude(new BigDecimal("31.221000"));
            return form;
        }

        private static MallCheckoutPreviewForm previewForm(Long skuId, Long addressId) {
            MallCheckoutPreviewForm form = new MallCheckoutPreviewForm();
            form.setItems(List.of(checkoutItem(skuId, ORDERED_QUANTITY)));
            form.setAddressId(addressId);
            return form;
        }

        private MallOrderSubmitForm submitForm(Long skuId, Long addressId, String priceFingerprint) {
            MallOrderSubmitForm form = new MallOrderSubmitForm();
            form.setItems(List.of(checkoutItem(skuId, ORDERED_QUANTITY)));
            form.setAddressId(addressId);
            form.setWarehouseId(seedWarehouseId());
            form.setPriceFingerprint(priceFingerprint);
            form.setRemark("PAY-01 IT");
            return form;
        }

        private static MallCheckoutItemForm checkoutItem(Long skuId, String quantity) {
            MallCheckoutItemForm item = new MallCheckoutItemForm();
            item.setSkuId(skuId);
            item.setQuantity(quantity);
            return item;
        }

        private static String newPhone() {
            long suffix = Math.abs(UUID.randomUUID().getMostSignificantBits() % 900_000_000L);
            return "13" + (100_000_000L + suffix);
        }

        private record MallAccount(Long accountId, String phone) {
        }
    }
}
