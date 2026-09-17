package com.xsy.scm.admin.module.business.product.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import com.xsy.scm.admin.constant.AdminSwaggerTagConst;
import com.xsy.scm.admin.module.business.product.domain.form.ProductCategoryTreeQueryForm;
import com.xsy.scm.admin.module.business.product.domain.vo.ProductCategoryTreeVO;
import com.xsy.scm.admin.module.business.product.service.ProductCategoryService;
import com.xsy.scm.base.common.domain.ResponseDTO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 产品分类
 *
 * @author xsy-scm
 */
@RestController
@Tag(name = AdminSwaggerTagConst.SupplyChain.SCM_PRODUCT)
public class ProductCategoryController {

    @Resource
    private ProductCategoryService productCategoryService;

    @Operation(summary = "查询产品分类层级树 @author xsy-scm")
    @PostMapping("/product/category/tree")
    @SaCheckPermission("product:query")
    public ResponseDTO<List<ProductCategoryTreeVO>> queryTree(@RequestBody @Valid ProductCategoryTreeQueryForm queryForm) {
        return productCategoryService.queryTree(queryForm);
    }
}
