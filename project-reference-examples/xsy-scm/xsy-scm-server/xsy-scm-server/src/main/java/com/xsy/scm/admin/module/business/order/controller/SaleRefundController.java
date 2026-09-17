package com.xsy.scm.admin.module.business.order.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.admin.constant.AdminSwaggerTagConst;
import com.xsy.scm.admin.module.business.order.domain.form.SaleRefundAddForm;
import com.xsy.scm.admin.module.business.order.domain.form.SaleRefundQueryForm;
import com.xsy.scm.admin.module.business.order.domain.form.SaleRefundUpdateForm;
import com.xsy.scm.admin.module.business.order.domain.vo.SaleRefundVO;
import com.xsy.scm.admin.module.business.order.service.SaleRefundService;
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
 * 销售退款单 Controller
 *
 * @author xsy-scm
 */
@RestController
@Tag(name = AdminSwaggerTagConst.SupplyChain.SCM_ORDER)
public class SaleRefundController {

    @Resource
    private SaleRefundService saleRefundService;

    @Operation(summary = "分页查询退款单 @author xsy-scm")
    @PostMapping("/order/refund/query")
    @SaCheckPermission("order:refund:query")
    public ResponseDTO<PageResult<SaleRefundVO>> query(@RequestBody @Valid SaleRefundQueryForm queryForm) {
        return saleRefundService.query(queryForm);
    }

    @Operation(summary = "添加退款单 @author xsy-scm")
    @PostMapping("/order/refund/add")
    @SaCheckPermission("order:refund:add")
    public ResponseDTO<String> add(@RequestBody @Valid SaleRefundAddForm addForm) {
        return saleRefundService.add(addForm);
    }

    @Operation(summary = "更新退款单 @author xsy-scm")
    @PostMapping("/order/refund/update")
    @SaCheckPermission("order:refund:update")
    public ResponseDTO<String> update(@RequestBody @Valid SaleRefundUpdateForm updateForm) {
        return saleRefundService.update(updateForm);
    }

    @Operation(summary = "删除退款单 @author xsy-scm")
    @GetMapping("/order/refund/delete/{refundId}")
    @SaCheckPermission("order:refund:delete")
    public ResponseDTO<String> delete(@PathVariable Long refundId) {
        return saleRefundService.delete(refundId);
    }

    @Operation(summary = "批量删除退款单 @author xsy-scm")
    @PostMapping("/order/refund/batchDelete")
    @SaCheckPermission("order:refund:batchDelete")
    public ResponseDTO<String> batchDelete(@RequestBody @Valid ValidateList<Long> idList) {
        return saleRefundService.batchDelete(idList);
    }
}
