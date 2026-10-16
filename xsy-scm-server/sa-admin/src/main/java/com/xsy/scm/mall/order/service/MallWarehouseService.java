package com.xsy.scm.mall.order.service;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.customer.domain.entity.CustomerEntity;
import com.xsy.scm.customer.service.CustomerService;
import com.xsy.scm.mall.account.constant.MallErrorCode;
import com.xsy.scm.mall.address.dao.MallCustomerAddressDao;
import com.xsy.scm.mall.address.domain.vo.MallAddressVO;
import com.xsy.scm.mall.order.dao.MallWarehouseDao;
import com.xsy.scm.mall.order.domain.vo.MallWarehouseOptionVO;
import com.xsy.scm.warehouse.service.WarehouseService;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 仅以客户绑定仓和地址区县配置解析配送仓；无匹配时失败关闭。 */
@Service
@RequiredArgsConstructor
public class MallWarehouseService {
    private final CustomerService customerService;
    private final WarehouseService warehouseService;
    private final MallCustomerAddressDao addressDao;
    private final MallWarehouseDao warehouseDao;

    @Transactional(readOnly = true)
    public List<MallWarehouseOptionVO> options(Long customerId, Long addressId) {
        CustomerEntity customer = customerService.require(customerId);
        MallAddressVO address = addressId == null
                ? addressDao.listByCustomer(customerId).stream().filter(row -> Boolean.TRUE.equals(row.getIsDefault()))
                        .findFirst().orElse(null)
                : addressDao.detailById(customerId, addressId);
        if (addressId != null && address == null) {
            throw new ScmBusinessException(MallErrorCode.ADDRESS_NOT_FOUND);
        }
        List<MallWarehouseOptionVO> result = new ArrayList<>();
        Long boundId = customer.getMallDeliveryWarehouseId();
        if (boundId != null) {
            var warehouse = warehouseService.require(boundId);
            if (!warehouseService.enabled(boundId)) {
                throw new ScmBusinessException(MallErrorCode.WAREHOUSE_UNAVAILABLE);
            }
            MallWarehouseOptionVO bound = new MallWarehouseOptionVO();
            bound.setWarehouseId(warehouse.getId());
            bound.setWarehouseName(warehouse.getName());
            bound.setSource("CUSTOMER");
            bound.setPriority(-1);
            result.add(bound);
            // 客户绑定仓是强约束，不允许用地址区县映射绕过绑定仓下单。
            return result;
        }
        if (address != null && address.getDistrictCode() != null) {
            for (MallWarehouseOptionVO option : warehouseDao.byDistrict(address.getDistrictCode())) {
                if (result.stream().noneMatch(row -> row.getWarehouseId().equals(option.getWarehouseId()))) {
                    result.add(option);
                }
            }
        }
        return result;
    }

    public Long require(Long customerId, Long addressId, Long warehouseId) {
        if (warehouseId == null) {
            throw new ScmBusinessException(MallErrorCode.WAREHOUSE_REQUIRED);
        }
        return options(customerId, addressId).stream().filter(option -> warehouseId.equals(option.getWarehouseId()))
                .findFirst().orElseThrow(() -> new ScmBusinessException(MallErrorCode.WAREHOUSE_UNAVAILABLE))
                .getWarehouseId();
    }

    public Long preferred(Long customerId, Long addressId) {
        return options(customerId, addressId).stream().findFirst().map(MallWarehouseOptionVO::getWarehouseId)
                .orElse(null);
    }
}
