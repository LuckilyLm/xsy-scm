package com.xsy.scm.product.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.product.domain.form.ProductDeleteForm;
import com.xsy.scm.product.domain.form.ProductSpuAddForm;
import com.xsy.scm.product.domain.form.ProductSpuBatchCategoryForm;
import com.xsy.scm.product.domain.form.ProductSpuBatchStatusForm;
import com.xsy.scm.product.domain.form.ProductSpuBatchTagForm;
import com.xsy.scm.product.domain.form.ProductSpuQueryForm;
import com.xsy.scm.product.domain.form.ProductSpuUpdateForm;
import com.xsy.scm.product.domain.form.ProductStatusForm;
import com.xsy.scm.product.domain.vo.ProductBatchResultVO;
import com.xsy.scm.product.domain.vo.ProductSpuDetailVO;
import com.xsy.scm.product.domain.vo.ProductSpuVO;
import com.xsy.scm.product.service.ProductBatchService;
import com.xsy.scm.product.service.ProductQueryService;
import com.xsy.scm.product.service.ProductSpuService;
import com.xsy.scm.product.permission.ProductPermission;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

@RestController
@RequestMapping("/scm/product")
@Tag(name = "SCM 商品档案")
@RequiredArgsConstructor
public class ProductController {
    private final ProductSpuService productSpuService;
    private final ProductQueryService productQueryService;
    private final ProductBatchService productBatchService;

    @PostMapping("/query")
    @SaCheckPermission(ProductPermission.QUERY)
    public ResponseDTO<PageResult<ProductSpuVO>> query(@Valid @RequestBody ProductSpuQueryForm form) {
        return ResponseDTO.ok(productQueryService.query(form));
    }

    @GetMapping("/detail/{spuId}")
    @SaCheckPermission(ProductPermission.QUERY)
    public ResponseDTO<ProductSpuDetailVO> detail(@PathVariable Long spuId) {
        return ResponseDTO.ok(productQueryService.detail(spuId));
    }

    @PostMapping("/add")
    @SaCheckPermission(ProductPermission.ADD)
    @OperateLog
    public ResponseDTO<Long> add(@Valid @RequestBody ProductSpuAddForm form) {
        if (!form.getImages().isEmpty()) StpUtil.checkPermission(ProductPermission.IMAGE);
        return ResponseDTO.ok(productSpuService.add(form));
    }

    @PostMapping("/update")
    @SaCheckPermission(ProductPermission.UPDATE)
    @OperateLog
    public ResponseDTO<String> update(@Valid @RequestBody ProductSpuUpdateForm form) {
        var existing = productQueryService.detail(form.getSpuId()).getImages();
        var before = existing.stream().map(i -> Arrays.asList(i.getImageId(), i.getFileKey(), i.getPrimaryFlag(), i.getSortOrder())).toList();
        var after = form.getImages().stream().map(i -> Arrays.asList(i.getImageId(), i.getFileKey(), i.getPrimaryFlag(), i.getSortOrder())).toList();
        if (!before.equals(after)) StpUtil.checkPermission(ProductPermission.IMAGE);
        productSpuService.update(form);
        return ResponseDTO.ok();
    }

    @PostMapping("/updateStatus")
    @SaCheckPermission(ProductPermission.STATUS)
    @OperateLog
    public ResponseDTO<String> updateStatus(@Valid @RequestBody ProductStatusForm form) {
        productSpuService.updateStatus(form);
        return ResponseDTO.ok();
    }

    @PostMapping("/delete")
    @SaCheckPermission(ProductPermission.DELETE)
    @OperateLog
    public ResponseDTO<String> delete(@Valid @RequestBody ProductDeleteForm form) {
        productSpuService.delete(form);
        return ResponseDTO.ok();
    }

    @PostMapping("/batch/updateStatus")
    @SaCheckPermission(ProductPermission.BATCH)
    @OperateLog
    public ResponseDTO<ProductBatchResultVO> batchStatus(@Valid @RequestBody ProductSpuBatchStatusForm form) {
        return ResponseDTO.ok(productBatchService.updateStatus(form));
    }

    @PostMapping("/batch/updateCategory")
    @SaCheckPermission(ProductPermission.BATCH)
    @OperateLog
    public ResponseDTO<ProductBatchResultVO> batchCategory(@Valid @RequestBody ProductSpuBatchCategoryForm form) {
        return ResponseDTO.ok(productBatchService.updateCategory(form));
    }

    @PostMapping("/batch/updateTags")
    @SaCheckPermission(ProductPermission.BATCH)
    @OperateLog
    public ResponseDTO<ProductBatchResultVO> batchTags(@Valid @RequestBody ProductSpuBatchTagForm form) {
        return ResponseDTO.ok(productBatchService.updateTags(form));
    }
}
