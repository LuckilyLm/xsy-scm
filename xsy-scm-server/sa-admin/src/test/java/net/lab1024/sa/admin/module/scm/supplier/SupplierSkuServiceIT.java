package net.lab1024.sa.admin.module.scm.supplier;

import com.fasterxml.jackson.databind.JsonNode;
import net.lab1024.sa.admin.module.scm.common.ScmW2PgITBase;
import net.lab1024.sa.admin.module.scm.supplier.domain.form.SupplierAddForm;
import net.lab1024.sa.admin.module.scm.supplier.domain.form.SupplierSkuItemForm;
import net.lab1024.sa.admin.module.scm.supplier.domain.form.SupplierSkuQueryForm;
import net.lab1024.sa.admin.module.scm.supplier.domain.form.SupplierSkuReplaceForm;
import net.lab1024.sa.admin.module.scm.supplier.domain.form.SupplierStatusForm;
import net.lab1024.sa.admin.module.scm.supplier.domain.vo.SupplierSkuVO;
import net.lab1024.sa.admin.module.scm.supplier.service.SupplierService;
import net.lab1024.sa.admin.module.scm.supplier.service.SupplierSkuService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 商品-供应商关系（{@code supplier_sku}）在真实 PostgreSQL 上的行为（T11）。
 *
 * <p>这是 W2 规则密度最高的一张表，因此 IT 覆盖它的全部 legacy 不变量：
 * 整表替换的差量语义（R5 / R7–R11）、快照冻结（R4）、
 * 以及<b>最容易被「顺手加个约束」破坏的 R12</b>——同一供应商允许多条默认来源。
 *
 * <p>另外单独验证「清空后重新添加同一 SKU」不会撞 {@code uk_supplier_sku_active}，
 * 因为该索引是 partial（{@code WHERE deleted = FALSE}）——这是软删 + 唯一索引组合的经典坑。
 */
@DisplayName("商品-供应商关系：整表替换与快照（PG IT）")
class SupplierSkuServiceIT extends ScmW2PgITBase {

    @Autowired
    private SupplierService supplierService;

    @Autowired
    private SupplierSkuService skuService;

    private Long supplier(String suffix) {
        SupplierAddForm form = new SupplierAddForm();
        form.setSupplierCode(prefix + "-" + suffix);
        form.setName("供应商" + suffix);
        return supplierService.add(form);
    }

    private SupplierSkuItemForm item(Long skuId) {
        SupplierSkuItemForm item = new SupplierSkuItemForm();
        item.setSkuId(skuId);
        item.setPurchaseUnit("kg");
        item.setDefaultFlag(false);
        return item;
    }

    private SupplierSkuItemForm item(Long id, Integer version, Long skuId) {
        SupplierSkuItemForm item = item(skuId);
        item.setId(id);
        item.setVersion(version);
        return item;
    }

    private void replace(Long supplierId, List<SupplierSkuItemForm> items) {
        SupplierSkuReplaceForm form = new SupplierSkuReplaceForm();
        form.setSupplierId(supplierId);
        form.setItems(items);
        skuService.replace(form);
    }

    private Map<String, Object> row(Long id) {
        return jdbc.queryForMap(
                "SELECT supplier_id, sku_id, supplier_code_snapshot, supplier_name_snapshot, "
                        + "sku_code_snapshot, sku_name_snapshot, purchase_unit, reference_price, "
                        + "purchaser_id, is_default, status, version, deleted, created_by "
                        + "FROM supplier_sku WHERE id = ?", id);
    }

    @Test
    @DisplayName("首次写入冻结快照：供应商与商品在关联建立后被改名也不影响已存快照")
    void replaceFreezesSnapshots() {
        Long supplierId = supplier("a");
        Long skuId = newOnShelfSku("A1");

        SupplierSkuItemForm item = item(skuId);
        item.setReferencePrice("12.3456");
        item.setDefaultFlag(true);
        item.setPurchaserId(anyEmployeeId());
        replace(supplierId, List.of(item));

        Long id = jdbc.queryForObject(
                "SELECT id FROM supplier_sku WHERE supplier_id = ? AND sku_id = ? AND deleted = FALSE",
                Long.class, supplierId, skuId);
        Map<String, Object> row = row(id);

        assertThat(row.get("supplier_code_snapshot")).isEqualTo((prefix + "-a").toUpperCase(Locale.ROOT));
        assertThat(row.get("supplier_name_snapshot")).isEqualTo("供应商a");
        assertThat(row.get("sku_code_snapshot")).isEqualTo(prefix + "A1-K");
        assertThat((String) row.get("sku_name_snapshot"))
                .as("legacy R4：名称取 SPU 名称，不是 SKU 规格名")
                .isEqualTo(prefix + "A1商品");
        assertThat(row.get("purchase_unit")).isEqualTo("kg");
        assertThat((BigDecimal) row.get("reference_price")).isEqualByComparingTo("12.3456");
        assertThat(row.get("is_default")).isEqualTo(true);
        assertThat(row.get("status")).isEqualTo("ENABLED");
        assertThat(((Number) row.get("version")).intValue()).isZero();
        assertThat(row.get("deleted")).isEqualTo(false);
        assertThat(row.get("created_by")).isEqualTo("1:1");
    }

    @Test
    @DisplayName("差量替换：保留行保持主键、版本推进；被移除行软删且不影响保留行")
    void retainsIdentityAndSoftDeletesRemoved() {
        Long supplierId = supplier("b");
        Long firstSku = newOnShelfSku("B1");
        Long secondSku = newOnShelfSku("B2");
        replace(supplierId, List.of(item(firstSku), item(secondSku)));

        Long keptId = jdbc.queryForObject(
                "SELECT id FROM supplier_sku WHERE supplier_id = ? AND sku_id = ? AND deleted = FALSE",
                Long.class, supplierId, firstSku);
        Long removedId = jdbc.queryForObject(
                "SELECT id FROM supplier_sku WHERE supplier_id = ? AND sku_id = ? AND deleted = FALSE",
                Long.class, supplierId, secondSku);

        SupplierSkuItemForm kept = item(keptId, 0, firstSku);
        kept.setPurchaseUnit("件");
        kept.setReferencePrice("9.5");
        replace(supplierId, List.of(kept));

        Map<String, Object> keptRow = row(keptId);
        assertThat(keptRow.get("purchase_unit")).isEqualTo("件");
        assertThat((BigDecimal) keptRow.get("reference_price")).isEqualByComparingTo("9.5000");
        assertThat(((Number) keptRow.get("version")).intValue()).as("保留行版本推进到 1").isEqualTo(1);
        assertThat(keptRow.get("deleted")).isEqualTo(false);

        assertThat(jdbc.queryForObject("SELECT deleted FROM supplier_sku WHERE id = ?", Boolean.class, removedId))
                .isTrue();
        assertThat(skuService.listBySupplierId(supplierId)).extracting(SupplierSkuVO::getId)
                .containsExactly(keptId);
    }

    @Test
    @DisplayName("空数组 = 清空全部关联（R11），不是无操作")
    void emptyItemsClearsAll() {
        Long supplierId = supplier("c");
        replace(supplierId, List.of(item(newOnShelfSku("C1")), item(newOnShelfSku("C2"))));

        replace(supplierId, List.of());

        assertThat(skuService.listBySupplierId(supplierId)).isEmpty();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM supplier_sku WHERE supplier_id = ? AND deleted = FALSE",
                Integer.class, supplierId)).isZero();
    }

    @Test
    @DisplayName("R12：同一供应商允许多条默认来源，绝不发明「只允许一条默认」的基数策略")
    void multipleDefaultsAreAllowed() {
        Long supplierId = supplier("d");
        Long firstSku = newOnShelfSku("D1");
        Long secondSku = newOnShelfSku("D2");

        SupplierSkuItemForm first = item(firstSku);
        first.setDefaultFlag(true);
        SupplierSkuItemForm second = item(secondSku);
        second.setDefaultFlag(true);
        replace(supplierId, List.of(first, second));

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM supplier_sku WHERE supplier_id = ? AND is_default = TRUE AND deleted = FALSE",
                Integer.class, supplierId)).isEqualTo(2);

        // 走服务读路径再断言一次：`is_default` 列名与实体属性 `defaultFlag` 不同源，
        // 自定义 resultMap 若漏掉显式映射，自动驼峰映射会把它折成 `isDefault` 而静默丢值
        // —— 只查裸 SQL 是抓不到这个回归的。
        List<SupplierSkuVO> readBack = skuService.listBySupplierId(supplierId);
        assertThat(readBack).hasSize(2);
        assertThat(readBack).allSatisfy(row -> assertThat(row.getDefaultFlag()).isTrue());
        assertThat(readBack).extracting(SupplierSkuVO::getSkuId)
                .containsExactlyInAnyOrder(firstSku, secondSku);
    }

    @Test
    @DisplayName("清空后重新添加同一 SKU 不撞 uk_supplier_sku_active（partial index）")
    void reAddAfterClearDoesNotViolateUniqueIndex() {
        Long supplierId = supplier("e");
        Long skuId = newOnShelfSku("E1");

        replace(supplierId, List.of(item(skuId)));
        replace(supplierId, List.of());
        replace(supplierId, List.of(item(skuId)));

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM supplier_sku WHERE supplier_id = ? AND sku_id = ? AND deleted = FALSE",
                Integer.class, supplierId, skuId)).isEqualTo(1);
    }

    @Test
    @DisplayName("请求内 skuId 重复 → 40943")
    void duplicateSkuIdInRequestRejected() {
        Long supplierId = supplier("f");
        Long skuId = newOnShelfSku("F1");

        expectCode(() -> replace(supplierId, List.of(item(skuId), item(skuId))), 40943);
    }

    @Test
    @DisplayName("跨供应商串用 id → 40943")
    void crossSupplierIdRejected() {
        Long first = supplier("g1");
        Long second = supplier("g2");
        Long skuId = newOnShelfSku("G1");

        replace(first, List.of(item(skuId)));
        Long otherRowId = jdbc.queryForObject(
                "SELECT id FROM supplier_sku WHERE supplier_id = ? AND deleted = FALSE", Long.class, first);

        expectCode(() -> replace(second, List.of(item(otherRowId, 0, skuId))), 40943);
    }

    @Test
    @DisplayName("既有行的 skuId 不允许变更 → 40943（换货等价于删旧增新）")
    void skuIdChangeOnExistingRowRejected() {
        Long supplierId = supplier("h");
        Long firstSku = newOnShelfSku("H1");
        Long secondSku = newOnShelfSku("H2");
        replace(supplierId, List.of(item(firstSku)));
        Long rowId = jdbc.queryForObject(
                "SELECT id FROM supplier_sku WHERE supplier_id = ? AND deleted = FALSE", Long.class, supplierId);

        expectCode(() -> replace(supplierId, List.of(item(rowId, 0, secondSku))), 40943);
    }

    @Test
    @DisplayName("不存在的 SKU → 40942")
    void unknownSkuRejected() {
        Long supplierId = supplier("i");
        expectCode(() -> replace(supplierId, List.of(item(-1L))), 40942);
    }

    @Test
    @DisplayName("默认采购员必须存在（40040）")
    void unknownPurchaserRejected() {
        Long supplierId = supplier("j");
        Long skuId = newOnShelfSku("J1");
        SupplierSkuItemForm item = item(skuId);
        item.setPurchaserId(-1L);

        expectCode(() -> replace(supplierId, List.of(item)), 40040);

        item.setPurchaserId(anyEmployeeId());
        replace(supplierId, List.of(item));
        assertThat(jdbc.queryForObject(
                "SELECT purchaser_id FROM supplier_sku WHERE supplier_id = ? AND deleted = FALSE",
                Long.class, supplierId)).isNotNull();
    }

    @Test
    @DisplayName("停用供应商不可维护关联（40940）")
    void disabledSupplierRejected() {
        Long supplierId = supplier("k");
        Long skuId = newOnShelfSku("K1");
        replace(supplierId, List.of(item(skuId)));

        SupplierStatusForm status = new SupplierStatusForm();
        status.setSupplierId(supplierId);
        status.setVersion(0);
        status.setStatus("DISABLED");
        supplierService.updateStatus(status);

        expectCode(() -> replace(supplierId, List.of(item(skuId))), 40940);
    }

    @Test
    @DisplayName("带 id 的行版本不一致 → 40921")
    void staleRowVersionRejected() {
        Long supplierId = supplier("l");
        Long skuId = newOnShelfSku("L1");
        replace(supplierId, List.of(item(skuId)));
        Long rowId = jdbc.queryForObject(
                "SELECT id FROM supplier_sku WHERE supplier_id = ? AND deleted = FALSE", Long.class, supplierId);

        expectCode(() -> replace(supplierId, List.of(item(rowId, 7, skuId))), 40921);
    }

    @Test
    @DisplayName("金额与 JSONB 契约：4 位小数字符串、null 保持 null、规格快照可读")
    void decimalAndJsonbContract() throws Exception {
        Long supplierId = supplier("m");
        Long skuId = newOnShelfSku("M1");
        SupplierSkuItemForm item = item(skuId);
        item.setReferencePrice("7.5");
        replace(supplierId, List.of(item));

        SupplierSkuVO vo = skuService.listBySupplierId(supplierId).getFirst();
        JsonNode node = json.readTree(json.writeValueAsString(vo));
        assertThat(node.get("referencePrice").asText()).isEqualTo("7.5000");
        assertThat(node.get("specValuesSnapshot").get("规格").asText()).isEqualTo("M1");

        // 参考价可空：清空后必须回到 null，而不是 0.0000
        SupplierSkuItemForm cleared = item(vo.getId(), vo.getVersion(), skuId);
        cleared.setReferencePrice(null);
        replace(supplierId, List.of(cleared));
        assertThat(jdbc.queryForObject(
                "SELECT reference_price FROM supplier_sku WHERE id = ?", BigDecimal.class, vo.getId())).isNull();
        JsonNode after = json.readTree(json.writeValueAsString(
                skuService.listBySupplierId(supplierId).getFirst()));
        assertThat(after.get("referencePrice").isNull()).as("null 不得被写成 0.0000").isTrue();
    }

    @Test
    @DisplayName("只读反查分页按 supplierId / skuId / status 过滤，并补全采购员姓名")
    void queryFiltersAndEnrichesPurchaserName() {
        Long supplierId = supplier("n");
        Long skuId = newOnShelfSku("N1");
        SupplierSkuItemForm item = item(skuId);
        item.setPurchaserId(anyEmployeeId());
        replace(supplierId, List.of(item));

        SupplierSkuQueryForm form = new SupplierSkuQueryForm();
        form.setPageNum(1L);
        form.setPageSize(20L);
        form.setSupplierId(supplierId);
        form.setSkuId(skuId);
        List<SupplierSkuVO> list = skuService.query(form).getList();
        assertThat(list).hasSize(1);
        assertThat(list.getFirst().getPurchaserName()).isNotBlank();

        SupplierSkuQueryForm byStatus = new SupplierSkuQueryForm();
        byStatus.setPageNum(1L);
        byStatus.setPageSize(20L);
        byStatus.setSupplierId(supplierId);
        byStatus.setStatus("DISABLED");
        assertThat(skuService.query(byStatus).getList()).isEmpty();
    }

    @Test
    @DisplayName("W3 采购入口：无关联时 40442")
    void requireEnabledForPurchasingRejectsUnknownRelation() {
        Long supplierId = supplier("o1");
        Long skuId = newOnShelfSku("O1");

        expectCode(() -> skuService.requireEnabledForPurchasing(supplierId, skuId), 40442);
    }

    @Test
    @DisplayName("W3 采购入口：关联存在且 SPU/SKU 同时上架时可采购")
    void requireEnabledForPurchasingAcceptsOrderableRelation() {
        Long supplierId = supplier("o2");
        Long skuId = newOnShelfSku("O2");
        replace(supplierId, List.of(item(skuId)));

        assertThat(skuService.requireEnabledForPurchasing(supplierId, skuId).getSkuId()).isEqualTo(skuId);
    }

    /**
     * 刻意制造「关联仍在，但 SPU 已下架」的脏状态（W1 的写入校验正常情况下不会让它出现），
     * 用来证明供应商域自己的守卫独立成立。
     *
     * <p>这个用例必须独立成一个方法：SPU 的下架是通过 {@code JdbcTemplate} 直接改库完成的，
     * 而 MyBatis 的一级缓存只会在「MyBatis 自己的写语句」后被清空，
     * 因此如果在同一个 SqlSession 里先查过一次 {@code selectEnabledBySkuId}，第二次会命中旧缓存。
     * 每个测试方法各自一个事务 = 各自一个 SqlSession，天然避开这个陷阱。
     */
    @Test
    @DisplayName("W3 采购入口：SPU 已下架时 40942")
    void requireEnabledForPurchasingRejectsOffShelfSpu() {
        Long supplierId = supplier("o3");
        Long skuId = newOnShelfSku("O3");
        replace(supplierId, List.of(item(skuId)));

        jdbc.update("UPDATE product_spu SET status = 'OFF_SHELF' WHERE id = "
                + "(SELECT spu_id FROM product_sku WHERE id = ?)", skuId);

        expectCode(() -> skuService.requireEnabledForPurchasing(supplierId, skuId), 40942);
    }
}
