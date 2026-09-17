package com.xsy.scm.admin.module.business.stock.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.admin.constant.AdminSwaggerTagConst;
import com.xsy.scm.admin.module.business.stock.domain.form.StockCheckItemAddForm;
import com.xsy.scm.admin.module.business.stock.domain.form.StockCheckItemQueryForm;
import com.xsy.scm.admin.module.business.stock.domain.form.StockCheckItemUpdateForm;
import com.xsy.scm.admin.module.business.stock.domain.vo.StockCheckItemVO;
import com.xsy.scm.admin.module.business.stock.service.StockCheckItemService;
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
 * 库存盘点明细 Controller
 *
 * @author xsy-scm
 */
@RestController
@Tag(name = AdminSwaggerTagConst.SupplyChain.SCM_STOCK)
public class StockCheckItemController {

    @Resource
    private StockCheckItemService stockCheckItemService;

    @Operation(summary = "分页查询盘点明细 @author xsy-scm")
    @PostMapping("/stock/check/item/query")
    @SaCheckPermission("stock:check:item:query")
    public ResponseDTO<PageResult<StockCheckItemVO>> query(@RequestBody @Valid StockCheckItemQueryForm queryForm) {
        return stockCheckItemService.query(queryForm);
    }

    @Operation(summary = "添加盘点明细 @author xsy-scm")
    @PostMapping("/stock/check/item/add")
    @SaCheckPermission("stock:check:item:add")
    public ResponseDTO<String> add(@RequestBody @Valid StockCheckItemAddForm addForm) {
        return stockCheckItemService.add(addForm);
    }

    @Operation(summary = "更新盘点明细 @author xsy-scm")
    @PostMapping("/stock/check/item/update")
    @SaCheckPermission("stock:check:item:update")
    public ResponseDTO<String> update(@RequestBody @Valid StockCheckItemUpdateForm updateForm) {
        return stockCheckItemService.update(updateForm);
    }

    @Operation(summary = "删除盘点明细 @author xsy-scm")
    @GetMapping("/stock/check/item/delete/{itemId}")
    @SaCheckPermission("stock:check:item:delete")
    public ResponseDTO<String> delete(@PathVariable Long itemId) {
        return stockCheckItemService.delete(itemId);
    }

    @Operation(summary = "批量删除盘点明细 @author xsy-scm")
    @PostMapping("/stock/check/item/batchDelete")
    @SaCheckPermission("stock:check:item:batchDelete")
    public ResponseDTO<String> batchDelete(@RequestBody @Valid ValidateList<Long> idList) {
        return stockCheckItemService.batchDelete(idList);
    }
}
