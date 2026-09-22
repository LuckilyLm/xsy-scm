package net.lab1024.sa.admin.module.scm.inventory;

import net.lab1024.sa.admin.module.scm.common.ScmW6PgITBase;
import net.lab1024.sa.admin.module.scm.inventory.domain.InventoryOutboundFact;
import net.lab1024.sa.admin.module.scm.inventory.domain.vo.InventoryStocktakeImportResultVO;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryStocktakeImportService;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryStocktakeService;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 盘点 Excel「导出快照模板 → 填实盘 → 导回草稿」的 PostgreSQL 集成测试（Wave 6 盘点效率）。
 *
 * <p>这条链路的两个关键性质在 mock 测试里都验不了，必须在真 PG + 真签名凭证 + 真 Excel 上跑：
 * <ol>
 *   <li><b>导入只建草稿、绝不碰库存</b> —— 成功导入后余额与盘点流水都不变，只有随后的
 *       {@code confirm} 才产生盘盈/盘亏流水。这是「导入≠调整」边界的直接证据；</li>
 *   <li><b>整批拒绝且不留草稿</b> —— 空白实盘 / 篡改凭证 / 混用来源行 / 快照漂移，任一命中都不产生
 *       草稿（校验全部发生在写库之前，所以共享测试事务里也不会残留半截行）。</li>
 * </ol>
 *
 * <p><b>漂移用例是本波次的裁决核心</b>（计划 §10.3）：账面 10 → 导出快照 → 出库 2（版本自增）→
 * 填实盘导回，即使「当前账面恰好等于要填的实盘」也必须因版本变化整批拒绝，不接受「先校验再保存」竞态。
 *
 * <p><b>为什么每个用例都填写全表实盘量</b>：模板是<b>整仓</b>余额快照，共享种子仓库还带着其它
 * (仓库, SKU) 的历史余额行，无法只针对本次造的 SKU 出一张「单行模板」。因此非目标行也必须填
 * 合法实盘（否则会在解析阶段就报 BLANK 而挡住它之后才做的凭证 / 来源核验），并在会走
 * {@code confirm} 的成功用例里把非目标行的实盘填成各自的账面快照（差异 0，确认时不产生流水、
 * 也不会因外键约束误伤别的行）。断言因此全部按 SKU 收窄，不用全仓计数。
 */
@DisplayName("盘点 Excel 导入（PG IT）")
class ScmStocktakeImportPgIT extends ScmW6PgITBase {

    @Autowired
    private InventoryStocktakeImportService importService;

    @Autowired
    private InventoryStocktakeService stocktakeService;

    // ------------------------------------------------------------------
    // 夹具：把一个 (仓库, SKU) 备货成指定账面量
    // ------------------------------------------------------------------

    private Object[] stocked(String suffix, String quantity) {
        Long skuId = newSkuOfType(suffix, "NON_STANDARD", "ON_SHELF");
        W6Fixture fixture = inboundFixture(suffix, skuId, quantity);
        confirmReceipt(fixture.receipt().getId(), quantity);
        return new Object[]{seedWarehouseId(), skuId};
    }

    private String skuCode(Long skuId) {
        return jdbc.queryForObject("SELECT sku_code FROM product_sku WHERE id = ?", String.class, skuId);
    }

    private interface SheetEditor {
        void edit(XSSFSheet sheet);
    }

    private MultipartFile editedFile(byte[] template, SheetEditor editor) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(template))) {
            XSSFSheet sheet = workbook.getSheetAt(0);
            editor.edit(sheet);
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                workbook.write(out);
                return new MockMultipartFile("file", "stocktake.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        out.toByteArray());
            }
        }
    }

    /**
     * 给每个数据行填实盘量：实盘 = {@code actualFor(该行SKU编码, 该行账面快照)}。
     * 「实盘 = 账面快照」即差异 0，用来把非目标行填成不影响确认的中性行。
     */
    private void fillActual(XSSFSheet sheet, BiFunction<String, String, String> actualFor) {
        for (int row = 1; row <= sheet.getLastRowNum(); row++) {
            var dataRow = sheet.getRow(row);
            if (dataRow == null) {
                continue;
            }
            var skuCell = dataRow.getCell(1);
            var bookCell = dataRow.getCell(5);
            String sku = skuCell == null ? null : skuCell.getStringCellValue();
            String book = bookCell == null ? "0" : bookCell.getStringCellValue();
            dataRow.createCell(6).setCellValue(actualFor.apply(sku, book));
        }
    }

    /** 全部行填同一合法实盘：只关心解析之后的凭证 / 来源 / 重复判定，不关心差异落地。 */
    private void fillUniform(XSSFSheet sheet, String actual) {
        fillActual(sheet, (sku, book) -> actual);
    }

    private static Set<String> codes(InventoryStocktakeImportResultVO result) {
        Set<String> codes = new HashSet<>();
        result.getErrors().forEach(error -> codes.add(error.getCode()));
        return codes;
    }

    private List<Map<String, Object>> stocktakeMovements(Long wh, Long sku) {
        return movementsOf(wh, sku).stream()
                .filter(m -> String.valueOf(m.get("movement_type")).startsWith("STOCKTAKE_"))
                .toList();
    }

    private String key() {
        return prefix + ":import:" + UUID.randomUUID();
    }

    // ------------------------------------------------------------------
    // 成功路径：导入只建草稿，确认才动库存
    // ------------------------------------------------------------------

    @Test
    @DisplayName("导入填好的模板 → 建草稿且完全不改库存；随后确认才写盘亏流水")
    void importCreatesDraftWithoutTouchingStockAndConfirmIsTheOnlyWriter() throws Exception {
        Object[] s = stocked("si1", "10.0000");
        Long wh = (Long) s[0];
        Long sku = (Long) s[1];
        String targetCode = skuCode(sku);

        var template = importService.buildTemplate(wh);
        // 目标行填实盘 8（差异 −2），其余行填各自账面快照（差异 0）。
        var file = editedFile(template, sheet ->
                fillActual(sheet, (code, book) -> targetCode.equals(code) ? "8.0000" : book));

        var result = importService.importFile(file, key());

        assertThat(result.getTotalErrors()).isZero();
        assertThat(result.getStocktakeId()).isNotNull();
        assertThat(result.isReplayed()).isFalse();

        // 导入只建草稿：目标 SKU 账面量没变，也没产生任何盘点流水。
        assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("10.0000");
        assertThat(stocktakeMovements(wh, sku)).isEmpty();

        // 确认才是库存的唯一写入者：差异 = 实盘 8 − 账面快照 10 = −2，施加到当前账面 10 → 8。
        stocktakeService.confirm(result.getStocktakeId());
        assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("8.0000");
        var movements = stocktakeMovements(wh, sku);
        assertThat(movements).hasSize(1);
        assertThat(movements.getFirst().get("movement_type")).isEqualTo("STOCKTAKE_LOSS");
    }

    @Test
    @DisplayName("同一 Idempotency-Key 重复导入 → 命中重放，返回同一张草稿而非第二张")
    void repeatedImportWithSameKeyReplaysSingleDraft() throws Exception {
        Object[] s = stocked("si2", "10.0000");
        Long wh = (Long) s[0];
        String targetCode = skuCode((Long) s[1]);

        var template = importService.buildTemplate(wh);
        var file = editedFile(template, sheet ->
                fillActual(sheet, (code, book) -> targetCode.equals(code) ? "8.0000" : book));
        var idempotencyKey = key();

        var first = importService.importFile(file, idempotencyKey);
        var second = importService.importFile(file, idempotencyKey);

        assertThat(first.getTotalErrors()).isZero();
        assertThat(first.isReplayed()).isFalse();
        assertThat(second.isReplayed()).isTrue();
        assertThat(second.getStocktakeId()).isEqualTo(first.getStocktakeId());
    }

    // ------------------------------------------------------------------
    // 整批拒绝：不产生任何草稿
    // ------------------------------------------------------------------

    @Test
    @DisplayName("实盘留空（空白≠0）→ BLANK_ACTUAL，整批拒绝且不建草稿")
    void blankActualRejectsWholeBatch() throws Exception {
        Object[] s = stocked("si3", "10.0000");
        Long wh = (Long) s[0];

        // 原样导回未填实盘的模板（第 6 列保持为空）。
        var template = importService.buildTemplate(wh);
        var file = editedFile(template, sheet -> {
        });

        var result = importService.importFile(file, key());

        assertThat(codes(result)).contains("BLANK_ACTUAL");
        assertThat(result.getStocktakeId()).isNull();
        assertThat(balanceRow(wh, (Long) s[1]).getQuantity()).isEqualByComparingTo("10.0000");
    }

    @Test
    @DisplayName("篡改快照凭证 → CREDENTIAL_INVALID，整批拒绝")
    void tamperedCredentialIsRejected() throws Exception {
        Object[] s = stocked("si4", "10.0000");
        Long wh = (Long) s[0];

        var template = importService.buildTemplate(wh);
        var file = editedFile(template, sheet -> {
            fillUniform(sheet, "8.0000");
            for (int row = 1; row <= sheet.getLastRowNum(); row++) {
                var dataRow = sheet.getRow(row);
                if (dataRow != null) {
                    dataRow.getCell(0).setCellValue(dataRow.getCell(0).getStringCellValue() + "x");
                }
            }
        });

        var result = importService.importFile(file, key());

        assertThat(codes(result)).contains("CREDENTIAL_INVALID");
        assertThat(result.getStocktakeId()).isNull();
    }

    @Test
    @DisplayName("把来源行的 SKU 换成别的编码 → 同时报 SOURCE_UNKNOWN 与 SOURCE_MISSING，整批拒绝")
    void replacingSourceRowIsRejected() throws Exception {
        Object[] s = stocked("si5", "10.0000");
        Long wh = (Long) s[0];

        var template = importService.buildTemplate(wh);
        var file = editedFile(template, sheet -> {
            fillUniform(sheet, "8.0000");
            sheet.getRow(1).getCell(1).setCellValue("REPLACED-9999");
        });

        var result = importService.importFile(file, key());

        // 被换上的编码不在快照来源集合内（SOURCE_UNKNOWN），原来源行随之缺失（SOURCE_MISSING）。
        assertThat(codes(result)).contains("SOURCE_UNKNOWN", "SOURCE_MISSING");
        assertThat(result.getStocktakeId()).isNull();
    }

    @Test
    @DisplayName("同一 SKU 在文件里出现两次 → DUPLICATE_SKU，整批拒绝")
    void duplicateSkuRowIsRejected() throws Exception {
        Object[] s = stocked("si6", "10.0000");
        Long wh = (Long) s[0];

        var template = importService.buildTemplate(wh);
        var file = editedFile(template, sheet -> {
            fillUniform(sheet, "8.0000");
            var first = sheet.getRow(1);
            var duplicate = sheet.createRow(sheet.getLastRowNum() + 1);
            duplicate.createCell(0).setCellValue(first.getCell(0).getStringCellValue());
            duplicate.createCell(1).setCellValue(first.getCell(1).getStringCellValue());
            duplicate.createCell(6).setCellValue("8.0000");
        });

        var result = importService.importFile(file, key());

        assertThat(codes(result)).contains("DUPLICATE_SKU");
        assertThat(result.getStocktakeId()).isNull();
    }

    // ------------------------------------------------------------------
    // 核心裁决：版本漂移必须整批拒绝（不接受「先校验再保存」竞态）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("导出后余额版本自增 → SNAPSHOT_STALE 整批拒绝，即便当前账面恰好等于实盘")
    void versionDriftRejectsWholeBatch() throws Exception {
        Object[] s = stocked("si7", "10.0000");
        Long wh = (Long) s[0];
        Long sku = (Long) s[1];

        // 导出快照：账面 10、版本 v。
        var template = importService.buildTemplate(wh);

        // 快照之后出库 2 → 账面 8、版本 v+1。填的实盘恰好等于「当前账面 8」，
        // 若实现按数量比较就会误放行；按版本判定才能挡住这个竞态。
        inventoryCommandService.postSalesOutbound(new InventoryOutboundFact(
                wh, sku, 0L, 930001L, new BigDecimal("2.0000"), null,
                OffsetDateTime.now(), "test:1"));
        assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("8.0000");

        var file = editedFile(template, sheet -> fillUniform(sheet, "8.0000"));
        var result = importService.importFile(file, key());

        assertThat(codes(result)).contains("SNAPSHOT_STALE");
        assertThat(result.getStocktakeId()).isNull();
        // 漂移拒绝后目标库存原样不动（仍是出库后的 8），也没有任何盘点流水。
        assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("8.0000");
        assertThat(stocktakeMovements(wh, sku)).isEmpty();
    }
}
