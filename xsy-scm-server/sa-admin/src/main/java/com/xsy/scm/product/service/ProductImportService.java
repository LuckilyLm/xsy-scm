package com.xsy.scm.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.constant.ScmEnableStatusEnum;
import com.xsy.scm.common.constant.ScmProductTypeEnum;
import com.xsy.scm.common.constant.ScmShelfStatusEnum;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.product.constant.ScmStorageMethodEnum;
import com.xsy.scm.product.dao.ProductCategoryDao;
import com.xsy.scm.product.dao.ProductImageDao;
import com.xsy.scm.product.dao.ProductSkuDao;
import com.xsy.scm.product.dao.ProductSpuDao;
import com.xsy.scm.product.dao.ProductTagDao;
import com.xsy.scm.product.dao.ProductUomDao;
import com.xsy.scm.product.domain.dto.ProductImportRow;
import com.xsy.scm.product.domain.entity.ProductCategoryEntity;
import com.xsy.scm.product.domain.entity.ProductImageEntity;
import com.xsy.scm.product.domain.entity.ProductSkuEntity;
import com.xsy.scm.product.domain.entity.ProductSpuEntity;
import com.xsy.scm.product.domain.entity.ProductTagEntity;
import com.xsy.scm.product.domain.entity.ProductUomEntity;
import com.xsy.scm.product.domain.form.ProductImageForm;
import com.xsy.scm.product.domain.form.ProductSkuForm;
import com.xsy.scm.product.domain.form.ProductSpuAddForm;
import com.xsy.scm.product.domain.form.ProductSpuUpdateForm;
import com.xsy.scm.product.domain.vo.ProductImportResultVO;
import com.xsy.scm.product.manager.ProductAggregateValidator;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

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
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.xsy.scm.product.service.ProductImportValueRules.addError;
import static com.xsy.scm.product.service.ProductImportValueRules.decimal;
import static com.xsy.scm.product.service.ProductImportValueRules.enumValue;
import static com.xsy.scm.product.service.ProductImportValueRules.integer;
import static com.xsy.scm.product.service.ProductImportValueRules.length;
import static com.xsy.scm.product.service.ProductImportValueRules.nonNegative;
import static com.xsy.scm.product.service.ProductImportValueRules.parseInteger;
import static com.xsy.scm.product.service.ProductImportValueRules.parseLong;
import static com.xsy.scm.product.service.ProductImportValueRules.positiveId;
import static com.xsy.scm.product.service.ProductImportValueRules.required;
import static com.xsy.scm.product.service.ProductImportValueRules.same;
import static com.xsy.scm.product.service.ProductImportValueRules.trim;

/**
 * 商品 Excel 导入先校验整批数据，再统一写入。新增和更新模式必须明确选择；更新时空单元格表示保留原值，需要清空时使用指定标记。
 */
@Service
@RequiredArgsConstructor
public class ProductImportService {
    public static final String TEMPLATE_VERSION = "1.0";
    /**
     * 新建商品规格的默认销售单位：Excel 未填或 JSON 未传时按斤录入，显式空白仍按各自校验拒绝。
     */
    public static final String DEFAULT_SALE_UNIT = "斤";
    /**
     * UPDATE 模式的显式清空标记：空白=保持原值，因此清除既存值必须另有记号。仅 UPDATE 解释该记号。
     */
    public static final String CLEAR_TOKEN = "(清空)";
    /**
     * 这些列没有枚举/数值校验兜底，标记出现在这里必须拒绝，否则会被当成普通值写进名称、单位或默认标记。
     */
    private static final List<Map.Entry<String, Function<ProductImportRow, String>>> CLEAR_GUARDED = List.of(
        Map.entry("商品名称", ProductImportRow::getSpuName), Map.entry("商品规格编码", ProductImportRow::getSkuCode),
        Map.entry("商品规格名称", ProductImportRow::getSpecName), Map.entry("销售单位", ProductImportRow::getSaleUnit),
        Map.entry("默认商品规格", ProductImportRow::getDefaultFlag));
    static final int MAX_ROWS = 20000;
    private static final int MAX_PRODUCTS = 5000;
    private static final int MAX_SKUS_PER_PRODUCT = 200;
    private static final Set<
        String> SHELF = Set.of(ScmShelfStatusEnum.ON_SHELF.name(), ScmShelfStatusEnum.OFF_SHELF.name());
    private static final Set<
        String> PRODUCT_TYPE = Set.of(ScmProductTypeEnum.STANDARD.name(), ScmProductTypeEnum.NON_STANDARD.name());
    private static final Set<String> STORAGE = Set.of(ScmStorageMethodEnum.AMBIENT.name(),
        ScmStorageMethodEnum.CHILLED.name(), ScmStorageMethodEnum.FROZEN.name());
    private static final Set<String> TRUTHY = Set.of("是", "Y", "YES", "TRUE", "1");

    private final ProductCategoryDao productCategoryDao;
    private final ProductTagDao productTagDao;
    private final ProductUomDao productUomDao;
    private final ProductSpuDao productSpuDao;
    private final ProductSkuDao productSkuDao;
    private final ProductImageDao productImageDao;
    private final ProductTagService productTagService;
    private final ProductImportWorkbookSupport productImportWorkbookSupport;
    private final ProductImportWriteService productImportWriteService;

    /**
     * CREATE 整批新增；UPDATE 按定位键改写既存商品，两者语义不可混用。
     */
    public enum ImportMode {
        CREATE,
        UPDATE
    }

    /** 页面不再传模式时，由模板表头决定；见 {@link ProductImportWorkbookSupport#detectMode}。 */
    public ImportMode detectMode(byte[] bytes) {
        return productImportWorkbookSupport.detectMode(bytes);
    }

    public ProductImportResultVO importFile(MultipartFile file, ImportMode mode) throws Exception {
        var result = new ProductImportResultVO();
        result.setMode(mode.name());
        var rows = productImportWorkbookSupport.readRows(file.getBytes(), mode, result);
        if (result.getTotalErrors() > 0)
            return result;
        if (mode == ImportMode.CREATE) {
            var assembled = assembleCreates(rows, result);
            if (result.getTotalErrors() > 0)
                return result;
            try {
                var ids = productImportWriteService.writeAll(assembled.forms());
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
        if (result.getTotalErrors() > 0)
            return result;
        try {
            result.setUpdatedProducts(productImportWriteService.writeUpdates(assembled.forms()));
            assembled.forms().forEach(form -> result.getSpuIds().add(form.getSpuId()));
            return result;
        } catch (ProductImportWriteService.ImportProductException exception) {
            addWriteError(result, assembled.groups().get(exception.getProductIndex()), exception.getCause());
            return result;
        }
    }

    private void addWriteError(ProductImportResultVO result, List<ProductImportRow> group, Throwable cause) {
        var code = cause instanceof ScmBusinessException business
            ? String.valueOf(business.getErrorCode().getCode())
            : "WRITE_CONFLICT";
        var message = cause instanceof ScmBusinessException business
            ? business.getErrorCode().getMsg()
            : "商品数据冲突或超出范围，整批已回滚";
        for (var row : group)
            addError(result, row.getRowNumber(), trim(row.getSpuCode()), "商品", code, message + "；整批已回滚");
    }

    public byte[] buildTemplate(ImportMode mode) throws IOException {
        return productImportWorkbookSupport.buildTemplate(mode);
    }

    private Assembly assembleCreates(List<ProductImportRow> rows, ProductImportResultVO result) {
        result.setTotalRows(rows.size());
        if (rows.isEmpty())
            addError(result, 0, null, "文件", "FILE_EMPTY", "导入文件没有数据行");
        if (rows.size() > MAX_ROWS)
            addError(result, 0, null, "文件", "ROW_LIMIT", "数据行不能超过 " + MAX_ROWS + " 行");

        var categoryMap = loadCategories(rows);
        var tagMap = loadTags(rows);
        var unitMap = loadUnits(rows);

        var groups = new LinkedHashMap<String, List<ProductImportRow>>();
        for (var row : rows)
            validateCreateRow(row, categoryMap, tagMap, unitMap, result);
        for (var row : rows) {
            var key = trim(row.getSpuCode());
            // 无分组标记时每行创建独立商品；多 SKU 属于同一 SPU 时填写相同的导入分组标记。
            groups.computeIfAbsent(key == null ? "ROW:" + row.getRowNumber() : "GROUP:" + key, k -> new ArrayList<>())
                .add(row);
        }
        result.setTotalProducts(groups.size());
        if (groups.size() > MAX_PRODUCTS) {
            addError(result, 0, null, "商品编码", "PRODUCT_LIMIT", "商品数不能超过 " + MAX_PRODUCTS + " 个");
        }

        var forms = new ArrayList<ProductSpuAddForm>();
        var orderedGroups = new ArrayList<List<ProductImportRow>>();
        for (var entry : groups.entrySet()) {
            var group = entry.getValue();
            orderedGroups.add(group);
            if (group.size() > MAX_SKUS_PER_PRODUCT)
                addError(result, group.getFirst().getRowNumber(), entry.getKey(), "商品规格编码", "SKU_LIMIT",
                    "单个商品规格不能超过 " + MAX_SKUS_PER_PRODUCT + " 个");
            validateGroup(entry.getKey(), group, result);
            if (result.getTotalErrors() == 0)
                forms.add(toCreateForm(group, categoryMap, tagMap));
        }
        return new Assembly(forms, orderedGroups);
    }

    /**
     * 更新模式先按商品ID读回现状，再用 Excel 的非空单元格覆盖：空白保持原值、未列出的 SKU 保持原样、 Excel 之外的字段（详情描述、税率、图片等）原值回写，不会把「只改一个市场价」变成整单替换。
     */
    private UpdateAssembly assembleUpdates(List<ProductImportRow> rows, ProductImportResultVO result) {
        result.setTotalRows(rows.size());
        if (rows.isEmpty())
            addError(result, 0, null, "文件", "FILE_EMPTY", "导入文件没有数据行");
        if (rows.size() > MAX_ROWS)
            addError(result, 0, null, "文件", "ROW_LIMIT", "数据行不能超过 " + MAX_ROWS + " 行");

        var categoryMap = loadCategories(rows);
        var tagMap = loadTags(rows);
        for (var row : rows)
            validateUpdateRow(row, categoryMap, tagMap, result);

        var spuIds = rows.stream().map(row -> parseLong(row.getSpuId())).filter(Objects::nonNull).distinct().toList();
        // 一次批量读现状，避免逐行查库；软删行读不到即按「商品不存在」报错。
        var spuMap = spuIds.isEmpty()
            ? Map.<Long, ProductSpuEntity>of()
            : productSpuDao
                .selectList(new LambdaQueryWrapper<ProductSpuEntity>().in(ProductSpuEntity::getId, spuIds)
                    .eq(ProductSpuEntity::getDeleted, false))
                .stream().collect(Collectors.toMap(ProductSpuEntity::getId, s -> s, (a, b) -> a));
        var skuMap = spuIds.isEmpty()
            ? Map.<Long, List<ProductSkuEntity>>of()
            : productSkuDao
                .selectList(new LambdaQueryWrapper<ProductSkuEntity>().in(ProductSkuEntity::getSpuId, spuIds)
                    .eq(ProductSkuEntity::getDeleted, false))
                .stream().collect(Collectors.groupingBy(ProductSkuEntity::getSpuId));
        var imageMap = spuIds.isEmpty()
            ? Map.<Long, List<ProductImageEntity>>of()
            : productImageDao
                .selectList(new LambdaQueryWrapper<ProductImageEntity>().in(ProductImageEntity::getSpuId, spuIds)
                    .eq(ProductImageEntity::getDeleted, false))
                .stream().collect(Collectors.groupingBy(ProductImageEntity::getSpuId));
        var tagIdMap = spuIds.isEmpty()
            ? Map.<Long, List<Long>>of()
            : productTagService.bySpuIds(spuIds).entrySet().stream().collect(
                Collectors.toMap(Map.Entry::getKey, e -> e.getValue().stream().map(t -> t.getTagId()).toList()));

        var groups = new LinkedHashMap<String, List<ProductImportRow>>();
        for (var row : rows) {
            var key = trim(row.getSpuId());
            if (key != null)
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }
        result.setTotalProducts(groups.size());
        if (groups.size() > MAX_PRODUCTS) {
            addError(result, 0, null, "商品ID", "PRODUCT_LIMIT", "商品数不能超过 " + MAX_PRODUCTS + " 个");
        }

        var forms = new ArrayList<ProductSpuUpdateForm>();
        var orderedGroups = new ArrayList<List<ProductImportRow>>();
        for (var entry : groups.entrySet()) {
            var group = entry.getValue();
            orderedGroups.add(group);
            if (group.size() > MAX_SKUS_PER_PRODUCT)
                addError(result, group.getFirst().getRowNumber(), trim(group.getFirst().getSpuCode()), "商品规格ID",
                    "SKU_LIMIT", "单个商品规格不能超过 " + MAX_SKUS_PER_PRODUCT + " 个");
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
        var firstRowNumber = first.getRowNumber();
        var spu = spuId == null ? null : spuMap.get(spuId);
        if (spu == null) {
            addError(result, firstRowNumber, trim(first.getSpuCode()), "商品ID", "PRODUCT_NOT_FOUND",
                "商品不存在或已删除，请重新导出更新模板");
            return;
        }
        // 版本在此处只做友好提示，真正拦截在 ProductSpuService.update 的事务内
        if (!Objects.equals(parseInteger(first.getSpuVersion()), spu.getVersion())) {
            for (var row : rows)
                addError(result, row.getRowNumber(), trim(row.getSpuCode()), "商品版本", "VERSION_CONFLICT",
                    "商品版本已变化（当前 " + spu.getVersion() + "），本次整批拒绝，请重新导出");
            return;
        }
        if (trim(first.getSpuCode()) != null
            && !Objects.equals(ProductAggregateValidator.normalizeCode(first.getSpuCode()), spu.getSpuCode()))
            addError(result, firstRowNumber, trim(first.getSpuCode()), "商品编码", "FIELD_LOCKED", "商品编码不能通过导入修改，请走商品编辑");

        var existingSkus = skuMap.getOrDefault(spuId, List.of()).stream()
            .collect(Collectors.toMap(ProductSkuEntity::getId, s -> s, (a, b) -> a));
        var seenSkuIds = new java.util.HashSet<Long>();
        var skuCodes = existingSkus.values().stream().map(ProductSkuEntity::getSkuCode)
            .collect(Collectors.toCollection(java.util.HashSet::new));
        int defaults = 0;
        for (var row : rows) {
            int rowNumber = row.getRowNumber();
            same(result, rowNumber, trim(row.getSpuCode()), "商品名称", first.getSpuName(), row.getSpuName());
            same(result, rowNumber, trim(row.getSpuCode()), "分类编码", first.getCategoryCode(), row.getCategoryCode());
            same(result, rowNumber, trim(row.getSpuCode()), "商品状态", first.getSpuStatus(), row.getSpuStatus());
            same(result, rowNumber, trim(row.getSpuCode()), "储存方式", first.getStorageMethod(), row.getStorageMethod());
            same(result, rowNumber, trim(row.getSpuCode()), "标签编码", first.getTagCodes(), row.getTagCodes());
            same(result, rowNumber, trim(row.getSpuCode()), "商品版本", first.getSpuVersion(), row.getSpuVersion());
            var skuId = parseLong(row.getSkuId());
            if (skuId != null) {
                var existing = existingSkus.get(skuId);
                if (existing == null || !seenSkuIds.add(skuId)) {
                    addError(result, rowNumber, trim(row.getSkuCode()), "商品规格ID", "SKU_NOT_OWNED", "商品规格不属于该商品或重复出现");
                    continue;
                }
                if (!Objects.equals(parseInteger(row.getSkuVersion()), existing.getVersion()))
                    addError(result, rowNumber, trim(row.getSkuCode()), "商品规格版本", "VERSION_CONFLICT",
                        "商品规格版本已变化（当前 " + existing.getVersion() + "），本次整批拒绝，请重新导出");
                if (trim(row.getSkuCode()) != null && !Objects
                    .equals(ProductAggregateValidator.normalizeCode(row.getSkuCode()), existing.getSkuCode()))
                    addError(result, rowNumber, trim(row.getSkuCode()), "商品规格编码", "FIELD_LOCKED",
                        "商品规格编码不能通过导入修改，请走商品编辑");
                if (isTruthy(row.getDefaultFlag()))
                    defaults++;
                else if (Boolean.TRUE.equals(existing.getDefaultFlag()) && trim(row.getDefaultFlag()) == null)
                    defaults++;
                continue;
            }
            // 新增 SKU 与新增导入的必填项相同；新增行不应填 SKU 版本
            if (trim(row.getSkuVersion()) != null)
                addError(result, rowNumber, trim(row.getSkuCode()), "商品规格版本", "NEW_SKU_VERSION", "新增商品规格不应填写商品规格版本");

            required(result, rowNumber, trim(row.getSkuCode()), "商品规格名称", row.getSpecName());
            required(result, rowNumber, trim(row.getSkuCode()), "商品类型", row.getProductType());
            required(result, rowNumber, trim(row.getSkuCode()), "市场价", row.getMarketPrice());
            required(result, rowNumber, trim(row.getSkuCode()), "商品规格状态", row.getSkuStatus());
            required(result, rowNumber, trim(row.getSkuCode()), "默认商品规格", row.getDefaultFlag());
            var skuCode = trim(row.getSkuCode());
            if (skuCode != null && !skuCodes.add(ProductAggregateValidator.normalizeCode(skuCode)))
                addError(result, rowNumber, trim(row.getSkuCode()), "商品规格编码", "SKU_CODE_DUPLICATE", "同一商品内商品规格编码不能重复");
            if (isTruthy(row.getDefaultFlag()))
                defaults++;
        }
        // 未列入 Excel 的既存 SKU 会被原样保留，其默认标记同样有效，不能按「文件里没写」判成没有默认
        for (var existing : existingSkus.values())
            if (!seenSkuIds.contains(existing.getId()) && Boolean.TRUE.equals(existing.getDefaultFlag()))
                defaults++;
        if (defaults == 0)
            addError(result, firstRowNumber, trim(first.getSpuCode()), "默认商品规格", "DEFAULT_SKU_INVALID",
                "商品必须保留一个默认商品规格");
        if (defaults > 1)
            addError(result, firstRowNumber, trim(first.getSpuCode()), "默认商品规格", "DEFAULT_SKU_INVALID", "默认商品规格只能有一个");
    }

    private ProductSpuUpdateForm toUpdateForm(List<ProductImportRow> rows, ProductSpuEntity spu,
        List<ProductSkuEntity> existingSkus, List<ProductImageEntity> existingImages, List<Long> existingTagIds,
        Map<String, ProductCategoryEntity> categoryMap, Map<String, ProductTagEntity> tagMap) {
        var first = rows.getFirst();
        var form = new ProductSpuUpdateForm();
        form.setSpuId(spu.getId());
        form.setVersion(spu.getVersion());
        // 编码不能通过导入修改，一律按库内值回写
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
        form.setTagIds(CLEAR_TOKEN.equals(tagCodes)
            ? List.of()
            : tagCodes == null
                ? existingTagIds
                : splitTags(tagCodes).stream().map(c -> tagMap.get(c).getId()).distinct().collect(Collectors.toList()));
        form.setImages(existingImages.stream().map(this::toImageForm).collect(Collectors.toList()));
        form.setSkuList(mergeSkus(rows, existingSkus));
        return form;
    }

    /**
     * 既存 SKU 按 skuId 覆盖，未列出的原样保留，空白列保持原值。
     */
    private List<ProductSkuForm> mergeSkus(List<ProductImportRow> rows, List<ProductSkuEntity> existingSkus) {
        var merged = existingSkus.stream()
            .collect(Collectors.toMap(ProductSkuEntity::getId, this::toSkuForm, (a, b) -> a, LinkedHashMap::new));
        var created = new ArrayList<ProductSkuForm>();
        for (var row : rows) {
            var skuId = parseLong(row.getSkuId());
            if (skuId != null) {
                applySkuOverride(merged.get(skuId), row);
                continue;
            }
            var form = new ProductSkuForm();
            form.setSkuCode(row.getSkuCode());
            form.setSaleUnit(saleUnitOf(row));
            form.setSpecName(trim(row.getSpecName()));
            // 新增 SKU 与新增导入的规格值写法一致
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
        if (form == null)
            return;
        var barcode = trim(row.getBarcode());
        if (CLEAR_TOKEN.equals(barcode))
            form.setBarcode(null);
        else if (barcode != null)
            form.setBarcode(barcode);
        var specName = trim(row.getSpecName());
        // 只改规格名称，不动既存规格属性 JSON；属性编辑属于商品编辑页职责
        if (specName != null && form.getSkuId() != null)
            form.setSpecName(specName);
        var saleUnit = trim(row.getSaleUnit());
        if (saleUnit != null)
            form.setSaleUnit(saleUnit);
        var productType = trim(row.getProductType());
        if (productType != null)
            form.setProductType(productType);
        var marketPrice = trim(row.getMarketPrice());
        if (marketPrice != null)
            form.setMarketPrice(new BigDecimal(marketPrice).setScale(4, RoundingMode.HALF_UP));
        var skuStatus = trim(row.getSkuStatus());
        if (skuStatus != null)
            form.setStatus(skuStatus);
        var defaultFlag = trim(row.getDefaultFlag());
        if (defaultFlag != null)
            form.setDefaultFlag(isTruthy(defaultFlag));
        var sortOrder = trim(row.getSortOrder());
        if (sortOrder != null)
            form.setSortOrder(Integer.parseInt(sortOrder));
    }

    private ProductSkuForm toSkuForm(ProductSkuEntity entity) {
        var form = new ProductSkuForm();
        form.setSkuId(entity.getId());
        form.setVersion(entity.getVersion());
        form.setSkuCode(entity.getSkuCode());
        form.setBarcode(entity.getBarcode());
        form.setSpecName(entity.getSpecName());
        form.setSpecValues(
            entity.getSpecValues() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(entity.getSpecValues()));
        form.setSaleUnit(entity.getSaleUnit());
        form.setMallMinQuantity(entity.getMallMinQuantity());
        form.setMallQuantityStep(entity.getMallQuantityStep());
        form.setProductType(entity.getProductType());
        form.setMarketPrice(entity.getMarketPrice());
        form.setStatus(entity.getStatus());
        form.setDefaultFlag(Boolean.TRUE.equals(entity.getDefaultFlag()));
        form.setSortOrder(entity.getSortOrder() == null ? 0 : entity.getSortOrder());
        return form;
    }

    /**
     * 图片不经导入编辑，按现状回写即可保持主图和排序；image_type 由同步逻辑不更新该列来保证。
     */
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

    private Map<String, ProductCategoryEntity> loadCategories(List<ProductImportRow> rows) {
        var codes = rows.stream().map(row -> trim(row.getCategoryCode())).filter(Objects::nonNull).distinct().toList();
        return codes.isEmpty()
            ? Map.of()
            : productCategoryDao
                .selectList(new LambdaQueryWrapper<ProductCategoryEntity>()
                    .in(ProductCategoryEntity::getCategoryCode, codes).eq(ProductCategoryEntity::getDeleted, false))
                .stream().collect(Collectors.toMap(ProductCategoryEntity::getCategoryCode, c -> c, (a, b) -> a));
    }

    private Map<String, ProductTagEntity> loadTags(List<ProductImportRow> rows) {
        var codes = rows.stream().flatMap(row -> splitTags(row.getTagCodes()).stream()).distinct().toList();
        return codes.isEmpty()
            ? Map.of()
            : productTagDao
                .selectList(new LambdaQueryWrapper<ProductTagEntity>().in(ProductTagEntity::getTagCode, codes)
                    .eq(ProductTagEntity::getDeleted, false))
                .stream().collect(Collectors.toMap(ProductTagEntity::getTagCode, t -> t, (a, b) -> a));
    }

    private void validateCreateRow(ProductImportRow row, Map<String, ProductCategoryEntity> categoryMap,
        Map<String, ProductTagEntity> tagMap, Map<String, ProductUomEntity> unitMap, ProductImportResultVO result) {
        var rowNumber = row.getRowNumber();
        var key = trim(row.getSpuCode());
        required(result, rowNumber, key, "模板版本", row.getTemplateVersion());
        if (trim(row.getTemplateVersion()) != null && !TEMPLATE_VERSION.equals(trim(row.getTemplateVersion())))
            addError(result, rowNumber, key, "模板版本", "TEMPLATE_VERSION", "模板版本不受支持，请重新下载模板");
        required(result, rowNumber, key, "商品名称", row.getSpuName());
        required(result, rowNumber, key, "分类编码", row.getCategoryCode());
        required(result, rowNumber, key, "商品状态", row.getSpuStatus());
        required(result, rowNumber, key, "商品规格名称", row.getSpecName());
        required(result, rowNumber, key, "商品类型", row.getProductType());
        required(result, rowNumber, key, "市场价", row.getMarketPrice());
        required(result, rowNumber, key, "商品规格状态", row.getSkuStatus());
        required(result, rowNumber, key, "默认商品规格", row.getDefaultFlag());
        validateSharedCells(row, categoryMap, tagMap, result, false, unitMap);
        // 未填销售单位时按默认单位落库；显式填写的单位已由共用校验检查，空值在这里检查默认单位。
        if (trim(row.getSaleUnit()) == null)
            requireUsableUnit(result, row, unitMap, DEFAULT_SALE_UNIT);
    }

    private void validateUpdateRow(ProductImportRow row, Map<String, ProductCategoryEntity> categoryMap,
        Map<String, ProductTagEntity> tagMap, ProductImportResultVO result) {
        var rowNumber = row.getRowNumber();
        var key = trim(row.getSpuCode());
        required(result, rowNumber, key, "模板版本", row.getTemplateVersion());
        if (trim(row.getTemplateVersion()) != null && !TEMPLATE_VERSION.equals(trim(row.getTemplateVersion())))
            addError(result, rowNumber, key, "模板版本", "TEMPLATE_VERSION", "模板版本不受支持，请重新下载模板");
        required(result, rowNumber, key, "商品ID", row.getSpuId());
        required(result, rowNumber, key, "商品版本", row.getSpuVersion());
        positiveId(result, rowNumber, key, "商品ID", row.getSpuId());
        positiveId(result, rowNumber, key, "商品规格ID", row.getSkuId());
        nonNegative(result, rowNumber, key, "商品版本", row.getSpuVersion());
        nonNegative(result, rowNumber, key, "商品规格版本", row.getSkuVersion());
        validateSharedCells(row, categoryMap, tagMap, result, true, Map.of());
        rejectClearMarker(result, row);
    }

    /**
     * 清空标记只对可空属性有效；名称、编码、规格、单位与默认标记没有「清成空」的语义，出现标记必须显性拒绝。
     */
    private void rejectClearMarker(ProductImportResultVO result, ProductImportRow row) {
        var key = trim(row.getSpuCode());
        for (var column : CLEAR_GUARDED)
            if (CLEAR_TOKEN.equals(trim(column.getValue().apply(row))))
                addError(result, row.getRowNumber(), key, column.getKey(), "CLEAR_NOT_ALLOWED",
                    "「" + CLEAR_TOKEN + "」只能用于别名、助记码、品牌、产地、标签编码与条码");
    }

    /**
     * 两种模式共用的取值与长度校验；必填项由各模式自行检查。
     */
    private void validateSharedCells(ProductImportRow row, Map<String, ProductCategoryEntity> categoryMap,
        Map<String, ProductTagEntity> tagMap, ProductImportResultVO result, boolean allowClearToken,
        Map<String, ProductUomEntity> unitMap) {
        var rowNumber = row.getRowNumber();
        var key = trim(row.getSpuCode());
        length(result, rowNumber, key, "商品编码", row.getSpuCode(), 64);
        length(result, rowNumber, key, "商品名称", row.getSpuName(), 150);
        length(result, rowNumber, key, "商品规格编码", row.getSkuCode(), 64);
        length(result, rowNumber, key, "商品规格名称", row.getSpecName(), 150);
        length(result, rowNumber, key, "销售单位", row.getSaleUnit(), 32);
        // 上限与 ProductSpuAddForm 的 @Size 及库里 VARCHAR 同数值：导入不走 @Valid，
        // 缺了这几列的逐行上限，超长要等整批写库才被数据库拒掉，定位不到是哪个单元格。
        length(result, rowNumber, key, "别名", row.getAlias(), 150);
        length(result, rowNumber, key, "助记码", row.getMnemonicCode(), 64);
        length(result, rowNumber, key, "品牌", row.getBrandName(), 100);
        length(result, rowNumber, key, "产地", row.getOrigin(), 100);

        var categoryCode = trim(row.getCategoryCode());
        if (categoryCode != null && !categoryMap.containsKey(categoryCode))
            addError(result, rowNumber, key, "分类编码", "CATEGORY_NOT_FOUND", "分类编码不存在");
        else if (categoryCode != null
            && !ScmEnableStatusEnum.ENABLED.name().equals(categoryMap.get(categoryCode).getStatus()))
            addError(result, rowNumber, key, "分类编码", "CATEGORY_DISABLED", "分类已停用，不能作为新商品分类");
        // 层级规则与 ProductCategoryService.requireSelectableCategory 一致；提前到逐行校验，
        // 免得填了一 / 二级分类要等整批写入才报 CATEGORY_PARENT_INVALID，看不出是哪个单元格的问题
        else if (categoryCode != null && !Integer.valueOf(3).equals(categoryMap.get(categoryCode).getLevel()))
            addError(result, rowNumber, key, "分类编码", "CATEGORY_LEVEL_INVALID", "商品只能绑定三级分类，请改填该分类下的三级分类");

        enumValue(result, rowNumber, key, "商品状态", row.getSpuStatus(), SHELF);
        enumValue(result, rowNumber, key, "商品规格状态", row.getSkuStatus(), SHELF);
        enumValue(result, rowNumber, key, "商品类型", row.getProductType(), PRODUCT_TYPE);
        if (trim(row.getStorageMethod()) != null)
            enumValue(result, rowNumber, key, "储存方式", row.getStorageMethod(), STORAGE);
        decimal(result, rowNumber, key, "市场价", row.getMarketPrice(), false);
        integer(result, rowNumber, key, "保质期天数", row.getShelfLifeDays(), 0, 36500);
        integer(result, rowNumber, key, "排序", row.getSortOrder(), 0, Integer.MAX_VALUE);
        // 单位与标签的「存在 + 启用」只在 CREATE 模式逐行检查。
        // UPDATE 不检查：写入路径只复核变动过的单位（ProductSpuService.update 的 changedUnits）
        // 和新挂的标签（assertNewBindings 允许已绑的停用标签保留），
        // 在这里照抄「必须在字典且启用」会把合法的历史值误判成单元格错误。
        if (!allowClearToken && trim(row.getSaleUnit()) != null)
            requireUsableUnit(result, row, unitMap, trim(row.getSaleUnit()));
        validateTagCodes(result, row, tagMap, allowClearToken);
    }

    private void requireUsableUnit(ProductImportResultVO result, ProductImportRow row,
        Map<String, ProductUomEntity> unitMap, String unit) {
        var uom = unitMap.get(unit);
        if (uom == null)
            addError(result, row.getRowNumber(), trim(row.getSpuCode()), "销售单位", "UOM_NOT_USABLE",
                "计量单位不存在，请先在单位字典里维护");
        else if (!ScmEnableStatusEnum.ENABLED.name().equals(uom.getStatus()))
            addError(result, row.getRowNumber(), trim(row.getSpuCode()), "销售单位", "UOM_NOT_USABLE", "计量单位已停用，请改选启用的单位");
    }

    /**
     * Excel 的销售单位为空时按默认单位落库；显式值保持原样交给字典校验。
     */
    private static String saleUnitOf(ProductImportRow row) {
        var unit = trim(row.getSaleUnit());
        return unit == null ? DEFAULT_SALE_UNIT : unit;
    }

    /**
     * CREATE 模式的单位字典：与 {@code ProductUomService.assertUsable} 同判据（活动行 + ENABLED）。
     */
    private Map<String, ProductUomEntity> loadUnits(List<ProductImportRow> rows) {
        var names = rows.stream().map(ProductImportService::saleUnitOf).distinct().toList();
        return names.isEmpty()
            ? Map.of()
            : productUomDao
                .selectList(new LambdaQueryWrapper<ProductUomEntity>().in(ProductUomEntity::getName, names)
                    .eq(ProductUomEntity::getDeleted, false))
                .stream().collect(Collectors.toMap(ProductUomEntity::getName, u -> u, (a, b) -> a));
    }

    /**
     * 商品更新允许清空全部标签；新建商品的标签编码必须有效且启用。导入时提前校验并定位错误行，不延迟到写库阶段。
     */
    private void validateTagCodes(ProductImportResultVO result, ProductImportRow row,
        Map<String, ProductTagEntity> tagMap, boolean allowClear) {
        var cell = trim(row.getTagCodes());
        if (cell == null || (allowClear && CLEAR_TOKEN.equals(cell)))
            return;
        for (var tagCode : splitTags(cell)) {
            var tag = tagMap.get(tagCode);
            if (tag == null) {
                addError(result, row.getRowNumber(), trim(row.getSpuCode()), "标签编码", "TAG_NOT_FOUND",
                    "标签编码不存在：" + tagCode);
            } else if (!allowClear && !ScmEnableStatusEnum.ENABLED.name().equals(tag.getStatus())) {
                addError(result, row.getRowNumber(), trim(row.getSpuCode()), "标签编码", "TAG_NOT_USABLE",
                    "标签已停用，请改选启用的标签：" + tagCode);
            }
        }
    }

    private void validateGroup(String spuCode, List<ProductImportRow> rows, ProductImportResultVO result) {
        var first = rows.getFirst();
        var specs = new java.util.HashSet<String>();
        int defaults = 0;
        for (var row : rows) {
            int rowNumber = row.getRowNumber();
            same(result, rowNumber, spuCode, "商品名称", first.getSpuName(), row.getSpuName());
            same(result, rowNumber, spuCode, "分类编码", first.getCategoryCode(), row.getCategoryCode());
            same(result, rowNumber, spuCode, "商品状态", first.getSpuStatus(), row.getSpuStatus());
            same(result, rowNumber, spuCode, "储存方式", first.getStorageMethod(), row.getStorageMethod());
            same(result, rowNumber, spuCode, "标签编码", first.getTagCodes(), row.getTagCodes());
            var spec = trim(row.getSpecName());
            if (spec != null && !specs.add(spec.trim().toLowerCase(Locale.ROOT)))
                addError(result, rowNumber, spuCode, "商品规格名称", "SKU_SPEC_DUPLICATE", "同一商品内规格名称不能重复");
            if (isTruthy(row.getDefaultFlag()))
                defaults++;
        }
        if (defaults == 0)
            addError(result, first.getRowNumber(), spuCode, "默认商品规格", "DEFAULT_SKU_INVALID", "商品必须且只能有一个默认商品规格");
        if (defaults > 1)
            addError(result, first.getRowNumber(), spuCode, "默认商品规格", "DEFAULT_SKU_INVALID", "默认商品规格只能有一个");
    }

    private ProductSpuAddForm toCreateForm(List<ProductImportRow> rows, Map<String, ProductCategoryEntity> categoryMap,
        Map<String, ProductTagEntity> tagMap) {
        var first = rows.getFirst();
        var form = new ProductSpuAddForm();
        // 导入分组标记不属于商品编码；真实编码在 ProductSpuService.add 发号。
        form.setName(trim(first.getSpuName()));
        form.setAlias(trim(first.getAlias()));
        form.setCategoryId(categoryMap.get(trim(first.getCategoryCode())).getId());
        form.setStatus(trim(first.getSpuStatus()));
        form.setMnemonicCode(trim(first.getMnemonicCode()));
        form.setBrandName(trim(first.getBrandName()));
        form.setOrigin(trim(first.getOrigin()));
        form.setStorageMethod(trim(first.getStorageMethod()));
        if (trim(first.getShelfLifeDays()) != null)
            form.setShelfLifeDays(Integer.parseInt(trim(first.getShelfLifeDays())));
        form.setTagIds(splitTags(first.getTagCodes()).stream().map(c -> tagMap.get(c).getId()).distinct()
            .collect(Collectors.toList()));
        var skuList = new ArrayList<ProductSkuForm>();
        for (var row : rows) {
            var sku = new ProductSkuForm();
            // SKU 内部码在 ProductSkuSyncManager 新增时发号。
            sku.setBarcode(trim(row.getBarcode()));
            sku.setSpecName(trim(row.getSpecName()));
            sku.setSpecValues(new LinkedHashMap<>(Map.of("规格", trim(row.getSpecName()))));
            sku.setSaleUnit(saleUnitOf(row));
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
        var trimmedValue = trim(value);
        return trimmedValue != null && TRUTHY.contains(trimmedValue.toUpperCase(Locale.ROOT));
    }

    private List<String> splitTags(String value) {
        var trimmedValue = trim(value);
        if (trimmedValue == null)
            return List.of();
        return java.util.Arrays.stream(trimmedValue.split("[,，]")).map(String::trim).filter(s -> !s.isEmpty())
            .distinct().toList();
    }

    private String keep(String requested, String current) {
        return requested == null ? current : requested;
    }

    /**
     * 可空属性的更新取值：空白=保持原值，清空标记=NULL，其余=覆盖。
     */
    private String keepOrClear(String requested, String current) {
        return CLEAR_TOKEN.equals(requested) ? null : keep(requested, current);
    }

    private String string(Integer value) {
        return value == null ? null : String.valueOf(value);
    }

    private record Assembly(List<ProductSpuAddForm> forms, List<List<ProductImportRow>> groups) {
    }

    private record UpdateAssembly(List<ProductSpuUpdateForm> forms, List<List<ProductImportRow>> groups) {
    }
}
