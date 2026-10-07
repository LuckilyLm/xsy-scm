package com.xsy.scm.finance;

import com.xsy.scm.common.error.ScmCommonErrorCode;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.customer.domain.form.CustomerAddForm;
import com.xsy.scm.customer.domain.form.CustomerStatusForm;
import com.xsy.scm.finance.dao.FinanceCounterpartySourceDao;
import com.xsy.scm.finance.domain.form.FinanceReceiptAddForm;
import com.xsy.scm.finance.domain.form.FinanceReceiptQueryForm;
import com.xsy.scm.finance.domain.vo.FinanceReceiptVO;
import com.xsy.scm.finance.service.FinanceReceiptQueryService;
import com.xsy.scm.finance.service.FinanceReceiptService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 收款登记的结算主体边界（PG IT）。
 *
 * <p>
 * 这里钉的是两条方向相反的语义，少一条都会被误读成「{@code selectCustomer} 应该改成
 * INNER JOIN 更严格」而被回退：
 *
 * <ol>
 * <li>{@link #danglingSettlementSubjectStillReadsThePayerButRejectsANewReceipt()} ——
 * <b>收紧新事实</b>：付款客户声明的结算父客户已经被软删时，客户事实整行仍然读得到，但结算 id
 * 与名称成对落空，登记收款被拒绝且不产生任何收款。{@code finance_receipt} 的这两列都是
 * NOT NULL，写进历史就不可改，宁可拒绝也不能把付款客户自己的名字当结算主体快照。</li>
 * <li>{@link #historicalReceiptStaysReadableAfterTheSettlementParentIsDeletedLater()} ——
 * <b>不破坏旧事实</b>：收款落库之后结算父客户才被软删，只读查询面必须照常返回这张历史收款。
 * 若为了收紧写入把 JOIN 改成 INNER，声明过结算主体的付款客户会整行查不出来，
 * {@code detail} 的范围判定随即拿不到 seller_id 而报越权，把「主档后来变了」升级成
 * 「审计历史不可读」——那与财务域保存既有事实的定位相反。</li>
 * </ol>
 *
 * <p>
 * 悬空状态是可达的，不是假想：{@code CustomerService.assertNotReferenced} 只拦协议价与 SKU
 * 可见性引用，不拦「仍被别人当作结算主体」，所以父客户可以被删掉而子客户继续指向它。
 * 夹具先经客户域建立合法集团关系，再只用 SQL 模拟删除父客户。
 */
@DisplayName("收款登记的结算主体边界（PG IT）")
class ScmFinanceReceiptSettlementPgIT extends ScmW5PgITBase {

    @Autowired
    private FinanceReceiptService financeReceiptService;

    @Autowired
    private FinanceReceiptQueryService financeReceiptQueries;

    @Autowired
    private FinanceCounterpartySourceDao counterpartySourceDao;

    private FinanceReceiptAddForm form(Long customerId, String amount) {
        FinanceReceiptAddForm form = new FinanceReceiptAddForm();
        form.setCustomerId(customerId);
        form.setAmount(amount);
        form.setMethod("CASH");
        form.setReceivedAt(OffsetDateTime.now(ZoneOffset.ofHours(8)));
        form.setRemark("结算主体边界 IT");
        return form;
    }

    private String key() {
        return prefix + ":add:" + UUID.randomUUID();
    }

    private Long groupCustomer(Long parentCustomerId) {
        CustomerAddForm form = new CustomerAddForm();
        form.setCustomerCode(prefix + "-G" + UUID.randomUUID().toString().substring(0, 8));
        form.setName("结算主体边界集团客户");
        form.setCustomerTypeId(customerTypeId(parentCustomerId == null ? "GROUP" : "ENTERPRISE"));
        form.setSettleMode("GROUP");
        form.setSellerId(1L);
        form.setParentCustomerId(parentCustomerId);
        form.setSettlementCustomerId(parentCustomerId);
        Long customerId = customerService.add(form);

        CustomerStatusForm status = new CustomerStatusForm();
        status.setCustomerId(customerId);
        status.setVersion(customerService.require(customerId).getVersion());
        status.setStatus("COOPERATING");
        customerService.updateStatus(status);
        return customerId;
    }

    private void softDelete(Long customerId) {
        jdbc.update("UPDATE customer SET deleted = TRUE WHERE id = ?", customerId);
        evictMybatisCache();
    }

    private int countReceiptsOf(Long customerId) {
        Integer value = jdbc.queryForObject(
                "SELECT count(*) FROM finance_receipt WHERE customer_id = ?", Integer.class, customerId);
        return value == null ? 0 : value;
    }

    /**
     * 非超管业务员，并把客户挂到他名下。
     *
     * <p>
     * 必须用非超管取证：{@link com.xsy.scm.common.scope.ScmValueScope#all()} 对 {@code null}
     * 归属直接放行，超管位下「结算行查不出来」与「查得出来」都同样能通过范围判定，
     * 于是下面两条边界用例全部失去区分能力（这正是本类要防的误改）。
     */
    private Long plainEmployeeOwning(Long customerId) {
        String loginName = (prefix + "-E" + UUID.randomUUID().toString().substring(0, 8)).toUpperCase();
        jdbc.update("INSERT INTO t_employee (employee_uid, login_name, login_pwd, actual_name, department_id,"
                + " administrator_flag, deleted_flag) VALUES (?, ?, ?, ?, 1, FALSE, FALSE)",
                UUID.randomUUID().toString().replace("-", ""), loginName, "$argon2id$it-placeholder", "结算边界业务员");
        Long employeeId = jdbc.queryForObject(
                "SELECT employee_id FROM t_employee WHERE login_name = ?", Long.class, loginName);
        assertThat(jdbc.queryForObject(
                "SELECT administrator_flag FROM t_employee WHERE employee_id = ?", Boolean.class, employeeId))
                        .as("越权与可读性取证禁止用超管位").isFalse();
        jdbc.update("UPDATE customer SET seller_id = ? WHERE id = ?", employeeId, customerId);
        evictMybatisCache();
        return employeeId;
    }

    private void asEmployee(Long employeeId) {
        var employee = new RequestEmployee();
        employee.setEmployeeId(employeeId);
        employee.setActualName("结算主体边界 IT 非超管");
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        employee.setAdministratorFlag(false);
        SmartRequestUtil.setRequestUser(employee);
        evictMybatisCache();
    }

    @Test
    @DisplayName("结算父客户已软删：付款客户整行仍可读、结算主体成对为空、新收款登记被拒且不落库")
    void danglingSettlementSubjectStillReadsThePayerButRejectsANewReceipt() {
        Long parent = groupCustomer(null);
        Long child = groupCustomer(parent);
        Long seller = plainEmployeeOwning(child);
        asEmployee(seller);
        softDelete(parent);

        var fact = counterpartySourceDao.selectCustomer(child);
        assertThat(fact).as("主档悬空不能把付款客户整行变没：只读面靠 seller_id 判范围").isNotNull();
        assertThat(fact.getCustomerId()).isEqualTo(child);
        assertThat(fact.getCustomerName()).isNotBlank();
        assertThat(fact.getSellerId()).isEqualTo(seller);
        // 结算 id 与名称要么成对来自同一行，要么成对为空 —— 不允许「id 是父客户、名称是付款客户」
        assertThat(fact.getSettlementCustomerId()).isNull();
        assertThat(fact.getSettlementCustomerName()).isNull();

        assertThatThrownBy(() -> financeReceiptService.add(form(child, "100.0000"), key()))
                .isInstanceOfSatisfying(ScmBusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ScmCommonErrorCode.VALIDATION_ERROR));

        assertThat(countReceiptsOf(child)).as("拒绝就不能留下半张收款事实").isZero();
    }

    @Test
    @DisplayName("收款登记之后再软删结算父客户：历史收款在列表与详情里都照常可读，快照不被改写")
    void historicalReceiptStaysReadableAfterTheSettlementParentIsDeletedLater() {
        Long parent = groupCustomer(null);
        Long child = groupCustomer(parent);
        Long seller = plainEmployeeOwning(child);
        asEmployee(seller);

        FinanceReceiptVO receipt = financeReceiptService.add(form(child, "88.0000"), key());
        assertThat(receipt.getSettlementCustomerId()).isEqualTo(parent);
        assertThat(countReceiptsOf(child)).isEqualTo(1);

        softDelete(parent);

        FinanceReceiptQueryForm query = new FinanceReceiptQueryForm();
        LocalDate today = LocalDate.now(ZoneOffset.ofHours(8));
        query.setStartDate(today.minusDays(1));
        query.setEndDate(today.plusDays(1));
        query.setCustomerId(child);
        var page = financeReceiptQueries.query(query);
        assertThat(page.getList()).as("列表不能因为结算行消失而漏掉这张历史收款")
                .extracting("receiptId").contains(receipt.getReceiptId());

        var detail = financeReceiptQueries.detail(receipt.getReceiptId());
        assertThat(detail.getReceipt().getReceiptNo()).isEqualTo(receipt.getReceiptNo());
        // 冻结的是登记当时的结算主体，主档后续变化不回写历史事实
        assertThat(detail.getReceipt().getSettlementCustomerId()).isEqualTo(parent);
    }

    @Test
    @DisplayName("自己结算自己的客户不受影响：结算主体成对解析为付款客户本身")
    void selfSettlingCustomerResolvesToItself() {
        Long customer = newCustomer();

        var fact = counterpartySourceDao.selectCustomer(customer);
        assertThat(fact.getSettlementCustomerId()).isEqualTo(fact.getCustomerId());
        assertThat(fact.getSettlementCustomerName()).isEqualTo(fact.getCustomerName());

        FinanceReceiptVO receipt = financeReceiptService.add(form(customer, "50.0000"), key());
        assertThat(receipt.getSettlementCustomerId()).isEqualTo(customer);
    }
}
