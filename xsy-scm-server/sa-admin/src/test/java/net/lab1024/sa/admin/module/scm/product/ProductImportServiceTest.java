package net.lab1024.sa.admin.module.scm.product;

import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.product.constant.ProductErrorCode;
import net.lab1024.sa.admin.module.scm.product.dao.ProductCategoryDao;
import net.lab1024.sa.admin.module.scm.product.dao.ProductTagDao;
import net.lab1024.sa.admin.module.scm.product.domain.entity.ProductCategoryEntity;
import net.lab1024.sa.admin.module.scm.product.domain.entity.ProductTagEntity;
import net.lab1024.sa.admin.module.scm.product.domain.form.ProductSpuAddForm;
import net.lab1024.sa.admin.module.scm.product.service.ProductImportService;
import net.lab1024.sa.admin.module.scm.product.service.ProductImportWriteService;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * 商品导入解析与全量校验的纯单元测试：不连库，DAO 全部 mock，
 * 只验证「0 错误才写、写失败整批回滚」这条计划 §5.8 核心契约。
 */
class ProductImportServiceTest {
    private final ProductCategoryDao categories = mock(ProductCategoryDao.class);
    private final ProductTagDao tags = mock(ProductTagDao.class);
    private final ProductImportWriteService writer = mock(ProductImportWriteService.class);
    private final ProductImportService service = new ProductImportService(categories, tags, writer);

    private static final String[] HEADERS = {"模板版本", "SPU编码", "商品名称", "别名", "分类编码", "助记码",
            "品牌", "产地", "储存方式", "保质期天数", "标签编码", "商品上下架", "SKU编码", "条码", "规格名称",
            "销售单位", "商品类型", "市场价", "SKU上下架", "默认SKU", "排序"};

    @BeforeEach
    void catalog() {
        var category = new ProductCategoryEntity();
        category.setId(100L);
        category.setCategoryCode("FRESH-FRUIT");
        category.setStatus("ENABLED");
        when(categories.selectList(any())).thenReturn(List.of(category));
        var tag = new ProductTagEntity();
        tag.setId(200L);
        tag.setTagCode("HOT");
        when(tags.selectList(any())).thenReturn(List.of(tag));
    }

    private MockMultipartFile workbook(Consumer<XSSFWorkbook> edit) throws Exception {
        try (var book = new XSSFWorkbook(); var bytes = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("商品导入");
            var header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) header.createCell(i).setCellValue(HEADERS[i]);
            // 物理空行不得让 Excel 第 4 行的定位前移到第 3 行
            sheet.createRow(1);
            var row = sheet.createRow(2);
            String[] data = {"1.0", "SPU0001", "示例蔬菜", "", "FRESH-FRUIT", "", "", "本地", "CHILLED",
                    "", "HOT", "ON_SHELF", "SKU0001", "", "500g/份", "份", "STANDARD", "9.9000", "ON_SHELF", "是", "0"};
            for (int i = 0; i < data.length; i++) row.createCell(i).setCellValue(data[i]);
            edit.accept(book);
            book.write(bytes);
            return new MockMultipartFile("file", "products.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes.toByteArray());
        }
    }

    @Test
    void validSingleSkuWritesThroughOnce() throws Exception {
        when(writer.writeAll(anyList())).thenReturn(List.of(5001L));
        var result = service.importFile(workbook(book -> {
        }));
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
        var result = service.importFile(workbook(b -> b.getSheetAt(0).getRow(2).getCell(11).setCellValue("MAYBE")));
        assertThat(result.getErrors()).anySatisfy(error -> {
            assertThat(error.getRowNumber()).isEqualTo(3);
            assertThat(error.getColumn()).isEqualTo("商品上下架");
            assertThat(error.getCode()).isEqualTo("ENUM_INVALID");
        });
        verifyNoInteractions(writer);
    }

    @Test
    void headerMismatchBlocksBeforeWrite() throws Exception {
        var result = service.importFile(workbook(b -> b.getSheetAt(0).getRow(0).getCell(1).setCellValue("错误列")));
        assertThat(result.getErrors()).anySatisfy(error -> {
            assertThat(error.getRowNumber()).isEqualTo(1);
            assertThat(error.getCode()).isEqualTo("HEADER_INVALID");
        });
        verifyNoInteractions(writer);
    }

    @Test
    void formulaCellIsRejected() throws Exception {
        var result = service.importFile(workbook(b -> b.getSheetAt(0).getRow(2).getCell(17).setCellFormula("1+1")));
        assertThat(result.getErrors()).extracting("code").contains("CELL_INVALID");
        verifyNoInteractions(writer);
    }

    @Test
    void extraSheetIsRejected() throws Exception {
        var result = service.importFile(workbook(b -> b.createSheet("更多")));
        assertThat(result.getErrors()).extracting("code").contains("SHEET_COUNT");
        verifyNoInteractions(writer);
    }

    @Test
    void unknownCategoryCodeIsRejected() throws Exception {
        var result = service.importFile(workbook(b -> b.getSheetAt(0).getRow(2).getCell(4).setCellValue("NOPE")));
        assertThat(result.getErrors()).anySatisfy(error -> assertThat(error.getCode()).isEqualTo("CATEGORY_NOT_FOUND"));
        verifyNoInteractions(writer);
    }

    @Test
    void missingDefaultSkuIsRejected() throws Exception {
        var result = service.importFile(workbook(b -> b.getSheetAt(0).getRow(2).getCell(19).setCellValue("否")));
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
        }));
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
        }));
        assertThat(result.getErrors()).anySatisfy(error -> assertThat(error.getCode()).isEqualTo("HEADER_CONFLICT"));
        verifyNoInteractions(writer);
    }

    @Test
    void writeFailureRollsBackWholeBatchAndLocatesGroup() throws Exception {
        when(writer.writeAll(anyList())).thenThrow(new ProductImportWriteService.ImportProductException(0,
                new ScmBusinessException(ProductErrorCode.PRODUCT_CODE_DUPLICATE)));
        var result = service.importFile(workbook(book -> {
        }));
        assertThat(result.getImportedProducts()).isZero();
        assertThat(result.getErrors()).singleElement().satisfies(error -> {
            assertThat(error.getRowNumber()).isEqualTo(3);
            assertThat(error.getCode()).isEqualTo("40923");
            assertThat(error.getMessage()).contains("整批已回滚");
        });
    }

    @Test
    void emptyDataIsRejected() throws Exception {
        var result = service.importFile(workbook(book -> book.getSheetAt(0).removeRow(book.getSheetAt(0).getRow(2))));
        assertThat(result.getErrors()).extracting("code").contains("FILE_EMPTY");
        verifyNoInteractions(writer);
    }

    @Test
    void malformedFileIsRejected() throws Exception {
        var file = new MockMultipartFile("file", "broken.xlsx", "application/octet-stream", new byte[]{1, 2, 3});
        assertThat(service.importFile(file).getErrors()).extracting("code").contains("FILE_INVALID");
        verifyNoInteractions(writer);
    }

    @Test
    void templateHeaderRoundTripsAndIsParseable() throws Exception {
        byte[] template = service.buildTemplate();
        when(writer.writeAll(anyList())).thenReturn(List.of(1L));
        var result = service.importFile(new MockMultipartFile("file", "t.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", template));
        // 模板示例行自带合法编码，能进入校验阶段（分类编码在 mock 目录里不存在 → 只报 CATEGORY_NOT_FOUND，无 HEADER 错误）
        assertThat(result.getErrors()).noneMatch(e -> e.getCode().equals("HEADER_INVALID") || e.getCode().equals("SHEET_COUNT"));
    }
}
