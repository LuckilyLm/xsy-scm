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
import net.lab1024.sa.admin.module.scm.warehouse.domain.form.WarehouseUpdateForm;
import net.lab1024.sa.admin.module.scm.warehouse.manager.WarehouseValidator;
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

    private boolean existsCode(String code, Long excludeId) {
        LambdaQueryWrapper<WarehouseEntity> wrapper = new LambdaQueryWrapper<WarehouseEntity>()
                .eq(WarehouseEntity::getWarehouseCode, code);
        if (excludeId != null) {
            wrapper.ne(WarehouseEntity::getId, excludeId);
        }
        return dao.selectCount(wrapper) > 0;
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
