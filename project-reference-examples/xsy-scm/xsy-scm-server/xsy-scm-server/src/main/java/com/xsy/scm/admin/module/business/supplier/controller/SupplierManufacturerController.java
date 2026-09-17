package com.xsy.scm.admin.module.business.supplier.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.admin.constant.AdminSwaggerTagConst;
import com.xsy.scm.admin.module.business.supplier.domain.form.SupplierManufacturerAddForm;
import com.xsy.scm.admin.module.business.supplier.domain.form.SupplierManufacturerQueryForm;
import com.xsy.scm.admin.module.business.supplier.domain.form.SupplierManufacturerUpdateForm;
import com.xsy.scm.admin.module.business.supplier.domain.vo.SupplierManufacturerVO;
import com.xsy.scm.admin.module.business.supplier.service.SupplierManufacturerService;
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
 * 供应商厂商信息 Controller
 *
 * @author xsy-scm
 */
@RestController
@Tag(name = AdminSwaggerTagConst.SupplyChain.SCM_PURCHASE)
public class SupplierManufacturerController {

    @Resource
    private SupplierManufacturerService supplierManufacturerService;

    @Operation(summary = "分页查询供应商厂商信息 @author xsy-scm")
    @PostMapping("/supplier/manufacturer/query")
    @SaCheckPermission("supplierManufacturer:query")
    public ResponseDTO<PageResult<SupplierManufacturerVO>> query(@RequestBody @Valid SupplierManufacturerQueryForm queryForm) {
        return supplierManufacturerService.query(queryForm);
    }

    @Operation(summary = "添加供应商厂商信息 @author xsy-scm")
    @PostMapping("/supplier/manufacturer/add")
    @SaCheckPermission("supplierManufacturer:add")
    public ResponseDTO<String> add(@RequestBody @Valid SupplierManufacturerAddForm addForm) {
        return supplierManufacturerService.add(addForm);
    }

    @Operation(summary = "更新供应商厂商信息 @author xsy-scm")
    @PostMapping("/supplier/manufacturer/update")
    @SaCheckPermission("supplierManufacturer:update")
    public ResponseDTO<String> update(@RequestBody @Valid SupplierManufacturerUpdateForm updateForm) {
        return supplierManufacturerService.update(updateForm);
    }

    @Operation(summary = "删除供应商厂商信息 @author xsy-scm")
    @GetMapping("/supplier/manufacturer/delete/{manufacturerId}")
    @SaCheckPermission("supplierManufacturer:delete")
    public ResponseDTO<String> delete(@PathVariable Long manufacturerId) {
        return supplierManufacturerService.delete(manufacturerId);
    }

    @Operation(summary = "批量删除供应商厂商信息 @author xsy-scm")
    @PostMapping("/supplier/manufacturer/batchDelete")
    @SaCheckPermission("supplierManufacturer:batchDelete")
    public ResponseDTO<String> batchDelete(@RequestBody @Valid ValidateList<Long> idList) {
        return supplierManufacturerService.batchDelete(idList);
    }
}
