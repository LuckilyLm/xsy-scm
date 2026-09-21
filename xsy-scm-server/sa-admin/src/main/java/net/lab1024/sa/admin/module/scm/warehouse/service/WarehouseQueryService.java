package net.lab1024.sa.admin.module.scm.warehouse.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
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

    /**
     * 下拉选择器：只返回 {@code ENABLED}，按编码排序。
     */
    public List<WarehouseVO> list() {
        return dao.selectList(new LambdaQueryWrapper<WarehouseEntity>()
                        .eq(WarehouseEntity::getStatus, ScmWarehouseStatusEnum.ENABLED.name())
                        .orderByAsc(WarehouseEntity::getWarehouseCode, WarehouseEntity::getId))
                .stream()
                .map(WarehouseQueryService::toVO)
                .toList();
    }

    public PageResult<WarehouseVO> query(WarehouseQueryForm form) {
        assertSortable(form);
        var page = SmartPageUtil.convert2PageQuery(form);
        if (page.orders().isEmpty()) {
            page.addOrder(OrderItem.asc("warehouse_code"), OrderItem.asc("id"));
        }
        List<WarehouseEntity> rows = dao.queryPage(page, form);
        List<WarehouseVO> list = new ArrayList<>(rows.size());
        rows.forEach(row -> list.add(toVO(row)));
        return SmartPageUtil.convert2PageResult(page, list);
    }

    public WarehouseVO detail(Long id) {
        return toVO(service.require(id));
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
