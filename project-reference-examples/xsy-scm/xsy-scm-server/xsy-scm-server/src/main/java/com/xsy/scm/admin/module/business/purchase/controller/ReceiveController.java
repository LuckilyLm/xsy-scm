package com.xsy.scm.admin.module.business.purchase.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.admin.constant.AdminSwaggerTagConst;
import com.xsy.scm.admin.module.business.purchase.domain.form.ReceiveAddForm;
import com.xsy.scm.admin.module.business.purchase.domain.form.ReceiveNoOrderAddForm;
import com.xsy.scm.admin.module.business.purchase.domain.form.ReceiveQueryForm;
import com.xsy.scm.admin.module.business.purchase.domain.form.ReceiveRelateForm;
import com.xsy.scm.admin.module.business.purchase.domain.form.ReceiveUpdateForm;
import com.xsy.scm.admin.module.business.purchase.domain.vo.ReceiveVO;
import com.xsy.scm.admin.module.business.purchase.service.ReceiveService;
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
 * 采购收货单 Controller
 *
 * @author xsy-scm
 */
@RestController
@Tag(name = AdminSwaggerTagConst.SupplyChain.SCM_PURCHASE)
public class ReceiveController {

    @Resource
    private ReceiveService receiveService;

    @Operation(summary = "分页查询收货单 @author xsy-scm")
    @PostMapping("/purchase/receive/query")
    @SaCheckPermission("purchase:receive:query")
    public ResponseDTO<PageResult<ReceiveVO>> query(@RequestBody @Valid ReceiveQueryForm queryForm) {
        return receiveService.query(queryForm);
    }

    @Operation(summary = "添加收货单 @author xsy-scm")
    @PostMapping("/purchase/receive/add")
    @SaCheckPermission("purchase:receive:add")
    public ResponseDTO<String> add(@RequestBody @Valid ReceiveAddForm addForm) {
        return receiveService.add(addForm);
    }

    @Operation(summary = "更新收货单 @author xsy-scm")
    @PostMapping("/purchase/receive/update")
    @SaCheckPermission("purchase:receive:update")
    public ResponseDTO<String> update(@RequestBody @Valid ReceiveUpdateForm updateForm) {
        return receiveService.update(updateForm);
    }

    @Operation(summary = "入库确认：已收→已入库并写库存流水（Q5） @author xsy-scm")
    @PostMapping("/purchase/receive/confirmInbound/{receiveId}")
    @SaCheckPermission("purchase:receive:confirmInbound")
    public ResponseDTO<String> confirmInbound(@PathVariable Long receiveId) {
        return receiveService.confirmInbound(receiveId);
    }

    @Operation(summary = "无单收货（现场收货，不关联采购单） @author xsy-scm")
    @PostMapping("/purchase/receive/addNoOrder")
    @SaCheckPermission("purchase:receive:addNoOrder")
    public ResponseDTO<String> addNoOrder(@RequestBody @Valid ReceiveNoOrderAddForm addForm) {
        return receiveService.addNoOrder(addForm);
    }

    @Operation(summary = "无单收货补关联采购单 @author xsy-scm")
    @PostMapping("/purchase/receive/relatePurchase")
    @SaCheckPermission("purchase:receive:relatePurchase")
    public ResponseDTO<String> relatePurchase(@RequestBody @Valid ReceiveRelateForm relateForm) {
        return receiveService.relatePurchase(relateForm);
    }

    @Operation(summary = "删除收货单 @author xsy-scm")
    @GetMapping("/purchase/receive/delete/{receiveId}")
    @SaCheckPermission("purchase:receive:delete")
    public ResponseDTO<String> delete(@PathVariable Long receiveId) {
        return receiveService.delete(receiveId);
    }

    @Operation(summary = "批量删除收货单 @author xsy-scm")
    @PostMapping("/purchase/receive/batchDelete")
    @SaCheckPermission("purchase:receive:batchDelete")
    public ResponseDTO<String> batchDelete(@RequestBody @Valid ValidateList<Long> idList) {
        return receiveService.batchDelete(idList);
    }
}
