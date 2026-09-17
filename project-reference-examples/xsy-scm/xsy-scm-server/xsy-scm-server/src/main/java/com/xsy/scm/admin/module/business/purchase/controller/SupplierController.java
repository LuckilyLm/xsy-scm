package com.xsy.scm.admin.module.business.purchase.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.admin.constant.AdminSwaggerTagConst;
import com.xsy.scm.admin.module.business.purchase.domain.form.SupplierAddForm;
import com.xsy.scm.admin.module.business.purchase.domain.form.SupplierQueryForm;
import com.xsy.scm.admin.module.business.purchase.domain.form.SupplierUpdateForm;
import com.xsy.scm.admin.module.business.purchase.domain.vo.SupplierVO;
import com.xsy.scm.admin.module.business.purchase.service.SupplierService;
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
 * 供应商档案 Controller
 *
 * <p>供应商为主数据，URL 前缀使用 /supplier/，区别于商品维度的 /product/supplier/。</p>
 *
 * @author xsy-scm
 */
@RestController
@Tag(name = AdminSwaggerTagConst.SupplyChain.SCM_PURCHASE)
public class SupplierController {

    @Resource
    private SupplierService supplierService;

    @Operation(summary = "分页查询供应商 @author xsy-scm")
    @PostMapping("/supplier/query")
    @SaCheckPermission("supplier:query")
    public ResponseDTO<PageResult<SupplierVO>> query(@RequestBody @Valid SupplierQueryForm queryForm) {
        return supplierService.query(queryForm);
    }

    @Operation(summary = "添加供应商 @author xsy-scm")
    @PostMapping("/supplier/add")
    @SaCheckPermission("supplier:add")
    public ResponseDTO<String> add(@RequestBody @Valid SupplierAddForm addForm) {
        return supplierService.add(addForm);
    }

    @Operation(summary = "更新供应商 @author xsy-scm")
    @PostMapping("/supplier/update")
    @SaCheckPermission("supplier:update")
    public ResponseDTO<String> update(@RequestBody @Valid SupplierUpdateForm updateForm) {
        return supplierService.update(updateForm);
    }

    @Operation(summary = "删除供应商 @author xsy-scm")
    @GetMapping("/supplier/delete/{supplierId}")
    @SaCheckPermission("supplier:delete")
    public ResponseDTO<String> delete(@PathVariable Long supplierId) {
        return supplierService.delete(supplierId);
    }

    @Operation(summary = "批量删除供应商 @author xsy-scm")
    @PostMapping("/supplier/batchDelete")
    @SaCheckPermission("supplier:batchDelete")
    public ResponseDTO<String> batchDelete(@RequestBody @Valid ValidateList<Long> idList) {
        return supplierService.batchDelete(idList);
    }

    @Operation(summary = "查询所有供应商 @author xsy-scm")
    @GetMapping("/supplier/queryAll")
    @SaCheckPermission("supplier:query")
    public ResponseDTO<List<SupplierVO>> queryAll() {
        return supplierService.queryAll();
    }
}
