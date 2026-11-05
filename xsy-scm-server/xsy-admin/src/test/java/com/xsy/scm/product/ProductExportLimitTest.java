package com.xsy.scm.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.xsy.scm.common.error.ScmExportErrorCode;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.product.controller.ProductExcelController;
import com.xsy.scm.product.domain.form.ProductSpuQueryForm;
import com.xsy.scm.product.domain.vo.ProductSkuVO;
import com.xsy.scm.product.domain.vo.ProductSpuVO;
import com.xsy.scm.product.service.ProductImportService;
import com.xsy.scm.product.service.ProductQueryService;
import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.List;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.module.support.securityprotect.service.SecurityFileService;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletResponse;

/** 默认导出与自选列导出必须在发送文件前拒绝截断或规格展开超限。 */
class ProductExportLimitTest {
    private final ProductQueryService query = mock(ProductQueryService.class);
    private final ProductExcelController controller = new ProductExcelController(mock(ProductImportService.class),
        query, mock(SecurityFileService.class));

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsMoreProductsThanTheLimitWithoutStartingAFile(boolean selected) {
        var form = form(selected);
        var page = page(List.of(), 100001L);
        when(query.query(any())).thenReturn(page);
        var response = new MockHttpServletResponse();
        assertLimit(form, response);
        assertThat(form.getPageNum()).isEqualTo(1L);
        assertThat(form.getPageSize()).isEqualTo(100001L);
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsTooManyExpandedSkuRows(boolean selected) {
        var spu = product();
        spu.setSkuList(Collections.nCopies(100001, new ProductSkuVO()));
        when(query.query(any())).thenReturn(page(List.of(spu), 1L));
        var response = new MockHttpServletResponse();
        assertLimit(form(selected), response);
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void completeSmallResultsAreStillReadableWorkbooks(boolean selected) throws Exception {
        when(query.query(any())).thenReturn(page(List.of(product()), 1L));
        var response = new MockHttpServletResponse();
        controller.export(form(selected), response);
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            assertThat(workbook.getSheetAt(0).getLastRowNum()).isEqualTo(1);
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(selected ? 0 : 5).getStringCellValue())
                .isEqualTo("测试商品");
        }
    }

    private void assertLimit(ProductSpuQueryForm form, MockHttpServletResponse response) {
        assertThatThrownBy(() -> controller.export(form, response))
            .isInstanceOfSatisfying(ScmBusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ScmExportErrorCode.EXPORT_ROW_LIMIT_EXCEEDED));
    }

    private ProductSpuQueryForm form(boolean selected) {
        var form = new ProductSpuQueryForm();
        if (selected) form.setExportColumnKeys(List.of("spuName"));
        return form;
    }

    private ProductSpuVO product() {
        var spu = new ProductSpuVO();
        spu.setSpuId(1L);
        spu.setName("测试商品");
        spu.setStatus("ENABLED");
        spu.setSkuList(List.of());
        return spu;
    }

    private PageResult<ProductSpuVO> page(List<ProductSpuVO> rows, Long total) {
        var page = new PageResult<ProductSpuVO>();
        page.setList(rows);
        page.setTotal(total);
        return page;
    }
}
