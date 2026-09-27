package com.xsy.scm.supplier.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.supplier.domain.form.SupplierSkuQueryForm;
import com.xsy.scm.supplier.domain.form.SupplierSkuReplaceForm;
import com.xsy.scm.supplier.domain.vo.SupplierSkuVO;
import com.xsy.scm.supplier.permission.SupplierPermission;
import com.xsy.scm.supplier.service.SupplierSkuService;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * SCM 商品-供应商关系（SKU 级）。
 *
 * <p>
 * 唯一写入口是 {@code /replace}，使用整表替换；不提供行级 add / update / delete，避免两套规则漂移。
 */
@RestController
@RequestMapping("/scm/supplier/sku")
@Tag(name = "SCM 商品-供应商关系")
@RequiredArgsConstructor
public class SupplierSkuController {

    private final SupplierSkuService supplierSkuService;

    /**
     * 按供应商列出活动关联行，供替换编辑页回填。
     */
    @GetMapping("/list/{supplierId}")
    @SaCheckPermission(SupplierPermission.SKU_QUERY)
    public ResponseDTO<
            List<
                    SupplierSkuVO>> listBySupplierId(@PathVariable Long supplierId) {
        return ResponseDTO.ok(supplierSkuService.listBySupplierId(supplierId));
    }

    /**
     * 只读反查：按 SKU 找供应商。
     */
    @PostMapping("/query")
    @SaCheckPermission(SupplierPermission.SKU_QUERY)
    public ResponseDTO<
            PageResult<
                    SupplierSkuVO>> query(@Valid @RequestBody SupplierSkuQueryForm form) {
        return ResponseDTO.ok(supplierSkuService.query(form));
    }

    @PostMapping("/replace")
    @SaCheckPermission(SupplierPermission.SKU_UPDATE)
    @OperateLog
    public ResponseDTO<
            String> replace(@Valid @RequestBody SupplierSkuReplaceForm form) {
        supplierSkuService.replace(form);
        return ResponseDTO.ok();
    }
}
