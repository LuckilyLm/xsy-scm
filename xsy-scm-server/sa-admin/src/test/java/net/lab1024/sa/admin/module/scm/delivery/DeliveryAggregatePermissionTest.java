package net.lab1024.sa.admin.module.scm.delivery;

import cn.dev33.satoken.annotation.SaCheckPermission;
import net.lab1024.sa.admin.module.scm.delivery.controller.DeliveryRouteController;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.DeliveryQueryForm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 配送读接口的权限形状：只按配送侧岗位收口，不叠订单查看权。
 *
 * <p>这些出口的返回体确实带订单金额（线路合计、停靠点合计、订单金额快照、打印明细行金额），
 * 但管控点是 {@code DeliveryVisibility}：缺 {@code scm:delivery:amount:query} 时由服务端把金额
 * 字段抹成 null。裁剪行为由 {@code ScmDeliveryDataScopePgIT} 覆盖，本类只钉<b>注解形状</b>。
 *
 * <p>为什么不用 {@code AND scm:order:query}：V56 种的 {@code SCM_DRIVER} 有意只给线路查询与打印、
 * 不给金额权限。叠上订单查看权会让司机连自己那条线路的详情与发货单都取不到——岗位设计被门禁打断，
 * 而泄漏本来已由字段裁剪堵住，属重复管控。
 */
@DisplayName("配送读接口权限形状：单域收口，金额靠字段裁剪而非叠权")
class DeliveryAggregatePermissionTest {

    private static final String ROUTE_QUERY = "scm:delivery:route:query";
    private static final String ROUTE_PRINT = "scm:delivery:route:print";
    private static final String ROUTE_PLAN = "scm:delivery:route:plan";
    private static final String ORDER_QUERY = "scm:order:query";

    /** 端点名 + 应有的那条配送侧权限 + 方法签名（签名变化要同步这里，否则取不到注解）。 */
    private record Endpoint(String name, String permission, Class<?>... parameterTypes) {
    }

    private static final Endpoint LIST = new Endpoint("list", ROUTE_QUERY, DeliveryQueryForm.class);
    private static final Endpoint DETAIL = new Endpoint("detail", ROUTE_QUERY, Long.class);
    private static final Endpoint MAP = new Endpoint("map", ROUTE_QUERY, Long.class);
    private static final Endpoint ORDERS_VIEW = new Endpoint("ordersView", ROUTE_QUERY, Long.class);
    private static final Endpoint CUSTOMERS_VIEW = new Endpoint("customersView", ROUTE_QUERY, Long.class);
    private static final Endpoint CANDIDATES = new Endpoint("candidates", ROUTE_PLAN, DeliveryQueryForm.class);
    private static final Endpoint PRINT = new Endpoint("print", ROUTE_PRINT, Long.class);

    private static String[] permissions(Endpoint endpoint) {
        Method method;
        try {
            method = DeliveryRouteController.class.getMethod(endpoint.name(), endpoint.parameterTypes());
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("配送控制器缺少方法：" + endpoint.name() + "，签名变更需同步本用例", e);
        }
        SaCheckPermission permission = method.getAnnotation(SaCheckPermission.class);
        assertThat(permission).as("%s 必须声明 @SaCheckPermission", endpoint.name()).isNotNull();
        return permission.value();
    }

    static Stream<Endpoint> readEndpoints() {
        return Stream.of(LIST, DETAIL, MAP, ORDERS_VIEW, CUSTOMERS_VIEW, CANDIDATES, PRINT);
    }

    @ParameterizedTest(name = "{0} 只要求配送侧那一条权限")
    @MethodSource("readEndpoints")
    void gatedByDeliveryPermissionOnly(Endpoint endpoint) {
        assertThat(permissions(endpoint)).containsExactly(endpoint.permission());
    }

    @Test
    @DisplayName("任何配送读出口都不得叠 scm:order:query，否则司机岗被打断")
    void noEndpointDemandsOrderQuery() {
        List<String> all = readEndpoints()
                .flatMap(endpoint -> Arrays.stream(permissions(endpoint)))
                .toList();
        assertThat(all).as("金额可见性由 DeliveryVisibility 裁剪，不是叠订单查看权").doesNotContain(ORDER_QUERY);
    }

    @Test
    @DisplayName("候选池要规划权：它是尚未分配的订单加客户地址，司机岗不该拿到")
    void candidatesRequirePlanning() {
        assertThat(permissions(CANDIDATES)).containsExactly(ROUTE_PLAN);
    }
}
