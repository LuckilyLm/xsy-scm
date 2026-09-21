package net.lab1024.sa.admin.module.scm.supplier.manager;

import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.supplier.domain.entity.SupplierSkuEntity;
import net.lab1024.sa.admin.module.scm.supplier.domain.form.SupplierSkuItemForm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static net.lab1024.sa.admin.module.scm.supplier.constant.SupplierErrorCode.SUPPLIER_SKU_DUPLICATE;

/**
 * 整表替换的差量计算结果（legacy 不变量 R5 / R7–R11）。
 *
 * <p>把「请求列表」与「库中现存列表」对齐成三类动作，落库阶段只负责执行、不再做任何判断——
 * 这样「先全部校验、再统一写」（R6）才能成立：只要 {@link #between} 返回成功，
 * 后续写库就不会因为业务规则失败而回滚一半。
 *
 * <p><b>刻意不做的事：</b>不校验 {@code defaultFlag} 的基数。同一供应商允许多条默认来源
 * （R12），任何「只允许一条默认」的假设都会与 legacy 冲突。
 */
public record SupplierSkuChangeSet(List<Matched> retained,
                                   List<SupplierSkuItemForm> inserted,
                                   List<Long> removedIds) {

    /**
     * 需要更新（含「无 id 但命中已存在 (supplierId, skuId) 而复用」）的一对行。
     */
    public record Matched(SupplierSkuEntity existing, SupplierSkuItemForm requested) {
    }

    /**
     * 计算差量。
     *
     * @param existing  库中该供应商的全部活动行（调用方已加锁读取）
     * @param requested 请求列表；空列表表示清空全部关联
     * @throws ScmBusinessException 40943（请求内 skuId 重复 / id 不属于该供应商 / skuId 被变更）、
     *                              40921（带 id 的行版本不一致）
     */
    public static SupplierSkuChangeSet between(List<SupplierSkuEntity> existing,
                                               List<SupplierSkuItemForm> requested) {
        List<SupplierSkuItemForm> items = requested == null ? List.of() : requested;

        // 校验段 A：请求内 skuId 不得重复（R7）
        Set<Long> requestedSkuIds = new HashSet<>();
        for (SupplierSkuItemForm item : items) {
            if (!requestedSkuIds.add(item.getSkuId())) {
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
                // 带 id 但不在该供应商名下 → 40943（R8）
                if (row == null) {
                    throw new ScmBusinessException(SUPPLIER_SKU_DUPLICATE);
                }
                // 已存在行的 skuId 不允许变更（R8）——变更等价于换一条记录，应删旧增新
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

            // 无 id：先尝试按 (supplierId, skuId) 复用既有行（R10），避免撞唯一索引
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

        // 库中有、请求里没有 → 软删（R11：空请求即清空全部）
        List<Long> removedIds = new ArrayList<>();
        for (SupplierSkuEntity row : existing) {
            if (!retainedIds.contains(row.getId())) {
                removedIds.add(row.getId());
            }
        }

        return new SupplierSkuChangeSet(List.copyOf(retained), List.copyOf(inserted), List.copyOf(removedIds));
    }
}
