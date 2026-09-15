package net.lab1024.sa.admin.module.scm.customer;

import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.customer.dao.CustomerDao;
import net.lab1024.sa.admin.module.scm.customer.dao.CustomerTypeDao;
import net.lab1024.sa.admin.module.scm.customer.domain.entity.CustomerEntity;
import net.lab1024.sa.admin.module.scm.customer.domain.entity.CustomerTypeEntity;
import net.lab1024.sa.admin.module.scm.customer.domain.form.CustomerAddForm;
import net.lab1024.sa.admin.module.scm.customer.manager.CustomerValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 客户校验器契约测试（无 DB，Mockito）。
 *
 * <p>覆盖 Target Design §9.1：账期组合、上级关系、字段归一化。错误码断言是硬门禁——
 * 编码冲突必须是 40936 而不是数据库异常，上级问题必须是 40032 而不是 40430。
 */
class CustomerValidatorTest {

    private final CustomerDao customerDao = mock(CustomerDao.class);

    private final CustomerTypeDao customerTypeDao = mock(CustomerTypeDao.class);

    private final CustomerValidator validator = new CustomerValidator(customerDao, customerTypeDao);

    private static int codeOf(Throwable t) {
        return ((ScmBusinessException) t).getErrorCode().getCode();
    }

    private static CustomerAddForm baseForm() {
        CustomerAddForm form = new CustomerAddForm();
        form.setCustomerCode("c001");
        form.setName("  测试客户  ");
        form.setCustomerTypeId(1L);
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
    @DisplayName("上级客户类型不是集团 → 40032")
    void parentTypeNotGroupRejected() {
        when(customerDao.selectById(7L)).thenReturn(customer(7L, 2L));
        when(customerTypeDao.selectById(2L)).thenReturn(type(2L, "ENTERPRISE"));
        assertThatThrownBy(() -> validator.validateParent(7L, 1L))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40032));
    }

    @Test
    @DisplayName("上级客户类型是集团 → 通过")
    void parentTypeGroupPasses() {
        when(customerDao.selectById(7L)).thenReturn(customer(7L, 3L));
        when(customerTypeDao.selectById(3L)).thenReturn(type(3L, "GROUP"));
        assertThatCode(() -> validator.validateParent(7L, 1L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("上溯成环（祖先等于自身）→ 40032")
    void parentCycleBackToSelfRejected() {
        when(customerDao.selectById(7L)).thenReturn(customer(7L, 3L, 1L));
        when(customerTypeDao.selectById(3L)).thenReturn(type(3L, "GROUP"));
        assertThatThrownBy(() -> validator.validateParent(7L, 1L))
                .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40032));
    }

    @Test
    @DisplayName("上溯超过最大深度（脏数据环）→ 40032")
    void parentDepthLimitRejected() {
        when(customerTypeDao.selectById(3L)).thenReturn(type(3L, "GROUP"));
        // 构造 1 → 2 → 3 → ... 的长链，永不收敛
        for (long id = 1; id <= CustomerValidator.MAX_PARENT_DEPTH + 5; id++) {
            when(customerDao.selectById(id)).thenReturn(customer(id, 3L, id + 1));
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

    private static CustomerEntity customer(Long id, Long typeId) {
        return customer(id, typeId, null);
    }

    private static CustomerEntity customer(Long id, Long typeId, Long parentId) {
        CustomerEntity entity = new CustomerEntity();
        entity.setId(id);
        entity.setCustomerTypeId(typeId);
        entity.setParentCustomerId(parentId);
        return entity;
    }

    private static CustomerTypeEntity type(Long id, String typeCode) {
        CustomerTypeEntity entity = new CustomerTypeEntity();
        entity.setId(id);
        entity.setTypeCode(typeCode);
        return entity;
    }
}
