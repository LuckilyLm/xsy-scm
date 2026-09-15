package net.lab1024.sa.admin.module.scm.supplier.service;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.constant.ScmEnableStatusEnum;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.supplier.dao.SupplierDao;
import net.lab1024.sa.admin.module.scm.supplier.dao.SupplierSkuDao;
import net.lab1024.sa.admin.module.scm.supplier.domain.entity.SupplierEntity;
import net.lab1024.sa.admin.module.scm.supplier.domain.form.SupplierQueryForm;
import net.lab1024.sa.admin.module.scm.supplier.domain.vo.SupplierDetailVO;
import net.lab1024.sa.admin.module.scm.supplier.domain.vo.SupplierOptionVO;
import net.lab1024.sa.admin.module.scm.supplier.domain.vo.SupplierSkuCountVO;
import net.lab1024.sa.admin.module.scm.supplier.domain.vo.SupplierVO;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR;
import static net.lab1024.sa.admin.module.scm.supplier.constant.SupplierErrorCode.SUPPLIER_NOT_FOUND;

/**
 * 供应商读路径。
 *
 * <p>管理列表返回全部状态（S11），下拉只返回 {@code ENABLED}；{@code skuCount} 一次查询批量补全。
 */
@Service
@RequiredArgsConstructor
public class SupplierQueryService {

    /** 排序白名单（修正 legacy D18）。 */
    private static final Set<String> SORTABLE = Set.of("supplier_code", "name", "status", "updated_at");

    private final SupplierDao suppliers;

    private final SupplierSkuDao supplierSkus;

    public PageResult<SupplierVO> query(SupplierQueryForm form) {
        assertSortable(form);
        var page = SmartPageUtil.convert2PageQuery(form);
        if (page.orders().isEmpty()) {
            page.addOrder(OrderItem.asc("name"), OrderItem.asc("id"));
        }
        List<SupplierEntity> rows = suppliers.queryPage(page, form);

        Map<Long, Long> skuCounts = skuCounts(rows);
        List<SupplierVO> list = new ArrayList<>(rows.size());
        for (SupplierEntity row : rows) {
            SupplierVO vo = new SupplierVO();
            BeanUtils.copyProperties(row, vo);
            vo.setSupplierId(row.getId());
            vo.setSkuCount(skuCounts.getOrDefault(row.getId(), 0L));
            list.add(vo);
        }
        return SmartPageUtil.convert2PageResult(page, list);
    }

    public SupplierDetailVO detail(Long supplierId) {
        SupplierEntity entity = suppliers.selectById(supplierId);
        if (entity == null) {
            throw new ScmBusinessException(SUPPLIER_NOT_FOUND);
        }
        SupplierDetailVO vo = new SupplierDetailVO();
        BeanUtils.copyProperties(entity, vo);
        vo.setSupplierId(entity.getId());
        vo.setSkuCount(supplierSkus.countActiveBySupplierId(supplierId));
        return vo;
    }

    /** 下拉选项：只返回 {@code ENABLED}（S11），按名称排序。 */
    public List<SupplierOptionVO> optionList() {
        List<SupplierEntity> rows = suppliers.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SupplierEntity>()
                        .eq(SupplierEntity::getStatus, ScmEnableStatusEnum.ENABLED.name())
                        .orderByAsc(SupplierEntity::getName, SupplierEntity::getId));
        List<SupplierOptionVO> list = new ArrayList<>(rows.size());
        for (SupplierEntity row : rows) {
            SupplierOptionVO vo = new SupplierOptionVO();
            vo.setSupplierId(row.getId());
            vo.setSupplierCode(row.getSupplierCode());
            vo.setName(row.getName());
            list.add(vo);
        }
        return list;
    }

    private Map<Long, Long> skuCounts(List<SupplierEntity> rows) {
        Map<Long, Long> counts = new HashMap<>();
        if (rows.isEmpty()) {
            return counts;
        }
        List<Long> ids = rows.stream().map(SupplierEntity::getId).toList();
        List<SupplierSkuCountVO> found = supplierSkus.countActiveBySupplierIds(ids);
        if (found != null) {
            found.forEach(row -> counts.put(row.getSupplierId(), row.getSkuCount() == null ? 0L : row.getSkuCount()));
        }
        return counts;
    }

    private void assertSortable(SupplierQueryForm form) {
        if (form.getSortItemList() == null) {
            return;
        }
        boolean illegal = form.getSortItemList().stream()
                .anyMatch(item -> item.getColumn() == null || !SORTABLE.contains(item.getColumn().toLowerCase()));
        if (illegal) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
    }
}
