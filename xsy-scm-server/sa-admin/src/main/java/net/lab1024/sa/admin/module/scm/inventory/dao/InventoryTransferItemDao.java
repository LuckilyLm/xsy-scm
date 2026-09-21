package net.lab1024.sa.admin.module.scm.inventory.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import net.lab1024.sa.admin.module.scm.inventory.domain.entity.InventoryTransferItemEntity;
import net.lab1024.sa.admin.module.scm.inventory.domain.vo.InventoryTransferItemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 调拨单明细读写。
 *
 * <p>明细整行替换只发生在**草稿态**（先逻辑删旧行再插新行，同一事务内）。
 * 发出之后不再有任何写方法被调用 —— 由服务层状态机与 {@code InventoryTransferDao} 的
 * SQL 守卫共同保证。
 *
 * <p>注意 {@link #updateUnitSnapshot} 只在**发出**时被调用一次：收货不再改单位快照，
 * 因为它是「源仓记账单位」的事实记录，收货要做的是**断言**目标仓与之一致，而不是改写它。
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
    int deleteByTransferId(@Param("transferId") Long transferId,
                           @Param("operator") String operator);

    /**
     * 回写单位快照（**发出**时按源仓记账单位写入）。
     */
    int updateUnitSnapshot(@Param("id") Long id,
                           @Param("unit") String unit,
                           @Param("operator") String operator);
}
