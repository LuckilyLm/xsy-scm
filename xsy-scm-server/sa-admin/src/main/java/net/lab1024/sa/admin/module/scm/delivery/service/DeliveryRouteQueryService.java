package net.lab1024.sa.admin.module.scm.delivery.service;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import net.lab1024.sa.admin.module.scm.delivery.dao.*;
import net.lab1024.sa.admin.module.scm.delivery.domain.entity.*;
import net.lab1024.sa.admin.module.scm.delivery.domain.form.*;
import net.lab1024.sa.admin.module.scm.delivery.domain.vo.*;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import static net.lab1024.sa.admin.module.scm.delivery.constant.DeliveryErrorCode.*;
import static net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR;
@Service @RequiredArgsConstructor
public class DeliveryRouteQueryService {
    private final DeliveryQueryDao queries;
    private final DeliveryRouteOrderDao orders;
    public static Page<?> page(DeliveryQueryForm form) {
        // SQL owns deterministic sorting. Never pass arbitrary client sort columns to MyBatis.
        if (form.getPageNum()==null || form.getPageNum()<1 || form.getPageSize()==null || form.getPageSize()<1 || form.getPageSize()>500
                || form.getSortItemList()!=null && !form.getSortItemList().isEmpty()) throw new ScmBusinessException(VALIDATION_ERROR);
        return SmartPageUtil.convert2PageQuery(form);
    }
    public PageResult<DeliveryRouteVO> query(DeliveryQueryForm form) {
        var page=page(form); return SmartPageUtil.convert2PageResult(page,queries.routes(page,form));
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public DeliveryDetailVO detail(Long id) {
        var route=queries.route(id); if(route==null) throw new ScmBusinessException(NOT_FOUND);
        var result=new DeliveryDetailVO();result.setRoute(route); result.setStops(queries.stops(id));
        result.setOrders(orders.selectList(new LambdaQueryWrapper<DeliveryRouteOrderEntity>()
            .eq(DeliveryRouteOrderEntity::getRouteId,id)
            .eq(!"CANCELLED".equals(route.getStatus()),DeliveryRouteOrderEntity::getAssignmentStatus,"ACTIVE")
            .orderByAsc(DeliveryRouteOrderEntity::getStopId,DeliveryRouteOrderEntity::getId)));
        return result;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public DeliveryPrintVO print(Long id) {
        var detail=detail(id);
        if(!java.util.Set.of("PLANNED","DISPATCHED","COMPLETED").contains(detail.getRoute().getStatus())) throw new ScmBusinessException(STATE_INVALID);
        var result=new DeliveryPrintVO();result.setDetail(detail);result.setItems(queries.printItems(id));return result;
    }
}
