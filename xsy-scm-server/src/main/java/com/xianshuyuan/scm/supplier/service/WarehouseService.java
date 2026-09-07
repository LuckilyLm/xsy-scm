package com.xianshuyuan.scm.supplier.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.customer.entity.EnabledStatus;
import com.xianshuyuan.scm.supplier.converter.SupplierConverter;
import com.xianshuyuan.scm.supplier.dto.MasterDataStatusRequest;
import com.xianshuyuan.scm.supplier.dto.WarehouseSaveRequest;
import com.xianshuyuan.scm.supplier.entity.WarehouseEntity;
import com.xianshuyuan.scm.supplier.mapper.WarehouseMapper;
import com.xianshuyuan.scm.supplier.vo.WarehouseVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WarehouseService {
    private final WarehouseMapper warehouses;

    public WarehouseService(WarehouseMapper warehouses) {
        this.warehouses = warehouses;
    }

    public WarehouseEntity requireWarehouse(long id) {
        WarehouseEntity entity = warehouses.selectById(id);
        if (entity == null || Boolean.TRUE.equals(entity.getDeleted())) {
            throw new BusinessException(SupplierErrorCodes.WAREHOUSE_NOT_FOUND);
        }
        return entity;
    }

    public WarehouseEntity requireEnabledWarehouse(long id) {
        WarehouseEntity entity = requireWarehouse(id);
        if (entity.getStatus() != EnabledStatus.ENABLED) {
            throw new BusinessException(SupplierErrorCodes.DISABLED);
        }
        return entity;
    }

    public List<WarehouseVO> listWarehouses() {
        return warehouses.selectList(new LambdaQueryWrapper<WarehouseEntity>()
                        .eq(WarehouseEntity::getDeleted, false)
                        .orderByAsc(WarehouseEntity::getName))
                .stream().map(SupplierConverter::toVO).toList();
    }

    public WarehouseVO warehouseView(long id) {
        return SupplierConverter.toVO(requireWarehouse(id));
    }

    @Transactional
    public long createWarehouse(WarehouseSaveRequest request) {
        WarehouseEntity entity = new WarehouseEntity();
        entity.setWarehouseCode(request.warehouseCode());
        entity.setName(request.name());
        entity.setAddress(request.address());
        entity.setRemark(request.remark());
        entity.setStatus(EnabledStatus.ENABLED);
        entity.setVersion(0);
        entity.setDeleted(false);
        entity.setCreatedBy("SYSTEM");
        warehouses.insert(entity);
        return entity.getId();
    }

    @Transactional
    public void updateWarehouse(long id, WarehouseSaveRequest request) {
        requireWarehouse(id);
        if (request.version() == null) {
            throw new BusinessException(SupplierErrorCodes.VERSION_CONFLICT);
        }
        WarehouseEntity entity = new WarehouseEntity();
        entity.setId(id);
        entity.setWarehouseCode(request.warehouseCode());
        entity.setName(request.name());
        entity.setAddress(request.address());
        entity.setRemark(request.remark());
        entity.setVersion(request.version());
        entity.setUpdatedBy("SYSTEM");
        if (warehouses.updateById(entity) != 1) {
            throw new BusinessException(SupplierErrorCodes.VERSION_CONFLICT);
        }
    }

    @Transactional
    public void updateWarehouseStatus(long id, MasterDataStatusRequest request) {
        WarehouseEntity entity = requireWarehouse(id);
        entity.setVersion(request.version());
        entity.setStatus(request.status());
        entity.setUpdatedBy("SYSTEM");
        if (warehouses.updateById(entity) != 1) {
            throw new BusinessException(SupplierErrorCodes.VERSION_CONFLICT);
        }
    }
}
