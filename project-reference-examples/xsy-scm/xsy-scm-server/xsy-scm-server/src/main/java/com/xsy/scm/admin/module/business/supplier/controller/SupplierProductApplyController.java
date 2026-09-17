package com.xsy.scm.admin.module.business.supplier.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.admin.constant.AdminSwaggerTagConst;
import com.xsy.scm.admin.module.business.supplier.domain.form.SupplierProductApplyAddForm;
import com.xsy.scm.admin.module.business.supplier.domain.form.SupplierProductApplyAuditForm;
import com.xsy.scm.admin.module.business.supplier.domain.form.SupplierProductApplyQueryForm;
import com.xsy.scm.admin.module.business.supplier.domain.form.SupplierProductApplyUpdateForm;
import com.xsy.scm.admin.module.business.supplier.domain.vo.SupplierProductApplyVO;
import com.xsy.scm.admin.module.business.supplier.service.SupplierProductApplyService;
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
 * 供应商商品提报 Controller
 *
 * @author xsy-scm
 */
@RestController
@Tag(name = AdminSwaggerTagConst.SupplyChain.SCM_PURCHASE)
public class SupplierProductApplyController {

    @Resource
    private SupplierProductApplyService supplierProductApplyService;

    @Operation(summary = "分页查询供应商商品提报 @author xsy-scm")
    @PostMapping("/supplier/productApply/query")
    @SaCheckPermission("supplierProductApply:query")
    public ResponseDTO<PageResult<SupplierProductApplyVO>> query(@RequestBody @Valid SupplierProductApplyQueryForm queryForm) {
        return supplierProductApplyService.query(queryForm);
    }

    @Operation(summary = "添加供应商商品提报 @author xsy-scm")
    @PostMapping("/supplier/productApply/add")
    @SaCheckPermission("supplierProductApply:add")
    public ResponseDTO<String> add(@RequestBody @Valid SupplierProductApplyAddForm addForm) {
        return supplierProductApplyService.add(addForm);
    }

    @Operation(summary = "更新供应商商品提报 @author xsy-scm")
    @PostMapping("/supplier/productApply/update")
    @SaCheckPermission("supplierProductApply:update")
    public ResponseDTO<String> update(@RequestBody @Valid SupplierProductApplyUpdateForm updateForm) {
        return supplierProductApplyService.update(updateForm);
    }

    @Operation(summary = "审核供应商商品提报 @author xsy-scm")
    @PostMapping("/supplier/productApply/audit")
    @SaCheckPermission("supplierProductApply:audit")
    public ResponseDTO<String> audit(@RequestBody @Valid SupplierProductApplyAuditForm auditForm) {
        return supplierProductApplyService.audit(auditForm);
    }

    @Operation(summary = "删除供应商商品提报 @author xsy-scm")
    @GetMapping("/supplier/productApply/delete/{applyId}")
    @SaCheckPermission("supplierProductApply:delete")
    public ResponseDTO<String> delete(@PathVariable Long applyId) {
        return supplierProductApplyService.delete(applyId);
    }

    @Operation(summary = "批量删除供应商商品提报 @author xsy-scm")
    @PostMapping("/supplier/productApply/batchDelete")
    @SaCheckPermission("supplierProductApply:batchDelete")
    public ResponseDTO<String> batchDelete(@RequestBody @Valid ValidateList<Long> idList) {
        return supplierProductApplyService.batchDelete(idList);
    }
}
