package com.xsy.scm.admin.module.business.stock.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.admin.constant.AdminSwaggerTagConst;
import com.xsy.scm.admin.module.business.stock.domain.form.StockCheckAddForm;
import com.xsy.scm.admin.module.business.stock.domain.form.StockCheckQueryForm;
import com.xsy.scm.admin.module.business.stock.domain.form.StockCheckUpdateForm;
import com.xsy.scm.admin.module.business.stock.domain.vo.StockCheckVO;
import com.xsy.scm.admin.module.business.stock.service.StockCheckService;
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
 * 库存盘点单 Controller
 *
 * @author xsy-scm
 */
@RestController
@Tag(name = AdminSwaggerTagConst.SupplyChain.SCM_STOCK)
public class StockCheckController {

    @Resource
    private StockCheckService stockCheckService;

    @Operation(summary = "分页查询盘点单 @author xsy-scm")
    @PostMapping("/stock/check/query")
    @SaCheckPermission("stock:check:query")
    public ResponseDTO<PageResult<StockCheckVO>> query(@RequestBody @Valid StockCheckQueryForm queryForm) {
        return stockCheckService.query(queryForm);
    }

    @Operation(summary = "添加盘点单 @author xsy-scm")
    @PostMapping("/stock/check/add")
    @SaCheckPermission("stock:check:add")
    public ResponseDTO<String> add(@RequestBody @Valid StockCheckAddForm addForm) {
        return stockCheckService.add(addForm);
    }

    @Operation(summary = "更新盘点单 @author xsy-scm")
    @PostMapping("/stock/check/update")
    @SaCheckPermission("stock:check:update")
    public ResponseDTO<String> update(@RequestBody @Valid StockCheckUpdateForm updateForm) {
        return stockCheckService.update(updateForm);
    }

    @Operation(summary = "删除盘点单 @author xsy-scm")
    @GetMapping("/stock/check/delete/{checkId}")
    @SaCheckPermission("stock:check:delete")
    public ResponseDTO<String> delete(@PathVariable Long checkId) {
        return stockCheckService.delete(checkId);
    }

    @Operation(summary = "批量删除盘点单 @author xsy-scm")
    @PostMapping("/stock/check/batchDelete")
    @SaCheckPermission("stock:check:batchDelete")
    public ResponseDTO<String> batchDelete(@RequestBody @Valid ValidateList<Long> idList) {
        return stockCheckService.batchDelete(idList);
    }

    @Operation(summary = "完成盘点单 @author xsy-scm")
    @PostMapping("/stock/check/complete/{checkId}")
    @SaCheckPermission("stock:check:complete")
    public ResponseDTO<String> complete(@PathVariable Long checkId) {
        return stockCheckService.complete(checkId);
    }
}
