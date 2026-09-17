package com.xsy.scm.admin.module.business.stock.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.admin.constant.AdminSwaggerTagConst;
import com.xsy.scm.admin.module.business.stock.domain.form.ProductConvertAddForm;
import com.xsy.scm.admin.module.business.stock.domain.form.ProductConvertApproveForm;
import com.xsy.scm.admin.module.business.stock.domain.form.ProductConvertQueryForm;
import com.xsy.scm.admin.module.business.stock.domain.form.ProductConvertUpdateForm;
import com.xsy.scm.admin.module.business.stock.domain.vo.ProductConvertDetailVO;
import com.xsy.scm.admin.module.business.stock.domain.vo.ProductConvertVO;
import com.xsy.scm.admin.module.business.stock.service.ProductConvertService;
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
 * 商品转换单 Controller
 *
 * @author xsy-scm
 */
@RestController
@Tag(name = AdminSwaggerTagConst.SupplyChain.SCM_STOCK)
public class ProductConvertController {

    @Resource
    private ProductConvertService productConvertService;

    @Operation(summary = "分页查询商品转换单 @author xsy-scm")
    @PostMapping("/productConvert/query")
    @SaCheckPermission("productConvert:query")
    public ResponseDTO<PageResult<ProductConvertVO>> query(@RequestBody @Valid ProductConvertQueryForm queryForm) {
        return productConvertService.query(queryForm);
    }

    @Operation(summary = "查询商品转换单详情 @author xsy-scm")
    @GetMapping("/productConvert/get/{convertId}")
    @SaCheckPermission("productConvert:query")
    public ResponseDTO<ProductConvertDetailVO> detail(@PathVariable Long convertId) {
        return productConvertService.detail(convertId);
    }

    @Operation(summary = "添加商品转换单 @author xsy-scm")
    @PostMapping("/productConvert/add")
    @SaCheckPermission("productConvert:add")
    public ResponseDTO<String> add(@RequestBody @Valid ProductConvertAddForm addForm) {
        return productConvertService.add(addForm);
    }

    @Operation(summary = "更新商品转换单 @author xsy-scm")
    @PostMapping("/productConvert/update")
    @SaCheckPermission("productConvert:update")
    public ResponseDTO<String> update(@RequestBody @Valid ProductConvertUpdateForm updateForm) {
        return productConvertService.update(updateForm);
    }

    @Operation(summary = "审核商品转换单 @author xsy-scm")
    @PostMapping("/productConvert/approve")
    @SaCheckPermission("productConvert:approve")
    public ResponseDTO<String> approve(@RequestBody @Valid ProductConvertApproveForm form) {
        return productConvertService.approve(form);
    }

    @Operation(summary = "驳回商品转换单 @author xsy-scm")
    @PostMapping("/productConvert/reject")
    @SaCheckPermission("productConvert:approve")
    public ResponseDTO<String> reject(@RequestBody @Valid ProductConvertApproveForm form) {
        return productConvertService.reject(form);
    }

    @Operation(summary = "删除商品转换单 @author xsy-scm")
    @GetMapping("/productConvert/delete/{convertId}")
    @SaCheckPermission("productConvert:delete")
    public ResponseDTO<String> delete(@PathVariable Long convertId) {
        return productConvertService.delete(convertId);
    }

    @Operation(summary = "批量删除商品转换单 @author xsy-scm")
    @PostMapping("/productConvert/batchDelete")
    @SaCheckPermission("productConvert:batchDelete")
    public ResponseDTO<String> batchDelete(@RequestBody @Valid ValidateList<Long> idList) {
        return productConvertService.batchDelete(idList);
    }
}
