package net.lab1024.sa.admin.module.scm.warehouse.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.constant.ScmOperator;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.warehouse.constant.ScmWarehouseStatusEnum;
import net.lab1024.sa.admin.module.scm.warehouse.constant.WarehouseErrorCode;
import net.lab1024.sa.admin.module.scm.warehouse.dao.WarehouseDao;
import net.lab1024.sa.admin.module.scm.warehouse.domain.entity.WarehouseEntity;
import net.lab1024.sa.admin.module.scm.warehouse.domain.form.WarehouseAddForm;
import net.lab1024.sa.admin.module.scm.warehouse.domain.form.WarehouseStatusForm;
import net.lab1024.sa.admin.module.scm.warehouse.domain.form.WarehouseUpdateForm;
import net.lab1024.sa.admin.module.scm.warehouse.manager.WarehouseValidator;
import net.lab1024.sa.admin.module.scm.warehouse.support.WarehouseDisableGuard;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

import static net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;

/**
 * 仓库读写（W5 Target Design §2.1 / §5.2 / §7.1）。
 *
 * <p>**错误码边界**：本类只抛 {@code WarehouseErrorCode} 与 {@code ScmCommonErrorCode}。
 * 「仓库已停用 → 不能用于新采购单」是**采购侧规则**，错误码
 * {@code PURCHASE_WAREHOUSE_DISABLED(40987)} 属于 {@code PurchaseErrorCode}，
 * 由 {@code purchase/support/PurchaseWarehouseReferenceGuard} 判定 ——
 * 这样 {@code warehouse} 域不必反向依赖 {@code purchase} 域（F5）。
 */
@Service
@RequiredArgsConstructor
public class WarehouseService {

    private final WarehouseDao dao;

    /** 停用前置守卫（B1，HD-B1-01）：inventory 域实现，避免 warehouse 反向依赖 purchase/inventory。 */
    private final WarehouseDisableGuard disableGuard;

    /** 读取仓库，不存在或已删除 → 40485。 */
    public WarehouseEntity require(Long id) {
        WarehouseEntity entity = id == null ? null : dao.selectById(id);
        if (entity == null) {
            throw new ScmBusinessException(WarehouseErrorCode.WAREHOUSE_NOT_FOUND);
        }
        return entity;
    }

    /**
     * 读取仓库并断言**启用**。
     *
     * <p>停用时抛 {@code WAREHOUSE_NOT_FOUND} 会掩盖真实原因，因此这里**不**抛错，
     * 只把判定结果交给调用方；采购侧的 {@code PurchaseWarehouseReferenceGuard} 负责抛 40987。
     */
    public boolean enabled(Long id) {
        return ScmWarehouseStatusEnum.ENABLED.name().equals(require(id).getStatus());
    }

    /** 全量仓库（含 DISABLED），供内部逻辑使用。 */
    public List<WarehouseEntity> all() {
        return dao.selectList(new LambdaQueryWrapper<WarehouseEntity>()
                .orderByAsc(WarehouseEntity::getWarehouseCode, WarehouseEntity::getId));
    }

    /**
     * 解析「默认仓库」= 当前**唯一启用**的仓库。
     *
     * <p>为什么需要它：销售订单没有仓库字段（G-03 单仓库口径），而订单确认时要预留库存，
     * 必须落在一个具体仓库上。启用仓库恰好一个时直接返回；0 个或多个都**不猜**，
     * 抛 {@code WAREHOUSE_DEFAULT_AMBIGUOUS(41018)} —— 猜错会把货占在错误的仓库，
     * 而且要到出库/盘点才暴露。
     */
    public WarehouseEntity defaultEnabledWarehouse() {
        List<WarehouseEntity> enabled = all().stream()
                .filter(w -> ScmWarehouseStatusEnum.ENABLED.name().equals(w.getStatus()))
                .toList();
        if (enabled.size() != 1) {
            throw new ScmBusinessException(WarehouseErrorCode.WAREHOUSE_DEFAULT_AMBIGUOUS);
        }
        return enabled.getFirst();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(WarehouseAddForm form) {
        WarehouseValidator.validateRequired(form);
        String code = WarehouseValidator.normalizeCode(form.getWarehouseCode());
        if (existsCode(code, null)) {
            throw new ScmBusinessException(WarehouseErrorCode.WAREHOUSE_CODE_DUPLICATE);
        }
        WarehouseEntity entity = new WarehouseEntity();
        entity.setWarehouseCode(code);
        entity.setName(WarehouseValidator.normalizeName(form.getName()));
        // §7.2 的 WarehouseAddForm 不含 status：新建一律 ENABLED（G-03 单仓库种子语义）。
        entity.setStatus(ScmWarehouseStatusEnum.ENABLED.name());
        entity.setAddress(form.getAddress());
        applyRegion(entity, form);
        entity.setRemark(form.getRemark());
        entity.setVersion(0);
        entity.setDeleted(false);
        stamp(entity, true);
        try {
            dao.insert(entity);
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
        String code = WarehouseValidator.normalizeCode(form.getWarehouseCode());
        if (existsCode(code, form.getId())) {
            throw new ScmBusinessException(WarehouseErrorCode.WAREHOUSE_CODE_DUPLICATE);
        }
        entity.setWarehouseCode(code);
        entity.setName(WarehouseValidator.normalizeName(form.getName()));
        entity.setAddress(form.getAddress());
        applyRegion(entity, form);
        entity.setRemark(form.getRemark());
        // status 不随表单变化：§7.2 的 WarehouseUpdateForm 字段清单里没有 status，
        // 因此保持 require() 读出的原值（实体声明 updateStrategy = ALWAYS，会原值回写）。
        entity.setVersion(form.getVersion());
        stamp(entity, false);
        try {
            if (dao.updateById(entity) != 1) {
                throw new ScmBusinessException(VERSION_CONFLICT);
            }
        } catch (DuplicateKeyException e) {
            throw new ScmBusinessException(WarehouseErrorCode.WAREHOUSE_CODE_DUPLICATE);
        }
    }

    /**
     * 启用仓库（B1，HD-B1-01）：{@code DISABLED → ENABLED}，靠乐观锁 version。
     *
     * <p>重复 enable（已是 ENABLED）抛 {@code WAREHOUSE_STATE_INVALID}，不做隐式状态覆盖。
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
        if (dao.updateById(entity) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }

    /**
     * 停用仓库（B1，HD-B1-01 严格模式）：{@code ENABLED → DISABLED}。
     *
     * <p>任一阻塞条件成立（库存余额 / 在途采购单 / 待入库收货单）即拒绝，并返回对应错误码，
     * 不使用 {@code WAREHOUSE_NOT_FOUND} 掩盖真实原因。阻塞检查与状态写入在同一事务。
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
        WarehouseErrorCode blocker = disableGuard.disableBlocker(entity.getId());
        if (blocker != null) {
            throw new ScmBusinessException(blocker);
        }
        entity.setStatus(ScmWarehouseStatusEnum.DISABLED.name());
        stamp(entity, false);
        if (dao.updateById(entity) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }

    private boolean existsCode(String code, Long excludeId) {
        LambdaQueryWrapper<WarehouseEntity> wrapper = new LambdaQueryWrapper<WarehouseEntity>()
                .eq(WarehouseEntity::getWarehouseCode, code);
        if (excludeId != null) {
            wrapper.ne(WarehouseEntity::getId, excludeId);
        }
        return dao.selectCount(wrapper) > 0;
    }

    /** 省 / 市 / 区整组随表单覆盖：实体列均为 updateStrategy = ALWAYS，清空选择即写回 NULL。 */
    private void applyRegion(WarehouseEntity entity, WarehouseAddForm form) {
        entity.setProvinceCode(form.getProvinceCode());
        entity.setProvinceName(form.getProvinceName());
        entity.setCityCode(form.getCityCode());
        entity.setCityName(form.getCityName());
        entity.setDistrictCode(form.getDistrictCode());
        entity.setDistrictName(form.getDistrictName());
        if (!form.isLocationComplete()) throw new ScmBusinessException(net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR);
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
