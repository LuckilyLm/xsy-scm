package com.xsy.scm.admin.module.business.order.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.admin.constant.AdminSwaggerTagConst;
import com.xsy.scm.admin.module.business.order.domain.form.SaleOrderItemAddForm;
import com.xsy.scm.admin.module.business.order.domain.form.SaleOrderItemQueryForm;
import com.xsy.scm.admin.module.business.order.domain.form.SaleOrderItemUpdateForm;
import com.xsy.scm.admin.module.business.order.domain.vo.SaleOrderItemVO;
import com.xsy.scm.admin.module.business.order.service.SaleOrderItemService;
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
 * 销售订单明细 Controller
 *
 * @author xsy-scm
 */
@RestController
@Tag(name = AdminSwaggerTagConst.SupplyChain.SCM_ORDER)
public class SaleOrderItemController {

    @Resource
    private SaleOrderItemService saleOrderItemService;

    @Operation(summary = "分页查询订单明细 @author xsy-scm")
    @PostMapping("/order/item/query")
    @SaCheckPermission("order:item:query")
    public ResponseDTO<PageResult<SaleOrderItemVO>> query(@RequestBody @Valid SaleOrderItemQueryForm queryForm) {
        return saleOrderItemService.query(queryForm);
    }

    @Operation(summary = "添加订单明细 @author xsy-scm")
    @PostMapping("/order/item/add")
    @SaCheckPermission("order:item:add")
    public ResponseDTO<String> add(@RequestBody @Valid SaleOrderItemAddForm addForm) {
        return saleOrderItemService.add(addForm);
    }

    @Operation(summary = "更新订单明细 @author xsy-scm")
    @PostMapping("/order/item/update")
    @SaCheckPermission("order:item:update")
    public ResponseDTO<String> update(@RequestBody @Valid SaleOrderItemUpdateForm updateForm) {
        return saleOrderItemService.update(updateForm);
    }

    @Operation(summary = "删除订单明细 @author xsy-scm")
    @GetMapping("/order/item/delete/{itemId}")
    @SaCheckPermission("order:item:delete")
    public ResponseDTO<String> delete(@PathVariable Long itemId) {
        return saleOrderItemService.delete(itemId);
    }

    @Operation(summary = "批量删除订单明细 @author xsy-scm")
    @PostMapping("/order/item/batchDelete")
    @SaCheckPermission("order:item:batchDelete")
    public ResponseDTO<String> batchDelete(@RequestBody @Valid ValidateList<Long> idList) {
        return saleOrderItemService.batchDelete(idList);
    }
}
