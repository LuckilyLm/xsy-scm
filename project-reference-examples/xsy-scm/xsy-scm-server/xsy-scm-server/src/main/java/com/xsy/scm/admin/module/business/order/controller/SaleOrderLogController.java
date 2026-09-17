package com.xsy.scm.admin.module.business.order.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.admin.constant.AdminSwaggerTagConst;
import com.xsy.scm.admin.module.business.order.domain.form.SaleOrderLogAddForm;
import com.xsy.scm.admin.module.business.order.domain.form.SaleOrderLogQueryForm;
import com.xsy.scm.admin.module.business.order.domain.vo.SaleOrderLogVO;
import com.xsy.scm.admin.module.business.order.service.SaleOrderLogService;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.ResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单操作日志 Controller
 *
 * <p>日志仅查询与写入。URL 风格：/order/log/query、/order/log/add。</p>
 *
 * @author xsy-scm
 */
@RestController
@Tag(name = AdminSwaggerTagConst.SupplyChain.SCM_ORDER)
public class SaleOrderLogController {

    @Resource
    private SaleOrderLogService saleOrderLogService;

    @Operation(summary = "分页查询订单操作日志 @author xsy-scm")
    @PostMapping("/order/log/query")
    @SaCheckPermission("order:log:query")
    public ResponseDTO<PageResult<SaleOrderLogVO>> query(@RequestBody @Valid SaleOrderLogQueryForm queryForm) {
        return saleOrderLogService.query(queryForm);
    }

    @Operation(summary = "写入订单操作日志 @author xsy-scm")
    @PostMapping("/order/log/add")
    @SaCheckPermission("order:log:add")
    public ResponseDTO<String> add(@RequestBody @Valid SaleOrderLogAddForm addForm) {
        return saleOrderLogService.add(addForm);
    }
}
