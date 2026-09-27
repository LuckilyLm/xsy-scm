package com.xsy.scm.product.service;

import com.xsy.scm.common.constant.ScmProductTypeEnum;
import com.xsy.scm.common.constant.ScmShelfStatusEnum;
import com.xsy.scm.product.constant.ScmStorageMethodEnum;
import com.xsy.scm.product.domain.dto.ProductImportRow;
import com.xsy.scm.product.domain.vo.ProductImportErrorVO;
import com.xsy.scm.product.domain.vo.ProductImportResultVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.ss.util.NumberToTextConverter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

/** Excel 模板生成和工作簿解析；产品领域校验与写入由导入服务负责。 */
@Slf4j
@Component
public class ProductImportWorkbookSupport {
    private static final List<
            String> CREATE_HEADERS = List.of("模板版本", "SPU编码", "商品名称", "别名", "分类编码", "助记码", "品牌", "产地", "储存方式", "保质期天数",
                    "标签编码", "商品上下架", "SKU编码", "条码", "规格名称", "销售单位", "商品类型", "市场价", "SKU上下架", "默认SKU", "排序");
    private static final List<
            String> UPDATE_HEADERS = List.of("模板版本", "SPU ID", "SPU版本", "SKU ID", "SKU版本", "SPU编码", "商品名称", "别名",
                    "分类编码", "助记码", "品牌", "产地", "储存方式", "保质期天数", "标签编码", "商品上下架", "SKU编码", "条码", "规格名称", "销售单位", "商品类型",
                    "市场价", "SKU上下架", "默认SKU", "排序");
    private static final List<
            BiConsumer<
                    ProductImportRow,
                    String>> CREATE_SETTERS = List.of(ProductImportRow::setTemplateVersion,
                            ProductImportRow::setSpuCode, ProductImportRow::setSpuName, ProductImportRow::setAlias,
                            ProductImportRow::setCategoryCode, ProductImportRow::setMnemonicCode,
                            ProductImportRow::setBrandName, ProductImportRow::setOrigin,
                            ProductImportRow::setStorageMethod, ProductImportRow::setShelfLifeDays,
                            ProductImportRow::setTagCodes, ProductImportRow::setSpuStatus, ProductImportRow::setSkuCode,
                            ProductImportRow::setBarcode, ProductImportRow::setSpecName, ProductImportRow::setSaleUnit,
                            ProductImportRow::setProductType, ProductImportRow::setMarketPrice,
                            ProductImportRow::setSkuStatus, ProductImportRow::setDefaultFlag,
                            ProductImportRow::setSortOrder);
    private static final List<
            BiConsumer<
                    ProductImportRow,
                    String>> UPDATE_SETTERS = List.of(ProductImportRow::setTemplateVersion, ProductImportRow::setSpuId,
                            ProductImportRow::setSpuVersion, ProductImportRow::setSkuId,
                            ProductImportRow::setSkuVersion, ProductImportRow::setSpuCode, ProductImportRow::setSpuName,
                            ProductImportRow::setAlias, ProductImportRow::setCategoryCode,
                            ProductImportRow::setMnemonicCode, ProductImportRow::setBrandName,
                            ProductImportRow::setOrigin, ProductImportRow::setStorageMethod,
                            ProductImportRow::setShelfLifeDays, ProductImportRow::setTagCodes,
                            ProductImportRow::setSpuStatus, ProductImportRow::setSkuCode, ProductImportRow::setBarcode,
                            ProductImportRow::setSpecName, ProductImportRow::setSaleUnit,
                            ProductImportRow::setProductType, ProductImportRow::setMarketPrice,
                            ProductImportRow::setSkuStatus, ProductImportRow::setDefaultFlag,
                            ProductImportRow::setSortOrder);

    public byte[] buildTemplate(ProductImportService.ImportMode mode) throws IOException {
        var headers = headers(mode);
        try (var workbook = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("商品导入");
            var header = sheet.createRow(0);
            var sample = sheet.createRow(1);
            for (int column = 0; column < headers.size(); column++) {
                header.createCell(column).setCellValue(headers.get(column));
                sample.createCell(column).setCellValue(sampleValue(mode, column));
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    public List<
            ProductImportRow> readRows(byte[] bytes, ProductImportService.ImportMode mode,
                    ProductImportResultVO result) {
        var headers = headers(mode);
        var setters = mode == ProductImportService.ImportMode.CREATE ? CREATE_SETTERS : UPDATE_SETTERS;
        var rows = new ArrayList<
                ProductImportRow>();
        var formatter = new DataFormatter(Locale.ROOT);
        try (var workbook = org.apache.poi.ss.usermodel.WorkbookFactory
                .create(new java.io.ByteArrayInputStream(bytes))) {
            if (workbook.getNumberOfSheets() != 1) {
                addError(result, 0, null, "文件", "SHEET_COUNT", "请保留模板中的一个工作表");
                return rows;
            }
            var sheet = workbook.getSheetAt(0);
            var header = sheet.getRow(0);
            for (int column = 0; column < headers.size(); column++) {
                if (header == null
                        || !headers.get(column).equals(trim(formatter.formatCellValue(header.getCell(column))))) {
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
                        setters.get(column).accept(row, value);
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

    private List<
            String> headers(ProductImportService.ImportMode mode) {
        return mode == ProductImportService.ImportMode.CREATE ? CREATE_HEADERS : UPDATE_HEADERS;
    }

    private String sampleValue(ProductImportService.ImportMode mode, int column) {
        if (mode == ProductImportService.ImportMode.UPDATE) {
            return switch (column) {
                case 0 -> ProductImportService.TEMPLATE_VERSION;
                case 1 -> "1001";
                case 2 -> "0";
                case 3 -> "2001";
                case 4 -> "0";
                case 5 -> "SPU0001";
                case 6 -> "示例蔬菜";
                case 8 -> "FRESH-FRUIT";
                case 11 -> "本地";
                case 12 -> ScmStorageMethodEnum.CHILLED.name();
                case 15 -> ScmShelfStatusEnum.ON_SHELF.name();
                case 16 -> "SKU0001";
                case 18 -> "500g/份";
                case 19 -> "份";
                case 20 -> ScmProductTypeEnum.STANDARD.name();
                case 21 -> "9.9000";
                case 22 -> ScmShelfStatusEnum.ON_SHELF.name();
                case 23 -> "是";
                case 24 -> "0";
                default -> "";
            };
        }
        return switch (column) {
            case 0 -> ProductImportService.TEMPLATE_VERSION;
            case 1 -> "SPU0001";
            case 2 -> "示例蔬菜";
            case 4 -> "FRESH-FRUIT";
            case 7 -> "本地";
            case 8 -> ScmStorageMethodEnum.CHILLED.name();
            case 11 -> ScmShelfStatusEnum.ON_SHELF.name();
            case 12 -> "SKU0001";
            case 14 -> "500g/份";
            case 15 -> "份";
            case 16 -> ScmProductTypeEnum.STANDARD.name();
            case 17 -> "9.9000";
            case 18 -> ScmShelfStatusEnum.ON_SHELF.name();
            case 19 -> "是";
            case 20 -> "0";
            default -> "";
        };
    }

    private void addError(ProductImportResultVO result, int rowNumber, String key, String column, String code,
            String message) {
        result.setTotalErrors(result.getTotalErrors() + 1);
        if (result.getErrors().size() < ProductImportService.MAX_ERRORS) {
            result.getErrors().add(new ProductImportErrorVO(rowNumber, key, column, code, message));
        }
    }

    private String trim(String value) {
        if (value == null)
            return null;
        var trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
