package com.xsy.scm.inventory.dao;

import com.xsy.scm.inventory.domain.entity.InventoryLotMovementEntity;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 批次流水只追加。SQL 见 mapper/scm/inventory/InventoryLotMovementDao.xml。
 *
 * <p>
 * 数据库侧有语句级触发器兜底：任何 UPDATE / DELETE / TRUNCATE 都会直接报错，因此这里只提供插入。
 */
@Mapper
public interface InventoryLotMovementDao {

    int insert(InventoryLotMovementEntity movement);

    /**
     * 按来源单据行汇总每个批次的净额（入库为正、出库为负）。
     *
     * <p>
     * 必须限定来源单据类型：不同单据表的行 id 可能相同，只按行 id 汇总会把其他单据的流水混进净额。
     */
    List<LotSourceNet> sumByLotForSource(@Param("warehouseId") Long warehouseId, @Param("skuId") Long skuId,
            @Param("sourceDocumentItemId") Long sourceDocumentItemId,
            @Param("sourceDocumentTypes") List<String> sourceDocumentTypes);

    /** 来源行在某批次上的净额。 */
    @Data
    class LotSourceNet {
        private Long lotId;
        private BigDecimal netQuantity;
    }
}
