package net.lab1024.sa.admin.module.scm.product.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.product.domain.form.*;
import net.lab1024.sa.admin.module.scm.product.domain.vo.*;
import net.lab1024.sa.admin.module.scm.product.service.*;
import net.lab1024.sa.base.common.domain.*;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/scm/product")
@Tag(name="SCM 商品档案")
@RequiredArgsConstructor
public class ProductController {
    private final ProductSpuService service;
    private final ProductQueryService query;
    private final ProductBatchService batch;
    @PostMapping("/query") @SaCheckPermission("scm:product:query")
    public ResponseDTO<PageResult<ProductSpuVO>> query(@Valid @RequestBody ProductSpuQueryForm form) { return ResponseDTO.ok(query.query(form)); }
    @GetMapping("/detail/{spuId}") @SaCheckPermission("scm:product:query")
    public ResponseDTO<ProductSpuDetailVO> detail(@PathVariable Long spuId) { return ResponseDTO.ok(query.detail(spuId)); }
    @PostMapping("/add") @SaCheckPermission("scm:product:add") @OperateLog
    public ResponseDTO<Long> add(@Valid @RequestBody ProductSpuAddForm form) {
        if(!form.getImages().isEmpty()) StpUtil.checkPermission("scm:product:image");
        return ResponseDTO.ok(service.add(form));
    }
    @PostMapping("/update") @SaCheckPermission("scm:product:update") @OperateLog
    public ResponseDTO<String> update(@Valid @RequestBody ProductSpuUpdateForm form) {
        var existing=query.detail(form.getSpuId()).getImages();
        var before=existing.stream().map(i -> Arrays.asList(i.getImageId(),i.getFileKey(),i.getPrimaryFlag(),i.getSortOrder())).toList();
        var after=form.getImages().stream().map(i -> Arrays.asList(i.getImageId(),i.getFileKey(),i.getPrimaryFlag(),i.getSortOrder())).toList();
        if(!before.equals(after)) StpUtil.checkPermission("scm:product:image");
        service.update(form); return ResponseDTO.ok();
    }
    @PostMapping("/updateStatus") @SaCheckPermission("scm:product:status") @OperateLog
    public ResponseDTO<String> updateStatus(@Valid @RequestBody ProductStatusForm form) { service.updateStatus(form); return ResponseDTO.ok(); }
    @PostMapping("/delete") @SaCheckPermission("scm:product:delete") @OperateLog
    public ResponseDTO<String> delete(@Valid @RequestBody ProductDeleteForm form) { service.delete(form); return ResponseDTO.ok(); }
    @PostMapping("/batch/updateStatus") @SaCheckPermission("scm:product:batch") @OperateLog
    public ResponseDTO<ProductBatchResultVO> batchStatus(@Valid @RequestBody ProductSpuBatchStatusForm form) { return ResponseDTO.ok(batch.updateStatus(form)); }
    @PostMapping("/batch/updateCategory") @SaCheckPermission("scm:product:batch") @OperateLog
    public ResponseDTO<ProductBatchResultVO> batchCategory(@Valid @RequestBody ProductSpuBatchCategoryForm form) { return ResponseDTO.ok(batch.updateCategory(form)); }
    @PostMapping("/batch/updateTags") @SaCheckPermission("scm:product:batch") @OperateLog
    public ResponseDTO<ProductBatchResultVO> batchTags(@Valid @RequestBody ProductSpuBatchTagForm form) { return ResponseDTO.ok(batch.updateTags(form)); }
}
