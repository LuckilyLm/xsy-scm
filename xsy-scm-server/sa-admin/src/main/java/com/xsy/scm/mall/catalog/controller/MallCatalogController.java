package com.xsy.scm.mall.catalog.controller;

import com.xsy.scm.mall.account.constant.MallErrorCode;
import com.xsy.scm.mall.account.domain.vo.RequestMallCustomer;
import com.xsy.scm.mall.catalog.domain.form.MallProductQueryForm;
import com.xsy.scm.mall.catalog.domain.vo.MallCategoryVO;
import com.xsy.scm.mall.catalog.domain.vo.MallProductVO;
import com.xsy.scm.mall.catalog.service.MallCatalogService;
import com.xsy.scm.common.exception.ScmBusinessException;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
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
        requirePasswordChanged();
        return ResponseDTO.ok(catalogService.categories(current()));
    }

    @GetMapping("/products")
    public ResponseDTO<PageResult<MallProductVO>> products(@Valid MallProductQueryForm form) {
        requirePasswordChanged();
        return ResponseDTO.ok(catalogService.products(current(), form));
    }

    @GetMapping("/products/{skuId}")
    public ResponseDTO<MallProductVO> product(@PathVariable Long skuId) {
        requirePasswordChanged();
        return ResponseDTO.ok(catalogService.product(current(), skuId));
    }

    @GetMapping("/frequent")
    public ResponseDTO<List<MallProductVO>> frequent(@RequestParam(defaultValue = "90") int days,
            @RequestParam(defaultValue = "20") int limit) {
        requirePasswordChanged();
        return ResponseDTO.ok(catalogService.frequent(current(), days, limit));
    }

    @GetMapping("/hot-keywords")
    public ResponseDTO<List<String>> hotKeywords() {
        requirePasswordChanged();
        return ResponseDTO.ok(List.of());
    }

    private static RequestMallCustomer current() {
        return (RequestMallCustomer) SmartRequestUtil.getRequestUser();
    }

    private static void requirePasswordChanged() {
        if (Boolean.TRUE.equals(current().getFirstPasswordChange())) {
            throw new ScmBusinessException(MallErrorCode.PASSWORD_CHANGE_REQUIRED);
        }
    }
}
