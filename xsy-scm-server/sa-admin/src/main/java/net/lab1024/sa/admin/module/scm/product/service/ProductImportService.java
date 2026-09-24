package net.lab1024.sa.admin.module.scm.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.product.dao.ProductCategoryDao;
import net.lab1024.sa.admin.module.scm.product.dao.ProductImageDao;
import net.lab1024.sa.admin.module.scm.product.dao.ProductSkuDao;
import net.lab1024.sa.admin.module.scm.product.dao.ProductSpuDao;
import net.lab1024.sa.admin.module.scm.product.dao.ProductTagDao;
import net.lab1024.sa.admin.module.scm.product.dao.ProductUomDao;
import net.lab1024.sa.admin.module.scm.product.domain.dto.ProductImportRow;
import net.lab1024.sa.admin.module.scm.product.domain.entity.ProductCategoryEntity;
import net.lab1024.sa.admin.module.scm.product.domain.entity.ProductImageEntity;
import net.lab1024.sa.admin.module.scm.product.domain.entity.ProductSkuEntity;
import net.lab1024.sa.admin.module.scm.product.domain.entity.ProductSpuEntity;
import net.lab1024.sa.admin.module.scm.product.domain.entity.ProductTagEntity;
import net.lab1024.sa.admin.module.scm.product.domain.entity.ProductUomEntity;
import net.lab1024.sa.admin.module.scm.product.domain.form.ProductImageForm;
import net.lab1024.sa.admin.module.scm.product.domain.form.ProductSkuForm;
import net.lab1024.sa.admin.module.scm.product.domain.form.ProductSpuAddForm;
import net.lab1024.sa.admin.module.scm.product.domain.form.ProductSpuUpdateForm;
import net.lab1024.sa.admin.module.scm.product.domain.vo.ProductImportErrorVO;
import net.lab1024.sa.admin.module.scm.product.domain.vo.ProductImportResultVO;
import net.lab1024.sa.admin.module.scm.product.manager.ProductAggregateValidator;
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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 商品 Excel 导入：解析 + 全量校验（0 错误才写）+ 交给 {@link ProductImportWriteService} 整批回滚写入。
 * 复用既有 ProductSpuService.add / update 的全部领域校验与保护，不新建导入旁路。
 *
 * <p>两种模式语义互斥且必须显式选择：
 * {@link ImportMode#CREATE} 整批新增；{@link ImportMode#UPDATE} 按 SPU ID / SKU ID + 版本定位既存行，
 * <b>留空表示保持原值</b>（不是清空），未出现在 Excel 的 SKU 也不会被删除；
 * 要把可空属性（别名、助记码、品牌、产地、标签编码、条码）清成空，在该单元格填 {@link #CLEAR_TOKEN}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductImportService {
    public static final String TEMPLATE_VERSION = "1.0";
    /** UPDATE 模式的显式清空标记：空白=保持原值，因此清除既存值必须另有记号。仅 UPDATE 解释该记号。 */
    public static final String CLEAR_TOKEN = "(清空)";
    /** 这些列没有枚举/数值校验兜底，标记出现在这里必须拒绝，否则会被当成普通值写进名称、单位或默认标记。 */
    private static final List<Map.Entry<String, Function<ProductImportRow, String>>> CLEAR_GUARDED = List.of(
            Map.entry("商品名称", ProductImportRow::getSpuName),
            Map.entry("SKU编码", ProductImportRow::getSkuCode),
            Map.entry("规格名称", ProductImportRow::getSpecName),
            Map.entry("销售单位", ProductImportRow::getSaleUnit),
            Map.entry("默认SKU", ProductImportRow::getDefaultFlag));
    private static final int MAX_ROWS = 20000;
    private static final int MAX_PRODUCTS = 5000;
    private static final int MAX_SKUS_PER_PRODUCT = 200;
    private static final int MAX_ERRORS = 1000;
    private static final List<String> CREATE_HEADERS = List.of("模板版本", "SPU编码", "商品名称", "别名", "分类编码", "助记码",
            "品牌", "产地", "储存方式", "保质期天数", "标签编码", "商品上下架", "SKU编码", "条码", "规格名称",
            "销售单位", "商品类型", "市场价", "SKU上下架", "默认SKU", "排序");
    /** UPDATE 把定位键放在最前面；SPU / SKU 编码是业务身份，只能核对不能改。 */
    private static final List<String> UPDATE_HEADERS = List.of("模板版本", "SPU ID", "SPU版本", "SKU ID", "SKU版本",
            "SPU编码", "商品名称", "别名", "分类编码", "助记码", "品牌", "产地", "储存方式", "保质期天数", "标签编码",
            "商品上下架", "SKU编码", "条码", "规格名称", "销售单位", "商品类型", "市场价", "SKU上下架", "默认SKU", "排序");
    private static final List<BiConsumer<ProductImportRow, String>> CREATE_SETTERS = List.of(
            ProductImportRow::setTemplateVersion, ProductImportRow::setSpuCode, ProductImportRow::setSpuName,
            ProductImportRow::setAlias, ProductImportRow::setCategoryCode, ProductImportRow::setMnemonicCode,
            ProductImportRow::setBrandName, ProductImportRow::setOrigin, ProductImportRow::setStorageMethod,
            ProductImportRow::setShelfLifeDays, ProductImportRow::setTagCodes, ProductImportRow::setSpuStatus,
            ProductImportRow::setSkuCode, ProductImportRow::setBarcode, ProductImportRow::setSpecName,
            ProductImportRow::setSaleUnit, ProductImportRow::setProductType, ProductImportRow::setMarketPrice,
            ProductImportRow::setSkuStatus, ProductImportRow::setDefaultFlag, ProductImportRow::setSortOrder);
    private static final List<BiConsumer<ProductImportRow, String>> UPDATE_SETTERS = List.of(
            ProductImportRow::setTemplateVersion, ProductImportRow::setSpuId, ProductImportRow::setSpuVersion,
            ProductImportRow::setSkuId, ProductImportRow::setSkuVersion, ProductImportRow::setSpuCode,
            ProductImportRow::setSpuName, ProductImportRow::setAlias, ProductImportRow::setCategoryCode,
            ProductImportRow::setMnemonicCode, ProductImportRow::setBrandName, ProductImportRow::setOrigin,
            ProductImportRow::setStorageMethod, ProductImportRow::setShelfLifeDays, ProductImportRow::setTagCodes,
            ProductImportRow::setSpuStatus, ProductImportRow::setSkuCode, ProductImportRow::setBarcode,
            ProductImportRow::setSpecName, ProductImportRow::setSaleUnit, ProductImportRow::setProductType,
            ProductImportRow::setMarketPrice, ProductImportRow::setSkuStatus, ProductImportRow::setDefaultFlag,
            ProductImportRow::setSortOrder);
    private static final Set<String> SHELF = Set.of("ON_SHELF", "OFF_SHELF");
    private static final Set<String> PRODUCT_TYPE = Set.of("STANDARD", "NON_STANDARD");
    private static final Set<String> STORAGE = Set.of("AMBIENT", "CHILLED", "FROZEN");
    private static final Set<String> TRUTHY = Set.of("是", "Y", "YES", "TRUE", "1");

    private final ProductCategoryDao categories;
    private final ProductTagDao tags;
    private final ProductUomDao units;
    private final ProductSpuDao spus;
    private final ProductSkuDao skus;
    private final ProductImageDao images;
    private final ProductTagService productTags;
    private final ProductImportWriteService writer;

    /** CREATE 整批新增；UPDATE 按定位键改写既存商品，两者语义不可混用。 */
    public enum ImportMode {
        CREATE, UPDATE
    }

    public ProductImportResultVO importFile(MultipartFile file, ImportMode mode) throws Exception {
        var result = new ProductImportResultVO();
        result.setMode(mode.name());
        var rows = readRows(file.getBytes(), mode, result);
        if (result.getTotalErrors() > 0) return result;
        if (mode == ImportMode.CREATE) {
            var assembled = assembleCreates(rows, result);
            if (result.getTotalErrors() > 0) return result;
            try {
                var ids = writer.writeAll(assembled.forms());
                result.setImportedProducts(ids.size());
                result.getSpuIds().addAll(ids);
                return result;
            } catch (ProductImportWriteService.ImportProductException exception) {
                var group = assembled.groups().get(exception.getProductIndex());
                addWriteError(result, group, exception.getCause());
                return result;
            }
        }
        var assembled = assembleUpdates(rows, result);
        if (result.getTotalErrors() > 0) return result;
        try {
            result.setUpdatedProducts(writer.writeUpdates(assembled.forms()));
            assembled.forms().forEach(form -> result.getSpuIds().add(form.getSpuId()));
            return result;
        } catch (ProductImportWriteService.ImportProductException exception) {
            addWriteError(result, assembled.groups().get(exception.getProductIndex()), exception.getCause());
            return result;
        }
    }

    private void addWriteError(ProductImportResultVO result, List<ProductImportRow> group, Throwable cause) {
        var code = cause instanceof ScmBusinessException business ? String.valueOf(business.getErrorCode().getCode()) : "WRITE_CONFLICT";
        var message = cause instanceof ScmBusinessException business ? business.getErrorCode().getMsg() : "商品数据冲突或超出范围，整批已回滚";
        for (var row : group)
            addError(result, row.getRowNumber(), trim(row.getSpuCode()), "商品", code, message + "；整批已回滚");
    }

    /** 真实 xlsx 模板：一行表头 + 一行示例，表头与解析口径单一来源，避免模板与校验漂移。 */
    public byte[] buildTemplate(ImportMode mode) throws IOException {
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

    private List<String> headers(ImportMode mode) {
        return mode == ImportMode.CREATE ? CREATE_HEADERS : UPDATE_HEADERS;
    }

    private String sampleValue(ImportMode mode, int column) {
        if (mode == ImportMode.UPDATE) {
            return switch (column) {
                case 0 -> TEMPLATE_VERSION;
                case 1 -> "1001";
                case 2 -> "0";
                case 3 -> "2001";
                case 4 -> "0";
                case 5 -> "SPU0001";
                case 6 -> "示例蔬菜";
                case 8 -> "FRESH-FRUIT";
                case 11 -> "本地";
                case 12 -> "CHILLED";
                case 15 -> "ON_SHELF";
                case 16 -> "SKU0001";
                case 18 -> "500g/份";
                case 19 -> "份";
                case 20 -> "STANDARD";
                case 21 -> "9.9000";
                case 22 -> "ON_SHELF";
                case 23 -> "是";
                case 24 -> "0";
                default -> "";
            };
        }
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

    private List<ProductImportRow> readRows(byte[] bytes, ImportMode mode, ProductImportResultVO result) {
        var headers = headers(mode);
        var setters = mode == ImportMode.CREATE ? CREATE_SETTERS : UPDATE_SETTERS;
        var rows = new ArrayList<ProductImportRow>();
        var formatter = new DataFormatter(Locale.ROOT);
        try (var workbook = org.apache.poi.ss.usermodel.WorkbookFactory.create(new java.io.ByteArrayInputStream(bytes))) {
            if (workbook.getNumberOfSheets() != 1) {
                addError(result, 0, null, "文件", "SHEET_COUNT", "请保留模板中的一个工作表");
                return rows;
            }
            var sheet = workbook.getSheetAt(0);
            var header = sheet.getRow(0);
            for (int column = 0; column < headers.size(); column++) {
                if (header == null || !headers.get(column).equals(trim(formatter.formatCellValue(header.getCell(column))))) {
                    addError(result, 1, null, CellReference.convertNumToColString(column), "HEADER_INVALID",
                            "表头应为“" + headers.get(column) + "”，请使用" + (mode == ImportMode.CREATE ? "新增" : "更新") + "模板");
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
                    var name = column < headers.size() ? headers.get(column) : CellReference.convertNumToColString(column);
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
                if (hasData) rows.add(row);
                if (rows.size() > MAX_ROWS) {
                    addError(result, row.getRowNumber(), null, "文件", "ROW_LIMIT", "数据行不能超过 " + MAX_ROWS + " 行");
                    break;
                }
            }
        } catch (Exception exception) {
            // 对调用方仍收敛成稳定的 FILE_INVALID，但服务端必须留下真因：解析循环里的 NPE、越界与 POI
            // 内部异常若只被改写成「请使用最新模板」，用户会反复重导模板而运维零线索。
            log.error("商品导入文件解析失败，整批按 FILE_INVALID 拒绝", exception);
            addError(result, 0, null, "文件", "FILE_INVALID", "Excel 文件无法读取，请使用最新模板");
        }
        result.setTotalRows(rows.size());
        return rows;
    }

    // ------------------------------------------------------------------
    // CREATE
    // ------------------------------------------------------------------

    private Assembly assembleCreates(List<ProductImportRow> rows, ProductImportResultVO result) {
        result.setTotalRows(rows.size());
        if (rows.isEmpty()) addError(result, 0, null, "文件", "FILE_EMPTY", "导入文件没有数据行");
        if (rows.size() > MAX_ROWS) addError(result, 0, null, "文件", "ROW_LIMIT", "数据行不能超过 " + MAX_ROWS + " 行");

        var categoryMap = loadCategories(rows);
        var tagMap = loadTags(rows);
        var unitMap = loadUnits(rows);

        var groups = new LinkedHashMap<String, List<ProductImportRow>>();
        for (var row : rows) validateCreateRow(row, categoryMap, tagMap, unitMap, result);
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
            if (result.getTotalErrors() == 0) forms.add(toCreateForm(group, categoryMap, tagMap));
        }
        return new Assembly(forms, orderedGroups);
    }

    // ------------------------------------------------------------------
    // UPDATE
    // ------------------------------------------------------------------

    /**
     * 更新模式先按定位键读回现状，再把 Excel 的非空单元格覆盖到现状之上：
     * 空白保持原值、未列出的 SKU 保持原样、Excel 之外的字段（详情描述、税率、图片等）一律原值回写，
     * 因此不会把「只改一个市场价」变成整单替换。
     */
    private UpdateAssembly assembleUpdates(List<ProductImportRow> rows, ProductImportResultVO result) {
        result.setTotalRows(rows.size());
        if (rows.isEmpty()) addError(result, 0, null, "文件", "FILE_EMPTY", "导入文件没有数据行");
        if (rows.size() > MAX_ROWS) addError(result, 0, null, "文件", "ROW_LIMIT", "数据行不能超过 " + MAX_ROWS + " 行");

        var categoryMap = loadCategories(rows);
        var tagMap = loadTags(rows);
        for (var row : rows) validateUpdateRow(row, categoryMap, tagMap, result);

        var spuIds = rows.stream().map(r -> parseLong(r.getSpuId())).filter(Objects::nonNull).distinct().toList();
        // 一次批量读现状，避免逐行查库；软删行读不到即按「商品不存在」报错。
        var spuMap = spuIds.isEmpty() ? Map.<Long, ProductSpuEntity>of()
                : spus.selectList(new LambdaQueryWrapper<ProductSpuEntity>().in(ProductSpuEntity::getId, spuIds)
                        .eq(ProductSpuEntity::getDeleted, false))
                .stream().collect(Collectors.toMap(ProductSpuEntity::getId, s -> s, (a, b) -> a));
        var skuMap = spuIds.isEmpty() ? Map.<Long, List<ProductSkuEntity>>of()
                : skus.selectList(new LambdaQueryWrapper<ProductSkuEntity>().in(ProductSkuEntity::getSpuId, spuIds)
                        .eq(ProductSkuEntity::getDeleted, false))
                .stream().collect(Collectors.groupingBy(ProductSkuEntity::getSpuId));
        var imageMap = spuIds.isEmpty() ? Map.<Long, List<ProductImageEntity>>of()
                : images.selectList(new LambdaQueryWrapper<ProductImageEntity>().in(ProductImageEntity::getSpuId, spuIds)
                        .eq(ProductImageEntity::getDeleted, false))
                .stream().collect(Collectors.groupingBy(ProductImageEntity::getSpuId));
        var tagIdMap = spuIds.isEmpty() ? Map.<Long, List<Long>>of()
                : productTags.bySpuIds(spuIds).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().stream().map(t -> t.getTagId()).toList()));

        var groups = new LinkedHashMap<String, List<ProductImportRow>>();
        for (var row : rows) {
            var key = trim(row.getSpuId());
            if (key != null) groups.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }
        result.setTotalProducts(groups.size());
        if (groups.size() > MAX_PRODUCTS) addError(result, 0, null, "SPU ID", "PRODUCT_LIMIT", "商品数不能超过 " + MAX_PRODUCTS + " 个");

        var forms = new ArrayList<ProductSpuUpdateForm>();
        var orderedGroups = new ArrayList<List<ProductImportRow>>();
        for (var entry : groups.entrySet()) {
            var group = entry.getValue();
            orderedGroups.add(group);
            if (group.size() > MAX_SKUS_PER_PRODUCT)
                addError(result, group.getFirst().getRowNumber(), trim(group.getFirst().getSpuCode()), "SKU ID", "SKU_LIMIT",
                        "单个商品 SKU 不能超过 " + MAX_SKUS_PER_PRODUCT + " 个");
            validateUpdateGroup(entry.getKey(), group, spuMap, skuMap, result);
            if (result.getTotalErrors() == 0) {
                var spu = spuMap.get(parseLong(entry.getKey()));
                forms.add(toUpdateForm(group, spu, skuMap.getOrDefault(spu.getId(), List.of()),
                        imageMap.getOrDefault(spu.getId(), List.of()), tagIdMap.getOrDefault(spu.getId(), List.of()),
                        categoryMap, tagMap));
            }
        }
        return new UpdateAssembly(forms, orderedGroups);
    }

    private void validateUpdateGroup(String spuKey, List<ProductImportRow> rows, Map<Long, ProductSpuEntity> spuMap,
                                     Map<Long, List<ProductSkuEntity>> skuMap, ProductImportResultVO result) {
        var first = rows.getFirst();
        var spuId = parseLong(spuKey);
        var n = first.getRowNumber();
        var spu = spuId == null ? null : spuMap.get(spuId);
        if (spu == null) {
            addError(result, n, trim(first.getSpuCode()), "SPU ID", "PRODUCT_NOT_FOUND", "商品不存在或已删除，请重新导出更新模板");
            return;
        }
        // 版本在此处只做友好提示，真正拦截在 ProductSpuService.update 的事务内
        if (!Objects.equals(parseInteger(first.getSpuVersion()), spu.getVersion())) {
            for (var row : rows)
                addError(result, row.getRowNumber(), trim(row.getSpuCode()), "SPU版本", "VERSION_CONFLICT",
                        "SPU 版本已变化（当前 " + spu.getVersion() + "），本次整批拒绝，请重新导出");
            return;
        }
        if (trim(first.getSpuCode()) != null && !Objects.equals(ProductAggregateValidator.normalizeCode(first.getSpuCode()), spu.getSpuCode()))
            addError(result, n, trim(first.getSpuCode()), "SPU编码", "FIELD_LOCKED", "SPU 编码不能通过导入修改，请走商品编辑");

        var existingSkus = skuMap.getOrDefault(spuId, List.of()).stream()
                .collect(Collectors.toMap(ProductSkuEntity::getId, s -> s, (a, b) -> a));
        var seenSkuIds = new java.util.HashSet<Long>();
        var skuCodes = existingSkus.values().stream().map(ProductSkuEntity::getSkuCode).collect(Collectors.toCollection(java.util.HashSet::new));
        int defaults = 0;
        for (var row : rows) {
            int rowNumber = row.getRowNumber();
            same(result, rowNumber, trim(row.getSpuCode()), "商品名称", first.getSpuName(), row.getSpuName());
            same(result, rowNumber, trim(row.getSpuCode()), "分类编码", first.getCategoryCode(), row.getCategoryCode());
            same(result, rowNumber, trim(row.getSpuCode()), "商品上下架", first.getSpuStatus(), row.getSpuStatus());
            same(result, rowNumber, trim(row.getSpuCode()), "储存方式", first.getStorageMethod(), row.getStorageMethod());
            same(result, rowNumber, trim(row.getSpuCode()), "标签编码", first.getTagCodes(), row.getTagCodes());
            same(result, rowNumber, trim(row.getSpuCode()), "SPU版本", first.getSpuVersion(), row.getSpuVersion());
            var skuId = parseLong(row.getSkuId());
            if (skuId != null) {
                var existing = existingSkus.get(skuId);
                if (existing == null || !seenSkuIds.add(skuId)) {
                    addError(result, rowNumber, trim(row.getSkuCode()), "SKU ID", "SKU_NOT_OWNED", "SKU 不属于该商品或重复出现");
                    continue;
                }
                if (!Objects.equals(parseInteger(row.getSkuVersion()), existing.getVersion()))
                    addError(result, rowNumber, trim(row.getSkuCode()), "SKU版本", "VERSION_CONFLICT",
                            "SKU 版本已变化（当前 " + existing.getVersion() + "），本次整批拒绝，请重新导出");
                if (trim(row.getSkuCode()) != null && !Objects.equals(ProductAggregateValidator.normalizeCode(row.getSkuCode()), existing.getSkuCode()))
                    addError(result, rowNumber, trim(row.getSkuCode()), "SKU编码", "FIELD_LOCKED", "SKU 编码不能通过导入修改，请走商品编辑");
                if (isTruthy(row.getDefaultFlag())) defaults++;
                else if (Boolean.TRUE.equals(existing.getDefaultFlag()) && trim(row.getDefaultFlag()) == null) defaults++;
                continue;
            }
            // 新增 SKU 走与新增导入相同的必填口径；SKU 版本对新增行没有意义
            if (trim(row.getSkuVersion()) != null)
                addError(result, rowNumber, trim(row.getSkuCode()), "SKU版本", "NEW_SKU_VERSION", "新增 SKU 不应填写 SKU 版本");
            required(result, rowNumber, trim(row.getSkuCode()), "SKU编码", row.getSkuCode());
            required(result, rowNumber, trim(row.getSkuCode()), "规格名称", row.getSpecName());
            required(result, rowNumber, trim(row.getSkuCode()), "销售单位", row.getSaleUnit());
            required(result, rowNumber, trim(row.getSkuCode()), "商品类型", row.getProductType());
            required(result, rowNumber, trim(row.getSkuCode()), "市场价", row.getMarketPrice());
            required(result, rowNumber, trim(row.getSkuCode()), "SKU上下架", row.getSkuStatus());
            required(result, rowNumber, trim(row.getSkuCode()), "默认SKU", row.getDefaultFlag());
            var skuCode = ProductAggregateValidator.normalizeCode(row.getSkuCode());
            if (skuCode != null && !skuCodes.add(skuCode))
                addError(result, rowNumber, trim(row.getSkuCode()), "SKU编码", "SKU_CODE_DUPLICATE", "同一商品内 SKU 编码不能重复");
            if (isTruthy(row.getDefaultFlag())) defaults++;
        }
        // 未列入 Excel 的既存 SKU 会被原样保留，其默认标记同样有效，不能按「文件里没写」判成没有默认
        for (var existing : existingSkus.values())
            if (!seenSkuIds.contains(existing.getId()) && Boolean.TRUE.equals(existing.getDefaultFlag())) defaults++;
        if (defaults == 0) addError(result, n, trim(first.getSpuCode()), "默认SKU", "DEFAULT_SKU_INVALID", "商品必须保留一个默认 SKU");
        if (defaults > 1) addError(result, n, trim(first.getSpuCode()), "默认SKU", "DEFAULT_SKU_INVALID", "默认 SKU 只能有一个");
    }

    private ProductSpuUpdateForm toUpdateForm(List<ProductImportRow> rows, ProductSpuEntity spu,
                                              List<ProductSkuEntity> existingSkus, List<ProductImageEntity> existingImages,
                                              List<Long> existingTagIds, Map<String, ProductCategoryEntity> categoryMap,
                                              Map<String, ProductTagEntity> tagMap) {
        var first = rows.getFirst();
        var form = new ProductSpuUpdateForm();
        form.setSpuId(spu.getId());
        form.setVersion(spu.getVersion());
        // 编码是身份不是属性：一律按库内值回写，校验阶段已保证 Excel 值与之一致
        form.setSpuCode(spu.getSpuCode());
        form.setName(keep(trim(first.getSpuName()), spu.getName()));
        form.setAlias(keepOrClear(trim(first.getAlias()), spu.getAlias()));
        var categoryCode = trim(first.getCategoryCode());
        form.setCategoryId(categoryCode == null ? spu.getCategoryId() : categoryMap.get(categoryCode).getId());
        form.setStatus(keep(trim(first.getSpuStatus()), spu.getStatus()));
        form.setMnemonicCode(keepOrClear(trim(first.getMnemonicCode()), spu.getMnemonicCode()));
        form.setBrandName(keepOrClear(trim(first.getBrandName()), spu.getBrandName()));
        form.setOrigin(keepOrClear(trim(first.getOrigin()), spu.getOrigin()));
        form.setStorageMethod(keep(trim(first.getStorageMethod()), spu.getStorageMethod()));
        form.setShelfLifeDays(parseInteger(keep(trim(first.getShelfLifeDays()), string(spu.getShelfLifeDays()))));
        // Excel 模板不含这些列，必须原值回写，否则「只改市场价」会把详情描述、税务字段清空
        form.setDescription(spu.getDescription());
        form.setLossRate(spu.getLossRate());
        form.setPurchaseWarningDays(spu.getPurchaseWarningDays());
        form.setInvoiceName(spu.getInvoiceName());
        form.setTaxCategoryCode(spu.getTaxCategoryCode());
        form.setTaxExempt(spu.getTaxExempt());
        form.setTaxRate(spu.getTaxRate());
        form.setMasterStatus(spu.getMasterStatus());
        var tagCodes = trim(first.getTagCodes());
        // 标记=解除全部标签，空白=保持现有标签，两者语义不同
        form.setTagIds(CLEAR_TOKEN.equals(tagCodes) ? List.of() : tagCodes == null ? existingTagIds
                : splitTags(tagCodes).stream().map(c -> tagMap.get(c).getId()).distinct().collect(Collectors.toList()));
        form.setImages(existingImages.stream().map(this::toImageForm).collect(Collectors.toList()));
        form.setSkuList(mergeSkus(rows, existingSkus));
        return form;
    }

    /** 既存 SKU 按 skuId 覆盖，未列出的原样保留，空白列保持原值。 */
    private List<ProductSkuForm> mergeSkus(List<ProductImportRow> rows, List<ProductSkuEntity> existingSkus) {
        var merged = existingSkus.stream().collect(Collectors.toMap(ProductSkuEntity::getId, this::toSkuForm,
                (a, b) -> a, LinkedHashMap::new));
        var created = new ArrayList<ProductSkuForm>();
        for (var row : rows) {
            var skuId = parseLong(row.getSkuId());
            if (skuId != null) {
                applySkuOverride(merged.get(skuId), row);
                continue;
            }
            var form = new ProductSkuForm();
            form.setSkuCode(row.getSkuCode());
            form.setSpecName(trim(row.getSpecName()));
            // 新增 SKU 与新增导入使用同一套规格快照口径
            form.setSpecValues(new LinkedHashMap<>(Map.of("规格", trim(row.getSpecName()))));
            applySkuOverride(form, row);
            created.add(form);
        }
        var result = new ArrayList<ProductSkuForm>(merged.size() + created.size());
        result.addAll(merged.values());
        result.addAll(created);
        return result;
    }

    private void applySkuOverride(ProductSkuForm form, ProductImportRow row) {
        if (form == null) return;
        var barcode = trim(row.getBarcode());
        if (CLEAR_TOKEN.equals(barcode)) form.setBarcode(null);
        else if (barcode != null) form.setBarcode(barcode);
        var specName = trim(row.getSpecName());
        // 只改规格名称，不动既存规格属性 JSON；属性编辑属于商品编辑页职责
        if (specName != null && form.getSkuId() != null) form.setSpecName(specName);
        var saleUnit = trim(row.getSaleUnit());
        if (saleUnit != null) form.setSaleUnit(saleUnit);
        var productType = trim(row.getProductType());
        if (productType != null) form.setProductType(productType);
        var marketPrice = trim(row.getMarketPrice());
        if (marketPrice != null) form.setMarketPrice(new BigDecimal(marketPrice).setScale(4, RoundingMode.HALF_UP));
        var skuStatus = trim(row.getSkuStatus());
        if (skuStatus != null) form.setStatus(skuStatus);
        var defaultFlag = trim(row.getDefaultFlag());
        if (defaultFlag != null) form.setDefaultFlag(isTruthy(defaultFlag));
        var sortOrder = trim(row.getSortOrder());
        if (sortOrder != null) form.setSortOrder(Integer.parseInt(sortOrder));
    }

    private ProductSkuForm toSkuForm(ProductSkuEntity entity) {
        var form = new ProductSkuForm();
        form.setSkuId(entity.getId());
        form.setVersion(entity.getVersion());
        form.setSkuCode(entity.getSkuCode());
        form.setBarcode(entity.getBarcode());
        form.setSpecName(entity.getSpecName());
        form.setSpecValues(entity.getSpecValues() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(entity.getSpecValues()));
        form.setSaleUnit(entity.getSaleUnit());
        form.setProductType(entity.getProductType());
        form.setMarketPrice(entity.getMarketPrice());
        form.setStatus(entity.getStatus());
        form.setDefaultFlag(Boolean.TRUE.equals(entity.getDefaultFlag()));
        form.setSortOrder(entity.getSortOrder() == null ? 0 : entity.getSortOrder());
        return form;
    }

    /** 图片不经导入编辑，按现状回写即可保持主图与排序不变；内容角色由同步链不更新该列来保证。 */
    private ProductImageForm toImageForm(ProductImageEntity entity) {
        var form = new ProductImageForm();
        form.setImageId(entity.getId());
        form.setVersion(entity.getVersion());
        form.setFileKey(entity.getFileKey());
        form.setFileName(entity.getFileName());
        form.setFileSize(entity.getFileSize());
        form.setPrimaryFlag(Boolean.TRUE.equals(entity.getPrimaryFlag()));
        form.setSortOrder(entity.getSortOrder() == null ? 0 : entity.getSortOrder());
        return form;
    }

    // ------------------------------------------------------------------
    // 共享校验
    // ------------------------------------------------------------------

    private Map<String, ProductCategoryEntity> loadCategories(List<ProductImportRow> rows) {
        var codes = rows.stream().map(r -> trim(r.getCategoryCode())).filter(Objects::nonNull).distinct().toList();
        return codes.isEmpty() ? Map.of() : categories.selectList(new LambdaQueryWrapper<ProductCategoryEntity>()
                .in(ProductCategoryEntity::getCategoryCode, codes).eq(ProductCategoryEntity::getDeleted, false))
                .stream().collect(Collectors.toMap(ProductCategoryEntity::getCategoryCode, c -> c, (a, b) -> a));
    }

    private Map<String, ProductTagEntity> loadTags(List<ProductImportRow> rows) {
        var codes = rows.stream().flatMap(r -> splitTags(r.getTagCodes()).stream()).distinct().toList();
        return codes.isEmpty() ? Map.of() : tags.selectList(new LambdaQueryWrapper<ProductTagEntity>()
                .in(ProductTagEntity::getTagCode, codes).eq(ProductTagEntity::getDeleted, false))
                .stream().collect(Collectors.toMap(ProductTagEntity::getTagCode, t -> t, (a, b) -> a));
    }

    private void validateCreateRow(ProductImportRow row, Map<String, ProductCategoryEntity> categoryMap,
                                   Map<String, ProductTagEntity> tagMap, Map<String, ProductUomEntity> unitMap,
                                   ProductImportResultVO result) {
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
        validateSharedCells(row, categoryMap, tagMap, result, false, unitMap);
    }

    private void validateUpdateRow(ProductImportRow row, Map<String, ProductCategoryEntity> categoryMap,
                                   Map<String, ProductTagEntity> tagMap, ProductImportResultVO result) {
        var n = row.getRowNumber();
        var key = trim(row.getSpuCode());
        required(result, n, key, "模板版本", row.getTemplateVersion());
        if (trim(row.getTemplateVersion()) != null && !TEMPLATE_VERSION.equals(trim(row.getTemplateVersion())))
            addError(result, n, key, "模板版本", "TEMPLATE_VERSION", "模板版本不受支持，请重新下载模板");
        required(result, n, key, "SPU ID", row.getSpuId());
        required(result, n, key, "SPU版本", row.getSpuVersion());
        positiveId(result, n, key, "SPU ID", row.getSpuId());
        positiveId(result, n, key, "SKU ID", row.getSkuId());
        nonNegative(result, n, key, "SPU版本", row.getSpuVersion());
        nonNegative(result, n, key, "SKU版本", row.getSkuVersion());
        validateSharedCells(row, categoryMap, tagMap, result, true, Map.of());
        rejectClearMarker(result, row);
    }

    /** 清空标记只对可空属性有效；名称、编码、规格、单位与默认标记没有「清成空」的语义，出现标记必须显性拒绝。 */
    private void rejectClearMarker(ProductImportResultVO result, ProductImportRow row) {
        var key = trim(row.getSpuCode());
        for (var column : CLEAR_GUARDED)
            if (CLEAR_TOKEN.equals(trim(column.getValue().apply(row))))
                addError(result, row.getRowNumber(), key, column.getKey(), "CLEAR_NOT_ALLOWED",
                        "「" + CLEAR_TOKEN + "」只能用于别名、助记码、品牌、产地、标签编码与条码");
    }

    /** 两模式共用的取值与长度校验；必填口径由各模式的调用方决定。 */
    private void validateSharedCells(ProductImportRow row, Map<String, ProductCategoryEntity> categoryMap,
                                     Map<String, ProductTagEntity> tagMap, ProductImportResultVO result,
                                     boolean allowClearToken, Map<String, ProductUomEntity> unitMap) {
        var n = row.getRowNumber();
        var key = trim(row.getSpuCode());
        length(result, n, key, "SPU编码", row.getSpuCode(), 64);
        length(result, n, key, "商品名称", row.getSpuName(), 150);
        length(result, n, key, "SKU编码", row.getSkuCode(), 64);
        length(result, n, key, "规格名称", row.getSpecName(), 150);
        length(result, n, key, "销售单位", row.getSaleUnit(), 32);
        // 上限与 ProductSpuAddForm 的 @Size 及库里 VARCHAR 同数值：导入不走 @Valid，
        // 缺了这几列的逐行上限，超长要等整批写库才被数据库拒掉，定位不到是哪个单元格。
        length(result, n, key, "别名", row.getAlias(), 150);
        length(result, n, key, "助记码", row.getMnemonicCode(), 64);
        length(result, n, key, "品牌", row.getBrandName(), 100);
        length(result, n, key, "产地", row.getOrigin(), 100);

        var categoryCode = trim(row.getCategoryCode());
        if (categoryCode != null && !categoryMap.containsKey(categoryCode))
            addError(result, n, key, "分类编码", "CATEGORY_NOT_FOUND", "分类编码不存在");
        else if (categoryCode != null && !"ENABLED".equals(categoryMap.get(categoryCode).getStatus()))
            addError(result, n, key, "分类编码", "CATEGORY_DISABLED", "分类已停用，不能作为新商品分类");
        // 层级规则与写入路径的 ProductCategoryService.requireSelectableCategory 同口径；前置到逐行校验，
        // 免得填了一 / 二级分类要等整批写入才收到 CATEGORY_PARENT_INVALID，看不出是哪个单元格的问题
        else if (categoryCode != null && !Integer.valueOf(3).equals(categoryMap.get(categoryCode).getLevel()))
            addError(result, n, key, "分类编码", "CATEGORY_LEVEL_INVALID", "商品只能绑定三级分类，请改填该分类下的三级分类");

        enumValue(result, n, key, "商品上下架", row.getSpuStatus(), SHELF);
        enumValue(result, n, key, "SKU上下架", row.getSkuStatus(), SHELF);
        enumValue(result, n, key, "商品类型", row.getProductType(), PRODUCT_TYPE);
        if (trim(row.getStorageMethod()) != null) enumValue(result, n, key, "储存方式", row.getStorageMethod(), STORAGE);
        decimal(result, n, key, "市场价", row.getMarketPrice(), false);
        integer(result, n, key, "保质期天数", row.getShelfLifeDays(), 0, 36500);
        integer(result, n, key, "排序", row.getSortOrder(), 0, Integer.MAX_VALUE);
        // 单位与标签的「存在 + 启用」只在 CREATE 模式逐行预判。
        // UPDATE 不预判：写入口劲只复核**变动过**的单位（ProductSpuService.update 的 changedUnits）
        // 与**新挂**的标签（assertNewBindings 允许已绑的停用标签原样保留），
        // 在这里照抄「必须在字典且启用」会把合法的历史值误拒成单元格错误。
        if (!allowClearToken) {
            var unit = trim(row.getSaleUnit());
            if (unit != null) {
                var uom = unitMap.get(unit);
                if (uom == null)
                    addError(result, n, key, "销售单位", "UOM_NOT_USABLE", "计量单位不存在，请先在单位字典里维护");
                else if (!"ENABLED".equals(uom.getStatus()))
                    addError(result, n, key, "销售单位", "UOM_NOT_USABLE", "计量单位已停用，请改选启用的单位");
            }
        }
        validateTagCodes(result, row, tagMap, allowClearToken);
    }

    /** CREATE 模式的单位字典：与 {@code ProductUomService.assertUsable} 同判据（活动行 + ENABLED）。 */
    private Map<String, ProductUomEntity> loadUnits(List<ProductImportRow> rows) {
        var names = rows.stream().map(r -> trim(r.getSaleUnit())).filter(Objects::nonNull).distinct().toList();
        return names.isEmpty() ? Map.of() : units.selectList(new LambdaQueryWrapper<ProductUomEntity>()
                .in(ProductUomEntity::getName, names).eq(ProductUomEntity::getDeleted, false))
                .stream().collect(Collectors.toMap(ProductUomEntity::getName, u -> u, (a, b) -> a));
    }

    /**
     * 标签编码列取值：UPDATE 允许显式清空标记（表示解除全部标签），CREATE 没有「原值」可清，标记按非法编码处理。
     *
     * <p>CREATE 同时复核启用态：{@code loadTags} 只按 {@code deleted=FALSE} 取行、不看 {@code status}，
     * 缺了这一步，填了停用标签要等整批写库被 {@code assertUsable} 抛 40028 才知道是哪一行。
     */
    private void validateTagCodes(ProductImportResultVO result, ProductImportRow row,
                                  Map<String, ProductTagEntity> tagMap, boolean allowClear) {
        var cell = trim(row.getTagCodes());
        if (cell == null || (allowClear && CLEAR_TOKEN.equals(cell))) return;
        for (var tagCode : splitTags(cell)) {
            var tag = tagMap.get(tagCode);
            if (tag == null) {
                addError(result, row.getRowNumber(), trim(row.getSpuCode()), "标签编码", "TAG_NOT_FOUND", "标签编码不存在：" + tagCode);
            } else if (!allowClear && !"ENABLED".equals(tag.getStatus())) {
                addError(result, row.getRowNumber(), trim(row.getSpuCode()), "标签编码", "TAG_NOT_USABLE",
                        "标签已停用，请改选启用的标签：" + tagCode);
            }
        }
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

    private ProductSpuAddForm toCreateForm(List<ProductImportRow> rows, Map<String, ProductCategoryEntity> categoryMap,
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
        return v != null && TRUTHY.contains(v.toUpperCase(Locale.ROOT));
    }

    private List<String> splitTags(String value) {
        var v = trim(value);
        if (v == null) return List.of();
        return java.util.Arrays.stream(v.split("[,，]")).map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
    }

    private String keep(String requested, String current) {
        return requested == null ? current : requested;
    }

    /** 可空属性的更新取值：空白=保持原值，清空标记=NULL，其余=覆盖。 */
    private String keepOrClear(String requested, String current) {
        return CLEAR_TOKEN.equals(requested) ? null : keep(requested, current);
    }

    private String string(Integer value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long parseLong(String value) {
        var v = trim(value);
        if (v == null) return null;
        try {
            return Long.valueOf(v);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Integer parseInteger(String value) {
        var v = trim(value);
        if (v == null) return null;
        try {
            return Integer.valueOf(v);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void positiveId(ProductImportResultVO r, int n, String key, String column, String value) {
        var v = trim(value);
        if (v != null && (parseLong(v) == null || parseLong(v) <= 0)) addError(r, n, key, column, "ID_INVALID", column + "必须是正整数");
    }

    private void nonNegative(ProductImportResultVO r, int n, String key, String column, String value) {
        var v = trim(value);
        if (v == null) return;
        var number = parseInteger(v);
        if (number == null || number < 0) addError(r, n, key, column, "INT_INVALID", column + "必须是 0 以上的整数");
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

    private record UpdateAssembly(List<ProductSpuUpdateForm> forms, List<List<ProductImportRow>> groups) {
    }
}
