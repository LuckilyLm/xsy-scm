package net.lab1024.sa.admin.module.scm.supplier.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.constant.ScmEnableStatusEnum;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.supplier.dao.SupplierSkuDao;
import net.lab1024.sa.admin.module.scm.supplier.domain.entity.SupplierSkuEntity;
import net.lab1024.sa.admin.module.scm.supplier.domain.form.SupplierSkuQueryForm;
import net.lab1024.sa.admin.module.scm.supplier.domain.form.SupplierSkuReplaceForm;
import net.lab1024.sa.admin.module.scm.supplier.domain.vo.SupplierSkuVO;
import net.lab1024.sa.admin.module.scm.supplier.manager.SupplierSkuSyncManager;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import net.lab1024.sa.admin.module.system.employee.dao.EmployeeDao;
import net.lab1024.sa.admin.module.system.employee.domain.vo.EmployeeVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static net.lab1024.sa.admin.module.scm.supplier.constant.SupplierErrorCode.SKU_DISABLED;
import static net.lab1024.sa.admin.module.scm.supplier.constant.SupplierErrorCode.SUPPLIER_SKU_NOT_FOUND;

/**
 * 商品-供应商关联的读写。
 *
 * <p>写路径只有一个入口 {@link #replace}（整表替换），读路径有两条：按供应商回填、按 SKU 反查。
 */
@Service
@RequiredArgsConstructor
public class SupplierSkuService {

    private final SupplierSkuDao dao;

    private final SupplierSkuSyncManager syncManager;

    private final SupplierService supplierService;

    private final EmployeeDao employees;

    /** 按供应商列出活动关联行，供替换编辑页回填。 */
    public List<SupplierSkuVO> listBySupplierId(Long supplierId) {
        // 供应商必须存在，否则回填一个不存在的供应商会得到「空列表」这种歧义结果
        supplierService.require(supplierId);
        List<SupplierSkuEntity> rows = dao.selectActiveBySupplierId(supplierId);
        return enrich(rows);
    }

    /** 只读反查分页（按 SKU 找供应商）。 */
    public PageResult<SupplierSkuVO> query(SupplierSkuQueryForm form) {
        var page = SmartPageUtil.convert2PageQuery(form);
        if (page.orders().isEmpty()) {
            page.addOrder(OrderItem.desc("is_default"), OrderItem.asc("id"));
        }
        List<SupplierSkuEntity> rows = dao.queryPage(page, form);
        return SmartPageUtil.convert2PageResult(page, enrich(rows));
    }

    /**
     * 整表替换某供应商的商品关联。
     *
     * <p>事务边界在这里，而不是在 Manager——Manager 只负责规则与落库，不决定事务范围。
     */
    @Transactional(rollbackFor = Exception.class)
    public void replace(SupplierSkuReplaceForm form) {
        syncManager.replace(form.getSupplierId(), form.getItems());
    }

    /**
     * 校验「某 SKU 可以由某供应商供货」。
     *
     * <p>W2 没有采购域，因此当前没有生产调用方；方法先落地，作为 W3 采购下单的唯一判定入口
     * （legacy 不变量 R17）——不允许 W3 旁路主数据直接查 {@code supplier_sku} 表。
     */
    public SupplierSkuEntity requireEnabledForPurchasing(Long supplierId, Long skuId) {
        supplierService.requireEnabled(supplierId);
        SupplierSkuEntity entity = dao.selectOne(new LambdaQueryWrapper<SupplierSkuEntity>()
                .eq(SupplierSkuEntity::getSupplierId, supplierId)
                .eq(SupplierSkuEntity::getSkuId, skuId)
                .eq(SupplierSkuEntity::getStatus, ScmEnableStatusEnum.ENABLED.name()));
        if (entity == null) {
            throw new ScmBusinessException(SUPPLIER_SKU_NOT_FOUND);
        }
        // SKU 与 SPU 必须同时上架，否则视为不可采购
        boolean orderable = dao.selectEnabledBySkuId(skuId).stream()
                .anyMatch(row -> Objects.equals(row.getSupplierId(), supplierId));
        if (!orderable) {
            throw new ScmBusinessException(SKU_DISABLED);
        }
        return entity;
    }

    private List<SupplierSkuVO> enrich(List<SupplierSkuEntity> rows) {
        List<SupplierSkuVO> list = new ArrayList<>(rows.size());
        if (rows.isEmpty()) {
            return list;
        }
        Map<Long, String> purchaserNames = purchaserNames(rows);
        for (SupplierSkuEntity row : rows) {
            SupplierSkuVO vo = new SupplierSkuVO();
            BeanUtils.copyProperties(row, vo);
            vo.setPurchaserName(purchaserNames.get(row.getPurchaserId()));
            list.add(vo);
        }
        return list;
    }

    private Map<Long, String> purchaserNames(List<SupplierSkuEntity> rows) {
        Set<Long> ids = new LinkedHashSet<>();
        rows.forEach(row -> {
            if (row.getPurchaserId() != null) {
                ids.add(row.getPurchaserId());
            }
        });
        Map<Long, String> names = new HashMap<>();
        if (ids.isEmpty()) {
            return names;
        }
        List<EmployeeVO> found = employees.getEmployeeByIds(ids);
        if (found != null) {
            found.stream().filter(e -> e != null && e.getEmployeeId() != null)
                    .forEach(e -> names.put(e.getEmployeeId(), e.getActualName()));
        }
        // 员工已被删除时保持 null，不阻断列表展示
        return names;
    }
}
