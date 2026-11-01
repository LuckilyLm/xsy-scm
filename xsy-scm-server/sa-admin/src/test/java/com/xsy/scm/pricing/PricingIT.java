package com.xsy.scm.pricing;

import com.xsy.scm.common.ScmW3PgITBase;
import com.xsy.scm.pricing.service.*;
import com.xsy.scm.pricing.domain.form.*;
import com.xsy.scm.pricing.constant.*;
import com.xsy.scm.customer.service.*;
import com.xsy.scm.customer.domain.form.*;
import com.xsy.scm.product.service.ProductSkuOptionQueryService;
import com.xsy.scm.product.domain.form.ProductSkuOptionQueryForm;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartRequestUtil;

import static org.assertj.core.api.Assertions.*;

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
                    SmartRequestUtil.remove();
                    return null;
                },
                () -> {
                    setThreadOperator();
                    start.await();
                    agreements.add(agreement(customerId, sku, "8.0000", AT, AT.plusDays(2)));
                    SmartRequestUtil.remove();
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
        SmartRequestUtil.setRequestUser(employee);
    }
}
