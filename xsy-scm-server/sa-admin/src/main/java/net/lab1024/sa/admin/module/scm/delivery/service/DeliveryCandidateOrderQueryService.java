package net.lab1024.sa.admin.module.scm.delivery.service;

import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeService;
import net.lab1024.sa.admin.module.scm.delivery.dao.DeliveryQueryDao;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.DeliveryQueryForm;
import net.lab1024.sa.admin.module.scm.delivery.domain.vo.DeliveryCandidateVO;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;

/**
 * 全量候选订单（待排线池）的只读入口。
 *
 * <p>调度 / 线路规划员与司机不是同一个角色（裁决「P0 基线收口裁决」第 9 条）：候选池暴露的是
 * <b>尚未分配</b>的订单、客户地址与电话，普通司机不得查询，因此这里要求的不是线路查询权，
 * 而是组单权 {@code scm:delivery:route:plan} 加上「调用者至少有一个授权仓库」。
 */
@Service
@RequiredArgsConstructor
public class DeliveryCandidateOrderQueryService {
    /** 组单 / 规划权；与 V43 种下的功能点逐字一致。 */
    public static final String ROUTE_PLAN_PERM = "scm:delivery:route:plan";

    private final DeliveryQueryDao queries;
    private final DeliveryEligibilityPolicy policy;
    private final ScmDataScopeService scopeService;

    public PageResult<DeliveryCandidateVO> query(DeliveryQueryForm form) {
        if (!ScmDataScopeService.hasPermission(ROUTE_PLAN_PERM))
            throw new ScmDataScopeException();
        var page = DeliveryRouteQueryService.page(form);
        // sales_order 上没有仓库列，因此仓库维度是「能不能取候选」的前置条件而非行级过滤；
        // 无任何授权仓的调用者拿不到候选行，也就无法把订单编进线路。
        var warehouseScope = scopeService.resolve().getWarehouseScope();
        if (warehouseScope.isEmpty()) return ScmDataScopeService.emptyPage(form);
        return SmartPageUtil.convert2PageResult(page, DeliveryVisibility.current()
                .candidates(queries.candidates(page, form, policy.candidateStatuses(), warehouseScope)));
    }
}
