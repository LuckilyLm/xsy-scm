package net.lab1024.sa.admin.module.scm.delivery.service;

import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeContext;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeService;
import net.lab1024.sa.admin.module.scm.common.scope.ScmValueScope;
import net.lab1024.sa.admin.module.scm.delivery.dao.*;
import net.lab1024.sa.admin.module.scm.delivery.domain.entity.*;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.*;
import net.lab1024.sa.admin.module.scm.delivery.domain.vo.*;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;

import static net.lab1024.sa.admin.module.scm.delivery.constant.DeliveryErrorCode.*;
import static net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR;

/**
 * 配送线路的只读入口：线路级数据范围与金额字段可见性都在这里收口，
 * Mapper 只负责按 {@link ScmValueScope} 拼谓词，不在 SQL 里判断权限。
 */
@Service
@RequiredArgsConstructor
public class DeliveryRouteQueryService {
    private final DeliveryQueryDao queries;
    private final DeliveryRouteOrderDao orders;
    private final DeliveryRouteDao routeRows;
    private final ScmDataScopeService scopeService;

    public static Page<?> page(DeliveryQueryForm form) {
        // SQL owns deterministic sorting. Never pass arbitrary client sort columns to MyBatis.
        if (form.getPageNum() == null || form.getPageNum() < 1 || form.getPageSize() == null || form.getPageSize() < 1 || form.getPageSize() > 500
                || form.getSortItemList() != null && !form.getSortItemList().isEmpty())
            throw new ScmBusinessException(VALIDATION_ERROR);
        return SmartPageUtil.convert2PageQuery(form);
    }

    public PageResult<DeliveryRouteVO> query(DeliveryQueryForm form) {
        var page = page(form);
        var scope = scopeService.resolve().getDriverScope();
        // 没有任何司机授权时直接给空分页：既不跑恒假谓词，也不让空集合渲染成 IN ()。
        if (scope.isEmpty()) return ScmDataScopeService.emptyPage(form);
        return SmartPageUtil.convert2PageResult(page,
                DeliveryVisibility.current().routes(queries.routes(page, form, scope)));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DeliveryDetailVO detail(Long id) {
        var route = scopedRoute(id, scopeService.resolve());
        var result = new DeliveryDetailVO();
        result.setRoute(route);
        // 停靠点与订单关系都挂在已经放行的线路下，因此不再各自收窄：收窄子集会破坏聚合口径。
        result.setStops(queries.stops(id));
        result.setOrders(orders.selectList(new LambdaQueryWrapper<DeliveryRouteOrderEntity>()
                .eq(DeliveryRouteOrderEntity::getRouteId, id)
                .eq(!"CANCELLED".equals(route.getStatus()), DeliveryRouteOrderEntity::getAssignmentStatus, "ACTIVE")
                .orderByAsc(DeliveryRouteOrderEntity::getStopId, DeliveryRouteOrderEntity::getId)));
        return DeliveryVisibility.current().detail(result);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DeliveryPrintVO print(Long id) {
        var detail = detail(id);
        if (!java.util.Set.of("PLANNED", "DISPATCHED", "COMPLETED").contains(detail.getRoute().getStatus()))
            throw new ScmBusinessException(STATE_INVALID);
        var result = new DeliveryPrintVO();
        result.setDetail(detail);
        result.setItems(DeliveryVisibility.current().printItems(queries.printItems(id)));
        return result;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public java.util.List<DeliveryOrderViewVO> orderView(Long id) {
        scopedRoute(id, scopeService.resolve());
        return DeliveryVisibility.current().orderView(queries.orderView(id));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public java.util.List<DeliveryCustomerViewVO> customerView(Long id) {
        scopedRoute(id, scopeService.resolve());
        return DeliveryVisibility.current().customerView(queries.customerView(id));
    }

    /**
     * 按司机维度读线路；SQL 已经收口，取不到行时再分「不存在」与「存在但不归本范围」。
     * 后者按无权访问处理：越权读不是「没有这条线路」，把它报成 404 会让调度台以为数据丢了。
     */
    private DeliveryRouteVO scopedRoute(Long id, ScmDataScopeContext context) {
        var scope = context.getDriverScope();
        // 维度为空时不进 SQL：空集合会渲染成非法的 IN ()，而这一侧无论如何都读不到行。
        var route = scope.isEmpty() ? null : queries.route(id, scope);
        if (route != null) return route;
        if (routeRows.selectById(id) == null) throw new ScmBusinessException(NOT_FOUND);
        throw new ScmDataScopeException();
    }
}
