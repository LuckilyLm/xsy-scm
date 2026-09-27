package com.xsy.scm.inventory.service;

import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.inventory.constant.ScmInventoryReservationStatusEnum;
import com.xsy.scm.inventory.dao.InventoryReservationDao;
import com.xsy.scm.inventory.domain.form.InventoryReservationQueryForm;
import com.xsy.scm.inventory.domain.vo.InventoryReservationVO;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 库存预留查询侧（只读）。
 *
 * <p>
 * 状态中文描述在服务层按枚举填充，避免前端硬编码状态字典。
 */
@Service
@RequiredArgsConstructor
public class InventoryReservationQueryService {

    private final InventoryReservationDao inventoryReservationDao;

    private final ScmDataScopeService dataScopeService;

    /**
     * 分页查询。
     */
    public PageResult<InventoryReservationVO> queryPage(InventoryReservationQueryForm query) {
        ScmDataScopeContext scope = dataScopeService.resolve();
        if (scope.warehouseNowhere()) {
            return ScmDataScopeService.emptyPage(query);
        }
        var page = SmartPageUtil.convert2PageQuery(query);
        List<InventoryReservationVO> list = inventoryReservationDao.queryPage(page, query, scope.getWarehouseScope());
        list.forEach(InventoryReservationQueryService::fillStatusDesc);
        return SmartPageUtil.convert2PageResult(page, list);
    }

    private static void fillStatusDesc(InventoryReservationVO vo) {
        for (ScmInventoryReservationStatusEnum item : ScmInventoryReservationStatusEnum.values()) {
            if (item.name().equals(vo.getStatus())) {
                vo.setStatusDesc(item.getDesc());
                return;
            }
        }
    }
}
