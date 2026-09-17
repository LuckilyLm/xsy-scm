package com.xsy.scm.admin.module.business.stock.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.admin.module.business.stock.domain.entity.StockFlowEntity;
import com.xsy.scm.admin.module.business.stock.domain.form.StockFlowQueryForm;
import com.xsy.scm.admin.module.business.stock.domain.vo.StockFlowVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 库存流水 Dao
 *
 * <p>流水不做逻辑删除，冲销使用反向流水，因此不提供 batchUpdateDeleted。</p>
 *
 * @author xsy-scm
 */
@Mapper
public interface StockFlowDao extends BaseMapper<StockFlowEntity> {

    /**
     * 根据规格查询流水
     */
    List<StockFlowEntity> queryBySku(@Param("skuId") Long skuId);

    /**
     * 根据关联业务查询流水
     */
    List<StockFlowEntity> queryByBiz(@Param("bizType") Integer bizType, @Param("bizId") Long bizId);

    /**
     * 分页查询库存流水
     */
    List<StockFlowVO> queryPage(Page page, @Param("queryForm") StockFlowQueryForm queryForm);
}
