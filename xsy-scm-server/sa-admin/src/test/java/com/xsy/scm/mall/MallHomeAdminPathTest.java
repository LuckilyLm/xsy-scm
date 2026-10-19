package com.xsy.scm.mall;

import static org.assertj.core.api.Assertions.assertThat;

import com.xsy.scm.mall.home.controller.MallHomeAdminController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 商城装修管理端的路径必须落在后台前缀上。
 *
 * <p>
 * {@code /scm/mall/**} 归 {@code MallCustomerInterceptor} 管，员工令牌打进去会被判成非客户身份（30007）。
 * 这条断言钉住路径，防止以后有人「统一一下前缀」又把它挪回去。
 */
class MallHomeAdminPathTest {

    @Test
    @DisplayName("管理端路径不落进客户商城的 /scm/mall/ 拦截范围")
    void adminPathIsNotUnderMallCustomerPrefix() {
        RequestMapping mapping = MallHomeAdminController.class.getAnnotation(RequestMapping.class);
        assertThat(mapping).isNotNull();
        String path = mapping.value()[0];

        assertThat(path).startsWith("/scm/mall-admin");
        assertThat(path).doesNotStartWith("/scm/mall/");
    }
}
