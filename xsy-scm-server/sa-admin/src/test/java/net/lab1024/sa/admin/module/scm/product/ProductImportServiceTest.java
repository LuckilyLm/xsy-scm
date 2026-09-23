package net.lab1024.sa.admin.module.scm.product;

import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.product.constant.ProductErrorCode;
import net.lab1024.sa.admin.module.scm.product.dao.ProductCategoryDao;
import net.lab1024.sa.admin.module.scm.product.dao.ProductImageDao;
import net.lab1024.sa.admin.module.scm.product.dao.ProductSkuDao;
import net.lab1024.sa.admin.module.scm.product.dao.ProductSpuDao;
import net.lab1024.sa.admin.module.scm.product.dao.ProductTagDao;
import net.lab1024.sa.admin.module.scm.product.domain.entity.ProductCategoryEntity;
import net.lab1024.sa.admin.module.scm.product.domain.entity.ProductSkuEntity;
import net.lab1024.sa.admin.module.scm.product.domain.entity.ProductSpuEntity;
import net.lab1024.sa.admin.module.scm.product.domain.entity.ProductTagEntity;
import net.lab1024.sa.admin.module.scm.product.domain.form.ProductSkuForm;
import net.lab1024.sa.admin.module.scm.product.domain.form.ProductSpuAddForm;
import net.lab1024.sa.admin.module.scm.product.domain.form.ProductSpuUpdateForm;
import net.lab1024.sa.admin.module.scm.product.domain.vo.ProductSpuTagVO;
import net.lab1024.sa.admin.module.scm.product.service.ProductImportService;
import net.lab1024.sa.admin.module.scm.product.service.ProductImportService.ImportMode;
import net.lab1024.sa.admin.module.scm.product.service.ProductImportWriteService;
import net.lab1024.sa.admin.module.scm.product.service.ProductTagService;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * 商品导入解析与全量校验的纯单元测试：不连库，DAO 全部 mock，
 * 验证「0 错误才写、写失败整批回滚」，以及更新模式「空白列保持原值、未列出 SKU 保留」的合并语义。
 */
class ProductImportServiceTest {
    private final ProductCategoryDao categories = mock(ProductCategoryDao.class);
    private final ProductTagDao tags = mock(ProductTagDao.class);
    private final ProductSpuDao spus = mock(ProductSpuDao.class);
    private final ProductSkuDao skus = mock(ProductSkuDao.class);
    private final ProductImageDao images = mock(ProductImageDao.class);
    private final ProductTagService productTags = mock(ProductTagService.class);
    private final ProductImportWriteService writer = mock(ProductImportWriteService.class);
    private final ProductImportService service = new ProductImportService(categories, tags, spus, skus, images, productTags, writer);

    private static final String[] HEADERS = {"模板版本", "SPU编码", "商品名称", "别名", "分类编码", "助记码",
            "品牌", "产地", "储存方式", "保质期天数", "标签编码", "商品上下架", "SKU编码", "条码", "规格名称",
            "销售单位", "商品类型", "市场价", "SKU上下架", "默认SKU", "排序"};

    private static final String[] UPDATE_HEADERS = {"模板版本", "SPU ID", "SPU版本", "SKU ID", "SKU版本",
            "SPU编码", "商品名称", "别名", "分类编码", "助记码", "品牌", "产地", "储存方式", "保质期天数", "标签编码",
            "商品上下架", "SKU编码", "条码", "规格名称", "销售单位", "商品类型", "市场价", "SKU上下架", "默认SKU", "排序"};

    /** 更新模板的列下标：定位键在最前，其余列留空即保持库内原值。 */
    private static final int SPU_VERSION = 2;
    private static final int SKU_ID = 3;
    private static final int SKU_VERSION = 4;
    private static final int SPU_CODE = 5;
    private static final int ALIAS = 7;
    private static final int BRAND_NAME = 10;
    private static final int TAG_CODES = 14;
    private static final int SKU_CODE = 16;
    private static final int BARCODE = 17;
    private static final int SPEC_NAME = 18;
    private static final int SALE_UNIT = 19;
    private static final int PRODUCT_TYPE = 20;
    private static final int MARKET_PRICE = 21;
    private static final int SKU_STATUS = 22;
    private static final int DEFAULT_SKU = 23;

    @BeforeEach
    void catalog() {
        var category = new ProductCategoryEntity();
        category.setId(100L);
        category.setCategoryCode("FRESH-FRUIT");
        category.setStatus("ENABLED");
        category.setLevel(3);
        // 二级分类：存在且启用，但不能挂商品，用于验证层级在逐行校验就被拦下
        var parent = new ProductCategoryEntity();
        parent.setId(99L);
        parent.setCategoryCode("VEGETABLE");
        parent.setStatus("ENABLED");
        parent.setLevel(2);
        when(categories.selectList(any())).thenReturn(List.of(category, parent));
        var tag = new ProductTagEntity();
        tag.setId(200L);
        tag.setTagCode("HOT");
        when(tags.selectList(any())).thenReturn(List.of(tag));
        when(spus.selectList(any())).thenReturn(List.of(currentSpu()));
        when(skus.selectList(any())).thenReturn(List.of(currentSku()));
        when(images.selectList(any())).thenReturn(List.of());
        var binding = new ProductSpuTagVO();
        binding.setSpuId(7L);
        binding.setTagId(200L);
        when(productTags.bySpuIds(any())).thenReturn(Map.of(7L, List.of(binding)));
    }

    private ProductSpuEntity currentSpu() {
        var spu = new ProductSpuEntity();
        spu.setId(7L);
        spu.setVersion(3);
        spu.setSpuCode("SPU0001");
        spu.setName("示例蔬菜");
        spu.setCategoryId(100L);
        spu.setStatus("ON_SHELF");
        spu.setStorageMethod("CHILLED");
        spu.setMasterStatus("ENABLED");
        spu.setDescription("原有详情描述");
        return spu;
    }

    private ProductSkuEntity currentSku() {
        var sku = new ProductSkuEntity();
        sku.setId(70L);
        sku.setSpuId(7L);
        sku.setVersion(2);
        sku.setSkuCode("SKU0001");
        sku.setSpecName("500g/份");
        sku.setSpecValues(Map.of("规格", "500g/份"));
        sku.setSaleUnit("份");
        sku.setProductType("STANDARD");
        sku.setMarketPrice(new BigDecimal("9.9000"));
        sku.setStatus("ON_SHELF");
        sku.setDefaultFlag(true);
        sku.setSortOrder(0);
        return sku;
    }

    /** 更新模式的单行文件：默认只改市场价，其余列留空表示保持原值。 */
    private MockMultipartFile updateWorkbook(Consumer<XSSFWorkbook> edit) throws Exception {
        return sheet(UPDATE_HEADERS, book -> {
            var row = book.getSheetAt(0).createRow(1);
            String[] data = {"1.0", "7", "3", "70", "2", "SPU0001", "", "", "", "", "", "", "", "", "",
                    "", "SKU0001", "", "", "", "", "12.3400", "", "", ""};
            for (int i = 0; i < data.length; i++) row.createCell(i).setCellValue(data[i]);
            edit.accept(book);
        });
    }

    private MockMultipartFile sheet(String[] headers, Consumer<XSSFWorkbook> edit) throws Exception {
        try (var book = new XSSFWorkbook(); var bytes = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("商品导入");
            var header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
            edit.accept(book);
            book.write(bytes);
            return new MockMultipartFile("file", "products.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes.toByteArray());
        }
    }

    private MockMultipartFile workbook(Consumer<XSSFWorkbook> edit) throws Exception {
        return sheet(HEADERS, book -> {
            // 物理空行不得让 Excel 第 4 行的定位前移到第 3 行
            book.getSheetAt(0).createRow(1);
            var row = book.getSheetAt(0).createRow(2);
            String[] data = {"1.0", "SPU0001", "示例蔬菜", "", "FRESH-FRUIT", "", "", "本地", "CHILLED",
                    "", "HOT", "ON_SHELF", "SKU0001", "", "500g/份", "份", "STANDARD", "9.9000", "ON_SHELF", "是", "0"};
            for (int i = 0; i < data.length; i++) row.createCell(i).setCellValue(data[i]);
            edit.accept(book);
        });
    }

    @Test
    void validSingleSkuWritesThroughOnce() throws Exception {
        when(writer.writeAll(anyList())).thenReturn(List.of(5001L));
        var result = service.importFile(workbook(book -> {
        }), ImportMode.CREATE);
        assertThat(result.getTotalErrors()).isZero();
        assertThat(result.getImportedProducts()).isEqualTo(1);
        assertThat(result.getSpuIds()).containsExactly(5001L);
        verify(writer).writeAll(argThat((List<ProductSpuAddForm> forms) -> {
            var form = forms.getFirst();
            return form.getSpuCode().equals("SPU0001")
                    && form.getCategoryId().equals(100L)
                    && form.getTagIds().contains(200L)
                    && form.getSkuList().getFirst().getMarketPrice().toPlainString().equals("9.9000")
                    && Boolean.TRUE.equals(form.getSkuList().getFirst().getDefaultFlag());
        }));
    }

    @Test
    void blankRowDoesNotShiftRowDiagnosis() throws Exception {
        var result = service.importFile(workbook(b -> b.getSheetAt(0).getRow(2).getCell(11).setCellValue("MAYBE")), ImportMode.CREATE);
        assertThat(result.getErrors()).anySatisfy(error -> {
            assertThat(error.getRowNumber()).isEqualTo(3);
            assertThat(error.getColumn()).isEqualTo("商品上下架");
            assertThat(error.getCode()).isEqualTo("ENUM_INVALID");
        });
        verifyNoInteractions(writer);
    }

    @Test
    void secondLevelCategoryIsReportedPerRowBeforeWrite() throws Exception {
        var result = service.importFile(workbook(b -> b.getSheetAt(0).getRow(2).getCell(4).setCellValue("VEGETABLE")),
                ImportMode.CREATE);
        // 分类存在且启用，只有层级不合规：必须在逐行校验就指到单元格，而不是等写库报 CATEGORY_PARENT_INVALID
        assertThat(result.getErrors()).singleElement().satisfies(error -> {
            assertThat(error.getRowNumber()).isEqualTo(3);
            assertThat(error.getColumn()).isEqualTo("分类编码");
            assertThat(error.getCode()).isEqualTo("CATEGORY_LEVEL_INVALID");
        });
        verifyNoInteractions(writer);
    }

    @Test
    void headerMismatchBlocksBeforeWrite() throws Exception {
        var result = service.importFile(workbook(b -> b.getSheetAt(0).getRow(0).getCell(1).setCellValue("错误列")), ImportMode.CREATE);
        assertThat(result.getErrors()).anySatisfy(error -> {
            assertThat(error.getRowNumber()).isEqualTo(1);
            assertThat(error.getCode()).isEqualTo("HEADER_INVALID");
        });
        verifyNoInteractions(writer);
    }

    @Test
    void formulaCellIsRejected() throws Exception {
        var result = service.importFile(workbook(b -> b.getSheetAt(0).getRow(2).getCell(17).setCellFormula("1+1")), ImportMode.CREATE);
        assertThat(result.getErrors()).extracting("code").contains("CELL_INVALID");
        verifyNoInteractions(writer);
    }

    @Test
    void extraSheetIsRejected() throws Exception {
        var result = service.importFile(workbook(b -> b.createSheet("更多")), ImportMode.CREATE);
        assertThat(result.getErrors()).extracting("code").contains("SHEET_COUNT");
        verifyNoInteractions(writer);
    }

    @Test
    void unknownCategoryCodeIsRejected() throws Exception {
        var result = service.importFile(workbook(b -> b.getSheetAt(0).getRow(2).getCell(4).setCellValue("NOPE")), ImportMode.CREATE);
        assertThat(result.getErrors()).anySatisfy(error -> assertThat(error.getCode()).isEqualTo("CATEGORY_NOT_FOUND"));
        verifyNoInteractions(writer);
    }

    @Test
    void missingDefaultSkuIsRejected() throws Exception {
        var result = service.importFile(workbook(b -> b.getSheetAt(0).getRow(2).getCell(19).setCellValue("否")), ImportMode.CREATE);
        assertThat(result.getErrors()).anySatisfy(error -> assertThat(error.getCode()).isEqualTo("DEFAULT_SKU_INVALID"));
        verifyNoInteractions(writer);
    }

    @Test
    void duplicateSkuCodeWithinProductIsRejected() throws Exception {
        var result = service.importFile(workbook(book -> {
            var sheet = book.getSheetAt(0);
            var second = sheet.createRow(3);
            String[] data = {"1.0", "SPU0001", "示例蔬菜", "", "FRESH-FRUIT", "", "", "本地", "CHILLED",
                    "", "HOT", "ON_SHELF", "SKU0001", "", "1kg/份", "份", "STANDARD", "18.0000", "ON_SHELF", "否", "1"};
            for (int i = 0; i < data.length; i++) second.createCell(i).setCellValue(data[i]);
        }), ImportMode.CREATE);
        assertThat(result.getErrors()).anySatisfy(error -> {
            assertThat(error.getCode()).isEqualTo("SKU_CODE_DUPLICATE");
            assertThat(error.getRowNumber()).isEqualTo(4);
        });
        verifyNoInteractions(writer);
    }

    @Test
    void inconsistentGroupFieldsRejected() throws Exception {
        var result = service.importFile(workbook(book -> {
            var sheet = book.getSheetAt(0);
            var second = sheet.createRow(3);
            String[] data = {"1.0", "SPU0001", "示例蔬菜", "", "FRESH-PRODUCE", "", "", "本地", "CHILLED",
                    "", "HOT", "ON_SHELF", "SKU0002", "", "1kg/份", "份", "STANDARD", "18.0000", "ON_SHELF", "否", "1"};
            for (int i = 0; i < data.length; i++) second.createCell(i).setCellValue(data[i]);
        }), ImportMode.CREATE);
        assertThat(result.getErrors()).anySatisfy(error -> assertThat(error.getCode()).isEqualTo("HEADER_CONFLICT"));
        verifyNoInteractions(writer);
    }

    @Test
    void writeFailureRollsBackWholeBatchAndLocatesGroup() throws Exception {
        when(writer.writeAll(anyList())).thenThrow(new ProductImportWriteService.ImportProductException(0,
                new ScmBusinessException(ProductErrorCode.PRODUCT_CODE_DUPLICATE)));
        var result = service.importFile(workbook(book -> {
        }), ImportMode.CREATE);
        assertThat(result.getImportedProducts()).isZero();
        assertThat(result.getErrors()).singleElement().satisfies(error -> {
            assertThat(error.getRowNumber()).isEqualTo(3);
            assertThat(error.getCode()).isEqualTo("40923");
            assertThat(error.getMessage()).contains("整批已回滚");
        });
    }

    @Test
    void emptyDataIsRejected() throws Exception {
        var result = service.importFile(workbook(book -> book.getSheetAt(0).removeRow(book.getSheetAt(0).getRow(2))), ImportMode.CREATE);
        assertThat(result.getErrors()).extracting("code").contains("FILE_EMPTY");
        verifyNoInteractions(writer);
    }

    @Test
    void malformedFileIsRejected() throws Exception {
        var file = new MockMultipartFile("file", "broken.xlsx", "application/octet-stream", new byte[]{1, 2, 3});
        assertThat(service.importFile(file, ImportMode.CREATE).getErrors()).extracting("code").contains("FILE_INVALID");
        verifyNoInteractions(writer);
    }

    @Test
    void templateHeaderRoundTripsAndIsParseable() throws Exception {
        byte[] template = service.buildTemplate(ImportMode.CREATE);
        when(writer.writeAll(anyList())).thenReturn(List.of(1L));
        var result = service.importFile(new MockMultipartFile("file", "t.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", template), ImportMode.CREATE);
        // 模板示例行自带合法编码，能进入校验阶段（分类编码在 mock 目录里不存在 → 只报 CATEGORY_NOT_FOUND，无 HEADER 错误）
        assertThat(result.getErrors()).noneMatch(e -> e.getCode().equals("HEADER_INVALID") || e.getCode().equals("SHEET_COUNT"));
    }

    @Test
    void updateModeKeepsBlankColumnsAtCurrentValue() throws Exception {
        when(writer.writeUpdates(anyList())).thenReturn(1);
        var result = service.importFile(updateWorkbook(book -> {
        }), ImportMode.UPDATE);
        assertThat(result.getMode()).isEqualTo("UPDATE");
        assertThat(result.getTotalErrors()).isZero();
        assertThat(result.getUpdatedProducts()).isEqualTo(1);
        assertThat(result.getSpuIds()).containsExactly(7L);
        verify(writer).writeUpdates(argThat((List<ProductSpuUpdateForm> forms) -> {
            var form = forms.getFirst();
            var sku = form.getSkuList().getFirst();
            // 文件只填了市场价：名称、描述、分类、标签与 SKU 其余列都必须回到库内原值
            return form.getSpuId().equals(7L) && form.getVersion().equals(3)
                    && "SPU0001".equals(form.getSpuCode())
                    && "示例蔬菜".equals(form.getName())
                    && "原有详情描述".equals(form.getDescription())
                    && "ENABLED".equals(form.getMasterStatus())
                    && Long.valueOf(100L).equals(form.getCategoryId())
                    && List.of(200L).equals(form.getTagIds())
                    && Long.valueOf(70L).equals(sku.getSkuId()) && sku.getVersion().equals(2)
                    && "SKU0001".equals(sku.getSkuCode()) && "500g/份".equals(sku.getSpecName())
                    && Map.of("规格", "500g/份").equals(sku.getSpecValues())
                    && "份".equals(sku.getSaleUnit()) && "ON_SHELF".equals(sku.getStatus())
                    && Boolean.TRUE.equals(sku.getDefaultFlag())
                    && "12.3400".equals(sku.getMarketPrice().toPlainString());
        }));
    }

    @Test
    void updateModeClearsOnlyMarkedNullableAttributes() throws Exception {
        when(writer.writeUpdates(anyList())).thenReturn(1);
        var spu = currentSpu();
        spu.setAlias("原别名");
        spu.setBrandName("原品牌");
        when(spus.selectList(any())).thenReturn(List.of(spu));
        var sku = currentSku();
        sku.setBarcode("BC-0001");
        when(skus.selectList(any())).thenReturn(List.of(sku));

        var result = service.importFile(updateWorkbook(book -> {
            var row = book.getSheetAt(0).getRow(1);
            for (int column : new int[]{ALIAS, BRAND_NAME, TAG_CODES, BARCODE})
                row.getCell(column).setCellValue(ProductImportService.CLEAR_TOKEN);
        }), ImportMode.UPDATE);

        assertThat(result.getTotalErrors()).isZero();
        verify(writer).writeUpdates(argThat((List<ProductSpuUpdateForm> forms) -> {
            var form = forms.getFirst();
            var merged = form.getSkuList().getFirst();
            // 只有带标记的列被清；名称、编码与规格等未标记列仍是库内原值
            return form.getAlias() == null && form.getBrandName() == null
                    && form.getTagIds().isEmpty() && merged.getBarcode() == null
                    && "示例蔬菜".equals(form.getName()) && "SKU0001".equals(merged.getSkuCode())
                    && "12.3400".equals(merged.getMarketPrice().toPlainString());
        }));
    }

    @Test
    void updateModeRejectsClearMarkerOnGuardedColumn() throws Exception {
        var result = service.importFile(updateWorkbook(book -> book.getSheetAt(0).getRow(1)
                .getCell(DEFAULT_SKU).setCellValue(ProductImportService.CLEAR_TOKEN)), ImportMode.UPDATE);
        assertThat(result.getErrors()).anySatisfy(error -> {
            assertThat(error.getCode()).isEqualTo("CLEAR_NOT_ALLOWED");
            assertThat(error.getColumn()).isEqualTo("默认SKU");
        });
        verifyNoInteractions(writer);
    }

    @Test
    void updateModeKeepsUnlistedExistingSkuAsDefault() throws Exception {
        when(writer.writeUpdates(anyList())).thenReturn(1);
        var result = service.importFile(updateWorkbook(book -> {
            var row = book.getSheetAt(0).getRow(1);
            // 整份文件只登记一个新 SKU：既存默认 SKU 70 没出现，仍要保留且继续算默认，否则会把补规格变成删原规格
            row.getCell(SKU_ID).setCellValue("");
            row.getCell(SKU_VERSION).setCellValue("");
            row.getCell(SKU_CODE).setCellValue("SKU0002");
            row.getCell(SPEC_NAME).setCellValue("1kg/份");
            row.getCell(SALE_UNIT).setCellValue("份");
            row.getCell(PRODUCT_TYPE).setCellValue("STANDARD");
            row.getCell(MARKET_PRICE).setCellValue("18.0000");
            row.getCell(SKU_STATUS).setCellValue("ON_SHELF");
            row.getCell(DEFAULT_SKU).setCellValue("否");
        }), ImportMode.UPDATE);
        assertThat(result.getTotalErrors()).isZero();
        verify(writer).writeUpdates(argThat((List<ProductSpuUpdateForm> forms) -> {
            var merged = forms.getFirst().getSkuList();
            return merged.size() == 2 && Long.valueOf(70L).equals(merged.getFirst().getSkuId())
                    && Boolean.TRUE.equals(merged.getFirst().getDefaultFlag())
                    && merged.get(1).getSkuId() == null && "SKU0002".equals(merged.get(1).getSkuCode())
                    && "18.0000".equals(merged.get(1).getMarketPrice().toPlainString());
        }));
    }

    @Test
    void updateModeRejectsStaleVersionWithoutWriting() throws Exception {
        var result = service.importFile(updateWorkbook(book -> book.getSheetAt(0).getRow(1)
                .getCell(SPU_VERSION).setCellValue("4")), ImportMode.UPDATE);
        assertThat(result.getErrors()).anySatisfy(error -> {
            assertThat(error.getCode()).isEqualTo("VERSION_CONFLICT");
            assertThat(error.getColumn()).isEqualTo("SPU版本");
            assertThat(error.getRowNumber()).isEqualTo(2);
        });
        verifyNoInteractions(writer);
    }

    @Test
    void updateModeRefusesToRetypeBusinessCodes() throws Exception {
        var result = service.importFile(updateWorkbook(book -> {
            var row = book.getSheetAt(0).getRow(1);
            row.getCell(SPU_CODE).setCellValue("SPU9999");
            row.getCell(SKU_CODE).setCellValue("SKU9999");
        }), ImportMode.UPDATE);
        assertThat(result.getErrors()).extracting("code").contains("FIELD_LOCKED");
        assertThat(result.getErrors()).allSatisfy(error -> {
            if (error.getCode().equals("FIELD_LOCKED"))
                assertThat(List.of("SPU编码", "SKU编码")).contains(error.getColumn());
        });
        verifyNoInteractions(writer);
    }

    @Test
    void createFileCannotBeImportedAsUpdate() throws Exception {
        var result = service.importFile(workbook(book -> {
        }), ImportMode.UPDATE);
        assertThat(result.getErrors()).extracting("code").contains("HEADER_INVALID");
        verifyNoInteractions(writer);
    }

    @Test
    void updateWriteFailureRollsBackWholeBatchAndLocatesRow() throws Exception {
        when(writer.writeUpdates(anyList())).thenThrow(new ProductImportWriteService.ImportProductException(0,
                new ScmBusinessException(ProductErrorCode.VERSION_CONFLICT)));
        var result = service.importFile(updateWorkbook(book -> {
        }), ImportMode.UPDATE);
        assertThat(result.getUpdatedProducts()).isZero();
        assertThat(result.getErrors()).singleElement().satisfies(error -> {
            assertThat(error.getRowNumber()).isEqualTo(2);
            assertThat(error.getCode()).isEqualTo("40921");
            assertThat(error.getMessage()).contains("整批已回滚");
        });
    }

    @Test
    void updateTemplateRoundTripsAgainstOwnHeaders() throws Exception {
        when(writer.writeUpdates(anyList())).thenReturn(1);
        byte[] template = service.buildTemplate(ImportMode.UPDATE);
        var result = service.importFile(new MockMultipartFile("file", "u.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", template), ImportMode.UPDATE);
        // 示例行按更新表头解析成功即无 HEADER 错；定位键在 mock 现状里不存在 → 只报业务错
        assertThat(result.getErrors()).noneMatch(e -> e.getCode().equals("HEADER_INVALID") || e.getCode().equals("SHEET_COUNT"));
        assertThat(result.getErrors()).extracting("code").contains("PRODUCT_NOT_FOUND");
    }
}
