package com.xsy.scm.admin.module.business.purchase.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.admin.constant.AdminSwaggerTagConst;
import com.xsy.scm.admin.module.business.purchase.domain.form.PurchaseItemAddForm;
import com.xsy.scm.admin.module.business.purchase.domain.form.PurchaseItemAssignSupplierForm;
import com.xsy.scm.admin.module.business.purchase.domain.form.PurchaseItemQueryForm;
import com.xsy.scm.admin.module.business.purchase.domain.form.PurchaseItemUpdateForm;
import com.xsy.scm.admin.module.business.purchase.domain.vo.PurchaseItemVO;
import com.xsy.scm.admin.module.business.purchase.service.PurchaseItemService;
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
 * 采购明细 Controller
 *
 * @author xsy-scm
 */
@RestController
@Tag(name = AdminSwaggerTagConst.SupplyChain.SCM_PURCHASE)
public class PurchaseItemController {

    @Resource
    private PurchaseItemService purchaseItemService;

    @Operation(summary = "分页查询采购明细 @author xsy-scm")
    @PostMapping("/purchase/item/query")
    @SaCheckPermission("purchase:item:query")
    public ResponseDTO<PageResult<PurchaseItemVO>> query(@RequestBody @Valid PurchaseItemQueryForm queryForm) {
        return purchaseItemService.query(queryForm);
    }

    @Operation(summary = "添加采购明细 @author xsy-scm")
    @PostMapping("/purchase/item/add")
    @SaCheckPermission("purchase:item:add")
    public ResponseDTO<String> add(@RequestBody @Valid PurchaseItemAddForm addForm) {
        return purchaseItemService.add(addForm);
    }

    @Operation(summary = "更新采购明细 @author xsy-scm")
    @PostMapping("/purchase/item/update")
    @SaCheckPermission("purchase:item:update")
    public ResponseDTO<String> update(@RequestBody @Valid PurchaseItemUpdateForm updateForm) {
        return purchaseItemService.update(updateForm);
    }

    @Operation(summary = "删除采购明细 @author xsy-scm")
    @GetMapping("/purchase/item/delete/{itemId}")
    @SaCheckPermission("purchase:item:delete")
    public ResponseDTO<String> delete(@PathVariable Long itemId) {
        return purchaseItemService.delete(itemId);
    }

    @Operation(summary = "采购单改绑供应商（供应商分拣实时分配） @author xsy-scm")
    @PostMapping("/purchase/item/reassignSupplier")
    @SaCheckPermission("purchase:item:reassignSupplier")
    public ResponseDTO<String> reassignSupplier(@RequestBody @Valid PurchaseItemAssignSupplierForm form) {
        return purchaseItemService.reassignSupplier(form);
    }

    @Operation(summary = "批量删除采购明细 @author xsy-scm")
    @PostMapping("/purchase/item/batchDelete")
    @SaCheckPermission("purchase:item:batchDelete")
    public ResponseDTO<String> batchDelete(@RequestBody @Valid ValidateList<Long> idList) {
        return purchaseItemService.batchDelete(idList);
    }
}
