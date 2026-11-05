package com.xsy.scm.common;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.customer.domain.form.CustomerAddForm;
import com.xsy.scm.customer.service.CustomerService;
import com.xsy.scm.finance.dao.FinancePayableDao;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 当前结构和服务路径验收：移除SQL业务保护后，后端仍维护同一套记录规则。 */
class ScmBackendRulesPgIT extends ScmW6PgITBase {
    @Autowired
    private CustomerService customers;
    @Autowired
    private FinancePayableDao payables;

    @Test
    void latestSchemaHasNoBusinessGuardsAndKeepsStructuralConstraints() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_constraint WHERE connamespace='xsy_v2'::regnamespace AND contype='c'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid WHERE c.relnamespace='xsy_v2'::regnamespace AND NOT t.tgisinternal", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_proc WHERE pronamespace='xsy_v2'::regnamespace AND prorettype='trigger'::regtype", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_constraint WHERE connamespace='xsy_v2'::regnamespace AND contype IN ('p','u')", Integer.class)).isGreaterThan(200);
        assertThat(jdbc.queryForObject("SELECT is_nullable FROM information_schema.columns WHERE table_schema='xsy_v2' AND table_name='xsy_customer' AND column_name='settlement_customer_id'", String.class)).isEqualTo("NO");
        assertThat(jdbc.queryForObject("SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1", String.class)).isEqualTo("1001");
    }

    @Test
    void customerSelfSettlementAndInvalidEnumAreHandledBeforePersistence() {
        CustomerAddForm form = new CustomerAddForm();
        form.setName(prefix + "自结算");
        Long id = customers.add(form);
        assertThat(jdbc.queryForObject("SELECT settlement_customer_id FROM xsy_customer WHERE id=?", Long.class, id)).isEqualTo(id);
        form.setName(prefix + "非法状态");
        form.setStatus("BOGUS");
        assertThatThrownBy(() -> customers.add(form)).isInstanceOf(ScmBusinessException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_customer WHERE name=?", Integer.class, form.getName())).isZero();
    }

    @Test
    void realMapperRejectsFactMutationWithoutDatabaseTriggers() {
        assertThatThrownBy(() -> inventoryMovementDao.deleteById((java.io.Serializable) Long.MAX_VALUE))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> payables.deleteById((java.io.Serializable) Long.MAX_VALUE))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid WHERE c.relnamespace='xsy_v2'::regnamespace AND NOT t.tgisinternal", Integer.class)).isZero();
    }

    @Test
    void purchaseConfirmationFreezesPaymentPeriodWithoutTrigger() {
        Long sku = newOnShelfSku("rule");
        W6Fixture fixture = inboundFixture("rule", sku, "5.0000");
        jdbc.update("UPDATE xsy_supplier SET payment_period_days=17 WHERE id=?", fixture.supplierId());
        confirmReceipt(fixture.receipt().getId(), "5.0000");
        Map<String,Object> frozen = jdbc.queryForMap("SELECT id,due_date,payment_rule_snapshot->>'paymentPeriodDays' AS days,(event_at AT TIME ZONE 'Asia/Shanghai')::date AS business_date FROM xsy_finance_payable WHERE source_type='PURCHASE_RECEIPT' AND source_id=?", fixture.receipt().getId());
        assertThat(frozen.get("days")).isEqualTo("17");
        LocalDate business = ((java.sql.Date) frozen.get("business_date")).toLocalDate();
        assertThat(((java.sql.Date) frozen.get("due_date")).toLocalDate()).isEqualTo(business.plusDays(17));
        jdbc.update("UPDATE xsy_supplier SET payment_period_days=99 WHERE id=?", fixture.supplierId());
        assertThat(jdbc.queryForObject("SELECT payment_rule_snapshot->>'paymentPeriodDays' FROM xsy_finance_payable WHERE id=?", String.class, frozen.get("id"))).isEqualTo("17");
    }
}
