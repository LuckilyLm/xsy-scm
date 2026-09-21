package net.lab1024.sa.admin.module.scm.inventory.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import net.lab1024.sa.admin.module.scm.inventory.domain.entity.InventoryOutboundItemEntity;
import net.lab1024.sa.admin.module.scm.inventory.domain.vo.InventoryOutboundItemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 出库单明细读写。
 *
 * <p>明细整行替换只发生在**草稿态**（先逻辑删旧行再插新行，同一事务内）。
 * 确认出库后不再有任何写方法被调用 —— 由服务层状态机保证，本接口不额外设卡。
 */
@Mapper
public interface InventoryOutboundItemDao extends BaseMapper<InventoryOutboundItemEntity> {

    /**
     * 某单下的明细（含展示字段，按 id 升序 —— 即录入顺序）。
     */
    List<InventoryOutboundItemVO> listByOutboundId(@Param("outboundId") Long outboundId);

    /**
     * 软删某单下的全部明细（草稿重存时用）。
     */
    int deleteByOutboundId(@Param("outboundId") Long outboundId,
                           @Param("operator") String operator);

    /**
     * 回写单位快照（确认出库时按余额记账单位写入）。
     */
    int updateUnitSnapshot(@Param("id") Long id,
                           @Param("unit") String unit,
                           @Param("operator") String operator);
}
