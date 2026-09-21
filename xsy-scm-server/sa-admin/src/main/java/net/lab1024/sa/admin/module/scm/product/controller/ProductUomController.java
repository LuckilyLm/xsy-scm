package net.lab1024.sa.admin.module.scm.product.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.product.domain.form.*;
import net.lab1024.sa.admin.module.scm.product.domain.vo.ProductUomVO;
import net.lab1024.sa.admin.module.scm.product.service.ProductUomService;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/scm/product/uom")
@Tag(name = "SCM 计量单位")
@RequiredArgsConstructor
public class ProductUomController {
    private final ProductUomService service;

    @PostMapping("/list")
    @SaCheckPermission("scm:product:uom:query")
    public ResponseDTO<List<ProductUomVO>> list(@Valid @RequestBody ProductAssistantQueryForm form) {
        return ResponseDTO.ok(service.list(form));
    }

    /**
     * 商品表单里的单位下拉：读权限即可取，否则只读角色连筛选条件都渲染不出来。
     */
    @GetMapping("/options")
    @SaCheckPermission("scm:product:query")
    public ResponseDTO<List<ProductUomVO>> options() {
        return ResponseDTO.ok(service.options());
    }

    @PostMapping("/add")
    @SaCheckPermission("scm:product:uom:add")
    @OperateLog
    public ResponseDTO<Long> add(@Valid @RequestBody ProductUomAddForm form) {
        return ResponseDTO.ok(service.add(form));
    }

    @PostMapping("/update")
    @SaCheckPermission("scm:product:uom:update")
    @OperateLog
    public ResponseDTO<String> update(@Valid @RequestBody ProductUomUpdateForm form) {
        service.update(form);
        return ResponseDTO.ok();
    }

    @PostMapping("/delete")
    @SaCheckPermission("scm:product:uom:delete")
    @OperateLog
    public ResponseDTO<String> delete(@Valid @RequestBody ProductUomKeyForm form) {
        service.delete(form);
        return ResponseDTO.ok();
    }
}
