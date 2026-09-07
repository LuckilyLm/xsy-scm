package com.xianshuyuan.scm.common.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void mapsBusinessExceptionWithoutExposingStackTrace() throws Exception {
        mockMvc.perform(get("/test/business"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(40401))
            .andExpect(jsonPath("$.message").value("商品不存在"))
            .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    void mapsValidationFailureToStableResponse() throws Exception {
        mockMvc.perform(post("/test/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(40000))
            .andExpect(jsonPath("$.message").value("名称不能为空"));
    }

    @Test
    void mapsNestedSupplierConstraintToDomainConflict() {
        Throwable databaseCause = new IllegalStateException(
                "duplicate key value violates unique constraint uk_supplier_code_active");
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
                "write rejected", new RuntimeException("persistence failure", databaseCause));

        var response = new GlobalExceptionHandler().handleDataConflict(exception);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().code()).isEqualTo(40944);
    }

    @Test
    void mapsWarehouseConstraintToDomainConflict() {
        var exception = new DataIntegrityViolationException(
                "duplicate key value violates unique constraint uk_warehouse_code_active");

        var response = new GlobalExceptionHandler().handleDataConflict(exception);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().code()).isEqualTo(40945);
    }

    @Test
    void mapsSupplierSkuConstraintToDomainConflict() {
        var exception = new DataIntegrityViolationException(
                "duplicate key value violates unique constraint uk_supplier_sku_active");

        var response = new GlobalExceptionHandler().handleDataConflict(exception);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().code()).isEqualTo(40946);
    }

    @Test
    void mapsUnknownConstraintToGenericDataConflict() {
        var exception = new DataIntegrityViolationException(
                "duplicate key value violates unique constraint uk_other_active");

        var response = new GlobalExceptionHandler().handleDataConflict(exception);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.DATA_CONFLICT.code());
    }

    @RestController
    static class TestController {

        @GetMapping("/test/business")
        void business() {
            throw new BusinessException(
                new ErrorCode(40401, HttpStatus.NOT_FOUND, "资源不存在"),
                "商品不存在"
            );
        }

        @PostMapping("/test/validation")
        void validation(@Valid @RequestBody TestRequest request) {
        }
    }

    record TestRequest(@NotBlank(message = "名称不能为空") String name) {
    }
}
