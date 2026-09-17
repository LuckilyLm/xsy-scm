package com.xsy.scm.admin.module.business.stock.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.admin.constant.AdminSwaggerTagConst;
import com.xsy.scm.admin.module.business.stock.domain.form.StockFlowQueryForm;
import com.xsy.scm.admin.module.business.stock.domain.vo.StockFlowVO;
import com.xsy.scm.admin.module.business.stock.service.StockFlowService;
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
 * 库存流水 Controller（只读）
 *
 * <p>仅提供查询，流水由库存业务层生成。</p>
 *
 * @author xsy-scm
 */
@RestController
@Tag(name = AdminSwaggerTagConst.SupplyChain.SCM_STOCK)
public class StockFlowController {

    @Resource
    private StockFlowService stockFlowService;

    @Operation(summary = "分页查询库存流水 @author xsy-scm")
    @PostMapping("/stock/flow/query")
    @SaCheckPermission("stock:flow:query")
    public ResponseDTO<PageResult<StockFlowVO>> query(@RequestBody @Valid StockFlowQueryForm queryForm) {
        return stockFlowService.query(queryForm);
    }
}
