package com.xsy.scm.order;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.xsy.scm.order.controller.OrderTagController;
import com.xsy.scm.order.domain.form.OrderTagAssignForm;
import com.xsy.scm.order.domain.form.OrderTagCreateForm;
import com.xsy.scm.order.permission.OrderPermission;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderTagPermissionTest {
    @Test
    void tagListingRequiresOrderQueryPermission() throws Exception {
        var guard = OrderTagController.class.getMethod("list").getAnnotation(SaCheckPermission.class);
        assertThat(guard.value()).containsExactly(OrderPermission.QUERY);
    }

    @Test
    void tagChangesRequireBothOrderQueryAndUpdatePermissions() throws Exception {
        var create = OrderTagController.class.getMethod("create", OrderTagCreateForm.class)
                .getAnnotation(SaCheckPermission.class);
        var apply = OrderTagController.class.getMethod("apply", OrderTagAssignForm.class)
                .getAnnotation(SaCheckPermission.class);
        assertThat(create.value()).containsExactly(OrderPermission.QUERY, OrderPermission.UPDATE);
        assertThat(apply.value()).containsExactly(OrderPermission.QUERY, OrderPermission.UPDATE);
        assertThat(create.mode()).isEqualTo(SaMode.AND);
        assertThat(apply.mode()).isEqualTo(SaMode.AND);
    }
}
