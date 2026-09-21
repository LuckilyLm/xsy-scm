package net.lab1024.sa.admin.module.scm.inventory.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import net.lab1024.sa.admin.module.scm.inventory.domain.entity.InventoryOutboundEntity;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryOutboundQueryForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.vo.InventoryOutboundVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 出库单头读写。
 *
 * <p>与余额 DAO 的关键差别：本表是**普通有状态单据**（草稿可改、可取消），
 * 因此有 update 方法。真正的不可变纪律在 {@code inventory_movement} 上，不在这里。
 */
@Mapper
public interface InventoryOutboundDao extends BaseMapper<InventoryOutboundEntity> {

    /**
     * 单号是否存在（软删范围内）。生成单号时用于冲突重试。
     */
    int countByOutboundNo(@Param("outboundNo") String outboundNo);

    /**
     * 取下一个单号序列值（PG sequence）。
     *
     * <p>与采购/收货同口径：全局单调递增、不按日 reset，**事务回滚后跳号是可接受的**。
     */
    long nextOutboundNo();

    /**
     * 无锁读（详情 / 状态校验前置）。
     */
    InventoryOutboundEntity selectById(@Param("id") Long id);

    /**
     * 锁定单据行（{@code SELECT ... FOR UPDATE}）。
     *
     * <p>锁序纪律：**单据锁先于余额锁**。确认出库时先锁本行，再按
     * {@code (warehouse_id, sku_id)} 升序锁余额行，与收货确认同一顺序，
     * 避免两条链路以相反顺序拿锁。
     */
    InventoryOutboundEntity lockById(@Param("id") Long id);

    /**
     * 置为已确认（带状态条件，防并发重复确认）。
     */
    int markConfirmed(@Param("id") Long id,
                      @Param("confirmedAt") OffsetDateTime confirmedAt,
                      @Param("operator") String operator);

    /**
     * 置为已取消（带状态条件，只有草稿能取消）。
     */
    int markCancelled(@Param("id") Long id,
                      @Param("operator") String operator);

    /**
     * 回写草稿头（仓库 / 备注）。
     */
    int updateDraft(@Param("id") Long id,
                    @Param("warehouseId") Long warehouseId,
                    @Param("remark") String remark,
                    @Param("operator") String operator);

    /**
     * 分页查询（联仓库取展示字段）。
     */
    List<InventoryOutboundVO> queryPage(Page<?> page, @Param("query") InventoryOutboundQueryForm query);

    /**
     * 详情。
     */
    InventoryOutboundVO detail(@Param("id") Long id);
}
