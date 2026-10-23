package com.xsy.scm.mall.order.service;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.customer.service.CustomerService;
import com.xsy.scm.mall.account.constant.MallErrorCode;
import com.xsy.scm.mall.order.dao.MallWarehouseDao;
import com.xsy.scm.mall.order.domain.form.MallCustomerWarehouseBindingForm;
import com.xsy.scm.mall.order.domain.form.MallDistrictWarehouseForm;
import com.xsy.scm.mall.order.domain.vo.MallWarehouseOptionVO;
import com.xsy.scm.warehouse.constant.ScmWarehouseStatusEnum;
import com.xsy.scm.warehouse.domain.entity.WarehouseEntity;
import com.xsy.scm.warehouse.permission.WarehousePermission;
import com.xsy.scm.warehouse.service.WarehouseService;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理客户默认配送仓与区县仓映射；每个目标仓都受员工当前仓库范围约束。
 */
@Service
@RequiredArgsConstructor
public class MallWarehouseAdminService {
    private final MallWarehouseDao mallWarehouseDao;
    private final CustomerService customerService;
    private final WarehouseService warehouseService;
    private final ScmDataScopeService dataScopeService;

    public List<MallWarehouseOptionVO> warehouses() {
        ScmValueScope scope = dataScopeService.resolve().getWarehouseScope();
        if (scope.isEmpty())
            return List.of();
        return warehouseService.all().stream()
            .filter(warehouse -> ScmWarehouseStatusEnum.ENABLED.name().equals(warehouse.getStatus()))
            .filter(warehouse -> scope.allows(warehouse.getId())).map(MallWarehouseAdminService::option).toList();
    }

    public List<MallWarehouseOptionVO> districtRules(Integer districtCode) {
        if (districtCode == null || districtCode <= 0) {
            throw new ScmBusinessException(MallErrorCode.ADDRESS_COORDINATE_INVALID);
        }
        ScmValueScope scope = dataScopeService.resolve().getWarehouseScope();
        return mallWarehouseDao.byDistrict(districtCode).stream()
            .filter(option -> scope.allows(option.getWarehouseId())).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveDistrictRule(MallDistrictWarehouseForm form) {
        requireEnabledWarehouse(form.getWarehouseId());
        mallWarehouseDao.insertDistrictMapping(form.getDistrictCode(), form.getWarehouseId(), form.getPriority(),
            com.xsy.scm.common.constant.ScmOperator.current());
    }

    @Transactional(rollbackFor = Exception.class)
    public void removeDistrictRule(Integer districtCode, Long warehouseId) {
        requireEnabledWarehouse(warehouseId);
        mallWarehouseDao.deleteDistrictMapping(districtCode, warehouseId,
            com.xsy.scm.common.constant.ScmOperator.current());
    }

    @Transactional(rollbackFor = Exception.class)
    public void bindCustomer(MallCustomerWarehouseBindingForm form) {
        if (form.getWarehouseId() != null) {
            requireEnabledWarehouse(form.getWarehouseId());
        }
        customerService.bindMallDeliveryWarehouse(form.getCustomerId(), form.getVersion(), form.getWarehouseId());
    }

    private void requireEnabledWarehouse(Long id) {
        ScmValueScope scope = dataScopeService.resolve().getWarehouseScope();
        if (!scope.allows(id))
            throw new ScmDataScopeException();
        if (!warehouseService.enabled(id))
            throw new ScmBusinessException(MallErrorCode.WAREHOUSE_UNAVAILABLE);
    }

    private static MallWarehouseOptionVO option(WarehouseEntity warehouse) {
        MallWarehouseOptionVO row = new MallWarehouseOptionVO();
        row.setWarehouseId(warehouse.getId());
        row.setWarehouseName(warehouse.getName());
        row.setSource(WarehousePermission.QUERY);
        row.setPriority(0);
        return row;
    }
}
