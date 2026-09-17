package com.xsy.scm.admin.module.business.stock.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.stock.domain.entity.StockBalanceEntity;
import com.xsy.scm.admin.module.business.stock.domain.form.StockBalanceQueryForm;
import com.xsy.scm.admin.module.business.stock.domain.vo.StockBalanceVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 库存余额 Dao
 *
 * <p>余额由流水推导，更新余额必须走库存业务层，禁止在本 Dao 之外直接 UPDATE。</p>
 *
 * @author xsy-scm
 */
@Mapper
public interface StockBalanceDao extends BaseMapper<StockBalanceEntity> {

    /**
     * 根据规格与仓库查询库存余额
     */
    StockBalanceEntity queryBySku(@Param("skuId") Long skuId, @Param("warehouseId") Long warehouseId);

    /**
     * 根据商品查询库存余额列表
     */
    List<StockBalanceEntity> queryByProductId(@Param("productId") Long productId);

    /**
     * 分页查询库存余额
     */
    List<StockBalanceVO> queryPage(Page page, @Param("queryForm") StockBalanceQueryForm queryForm);

    /**
     * 批量更新删除状态
     */
    void batchUpdateDeleted(@Param("balanceIdList") List<Long> balanceIdList,
                            @Param("deletedFlag") Boolean deletedFlag);
}
