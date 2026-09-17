package com.xsy.scm.admin.module.business.product.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.admin.constant.AdminSwaggerTagConst;
import com.xsy.scm.admin.module.business.product.domain.form.ProductSupplierAddForm;
import com.xsy.scm.admin.module.business.product.domain.form.ProductSupplierQueryForm;
import com.xsy.scm.admin.module.business.product.domain.form.ProductSupplierUpdateForm;
import com.xsy.scm.admin.module.business.product.domain.vo.ProductSupplierVO;
import com.xsy.scm.admin.module.business.product.service.ProductSupplierService;
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

/**
 * 商品-供应商关系 Controller
 *
 * <p>URL 风格沿用项目动作式约定：/product/supplier/query、/product/supplier/add 等。</p>
 *
 * @author xsy-scm
 */
@RestController
@Tag(name = AdminSwaggerTagConst.SupplyChain.SCM_PRODUCT)
public class ProductSupplierController {

    @Resource
    private ProductSupplierService productSupplierService;

    @Operation(summary = "分页查询商品供应商关系 @author xsy-scm")
    @PostMapping("/product/supplier/query")
    @SaCheckPermission("product:supplier:query")
    public ResponseDTO<PageResult<ProductSupplierVO>> query(@RequestBody @Valid ProductSupplierQueryForm queryForm) {
        return productSupplierService.query(queryForm);
    }

    @Operation(summary = "添加商品供应商关系 @author xsy-scm")
    @PostMapping("/product/supplier/add")
    @SaCheckPermission("product:supplier:add")
    public ResponseDTO<String> add(@RequestBody @Valid ProductSupplierAddForm addForm) {
        return productSupplierService.add(addForm);
    }

    @Operation(summary = "更新商品供应商关系 @author xsy-scm")
    @PostMapping("/product/supplier/update")
    @SaCheckPermission("product:supplier:update")
    public ResponseDTO<String> update(@RequestBody @Valid ProductSupplierUpdateForm updateForm) {
        return productSupplierService.update(updateForm);
    }

    @Operation(summary = "删除商品供应商关系 @author xsy-scm")
    @GetMapping("/product/supplier/delete/{id}")
    @SaCheckPermission("product:supplier:delete")
    public ResponseDTO<String> delete(@PathVariable Long id) {
        return productSupplierService.delete(id);
    }

    @Operation(summary = "批量删除商品供应商关系 @author xsy-scm")
    @PostMapping("/product/supplier/batchDelete")
    @SaCheckPermission("product:supplier:batchDelete")
    public ResponseDTO<String> batchDelete(@RequestBody @Valid ValidateList<Long> idList) {
        return productSupplierService.batchDelete(idList);
    }
}
