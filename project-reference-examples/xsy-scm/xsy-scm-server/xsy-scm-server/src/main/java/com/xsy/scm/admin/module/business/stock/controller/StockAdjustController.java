package com.xsy.scm.admin.module.business.stock.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.admin.constant.AdminSwaggerTagConst;
import com.xsy.scm.admin.module.business.stock.domain.form.StockAdjustAddForm;
import com.xsy.scm.admin.module.business.stock.domain.form.StockAdjustApproveForm;
import com.xsy.scm.admin.module.business.stock.domain.form.StockAdjustQueryForm;
import com.xsy.scm.admin.module.business.stock.domain.form.StockAdjustUpdateForm;
import com.xsy.scm.admin.module.business.stock.domain.vo.StockAdjustVO;
import com.xsy.scm.admin.module.business.stock.service.StockAdjustService;
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
 * 库存调整单 Controller
 *
 * <p>URL 风格沿用项目动作式约定：/stock/adjust/query、/stock/adjust/add、/stock/adjust/update、/stock/adjust/delete/{id}</p>
 *
 * @author xsy-scm
 */
@RestController
@Tag(name = AdminSwaggerTagConst.SupplyChain.SCM_STOCK)
public class StockAdjustController {

    @Resource
    private StockAdjustService stockAdjustService;

    @Operation(summary = "分页查询库存调整单 @author xsy-scm")
    @PostMapping("/stock/adjust/query")
    @SaCheckPermission("stock:adjust:query")
    public ResponseDTO<PageResult<StockAdjustVO>> query(@RequestBody @Valid StockAdjustQueryForm queryForm) {
        return stockAdjustService.query(queryForm);
    }

    @Operation(summary = "添加库存调整单 @author xsy-scm")
    @PostMapping("/stock/adjust/add")
    @SaCheckPermission("stock:adjust:add")
    public ResponseDTO<String> add(@RequestBody @Valid StockAdjustAddForm addForm) {
        return stockAdjustService.add(addForm);
    }

    @Operation(summary = "更新库存调整单 @author xsy-scm")
    @PostMapping("/stock/adjust/update")
    @SaCheckPermission("stock:adjust:update")
    public ResponseDTO<String> update(@RequestBody @Valid StockAdjustUpdateForm updateForm) {
        return stockAdjustService.update(updateForm);
    }

    @Operation(summary = "删除库存调整单 @author xsy-scm")
    @GetMapping("/stock/adjust/delete/{adjustId}")
    @SaCheckPermission("stock:adjust:delete")
    public ResponseDTO<String> delete(@PathVariable Long adjustId) {
        return stockAdjustService.delete(adjustId);
    }

    @Operation(summary = "批量删除库存调整单 @author xsy-scm")
    @PostMapping("/stock/adjust/batchDelete")
    @SaCheckPermission("stock:adjust:batchDelete")
    public ResponseDTO<String> batchDelete(@RequestBody @Valid ValidateList<Long> idList) {
        return stockAdjustService.batchDelete(idList);
    }

    @Operation(summary = "审核通过库存调整单 @author xsy-scm")
    @PostMapping("/stock/adjust/approve")
    @SaCheckPermission("stock:adjust:approve")
    public ResponseDTO<String> approve(@RequestBody @Valid StockAdjustApproveForm form) {
        return stockAdjustService.approve(form);
    }

    @Operation(summary = "驳回库存调整单 @author xsy-scm")
    @PostMapping("/stock/adjust/reject")
    @SaCheckPermission("stock:adjust:reject")
    public ResponseDTO<String> reject(@RequestBody @Valid StockAdjustApproveForm form) {
        return stockAdjustService.reject(form);
    }
}
