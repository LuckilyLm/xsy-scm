package net.lab1024.sa.admin.module.scm.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.lab1024.sa.admin.AdminApplication;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.customer.domain.form.CustomerAddForm;
import net.lab1024.sa.admin.module.scm.customer.domain.form.CustomerStatusForm;
import net.lab1024.sa.admin.module.scm.customer.service.CustomerService;
import net.lab1024.sa.admin.module.scm.order.domain.form.OrderActualQuantityForm;
import net.lab1024.sa.admin.module.scm.order.domain.form.OrderAddressForm;
import net.lab1024.sa.admin.module.scm.order.domain.form.OrderVersionForm;
import net.lab1024.sa.admin.module.scm.order.domain.form.SalesOrderAddForm;
import net.lab1024.sa.admin.module.scm.order.domain.form.SalesOrderItemForm;
import net.lab1024.sa.admin.module.scm.order.domain.vo.SalesOrderDetailVO;
import net.lab1024.sa.admin.module.scm.order.service.SalesOrderService;
import net.lab1024.sa.admin.module.scm.product.domain.form.ProductSkuForm;
import net.lab1024.sa.admin.module.scm.product.domain.form.ProductSpuAddForm;
import net.lab1024.sa.admin.module.scm.product.service.ProductSpuService;
import net.lab1024.sa.admin.module.scm.purchase.dao.PurchaseDemandAllocationDao;
import net.lab1024.sa.admin.module.scm.purchase.dao.PurchaseDemandDao;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseDemandAllocationEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseDemandEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseDemandGenerateForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseOrderAddForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseOrderUpdateForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseOrderVersionForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseReceiptConfirmForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseReceiptCreateForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseReceiptDeleteForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseReceiptQueryForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseReceiptUpdateForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseOrderItemVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseOrderVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseReceiptItemVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseReceiptVO;
import net.lab1024.sa.admin.module.scm.purchase.service.PurchaseDemandService;
import net.lab1024.sa.admin.module.scm.purchase.service.PurchaseOrderService;
import net.lab1024.sa.admin.module.scm.purchase.service.PurchaseQueryService;
import net.lab1024.sa.admin.module.scm.purchase.service.PurchaseReceiptService;
import net.lab1024.sa.admin.module.scm.supplier.domain.form.SupplierAddForm;
import net.lab1024.sa.admin.module.scm.supplier.domain.form.SupplierSkuItemForm;
import net.lab1024.sa.admin.module.scm.supplier.domain.form.SupplierSkuReplaceForm;
import net.lab1024.sa.admin.module.scm.supplier.service.SupplierService;
import net.lab1024.sa.admin.module.scm.supplier.service.SupplierSkuService;
import net.lab1024.sa.admin.module.scm.warehouse.domain.form.WarehouseAddForm;
import net.lab1024.sa.admin.module.scm.warehouse.service.WarehouseService;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mybatis.spring.SqlSessionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W5 PostgreSQL 集成测试基类（仿 {@code ScmW2PgITBase} / {@code ScmW3PgITBase}）。
 *
 * <p>W5 的读写在数据库里才有完整语义（partial unique index、CHECK 约束、{@code FOR UPDATE} 锁序、
 * {@code JSONB} 映射、{@code NUMERIC(18,4)} 精度、PG sequence 单号），因此这部分只能用真实
 * PostgreSQL 验证。
 *
 * <p><b>事务策略：</b>与 W1–W4 一致 —— 整个用例包在一个事务里，结束时回滚，不向开发库留下数据。
 * 种子数据（{@code warehouse} 的 WH001、{@code t_config} 的采购容差）由 V15 migration 提供，只读使用。
 *
 * <p><b>不 mock 上游域：</b>{@code supplier_sku} 依赖真实存在的 {@code product_spu} / {@code product_sku}，
 * 所以用 W1 已验收的 {@link ProductSpuService} 造数，而不是直接插表 ——
 * 直接插表会绕过 W1 的聚合不变量，让 IT 验证到一份「现实中不可能出现」的商品数据。
 *
 * <p><b>不 mock 需求来源：</b>采购需求来自 W4 的 {@code sales_order} / {@code sales_order_item}，
 * 因此 W5 的 IT 通过 W4 已验收的 {@code SalesOrderService} 造真实订单，再驱动汇总，
 * 而不是往 {@code purchase_demand} 里直接插行。
 */
@SpringBootTest(classes = AdminApplication.class, properties = {
        "project.log-directory=D:/Browser Download/xsy-scm/.runtime/logs/test",
        "file.storage.local.upload-path=D:/Browser Download/xsy-scm/.runtime/upload/",
        "file.storage.local.url-prefix=http://127.0.0.1:18082",
        "logging.level.root=WARN"})
@Transactional
public abstract class ScmW5PgITBase {

    /** V15 播种的默认仓库编码（G-03 单仓库口径）。 */
    public static final String SEED_WAREHOUSE_CODE = "WH001";

    /**
     * 基类造的 SKU 一律以 {@code kg} 为销售单位（见 {@link #newProductSku}）。
     *
     * <p>**Q17**：采购单位必须与需求单位（= 销售单位）一致才允许自动分配，
     * 因此默认采购单位也是 {@code kg}；要测「单位不一致 → 40971」的用例显式传别的单位。
     */
    public static final String DEFAULT_PURCHASE_UNIT = "kg";

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected ObjectMapper json;

    @Autowired
    protected ProductSpuService productSpuService;

    @Autowired
    protected WarehouseService warehouseService;

    @Autowired
    protected SupplierService supplierService;

    @Autowired
    protected SupplierSkuService supplierSkuService;

    @Autowired
    protected CustomerService customerService;

    @Autowired
    protected SalesOrderService salesOrderService;

    // ---- W5 自身（需求 / 采购单 / 只读查询）----

    @Autowired
    protected PurchaseDemandService purchaseDemandService;

    @Autowired
    protected PurchaseOrderService purchaseOrderService;

    @Autowired
    protected PurchaseReceiptService purchaseReceiptService;

    @Autowired
    protected PurchaseQueryService purchaseQueryService;

    @Autowired
    protected PurchaseDemandDao purchaseDemandDao;

    @Autowired
    protected PurchaseDemandAllocationDao purchaseDemandAllocationDao;

    @Autowired
    protected SqlSessionFactory sqlSessionFactory;

    @Autowired
    protected PlatformTransactionManager transactionManager;

    /** 每个用例独立的编码前缀，避免与其它用例或既有数据撞 partial unique index。 */
    protected String prefix;

    /**
     * 同一用例内造第几张销售订单。
     *
     * <p><b>为什么必须逐张唯一</b>：W4 的 `SalesOrderService` 按「幂等键 + 请求内容哈希」判定重放。
     * 一个用例里造两张订单（例如「一个 SKU 要两个需求」）如果复用同一个幂等键，第二次会因为
     * **内容不同**而抛 40990「相同幂等键的请求内容不一致」—— 看起来像 W5 的分配逻辑出错，
     * 实际只是测试自己把幂等键写重了。
     */
    private int salesOrderSequence;

    /**
     * 同一用例内造第几个客户。
     *
     * <p><b>为什么必须逐个唯一</b>：`t_customer.customer_code` 上有唯一索引，而
     * {@link #newCustomer()} 无参（调用方只关心「有个客户」）。若编码固定为 {@code prefix}，
     * 一个用例里调两次（例如 {@code receiptFixture} 造两套数据）就会撞「客户编码已存在」——
     * 报错点落在上游 W2 的 {@code CustomerService.add}，看起来像客户域坏了。
     */
    private int customerSequence;

    @BeforeEach
    void setUpOperator() {
        prefix = "W5-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT);
        salesOrderSequence = 0;
        customerSequence = 0;
        RequestEmployee employee = new RequestEmployee();
        employee.setEmployeeId(1L);
        employee.setActualName("W5 IT");
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        SmartRequestUtil.setRequestUser(employee);
    }

    @AfterEach
    void tearDownOperator() {
        SmartRequestUtil.remove();
    }

    // ------------------------------------------------------------------
    // 断言辅助
    // ------------------------------------------------------------------

    /**
     * 清空 MyBatis 一级缓存。
     *
     * <p><b>为什么必须显式调用</b>：整个用例跑在一个 Spring 事务里，MyBatis 的 SqlSession 与事务同生命周期，
     * 因此一级缓存跨越多次 Service 调用。{@code JdbcTemplate} 直改库**不会**清这个缓存，
     * 于是「先用 Service 读、再用 JdbcTemplate 改、再用 Service 读」会读到事务内的旧值，
     * 表现为断言莫名失败。凡是绕过 DAO 改库之后还要走 Service 读，就必须先调本方法。
     */
    protected void evictMybatisCache() {
        SqlSessionUtils.getSqlSession(sqlSessionFactory).clearCache();
    }

    /** 断言动作抛出带指定业务码的 {@link ScmBusinessException}。 */
    protected static void expectCode(Runnable action, int code) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ScmBusinessException.class,
                        e -> assertThat(e.getErrorCode().getCode()).isEqualTo(code));
    }

    /**
     * 在**同一个事务 / 同一条连接**里执行一段必然失败的 SQL，并断言它失败
     * （CHECK / 唯一索引 / NOT NULL）。
     *
     * <p><b>为什么必须隔离</b>：PostgreSQL 一旦在事务内报错就把整个事务置为 aborted，
     * 后续语句全部以「current transaction is aborted」失败 —— 一个「验证 CHECK 生效」的断言
     * 会顺手毁掉这个用例的其余部分。
     *
     * <p><b>为什么用 SAVEPOINT 而**不是** {@code PROPAGATION_REQUIRES_NEW}</b>：
     * `REQUIRES_NEW` 会挂起外层事务、另开一条连接，但外层事务**仍然持有**相关行的锁与唯一索引项。
     * 于是内层连接去插同一个键时**永久阻塞**等待外层提交，而外层正在等内层返回 ——
     * PostgreSQL 的死锁检测**看不到**这种循环（外层只是在应用层等待，没有向数据库发任何语句），
     * 结果是整个测试**无声挂死**，既不报错也不超时。
     *
     * <p>SAVEPOINT 在同一条连接上完成隔离：失败后 {@code ROLLBACK TO SAVEPOINT}，
     * 外层事务恢复可用；而且同一事务内的唯一索引冲突是**立即报错**（不是等锁），
     * 因此不存在自我阻塞。
     */
    protected void expectSqlFailure(String sql, Object... args) {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            Savepoint savepoint = connection.setSavepoint("expect_sql_failure");
            boolean failed = false;
            try {
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    for (int index = 0; index < args.length; index++) {
                        statement.setObject(index + 1, args[index]);
                    }
                    statement.executeUpdate();
                }
            } catch (SQLException e) {
                failed = true;
                connection.rollback(savepoint);
            }
            assertThat(failed)
                    .as("期望这条 SQL 失败，但它成功了（约束没生效？）：%s", sql)
                    .isTrue();
            return null;
        });
    }

    // ------------------------------------------------------------------
    // 数据辅助
    // ------------------------------------------------------------------

    /** V15 播种的默认仓库 id。 */
    protected Long seedWarehouseId() {
        return warehouseId(SEED_WAREHOUSE_CODE);
    }

    protected Long warehouseId(String warehouseCode) {
        return jdbc.queryForObject(
                "SELECT id FROM warehouse WHERE warehouse_code = ? AND deleted = FALSE",
                Long.class, warehouseCode);
    }

    /** 新建一个独立仓库（测试用，避免与种子仓库的状态改动互相污染）。 */
    protected Long newWarehouse(String suffix) {
        WarehouseAddForm form = new WarehouseAddForm();
        form.setWarehouseCode(prefix + "-" + suffix);
        form.setName(prefix + "仓" + suffix);
        return warehouseService.create(form);
    }

    /** 任一可用员工 id，用于业务员 / 默认采购员引用。 */
    protected Long anyEmployeeId() {
        return jdbc.queryForObject(
                "SELECT employee_id FROM t_employee WHERE deleted_flag = FALSE ORDER BY employee_id LIMIT 1",
                Long.class);
    }

    protected Long newOnShelfSku(String suffix) {
        return newProductSku(suffix, "ON_SHELF", "ON_SHELF");
    }

    /**
     * 用 W1 已验收的商品聚合服务造一个真实 SKU，返回 {@code product_sku.id}。
     *
     * @param spuStatus SPU 上下架状态（{@code ON_SHELF} / {@code OFF_SHELF}）
     * @param skuStatus SKU 上下架状态
     */
    protected Long newProductSku(String suffix, String spuStatus, String skuStatus) {
        ProductSpuAddForm form = new ProductSpuAddForm();
        form.setSpuCode(prefix + suffix);
        form.setName(prefix + suffix + "商品");
        form.setStatus(spuStatus);
        form.setCategoryId(jdbc.queryForObject(
                "SELECT id FROM product_category WHERE category_code = 'FRESH-FRUIT' AND deleted = FALSE", Long.class));

        ProductSkuForm sku = new ProductSkuForm();
        sku.setSkuCode(prefix + suffix + "-K");
        sku.setSpecName("规格" + suffix);
        sku.setSpecValues(new LinkedHashMap<>(Map.of("规格", suffix)));
        sku.setSaleUnit("kg");
        sku.setProductType("NON_STANDARD");
        sku.setMarketPrice(new BigDecimal("1.2000"));
        sku.setStatus(skuStatus);
        sku.setDefaultFlag(true);
        form.setSkuList(new ArrayList<>(List.of(sku)));

        Long spuId = productSpuService.add(form);
        return jdbc.queryForObject(
                "SELECT id FROM product_sku WHERE spu_id = ? AND deleted = FALSE", Long.class, spuId);
    }

    /** 直接改库把仓库置为 DISABLED（`status` 不在 §7.2 的表单字段清单内，见验收报告 G1）。 */
    protected void disableWarehouse(Long warehouseId) {
        int updated = jdbc.update(
                "UPDATE warehouse SET status = 'DISABLED', version = version + 1, updated_at = CURRENT_TIMESTAMP "
                        + "WHERE id = ? AND deleted = FALSE", warehouseId);
        assertThat(updated).isEqualTo(1);
        evictMybatisCache();
    }

    // ------------------------------------------------------------------
    // 上游域造数（W2 供应商 / 客户，W4 销售订单）
    // ------------------------------------------------------------------

    /**
     * 新建一个启用状态的供应商（走 W2 已验收的聚合服务，不直接插表）。
     */
    protected Long newSupplier(String suffix) {
        SupplierAddForm form = new SupplierAddForm();
        form.setSupplierCode(prefix + "-" + suffix);
        form.setName(prefix + "供应商" + suffix);
        return supplierService.add(form);
    }

    /**
     * 把 SKU 挂到供应商上（W2 的**唯一写入口** `replace`，空数组 = 清空）。
     *
     * <p>`purchaseUnit` 就是 W5 的采购单位来源（{@code supplier_sku.purchase_unit} →
     * {@code purchase_order_item.purchase_unit_snapshot}）。**Q17** 要求它与需求单位
     * （{@code sales_order_item.sale_unit_snapshot}）一致才能自动分配，因此调用方要
     * 传 SKU 的 {@code sale_unit}（本基类造的 SKU 一律是 {@code kg}）。
     */
    protected void linkSupplierSku(Long supplierId, Long skuId, String purchaseUnit) {
        SupplierSkuItemForm item = new SupplierSkuItemForm();
        item.setSkuId(skuId);
        item.setPurchaseUnit(purchaseUnit);
        item.setDefaultFlag(true);
        SupplierSkuReplaceForm form = new SupplierSkuReplaceForm();
        form.setSupplierId(supplierId);
        form.setItems(new ArrayList<>(List.of(item)));
        supplierSkuService.replace(form);
    }

    /** 一个「供应商 + 已挂 SKU」的现成组合，采购单位默认取 {@code kg}（与 SKU 的销售单位一致）。 */
    protected Long newPurchasableSupplier(String suffix, Long skuId) {
        Long supplierId = newSupplier(suffix);
        linkSupplierSku(supplierId, skuId, DEFAULT_PURCHASE_UNIT);
        return supplierId;
    }

    /**
     * 一次把**多个** SKU 挂到同一个供应商。
     *
     * <p><b>不能循环调用 {@link #linkSupplierSku}</b>：它走的是 W2 的唯一写入口 {@code replace}，
     * 语义是**整体替换**（空数组 = 清空），第二次调用会把第一次挂上的 SKU 全部清掉。
     */
    protected void linkSupplierSkus(Long supplierId, Long... skuIds) {
        List<SupplierSkuItemForm> items = new ArrayList<>(skuIds.length);
        for (Long skuId : skuIds) {
            SupplierSkuItemForm item = new SupplierSkuItemForm();
            item.setSkuId(skuId);
            item.setPurchaseUnit(DEFAULT_PURCHASE_UNIT);
            item.setDefaultFlag(true);
            items.add(item);
        }
        SupplierSkuReplaceForm form = new SupplierSkuReplaceForm();
        form.setSupplierId(supplierId);
        form.setItems(items);
        supplierSkuService.replace(form);
    }

    /**
     * 客户类型 id（V2 种子字典，{@code ENTERPRISE} 一定存在）。
     *
     * <p>注意列名是 {@code id} 而**不是** {@code customer_type_id}：W2 的 `customer_type`
     * 表沿用「表名 = 实体名」的主键命名，没有前缀。写错会得到一个 `BadSqlGrammar`，
     * 而它的栈顶看起来像「fixture 造数失败」，很容易误判成需求生成的问题。
     */
    protected Long customerTypeId(String typeCode) {
        return jdbc.queryForObject(
                "SELECT id FROM customer_type WHERE type_code = ? AND deleted = FALSE",
                Long.class, typeCode);
    }

    /**
     * 新建一个 {@code COOPERATING} 客户（W2 的 `add` + `updateStatus` 两步）。
     *
     * <p>编码带**逐个递增的序号**，同一用例内可反复调用（见 {@link #customerSequence}）。
     */
    protected Long newCustomer() {
        String code = prefix + "-C" + (++customerSequence);
        CustomerAddForm form = new CustomerAddForm();
        form.setCustomerCode(code);
        form.setName(code + "客户");
        form.setCustomerTypeId(customerTypeId("ENTERPRISE"));
        form.setSettleMode("INDEPENDENT");
        form.setSellerId(anyEmployeeId());
        Long customerId = customerService.add(form);

        CustomerStatusForm status = new CustomerStatusForm();
        status.setCustomerId(customerId);
        status.setVersion(0);
        status.setStatus("COOPERATING");
        customerService.updateStatus(status);
        return customerId;
    }

    /**
     * 造一张**已确认**的销售订单（W5 需求的唯一来源口径：`status = CONFIRMED` + `confirmed_at`）。
     *
     * <p>链路完全走 W4 已验收的命令：`create → submit → actualQuantity → confirm`。
     * 非标品必须在 `confirm` 前显式给实数量，因此这里一定要调 `actualQuantity`。
     *
     * <p>幂等键带**逐张递增的序号**：同一用例造多张订单时必须各不相同，否则第二次会撞
     * W4 的「同键不同内容」判定（40990）。见 {@link #salesOrderSequence}。
     *
     * @return 该订单的 id（{@code sales_order.id}）
     */
    protected Long confirmedSalesOrder(Long customerId, Long skuId,
                                      String orderedQuantity, String actualQuantity) {
        String scope = prefix + ":so:" + (++salesOrderSequence);
        SalesOrderAddForm form = new SalesOrderAddForm();
        form.setCustomerId(customerId);
        form.setOrderSource("ADMIN");
        form.setRemark("W5 IT 来源订单");
        OrderAddressForm address = new OrderAddressForm();
        address.setReceiverName("W5 IT");
        address.setReceiverPhone("13800000000");
        address.setAddress("W5 IT 地址");
        form.setAddress(address);
        SalesOrderItemForm item = new SalesOrderItemForm();
        item.setSkuId(skuId);
        item.setOrderedQuantity(orderedQuantity);
        item.setManualPriceOverride(false);
        form.setItems(new ArrayList<>(List.of(item)));

        SalesOrderDetailVO order = salesOrderService.create(form, scope + ":create");
        order = salesOrderService.submit(salesOrderVersion(order), scope + ":submit");

        OrderActualQuantityForm actual = new OrderActualQuantityForm();
        actual.setOrderId(order.getOrderId());
        actual.setItemId(order.getItems().getFirst().getItemId());
        actual.setVersion(order.getItems().getFirst().getVersion());
        actual.setActualQuantity(actualQuantity);
        actual.setReason("W5 IT 实重");
        order = salesOrderService.actualQuantity(actual, scope + ":actual");

        return salesOrderService.confirm(salesOrderVersion(order), scope + ":confirm").getOrderId();
    }

    /** 已确认订单的来源行 id（{@code purchase_demand.sales_order_item_id} 的来源）。 */
    protected Long confirmedSalesOrderItemId(Long orderId) {
        return jdbc.queryForObject(
                "SELECT id FROM sales_order_item WHERE order_id = ? AND deleted = FALSE ORDER BY id LIMIT 1",
                Long.class, orderId);
    }

    /** 来源订单的确认时间（Q6a 的 `demand_date` 与 `source_confirmed_at` 都取自它）。 */
    protected java.time.OffsetDateTime salesOrderConfirmedAt(Long orderId) {
        return jdbc.queryForObject(
                "SELECT confirmed_at FROM sales_order WHERE id = ?", java.time.OffsetDateTime.class, orderId);
    }

    /** 一行 SKU + 供应商 + 客户 + 已确认订单的完整前置条件，返回需求生成所需的 id 组合。 */
    protected Fixture fixture(String suffix, String orderedQuantity, String actualQuantity) {
        Long skuId = newOnShelfSku(suffix);
        Long supplierId = newPurchasableSupplier(suffix, skuId);
        Long customerId = newCustomer();
        Long salesOrderId = confirmedSalesOrder(customerId, skuId, orderedQuantity, actualQuantity);
        return new Fixture(skuId, supplierId, customerId, salesOrderId,
                confirmedSalesOrderItemId(salesOrderId), salesOrderConfirmedAt(salesOrderId));
    }

    /** {@link #fixture} 的返回值。 */
    protected record Fixture(Long skuId, Long supplierId, Long customerId,
                             Long salesOrderId, Long salesOrderItemId,
                             java.time.OffsetDateTime confirmedAt) {
    }

    // ------------------------------------------------------------------
    // W5 采购单造数（T11–T14 共用）
    // ------------------------------------------------------------------

    /**
     * 汇总窗口内**恰好这一张**已确认订单 → 需求，并返回该需求。
     *
     * <p>窗口取 {@code [confirmedAt, confirmedAt + 1s)}：开发库里有 W4 遗留的 14 张
     * CONFIRMED 订单，窗口一旦放宽就会把它们一起汇总，让「本次生成几条」失去可断言性。
     */
    protected PurchaseDemandEntity generateDemandFor(Long supplierId, Long salesOrderId) {
        OffsetDateTime confirmedAt = salesOrderConfirmedAt(salesOrderId);
        PurchaseDemandGenerateForm form = new PurchaseDemandGenerateForm();
        form.setStartAt(confirmedAt);
        form.setEndAt(confirmedAt.plusSeconds(1));
        form.setWarehouseId(seedWarehouseId());
        form.setSupplierId(supplierId);
        purchaseDemandService.generate(form, prefix + ":gen:" + salesOrderId);
        return demandOfSourceItem(confirmedSalesOrderItemId(salesOrderId));
    }

    /** 按来源销售订单行读**唯一**的活动需求（{@code uk_purchase_demand_source_active} 保证唯一）。 */
    protected PurchaseDemandEntity demandOfSourceItem(Long salesOrderItemId) {
        List<PurchaseDemandEntity> rows =
                purchaseDemandDao.listActiveBySourceItemIds(List.of(salesOrderItemId));
        assertThat(rows)
                .as("来源行 %s 必须恰好有一条活动采购需求", salesOrderItemId)
                .hasSize(1);
        return rows.getFirst();
    }

    /** 重新读需求（断言分配 / 状态变化时用；绕开事务内的一级缓存）。 */
    protected PurchaseDemandEntity reloadDemand(Long demandId) {
        evictMybatisCache();
        return purchaseDemandDao.selectById(demandId);
    }

    /** 一条分配：{@code (demandId, quantity, demandVersion)} —— allocation 身份的一半。 */
    protected PurchaseOrderAddForm.Allocation allocation(PurchaseDemandEntity demand, String quantity) {
        PurchaseOrderAddForm.Allocation row = new PurchaseOrderAddForm.Allocation();
        row.setDemandId(demand.getId());
        row.setQuantity(quantity);
        row.setDemandVersion(demand.getVersion());
        return row;
    }

    /** 单行采购单请求；{@code allocations} 省略 = 该行没有任何需求来源。 */
    protected PurchaseOrderAddForm orderForm(Long supplierId, Long warehouseId, Long skuId,
                                             String quantity, String price,
                                             PurchaseOrderAddForm.Allocation... allocations) {
        PurchaseOrderAddForm form = new PurchaseOrderAddForm();
        form.setSupplierId(supplierId);
        form.setPurchaserId(anyEmployeeId());
        form.setWarehouseId(warehouseId);
        form.setPlannedArrivalDate(java.time.LocalDate.now().plusDays(3));
        form.setRemark("W5 IT 采购单");
        form.setItems(new ArrayList<>(List.of(item(skuId, quantity, price, allocations))));
        return form;
    }

    /** 一个采购行请求（无 id = 新增行）。 */
    protected PurchaseOrderAddForm.Item item(Long skuId, String quantity, String price,
                                             PurchaseOrderAddForm.Allocation... allocations) {
        PurchaseOrderAddForm.Item item = new PurchaseOrderAddForm.Item();
        item.setSkuId(skuId);
        item.setQuantity(quantity);
        item.setPrice(price);
        item.setAllocations(new ArrayList<>(List.of(allocations)));
        return item;
    }

    /**
     * 造一张**草稿**采购单：一个 SKU 行 + 指定分配集合。
     *
     * <p>{@code itemQuantity} 与分配合计刻意解耦 —— 采购行可以有「没有需求来源」的部分，
     * 这是 §7.3「一采购行一个 SKU，可承接多个需求」的必然结果（采购量 ≠ 需求量）。
     */
    protected PurchaseOrderVO createDraftOrder(String suffix, Long supplierId, Long skuId,
                                               String itemQuantity, String price,
                                               PurchaseOrderAddForm.Allocation... allocations) {
        return purchaseOrderService.create(
                orderForm(supplierId, seedWarehouseId(), skuId, itemQuantity, price, allocations),
                prefix + ":" + suffix + ":po");
    }

    /**
     * 把既有采购单读成一次**单行**编辑请求（保留行 + 替换分配集合）。
     *
     * <p>从 {@code orderDetail} 回读 {@code version}，而不是让调用方自己数 ——
     * 编辑请求的版本必须与库中一致，写错会得到 40921 而不是被测的行为。
     */
    protected PurchaseOrderUpdateForm editForm(Long orderId, String itemQuantity, String itemPrice,
                                               PurchaseOrderAddForm.Allocation... allocations) {
        PurchaseOrderVO current = purchaseQueryService.orderDetail(orderId);
        PurchaseOrderItemVO existing = current.getItems().getFirst();

        PurchaseOrderUpdateForm form = new PurchaseOrderUpdateForm();
        form.setId(current.getId());
        form.setVersion(current.getVersion());
        form.setSupplierId(current.getSupplierId());
        form.setPurchaserId(current.getPurchaserId());
        form.setWarehouseId(current.getWarehouseId());
        form.setPlannedArrivalDate(current.getPlannedArrivalDate());
        form.setRemark(current.getRemark());

        PurchaseOrderAddForm.Item row = item(existing.getSkuId(), itemQuantity, itemPrice, allocations);
        row.setId(existing.getId());
        row.setVersion(existing.getVersion());
        form.setItems(new ArrayList<>(List.of(row)));
        return form;
    }

    /** 采购单当前的全部活动分配（按 item 展开）。 */
    protected List<PurchaseDemandAllocationEntity> allocationsOf(Long orderId) {
        evictMybatisCache();
        return purchaseDemandAllocationDao.listActiveByOrderId(orderId);
    }

    /** 指定采购行上的分配（断言「只改了一条」时必须逐条比对 id）。 */
    protected PurchaseDemandAllocationEntity allocationOf(Long purchaseOrderItemId, Long demandId) {
        evictMybatisCache();
        return purchaseDemandAllocationDao.listActiveByOrderItemIds(List.of(purchaseOrderItemId)).stream()
                .filter(row -> row.getPurchaseDemandId().equals(demandId))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "allocation 不存在: item=" + purchaseOrderItemId + " demand=" + demandId));
    }

    /** 重新读采购单（断言状态 / 版本变化时用）。 */
    protected PurchaseOrderVO reloadOrder(Long orderId) {
        evictMybatisCache();
        return purchaseQueryService.orderDetail(orderId);
    }

    // ------------------------------------------------------------------
    // W5 收货造数（T13 共用）
    // ------------------------------------------------------------------

    /**
     * 提交采购单 —— 收货的前置状态（只有 {@code SUBMITTED} / {@code PARTIALLY_RECEIVED} 可收货）。
     *
     * <p>版本从库里回读，调用方不必自己数：数错会得到 40921，而不是被测的行为。
     */
    protected PurchaseOrderVO submitOrder(Long orderId) {
        PurchaseOrderVersionForm form = new PurchaseOrderVersionForm();
        form.setId(orderId);
        form.setVersion(reloadOrder(orderId).getVersion());
        return purchaseOrderService.submit(form, prefix + ":submit:" + orderId);
    }

    /**
     * 为采购单建一张草稿收货单（收货行由服务端按采购单的**全部活动行**自动生成）。
     *
     * <p>幂等键固定为 {@code prefix + ":receipt:" + orderId}，因此**同一个采购单只能调一次**。
     * 若先调它并让 `create` 因业务原因抛错（例如 DRAFT 单 → 40991），这次失败已经在本用例的
     * 事务里插入了 claim 行；再用同一个键调第二次会命中「已提交的幂等记录缺少 result_data」。
     * 那种场景要改用 {@link #createAnotherReceipt(Long, String)} 换键。
     */
    protected PurchaseReceiptVO createReceipt(Long orderId) {
        PurchaseReceiptCreateForm form = new PurchaseReceiptCreateForm();
        form.setPurchaseOrderId(orderId);
        form.setRemark("W5 IT 收货单");
        return purchaseReceiptService.create(form, prefix + ":receipt:" + orderId);
    }

    /**
     * 同一采购单的**第二张及以后**的收货单（分次到货是正常业务，W5 不限张数）。
     *
     * <p>幂等键必须逐张唯一：`create` 的 scope 是 `PURCHASE_RECEIPT_CREATE:<采购单id>`，
     * 同键同内容会命中重放、拿回同一张单（这正是 {@link #createReceipt} 的行为）。
     */
    protected PurchaseReceiptVO createAnotherReceipt(Long orderId, String suffix) {
        PurchaseReceiptCreateForm form = new PurchaseReceiptCreateForm();
        form.setPurchaseOrderId(orderId);
        form.setRemark("W5 IT 收货单 " + suffix);
        return purchaseReceiptService.create(form, prefix + ":receipt:" + orderId + ":" + suffix);
    }

    /**
     * 一条收货明细：声明数量 = 实重 = {@code quantity}。
     *
     * <p><b>为什么默认带实重</b>：基类造的 SKU 一律是 {@code NON_STANDARD}，而非标品的
     * 「有效数量」取 {@code actualWeight}、且 {@code weightSource} 必须是 {@code MANUAL}（§7.5）——
     * 只填声明数量会直接得到 40083。
     */
    protected PurchaseReceiptConfirmForm.Item receiptLine(Long receiptItemId, Integer version,
                                                          String quantity) {
        return receiptLine(receiptItemId, version, quantity, quantity);
    }

    /**
     * 一条收货明细，声明数量与实重**分开**给。
     *
     * <p>{@code actualWeight == null} 时三字段全空 —— 那是**标品**的形状；
     * 基类造的 SKU 是非标品，因此只有「验证非标品缺实重被拒」这类用例才传 null。
     */
    protected PurchaseReceiptConfirmForm.Item receiptLine(Long receiptItemId, Integer version,
                                                          String declaredQuantity,
                                                          String actualWeight) {
        PurchaseReceiptConfirmForm.Item item = new PurchaseReceiptConfirmForm.Item();
        item.setReceiptItemId(receiptItemId);
        item.setVersion(version);
        item.setReceivedQuantity(declaredQuantity);
        if (actualWeight != null) {
            item.setActualWeight(actualWeight);
            item.setWeightSource("MANUAL");
        }
        return item;
    }

    /** 确认收货请求。必须覆盖该收货单的**全部**活动行，否则 40998。 */
    protected PurchaseReceiptConfirmForm confirmForm(Long receiptId, Integer version,
                                                     PurchaseReceiptConfirmForm.Item... items) {
        PurchaseReceiptConfirmForm form = new PurchaseReceiptConfirmForm();
        form.setId(receiptId);
        form.setVersion(version);
        form.setItems(new ArrayList<>(List.of(items)));
        return form;
    }

    /** 重新读收货单明细。 */
    protected PurchaseReceiptVO reloadReceipt(Long receiptId) {
        evictMybatisCache();
        return purchaseQueryService.receiptDetail(receiptId);
    }

    /**
     * 按采购单找收货单（断言恰好一张）。
     *
     * <p>只适用于「本用例为该采购单只建了一张收货单」的场景：W5 **不限制**同一采购单的
     * 草稿收货单张数（分次到货是正常业务），所以多次建单的用例请直接用
     * {@link #reloadReceipt(Long)}。
     */
    protected PurchaseReceiptVO receiptOfOrder(Long orderId) {
        evictMybatisCache();
        PurchaseReceiptQueryForm query = new PurchaseReceiptQueryForm();
        query.setPurchaseOrderId(orderId);
        // 分页参数必须显式给：PageParam 的默认值是 null，convert2PageQuery 会直接 NPE
        query.setPageNum(1L);
        query.setPageSize(20L);
        var page = purchaseQueryService.receiptQuery(query);
        assertThat(page.getList()).as("采购单 %s 的收货单", orderId).hasSize(1);
        // 列表投影不带明细，统一回成 detail 形状，免得调用方拿到 null items
        return purchaseQueryService.receiptDetail(page.getList().getFirst().getId());
    }

    /** 收货单的收货行（按 sortOrder 升序）。 */
    protected List<PurchaseReceiptItemVO> receiptItems(Long receiptId) {
        evictMybatisCache();
        return purchaseQueryService.receiptItems(receiptId);
    }

    /** 「提交采购单 + 建收货单」的常用组合，返回草稿收货单。 */
    protected PurchaseReceiptVO submittedOrderReceipt(Long orderId) {
        submitOrder(orderId);
        return createReceipt(orderId);
    }

    /** 更新收货单备注的请求（`receipt.update` 只允许改备注）。 */
    protected PurchaseReceiptUpdateForm receiptRemarkForm(Long receiptId, Integer version, String remark) {
        PurchaseReceiptUpdateForm form = new PurchaseReceiptUpdateForm();
        form.setId(receiptId);
        form.setVersion(version);
        form.setRemark(remark);
        return form;
    }

    /** 删除收货单的请求。 */
    protected PurchaseReceiptDeleteForm receiptDeleteForm(Long receiptId) {
        PurchaseReceiptDeleteForm form = new PurchaseReceiptDeleteForm();
        form.setId(receiptId);
        return form;
    }

    /**
     * 一张**已提交**的采购单 + 它的草稿收货单（T13 六个收货 IT 的通用前置）。
     *
     * <p>{@code plannedQuantity} 同时用作销售订单的订购量与实数量，因此
     * {@code required_quantity == planned_quantity == 分配量} ——
     * 这样「收满」就是「收到 planned」，断言不必在两套口径之间换算。
     */
    protected record ReceiptFixture(Long skuId, Long supplierId, PurchaseDemandEntity demand,
                                    PurchaseOrderVO order, PurchaseReceiptVO receipt) {

        /**
         * 该采购单唯一活动行的 id（`purchase_receipt_item.purchase_order_item_id`）。
         *
         * <p>record 里的**自定义方法**（非组件 accessor）默认是包私有，
         * 而子类在 `...module.scm.purchase` 包、本基类在 `...module.scm.common` ——
         * 因此这里必须显式写 `public`，否则子类编译不过。
         */
        public Long orderItemId() {
            return order.getItems().getFirst().getId();
        }

        /** 收货行（服务端按采购单活动行自动生成，因此只有一行）。 */
        public Long receiptItemId() {
            return receipt.getItems().getFirst().getId();
        }
    }

    protected ReceiptFixture receiptFixture(String suffix, String plannedQuantity) {
        Long skuId = newOnShelfSku(suffix);
        Long supplierId = newPurchasableSupplier(suffix, skuId);
        Long customerId = newCustomer();
        Long salesOrder = confirmedSalesOrder(customerId, skuId, plannedQuantity, plannedQuantity);
        PurchaseDemandEntity demand = generateDemandFor(supplierId, salesOrder);
        PurchaseOrderVO order = createDraftOrder(suffix, supplierId, skuId, plannedQuantity, "6.2000",
                allocation(demand, plannedQuantity));
        PurchaseReceiptVO receipt = submittedOrderReceipt(order.getId());
        return new ReceiptFixture(skuId, supplierId, demand, order, receipt);
    }

    private static OrderVersionForm salesOrderVersion(SalesOrderDetailVO order) {
        OrderVersionForm form = new OrderVersionForm();
        form.setOrderId(order.getOrderId());
        form.setVersion(order.getVersion());
        return form;
    }
}
