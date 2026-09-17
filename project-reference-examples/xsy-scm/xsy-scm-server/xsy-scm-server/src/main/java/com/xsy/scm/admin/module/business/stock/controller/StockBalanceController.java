package com.xsy.scm.admin.module.business.stock.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.admin.constant.AdminSwaggerTagConst;
import com.xsy.scm.admin.module.business.stock.domain.form.StockBalanceQueryForm;
import com.xsy.scm.admin.module.business.stock.domain.vo.StockBalanceVO;
import com.xsy.scm.admin.module.business.stock.service.StockBalanceService;
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
 * 库存余额 Controller（只读）
 *
 * <p>仅提供查询，不允许通过接口直接变更库存余额。</p>
 *
 * @author xsy-scm
 */
@RestController
@Tag(name = AdminSwaggerTagConst.SupplyChain.SCM_STOCK)
public class StockBalanceController {

    @Resource
    private StockBalanceService stockBalanceService;

    @Operation(summary = "分页查询库存余额 @author xsy-scm")
    @PostMapping("/stock/balance/query")
    @SaCheckPermission("stock:balance:query")
    public ResponseDTO<PageResult<StockBalanceVO>> query(@RequestBody @Valid StockBalanceQueryForm queryForm) {
        return stockBalanceService.query(queryForm);
    }
}
