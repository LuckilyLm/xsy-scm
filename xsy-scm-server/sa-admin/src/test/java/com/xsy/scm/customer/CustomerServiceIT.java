package com.xsy.scm.customer;

import cn.dev33.satoken.stp.StpUtil;
import com.xsy.scm.businesscategory.domain.form.BusinessCategoryAddForm;
import com.xsy.scm.businesscategory.domain.entity.BusinessCategoryEntity;
import com.xsy.scm.businesscategory.domain.form.BusinessCategoryUpdateForm;
import com.xsy.scm.businesscategory.service.BusinessCategoryService;
import com.xsy.scm.common.ScmW2PgITBase;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.customer.domain.form.CustomerAddForm;
import com.xsy.scm.customer.domain.form.CustomerDeleteForm;
import com.xsy.scm.customer.domain.form.CustomerSkuVisibilityItemForm;
import com.xsy.scm.customer.domain.form.CustomerStatusForm;
import com.xsy.scm.customer.domain.form.CustomerUpdateForm;
import com.xsy.scm.customer.domain.form.CustomerVisibilityReplaceForm;
import com.xsy.scm.customer.service.CustomerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

/**
 * 客户写路径在真实 PostgreSQL 上的行为。
 *
 * <p>验证的都是「只有在数据库里才成立」的约定：服务端编码生成（PG sequence，{@code CUS######}）、
 * {@code NUMERIC(18,4)} 的定点数写入数据库、{@code ck_customer_credit_period} 与 Validator 一致、
 * 以及状态机只能通过 {@code updateStatus} 前进。
 */
@DisplayName("客户：写路径与状态机（PG IT）")
class CustomerServiceIT extends ScmW2PgITBase {

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
