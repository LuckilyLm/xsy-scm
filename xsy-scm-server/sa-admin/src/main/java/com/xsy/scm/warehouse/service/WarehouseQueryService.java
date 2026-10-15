package com.xsy.scm.warehouse.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.warehouse.constant.ScmWarehouseStatusEnum;
import com.xsy.scm.warehouse.dao.WarehouseDao;
import com.xsy.scm.warehouse.domain.entity.WarehouseEntity;
import com.xsy.scm.warehouse.domain.form.WarehouseQueryForm;
import com.xsy.scm.warehouse.domain.vo.WarehouseVO;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR;

/** 仓库查询按员工授权范围过滤；下拉列表只返回可用仓库，管理列表仍显示管理范围内的其他状态。 */
@Service
@RequiredArgsConstructor
public class WarehouseQueryService {

    /**
     * 排序白名单：只有这些列允许来自客户端。
     */
    private static final Set<String> SORTABLE = Set.of("warehouse_code", "name", "status", "created_at", "updated_at");

    private final WarehouseDao warehouseDao;

    private final WarehouseService warehouseService;

    private final ScmDataScopeService dataScopeService;

    /**
     * 下拉选择器：只返回 {@code ENABLED} 且落在授权范围内的仓库，按编码排序。
     */
    public List<WarehouseVO> list() {
        ScmValueScope scope = dataScopeService.resolve().getWarehouseScope();
        if (scope.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<WarehouseEntity> query = new LambdaQueryWrapper<WarehouseEntity>()
                .eq(WarehouseEntity::getStatus, ScmWarehouseStatusEnum.ENABLED.name());
        if (!scope.isAll()) {
            query.in(WarehouseEntity::getId, scope.getIds());
        }
        return warehouseDao.selectList(query.orderByAsc(WarehouseEntity::getWarehouseCode, WarehouseEntity::getId))
                .stream().map(WarehouseQueryService::toVO).toList();
    }

    public PageResult<WarehouseVO> query(WarehouseQueryForm form) {
        assertSortable(form);
        ScmValueScope scope = dataScopeService.resolve().getWarehouseScope();
        if (scope.isEmpty()) {
            return ScmDataScopeService.emptyPage(form);
        }
        var page = SmartPageUtil.convert2PageQuery(form);
        if (page.orders().isEmpty()) {
            page.addOrder(OrderItem.asc("warehouse_code"), OrderItem.asc("id"));
        }
        List<WarehouseEntity> rows = warehouseDao.queryPage(page, form, scope);
        List<WarehouseVO> list = new ArrayList<>(rows.size());
        rows.forEach(row -> list.add(toVO(row)));
        return SmartPageUtil.convert2PageResult(page, list);
    }

    /**
     * 仓库详情；未授权的仓按无权限回答（30005），不按「不存在」回答，否则探测仓库编号与探测授权可以分辨出来。
     */
    public WarehouseVO detail(Long id) {
        WarehouseEntity entity = warehouseService.require(id);
        if (!dataScopeService.resolve().getWarehouseScope().allows(entity.getId())) {
            throw new ScmDataScopeException();
        }
        return toVO(entity);
    }

    private void assertSortable(WarehouseQueryForm form) {
        if (form.getSortItemList() == null) {
            return;
        }
        boolean illegal = form.getSortItemList().stream()
                .anyMatch(item -> item.getColumn() == null || !SORTABLE.contains(item.getColumn().toLowerCase()));
        if (illegal) {
            throw new ScmBusinessException(VALIDATION_ERROR);
        }
    }

    private static WarehouseVO toVO(WarehouseEntity entity) {
        WarehouseVO vo = new WarehouseVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
