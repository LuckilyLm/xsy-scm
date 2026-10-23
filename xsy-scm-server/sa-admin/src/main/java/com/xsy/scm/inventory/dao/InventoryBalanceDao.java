package com.xsy.scm.inventory.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.inventory.domain.entity.InventoryBalanceEntity;
import com.xsy.scm.inventory.domain.form.InventoryBalanceQueryForm;
import com.xsy.scm.inventory.domain.vo.InventoryBalanceVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 库存余额的数据库操作。首次入库可并发创建余额行；此后数量只能在持锁后增减，不能直接覆盖。创建记录时必须匹配数据库的有效余额唯一索引。
 */
@Mapper
public interface InventoryBalanceDao extends BaseMapper<InventoryBalanceEntity> {

    /**
     * 并发安全的「首建余额行」：冲突目标与部分唯一索引完全匹配。
     *
     * <p>
     * 返回 1 = 本次插入了新行；返回 0 = 行已存在。两种情况都不是错误：调用方随后统一走 {@link #lockByWarehouseAndSku} 拿行锁， 先插入与后插入的调用方最终都会拿到同一行。
     */
    int insertOnConflictDoNothing(@Param("warehouseId") Long warehouseId, @Param("skuId") Long skuId,
        @Param("unit") String unit, @Param("operator") String operator);

    /**
     * 锁定余额行（{@code SELECT... FOR UPDATE}）。
     *
     * <p>
     * 加锁顺序：余额锁总是整个事务里最后获取的锁，多把余额锁之间必须按 {@code (warehouse_id, sku_id)} 升序获取（排序发生在调用方
     * {@code PurchaseReceiptService.confirm}）。
     */
    InventoryBalanceEntity lockByWarehouseAndSku(@Param("warehouseId") Long warehouseId, @Param("skuId") Long skuId);

    /**
     * 无锁读余额行（只读查询路径 / {@code queryAvailability}）。
     */
    InventoryBalanceEntity selectByWarehouseAndSku(@Param("warehouseId") Long warehouseId, @Param("skuId") Long skuId);

    /**
     * 持锁后的增量更新（{@code quantity = quantity + ?}）。
     *
     * <p>
     * 为什么用增量而不是赋值：赋值会把「读到的值 + 增量」变成两步写，一旦漏了行锁就会丢失更新；增量在数据库侧完成，配合行锁没有时间窗口。{@code version} 同步自增，作为额外保护。
     *
     * @return 影响行数，必须为 1（否则说明行被删除或 id 不存在 → 调用方抛 40921）
     */
    int incrementQuantity(@Param("id") Long id, @Param("quantity") BigDecimal quantity,
        @Param("operator") String operator);

    /**
     * 持锁后的增量扣减（{@code quantity = quantity - ?}），用于出库。
     *
     * <p>
     * 与 {@link #incrementQuantity} 同一要求：只在持行锁之后调用，且是数据库侧增量而非赋值。
     *
     * @return 影响行数，必须为 1（否则说明行被删除或 id 不存在 → 调用方抛 40921）
     */
    int decrementQuantity(@Param("id") Long id, @Param("quantity") BigDecimal quantity,
        @Param("operator") String operator);

    /**
     * 持锁后的预留占用自增（{@code reserved_quantity = reserved_quantity + ?}）。
     *
     * <p>
     * 只改预留计数，不动 {@code quantity} —— 预留不改变物理库存。「占用不得超过现有量」由 {@code ck_inventory_balance_available} 在数据库层兜底。
     */
    int incrementReserved(@Param("id") Long id, @Param("quantity") BigDecimal quantity,
        @Param("operator") String operator);

    /**
     * 持锁后的预留占用自减（释放预留）。
     */
    int decrementReserved(@Param("id") Long id, @Param("quantity") BigDecimal quantity,
        @Param("operator") String operator);

    /**
     * 持锁后的数量增量 + 成本重算，一条语句完成（移动加权）。
     *
     * <p>
     * 与 {@link #incrementQuantity} 同一要求：只在持有行锁之后调用，且是数据库侧赋值而不是「读-改-写」—— 调用方在持锁状态下算出新均价，这里一次写入。
     *
     * <p>
     * 为什么合并成一条：这是一次逻辑上的行变更，{@code version} 应当只自增一次。拆成「先增量、再改均价」会让一张收货单把余额 version 顶高 2，而 version 是用于判断这行是否变动过的计数器，跳 2
     * 会让这个信息失真。两条语句之间也不存在需要被观察到的中间态。
     *
     * <p>
     * 三条带成本记录的入库路径调用它：采购入库、调拨转入（成本回读发出流水）、规格转换转入（成本由调用方按持锁时读到的数据换算）。报溢等不带来新成本记录的入库按现有均价入账、均价不变，走普通的
     * {@link #incrementQuantity}；出库不改均价。
     *
     * @return 影响行数，必须为 1
     */
    int incrementQuantityAndSetAvgCost(@Param("id") Long id, @Param("quantity") BigDecimal quantity,
        @Param("avgCost") BigDecimal avgCost, @Param("operator") String operator);

    /**
     * 余额分页（联仓库 / SKU / 商品取展示字段）。
     *
     * <p>
     * {@code scope} 为 null 时 SQL 退化为恒假条件：数据范围必须由调用方显式下传，「没传就等于全部」会让任何漏传的新调用点变成越权入口。
     */
    List<InventoryBalanceVO> queryPage(Page<?> page, @Param("query") InventoryBalanceQueryForm query,
        @Param("scope") ScmValueScope scope);

    /**
     * 余额详情（按 id）。
     */
    InventoryBalanceVO detail(@Param("id") Long id);

    /**
     * 导出盘点 Excel 模板用：取某仓库全部活动余额行（联 SKU / 商品展示字段），按 {@code sku_id} 升序。
     *
     * <p>
     * 模板记录这一仓库当前所有可盘点余额，因此不做分页、不接客户端排序；排序固定 {@code sku_id}，与确认阶段的加锁顺序一致，保证凭证来源集合稳定可复核。
     */
    List<InventoryBalanceVO> listActiveByWarehouse(@Param("warehouseId") Long warehouseId);
}
