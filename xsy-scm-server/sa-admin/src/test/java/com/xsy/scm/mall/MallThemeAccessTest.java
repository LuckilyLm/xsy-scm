package com.xsy.scm.mall;

import com.xsy.scm.mall.home.controller.MallHomeController;
import com.xsy.scm.mall.home.domain.vo.MallThemeVO;
import net.lab1024.sa.base.common.annoation.NoNeedLogin;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MallThemeAccessTest {
    @Test
    void onlyLightweightThemeReadIsAnonymous() throws NoSuchMethodException {
        assertThat(MallHomeController.class.getMethod("theme").getAnnotation(NoNeedLogin.class)).isNotNull();
        assertThat(MallHomeController.class.getMethod("published", Long.class)
                .getAnnotation(NoNeedLogin.class)).isNull();
        assertThat(MallHomeController.class.getAnnotation(NoNeedLogin.class)).isNull();
        assertThat(MallThemeVO.class.getDeclaredFields()).extracting(java.lang.reflect.Field::getName)
                .containsExactlyInAnyOrder("releaseId", "themeCode");
    }
}
