package com.xsy.scm.inventory.service;

import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.inventory.dao.InventoryMovementDao;
import com.xsy.scm.inventory.domain.form.InventoryMovementQueryForm;
import com.xsy.scm.inventory.domain.vo.InventoryMovementVO;
import com.xsy.scm.report.support.ScmReportAccess;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 库存流水是不可修改的记账事实，本服务只提供查询。按流水实际发生时间和来源筛选，数据范围由仓库授权决定，不允许用读接口修改历史账。 */
@Service
@RequiredArgsConstructor
public class InventoryMovementQueryService {

    private final InventoryMovementDao inventoryMovementDao;

    private final ScmDataScopeService dataScopeService;

    @Transactional(readOnly = true)
    public PageResult<InventoryMovementVO> query(InventoryMovementQueryForm form) {
        InventoryBalanceQueryService.rejectClientSort(form);
        ScmDataScopeContext scope = dataScopeService.resolve();
        if (scope.warehouseNowhere()) {
            return ScmDataScopeService.emptyPage(form);
        }
        var page = SmartPageUtil.convert2PageQuery(form);
        List<InventoryMovementVO> list = inventoryMovementDao.queryPage(page, form, scope.getWarehouseScope());
        // unit_cost 就是这一笔入库的采购价快照，属于成本事实；可见性只按仓库判定，
        // 与 created_by 是否为空无关（回填行的 created_by 是 NULL）。
        ScmReportAccess.maskCost(list, scope.isCostVisible(), vo -> vo.setUnitCost(null));
        return SmartPageUtil.convert2PageResult(page, list);
    }
}
