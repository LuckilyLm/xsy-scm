package com.xsy.scm.admin.module.business.product.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.admin.constant.AdminSwaggerTagConst;
import com.xsy.scm.admin.module.business.product.domain.form.ProductBarcodeAddForm;
import com.xsy.scm.admin.module.business.product.domain.form.ProductBarcodeQueryForm;
import com.xsy.scm.admin.module.business.product.domain.form.ProductBarcodeUpdateForm;
import com.xsy.scm.admin.module.business.product.domain.vo.ProductBarcodeVO;
import com.xsy.scm.admin.module.business.product.service.ProductBarcodeService;
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
 * 商品条码 Controller
 *
 * @author xsy-scm
 */
@RestController
@Tag(name = AdminSwaggerTagConst.SupplyChain.SCM_PRODUCT)
public class ProductBarcodeController {

    @Resource
    private ProductBarcodeService productBarcodeService;

    @Operation(summary = "分页查询商品条码 @author xsy-scm")
    @PostMapping("/product/barcode/query")
    @SaCheckPermission("productBarcode:query")
    public ResponseDTO<PageResult<ProductBarcodeVO>> query(@RequestBody @Valid ProductBarcodeQueryForm queryForm) {
        return productBarcodeService.query(queryForm);
    }

    @Operation(summary = "按条码查询商品（扫码入口） @author xsy-scm")
    @GetMapping("/product/barcode/getByCode/{barcode}")
    @SaCheckPermission("productBarcode:query")
    public ResponseDTO<ProductBarcodeVO> getByCode(@PathVariable String barcode) {
        return productBarcodeService.getByBarcode(barcode);
    }

    @Operation(summary = "添加商品条码 @author xsy-scm")
    @PostMapping("/product/barcode/add")
    @SaCheckPermission("productBarcode:add")
    public ResponseDTO<String> add(@RequestBody @Valid ProductBarcodeAddForm addForm) {
        return productBarcodeService.add(addForm);
    }

    @Operation(summary = "更新商品条码 @author xsy-scm")
    @PostMapping("/product/barcode/update")
    @SaCheckPermission("productBarcode:update")
    public ResponseDTO<String> update(@RequestBody @Valid ProductBarcodeUpdateForm updateForm) {
        return productBarcodeService.update(updateForm);
    }

    @Operation(summary = "删除商品条码 @author xsy-scm")
    @GetMapping("/product/barcode/delete/{barcodeId}")
    @SaCheckPermission("productBarcode:delete")
    public ResponseDTO<String> delete(@PathVariable Long barcodeId) {
        return productBarcodeService.delete(barcodeId);
    }

    @Operation(summary = "批量删除商品条码 @author xsy-scm")
    @PostMapping("/product/barcode/batchDelete")
    @SaCheckPermission("productBarcode:batchDelete")
    public ResponseDTO<String> batchDelete(@RequestBody @Valid ValidateList<Long> idList) {
        return productBarcodeService.batchDelete(idList);
    }
}
