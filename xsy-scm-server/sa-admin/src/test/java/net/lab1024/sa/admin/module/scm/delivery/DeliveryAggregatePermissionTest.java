package net.lab1024.sa.admin.module.scm.delivery;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import net.lab1024.sa.admin.module.scm.delivery.controller.DeliveryRouteController;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.DeliveryPrintCustomersForm;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.DeliveryPrintOrdersForm;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.DeliveryQueryForm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 配送线路聚合出口的权限交集。
 *
 * <p>线路详情、地图视图、待选订单、订单/客户视角与三个打印入口的返回体都带订单事实
 * （订单号、收货人地址、明细行锁定价与结算额），单靠配送侧权限就是读订单的旁路——
 * 与缺口预览、客户 360 同型。
 *
 * <p>断言针对<b>注解形状</b>：两条权限 + {@code AND}。运行时拦截由底座 Sa-Token 过滤器承担，
 * 不在此处复刻框架行为。
 */
@DisplayName("配送聚合出口权限交集：配送侧权限 AND 订单查看权")
class DeliveryAggregatePermissionTest {

    private static final String ROUTE_QUERY = "scm:delivery:route:query";
    private static final String ROUTE_PRINT = "scm:delivery:route:print";
    private static final String ORDER_QUERY = "scm:order:query";

    /** 端点名 + 该端点在配送侧应有的那条权限（另一条固定为订单查看权）。 */
    private record Endpoint(String name, SaCheckPermission permission, String deliveryPermission) {
        /** 参数化用例名只取端点名，避免把注解代理的 toString 打进报告。 */
        @Override
        public String toString() {
            return name;
        }
    }

    private static Endpoint endpoint(String methodName, String deliveryPermission, Class<?>... parameterTypes) {
        Method method;
        try {
            method = DeliveryRouteController.class.getMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("配送控制器缺少方法：" + methodName + "，签名变更需同步本用例", e);
        }
        SaCheckPermission permission = method.getAnnotation(SaCheckPermission.class);
        assertThat(permission).as("%s 必须声明 @SaCheckPermission", methodName).isNotNull();
        return new Endpoint(methodName, permission, deliveryPermission);
    }

    static Stream<Endpoint> orderReadingEndpoints() {
        return Stream.of(
                // detail 与 map 返回同一份 DeliveryDetailVO，其中的 orders 是
                // DeliveryRouteOrderEntity 清单（orderNoSnapshot / customerId / orderAmountSnapshot）。
                endpoint("detail", ROUTE_QUERY, Long.class),
                endpoint("map", ROUTE_QUERY, Long.class),
                endpoint("print", ROUTE_PRINT, Long.class),
                endpoint("ordersView", ROUTE_QUERY, Long.class),
                endpoint("customersView", ROUTE_QUERY, Long.class),
                endpoint("candidates", ROUTE_QUERY, DeliveryQueryForm.class),
                endpoint("printOrders", ROUTE_PRINT, Long.class, DeliveryPrintOrdersForm.class, String.class),
                endpoint("printCustomers", ROUTE_PRINT, Long.class, DeliveryPrintCustomersForm.class, String.class));
    }

    @ParameterizedTest(name = "{0} 必须同时声明配送侧权限与订单查看权")
    @MethodSource("orderReadingEndpoints")
    void declaresBothPermissions(Endpoint endpoint) {
        assertThat(List.of(endpoint.permission().value()))
                .containsExactlyInAnyOrder(endpoint.deliveryPermission(), ORDER_QUERY);
    }

    @ParameterizedTest(name = "{0} 的模式必须是 AND：OR 等于没有收紧")
    @MethodSource("orderReadingEndpoints")
    void modeIsAndNotOr(Endpoint endpoint) {
        assertThat(endpoint.permission().mode()).isEqualTo(SaMode.AND);
    }

    @Test
    @DisplayName("纯线路主数据接口未被顺手扩权（修旁路不能把本域接口一起改严）")
    void routeMasterDataPermissionUnchanged() {
        // 这些出口只返回线路/选择项本身，不含任何订单事实，多加一条订单查看权只会打断配送岗。
        assertThat(permissions("list", DeliveryQueryForm.class)).containsExactly(ROUTE_QUERY);
        assertThat(permissions("driverOptions")).containsExactly(ROUTE_QUERY);
        assertThat(permissions("vehicleOptions")).containsExactly(ROUTE_QUERY);
        assertThat(permissions("warehouseOptions")).containsExactly(ROUTE_QUERY);
    }

    private static List<String> permissions(String methodName, Class<?>... parameterTypes) {
        return List.of(endpoint(methodName, ROUTE_QUERY, parameterTypes).permission().value());
    }
}
