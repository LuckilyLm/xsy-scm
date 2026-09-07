package com.xianshuyuan.scm.product.controller;

import com.xianshuyuan.scm.common.api.ApiResponse;
import com.xianshuyuan.scm.common.api.PageData;
import com.xianshuyuan.scm.product.dto.ProductPageQuery;
import com.xianshuyuan.scm.product.dto.ProductSaveRequest;
import com.xianshuyuan.scm.product.dto.ProductStatusRequest;
import com.xianshuyuan.scm.product.entity.ProductType;
import com.xianshuyuan.scm.product.entity.ShelfStatus;
import com.xianshuyuan.scm.product.service.ProductApplicationService;
import com.xianshuyuan.scm.product.service.ProductQueryService;
import com.xianshuyuan.scm.product.vo.ProductDetailResponse;
import com.xianshuyuan.scm.product.vo.ProductSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "商品档案")
@Validated
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductQueryService queryService;
    private final ProductApplicationService applicationService;

    public ProductController(
            ProductQueryService queryService,
            ProductApplicationService applicationService
    ) {
        this.queryService = queryService;
        this.applicationService = applicationService;
    }

    @Operation(summary = "分页查询商品")
    @GetMapping
    public ApiResponse<PageData<ProductSummaryResponse>> page(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码不能小于1") long page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "每页数量不能小于1")
            @Max(value = 100, message = "每页数量不能超过100") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) ShelfStatus spuStatus,
            @RequestParam(required = false) ShelfStatus skuStatus,
            @RequestParam(required = false) ProductType productType
    ) {
        return ApiResponse.success(queryService.page(new ProductPageQuery(
                page, pageSize, keyword, categoryId, spuStatus, skuStatus, productType
        )));
    }

    @Operation(summary = "查询商品详情")
    @GetMapping("/{id}")
    public ApiResponse<ProductDetailResponse> get(@PathVariable long id) {
        return ApiResponse.success(queryService.get(id));
    }

    @Operation(summary = "新增商品")
    @PostMapping
    public ApiResponse<Long> create(@Valid @RequestBody ProductSaveRequest request) {
        return ApiResponse.success(applicationService.create(request));
    }

    @Operation(summary = "编辑商品")
    @PutMapping("/{id}")
    public ApiResponse<Void> update(
            @PathVariable long id,
            @Valid @RequestBody ProductSaveRequest request
    ) {
        applicationService.update(id, request);
        return ApiResponse.success(null);
    }

    @Operation(summary = "修改商品上下架状态")
    @PutMapping("/{id}/status")
    public ApiResponse<Void> updateStatus(
            @PathVariable long id,
            @Valid @RequestBody ProductStatusRequest request
    ) {
        applicationService.updateStatus(id, request.version(), request.status());
        return ApiResponse.success(null);
    }

    @Operation(summary = "删除商品")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable long id,
            @RequestParam @Min(value = 0, message = "版本号不正确") int version
    ) {
        applicationService.delete(id, version);
        return ApiResponse.success(null);
    }
}
