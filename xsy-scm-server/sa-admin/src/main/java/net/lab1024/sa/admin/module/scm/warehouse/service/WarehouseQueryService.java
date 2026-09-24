package net.lab1024.sa.admin.module.scm.warehouse.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeException;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeService;
import net.lab1024.sa.admin.module.scm.common.scope.ScmValueScope;
import net.lab1024.sa.admin.module.scm.warehouse.constant.ScmWarehouseStatusEnum;
import net.lab1024.sa.admin.module.scm.warehouse.dao.WarehouseDao;
import net.lab1024.sa.admin.module.scm.warehouse.domain.entity.WarehouseEntity;
import net.lab1024.sa.admin.module.scm.warehouse.domain.form.WarehouseQueryForm;
import net.lab1024.sa.admin.module.scm.warehouse.domain.vo.WarehouseVO;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.VALIDATION_ERROR;

/**
 * 仓库查询（只读，不开事务）。
 *
 * <p>`GET /scm/warehouse/list` 只返回 **ENABLED** 仓库：它是给采购单/收货单的**下拉选择器**用的，
 * 与 W2 {@code CustomerTypeService.optionList()} 的口径一致；管理页用 {@code POST /query}。
 *
 * <p><b>三个读入口都按仓库授权范围收窄</b>（P0-H 裁决第 3 条）：未授权仓库的 id、名称、地址一律不给，
 * 否则选择器就成了绕过仓库范围的旁门 —— 拿到别人仓库的 id 就能提交别人的入库单。
 * 历史单据不受影响：它们展示的是自己行上的仓库名称快照，不经过本类。
 */
@Service
@RequiredArgsConstructor
public class WarehouseQueryService {

    /**
     * 排序白名单：只有这些列允许来自客户端。
     */
    private static final Set<String> SORTABLE =
            Set.of("warehouse_code", "name", "status", "created_at", "updated_at");

    private final WarehouseDao dao;

    private final WarehouseService service;

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
        return dao.selectList(query.orderByAsc(WarehouseEntity::getWarehouseCode, WarehouseEntity::getId))
                .stream()
                .map(WarehouseQueryService::toVO)
                .toList();
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
        List<WarehouseEntity> rows = dao.queryPage(page, form, scope);
        List<WarehouseVO> list = new ArrayList<>(rows.size());
        rows.forEach(row -> list.add(toVO(row)));
        return SmartPageUtil.convert2PageResult(page, list);
    }

    /**
     * 仓库详情；未授权的仓按无权限回答（30005），不按「不存在」回答，
     * 否则探测仓库编号与探测授权可以分辨出来。
     */
    public WarehouseVO detail(Long id) {
        WarehouseEntity entity = service.require(id);
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
