package com.xianshuyuan.scm.supplier.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.customer.entity.EnabledStatus;
import com.xianshuyuan.scm.supplier.converter.SupplierConverter;
import com.xianshuyuan.scm.supplier.dto.SupplierSaveRequest;
import com.xianshuyuan.scm.supplier.entity.SupplierEntity;
import com.xianshuyuan.scm.supplier.mapper.SupplierMapper;
import com.xianshuyuan.scm.supplier.vo.SupplierVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SupplierService {
    private final SupplierMapper suppliers;

    public SupplierService(SupplierMapper suppliers) {
        this.suppliers = suppliers;
    }

    public SupplierEntity requireSupplier(long id) {
        var e = suppliers.selectById(id);
        if (e == null || Boolean.TRUE.equals(e.getDeleted()))
            throw new BusinessException(SupplierErrorCodes.SUPPLIER_NOT_FOUND);
        return e;
    }

    public SupplierEntity requireEnabledSupplier(long id) {
        var e = requireSupplier(id);
        if (e.getStatus() != EnabledStatus.ENABLED) throw new BusinessException(SupplierErrorCodes.DISABLED);
        return e;
    }

    public SupplierEntity requireEnabledSupplierForUpdate(long id) {
        var entity = suppliers.selectActiveByIdForUpdate(id);
        if (entity == null) {
            throw new BusinessException(SupplierErrorCodes.SUPPLIER_NOT_FOUND);
        }
        if (entity.getStatus() != EnabledStatus.ENABLED) {
            throw new BusinessException(SupplierErrorCodes.DISABLED);
        }
        return entity;
    }

    public List<SupplierVO> listSuppliers() {
        return suppliers.selectList(new LambdaQueryWrapper<SupplierEntity>()
                        .eq(SupplierEntity::getDeleted, false)
                        .orderByAsc(SupplierEntity::getName))
                .stream().map(SupplierConverter::toVO).toList();
    }

    public SupplierVO supplierView(long id) {
        return SupplierConverter.toVO(requireSupplier(id));
    }

    @Transactional
    public long createSupplier(SupplierSaveRequest r) {
        var e = new SupplierEntity();
        e.setSupplierCode(r.supplierCode());
        e.setName(r.name());
        e.setRemark(r.remark());
        e.setStatus(EnabledStatus.ENABLED);
        e.setVersion(0);
        e.setDeleted(false);
        e.setCreatedBy("SYSTEM");
        suppliers.insert(e);
        return e.getId();
    }

    @Transactional
    public void updateSupplier(long id, SupplierSaveRequest r) {
        requireSupplier(id);
        if (r.version() == null) throw new BusinessException(SupplierErrorCodes.VERSION_CONFLICT);
        var e = new SupplierEntity();
        e.setId(id);
        e.setSupplierCode(r.supplierCode());
        e.setName(r.name());
        e.setRemark(r.remark());
        e.setVersion(r.version());
        e.setUpdatedBy("SYSTEM");
        if (suppliers.updateById(e) != 1) throw new BusinessException(SupplierErrorCodes.VERSION_CONFLICT);
    }

    @Transactional
    public void updateSupplierStatus(long id, int version, EnabledStatus status) {
        var entity = requireSupplier(id);
        entity.setVersion(version);
        entity.setStatus(status);
        entity.setUpdatedBy("SYSTEM");
        if (suppliers.updateById(entity) != 1) {
            throw new BusinessException(SupplierErrorCodes.VERSION_CONFLICT);
        }
    }

}
