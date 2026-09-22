package net.lab1024.sa.admin.module.scm.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.product.dao.ProductCategoryDao;
import net.lab1024.sa.admin.module.scm.product.dao.ProductTagDao;
import net.lab1024.sa.admin.module.scm.product.domain.dto.ProductImportRow;
import net.lab1024.sa.admin.module.scm.product.domain.entity.ProductCategoryEntity;
import net.lab1024.sa.admin.module.scm.product.domain.entity.ProductTagEntity;
import net.lab1024.sa.admin.module.scm.product.domain.form.ProductSkuForm;
import net.lab1024.sa.admin.module.scm.product.domain.form.ProductSpuAddForm;
import net.lab1024.sa.admin.module.scm.product.domain.vo.ProductImportErrorVO;
import net.lab1024.sa.admin.module.scm.product.domain.vo.ProductImportResultVO;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.ss.util.NumberToTextConverter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * 商品 Excel 导入：解析 + 全量校验（0 错误才写）+ 交给 {@link ProductImportWriteService} 整批回滚写入。
 * 复用既有 ProductSpuService.add 的全部领域校验与保护，不新建导入旁路。
 */
@Service
@RequiredArgsConstructor
public class ProductImportService {
    public static final String TEMPLATE_VERSION = "1.0";
    private static final int MAX_ROWS = 20000;
    private static final int MAX_PRODUCTS = 5000;
    private static final int MAX_SKUS_PER_PRODUCT = 200;
    private static final int MAX_ERRORS = 1000;
    private static final List<String> HEADERS = List.of("模板版本", "SPU编码", "商品名称", "别名", "分类编码", "助记码",
            "品牌", "产地", "储存方式", "保质期天数", "标签编码", "商品上下架", "SKU编码", "条码", "规格名称",
            "销售单位", "商品类型", "市场价", "SKU上下架", "默认SKU", "排序");
    private static final List<BiConsumer<ProductImportRow, String>> SETTERS = List.of(
            ProductImportRow::setTemplateVersion, ProductImportRow::setSpuCode, ProductImportRow::setSpuName,
            ProductImportRow::setAlias, ProductImportRow::setCategoryCode, ProductImportRow::setMnemonicCode,
            ProductImportRow::setBrandName, ProductImportRow::setOrigin, ProductImportRow::setStorageMethod,
            ProductImportRow::setShelfLifeDays, ProductImportRow::setTagCodes, ProductImportRow::setSpuStatus,
            ProductImportRow::setSkuCode, ProductImportRow::setBarcode, ProductImportRow::setSpecName,
            ProductImportRow::setSaleUnit, ProductImportRow::setProductType, ProductImportRow::setMarketPrice,
            ProductImportRow::setSkuStatus, ProductImportRow::setDefaultFlag, ProductImportRow::setSortOrder);
    private static final Set<String> SHELF = Set.of("ON_SHELF", "OFF_SHELF");
    private static final Set<String> PRODUCT_TYPE = Set.of("STANDARD", "NON_STANDARD");
    private static final Set<String> STORAGE = Set.of("AMBIENT", "CHILLED", "FROZEN");
    private static final Set<String> TRUTHY = Set.of("是", "Y", "YES", "TRUE", "1");

    private final ProductCategoryDao categories;
    private final ProductTagDao tags;
    private final ProductImportWriteService writer;

    public ProductImportResultVO importFile(MultipartFile file) throws Exception {
        var result = new ProductImportResultVO();
        var rows = readRows(file.getBytes(), result);
        if (result.getTotalErrors() > 0) return result;
        var assembled = assemble(rows, result);
        if (result.getTotalErrors() > 0) return result;
        try {
            var ids = writer.writeAll(assembled.forms());
            result.setImportedProducts(ids.size());
            result.getSpuIds().addAll(ids);
            return result;
        } catch (ProductImportWriteService.ImportProductException exception) {
            var group = assembled.groups().get(exception.getProductIndex());
            var cause = exception.getCause();
            var code = cause instanceof ScmBusinessException business ? String.valueOf(business.getErrorCode().getCode()) : "WRITE_CONFLICT";
            var message = cause instanceof ScmBusinessException business ? business.getErrorCode().getMsg() : "商品数据冲突或超出范围，整批已回滚";
            for (var row : group) addError(result, row.getRowNumber(), trim(row.getSpuCode()), "商品", code, message + "；整批已回滚");
            return result;
        }
    }

    /** 真实 xlsx 模板：一行表头 + 一行示例，表头与 {@link #HEADERS} 单一来源，避免模板与解析漂移。 */
    public byte[] buildTemplate() throws IOException {
        try (var workbook = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("商品导入");
            var header = sheet.createRow(0);
            var sample = sheet.createRow(1);
            for (int column = 0; column < HEADERS.size(); column++) {
                header.createCell(column).setCellValue(HEADERS.get(column));
                sample.createCell(column).setCellValue(sampleValue(column));
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private String sampleValue(int column) {
        return switch (column) {
            case 0 -> TEMPLATE_VERSION;
            case 1 -> "SPU0001";
            case 2 -> "示例蔬菜";
            case 4 -> "FRESH-FRUIT";
            case 7 -> "本地";
            case 8 -> "CHILLED";
            case 11 -> "ON_SHELF";
            case 12 -> "SKU0001";
            case 14 -> "500g/份";
            case 15 -> "份";
            case 16 -> "STANDARD";
            case 17 -> "9.9000";
            case 18 -> "ON_SHELF";
            case 19 -> "是";
            case 20 -> "0";
            default -> "";
        };
    }

    private List<ProductImportRow> readRows(byte[] bytes, ProductImportResultVO result) {
        var rows = new ArrayList<ProductImportRow>();
        var formatter = new DataFormatter(java.util.Locale.ROOT);
        try (var workbook = org.apache.poi.ss.usermodel.WorkbookFactory.create(new java.io.ByteArrayInputStream(bytes))) {
            if (workbook.getNumberOfSheets() != 1) {
                addError(result, 0, null, "文件", "SHEET_COUNT", "请保留模板中的一个工作表");
                return rows;
            }
            var sheet = workbook.getSheetAt(0);
            var header = sheet.getRow(0);
            for (int column = 0; column < HEADERS.size(); column++) {
                if (header == null || !HEADERS.get(column).equals(trim(formatter.formatCellValue(header.getCell(column))))) {
                    addError(result, 1, null, CellReference.convertNumToColString(column), "HEADER_INVALID", "表头应为“" + HEADERS.get(column) + "”，请使用最新模板");
                }
            }
            if (result.getTotalErrors() > 0) return rows;
            for (var excelRow : sheet) {
                if (excelRow.getRowNum() == 0) continue;
                var row = new ProductImportRow();
                row.setRowNumber(excelRow.getRowNum() + 1);
                boolean hasData = false;
                for (var cell : excelRow) {
                    var value = trim(formatter.formatCellValue(cell));
                    if (value == null) continue;
                    hasData = true;
                    var column = cell.getColumnIndex();
                    var name = column < HEADERS.size() ? HEADERS.get(column) : CellReference.convertNumToColString(column);
                    if (cell.getCellType() == CellType.FORMULA || cell.getCellType() == CellType.ERROR) {
                        addError(result, row.getRowNumber(), null, name, "CELL_INVALID", "不能使用公式或错误单元格");
                    } else if (column >= HEADERS.size()) {
                        addError(result, row.getRowNumber(), null, name, "COLUMN_UNEXPECTED", "模板之外的列不能填写数据");
                    } else {
                        if (cell.getCellType() == CellType.NUMERIC)
                            value = NumberToTextConverter.toText(cell.getNumericCellValue());
                        SETTERS.get(column).accept(row, value);
                    }
                }
                if (hasData) rows.add(row);
                if (rows.size() > MAX_ROWS) {
                    addError(result, row.getRowNumber(), null, "文件", "ROW_LIMIT", "数据行不能超过 " + MAX_ROWS + " 行");
                    break;
                }
            }
        } catch (Exception exception) {
            addError(result, 0, null, "文件", "FILE_INVALID", "Excel 文件无法读取，请使用最新模板");
        }
        result.setTotalRows(rows.size());
        return rows;
    }

    private Assembly assemble(List<ProductImportRow> rows, ProductImportResultVO result) {
        result.setTotalRows(rows.size());
        if (rows.isEmpty()) addError(result, 0, null, "文件", "FILE_EMPTY", "导入文件没有数据行");
        if (rows.size() > MAX_ROWS) addError(result, 0, null, "文件", "ROW_LIMIT", "数据行不能超过 " + MAX_ROWS + " 行");

        var categoryCodes = rows.stream().map(r -> trim(r.getCategoryCode())).filter(Objects::nonNull).distinct().toList();
        var categoryMap = categoryCodes.isEmpty() ? Map.<String, ProductCategoryEntity>of()
                : categories.selectList(new LambdaQueryWrapper<ProductCategoryEntity>()
                    .in(ProductCategoryEntity::getCategoryCode, categoryCodes).eq(ProductCategoryEntity::getDeleted, false))
                .stream().collect(Collectors.toMap(ProductCategoryEntity::getCategoryCode, c -> c, (a, b) -> a));
        var allTagCodes = rows.stream().flatMap(r -> splitTags(r.getTagCodes()).stream()).distinct().toList();
        var tagMap = allTagCodes.isEmpty() ? Map.<String, ProductTagEntity>of()
                : tags.selectList(new LambdaQueryWrapper<ProductTagEntity>()
                    .in(ProductTagEntity::getTagCode, allTagCodes).eq(ProductTagEntity::getDeleted, false))
                .stream().collect(Collectors.toMap(ProductTagEntity::getTagCode, t -> t, (a, b) -> a));

        var groups = new LinkedHashMap<String, List<ProductImportRow>>();
        for (var row : rows) validateRow(row, categoryMap, tagMap, result);
        for (var row : rows) {
            var key = trim(row.getSpuCode());
            if (key != null) groups.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }
        result.setTotalProducts(groups.size());
        if (groups.size() > MAX_PRODUCTS) addError(result, 0, null, "SPU编码", "PRODUCT_LIMIT", "商品数不能超过 " + MAX_PRODUCTS + " 个");

        var forms = new ArrayList<ProductSpuAddForm>();
        var orderedGroups = new ArrayList<List<ProductImportRow>>();
        for (var entry : groups.entrySet()) {
            var group = entry.getValue();
            orderedGroups.add(group);
            if (group.size() > MAX_SKUS_PER_PRODUCT)
                addError(result, group.getFirst().getRowNumber(), entry.getKey(), "SKU编码", "SKU_LIMIT", "单个商品 SKU 不能超过 " + MAX_SKUS_PER_PRODUCT + " 个");
            validateGroup(entry.getKey(), group, result);
            if (result.getTotalErrors() == 0) forms.add(toForm(group, categoryMap, tagMap));
        }
        return new Assembly(forms, orderedGroups);
    }

    private void validateRow(ProductImportRow row, Map<String, ProductCategoryEntity> categoryMap,
                             Map<String, ProductTagEntity> tagMap, ProductImportResultVO result) {
        var n = row.getRowNumber();
        var key = trim(row.getSpuCode());
        required(result, n, key, "模板版本", row.getTemplateVersion());
        if (trim(row.getTemplateVersion()) != null && !TEMPLATE_VERSION.equals(trim(row.getTemplateVersion())))
            addError(result, n, key, "模板版本", "TEMPLATE_VERSION", "模板版本不受支持，请重新下载模板");
        required(result, n, key, "SPU编码", row.getSpuCode());
        required(result, n, key, "商品名称", row.getSpuName());
        required(result, n, key, "分类编码", row.getCategoryCode());
        required(result, n, key, "商品上下架", row.getSpuStatus());
        required(result, n, key, "SKU编码", row.getSkuCode());
        required(result, n, key, "规格名称", row.getSpecName());
        required(result, n, key, "销售单位", row.getSaleUnit());
        required(result, n, key, "商品类型", row.getProductType());
        required(result, n, key, "市场价", row.getMarketPrice());
        required(result, n, key, "SKU上下架", row.getSkuStatus());
        required(result, n, key, "默认SKU", row.getDefaultFlag());
        length(result, n, key, "SPU编码", row.getSpuCode(), 64);
        length(result, n, key, "商品名称", row.getSpuName(), 150);
        length(result, n, key, "SKU编码", row.getSkuCode(), 64);
        length(result, n, key, "规格名称", row.getSpecName(), 150);
        length(result, n, key, "销售单位", row.getSaleUnit(), 32);

        var categoryCode = trim(row.getCategoryCode());
        if (categoryCode != null && !categoryMap.containsKey(categoryCode))
            addError(result, n, key, "分类编码", "CATEGORY_NOT_FOUND", "分类编码不存在");
        else if (categoryCode != null && !"ENABLED".equals(categoryMap.get(categoryCode).getStatus()))
            addError(result, n, key, "分类编码", "CATEGORY_DISABLED", "分类已停用，不能作为新商品分类");

        enumValue(result, n, key, "商品上下架", row.getSpuStatus(), SHELF);
        enumValue(result, n, key, "SKU上下架", row.getSkuStatus(), SHELF);
        enumValue(result, n, key, "商品类型", row.getProductType(), PRODUCT_TYPE);
        if (trim(row.getStorageMethod()) != null) enumValue(result, n, key, "储存方式", row.getStorageMethod(), STORAGE);
        decimal(result, n, key, "市场价", row.getMarketPrice(), false);
        integer(result, n, key, "保质期天数", row.getShelfLifeDays(), 0, 36500);
        integer(result, n, key, "排序", row.getSortOrder(), 0, Integer.MAX_VALUE);
        for (var tagCode : splitTags(row.getTagCodes()))
            if (!tagMap.containsKey(tagCode)) addError(result, n, key, "标签编码", "TAG_NOT_FOUND", "标签编码不存在：" + tagCode);
    }

    private void validateGroup(String spuCode, List<ProductImportRow> rows, ProductImportResultVO result) {
        var first = rows.getFirst();
        var skuCodes = new java.util.HashSet<String>();
        int defaults = 0;
        for (var row : rows) {
            int n = row.getRowNumber();
            same(result, n, spuCode, "商品名称", first.getSpuName(), row.getSpuName());
            same(result, n, spuCode, "分类编码", first.getCategoryCode(), row.getCategoryCode());
            same(result, n, spuCode, "商品上下架", first.getSpuStatus(), row.getSpuStatus());
            same(result, n, spuCode, "储存方式", first.getStorageMethod(), row.getStorageMethod());
            same(result, n, spuCode, "标签编码", first.getTagCodes(), row.getTagCodes());
            var skuCode = trim(row.getSkuCode());
            if (skuCode != null && !skuCodes.add(skuCode))
                addError(result, n, spuCode, "SKU编码", "SKU_CODE_DUPLICATE", "同一商品内 SKU 编码不能重复");
            if (isTruthy(row.getDefaultFlag())) defaults++;
        }
        if (defaults == 0) addError(result, first.getRowNumber(), spuCode, "默认SKU", "DEFAULT_SKU_INVALID", "商品必须且只能有一个默认 SKU");
        if (defaults > 1) addError(result, first.getRowNumber(), spuCode, "默认SKU", "DEFAULT_SKU_INVALID", "默认 SKU 只能有一个");
    }

    private ProductSpuAddForm toForm(List<ProductImportRow> rows, Map<String, ProductCategoryEntity> categoryMap,
                                     Map<String, ProductTagEntity> tagMap) {
        var first = rows.getFirst();
        var form = new ProductSpuAddForm();
        form.setSpuCode(first.getSpuCode());
        form.setName(trim(first.getSpuName()));
        form.setAlias(trim(first.getAlias()));
        form.setCategoryId(categoryMap.get(trim(first.getCategoryCode())).getId());
        form.setStatus(trim(first.getSpuStatus()));
        form.setMnemonicCode(trim(first.getMnemonicCode()));
        form.setBrandName(trim(first.getBrandName()));
        form.setOrigin(trim(first.getOrigin()));
        form.setStorageMethod(trim(first.getStorageMethod()));
        if (trim(first.getShelfLifeDays()) != null) form.setShelfLifeDays(Integer.parseInt(trim(first.getShelfLifeDays())));
        form.setTagIds(splitTags(first.getTagCodes()).stream().map(c -> tagMap.get(c).getId()).distinct().collect(Collectors.toList()));
        var skuList = new ArrayList<ProductSkuForm>();
        for (var row : rows) {
            var sku = new ProductSkuForm();
            sku.setSkuCode(row.getSkuCode());
            sku.setBarcode(trim(row.getBarcode()));
            sku.setSpecName(trim(row.getSpecName()));
            sku.setSpecValues(new LinkedHashMap<>(Map.of("规格", trim(row.getSpecName()))));
            sku.setSaleUnit(trim(row.getSaleUnit()));
            sku.setProductType(trim(row.getProductType()));
            sku.setMarketPrice(new BigDecimal(trim(row.getMarketPrice())).setScale(4, RoundingMode.HALF_UP));
            sku.setStatus(trim(row.getSkuStatus()));
            sku.setDefaultFlag(isTruthy(row.getDefaultFlag()));
            sku.setSortOrder(trim(row.getSortOrder()) == null ? 0 : Integer.parseInt(trim(row.getSortOrder())));
            skuList.add(sku);
        }
        form.setSkuList(skuList);
        return form;
    }

    private boolean isTruthy(String value) {
        var v = trim(value);
        return v != null && TRUTHY.contains(v.toUpperCase(java.util.Locale.ROOT));
    }

    private List<String> splitTags(String value) {
        var v = trim(value);
        if (v == null) return List.of();
        return java.util.Arrays.stream(v.split("[,，]")).map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
    }

    private void required(ProductImportResultVO r, int n, String key, String column, String value) {
        if (trim(value) == null) addError(r, n, key, column, "REQUIRED", column + "不能为空");
    }

    private void length(ProductImportResultVO r, int n, String key, String column, String value, int max) {
        var v = trim(value);
        if (v != null && v.length() > max) addError(r, n, key, column, "TOO_LONG", column + "不能超过 " + max + " 个字符");
    }

    private void enumValue(ProductImportResultVO r, int n, String key, String column, String value, Set<String> allowed) {
        var v = trim(value);
        if (v != null && !allowed.contains(v)) addError(r, n, key, column, "ENUM_INVALID", column + "取值必须是 " + allowed);
    }

    private void decimal(ProductImportResultVO r, int n, String key, String column, String value, boolean positive) {
        var v = trim(value);
        if (v == null) return;
        try {
            if (!v.matches("[0-9]{1,14}(\\.[0-9]{1,4})?")) throw new NumberFormatException();
            var number = new BigDecimal(v);
            if (positive ? number.signum() <= 0 : number.signum() < 0) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            addError(r, n, key, column, "DECIMAL_INVALID", column + "必须是" + (positive ? "大于零的" : "非负") + "四位以内小数");
        }
    }

    private void integer(ProductImportResultVO r, int n, String key, String column, String value, int min, int max) {
        var v = trim(value);
        if (v == null) return;
        try {
            var number = Integer.parseInt(v);
            if (number < min || number > max) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            addError(r, n, key, column, "INT_INVALID", column + "必须是 " + min + "~" + max + " 的整数");
        }
    }

    private void same(ProductImportResultVO r, int n, String key, String column, String expected, String actual) {
        if (!Objects.equals(trim(expected), trim(actual))) addError(r, n, key, column, "HEADER_CONFLICT", "同一商品的" + column + "必须一致");
    }

    private String trim(String value) {
        if (value == null) return null;
        var trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void addError(ProductImportResultVO r, int n, String key, String column, String code, String message) {
        r.setTotalErrors(r.getTotalErrors() + 1);
        if (r.getErrors().size() < MAX_ERRORS) r.getErrors().add(new ProductImportErrorVO(n, key, column, code, message));
    }

    private record Assembly(List<ProductSpuAddForm> forms, List<List<ProductImportRow>> groups) {
    }
}
