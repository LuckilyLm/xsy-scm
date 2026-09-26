package net.lab1024.sa.admin.module.scm.finance;

import net.lab1024.sa.admin.module.scm.common.ScmW5PgITBase;
import net.lab1024.sa.admin.module.scm.finance.domain.form.FinancePaymentAddForm;
import net.lab1024.sa.admin.module.scm.finance.domain.vo.FinancePaymentVO;
import net.lab1024.sa.admin.module.scm.finance.service.FinancePaymentService;
import net.lab1024.sa.admin.module.scm.order.domain.form.OrderRefundCompleteForm;
import net.lab1024.sa.admin.module.scm.order.domain.form.OrderReturnAddForm;
import net.lab1024.sa.admin.module.scm.order.domain.form.OrderReturnApproveForm;
import net.lab1024.sa.admin.module.scm.order.domain.form.OrderReturnApproveItemForm;
import net.lab1024.sa.admin.module.scm.order.domain.form.OrderReturnItemForm;
import net.lab1024.sa.admin.module.scm.order.service.OrderRefundService;
import net.lab1024.sa.admin.module.scm.order.service.OrderReturnService;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 付款登记（Finance R1 F1-3B，PG IT）。
 *
 * <p>这里钉的是「一笔付出就是一个事实，且它只有两种成立方式」：
 * {@code SUPPLIER} + 无来源（付款 / 预付，允许当前没有任何应付，Q16），
 * {@code CUSTOMER} + {@code ORDER_REFUND}（退款付款，必须 COMPLETED 且金额与对方逐值相等，Q19）。
 * 其余组合一律 41139，包括没有需求基线的「客户无来源付款」（属 P5）。
 *
 * <p>另外两组必须显性成立的边界：<b>付款不核销</b>（核销是 F1-4）与
 * <b>退款付款不二次冲减应收</b>（Q27：Return 已经红冲过一次，钱付出去不再动应收）。
 */
@DisplayName("付款登记（Finance R1 F1-3B，PG IT）")
class ScmFinancePaymentPgIT extends ScmW5PgITBase {

    private static final OffsetDateTime PAST =
            OffsetDateTime.of(2026, 4, 12, 15, 45, 0, 0, ZoneOffset.ofHours(8));

    @Autowired
    private FinancePaymentService financePaymentService;

    @Autowired
    private OrderReturnService returns;

    @Autowired
    private OrderRefundService refunds;

    // ------------------------------------------------------------------
    // 夹具
    // ------------------------------------------------------------------

    private FinancePaymentAddForm form(String counterpartyType, Long counterpartyId, String amount,
                                       String method, OffsetDateTime paidAt,
                                       String sourceType, Long sourceId) {
        FinancePaymentAddForm form = new FinancePaymentAddForm();
        form.setCounterpartyType(counterpartyType);
        form.setCounterpartyId(counterpartyId);
        form.setAmount(amount);
        form.setMethod(method);
        form.setPaidAt(paidAt);
        form.setSourceType(sourceType);
        form.setSourceId(sourceId);
        form.setExternalReference("PM-REF-" + UUID.randomUUID().toString().substring(0, 8));
        form.setRemark("F1-3B IT");
        return form;
    }

    private FinancePaymentAddForm supplierForm(Long supplierId, String amount) {
        return form("SUPPLIER", supplierId, amount, "BANK_TRANSFER", PAST, null, null);
    }

    private FinancePaymentAddForm refundForm(Long customerId, String amount, Long refundId) {
        return form("CUSTOMER", customerId, amount, "BANK_TRANSFER", PAST, "ORDER_REFUND", refundId);
    }

    private FinancePaymentVO add(FinancePaymentAddForm form) {
        return financePaymentService.add(form, key("add"));
    }

    private FinancePaymentVO add(FinancePaymentAddForm form, String idempotencyKey) {
        return financePaymentService.add(form, idempotencyKey);
    }

    private String key(String tag) {
        return prefix + ":" + tag + ":" + UUID.randomUUID();
    }

    private Long supplier() {
        return newSupplier("P" + Math.abs(UUID.randomUUID().getMostSignificantBits() % 1000));
    }

    private Long customerOwnedBy(Long sellerId) {
        Long customerId = newCustomer();
        jdbc.update("UPDATE customer SET seller_id = ? WHERE id = ?", sellerId, customerId);
        evictMybatisCache();
        return customerId;
    }

    /**
     * 走完整订单域链路造一张<b>已 COMPLETED 的退款</b>：确认订单 → 退货批准 → 退款完成。
     *
     * <p>不直插 {@code order_refund}：退款金额、客户与状态都必须是订单域真实计算的结果，
     * 否则「付款金额必须逐值等于 refund_amount」这条校验就变成两个自造数字互相印证。
     */
    private Refund completedRefund(String tag, String ordered, String approved) {
        Long customerId = customerOwnedBy(null);
        // 商品编码是 prefix + suffix，而 prefix 只在**单个用例**内唯一，
        // 因此同一用例里造多张退款必须各用自己的 tag，否则第二张撞 SPU 编码唯一。
        Long skuId = newOnShelfSku(tag);
        Long orderId = confirmedSalesOrder(customerId, skuId, ordered, ordered);
        Long orderItemId = confirmedSalesOrderItemId(orderId);

        var create = new OrderReturnAddForm();
        create.setOrderId(orderId);
        create.setReason("品质问题 " + tag);
        var row = new OrderReturnItemForm();
        row.setOrderItemId(orderItemId);
        row.setRequestedQuantity(approved);
        create.setItems(List.of(row));
        var created = returns.create(create, key("return-create:" + tag));

        var approve = new OrderReturnApproveForm();
        approve.setReturnId(created.getReturnId());
        approve.setVersion(created.getVersion());
        var approvedRow = new OrderReturnApproveItemForm();
        approvedRow.setOrderItemId(orderItemId);
        approvedRow.setApprovedQuantity(approved);
        approve.setItems(List.of(approvedRow));
        returns.approve(approve, key("return-approve:" + tag));

        // 按 order_id 直查退款行，不绕 refunds.query(...).getFirst()：
        // 那个分页读接口按 orderSellerScope 收窄（非超管身份下会拿不到行），
        // 而夹具要的只是「这一单的退款」，直查不依赖读侧范围也不依赖行序。
        Long refundId = jdbc.queryForObject(
                "SELECT id FROM order_refund WHERE order_id = ? AND deleted = FALSE", Long.class, orderId);
        Integer refundVersion = jdbc.queryForObject(
                "SELECT version FROM order_refund WHERE id = ?", Integer.class, refundId);

        var complete = new OrderRefundCompleteForm();
        complete.setRefundId(refundId);
        complete.setVersion(refundVersion);
        // order_refund.external_reference 上有部分唯一索引（uk_order_refund_external_reference_active），
        // 与财务侧刻意「可重复」的同名列正相反，因此这里的值必须每次唯一。
        // 也正因为它唯一，把它复制进 finance_payment.external_reference（§15 明令禁止）
        // 会让两域的凭据号互相牵连，D 用例对此有显式断言。
        complete.setExternalReference("ORDER-SIDE-" + tag + "-" + UUID.randomUUID());
        refunds.complete(complete, key("refund-complete:" + tag));
        evictMybatisCache();

        Map<String, Object> row1 = jdbc.queryForMap(
                "SELECT * FROM order_refund WHERE id = ?", refundId);
        return new Refund(refundId, orderId, customerId,
                (BigDecimal) row1.get("refund_amount"), String.valueOf(row1.get("status")));
    }

    private record Refund(Long refundId, Long orderId, Long customerId,
                          BigDecimal refundAmount, String status) {
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private Map<String, Object> paymentRow(Long paymentId) {
        return jdbc.queryForMap("SELECT * FROM finance_payment WHERE id = ?", paymentId);
    }

    private List<Map<String, Object>> logsOf(Long paymentId) {
        // business_id 只在同一 business_type 内唯一（各事实表各有 id 序列），必须带类型
        return jdbc.queryForList(
                "SELECT * FROM finance_operation_log WHERE business_type = 'PAYMENT' AND business_id = ?",
                paymentId);
    }

    /**
     * 某供应商名下的付款数。
     *
     * <p>{@code counterparty_id} 是<b>多态</b>列（供应商 id 或客户 id，两条独立的 identity 序列），
     * 因此单按 id 计数会把同号的客户付款一起算进来 —— 必须连 {@code counterparty_type} 一起判。
     */
    private int paymentsOfSupplier(Long supplierId) {
        return count("SELECT count(*) FROM finance_payment"
                + " WHERE counterparty_type = 'SUPPLIER' AND counterparty_id = ?", supplierId);
    }

    private int payLogsOfSupplier(Long supplierId) {
        return count("SELECT count(*) FROM finance_operation_log l"
                + " JOIN finance_payment p ON p.id = l.business_id"
                + " WHERE l.business_type = 'PAYMENT' AND p.counterparty_type = 'SUPPLIER'"
                + " AND p.counterparty_id = ?", supplierId);
    }

    /** 某退款名下的付款数（来源两列一起判，与 {@code uk_finance_payment_source_active} 同口径）。 */
    private int paymentsOfRefund(Long refundId) {
        return count("SELECT count(*) FROM finance_payment"
                + " WHERE source_type = 'ORDER_REFUND' AND source_id = ?", refundId);
    }

    private int payLogsOfRefund(Long refundId) {
        return count("SELECT count(*) FROM finance_operation_log l"
                + " JOIN finance_payment p ON p.id = l.business_id"
                + " WHERE l.business_type = 'PAYMENT' AND p.source_type = 'ORDER_REFUND'"
                + " AND p.source_id = ?", refundId);
    }

    /** 切换到非超管身份取证数据范围与功能权限（P0 裁决第 5 条：越权取证禁止用超管位）。 */
    private void asEmployee(Long employeeId) {
        var employee = new RequestEmployee();
        employee.setEmployeeId(employeeId);
        employee.setActualName("F1-3B IT 非超管");
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        employee.setAdministratorFlag(false);
        SmartRequestUtil.setRequestUser(employee);
        evictMybatisCache();
    }

    private void asAdmin() {
        var employee = new RequestEmployee();
        employee.setEmployeeId(1L);
        employee.setActualName("W5 IT");
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        employee.setAdministratorFlag(true);
        SmartRequestUtil.setRequestUser(employee);
        evictMybatisCache();
    }

    private Long newPlainEmployee(String tag) {
        String loginName = (prefix + "-" + tag).toUpperCase();
        jdbc.update("INSERT INTO t_employee (employee_uid, login_name, login_pwd, actual_name, department_id,"
                + " administrator_flag, deleted_flag) VALUES (?, ?, ?, ?, 1, FALSE, FALSE)",
                UUID.randomUUID().toString().replace("-", ""), loginName, "$argon2id$it-placeholder", "财务" + tag);
        Long employeeId = jdbc.queryForObject(
                "SELECT employee_id FROM t_employee WHERE login_name = ?", Long.class, loginName);
        assertThat(jdbc.queryForObject(
                "SELECT administrator_flag FROM t_employee WHERE employee_id = ?", Boolean.class, employeeId))
                .as("范围取证必须用非超管账号").isFalse();
        evictMybatisCache();
        return employeeId;
    }

    private static String currentOperator() {
        return net.lab1024.sa.admin.module.scm.common.constant.ScmOperator.current();
    }

    /**
     * {@code timestamptz} 在 Map 读法里返回 {@link java.sql.Timestamp}，统一成时刻才可比。
     */
    private static OffsetDateTime timestampOf(Object value) {
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant().atOffset(ZoneOffset.UTC);
        }
        throw new IllegalArgumentException("无法归一为 OffsetDateTime 的列类型: "
                + (value == null ? "null" : value.getClass().getName()));
    }

    // ------------------------------------------------------------------
    // A / B / C / N. SUPPLIER 模式
    // ------------------------------------------------------------------

    @Test
    @DisplayName("A 供应商预付：没有任何应付也照常登记，且不产生核销行")
    void supplierPrepaymentWithoutAnyPayableIsAllowed() {
        Long supplierId = supplier();
        assertThat(count("SELECT count(*) FROM finance_payable WHERE supplier_id = ?", supplierId)).isZero();

        FinancePaymentVO result = add(supplierForm(supplierId, "500.0000"));

        Map<String, Object> row = paymentRow(result.getPaymentId());
        assertThat(row.get("counterparty_type")).isEqualTo("SUPPLIER");
        assertThat(row.get("counterparty_id")).isEqualTo(supplierId);
        assertThat(row.get("source_type")).as("预付没有业务来源").isNull();
        assertThat(row.get("source_id")).isNull();
        assertThat(row.get("entry_type")).isEqualTo("NORMAL");
        assertThat(row.get("reverse_of_id")).isNull();
        assertThat(row.get("reason")).isNull();
        assertThat((BigDecimal) row.get("amount")).isEqualByComparingTo("500.0000");
        assertThat(String.valueOf(row.get("payment_no"))).matches("PM\\d{14,}");
        assertThat(count("SELECT count(*) FROM finance_write_off")).as("核销属 F1-4").isZero();
        assertThat(result.getCounterpartyName()).as("回显登记时冻结的对方名").isEqualTo(row.get("counterparty_name_snapshot"));
    }

    @Test
    @DisplayName("B 已有应付的供应商付款：付款成立、应付一字未改、仍然零核销")
    void supplierPaymentWithExistingPayableNeverAutoWritesOff() {
        Long supplierId = supplier();
        // 受控夹具：应付生成属 F1-2A，这里按 schema 直插一张正常应付，
        // 目的是证明「付款不会自动去冲它」，不依赖也不提前实现 F1-4 的核销能力。
        Long purchaseOrderId = 900_001L;
        long receiptId = 900_002L;
        jdbc.update("INSERT INTO finance_payable (payable_no, source_type, source_id, purchase_order_id,"
                        + " supplier_id, supplier_name_snapshot, entry_type, amount, event_at,"
                        + " created_at, updated_at, created_by)"
                        + " VALUES ('AP-FIXTURE-PB', 'PURCHASE_RECEIPT', ?, ?, ?, '夹具供应商', 'NORMAL',"
                        + " 500.0000, now(), now(), now(), ?)",
                receiptId, purchaseOrderId, supplierId, currentOperator());
        evictMybatisCache();
        Map<String, Object> before = jdbc.queryForMap(
                "SELECT * FROM finance_payable WHERE source_id = ?", receiptId);

        add(supplierForm(supplierId, "500.0000"));

        Map<String, Object> after = jdbc.queryForMap(
                "SELECT * FROM finance_payable WHERE source_id = ?", receiptId);
        assertThat(after.get("amount")).isEqualTo(before.get("amount"));
        assertThat(after.get("version")).isEqualTo(before.get("version"));
        assertThat(after.get("updated_at")).isEqualTo(before.get("updated_at"));
        assertThat(count("SELECT count(*) FROM finance_write_off"))
                .as("等额也不自动核销，付款只是「一笔待核销款」").isZero();
    }

    @Test
    @DisplayName("C 供应商两笔完全相同的事实：不同命令即两张付款单（没有自然唯一键）")
    void twoIdenticalSupplierPaymentsAreBothAccepted() {
        Long supplierId = supplier();
        FinancePaymentAddForm first = supplierForm(supplierId, "120.0000");
        String sharedReference = "PM-STATEMENT-777";
        first.setExternalReference(sharedReference);
        FinancePaymentAddForm second = supplierForm(supplierId, "120.0000");
        second.setExternalReference(sharedReference);
        second.setPaidAt(first.getPaidAt());

        Long firstId = add(first, key("same-1")).getPaymentId();
        Long secondId = add(second, key("same-2")).getPaymentId();

        assertThat(firstId).isNotEqualTo(secondId);
        assertThat(paymentsOfSupplier(supplierId))
                .as("对方 / 金额 / 时点 / 凭据号四项全同也不构成重复事实").isEqualTo(2);

        // 库侧取证：finance_payment 的全部唯一索引只有主键、单据号、来源（含 source_id IS NOT NULL 谓词）
        // 与反向唯一 —— 没有任何按业务字段组合的自然键。
        assertThat(jdbc.queryForList(
                "SELECT indexdef FROM pg_indexes WHERE tablename = 'finance_payment'"
                        + " AND indexdef ILIKE '%unique%'", String.class))
                .allSatisfy(def -> assertThat(def).satisfiesAnyOf(
                        sql -> assertThat(sql).contains("payment_no"),
                        sql -> assertThat(sql).contains("source_type, source_id"),
                        sql -> assertThat(sql).contains("reverse_of_id"),
                        sql -> assertThat(sql).contains("finance_payment_pkey")));
        assertThat(count("SELECT count(*) FROM pg_indexes WHERE tablename = 'finance_payment'"
                + " AND indexdef ILIKE '%unique%' AND indexdef ILIKE '%external_reference%'")).isZero();
    }

    // ------------------------------------------------------------------
    // D / E / F / G / H / I / J / K. CUSTOMER 退款付款
    // ------------------------------------------------------------------

    @Test
    @DisplayName("D 退款付款：COMPLETED + 金额逐值相等 + 对方等于退款客户 → 成功且带来源")
    void completedRefundPaymentSucceedsAndCarriesSource() {
        Refund refund = completedRefund("D", "10.0000", "2.0000");
        assertThat(refund.status()).isEqualTo("COMPLETED");
        String exact = refund.refundAmount().toPlainString();

        FinancePaymentVO result = add(refundForm(refund.customerId(), exact, refund.refundId()));

        Map<String, Object> row = paymentRow(result.getPaymentId());
        assertThat(row.get("counterparty_type")).isEqualTo("CUSTOMER");
        assertThat(row.get("counterparty_id")).isEqualTo(refund.customerId());
        assertThat(row.get("source_type")).isEqualTo("ORDER_REFUND");
        assertThat(row.get("source_id")).isEqualTo(refund.refundId());
        assertThat((BigDecimal) row.get("amount")).isEqualByComparingTo(exact);
        assertThat(jdbc.queryForObject("SELECT name FROM customer WHERE id = ?", String.class,
                refund.customerId())).isEqualTo(row.get("counterparty_name_snapshot"));
        // 订单侧凭据号绝不复制进财务侧（两域各自的资金凭证）
        assertThat(row.get("external_reference"))
                .isNotEqualTo("ORDER-SIDE-D")
                .isEqualTo(result.getExternalReference());
    }

    @Test
    @DisplayName("E 退款仍 PENDING：41139 拒绝，不产生付款")
    void pendingRefundCannotBePaid() {
        Refund refund = completedRefund("E", "10.0000", "1.0000");
        // 撤回到 PENDING：夹具用真链路造单，这里只把状态与完成时点成对改回去
        assertThat(jdbc.update("UPDATE order_refund SET status = 'PENDING', completed_at = NULL WHERE id = ?",
                refund.refundId())).isEqualTo(1);
        evictMybatisCache();

        expectCode(() -> add(refundForm(refund.customerId(), refund.refundAmount().toPlainString(),
                refund.refundId())), 41139);
        assertThat(paymentsOfRefund(refund.refundId())).isZero();
    }

    @Test
    @DisplayName("F 金额不等（含差一分钱与 scale 敏感值）：41139 拒绝，不做 2 位舍入")
    void amountMustMatchRefundExactly() {
        Refund refund = completedRefund("F", "10.0000", "3.0000");
        String exact = refund.refundAmount().toPlainString();

        expectCode(() -> add(refundForm(refund.customerId(),
                refund.refundAmount().add(new BigDecimal("0.0001")).toPlainString(), refund.refundId())), 41139);
        expectCode(() -> add(refundForm(refund.customerId(),
                refund.refundAmount().subtract(new BigDecimal("0.0001")).toPlainString(), refund.refundId())), 41139);
        // 「四舍五入到 2 位再比」的实现会在这里放行，因此最后一位非零的金额是判别性夹具
        if (refund.refundAmount().scale() < 4) {
            // order_refund 是 NUMERIC(18,4)，读回来标度即 4；真到这一步说明夹具不再判别，必须让它响
            throw new AssertionError("退款金额标度不是 4，判别性夹具失效: " + exact);
        }

        assertThat(paymentsOfRefund(refund.refundId())).isZero();
        assertThat(add(refundForm(refund.customerId(), exact, refund.refundId())).getPaymentId()).isNotNull();
    }

    @Test
    @DisplayName("G 客户与退款不一致：41139 拒绝（付款对方以 order_refund.customer_id 为权威）")
    void counterpartyMustEqualRefundCustomer() {
        Refund refund = completedRefund("G", "10.0000", "1.0000");
        Long otherCustomer = customerOwnedBy(null);

        expectCode(() -> add(refundForm(otherCustomer, refund.refundAmount().toPlainString(),
                refund.refundId())), 41139);
        assertThat(paymentsOfRefund(refund.refundId())).isZero();
    }

    @Test
    @DisplayName("H 客户无来源付款：本期不支持（客户提现 / 余额退款属 P5），41139 拒绝")
    void customerWithoutSourceIsRejected() {
        Refund refund = completedRefund("H", "10.0000", "1.0000");

        expectCode(() -> add(form("CUSTOMER", refund.customerId(), refund.refundAmount().toPlainString(),
                "CASH", PAST, null, null)), 41139);
        // 只给一半来源也一样不成对（ck_finance_payment_source_pairing 同向，服务层先给可解释码）
        expectCode(() -> add(form("CUSTOMER", refund.customerId(), refund.refundAmount().toPlainString(),
                "CASH", PAST, "ORDER_REFUND", null)), 41139);
        expectCode(() -> add(form("CUSTOMER", refund.customerId(), refund.refundAmount().toPlainString(),
                "CASH", PAST, null, refund.refundId())), 41139);
        assertThat(count("SELECT count(*) FROM finance_payment"
                + " WHERE counterparty_type = 'CUSTOMER' AND counterparty_id = ?", refund.customerId()))
                .isZero();
    }

    @Test
    @DisplayName("I 供应商带 ORDER_REFUND 来源：模式配对错误，41139 拒绝")
    void supplierWithRefundSourceIsRejected() {
        Long supplierId = supplier();
        Refund refund = completedRefund("I", "10.0000", "1.0000");

        expectCode(() -> add(form("SUPPLIER", supplierId, "10.0000", "CASH", PAST,
                "ORDER_REFUND", refund.refundId())), 41139);
        // 供应商侧只给 sourceId 也一样不成对
        expectCode(() -> add(form("SUPPLIER", supplierId, "10.0000", "CASH", PAST, null, refund.refundId())), 41139);
        assertThat(paymentsOfSupplier(supplierId)).isZero();
    }

    @Test
    @DisplayName("J 不支持的来源类型：41139 拒绝；绕过 Java 也被库 CHECK 拒绝")
    void unsupportedSourceTypeIsRejectedTwice() {
        Long supplierId = supplier();
        Refund refund = completedRefund("J", "10.0000", "1.0000");

        expectCode(() -> add(form("CUSTOMER", refund.customerId(), refund.refundAmount().toPlainString(),
                "CASH", PAST, "PURCHASE_ORDER", refund.refundId())), 41139);
        expectCode(() -> add(form("CUSTOMER", refund.customerId(), refund.refundAmount().toPlainString(),
                "CASH", PAST, "SALES_ORDER", 12345L)), 41139);
        assertThat(paymentsOfRefund(refund.refundId()) + paymentsOfSupplier(supplierId))
                .as("两种越界来源都没落库").isZero();

        expectSqlFailure("INSERT INTO finance_payment (payment_no, counterparty_type, counterparty_id,"
                        + " counterparty_name_snapshot, amount, method, paid_at, entry_type, source_type,"
                        + " source_id) VALUES ('PM-J-1', 'CUSTOMER', ?, '客户', 10.0000, 'CASH', now(),"
                        + " 'NORMAL', 'PURCHASE_ORDER', ?)",
                refund.customerId(), refund.refundId());
        // 供应商（无来源）与未知对方类型同样被库层挡住
        expectSqlFailure("INSERT INTO finance_payment (payment_no, counterparty_type, counterparty_id,"
                        + " counterparty_name_snapshot, amount, method, paid_at, entry_type)"
                        + " VALUES ('PM-J-2', 'WAREHOUSE', ?, '仓库', 10.0000, 'CASH', now(), 'NORMAL')",
                supplierId);
    }

    @Test
    @DisplayName("K 同一退款顺序重复付款（两个不同幂等键）：第二次被来源唯一索引拒绝")
    void secondPaymentForSameRefundIsRejectedBySourceUnique() {
        Refund refund = completedRefund("K", "10.0000", "2.0000");
        String exact = refund.refundAmount().toPlainString();

        Long firstId = add(refundForm(refund.customerId(), exact, refund.refundId()), key("k1")).getPaymentId();
        expectCode(() -> add(refundForm(refund.customerId(), exact, refund.refundId()), key("k2")), 41139);

        assertThat(paymentsOfRefund(refund.refundId())).isEqualTo(1);
        assertThat(payLogsOfRefund(refund.refundId())).as("失败的那次不留日志").isEqualTo(1);
        assertThat(paymentRow(firstId).get("source_id")).isEqualTo(refund.refundId());
        // 异常必须是业务码，不能把 DuplicateKeyException / 约束名泄漏到接口
        assertThatThrownBy(() -> add(refundForm(refund.customerId(), exact, refund.refundId()), key("k3")))
                .isInstanceOf(net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException.class)
                .hasMessageNotContaining("uk_finance_payment_source_active")
                .hasMessageNotContaining("DuplicateKey");
    }

    // ------------------------------------------------------------------
    // M. 付款不二次冲减应收（Q27）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("M 退款付款成立后：正常应收与红字应收一字未改、零核销")
    void paymentNeverReducesReceivableTwice() {
        Refund refund = completedRefund("M", "10.0000", "2.0000");
        // 该退货发生在签收之前，因此批准时成功跳过、库里此刻没有红字。
        // 直接按 schema 补两张应收事实作为「已存在」的前置，不依赖 F1-2B 的签收链路。
        jdbc.update("INSERT INTO finance_receivable (receivable_no, source_type, source_id, order_id,"
                        + " customer_id, customer_name_snapshot, entry_type, amount, event_at,"
                        + " created_at, updated_at, created_by)"
                        + " VALUES ('AR-M-NORMAL', 'SALES_ORDER', ?, ?, ?, '夹具客户', 'NORMAL', 80.0000,"
                        + " now(), now(), now(), ?)",
                refund.orderId(), refund.orderId(), refund.customerId(), currentOperator());
        Long normalId = jdbc.queryForObject(
                "SELECT id FROM finance_receivable WHERE receivable_no = 'AR-M-NORMAL'", Long.class);
        jdbc.update("INSERT INTO finance_receivable (receivable_no, source_type, source_id, order_id,"
                        + " customer_id, customer_name_snapshot, entry_type, amount, event_at,"
                        + " original_receivable_id, reason, created_at, updated_at, created_by)"
                        + " VALUES ('AR-M-RED', 'ORDER_RETURN', ?, ?, ?, '夹具客户', 'RED', 20.0000,"
                        + " now(), ?, '品质问题', now(), now(), ?)",
                refund.refundId(), refund.orderId(), refund.customerId(), normalId, currentOperator());
        evictMybatisCache();
        List<Map<String, Object>> before = jdbc.queryForList(
                "SELECT * FROM finance_receivable WHERE order_id = ? ORDER BY entry_type", refund.orderId());
        assertThat(before).hasSize(2);

        add(refundForm(refund.customerId(), refund.refundAmount().toPlainString(), refund.refundId()));

        List<Map<String, Object>> after = jdbc.queryForList(
                "SELECT * FROM finance_receivable WHERE order_id = ? ORDER BY entry_type", refund.orderId());
        assertThat(after).hasSize(2);
        for (int index = 0; index < after.size(); index++) {
            assertThat(after.get(index).get("amount")).isEqualTo(before.get(index).get("amount"));
            assertThat(after.get(index).get("version")).isEqualTo(before.get(index).get("version"));
            assertThat(after.get(index).get("updated_at")).isEqualTo(before.get(index).get("updated_at"));
        }
        assertThat(count("SELECT count(*) FROM finance_write_off")).as("付款不核销").isZero();
        assertThat(count("SELECT count(*) FROM finance_receivable WHERE source_id = ? AND entry_type = 'RED'"
                + " AND receivable_no <> 'AR-M-RED'", refund.refundId())).as("不追加第二张红字").isZero();
    }

    // ------------------------------------------------------------------
    // O / P / Q / R / S. 时点、方式与金额形态
    // ------------------------------------------------------------------

    @Test
    @DisplayName("O paid_at 由调用方给出并逐值落库；P 未来时点不被拒绝")
    void paidAtIsTheCallerSuppliedMoment() {
        Long supplierId = supplier();
        Long receiptId = add(supplierForm(supplierId, "31.0000")).getPaymentId();

        assertThat(timestampOf(paymentRow(receiptId).get("paid_at")).toInstant()).isEqualTo(PAST.toInstant());
        assertThat(jdbc.queryForObject(
                "SELECT paid_at = ?::timestamptz FROM finance_payment WHERE id = ?",
                Boolean.class, PAST.toInstant().toString(), receiptId)).isTrue();

        // 夹具取到微秒：timestamptz 只到微秒且会舍入纳秒，归一放夹具侧才能保持逐值判等
        OffsetDateTime future = OffsetDateTime.now().plusDays(21).truncatedTo(ChronoUnit.MICROS);
        Long futureId = add(form("SUPPLIER", supplierId, "32.0000", "CASH", future, null, null)).getPaymentId();
        assertThat(timestampOf(paymentRow(futureId).get("paid_at")).toInstant()).isEqualTo(future.toInstant());
    }

    @Test
    @DisplayName("Q 方式三值与收款共用同一枚举；R 越界方式服务层 41140、库层 CHECK 二道")
    void methodSharesTheReceiptEnumAndRejectsEverythingElse() {
        Long supplierId = supplier();
        assertThat(add(form("SUPPLIER", supplierId, "1.0000", "CASH", PAST, null, null)).getMethod())
                .isEqualTo("CASH");
        assertThat(add(form("SUPPLIER", supplierId, "1.0000", "BANK_TRANSFER", PAST, null, null)).getMethod())
                .isEqualTo("BANK_TRANSFER");
        assertThat(add(form("SUPPLIER", supplierId, "1.0000", "OTHER", PAST, null, null)).getMethod())
                .isEqualTo("OTHER");

        expectCode(() -> add(form("SUPPLIER", supplierId, "1.0000", "WECHAT_PAY", PAST, null, null)), 41140);
        expectCode(() -> add(form("SUPPLIER", supplierId, "1.0000", "BALANCE", PAST, null, null)), 41140);
        expectCode(() -> add(form("SUPPLIER", supplierId, "1.0000", "RECHARGE", PAST, null, null)), 41140);
        assertThat(paymentsOfSupplier(supplierId)).isEqualTo(3);

        expectSqlFailure("INSERT INTO finance_payment (payment_no, counterparty_type, counterparty_id,"
                        + " counterparty_name_snapshot, amount, method, paid_at, entry_type)"
                        + " VALUES ('PM-R-1', 'SUPPLIER', ?, '供应商', 1.0000, 'ONLINE_PAYMENT', now(), 'NORMAL')",
                supplierId);
    }

    @Test
    @DisplayName("S 金额形态与收款同一契约：0 / 负 / 超精度 / 科学计数 / NaN 全拒，合法值补齐 scale 4")
    void amountUsesTheSharedStrictDecimalContract() {
        Long supplierId = supplier();
        expectCode(() -> add(supplierForm(supplierId, "0")), 40000);
        expectCode(() -> add(supplierForm(supplierId, "0.0000")), 40000);
        expectCode(() -> add(supplierForm(supplierId, "-1.0000")), 40000);
        expectCode(() -> add(supplierForm(supplierId, "12.34567")), 40000);
        expectCode(() -> add(supplierForm(supplierId, "1e5")), 40000);
        expectCode(() -> add(supplierForm(supplierId, "NaN")), 40000);
        assertThat(paymentsOfSupplier(supplierId)).isZero();

        Long preciseId = add(supplierForm(supplierId, "99.9999")).getPaymentId();
        assertThat((BigDecimal) paymentRow(preciseId).get("amount")).isEqualByComparingTo("99.9999");
        assertThat(((BigDecimal) paymentRow(preciseId).get("amount")).scale()).as("落库标度恒 4").isEqualTo(4);
        Long paddedId = add(supplierForm(supplierId, "10")).getPaymentId();
        assertThat(((BigDecimal) paymentRow(paddedId).get("amount")).toPlainString()).isEqualTo("10.0000");
    }

    // ------------------------------------------------------------------
    // T / U. 数据范围（第三批 D-5）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("T 退款付款范围：客户归属本人可付；他人客户与未分配客户都失败关闭")
    void customerScopeFollowsSellerIdAndFailsClosed() {
        Refund mine = completedRefund("T-MINE", "10.0000", "1.0000");
        Refund theirs = completedRefund("T-THEIRS", "10.0000", "1.0000");
        Long employeeId = newPlainEmployee("T");
        jdbc.update("UPDATE customer SET seller_id = ? WHERE id = ?", employeeId, mine.customerId());
        Long otherSeller = newPlainEmployee("T-OTHER");
        jdbc.update("UPDATE customer SET seller_id = ? WHERE id = ?", otherSeller, theirs.customerId());
        evictMybatisCache();

        try {
            asEmployee(employeeId);
            // 自己的客户：可付（金额取库上真值）
            String mineAmount = jdbc.queryForObject("SELECT refund_amount FROM order_refund WHERE id = ?",
                    BigDecimal.class, mine.refundId()).toPlainString();
            assertThat(add(refundForm(mine.customerId(), mineAmount, mine.refundId())).getPaymentId())
                    .isNotNull();

            // 别人的客户与不存在的退款必须给出<b>同一个</b>码，否则「存在但不是你的」就成了探测信号
            String theirsAmount = jdbc.queryForObject("SELECT refund_amount FROM order_refund WHERE id = ?",
                    BigDecimal.class, theirs.refundId()).toPlainString();
            expectCode(() -> add(refundForm(theirs.customerId(), theirsAmount, theirs.refundId())), 41139);
            expectCode(() -> add(refundForm(theirs.customerId(), theirsAmount, 999999999L)), 41139);
            assertThat(paymentsOfRefund(theirs.refundId())).isZero();
        } finally {
            asAdmin();
        }

        // 未分配业务员的客户：非超管不可代其付款（completedRefund 造出来的客户 seller_id 为 NULL）
        Refund unassigned = completedRefund("T-NONE", "10.0000", "1.0000");
        try {
            asEmployee(newPlainEmployee("T2"));
            expectCode(() -> add(refundForm(unassigned.customerId(),
                    unassigned.refundAmount().toPlainString(), unassigned.refundId())), 41139);
        } finally {
            asAdmin();
            assertThat(add(refundForm(unassigned.customerId(),
                    unassigned.refundAmount().toPlainString(), unassigned.refundId())).getPaymentId())
                    .as("全范围账号可以付，证明拒绝来自范围而不是字段形态").isNotNull();
        }
    }

    @Test
    @DisplayName("U 供应商付款不受 purchaserScope / 仓库范围影响（D-5 的「无行级收窄」来自裁决不是角色 bypass）")
    void supplierPaymentIsNotNarrowedByPurchaserOrWarehouseScope() {
        Long supplierId = supplier();
        Long employeeId = newPlainEmployee("U");
        try {
            asEmployee(employeeId);
            // 该账号没有任何角色、没有任何仓库 / 采购员范围授权，连一张应付都不属于它；
            // 供应商侧按 D-5 不收窄，因此付款照样成立。
            FinancePaymentVO result = add(supplierForm(supplierId, "77.0000"));
            assertThat(result.getPaymentId()).isNotNull();
            assertThat(paymentRow(result.getPaymentId()).get("created_by"))
                    .as("登记人是这个非超管账号本人，不是超管兜底")
                    .isEqualTo(currentOperatorOf(employeeId));
        } finally {
            asAdmin();
        }
        assertThat(paymentsOfSupplier(supplierId)).isEqualTo(1);
    }

    /** {@code ScmOperator.current()} 的口径：{@code userType.getValue() + ":" + employeeId}。 */
    private static String currentOperatorOf(Long employeeId) {
        return UserTypeEnum.ADMIN_EMPLOYEE.getValue() + ":" + employeeId;
    }

    // ------------------------------------------------------------------
    // V / W / X. 幂等与日志
    // ------------------------------------------------------------------

    @Test
    @DisplayName("V 幂等重放：同键同内容返回同一张付款单，库里一张、日志一条")
    void sameKeyAndContentReplaysTheFirstResult() {
        Long supplierId = supplier();
        FinancePaymentAddForm form = supplierForm(supplierId, "210.0000");
        String idempotencyKey = key("replay");

        FinancePaymentVO first = add(form, idempotencyKey);
        FinancePaymentVO replay = add(form, idempotencyKey);

        assertThat(replay.getPaymentId()).isEqualTo(first.getPaymentId());
        assertThat(replay.getPaymentNo()).isEqualTo(first.getPaymentNo());
        assertThat(replay.getAmount()).isEqualByComparingTo(first.getAmount());
        assertThat(replay.getSourceType()).isNull();
        assertThat(paymentsOfSupplier(supplierId)).isEqualTo(1);
        assertThat(logsOf(first.getPaymentId())).hasSize(1);
        assertThat(count("SELECT count(*) FROM idempotency_record WHERE idempotency_key = ?", idempotencyKey))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("W 幂等冲突 40966 与缺键 40069：两种失败都不产生付款")
    void conflictAndMissingKeyProduceNothing() {
        Long supplierId = supplier();
        String idempotencyKey = key("conflict");
        add(supplierForm(supplierId, "10.0000"), idempotencyKey);

        expectCode(() -> add(supplierForm(supplierId, "90.0000"), idempotencyKey), 40966);
        expectCode(() -> add(supplierForm(supplierId, "10.0000"), "  "), 40069);

        assertThat(paymentsOfSupplier(supplierId)).isEqualTo(1);
        assertThat(payLogsOfSupplier(supplierId)).isEqualTo(1);
    }

    @Test
    @DisplayName("X 操作日志 PAY：改前为空、快照含来源与对方、操作人是登记人；重放不追加")
    void operationLogPaysTheRegistration() throws Exception {
        Refund refund = completedRefund("X", "10.0000", "2.0000");
        // 重放必须用**同一把** key 与**同一份请求内容**：换 key 就是另一条命令（会被来源唯一索引
        // 按重复事实拒掉），换内容则按既有语义报 40966 —— 两者都不是「重放」。
        String idempotencyKey = key("x");
        FinancePaymentAddForm form = refundForm(refund.customerId(),
                refund.refundAmount().toPlainString(), refund.refundId());
        FinancePaymentVO result = financePaymentService.add(form, idempotencyKey);

        List<Map<String, Object>> logs = logsOf(result.getPaymentId());
        assertThat(logs).hasSize(1);
        Map<String, Object> log = logs.getFirst();
        assertThat(log.get("operation_type")).isEqualTo("PAY");
        assertThat(log.get("before_data")).isNull();
        assertThat(log.get("operator")).isEqualTo(currentOperator());
        assertThat(log.get("reason")).isNull();

        Map<String, Object> after = json.readValue(String.valueOf(log.get("after_data")),
                new TypeReference<Map<String, Object>>() {
                });
        assertThat(after)
                .containsEntry("paymentNo", result.getPaymentNo())
                .containsEntry("counterpartyType", "CUSTOMER")
                .containsEntry("amount", refund.refundAmount().toPlainString())
                .containsEntry("method", "BANK_TRANSFER")
                .containsEntry("sourceType", "ORDER_REFUND")
                .containsEntry("entryType", "NORMAL");
        assertThat(((Number) after.get("counterpartyId")).longValue()).isEqualTo(refund.customerId());
        assertThat(((Number) after.get("sourceId")).longValue()).isEqualTo(refund.refundId());
        // 时点一律以 ISO 字符串入档（JSONB 侧的 typeHandler 未注册 JavaTimeModule），
        // 因此这里比对的是登记人给的那个带偏移的业务时点本身
        assertThat(after.get("paidAt")).isEqualTo(PAST.toString());

        // 同一笔付款不会因为重放再产生第二条日志
        FinancePaymentVO replay = financePaymentService.add(form, idempotencyKey);
        assertThat(replay.getPaymentId()).isEqualTo(result.getPaymentId());
        assertThat(logsOf(result.getPaymentId())).hasSize(1);
        assertThat(paymentsOfRefund(refund.refundId())).isEqualTo(1);
    }

    @Test
    @DisplayName("付款单号取 PM 前缀 + 全局序列，且跳号只来自 nextval 不随事务回滚")
    void paymentNumberComesFromTheGlobalSequence() {
        Long supplierId = supplier();
        String firstNo = add(supplierForm(supplierId, "1.0000")).getPaymentNo();
        expectCode(() -> add(supplierForm(supplierId, "0")), 40000);
        String secondNo = add(supplierForm(supplierId, "2.0000")).getPaymentNo();

        assertThat(firstNo).matches("PM\\d{8}\\d{6,}");
        assertThat(secondNo).isNotEqualTo(firstNo);
        long first = Long.parseLong(firstNo.substring(10));
        long second = Long.parseLong(secondNo.substring(10));
        // 失败的那一次也已经 nextval 过，因此序号允许跳、但必须单调递增（全局非重置序列）
        assertThat(second).isGreaterThan(first);
    }
}
