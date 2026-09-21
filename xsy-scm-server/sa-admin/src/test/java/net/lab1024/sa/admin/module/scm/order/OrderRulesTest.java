package net.lab1024.sa.admin.module.scm.order;

import net.lab1024.sa.admin.module.scm.order.manager.*;
import net.lab1024.sa.admin.module.scm.order.domain.entity.*;
import net.lab1024.sa.admin.module.scm.order.domain.form.*;
import net.lab1024.sa.admin.module.scm.order.constant.*;
import net.lab1024.sa.admin.module.scm.order.service.OrderNumberGenerator;
import net.lab1024.sa.admin.module.scm.order.support.OrderIdempotencyRequestHasher;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

class OrderRulesTest {
    @ParameterizedTest
    @CsvSource({"DRAFT,DRAFT,false", "DRAFT,PENDING,true", "DRAFT,CONFIRMED,false", "DRAFT,CANCELLED,true", "PENDING,DRAFT,false", "PENDING,PENDING,false", "PENDING,CONFIRMED,true", "PENDING,CANCELLED,true", "CONFIRMED,DRAFT,false", "CONFIRMED,PENDING,false", "CONFIRMED,CONFIRMED,false", "CONFIRMED,CANCELLED,false", "CANCELLED,DRAFT,false", "CANCELLED,PENDING,false", "CANCELLED,CONFIRMED,false", "CANCELLED,CANCELLED,false"})
    void stateGraph(String from, String to, boolean allowed) {
        assertThat(OrderStateMachine.canTransition(from, to)).isEqualTo(allowed);
    }

    @Test
    void nullPropagatesButFreePricesAndHalfUpRemainValid() {
        assertThat(OrderAmountCalculator.lineAmount(new BigDecimal("3.0000"), null)).isNull();
        assertThat(OrderAmountCalculator.orderAmount(Arrays.asList(BigDecimal.ONE, null))).isNull();
        assertThat(OrderAmountCalculator.lineAmount(new BigDecimal("1.0005"), new BigDecimal("0.1000"))).isEqualByComparingTo("0.1001");
        assertThat(OrderAmountCalculator.lineAmount(new BigDecimal("3.0000"), BigDecimal.ZERO)).isEqualByComparingTo("0.0000");
        assertThatThrownBy(() -> OrderAmountCalculator.lineAmount(new BigDecimal("99999999999999.9999"), BigDecimal.TEN)).isInstanceOf(ScmBusinessException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "1.00000", "-1.0000", "1e4", "100000000000000.0000", "0.0000"})
    void rejectsInvalidQuantity(String value) {
        assertThatThrownBy(() -> OrderValidator.decimal(value, true)).isInstanceOf(ScmBusinessException.class);
    }

    @Test
    void diffKeepsIdentityAndRejectsStaleOrForeignItems() {
        var old = new SalesOrderItemEntity();
        old.setId(7L);
        old.setVersion(2);
        var retained = new SalesOrderItemEntity();
        retained.setId(7L);
        retained.setVersion(2);
        var added = new SalesOrderItemEntity();
        var diff = SalesOrderItemChangeSet.between(List.of(old), List.of(retained, added));
        assertThat(diff.updated()).containsExactly(retained);
        assertThat(diff.inserted()).containsExactly(added);
        retained.setVersion(1);
        assertThatThrownBy(() -> SalesOrderItemChangeSet.between(List.of(old), List.of(retained))).isInstanceOf(ScmBusinessException.class);
        retained.setId(8L);
        assertThatThrownBy(() -> SalesOrderItemChangeSet.between(List.of(old), List.of(retained))).isInstanceOf(ScmBusinessException.class);
        assertThat(SalesOrderItemChangeSet.between(List.of(old), List.of()).removed()).containsExactly(old);
    }

    @Test
    void canonicalHashHandlesNestedKeysAndDecimals() {
        var h = new OrderIdempotencyRequestHasher(new ObjectMapper());
        assertThat(h.hash(Map.of("b", List.of(Map.of("p", "1.5000")), "a", 2))).isEqualTo(h.hash(Map.of("a", 2, "b", List.of(Map.of("p", "1.5")))));
        assertThat(h.hash(Map.of("p", "0.0000"))).isNotEqualTo(h.hash(Collections.singletonMap("p", null)));
    }

    @Test
    void numberingExpandsNaturallyAndPriceEnumHasNoUnpriced() {
        assertThat(OrderNumberGenerator.format("SO", 999999)).matches("SO[0-9]{8}999999");
        assertThat(OrderNumberGenerator.format("RT", 1000000)).matches("RT[0-9]{8}1000000");
        assertThat(ScmOrderPriceSourceEnum.values()).extracting(Enum::name).containsExactly("AGREEMENT", "CUSTOMER_TYPE", "MARKET", "OVERRIDE");
    }

    @Test
    void draftValidationEnforcesSourceDuplicateAndOverrideRules() {
        var f = new SalesOrderAddForm();
        f.setOrderSource("ADMIN");
        var i = new SalesOrderItemForm();
        i.setSkuId(1L);
        i.setOrderedQuantity("1.0000");
        i.setManualPriceOverride(false);
        f.setItems(List.of(i));
        OrderValidator.draft(f);
        f.setItems(List.of(i, i));
        assertThatThrownBy(() -> OrderValidator.draft(f)).isInstanceOf(ScmBusinessException.class);
        f.setItems(List.of(i));
        i.setManualPriceOverride(true);
        i.setUnitPrice("0.0000");
        assertThatThrownBy(() -> OrderValidator.draft(f)).isInstanceOf(ScmBusinessException.class);
        i.setOverrideReason("赠送");
        OrderValidator.draft(f);
        f.setOriginalOrderId(9L);
        assertThatThrownBy(() -> OrderValidator.draft(f)).isInstanceOf(ScmBusinessException.class);
        f.setOrderSource("SUPPLEMENT");
        f.setSupplementReason("补录");
        OrderValidator.draft(f);
        assertThatThrownBy(() -> OrderValidator.reason(" ", OrderErrorCode.ORDER_CANCEL_REASON_REQUIRED)).isInstanceOf(ScmBusinessException.class);
    }

    @Test
    void newCodesDoNotCollideWithExistingDomains() {
        var seen = new HashSet<Integer>();
        for (var c : net.lab1024.sa.admin.module.scm.pricing.constant.PricingErrorCode.values()) seen.add(c.getCode());
        for (var c : net.lab1024.sa.admin.module.scm.customer.constant.CustomerErrorCode.values())
            seen.add(c.getCode());
        for (var c : net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.values()) seen.add(c.getCode());
        for (var c : OrderErrorCode.values()) assertThat(seen.add(c.getCode())).as(c.name()).isTrue();
    }
}
