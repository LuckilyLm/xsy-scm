package net.lab1024.sa.admin.module.scm.common;

import net.lab1024.sa.admin.module.scm.inventory.dao.InventoryBalanceDao;
import net.lab1024.sa.admin.module.scm.inventory.dao.InventoryMovementDao;
import net.lab1024.sa.admin.module.scm.inventory.domain.entity.InventoryBalanceEntity;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryBalanceQueryService;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryCommandService;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryMovementQueryService;
import net.lab1024.sa.admin.module.scm.product.domain.form.ProductSkuForm;
import net.lab1024.sa.admin.module.scm.product.domain.form.ProductSpuAddForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseDemandEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseOrderVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseReceiptItemVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseReceiptVO;
import net.lab1024.sa.admin.module.scm.purchase.support.PurchaseInventoryContract;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W6 PostgreSQL 集成测试基类（仿 {@link ScmW5PgITBase}）。
 *
 * <p>W6 的语义几乎全部落在数据库里 —— 部分唯一索引、{@code CHECK} 约束、{@code ON CONFLICT}
 * 的冲突目标匹配、{@code FOR UPDATE} 锁序、{@code NUMERIC(18,4)} 精度、以及
 * **migration 内的一次性 backfill**。因此 W6 的验证主体是 PG IT，而不是 mock 测试。
 *
 * <p><b>为什么直接继承 {@link ScmW5PgITBase}</b>：入库的唯一来源是 W5 的收货确认，
 * 造数链路（商品 → 供应商挂 SKU → 客户 → 销售订单 → 需求 → 采购单 → 收货单）在 W5 基类里
 * 已经用**已验收的领域服务**实现完毕。W6 复用它而不是往表里直插行 —— 直插行会绕过
 * W5 的聚合不变量，让 IT 验证到一份「现实中不可能出现」的采购数据。
 *
 * <p><b>事务策略</b>：与 W1–W5 一致，用例包在一个事务里、结束时回滚。
 * 唯一的例外是需要**真并发**的两个用例（见 {@code ScmInventoryConcurrencyIT}），
 * 它们用 {@code Propagation.NOT_SUPPORTED} 关掉外层事务，靠 {@link #prefix} 的随机后缀隔离数据。
 *
 * <p><b>前缀</b>：基类会把 {@code prefix} 覆盖成 {@code W6-<12 位随机>}，
 * 避免与 W1–W5 的用例或开发库既有数据撞唯一索引。
 */
public abstract class ScmW6PgITBase extends ScmW5PgITBase {

    /**
     * W6 唯一写入路径（收货确认同事务调用；IT 也直接调用它验证命令侧的边界）。
     */
    @Autowired
    protected InventoryCommandService inventoryCommandService;

    @Autowired
    protected InventoryBalanceQueryService inventoryBalanceQueryService;

    @Autowired
    protected InventoryMovementQueryService inventoryMovementQueryService;

    @Autowired
    protected InventoryBalanceDao inventoryBalanceDao;

    @Autowired
    protected InventoryMovementDao inventoryMovementDao;

    @Autowired
    protected ApplicationContext applicationContext;

    /**
     * W6 的迁移文件名。
     *
     * <p>backfill 的 SQL **不复制到测试里**：本基类直接从 classpath 上读 V19 的原文并按
     * {@code -- Step N} 标记切段执行。理由：把 SQL 抄一份到测试里，抄错或漂移都会让
     * 「backfill 已验证」变成一句没有依据的话；读原文则保证被测的就是**上线的那段 SQL**。
     * 标记一旦被改名，切段会立刻失败（断言非零长度），不会静默跳过。
     */
    protected static final String V19 = "V19__scm_inventory.sql";

    @BeforeEach
    void setUpW6Prefix() {
        // 基类（W5）的 @BeforeEach 先跑并把 prefix 设成 W5-xxxx；JUnit5 保证父类先于子类，
        // 因此这里覆盖是安全的、也是必需的。
        prefix = "W6-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------
    // 造数：库存视角的收货夹具
    // ------------------------------------------------------------------

    /**
     * 一个「SKU + 供应商 + 需求 + 已提交采购单 + 草稿收货单」的组合。
     *
     * <p>与 W5 的 {@code ReceiptFixture} 的区别：**SKU 由调用方传入**。
     * W6 的核心用例是「同一个 (warehouse, sku) 被多次入库」，必须能跨夹具共享 SKU；
     * W5 的夹具每次新建 SKU，正好不满足这个需要。
     */
    protected record W6Fixture(Long skuId, Long supplierId, PurchaseDemandEntity demand,
                               PurchaseOrderVO order, PurchaseReceiptVO receipt) {

        /**
         * 采购单唯一活动行的 id。record 的自定义方法默认包私有，子类在别的包，必须显式 public。
         */
        public Long orderItemId() {
            return order.getItems().getFirst().getId();
        }

        /**
         * 收货行的 id（= 库存流水的 {@code source_document_item_id}）。
         */
        public Long receiptItemId() {
            return receipt.getItems().getFirst().getId();
        }
    }

    /**
     * 造一套**带需求来源**的入库夹具：销售订单 → 需求 → 采购单（含分配）→ 收货单。
     *
     * <p>用它的场景是「走完整业务链的入库」。需求分配要求采购单位 == 需求单位（W5 Q17），
     * 因此这里的采购单位固定 {@code kg}（与基类造的 SKU 销售单位一致）。
     */
    protected W6Fixture inboundFixture(String suffix, Long skuId, String quantity) {
        Long supplierId = newPurchasableSupplier(suffix, skuId);
        Long customerId = newCustomer();
        Long salesOrder = confirmedSalesOrder(customerId, skuId, quantity, quantity);
        PurchaseDemandEntity demand = generateDemandFor(supplierId, salesOrder);
        PurchaseOrderVO order = createDraftOrder(suffix, supplierId, skuId, quantity, "6.2000",
                allocation(demand, quantity));
        PurchaseReceiptVO receipt = submittedOrderReceipt(order.getId());
        return new W6Fixture(skuId, supplierId, demand, order, receipt);
    }

    /**
     * 造一套**没有需求来源**的入库夹具（采购行无分配，W5 §7.3 允许）。
     *
     * <p>需要它的唯一原因是**单位**：需求分配要求采购单位 == 销售单位，
     * 所以「同一个 SKU 用不同采购单位入库」这件事无法经由需求分配走通。
     * 无来源采购行绕开了这条约束，让 Q13 的单位不匹配场景可以在真实数据上被构造出来。
     */
    protected W6Fixture freeInboundFixture(String suffix, Long skuId, String quantity,
                                           String purchaseUnit) {
        Long supplierId = newSupplier(suffix);
        linkSupplierSku(supplierId, skuId, purchaseUnit);
        PurchaseOrderVO order = createDraftOrder(suffix, supplierId, skuId, quantity, "6.2000");
        PurchaseReceiptVO receipt = submittedOrderReceipt(order.getId());
        return new W6Fixture(skuId, supplierId, null, order, receipt);
    }

    /**
     * 造一个指定上下架状态与**商品类型**的 SKU。
     *
     * <p>W5 基类的 {@code newProductSku} 把 {@code productType} 写死为 {@code NON_STANDARD}
     * （非标品必须有实重），因此「标品 + 非标品混合收货」这条用例需要这里另开一个入口。
     * 标品与非标品的差别只在校验口径（非标品：实重必填且来源 MANUAL），不是另一张表。
     */
    protected Long newSkuOfType(String suffix, String productType, String status) {
        ProductSpuAddForm form = new ProductSpuAddForm();
        form.setSpuCode(prefix + suffix);
        form.setName(prefix + suffix + "商品");
        form.setStatus(status);
        form.setCategoryId(jdbc.queryForObject(
                "SELECT id FROM product_category WHERE category_code = 'FRESH-FRUIT' AND deleted = FALSE",
                Long.class));

        ProductSkuForm sku = new ProductSkuForm();
        sku.setSkuCode(prefix + suffix + "-K");
        sku.setSpecName("规格" + suffix);
        sku.setSpecValues(new LinkedHashMap<>(Map.of("规格", suffix)));
        sku.setSaleUnit("kg");
        sku.setProductType(productType);
        sku.setMarketPrice(new BigDecimal("1.2000"));
        sku.setStatus(status);
        sku.setDefaultFlag(true);
        form.setSkuList(new ArrayList<>(List.of(sku)));

        Long spuId = productSpuService.add(form);
        return jdbc.queryForObject(
                "SELECT id FROM product_sku WHERE spu_id = ? AND deleted = FALSE", Long.class, spuId);
    }

    // ------------------------------------------------------------------
    // 动作：确认收货（入库的唯一触发点）
    // ------------------------------------------------------------------

    /**
     * 确认收货（幂等键由本方法生成）。
     *
     * <p>幂等键必须**逐次唯一**，否则第二次确认会命中 W5 的幂等重放而拿回第一次的结果 ——
     * 那是 {@code PurchaseReceiptServiceIT} 的用例，不是入库用例想验证的东西。
     * 要验证重放请用 {@link #confirmReceipt(Long, String, String)} 显式传键。
     */
    protected PurchaseReceiptVO confirmReceipt(Long receiptId, String quantity) {
        return confirmReceipt(receiptId, quantity,
                prefix + ":confirm:" + receiptId + ":" + quantity + ":" + UUID.randomUUID());
    }

    /**
     * 确认收货，显式指定幂等键。
     *
     * <p>版本与行版本都在这里**回读**，调用方不必自己数：数错会得到 40921，
     * 而不是被测的行为。
     */
    protected PurchaseReceiptVO confirmReceipt(Long receiptId, String quantity, String idempotencyKey) {
        PurchaseReceiptVO current = reloadReceipt(receiptId);
        PurchaseReceiptItemVO line = current.getItems().getFirst();
        return purchaseReceiptService.confirm(
                confirmForm(current.getId(), current.getVersion(),
                        receiptLine(line.getId(), line.getVersion(), quantity)),
                idempotencyKey);
    }

    // ------------------------------------------------------------------
    // 断言辅助：余额 / 流水
    // ------------------------------------------------------------------

    /**
     * 余额行（无锁读，不存在返回 {@code null}）。
     */
    protected InventoryBalanceEntity balanceRow(Long warehouseId, Long skuId) {
        evictMybatisCache();
        return inventoryBalanceDao.selectByWarehouseAndSku(warehouseId, skuId);
    }

    /**
     * 余额行数（物理行数，含 soft-deleted —— 用来验证「并发下只建了一行」）。
     */
    protected int balanceRowCount(Long warehouseId, Long skuId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM inventory_balance WHERE warehouse_id = ? AND sku_id = ?",
                Integer.class, warehouseId, skuId);
    }

    /**
     * 活动流水条数。
     */
    protected int movementCount(Long warehouseId, Long skuId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM inventory_movement "
                        + "WHERE warehouse_id = ? AND sku_id = ? AND deleted = FALSE",
                Integer.class, warehouseId, skuId);
    }

    /**
     * 活动流水，按 {@code occurred_at ASC, id ASC}（= 业务发生顺序 = 回放顺序）。
     *
     * <p>刻意按**业务时刻**排序而不是 id：Q5 的修正点正是「id 顺序可能与 occurred_at 顺序不一致」，
     * 用 id 排序会把被测的缺陷藏起来。
     */
    protected List<Map<String, Object>> movementsOf(Long warehouseId, Long skuId) {
        return jdbc.queryForList(
                "SELECT * FROM inventory_movement "
                        + "WHERE warehouse_id = ? AND sku_id = ? AND deleted = FALSE "
                        + "ORDER BY occurred_at ASC, id ASC", warehouseId, skuId);
    }

    /**
     * 某个收货行是否已有活动流水（源身份防重）。
     */
    protected int movementsOfReceiptItem(Long receiptItemId) {
        return inventoryMovementDao.countActiveBySourceItem(
                PurchaseInventoryContract.SOURCE_DOCUMENT_TYPE, receiptItemId);
    }

    /**
     * 收货单的确认时刻（{@code purchase_receipt.confirmed_at}）。
     */
    protected OffsetDateTime receiptConfirmedAt(Long receiptId) {
        return jdbc.queryForObject(
                "SELECT confirmed_at FROM purchase_receipt WHERE id = ?", OffsetDateTime.class, receiptId);
    }

    /**
     * 收货单的操作者（{@code purchase_receipt.operator}）。
     */
    protected String receiptOperator(Long receiptId) {
        return jdbc.queryForObject(
                "SELECT operator FROM purchase_receipt WHERE id = ?", String.class, receiptId);
    }

    /**
     * 采购行累计收货量。
     */
    protected BigDecimal receivedQuantityOf(Long purchaseOrderItemId) {
        evictMybatisCache();
        return jdbc.queryForObject(
                "SELECT received_quantity FROM purchase_order_item WHERE id = ?",
                BigDecimal.class, purchaseOrderItemId);
    }

    /**
     * 把 {@code jdbc} 结果里的时间列归一成 {@link OffsetDateTime}。
     *
     * <p><b>为什么需要它</b>：PostgreSQL 驱动在**类型化读取**
     * （{@code queryForObject(sql, OffsetDateTime.class)}）时直接返回 {@code OffsetDateTime}，
     * 但在 {@code queryForList} 的 Map 读法里 {@code timestamptz} 返回的是
     * {@link java.sql.Timestamp}。两种读法混用时若不归一，就会得到一个只在 Map 读法上
     * 出现的 {@code ClassCastException} —— 看起来像「流水表的时间列类型不对」，其实是读法差异。
     *
     * <p>{@link java.sql.Timestamp#toInstant()} 给出的是正确的**时刻**，因此断言应当比较
     * {@code toInstant()} 而不是比较 offset（两者的 offset 表示可以不同而时刻相同）。
     */
    protected static OffsetDateTime timestampOf(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant().atOffset(java.time.ZoneOffset.UTC);
        }
        throw new IllegalArgumentException(
                "无法归一为 OffsetDateTime 的列类型: " + value.getClass().getName());
    }

    // ------------------------------------------------------------------
    // 迁移 SQL 切段（保证被测的就是上线的那段 SQL）
    // ------------------------------------------------------------------

    /**
     * 读取 classpath 上的迁移文件原文。
     */
    protected static String migrationSql(String fileName) {
        try (InputStream in = ScmW6PgITBase.class.getClassLoader()
                .getResourceAsStream("db/migration/" + fileName)) {
            assertThat(in).as("classpath 上找不到迁移文件 db/migration/%s", fileName).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读取迁移文件失败: " + fileName, e);
        }
    }

    /**
     * 按标记切出迁移文件的一段 SQL。
     *
     * @param fromMarker 起始标记（含）
     * @param toMarker   结束标记（不含）
     */
    protected static String migrationSection(String fileName, String fromMarker, String toMarker) {
        String sql = migrationSql(fileName);
        int from = sql.indexOf(fromMarker);
        assertThat(from)
                .as("%s 中找不到起始标记 %s（标记被改名了？）", fileName, fromMarker)
                .isGreaterThanOrEqualTo(0);
        int to = sql.indexOf(toMarker, from);
        assertThat(to)
                .as("%s 中找不到结束标记 %s（标记被改名了？）", fileName, toMarker)
                .isGreaterThan(from);
        return sql.substring(from, to);
    }

    /**
     * V19 Step 1：Q13 单位不变量前置检查（同 (warehouse, sku) 混单位 → RAISE EXCEPTION）。
     */
    protected void runBackfillUnitPreCheck() {
        jdbc.execute(migrationSection(V19, "-- Step 1", "-- Step 2"));
    }

    /**
     * V19 Step 2：回放历史 CONFIRMED 收货行为 PURCHASE_IN 流水（幂等）。
     */
    protected void runBackfillMovements() {
        jdbc.execute(migrationSection(V19, "-- Step 2", "-- Step 3"));
    }

    /**
     * V19 Step 3：由流水汇总余额（幂等）。
     */
    protected void runBackfillBalances() {
        jdbc.execute(migrationSection(V19, "-- Step 3", "-- Step 4"));
    }

    /**
     * V19 Step 4：对账断言（缺失流水 / 余额与流水不等 → RAISE EXCEPTION）。
     */
    protected void runBackfillReconciliation() {
        jdbc.execute(migrationSection(V19, "-- Step 4", "COMMENT ON TABLE"));
    }

    /**
     * 把某个收货行的采购单位快照改成别的值。
     *
     * <p><b>为什么需要它</b>：Q13 的「同 (warehouse, sku) 混单位」在实时路径上**不可能产生**
     * ——第二笔异单位入库会在 {@code InventoryCommandService} 抛 41001 并整体回滚，
     * 因此永远落不了库。要验证 backfill 的前置检查，只能构造一份「历史遗留的脏数据」：
     * 先用实时路径正常确认两笔同单位收货，再把其中一笔的历史快照改成异单位。
     * 这**正是**前置检查存在的理由（实时路径上线前遗留的数据没人挡过）。
     */
    protected void overrideReceiptItemUnit(Long receiptItemId, String unit) {
        int updated = jdbc.update(
                "UPDATE purchase_receipt_item SET purchase_unit_snapshot = ? WHERE id = ?",
                unit, receiptItemId);
        assertThat(updated).isEqualTo(1);
        evictMybatisCache();
    }

    /**
     * 把收货单的确认时刻改成一个**确定的历史时刻**。
     *
     * <p>用来构造「{@code receipt_item.id} 顺序与 {@code confirmed_at} 顺序不一致」的历史数据
     * （Q5 修正的核心场景），以及证明回放的 {@code occurred_at} 取的是收货事实而不是 {@code now()}。
     */
    protected void overrideReceiptConfirmedAt(Long receiptId, OffsetDateTime confirmedAt) {
        int updated = jdbc.update(
                "UPDATE purchase_receipt SET confirmed_at = ? WHERE id = ?", confirmedAt, receiptId);
        assertThat(updated).isEqualTo(1);
        evictMybatisCache();
    }

    /**
     * 在事务临时账本中移除某个 (warehouse, sku) 的流水，构造迁移前历史。
     *
     * <p><b>只用于「把时钟拨回去」</b>：要验证 backfill 的回放顺序，必须让这些历史收货看起来
     * 「还没有被任何路径入库过」。实时路径已经写过一遍流水，所以先物理删掉它们，
     * 再执行 V19 的 backfill 段，观察回放结果。
     *
     * <p>V21 禁止真实账本的 UPDATE / DELETE / TRUNCATE，因此复制到当前连接的临时表，
     * 让后续未限定 schema 的 V19 回放 SQL 使用该副本。LIKE INCLUDING ALL 保留列、
     * CHECK 和唯一索引，不复制触发器；真实表的保护始终开启，未删除任何真实流水。
     * 临时表及其 identity 序列随测试事务结束而销毁。
     */
    protected void eraseMovementsFor(Long warehouseId, Long skuId) {
        jdbc.execute("CREATE TEMP TABLE w6_movement_replay "
                + "(LIKE inventory_movement INCLUDING ALL) ON COMMIT DROP");
        jdbc.execute("INSERT INTO pg_temp.w6_movement_replay SELECT * FROM inventory_movement");
        jdbc.execute("ALTER TABLE pg_temp.w6_movement_replay RENAME TO inventory_movement");
        jdbc.execute("SELECT setval(pg_get_serial_sequence('pg_temp.inventory_movement', 'id'), "
                + "COALESCE((SELECT max(id) FROM pg_temp.inventory_movement), 1), "
                + "EXISTS (SELECT 1 FROM pg_temp.inventory_movement))");
        int deleted = jdbc.update(
                "DELETE FROM pg_temp.inventory_movement WHERE warehouse_id = ? AND sku_id = ?",
                warehouseId, skuId);
        assertThat(deleted).isGreaterThan(0);
        evictMybatisCache();
    }
}
