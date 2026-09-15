package net.lab1024.sa.admin.module.scm.supplier;

import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.common.handler.ScmExceptionHandler;
import net.lab1024.sa.admin.module.scm.supplier.controller.SupplierSkuController;
import net.lab1024.sa.admin.module.scm.supplier.domain.form.SupplierSkuReplaceForm;
import net.lab1024.sa.admin.module.scm.supplier.domain.vo.SupplierSkuVO;
import net.lab1024.sa.admin.module.scm.supplier.service.SupplierSkuService;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.SystemEnvironment;
import net.lab1024.sa.base.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static net.lab1024.sa.admin.module.scm.supplier.constant.SupplierErrorCode.SKU_DISABLED;
import static net.lab1024.sa.admin.module.scm.supplier.constant.SupplierErrorCode.SUPPLIER_SKU_DUPLICATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 商品-供应商关系端点契约测试。
 *
 * <p>重点锁定两件事：
 * <ol>
 *   <li>整表替换的「空数组 = 清空」语义不会被误当成「无操作」；</li>
 *   <li>定点数字段只接受 JSON <b>字符串</b>，数字字面量与超 4 位小数都被拦下。</li>
 * </ol>
 */
@WebMvcTest(SupplierSkuController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = {SupplierSkuController.class, ScmExceptionHandler.class, GlobalExceptionHandler.class})
class SupplierSkuControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SupplierSkuService service;

    @MockitoBean(name = "systemEnvironment")
    private SystemEnvironment environment;

    @Test
    @DisplayName("按供应商回填：返回数组信封")
    void listsBySupplier() throws Exception {
        when(service.listBySupplierId(3L)).thenReturn(List.of());

        mvc.perform(get("/scm/supplier/sku/list/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("反查分页：返回分页信封")
    void queryReturnsEnvelope() throws Exception {
        PageResult<SupplierSkuVO> page = new PageResult<>();
        page.setPageNum(1L);
        page.setPageSize(20L);
        page.setTotal(0L);
        page.setPages(0L);
        page.setList(List.of());
        page.setEmptyFlag(true);
        when(service.query(any())).thenReturn(page);

        mvc.perform(post("/scm/supplier/sku/query").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pageNum\":1,\"pageSize\":20,\"skuId\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.list").isArray());
    }

    @Test
    @DisplayName("空数组 = 清空全部关联（请求确实送达服务层，不是被当成无操作）")
    void emptyItemsMeansClearAll() throws Exception {
        mvc.perform(post("/scm/supplier/sku/replace").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":3,\"items\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        ArgumentCaptor<SupplierSkuReplaceForm> captor = ArgumentCaptor.forClass(SupplierSkuReplaceForm.class);
        verify(service).replace(captor.capture());
        assertThat(captor.getValue().getSupplierId()).isEqualTo(3L);
        assertThat(captor.getValue().getItems()).isEmpty();
    }

    @Test
    @DisplayName("参考价必须是 JSON 字符串：数字字面量被拒绝")
    void rejectsNumericLiteralReferencePrice() throws Exception {
        mvc.perform(post("/scm/supplier/sku/replace").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":3,\"items\":[{\"skuId\":5,\"purchaseUnit\":\"箱\",\"referencePrice\":12.34,\"defaultFlag\":false}]}"))
                .andExpect(jsonPath("$.code").value(30001));
    }

    @Test
    @DisplayName("参考价超过 4 位小数被拒绝")
    void rejectsTooManyDecimals() throws Exception {
        mvc.perform(post("/scm/supplier/sku/replace").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":3,\"items\":[{\"skuId\":5,\"purchaseUnit\":\"箱\",\"referencePrice\":\"12.34000\",\"defaultFlag\":false}]}"))
                .andExpect(jsonPath("$.code").value(30001));
    }

    @Test
    @DisplayName("参考价为 null 表示「未定价」，允许通过")
    void allowsNullReferencePrice() throws Exception {
        mvc.perform(post("/scm/supplier/sku/replace").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":3,\"items\":[{\"skuId\":5,\"purchaseUnit\":\"箱\",\"referencePrice\":null,\"defaultFlag\":true}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("SKU 未上架 → 40942")
    void mapsSkuDisabled() throws Exception {
        doThrow(new ScmBusinessException(SKU_DISABLED)).when(service).replace(any());

        mvc.perform(post("/scm/supplier/sku/replace").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":3,\"items\":[{\"skuId\":5,\"purchaseUnit\":\"箱\",\"defaultFlag\":false}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40942));
    }

    @Test
    @DisplayName("关联重复 → 40943")
    void mapsDuplicate() throws Exception {
        doThrow(new ScmBusinessException(SUPPLIER_SKU_DUPLICATE)).when(service).replace(any());

        mvc.perform(post("/scm/supplier/sku/replace").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":3,\"items\":[{\"skuId\":5,\"purchaseUnit\":\"箱\",\"defaultFlag\":false}]}"))
                .andExpect(jsonPath("$.code").value(40943));
    }

    @Test
    @DisplayName("版本冲突 → 40921")
    void mapsVersionConflict() throws Exception {
        doThrow(new ScmBusinessException(VERSION_CONFLICT)).when(service).replace(any());

        mvc.perform(post("/scm/supplier/sku/replace").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":3,\"items\":[{\"id\":9,\"version\":0,\"skuId\":5,\"purchaseUnit\":\"箱\",\"defaultFlag\":false}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40921));
    }

    @Test
    @DisplayName("采购单位必填")
    void requiresPurchaseUnit() throws Exception {
        mvc.perform(post("/scm/supplier/sku/replace").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":3,\"items\":[{\"skuId\":5,\"defaultFlag\":false}]}"))
                .andExpect(jsonPath("$.code").value(30001));
    }

    @Test
    @DisplayName("超过 500 行被拒绝（风险 R2 的请求体上限）")
    void rejectsOversizedPayload() throws Exception {
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < 501; i++) {
            if (i > 0) {
                items.append(',');
            }
            items.append("{\"skuId\":").append(i + 1).append(",\"purchaseUnit\":\"箱\",\"defaultFlag\":false}");
        }
        mvc.perform(post("/scm/supplier/sku/replace").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":3,\"items\":[" + items + "]}"))
                .andExpect(jsonPath("$.code").value(30001));
    }
}
