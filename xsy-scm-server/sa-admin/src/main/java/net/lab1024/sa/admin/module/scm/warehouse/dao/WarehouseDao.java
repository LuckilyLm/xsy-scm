package net.lab1024.sa.admin.module.scm.warehouse.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import net.lab1024.sa.admin.module.scm.common.scope.ScmValueScope;
import net.lab1024.sa.admin.module.scm.warehouse.domain.entity.WarehouseEntity;
import net.lab1024.sa.admin.module.scm.warehouse.domain.form.WarehouseQueryForm;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 仓库读写。
 *
 * <p>**没有删除方法**：W5 的仓库端点只有 list / query / detail / create / update（§7.1），
 * 仓库作为主数据不做逻辑删除。
 */
@Mapper
public interface WarehouseDao extends BaseMapper<WarehouseEntity> {

    /**
     * 分页查询；排序由 Service 的白名单校验后通过 {@link Page} 的 orders 传入。
     *
     * <p>{@code scope} 是当前员工的仓库授权范围，不是可选筛选：传 {@code null} 按 0 行处理。
     */
    List<WarehouseEntity> queryPage(Page<?> page, @Param("query") WarehouseQueryForm query,
                                    @Param("scope") ScmValueScope scope);
}
