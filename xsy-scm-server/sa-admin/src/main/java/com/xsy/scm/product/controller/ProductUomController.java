package com.xsy.scm.product.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.product.domain.form.ProductAssistantQueryForm;
import com.xsy.scm.product.domain.form.ProductUomAddForm;
import com.xsy.scm.product.domain.form.ProductUomKeyForm;
import com.xsy.scm.product.domain.form.ProductUomUpdateForm;
import com.xsy.scm.product.domain.vo.ProductUomVO;
import com.xsy.scm.product.service.ProductUomService;
import com.xsy.scm.product.permission.ProductPermission;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/scm/product/uom")
@Tag(name = "SCM 计量单位")
@RequiredArgsConstructor
public class ProductUomController {
    private final ProductUomService productUomService;

    @PostMapping("/list")
    @SaCheckPermission(ProductPermission.UOM_QUERY)
    public ResponseDTO<
            List<
                    ProductUomVO>> list(@Valid @RequestBody ProductAssistantQueryForm form) {
        return ResponseDTO.ok(productUomService.list(form));
    }

    /**
     * 商品表单里的单位下拉：读权限即可取，否则只读角色连筛选条件都渲染不出来。
     */
    @GetMapping("/options")
    @SaCheckPermission(ProductPermission.QUERY)
    public ResponseDTO<
            List<
                    ProductUomVO>> options() {
        return ResponseDTO.ok(productUomService.options());
    }

    @PostMapping("/add")
    @SaCheckPermission(ProductPermission.UOM_ADD)
    @OperateLog
    public ResponseDTO<
            Long> add(@Valid @RequestBody ProductUomAddForm form) {
        return ResponseDTO.ok(productUomService.add(form));
    }

    @PostMapping("/update")
    @SaCheckPermission(ProductPermission.UOM_UPDATE)
    @OperateLog
    public ResponseDTO<
            String> update(@Valid @RequestBody ProductUomUpdateForm form) {
        productUomService.update(form);
        return ResponseDTO.ok();
    }

    @PostMapping("/delete")
    @SaCheckPermission(ProductPermission.UOM_DELETE)
    @OperateLog
    public ResponseDTO<
            String> delete(@Valid @RequestBody ProductUomKeyForm form) {
        productUomService.delete(form);
        return ResponseDTO.ok();
    }
}
