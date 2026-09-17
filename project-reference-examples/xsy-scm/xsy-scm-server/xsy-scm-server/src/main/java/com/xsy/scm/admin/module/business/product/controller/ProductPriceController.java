package com.xsy.scm.admin.module.business.product.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.admin.constant.AdminSwaggerTagConst;
import com.xsy.scm.admin.module.business.product.domain.form.ProductPriceAddForm;
import com.xsy.scm.admin.module.business.product.domain.form.ProductPriceQueryForm;
import com.xsy.scm.admin.module.business.product.domain.form.ProductPriceUpdateForm;
import com.xsy.scm.admin.module.business.product.domain.vo.ProductPriceVO;
import com.xsy.scm.admin.module.business.product.service.ProductPriceService;
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
 * 商品价格 Controller
 *
 * <p>URL 风格沿用项目动作式约定：/product/price/query、/product/price/add 等。</p>
 *
 * @author xsy-scm
 */
@RestController
@Tag(name = AdminSwaggerTagConst.SupplyChain.SCM_PRODUCT)
public class ProductPriceController {

    @Resource
    private ProductPriceService productPriceService;

    @Operation(summary = "分页查询商品价格 @author xsy-scm")
    @PostMapping("/product/price/query")
    @SaCheckPermission("product:price:query")
    public ResponseDTO<PageResult<ProductPriceVO>> query(@RequestBody @Valid ProductPriceQueryForm queryForm) {
        return productPriceService.query(queryForm);
    }

    @Operation(summary = "添加商品价格 @author xsy-scm")
    @PostMapping("/product/price/add")
    @SaCheckPermission("product:price:add")
    public ResponseDTO<String> add(@RequestBody @Valid ProductPriceAddForm addForm) {
        return productPriceService.add(addForm);
    }

    @Operation(summary = "更新商品价格 @author xsy-scm")
    @PostMapping("/product/price/update")
    @SaCheckPermission("product:price:update")
    public ResponseDTO<String> update(@RequestBody @Valid ProductPriceUpdateForm updateForm) {
        return productPriceService.update(updateForm);
    }

    @Operation(summary = "删除商品价格 @author xsy-scm")
    @GetMapping("/product/price/delete/{priceId}")
    @SaCheckPermission("product:price:delete")
    public ResponseDTO<String> delete(@PathVariable Long priceId) {
        return productPriceService.delete(priceId);
    }

    @Operation(summary = "批量删除商品价格 @author xsy-scm")
    @PostMapping("/product/price/batchDelete")
    @SaCheckPermission("product:price:batchDelete")
    public ResponseDTO<String> batchDelete(@RequestBody @Valid ValidateList<Long> idList) {
        return productPriceService.batchDelete(idList);
    }
}
