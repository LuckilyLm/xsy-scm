package net.lab1024.sa.admin.module.scm.product.controller;
import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.product.domain.form.*;
import net.lab1024.sa.admin.module.scm.product.domain.vo.*;
import net.lab1024.sa.admin.module.scm.product.service.ProductCategoryService;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
@RequestMapping("/scm/product/category")
@Tag(name="SCM 商品分类")
@RequiredArgsConstructor
public class ProductCategoryController {
    private final ProductCategoryService service;
    @PostMapping("/tree") @SaCheckPermission("scm:product:category:query")
    public ResponseDTO<List<ProductCategoryTreeVO>> tree() { return ResponseDTO.ok(service.tree()); }
    @GetMapping("/{categoryId}") @SaCheckPermission("scm:product:category:query")
    public ResponseDTO<ProductCategoryVO> detail(@PathVariable Long categoryId) { return ResponseDTO.ok(service.detail(categoryId)); }
    @PostMapping("/add") @SaCheckPermission("scm:product:category:add") @OperateLog
    public ResponseDTO<Long> add(@Valid @RequestBody ProductCategoryAddForm form) { return ResponseDTO.ok(service.add(form)); }
    @PostMapping("/update") @SaCheckPermission("scm:product:category:update") @OperateLog
    public ResponseDTO<String> update(@Valid @RequestBody ProductCategoryUpdateForm form) { service.update(form); return ResponseDTO.ok(); }
    @PostMapping("/delete") @SaCheckPermission("scm:product:category:delete") @OperateLog
    public ResponseDTO<String> delete(@Valid @RequestBody ProductCategoryDeleteForm form) { service.delete(form); return ResponseDTO.ok(); }
}
