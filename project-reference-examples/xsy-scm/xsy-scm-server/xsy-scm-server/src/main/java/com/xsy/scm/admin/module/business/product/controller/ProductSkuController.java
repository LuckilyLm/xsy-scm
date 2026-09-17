package com.xsy.scm.admin.module.business.product.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.admin.constant.AdminSwaggerTagConst;
import com.xsy.scm.admin.module.business.product.domain.form.ProductSkuAddForm;
import com.xsy.scm.admin.module.business.product.domain.form.ProductSkuQueryForm;
import com.xsy.scm.admin.module.business.product.domain.form.ProductSkuUpdateForm;
import com.xsy.scm.admin.module.business.product.domain.vo.ProductSkuVO;
import com.xsy.scm.admin.module.business.product.service.ProductSkuService;
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

import java.util.List;

/**
 * 商品规格 SKU Controller
 *
 * <p>URL 风格沿用项目动作式约定：/product/sku/query、/product/sku/add 等。</p>
 *
 * @author xsy-scm
 */
@RestController
@Tag(name = AdminSwaggerTagConst.SupplyChain.SCM_PRODUCT)
public class ProductSkuController {

    @Resource
    private ProductSkuService productSkuService;

    @Operation(summary = "分页查询商品规格 @author xsy-scm")
    @PostMapping("/product/sku/query")
    @SaCheckPermission("product:sku:query")
    public ResponseDTO<PageResult<ProductSkuVO>> query(@RequestBody @Valid ProductSkuQueryForm queryForm) {
        return productSkuService.query(queryForm);
    }

    @Operation(summary = "添加商品规格 @author xsy-scm")
    @PostMapping("/product/sku/add")
    @SaCheckPermission("product:sku:add")
    public ResponseDTO<String> add(@RequestBody @Valid ProductSkuAddForm addForm) {
        return productSkuService.add(addForm);
    }

    @Operation(summary = "更新商品规格 @author xsy-scm")
    @PostMapping("/product/sku/update")
    @SaCheckPermission("product:sku:update")
    public ResponseDTO<String> update(@RequestBody @Valid ProductSkuUpdateForm updateForm) {
        return productSkuService.update(updateForm);
    }

    @Operation(summary = "删除商品规格 @author xsy-scm")
    @GetMapping("/product/sku/delete/{skuId}")
    @SaCheckPermission("product:sku:delete")
    public ResponseDTO<String> delete(@PathVariable Long skuId) {
        return productSkuService.delete(skuId);
    }

    @Operation(summary = "批量删除商品规格 @author xsy-scm")
    @PostMapping("/product/sku/batchDelete")
    @SaCheckPermission("product:sku:batchDelete")
    public ResponseDTO<String> batchDelete(@RequestBody @Valid ValidateList<Long> idList) {
        return productSkuService.batchDelete(idList);
    }

    @Operation(summary = "查询所有商品规格 @author xsy-scm")
    @GetMapping("/product/sku/queryAll")
    @SaCheckPermission("product:sku:query")
    public ResponseDTO<List<ProductSkuVO>> queryAll() {
        return productSkuService.queryAll();
    }
}
