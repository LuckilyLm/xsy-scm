package net.lab1024.sa.admin.module.scm.purchase;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import net.lab1024.sa.admin.module.scm.inventory.controller.InventoryBalanceController;
import net.lab1024.sa.admin.module.scm.purchase.controller.PurchaseDemandController;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseDemandSummaryPreviewForm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 缺口预览的权限交集（Wave 2A 审计修复）。
 *
 * <p>预览返回库存现有量与预留量，因此单靠采购需求查看权就是越权读取余额的旁路。
 * 这里的断言针对**注解形状**：两条权限 + {@code AND}。运行时拦截由底座 Sa-Token
 * 过滤器承担（W6 权限 IT 已验收），业务 Web 测试不复刻框架行为，因此不在此处重测。
 */
@DisplayName("缺口预览权限交集：采购需求查看权 AND 库存余额查看权")
class PurchaseDemandSummaryPreviewPermissionTest {

    private static final String DEMAND_QUERY = "scm:purchase:demand:query";

    private static final String BALANCE_QUERY = "scm:inventory:balance:query";

    private SaCheckPermission previewPermission() throws NoSuchMethodException {
        Method method = PurchaseDemandController.class
                .getMethod("summaryPreview", PurchaseDemandSummaryPreviewForm.class);
        SaCheckPermission permission = method.getAnnotation(SaCheckPermission.class);
        assertThat(permission).as("summary-preview 必须声明 @SaCheckPermission").isNotNull();
        return permission;
    }

    @Test
    @DisplayName("预览端点同时声明两条查询权限，缺一即越权路径复活")
    void declaresBothQueryPermissions() throws NoSuchMethodException {
        assertThat(List.of(previewPermission().value()))
                .containsExactlyInAnyOrder(DEMAND_QUERY, BALANCE_QUERY);
    }

    @Test
    @DisplayName("模式必须是 AND：OR 会让只有采购权限的人照样读到余额")
    void modeIsAndNotOr() throws NoSuchMethodException {
        SaCheckPermission permission = previewPermission();
        assertThat(permission.mode()).isEqualTo(SaMode.AND);
    }

    @Test
    @DisplayName("库存余额接口自身的权限未被下调或改写（修权限不能靠放宽另一侧）")
    void inventoryBalancePermissionUnchanged() throws NoSuchMethodException {
        Method query = InventoryBalanceController.class.getMethod("query",
                net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryBalanceQueryForm.class);
        assertThat(List.of(query.getAnnotation(SaCheckPermission.class).value())).containsExactly(BALANCE_QUERY);
    }
}
