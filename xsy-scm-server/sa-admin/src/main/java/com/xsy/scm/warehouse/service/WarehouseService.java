package com.xsy.scm.warehouse.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.no.ScmBusinessNoService;
import com.xsy.scm.common.no.ScmBusinessNoType;
import com.xsy.scm.warehouse.constant.ScmWarehouseStatusEnum;
import com.xsy.scm.warehouse.constant.WarehouseErrorCode;
import com.xsy.scm.warehouse.dao.WarehouseDao;
import com.xsy.scm.warehouse.domain.entity.WarehouseEntity;
import com.xsy.scm.warehouse.domain.form.WarehouseAddForm;
import com.xsy.scm.warehouse.domain.form.WarehouseStatusForm;
import com.xsy.scm.warehouse.domain.form.WarehouseUpdateForm;
import com.xsy.scm.warehouse.manager.WarehouseValidator;
import com.xsy.scm.warehouse.support.WarehouseDisableGuard;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;

/**
 * 维护仓库基本信息和启停状态。仓库停用的校验由仓库模块负责；新采购单使用停用仓库的限制由采购模块负责。
 */
@Service
@RequiredArgsConstructor
public class WarehouseService {

    private final WarehouseDao warehouseDao;

    /** 停用前置检查由库存域实现，避免仓库域反向依赖库存域。 */
    private final WarehouseDisableGuard warehouseDisableGuard;

    private final ScmBusinessNoService businessNoService;

    /** 读取仓库，不存在或已删除返回 40485。 */
    public WarehouseEntity require(Long id) {
        WarehouseEntity entity = id == null ? null : warehouseDao.selectById(id);
        if (entity == null) {
            throw new ScmBusinessException(WarehouseErrorCode.WAREHOUSE_NOT_FOUND);
        }
        return entity;
    }

    /** 只返回仓库是否启用；停用时用哪个错误码由采购等调用方决定。 */
    public boolean enabled(Long id) {
        return ScmWarehouseStatusEnum.ENABLED.name().equals(require(id).getStatus());
    }

    /** 全部仓库（含停用），供内部逻辑使用。 */
    public List<WarehouseEntity> all() {
        return warehouseDao.selectList(new LambdaQueryWrapper<WarehouseEntity>()
            .orderByAsc(WarehouseEntity::getWarehouseCode, WarehouseEntity::getId));
    }

    /** 只有恰好一个启用仓库时才自动选为默认仓库；没有或多个都报错，不能猜。 */
    public WarehouseEntity defaultEnabledWarehouse() {
        List<WarehouseEntity> enabled = all().stream()
            .filter(w -> ScmWarehouseStatusEnum.ENABLED.name().equals(w.getStatus())).toList();
        if (enabled.size() != 1) {
            throw new ScmBusinessException(WarehouseErrorCode.WAREHOUSE_DEFAULT_AMBIGUOUS);
        }
        return enabled.getFirst();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(WarehouseAddForm form) {
        WarehouseValidator.validateRequired(form);
        WarehouseEntity entity = new WarehouseEntity();
        entity.setWarehouseCode(businessNoService.next(ScmBusinessNoType.WAREHOUSE));
        entity.setName(WarehouseValidator.normalizeName(form.getName()));
        // WarehouseAddForm 不含 status：新建一律 ENABLED。
        entity.setStatus(ScmWarehouseStatusEnum.ENABLED.name());
        entity.setAddress(form.getAddress());
        applyRegion(entity, form);
        entity.setRemark(form.getRemark());
        // 批次追踪只在新建时决定：新建仓尚无库存，因此不需要期初批次回填。
        entity.setLotTrackingEnabled(Boolean.TRUE.equals(form.getLotTrackingEnabled()));
        entity.setVersion(0);
        entity.setDeleted(false);
        stamp(entity, true);
        try {
            warehouseDao.insert(entity);
        } catch (DuplicateKeyException e) {
            // 并发兜底：显式查重与插入之间存在窗口，由 uk_warehouse_code_active 兜住
            throw new ScmBusinessException(WarehouseErrorCode.WAREHOUSE_CODE_DUPLICATE);
        }
        return entity.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(WarehouseUpdateForm form) {
        WarehouseValidator.validateRequired(form);
        WarehouseEntity entity = require(form.getId());
        if (!Objects.equals(entity.getVersion(), form.getVersion())) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
        entity.setName(WarehouseValidator.normalizeName(form.getName()));
        entity.setAddress(form.getAddress());
        applyRegion(entity, form);
        entity.setRemark(form.getRemark());
        // status 不随表单变化：WarehouseUpdateForm 字段清单里没有 status，
        // 因此保持 require() 读出的原值（实体声明 updateStrategy = ALWAYS，会原值回写）。
        entity.setVersion(form.getVersion());
        stamp(entity, false);
        if (warehouseDao.updateById(entity) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }

    /**
     * 启用仓库（{@code DISABLED → ENABLED}），靠乐观锁 version。
     *
     * <p>
     * 已启用再启用抛 {@code WAREHOUSE_STATE_INVALID}，不做隐式覆盖。
     */
    @Transactional(rollbackFor = Exception.class)
    public void enable(WarehouseStatusForm form) {
        WarehouseEntity entity = require(form.getId());
        if (!Objects.equals(entity.getVersion(), form.getVersion())) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
        if (ScmWarehouseStatusEnum.ENABLED.name().equals(entity.getStatus())) {
            throw new ScmBusinessException(WarehouseErrorCode.WAREHOUSE_STATE_INVALID);
        }
        entity.setStatus(ScmWarehouseStatusEnum.ENABLED.name());
        stamp(entity, false);
        if (warehouseDao.updateById(entity) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }

    /**
     * 停用仓库（{@code ENABLED → DISABLED}）。
     *
     * <p>
     * 任一阻塞条件成立（库存余额 / 在途采购单 / 待入库收货单）即拒绝，并返回对应错误码。 阻塞检查与状态写入在同一事务。
     */
    @Transactional(rollbackFor = Exception.class)
    public void disable(WarehouseStatusForm form) {
        WarehouseEntity entity = require(form.getId());
        if (!Objects.equals(entity.getVersion(), form.getVersion())) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
        if (ScmWarehouseStatusEnum.DISABLED.name().equals(entity.getStatus())) {
            throw new ScmBusinessException(WarehouseErrorCode.WAREHOUSE_STATE_INVALID);
        }
        WarehouseErrorCode blocker = warehouseDisableGuard.disableBlocker(entity.getId());
        if (blocker != null) {
            throw new ScmBusinessException(blocker);
        }
        entity.setStatus(ScmWarehouseStatusEnum.DISABLED.name());
        stamp(entity, false);
        if (warehouseDao.updateById(entity) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }

    /** 省 / 市 / 区整组随表单覆盖；清空选择即写回 NULL。 */
    private void applyRegion(WarehouseEntity entity, WarehouseAddForm form) {
        entity.setProvinceCode(form.getProvinceCode());
        entity.setProvinceName(form.getProvinceName());
        entity.setCityCode(form.getCityCode());
        entity.setCityName(form.getCityName());
        entity.setDistrictCode(form.getDistrictCode());
        entity.setDistrictName(form.getDistrictName());
        if (!form.isLocationComplete())
            throw new ScmBusinessException(com.xsy.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR);
        entity.setLongitude(form.getLongitude());
        entity.setLatitude(form.getLatitude());
        entity.setGeomCrs(form.getGeomCrs());
    }

    private void stamp(WarehouseEntity entity, boolean creating) {
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
