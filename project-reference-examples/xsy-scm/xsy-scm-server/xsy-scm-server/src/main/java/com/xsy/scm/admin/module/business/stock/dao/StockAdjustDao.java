package com.xsy.scm.admin.module.business.stock.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.stock.domain.entity.StockAdjustEntity;
import com.xsy.scm.admin.module.business.stock.domain.form.StockAdjustQueryForm;
import com.xsy.scm.admin.module.business.stock.domain.vo.StockAdjustVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 库存调整单 Dao（报损 / 报溢 / 盘点调整 / 规格转换）
 *
 * @author xsy-scm
 */
@Mapper
public interface StockAdjustDao extends BaseMapper<StockAdjustEntity> {

    /**
     * 根据规格查询调整记录
     */
    List<StockAdjustEntity> queryBySkuId(@Param("skuId") Long skuId);

    /**
     * 分页查询库存调整单
     */
    List<StockAdjustVO> queryPage(Page page, @Param("queryForm") StockAdjustQueryForm queryForm);

    /**
     * 批量更新删除状态
     */
    void batchUpdateDeleted(@Param("adjustIdList") List<Long> adjustIdList,
                            @Param("deletedFlag") Boolean deletedFlag);
}
