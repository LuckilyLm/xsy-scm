package com.xsy.scm.admin.module.business.order.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.admin.constant.AdminSwaggerTagConst;
import com.xsy.scm.admin.module.business.order.domain.form.SaleOrderAddForm;
import com.xsy.scm.admin.module.business.order.domain.form.SaleOrderQueryForm;
import com.xsy.scm.admin.module.business.order.domain.form.SaleOrderUpdateForm;
import com.xsy.scm.admin.module.business.order.domain.vo.SaleOrderVO;
import com.xsy.scm.admin.module.business.order.service.SaleOrderService;
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
 * 销售订单 Controller
 *
 * <p>URL 风格沿用项目动作式约定：/order/query、/order/add、/order/update、/order/delete/{id}</p>
 *
 * @author xsy-scm
 */
@RestController
@Tag(name = AdminSwaggerTagConst.SupplyChain.SCM_ORDER)
public class SaleOrderController {

    @Resource
    private SaleOrderService saleOrderService;

    @Operation(summary = "分页查询销售订单 @author xsy-scm")
    @PostMapping("/order/query")
    @SaCheckPermission("order:query")
    public ResponseDTO<PageResult<SaleOrderVO>> query(@RequestBody @Valid SaleOrderQueryForm queryForm) {
        return saleOrderService.query(queryForm);
    }

    @Operation(summary = "添加销售订单 @author xsy-scm")
    @PostMapping("/order/add")
    @SaCheckPermission("order:add")
    public ResponseDTO<String> add(@RequestBody @Valid SaleOrderAddForm addForm) {
        return saleOrderService.add(addForm);
    }

    @Operation(summary = "更新销售订单 @author xsy-scm")
    @PostMapping("/order/update")
    @SaCheckPermission("order:update")
    public ResponseDTO<String> update(@RequestBody @Valid SaleOrderUpdateForm updateForm) {
        return saleOrderService.update(updateForm);
    }

    @Operation(summary = "删除销售订单 @author xsy-scm")
    @GetMapping("/order/delete/{orderId}")
    @SaCheckPermission("order:delete")
    public ResponseDTO<String> delete(@PathVariable Long orderId) {
        return saleOrderService.delete(orderId);
    }

    @Operation(summary = "批量删除销售订单 @author xsy-scm")
    @PostMapping("/order/batchDelete")
    @SaCheckPermission("order:batchDelete")
    public ResponseDTO<String> batchDelete(@RequestBody @Valid ValidateList<Long> idList) {
        return saleOrderService.batchDelete(idList);
    }

    @Operation(summary = "销售订单发货（触发销售出库，库存反向流水） @author xsy-scm")
    @PostMapping("/order/deliver/{orderId}")
    @SaCheckPermission("order:deliver")
    public ResponseDTO<String> deliver(@PathVariable Long orderId) {
        return saleOrderService.deliver(orderId);
    }

    @Operation(summary = "销售订单确认（草稿/待确认 → 已确认，发货前置动作） @author xsy-scm")
    @PostMapping("/order/confirm/{orderId}")
    @SaCheckPermission("order:confirm")
    public ResponseDTO<String> confirm(@PathVariable Long orderId) {
        return saleOrderService.confirm(orderId);
    }

    @Operation(summary = "销售订单签收（配送中 → 已签收，并生成应收） @author xsy-scm")
    @PostMapping("/order/sign/{orderId}")
    @SaCheckPermission("order:sign")
    public ResponseDTO<String> sign(@PathVariable Long orderId) {
        return saleOrderService.sign(orderId);
    }
}
