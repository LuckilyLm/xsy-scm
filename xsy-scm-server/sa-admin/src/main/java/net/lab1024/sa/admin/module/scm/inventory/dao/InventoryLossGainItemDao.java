package net.lab1024.sa.admin.module.scm.inventory.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import net.lab1024.sa.admin.module.scm.inventory.domain.entity.InventoryLossGainItemEntity;
import net.lab1024.sa.admin.module.scm.inventory.domain.vo.InventoryLossGainItemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 报损报溢单明细读写。
 *
 * <p>明细整行替换只发生在**待审核态**（先逻辑删旧行再插新行，同一事务内）。
 * 审批之后不再有任何写方法被调用 —— 由服务层状态机与 {@code InventoryLossGainDao} 的
 * SQL 守卫共同保证。
 */
@Mapper
public interface InventoryLossGainItemDao extends BaseMapper<InventoryLossGainItemEntity> {

    /** 某单下的明细（含展示字段，按 id 升序 —— 即录入顺序）。 */
    List<InventoryLossGainItemVO> listByLossGainId(@Param("lossGainId") Long lossGainId);

    /** 软删某单下的全部明细（待审核重存时用）。 */
    int deleteByLossGainId(@Param("lossGainId") Long lossGainId,
                           @Param("operator") String operator);

    /** 回写单位快照（审批通过时按余额记账单位写入）。 */
    int updateUnitSnapshot(@Param("id") Long id,
                           @Param("unit") String unit,
                           @Param("operator") String operator);
}
