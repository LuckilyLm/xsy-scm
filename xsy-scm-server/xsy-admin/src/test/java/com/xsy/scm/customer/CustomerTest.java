package com.xsy.scm.customer;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.xsy.scm.admin.module.system.login.domain.RequestEmployee;
import com.xsy.scm.balance.domain.form.BalanceCorrectionForm;
import com.xsy.scm.balance.service.CustomerBalanceService;
import com.xsy.scm.base.common.domain.PageParam;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.SystemEnvironment;
import com.xsy.scm.base.common.enumeration.UserTypeEnum;
import com.xsy.scm.base.common.exception.BusinessException;
import com.xsy.scm.base.common.util.XsyRequestUtil;
import com.xsy.scm.base.handler.GlobalExceptionHandler;
import com.xsy.scm.businesscategory.domain.entity.BusinessCategoryEntity;
import com.xsy.scm.businesscategory.domain.form.BusinessCategoryAddForm;
import com.xsy.scm.businesscategory.domain.form.BusinessCategoryUpdateForm;
import com.xsy.scm.businesscategory.service.BusinessCategoryService;
import com.xsy.scm.common.ScmW2PgITBase;
import com.xsy.scm.common.ScmW3PgITBase;
import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.handler.ScmExceptionHandler;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.customer.controller.CustomerController;
import com.xsy.scm.customer.dao.CustomerDao;
import com.xsy.scm.customer.dao.CustomerGroupDao;
import com.xsy.scm.customer.domain.entity.CustomerEntity;
import com.xsy.scm.customer.domain.form.CustomerAddForm;
import com.xsy.scm.customer.domain.form.CustomerDeleteForm;
import com.xsy.scm.customer.domain.form.CustomerQueryForm;
import com.xsy.scm.customer.domain.form.CustomerSkuVisibilityItemForm;
import com.xsy.scm.customer.domain.form.CustomerStatusForm;
import com.xsy.scm.customer.domain.form.CustomerUpdateForm;
import com.xsy.scm.customer.domain.form.CustomerVisibilityReplaceForm;
import com.xsy.scm.customer.domain.vo.CustomerDetailVO;
import com.xsy.scm.customer.domain.vo.CustomerFrequentSkuVO;
import com.xsy.scm.customer.domain.vo.CustomerOptionVO;
import com.xsy.scm.customer.domain.vo.CustomerPromotionVO;
import com.xsy.scm.customer.domain.vo.CustomerVO;
import com.xsy.scm.customer.manager.CustomerValidator;
import com.xsy.scm.customer.service.CustomerGroupService;
import com.xsy.scm.customer.service.CustomerPromotionService;
import com.xsy.scm.customer.service.CustomerQueryService;
import com.xsy.scm.customer.service.CustomerService;
import com.xsy.scm.customer.service.CustomerSkuAliasService;
import com.xsy.scm.order.domain.form.OrderActualQuantityForm;
import com.xsy.scm.order.domain.form.OrderAddressForm;
import com.xsy.scm.order.domain.form.OrderCancelForm;
import com.xsy.scm.order.domain.form.OrderVersionForm;
import com.xsy.scm.order.domain.form.SalesOrderAddForm;
import com.xsy.scm.order.domain.form.SalesOrderItemForm;
import com.xsy.scm.order.domain.vo.SalesOrderDetailVO;
import com.xsy.scm.order.service.SalesOrderService;
import com.xsy.scm.payment.domain.form.PaymentRefundQueryForm;
import com.xsy.scm.payment.service.PaymentQueryService;
import com.xsy.scm.supplier.domain.form.SupplierAddForm;
import com.xsy.scm.supplier.service.SupplierService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static com.xsy.scm.customer.constant.CustomerErrorCode.CUSTOMER_NOT_FOUND;
import static com.xsy.scm.customer.constant.CustomerErrorCode.CUSTOMER_NOT_TRADABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CustomerTest {

    @Nested
    @WebMvcTest(CustomerController.class)
    @AutoConfigureMockMvc(addFilters = false)
    @ContextConfiguration(classes = {CustomerController.class, ScmExceptionHandler.class, GlobalExceptionHandler.class})
    class CustomerControllerTest {
        @Autowired
        private MockMvc mvc;

        @MockitoBean
        private CustomerService service;

        @MockitoBean
        private CustomerQueryService queryService;

        @MockitoBean
        private CustomerSkuAliasService customerSkuAliasService;

        @MockitoBean(name = "systemEnvironment")
        private SystemEnvironment environment;

        private static PageResult<CustomerVO> emptyPage() {
            PageResult<CustomerVO> page = new PageResult<>();
            page.setPageNum(1L);
            page.setPageSize(20L);
            page.setTotal(0L);
            page.setPages(0L);
            page.setList(List.of());
            page.setEmptyFlag(true);
            return page;
        }

        @Test
        @DisplayName("列表返回 XSY 分页信封")
        void returnsPaginationEnvelope() throws Exception {
            when(queryService.query(any())).thenReturn(emptyPage());

            mvc.perform(post("/scm/customer/query").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"pageNum\":1,\"pageSize\":20}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.ok").value(true))
                    .andExpect(jsonPath("$.data.list").isArray())
                    .andExpect(jsonPath("$.data.pageNum").value(1))
                    .andExpect(jsonPath("$.data.emptyFlag").value(true));
        }

        @Test
        @DisplayName("分页边界：缺 pageNum / pageSize=101 都被参数校验拦下")
        void validatesPageBounds() throws Exception {
            for (String body : List.of(
                    "{\"pageSize\":20}",
                    "{\"pageNum\":0,\"pageSize\":20}",
                    "{\"pageNum\":1,\"pageSize\":101}")) {
                mvc.perform(post("/scm/customer/query").contentType(MediaType.APPLICATION_JSON).content(body))
                        .andExpect(jsonPath("$.code").value(30001));
            }
        }

        @Test
        @DisplayName("缺少 version 的删除请求被拦下")
        void requiresVersionOnDelete() throws Exception {
            mvc.perform(post("/scm/customer/delete").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"customerId\":1}"))
                    .andExpect(jsonPath("$.code").value(30001));
        }

        @Test
        @DisplayName("客户不存在 → 40430")
        void mapsNotFound() throws Exception {
            when(queryService.detail(1L)).thenThrow(new ScmBusinessException(CUSTOMER_NOT_FOUND));

            mvc.perform(get("/scm/customer/detail/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40430))
                    .andExpect(jsonPath("$.ok").value(false));
        }

        @Test
        @DisplayName("乐观锁冲突 → 40921，且保持 HTTP 200")
        void mapsVersionConflict() throws Exception {
            doThrow(new ScmBusinessException(VERSION_CONFLICT)).when(service).delete(any());

            mvc.perform(post("/scm/customer/delete").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"customerId\":1,\"version\":0}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40921))
                    .andExpect(jsonPath("$.ok").value(false));
        }

        @Test
        @DisplayName("新增客户成功返回新客户 id 信封（编码由服务端生成，不再由表单提交）")
        void createsCustomer() throws Exception {
            when(service.add(any())).thenReturn(42L);

            mvc.perform(post("/scm/customer/add").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"测试\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.ok").value(true))
                    .andExpect(jsonPath("$.data").value(42));
        }

        @Test
        @DisplayName("SCM 业务码原样透出（以 40930 不可交易为例），不被降级为系统错误码")
        void passesThroughArbitraryScmCode() throws Exception {
            when(queryService.detail(9L)).thenThrow(new ScmBusinessException(CUSTOMER_NOT_TRADABLE));

            mvc.perform(get("/scm/customer/detail/9"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40930))
                    .andExpect(jsonPath("$.msg").value("客户状态不可交易"));
        }

        @Test
        @DisplayName("同码补充文案原样返回，前端可展示具体客户状态")
        void passesThroughDetailMessage() throws Exception {
            when(queryService.detail(9L))
                    .thenThrow(new ScmBusinessException(CUSTOMER_NOT_TRADABLE, "客户当前状态为黑名单，不可交易"));

            mvc.perform(get("/scm/customer/detail/9"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40930))
                    .andExpect(jsonPath("$.msg").value("客户当前状态为黑名单，不可交易"));
        }

        @Test
        @DisplayName("下拉接口返回数组信封")
        void returnsOptionList() throws Exception {
            when(queryService.optionList()).thenReturn(List.of());

            mvc.perform(post("/scm/customer/option/list").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").isArray());
        }

        @Test
        @DisplayName("非 SCM 异常仍交给 XSY 全局处理器（不抢别人的异常）")
        void leavesNonScmExceptionsToGlobalHandler() throws Exception {
            when(environment.isProd()).thenReturn(true);
            when(queryService.detail(1L)).thenThrow(new BusinessException("system module error"));

            mvc.perform(get("/scm/customer/detail/1"))
                    .andExpect(jsonPath("$.code").value(10001))
                    .andExpect(jsonPath("$.msg").value("system module error"));

            when(queryService.detail(2L)).thenThrow(new IllegalStateException("private details"));

            mvc.perform(get("/scm/customer/detail/2"))
                    .andExpect(jsonPath("$.code").value(10001));
        }
    }

    @Nested
    class CustomerFrequentSkuIT extends ScmW3PgITBase {
        @Autowired
        SalesOrderService orders;

        @Autowired
        CustomerService customers;

        @Autowired
        CustomerQueryService customerQuery;

        private Long customer(String sfx) {
            var f = new CustomerAddForm();
            f.setName(prefix + sfx);
            f.setSettleMode("INDEPENDENT");
            f.setSellerId(anyEmployeeId());
            Long id = customers.add(f);
            var s = new CustomerStatusForm();
            s.setCustomerId(id);
            s.setVersion(0);
            s.setStatus("COOPERATING");
            customers.updateStatus(s);
            return id;
        }

        /** 单行下单表单；manual=true 时锁 9.0000 手改价，否则走市场价 1.2000。 */
        private SalesOrderAddForm form(Long c, Long sku, boolean manual) {
            var f = new SalesOrderAddForm();
            f.setCustomerId(c);
            f.setOrderSource("ADMIN");
            var a = new OrderAddressForm();
            a.setReceiverName("测试客户");
            a.setReceiverPhone("13800000000");
            a.setAddress("测试地址");
            f.setAddress(a);
            var i = new SalesOrderItemForm();
            i.setSkuId(sku);
            i.setOrderedQuantity("2.0000");
            if (manual) {
                i.setManualPriceOverride(true);
                i.setUnitPrice("9.0000");
                i.setOverrideReason("协议改价");
            } else {
                i.setManualPriceOverride(false);
            }
            f.setItems(new ArrayList<>(List.of(i)));
            return f;
        }

        private OrderVersionForm version(SalesOrderDetailVO o) {
            var f = new OrderVersionForm();
            f.setOrderId(o.getOrderId());
            f.setVersion(o.getVersion());
            return f;
        }

        /** 新建即停在 PENDING（已锁价但未确认）。 */
        private SalesOrderDetailVO submitted(Long c, Long sku, String tag) {
            return orders.create(form(c, sku, false), prefix + tag + "c");
        }

        /** 新建 → 录实重 → confirm，得到带锁定单价的 CONFIRMED 单。 */
        private SalesOrderDetailVO confirmed(Long c, Long sku, String tag, boolean manual) {
            var o = orders.create(form(c, sku, manual), prefix + tag + "c");
            var a = new OrderActualQuantityForm();
            a.setOrderId(o.getOrderId());
            a.setItemId(o.getItems().getFirst().getItemId());
            a.setVersion(o.getItems().getFirst().getVersion());
            a.setActualQuantity("1.5000");
            a.setReason("实际称重");
            o = orders.actualQuantity(a, prefix + tag + "a");
            return orders.confirm(version(o), prefix + tag + "f");
        }

        @Test
        void aggregatesConfirmedCountAndQuantityAndTakesLatestPrice() {
            Long c = customer("A");
            Long sku = newOnShelfSku("A");

            // 两张已确认单：先 1.2000（市场），后 9.0000（手改）；同事务内 confirmed_at 相同，靠 order_id DESC 定最新
            confirmed(c, sku, "1", false);
            confirmed(c, sku, "2", true);

            List<CustomerFrequentSkuVO> rows = customerQuery.frequentSkus(c, 90, 20);
            assertThat(rows).hasSize(1);
            CustomerFrequentSkuVO row = rows.getFirst();
            assertThat(row.getSkuId()).isEqualTo(sku);
            assertThat(row.getUnit()).isEqualTo("kg");
            // 订单次数 = 两张已确认单；订购量 = 2.0000 + 2.0000，不是实重 1.5×2
            assertThat(row.getOrderCount()).isEqualTo(2L);
            assertThat(row.getOrderedQuantity()).isEqualByComparingTo("4.0000");
            // 最近单价取最新一张（9.0000），既非平均 5.1 也非最早 1.2
            assertThat(row.getRecentUnitPrice()).isEqualByComparingTo("9.0000");
            assertThat(row.getSkuCode()).isEqualTo(skuCodeOf(sku));
            assertThat(row.getProductName()).isEqualTo(prefix + "A商品");
            assertThat(row.getLastConfirmedAt()).isNotNull();
        }

        @Test
        void excludesDraftPendingAndCancelled() {
            Long c = customer("B");
            Long sku = newOnShelfSku("B");

            confirmed(c, sku, "cf", false);
            orders.create(form(c, sku, false), prefix + "draft");
            submitted(c, sku, "pending");
            var toCancel = submitted(c, sku, "tc");
            var cancel = new OrderCancelForm();
            cancel.setOrderId(toCancel.getOrderId());
            cancel.setVersion(toCancel.getVersion());
            cancel.setReason("客户取消");
            orders.cancel(cancel, prefix + "cx");

            // 四张单里只有 CONFIRMED 计入：次数 1、订购量 2.0000
            List<CustomerFrequentSkuVO> rows = customerQuery.frequentSkus(c, 90, 20);
            assertThat(rows).hasSize(1);
            assertThat(rows.getFirst().getOrderCount()).isEqualTo(1L);
            assertThat(rows.getFirst().getOrderedQuantity()).isEqualByComparingTo("2.0000");
        }

        @Test
        void limitReturnsHighestFrequencyFirst() {
            Long c = customer("C");
            Long hot = newOnShelfSku("C1");
            Long cold = newOnShelfSku("C2");
            confirmed(c, hot, "h1", false);
            confirmed(c, hot, "h2", false);
            confirmed(c, cold, "c1", false);

            // limit=1 只保留频次最高的 SKU（2 次）
            assertThat(customerQuery.frequentSkus(c, 90, 1))
                    .extracting(CustomerFrequentSkuVO::getSkuId).containsExactly(hot);
            // limit=2 按频次降序：hot(2) 在 cold(1) 之前
            assertThat(customerQuery.frequentSkus(c, 90, 2))
                    .extracting(CustomerFrequentSkuVO::getSkuId).containsExactly(hot, cold);
        }

        @Test
        void onlyCountsConfirmationsWithinDayWindow() {
            Long c = customer("D");
            Long sku = newOnShelfSku("D");
            SalesOrderDetailVO o = confirmed(c, sku, "d", false);
            // 把确认时间推到 200 天前，落在 90 天窗口外、365 天窗口内
            jdbc.update("UPDATE xsy_sales_order SET confirmed_at = now() - interval '200 days' WHERE id = ?",
                    o.getOrderId());

            assertThat(customerQuery.frequentSkus(c, 90, 20)).isEmpty();
            assertThat(customerQuery.frequentSkus(c, 365, 20)).hasSize(1);
        }

        @Test
        void nullLockedPriceHasNoFallback() {
            Long c = customer("E");
            Long sku = newOnShelfSku("E");
            SalesOrderDetailVO o = confirmed(c, sku, "e", false);
            jdbc.update("UPDATE xsy_sales_order_item SET locked_unit_price = NULL WHERE order_id = ?",
                    o.getOrderId());

            List<CustomerFrequentSkuVO> rows = customerQuery.frequentSkus(c, 90, 20);
            assertThat(rows).hasSize(1);
            // 锁定单价缺失 → null，不用市场价 / 草稿价兜底；订购量与次数不受影响
            assertThat(rows.getFirst().getRecentUnitPrice()).isNull();
            assertThat(rows.getFirst().getOrderedQuantity()).isEqualByComparingTo("2.0000");
            assertThat(rows.getFirst().getOrderCount()).isEqualTo(1L);
        }

        @Test
        void rejectsUnknownCustomer() {
            Long absent = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) + 1 FROM xsy_customer", Long.class);
            expectCode(() -> customerQuery.frequentSkus(absent, 90, 20), 40430);
        }
    }

    @Nested
    class CustomerUnitTest {
        @Nested

        class CustomerGroupServiceTest {
            private final CustomerGroupDao groups = mock(CustomerGroupDao.class);

            private final CustomerDao customers = mock(CustomerDao.class);

            private final CustomerGroupService service = new CustomerGroupService(groups, customers);

            @Test
            void movingOnlyMemberOutOfGroupIsRejected() {
                when(customers.selectEmptiedGroupIds(List.of(3L), 9L)).thenReturn(List.of(7L));

                assertThatThrownBy(() -> service.assertCanMove(List.of(3L), 9L))
                    .isInstanceOf(ScmBusinessException.class)
                    .hasMessageContaining("原分组");
            }

            @Test
            void movingMemberThatLeavesOthersBehindIsAllowed() {
                when(customers.selectEmptiedGroupIds(List.of(3L), 9L)).thenReturn(List.of());

                assertThatCode(() -> service.assertCanMove(List.of(3L), 9L)).doesNotThrowAnyException();
            }

            @Test
            void emptyMoveDoesNotQuery() {
                service.assertCanMove(List.of(), 9L);

                verifyNoInteractions(customers);
            }
        }

        @Nested

        class CustomerValidatorTest {
            private final CustomerDao customerDao = mock(CustomerDao.class);

            private final CustomerValidator validator = new CustomerValidator(customerDao);

            private static int codeOf(Throwable t) {
                return ((ScmBusinessException) t).getErrorCode().getCode();
            }

            private static CustomerAddForm baseForm() {
                CustomerAddForm form = new CustomerAddForm();
                form.setName("  测试客户  ");
                form.setSettleMode("INDEPENDENT");
                return form;
            }

            // ------------------------------------------------------------------
            // 账期组合
            // ------------------------------------------------------------------

            @Test
            @DisplayName("不设置账期：五个账期字段全空 → 通过")
            void noCreditPeriodPasses() {
                assertThatCode(() -> validator.validateCreditPeriod(baseForm())).doesNotThrowAnyException();
            }

            @Test
            @DisplayName("不设置账期但带了阈值 → 拒绝")
            void absentTypeWithThresholdRejected() {
                CustomerAddForm form = baseForm();
                form.setCreditAmountThreshold("100");
                assertThatThrownBy(() -> validator.validateCreditPeriod(form))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40000));
            }

            @Test
            @DisplayName("按金额：缺阈值 → 拒绝")
            void byAmountWithoutThresholdRejected() {
                CustomerAddForm form = baseForm();
                form.setCreditPeriodType("BY_AMOUNT");
                assertThatThrownBy(() -> validator.validateCreditPeriod(form))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40000));
            }

            @Test
            @DisplayName("按金额：带阈值、其余账期字段为空 → 通过")
            void byAmountWithThresholdPasses() {
                CustomerAddForm form = baseForm();
                form.setCreditPeriodType("BY_AMOUNT");
                form.setCreditAmountThreshold("10000.5000");
                assertThatCode(() -> validator.validateCreditPeriod(form)).doesNotThrowAnyException();
            }

            @Test
            @DisplayName("按金额：同时带账期值 → 拒绝（两种形态互斥）")
            void byAmountWithPeriodValueRejected() {
                CustomerAddForm form = baseForm();
                form.setCreditPeriodType("BY_AMOUNT");
                form.setCreditAmountThreshold("100");
                form.setCreditPeriodValue(5);
                assertThatThrownBy(() -> validator.validateCreditPeriod(form))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40000));
            }

            @Test
            @DisplayName("按时间·天：带结算日 → 拒绝")
            void byTimeDayWithSettleDayRejected() {
                CustomerAddForm form = baseForm();
                form.setCreditPeriodType("BY_TIME");
                form.setCreditPeriodValue(30);
                form.setCreditPeriodUnit("DAY");
                form.setSettleDay(5);
                assertThatThrownBy(() -> validator.validateCreditPeriod(form))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40000));
            }

            @Test
            @DisplayName("按时间·天：不带结算日 → 通过")
            void byTimeDayWithoutSettleDayPasses() {
                CustomerAddForm form = baseForm();
                form.setCreditPeriodType("BY_TIME");
                form.setCreditPeriodValue(30);
                form.setCreditPeriodUnit("DAY");
                assertThatCode(() -> validator.validateCreditPeriod(form)).doesNotThrowAnyException();
            }

            @Test
            @DisplayName("按时间·月：结算日 1 与 28 通过，0 与 29 拒绝，缺省通过")
            void byTimeMonthSettleDayBounds() {
                CustomerAddForm ok1 = monthForm(1);
                CustomerAddForm ok28 = monthForm(28);
                CustomerAddForm okAbsent = monthForm(null);
                CustomerAddForm bad0 = monthForm(0);
                CustomerAddForm bad29 = monthForm(29);

                assertThatCode(() -> validator.validateCreditPeriod(ok1)).doesNotThrowAnyException();
                assertThatCode(() -> validator.validateCreditPeriod(ok28)).doesNotThrowAnyException();
                assertThatCode(() -> validator.validateCreditPeriod(okAbsent)).doesNotThrowAnyException();
                assertThatThrownBy(() -> validator.validateCreditPeriod(bad0))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40000));
                assertThatThrownBy(() -> validator.validateCreditPeriod(bad29))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40000));
            }

            @Test
            @DisplayName("按时间：账期值必须为正")
            void byTimeRequiresPositiveValue() {
                CustomerAddForm form = monthForm(null);
                form.setCreditPeriodValue(0);
                assertThatThrownBy(() -> validator.validateCreditPeriod(form))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40000));
            }

            @Test
            @DisplayName("按时间：缺单位 → 拒绝")
            void byTimeRequiresUnit() {
                CustomerAddForm form = baseForm();
                form.setCreditPeriodType("BY_TIME");
                form.setCreditPeriodValue(30);
                assertThatThrownBy(() -> validator.validateCreditPeriod(form))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40000));
            }

            @Test
            @DisplayName("未知账期类型 → 拒绝")
            void unknownCreditPeriodTypeRejected() {
                CustomerAddForm form = baseForm();
                form.setCreditPeriodType("BY_WHATEVER");
                assertThatThrownBy(() -> validator.validateCreditPeriod(form))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40000));
            }

            private static CustomerAddForm monthForm(Integer settleDay) {
                CustomerAddForm form = baseForm();
                form.setCreditPeriodType("BY_TIME");
                form.setCreditPeriodValue(1);
                form.setCreditPeriodUnit("MONTH");
                form.setSettleDay(settleDay);
                return form;
            }

            // ------------------------------------------------------------------
            // 上级客户关系
            // ------------------------------------------------------------------

            @Test
            @DisplayName("上级客户 = 自身 → 40032")
            void parentEqualsSelfRejected() {
                assertThatThrownBy(() -> validator.validateParent(9L, 9L))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40032));
            }

            @Test
            @DisplayName("上级客户不存在 → 40430")
            void parentMissingRejected() {
                when(customerDao.selectById(7L)).thenReturn(null);
                assertThatThrownBy(() -> validator.validateParent(7L, 1L))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40430));
            }

            @Test
            @DisplayName("任意存在的客户都能作为上级 → 通过")
            void anyExistingParentPasses() {
                when(customerDao.selectById(7L)).thenReturn(customer(7L));
                assertThatCode(() -> validator.validateParent(7L, 1L)).doesNotThrowAnyException();
            }

            @Test
            @DisplayName("上溯成环（祖先等于自身）→ 40032")
            void parentCycleBackToSelfRejected() {
                when(customerDao.selectById(7L)).thenReturn(customer(7L, 1L));
                assertThatThrownBy(() -> validator.validateParent(7L, 1L))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40032));
            }

            @Test
            @DisplayName("上溯超过最大深度（脏数据环）→ 40032")
            void parentDepthLimitRejected() {
                // 构造 1 → 2 → 3 → ... 的长链，永不收敛
                for (long id = 1; id <= CustomerValidator.MAX_PARENT_DEPTH + 5; id++) {
                    when(customerDao.selectById(id)).thenReturn(customer(id, id + 1));
                }
                assertThatThrownBy(() -> validator.validateParent(1L, null))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40032));
            }

            @Test
            @DisplayName("独立客户（无上级）→ 直接通过，不查库")
            void independentCustomerPasses() {
                assertThatCode(() -> validator.validateParent(null, 1L)).doesNotThrowAnyException();
            }

            // ------------------------------------------------------------------
            // 归一化
            // ------------------------------------------------------------------

            @Test
            @DisplayName("编码归一化：trim + upper")
            void normalizesCode() {
                assertThat(CustomerValidator.normalizeCode("  kh001  ")).isEqualTo("KH001");
                assertThat(CustomerValidator.normalizeCode("Kh-001")).isEqualTo("KH-001");
                assertThat(CustomerValidator.normalizeCode(null)).isNull();
            }

            @Test
            @DisplayName("名称归一化：仅 trim，保留大小写")
            void normalizesName() {
                assertThat(CustomerValidator.normalizeName("  张三蔬菜  ")).isEqualTo("张三蔬菜");
                assertThat(CustomerValidator.normalizeName("Acme Foods")).isEqualTo("Acme Foods");
                assertThat(CustomerValidator.normalizeName(null)).isNull();
            }

            @Test
            @DisplayName("可选文本归一化：空白 → null，以便真正清空列")
            void normalizesOptionalToNull() {
                assertThat(CustomerValidator.normalizeOptional("   ")).isNull();
                assertThat(CustomerValidator.normalizeOptional("")).isNull();
                assertThat(CustomerValidator.normalizeOptional(null)).isNull();
                assertThat(CustomerValidator.normalizeOptional(" 13800000000 ")).isEqualTo("13800000000");
            }

            private static CustomerEntity customer(Long id) {
                return customer(id, null);
            }

            private static CustomerEntity customer(Long id, Long parentId) {
                CustomerEntity entity = new CustomerEntity();
                entity.setId(id);
                entity.setParentCustomerId(parentId);
                return entity;
            }
        }
    }

    @Nested
    class CustomerW2PgIT extends ScmW2PgITBase {
        @Nested
        @DisplayName("客户：读路径与补全（PG IT）")
        class CustomerQueryServiceIT {
            @Autowired
            private CustomerService service;

            @Autowired
            private CustomerQueryService queryService;

            @Autowired
            private SupplierService supplierService;

            private CustomerAddForm form(String suffix) {
                CustomerAddForm form = new CustomerAddForm();
                form.setName("客户" + suffix);
                // 编码由服务端生成、不再携带测试前缀；用联系人字段承载前缀，保证 keyword 查询能收敛到本用例的数据
                form.setContactName("联系人" + prefix);
                return form;
            }

            private CustomerQueryForm queryForm() {
                CustomerQueryForm form = new CustomerQueryForm();
                form.setPageNum(1L);
                form.setPageSize(50L);
                form.setKeyword(prefix);
                return form;
            }

            @Test
            @DisplayName("关键字覆盖编码 / 名称 / 联系人 / 联系电话")
            void keywordCoversCodeNameContactAndPhone() {
                CustomerAddForm form = form("K1");
                form.setContactName(prefix + "CONTACT-NAME");
                form.setContactPhone("13800001111");
                Long id = service.add(form);
                String generatedCode = jdbc.queryForObject(
                        "SELECT customer_code FROM xsy_customer WHERE id = ?", String.class, id);

                for (String keyword : List.of("客户K1", generatedCode, prefix + "CONTACT-NAME", "13800001111")) {
                    CustomerQueryForm query = queryForm();
                    query.setKeyword(keyword);
                    assertThat(queryService.query(query).getList())
                            .as("关键字 %s 必须命中新建客户", keyword)
                            .extracting(CustomerVO::getCustomerId).contains(id);
                }
            }

            @Test
            @DisplayName("状态 / 结算方式 / 上级客户筛选都生效")
            void filtersAreApplied() {
                Long group = service.add(groupForm("G1"));
                CustomerAddForm childForm = form("F1");
                childForm.setParentCustomerId(group);
                Long child = service.add(childForm);

                // 新建客户默认都是合作中，先把集团停用，状态筛选才有区分度。
                CustomerStatusForm groupStatus = new CustomerStatusForm();
                groupStatus.setCustomerId(group);
                groupStatus.setVersion(0);
                groupStatus.setStatus("SUSPENDED");
                service.updateStatus(groupStatus);

                CustomerQueryForm byStatus = queryForm();
                byStatus.setStatus("COOPERATING");
                assertThat(queryService.query(byStatus).getList()).extracting(CustomerVO::getCustomerId)
                        .containsExactly(child);

                CustomerQueryForm bySettleMode = queryForm();
                bySettleMode.setSettleMode("GROUP");
                assertThat(queryService.query(bySettleMode).getList()).extracting(CustomerVO::getCustomerId)
                        .containsExactly(group);

                CustomerQueryForm byParent = queryForm();
                byParent.setParentCustomerId(group);
                assertThat(queryService.query(byParent).getList()).extracting(CustomerVO::getCustomerId)
                        .containsExactly(child);
            }

            private CustomerAddForm groupForm(String suffix) {
                CustomerAddForm form = new CustomerAddForm();
                form.setName("集团" + suffix);
                form.setContactName("联系人" + prefix);
                form.setSettleMode("GROUP");
                return form;
            }

            @Test
            @DisplayName("列表与详情批量补全上级客户名 / 业务员名 / 供应商名")
            void enrichmentResolvesNamesFromRelatedTables() {
                Long group = service.add(groupForm("G2"));
                Long employeeId = anyEmployeeId();

                SupplierAddForm supplierForm = new SupplierAddForm();
                supplierForm.setName("供应商" + prefix);
                Long supplierId = supplierService.add(supplierForm);

                CustomerAddForm form = form("E1");
                form.setParentCustomerId(group);
                form.setSellerId(employeeId);
                form.setSupplierId(supplierId);
                Long id = service.add(form);

                CustomerVO row = queryService.query(queryForm()).getList().stream()
                        .filter(vo -> vo.getCustomerId().equals(id)).findFirst().orElseThrow();
                assertThat(row.getParentCustomerName()).isEqualTo("集团G2");
                assertThat(row.getSellerName()).isNotBlank();

                CustomerDetailVO detail = queryService.detail(id);
                assertThat(detail.getParentCustomerName()).isEqualTo("集团G2");
                assertThat(detail.getSellerName()).isNotBlank();
                assertThat(detail.getSupplierName()).isEqualTo("供应商" + prefix);
            }

            @Test
            @DisplayName("客户分类名称批量补全，并可按分类筛选")
            void businessCategoryNameIsEnrichedAndFilterable() {
                Long categoryId = jdbc.queryForObject(
                    "SELECT id FROM xsy_business_category WHERE scope = 'CUSTOMER' AND category_code = 'CUSTOMER_EGG'",
                    Long.class);
                CustomerAddForm form = form("BC");
                form.setBusinessCategoryId(categoryId);
                Long id = service.add(form);

                CustomerQueryForm byCategory = queryForm();
                byCategory.setBusinessCategoryId(categoryId);
                CustomerVO row = queryService.query(byCategory).getList().stream()
                    .filter(vo -> vo.getCustomerId().equals(id)).findFirst().orElseThrow();
                assertThat(row.getBusinessCategoryId()).isEqualTo(categoryId);
                assertThat(row.getBusinessCategoryName()).isEqualTo("蛋");

                CustomerDetailVO detail = queryService.detail(id);
                assertThat(detail.getBusinessCategoryId()).isEqualTo(categoryId);
                assertThat(detail.getBusinessCategoryName()).isEqualTo("蛋");

                Long otherCategoryId = jdbc.queryForObject(
                    "SELECT id FROM xsy_business_category WHERE scope = 'CUSTOMER' AND category_code = 'CUSTOMER_MEAT'",
                    Long.class);
                CustomerQueryForm otherCategory = queryForm();
                otherCategory.setBusinessCategoryId(otherCategoryId);
                assertThat(queryService.query(otherCategory).getList()).extracting(CustomerVO::getCustomerId)
                    .doesNotContain(id);
            }

            @Test
            @DisplayName("金额约定：授信额度序列化为 4 位小数字符串，未设置的账期阈值保持 null")
            void decimalContractSurvivesSerialization() throws Exception {
                CustomerAddForm form = form("J1");
                form.setCreditLimit("1234.5");
                Long id = service.add(form);

                JsonNode detail = objectMapper.readTree(objectMapper.writeValueAsString(queryService.detail(id)));
                assertThat(detail.get("creditLimit").asText()).isEqualTo("1234.5000");
                assertThat(detail.get("creditAmountThreshold").isNull())
                        .as("null 不得被序列化成 0.0000").isTrue();

                CustomerAddForm withThreshold = form("J2");
                withThreshold.setCreditPeriodType("BY_AMOUNT");
                withThreshold.setCreditAmountThreshold("0");
                Long thresholdId = service.add(withThreshold);
                JsonNode threshold = objectMapper.readTree(objectMapper.writeValueAsString(queryService.detail(thresholdId)));
                assertThat(threshold.get("creditAmountThreshold").asText()).isEqualTo("0.0000");
            }

            @Test
            @DisplayName("详情不存在 → 40430；下拉返回授权范围内的活动客户并带状态")
            void detailMissingAndOptionList() {
                expectCode(() -> queryService.detail(-1L), 40430);

                Long id = service.add(form("O1"));
                List<CustomerOptionVO> options = queryService.optionList();
                CustomerOptionVO mine = options.stream()
                        .filter(vo -> vo.getCustomerId().equals(id)).findFirst().orElseThrow();
                assertThat(mine.getStatus()).isEqualTo("COOPERATING");
                assertThat(mine.getCustomerCode()).matches("^CUS\\d{6,}$");
            }

            @Test
            @DisplayName("排序白名单：合法列通过，未知列 40000")
            void sortWhitelistIsEnforced() {
                Long id = service.add(form("S1"));

                PageParam.SortItem legal = new PageParam.SortItem();
                legal.setIsAsc(false);
                legal.setColumn("updated_at");
                CustomerQueryForm good = queryForm();
                good.setSortItemList(List.of(legal));
                assertThat(queryService.query(good).getList()).extracting(CustomerVO::getCustomerId).contains(id);

                PageParam.SortItem illegal = new PageParam.SortItem();
                illegal.setIsAsc(true);
                illegal.setColumn("credit_limit");
                CustomerQueryForm bad = queryForm();
                bad.setSortItemList(List.of(illegal));
                expectCode(() -> queryService.query(bad), 40000);
            }
        }

        @Nested
        @DisplayName("客户：写路径与状态机（PG IT）")
        class CustomerServiceIT {
            @Autowired
            private CustomerService service;

            @Autowired
            private BusinessCategoryService businessCategoryService;

            private CustomerAddForm form(String suffix) {
                CustomerAddForm form = new CustomerAddForm();
                form.setName("客户" + suffix);
                return form;
            }

            private CustomerUpdateForm updateForm(Long id, int version) {
                CustomerUpdateForm form = new CustomerUpdateForm();
                form.setCustomerId(id);
                form.setVersion(version);
                form.setName("改名");
                return form;
            }

            private String customerCode(Long id) {
                return jdbc.queryForObject("SELECT customer_code FROM xsy_customer WHERE id = ?", String.class, id);
            }

            private String statusOf(Long id) {
                return jdbc.queryForObject("SELECT status FROM xsy_customer WHERE id = ?", String.class, id);
            }

            private Long sellerOf(Long id) {
                return jdbc.queryForObject("SELECT seller_id FROM xsy_customer WHERE id = ?", Long.class, id);
            }

            @Test
            @DisplayName("新建客户默认合作中，授信额度缺省落 0.0000，审计写入操作人")
            void newCustomerStartsAsCooperatingWithZeroCreditLimit() {
                Long id = service.add(form("C1"));

                Map<String, Object> row = jdbc.queryForMap(
                        "SELECT status, version, deleted, settle_mode, credit_limit, created_by "
                                + "FROM xsy_customer WHERE id = ?", id);
                assertThat(row.get("status")).isEqualTo("COOPERATING");
                assertThat(((Number) row.get("version")).intValue()).isZero();
                assertThat(row.get("deleted")).isEqualTo(false);
                assertThat(row.get("settle_mode")).isEqualTo("INDEPENDENT");
                assertThat((BigDecimal) row.get("credit_limit")).isEqualByComparingTo("0.0000");
                assertThat(row.get("created_by")).isEqualTo("1:1");
            }

            @Test
            @DisplayName("编码由服务端生成（CUS######）：同名两次创建都成功且不重号；软删后可重建")
            void generatesUniqueCodesAndAllowsRecreateAfterSoftDelete() {
                Long id = service.add(form("c1"));
                String firstCode = customerCode(id);
                assertThat(firstCode).matches("^CUS\\d{6,}$");

                Long sameName = service.add(form("c1"));
                assertThat(customerCode(sameName)).as("两次创建不重号")
                        .matches("^CUS\\d{6,}$")
                        .isNotEqualTo(firstCode);

                CustomerDeleteForm deletion = new CustomerDeleteForm();
                deletion.setCustomerId(id);
                deletion.setVersion(0);
                service.delete(deletion);

                Long recreated = service.add(form("c1"));
                assertThat(customerCode(recreated)).as("软删后重建成功并拿到新编码")
                        .matches("^CUS\\d{6,}$")
                        .isNotEqualTo(firstCode);
            }

            @Test
            @DisplayName("不传状态时 update 不触碰 status：先停用再编辑，状态保持 DISABLED（legacy C7）")
            void updateNeverTouchesStatus() {
                Long id = service.add(form("U1"));

                CustomerStatusForm status = new CustomerStatusForm();
                status.setCustomerId(id);
                status.setVersion(0);
                status.setStatus("SUSPENDED");
                service.updateStatus(status);

                service.update(updateForm(id, 1));

                assertThat(jdbc.queryForObject("SELECT status FROM xsy_customer WHERE id = ?", String.class, id))
                        .isEqualTo("SUSPENDED");
                assertThat(jdbc.queryForObject("SELECT name FROM xsy_customer WHERE id = ?", String.class, id))
                        .isEqualTo("改名");
            }

            @Test
            @DisplayName("新增/编辑表单可直接改状态：与其它字段同一事务写入数据库")
            void formCanChangeStatusWithStatusPermission() {
                try (MockedStatic<StpUtil> permissions = mockStatic(StpUtil.class)) {
                    permissions.when(() -> StpUtil.hasPermission(anyString())).thenReturn(true);

                    CustomerAddForm add = form("S2");
                    add.setStatus("COOPERATING");
                    Long id = service.add(add);
                    assertThat(statusOf(id)).isEqualTo("COOPERATING");

                    CustomerUpdateForm update = updateForm(id, 0);
                    update.setStatus("SUSPENDED");
                    service.update(update);
                    assertThat(statusOf(id)).isEqualTo("SUSPENDED");
                    assertThat(jdbc.queryForObject("SELECT version FROM xsy_customer WHERE id = ?", Integer.class, id))
                            .isEqualTo(1);
                }
            }

            @Test
            @DisplayName("编辑表单可改派业务员，也可显式清空；需要客户分配权限")
            void formCanReassignSellerWithAssignPermission() {
                Long id = service.add(form("S3"));
                Long seller = anyEmployeeId();
                jdbc.update("UPDATE xsy_customer SET seller_id = NULL WHERE id = ?", id);

                CustomerUpdateForm assign = updateForm(id, 0);
                assign.setSellerId(seller);
                try (MockedStatic<StpUtil> permissions = mockStatic(StpUtil.class)) {
                    permissions.when(() -> StpUtil.hasPermission(anyString())).thenReturn(true);
                    service.update(assign);
                }
                assertThat(sellerOf(id)).isEqualTo(seller);

                CustomerUpdateForm clear = updateForm(id, 1);
                clear.setClearSeller(true);
                try (MockedStatic<StpUtil> permissions = mockStatic(StpUtil.class)) {
                    permissions.when(() -> StpUtil.hasPermission(anyString())).thenReturn(true);
                    service.update(clear);
                }
                assertThat(sellerOf(id)).isNull();
            }

            @Test
            @DisplayName("旧版本提交在 update 与 updateStatus 上都得到 40921")
            void staleVersionConflictsOnBothWritePaths() {
                Long id = service.add(form("S1"));
                CustomerUpdateForm update = updateForm(id, 0);
                service.update(update);

                expectCode(() -> service.update(update), 40921);

                CustomerStatusForm status = new CustomerStatusForm();
                status.setCustomerId(id);
                status.setVersion(0);
                status.setStatus("COOPERATING");
                expectCode(() -> service.updateStatus(status), 40921);
            }

            @Test
            @DisplayName("requireTradable 只接受 COOPERATING")
            void requireTradableAcceptsOnlyCooperating() {
                Long id = service.add(form("T1"));

                CustomerStatusForm status = new CustomerStatusForm();
                status.setCustomerId(id);
                status.setVersion(0);
                status.setStatus("COOPERATING");
                service.updateStatus(status);

                assertThat(service.requireTradable(id).getStatus()).isEqualTo("COOPERATING");
            }

            @ParameterizedTest
            @ValueSource(strings = {"SUSPENDED", "BLACKLIST"})
            @DisplayName("非 COOPERATING 状态不可交易（40930），错误文案带具体状态")
            void requireTradableRejectsEveryOtherStatus(String target) {
                Long id = service.add(form("T2"));

                CustomerStatusForm status = new CustomerStatusForm();
                status.setCustomerId(id);
                status.setVersion(0);
                status.setStatus(target);
                service.updateStatus(status);

                String statusLabel = "SUSPENDED".equals(target) ? "暂停合作" : "黑名单";
                assertThatThrownBy(() -> service.requireTradable(id))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> {
                            assertThat(e.getErrorCode().getCode()).isEqualTo(40930);
                            assertThat(e).hasMessage("客户当前状态为" + statusLabel + "，不可交易");
                        });
            }

            @Test
            @DisplayName("删除是软删并推进版本；删除后读取 40430")
            void deleteIsSoftAndAdvancesVersion() {
                Long id = service.add(form("D1"));

                CustomerDeleteForm deletion = new CustomerDeleteForm();
                deletion.setCustomerId(id);
                deletion.setVersion(0);
                service.delete(deletion);

                assertThat(jdbc.queryForObject("SELECT deleted FROM xsy_customer WHERE id = ?", Boolean.class, id)).isTrue();
                assertThat(jdbc.queryForObject("SELECT version FROM xsy_customer WHERE id = ?", Integer.class, id))
                        .isEqualTo(1);
                expectCode(() -> service.require(id), 40430);
            }

            @Test
            @DisplayName("客户分类必须属于 CUSTOMER 作用域且启用；编辑可清空")
            void businessCategoryMustBeEnabledCustomerScope() {
                Long customerCategory = jdbc.queryForObject(
                    "SELECT id FROM xsy_business_category WHERE scope = 'CUSTOMER' AND category_code = 'CUSTOMER_VEG'",
                    Long.class);
                Long supplierCategory = jdbc.queryForObject(
                    "SELECT id FROM xsy_business_category WHERE scope = 'SUPPLIER' AND category_code = 'SUPPLIER_VEG'",
                    Long.class);

                CustomerAddForm form = form("BC1");
                form.setBusinessCategoryId(customerCategory);
                Long id = service.add(form);
                assertThat(jdbc.queryForObject(
                    "SELECT business_category_id FROM xsy_customer WHERE id = ?", Long.class, id))
                    .isEqualTo(customerCategory);

                CustomerAddForm crossScope = form("BC2");
                crossScope.setBusinessCategoryId(supplierCategory);
                expectCode(() -> service.add(crossScope), 40444);

                BusinessCategoryAddForm disabledForm = new BusinessCategoryAddForm();
                disabledForm.setScope("CUSTOMER");
                disabledForm.setCategoryCode(prefix + "DISABLED");
                disabledForm.setName(prefix + "停用分类");
                disabledForm.setSortOrder(0);
                disabledForm.setStatus("ENABLED");
                Long disabledId = businessCategoryService.add(disabledForm);
                BusinessCategoryEntity disabled = businessCategoryService.require(disabledId);
                BusinessCategoryUpdateForm disable = new BusinessCategoryUpdateForm();
                disable.setCategoryId(disabledId);
                disable.setVersion(disabled.getVersion());
                disable.setScope("CUSTOMER");
                disable.setCategoryCode(disabled.getCategoryCode());
                disable.setName(disabled.getName());
                disable.setSortOrder(disabled.getSortOrder());
                disable.setStatus("DISABLED");
                businessCategoryService.update(disable);

                CustomerAddForm disabledCategory = form("BC3");
                disabledCategory.setBusinessCategoryId(disabledId);
                expectCode(() -> service.add(disabledCategory), 40954);

                CustomerUpdateForm clear = updateForm(id, 0);
                clear.setBusinessCategoryId(null);
                service.update(clear);
                assertThat(jdbc.queryForObject(
                    "SELECT business_category_id FROM xsy_customer WHERE id = ?", Long.class, id)).isNull();
            }

            @Test
            @DisplayName("客户自己持有可见性白名单时仍然拒绝删除（40939，守卫未被放宽）")
            void deleteIsRejectedWhileTheCustomerOwnsVisibilityRows() {
                Long id = service.add(form("VIS-REF"));
                jdbc.update("INSERT INTO xsy_customer_sku_visibility (customer_id, sku_id, created_by, updated_by) "
                        + "VALUES (?, 1, '1:1', '1:1')", id);

                CustomerDeleteForm deletion = new CustomerDeleteForm();
                deletion.setCustomerId(id);
                deletion.setVersion(0);
                expectCode(() -> service.delete(deletion), 40939);

                assertThat(jdbc.queryForObject("SELECT deleted FROM xsy_customer WHERE id = ?", Boolean.class, id))
                        .as("被拒时不得留下半删除状态")
                        .isFalse();
            }

            @Test
            @DisplayName("账期三种合法形态都能写入数据库，数值按 NUMERIC(18,4) 保真")
            void creditPeriodThreeFormsPersist() {
                CustomerAddForm byAmount = form("A1");
                byAmount.setCreditPeriodType("BY_AMOUNT");
                byAmount.setCreditAmountThreshold("5000");
                Long amountId = service.add(byAmount);
                assertThat(jdbc.queryForObject(
                        "SELECT credit_amount_threshold FROM xsy_customer WHERE id = ?", BigDecimal.class, amountId))
                        .isEqualByComparingTo("5000.0000");

                CustomerAddForm byDay = form("A2");
                byDay.setCreditPeriodType("BY_TIME");
                byDay.setCreditPeriodValue(30);
                byDay.setCreditPeriodUnit("DAY");
                Long dayId = service.add(byDay);
                assertThat(jdbc.queryForObject(
                        "SELECT credit_period_value FROM xsy_customer WHERE id = ?", Integer.class, dayId)).isEqualTo(30);
                assertThat(jdbc.queryForObject(
                        "SELECT settle_day FROM xsy_customer WHERE id = ?", Integer.class, dayId)).isNull();

                CustomerAddForm byMonth = form("A3");
                byMonth.setCreditPeriodType("BY_TIME");
                byMonth.setCreditPeriodValue(1);
                byMonth.setCreditPeriodUnit("MONTH");
                byMonth.setSettleDay(15);
                Long monthId = service.add(byMonth);
                assertThat(jdbc.queryForObject(
                        "SELECT settle_day FROM xsy_customer WHERE id = ?", Integer.class, monthId)).isEqualTo(15);
            }

            @ParameterizedTest
            @ValueSource(strings = {"amountWithPeriod", "timeWithoutValue", "dayWithSettleDay", "monthSettleDay29"})
            @DisplayName("账期非法组合被 Validator 拦为 40000（与 ck_customer_credit_period 等价）")
            void invalidCreditPeriodCombinationRejected(String kind) {
                CustomerAddForm form = form("X1");
                switch (kind) {
                    case "amountWithPeriod" -> {
                        form.setCreditPeriodType("BY_AMOUNT");
                        form.setCreditAmountThreshold("100");
                        form.setCreditPeriodValue(30);
                        form.setCreditPeriodUnit("DAY");
                    }
                    case "timeWithoutValue" -> {
                        form.setCreditPeriodType("BY_TIME");
                        form.setCreditPeriodUnit("DAY");
                    }
                    case "dayWithSettleDay" -> {
                        form.setCreditPeriodType("BY_TIME");
                        form.setCreditPeriodValue(30);
                        form.setCreditPeriodUnit("DAY");
                        form.setSettleDay(10);
                    }
                    case "monthSettleDay29" -> {
                        form.setCreditPeriodType("BY_TIME");
                        form.setCreditPeriodValue(1);
                        form.setCreditPeriodUnit("MONTH");
                        form.setSettleDay(29);
                    }
                    default -> throw new AssertionError(kind);
                }
                expectCode(() -> service.add(form), 40000);
            }

            @Test
            @DisplayName("上级客户可以是任意客户；自身 / 环形得到 40032，不存在得 40430")
            void parentCustomerMustExistAndBeAcyclic() {
                Long parent = service.add(form("G1"));
                Long member = service.add(form("M1"));

                CustomerUpdateForm child = updateForm(member, 0);
                child.setParentCustomerId(parent);
                service.update(child);
                assertThat(jdbc.queryForObject("SELECT parent_customer_id FROM xsy_customer WHERE id = ?", Long.class, member))
                        .isEqualTo(parent);

                // 任意客户都能作为上级
                CustomerAddForm nested = form("N1");
                nested.setParentCustomerId(member);
                service.add(nested);

                // 自身作为上级
                CustomerUpdateForm self = updateForm(member, 1);
                self.setParentCustomerId(member);
                expectCode(() -> service.update(self), 40032);

                // 上级不存在
                CustomerAddForm missing = form("N2");
                missing.setParentCustomerId(Long.MAX_VALUE);
                expectCode(() -> service.add(missing), 40430);
            }

            @Test
            @DisplayName("环形上级被上溯检测拦下（40032）")
            void cyclicParentIsDetected() {
                Long a = service.add(form("R1"));
                Long b = service.add(form("R2"));

                // a.parent = b
                CustomerUpdateForm link = updateForm(a, 0);
                link.setParentCustomerId(b);
                service.update(link);

                // 再把 b.parent 指回 a → 成环
                CustomerUpdateForm back = new CustomerUpdateForm();
                back.setCustomerId(b);
                back.setVersion(0);
                back.setName("客户R2");
                back.setParentCustomerId(a);
                expectCode(() -> service.update(back), 40032);
            }

            @Test
            @DisplayName("不存在 / 已删除客户统一 40430")
            void missingCustomerIs40430() {
                expectCode(() -> service.require(-1L), 40430);
                expectCode(() -> service.require(null), 40430);
            }

            /**
             * 只声明策略、不带清单时必须按空清单处理。
             *
             * <p>{@code CustomerService.add/update} 的条件是「策略或清单任一非空就送进 replace」，
             * 所以「切到 ALL_ENABLED 且不动清单」这一唯一合法请求会以 {@code visibilities == null} 抵达
             * replace，必须按空清单处理，不能当成明细行不合法。
             */
            @Test
            @DisplayName("详情页可整体替换可售商品：白名单增删后切回全部可售会清空明细")
            void replaceVisibilityAddsRemovesAndSwitchesToAllEnabled() {
                Long id = service.add(form("VIS-REPLACE"));
                Long firstSku = newOnShelfSku("VIS-REPLACE-1");
                Long secondSku = newOnShelfSku("VIS-REPLACE-2");

                CustomerVisibilityReplaceForm whitelist = new CustomerVisibilityReplaceForm();
                whitelist.setCustomerId(id);
                whitelist.setVisibilityPolicy("ALLOWLIST");
                whitelist.setVisibilities(new java.util.ArrayList<>(java.util.List.of(item(firstSku), item(secondSku))));
                service.replaceVisibility(whitelist);

                assertThat(jdbc.queryForObject(
                        "SELECT visibility_policy FROM xsy_customer WHERE id = ?", String.class, id)).isEqualTo("ALLOWLIST");
                var rows = jdbc.queryForList(
                        "SELECT id, version, sku_id FROM xsy_customer_sku_visibility "
                                + "WHERE customer_id = ? AND deleted = FALSE ORDER BY sku_id", id);
                assertThat(rows).hasSize(2);

                // 只保留 firstSku：secondSku 软删，firstSku 带 id + version 原样保留。
                var retained = rows.stream()
                        .filter(row -> ((Number) row.get("sku_id")).longValue() == firstSku)
                        .findFirst()
                        .orElseThrow();
                CustomerVisibilityReplaceForm shrink = new CustomerVisibilityReplaceForm();
                shrink.setCustomerId(id);
                shrink.setVisibilityPolicy("ALLOWLIST");
                var kept = new CustomerSkuVisibilityItemForm();
                kept.setId(((Number) retained.get("id")).longValue());
                kept.setVersion(((Number) retained.get("version")).intValue());
                kept.setSkuId(firstSku);
                shrink.setVisibilities(new java.util.ArrayList<>(java.util.List.of(kept)));
                service.replaceVisibility(shrink);

                assertThat(jdbc.queryForList(
                        "SELECT sku_id FROM xsy_customer_sku_visibility WHERE customer_id = ? AND deleted = FALSE",
                        Long.class, id)).containsExactly(firstSku);

                CustomerVisibilityReplaceForm all = new CustomerVisibilityReplaceForm();
                all.setCustomerId(id);
                all.setVisibilityPolicy("ALL_ENABLED");
                service.replaceVisibility(all);

                assertThat(jdbc.queryForObject(
                        "SELECT visibility_policy FROM xsy_customer WHERE id = ?", String.class, id)).isEqualTo("ALL_ENABLED");
                assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM xsy_customer_sku_visibility WHERE customer_id = ? AND deleted = FALSE",
                        Integer.class, id)).isZero();
            }

            private CustomerSkuVisibilityItemForm item(Long skuId) {
                CustomerSkuVisibilityItemForm item = new CustomerSkuVisibilityItemForm();
                item.setSkuId(skuId);
                return item;
            }

            @Test
            @DisplayName("只改可见性策略、visibilities 留空时不得被 40034 拒掉")
            void policyOnlyWithoutVisibilityListIsAccepted() {
                var form = form("VIS-POLICY-ONLY");
                form.setVisibilityPolicy("ALL_ENABLED");
                Long id = service.add(form);
                assertThat(jdbc.queryForObject(
                        "SELECT visibility_policy FROM xsy_customer WHERE id = ?", String.class, id)).isEqualTo("ALL_ENABLED");
            }

            @Test
            @DisplayName("归一化 null 不放宽冲突判定：ALL_ENABLED 带上明细仍按 40033 拒绝")
            void allEnabledWithExplicitItemsStillConflicts() {
                var form = form("VIS-CONFLICT");
                form.setVisibilityPolicy("ALL_ENABLED");
                var item = new CustomerSkuVisibilityItemForm();
                item.setSkuId(1L);
                form.setVisibilities(new java.util.ArrayList<>(java.util.List.of(item)));
                expectCode(() -> service.add(form), 40033);
            }
        }

        @Nested

        class ScmCustomerBalanceScopePgIT {
            @Autowired
            private CustomerService customerService;

            @Autowired
            private CustomerQueryService customerQueryService;

            @Autowired
            private CustomerBalanceService customerBalanceService;

            @Autowired
            private PaymentQueryService paymentQueryService;

            private Long sellerA;
            private Long sellerB;

            @BeforeEach
            void employees() {
                sellerA = newEmployee("A");
                sellerB = newEmployee("B");
            }

            @Test
            void customerWritesAndOptionsUseTheSameOwnerScope() {
                Long customerId = as(sellerB, () -> customerService.add(customerForm()));
                Integer version = jdbc.queryForObject("SELECT version FROM xsy_customer WHERE id = ?", Integer.class, customerId);

                assertThat(as(sellerA, () -> customerQueryService.optionList())).noneMatch(option -> customerId.equals(option.getCustomerId()));
                assertThat(as(sellerB, () -> customerQueryService.optionList())).anyMatch(option -> customerId.equals(option.getCustomerId()));
                assertThatThrownBy(() -> as(sellerA, () -> {
                    customerService.update(updateForm(customerId, version));
                    return null;
                }))
                        .isInstanceOf(ScmDataScopeException.class);

                CustomerStatusForm status = new CustomerStatusForm();
                status.setCustomerId(customerId);
                status.setVersion(version);
                status.setStatus("COOPERATING");
                assertThatThrownBy(() -> as(sellerA, () -> {
                    customerService.updateStatus(status);
                    return null;
                }))
                        .isInstanceOf(ScmDataScopeException.class);

                CustomerDeleteForm deletion = new CustomerDeleteForm();
                deletion.setCustomerId(customerId);
                deletion.setVersion(version);
                assertThatThrownBy(() -> as(sellerA, () -> {
                    customerService.delete(deletion);
                    return null;
                }))
                        .isInstanceOf(ScmDataScopeException.class);
                assertThat(jdbc.queryForObject("SELECT version FROM xsy_customer WHERE id = ?", Integer.class, customerId))
                        .isEqualTo(version);
            }

            @Test
            void balanceCorrectionReplayRechecksCurrentOwner() {
                Long customerId = as(sellerB, () -> customerService.add(customerForm()));
                BalanceCorrectionForm form = new BalanceCorrectionForm();
                form.setCustomerId(customerId);
                form.setDirection("CREDIT");
                form.setAmount(new BigDecimal("10.0000"));
                form.setReason("范围回归");
                String key = prefix + "-correction";

                assertThatThrownBy(() -> as(sellerA, () -> customerBalanceService.correct(form, key)))
                        .isInstanceOf(ScmDataScopeException.class);
                as(sellerB, () -> customerBalanceService.correct(form, key));
                jdbc.update("UPDATE xsy_customer SET seller_id = ? WHERE id = ?", sellerA, customerId);
                assertThatThrownBy(() -> as(sellerB, () -> customerBalanceService.correct(form, key)))
                        .isInstanceOf(ScmDataScopeException.class);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM xsy_customer_balance_movement WHERE customer_id = ?",
                        Integer.class, customerId)).isEqualTo(1);
            }

            @Test
            void refundListIntersectsCustomerAndOrderScope() {
                Long customerA = as(sellerA, () -> customerService.add(customerForm()));
                Long customerB = as(sellerB, () -> customerService.add(customerForm()));
                Long orderA = order(customerA, sellerA, "A");
                Long orderB = order(customerB, sellerB, "B");
                Long refundA = refund(customerA, orderA, "A");
                Long refundB = refund(customerB, orderB, "B");
                Long mismatched = refund(customerA, orderB, "M");

                PaymentRefundQueryForm form = new PaymentRefundQueryForm();
                form.setPageNum(1L);
                form.setPageSize(20L);
                assertThat(as(sellerA, () -> paymentQueryService.refundPage(form)).getList())
                        .extracting(row -> row.getId()).contains(refundA).doesNotContain(refundB, mismatched);
                assertThat(as(sellerB, () -> paymentQueryService.refundPage(form)).getList())
                        .extracting(row -> row.getId()).contains(refundB).doesNotContain(refundA, mismatched);
            }

            private Long order(Long customerId, Long sellerId, String suffix) {
                String customerCode = jdbc.queryForObject("SELECT customer_code FROM xsy_customer WHERE id = ?", String.class,
                        customerId);
                return jdbc.queryForObject("INSERT INTO xsy_sales_order (order_no, customer_id, customer_code_snapshot,"
                                + " customer_name_snapshot, settle_mode_snapshot, settlement_customer_id,"
                                + " settlement_customer_name_snapshot, seller_id)"
                                + " VALUES (?, ?, ?, ?, 'INDEPENDENT', ?, ?, ?) RETURNING id",
                        Long.class, prefix + "-ORDER-" + suffix, customerId, customerCode, prefix + "客户", customerId,
                        prefix + "客户", sellerId);
            }

            private Long refund(Long customerId, Long orderId, String suffix) {
                Long intentId = jdbc.queryForObject("INSERT INTO xsy_payment_intent (intent_no, customer_id,"
                                + " customer_name_snapshot, source_type, source_id, source_no_snapshot, amount, method, provider)"
                                + " VALUES (?, ?, ?, 'SALES_ORDER', ?, ?, 10, 'ONLINE', 'MOCK') RETURNING id",
                        Long.class, prefix + "-INTENT-" + suffix, customerId, prefix + "客户", orderId,
                        prefix + "-ORDER-" + suffix);
                return jdbc.queryForObject("INSERT INTO xsy_payment_refund (refund_no, intent_id, transaction_id, provider, amount)"
                                + " VALUES (?, ?, ?, 'MOCK', 10) RETURNING id",
                        Long.class, prefix + "-REFUND-" + suffix, intentId, 100000L + intentId);
            }

            private CustomerAddForm customerForm() {
                CustomerAddForm form = new CustomerAddForm();
                form.setName(prefix + "客户");
                form.setSettleMode("INDEPENDENT");
                return form;
            }

            private CustomerUpdateForm updateForm(Long customerId, Integer version) {
                CustomerUpdateForm form = new CustomerUpdateForm();
                form.setCustomerId(customerId);
                form.setVersion(version);
                form.setName(prefix + "改名");
                form.setSettleMode("INDEPENDENT");
                return form;
            }

            private Long newEmployee(String suffix) {
                String login = (prefix + "-" + suffix).toUpperCase(Locale.ROOT);
                jdbc.update("INSERT INTO t_employee (employee_uid, login_name, login_pwd, actual_name, department_id,"
                                + " administrator_flag, deleted_flag) VALUES (?, ?, ?, ?, 1, FALSE, FALSE)",
                        UUID.randomUUID().toString().replace("-", ""), login, "$argon2id$it-placeholder", "业务员" + suffix);
                return jdbc.queryForObject("SELECT employee_id FROM t_employee WHERE login_name = ?", Long.class, login);
            }

            private <T> T as(Long employeeId, Supplier<T> action) {
                RequestEmployee employee = new RequestEmployee();
                employee.setEmployeeId(employeeId);
                employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
                employee.setAdministratorFlag(false);
                employee.setDepartmentId(1L);
                XsyRequestUtil.setRequestUser(employee);
                try (MockedStatic<StpUtil> ignored = mockStatic(StpUtil.class)) {
                    return action.get();
                }
            }
        }
    }

    @Nested
    class CustomerW5PgIT extends ScmW5PgITBase {
        @Nested

        class CustomerPromotionPgIT {
            @Autowired
            private CustomerPromotionService customerPromotionService;

            @Test
            @DisplayName("#1 生成推广码后扫码记录来源，重复生成让旧码失效（40041）")
            void issueCodeRecordsScanAndReissueRevokes() {
                Long customerId = newCustomer();
                CustomerPromotionVO first = customerPromotionService.create(customerId, "W9 IT");
                assertThat(first.getCode()).hasSize(10);

                CustomerPromotionVO.Landing landing = customerPromotionService.landing(first.getCode(), "127.0.0.1",
                        "W9 IT agent");
                assertThat(landing.getCustomerName()).isNotBlank();

                // 扫码只能新增：一条记录，且能查到
                assertThat(customerPromotionService.scans(first.getId(), 20)).hasSize(1);

                CustomerPromotionVO second = customerPromotionService.create(customerId, "W9 IT");
                assertThatThrownBy(() -> customerPromotionService.landing(first.getCode(), "127.0.0.1", null))
                        .isInstanceOf(ScmBusinessException.class)
                        .satisfies(error -> assertThat(((ScmBusinessException) error).getErrorCode().getCode())
                                .isEqualTo(40041));
                assertThat(customerPromotionService.landing(second.getCode(), "127.0.0.1", null).getCustomerName())
                        .isNotBlank();
            }

            @Test
            @DisplayName("#2 撤销后不可扫码，未知码被拒（40042）")
            void revokeBlocksLanding() {
                Long customerId = newCustomer();
                CustomerPromotionVO code = customerPromotionService.create(customerId, "W9 IT");
                customerPromotionService.revoke(code.getId());

                assertThatThrownBy(() -> customerPromotionService.landing(code.getCode(), "127.0.0.1", null))
                        .isInstanceOf(ScmBusinessException.class)
                        .satisfies(error -> assertThat(((ScmBusinessException) error).getErrorCode().getCode())
                                .isEqualTo(40041));

                assertThatThrownBy(() -> customerPromotionService.landing("NOTEXIST99", "127.0.0.1", null))
                        .isInstanceOf(ScmBusinessException.class)
                        .satisfies(error -> assertThat(((ScmBusinessException) error).getErrorCode().getCode())
                                .isEqualTo(40042));
            }
        }

        @Nested

        class CustomerSharedSettlementPgIT {
            @Autowired
            private CustomerService customerService;

            @Autowired
            private CustomerQueryService customerQueryService;

            @Test
            @DisplayName("#1 集团主体可授权，独立结算客户即使提交也会被置假")
            void onlyGroupCustomerKeepsGrant() {
                Long groupId = customerService.add(form("W9 集团", "GROUP", true));
                CustomerDetailVO group = customerQueryService.detail(groupId);
                assertThat(group.getSharedSettlementEnabled()).isTrue();

                Long independentId = customerService.add(form("W9 独立", "INDEPENDENT", true));
                CustomerDetailVO independent = customerQueryService.detail(independentId);
                assertThat(independent.getSharedSettlementEnabled()).isFalse();
            }

            @Test
            @DisplayName("#2 非集团客户提交共享结算时，服务端按集团模式归一为关闭")
            void independentCustomerCannotEnableSharedSettlement() {
                Long customerId = customerService.add(form("W9 约束", "INDEPENDENT", true));

                assertThat(jdbc.queryForObject(
                        "SELECT shared_settlement_enabled FROM xsy_customer WHERE id = ?", Boolean.class, customerId))
                        .isFalse();
            }

            private CustomerAddForm form(String name, String settleMode, boolean shared) {
                CustomerAddForm form = new CustomerAddForm();
                form.setName(name);
                form.setSettleMode(settleMode);
                form.setSharedSettlementEnabled(shared);
                return form;
            }
        }
    }
}
