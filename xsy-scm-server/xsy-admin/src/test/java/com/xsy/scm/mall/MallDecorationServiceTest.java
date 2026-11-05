package com.xsy.scm.mall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.mall.decoration.dao.MallDecorationDao;
import com.xsy.scm.mall.decoration.domain.form.MallPagePublishForm;
import com.xsy.scm.mall.decoration.domain.vo.MallPageVO;
import com.xsy.scm.mall.decoration.domain.vo.MallStoreVO;
import com.xsy.scm.mall.decoration.service.MallDecorationService;
import com.xsy.scm.mall.home.service.MallHomeLayoutService;
import com.xsy.scm.admin.module.system.login.domain.RequestEmployee;
import com.xsy.scm.base.common.enumeration.UserTypeEnum;
import com.xsy.scm.base.common.util.XsyRequestUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MallDecorationServiceTest {
    private final MallDecorationDao decorationDao = mock(MallDecorationDao.class);
    private final MallHomeLayoutService layoutService = mock(MallHomeLayoutService.class);
    private final MallDecorationService service = new MallDecorationService(decorationDao, layoutService);

    @Test
    void storefrontIsPublicAndDoesNotExposeAdministrationFields() {
        MallStoreVO store = new MallStoreVO();
        store.setStoreName("鲜蔬源");
        store.setStoreLink("https://example.com/mall");
        store.setVersion(7);
        store.setUpdatedBy("internal-operator");
        when(decorationDao.store()).thenReturn(store);

        var result = service.storefront();

        assertThat(result.storeName()).isEqualTo("鲜蔬源");
        assertThat(result.storeLink()).isEqualTo("https://example.com/mall");
        assertThat(result.getClass().getRecordComponents()).extracting(component -> component.getName())
            .containsExactly("storeName", "storeLink");
        verify(layoutService, never()).requireGlobalCustomerScope();
    }

    @Test
    void unconfiguredStorefrontUsesPublicDefaults() {
        assertThat(service.storefront().storeName()).isEqualTo("商城");
        assertThat(service.storefront().storeLink()).isNull();
    }

    @BeforeEach
    void setUpOperator() {
        RequestEmployee employee = new RequestEmployee();
        employee.setEmployeeId(1L);
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        XsyRequestUtil.setRequestUser(employee);
    }

    @AfterEach
    void tearDownOperator() {
        XsyRequestUtil.remove();
    }

    @Test
    void publishedPageUsesTheTitleSnapshotFromTheSameRelease() {
        MallPageVO page = page();
        page.setTitle("改过但还没发布的标题");
        page.setPublishedTitle("线上标题");
        page.setPublishedContent("<p>线上正文</p>");
        when(decorationDao.page("ABOUT")).thenReturn(page);

        var published = service.publishedPage("ABOUT");

        assertThat(published.title()).isEqualTo("线上标题");
        assertThat(published.content()).isEqualTo("<p>线上正文</p>");
    }

    @Test
    void imageOnlyPageCanBePublished() {
        MallPageVO page = page();
        page.setContent("<p><img src=\"https://cdn.example.com/about.png\" alt=\"资质说明\"></p>");
        page.setPublishedVersion(1);
        page.setPublishedContent(page.getContent());
        when(decorationDao.lockPage("ABOUT")).thenReturn(page);
        when(decorationDao.publishPage("ABOUT", 1, "1:1")).thenReturn(1);
        when(decorationDao.page("ABOUT")).thenReturn(page);

        service.publishPage(publishForm());

        verify(decorationDao).publishPage("ABOUT", 1, "1:1");
    }

    @Test
    void blankPageIsStillRejectedOnPublish() {
        MallPageVO page = page();
        page.setContent("<p>  </p>");
        when(decorationDao.lockPage("ABOUT")).thenReturn(page);

        assertThatThrownBy(() -> service.publishPage(publishForm()))
            .isInstanceOf(ScmBusinessException.class);
        verify(decorationDao, never()).publishPage("ABOUT", 1, "1:1");
    }

    private static MallPagePublishForm publishForm() {
        MallPagePublishForm form = new MallPagePublishForm();
        form.setPageCode("ABOUT");
        form.setVersion(1);
        return form;
    }

    private static MallPageVO page() {
        MallPageVO page = new MallPageVO();
        page.setPageCode("ABOUT");
        page.setTitle("关于我们");
        page.setContent("");
        page.setVersion(1);
        return page;
    }
}
