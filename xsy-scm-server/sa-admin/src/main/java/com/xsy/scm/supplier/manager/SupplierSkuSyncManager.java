package com.xsy.scm.supplier.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.constant.ScmEnableStatusEnum;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.util.ScmDecimalStrings;
import com.xsy.scm.supplier.dao.SupplierDao;
import com.xsy.scm.supplier.dao.SupplierSkuDao;
import com.xsy.scm.supplier.domain.entity.SupplierEntity;
import com.xsy.scm.supplier.domain.entity.SupplierSkuEntity;
import com.xsy.scm.supplier.domain.form.SupplierSkuItemForm;
import com.xsy.scm.supplier.domain.vo.OrderableSkuVO;
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

import static com.xsy.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static com.xsy.scm.supplier.constant.SupplierErrorCode.SKU_DISABLED;
import static com.xsy.scm.supplier.constant.SupplierErrorCode.SUPPLIER_DISABLED;
import static com.xsy.scm.supplier.constant.SupplierErrorCode.SUPPLIER_NOT_FOUND;
import static com.xsy.scm.supplier.constant.SupplierErrorCode.SUPPLIER_SKU_CONFLICT;
import static com.xsy.scm.supplier.constant.SupplierErrorCode.SUPPLIER_SKU_PURCHASER_INVALID;

/**
 * 更新供应商关联商品时，先校验全部明细，再统一新增、修改或停用。先锁供应商，再锁关联明细；事务由上层服务控制。
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
     * <p>
     * 空列表表示清空全部关联，不表示「无操作」。
     */
    public void replace(Long supplierId, List<SupplierSkuItemForm> items) {
        SupplierEntity supplier = supplierDao.selectActiveByIdForUpdate(supplierId);
        if (supplier == null) {
            throw new ScmBusinessException(SUPPLIER_NOT_FOUND);
        }
        if (!ScmEnableStatusEnum.ENABLED.name().equals(supplier.getStatus())) {
            throw new ScmBusinessException(SUPPLIER_DISABLED);
        }
        List<SupplierSkuEntity> existing = supplierSkuDao.selectActiveBySupplierIdForUpdate(supplierId);

        // 先算差量并校验，再统一写入。
        SupplierSkuChangeSet changeSet = SupplierSkuChangeSet.between(existing, items);
        List<Planned> retained = planRetained(changeSet, supplier);
        List<Planned> inserted = planInserted(changeSet, supplier);

        String operator = ScmOperator.current();
        OffsetDateTime now = OffsetDateTime.now();

        // 先删后写：货号换绑商品（旧行删除、新行插入同一货号）若先插后删会撞上
        // uk_supplier_sku_item_code_active；软删先释放货号，更新与新增才不会误判冲突。
        Map<Long, Integer> versionById = new HashMap<>();
        existing.forEach(row -> versionById.put(row.getId(), row.getVersion()));
        for (Long removedId : changeSet.removedIds()) {
            Integer version = versionById.get(removedId);
            if (supplierSkuDao.softDeleteOwnedWithVersion(supplierId, removedId, version, operator) != 1) {
                throw new ScmBusinessException(VERSION_CONFLICT);
            }
        }

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
    }

    /**
     * 构造待更新的行：先批量校验采购员与 SKU，再逐条组装。
     */
    private List<Planned> planRetained(SupplierSkuChangeSet changeSet, SupplierEntity supplier) {
        List<Planned> planned = new ArrayList<>(changeSet.retained().size());
        Map<Long, OrderableSkuVO> skus = loadOrderableSkus(collectSkuIds(changeSet));
        validatePurchasers(changeSet);
        for (SupplierSkuChangeSet.Matched matched : changeSet.retained()) {
            planned.add(new Planned(matched.existing(), matched.requested(), skus.get(matched.requested().getSkuId()),
                supplier));
        }
        return planned;
    }

    /**
     * 构造待插入的行。
     */
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
     * <p>
     * 缺任何一个 skuId 都意味着它不存在、已删除或未上架，返回 40942。
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

    /**
     * 批量校验默认采购员存在（引用 SmartAdmin {@code t_employee}，不旁路主数据）。
     */
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
        List<EmployeeEntity> found = employeeDao
            .selectList(new LambdaQueryWrapper<EmployeeEntity>().in(EmployeeEntity::getEmployeeId, purchaserIds));
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

    /** 把请求行与商品信息写入实体；不负责主键、版本与审计字段。 */
    private void fill(SupplierSkuEntity entity, Planned planned, String operator, OffsetDateTime now) {
        SupplierSkuItemForm item = planned.requested();
        OrderableSkuVO sku = planned.sku();
        SupplierEntity supplier = planned.supplier();

        entity.setSkuId(item.getSkuId());
        entity.setSupplierItemCode(SupplierSkuChangeSet.normalizeSupplierItemCode(item.getSupplierItemCode()));
        entity.setSupplierCodeSnapshot(supplier.getSupplierCode());
        entity.setSupplierNameSnapshot(supplier.getName());
        entity.setSkuCodeSnapshot(sku.getSkuCode());
        // 供应商商品名称取 SPU 名称，而不是 SKU 规格名。
        entity.setSkuNameSnapshot(sku.getProductName());
        entity.setSpecValuesSnapshot(sku.getSpecValues() == null ? Map.of() : sku.getSpecValues());
        entity.setPurchaseUnit(item.getPurchaseUnit());
        entity.setReferencePrice(ScmDecimalStrings.parseScale4(item.getReferencePrice()));
        entity.setPurchaserId(item.getPurchaserId());
        // 同一供应商允许多个默认来源，因此不校验默认标记的数量。
        entity.setDefaultFlag(Boolean.TRUE.equals(item.getDefaultFlag()));
        entity.setStatus(item.getStatus() == null ? ScmEnableStatusEnum.ENABLED.name() : item.getStatus());
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(operator);
    }

    /** 一条待写行：既有实体（新增时为 null）+ 请求行 + 商品信息 + 供应商。 */
    private record Planned(SupplierSkuEntity existing, SupplierSkuItemForm requested, OrderableSkuVO sku,
        SupplierEntity supplier) {
    }
}
