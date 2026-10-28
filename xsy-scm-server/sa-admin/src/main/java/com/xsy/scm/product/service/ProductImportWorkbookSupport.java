package com.xsy.scm.product.service;

import com.xsy.scm.product.constant.ProductEnumLabels;
import com.xsy.scm.product.domain.dto.ProductImportRow;
import com.xsy.scm.product.domain.vo.ProductImportResultVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.ss.util.NumberToTextConverter;
import org.apache.poi.xssf.usermodel.XSSFDataValidationHelper;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;

import static com.xsy.scm.product.service.ProductImportValueRules.addError;
import static com.xsy.scm.product.service.ProductImportValueRules.trim;

/**
 * Excel 模板生成和工作簿解析；产品领域校验与写入由导入服务负责。
 */
@Slf4j
@Component
public class ProductImportWorkbookSupport {
    /**
     * 模板下拉、解析归一化与导出中文标签都取自 {@link ProductEnumLabels}，避免多处定义不一致。
     */
    private static final Map<String, List<String>> ENUM_OPTIONS = ProductEnumLabels.OPTIONS;

    /**
     * 数据验证覆盖到第 2000 行：模板上限 20000 行，但下拉预置到这个深度足够日常使用且不撑大文件。
     */
    private static final int VALIDATION_LAST_ROW = 2000;

    // 分组标记只用于把本次导入的多行规格归入一个商品，不是保存的 SPU 编码。
    private static final List<String> CREATE_HEADERS = List.of("模板版本", "导入商品分组", "商品名称", "别名", "分类编码", "助记码", "品牌",
        "产地", "储存方式", "保质期天数", "标签编码", "商品状态", "条码", "商品规格名称", "销售单位", "商品类型", "市场价", "商品规格状态", "默认商品规格", "排序");
    private static final List<
        String> LEGACY_CREATE_HEADERS = List.of("模板版本", "商品编码", "商品名称", "别名", "分类编码", "助记码", "品牌", "产地", "储存方式",
            "保质期天数", "标签编码", "商品状态", "商品规格编码", "条码", "商品规格名称", "销售单位", "商品类型", "市场价", "商品规格状态", "默认商品规格", "排序");
    private static final List<String> UPDATE_HEADERS = List.of("模板版本", "商品ID", "商品版本", "商品规格ID", "商品规格版本", "商品编码",
        "商品名称", "别名", "分类编码", "助记码", "品牌", "产地", "储存方式", "保质期天数", "标签编码", "商品状态", "商品规格编码", "条码", "商品规格名称", "销售单位",
        "商品类型", "市场价", "商品规格状态", "默认商品规格", "排序");
    private static final List<BiConsumer<ProductImportRow, String>> CREATE_SETTERS = List.of(
        ProductImportRow::setTemplateVersion, ProductImportRow::setSpuCode, ProductImportRow::setSpuName,
        ProductImportRow::setAlias, ProductImportRow::setCategoryCode, ProductImportRow::setMnemonicCode,
        ProductImportRow::setBrandName, ProductImportRow::setOrigin, ProductImportRow::setStorageMethod,
        ProductImportRow::setShelfLifeDays, ProductImportRow::setTagCodes, ProductImportRow::setSpuStatus,
        ProductImportRow::setBarcode, ProductImportRow::setSpecName, ProductImportRow::setSaleUnit,
        ProductImportRow::setProductType, ProductImportRow::setMarketPrice, ProductImportRow::setSkuStatus,
        ProductImportRow::setDefaultFlag, ProductImportRow::setSortOrder);
    private static final List<BiConsumer<ProductImportRow, String>> LEGACY_CREATE_SETTERS = List.of(
        ProductImportRow::setTemplateVersion, ProductImportRow::setSpuCode, ProductImportRow::setSpuName,
        ProductImportRow::setAlias, ProductImportRow::setCategoryCode, ProductImportRow::setMnemonicCode,
        ProductImportRow::setBrandName, ProductImportRow::setOrigin, ProductImportRow::setStorageMethod,
        ProductImportRow::setShelfLifeDays, ProductImportRow::setTagCodes, ProductImportRow::setSpuStatus,
        ProductImportRow::setSkuCode, ProductImportRow::setBarcode, ProductImportRow::setSpecName,
        ProductImportRow::setSaleUnit, ProductImportRow::setProductType, ProductImportRow::setMarketPrice,
        ProductImportRow::setSkuStatus, ProductImportRow::setDefaultFlag, ProductImportRow::setSortOrder);
    private static final List<
        BiConsumer<ProductImportRow, String>> UPDATE_SETTERS = List.of(ProductImportRow::setTemplateVersion,
            ProductImportRow::setSpuId, ProductImportRow::setSpuVersion, ProductImportRow::setSkuId,
            ProductImportRow::setSkuVersion, ProductImportRow::setSpuCode, ProductImportRow::setSpuName,
            ProductImportRow::setAlias, ProductImportRow::setCategoryCode, ProductImportRow::setMnemonicCode,
            ProductImportRow::setBrandName, ProductImportRow::setOrigin, ProductImportRow::setStorageMethod,
            ProductImportRow::setShelfLifeDays, ProductImportRow::setTagCodes, ProductImportRow::setSpuStatus,
            ProductImportRow::setSkuCode, ProductImportRow::setBarcode, ProductImportRow::setSpecName,
            ProductImportRow::setSaleUnit, ProductImportRow::setProductType, ProductImportRow::setMarketPrice,
            ProductImportRow::setSkuStatus, ProductImportRow::setDefaultFlag, ProductImportRow::setSortOrder);

    public byte[] buildTemplate(ProductImportService.ImportMode mode) throws IOException {
        var headers = headers(mode);
        try (var workbook = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("商品导入");
            var header = sheet.createRow(0);
            var sample = sheet.createRow(1);
            for (int column = 0; column < headers.size(); column++) {
                header.createCell(column).setCellValue(headers.get(column));
                sample.createCell(column).setCellValue(sampleValue(mode, column));
                sheet.setColumnWidth(column, Math.min(28, Math.max(12, headers.get(column).length() * 3)) * 256);
            }
            applyEnumValidations(sheet, headers);
            // 冻结表头与示例行：几千行数据往下翻时列名还在。
            sheet.createFreezePane(0, 2);
            workbook.write(out);
            return out.toByteArray();
        }
    }

    /**
     * 给枚举列加下拉选项，填了列表外的值 Excel 直接拦下。允许空单元格，必填项由服务端校验。
     */
    private void applyEnumValidations(XSSFSheet sheet, List<String> headers) {
        var helper = new XSSFDataValidationHelper(sheet);
        for (int column = 0; column < headers.size(); column++) {
            var options = ENUM_OPTIONS.get(headers.get(column));
            if (options == null)
                continue;
            var constraint = helper.createExplicitListConstraint(options.toArray(String[]::new));
            var address = new CellRangeAddressList(1, VALIDATION_LAST_ROW, column, column);
            var validation = helper.createValidation(constraint, address);
            validation.setShowErrorBox(true);
            validation.createErrorBox("取值无效", "请从下拉列表选择：" + String.join(" / ", options));
            validation.setShowPromptBox(true);
            validation.createPromptBox(headers.get(column), String.join(" / ", options));
            validation.setEmptyCellAllowed(true);
            sheet.addValidationData(validation);
        }
    }

    /**
     * 页面已取消模式开关，只靠模板区分：更新模板第 2 列是「商品ID」定位键，新增模板是「导入商品分组」。 不做这一步判断，更新文件会按 CREATE 解析——表头校验整批报错，或被当成新增静默多建商品。
     */
    public ProductImportService.ImportMode detectMode(byte[] bytes) {
        try (var workbook = org.apache.poi.ss.usermodel.WorkbookFactory
            .create(new java.io.ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheetAt(0);
            var header = sheet.getRow(0);
            var second = header == null
                ? null
                : trim(new DataFormatter(Locale.ROOT).formatCellValue(header.getCell(1)));
            return "商品ID".equals(second)
                ? ProductImportService.ImportMode.UPDATE
                : ProductImportService.ImportMode.CREATE;
        } catch (Exception exception) {
            // 读不出来交给 readRows 统一报 FILE_INVALID，这里不吞成用户可见错误
            log.warn("商品导入文件无法预判模式，按新增处理", exception);
            return ProductImportService.ImportMode.CREATE;
        }
    }

    public List<ProductImportRow> readRows(byte[] bytes, ProductImportService.ImportMode mode,
        ProductImportResultVO result) {
        var rows = new ArrayList<ProductImportRow>();
        var formatter = new DataFormatter(Locale.ROOT);
        try (var workbook = org.apache.poi.ss.usermodel.WorkbookFactory
            .create(new java.io.ByteArrayInputStream(bytes))) {
            if (workbook.getNumberOfSheets() != 1) {
                addError(result, 0, null, "文件", "SHEET_COUNT", "请保留模板中的一个工作表");
                return rows;
            }
            var sheet = workbook.getSheetAt(0);
            var header = sheet.getRow(0);
            var secondHeader = header == null ? null : trim(formatter.formatCellValue(header.getCell(1)));
            boolean legacyCreate = mode == ProductImportService.ImportMode.CREATE
                && ("商品编码".equals(secondHeader) || "SPU编码".equals(secondHeader));
            var headers = legacyCreate ? LEGACY_CREATE_HEADERS : headers(mode);
            var setters = mode == ProductImportService.ImportMode.UPDATE
                ? UPDATE_SETTERS
                : legacyCreate ? LEGACY_CREATE_SETTERS : CREATE_SETTERS;
            for (int column = 0; column < headers.size(); column++) {
                String headerValue = header == null ? null : trim(formatter.formatCellValue(header.getCell(column)));
                if (!headerMatches(mode, column, headerValue, legacyCreate)) {
                    addError(result, 1, null, CellReference.convertNumToColString(column), "HEADER_INVALID",
                        "表头应为“" + headers.get(column) + "”，请使用"
                            + (mode == ProductImportService.ImportMode.CREATE ? "新增" : "更新") + "模板");
                }
            }
            if (result.getTotalErrors() > 0)
                return rows;
            for (var excelRow : sheet) {
                if (excelRow.getRowNum() == 0)
                    continue;
                var row = new ProductImportRow();
                row.setRowNumber(excelRow.getRowNum() + 1);
                boolean hasData = false;
                for (var cell : excelRow) {
                    var value = trim(formatter.formatCellValue(cell));
                    if (value == null)
                        continue;
                    hasData = true;
                    var column = cell.getColumnIndex();
                    var name = column < headers.size()
                        ? headers.get(column)
                        : CellReference.convertNumToColString(column);
                    if (cell.getCellType() == CellType.FORMULA || cell.getCellType() == CellType.ERROR) {
                        addError(result, row.getRowNumber(), null, name, "CELL_INVALID", "不能使用公式或错误单元格");
                    } else if (column >= headers.size()) {
                        addError(result, row.getRowNumber(), null, name, "COLUMN_UNEXPECTED", "模板之外的列不能填写数据");
                    } else {
                        if (cell.getCellType() == CellType.NUMERIC)
                            value = NumberToTextConverter.toText(cell.getNumericCellValue());
                        setters.get(column).accept(row, normalizeEnumValue(headers.get(column), value));
                    }
                }
                if (hasData)
                    rows.add(row);
                if (rows.size() > ProductImportService.MAX_ROWS) {
                    addError(result, row.getRowNumber(), null, "文件", "ROW_LIMIT",
                        "数据行不能超过 " + ProductImportService.MAX_ROWS + " 行");
                    break;
                }
            }
        } catch (Exception exception) {
            log.error("商品导入文件解析失败，整批按 FILE_INVALID 拒绝", exception);
            addError(result, 0, null, "文件", "FILE_INVALID", "Excel 文件无法读取，请使用最新模板");
        }
        result.setTotalRows(rows.size());
        return rows;
    }

    private List<String> headers(ProductImportService.ImportMode mode) {
        return mode == ProductImportService.ImportMode.CREATE ? CREATE_HEADERS : UPDATE_HEADERS;
    }

    /**
     * 兼容早前下载的技术表头，导出只给业务列名。
     */
    private boolean headerMatches(ProductImportService.ImportMode mode, int column, String value,
        boolean legacyCreate) {
        var headerList = legacyCreate ? LEGACY_CREATE_HEADERS : headers(mode);
        if (headerList.get(column).equals(value))
            return true;
        if (mode == ProductImportService.ImportMode.CREATE && !legacyCreate)
            return false;
        if (mode == ProductImportService.ImportMode.CREATE) {
            return switch (column) {
                case 1 -> "SPU编码".equals(value);
                case 11 -> "商品上下架".equals(value);
                case 12 -> "SKU编码".equals(value) || "规格编码".equals(value);
                case 14 -> "规格名称".equals(value);
                case 18 -> "SKU上下架".equals(value) || "规格状态".equals(value);
                case 19 -> "默认SKU".equals(value) || "默认规格".equals(value);
                default -> false;
            };
        }
        return switch (column) {
            case 1 -> "SPU ID".equals(value);
            case 2 -> "SPU版本".equals(value);
            case 3 -> "SKU ID".equals(value) || "规格ID".equals(value);
            case 4 -> "SKU版本".equals(value) || "规格版本".equals(value);
            case 5 -> "SPU编码".equals(value);
            case 15 -> "商品上下架".equals(value);
            case 16 -> "SKU编码".equals(value) || "规格编码".equals(value);
            case 18 -> "规格名称".equals(value);
            case 22 -> "SKU上下架".equals(value) || "规格状态".equals(value);
            case 23 -> "默认SKU".equals(value) || "默认规格".equals(value);
            default -> false;
        };
    }

    /**
     * 把中文标签转成枚举名；不在映射表里的值原样返回，交给服务端校验。
     */
    private static String normalizeEnumValue(String header, String value) {
        return ProductEnumLabels.normalize(header, value);
    }

    private String sampleValue(ProductImportService.ImportMode mode, int column) {
        if (mode == ProductImportService.ImportMode.UPDATE) {
            return switch (column) {
                case 0 -> ProductImportService.TEMPLATE_VERSION;
                case 1 -> "1001";
                case 2 -> "0";
                case 3 -> "2001";
                case 4 -> "0";
                case 5 -> "M0001";
                case 6 -> "示例蔬菜";
                case 8 -> "FRESH-FRUIT";
                case 11 -> "本地";
                case 12 -> "冷藏";
                case 15 -> "上架";
                case 16 -> "G000001";
                case 18 -> "500g/份";
                case 19 -> "份";
                case 20 -> "标品";
                case 21 -> "9.9000";
                case 22 -> "上架";
                case 23 -> "是";
                case 24 -> "0";
                default -> "";
            };
        }
        return switch (column) {
            case 0 -> ProductImportService.TEMPLATE_VERSION;
            case 1 -> "A01";
            case 2 -> "示例蔬菜";
            case 4 -> "FRESH-FRUIT";
            case 7 -> "本地";
            case 8 -> "冷藏";
            case 11 -> "上架";
            case 13 -> "500g/份";
            case 14 -> "份";
            case 15 -> "标品";
            case 16 -> "9.9000";
            case 17 -> "上架";
            case 18 -> "是";
            case 19 -> "0";
            default -> "";
        };
    }

}
