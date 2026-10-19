package com.xsy.scm.inventory.dao;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.inventory.domain.form.InventoryLotBalanceQueryForm;
import com.xsy.scm.inventory.domain.form.InventoryLotMovementQueryForm;
import com.xsy.scm.inventory.domain.vo.InventoryLotBalanceVO;
import com.xsy.scm.inventory.domain.vo.InventoryLotMovementVO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 批次库存与批次流水的只读查询。SQL 见 mapper/scm/inventory/InventoryLotQueryDao.xml。
 *
 * <p>
 * 仓库数据范围由 Service 显式下传：{@code scope} 为 null 时必须恒假，否则任何漏传范围的调用点都会静默变成全仓可见 （与余额查询同一条失败关闭口径）。
 */
@Mapper
public interface InventoryLotQueryDao {

    /** 批次余额分页：只列出仍有数量的批次，零余额批次留给流水回看。 */
    List<InventoryLotBalanceVO> queryBalancePage(Page<?> page, @Param("query") InventoryLotBalanceQueryForm query,
            @Param("scope") ScmValueScope scope);

    /** 批次流水分页：按发生时刻倒序，保留有符号数量。 */
    List<InventoryLotMovementVO> queryMovementPage(Page<?> page, @Param("query") InventoryLotMovementQueryForm query,
            @Param("scope") ScmValueScope scope);
}
