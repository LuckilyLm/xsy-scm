package net.lab1024.sa.admin.module.scm.order;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import net.lab1024.sa.admin.module.scm.order.controller.SalesOrderController;
import net.lab1024.sa.admin.module.scm.pricing.domain.form.PriceResolveForm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 订单侧两个定价/客户事实聚合出口的权限交集。
 *
 * <p>价格预览与 {@code PriceResolveController#preview} 调用同一个 {@code PriceResolver#preview}，
 * 只挂订单查看权就等于绕开定价查看权读客户协议价；最近已确认订单价与客户 360 的 frequent-skus
 * 同数据面（指定客户的历史成交事实），门禁口径必须一致。断言注解形状，运行时拦截由底座承担。
 */
@DisplayName("订单聚合出口权限交集：订单查看权 AND 定价/客户查看权")
class OrderAggregatePermissionTest {

    private static final String ORDER_QUERY = "scm:order:query";
    private static final String PRICING_RESOLVE_QUERY = "scm:pricing:resolve:query";
    private static final String CUSTOMER_QUERY = "scm:customer:query";

    private SaCheckPermission annotation(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = SalesOrderController.class.getMethod(methodName, parameterTypes);
        SaCheckPermission permission = method.getAnnotation(SaCheckPermission.class);
        assertThat(permission).as("%s 必须声明 @SaCheckPermission", methodName).isNotNull();
        return permission;
    }

    @Test
    @DisplayName("价格预览要求订单查看权 AND 定价解析查看权，且模式为 AND")
    void pricePreviewRequiresBothPermissions() throws NoSuchMethodException {
        SaCheckPermission permission = annotation("preview", PriceResolveForm.class);
        assertThat(List.of(permission.value())).containsExactlyInAnyOrder(ORDER_QUERY, PRICING_RESOLVE_QUERY);
        assertThat(permission.mode()).isEqualTo(SaMode.AND);
    }

    @Test
    @DisplayName("最近已确认订单价要求订单查看权 AND 客户查看权，与 frequent-skus 同口径")
    void recentPricesRequiresBothPermissions() throws NoSuchMethodException {
        SaCheckPermission permission = annotation("recentPrices", Long.class, Long.class, int.class);
        assertThat(List.of(permission.value())).containsExactlyInAnyOrder(ORDER_QUERY, CUSTOMER_QUERY);
        assertThat(permission.mode()).isEqualTo(SaMode.AND);
    }

    @Test
    @DisplayName("定价解析接口自身的权限未被下调，订单列表权限未被扩宽")
    void neighbouringPermissionsUnchanged() throws NoSuchMethodException {
        Method orderQuery = SalesOrderController.class.getMethod("query",
                net.lab1024.sa.admin.module.scm.order.domain.form.SalesOrderQueryForm.class);
        assertThat(List.of(orderQuery.getAnnotation(SaCheckPermission.class).value())).containsExactly(ORDER_QUERY);
    }
}
