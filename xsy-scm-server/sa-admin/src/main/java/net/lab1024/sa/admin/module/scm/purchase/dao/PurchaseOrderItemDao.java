package net.lab1024.sa.admin.module.scm.purchase.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import net.lab1024.sa.admin.module.scm.common.scope.ScmValueScope;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseOrderItemEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseReceiptItemWorkbenchQueryForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseReceiptItemWorkbenchVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 采购单行（W5 Target Design §5.6，**Q13**）。
 *
 * <p>行身份 = {@code (purchase_order_id, sku_id)}，由
 * {@code uk_purchase_order_item_order_sku_active} 强制。
 *
 * <p>**`received_quantity` 只增不减**，且**不受 `planned_quantity` 上限约束** ——
 * 超收上限是运行时 `t_config` 容差值（`planned × (1 + tolerance/100)`），
 * 静态 CHECK 表达不了（见 V15 的注释）。累计的唯一入口是
 * {@link #accumulateReceived}，且必须持有行锁（{@link #lockByOrderId}）。
 */
@Mapper
public interface PurchaseOrderItemDao extends BaseMapper<PurchaseOrderItemEntity> {

    /**
     * 本单全部活动行，按 `sort_order` 排序。
     */
    List<PurchaseOrderItemEntity> listByOrderId(@Param("purchaseOrderId") Long purchaseOrderId);

    /**
     * 多单活动行（批量详情用）。
     */
    List<PurchaseOrderItemEntity> listByOrderIds(@Param("ids") List<Long> ids);

    /**
     * 锁定本单全部行（`ORDER BY id ASC FOR UPDATE`）。
     *
     * <p>收货确认必须先锁行再累计：两笔收货并发收同一剩余量时，
     * 只有行锁能让第二笔看到第一笔的 `received_quantity`。
     */
    List<PurchaseOrderItemEntity> lockByOrderId(@Param("purchaseOrderId") Long purchaseOrderId);

    /**
     * 单行 `FOR UPDATE`。
     */
    PurchaseOrderItemEntity lock(@Param("id") Long id);

    /**
     * 累计已收数量（`received_quantity = received_quantity + delta`），不做上限判定。
     */
    int accumulateReceived(@Param("id") Long id,
                           @Param("delta") BigDecimal delta,
                           @Param("operator") String operator);

    /**
     * 软删单行。
     */
    int softDelete(@Param("id") Long id,
                   @Param("version") Integer version,
                   @Param("operator") String operator);

    /**
     * 软删本单全部行（整单删除时调用）。
     */
    int softDeleteByOrderId(@Param("purchaseOrderId") Long purchaseOrderId,
                            @Param("operator") String operator);

    /**
     * 活动行数（DELETE / 状态推导用）。
     */
    int countActiveByOrderId(@Param("purchaseOrderId") Long purchaseOrderId);

    /**
     * 按商品收货工作台（Wave 2B §6.3，只读）：跨「可收货」采购单按 {@code (sku_id, 采购单位)} 归并计划 / 已收量。
     *
     * <p>分页 count 必须走聚合组数（调用方置 {@code optimizeCountSql=false}），排序固定，不接受客户端排序。
     *
     * <p>{@code scope} 是调用者的仓库授权范围，谓词落在 {@code purchase_order.warehouse_id} 上：
     * 收货单的仓库在建单时从采购单继承，因此这一列同时就是收货行所在的那个仓。
     * {@code null} 在 Mapper 里失败关闭，不表示「全部」。
     */
    List<PurchaseReceiptItemWorkbenchVO> workbench(Page<?> page,
                                                   @Param("query") PurchaseReceiptItemWorkbenchQueryForm form,
                                                   @Param("scope") ScmValueScope scope);
}
