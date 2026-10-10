package com.xsy.scm.mall.catalog.controller;

import com.xsy.scm.mall.account.support.MallRequestSupport;
import com.xsy.scm.mall.catalog.domain.form.MallProductQueryForm;
import com.xsy.scm.mall.catalog.domain.vo.MallCategoryVO;
import com.xsy.scm.mall.catalog.domain.vo.MallProductVO;
import com.xsy.scm.mall.catalog.service.MallCatalogService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/scm/mall/catalog")
@RequiredArgsConstructor
public class MallCatalogController {
    private final MallCatalogService catalogService;

    @GetMapping("/categories")
    public ResponseDTO<List<MallCategoryVO>> categories() {
        MallRequestSupport.requirePasswordChanged();
        return ResponseDTO.ok(catalogService.categories(MallRequestSupport.current()));
    }

    @GetMapping("/products")
    public ResponseDTO<PageResult<MallProductVO>> products(@Valid MallProductQueryForm form) {
        MallRequestSupport.requirePasswordChanged();
        return ResponseDTO.ok(catalogService.products(MallRequestSupport.current(), form));
    }

    @GetMapping("/products/{skuId}")
    public ResponseDTO<MallProductVO> product(@PathVariable Long skuId) {
        MallRequestSupport.requirePasswordChanged();
        return ResponseDTO.ok(catalogService.product(MallRequestSupport.current(), skuId));
    }

    @GetMapping("/frequent")
    public ResponseDTO<List<MallProductVO>> frequent(@RequestParam(defaultValue = "90") int days,
            @RequestParam(defaultValue = "20") int limit) {
        MallRequestSupport.requirePasswordChanged();
        return ResponseDTO.ok(catalogService.frequent(MallRequestSupport.current(), days, limit));
    }

    @GetMapping("/hot-keywords")
    public ResponseDTO<List<String>> hotKeywords() {
        MallRequestSupport.requirePasswordChanged();
        return ResponseDTO.ok(List.of());
    }
}
