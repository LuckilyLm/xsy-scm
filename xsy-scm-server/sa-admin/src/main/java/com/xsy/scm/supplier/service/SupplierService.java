package com.xsy.scm.supplier.service;

import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.constant.ScmEnableStatusEnum;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.no.ScmBusinessNoService;
import com.xsy.scm.common.no.ScmBusinessNoType;
import com.xsy.scm.supplier.dao.SupplierDao;
import com.xsy.scm.supplier.dao.SupplierSkuDao;
import com.xsy.scm.supplier.domain.entity.SupplierEntity;
import com.xsy.scm.supplier.domain.form.SupplierAddForm;
import com.xsy.scm.supplier.domain.form.SupplierDeleteForm;
import com.xsy.scm.supplier.domain.form.SupplierStatusForm;
import com.xsy.scm.supplier.domain.form.SupplierUpdateForm;
import com.xsy.scm.supplier.manager.SupplierValidator;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Objects;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR;
import static com.xsy.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static com.xsy.scm.supplier.constant.SupplierErrorCode.SUPPLIER_CODE_DUPLICATE;
import static com.xsy.scm.supplier.constant.SupplierErrorCode.SUPPLIER_DISABLED;
import static com.xsy.scm.supplier.constant.SupplierErrorCode.SUPPLIER_IN_USE;
import static com.xsy.scm.supplier.constant.SupplierErrorCode.SUPPLIER_NOT_FOUND;

/**
 * 供应商写路径。
 *
 * <p>
 * 状态字段只由专用命令维护：
 * <ul>
 * <li>{@link #update} 不触碰 {@code status}，更新表单不含该字段；</li>
 * <li>{@link #add} 固定使用 {@code ENABLED}，不接受客户端指定初始状态。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierDao supplierDao;

    private final SupplierSkuDao supplierSkuDao;

    private final ScmBusinessNoService businessNoService;

    /**
     * 读取供应商，不存在或已删除 → 40440。
     */
    public SupplierEntity require(Long supplierId) {
        SupplierEntity entity = supplierId == null ? null : supplierDao.selectById(supplierId);
        if (entity == null) {
            throw new ScmBusinessException(SUPPLIER_NOT_FOUND);
        }
        return entity;
    }

    /**
     * 读取供应商并校验乐观锁版本：不存在 → 40440，版本不一致 → 40921。
     */
    public SupplierEntity require(Long supplierId, Integer version) {
        SupplierEntity entity = require(supplierId);
        if (!Objects.equals(entity.getVersion(), version)) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
        return entity;
    }

    /**
     * 读取可用供应商；记录必须存在且状态为 {@code ENABLED}。
     */
    public SupplierEntity requireEnabled(Long supplierId) {
        SupplierEntity entity = require(supplierId);
        if (!ScmEnableStatusEnum.ENABLED.name().equals(entity.getStatus())) {
            throw new ScmBusinessException(SUPPLIER_DISABLED);
        }
        return entity;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long add(SupplierAddForm form) {
        SupplierEntity entity = new SupplierEntity();
        entity.setSupplierCode(businessNoService.next(ScmBusinessNoType.SUPPLIER));
        apply(entity, form);
        // 新建供应商强制启用，不接受客户端指定状态。
        entity.setStatus(ScmEnableStatusEnum.ENABLED.name());
        entity.setVersion(0);
        entity.setDeleted(false);
        stamp(entity, true);
        try {
            supplierDao.insert(entity);
        } catch (DuplicateKeyException e) {
            throw new ScmBusinessException(SUPPLIER_CODE_DUPLICATE);
        }
        return entity.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(SupplierUpdateForm form) {
        SupplierEntity entity = require(form.getSupplierId(), form.getVersion());
        apply(entity, form);
        // apply 不触碰 status；状态只能通过 updateStatus 变更。
        entity.setVersion(form.getVersion());
        stamp(entity, false);
        try {
            if (supplierDao.updateById(entity) != 1) {
                throw new ScmBusinessException(VERSION_CONFLICT);
            }
        } catch (DuplicateKeyException e) {
            throw new ScmBusinessException(SUPPLIER_CODE_DUPLICATE);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(SupplierStatusForm form) {
        SupplierEntity entity = require(form.getSupplierId(), form.getVersion());
        entity.setStatus(form.getStatus());
        entity.setVersion(form.getVersion());
        stamp(entity, false);
        if (supplierDao.updateById(entity) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }

    /**
     * 删除供应商。
     *
     * <p>
     * 被活动 {@code supplier_sku} 引用时拒绝删除—— 否则商品关联会指向一个不存在的供应商。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(SupplierDeleteForm form) {
        require(form.getSupplierId(), form.getVersion());
        if (supplierSkuDao.countActiveBySupplierId(form.getSupplierId()) > 0) {
            throw new ScmBusinessException(SUPPLIER_IN_USE);
        }
        if (supplierDao.softDelete(form.getSupplierId(), form.getVersion(), ScmOperator.current()) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }

    private void apply(SupplierEntity entity, SupplierAddForm form) {
        entity.setName(SupplierValidator.normalizeName(form.getName()));
        entity.setPaymentPeriodDays(form.getPaymentPeriodDays() == null ? 0 : form.getPaymentPeriodDays());
        entity.setContactName(SupplierValidator.normalizeOptional(form.getContactName()));
        entity.setContactPhone(SupplierValidator.normalizeOptional(form.getContactPhone()));
        entity.setAddress(SupplierValidator.normalizeOptional(form.getAddress()));
        entity.setProvinceCode(form.getProvinceCode());
        entity.setProvinceName(SupplierValidator.normalizeOptional(form.getProvinceName()));
        entity.setCityCode(form.getCityCode());
        entity.setCityName(SupplierValidator.normalizeOptional(form.getCityName()));
        entity.setDistrictCode(form.getDistrictCode());
        entity.setDistrictName(SupplierValidator.normalizeOptional(form.getDistrictName()));
        // 点位成组落库：半组坐标在地图上无法解释，DB 的 ck_supplier_location_complete 也会拒绝。
        // 表单侧的 ScmLocationForm 已做同一条断言，这里是服务端不变量断言 —— 与客户同一取向。
        if (!form.isLocationComplete()) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
        entity.setLongitude(form.getLongitude());
        entity.setLatitude(form.getLatitude());
        entity.setGeomCrs(form.getGeomCrs());
        entity.setRemark(SupplierValidator.normalizeOptional(form.getRemark()));
    }

    private void stamp(SupplierEntity entity, boolean creating) {
        OffsetDateTime now = OffsetDateTime.now();
        String operator = ScmOperator.current();
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(operator);
        if (creating) {
            entity.setCreatedAt(now);
            entity.setCreatedBy(operator);
        }
    }
}
