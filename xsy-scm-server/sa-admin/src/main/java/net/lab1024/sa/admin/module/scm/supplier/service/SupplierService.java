package net.lab1024.sa.admin.module.scm.supplier.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.constant.ScmEnableStatusEnum;
import net.lab1024.sa.admin.module.scm.common.constant.ScmOperator;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.supplier.dao.SupplierDao;
import net.lab1024.sa.admin.module.scm.supplier.dao.SupplierSkuDao;
import net.lab1024.sa.admin.module.scm.supplier.domain.entity.SupplierEntity;
import net.lab1024.sa.admin.module.scm.supplier.domain.form.SupplierAddForm;
import net.lab1024.sa.admin.module.scm.supplier.domain.form.SupplierDeleteForm;
import net.lab1024.sa.admin.module.scm.supplier.domain.form.SupplierStatusForm;
import net.lab1024.sa.admin.module.scm.supplier.domain.form.SupplierUpdateForm;
import net.lab1024.sa.admin.module.scm.supplier.manager.SupplierValidator;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Objects;

import static net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static net.lab1024.sa.admin.module.scm.supplier.constant.SupplierErrorCode.SUPPLIER_CODE_DUPLICATE;
import static net.lab1024.sa.admin.module.scm.supplier.constant.SupplierErrorCode.SUPPLIER_DISABLED;
import static net.lab1024.sa.admin.module.scm.supplier.constant.SupplierErrorCode.SUPPLIER_IN_USE;
import static net.lab1024.sa.admin.module.scm.supplier.constant.SupplierErrorCode.SUPPLIER_NOT_FOUND;

/**
 * 供应商写路径。
 *
 * <p>两条 legacy 不变量在方法签名层面就固化下来：
 * <ul>
 *   <li>S6：{@link #update} 不触碰 {@code status}——表单里根本没有该字段；</li>
 *   <li>S7：{@link #add} 强制 {@code ENABLED}——不接受客户端指定初始状态。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierDao dao;

    private final SupplierSkuDao supplierSkuDao;

    /**
     * 读取供应商，不存在或已删除 → 40440。
     */
    public SupplierEntity require(Long supplierId) {
        SupplierEntity entity = supplierId == null ? null : dao.selectById(supplierId);
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
     * 读取「可用」供应商：必须存在且 {@code ENABLED}（legacy 不变量 S3）。
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
        String code = SupplierValidator.normalizeCode(form.getSupplierCode());
        if (existsCode(code, null)) {
            throw new ScmBusinessException(SUPPLIER_CODE_DUPLICATE);
        }
        SupplierEntity entity = new SupplierEntity();
        apply(entity, form);
        // S7：新建供应商强制启用，不接受客户端指定
        entity.setStatus(ScmEnableStatusEnum.ENABLED.name());
        entity.setVersion(0);
        entity.setDeleted(false);
        stamp(entity, true);
        try {
            dao.insert(entity);
        } catch (DuplicateKeyException e) {
            throw new ScmBusinessException(SUPPLIER_CODE_DUPLICATE);
        }
        return entity.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(SupplierUpdateForm form) {
        SupplierEntity entity = require(form.getSupplierId(), form.getVersion());
        String code = SupplierValidator.normalizeCode(form.getSupplierCode());
        if (existsCode(code, form.getSupplierId())) {
            throw new ScmBusinessException(SUPPLIER_CODE_DUPLICATE);
        }
        apply(entity, form);
        // S6：apply 不触碰 status —— 状态只能通过 updateStatus 变更
        entity.setVersion(form.getVersion());
        stamp(entity, false);
        try {
            if (dao.updateById(entity) != 1) {
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
        if (dao.updateById(entity) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }

    /**
     * 删除供应商。
     *
     * <p>legacy 没有删除端点，W2 新增（Target Design Q4）。被活动 {@code supplier_sku} 引用时拒绝——
     * 否则商品关联会指向一个不存在的供应商。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(SupplierDeleteForm form) {
        require(form.getSupplierId(), form.getVersion());
        if (supplierSkuDao.countActiveBySupplierId(form.getSupplierId()) > 0) {
            throw new ScmBusinessException(SUPPLIER_IN_USE);
        }
        if (dao.softDelete(form.getSupplierId(), form.getVersion(), ScmOperator.current()) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }

    /**
     * 活动记录内编码是否已存在（编码大小写不敏感：先归一化再比较）。
     */
    public boolean existsCode(String normalizedCode, Long excludeId) {
        LambdaQueryWrapper<SupplierEntity> wrapper = new LambdaQueryWrapper<SupplierEntity>()
                .eq(SupplierEntity::getSupplierCode, normalizedCode);
        if (excludeId != null) {
            wrapper.ne(SupplierEntity::getId, excludeId);
        }
        return dao.selectCount(wrapper) > 0;
    }

    private void apply(SupplierEntity entity, SupplierAddForm form) {
        entity.setSupplierCode(SupplierValidator.normalizeCode(form.getSupplierCode()));
        entity.setName(SupplierValidator.normalizeName(form.getName()));
        entity.setContactName(SupplierValidator.normalizeOptional(form.getContactName()));
        entity.setContactPhone(SupplierValidator.normalizeOptional(form.getContactPhone()));
        entity.setAddress(SupplierValidator.normalizeOptional(form.getAddress()));
        entity.setProvinceCode(form.getProvinceCode());
        entity.setProvinceName(SupplierValidator.normalizeOptional(form.getProvinceName()));
        entity.setCityCode(form.getCityCode());
        entity.setCityName(SupplierValidator.normalizeOptional(form.getCityName()));
        entity.setDistrictCode(form.getDistrictCode());
        entity.setDistrictName(SupplierValidator.normalizeOptional(form.getDistrictName()));
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
