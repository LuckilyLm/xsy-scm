package com.xsy.scm.purchase.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.purchase.domain.entity.PurchaseOrderItemEntity;
import com.xsy.scm.purchase.domain.form.PurchaseReceiptItemWorkbenchQueryForm;
import com.xsy.scm.purchase.domain.vo.PurchaseReceiptItemWorkbenchVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/** 同一采购单内每个商品规格最多一行，数据库唯一索引保证。数量变更与需求分配由服务层统一校验，不能只靠页面去重。 */
@Mapper
public interface PurchaseOrderItemDao extends BaseMapper<PurchaseOrderItemEntity> {

    /**
     * 本单全部活动行，按 {@code sort_order} 排序。
     */
    List<PurchaseOrderItemEntity> listByOrderId(@Param("purchaseOrderId") Long purchaseOrderId);

    /**
     * 多单活动行（批量详情用）。
     */
    List<PurchaseOrderItemEntity> listByOrderIds(@Param("ids") List<Long> ids);

    /**
     * 锁定本单全部行（{@code ORDER BY id ASC FOR UPDATE}）。
     *
     * <p>
     * 收货确认必须先锁行再累计：两笔收货并发收同一剩余量时，只有行锁能让第二笔看到第一笔的 {@code received_quantity}。
     */
    List<PurchaseOrderItemEntity> lockByOrderId(@Param("purchaseOrderId") Long purchaseOrderId);

    /**
     * 单行 {@code FOR UPDATE}。
     */
    PurchaseOrderItemEntity lock(@Param("id") Long id);

    /**
     * 累计已收数量（{@code received_quantity = received_quantity + delta}），不做上限判定。
     */
    int accumulateReceived(@Param("id") Long id, @Param("delta") BigDecimal delta, @Param("operator") String operator);

    /**
     * 软删单行。
     */
    int softDelete(@Param("id") Long id, @Param("version") Integer version, @Param("operator") String operator);

    /**
     * 软删本单全部行（整单删除时调用）。
     */
    int softDeleteByOrderId(@Param("purchaseOrderId") Long purchaseOrderId, @Param("operator") String operator);

    /**
     * 活动行数（DELETE / 状态推导用）。
     */
    int countActiveByOrderId(@Param("purchaseOrderId") Long purchaseOrderId);

    /** 按商品规格和采购单位汇总可收货采购明细，只读不改采购和库存。分页必须统计分组数量，不能把明细行数当作汇总行数。 */
    List<PurchaseReceiptItemWorkbenchVO> workbench(Page<?> page,
            @Param("query") PurchaseReceiptItemWorkbenchQueryForm form, @Param("scope") ScmValueScope scope);
}
