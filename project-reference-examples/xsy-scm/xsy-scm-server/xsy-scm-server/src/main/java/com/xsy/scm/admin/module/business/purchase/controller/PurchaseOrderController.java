package com.xsy.scm.admin.module.business.purchase.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.admin.constant.AdminSwaggerTagConst;
import com.xsy.scm.admin.module.business.purchase.domain.form.PurchaseOrderAddForm;
import com.xsy.scm.admin.module.business.purchase.domain.form.PurchaseOrderQueryForm;
import com.xsy.scm.admin.module.business.purchase.domain.form.PurchaseOrderUpdateForm;
import com.xsy.scm.admin.module.business.purchase.domain.vo.PurchaseOrderVO;
import com.xsy.scm.admin.module.business.purchase.domain.form.PurchaseGenerateForm;
import com.xsy.scm.admin.module.business.purchase.domain.vo.PurchaseGeneratePreviewVO;
import com.xsy.scm.admin.module.business.purchase.service.PurchaseOrderService;
import com.xsy.scm.admin.module.business.purchase.service.PurchaseOrderGenerateService;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.ResponseDTO;
import com.xsy.scm.base.common.domain.ValidateList;
import java.util.List;
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
 * 采购单 Controller
 *
 * <p>URL 风格沿用项目动作式约定：/purchase/query、/purchase/add、/purchase/update、/purchase/delete/{id}</p>
 *
 * @author xsy-scm
 */
@RestController
@Tag(name = AdminSwaggerTagConst.SupplyChain.SCM_PURCHASE)
public class PurchaseOrderController {

    @Resource
    private PurchaseOrderService purchaseOrderService;

    @Resource
    private PurchaseOrderGenerateService purchaseOrderGenerateService;

    @Operation(summary = "分页查询采购单 @author xsy-scm")
    @PostMapping("/purchase/query")
    @SaCheckPermission("purchase:query")
    public ResponseDTO<PageResult<PurchaseOrderVO>> query(@RequestBody @Valid PurchaseOrderQueryForm queryForm) {
        return purchaseOrderService.query(queryForm);
    }

    @Operation(summary = "添加采购单 @author xsy-scm")
    @PostMapping("/purchase/add")
    @SaCheckPermission("purchase:add")
    public ResponseDTO<String> add(@RequestBody @Valid PurchaseOrderAddForm addForm) {
        return purchaseOrderService.add(addForm);
    }

    @Operation(summary = "更新采购单 @author xsy-scm")
    @PostMapping("/purchase/update")
    @SaCheckPermission("purchase:update")
    public ResponseDTO<String> update(@RequestBody @Valid PurchaseOrderUpdateForm updateForm) {
        return purchaseOrderService.update(updateForm);
    }

    @Operation(summary = "删除采购单 @author xsy-scm")
    @GetMapping("/purchase/delete/{purchaseId}")
    @SaCheckPermission("purchase:delete")
    public ResponseDTO<String> delete(@PathVariable Long purchaseId) {
        return purchaseOrderService.delete(purchaseId);
    }

    @Operation(summary = "批量删除采购单 @author xsy-scm")
    @PostMapping("/purchase/batchDelete")
    @SaCheckPermission("purchase:batchDelete")
    public ResponseDTO<String> batchDelete(@RequestBody @Valid ValidateList<Long> idList) {
        return purchaseOrderService.batchDelete(idList);
    }

    @Operation(summary = "采购单生成-预览汇总 @author xsy-scm")
    @PostMapping("/purchase/generate/preview")
    @SaCheckPermission("purchase:generate:preview")
    public ResponseDTO<List<PurchaseGeneratePreviewVO>> generatePreview(@RequestBody @Valid PurchaseGenerateForm form) {
        return purchaseOrderGenerateService.preview(form);
    }

    @Operation(summary = "采购单生成-按汇总落库 @author xsy-scm")
    @PostMapping("/purchase/generate")
    @SaCheckPermission("purchase:generate")
    public ResponseDTO<String> generate(@RequestBody @Valid PurchaseGenerateForm form) {
        return purchaseOrderGenerateService.generate(form);
    }

    @Operation(summary = "接单：待接单→采购中 @author xsy-scm")
    @PostMapping("/purchase/accept/{purchaseId}")
    @SaCheckPermission("purchase:accept")
    public ResponseDTO<String> accept(@PathVariable Long purchaseId) {
        return purchaseOrderService.accept(purchaseId);
    }
}
