package net.lab1024.sa.admin.module.scm.product;

import net.lab1024.sa.admin.AdminApplication;
import net.lab1024.sa.admin.module.scm.product.domain.form.ProductSkuForm;
import net.lab1024.sa.admin.module.scm.product.domain.form.ProductSpuAddForm;
import net.lab1024.sa.admin.module.scm.product.domain.form.ProductTagAddForm;
import net.lab1024.sa.admin.module.scm.product.service.ProductImportService;
import net.lab1024.sa.admin.module.scm.product.service.ProductImportService.ImportMode;
import net.lab1024.sa.admin.module.scm.product.service.ProductSpuService;
import net.lab1024.sa.admin.module.scm.product.service.ProductTagService;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.admin.test.PgITPaths;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * 商品 Excel 更新导入（修复计划 §7.2）的真实写库验证。
 * 单元测试只能证明合并结果，这里要证明的是「只改一列不会清掉其他列、未列出的 SKU 不会被删」
 * 这类只有落到 {@code ProductSpuService.update} 与变更集上才会暴露的破坏性语义，以及整批回滚。
 * <p>本类<b>刻意不加</b> {@code @Transactional}：更新写入必须真的提交，之后才能在新的只读事务里
 * 读回事实；写阶段冲突的整批回滚也只有脱离测试事务才观察得到。用例结束时按前缀清理自建数据。
 */
@SpringBootTest(classes = AdminApplication.class, properties = {
        "project.log-directory=" + PgITPaths.DEFAULT_LOG_DIR,
        "file.storage.local.upload-path=" + PgITPaths.DEFAULT_UPLOAD_PATH,
        "file.storage.local.url-prefix=http://127.0.0.1:18082",
        "logging.level.root=WARN"})
class ProductImportUpdatePgIT {
    private static final List<String> HEADERS = List.of("模板版本", "SPU ID", "SPU版本", "SKU ID", "SKU版本",
            "SPU编码", "商品名称", "别名", "分类编码", "助记码", "品牌", "产地", "储存方式", "保质期天数", "标签编码",
            "商品上下架", "SKU编码", "条码", "规格名称", "销售单位", "商品类型", "市场价", "SKU上下架", "默认SKU", "排序");
    private static final int SPU_ID = 1;
    private static final int SPU_VERSION = 2;
    private static final int SKU_ID = 3;
    private static final int SKU_VERSION = 4;
    private static final int ALIAS = 7;
    private static final int TAG_CODES = 14;
    private static final int SKU_CODE = 16;
    private static final int BARCODE = 17;
    private static final int MARKET_PRICE = 21;

    @Autowired
    ProductImportService imports;
    @Autowired
    ProductSpuService service;
    @Autowired
    ProductTagService tags;
    @Autowired
    JdbcTemplate jdbc;

    private String prefix;
    private int productSeq;
    private String lastProductName;
    private final List<Long> createdSpuIds = new ArrayList<>();
    private final List<Long> createdTagIds = new ArrayList<>();

    @BeforeEach
    void operator() {
        prefix = "IU-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase(Locale.ROOT);
        var employee = new RequestEmployee();
        employee.setEmployeeId(1L);
        employee.setActualName("Import Update IT");
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        SmartRequestUtil.setRequestUser(employee);
    }

    @AfterEach
    void cleanUp() {
        SmartRequestUtil.remove();
        for (var spuId : createdSpuIds) {
            jdbc.update("DELETE FROM product_tag_relation WHERE spu_id=?", spuId);
            jdbc.update("DELETE FROM product_image WHERE spu_id=?", spuId);
            jdbc.update("DELETE FROM product_sku WHERE spu_id=?", spuId);
            jdbc.update("DELETE FROM product_spu WHERE id=?", spuId);
        }
        for (var tagId : createdTagIds) jdbc.update("DELETE FROM product_tag WHERE id=?", tagId);
    }

    @Test
    void onlyFilledColumnsChangeAndUnlistedSkuSurvives() throws Exception {
        var product = product();
        var keepMe = skuIdOf(product, 1);
        var result = imports.importFile(updateRows(row(product, keepMe, cell(MARKET_PRICE, "3.4500"))), ImportMode.UPDATE);
        assertThat(result.getTotalErrors()).isZero();
        assertThat(result.getUpdatedProducts()).isEqualTo(1);
        assertThat(result.getMode()).isEqualTo("UPDATE");

        var sku = jdbc.queryForMap("SELECT market_price, sale_unit, product_type, status, is_default, spec_values::text AS specs,"
                + " version FROM product_sku WHERE id=?", keepMe);
        assertThat((BigDecimal) sku.get("market_price")).isEqualByComparingTo("3.4500");
        // 文件里这些列全是空白：单位、类型、上下架、默认标记与规格快照都必须还是原值（本行是非默认 SKU）
        assertThat(sku.get("sale_unit")).isEqualTo("份");
        assertThat(sku.get("product_type")).isEqualTo("STANDARD");
        assertThat(sku.get("status")).isEqualTo("ON_SHELF");
        assertThat(sku.get("is_default")).isEqualTo(false);
        assertThat(String.valueOf(sku.get("specs"))).contains("1kg/箱");
        assertThat((Integer) sku.get("version")).isGreaterThan(0);

        var spu = jdbc.queryForMap("SELECT name, alias, description, storage_method, master_status, version FROM product_spu WHERE id=?", product);
        assertThat(spu.get("name")).isEqualTo(lastProductName);
        assertThat(spu.get("alias")).isEqualTo("原别名");
        assertThat(spu.get("description")).isEqualTo("原详情描述，不能被只改市场价的导入清空");
        assertThat(spu.get("storage_method")).isEqualTo("CHILLED");
        assertThat(spu.get("master_status")).isEqualTo("ENABLED");
        assertThat((Integer) spu.get("version")).isEqualTo(1);

        // 未出现在文件里的第二个 SKU 不能被删掉，默认标记也必须还留在它身上；标签关系同理
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product_sku WHERE spu_id=? AND deleted=FALSE", Integer.class, product)).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT id FROM product_sku WHERE spu_id=? AND deleted=FALSE AND is_default=TRUE", Long.class, product))
                .containsExactly(skuIdOf(product, 0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product_tag_relation WHERE spu_id=? AND deleted=FALSE", Integer.class, product)).isEqualTo(1);
    }

    @Test
    void staleVersionRejectsTheWholeBatchBeforeAnyWrite() throws Exception {
        var product = product();
        var target = skuIdOf(product, 0);
        var result = imports.importFile(updateRows(row(product, target,
                cell(MARKET_PRICE, "8.8800"), cell(SPU_VERSION, "99"))), ImportMode.UPDATE);
        assertThat(result.getErrors()).anySatisfy(error -> {
            assertThat(error.getCode()).isEqualTo("VERSION_CONFLICT");
            assertThat(error.getColumn()).isEqualTo("SPU版本");
        });
        assertThat(result.getUpdatedProducts()).isZero();
        assertThat(price(target)).isEqualByComparingTo("1.2000");
        assertThat(spuVersion(product)).isZero();
    }

    @Test
    void writePhaseConflictRollsBackTheEarlierProductToo() throws Exception {
        var first = product();
        var second = product();
        var firstPriceTarget = skuIdOf(first, 0);
        var secondPriceTarget = skuIdOf(second, 0);
        // 第二个商品的 SKU 改成第一个商品既有条码：唯一索引在写阶段才炸，整批必须一起回滚
        var clash = jdbc.queryForObject("SELECT barcode FROM product_sku WHERE id=? AND deleted=FALSE", String.class, firstPriceTarget);
        assertThat(clash).isNotBlank();
        var result = imports.importFile(updateRows(
                row(first, firstPriceTarget, cell(MARKET_PRICE, "5.0000")),
                row(second, secondPriceTarget, cell(MARKET_PRICE, "6.0000"), cell(BARCODE, clash))), ImportMode.UPDATE);
        assertThat(result.getErrors()).singleElement().satisfies(error -> {
            assertThat(error.getCode()).isEqualTo("40023");
            assertThat(error.getMessage()).contains("整批已回滚");
        });
        assertThat(result.getUpdatedProducts()).isZero();
        assertThat(price(firstPriceTarget)).isEqualByComparingTo("1.2000");
        assertThat(price(secondPriceTarget)).isEqualByComparingTo("1.2000");
        assertThat(spuVersion(first)).isZero();
        assertThat(spuVersion(second)).isZero();
    }

    @Test
    void clearMarkerRemovesMarkedValuesAndKeepsEverythingElse() throws Exception {
        var product = product();
        var target = skuIdOf(product, 0);
        var result = imports.importFile(updateRows(row(product, target,
                cell(ALIAS, ProductImportService.CLEAR_TOKEN), cell(TAG_CODES, ProductImportService.CLEAR_TOKEN),
                cell(BARCODE, ProductImportService.CLEAR_TOKEN))), ImportMode.UPDATE);
        assertThat(result.getTotalErrors()).isZero();
        assertThat(jdbc.queryForObject("SELECT alias FROM product_spu WHERE id=?", String.class, product)).isNull();
        assertThat(jdbc.queryForObject("SELECT barcode FROM product_sku WHERE id=?", String.class, target)).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product_tag_relation WHERE spu_id=? AND deleted=FALSE",
                Integer.class, product)).isZero();
        // 未标记的列一个字都没动：描述与市场价仍是原值
        assertThat(jdbc.queryForObject("SELECT description FROM product_spu WHERE id=?", String.class, product))
                .isEqualTo("原详情描述，不能被只改市场价的导入清空");
        assertThat(price(target)).isEqualByComparingTo("1.2000");
    }

    // ------------------------------------------------------------------
    // fixtures
    // ------------------------------------------------------------------

    /** 两 SKU 商品：首个 SKU 为默认且带条码，另带别名、描述与标签，作为「不能被导入抹掉」的原值。 */
    private Long product() {
        productSeq++;
        lastProductName = code("A") + " 商品";
        var form = new ProductSpuAddForm();
        form.setSpuCode(code("SPU"));
        form.setName(lastProductName);
        form.setAlias("原别名");
        form.setDescription("原详情描述，不能被只改市场价的导入清空");
        form.setStorageMethod("CHILLED");
        form.setCategoryId(jdbc.queryForObject("SELECT id FROM product_category WHERE category_code='FRESH-FRUIT' AND deleted=FALSE", Long.class));
        form.setStatus("OFF_SHELF");
        var tagId = tag();
        form.setTagIds(List.of(tagId));
        var primary = sku(true, "1", "500g/份");
        var extra = sku(false, "2", "1kg/箱");
        form.setSkuList(new ArrayList<>(List.of(primary, extra)));
        var id = service.add(form);
        createdSpuIds.add(id);
        return id;
    }

    private ProductSkuForm sku(boolean defaultFlag, String tag, String specName) {
        var sku = new ProductSkuForm();
        sku.setSkuCode(code("SKU" + tag));
        sku.setBarcode(code("BAR" + tag));
        sku.setSpecName(specName);
        sku.setSpecValues(Map.of("规格", specName));
        sku.setSaleUnit("份");
        sku.setProductType("STANDARD");
        sku.setMarketPrice(new BigDecimal("1.2000"));
        sku.setStatus("ON_SHELF");
        sku.setDefaultFlag(defaultFlag);
        sku.setSortOrder(defaultFlag ? 0 : 1);
        return sku;
    }

    private Long tag() {
        var form = new ProductTagAddForm();
        form.setTagCode(code("TAG"));
        form.setName(form.getTagCode());
        form.setStatus("ENABLED");
        var id = tags.add(form);
        createdTagIds.add(id);
        return id;
    }

    private String code(String suffix) {
        return prefix + productSeq + "-" + suffix;
    }

    /** 商品 SKU 按 id 升序稳定定位，避免依赖插入顺序的不确定返回。 */
    private Long skuIdOf(Long spuId, int index) {
        return jdbc.queryForList("SELECT id FROM product_sku WHERE spu_id=? AND deleted=FALSE ORDER BY id", Long.class, spuId).get(index);
    }

    private Integer skuVersion(Long skuId) {
        return jdbc.queryForObject("SELECT version FROM product_sku WHERE id=?", Integer.class, skuId);
    }

    private int spuVersion(Long spuId) {
        return jdbc.queryForObject("SELECT version FROM product_spu WHERE id=?", Integer.class, spuId);
    }

    private BigDecimal price(Long skuId) {
        return jdbc.queryForObject("SELECT market_price FROM product_sku WHERE id=?", BigDecimal.class, skuId);
    }

    private String[] row(Long spuId, Long skuId, String[]... overrides) {
        var cells = new String[HEADERS.size()];
        Arrays.fill(cells, "");
        cells[0] = ProductImportService.TEMPLATE_VERSION;
        cells[SPU_ID] = String.valueOf(spuId);
        cells[SPU_VERSION] = String.valueOf(spuVersion(spuId));
        cells[SKU_ID] = String.valueOf(skuId);
        cells[SKU_VERSION] = String.valueOf(skuVersion(skuId));
        cells[SKU_CODE] = jdbc.queryForObject("SELECT sku_code FROM product_sku WHERE id=?", String.class, skuId);
        for (var override : overrides) cells[Integer.parseInt(override[0])] = override[1];
        return cells;
    }

    private String[] cell(int column, String value) {
        return new String[]{String.valueOf(column), value};
    }

    private MockMultipartFile updateRows(String[]... rows) throws Exception {
        try (var book = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("商品更新导入");
            var header = sheet.createRow(0);
            for (int column = 0; column < HEADERS.size(); column++) header.createCell(column).setCellValue(HEADERS.get(column));
            int number = 1;
            for (var row : rows) {
                var excelRow = sheet.createRow(number++);
                for (int column = 0; column < row.length; column++) excelRow.createCell(column).setCellValue(row[column]);
            }
            book.write(out);
            return new MockMultipartFile("file", "update.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }
}
