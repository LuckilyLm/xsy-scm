package net.lab1024.sa.admin.module.scm.inventory.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import net.lab1024.sa.admin.module.scm.inventory.domain.entity.InventoryStocktakeEntity;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryStocktakeQueryForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.vo.InventoryStocktakeVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 盘点单头读写。
 *
 * <p>与出库单 DAO 同构：本表是**普通有状态单据**（草稿可改、可取消），因此有 update 方法。
 * 真正的不可变纪律在 {@code inventory_movement} 上，不在这里。
 */
@Mapper
public interface InventoryStocktakeDao extends BaseMapper<InventoryStocktakeEntity> {

    /** 单号是否存在（软删范围内）。生成单号时用于冲突重试。 */
    int countByStocktakeNo(@Param("stocktakeNo") String stocktakeNo);

    /** 取下一个单号序列值（PG sequence，全局单调递增、不按日 reset，跳号可接受）。 */
    long nextStocktakeNo();

    /** 无锁读（详情 / 状态校验前置）。 */
    InventoryStocktakeEntity selectById(@Param("id") Long id);

    /**
     * 锁定单据行（{@code SELECT ... FOR UPDATE}）。
     *
     * <p>锁序纪律：**单据锁先于余额锁**。确认盘点时先锁本行，再按
     * {@code (warehouse_id, sku_id)} 升序锁余额行，与收货确认、出库确认同一顺序，
     * 避免三条链路以相反顺序拿锁而死锁。
     */
    InventoryStocktakeEntity lockById(@Param("id") Long id);

    /** 置为已确认（带状态条件，防并发重复确认）。 */
    int markConfirmed(@Param("id") Long id,
                      @Param("confirmedAt") OffsetDateTime confirmedAt,
                      @Param("operator") String operator);

    /** 置为已取消（带状态条件，只有草稿能取消）。 */
    int markCancelled(@Param("id") Long id,
                      @Param("operator") String operator);

    /** 回写草稿头（仓库 / 备注）。 */
    int updateDraft(@Param("id") Long id,
                    @Param("warehouseId") Long warehouseId,
                    @Param("remark") String remark,
                    @Param("operator") String operator);

    /** 分页查询（联仓库取展示字段）。 */
    List<InventoryStocktakeVO> queryPage(Page<?> page, @Param("query") InventoryStocktakeQueryForm query);

    /** 详情。 */
    InventoryStocktakeVO detail(@Param("id") Long id);
}
