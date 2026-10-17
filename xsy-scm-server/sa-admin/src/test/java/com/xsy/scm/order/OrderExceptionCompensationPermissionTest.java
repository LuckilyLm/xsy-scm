package com.xsy.scm.order;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.xsy.scm.order.controller.OrderExchangeController;
import com.xsy.scm.order.controller.OrderReceivedCorrectionController;
import com.xsy.scm.order.domain.form.OrderExchangeCreateForm;
import com.xsy.scm.order.domain.form.OrderReceivedCorrectionForm;
import com.xsy.scm.order.permission.OrderPermission;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderExceptionCompensationPermissionTest {
    @Test
    void replacementOrderMustRespectOriginalReturnVisibilityAndOrderCreationRights() throws Exception {
        var read = OrderExchangeController.class.getMethod("replacement", Long.class)
                .getAnnotation(SaCheckPermission.class);
        var create = OrderExchangeController.class.getMethod("create", OrderExchangeCreateForm.class, String.class)
                .getAnnotation(SaCheckPermission.class);
        assertThat(read.value()).containsExactly(OrderPermission.RETURN_QUERY);
        assertThat(create.value()).containsExactly(OrderPermission.ADD, OrderPermission.RETURN_QUERY);
        assertThat(create.mode()).isEqualTo(SaMode.AND);
    }

    @Test
    void customerReceivedCorrectionRequiresQueryAndUpdatePermissions() throws Exception {
        var read = OrderReceivedCorrectionController.class.getMethod("candidate", Long.class, Long.class)
                .getAnnotation(SaCheckPermission.class);
        var create = OrderReceivedCorrectionController.class
                .getMethod("record", OrderReceivedCorrectionForm.class, String.class)
                .getAnnotation(SaCheckPermission.class);
        assertThat(read.value()).containsExactly(OrderPermission.QUERY);
        assertThat(create.value()).containsExactly(OrderPermission.QUERY, OrderPermission.UPDATE);
        assertThat(create.mode()).isEqualTo(SaMode.AND);
    }
}
