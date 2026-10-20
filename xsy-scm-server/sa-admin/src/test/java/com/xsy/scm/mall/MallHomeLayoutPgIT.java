package com.xsy.scm.mall;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.mall.account.domain.vo.RequestMallCustomer;
import com.xsy.scm.mall.home.domain.form.MallHomeBlockForm;
import com.xsy.scm.mall.home.domain.form.MallHomeDraftSaveForm;
import com.xsy.scm.mall.home.domain.form.MallHomePublishForm;
import com.xsy.scm.mall.home.domain.form.MallHomeRollbackForm;
import com.xsy.scm.mall.home.controller.MallHomeAdminController;
import com.xsy.scm.mall.permission.MallPermission;
import com.xsy.scm.mall.home.service.MallHomeLayoutService;
import com.xsy.scm.mall.home.service.MallHomeProjectionService;
import java.util.List;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MallHomeLayoutPgIT extends ScmW5PgITBase {
    @Autowired
    private MallHomeLayoutService layouts;
    @Autowired
    private MallHomeProjectionService projection;

    @Test
    void draftPublishRollbackAndCustomerProjectionUseImmutableReleaseFacts() {
        Long visibleCustomer = customerWithWarehouse();
        Long hiddenCustomer = customerWithWarehouse();
        var initial = layouts.draft();
        var first = layouts.save(save(initial.version(), announcement("one", "第一版", visibleCustomer)));
        assertThat(first.version()).isEqualTo(initial.version() + 1);
        assertThatThrownBy(() -> layouts.save(save(initial.version(), announcement("stale", "过期草稿", visibleCustomer))))
                .isInstanceOf(ScmBusinessException.class);
        assertThat(projection.published(customer(visibleCustomer), seedWarehouseId()).releaseId())
                .isEqualTo(initial.currentReleaseId());

        Long firstRelease = layouts.publish(publish(first.version(), first.pointerVersion()));
        assertThat(projection.published(customer(visibleCustomer), seedWarehouseId()).blocks())
                .extracting(block -> block.getText()).containsExactly("第一版");
        assertThat(projection.published(customer(hiddenCustomer), seedWarehouseId()).blocks()).isEmpty();

        var secondDraft = layouts.save(save(first.version(), announcement("two", "第二版", visibleCustomer)));
        Long secondRelease = layouts.publish(publish(secondDraft.version(), secondDraft.pointerVersion()));
        assertThat(secondRelease).isNotEqualTo(firstRelease);
        assertThatThrownBy(() -> layouts.publish(publish(secondDraft.version(), secondDraft.pointerVersion())))
                .isInstanceOf(ScmBusinessException.class);
        assertThat(jdbc.queryForObject("SELECT content::text FROM xsy_mall_home_release WHERE id=?",
                String.class, firstRelease)).contains("第一版");

        var rollback = new MallHomeRollbackForm();
        rollback.setTargetReleaseId(firstRelease);
        rollback.setPointerVersion(secondDraft.pointerVersion() + 1);
        rollback.setReason("恢复第一版");
        Long restoredRelease = layouts.rollback(rollback);
        assertThat(restoredRelease).isNotIn(firstRelease, secondRelease);
        assertThat(jdbc.queryForMap("SELECT publish_kind, source_release_id, reason FROM xsy_mall_home_release WHERE id=?",
                restoredRelease)).containsEntry("publish_kind", "ROLLBACK")
                        .containsEntry("source_release_id", firstRelease)
                        .containsEntry("reason", "恢复第一版");
        assertThat(projection.published(customer(visibleCustomer), seedWarehouseId()).releaseId())
                .isEqualTo(restoredRelease);
        assertThat(projection.published(customer(visibleCustomer), seedWarehouseId()).blocks())
                .extracting(block -> block.getText()).containsExactly("第一版");
        assertThat(jdbc.queryForObject("SELECT content::text FROM xsy_mall_home_release WHERE id=?",
                String.class, secondRelease)).contains("第二版");
        expectSqlFailure("UPDATE xsy_mall_home_release SET reason='tampered' WHERE id=?", firstRelease);
        expectSqlFailure("DELETE FROM xsy_mall_home_release WHERE id=?", firstRelease);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_mall_home_release WHERE id=?",
                Integer.class, firstRelease)).isEqualTo(1);
    }

    @Test
    void nonGlobalCustomerScopeCannotReadOrChangeGlobalLayout() {
        var admin = SmartRequestUtil.getRequestUser();
        RequestEmployee restricted = new RequestEmployee();
        restricted.setEmployeeId(900000001L);
        restricted.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        restricted.setAdministratorFlag(false);
        SmartRequestUtil.setRequestUser(restricted);
        try {
            assertThatThrownBy(layouts::draft).isInstanceOf(ScmDataScopeException.class);
            assertThatThrownBy(layouts::history).isInstanceOf(ScmDataScopeException.class);
            assertThatThrownBy(() -> projection.draftPreview(1L, seedWarehouseId()))
                    .isInstanceOf(ScmDataScopeException.class);
            assertThatThrownBy(() -> layouts.save(save(0, announcement("x", "无权", null))))
                    .isInstanceOf(ScmDataScopeException.class);
            assertThatThrownBy(() -> layouts.publish(publish(0, 0)))
                    .isInstanceOf(ScmDataScopeException.class);
            var rollback = new MallHomeRollbackForm();
            rollback.setTargetReleaseId(1L);
            rollback.setPointerVersion(0);
            rollback.setReason("无权");
            assertThatThrownBy(() -> layouts.rollback(rollback)).isInstanceOf(ScmDataScopeException.class);
        } finally {
            SmartRequestUtil.setRequestUser(admin);
        }
    }

    @Test
    void adminEndpointsRequireTheMatchingFunctionPermissions() throws NoSuchMethodException {
        assertThat(MallHomeAdminController.class.getMethod("draft").getAnnotation(SaCheckPermission.class).value())
                .containsExactly(MallPermission.HOME_QUERY);
        assertThat(MallHomeAdminController.class.getMethod("history").getAnnotation(SaCheckPermission.class).value())
                .containsExactly(MallPermission.HOME_QUERY);
        assertThat(MallHomeAdminController.class.getMethod("preview",
                com.xsy.scm.mall.home.domain.form.MallHomePreviewForm.class)
                .getAnnotation(SaCheckPermission.class).value()).containsExactly(MallPermission.HOME_QUERY);
        assertThat(MallHomeAdminController.class.getMethod("save", MallHomeDraftSaveForm.class)
                .getAnnotation(SaCheckPermission.class).value()).containsExactly(MallPermission.HOME_EDIT);
        assertThat(MallHomeAdminController.class.getMethod("publish", MallHomePublishForm.class)
                .getAnnotation(SaCheckPermission.class).value()).containsExactly(MallPermission.HOME_PUBLISH);
        assertThat(MallHomeAdminController.class.getMethod("rollback", MallHomeRollbackForm.class)
                .getAnnotation(SaCheckPermission.class).value()).containsExactly(MallPermission.HOME_PUBLISH);
    }

    @Test
    void globalThemeFollowsPublishedPointerAndRollbackWithoutCustomerOrWarehouse() {
        var initial = layouts.draft();
        var before = projection.publishedTheme();
        var warm = save(initial.version(), announcement("warm", "暖色主题", null));
        warm.setThemeCode("WARM");
        var warmDraft = layouts.save(warm);
        assertThat(projection.publishedTheme()).isEqualTo(before);
        Long warmRelease = layouts.publish(publish(warmDraft.version(), warmDraft.pointerVersion()));
        assertThat(projection.publishedTheme().getThemeCode()).isEqualTo("WARM");
        var ocean = save(warmDraft.version(), announcement("ocean", "蓝色主题", null));
        ocean.setThemeCode("OCEAN");
        var oceanDraft = layouts.save(ocean);
        Long oceanRelease = layouts.publish(publish(oceanDraft.version(), oceanDraft.pointerVersion()));
        assertThat(projection.publishedTheme().getThemeCode()).isEqualTo("OCEAN");
        var rollback = new MallHomeRollbackForm();
        rollback.setTargetReleaseId(warmRelease);
        rollback.setPointerVersion(layouts.draft().pointerVersion());
        rollback.setReason("恢复暖色主题");
        Long rollbackRelease = layouts.rollback(rollback);
        var operator = SmartRequestUtil.getRequestUser();
        SmartRequestUtil.remove();
        try {
            assertThat(projection.publishedTheme().getThemeCode()).isEqualTo("WARM");
            assertThat(projection.publishedTheme().getReleaseId()).isEqualTo(rollbackRelease);
        } finally {
            SmartRequestUtil.setRequestUser(operator);
        }
        assertThat(jdbc.queryForObject("SELECT theme_code FROM xsy_mall_home_release WHERE id=?",
                String.class, oceanRelease)).isEqualTo("OCEAN");
    }

    private static MallHomeDraftSaveForm save(int version, MallHomeBlockForm block) {
        var form = new MallHomeDraftSaveForm();
        form.setVersion(version);
        form.setBlocks(List.of(block));
        return form;
    }

    private static MallHomePublishForm publish(int draftVersion, int pointerVersion) {
        var form = new MallHomePublishForm();
        form.setDraftVersion(draftVersion);
        form.setPointerVersion(pointerVersion);
        return form;
    }

    private static MallHomeBlockForm announcement(String key, String text, Long customerId) {
        var block = new MallHomeBlockForm();
        block.setKey(key);
        block.setSchemaVersion(1);
        block.setType("ANNOUNCEMENT");
        block.setSortOrder(0);
        block.setEnabled(true);
        block.setText(text);
        block.setLinkType("NONE");
        block.setCustomerIds(customerId == null ? List.of() : List.of(customerId));
        return block;
    }

    /**
     * 商城首页投影要按「客户可选的配送仓」校验仓库；裸 newCustomer() 没有绑定仓也没有地址，
     * 会让投影直接报 WAREHOUSE_UNAVAILABLE。这里把种子仓绑到客户上，不依赖环境里的地址数据。
     */
    private Long customerWithWarehouse() {
        Long customerId = newCustomer();
        jdbc.update("UPDATE xsy_customer SET mall_delivery_warehouse_id = ? WHERE id = ?", seedWarehouseId(),
                customerId);
        evictMybatisCache();
        return customerId;
    }

    private static RequestMallCustomer customer(Long id) {
        var customer = new RequestMallCustomer();
        customer.setCustomerId(id);
        return customer;
    }
}
