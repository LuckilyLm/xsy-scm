package com.xsy.scm.admin.module.business.product.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.admin.constant.AdminSwaggerTagConst;
import com.xsy.scm.admin.module.business.product.domain.form.ProductAddForm;
import com.xsy.scm.admin.module.business.product.domain.form.ProductQueryForm;
import com.xsy.scm.admin.module.business.product.domain.form.ProductUpdateForm;
import com.xsy.scm.admin.module.business.product.domain.vo.ProductVO;
import com.xsy.scm.admin.module.business.product.service.ProductService;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.ResponseDTO;
import com.xsy.scm.base.common.domain.ValidateList;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商品 Controller
 *
 * <p>URL 风格沿用项目现有动作式约定：/product/query、/product/add、/product/update、/product/delete/{id}</p>
 *
 * @author xsy-scm
 */
@RestController
@Tag(name = AdminSwaggerTagConst.SupplyChain.SCM_PRODUCT)
public class ProductController {

    @Resource
    private ProductService productService;

    @Operation(summary = "分页查询商品 @author xsy-scm")
    @PostMapping("/product/query")
    @SaCheckPermission("product:query")
    public ResponseDTO<PageResult<ProductVO>> query(@RequestBody @Valid ProductQueryForm queryForm) {
        return productService.query(queryForm);
    }

    @Operation(summary = "添加商品 @author xsy-scm")
    @PostMapping("/product/add")
    @SaCheckPermission("product:add")
    public ResponseDTO<String> add(@RequestBody @Valid ProductAddForm addForm) {
        return productService.add(addForm);
    }

    @Operation(summary = "更新商品 @author xsy-scm")
    @PostMapping("/product/update")
    @SaCheckPermission("product:update")
    public ResponseDTO<String> update(@RequestBody @Valid ProductUpdateForm updateForm) {
        return productService.update(updateForm);
    }

    @Operation(summary = "删除商品 @author xsy-scm")
    @GetMapping("/product/delete/{productId}")
    @SaCheckPermission("product:delete")
    public ResponseDTO<String> delete(@PathVariable Long productId) {
        return productService.delete(productId);
    }

    @Operation(summary = "批量删除商品 @author xsy-scm")
    @PostMapping("/product/batchDelete")
    @SaCheckPermission("product:batchDelete")
    public ResponseDTO<String> batchDelete(@RequestBody @Valid ValidateList<Long> idList) {
        return productService.batchDelete(idList);
    }

    @Operation(summary = "查询所有商品 @author xsy-scm")
    @GetMapping("/product/queryAll")
    @SaCheckPermission("product:query")
    public ResponseDTO<List<ProductVO>> queryAll() {
        return productService.queryAll();
    }
}
