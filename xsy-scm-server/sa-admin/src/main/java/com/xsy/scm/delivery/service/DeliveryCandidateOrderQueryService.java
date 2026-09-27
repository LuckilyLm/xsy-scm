package com.xsy.scm.delivery.service;

import com.xsy.scm.common.scope.ScmDataScopeException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.delivery.dao.DeliveryQueryDao;
import com.xsy.scm.delivery.domain.form.DeliveryQueryForm;
import com.xsy.scm.delivery.domain.vo.DeliveryCandidateVO;
import com.xsy.scm.delivery.permission.DeliveryPermission;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;

/**
 * 全量候选订单（待排线池）的只读入口。
 *
 * <p>候选池暴露<b>尚未分配</b>的订单、客户地址与电话，因此需要线路规划权限和至少一个授权仓库；
 * 司机只持签收权限，不能读取候选池。
 */
@Service
@RequiredArgsConstructor
public class DeliveryCandidateOrderQueryService {
    /** 读取候选池前必须持有线路规划权限。 */

    private final DeliveryQueryDao deliveryQueryDao;
    private final DeliveryEligibilityPolicy policy;
    private final ScmDataScopeService dataScopeService;

    public PageResult<DeliveryCandidateVO> query(DeliveryQueryForm form) {
        if (!ScmDataScopeService.hasPermission(DeliveryPermission.ROUTE_PLAN))
            throw new ScmDataScopeException();
        var page = DeliveryRouteQueryService.page(form);
        // sales_order 上没有仓库列，因此仓库维度是「能不能取候选」的前置条件而非行级过滤；
        // 无任何授权仓的调用者拿不到候选行，也就无法把订单编进线路。
        var warehouseScope = dataScopeService.resolve().getWarehouseScope();
        if (warehouseScope.isEmpty()) return ScmDataScopeService.emptyPage(form);
        return SmartPageUtil.convert2PageResult(page, DeliveryVisibility.current()
                .candidates(deliveryQueryDao.candidates(page, form, policy.candidateStatuses(), warehouseScope)));
    }
}
