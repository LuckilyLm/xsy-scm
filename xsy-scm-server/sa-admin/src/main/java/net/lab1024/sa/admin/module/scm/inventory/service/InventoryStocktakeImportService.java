package net.lab1024.sa.admin.module.scm.inventory.service;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.constant.ScmOperator;
import net.lab1024.sa.admin.module.scm.inventory.dao.InventoryBalanceDao;
import net.lab1024.sa.admin.module.scm.inventory.domain.vo.InventoryBalanceVO;
import net.lab1024.sa.admin.module.scm.inventory.domain.vo.InventoryStocktakeImportErrorVO;
import net.lab1024.sa.admin.module.scm.inventory.domain.vo.InventoryStocktakeImportResultVO;
import net.lab1024.sa.admin.module.scm.inventory.support.StocktakeSnapshotDriftException;
import net.lab1024.sa.admin.module.scm.inventory.support.StocktakeSnapshotSigner;
import net.lab1024.sa.admin.module.scm.warehouse.service.WarehouseService;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.ss.util.NumberToTextConverter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 盘点效率：按仓库导出带签名快照的 Excel 模板，并把用户填好实盘量的 Excel 导回为<b>草稿</b>盘点单。
 *
 * <p><b>本类只负责「解析 + 全量校验 + 凭证核验」，不直接碰库存</b>：真正的落库交给
 * {@link InventoryStocktakeImportTxService}（持锁核验 + 复用既有 {@code create}）。导入绝不写
 * {@code inventory_balance.quantity} 或 {@code inventory_movement} —— 只有后续 {@code confirm} 才改库存。
 *
 * <p><b>整批语义</b>：任一空行 / 空白实盘量 / 来源集合被增删替换 / 凭证被篡改或过期 / 快照漂移，
 * 全部导致整批拒绝且不产生草稿。这与商品、订单导入同一取向：先全部校验，0 错误才写。
 *
 * <p><b>单元格里的账面量 / 单位 / 版本一律不信任</b>：它们只是给人看的快照，权威值来自签名凭证，
 * 并在导入时与持锁读取的当前余额逐项复核（消除「先校验、再保存」竞态）。
 */
@Service
@RequiredArgsConstructor
public class InventoryStocktakeImportService {

    public static final String TEMPLATE_VERSION = "1.0";
    private static final List<String> HEADERS = List.of(
            "快照凭证", "SKU编码", "商品名称", "规格", "记账单位", "账面数量快照", "实盘数量", "备注");
    private static final int COL_CREDENTIAL = 0;
    private static final int COL_SKU_CODE = 1;
    private static final int COL_ACTUAL = 6;
    private static final int COL_REMARK = 7;
    private static final int MAX_ROWS = 5000;
    private static final int MAX_ERRORS = 1000;
    /** 实盘量：非负、整数位 ≤14、小数 ≤4；空白由 required 单独挡（空白不等于 0）。 */
    private static final String ACTUAL_PATTERN = "[0-9]{1,14}(\\.[0-9]{1,4})?";

    private final InventoryBalanceDao balanceDao;
    private final WarehouseService warehouseService;
    private final StocktakeSnapshotSigner signer;
    private final InventoryStocktakeImportTxService txService;

    @Value("${scm.inventory.stocktake.snapshot.ttl-minutes:240}")
    private long ttlMinutes;

    /**
     * 生成某仓库的盘点导入模板：一行表头 + 每余额一行，每行携带同一份签名快照凭证。
     *
     * @return xlsx 字节；仓库不存在时 {@code warehouseService.require} 直接抛业务异常
     */
    public byte[] buildTemplate(Long warehouseId) throws IOException {
        warehouseService.require(warehouseId);
        String operator = ScmOperator.current();
        List<InventoryBalanceVO> balances = balanceDao.listActiveByWarehouse(warehouseId);

        List<StocktakeSnapshotSigner.Entry> entries = new ArrayList<>(balances.size());
        for (InventoryBalanceVO balance : balances) {
            entries.add(new StocktakeSnapshotSigner.Entry(balance.getSkuCode(), balance.getSkuId(),
                    balance.getId(), balance.getUnit(), balance.getVersion(), balance.getQuantity()));
        }
        long now = OffsetDateTime.now().toEpochSecond();
        String credential = signer.sign(new StocktakeSnapshotSigner.Payload(
                TEMPLATE_VERSION, warehouseId, operator, now, now + ttlMinutes * 60, entries));

        try (var workbook = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("盘点导入");
            var header = sheet.createRow(0);
            for (int column = 0; column < HEADERS.size(); column++) {
                header.createCell(column).setCellValue(HEADERS.get(column));
            }
            int rowIndex = 1;
            for (InventoryBalanceVO balance : balances) {
                var row = sheet.createRow(rowIndex++);
                row.createCell(COL_CREDENTIAL).setCellValue(credential);
                row.createCell(COL_SKU_CODE).setCellValue(nullToEmpty(balance.getSkuCode()));
                row.createCell(2).setCellValue(nullToEmpty(balance.getProductName()));
                row.createCell(3).setCellValue(nullToEmpty(balance.getSkuName()));
                row.createCell(4).setCellValue(nullToEmpty(balance.getUnit()));
                row.createCell(5).setCellValue(balance.getQuantity() == null ? "" : balance.getQuantity().toPlainString());
                // 实盘数量 / 备注 留空，由用户填写
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    /**
     * 导入用户填好实盘量的 Excel → 新建草稿盘点单。
     *
     * @param idempotencyKey 客户端为「同一次上传」稳定生成的键；响应丢失后原样重发即命中重放，不建第二张草稿
     */
    public InventoryStocktakeImportResultVO importFile(MultipartFile file, String idempotencyKey) throws Exception {
        var result = new InventoryStocktakeImportResultVO();
        var bytes = file.getBytes();
        var sheet = parseRows(bytes, result);
        if (result.getTotalErrors() > 0) {
            return result;
        }

        // 凭证核验（签名 / 有效期）：失败即整批拒绝，不进入任何写路径。
        StocktakeSnapshotSigner.Payload payload;
        try {
            payload = signer.verify(sheet.credential, OffsetDateTime.now().toEpochSecond());
        } catch (StocktakeSnapshotSigner.SnapshotCredentialException exception) {
            addError(result, 0, null, "快照凭证", "CREDENTIAL_INVALID",
                    exception.getMessage() + "；请重新导出模板");
            return result;
        }
        if (!TEMPLATE_VERSION.equals(payload.templateVersion())) {
            addError(result, 0, null, "快照凭证", "CREDENTIAL_INVALID", "模板版本不受支持，请重新下载模板");
            return result;
        }
        if (!ScmOperator.current().equals(payload.operator())) {
            addError(result, 0, null, "快照凭证", "OPERATOR_MISMATCH",
                    "该模板由他人导出，请用本人重新导出的模板导入");
            return result;
        }

        // 来源集合必须与凭证完全一致：不能增删 / 替换行。
        Map<String, StocktakeSnapshotSigner.Entry> authoritative = new LinkedHashMap<>();
        for (var entry : payload.entries()) {
            authoritative.put(entry.skuCode(), entry);
        }
        var sheetCodes = new HashSet<String>();
        for (var filled : sheet.rows) {
            if (!authoritative.containsKey(filled.skuCode)) {
                addError(result, filled.rowNumber, filled.skuCode, "SKU编码", "SOURCE_UNKNOWN",
                        "该行不在本次快照来源集合内，请重新导出模板，不要手工增删行");
            }
            sheetCodes.add(filled.skuCode);
        }
        for (var code : authoritative.keySet()) {
            if (!sheetCodes.contains(code)) {
                addError(result, 0, code, "SKU编码", "SOURCE_MISSING",
                        "快照来源行 " + code + " 在文件中缺失，请重新导出并填写完整");
            }
        }
        if (result.getTotalErrors() > 0) {
            return result;
        }

        var lines = new ArrayList<InventoryStocktakeService.SnapshotLine>();
        for (var filled : sheet.rows) {
            var entry = authoritative.get(filled.skuCode);
            lines.add(new InventoryStocktakeService.SnapshotLine(entry.skuCode(), entry.skuId(),
                    entry.balanceId(), entry.unit(), entry.version(), entry.bookQuantity(),
                    filled.actualQuantity, filled.remark));
        }

        try {
            var commit = txService.commit(payload.warehouseId(), lines, idempotencyKey,
                    fingerprint(payload, lines));
            result.setStocktakeId(commit.stocktakeId());
            result.setReplayed(commit.replayed());
            result.setImportedItems(lines.size());
            return result;
        } catch (StocktakeSnapshotDriftException exception) {
            // 整事务（含幂等认领）已回滚：不落任何草稿，要求重导。
            addError(result, 0, exception.getSkuCode(), "账面数量快照", "SNAPSHOT_STALE",
                    "余额自导出后已变动（SKU " + exception.getSkuCode() + "），整批未导入，请重新导出并核对实盘");
            return result;
        }
    }

    // ------------------------------------------------------------------
    // 解析与行级校验
    // ------------------------------------------------------------------

    private record FilledRow(int rowNumber, String skuCode, BigDecimal actualQuantity, String remark) {
    }

    private record Sheet(String credential, List<FilledRow> rows) {
    }

    private Sheet parseRows(byte[] bytes, InventoryStocktakeImportResultVO result) {
        var rows = new ArrayList<FilledRow>();
        String credential = null;
        var formatter = new DataFormatter(java.util.Locale.ROOT);
        var seenSkuCodes = new HashSet<String>();
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            if (workbook.getNumberOfSheets() != 1) {
                addError(result, 0, null, "文件", "SHEET_COUNT", "请保留模板中的一个工作表，避免遗漏来源行");
                return new Sheet(null, rows);
            }
            var sheet = workbook.getSheetAt(0);
            var header = sheet.getRow(0);
            for (int column = 0; column < HEADERS.size(); column++) {
                if (header == null || !HEADERS.get(column).equals(trim(formatter.formatCellValue(header.getCell(column))))) {
                    addError(result, 1, null, CellReference.convertNumToColString(column), "HEADER_INVALID",
                            "表头应为“" + HEADERS.get(column) + "”，请使用最新模板");
                }
            }
            if (result.getTotalErrors() > 0) {
                return new Sheet(null, rows);
            }

            for (var excelRow : sheet) {
                if (excelRow.getRowNum() == 0) {
                    continue;
                }
                int rowNumber = excelRow.getRowNum() + 1;
                Map<Integer, String> cells = new LinkedHashMap<>();
                boolean hasData = false;
                for (var cell : excelRow) {
                    if (cell.getCellType() == CellType.FORMULA || cell.getCellType() == CellType.ERROR) {
                        addError(result, rowNumber, null, "文件", "CELL_INVALID", "不能使用公式或错误单元格，请填写实际值");
                        continue;
                    }
                    var value = trim(cell.getCellType() == CellType.NUMERIC
                            ? NumberToTextConverter.toText(cell.getNumericCellValue())
                            : formatter.formatCellValue(cell));
                    if (value == null) {
                        continue;
                    }
                    hasData = true;
                    if (cell.getColumnIndex() >= HEADERS.size()) {
                        addError(result, rowNumber, null, CellReference.convertNumToColString(cell.getColumnIndex()),
                                "COLUMN_UNEXPECTED", "模板之外的列不能填写数据");
                        continue;
                    }
                    cells.put(cell.getColumnIndex(), value);
                }
                if (!hasData) {
                    continue;
                }
                if (rows.size() >= MAX_ROWS) {
                    addError(result, rowNumber, null, "文件", "ROW_LIMIT", "数据行不能超过 " + MAX_ROWS + " 行");
                    break;
                }

                // 凭证：每行携带同一份；首行确立基准，后续不一致即视为被篡改 / 混用两份导出。
                var rowCredential = cells.get(COL_CREDENTIAL);
                if (rowCredential == null) {
                    addError(result, rowNumber, null, "快照凭证", "CREDENTIAL_MISSING", "该行缺少快照凭证，请勿删除凭证列");
                } else if (credential == null) {
                    credential = rowCredential;
                } else if (!credential.equals(rowCredential)) {
                    addError(result, rowNumber, null, "快照凭证", "CREDENTIAL_MISMATCH", "所有行的快照凭证必须一致，请勿混用多次导出");
                }

                var skuCode = cells.get(COL_SKU_CODE);
                if (skuCode == null) {
                    addError(result, rowNumber, null, "SKU编码", "REQUIRED", "SKU编码不能为空");
                    continue;
                }
                if (!seenSkuCodes.add(skuCode)) {
                    addError(result, rowNumber, skuCode, "SKU编码", "DUPLICATE_SKU", "同一 SKU 在盘点单中只能出现一次");
                    continue;
                }

                var actualText = cells.get(COL_ACTUAL);
                if (actualText == null) {
                    addError(result, rowNumber, skuCode, "实盘数量", "BLANK_ACTUAL", "实盘数量不能为空（空白不等于 0，请显式填写）");
                    continue;
                }
                if (!actualText.matches(ACTUAL_PATTERN)) {
                    addError(result, rowNumber, skuCode, "实盘数量", "DECIMAL_INVALID", "实盘数量必须是非负、四位以内小数的数值");
                    continue;
                }
                var remark = cells.get(COL_REMARK);
                if (remark != null && remark.length() > 500) {
                    addError(result, rowNumber, skuCode, "备注", "TOO_LONG", "备注不能超过 500 个字符");
                    continue;
                }
                rows.add(new FilledRow(rowNumber, skuCode, new BigDecimal(actualText), remark));
            }
        } catch (Exception exception) {
            addError(result, 0, null, "文件", "FILE_INVALID", "Excel 文件无法读取，请使用最新模板");
        }
        if (rows.isEmpty()) {
            addError(result, 0, null, "文件", "FILE_EMPTY", "导入文件没有数据行");
        }
        result.setTotalRows(rows.size());
        return new Sheet(credential, rows);
    }

    /** 参与幂等哈希的指纹：凭证 + 每行（skuCode / 实盘量 / 备注）的稳定序列，内容变即视为不同请求。 */
    private static Object fingerprint(StocktakeSnapshotSigner.Payload payload,
                                      List<InventoryStocktakeService.SnapshotLine> lines) {
        var actual = new ArrayList<Map<String, Object>>();
        for (var line : lines) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("skuCode", line.skuCode());
            row.put("actual", line.actualQuantity().toPlainString());
            row.put("remark", line.remark());
            actual.add(row);
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("warehouseId", payload.warehouseId());
        request.put("entries", payload.entries());
        request.put("actual", actual);
        return request;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        var trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static void addError(InventoryStocktakeImportResultVO result, int row, String skuCode,
                                 String column, String code, String message) {
        result.setTotalErrors(result.getTotalErrors() + 1);
        if (result.getErrors().size() < MAX_ERRORS) {
            result.getErrors().add(new InventoryStocktakeImportErrorVO(row, skuCode, column, code, message));
        }
    }
}
