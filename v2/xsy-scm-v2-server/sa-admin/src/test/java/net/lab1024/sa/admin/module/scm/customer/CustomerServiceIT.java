package net.lab1024.sa.admin.module.scm.customer;

import net.lab1024.sa.admin.module.scm.common.ScmW2PgITBase;
import net.lab1024.sa.admin.module.scm.customer.domain.form.CustomerAddForm;
import net.lab1024.sa.admin.module.scm.customer.domain.form.CustomerDeleteForm;
import net.lab1024.sa.admin.module.scm.customer.domain.form.CustomerStatusForm;
import net.lab1024.sa.admin.module.scm.customer.domain.form.CustomerUpdateForm;
import net.lab1024.sa.admin.module.scm.customer.service.CustomerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 客户写路径在真实 PostgreSQL 上的行为（T11）。
 *
 * <p>验证的都是「只有在数据库里才成立」的契约：partial unique index 与显式查重双保险、
 * {@code NUMERIC(18,4)} 的定点数落库、{@code ck_customer_credit_period} 与 Validator 一致、
 * 以及状态机只能通过 {@code updateStatus} 前进。
 */
@DisplayName("客户：写路径与状态机（PG IT）")
class CustomerServiceIT extends ScmW2PgITBase {

    @Autowired
    private CustomerService service;

    private CustomerAddForm form(String suffix) {
        CustomerAddForm form = new CustomerAddForm();
        form.setCustomerCode(prefix + suffix);
        form.setName("客户" + suffix);
        form.setCustomerTypeId(customerTypeId("ENTERPRISE"));
        return form;
    }

    private CustomerAddForm form(String suffix, Long typeId) {
        CustomerAddForm form = form(suffix);
        form.setCustomerTypeId(typeId);
        return form;
    }

    private CustomerUpdateForm updateForm(Long id, int version) {
        CustomerUpdateForm form = new CustomerUpdateForm();
        form.setCustomerId(id);
        form.setVersion(version);
        form.setCustomerCode(prefix + "-U");
        form.setName("改名");
        form.setCustomerTypeId(customerTypeId("ENTERPRISE"));
        return form;
    }

    @Test
    @DisplayName("新建客户状态恒为 POTENTIAL，授信额度缺省落 0.0000，审计写入操作人")
    void newCustomerStartsAsPotentialWithZeroCreditLimit() {
        Long id = service.add(form("C1"));

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT status, version, deleted, settle_mode, credit_limit, created_by "
                        + "FROM customer WHERE id = ?", id);
        assertThat(row.get("status")).isEqualTo("POTENTIAL");
        assertThat(((Number) row.get("version")).intValue()).isZero();
        assertThat(row.get("deleted")).isEqualTo(false);
        assertThat(row.get("settle_mode")).isEqualTo("INDEPENDENT");
        assertThat((BigDecimal) row.get("credit_limit")).isEqualByComparingTo("0.0000");
        assertThat(row.get("created_by")).isEqualTo("1:1");
    }

    @Test
    @DisplayName("编码大小写不敏感：归一化后重复被拒 40936，软删后编码可复用")
    void codeIsNormalizedAndDuplicateRejected() {
        Long id = service.add(form("c1"));
        assertThat(jdbc.queryForObject("SELECT customer_code FROM customer WHERE id = ?", String.class, id))
                .isEqualTo((prefix + "c1").toUpperCase(Locale.ROOT));

        expectCode(() -> service.add(form("C1")), 40936);

        CustomerDeleteForm deletion = new CustomerDeleteForm();
        deletion.setCustomerId(id);
        deletion.setVersion(0);
        service.delete(deletion);

        assertThat(service.add(form("C1"))).as("partial unique index 只约束活动行").isNotEqualTo(id);
    }

    @Test
    @DisplayName("update 不触碰 status：先停用再编辑，状态保持 DISABLED（legacy C7）")
    void updateNeverTouchesStatus() {
        Long id = service.add(form("U1"));

        CustomerStatusForm status = new CustomerStatusForm();
        status.setCustomerId(id);
        status.setVersion(0);
        status.setStatus("SUSPENDED");
        service.updateStatus(status);

        service.update(updateForm(id, 1));

        assertThat(jdbc.queryForObject("SELECT status FROM customer WHERE id = ?", String.class, id))
                .isEqualTo("SUSPENDED");
        assertThat(jdbc.queryForObject("SELECT name FROM customer WHERE id = ?", String.class, id))
                .isEqualTo("改名");
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
    @ValueSource(strings = {"POTENTIAL", "SUSPENDED", "BLACKLIST"})
    @DisplayName("非 COOPERATING 状态不可交易（40930）")
    void requireTradableRejectsEveryOtherStatus(String target) {
        Long id = service.add(form("T2"));

        CustomerStatusForm status = new CustomerStatusForm();
        status.setCustomerId(id);
        status.setVersion(0);
        status.setStatus(target);
        service.updateStatus(status);

        expectCode(() -> service.requireTradable(id), 40930);
    }

    @Test
    @DisplayName("删除是软删并推进版本；删除后读取 40430")
    void deleteIsSoftAndAdvancesVersion() {
        Long id = service.add(form("D1"));

        CustomerDeleteForm deletion = new CustomerDeleteForm();
        deletion.setCustomerId(id);
        deletion.setVersion(0);
        service.delete(deletion);

        assertThat(jdbc.queryForObject("SELECT deleted FROM customer WHERE id = ?", Boolean.class, id)).isTrue();
        assertThat(jdbc.queryForObject("SELECT version FROM customer WHERE id = ?", Integer.class, id))
                .isEqualTo(1);
        expectCode(() -> service.require(id), 40430);
    }

    @Test
    @DisplayName("账期三种合法形态都能落库，数值按 NUMERIC(18,4) 保真")
    void creditPeriodThreeFormsPersist() {
        CustomerAddForm byAmount = form("A1");
        byAmount.setCreditPeriodType("BY_AMOUNT");
        byAmount.setCreditAmountThreshold("5000");
        Long amountId = service.add(byAmount);
        assertThat(jdbc.queryForObject(
                "SELECT credit_amount_threshold FROM customer WHERE id = ?", BigDecimal.class, amountId))
                .isEqualByComparingTo("5000.0000");

        CustomerAddForm byDay = form("A2");
        byDay.setCreditPeriodType("BY_TIME");
        byDay.setCreditPeriodValue(30);
        byDay.setCreditPeriodUnit("DAY");
        Long dayId = service.add(byDay);
        assertThat(jdbc.queryForObject(
                "SELECT credit_period_value FROM customer WHERE id = ?", Integer.class, dayId)).isEqualTo(30);
        assertThat(jdbc.queryForObject(
                "SELECT settle_day FROM customer WHERE id = ?", Integer.class, dayId)).isNull();

        CustomerAddForm byMonth = form("A3");
        byMonth.setCreditPeriodType("BY_TIME");
        byMonth.setCreditPeriodValue(1);
        byMonth.setCreditPeriodUnit("MONTH");
        byMonth.setSettleDay(15);
        Long monthId = service.add(byMonth);
        assertThat(jdbc.queryForObject(
                "SELECT settle_day FROM customer WHERE id = ?", Integer.class, monthId)).isEqualTo(15);
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
    @DisplayName("上级客户必须是 GROUP 类型；自身 / 环形 / 非集团都得到 40032")
    void parentMustBeGroupTypeAndAcyclic() {
        Long groupTypeId = customerTypeId("GROUP");
        Long enterpriseTypeId = customerTypeId("ENTERPRISE");

        Long group = service.add(form("G1", groupTypeId));
        Long member = service.add(form("M1", enterpriseTypeId));

        CustomerUpdateForm child = updateForm(member, 0);
        child.setParentCustomerId(group);
        service.update(child);
        assertThat(jdbc.queryForObject("SELECT parent_customer_id FROM customer WHERE id = ?", Long.class, member))
                .isEqualTo(group);

        // 自身作为上级
        CustomerUpdateForm self = updateForm(member, 1);
        self.setParentCustomerId(member);
        expectCode(() -> service.update(self), 40032);

        // 上级不是集团
        CustomerAddForm nonGroup = form("N1");
        nonGroup.setParentCustomerId(member);
        expectCode(() -> service.add(nonGroup), 40032);

        // 上级不存在
        CustomerAddForm missing = form("N2");
        missing.setParentCustomerId(-1L);
        expectCode(() -> service.add(missing), 40430);
    }

    @Test
    @DisplayName("环形上级被上溯检测拦下（40032）")
    void cyclicParentIsDetected() {
        Long groupTypeId = customerTypeId("GROUP");
        Long a = service.add(form("R1", groupTypeId));
        Long b = service.add(form("R2", groupTypeId));

        // a.parent = b
        CustomerUpdateForm link = updateForm(a, 0);
        link.setParentCustomerId(b);
        service.update(link);

        // 再把 b.parent 指回 a → 成环
        CustomerUpdateForm back = new CustomerUpdateForm();
        back.setCustomerId(b);
        back.setVersion(0);
        back.setCustomerCode(prefix + "-R2");
        back.setName("客户R2");
        back.setCustomerTypeId(groupTypeId);
        back.setParentCustomerId(a);
        expectCode(() -> service.update(back), 40032);
    }

    @Test
    @DisplayName("不存在 / 已删除客户统一 40430")
    void missingCustomerIs40430() {
        expectCode(() -> service.require(-1L), 40430);
        expectCode(() -> service.require(null), 40430);
    }
}
