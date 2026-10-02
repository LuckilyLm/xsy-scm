package com.xsy.scm.inventory.dao;

import com.xsy.scm.inventory.domain.entity.InventoryWarningStateEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 库存预警通知去重状态的读写。
 *
 * <p>
 * <b>刻意不继承 {@code BaseMapper}</b>：这里没有「按 id 增删改」的通用语义，三个方法各自
 * 对应通知流程里的一步（读上次状态 / 首次落状态 / 推进状态），暴露通用 CRUD 只会让人以为
 * 这张表可以被随意改写 —— 它的每一列都直接决定「会不会重复发通知」。
 */
@Mapper
public interface InventoryWarningStateDao {

    /**
     * 读某 (仓库, SKU) 的上次状态，并加行锁串行化同一键上的并发评估。
     */
    InventoryWarningStateEntity selectForUpdate(@Param("warehouseId") Long warehouseId, @Param("skuId") Long skuId);

    /**
     * 首次落状态；已存在时返回 0（并发对手先写成功），调用方须重读收敛。
     */
    int insertIfAbsent(@Param("warehouseId") Long warehouseId, @Param("skuId") Long skuId,
            @Param("status") String status, @Param("epoch") Integer epoch, @Param("operator") String operator);

    /**
     * 推进状态与纪元。
     *
     * @return 影响行数，必须为 1
     */
    int updateState(@Param("warehouseId") Long warehouseId, @Param("skuId") Long skuId,
            @Param("status") String status, @Param("epoch") Integer epoch, @Param("operator") String operator);
}
