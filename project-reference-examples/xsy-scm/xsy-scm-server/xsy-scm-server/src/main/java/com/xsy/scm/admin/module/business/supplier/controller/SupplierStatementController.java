package com.xsy.scm.admin.module.business.supplier.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.admin.constant.AdminSwaggerTagConst;
import com.xsy.scm.admin.module.business.supplier.domain.form.SupplierStatementAddForm;
import com.xsy.scm.admin.module.business.supplier.domain.form.SupplierStatementConfirmForm;
import com.xsy.scm.admin.module.business.supplier.domain.form.SupplierStatementQueryForm;
import com.xsy.scm.admin.module.business.supplier.domain.form.SupplierStatementUpdateForm;
import com.xsy.scm.admin.module.business.supplier.domain.vo.SupplierStatementVO;
import com.xsy.scm.admin.module.business.supplier.service.SupplierStatementService;
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
 * 供应商对账单 Controller
 *
 * @author xsy-scm
 */
@RestController
@Tag(name = AdminSwaggerTagConst.SupplyChain.SCM_PURCHASE)
public class SupplierStatementController {

    @Resource
    private SupplierStatementService supplierStatementService;

    @Operation(summary = "分页查询供应商对账单 @author xsy-scm")
    @PostMapping("/supplier/statement/query")
    @SaCheckPermission("supplierStatement:query")
    public ResponseDTO<PageResult<SupplierStatementVO>> query(@RequestBody @Valid SupplierStatementQueryForm queryForm) {
        return supplierStatementService.query(queryForm);
    }

    @Operation(summary = "添加供应商对账单 @author xsy-scm")
    @PostMapping("/supplier/statement/add")
    @SaCheckPermission("supplierStatement:add")
    public ResponseDTO<String> add(@RequestBody @Valid SupplierStatementAddForm addForm) {
        return supplierStatementService.add(addForm);
    }

    @Operation(summary = "更新供应商对账单 @author xsy-scm")
    @PostMapping("/supplier/statement/update")
    @SaCheckPermission("supplierStatement:update")
    public ResponseDTO<String> update(@RequestBody @Valid SupplierStatementUpdateForm updateForm) {
        return supplierStatementService.update(updateForm);
    }

    @Operation(summary = "供应商确认/驳回对账单 @author xsy-scm")
    @PostMapping("/supplier/statement/confirm")
    @SaCheckPermission("supplierStatement:confirm")
    public ResponseDTO<String> confirm(@RequestBody @Valid SupplierStatementConfirmForm confirmForm) {
        return supplierStatementService.confirm(confirmForm);
    }

    @Operation(summary = "结算供应商对账单 @author xsy-scm")
    @PostMapping("/supplier/statement/settle/{statementId}")
    @SaCheckPermission("supplierStatement:settle")
    public ResponseDTO<String> settle(@PathVariable Long statementId) {
        return supplierStatementService.settle(statementId);
    }

    @Operation(summary = "删除供应商对账单 @author xsy-scm")
    @GetMapping("/supplier/statement/delete/{statementId}")
    @SaCheckPermission("supplierStatement:delete")
    public ResponseDTO<String> delete(@PathVariable Long statementId) {
        return supplierStatementService.delete(statementId);
    }

    @Operation(summary = "批量删除供应商对账单 @author xsy-scm")
    @PostMapping("/supplier/statement/batchDelete")
    @SaCheckPermission("supplierStatement:batchDelete")
    public ResponseDTO<String> batchDelete(@RequestBody @Valid ValidateList<Long> idList) {
        return supplierStatementService.batchDelete(idList);
    }
}
