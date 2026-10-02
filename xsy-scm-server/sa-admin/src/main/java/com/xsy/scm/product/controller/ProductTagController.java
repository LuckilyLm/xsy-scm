package com.xsy.scm.product.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.product.domain.form.ProductAssistantQueryForm;
import com.xsy.scm.product.domain.form.ProductTagAddForm;
import com.xsy.scm.product.domain.form.ProductTagKeyForm;
import com.xsy.scm.product.domain.form.ProductTagUpdateForm;
import com.xsy.scm.product.domain.vo.ProductTagVO;
import com.xsy.scm.product.service.ProductTagService;
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
@RequestMapping("/scm/product/tag")
@Tag(name = "SCM 商品标签")
@RequiredArgsConstructor
public class ProductTagController {
    private final ProductTagService productTagService;

    @PostMapping("/list")
    @SaCheckPermission(ProductPermission.TAG_QUERY)
    public ResponseDTO<List<ProductTagVO>> list(@Valid @RequestBody ProductAssistantQueryForm form) {
        return ResponseDTO.ok(productTagService.list(form));
    }

    @GetMapping("/options")
    @SaCheckPermission(ProductPermission.QUERY)
    public ResponseDTO<List<ProductTagVO>> options() {
        return ResponseDTO.ok(productTagService.options());
    }

    @PostMapping("/add")
    @SaCheckPermission(ProductPermission.TAG_ADD)
    @OperateLog
    public ResponseDTO<Long> add(@Valid @RequestBody ProductTagAddForm form) {
        return ResponseDTO.ok(productTagService.add(form));
    }

    @PostMapping("/update")
    @SaCheckPermission(ProductPermission.TAG_UPDATE)
    @OperateLog
    public ResponseDTO<String> update(@Valid @RequestBody ProductTagUpdateForm form) {
        productTagService.update(form);
        return ResponseDTO.ok();
    }

    @PostMapping("/delete")
    @SaCheckPermission(ProductPermission.TAG_DELETE)
    @OperateLog
    public ResponseDTO<String> delete(@Valid @RequestBody ProductTagKeyForm form) {
        productTagService.delete(form);
        return ResponseDTO.ok();
    }
}
