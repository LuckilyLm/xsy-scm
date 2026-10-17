package com.xsy.scm.supplier.manager;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.supplier.domain.entity.SupplierSkuEntity;
import com.xsy.scm.supplier.domain.form.SupplierSkuItemForm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static com.xsy.scm.supplier.constant.SupplierErrorCode.SUPPLIER_SKU_DUPLICATE;

/** 供应商商品关联先比较请求与现有记录，得到新增、更新和删除差量；通过校验后再统一保存。一个供应商可以有多个优先采购来源。 */
public record SupplierSkuChangeSet(List<Matched> retained, List<SupplierSkuItemForm> inserted, List<Long> removedIds) {

    /**
     * 需要更新（含「无 id 但命中已存在 (supplierId, skuId) 而复用」）的一对行。
     */
    public record Matched(SupplierSkuEntity existing, SupplierSkuItemForm requested) {
    }

    /**
     * 计算整表替换差量。
     *
     * <p>
     * {@code requested} 为空列表表示<b>清空全部关联</b>，不是「无操作」。
     *
     * @throws ScmBusinessException
     *             40943（请求内 skuId 重复 / id 不属于该供应商 / skuId 被变更）、 40921（带 id 的行版本不一致）
     */
    /** 外部货号保留大小写和前导零，只去掉首尾空格，空串当作未提供。 */
    public static String normalizeSupplierItemCode(String raw) {
        if (raw == null)
            return null;
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static SupplierSkuChangeSet between(List<SupplierSkuEntity> existing, List<SupplierSkuItemForm> requested) {
        List<SupplierSkuItemForm> items = requested == null ? List.of() : requested;

        // 校验段 A：请求内 skuId 不得重复。
        Set<Long> requestedSkuIds = new HashSet<>();
        Set<String> supplierItemCodes = new HashSet<>();
        for (SupplierSkuItemForm item : items) {
            if (!requestedSkuIds.add(item.getSkuId())) {
                throw new ScmBusinessException(SUPPLIER_SKU_DUPLICATE);
            }
            String supplierItemCode = normalizeSupplierItemCode(item.getSupplierItemCode());
            if (supplierItemCode != null && !supplierItemCodes.add(supplierItemCode)) {
                throw new ScmBusinessException(SUPPLIER_SKU_DUPLICATE);
            }
        }

        Map<Long, SupplierSkuEntity> existingById = new HashMap<>();
        Map<Long, SupplierSkuEntity> existingBySkuId = new HashMap<>();
        for (SupplierSkuEntity row : existing) {
            existingById.put(row.getId(), row);
            existingBySkuId.put(row.getSkuId(), row);
        }

        List<Matched> retained = new ArrayList<>();
        List<SupplierSkuItemForm> inserted = new ArrayList<>();
        Set<Long> retainedIds = new HashSet<>();

        for (SupplierSkuItemForm item : items) {
            if (item.getId() != null) {
                SupplierSkuEntity row = existingById.get(item.getId());
                // 带 id 但不在该供应商名下时拒绝整次替换。
                if (row == null) {
                    throw new ScmBusinessException(SUPPLIER_SKU_DUPLICATE);
                }
                // 已存在行的 skuId 不允许变更；换 SKU 应删旧增新。
                if (!Objects.equals(row.getSkuId(), item.getSkuId())) {
                    throw new ScmBusinessException(SUPPLIER_SKU_DUPLICATE);
                }
                if (!Objects.equals(row.getVersion(), item.getVersion())) {
                    throw new ScmBusinessException(VERSION_CONFLICT);
                }
                retained.add(new Matched(row, item));
                retainedIds.add(row.getId());
                continue;
            }

            // 无 id：先按 (supplierId, skuId) 复用既有行，避免撞唯一索引。
            SupplierSkuEntity row = existingBySkuId.get(item.getSkuId());
            if (row != null) {
                // 复用路径的版本号是可选的：客户端可能并不知道这一行已经存在
                if (item.getVersion() != null && !Objects.equals(row.getVersion(), item.getVersion())) {
                    throw new ScmBusinessException(VERSION_CONFLICT);
                }
                retained.add(new Matched(row, item));
                retainedIds.add(row.getId());
            } else {
                inserted.add(item);
            }
        }

        // 库中有、请求里没有的行软删；空请求会清空全部关联。
        List<Long> removedIds = new ArrayList<>();
        for (SupplierSkuEntity row : existing) {
            if (!retainedIds.contains(row.getId())) {
                removedIds.add(row.getId());
            }
        }

        return new SupplierSkuChangeSet(List.copyOf(retained), List.copyOf(inserted), List.copyOf(removedIds));
    }
}
