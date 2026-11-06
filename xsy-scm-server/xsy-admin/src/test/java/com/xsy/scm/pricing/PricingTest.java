package com.xsy.scm.pricing;

import com.xsy.scm.admin.XsyApplication;
import com.xsy.scm.admin.module.system.login.domain.RequestEmployee;
import com.xsy.scm.admin.test.PgITMigrationHistory;
import com.xsy.scm.admin.test.PgITPaths;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.SystemEnvironment;
import com.xsy.scm.base.common.enumeration.UserTypeEnum;
import com.xsy.scm.base.common.util.XsyRequestUtil;
import com.xsy.scm.base.handler.GlobalExceptionHandler;
import com.xsy.scm.common.ScmBaselineMigrationPgITBase;
import com.xsy.scm.common.ScmW3PgITBase;
import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.common.constant.ScmSettleModeEnum;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.handler.ScmExceptionHandler;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmWarehouseScopeGuard;
import com.xsy.scm.customer.constant.CustomerVisibilityPolicy;
import com.xsy.scm.customer.controller.CustomerVisibilityController;
import com.xsy.scm.customer.dao.CustomerSkuVisibilityDao;
import com.xsy.scm.customer.domain.entity.CustomerEntity;
import com.xsy.scm.customer.domain.form.*;
import com.xsy.scm.customer.domain.form.CustomerGroupAddForm;
import com.xsy.scm.customer.domain.form.CustomerGroupDeleteForm;
import com.xsy.scm.customer.domain.form.CustomerGroupUpdateForm;
import com.xsy.scm.customer.domain.vo.CustomerGroupVO;
import com.xsy.scm.customer.service.*;
import com.xsy.scm.customer.service.CustomerGroupService;
import com.xsy.scm.customer.service.CustomerService;
import com.xsy.scm.customer.service.CustomerSkuVisibilityService;
import com.xsy.scm.pricing.constant.*;
import com.xsy.scm.pricing.constant.AgreementFormulaType;
import com.xsy.scm.pricing.constant.ScmPriceSourceEnum;
import com.xsy.scm.pricing.constant.ScmPriceStatusEnum;
import com.xsy.scm.pricing.constant.ScmUnpricedReasonEnum;
import com.xsy.scm.pricing.controller.*;
import com.xsy.scm.pricing.dao.AgreementPriceDao;
import com.xsy.scm.pricing.dao.CustomerGroupPriceDao;
import com.xsy.scm.pricing.dao.PricingCostBasisDao;
import com.xsy.scm.pricing.dao.TimePriceDao;
import com.xsy.scm.pricing.domain.entity.AgreementPriceEntity;
import com.xsy.scm.pricing.domain.entity.CustomerGroupPriceEntity;
import com.xsy.scm.pricing.domain.form.*;
import com.xsy.scm.pricing.domain.form.CustomerGroupPriceAddForm;
import com.xsy.scm.pricing.domain.form.CustomerGroupPriceQueryForm;
import com.xsy.scm.pricing.domain.form.PriceHistoryQueryForm;
import com.xsy.scm.pricing.domain.form.TimePriceForm;
import com.xsy.scm.pricing.domain.form.TimePriceQueryForm;
import com.xsy.scm.pricing.domain.vo.*;
import com.xsy.scm.pricing.domain.vo.PriceHistoryVO;
import com.xsy.scm.pricing.domain.vo.PricingCostBasisVO;
import com.xsy.scm.pricing.domain.vo.ResolvedPriceVO;
import com.xsy.scm.pricing.domain.vo.TimePriceVO;
import com.xsy.scm.pricing.manager.AgreementFormulaCalculator;
import com.xsy.scm.pricing.manager.PriceValidation;
import com.xsy.scm.pricing.service.*;
import com.xsy.scm.pricing.service.CustomerGroupPriceQueryService;
import com.xsy.scm.pricing.service.CustomerGroupPriceService;
import com.xsy.scm.pricing.service.PriceHistoryQueryService;
import com.xsy.scm.pricing.service.PriceResolver;
import com.xsy.scm.pricing.service.TimePriceService;
import com.xsy.scm.product.controller.ProductSkuController;
import com.xsy.scm.product.dao.ProductSkuOptionDao;
import com.xsy.scm.product.domain.form.ProductSkuForm;
import com.xsy.scm.product.domain.form.ProductSkuOptionQueryForm;
import com.xsy.scm.product.domain.form.ProductSpuAddForm;
import com.xsy.scm.product.domain.vo.ProductSkuOptionVO;
import com.xsy.scm.product.service.ProductBundleComponentQueryService;
import com.xsy.scm.product.service.ProductSkuOptionQueryService;
import com.xsy.scm.product.service.ProductSkuStockConversionService;
import com.xsy.scm.purchase.constant.ScmPurchaseStatusEnum;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PricingTest {

    @Nested
    class PricingIT extends ScmW3PgITBase {
        @Autowired
        org.apache.ibatis.session.SqlSession sqlSession;
        @Autowired
        AgreementPriceService agreements;
        @Autowired
        AgreementPriceQueryService queries;
        @Autowired
        PriceResolver resolver;
        @Autowired
        com.xsy.scm.pricing.service.TimePriceService timePrices;
        @Autowired
        PriceHistoryQueryService history;
        @Autowired
        CustomerService customers;
        @Autowired
        CustomerQueryService customerQueries;
        @Autowired
        CustomerSkuVisibilityService visibility;
        @Autowired
        ProductSkuOptionQueryService skuOptions;
        static final OffsetDateTime AT = OffsetDateTime.parse("2026-09-15T00:00:00Z");

        /**
         * 时价的位置：市场价之上、逐客协议价之下。
         *
         * <p>
         * 只验写入约束不够，必须证明真实选价链路真的按这个顺序取价。
         */
        @Test
        void timePriceSitsBetweenAgreementAndMarket() {
            Long c = customer(), s = newOnShelfSku("TP");
            var timePrice = new com.xsy.scm.pricing.domain.form.TimePriceForm();
            timePrice.setSkuId(s);
            timePrice.setUnitPrice(new java.math.BigDecimal("8.8000"));
            timePrice.setStartAt(AT.minusDays(1));
            timePrice.setEndAt(AT.plusDays(1));
            timePrices.create(timePrice, "W9 IT");

            // 无协议价：时价压过市场价
            var byTime = resolver.resolve(c, List.of(s), AT).getFirst();
            assertThat(byTime.getPriceSource()).isEqualTo(ScmPriceSourceEnum.TIME_PRICE);
            assertThat(byTime.getUnitPrice()).isEqualByComparingTo("8.8000");

            // 时价区间之外：回落到市场价
            assertThat(resolver.resolve(c, List.of(s), AT.plusDays(3)).getFirst().getPriceSource())
                    .isEqualTo(ScmPriceSourceEnum.MARKET);

            // 加逐客协议价：客户直接定价压过时价
            agreements.add(agreement(c, s, "3.0000", AT.minusDays(2), null));
            var byAgreement = resolver.resolve(c, List.of(s), AT).getFirst();
            assertThat(byAgreement.getPriceSource()).isEqualTo(ScmPriceSourceEnum.AGREEMENT);
            assertThat(byAgreement.getUnitPrice()).isEqualByComparingTo("3.0000");
        }

        Long customer() {
            var f = new CustomerAddForm();
            f.setName(prefix);
            f.setSettleMode("INDEPENDENT");
            Long id = customers.add(f);
            var status = new CustomerStatusForm();
            status.setCustomerId(id);
            status.setVersion(0);
            status.setStatus("COOPERATING");
            customers.updateStatus(status);
            return id;
        }

        AgreementPriceAddForm agreement(Long c, Long s, String price, OffsetDateTime from, OffsetDateTime to) {
            var f = new AgreementPriceAddForm();
            f.setCustomerId(c);
            f.setSkuId(s);
            f.setUnitPrice(price);
            f.setEffectiveFrom(from);
            f.setEffectiveTo(to);
            return f;
        }

        @Test
        void priorityZeroHalfOpenAndAuditedCrud() {
            Long c = customer(), s = newOnShelfSku("A");
            Long id = agreements.add(agreement(c, s, "0.0000", AT, AT.plusDays(1)));
            var result = resolver.resolve(c, List.of(s), AT).getFirst();
            assertThat(result.getPriceSource()).isEqualTo(ScmPriceSourceEnum.AGREEMENT);
            assertThat(result.getUnitPrice()).isEqualByComparingTo("0");
            assertThat(result.isSellable()).isTrue();
            assertThat(resolver.resolve(c, List.of(s), AT.plusDays(1)).getFirst().getPriceSource()).isEqualTo(ScmPriceSourceEnum.MARKET);
            assertThat(resolver.resolve(c, List.of(s), AT.minusDays(3)).getFirst().getPriceSource()).isEqualTo(ScmPriceSourceEnum.MARKET);
            expectCode(() -> agreements.add(agreement(c, s, "2", AT, AT.plusHours(1))), 40933);
            agreements.add(agreement(c, s, "2", AT.plusDays(1), null));
            var update = new AgreementPriceUpdateForm();
            org.springframework.beans.BeanUtils.copyProperties(agreement(c, s, "5", AT, AT.plusDays(1)), update);
            update.setAgreementPriceId(id);
            update.setVersion(0);
            agreements.update(update);
            expectCode(() -> agreements.update(update), 40921);
            var d = new AgreementPriceDeleteForm();
            d.setAgreementPriceId(id);
            d.setVersion(1);
            agreements.delete(d);
            var h = new PriceHistoryQueryForm();
            h.setPageNum(1L);
            h.setPageSize(20L);
            h.setCustomerId(c);
            var logs = history.query(h).getList();
            assertThat(logs).hasSize(4);
            assertThat(logs).allSatisfy(l -> assertThat(l.getOperator()).isEqualTo("1:1"));
            assertThat(logs).anySatisfy(l -> {
                assertThat(l.getOperationType()).isEqualTo("DELETE");
                assertThat(l.getAfterData().get("deleted")).isEqualTo(true);
                assertThat(l.getAfterData().get("version")).isEqualTo(2);
            });
            var q = new AgreementPriceQueryForm();
            q.setPageNum(1L);
            q.setPageSize(20L);
            q.setCustomerId(c);
            assertThat(queries.query(q).getList()).hasSize(1);
        }

        @Test
        void pricedButUnavailableAndGenuineMissingSku() {
            Long c = customer(), s = newOnShelfSku("B");
            jdbc.update("UPDATE xsy_product_sku SET market_price=5,status='OFF_SHELF' WHERE id=?", s);
            var r = resolver.resolve(c, List.of(s), AT).getFirst();
            assertThat(r.getPriceStatus()).isEqualTo(ScmPriceStatusEnum.PRICED);
            assertThat(r.getUnitPrice()).isEqualByComparingTo("5");
            assertThat(r.getUnavailableReason()).isEqualTo(ScmUnavailableReasonEnum.SKU_OFF_SHELF);
            assertThat(r.getUnpricedReason()).isNull();
            expectCode(() -> resolver.requireResolvable(c, List.of(s), AT), 40949);
            jdbc.update("UPDATE xsy_product_sku SET status='ON_SHELF' WHERE id=?", s);
            jdbc.update("UPDATE xsy_customer SET visibility_policy='ALLOWLIST' WHERE id=?", c);
            sqlSession.clearCache();
            r = resolver.resolve(c, List.of(s), AT).getFirst();
            assertThat(r.getUnavailableReason()).isEqualTo(ScmUnavailableReasonEnum.NOT_VISIBLE);
            assertThat(r.getPriceStatus()).isEqualTo(ScmPriceStatusEnum.PRICED);
            r = resolver.resolve(c, List.of(Long.MAX_VALUE), AT).getFirst();
            assertThat(r.getUnavailableReason()).isEqualTo(ScmUnavailableReasonEnum.SKU_NOT_FOUND);
            assertThat(r.getUnpricedReason()).isEqualTo(ScmUnpricedReasonEnum.NO_PRICE_SOURCE);
            assertThat(r.getUnitPrice()).isNull();
        }

        @Test
        void visibilityIdentityEmptyPolicyAndReferences() {
            Long c = customer(), s = newOnShelfSku("C");
            var item = new CustomerSkuVisibilityItemForm();
            item.setSkuId(s);
            visibility.replace(c, "ALLOWLIST", List.of(item));
            var stored = visibility.list(c).getFirst();
            expectCode(() -> visibility.replace(c, "ALLOWLIST", List.of(item)), 40034);
            item.setId(stored.id());
            item.setVersion(stored.version());
            visibility.replace(c, "ALLOWLIST", List.of(item));
            expectCode(() -> visibility.replace(c, "ALLOWLIST", List.of(item)), 40921);
            var d = new CustomerDeleteForm();
            d.setCustomerId(c);
            d.setVersion(1);
            expectCode(() -> customers.delete(d), 40939);
            visibility.replace(c, "ALLOWLIST", List.of());
            assertThat(visibility.list(c)).isEmpty();
            customers.delete(d);
        }

        @Test
        void optionsBoundedAndStatusAware() {
            newOnShelfSku("D");
            newOnShelfSku("E");
            var q = new ProductSkuOptionQueryForm();
            q.setKeyword(prefix);
            q.setLimit(1);
            var response = skuOptions.optionList(q);
            assertThat(response.options()).hasSize(1);
            assertThat(response.truncated()).isTrue();
            assertThat(response.options().getFirst().getSpecValues()).isNotEmpty();
        }

        @Test
        void historyInvalidSourceIsUnfilteredAndHalfOpenDatesAreStable() {
            var query = new PriceHistoryQueryForm();
            query.setPageNum(1L);
            query.setPageSize(20L);
            query.setSource("UNKNOWN");
            var result = history.query(query);
            assertThat(query.getSource()).isNull();
            assertThat(result.getPageNum()).isEqualTo(1L);
        }

        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        void concurrentOverlappingAgreementWritesAllowOnlyOneWinner() throws Exception {
            Long customerId = customer();
            Long sku = newOnShelfSku("CONCURRENT");
            var start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            List<java.util.concurrent.Callable<Object>> tasks = List.of(
                    () -> {
                        setThreadOperator();
                        start.await();
                        agreements.add(agreement(customerId, sku, "7.0000", AT, AT.plusDays(2)));
                        XsyRequestUtil.remove();
                        return null;
                    },
                    () -> {
                        setThreadOperator();
                        start.await();
                        agreements.add(agreement(customerId, sku, "8.0000", AT, AT.plusDays(2)));
                        XsyRequestUtil.remove();
                        return null;
                    });
            var futures = tasks.stream().map(pool::submit).toList();
            start.countDown();
            int successes = 0, overlaps = 0;
            for (Future<?> future : futures) {
                try {
                    future.get();
                    successes++;
                } catch (java.util.concurrent.ExecutionException e) {
                    if (e.getCause() instanceof com.xsy.scm.common.exception.ScmBusinessException ex && ex.getErrorCode().getCode() == 40933)
                        overlaps++;
                    else throw e;
                }
            }
            pool.shutdownNow();
            assertThat(successes).isEqualTo(1);
            assertThat(overlaps).isEqualTo(1);
        }

        private static void setThreadOperator() {
            RequestEmployee employee = new RequestEmployee();
            employee.setEmployeeId(1L);
            employee.setActualName("W3 concurrent IT");
            employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
            XsyRequestUtil.setRequestUser(employee);
        }
    }

    @Nested
    class PricingUnitTest {
        @Nested

        class AgreementFormulaCalculatorTest {
            @Test
            void supportsFourExplicitFormulaTypes() {
                assertPrice(AgreementFormulaType.FIXED, "7.1234", null, null, null, "7.1234");
                assertPrice(AgreementFormulaType.MARKET_PLUS_AMOUNT, null, "2.5000", "10.0000", null, "12.5000");
                assertPrice(AgreementFormulaType.COST_MARKUP_RATE, null, "0.2000", null, "8.0000", "9.6000");
                assertPrice(AgreementFormulaType.MARKET_MARKUP_RATE, null, "0.2000", "10.0000", null, "12.0000");
            }

            @Test
            void roundsAtFourDecimalPlacesWithHalfUp() {
                assertPrice(AgreementFormulaType.MARKET_MARKUP_RATE, null, "0.3333", "1.0000", null, "1.3333");
                assertPrice(AgreementFormulaType.COST_MARKUP_RATE, null, "0.1000", null, "1.2345", "1.3580");
                assertPrice(AgreementFormulaType.FIXED, "1.23445", null, null, null, "1.2345");
            }

            @Test
            void missingInputsMustNotFallbackToMarketPriceOrZero() {
                assertThat(calc(null, null, null, "10.0000", null)).isNull();
                assertThat(calc(AgreementFormulaType.FIXED, null, null, "10.0000", null)).isNull();
                assertThat(calc(AgreementFormulaType.MARKET_PLUS_AMOUNT, null, "2", null, null)).isNull();
                assertThat(calc(AgreementFormulaType.COST_MARKUP_RATE, null, "0.20", "10", null)).isNull();
                assertThat(calc(AgreementFormulaType.MARKET_MARKUP_RATE, null, "0.20", null, "5")).isNull();
                assertThat(calc(AgreementFormulaType.MARKET_MARKUP_RATE, null, null, "5", null)).isNull();
            }

            @Test
            void rejectsNegativeParametersAndOverflowWithoutCrashing() {
                assertThat(calc(AgreementFormulaType.MARKET_MARKUP_RATE, null, "-0.5000", "20", null)).isNull();
                assertThat(calc(AgreementFormulaType.MARKET_PLUS_AMOUNT, null, "-2", "10", null)).isNull();
                assertThat(calc(AgreementFormulaType.COST_MARKUP_RATE, null, "-1", null, "5")).isNull();
                assertThat(calc(AgreementFormulaType.FIXED, "-0.0001", null, null, null)).isNull();
                assertThat(calc(AgreementFormulaType.FIXED, "100000000000000.0000", null, null, null)).isNull();
                assertThat(calc(AgreementFormulaType.MARKET_PLUS_AMOUNT, null, "99999999999999.9999",
                        "99999999999999.9999", null)).isNull();
            }

            @Test
            void zeroIsAnExplicitPriceNotAMissingValue() {
                assertPrice(AgreementFormulaType.FIXED, "0.0000", null, null, null, "0.0000");
                assertPrice(AgreementFormulaType.MARKET_MARKUP_RATE, null, "0.1000", "0.0000", null, "0.0000");
            }

            @Test
            void purchaseCostFormulaAppliesLossTaxAndMarkupAsPercentages() {
                // 损耗率、税率是百分数：5.0000 / 13.0000 分别换算成 1.05 / 1.13。
                assertThat(calcPurchase("10.0000", "0.1000", "10.0000", "5.0000", "13.0000"))
                    .isEqualByComparingTo("13.0515");
                // 最近采购价缺失时回落到当前销售单位成本，不按 0 处理。
                assertThat(calcPurchase(null, "0.2000", "8.0000", "0.0000", "0.0000")).isEqualByComparingTo("9.6000");
                // 结果仍按四位小数 HALF_UP。
                assertThat(calcPurchase("10.0000", "0.1000", null, "3.3333", "13.0000")).isEqualByComparingTo("12.8443");
            }

            @Test
            void purchaseCostFormulaReturnsUnpricedWhenRatesAreMissingOrInvalid() {
                // 损耗率或税率缺失时不能当 0 计算。
                assertThat(calcPurchase("10.0000", "0.1000", "8.0000", null, "13.0000")).isNull();
                assertThat(calcPurchase("10.0000", "0.1000", "8.0000", "5.0000", null)).isNull();
                assertThat(calcPurchase("10.0000", "0.1000", "8.0000", "-1.0000", "13.0000")).isNull();
                assertThat(calcPurchase("10.0000", "0.1000", "8.0000", "5.0000", "101.0000")).isNull();
                // 最近采购价与配送仓成本都没有时同样不可定价。
                assertThat(calcPurchase(null, "0.1000", null, "5.0000", "13.0000")).isNull();
            }

            private static BigDecimal calcPurchase(String latestPurchasePrice, String parameter, String saleUnitCost,
                    String lossRate, String taxRate) {
                return AgreementFormulaCalculator.calculate(AgreementFormulaType.PURCHASE_COST_LOSS_TAX_MARKUP, null,
                        decimal(parameter), null, decimal(saleUnitCost), decimal(latestPurchasePrice), decimal(lossRate),
                        decimal(taxRate));
            }

            private static void assertPrice(AgreementFormulaType formula, String fixed, String parameter,
                    String market, String cost, String expected) {
                assertThat(calc(formula, fixed, parameter, market, cost)).isEqualByComparingTo(expected);
            }

            private static BigDecimal calc(AgreementFormulaType formula, String fixed, String parameter,
                    String market, String cost) {
                return AgreementFormulaCalculator.calculate(formula, decimal(fixed), decimal(parameter),
                        decimal(market), decimal(cost));
            }

            private static BigDecimal decimal(String value) {
                return value == null ? null : new BigDecimal(value);
            }
        }

        @Nested

        class PriceResolverAgreementFormulaTest {
            private final CustomerService customers = mock(CustomerService.class);
            private final AgreementPriceDao agreements = mock(AgreementPriceDao.class);
            private final CustomerGroupPriceDao groupPrices = mock(CustomerGroupPriceDao.class);
            private final TimePriceDao timePrices = mock(TimePriceDao.class);
            private final ProductSkuOptionDao products = mock(ProductSkuOptionDao.class);
            private final ProductBundleComponentQueryService bundles = mock(ProductBundleComponentQueryService.class);
            private final CustomerSkuVisibilityDao visibility = mock(CustomerSkuVisibilityDao.class);
            private final PricingCostBasisDao costs = mock(PricingCostBasisDao.class);

            private final PriceResolver resolver = new PriceResolver(customers, agreements, groupPrices, timePrices,
                    products, bundles, visibility, costs);

            private AgreementPriceEntity agreement;

            private CustomerEntity customer;

            @BeforeEach
            void setup() {
                customer = new CustomerEntity();
                customer.setId(8L);
                when(customers.requireTradable(8L)).thenReturn(customer);

                var sku = new ProductSkuOptionVO();
                sku.setSkuId(40L);
                sku.setSkuCode("SKU40");
                sku.setSpuId(30L);
                sku.setSaleUnit("斤");
                sku.setStatus("ON_SHELF");
                sku.setSpuStatus("ON_SHELF");
                sku.setCategoryStatus("ENABLED");
                sku.setMarketPrice(new BigDecimal("20.0000"));
                when(products.selectByIds(List.of(40L))).thenReturn(List.of(sku));

                agreement = new AgreementPriceEntity();
                agreement.setId(100L);
                agreement.setVersion(3);
                agreement.setSkuId(40L);
                agreement.setFormulaType("COST_MARKUP_RATE");
                agreement.setFormulaParameter(new BigDecimal("0.2000"));
                when(agreements.selectEffective(eq(8L), eq(List.of(40L)), any(OffsetDateTime.class)))
                        .thenReturn(List.of(agreement));
                when(visibility.policy(8L)).thenReturn(CustomerVisibilityPolicy.ALL_ENABLED);
            }

            @Test
            void noExplicitWarehouseMustNotReadAnyDefaultOrInventCostQuote() {
                var price = resolver.resolve(8L, List.of(40L), OffsetDateTime.now(), null).getFirst();

                assertThat(price.getPriceStatus()).isEqualTo(ScmPriceStatusEnum.UNPRICED);
                assertThat(price.getUnpricedReason()).isEqualTo(ScmUnpricedReasonEnum.AGREEMENT_COST_MISSING);
                assertThat(price.getUnitPrice()).isNull();
                assertThat(price.getPriceBasisSnapshot()).isNull();
                verifyNoInteractions(costs, bundles);
            }

            @Test
            void explicitWarehouseCostIsConvertedIntoSalesUnitAndFrozenInBasis() {
                var cost = new PricingCostBasisVO();
                cost.setSkuId(40L);
                cost.setWarehouseId(7L);
                cost.setStockUnit("kg");
                cost.setStockUnitCost(new BigDecimal("5.0000"));
                cost.setStockUnitsPerSaleUnit(new BigDecimal("2.0000"));
                cost.setBalanceVersion(11);
                when(costs.byWarehouseAndSkus(7L, List.of(40L))).thenReturn(List.of(cost));
                when(bundles.listByBundleSkus(List.of(40L))).thenReturn(List.of());

                var price = resolver.resolve(8L, List.of(40L), OffsetDateTime.now(), 7L).getFirst();

                assertThat(price.getUnitPrice()).isEqualByComparingTo("12.0000");
                assertThat(price.getPriceStatus()).isEqualTo(ScmPriceStatusEnum.PRICED);
                assertThat(price.getPriceBasisSnapshot()).containsEntry("warehouseId", "7")
                        .containsEntry("stockUnitCost", "5.0000")
                        .containsEntry("stockUnitsPerSaleUnit", "2.0000")
                        .containsEntry("inventoryBalanceVersion", "11")
                        .containsEntry("basisUnitPrice", "10.0000")
                        .containsEntry("formulaType", "COST_MARKUP_RATE");
                verify(costs).byWarehouseAndSkus(7L, List.of(40L));
            }

            @Test
            void missingSelectedWarehouseCostIsUnpricedEvenWhenMarketPriceExists() {
                when(costs.byWarehouseAndSkus(7L, List.of(40L))).thenReturn(List.of());
                when(bundles.listByBundleSkus(List.of(40L))).thenReturn(List.of());

                var price = resolver.resolve(8L, List.of(40L), OffsetDateTime.now(), 7L).getFirst();

                assertThat(price.getPriceStatus()).isEqualTo(ScmPriceStatusEnum.UNPRICED);
                assertThat(price.getUnitPrice()).isNull();
                assertThat(price.getUnpricedReason()).isEqualTo(ScmUnpricedReasonEnum.AGREEMENT_COST_MISSING);
            }

            @Test
            void marketMarkupUsesOriginalMarketPriceAndNeverReadsWarehouseCost() {
                agreement.setFormulaType("MARKET_MARKUP_RATE");
                var price = resolver.resolve(8L, List.of(40L), OffsetDateTime.now(), null).getFirst();

                assertThat(price.getUnitPrice()).isEqualByComparingTo("24.0000");
                assertThat(price.getPriceBasisSnapshot()).containsEntry("basisSource", "MARKET_PRICE")
                        .containsEntry("basisUnitPrice", "20.0000");
                verifyNoInteractions(costs, bundles);
            }

            @Test
            void purchaseCostFormulaUsesLatestCommittedPurchaseBeforeWarehouseCost() {
                agreement.setFormulaType("PURCHASE_COST_LOSS_TAX_MARKUP");
                agreement.setFormulaParameter(new BigDecimal("0.1000"));
                var cost = cost("8.0000", "5.0000", "13.0000");
                when(costs.byWarehouseAndSkus(7L, List.of(40L))).thenReturn(List.of(cost));
                when(costs.latestPurchasePrices(List.of(40L), ScmPurchaseStatusEnum.committedNames()))
                        .thenReturn(List.of(latestPurchase("10.0000")));
                when(bundles.listByBundleSkus(List.of(40L))).thenReturn(List.of());

                var price = resolver.resolve(8L, List.of(40L), OffsetDateTime.now(), 7L).getFirst();

                assertThat(price.getUnitPrice()).isEqualByComparingTo("13.0515");
                assertThat(price.getPriceStatus()).isEqualTo(ScmPriceStatusEnum.PRICED);
                assertThat(price.getPriceBasisSnapshot()).containsEntry("basisSource", "LATEST_PURCHASE_PRICE")
                        .containsEntry("basisUnitPrice", "10.0000")
                        .containsEntry("latestPurchasePrice", "10.0000")
                        .containsEntry("lossRate", "5.0000")
                        .containsEntry("taxRate", "13.0000")
                        .containsEntry("formulaType", "PURCHASE_COST_LOSS_TAX_MARKUP");
            }

            @Test
            void purchasePriceInAnotherUnitIsConvertedIntoSaleUnitBeforeApplyingFormula() {
                agreement.setFormulaType("PURCHASE_COST_LOSS_TAX_MARKUP");
                agreement.setFormulaParameter(new BigDecimal("0.1000"));
                when(costs.byWarehouseAndSkus(7L, List.of(40L))).thenReturn(List.of(cost("8.0000", "5.0000", "13.0000")));
                var latest = latestPurchase("100.0000");
                latest.setPurchaseUnit("箱");
                latest.setSaleUnit("斤");
                // 1 斤 = 0.1 箱：100 元/箱折算 10 元/斤
                latest.setStockUnitsPerSaleUnit(new BigDecimal("0.1000"));
                when(costs.latestPurchasePrices(List.of(40L), ScmPurchaseStatusEnum.committedNames()))
                        .thenReturn(List.of(latest));
                when(bundles.listByBundleSkus(List.of(40L))).thenReturn(List.of());

                var price = resolver.resolve(8L, List.of(40L), OffsetDateTime.now(), 7L).getFirst();

                assertThat(price.getUnitPrice()).isEqualByComparingTo("13.0515");
                assertThat(price.getPriceBasisSnapshot())
                        .containsEntry("basisSource", "LATEST_PURCHASE_PRICE")
                        .containsEntry("basisUnitPrice", "10.0000")
                        .containsEntry("latestPurchasePrice", "100.0000")
                        .containsEntry("latestPurchaseUnit", "箱")
                        .containsEntry("latestPurchasePriceSaleUnit", "10.0000");
            }

            @Test
            void purchasePriceInAnotherUnitWithoutConversionFallsBackToWarehouseCost() {
                agreement.setFormulaType("PURCHASE_COST_LOSS_TAX_MARKUP");
                agreement.setFormulaParameter(new BigDecimal("0.1000"));
                when(costs.byWarehouseAndSkus(7L, List.of(40L))).thenReturn(List.of(cost("8.0000", "5.0000", "13.0000")));
                var latest = latestPurchase("100.0000");
                latest.setPurchaseUnit("箱");
                latest.setSaleUnit("斤");
                when(costs.latestPurchasePrices(List.of(40L), ScmPurchaseStatusEnum.committedNames()))
                        .thenReturn(List.of(latest));
                when(bundles.listByBundleSkus(List.of(40L))).thenReturn(List.of());

                var price = resolver.resolve(8L, List.of(40L), OffsetDateTime.now(), 7L).getFirst();

                assertThat(price.getUnitPrice()).isEqualByComparingTo("10.4412");
                assertThat(price.getPriceBasisSnapshot())
                        .containsEntry("basisSource", "WAREHOUSE_WEIGHTED_AVG_COST")
                        .containsEntry("basisUnitPrice", "8.0000")
                        .containsEntry("latestPurchasePriceSaleUnit", null);
            }

            @Test
            void purchaseCostFormulaFallsBackToSaleUnitCostWhenLatestPurchaseMissing() {
                agreement.setFormulaType("PURCHASE_COST_LOSS_TAX_MARKUP");
                agreement.setFormulaParameter(new BigDecimal("0.1000"));
                when(costs.byWarehouseAndSkus(7L, List.of(40L))).thenReturn(List.of(cost("8.0000", "5.0000", "13.0000")));
                when(costs.latestPurchasePrices(List.of(40L), ScmPurchaseStatusEnum.committedNames()))
                        .thenReturn(List.of());
                when(bundles.listByBundleSkus(List.of(40L))).thenReturn(List.of());

                var price = resolver.resolve(8L, List.of(40L), OffsetDateTime.now(), 7L).getFirst();

                assertThat(price.getUnitPrice()).isEqualByComparingTo("10.4412");
                assertThat(price.getPriceBasisSnapshot()).containsEntry("basisSource", "WAREHOUSE_WEIGHTED_AVG_COST")
                        .containsEntry("basisUnitPrice", "8.0000");
            }

            @Test
            void purchaseCostFormulaIsUnpricedWhenProductRateIsMissing() {
                agreement.setFormulaType("PURCHASE_COST_LOSS_TAX_MARKUP");
                when(costs.byWarehouseAndSkus(7L, List.of(40L))).thenReturn(List.of(cost("8.0000", null, "13.0000")));
                when(costs.latestPurchasePrices(List.of(40L), ScmPurchaseStatusEnum.committedNames()))
                        .thenReturn(List.of(latestPurchase("10.0000")));
                when(bundles.listByBundleSkus(List.of(40L))).thenReturn(List.of());

                var price = resolver.resolve(8L, List.of(40L), OffsetDateTime.now(), 7L).getFirst();

                assertThat(price.getPriceStatus()).isEqualTo(ScmPriceStatusEnum.UNPRICED);
                assertThat(price.getUnpricedReason()).isEqualTo(ScmUnpricedReasonEnum.AGREEMENT_RESULT_INVALID);
            }

            @Test
            void purchaseCostFormulaIsUnpricedWhenBothLatestPurchaseAndWarehouseCostAreMissing() {
                agreement.setFormulaType("PURCHASE_COST_LOSS_TAX_MARKUP");
                when(costs.byWarehouseAndSkus(7L, List.of(40L))).thenReturn(List.of(cost(null, "5.0000", "13.0000")));
                when(costs.latestPurchasePrices(List.of(40L), ScmPurchaseStatusEnum.committedNames()))
                        .thenReturn(List.of());
                when(bundles.listByBundleSkus(List.of(40L))).thenReturn(List.of());

                var price = resolver.resolve(8L, List.of(40L), OffsetDateTime.now(), 7L).getFirst();

                assertThat(price.getPriceStatus()).isEqualTo(ScmPriceStatusEnum.UNPRICED);
                assertThat(price.getUnpricedReason()).isEqualTo(ScmUnpricedReasonEnum.AGREEMENT_COST_MISSING);
            }

            @Test
            void groupPriceAppliesByProductAndSaleUnitAboveTimePrice() {
                customer.setCustomerGroupId(9L);
                when(agreements.selectEffective(eq(8L), eq(List.of(40L)), any(OffsetDateTime.class)))
                        .thenReturn(List.of());
                var groupPrice = new CustomerGroupPriceEntity();
                groupPrice.setId(200L);
                groupPrice.setVersion(0);
                groupPrice.setGroupId(9L);
                groupPrice.setSpuId(30L);
                groupPrice.setSaleUnit("斤");
                groupPrice.setUnitPrice(new BigDecimal("12.0000"));
                when(groupPrices.selectEffective(eq(9L), eq(List.of(30L)), any(OffsetDateTime.class)))
                        .thenReturn(List.of(groupPrice));
                var price = resolver.resolve(8L, List.of(40L), OffsetDateTime.now()).getFirst();

                assertThat(price.getPriceSource()).isEqualTo(ScmPriceSourceEnum.CUSTOMER_GROUP);
                assertThat(price.getSourceRecordId()).isEqualTo(200L);
                assertThat(price.getUnitPrice()).isEqualByComparingTo("12");
            }

            @Test
            void groupPriceDoesNotCrossSaleUnits() {
                customer.setCustomerGroupId(9L);
                when(agreements.selectEffective(eq(8L), eq(List.of(40L)), any(OffsetDateTime.class)))
                        .thenReturn(List.of());
                var groupPrice = new CustomerGroupPriceEntity();
                groupPrice.setId(200L);
                groupPrice.setSpuId(30L);
                groupPrice.setSaleUnit("箱");
                groupPrice.setUnitPrice(new BigDecimal("99.0000"));
                when(groupPrices.selectEffective(eq(9L), eq(List.of(30L)), any(OffsetDateTime.class)))
                        .thenReturn(List.of(groupPrice));

                var price = resolver.resolve(8L, List.of(40L), OffsetDateTime.now()).getFirst();

                // 斤与箱是不同单位，不做隐式换算，回落到市场价。
                assertThat(price.getPriceSource()).isEqualTo(ScmPriceSourceEnum.MARKET);
                assertThat(price.getUnitPrice()).isEqualByComparingTo("20");
            }

            private static PricingCostBasisVO cost(String saleUnitCost, String lossRate, String taxRate) {
                var cost = new PricingCostBasisVO();
                cost.setSkuId(40L);
                cost.setWarehouseId(7L);
                cost.setStockUnit("kg");
                cost.setStockUnitCost(saleUnitCost == null ? null : new BigDecimal(saleUnitCost));
                cost.setStockUnitsPerSaleUnit(BigDecimal.ONE);
                cost.setBalanceVersion(11);
                cost.setLossRate(lossRate == null ? null : new BigDecimal(lossRate));
                cost.setTaxRate(taxRate == null ? null : new BigDecimal(taxRate));
                return cost;
            }

            private static PricingCostBasisVO latestPurchase(String purchasePrice) {
                var price = new PricingCostBasisVO();
                price.setSkuId(40L);
                price.setLatestPurchasePrice(new BigDecimal(purchasePrice));
                price.setPurchaseUnit("斤");
                price.setSaleUnit("斤");
                return price;
            }
        }

        @Nested

        class PriceResolverTest {
            @Test
            void unavailableDoesNotMeanUnpriced() {
                var r = new ResolvedPriceVO();
                r.setSellable(false);
                r.setUnavailableReason(ScmUnavailableReasonEnum.SKU_OFF_SHELF);
                r.price(new BigDecimal("5.0000"), ScmPriceSourceEnum.MARKET, null);
                assertThat(r.getPriceStatus()).isEqualTo(ScmPriceStatusEnum.PRICED);
                assertThat(r.getUnitPrice()).isEqualByComparingTo("5");
                assertThat(r.getUnpricedReason()).isNull();
                assertThat(r.isSellable()).isFalse();
            }

            @Test
            void zeroIsPricedAndMissingSourceAloneIsUnpriced() {
                var r = new ResolvedPriceVO();
                r.price(BigDecimal.ZERO, ScmPriceSourceEnum.MARKET, null);
                assertThat(r.getPriceStatus()).isEqualTo(ScmPriceStatusEnum.PRICED);
                r.price(null, ScmPriceSourceEnum.MARKET, null);
                assertThat(r.getPriceStatus()).isEqualTo(ScmPriceStatusEnum.UNPRICED);
                assertThat(r.getUnpricedReason()).isEqualTo(ScmUnpricedReasonEnum.NO_PRICE_SOURCE);
                assertThat(r.getUnitPrice()).isNull();
                assertThat(r.getPriceSource()).isNull();
                // 新公式可以引入更细的未定价原因，但不影响缺少价格来源的既有语义。
                assertThat(ScmUnpricedReasonEnum.values()).contains(ScmUnpricedReasonEnum.NO_PRICE_SOURCE);
                // 时价压在市场价之上；客户分组价又压在时价之上。
                assertThat(ScmPriceSourceEnum.values()).hasSize(5);
                assertThat(ScmPriceSourceEnum.values()).contains(ScmPriceSourceEnum.TIME_PRICE,
                    ScmPriceSourceEnum.CUSTOMER_GROUP);
            }

            @Test
            void fiveUnavailableReasonsAreIndependent() {
                assertThat(PriceValidation.unavailable(null, true)).isEqualTo(ScmUnavailableReasonEnum.SKU_NOT_FOUND);
                var s = new ProductSkuOptionVO();
                assertThat(PriceValidation.unavailable(s, true)).isEqualTo(ScmUnavailableReasonEnum.SKU_OFF_SHELF);
                s.setStatus("ON_SHELF");
                assertThat(PriceValidation.unavailable(s, true)).isEqualTo(ScmUnavailableReasonEnum.SPU_OFF_SHELF);
                s.setSpuStatus("ON_SHELF");
                assertThat(PriceValidation.unavailable(s, true)).isEqualTo(ScmUnavailableReasonEnum.CATEGORY_DISABLED);
                s.setCategoryStatus("ENABLED");
                assertThat(PriceValidation.unavailable(s, false)).isEqualTo(ScmUnavailableReasonEnum.NOT_VISIBLE);
                assertThat(PriceValidation.unavailable(s, true)).isNull();
            }

            @Test
            void periodsAndDecimalLimits() {
                var from = OffsetDateTime.parse("2026-09-01T00:00:00Z");
                PriceValidation.amountAndPeriod("0.0000", from, null);
                assertThatThrownBy(() -> PriceValidation.amountAndPeriod("-1", from, null)).hasMessageContaining("价格");
                assertThatThrownBy(() -> PriceValidation.amountAndPeriod("1.00001", from, null)).hasMessageContaining("价格");
                assertThatThrownBy(() -> PriceValidation.amountAndPeriod("1", from, from)).hasMessageContaining("结束时间");
            }
        }

        @Nested

        class ScmPriceSourceLabelTest {
            @Test
            @DisplayName("每一种价格来源都有中文展示名")
            void everySourceHasLabel() {
                for (ScmPriceSourceEnum source : ScmPriceSourceEnum.values()) {
                    assertThat(source.getLabel()).as(source.name()).isNotBlank();
                }
                assertThat(ScmPriceSourceEnum.AGREEMENT.getLabel()).isEqualTo("客户协议价");
                assertThat(ScmPriceSourceEnum.CUSTOMER_GROUP.getLabel()).isEqualTo("客户分组价");
                assertThat(ScmPriceSourceEnum.TIME_PRICE.getLabel()).isEqualTo("时价");
                assertThat(ScmPriceSourceEnum.MARKET.getLabel()).isEqualTo("市场价");
                assertThat(ScmPriceSourceEnum.LEGACY.getLabel()).isEqualTo("历史价格");
            }

            @Test
            @DisplayName("枚举名转展示名；未知值原样返回，空值透传")
            void labelOfTranslatesAndPassthrough() {
                assertThat(ScmPriceSourceEnum.labelOf("TIME_PRICE")).isEqualTo("时价");
                assertThat(ScmPriceSourceEnum.labelOf("NOT_A_SOURCE")).isEqualTo("NOT_A_SOURCE");
                assertThat(ScmPriceSourceEnum.labelOf(null)).isNull();
                assertThat(ScmSettleModeEnum.labelOf("GROUP")).isEqualTo("集团结算");
                assertThat(ScmSettleModeEnum.labelOf("INDEPENDENT")).isEqualTo("独立结算");
            }
        }
    }

    @Nested
    class PricingW5PgIT extends ScmW5PgITBase {
        @Nested

        class CustomerGroupPricePgIT {
            @Autowired
            private CustomerGroupService customerGroupService;

            @Autowired
            private CustomerGroupPriceService customerGroupPriceService;

            @Autowired
            private CustomerGroupPriceQueryService customerGroupPriceQueryService;

            @Autowired
            private PriceResolver priceResolver;

            @Autowired
            private PriceHistoryQueryService priceHistoryQueryService;

            @Test
            @DisplayName("#1 组内同单位规格共用一个分组价，组外客户仍走市场价")
            void groupPriceCoversEverySpecInSameUnit() {
                SkuPair skus = newSpuWithTwoSkus("CGP1");
                Long member = newCustomer();
                Long outsider = newCustomer();
                Group group = createGroup("CGP1", member);
                OffsetDateTime now = OffsetDateTime.now();

                Long groupPriceId = addPrice(group.id(), skus.first(), "3.3000",
                    now.minusHours(1), now.plusHours(2));

                List<ResolvedPriceVO> memberPrices = priceResolver.resolve(member,
                    List.of(skus.first(), skus.second()), now);
                assertThat(memberPrices).hasSize(2).allSatisfy(price -> {
                    assertThat(price.getPriceSource()).isEqualTo(ScmPriceSourceEnum.CUSTOMER_GROUP);
                    assertThat(price.getUnitPrice()).isEqualByComparingTo("3.3000");
                    assertThat(price.getSourceRecordId()).isEqualTo(groupPriceId);
                });

                ResolvedPriceVO outsiderPrice = priceResolver.resolve(outsider, List.of(skus.first()), now).getFirst();
                assertThat(outsiderPrice.getPriceSource()).isEqualTo(ScmPriceSourceEnum.MARKET);
                assertThat(outsiderPrice.getUnitPrice()).isEqualByComparingTo("1.2000");

                // 列表按商品维度展示，详情回填任一可用规格供编辑；不按规格复制成两行
                var page = customerGroupPriceQueryService.query(query(group.id()));
                assertThat(page.getList()).hasSize(1);
                assertThat(page.getList().getFirst().getGroupName()).isEqualTo(group.name());
                assertThat(page.getList().getFirst().getSaleUnit()).isEqualTo("kg");
            }

            @Test
            @DisplayName("#2 同分组/商品/单位区间重叠被拒（40956），首尾相接允许")
            void overlappingPeriodIsRejected() {
                Long skuId = newOnShelfSku("CGP2");
                Long member = newCustomer();
                Group group = createGroup("CGP2", member);
                OffsetDateTime now = OffsetDateTime.now();
                addPrice(group.id(), skuId, "2.0000", now.minusHours(2), now.plusHours(2));

                expectCode(() -> addPrice(group.id(), skuId, "2.5000", now.plusHours(1), now.plusHours(3)), 40956);

                Long adjacentId = addPrice(group.id(), skuId, "2.8000", now.plusHours(2), now.plusHours(4));
                assertThat(adjacentId).isNotNull();
            }

            @Test
            @DisplayName("#3 停用分组后不再取价，也不能再新增分组价（40435）")
            void disabledGroupIsIgnored() {
                Long skuId = newOnShelfSku("CGP3");
                Long member = newCustomer();
                Group group = createGroup("CGP3", member);
                OffsetDateTime now = OffsetDateTime.now();
                addPrice(group.id(), skuId, "4.0000", now.minusHours(1), now.plusHours(2));

                CustomerGroupVO detail = customerGroupService.detail(group.id());
                CustomerGroupUpdateForm update = new CustomerGroupUpdateForm();
                update.setGroupId(group.id());
                update.setVersion(detail.getVersion());
                update.setGroupCode(detail.getGroupCode());
                update.setGroupName(detail.getGroupName());
                update.setStatus("DISABLED");
                update.setCustomerIds(List.of(member));
                customerGroupService.update(update);

                ResolvedPriceVO price = priceResolver.resolve(member, List.of(skuId), now).getFirst();
                assertThat(price.getPriceSource()).isEqualTo(ScmPriceSourceEnum.MARKET);

                expectCode(() -> addPrice(group.id(), skuId, "4.5000", now.plusHours(3), now.plusHours(4)), 40435);
            }

            @Test
            @DisplayName("#4 客户是原分组唯一成员时不能移入新分组（40957）")
            void lastMemberCannotBeMovedOut() {
                Long onlyMember = newCustomer();
                Group first = createGroup("CGP4A", onlyMember);

                CustomerGroupAddForm second = new CustomerGroupAddForm();
                second.setGroupCode(prefix + "CGP4B");
                second.setGroupName(prefix + "第二组");
                second.setStatus("ENABLED");
                second.setCustomerIds(List.of(onlyMember));

                expectCode(() -> customerGroupService.add(second), 40957);
                assertThat(jdbc.queryForObject(
                    "SELECT customer_group_id FROM xsy_customer WHERE id = ? AND deleted = FALSE",
                    Long.class, onlyMember)).isEqualTo(first.id());
            }

            @Test
            @DisplayName("#5 删除分组会移出成员并立即停止取价，历史仍可按分组查")
            void deletingGroupDetachesMembersAndKeepsHistory() {
                Long skuId = newOnShelfSku("CGP5");
                Long firstMember = newCustomer();
                Long secondMember = newCustomer();
                Group group = createGroup("CGP5", firstMember, secondMember);
                OffsetDateTime now = OffsetDateTime.now();
                addPrice(group.id(), skuId, "5.6000", now.minusHours(1), now.plusHours(2));

                CustomerGroupDeleteForm delete = new CustomerGroupDeleteForm();
                delete.setGroupId(group.id());
                delete.setVersion(customerGroupService.detail(group.id()).getVersion());
                customerGroupService.delete(delete);

                assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM xsy_customer WHERE id IN (?, ?) AND customer_group_id IS NOT NULL",
                    Integer.class, firstMember, secondMember)).isZero();
                assertThat(priceResolver.resolve(firstMember, List.of(skuId), now).getFirst().getPriceSource())
                    .isEqualTo(ScmPriceSourceEnum.MARKET);

                PriceHistoryQueryForm history = new PriceHistoryQueryForm();
                history.setPageNum(1L);
                history.setPageSize(20L);
                history.setSource("CUSTOMER_GROUP");
                history.setGroupId(group.id());
                var page = priceHistoryQueryService.query(history);
                assertThat(page.getList()).isNotEmpty();
                PriceHistoryVO record = page.getList().getFirst();
                assertThat(record.getOperationType()).isEqualTo("CREATE");
                assertThat(record.getGroupName()).isEqualTo(group.name());
                assertThat(record.getCurrentUnitPrice()).isEqualByComparingTo("5.6000");
            }

            // ------------------------------------------------------------------
            // 夹具
            // ------------------------------------------------------------------

            private record Group(Long id, String name) {
            }

            private record SkuPair(Long first, Long second) {
            }

            private Group createGroup(String suffix, Long... customerIds) {
                CustomerGroupAddForm form = new CustomerGroupAddForm();
                form.setGroupCode(prefix + "G" + suffix);
                form.setGroupName(prefix + "分组" + suffix);
                form.setStatus("ENABLED");
                form.setCustomerIds(List.of(customerIds));
                Long groupId = customerGroupService.add(form);
                return new Group(groupId, form.getGroupName());
            }

            private Long addPrice(Long groupId, Long skuId, String unitPrice, OffsetDateTime from, OffsetDateTime to) {
                CustomerGroupPriceAddForm form = new CustomerGroupPriceAddForm();
                form.setGroupId(groupId);
                form.setSkuId(skuId);
                form.setUnitPrice(unitPrice);
                form.setEffectiveFrom(from);
                form.setEffectiveTo(to);
                return customerGroupPriceService.add(form);
            }

            private CustomerGroupPriceQueryForm query(Long groupId) {
                var form = new CustomerGroupPriceQueryForm();
                form.setPageNum(1L);
                form.setPageSize(20L);
                form.setGroupId(groupId);
                return form;
            }

            /**
             * 同一商品下两个规格、同一销售单位：分组价必须同时覆盖两者。
             */
            private SkuPair newSpuWithTwoSkus(String suffix) {
                ProductSpuAddForm form = new ProductSpuAddForm();
                form.setSpuCode(prefix + suffix);
                form.setName(prefix + suffix + "商品");
                form.setStatus("ON_SHELF");
                form.setCategoryId(jdbc.queryForObject(
                    "SELECT id FROM xsy_product_category WHERE category_code = 'FRESH-FRUIT' AND deleted = FALSE", Long.class));

                ProductSkuForm first = sku(suffix + "-A", "规格A", true);
                ProductSkuForm second = sku(suffix + "-B", "规格B", false);
                form.setSkuList(new ArrayList<>(List.of(first, second)));

                Long spuId = productSpuService.add(form);
                List<Long> ids = jdbc.queryForList(
                    "SELECT id FROM xsy_product_sku WHERE spu_id = ? AND deleted = FALSE ORDER BY id", Long.class, spuId);
                assertThat(ids).hasSize(2);
                return new SkuPair(ids.get(0), ids.get(1));
            }

            private ProductSkuForm sku(String suffix, String specName, boolean defaultFlag) {
                ProductSkuForm sku = new ProductSkuForm();
                sku.setSkuCode(prefix + suffix);
                sku.setSpecName(specName);
                sku.setSpecValues(new LinkedHashMap<>(Map.of("规格", suffix)));
                sku.setSaleUnit("kg");
                sku.setProductType("NON_STANDARD");
                sku.setMarketPrice(new BigDecimal("1.2000"));
                sku.setStatus("ON_SHELF");
                sku.setDefaultFlag(defaultFlag);
                return sku;
            }
        }

        @Nested

        class TimePricePgIT {
            @Autowired
            private TimePriceService timePriceService;

            @Test
            @DisplayName("#1 新增时价后可查询，生效中状态按当前时刻判定")
            void createAndQuery() {
                Long skuId = newOnShelfSku("TP1");
                OffsetDateTime now = OffsetDateTime.now();
                TimePriceVO created = timePriceService.create(
                        form(skuId, "9.9000", now.minusHours(1), now.plusHours(1), "临调"), "W9 IT");

                assertThat(created.getId()).isNotNull();
                assertThat(created.getUnitPrice()).isEqualByComparingTo("9.9000");

                TimePriceQueryForm query = new TimePriceQueryForm();
                query.setPageNum(1L);
                query.setPageSize(20L);
                query.setSkuId(skuId);
                var page = timePriceService.query(query);
                assertThat(page.getList()).hasSize(1);
                assertThat(page.getList().getFirst().getEffectiveNow()).isTrue();

                // 时价必须留审计：谁在什么时候把哪个规格的临时价改成了多少
                Integer logs = jdbc.queryForObject(
                        "SELECT count(*) FROM xsy_price_operation_log WHERE price_type = 'TIME_PRICE' AND price_id = ? "
                                + "AND operation_type = 'CREATE'", Integer.class, created.getId());
                assertThat(logs).as("新增时价要写一条 CREATE 审计").isEqualTo(1);
            }

            @Test
            @DisplayName("#2 同规格区间重叠被拒（40052），区间倒置被拒（40051）")
            void rejectsOverlapAndInvalidWindow() {
                Long skuId = newOnShelfSku("TP2");
                OffsetDateTime now = OffsetDateTime.now();
                timePriceService.create(form(skuId, "5.0000", now.minusHours(2), now.plusHours(2), null), "W9 IT");

                assertThatThrownBy(() -> timePriceService.create(
                        form(skuId, "6.0000", now.plusHours(1), now.plusHours(3), null), "W9 IT"))
                        .isInstanceOf(ScmBusinessException.class)
                        .satisfies(error -> assertThat(((ScmBusinessException) error).getErrorCode().getCode())
                                .isEqualTo(40052));

                assertThatThrownBy(() -> timePriceService.create(
                        form(skuId, "6.0000", now.plusHours(5), now.plusHours(4), null), "W9 IT"))
                        .isInstanceOf(ScmBusinessException.class)
                        .satisfies(error -> assertThat(((ScmBusinessException) error).getErrorCode().getCode())
                                .isEqualTo(40051));

                // 首尾相接不算重叠：半开区间 [start, end)
                TimePriceVO adjacent = timePriceService.create(
                        form(skuId, "7.0000", now.plusHours(2), now.plusHours(4), null), "W9 IT");
                assertThat(adjacent.getId()).isNotNull();
            }

            private TimePriceForm form(Long skuId, String price, OffsetDateTime startAt, OffsetDateTime endAt,
                    String remark) {
                TimePriceForm form = new TimePriceForm();
                form.setSkuId(skuId);
                form.setUnitPrice(new BigDecimal(price));
                form.setStartAt(startAt);
                form.setEndAt(endAt);
                form.setRemark(remark);
                return form;
            }
        }
    }

    @Nested
    @WebMvcTest({AgreementPriceController.class, PriceHistoryController.class, PriceResolveController.class,
            CustomerVisibilityController.class, ProductSkuController.class})
    @AutoConfigureMockMvc(addFilters = false)
    @ContextConfiguration(classes = {AgreementPriceController.class, PriceHistoryController.class,
            PriceResolveController.class, CustomerVisibilityController.class, ProductSkuController.class,
            ScmExceptionHandler.class, GlobalExceptionHandler.class})
    class PricingWebTest {
        @Autowired
        MockMvc mvc;
        @MockitoBean
        AgreementPriceService agreementService;
        @MockitoBean
        AgreementPriceQueryService agreementQueries;
        @MockitoBean
        PriceHistoryQueryService historyService;
        @MockitoBean
        PriceResolver resolver;
        @MockitoBean
        CustomerService customers;
        @MockitoBean
        CustomerSkuVisibilityService visibilityService;
        @MockitoBean
        ProductSkuOptionQueryService skuService;
        @MockitoBean
        ProductSkuStockConversionService skuConversionService;
        @MockitoBean
        ScmWarehouseScopeGuard warehouseScopeGuard;
        @MockitoBean(name = "systemEnvironment")
        SystemEnvironment environment;

        private static <T> PageResult<T> emptyPage() {
            PageResult<T> page = new PageResult<>();
            page.setPageNum(1L);
            page.setPageSize(20L);
            page.setTotal(0L);
            page.setPages(0L);
            page.setList(List.of());
            page.setEmptyFlag(true);
            return page;
        }

        @Test
        void agreementQueryReturnsXSYPageEnvelope() throws Exception {
            when(agreementQueries.query(any())).thenReturn(emptyPage());
            mvc.perform(post("/scm/pricing/agreement-price/query").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"pageNum\":1,\"pageSize\":20}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.list").isArray());
        }

        @Test
        void historyNormalizesInvalidSourceAndReturnsPage() throws Exception {
            when(historyService.query(any())).thenReturn(emptyPage());
            mvc.perform(post("/scm/pricing/history/query").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"pageNum\":1,\"pageSize\":20,\"source\":\"UNKNOWN\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        }

        @Test
        void resolveRequiresCustomerAndSkuIds() throws Exception {
            mvc.perform(post("/scm/pricing/resolve").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"customerId\":1,\"skuIds\":[]}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(30001));
        }

        @Test
        void resolveRejectsCustomerOutsideCurrentScope() throws Exception {
            when(customers.requireInCurrentScope(1L)).thenThrow(new ScmDataScopeException());
            mvc.perform(post("/scm/pricing/resolve").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"customerId\":1,\"skuIds\":[2]}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(30005));
            verifyNoInteractions(resolver);
        }

        @Test
        void visibilityAndSkuOptionValidateRequests() throws Exception {
            mvc.perform(post("/scm/customer/visibility/reverse/query").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"pageNum\":1,\"pageSize\":20,\"visibilityPolicy\":\"BROKEN\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(30001));
            mvc.perform(post("/scm/product/sku/option-list").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"limit\":201}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(30001));
        }
    }

    @Nested
    @SpringBootTest(classes = XsyApplication.class, properties = {
            "project.log-directory=" + PgITPaths.DEFAULT_LOG_DIR,
            "file.storage.local.upload-path=" + PgITPaths.DEFAULT_UPLOAD_PATH,
            "file.storage.local.url-prefix=http://127.0.0.1:18082",
            "logging.level.root=WARN"})
    @Transactional
    class ScmAgreementFormulaMigrationPgIT {
        @Autowired
        private JdbcTemplate jdbc;

        @Test
        void legacyFixedPriceKeepsDefaultFormulaType() {
            jdbc.update("INSERT INTO xsy_customer_agreement_price "
                    + "(id, customer_id, sku_id, unit_price, effective_from) "
                    + "VALUES (91110, 90001, 80001, 8.5000, now())");
            assertThat(jdbc.queryForObject("SELECT formula_type FROM xsy_customer_agreement_price "
                    + "WHERE id=91110", String.class)).isEqualTo("FIXED");
        }

        @Test
        void costMarkupAndMarketMarkupPermitNullFixedPrice() {
            jdbc.update("INSERT INTO xsy_customer_agreement_price "
                    + "(id, customer_id, sku_id, unit_price, formula_type, formula_parameter, effective_from) "
                    + "VALUES (91111, 90001, 80002, null, 'COST_MARKUP_RATE', 0.2000, now())");
            jdbc.update("INSERT INTO xsy_customer_agreement_price "
                    + "(id, customer_id, sku_id, unit_price, formula_type, formula_parameter, effective_from) "
                    + "VALUES (91112, 90001, 80003, null, 'MARKET_MARKUP_RATE', 0.1000, now())");
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM xsy_customer_agreement_price WHERE id IN (91111,91112)"
                    + " AND unit_price IS NULL", Integer.class)).isEqualTo(2);
        }

        @Test
        void purchaseCostLossTaxMarkupPermitsNullFixedPrice() {
            jdbc.update("INSERT INTO xsy_customer_agreement_price "
                    + "(id, customer_id, sku_id, unit_price, formula_type, formula_parameter, effective_from) "
                    + "VALUES (91116, 90001, 80007, null, 'PURCHASE_COST_LOSS_TAX_MARKUP', 0.1000, now())");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_customer_agreement_price "
                    + "WHERE id=91116 AND unit_price IS NULL", Integer.class)).isEqualTo(1);
        }
    }

    @Nested
    class ScmPricingMigrationIT extends ScmBaselineMigrationPgITBase {
        @Autowired
        Flyway flyway;

        @Test
        void validatesMigrationsAndPricingSchema() throws Exception {
            flyway.validate();
            try (var connection = jdbc.getDataSource().getConnection(); var statement = connection.createStatement()) {
                PgITMigrationHistory.assertCovered(statement, "10", "11");
            }
            assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema=current_schema() AND table_name IN ('xsy_customer_agreement_price','xsy_price_operation_log','xsy_customer_sku_visibility')", Integer.class)).isEqualTo(3);
            assertThat(jdbc.queryForObject("SELECT is_nullable FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='xsy_product_sku' AND column_name='market_price'", String.class)).isEqualTo("NO");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_constraint WHERE connamespace=current_schema()::regnamespace AND contype='x'", Integer.class)).isZero();
            // 最终模块基线只初始化现行菜单，退役的时价入口不保留软删占位行。
            assertThat(jdbc.queryForList("SELECT menu_id FROM t_menu WHERE menu_id IN (425,435,486,487,501,502,503,504,505,506,511,512,513,514,521,522,523,524,525,531,541) ORDER BY menu_id", Long.class))
                .containsExactly(425L,435L,486L,501L,502L,505L,506L,511L,512L,513L,514L,531L,541L);
            assertThat(jdbc.queryForObject("SELECT api_perms FROM t_menu WHERE menu_id=511", String.class))
                .isEqualTo(com.xsy.scm.pricing.permission.PricingPermission.AGREEMENT_QUERY);
        }
    }
}
