package net.lab1024.sa.admin.module.scm.product.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.product.domain.form.*;
import net.lab1024.sa.admin.module.scm.product.domain.vo.ProductTagVO;
import net.lab1024.sa.admin.module.scm.product.service.ProductTagService;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/scm/product/tag")
@Tag(name = "SCM 商品标签")
@RequiredArgsConstructor
public class ProductTagController {
    private final ProductTagService service;

    @PostMapping("/list")
    @SaCheckPermission("scm:product:tag:query")
    public ResponseDTO<List<ProductTagVO>> list(@Valid @RequestBody ProductAssistantQueryForm form) {
        return ResponseDTO.ok(service.list(form));
    }

    @GetMapping("/options")
    @SaCheckPermission("scm:product:query")
    public ResponseDTO<List<ProductTagVO>> options() {
        return ResponseDTO.ok(service.options());
    }

    @PostMapping("/add")
    @SaCheckPermission("scm:product:tag:add")
    @OperateLog
    public ResponseDTO<Long> add(@Valid @RequestBody ProductTagAddForm form) {
        return ResponseDTO.ok(service.add(form));
    }

    @PostMapping("/update")
    @SaCheckPermission("scm:product:tag:update")
    @OperateLog
    public ResponseDTO<String> update(@Valid @RequestBody ProductTagUpdateForm form) {
        service.update(form);
        return ResponseDTO.ok();
    }

    @PostMapping("/delete")
    @SaCheckPermission("scm:product:tag:delete")
    @OperateLog
    public ResponseDTO<String> delete(@Valid @RequestBody ProductTagKeyForm form) {
        service.delete(form);
        return ResponseDTO.ok();
    }
}
