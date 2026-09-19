package net.lab1024.sa.admin.module.scm.inventory.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import net.lab1024.sa.admin.module.scm.inventory.domain.entity.InventoryConversionItemEntity;
import net.lab1024.sa.admin.module.scm.inventory.domain.vo.InventoryConversionItemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 规格转换单明细读写。
 *
 * <p>明细整行替换只发生在**待审核态**（先逻辑删旧行再插新行，同一事务内）。
 * 审批之后不再有任何写方法被调用。
 *
 * <p>注意**没有** {@code updateUnitSnapshot}：与调拨不同，转换的单位不是「从余额读出来回写」的，
 * 而是**单据自己声明的**（折算关系的一部分）。执行时只做比对，不改写。
 */
@Mapper
public interface InventoryConversionItemDao extends BaseMapper<InventoryConversionItemEntity> {

    /** 某单下的明细（含源 / 目标两套展示字段，按 id 升序 —— 即录入顺序）。 */
    List<InventoryConversionItemVO> listByConversionId(@Param("conversionId") Long conversionId);

    /** 软删某单下的全部明细（待审核重存时用）。 */
    int deleteByConversionId(@Param("conversionId") Long conversionId,
                             @Param("operator") String operator);
}
