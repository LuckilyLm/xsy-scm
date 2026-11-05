package com.xsy.scm.mall.decoration.controller;

import com.xsy.scm.mall.decoration.domain.vo.MallPublishedPageVO;
import com.xsy.scm.mall.decoration.domain.vo.MallStorefrontVO;
import com.xsy.scm.mall.decoration.service.MallDecorationService;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.base.common.annoation.NoNeedLogin;
import com.xsy.scm.base.common.domain.ResponseDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/scm/mall/decoration")
public class MallDecorationController {
    private final MallDecorationService decorationService;

    /** @return 已保存的公开店铺资料，不要求登录，不包含后台维护字段 */
    @GetMapping("/store")
    @NoNeedLogin
    public ResponseDTO<MallStorefrontVO> store() {
        return ResponseDTO.ok(decorationService.storefront());
    }

    /**
     * 商城单页：只返回已发布内容，草稿不出后台。
     *
     * <p>
     * 公告、条款、关于我们与售后规则属于公开信息，登录页与未登录的「我的」 页面也需要查看，因此不要求登录；与 /home/theme 一样只开放指针后的发布内容。
     */
    @GetMapping("/page/{pageCode}")
    @NoNeedLogin
    public ResponseDTO<MallPublishedPageVO> page(@PathVariable String pageCode) {
        return ResponseDTO.ok(decorationService.publishedPage(pageCode));
    }
}
