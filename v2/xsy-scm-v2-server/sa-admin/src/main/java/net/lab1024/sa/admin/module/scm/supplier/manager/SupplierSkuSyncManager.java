package net.lab1024.sa.admin.module.scm.supplier.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.constant.ScmEnableStatusEnum;
import net.lab1024.sa.admin.module.scm.common.constant.ScmOperator;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.common.util.ScmDecimalStrings;
import net.lab1024.sa.admin.module.scm.supplier.dao.SupplierDao;
import net.lab1024.sa.admin.module.scm.supplier.dao.SupplierSkuDao;
import net.lab1024.sa.admin.module.scm.supplier.domain.entity.SupplierEntity;
import net.lab1024.sa.admin.module.scm.supplier.domain.entity.SupplierSkuEntity;
import net.lab1024.sa.admin.module.scm.supplier.domain.form.SupplierSkuItemForm;
import net.lab1024.sa.admin.module.scm.supplier.domain.vo.OrderableSkuVO;
import net.lab1024.sa.admin.module.system.employee.dao.EmployeeDao;
import net.lab1024.sa.admin.module.system.employee.domain.entity.EmployeeEntity;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static net.lab1024.sa.admin.module.scm.supplier.constant.SupplierErrorCode.SKU_DISABLED;
import static net.lab1024.sa.admin.module.scm.supplier.constant.SupplierErrorCode.SUPPLIER_DISABLED;
import static net.lab1024.sa.admin.module.scm.supplier.constant.SupplierErrorCode.SUPPLIER_NOT_FOUND;
import static net.lab1024.sa.admin.module.scm.supplier.constant.SupplierErrorCode.SUPPLIER_SKU_CONFLICT;
import static net.lab1024.sa.admin.module.scm.supplier.constant.SupplierErrorCode.SUPPLIER_SKU_PURCHASER_INVALID;

/**
 * {@code supplier_sku} 的差量落库。
 *
 * <p><b>严格保留 legacy 的四段式（R2 / R6）：</b>
 * <ol>
 *   <li><b>校验段 A</b>：请求内一致性（skuId 不重复、id 归属正确）——由
 *       {@link SupplierSkuChangeSet#between} 完成；</li>
 *   <li><b>锁段</b>：先锁 {@code supplier} 行，再锁 {@code supplier_sku} 行。锁序固定，
 *       且 supplier 的启用状态在持有行锁之后才判断，避免「判断完状态、供应商刚好被停用」的窗口；</li>
 *   <li><b>校验段 B</b>：对每条待写行做外部校验（采购员存在、SKU 可下单、构造快照）。
 *       这一步<b>只构造、不写库</b>；</li>
 *   <li><b>写段</b>：校验段 A/B 全部通过后才开始更新 / 插入 / 软删。</li>
 * </ol>
 *
 * <p>Manager 不控制事务——事务边界在 {@code SupplierSkuService.replace}。
 */
@Component
@RequiredArgsConstructor
public class SupplierSkuSyncManager {

    private final SupplierDao supplierDao;

    private final SupplierSkuDao supplierSkuDao;

    private final EmployeeDao employeeDao;

    /**
     * 用请求列表整表替换某供应商的商品关联。
     *
     * <p>空列表表示清空全部关联（R11），不是「无操作」。
     */
    public void replace(Long supplierId, List<SupplierSkuItemForm> items) {
        // ---- 锁段 ----
        SupplierEntity supplier = supplierDao.selectActiveByIdForUpdate(supplierId);
        if (supplier == null) {
            throw new ScmBusinessException(SUPPLIER_NOT_FOUND);
        }
        if (!ScmEnableStatusEnum.ENABLED.name().equals(supplier.getStatus())) {
            throw new ScmBusinessException(SUPPLIER_DISABLED);
        }
        List<SupplierSkuEntity> existing = supplierSkuDao.selectActiveBySupplierIdForUpdate(supplierId);

        // ---- 校验段 A（差量计算内含请求内一致性校验） ----
        SupplierSkuChangeSet changeSet = SupplierSkuChangeSet.between(existing, items);

        // ---- 校验段 B：只构造、不写库 ----
        List<Planned> retained = planRetained(changeSet, supplier);
        List<Planned> inserted = planInserted(changeSet, supplier);

        // ---- 写段 ----
        String operator = ScmOperator.current();
        OffsetDateTime now = OffsetDateTime.now();

        for (Planned planned : retained) {
            SupplierSkuEntity entity = planned.existing();
            fill(entity, planned, operator, now);
            entity.setVersion(planned.existing().getVersion());
            try {
                if (supplierSkuDao.updateById(entity) != 1) {
                    throw new ScmBusinessException(VERSION_CONFLICT);
                }
            } catch (DuplicateKeyException e) {
                throw new ScmBusinessException(SUPPLIER_SKU_CONFLICT);
            }
        }

        for (Planned planned : inserted) {
            SupplierSkuEntity entity = new SupplierSkuEntity();
            fill(entity, planned, operator, now);
            entity.setSupplierId(supplierId);
            entity.setVersion(0);
            entity.setDeleted(false);
            entity.setCreatedAt(now);
            entity.setCreatedBy(operator);
            try {
                supplierSkuDao.insert(entity);
            } catch (DuplicateKeyException e) {
                throw new ScmBusinessException(SUPPLIER_SKU_CONFLICT);
            }
        }

        Map<Long, Integer> versionById = new HashMap<>();
        existing.forEach(row -> versionById.put(row.getId(), row.getVersion()));
        for (Long removedId : changeSet.removedIds()) {
            Integer version = versionById.get(removedId);
            if (supplierSkuDao.softDeleteOwnedWithVersion(supplierId, removedId, version, operator) != 1) {
                throw new ScmBusinessException(VERSION_CONFLICT);
            }
        }
    }

    /** 构造待更新的行：先批量校验采购员与 SKU，再逐条组装。 */
    private List<Planned> planRetained(SupplierSkuChangeSet changeSet, SupplierEntity supplier) {
        List<Planned> planned = new ArrayList<>(changeSet.retained().size());
        Map<Long, OrderableSkuVO> skus = loadOrderableSkus(collectSkuIds(changeSet));
        validatePurchasers(changeSet);
        for (SupplierSkuChangeSet.Matched matched : changeSet.retained()) {
            planned.add(new Planned(matched.existing(), matched.requested(),
                    skus.get(matched.requested().getSkuId()), supplier));
        }
        return planned;
    }

    /** 构造待插入的行。 */
    private List<Planned> planInserted(SupplierSkuChangeSet changeSet, SupplierEntity supplier) {
        List<Planned> planned = new ArrayList<>(changeSet.inserted().size());
        Map<Long, OrderableSkuVO> skus = loadOrderableSkus(collectSkuIds(changeSet));
        for (SupplierSkuItemForm item : changeSet.inserted()) {
            planned.add(new Planned(null, item, skus.get(item.getSkuId()), supplier));
        }
        return planned;
    }

    private Set<Long> collectSkuIds(SupplierSkuChangeSet changeSet) {
        Set<Long> ids = new LinkedHashSet<>();
        changeSet.retained().forEach(matched -> ids.add(matched.requested().getSkuId()));
        changeSet.inserted().forEach(item -> ids.add(item.getSkuId()));
        return ids;
    }

    /**
     * 批量取可下单 SKU（SPU 与 SKU 同时上架）。
     *
     * <p>缺任何一个 skuId 都意味着它不存在 / 已软删 / 未上架 → 40942。
     */
    private Map<Long, OrderableSkuVO> loadOrderableSkus(Set<Long> skuIds) {
        Map<Long, OrderableSkuVO> map = new HashMap<>();
        if (skuIds.isEmpty()) {
            return map;
        }
        List<OrderableSkuVO> rows = supplierSkuDao.selectOrderableSkuByIds(skuIds);
        if (rows != null) {
            rows.forEach(row -> map.put(row.getSkuId(), row));
        }
        for (Long skuId : skuIds) {
            if (!map.containsKey(skuId)) {
                throw new ScmBusinessException(SKU_DISABLED);
            }
        }
        return map;
    }

    /** 批量校验默认采购员存在（引用 SmartAdmin {@code t_employee}，不旁路主数据）。 */
    private void validatePurchasers(SupplierSkuChangeSet changeSet) {
        Set<Long> purchaserIds = new HashSet<>();
        changeSet.retained().forEach(matched -> {
            if (matched.requested().getPurchaserId() != null) {
                purchaserIds.add(matched.requested().getPurchaserId());
            }
        });
        changeSet.inserted().forEach(item -> {
            if (item.getPurchaserId() != null) {
                purchaserIds.add(item.getPurchaserId());
            }
        });
        if (purchaserIds.isEmpty()) {
            return;
        }
        List<EmployeeEntity> found = employeeDao.selectList(new LambdaQueryWrapper<EmployeeEntity>()
                .in(EmployeeEntity::getEmployeeId, purchaserIds));
        Set<Long> existing = new HashSet<>();
        if (found != null) {
            found.forEach(employee -> existing.add(employee.getEmployeeId()));
        }
        for (Long purchaserId : purchaserIds) {
            if (!existing.contains(purchaserId)) {
                throw new ScmBusinessException(SUPPLIER_SKU_PURCHASER_INVALID);
            }
        }
    }

    /** 把请求行与快照写入实体；不负责主键、版本与审计字段。 */
    private void fill(SupplierSkuEntity entity, Planned planned, String operator, OffsetDateTime now) {
        SupplierSkuItemForm item = planned.requested();
        OrderableSkuVO sku = planned.sku();
        SupplierEntity supplier = planned.supplier();

        entity.setSkuId(item.getSkuId());
        entity.setSupplierCodeSnapshot(supplier.getSupplierCode());
        entity.setSupplierNameSnapshot(supplier.getName());
        entity.setSkuCodeSnapshot(sku.getSkuCode());
        // 名称取 SPU 名称而不是 SKU 规格名（legacy 不变量 R4）
        entity.setSkuNameSnapshot(sku.getProductName());
        entity.setSpecValuesSnapshot(sku.getSpecValues() == null ? Map.of() : sku.getSpecValues());
        entity.setPurchaseUnit(item.getPurchaseUnit());
        entity.setReferencePrice(ScmDecimalStrings.parseScale4(item.getReferencePrice()));
        entity.setPurchaserId(item.getPurchaserId());
        // 刻意不做「只允许一条默认」的校验：同一供应商允许多条默认来源（R12）
        entity.setDefaultFlag(Boolean.TRUE.equals(item.getDefaultFlag()));
        entity.setStatus(item.getStatus() == null ? ScmEnableStatusEnum.ENABLED.name() : item.getStatus());
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(operator);
    }

    /** 一条待写行：既有实体（新增时为 {@code null}）+ 请求行 + 快照来源 + 供应商。 */
    private record Planned(SupplierSkuEntity existing,
                           SupplierSkuItemForm requested,
                           OrderableSkuVO sku,
                           SupplierEntity supplier) {
    }
}
