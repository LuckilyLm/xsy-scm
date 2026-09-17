package com.xsy.scm.admin.module.business.supplier.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.admin.constant.AdminSwaggerTagConst;
import com.xsy.scm.admin.module.business.supplier.domain.form.SupplierAccountAddForm;
import com.xsy.scm.admin.module.business.supplier.domain.form.SupplierAccountQueryForm;
import com.xsy.scm.admin.module.business.supplier.domain.form.SupplierAccountUpdateForm;
import com.xsy.scm.admin.module.business.supplier.domain.vo.SupplierAccountVO;
import com.xsy.scm.admin.module.business.supplier.service.SupplierAccountService;
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
 * 供应商账号 Controller
 *
 * @author xsy-scm
 */
@RestController
@Tag(name = AdminSwaggerTagConst.SupplyChain.SCM_PURCHASE)
public class SupplierAccountController {

    @Resource
    private SupplierAccountService supplierAccountService;

    @Operation(summary = "分页查询供应商账号 @author xsy-scm")
    @PostMapping("/supplier/account/query")
    @SaCheckPermission("supplierAccount:query")
    public ResponseDTO<PageResult<SupplierAccountVO>> query(@RequestBody @Valid SupplierAccountQueryForm queryForm) {
        return supplierAccountService.query(queryForm);
    }

    @Operation(summary = "添加供应商账号 @author xsy-scm")
    @PostMapping("/supplier/account/add")
    @SaCheckPermission("supplierAccount:add")
    public ResponseDTO<String> add(@RequestBody @Valid SupplierAccountAddForm addForm) {
        return supplierAccountService.add(addForm);
    }

    @Operation(summary = "更新供应商账号 @author xsy-scm")
    @PostMapping("/supplier/account/update")
    @SaCheckPermission("supplierAccount:update")
    public ResponseDTO<String> update(@RequestBody @Valid SupplierAccountUpdateForm updateForm) {
        return supplierAccountService.update(updateForm);
    }

    @Operation(summary = "删除供应商账号 @author xsy-scm")
    @GetMapping("/supplier/account/delete/{accountId}")
    @SaCheckPermission("supplierAccount:delete")
    public ResponseDTO<String> delete(@PathVariable Long accountId) {
        return supplierAccountService.delete(accountId);
    }

    @Operation(summary = "批量删除供应商账号 @author xsy-scm")
    @PostMapping("/supplier/account/batchDelete")
    @SaCheckPermission("supplierAccount:batchDelete")
    public ResponseDTO<String> batchDelete(@RequestBody @Valid ValidateList<Long> idList) {
        return supplierAccountService.batchDelete(idList);
    }
}
