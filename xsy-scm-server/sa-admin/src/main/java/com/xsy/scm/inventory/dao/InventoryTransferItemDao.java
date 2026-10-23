package com.xsy.scm.inventory.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.inventory.domain.entity.InventoryTransferItemEntity;
import com.xsy.scm.inventory.domain.vo.InventoryTransferItemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 调拨单明细读写。
 *
 * <p>
 * 明细整行替换只发生在草稿态（先逻辑删旧行再插新行，同一事务内）。发出之后不再有任何写方法被调用 —— 由服务层状态机与 {@code InventoryTransferDao} 的 SQL 条件共同保证。
 *
 * <p>
 * 注意 {@link #updateUnitSnapshot} 只在发出时被调用一次：收货不再改单位记录，因为它是「源仓记账单位」的记录，收货要做的是核对目标仓与之一致，而不是改写它。
 */
@Mapper
public interface InventoryTransferItemDao extends BaseMapper<InventoryTransferItemEntity> {

    /**
     * 某单下的明细（含展示字段，按 id 升序 —— 即录入顺序）。
     */
    List<InventoryTransferItemVO> listByTransferId(@Param("transferId") Long transferId);

    /**
     * 软删某单下的全部明细（草稿重存时用）。
     */
    int deleteByTransferId(@Param("transferId") Long transferId, @Param("operator") String operator);

    /**
     * 回写单位记录（发出时按源仓记账单位写入）。
     */
    int updateUnitSnapshot(@Param("id") Long id, @Param("unit") String unit, @Param("operator") String operator);
}
