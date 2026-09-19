package net.lab1024.sa.admin.module.scm.inventory.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import net.lab1024.sa.admin.module.scm.inventory.domain.entity.InventoryBalanceEntity;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryBalanceQueryForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.vo.InventoryBalanceVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 库存余额读写（W6 Target Design §1 / §6.2 / §8.2）。
 *
 * <p><b>唯一的写入口</b>是 {@link #insertOnConflictDoNothing}（并发首建余额）+
 * {@link #incrementQuantity}（持锁后自增），两者都在 {@code InventoryCommandService} 里
 * **先持行锁再调用**。本接口没有任何「设置绝对数量」的方法 —— 余额不是可以被随意赋值的状态，
 * 它只能是流水的净和。
 *
 * <p><b>Q11</b>：{@link #insertOnConflictDoNothing} 的冲突目标与部分唯一索引
 * {@code uk_inventory_balance_wh_sku_active} **完全匹配**（含 {@code WHERE deleted = FALSE} 谓词）。
 * 业务代码不制造任何 PG 版本分支；该 SQL 由 PG IT 在真实 PostgreSQL 上执行验证。
 */
@Mapper
public interface InventoryBalanceDao extends BaseMapper<InventoryBalanceEntity> {

    /**
     * 并发安全的「首建余额行」：冲突目标与部分唯一索引完全匹配（Q11）。
     *
     * <p>返回 1 = 本次插入了新行（本事务是赢家）；返回 0 = 行已存在（本事务是输家）。
     * 两种情况都**不是错误**：调用方随后统一走 {@link #lockByWarehouseAndSku} 拿行锁，
     * 赢家与输家最终都会拿到**同一行**。
     */
    int insertOnConflictDoNothing(@Param("warehouseId") Long warehouseId,
                                  @Param("skuId") Long skuId,
                                  @Param("unit") String unit,
                                  @Param("operator") String operator);

    /**
     * 锁定余额行（{@code SELECT ... FOR UPDATE}）。
     *
     * <p>锁序纪律（§8.1）：余额锁永远是整个事务里**最后**获取的锁，
     * 且多把余额锁之间必须按 {@code (warehouse_id, sku_id)} 字典序升序获取
     * （排序发生在调用方 {@code PurchaseReceiptService.confirm}，见 §6.3）。
     */
    InventoryBalanceEntity lockByWarehouseAndSku(@Param("warehouseId") Long warehouseId,
                                                 @Param("skuId") Long skuId);

    /** 无锁读余额行（只读查询路径 / {@code queryAvailability}）。 */
    InventoryBalanceEntity selectByWarehouseAndSku(@Param("warehouseId") Long warehouseId,
                                                   @Param("skuId") Long skuId);

    /**
     * 持锁后的增量更新（{@code quantity = quantity + ?}）。
     *
     * <p><b>为什么是增量而不是赋值</b>：赋值会把「读到的值 + 增量」变成两步写，
     * 一旦有人漏了行锁就是丢失更新；增量在 DB 侧完成，配合行锁天然无窗口。
     * {@code version} 同步自增，作为纵深防御（§8.5）。
     *
     * @return 影响行数，必须为 1（否则说明行被删除或 id 不存在 → 调用方抛 40921）
     */
    int incrementQuantity(@Param("id") Long id,
                          @Param("quantity") BigDecimal quantity,
                          @Param("operator") String operator);

    /**
     * 持锁后的增量扣减（{@code quantity = quantity - ?}），用于出库。
     *
     * <p>与 {@link #incrementQuantity} 同一纪律：只在持行锁之后调用，且是 DB 侧增量而非赋值。
     *
     * @return 影响行数，必须为 1（否则说明行被删除或 id 不存在 → 调用方抛 40921）
     */
    int decrementQuantity(@Param("id") Long id,
                          @Param("quantity") BigDecimal quantity,
                          @Param("operator") String operator);

    /**
     * 持锁后的预留占用自增（{@code reserved_quantity = reserved_quantity + ?}）。
     *
     * <p>只改预留计数，**不动 {@code quantity}** —— 预留不改变物理库存。
     * 「占用不得超过现有量」由 {@code ck_inventory_balance_available} 在 DB 层兜底。
     */
    int incrementReserved(@Param("id") Long id,
                          @Param("quantity") BigDecimal quantity,
                          @Param("operator") String operator);

    /** 持锁后的预留占用自减（释放预留）。 */
    int decrementReserved(@Param("id") Long id,
                          @Param("quantity") BigDecimal quantity,
                          @Param("operator") String operator);

    /** 余额分页（联仓库 / SKU / 商品取展示字段，§2.1「余额是活状态」）。 */
    List<InventoryBalanceVO> queryPage(Page<?> page, @Param("query") InventoryBalanceQueryForm query);

    /** 余额详情（按 id）。 */
    InventoryBalanceVO detail(@Param("id") Long id);
}
